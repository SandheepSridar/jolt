package com.jolt.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmDao {
    @Query("SELECT * FROM alarms ORDER BY hour, minute")
    fun observeAll(): Flow<List<Alarm>>

    @Query("SELECT * FROM alarms")
    suspend fun getAll(): List<Alarm>

    @Query("SELECT * FROM alarms WHERE id = :id")
    suspend fun get(id: Long): Alarm?

    @Insert
    suspend fun insert(alarm: Alarm): Long

    @Update
    suspend fun update(alarm: Alarm)

    @Delete
    suspend fun delete(alarm: Alarm)
}

@Dao
interface ObjectDao {
    @Query("SELECT * FROM objects ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<MissionObject>>

    @Query("SELECT * FROM objects WHERE id = :id")
    suspend fun get(id: Long): MissionObject?

    @Insert
    suspend fun insert(obj: MissionObject): Long

    @Update
    suspend fun update(obj: MissionObject)

    @Delete
    suspend fun delete(obj: MissionObject)

    @Insert
    suspend fun insertShots(shots: List<ReferenceShot>)

    @Query("SELECT * FROM reference_shots WHERE objectId = :objectId")
    suspend fun shotsFor(objectId: Long): List<ReferenceShot>

    @Query("SELECT * FROM reference_shots")
    fun observeShots(): Flow<List<ReferenceShot>>
}
