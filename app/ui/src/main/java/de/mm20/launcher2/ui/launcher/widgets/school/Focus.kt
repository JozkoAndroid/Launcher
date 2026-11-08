package de.mm20.launcher2.ui.launcher.widgets.school

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.schoolbrief.BriefRepository
import de.mm20.launcher2.schoolbrief.PlanStep
import de.mm20.launcher2.ui.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** A running focus session on one step of Schoolhelp's day plan. */
data class Focus(val step: Int, val text: String, val minutes: Int, val endsAt: Long)

/**
 * Focus timer (round 13, idea 1): start a plan step's minutes; when they are up, a notification says so
 * and the step is ticked in Schoolhelp (queued on the phone if Schoolhelp is down). Kept in preferences,
 * so it survives the launcher being restarted.
 */
object FocusTimer : KoinComponent {
    private val brief: BriefRepository by inject()
    private const val Prefs = "focus"
    private val state = MutableStateFlow<Focus?>(null)
    private var loaded = false

    fun current(context: Context): StateFlow<Focus?> {
        if (!loaded) {
            loaded = true
            val p = context.getSharedPreferences(Prefs, Context.MODE_PRIVATE)
            val ends = p.getLong("ends", 0L)
            if (ends > 0) state.value = Focus(p.getInt("step", 0), p.getString("text", "").orEmpty(), p.getInt("minutes", 0), ends)
        }
        return state
    }

    fun start(context: Context, s: PlanStep) {
        val minutes = s.minutes ?: return
        val f = Focus(s.index, s.text, minutes, System.currentTimeMillis() + minutes * 60_000L)
        save(context, f)
        alarms(context).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, f.endsAt, alarm(context))
    }

    /** Ends the session; [done] = tick the step in Schoolhelp. */
    fun stop(context: Context, done: Boolean) {
        val f = current(context).value ?: return
        if (done) brief.setStepDone(f.step, true)
        alarms(context).cancel(alarm(context))
        save(context, null)
    }

    internal fun finished(context: Context) {
        val f = current(context).value ?: return
        stop(context, done = true)
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("focus", context.getString(R.string.focus_channel), NotificationManager.IMPORTANCE_HIGH))
        val n = android.app.Notification.Builder(context, "focus")
            .setSmallIcon(R.drawable.timer_24px)
            .setContentTitle(context.getString(R.string.focus_done_title))
            .setContentText(f.text)
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(4711, n) }
    }

    private fun save(context: Context, f: Focus?) {
        state.value = f
        context.getSharedPreferences(Prefs, Context.MODE_PRIVATE).edit().apply {
            if (f == null) clear() else putInt("step", f.step).putString("text", f.text).putInt("minutes", f.minutes).putLong("ends", f.endsAt)
        }.apply()
    }

    private fun alarms(context: Context) = context.getSystemService(AlarmManager::class.java)
    private fun alarm(context: Context) = PendingIntent.getBroadcast(
        context, 4711, Intent(context, FocusAlarmReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

class FocusAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) = FocusTimer.finished(context.applicationContext)
}

/** "18:42", ticking each second while shown; finishes the session if it ran out while away. */
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

/** On "Môj deň": the countdown large, the step under it; done or stop as quiet text buttons. */
@Composable
internal fun FocusPanel(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val f by FocusTimer.current(context).collectAsState()
    val focus = f ?: return
    val left = rememberFocusLeft(focus)
    Column(modifier.widthIn(max = 640.dp).fillMaxWidth().padding(top = 24.dp)) {
        Text(stringResource(R.string.focus_label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(clock(left), style = SchoolFonts.display(64).merge(SchoolTokens.Tabular), color = MaterialTheme.colorScheme.primary)
        Text(focus.text, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { FocusTimer.stop(context, done = true) }) { Text(stringResource(R.string.focus_finish)) }
            TextButton(onClick = { FocusTimer.stop(context, done = false) }) { Text(stringResource(R.string.focus_cancel)) }
        }
    }
}

/** On home, above the dock: a quiet line while a session runs ("● 18:42 · Für Ethik lernen"). */
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
