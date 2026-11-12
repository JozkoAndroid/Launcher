package de.mm20.launcher2.ui.launcher.widgets.school.context

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.mm20.launcher2.music.MusicService
import de.mm20.launcher2.music.PlaybackState
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class ContextVM(app: Application) : AndroidViewModel(app), KoinComponent {
    private val music: MusicService by inject()
    private val permissions: PermissionsManager by inject()
    private val notificationRepository: de.mm20.launcher2.notifications.NotificationRepository by inject()
    private val brief: de.mm20.launcher2.schoolbrief.BriefRepository by inject()
    private val widgets: de.mm20.launcher2.widgets.WidgetRepository by inject()
    private val store = ContextSettingsStore(app)

    val lunchPackage = MutableStateFlow(store.lunchPackage)

    /** App labels by package: looked up once, not on every notification. */
    private val labels = java.util.concurrent.ConcurrentHashMap<String, String>()

    /**
     * Round 36 (device: "I set the steps goal to 10 000, the tile still says 8 000"): one value for the whole
     * app. The settings screen has its own ViewModel; a copy per ViewModel left home with the old goal.
     */
    val settings: MutableStateFlow<ContextSettings> = ContextSettingsStore.shared(store)

    /** Usage access (mobile data, Instagram): a dedicated block without it shows a setup chip. */
    val usageAccess = MutableStateFlow(true)

    private val musicCard: Flow<ContextCard.Music?> =
        combine(music.playbackState, music.title, music.artist) { state, title, artist ->
            // Playing, or paused in an active session so the inline play button stays reachable.
            if (title.isNullOrBlank() || state == PlaybackState.Stopped) null
            else ContextCard.Music(title, artist, playing = state == PlaybackState.Playing)
        }

    /** Asks for a fresh read now (round 15: the data tile showed 1,3 GB while Android's screen said 1,4). */
    private val refreshes = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    fun refresh() { refreshes.tryEmit(Unit) }

    /** Polled cards: every 5 minutes while a block is on screen, and at once on [refresh] (back to home). */
    private val polled: Flow<List<ContextCard>> = kotlinx.coroutines.flow.channelFlow {
        launch { refreshes.collect { send(readPolled()) } }
        // A changed goal or plan shows at once, not after the next 5-minute read.
        launch { settings.drop(1).collect { send(readPolled()) } }
        while (true) {
            send(readPolled())
            delay(5 * 60_000L)
        }
    }
        // Contacts, name-day JSON, data and app usage queries: never on the main thread (jank on swipe).
        .flowOn(kotlinx.coroutines.Dispatchers.IO)
        .shareIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000), replay = 1)

    /**
     * Today's lunch from the lunch app's notification (never EduPage) (chosen in settings, or detected by name).
     * Kept until 15:00 even after the notification is dismissed.
     */
    private val lunchCard: Flow<ContextCard.Lunch?> = combine(notificationRepository.notifications, lunchPackage, polled) { list, chosen, _ ->
        val ctx = getApplication<Application>()
        val zone = ZoneId.systemDefault()
        val now = java.time.LocalDateTime.now(zone)
        // Chosen app, else any app whose name looks like a lunch app (e.g. "Lunch Bell").
        val label = { pkg: String -> labels.getOrPut(pkg) { ContextSources.appLabel(ctx, pkg) } }
        val isLunchApp = { pkg: String ->
            !ContextRules.isEduPage(pkg, label(pkg)) && (if (chosen != null) pkg == chosen else ContextRules.looksLikeLunchApp(label(pkg)))
        }
        list.filter { isLunchApp(it.packageName) }.maxByOrNull { it.postTime }?.let { n ->
            val day = ContextRules.lunchDay(java.time.Instant.ofEpochMilli(n.postTime).atZone(zone).toLocalDateTime())
            ContextRules.lunchText(
                n.extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString(),
                n.extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString(),
                n.extras.getCharSequence(android.app.Notification.EXTRA_BIG_TEXT)?.toString(),
            )?.let { store.storeLunch(day, n.packageName, it) }
        }
        store.storedLunch()?.takeIf { (day, pkg, _) -> ContextRules.lunchVisible(day, now) && !ContextRules.isEduPage(pkg, label(pkg)) }?.let { (day, pkg, text) ->
            ContextCard.Lunch(text, pkg, tomorrow = day.isAfter(now.toLocalDate()))
        }
    }.flowOn(kotlinx.coroutines.Dispatchers.Default)

    /** School chips from the brief: nearest test within 3 days, unread Schoolhelp messages. */
    private val schoolCards: Flow<List<ContextCard>> = combine(brief.feed, brief.unreadChats, polled) { f, unread, _ ->
        listOfNotNull(
            f?.let { ContextRules.testSoon(it.tasks, LocalDate.now()) },
            unread.takeIf { it > 0 }?.let { ContextCard.Chats(it) },
        )
    }

    /** Every card with a value right now, most urgent first (single chip blocks pick theirs from here). */
    val allCards: Flow<List<ContextCard>> = combine(musicCard, lunchCard, schoolCards, polled) { m, l, s, p ->
        (listOfNotNull(m, l) + s + p).sortedBy { it.rank }
    }
        // One shared computation for "Práve teraz" and every chip block, off the main thread.
        .flowOn(kotlinx.coroutines.Dispatchers.Default)
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Chip kinds that have their own block somewhere: "Práve teraz" leaves them out (device test: duplicates). */
    private val ownBlocks: Flow<Set<String>> = combine(
        de.mm20.launcher2.widgets.ChipWidget.Kinds.map { k -> widgets.exists(de.mm20.launcher2.widgets.ChipWidget.TypePrefix + k).map { if (it) k else null } }
    ) { it.filterNotNull().toSet() }

    /** "Práve teraz": the most urgent four, without kinds that have their own block. */
    val cards: Flow<List<ContextCard>> = combine(allCards, ownBlocks, settings) { all, own, s ->
        val now = LocalTime.now()
        all.filter { it.kind() !in own && ContextRules.urgent(it, now, s) }.take(ContextRules.MaxCards)
    }

    fun setLunchPackage(pkg: String?) {
        store.lunchPackage = pkg; lunchPackage.value = pkg
    }

    fun lunchCandidates() = ContextSources.lunchAppCandidates(getApplication())
    fun appLabel(pkg: String) = ContextSources.appLabel(getApplication(), pkg)

    private suspend fun readPolled(): List<ContextCard> {
        val ctx = getApplication<Application>()
        val zone = ZoneId.systemDefault()
        val s = settings.value
        val today = LocalDate.now(zone)
        val now = System.currentTimeMillis()
        val out = mutableListOf<ContextCard>()

        val cycleFrom = ContextRules.cycleStart(today, s).atStartOfDay(zone).toInstant().toEpochMilli()
        usageAccess.value = ContextSources.hasUsageAccess(ctx)
        // Every value is read; "Práve teraz" shows only the urgent ones (ContextRules.urgent), a dedicated block shows its value always.
        val renews = ContextRules.cycleStart(today, s).plusDays(s.cycleDays)
        ContextSources.mobileBytes(ctx, cycleFrom, now)?.let { used -> out += ContextCard.MobileData(used, s.planBytes, renews) }
        out += ContextSources.birthdays(ctx, today)
        val names = ContextRules.nameDay(ContextSources.nameDays(ctx), today)
        if (names.isNotEmpty()) {
            val contact = ContextSources.nameDayContact(ctx, names)
            out += ContextCard.NameDay(names, contact?.first, contact?.second)
        }
        ContextSources.stepsToday(ctx, zone)?.let { steps -> out += ContextCard.Steps(steps, s.stepsGoal) }
        ContextSources.instagramMinutes(ctx, now, zone)?.let { min -> out += ContextCard.Instagram(min) }
        return out
    }

    fun togglePause() = music.togglePause()

    /** Tap actions per card (spec): player, data usage, contact, Samsung Health. Instagram has none. */
    fun open(card: ContextCard) {
        val ctx = getApplication<Application>()
        val intent: Intent? = when (card) {
            is ContextCard.Music -> {
                runCatching { music.openPlayer()?.send() }; null
            }
            is ContextCard.MobileData -> Intent(android.provider.Settings.ACTION_DATA_USAGE_SETTINGS)
            is ContextCard.Birthday -> Intent(Intent.ACTION_VIEW, Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, card.contactId.toString()))
            is ContextCard.Steps -> ctx.packageManager.getLaunchIntentForPackage(ContextSources.SamsungHealthPackage)
            is ContextCard.Instagram -> null
            is ContextCard.TestSoon, is ContextCard.Chats -> {
                de.mm20.launcher2.ui.launcher.widgets.school.Links.openSchoolhelp(ctx); null
            }
            is ContextCard.Lunch -> ctx.packageManager.getLaunchIntentForPackage(card.packageName)
            is ContextCard.NameDay -> card.contactId?.let {
                Intent(Intent.ACTION_VIEW, Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, it.toString()))
            }
        }
        intent?.let { runCatching { ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    }

    // --- Settings sheet ---

    fun saveSettings(s: ContextSettings) {
        store.save(s); settings.value = s
    }

    val hasNotificationAccess: Flow<Boolean> = permissions.hasPermission(PermissionGroup.Notifications)
    val hasContacts: Flow<Boolean> = permissions.hasPermission(PermissionGroup.Contacts)
    fun hasUsageAccess() = ContextSources.hasUsageAccess(getApplication())
    suspend fun hasStepsPermission() = ContextSources.hasStepsPermission(getApplication())
    fun healthConnectAvailable() = ContextSources.healthConnectAvailable(getApplication())

    fun requestNotificationAccess(activity: androidx.appcompat.app.AppCompatActivity) =
        permissions.requestPermission(activity, PermissionGroup.Notifications)

    val hasCalendar: Flow<Boolean> = permissions.hasPermission(PermissionGroup.Calendar)
    fun requestCalendar(activity: androidx.appcompat.app.AppCompatActivity) =
        permissions.requestPermission(activity, PermissionGroup.Calendar)

    val hasLocation: Flow<Boolean> = permissions.hasPermission(PermissionGroup.Location)
    fun requestLocation(activity: androidx.appcompat.app.AppCompatActivity) =
        permissions.requestPermission(activity, PermissionGroup.Location)

    fun requestContacts(activity: androidx.appcompat.app.AppCompatActivity) =
        permissions.requestPermission(activity, PermissionGroup.Contacts)

    fun openUsageAccess() = runCatching { getApplication<Application>().startActivity(ContextSources.usageAccessIntent()) }
}
