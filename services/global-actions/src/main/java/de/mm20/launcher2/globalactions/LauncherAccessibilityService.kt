package de.mm20.launcher2.globalactions

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import de.mm20.launcher2.permissions.PermissionsManager
import org.koin.android.ext.android.inject
import java.lang.ref.WeakReference

class LauncherAccessibilityService: AccessibilityService() {

    private val permissionManager: PermissionsManager by inject()

    /**
     * Fork (round 15): during a focus session with blocking on, distracting apps are sent back home.
     * The session lives in the "focus" preferences (written by the launcher's focus timer).
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg !in FocusBlocked) return
        val p = getSharedPreferences("focus", MODE_PRIVATE)
        val ends = p.getLong("ends", 0L)
        val left = ends - System.currentTimeMillis()
        if (!p.getBoolean("block", false) || left <= 0) return
        performGlobalAction(GLOBAL_ACTION_HOME)
        val min = (left / 60_000L + 1).toInt()
        android.widget.Toast.makeText(
            this, getString(de.mm20.launcher2.i18n.R.string.focus_blocked, min), android.widget.Toast.LENGTH_SHORT,
        ).show()
    }

    override fun onInterrupt() {

    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = WeakReference(this)
        permissionManager.reportAccessibilityServiceState(true)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        permissionManager.reportAccessibilityServiceState(false)
        instance = null
        return super.onUnbind(intent)
    }

    companion object {
        /** Instagram, YouTube, Threads. */
        val FocusBlocked = setOf("com.instagram.android", "com.google.android.youtube", "com.instagram.barcelona")

        private var instance: WeakReference<LauncherAccessibilityService>? = null
        internal fun getInstance(): LauncherAccessibilityService? {
            return instance?.get()
        }
    }
}