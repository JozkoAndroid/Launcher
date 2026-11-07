package de.mm20.launcher2.ui.launcher.widgets.school.context

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.lifecycle.AndroidViewModel
import de.mm20.launcher2.music.MusicService
import de.mm20.launcher2.music.PlaybackState
import de.mm20.launcher2.permissions.PermissionGroup
import de.mm20.launcher2.permissions.PermissionsManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class ContextVM(app: Application) : AndroidViewModel(app), KoinComponent {
    private val music: MusicService by inject()
    private val permissions: PermissionsManager by inject()
    private val store = ContextSettingsStore(app)

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
    }

    val cards: Flow<List<ContextCard>> = combine(musicCard, polled) { m, p -> ContextRules.pick(listOfNotNull(m) + p) }

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
