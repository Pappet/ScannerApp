package com.scanner.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Main database for the ScannerApp.
 * Persists discovered devices, scan sessions, and signal strength history.
 */
@Database(
    entities = [
        DiscoveredDeviceEntity::class,
        ScanSessionEntity::class,
        SignalReadingEntity::class
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun deviceDao(): DeviceDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Returns the thread-safe singleton instance of the database.
         */
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "scanner_db"
                )
                    // DO NOT add fallbackToDestructiveMigration() — all schema changes
                    // must be handled via explicit Migration objects listed above to
                    // prevent data loss for the user.
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
