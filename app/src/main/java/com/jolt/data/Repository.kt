package com.jolt.data

import android.content.Context
import android.graphics.Bitmap
import com.jolt.alarm.AlarmScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Every write that can change the next alarm goes through here so the schedule stays in sync. */
class Repository(
    private val context: Context,
    private val db: JoltDatabase,
    private val scheduler: AlarmScheduler,
) {
    val alarms = db.alarms().observeAll()
    val objects = db.objects().observeAll()
    val shots = db.objects().observeShots()

    suspend fun alarm(id: Long) = db.alarms().get(id)
    suspend fun obj(id: Long) = db.objects().get(id)
    suspend fun shotsFor(objectId: Long) = db.objects().shotsFor(objectId)

    suspend fun saveAlarm(alarm: Alarm): Long {
        // Editing an alarm cancels any pending snooze for it.
        val clean = alarm.copy(snoozedUntil = null, snoozeCount = 0)
        val id = if (alarm.id == 0L) db.alarms().insert(clean) else clean.id.also { db.alarms().update(clean) }
        scheduler.scheduleNext()
        return id
    }

    suspend fun setEnabled(alarm: Alarm, enabled: Boolean) = saveAlarm(alarm.copy(enabled = enabled))

    suspend fun deleteAlarm(alarm: Alarm) {
        db.alarms().delete(alarm)
        scheduler.scheduleNext()
    }

    /** Called when an alarm starts ringing. One-off alarms switch off so they don't come back tomorrow. */
    suspend fun markFired(id: Long): Alarm? {
        val alarm = db.alarms().get(id) ?: return null
        val updated = alarm.copy(enabled = alarm.enabled && alarm.repeats, snoozedUntil = null)
        db.alarms().update(updated)
        scheduler.scheduleNext()
        return updated
    }

    suspend fun snooze(id: Long) {
        val alarm = db.alarms().get(id) ?: return
        val until = System.currentTimeMillis() + alarm.snoozeMinutes * 60_000L
        db.alarms().update(alarm.copy(snoozedUntil = until, snoozeCount = alarm.snoozeCount + 1))
        scheduler.scheduleNext()
    }

    suspend fun dismiss(id: Long) {
        db.alarms().get(id)?.let {
            if (it.deleteAfterDismiss && !it.repeats) db.alarms().delete(it)
            else db.alarms().update(it.copy(snoozedUntil = null, snoozeCount = 0))
        }
        scheduler.scheduleNext()
    }

    suspend fun createObject(name: String, strictness: Strictness, shots: List<Pair<Bitmap, FloatArray>>): Long =
        withContext(Dispatchers.IO) {
            val dir = File(context.filesDir, "objects").apply { mkdirs() }
            val id = db.objects().insert(MissionObject(name = name, threshold = strictness.threshold))
            db.objects().insertShots(
                shots.map { (bitmap, vector) ->
                    val file = File(dir, "${UUID.randomUUID()}.jpg")
                    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
                    ReferenceShot(objectId = id, photoPath = file.absolutePath, embedding = vector.toByteArray())
                },
            )
            id
        }

    suspend fun setStrictness(obj: MissionObject, strictness: Strictness) =
        db.objects().update(obj.copy(threshold = strictness.threshold))

    suspend fun deleteObject(obj: MissionObject) = withContext(Dispatchers.IO) {
        val files = db.objects().shotsFor(obj.id).map { File(it.photoPath) }
        db.objects().delete(obj) // Alarms using it fall back to no mission via SET_NULL.
        files.forEach { it.delete() }
    }
}
