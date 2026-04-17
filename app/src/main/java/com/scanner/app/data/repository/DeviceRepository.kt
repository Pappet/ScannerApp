package com.scanner.app.data.repository

import android.content.Context
import com.scanner.app.data.WifiNetwork
import com.scanner.app.data.BluetoothDevice
import com.scanner.app.data.DeviceType
import com.scanner.app.data.db.*
import com.scanner.app.util.MacVendorLookup
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject
import java.time.Instant
import java.time.temporal.ChronoUnit

class DeviceRepository(context: Context) {

    private val dao = AppDatabase.getInstance(context).deviceDao()

    // ═══════════════════════════════════════════════════════════════
    //  Persist scan results
    // ═══════════════════════════════════════════════════════════════

    /**
     * Persist WiFi scan results: upsert devices, create session, record signal readings.
     */
    suspend fun persistWifiScan(
        networks: List<WifiNetwork>,
        durationMs: Long? = null,
        location: android.location.Location? = null
    ) {
        val sessionId = dao.insertScanSession(
            ScanSessionEntity(
                scanType = ScanType.WIFI,
                timestamp = Instant.now(),
                deviceCount = networks.size,
                durationMs = durationMs
            )
        )

        val readings = mutableListOf<SignalReadingEntity>()
        val now = Instant.now()

        for (network in networks) {
            val existing = dao.getDeviceByAddress(network.bssid)
            val metaJson = try {
                existing?.metadata?.let { JSONObject(it) } ?: JSONObject()
            } catch (_: Exception) { JSONObject() }

            metaJson.apply {
                put("frequency", network.frequency)
                put("channel", network.channel)
                put("band", network.band)
                put("security", network.securityType)
                put("isConnected", network.isConnected)
                put("wpsEnabled", network.wpsEnabled)
                put("rawCapabilities", network.rawCapabilities)
                network.vendor?.let { put("vendor", it) }
                network.wifiStandard?.let { put("wifiStandard", it) }
                network.channelWidth?.let { put("channelWidth", it) }
                network.distance?.let { put("distance", it) }
                location?.let {
                    put("latitude", it.latitude)
                    put("longitude", it.longitude)
                    if (it.hasAltitude()) put("altitude", it.altitude)
                    if (it.hasAccuracy()) put("accuracy", it.accuracy.toDouble())
                }
            }

            val deviceId = dao.upsertDevice(
                address = network.bssid,
                name = network.ssid,
                category = DeviceCategory.WIFI,
                signalStrength = network.signalStrength,
                metadata = metaJson.toString()
            )

            readings.add(
                SignalReadingEntity(
                    deviceId = deviceId,
                    signalStrength = network.signalStrength,
                    timestamp = now,
                    scanSessionId = sessionId
                )
            )
        }

        if (readings.isNotEmpty()) {
            dao.insertSignalReadings(readings)
        }
    }

