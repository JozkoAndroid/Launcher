package de.mm20.launcher2.ui.launcher.widgets.clock.parts

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.AlarmClock
import android.text.format.DateUtils
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.ui.R
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.*

class AlarmPartProvider : PartProvider {

    private val nextAlarmTime = mutableStateOf<Long?>(null)

    private val time = MutableStateFlow(System.currentTimeMillis())

    override fun setTime(time: Long) {
        this.time.value = time
    }

    override fun getRanking(context: Context): Flow<Int> = channelFlow {
        val nextAlarm = getNextAlarmTime(context)
        nextAlarm.collectLatest { alarm ->
            nextAlarmTime.value = alarm
            if (alarm == null) {
                send(0)
            } else {
                time.collectLatest {
                    if (alarm > it + 8 * 60 * 60 * 1000) {
                        send(0)
                    } else {
                        send(60)
                    }
                }
            }
        }
    }

    private fun getNextAlarmTime(context: Context): Flow<Long?> = callbackFlow {
        val alarmManager: AlarmManager = context.getSystemService() ?: return@callbackFlow
        trySendBlocking(alarmManager.nextAlarmClock?.triggerTime)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                trySendBlocking(alarmManager.nextAlarmClock?.triggerTime)
            }
        }
        context.registerReceiver(receiver, IntentFilter().apply {
            addAction(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED)
        })
        awaitClose {
            context.unregisterReceiver(receiver)
        }
    }

    @Composable
    override fun Component(compactLayout: Boolean) {
        val context = LocalContext.current

        val alarmTime by nextAlarmTime
        val time by this.time.collectAsState(System.currentTimeMillis())

        alarmTime?.let {

            if (!compactLayout) {

                TextButton(
                    onClick = {
                        context.tryStartActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS))
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = LocalContentColor.current
                    )
                ) {
                    Icon(
                        painterResource(R.drawable.alarm_24px),
                        contentDescription = null
                    )
                    Text(
                        modifier = Modifier.padding(start = 12.dp),
                        // Fork: the alarm's actual time ("6:45", or "pi 6:45" when not today);
                        // the relative "in 1 hour" did not refresh correctly on the device.
                        text = alarmLabel(context, it, time),
                    )
                }
            } else {
                TextButton(
                    onClick = {
                        context.tryStartActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS))
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = LocalContentColor.current
                    )
                ) {
                    Icon(
                        modifier = Modifier.padding(end = 8.dp).size(32.dp),
                        painter = painterResource(R.drawable.alarm_24px),
                        contentDescription = null
                    )
                    Text(
                        modifier = Modifier.padding(start = 12.dp),
                        // Fork: the alarm's actual time ("6:45", or "pi 6:45" when not today);
                        // the relative "in 1 hour" did not refresh correctly on the device.
                        text = alarmLabel(context, it, time),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}
/** Fork: absolute alarm time in the app's locale, with the weekday when the alarm is not today. */
internal fun alarmLabel(context: Context, alarmMs: Long, nowMs: Long): String {
    val zone = java.time.ZoneId.systemDefault()
    val alarm = java.time.Instant.ofEpochMilli(alarmMs).atZone(zone)
    val today = java.time.Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
    val t = android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(alarmMs))
    if (alarm.toLocalDate() == today) return t
    val locale = context.resources.configuration.locales[0]
    val day = alarm.format(java.time.format.DateTimeFormatter.ofPattern("EEE", locale))
    return "$day $t"
}
