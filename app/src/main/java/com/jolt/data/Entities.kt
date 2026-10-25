package com.jolt.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.nio.ByteBuffer
import java.nio.ByteOrder

@Entity(
    tableName = "alarms",
    foreignKeys = [
        ForeignKey(
            entity = MissionObject::class,
            parentColumns = ["id"],
            childColumns = ["objectId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("objectId")],
)
data class Alarm(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hour: Int,
    val minute: Int,
    /** Bit 0 = Monday … bit 6 = Sunday. 0 means a one-off alarm. */
    val days: Int = 0,
    val enabled: Boolean = true,
    val label: String = "",
    /** Null means the system default alarm sound. */
    val soundUri: String? = null,
    val vibrate: Boolean = true,
    val snoozeMinutes: Int = 5,
    val maxSnoozes: Int = 1,
    /** Your own photographed object to scan to dismiss. At most one of this and [category]. */
    val objectId: Long? = null,
    val snoozedUntil: Long? = null,
    val snoozeCount: Int = 0,
    /** A generic mission ([com.jolt.mission.Category] name), e.g. any sink. */
    val category: String? = null,
    /** Up to 5 alarm sound URIs, newline-separated; one is picked at random each time. Wins over [soundUri]. */
    val randomSounds: String? = null,
) {
    val repeats: Boolean get() = days != 0
    val hasMission: Boolean get() = objectId != null || category != null
    val randomSoundList: List<String> get() = randomSounds?.lines()?.filter { it.isNotBlank() }.orEmpty()
}

@Entity(tableName = "objects")
data class MissionObject(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Minimum cosine similarity a frame needs to count as a match. */
    val threshold: Float = Strictness.Normal.threshold,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "reference_shots",
    foreignKeys = [
        ForeignKey(
            entity = MissionObject::class,
            parentColumns = ["id"],
            childColumns = ["objectId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("objectId")],
)
data class ReferenceShot(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val objectId: Long,
    val photoPath: String,
    val embedding: ByteArray,
) {
    fun vector(): FloatArray = embedding.toFloatArray()
}

enum class Strictness(val label: String, val threshold: Float) {
    Relaxed("Relaxed", 0.70f),
    Normal("Normal", 0.78f),
    Strict("Strict", 0.86f);

    companion object {
        fun closestTo(threshold: Float) = entries.minBy { kotlin.math.abs(it.threshold - threshold) }
    }
}

fun FloatArray.toByteArray(): ByteArray {
    val buffer = ByteBuffer.allocate(size * 4).order(ByteOrder.LITTLE_ENDIAN)
    buffer.asFloatBuffer().put(this)
    return buffer.array()
}

fun ByteArray.toFloatArray(): FloatArray {
    val floats = ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
    return FloatArray(floats.remaining()).also { floats.get(it) }
}
