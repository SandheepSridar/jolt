package com.jolt.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.jolt.MainActivity
import com.jolt.data.AlarmDao
import java.time.ZoneId

/**
 * Keeps exactly one system alarm registered: the next one to ring.
 *
 * setAlarmClock() is exact, still fires in Doze, and needs no running service, so Jolt
 * runs no code at all between alarms.
 */
class AlarmScheduler(private val context: Context, private val dao: AlarmDao) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun canSchedule(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    suspend fun scheduleNext() {
        val next = AlarmTimes.earliest(dao.getAll(), System.currentTimeMillis(), ZoneId.systemDefault())
        if (next == null) {
            alarmManager.cancel(fireIntent(0))
            return
        }
        val (alarm, triggerAt) = next
        val showIntent = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        try {
            alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, showIntent), fireIntent(alarm.id))
        } catch (e: SecurityException) {
            Log.e(TAG, "Exact alarm permission missing", e)
        }
    }

    private fun fireIntent(alarmId: Long): PendingIntent = PendingIntent.getBroadcast(
        context, 0,
        Intent(context, AlarmReceiver::class.java).putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private companion object {
        const val TAG = "AlarmScheduler"
    }
}
