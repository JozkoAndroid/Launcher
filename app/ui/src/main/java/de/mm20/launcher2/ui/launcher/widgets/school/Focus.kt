package de.mm20.launcher2.ui.launcher.widgets.school

import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import de.mm20.launcher2.schoolbrief.BriefRepository
import de.mm20.launcher2.schoolbrief.PlanStep
import de.mm20.launcher2.ui.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** A running focus session, on a plan step of Schoolhelp's day ([step]) or free ([step] = null). */
data class Focus(val step: Int?, val text: String, val minutes: Int, val endsAt: Long)

/**
 * Focus timer. Round 15 (device test: "it doesn't pop up anywhere"): it could only start from a
 * Schoolhelp plan step with minutes, and nothing showed outside the launcher. Now:
 * - start: a plan step's play button, or a free session (15/25/45 min) on "Môj deň";
 * - while running: a silent notification with the live countdown (lock screen, status bar), "Hotovo"
 *   and "Zastaviť" on it;
 * - at the end: an exact alarm and a loud notification with "Hotovo" and "+5 min".
 * Time running out no longer ticks the step: only "Hotovo" does. Kept in preferences, so it survives the
 * launcher being restarted.
 */
object FocusTimer : KoinComponent {
    private val brief: BriefRepository by inject()
    private const val Prefs = "focus"
    private val state = MutableStateFlow<Focus?>(null)
    private var loaded = false

    private const val LiveChannel = "focus_live"
    private const val EndChannel = "focus"
    private const val LiveId = 4711
    private const val EndId = 4712

    internal const val ActionDone = "de.mm20.launcher2.school.FOCUS_DONE"
    internal const val ActionStop = "de.mm20.launcher2.school.FOCUS_STOP"
    internal const val ActionExtend = "de.mm20.launcher2.school.FOCUS_EXTEND"
    internal const val ExtraStep = "step"
    internal const val ExtraText = "text"
    const val ExtendMinutes = 5

    fun current(context: Context): StateFlow<Focus?> {
        if (!loaded) {
            loaded = true
            val p = context.getSharedPreferences(Prefs, Context.MODE_PRIVATE)
            val ends = p.getLong("ends", 0L)
            if (ends > 0) {
                state.value = Focus(
                    p.getInt("step", -1).takeIf { it >= 0 }, p.getString("text", "").orEmpty(), p.getInt("minutes", 0), ends,
                )
            }
        }
        return state
    }

    fun start(context: Context, s: PlanStep) {
        start(context, s.index, s.text, s.minutes ?: return)
    }