    /**
     * Persist Bluetooth scan results.
     */
    suspend fun persistBluetoothScan(
        devices: List<BluetoothDevice>,
        durationMs: Long? = null
    ) {
        val sessionId = dao.insertScanSession(
            ScanSessionEntity(
                scanType = ScanType.BLUETOOTH,
                timestamp = Instant.now(),
                deviceCount = devices.size,
                durationMs = durationMs
            )
        )

        val readings = mutableListOf<SignalReadingEntity>()
        val now = Instant.now()

        for (device in devices) {
            val category = when (device.type) {
                DeviceType.CLASSIC -> DeviceCategory.BT_CLASSIC
                DeviceType.BLE -> DeviceCategory.BT_BLE
                DeviceType.DUAL -> DeviceCategory.BT_DUAL
                DeviceType.UNKNOWN -> DeviceCategory.BT_BLE
            }

            val existing = dao.getDeviceByAddress(device.address)
            val metaJson = try {
                existing?.metadata?.let { JSONObject(it) } ?: JSONObject()
            } catch (_: Exception) { JSONObject() }

            // Preserve existing gattData if present (avoid overwriting GATT scan results)
            val existingGattData = metaJson.opt("gattData")

            metaJson.apply {
                put("deviceClass", device.deviceClass ?: "")
                put("bondState", device.bondState.name)
                put("isConnected", device.isConnected)
                // Re-inject gattData so it survives BT scan upserts
                if (existingGattData != null) {
                    put("gattData", existingGattData)
                }
            }

            val deviceId = dao.upsertDevice(
                address = device.address,
                name = device.name,
                category = category,
                signalStrength = device.rssi,
                metadata = metaJson.toString()
            )

            device.rssi?.let { rssi ->
                readings.add(
                    SignalReadingEntity(
                        deviceId = deviceId,
                        signalStrength = rssi,
                        timestamp = now,
                        scanSessionId = sessionId
                    )
                )
            }
        }

        if (readings.isNotEmpty()) {
            dao.insertSignalReadings(readings)
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Device queries (exposed as Flow for Compose)
    // ═══════════════════════════════════════════════════════════════

    fun observeAllDevices(): Flow<List<DiscoveredDeviceEntity>> =
        dao.observeAllDevices()

    fun observeDevicesByCategory(category: DeviceCategory): Flow<List<DiscoveredDeviceEntity>> =
        dao.observeDevicesByCategory(category)

    fun observeFavorites(): Flow<List<DiscoveredDeviceEntity>> =
        dao.observeFavorites()

    fun searchDevices(query: String): Flow<List<DiscoveredDeviceEntity>> =
        dao.searchDevices(query)

    fun observeDevicesWithReadingCount(): Flow<List<DeviceWithReadingCount>> =
        dao.observeDevicesWithReadingCount()

    fun observeRecentDevices(hours: Int = 24): Flow<List<DiscoveredDeviceEntity>> =
        dao.observeRecentDevices(Instant.now().minus(hours.toLong(), ChronoUnit.HOURS))

    suspend fun getDeviceById(id: Long): DiscoveredDeviceEntity? =
        dao.getDeviceById(id)

    suspend fun getDeviceByAddress(address: String): DiscoveredDeviceEntity? =
        dao.getDeviceByAddress(address)

    fun observeDeviceById(id: Long): Flow<DiscoveredDeviceEntity?> =
        dao.observeDeviceById(id)

    // ═══════════════════════════════════════════════════════════════
    //  Device management
    // ═══════════════════════════════════════════════════════════════

    suspend fun toggleFavorite(id: Long) {
        val device = dao.getDeviceById(id) ?: return
        dao.setFavorite(id, !device.isFavorite)
    }

    suspend fun setCustomLabel(id: Long, label: String?) =
        dao.setCustomLabel(id, label)

    suspend fun setNotes(id: Long, notes: String?) =
        dao.setNotes(id, notes)

    suspend fun deleteDevice(id: Long) =
        dao.deleteDeviceById(id)

    // ═══════════════════════════════════════════════════════════════
    //  Signal history
    // ═══════════════════════════════════════════════════════════════

    fun observeSignalHistory(deviceId: Long, limit: Int = 360): Flow<List<SignalOverTime>> =
        dao.observeSignalHistory(deviceId, limit)

    // ═══════════════════════════════════════════════════════════════
    //  Statistics
    // ═══════════════════════════════════════════════════════════════

    fun observeTotalDeviceCount(): Flow<Int> = dao.observeTotalDeviceCount()
    fun observeWifiCount(): Flow<Int> = dao.observeWifiCount()
    fun observeBluetoothCount(): Flow<Int> = dao.observeBluetoothCount()

    suspend fun getTotalScanCount(): Int = dao.getTotalScanCount()
    suspend fun getDeviceCountByCategory(): List<CategoryCount> = dao.getDeviceCountByCategory()

    // ═══════════════════════════════════════════════════════════════
    //  Maintenance
    // ═══════════════════════════════════════════════════════════════

    /**
     * Delete signal readings older than N days to keep the DB lean.
     */
    suspend fun cleanupOldReadings(keepDays: Long = 30) {
        val cutoff = Instant.now().minus(keepDays, ChronoUnit.DAYS)
        dao.deleteOldReadings(cutoff)
    }

    fun observeScanSessions(): Flow<List<ScanSessionEntity>> =
        dao.observeAllSessions()

    // ═══════════════════════════════════════════════════════════════
    //  LAN Device persistence
    // ═══════════════════════════════════════════════════════════════

    suspend fun persistLanScan(devices: List<com.scanner.app.util.LanDevice>) {
        dao.insertScanSession(
            ScanSessionEntity(
                scanType = ScanType.LAN,
                timestamp = Instant.now(),
                deviceCount = devices.size,
                durationMs = null
            )
        )

        for (device in devices) {
            val address = device.mac ?: "lan:${device.ip}"
            val existing = dao.getDeviceByAddress(address)
            val metaJson = try {
                existing?.metadata?.let { JSONObject(it) } ?: JSONObject()
            } catch (_: Exception) { JSONObject() }

            metaJson.apply {
                put("ip", device.ip)
                put("mac", device.mac ?: "")
                put("vendor", device.vendor ?: "")
                put("hostname", device.hostname ?: "")
                put("isGateway", device.isGateway)
                put("isOwnDevice", device.isOwnDevice)
                put("discoveredVia", device.discoveredVia.name)
                device.latencyMs?.let { put("latencyMs", it.toDouble()) }
                // Full vendor name from OUI lookup
                device.mac?.let { mac ->
                    MacVendorLookup.lookup(mac)?.let { put("vendorFull", it) }
                }
                // UPnP info
                device.upnpInfo?.let { upnp ->
                    upnp.friendlyName?.let { put("upnpName", it) }
                    upnp.manufacturer?.let { put("upnpManufacturer", it) }
                    upnp.modelName?.let { put("upnpModel", it) }
                    upnp.modelDescription?.let { put("upnpDescription", it) }
                    upnp.deviceType?.let { put("upnpDeviceType", it) }
                    if (upnp.services.isNotEmpty()) {
                        put("upnpServices", org.json.JSONArray(upnp.services))
                    }
                }
                if (device.services.isNotEmpty()) {
                    val servicesArr = org.json.JSONArray()
                    device.services.forEach { svc ->
                        val svcObj = JSONObject()
                        svcObj.put("name", svc.name)
                        svcObj.put("type", svc.type)
                        svcObj.put("port", svc.port)
                        servicesArr.put(svcObj)
                    }
                    put("services", servicesArr)
                }
            }

            dao.upsertDevice(
                address = address,
                name = device.hostname ?: device.vendor ?: device.ip,
                category = DeviceCategory.LAN,
                signalStrength = null,
                metadata = metaJson.toString()
            )
        }
    }

    suspend fun persistPortScanResults(
        ip: String,
        results: List<com.scanner.app.util.PortScanResult>
    ) {
        // Find device by IP in metadata, update with port scan results
        val address = "lan:$ip"
        val device = dao.getDeviceByAddress(address)
            ?: dao.getDeviceByAddress(ip)
            ?: return

        val existingMeta = try {
            device.metadata?.let { JSONObject(it) } ?: JSONObject()
        } catch (_: Exception) { JSONObject() }

        val portsArr = org.json.JSONArray()
        results.forEach { port ->
            val portObj = JSONObject()
            portObj.put("port", port.port)
            portObj.put("service", port.serviceName)
            portObj.put("state", port.state.name)
            port.banner?.let { portObj.put("banner", it) }
            port.latencyMs?.let { portObj.put("latencyMs", it.toDouble()) }
            portsArr.put(portObj)
        }
        existingMeta.put("openPorts", portsArr)
        existingMeta.put("portScanTime", Instant.now().toString())

        dao.updateDevice(device.copy(metadata = existingMeta.toString()))
    }

    suspend fun persistGattData(address: String, gattJsonStr: String) {
        val existing = dao.getDeviceByAddress(address)
        val existingMeta = try {
            existing?.metadata?.let { JSONObject(it) } ?: JSONObject()
        } catch (_: Exception) { JSONObject() }
        
        try {
            val gattData = JSONObject(gattJsonStr)
            existingMeta.put("gattData", gattData)
            
            dao.upsertDevice(
                address = address,
                name = existing?.name ?: "(Unbekannt)",
                category = existing?.deviceCategory ?: DeviceCategory.BT_BLE,
                signalStrength = existing?.lastSignalStrength,
                metadata = existingMeta.toString()
            )
        } catch (_: Exception) {}
    }
}
