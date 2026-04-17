package com.scanner.app.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface DeviceDao {

    // ═══════════════════════════════════════════════════════════════
    //  DEVICES — CRUD
    // ═══════════════════════════════════════════════════════════════

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDevice(device: DiscoveredDeviceEntity): Long

    @Update
    suspend fun updateDevice(device: DiscoveredDeviceEntity)

    @Delete
    suspend fun deleteDevice(device: DiscoveredDeviceEntity)

    @Query("DELETE FROM discovered_devices WHERE id = :id")
    suspend fun deleteDeviceById(id: Long)

    /**
     * Upsert: Insert if new, update lastSeen/signal/timesSeen if existing.
     * Returns the device ID (existing or new).
     */
    @Transaction
    suspend fun upsertDevice(
        address: String,
        name: String,
        category: DeviceCategory,
        signalStrength: Int?,
        metadata: String? = null
    ): Long {
        val existing = getDeviceByAddress(address)
        val now = Instant.now()

        return if (existing != null) {
            updateDevice(
                existing.copy(
                    name = if (name.isNotBlank() && !name.startsWith("(")) name else existing.name,
                    lastSeen = now,
                    lastSignalStrength = signalStrength ?: existing.lastSignalStrength,
                    timesSeen = existing.timesSeen + 1,
                    metadata = metadata ?: existing.metadata
                )
            )
            existing.id
        } else {
            insertDevice(
                DiscoveredDeviceEntity(
                    address = address,
                    name = name,
                    deviceCategory = category,
                    firstSeen = now,
                    lastSeen = now,
                    lastSignalStrength = signalStrength,
                    metadata = metadata
                )
            )
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  DEVICES — Queries
    // ═══════════════════════════════════════════════════════════════

    @Query("SELECT * FROM discovered_devices WHERE address = :address LIMIT 1")
    suspend fun getDeviceByAddress(address: String): DiscoveredDeviceEntity?

    @Query("SELECT * FROM discovered_devices WHERE id = :id")
    suspend fun getDeviceById(id: Long): DiscoveredDeviceEntity?

    @Query("SELECT * FROM discovered_devices WHERE id = :id")
    fun observeDeviceById(id: Long): Flow<DiscoveredDeviceEntity?>

    // All devices, newest first
    @Query("SELECT * FROM discovered_devices ORDER BY last_seen DESC")
    fun observeAllDevices(): Flow<List<DiscoveredDeviceEntity>>

    // Filter by category
    @Query("SELECT * FROM discovered_devices WHERE device_category = :category ORDER BY last_seen DESC")
    fun observeDevicesByCategory(category: DeviceCategory): Flow<List<DiscoveredDeviceEntity>>

    // Favorites only
    @Query("SELECT * FROM discovered_devices WHERE is_favorite = 1 ORDER BY last_seen DESC")
    fun observeFavorites(): Flow<List<DiscoveredDeviceEntity>>

    // Search by name, label, or address
    @Query("""
        SELECT * FROM discovered_devices 
        WHERE name LIKE '%' || :query || '%' 
           OR custom_label LIKE '%' || :query || '%'
           OR address LIKE '%' || :query || '%'
           OR notes LIKE '%' || :query || '%'
           OR metadata LIKE '%' || :query || '%'
        ORDER BY last_seen DESC
    """)
    fun searchDevices(query: String): Flow<List<DiscoveredDeviceEntity>>

    // Devices with reading counts (for inventory overview)
    @Query("""
        SELECT d.*, COUNT(r.id) as reading_count 
        FROM discovered_devices d 
        LEFT JOIN signal_readings r ON d.id = r.device_id
        GROUP BY d.id
        ORDER BY d.last_seen DESC
    """)
    fun observeDevicesWithReadingCount(): Flow<List<DeviceWithReadingCount>>

    // Recently seen (last N hours)
    @Query("SELECT * FROM discovered_devices WHERE last_seen > :since ORDER BY last_seen DESC")
    fun observeRecentDevices(since: Instant): Flow<List<DiscoveredDeviceEntity>>

    // Device count per category
    @Query("SELECT device_category, COUNT(*) as count FROM discovered_devices GROUP BY device_category")
    suspend fun getDeviceCountByCategory(): List<CategoryCount>

    // ─── Favorites / Labels ─────────────────────────────────────

    @Query("UPDATE discovered_devices SET is_favorite = :isFavorite WHERE id = :id")
    suspend fun setFavorite(id: Long, isFavorite: Boolean)

    @Query("UPDATE discovered_devices SET custom_label = :label WHERE id = :id")
    suspend fun setCustomLabel(id: Long, label: String?)

    @Query("UPDATE discovered_devices SET notes = :notes WHERE id = :id")
    suspend fun setNotes(id: Long, notes: String?)

    // ═══════════════════════════════════════════════════════════════
    //  SCAN SESSIONS
    // ═══════════════════════════════════════════════════════════════

    @Insert
    suspend fun insertScanSession(session: ScanSessionEntity): Long

    @Query("SELECT * FROM scan_sessions ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentSessions(limit: Int = 50): List<ScanSessionEntity>

    @Query("SELECT * FROM scan_sessions ORDER BY timestamp DESC")
    fun observeAllSessions(): Flow<List<ScanSessionEntity>>

    @Query("SELECT COUNT(*) FROM scan_sessions")
    suspend fun getTotalScanCount(): Int

    // ═══════════════════════════════════════════════════════════════
    //  SIGNAL READINGS
    // ═══════════════════════════════════════════════════════════════

    @Insert
    suspend fun insertSignalReading(reading: SignalReadingEntity)

    @Insert
    suspend fun insertSignalReadings(readings: List<SignalReadingEntity>)

    // Signal history for a specific device (for graphs)
    @Query("""
        SELECT signal_strength, timestamp 
        FROM signal_readings 
        WHERE device_id = :deviceId 
        ORDER BY timestamp DESC 
        LIMIT :limit
    """)
    fun observeSignalHistory(deviceId: Long, limit: Int = 360): Flow<List<SignalOverTime>>

    // Signal history within time range
    @Query("""
        SELECT signal_strength, timestamp 
        FROM signal_readings 
        WHERE device_id = :deviceId AND timestamp BETWEEN :from AND :to
        ORDER BY timestamp ASC
    """)
    fun getSignalHistoryRange(deviceId: Long, from: Instant, to: Instant): Flow<List<SignalOverTime>>

    // Cleanup old readings (keep last N days)
    @Query("DELETE FROM signal_readings WHERE timestamp < :before")
    suspend fun deleteOldReadings(before: Instant)

    // ═══════════════════════════════════════════════════════════════
    //  STATISTICS
    // ═══════════════════════════════════════════════════════════════

    @Query("SELECT COUNT(*) FROM discovered_devices")
    fun observeTotalDeviceCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM discovered_devices WHERE device_category = 'WIFI'")
    fun observeWifiCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM discovered_devices WHERE device_category IN ('BT_CLASSIC', 'BT_BLE', 'BT_DUAL')")
    fun observeBluetoothCount(): Flow<Int>

    @Query("""
        SELECT AVG(last_signal_strength) 
        FROM discovered_devices 
        WHERE last_signal_strength IS NOT NULL AND device_category = :category
    """)
    suspend fun getAverageSignal(category: DeviceCategory): Double?
}

data class CategoryCount(
    @ColumnInfo(name = "device_category") val category: DeviceCategory,
    @ColumnInfo(name = "count") val count: Int
)
