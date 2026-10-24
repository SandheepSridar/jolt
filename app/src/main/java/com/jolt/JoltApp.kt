package com.jolt

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.jolt.alarm.AlarmScheduler
import com.jolt.data.JoltDatabase
import com.jolt.data.Repository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class JoltApp : Application() {
    /** Process-wide scope for short writes that must outlive a screen (e.g. dismissing an alarm). */
    val appScope = CoroutineScope(SupervisorJob())

    val database by lazy { JoltDatabase.create(this) }
    val scheduler by lazy { AlarmScheduler(this, database.alarms()) }
    val repository by lazy { Repository(this, database, scheduler) }

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(ALARM_CHANNEL, "Ringing alarms", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Shows the alarm screen when an alarm goes off"
                setSound(null, null) // AlarmService plays the sound itself.
                enableVibration(false)
            },
        )
    }

    companion object {
        const val ALARM_CHANNEL = "ringing"
    }
}

val Context.jolt: JoltApp get() = applicationContext as JoltApp
