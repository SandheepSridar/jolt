package com.jolt.alarm

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import com.jolt.ui.RingActivity

/**
 * Force stop can't be blocked, only kept out of reach. While an alarm rings, this sends the user
 * straight back to the alarm whenever Settings, Device care or the uninstall dialog opens, and
 * closes the power menu. When nothing is ringing it ignores every event.
 */
class RingGuard : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (AlarmService.ringing.value == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        val cls = event.className?.toString().orEmpty()

        when {
            pkg in BLOCKED_PACKAGES -> {
                Log.i(TAG, "Blocked $pkg/$cls while ringing")
                performGlobalAction(GLOBAL_ACTION_HOME)
                startActivity(RingActivity.intent(this))
            }
            pkg == SYSTEM_UI && cls.contains("globalactions", ignoreCase = true) -> {
                Log.i(TAG, "Closed power menu while ringing")
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
        }
    }

    override fun onInterrupt() = Unit

    companion object {
        private const val TAG = "RingGuard"
        private const val SYSTEM_UI = "com.android.systemui"

        /** Everywhere Jolt can be force stopped or uninstalled from. */
        private val BLOCKED_PACKAGES = setOf(
            "com.android.settings",
            "com.samsung.android.lool", // Device care
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
        )

        fun isEnabled(context: Context): Boolean {
            val self = ComponentName(context, RingGuard::class.java)
            return context.getSystemService(AccessibilityManager::class.java)
                .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .any { it.resolveInfo.serviceInfo.let { s -> ComponentName(s.packageName, s.name) } == self }
        }
    }
}
