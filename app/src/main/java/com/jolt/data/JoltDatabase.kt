package com.jolt.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Alarm::class, MissionObject::class, ReferenceShot::class],
    version = 1,
    exportSchema = false,
)
abstract class JoltDatabase : RoomDatabase() {
    abstract fun alarms(): AlarmDao
    abstract fun objects(): ObjectDao

    companion object {
        fun create(context: Context): JoltDatabase =
            Room.databaseBuilder(context, JoltDatabase::class.java, "jolt.db").build()
    }
}
