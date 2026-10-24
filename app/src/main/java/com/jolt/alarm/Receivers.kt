package com.jolt.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.jolt.jolt
import com.jolt.ui.RingActivity
import kotlinx.coroutines.launch

/** Fired by AlarmManager at alarm time. Starts the ringing service and opens the ring screen. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_ALARM_ID, -1)
        ContextCompat.startForegroundService(context, AlarmService.fireIntent(context, id))
        // The full-screen notification only takes over the screen when the phone is locked or
        // idle; while it's in use, Android shows a small pop-up instead. An alarm-clock broadcast
        // is allowed to start an activity, so open the ring screen directly as well.
        try {
            context.startActivity(RingActivity.intent(context))
        } catch (e: Exception) {
            Log.w("AlarmReceiver", "Couldn't open the ring screen; relying on the notification", e)
        }
    }

    companion object {
        const val EXTRA_ALARM_ID = "alarm_id"
    }
}

/** Re-registers the next alarm after a reboot, clock/time zone change or app update, then exits. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        context.jolt.appScope.launch {
            try {
                context.jolt.scheduler.scheduleNext()
            } finally {
                pending.finish()
            }
        }
    }
}
