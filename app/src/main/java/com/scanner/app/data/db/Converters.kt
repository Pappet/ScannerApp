package com.scanner.app.data.db

import androidx.room.TypeConverter
import java.time.Instant

/**
 * Room TypeConverters for mapping complex data types to SQLite-compatible primitives.
 */
class Converters {

    /** Converts [Instant] to Epoch Milliseconds for database storage. */
    @TypeConverter
    fun fromInstant(value: Instant?): Long? = value?.toEpochMilli()

    /** Converts Epoch Milliseconds back to [Instant]. */
    @TypeConverter
    fun toInstant(value: Long?): Instant? = value?.let { Instant.ofEpochMilli(it) }

    /** Converts [DeviceCategory] to String. */
    @TypeConverter
    fun fromDeviceCategory(value: DeviceCategory): String = value.name

    /** Converts String back to [DeviceCategory]. */
    @TypeConverter
    fun toDeviceCategory(value: String): DeviceCategory =
        try {
            DeviceCategory.valueOf(value)
        } catch (e: Exception) {
            // Handle legacy categories from pre-Spectrum versions
            when (value) {
                "CLASSIC" -> DeviceCategory.BT_CLASSIC
                "BLE" -> DeviceCategory.BT_BLE
                "DUAL" -> DeviceCategory.BT_DUAL
                else -> DeviceCategory.WIFI
            }
        }

    /** Converts [ScanType] to String. */
    @TypeConverter
    fun fromScanType(value: ScanType): String = value.name

    /** Converts String back to [ScanType]. */
    @TypeConverter
    fun toScanType(value: String): ScanType =
        ScanType.valueOf(value)
}
