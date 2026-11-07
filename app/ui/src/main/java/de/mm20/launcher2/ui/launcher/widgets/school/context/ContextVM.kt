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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.shareIn
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
    private val store = ContextSettingsStore(app)

    val lunchPackage = MutableStateFlow(store.lunchPackage)

    val settings = MutableStateFlow(store.load())

    private val musicCard: Flow<ContextCard.Music?> =
        combine(music.playbackState, music.title, music.artist) { state, title, artist ->
            // Playing, or paused in an active session so the inline play button stays reachable.
            if (title.isNullOrBlank() || state == PlaybackState.Stopped) null
            else ContextCard.Music(title, artist, playing = state == PlaybackState.Playing)
        }

    /** Polled cards: refreshed every 5 minutes while the block is on screen. */
    private val polled: Flow<List<ContextCard>> = flow {
        while (true) {
            emit(readPolled())
            delay(5 * 60_000L)
        }
    }.shareIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000), replay = 1)

    /**
     * Today's lunch from the EduPage food app's notification (chosen in settings, or detected by name).
     * Kept until 15:00 even after the notification is dismissed.
     */
    private val lunchCard: Flow<ContextCard.Lunch?> = combine(notificationRepository.notifications, lunchPackage, polled) { list, chosen, _ ->
        val ctx = getApplication<Application>()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        // Chosen app, else any app whose name looks like a lunch app (e.g. "Lunch Bell").
        val isLunchApp = { pkg: String -> if (chosen != null) pkg == chosen else ContextRules.looksLikeLunchApp(ContextSources.appLabel(ctx, pkg)) }
        list.filter {
            isLunchApp(it.packageName) &&
                ContextRules.lunchDay(java.time.Instant.ofEpochMilli(it.postTime).atZone(zone).toLocalDateTime()) == today
        }
            .maxByOrNull { it.postTime }
            ?.let { n ->
                ContextRules.lunchText(
                    n.extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString(),
                    n.extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString(),
                    n.extras.getCharSequence(android.app.Notification.EXTRA_BIG_TEXT)?.toString(),
                )?.let { store.storeLunch(today, n.packageName, it) }
            }
        if (LocalTime.now(zone).isAfter(ContextRules.LunchUntil)) null
        else store.storedLunch(today)?.let { (pkg, text) -> ContextCard.Lunch(text, pkg) }
    }

    /** School chips from the brief: nearest test within 3 days, unread Schoolhelp messages. */
    private val schoolCards: Flow<List<ContextCard>> = combine(brief.feed, brief.unreadChats, polled) { f, unread, _ ->
        listOfNotNull(
            f?.let { ContextRules.testSoon(it.tasks, LocalDate.now()) },
            unread.takeIf { it > 0 }?.let { ContextCard.Chats(it) },
        )
    }

    val cards: Flow<List<ContextCard>> = combine(musicCard, lunchCard, schoolCards, polled) { m, l, s, p ->
        ContextRules.pick(listOfNotNull(m, l) + s + p)
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
        ContextSources.mobileBytes(ctx, cycleFrom, now)?.let { used ->
            if (ContextRules.showData(used, s)) out += ContextCard.MobileData(used, s.planBytes)
        }
        out += ContextSources.birthdays(ctx, today)
        val names = ContextRules.nameDay(ContextSources.nameDays(ctx), today)
        if (names.isNotEmpty()) {
            val contact = ContextSources.nameDayContact(ctx, names)
            out += ContextCard.NameDay(names, contact?.first, contact?.second)
        }
        if (!LocalTime.now(zone).isBefore(s.stepsAfter)) {
            ContextSources.stepsToday(ctx, zone)?.let { steps ->
                if (ContextRules.showSteps(LocalTime.now(zone), steps, s)) out += ContextCard.Steps(steps, s.stepsGoal)
            }
        }
        ContextSources.instagramMinutes(ctx, now, zone)?.let { min ->
            if (ContextRules.showInstagram(min)) out += ContextCard.Instagram(min)
        }
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

    fun requestContacts(activity: androidx.appcompat.app.AppCompatActivity) =
        permissions.requestPermission(activity, PermissionGroup.Contacts)

    fun openUsageAccess() = runCatching { getApplication<Application>().startActivity(ContextSources.usageAccessIntent()) }
}