    fun start(context: Context, step: Int?, text: String, minutes: Int) {
        val f = Focus(step, text, minutes, System.currentTimeMillis() + minutes * 60_000L)
        save(context, f)
        val am = alarms(context)
        // Exact (USE_EXACT_ALARM): round 13's inexact alarm came minutes late.
        if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, f.endsAt, alarm(context))
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, f.endsAt, alarm(context))
        }
        nm(context).cancel(EndId)
        notify(context, LiveId, live(context, f))
    }

    /** Ends the session early; [done] = tick the plan step in Schoolhelp. */
    fun stop(context: Context, done: Boolean) {
        val f = current(context).value ?: return
        if (done) f.step?.let { brief.setStepDone(it, true) }
        alarms(context).cancel(alarm(context))
        nm(context).cancel(LiveId)
        save(context, null)
    }

    /** "Hotovo" on a notification: the running session, or the step of the one that just ran out. */
    internal fun done(context: Context, step: Int?) {
        if (current(context).value != null) stop(context, done = true)
        else step?.let { brief.setStepDone(it, true) }
        nm(context).cancel(EndId)
    }

    /** Time is up (alarm, or the countdown on screen reached 0): the loud notification, nothing ticked. */
    internal fun finished(context: Context) {
        val f = current(context).value ?: return
        alarms(context).cancel(alarm(context))
        save(context, null)
        val nm = nm(context)
        nm.cancel(LiveId)
        val n = NotificationCompat.Builder(context, EndChannel)
            .setSmallIcon(R.drawable.timer_24px)
            .setContentTitle(context.getString(R.string.focus_done_title))
            .setContentText(f.text)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openLauncher(context))
            .setAutoCancel(true)
            .apply { if (f.step != null) addAction(0, context.getString(R.string.focus_finish), action(context, ActionDone, f)) }
            .addAction(0, context.getString(R.string.focus_extend, ExtendMinutes), action(context, ActionExtend, f))
            .build()
        notify(context, EndId, n)
    }

    /** True if the end of a session can be shown outside the launcher. */
    fun canNotify(context: Context) = NotificationManagerCompat.from(context).areNotificationsEnabled()

    private fun live(context: Context, f: Focus) = NotificationCompat.Builder(context, LiveChannel)
        .setSmallIcon(R.drawable.timer_24px)
        .setContentTitle(f.text)
        .setContentText(context.getString(R.string.focus_live_text, f.minutes))
        // The system draws the countdown; nothing in the launcher has to run for it.
        .setWhen(f.endsAt)
        .setShowWhen(true)
        .setUsesChronometer(true)
        .setChronometerCountDown(true)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setSilent(true)
        .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
        // Android 16 Live Update (status bar chip, lock screen) where the phone supports it.
        .setRequestPromotedOngoing(true)
        .setContentIntent(openLauncher(context))
        .addAction(0, context.getString(R.string.focus_finish), action(context, ActionDone, f))
        .addAction(0, context.getString(R.string.focus_cancel), action(context, ActionStop, f))
        .build()

    private fun notify(context: Context, id: Int, n: android.app.Notification) {
        val nm = nm(context)
        nm.createNotificationChannel(NotificationChannel(LiveChannel, context.getString(R.string.focus_live_channel), NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(EndChannel, context.getString(R.string.focus_channel), NotificationManager.IMPORTANCE_HIGH))
        runCatching { nm.notify(id, n) }
    }

    private fun save(context: Context, f: Focus?) {
        state.value = f
        context.getSharedPreferences(Prefs, Context.MODE_PRIVATE).edit().apply {
            if (f == null) clear()
            // "block" (round 15): the launcher's accessibility service sends Instagram, YouTube and Threads
            // home while a session runs (services/global-actions LauncherAccessibilityService).
            else putInt("step", f.step ?: -1).putString("text", f.text).putInt("minutes", f.minutes).putLong("ends", f.endsAt).putBoolean("block", true)
        }.apply()
    }

    private fun nm(context: Context) = context.getSystemService(NotificationManager::class.java)
    private fun alarms(context: Context) = context.getSystemService(AlarmManager::class.java)
    private fun alarm(context: Context) = PendingIntent.getBroadcast(
        context, LiveId, Intent(context, FocusAlarmReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun action(context: Context, action: String, f: Focus) = PendingIntent.getBroadcast(
        context, action.hashCode(),
        Intent(context, FocusAlarmReceiver::class.java).setAction(action)
            .putExtra(ExtraStep, f.step ?: -1).putExtra(ExtraText, f.text),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun openLauncher(context: Context): PendingIntent? {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        return PendingIntent.getActivity(context, LiveId, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}

class FocusAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val c = context.applicationContext
        val step = intent?.getIntExtra(FocusTimer.ExtraStep, -1)?.takeIf { it >= 0 }
        when (intent?.action) {
            FocusTimer.ActionDone -> FocusTimer.done(c, step)
            FocusTimer.ActionStop -> FocusTimer.stop(c, done = false)
            FocusTimer.ActionExtend -> FocusTimer.start(c, step, intent.getStringExtra(FocusTimer.ExtraText).orEmpty(), FocusTimer.ExtendMinutes)
            else -> FocusTimer.finished(c)
        }
    }
}

/**
 * Starts a session; asks for the notification permission first (the countdown and the end are
 * notifications). Denied still starts: the countdown then shows only in the launcher.
 */
@Composable
internal fun rememberFocusStarter(): (step: Int?, text: String, minutes: Int) -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<Triple<Int?, String, Int>?>(null) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        pending?.let { (s, t, m) -> FocusTimer.start(context, s, t, m) }
        pending = null
    }
    return { step, text, minutes ->
        val granted = Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (granted) FocusTimer.start(context, step, text, minutes)
        else {
            pending = Triple(step, text, minutes)
            ask.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

/** Time left in ms, ticking each second while shown; ends the session if it ran out while away. */
@Composable
internal fun rememberFocusLeft(f: Focus): Long {
    val context = LocalContext.current
    var left by remember(f) { mutableLongStateOf(f.endsAt - System.currentTimeMillis()) }
    LaunchedEffect(f) {
        while (true) {
            left = f.endsAt - System.currentTimeMillis()
            if (left <= 0) { FocusTimer.finished(context.applicationContext); break }
            delay(1000L - System.currentTimeMillis() % 1000L)
        }
    }
    return left.coerceAtLeast(0)
}

internal fun clock(ms: Long): String = "%d:%02d".format(ms / 60_000, ms / 1000 % 60)

/** Free sessions from the focus bar on "Môj deň", without a Schoolhelp plan step (user: 15, 20, 30). */
internal val FreeFocusMinutes = listOf(15, 20, 30, 45)

/**
 * On "Môj deň", under the date. Running: the countdown large, the step under it, done or stop as quiet
 * text buttons. Otherwise one quiet line to start a free session.
 */
@Composable
internal fun FocusPanel(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val f by FocusTimer.current(context).collectAsState()
    // Starting is in the focus bar at the bottom ("Fokus" mode); here only the running session.
    val focus = f ?: return
    val left = rememberFocusLeft(focus)
    Column(modifier.widthIn(max = 640.dp).fillMaxWidth().padding(top = 24.dp)) {
        Text(stringResource(R.string.focus_label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(clock(left), style = SchoolFonts.display(64).merge(SchoolTokens.Tabular), color = MaterialTheme.colorScheme.primary)
        Text(focus.text, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (!FocusTimer.canNotify(context)) {
            // Honest: with notifications off, the end shows only here.
            Text(stringResource(R.string.focus_no_notifications), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { FocusTimer.stop(context, done = true) }) { Text(stringResource(R.string.focus_finish)) }
            TextButton(onClick = { FocusTimer.stop(context, done = false) }) { Text(stringResource(R.string.focus_cancel)) }
        }
    }
}

/** On home, above the dock: a quiet line while a session runs ("18:42 · Für Ethik lernen"). */
@Composable
internal fun FocusLine(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val f by FocusTimer.current(context).collectAsState()
    val focus = f ?: return
    val left = rememberFocusLeft(focus)
    val content = schoolContentColor()
    Row(modifier.padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            clock(left) + " · " + focus.text,
            style = MaterialTheme.typography.bodyMedium.merge(SchoolTokens.Tabular),
            color = content, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 320.dp),
        )
    }
}

/**
 * The focus bar ("Fokus" on "Môj deň", round 15): the same pill and ink button as every bar. Idle: pick
 * 15, 20, 30 or 45 minutes and start. Running: the countdown and a stop button. Blocking needs the
 * launcher's accessibility service; until it is on, one quiet line offers to turn it on.
 */
@Composable
internal fun FocusBar(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val f by FocusTimer.current(context).collectAsState()
    var minutes by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableIntStateOf(20) }
    val permissions: de.mm20.launcher2.permissions.PermissionsManager = org.koin.compose.koinInject()
    val canBlock by remember { permissions.hasPermission(de.mm20.launcher2.permissions.PermissionGroup.Accessibility) }.collectAsState(true)
    val start = rememberFocusStarter()
    val freeText = stringResource(R.string.focus_free)
    val text = MaterialTheme.colorScheme.onSurface
    Column(modifier.widthIn(max = 640.dp).fillMaxWidth()) {
        if (!canBlock) {
            Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Secondary(stringResource(R.string.focus_block_off), Modifier.weight(1f))
                TextButton(onClick = {
                    (context as? androidx.appcompat.app.AppCompatActivity)?.let {
                        permissions.requestPermission(it, de.mm20.launcher2.permissions.PermissionGroup.Accessibility)
                    }
                }) { Text(stringResource(R.string.focus_block_on)) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            val running = f
            PillBar(Modifier.weight(1f)) {
                if (running == null) {
                    for (m in FreeFocusMinutes) {
                        val on = m == minutes
                        Text(
                            "$m min",
                            style = MaterialTheme.typography.labelLarge.merge(SchoolTokens.Tabular),
                            color = if (on) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 2.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(if (on) text else androidx.compose.ui.graphics.Color.Transparent)
                                .clickable(role = androidx.compose.ui.semantics.Role.RadioButton) { minutes = m }
                                .padding(vertical = 12.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                } else {
                    val left = rememberFocusLeft(running)
                    Text(
                        clock(left), style = SchoolFonts.display(24).merge(SchoolTokens.Tabular),
                        color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 14.dp),
                    )
                    Text(
                        running.text, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            if (running == null) {
                InkButton(R.drawable.play_arrow_24px, stringResource(R.string.focus_start, minutes), text) { start(null, freeText, minutes) }
            } else {
                InkButton(R.drawable.stop_24px, stringResource(R.string.focus_cancel), text) { FocusTimer.stop(context, done = false) }
            }
        }
    }
}
