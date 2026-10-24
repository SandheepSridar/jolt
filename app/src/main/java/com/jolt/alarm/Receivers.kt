package com.jolt.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.jolt.jolt
import kotlinx.coroutines.launch

/** Fired by AlarmManager at alarm time. Hands straight off to the ringing service. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_ALARM_ID, -1)
        ContextCompat.startForegroundService(context, AlarmService.fireIntent(context, id))
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
