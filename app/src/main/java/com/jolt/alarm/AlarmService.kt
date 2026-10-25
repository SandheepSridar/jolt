package com.jolt.alarm

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.jolt.JoltApp
import com.jolt.R
import com.jolt.data.Alarm
import com.jolt.jolt
import com.jolt.ui.RingActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Runs only while an alarm is ringing: from the moment it fires until it is snoozed or the
 * mission is completed. Then it stops itself and Jolt goes back to using no resources.
 */
class AlarmService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var savedAlarmVolume: Int? = null
    private var lockedVolume: Int? = null
    private var volumeGuardRegistered = false
    private var volume = START_VOLUME
    private var targetVolume = 1f

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getLongExtra(EXTRA_ID, -1) ?: -1
        when (intent?.action) {
            ACTION_FIRE -> fire(id)
            ACTION_DUCK -> duck()
            ACTION_SNOOZE -> finish { jolt.repository.snooze(id) }
            ACTION_DISMISS -> finish { jolt.repository.dismiss(id) }
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun fire(id: Long) {
        // Must be foreground within a few seconds of startForegroundService(), before any DB work.
        goForeground(notification(_ringing.value?.alarm))
        scope.launch {
            val alarm = jolt.repository.markFired(id)
            when {
                _ringing.value != null -> Unit // Already ringing for another alarm; that one wins.
                alarm == null -> stopEverything()
                else -> startRinging(alarm)
            }
        }
    }

    private fun startRinging(alarm: Alarm) {
        _ringing.value = Ringing(alarm)
        goForeground(notification(alarm))
        startSound(alarm)
        if (alarm.vibrate) startVibration()
        handler.post(rampVolume)
    }

    private fun goForeground(notification: Notification) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED
        } else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    private fun notification(alarm: Alarm?): Notification {
        val ringIntent = PendingIntent.getActivity(
            this, 0, RingActivity.intent(this),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val title = alarm?.let { "%d:%02d".format(it.hour, it.minute) } ?: getString(R.string.app_name)
        return NotificationCompat.Builder(this, JoltApp.ALARM_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(alarm?.label?.ifBlank { null } ?: getString(R.string.ringing_text))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(ringIntent)
            // Must be on the very first post: an update to an already-shown notification never
            // launches its full-screen intent. RingActivity waits briefly for the alarm details.
            .setFullScreenIntent(ringIntent, true)
            .build()
    }

    private fun startSound(alarm: Alarm) {
        val audio = getSystemService(AudioManager::class.java)
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        val current = audio.getStreamVolume(AudioManager.STREAM_ALARM)
        val floor = (max * 0.7f).toInt()
        if (current < floor) {
            // A muted alarm stream would make the alarm useless; restored when we stop.
            savedAlarmVolume = current
            audio.setStreamVolume(AudioManager.STREAM_ALARM, floor, 0)
        }
        lockVolume(maxOf(current, floor))

        val candidates = listOfNotNull(
            pickSound(alarm)?.toUri(),
            RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM),
            Settings.System.DEFAULT_ALARM_ALERT_URI,
            Settings.System.DEFAULT_RINGTONE_URI,
        )
        player = candidates.firstNotNullOfOrNull(::createPlayer)
    }

    /**
     * Turning the volume down is an easy way to ignore the alarm, so while it rings any drop
     * below [level] is undone straight away. The listener exists only while ringing.
     */
    private fun lockVolume(level: Int) {
        lockedVolume = level
        ContextCompat.registerReceiver(
            this, volumeGuard, IntentFilter(VOLUME_CHANGED_ACTION), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        volumeGuardRegistered = true
    }

    private val volumeGuard = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val level = lockedVolume ?: return
            val audio = getSystemService(AudioManager::class.java)
            if (audio.getStreamVolume(AudioManager.STREAM_ALARM) < level) {
                audio.setStreamVolume(AudioManager.STREAM_ALARM, level, 0)
            }
        }
    }

    /** The alarm's sound, or a random one of its picks, never the same one twice in a row. */
    private fun pickSound(alarm: Alarm): String? {
        val sounds = alarm.randomSoundList
        if (sounds.isEmpty()) return alarm.soundUri
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val key = "last_sound_${alarm.id}"
        val picked = pickRandomSound(sounds, prefs.getString(key, null)) ?: return alarm.soundUri
        prefs.edit().putString(key, picked).apply()
        Log.i(TAG, "Random sound: ${runCatching { RingtoneManager.getRingtone(this, picked.toUri())?.getTitle(this) }.getOrNull() ?: picked}")
        return picked
    }

    private fun createPlayer(uri: Uri): MediaPlayer? = try {
        MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            setWakeMode(this@AlarmService, PowerManager.PARTIAL_WAKE_LOCK)
            setDataSource(this@AlarmService, uri)
            isLooping = true
            setVolume(volume, volume)
            prepare()
            start()
        }
    } catch (e: Exception) {
        Log.w(TAG, "Can't play $uri", e)
        null
    }

    private fun startVibration() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }
        val effect = VibrationEffect.createWaveform(longArrayOf(0, 600, 400, 600, 1400), 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator?.vibrate(effect, android.os.VibrationAttributes.createForUsage(android.os.VibrationAttributes.USAGE_ALARM))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
        }
    }

    /** Gently raises the volume to [targetVolume], or lowers it straight away. */
    private val rampVolume = object : Runnable {
        override fun run() {
            volume = if (volume < targetVolume) minOf(targetVolume, volume + RAMP_STEP) else targetVolume
            player?.setVolume(volume, volume)
            if (volume != targetVolume) handler.postDelayed(this, 1000)
        }
    }

    private val restoreVolume = Runnable {
        targetVolume = 1f
        handler.removeCallbacks(rampVolume)
        handler.post(rampVolume)
    }

    /** Quieter while the user is scanning; back to full volume if nothing matches for a minute. */
    private fun duck() {
        if (_ringing.value == null) return
        targetVolume = DUCKED_VOLUME
        handler.removeCallbacks(rampVolume)
        handler.post(rampVolume)
        handler.removeCallbacks(restoreVolume)
        handler.postDelayed(restoreVolume, DUCK_TIMEOUT_MS)
    }

    private fun finish(update: suspend () -> Unit) {
        stopEverything()
        jolt.appScope.launch { update() }
    }

    private fun stopEverything() {
        handler.removeCallbacksAndMessages(null)
        lockedVolume = null
        if (volumeGuardRegistered) {
            unregisterReceiver(volumeGuard)
            volumeGuardRegistered = false
        }
        player?.run { stop(); release() }
        player = null
        vibrator?.cancel()
        vibrator = null
        savedAlarmVolume?.let {
            getSystemService(AudioManager::class.java).setStreamVolume(AudioManager.STREAM_ALARM, it, 0)
        }
        savedAlarmVolume = null
        _ringing.value = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (_ringing.value != null || player != null) stopEverything()
        scope.cancel()
        super.onDestroy()
    }

    data class Ringing(val alarm: Alarm)

    companion object {
        private const val TAG = "AlarmService"
        private const val PREFS = "alarm_service"
        private const val NOTIFICATION_ID = 1
        private const val START_VOLUME = 0.15f
        private const val RAMP_STEP = 0.03f // Full volume after about 30 s.
        private const val DUCKED_VOLUME = 0.3f
        private const val DUCK_TIMEOUT_MS = 60_000L
        /** Not in the public SDK, but sent by the system on every stream volume change. */
        private const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"

        private const val EXTRA_ID = "alarm_id"
        private const val ACTION_FIRE = "com.jolt.FIRE"
        private const val ACTION_DUCK = "com.jolt.DUCK"
        private const val ACTION_SNOOZE = "com.jolt.SNOOZE"
        private const val ACTION_DISMISS = "com.jolt.DISMISS"

        private val _ringing = MutableStateFlow<Ringing?>(null)

        /** The alarm currently ringing, observed by RingActivity. */
        val ringing: StateFlow<Ringing?> = _ringing.asStateFlow()

        fun fireIntent(context: Context, alarmId: Long) = intent(context, ACTION_FIRE, alarmId)

        fun duck(context: Context) = context.startService(intent(context, ACTION_DUCK, -1))
        fun snooze(context: Context, alarmId: Long) = context.startService(intent(context, ACTION_SNOOZE, alarmId))
        fun dismiss(context: Context, alarmId: Long) = context.startService(intent(context, ACTION_DISMISS, alarmId))

        private fun intent(context: Context, action: String, alarmId: Long) =
            Intent(context, AlarmService::class.java).setAction(action).putExtra(EXTRA_ID, alarmId)
    }
}
