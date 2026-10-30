package com.jolt.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Alarm::class, MissionObject::class, ReferenceShot::class],
    version = 3,
    exportSchema = false,
)
abstract class JoltDatabase : RoomDatabase() {
    abstract fun alarms(): AlarmDao
    abstract fun objects(): ObjectDao

    companion object {
        fun create(context: Context): JoltDatabase =
            Room.databaseBuilder(context, JoltDatabase::class.java, "jolt.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()

        /** v1.1: generic category missions and random alarm sounds. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE alarms ADD COLUMN category TEXT")
                db.execSQL("ALTER TABLE alarms ADD COLUMN randomSounds TEXT")
            }
        }

        /** v1.3: one-off alarms that delete themselves once dismissed. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE alarms ADD COLUMN deleteAfterDismiss INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
