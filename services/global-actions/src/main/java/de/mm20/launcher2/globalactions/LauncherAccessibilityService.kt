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
     * Fork (round 15): during a focus session (written by the school launcher's FocusTimer into the "focus"
     * preferences: "ends", "block"), a blocked app that comes to the front is sent home with a short note.
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        val prefs = getSharedPreferences("focus", MODE_PRIVATE)
        val left = prefs.getLong("ends", 0L) - System.currentTimeMillis()
        if (left <= 0) return
        if (pkg !in prefs.getStringSet("block", emptySet()).orEmpty()) return
        performGlobalAction(GLOBAL_ACTION_HOME)
        val now = System.currentTimeMillis()
        if (now - lastNote < 2000) return
        lastNote = now
        val name = runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)) }.getOrDefault(pkg)
        val minutes = ((left + 59_999) / 60_000).toInt()
        android.widget.Toast.makeText(this, getString(de.mm20.launcher2.i18n.R.string.focus_blocked, name, minutes), android.widget.Toast.LENGTH_SHORT).show()
    }

    private var lastNote = 0L

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
        private var instance: WeakReference<LauncherAccessibilityService>? = null
        internal fun getInstance(): LauncherAccessibilityService? {
            return instance?.get()
        }
    }
}