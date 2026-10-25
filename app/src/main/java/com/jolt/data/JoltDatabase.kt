package com.jolt.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Alarm::class, MissionObject::class, ReferenceShot::class],
    version = 2,
    exportSchema = false,
)
abstract class JoltDatabase : RoomDatabase() {
    abstract fun alarms(): AlarmDao
    abstract fun objects(): ObjectDao

    companion object {
        fun create(context: Context): JoltDatabase =
            Room.databaseBuilder(context, JoltDatabase::class.java, "jolt.db")
                .addMigrations(MIGRATION_1_2)
                .build()

        /** v1.1: generic category missions and random alarm sounds. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE alarms ADD COLUMN category TEXT")
                db.execSQL("ALTER TABLE alarms ADD COLUMN randomSounds TEXT")
            }
        }
    }
}
