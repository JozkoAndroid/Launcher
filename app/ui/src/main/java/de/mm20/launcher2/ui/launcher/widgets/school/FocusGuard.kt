package de.mm20.launcher2.ui.launcher.widgets.school

import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import de.mm20.launcher2.ui.R

/**
 * Keeps distracting apps closed during a focus session (round 15). Round 15b used the accessibility
 * service; on the device Play Protect refused it for a sideloaded app. This needs no restricted
 * setting: usage access (already granted for the data and Instagram tiles) says which app is in front,
 * once a second while a session runs; a blocked one gets a full-screen cover ("display over other apps")
 * and is sent home. Runs only during a session, as a foreground service under the countdown notification.
 */
class FocusGuardService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var cover: View? = null
    private var lastCheck = 0L
    private var front: String? = null

    private val tick = object : Runnable {
        override fun run() {
            val f = FocusTimer.current(this@FocusGuardService).value
            if (f == null || f.endsAt <= System.currentTimeMillis()) {
                stopSelf()
                return
            }
            check(f)
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val f = FocusTimer.current(this).value
        val n = f?.let { FocusTimer.liveNotification(this, it) }
        if (n == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= 34) startForeground(FocusTimer.LiveId, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            else startForeground(FocusTimer.LiveId, n)
        }.onFailure { stopSelf(); return START_NOT_STICKY }
        lastCheck = System.currentTimeMillis() - 5000L
        handler.removeCallbacks(tick)
        handler.post(tick)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        hideCover()
        super.onDestroy()
    }

    /** The app in front from usage events since the last check; a blocked one is covered and sent home. */
    private fun check(f: Focus) {
        val usm = getSystemService(UsageStatsManager::class.java) ?: return
        val now = System.currentTimeMillis()
        runCatching {
            val events = usm.queryEvents(lastCheck, now)
            val e = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(e)
                if (e.eventType == UsageEvents.Event.ACTIVITY_RESUMED) front = e.packageName
            }
        }
        lastCheck = now
        val blocked = front?.takeIf { it in BlockedApps.map { b -> b.first } }
        if (blocked != null) {
            showCover(blocked, f)
            goHome()
        } else {
            hideCover()
        }
    }

    private fun goHome() {
        runCatching {
            startActivity(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    private fun showCover(pkg: String, f: Focus) {
        if (!Settings.canDrawOverlays(this)) return
        val name = BlockedApps.firstOrNull { it.first == pkg }?.second ?: pkg
        val minutes = ((f.endsAt - System.currentTimeMillis() + 59_999) / 60_000).toInt()
        val text = getString(R.string.focus_blocked, name, minutes)
        cover?.let { (it.tag as? TextView)?.text = text; return }

        fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()
        val label = TextView(this).apply {
            setText(R.string.focus_label)
            setTextColor(Color.argb(0x99, 0xF4, 0xF4, 0xF6))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        }
        val message = TextView(this).apply {
            this.text = text
            setTextColor(Color.rgb(0xF4, 0xF4, 0xF6))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            setPadding(0, dp(8), 0, dp(28))
        }
        // The ink pill, as in the launcher.
        val home = TextView(this).apply {
            setText(R.string.focus_go_home)
            setTextColor(Color.rgb(0x16, 0x18, 0x1C))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(16), dp(28), dp(16))
            background = GradientDrawable().apply { setColor(Color.rgb(0xF4, 0xF4, 0xF6)); cornerRadius = dp(32).toFloat() }
            setOnClickListener { goHome(); hideCover() }
        }
        val view = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(32), dp(32), dp(32), dp(32))
            setBackgroundColor(Color.rgb(0x15, 0x17, 0x1B))
            addView(label)
            addView(message)
            addView(home, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            tag = message
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.OPAQUE,
        )
        runCatching { getSystemService(WindowManager::class.java).addView(view, params); cover = view }
    }

    private fun hideCover() {
        val v = cover ?: return
        cover = null
        runCatching { getSystemService(WindowManager::class.java).removeView(v) }
    }

    companion object {
        fun start(context: Context) {
            if (!FocusGuard.canBlock(context)) return
            runCatching { context.startForegroundService(Intent(context, FocusGuardService::class.java)) }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, FocusGuardService::class.java)) }
        }
    }
}

/** What blocking needs: usage access (which app is in front) and display over other apps (the cover). */
object FocusGuard {
    fun hasUsageAccess(context: Context) = de.mm20.launcher2.ui.launcher.widgets.school.context.ContextSources.hasUsageAccess(context)
    fun canOverlay(context: Context) = Settings.canDrawOverlays(context)
    fun canBlock(context: Context) = hasUsageAccess(context) && canOverlay(context)

    /** Opens the screen for the first missing permission. */
    fun requestMissing(context: Context) {
        val intent = when {
            !hasUsageAccess(context) -> de.mm20.launcher2.ui.launcher.widgets.school.context.ContextSources.usageAccessIntent()
            !canOverlay(context) -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + context.packageName))
            else -> return
        }
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    fun overlayIntent(context: Context) =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + context.packageName))
}
