package com.scanner.app.util

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/**
 * Information about a GATT Service discovered on a BLE device.
 */
data class GattServiceInfo(
    val uuid: UUID,
    val name: String,
    val category: BleUuidDatabase.ServiceCategory,
    val isStandard: Boolean,
    val characteristics: List<GattCharacteristicInfo>
)

/**
 * Information about a GATT Characteristic within a service.
 */
data class GattCharacteristicInfo(
    val uuid: UUID,
    val name: String,
    val isStandard: Boolean,
    val properties: List<CharacteristicProperty>,
    val value: ByteArray? = null,
    val stringValue: String? = null,
    val descriptors: List<GattDescriptorInfo> = emptyList()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GattCharacteristicInfo) return false
        return uuid == other.uuid
    }
    override fun hashCode() = uuid.hashCode()
}

/**
 * Information about a GATT Descriptor associated with a characteristic.
 */
data class GattDescriptorInfo(
    val uuid: UUID,
    val name: String,
    val value: ByteArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GattDescriptorInfo) return false
        return uuid == other.uuid
    }
    override fun hashCode() = uuid.hashCode()
}

/**
 * Categories of properties a characteristic can have (Read, Write, etc.).
 */
enum class CharacteristicProperty(val label: String) {
    READ("Lesen"),
    WRITE("Schreiben"),
    WRITE_NO_RESPONSE("Schreiben (ohne Antwort)"),
    NOTIFY("Benachrichtigen"),
    INDICATE("Indikation"),
    BROADCAST("Broadcast"),
    SIGNED_WRITE("Signiertes Schreiben"),
    EXTENDED_PROPS("Erweitert");

    companion object {
        fun fromProperties(props: Int): List<CharacteristicProperty> {
            val result = mutableListOf<CharacteristicProperty>()
            if (props and BluetoothGattCharacteristic.PROPERTY_READ != 0) result.add(READ)
            if (props and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) result.add(WRITE)
            if (props and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) result.add(WRITE_NO_RESPONSE)
            if (props and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) result.add(NOTIFY)
            if (props and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) result.add(INDICATE)
            if (props and BluetoothGattCharacteristic.PROPERTY_BROADCAST != 0) result.add(BROADCAST)
            if (props and BluetoothGattCharacteristic.PROPERTY_SIGNED_WRITE != 0) result.add(SIGNED_WRITE)
            if (props and BluetoothGattCharacteristic.PROPERTY_EXTENDED_PROPS != 0) result.add(EXTENDED_PROPS)
            return result
        }
    }
}

/**
 * Represents the current state of a [GattExplorer] session.
 */
data class GattExplorerState(
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val services: List<GattServiceInfo> = emptyList(),
    val deviceName: String? = null,
    val deviceAddress: String = "",
    val rssi: Int? = null,
    val error: String? = null,
    val isReadingCharacteristics: Boolean = false,
    val readProgress: Int = 0,
    val readTotal: Int = 0
)

/**
 * Possible connection and discovery states for BLE devices.
 */
enum class ConnectionState(val label: String) {
    DISCONNECTED("Getrennt"),
    CONNECTING("Verbinde..."),
    CONNECTED("Verbunden"),
    DISCOVERING("Dienste werden erkannt..."),
    READING("Werte lesen..."),
    READY("Bereit"),
    FAILED("Fehlgeschlagen")
}

/**
 * A utility class for exploring the GATT profile of a BLE device.
 * It manages connection, service discovery, and reading characteristic values asynchronously.
 * Uses a [Mutex] to serialize characteristic reads as required by the Bluetooth stack.
 */
@SuppressLint("MissingPermission")
class GattExplorer(private val context: Context) {

    private var bluetoothGatt: BluetoothGatt? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _state = MutableStateFlow(GattExplorerState())
    val state: StateFlow<GattExplorerState> = _state.asStateFlow()

    // Mutex ensures only one characteristic read is in-flight at a time.
    // Without this, a second read request could overwrite readCharContinuation before
    // the first read's onCharacteristicRead callback fires, causing the first await()
    // to hang until timeout.
    private val readCharLock = Mutex()
    @Volatile
    private var readCharContinuation: CompletableDeferred<ByteArray?>? = null
    private var serviceDiscoveryContinuation: CompletableDeferred<Boolean>? = null

    /**
     * Initiates a connection to a BLE device by its MAC address.
     * Automatically triggers service discovery and characteristic reading upon successful connection.
     */
    fun connect(address: String) {
        disconnect()

        try {
            val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter = manager?.adapter
            if (adapter == null) {
                _state.value = GattExplorerState(
                    connectionState = ConnectionState.FAILED,
                    deviceAddress = address,
                    error = "Bluetooth nicht verfügbar"
                )
                return
            }

            val device = adapter.getRemoteDevice(address)

            val devName = try {
                var n: String? = null
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    n = device.alias
                }
                if (n.isNullOrBlank()) {
                    n = device.name
                }
                n
            } catch (e: SecurityException) {
                android.util.Log.e("GattExplorer", "SecurityException: Permission denied reading device name/alias", e)
                null
            } catch (e: Exception) {
                android.util.Log.w("GattExplorer", "Error reading device name/alias", e)
                null
            }

            _state.value = GattExplorerState(
                connectionState = ConnectionState.CONNECTING,
                deviceAddress = address,
                deviceName = devName
            )

            bluetoothGatt = device.connectGatt(context, false, gattCallback, android.bluetooth.BluetoothDevice.TRANSPORT_LE)
        } catch (e: SecurityException) {
            _state.value = GattExplorerState(
                connectionState = ConnectionState.FAILED,
                deviceAddress = address,
                error = "Bluetooth-Berechtigung fehlt"
            )
        } catch (e: Exception) {
            _state.value = GattExplorerState(
                connectionState = ConnectionState.FAILED,
                deviceAddress = address,
                error = "Verbindungsfehler: ${e.message}"
            )
        }
    }

    /**
     * Disconnects from the current device and closes the GATT client.
     */
    fun disconnect() {
        try {
            bluetoothGatt?.disconnect()
        } catch (_: Exception) {}
        try {
            bluetoothGatt?.close()
        } catch (_: Exception) {}
        bluetoothGatt = null
        _state.value = GattExplorerState()
    }

    /**
     * Cleanup resources, disconnect, and cancel the internal coroutine scope.
     */
    fun cleanup() {
        disconnect()
        scope.cancel()
    }

    // ─── GATT Callback ──────────────────────────────────────────

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            try {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        _state.value = _state.value.copy(
                            connectionState = ConnectionState.DISCOVERING
                        )
                        gatt.discoverServices()
                        gatt.readRemoteRssi()
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        _state.value = _state.value.copy(
                            connectionState = ConnectionState.DISCONNECTED
                        )
                        try { gatt.close() } catch (_: Exception) {}
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("GattExplorer", "Error in onConnectionStateChange", e)
                _state.value = _state.value.copy(
                    connectionState = ConnectionState.FAILED,
                    error = "Verbindungsfehler: ${e.message}"
                )
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            try {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    serviceDiscoveryContinuation?.complete(true)
                    scope.launch {
                        try {
                            processServices(gatt)
                        } catch (e: Exception) {
                            android.util.Log.e("GattExplorer", "Error processing services", e)
                            _state.value = _state.value.copy(
                                connectionState = ConnectionState.FAILED,
                                error = "Fehler beim Lesen der Dienste"
                            )
                        }
                    }
                } else {
                    _state.value = _state.value.copy(
                        connectionState = ConnectionState.FAILED,
                        error = "Service Discovery fehlgeschlagen (Status: $status)"
                    )
                    serviceDiscoveryContinuation?.complete(false)
                }
            } catch (e: Exception) {
                android.util.Log.e("GattExplorer", "Error in onServicesDiscovered", e)
            }
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                @Suppress("DEPRECATION")
                readCharContinuation?.complete(characteristic.value)
            } else {
                readCharContinuation?.complete(null)
            }
        }

        override fun onReadRemoteRssi(gatt: BluetoothGatt, rssi: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                _state.value = _state.value.copy(rssi = rssi)
            }
        }
    }

    // ─── Service Processing ─────────────────────────────────────

    private suspend fun processServices(gatt: BluetoothGatt) {
        val gattServices = gatt.services ?: return

        // First pass: build service tree without reading values
        val services = gattServices.map { service ->
            val characteristics = service.characteristics.map { char ->
                val properties = CharacteristicProperty.fromProperties(char.properties)
                val descriptors = char.descriptors.map { desc ->
                    GattDescriptorInfo(
                        uuid = desc.uuid,
                        name = BleUuidDatabase.descriptorName(desc.uuid)
                    )
                }
                GattCharacteristicInfo(
                    uuid = char.uuid,
                    name = BleUuidDatabase.characteristicName(char.uuid),
                    isStandard = BleUuidDatabase.isStandardUuid(char.uuid),
                    properties = properties,
                    descriptors = descriptors
                )
            }
            GattServiceInfo(
                uuid = service.uuid,
                name = BleUuidDatabase.serviceName(service.uuid),
                category = BleUuidDatabase.serviceCategory(service.uuid),
                isStandard = BleUuidDatabase.isStandardUuid(service.uuid),
                characteristics = characteristics
            )
        }

        _state.value = _state.value.copy(
            connectionState = ConnectionState.READING,
            services = services,
            isReadingCharacteristics = true
        )

        // Second pass: read readable characteristics
        val readableChars = gattServices.flatMap { svc ->
            svc.characteristics.filter { char ->
                char.properties and BluetoothGattCharacteristic.PROPERTY_READ != 0
            }.map { char -> svc to char }
        }

        _state.value = _state.value.copy(
            readTotal = readableChars.size
        )

        val updatedServices = services.toMutableList()

        for ((index, pair) in readableChars.withIndex()) {
            val (_, char) = pair
            _state.value = _state.value.copy(readProgress = index + 1)

            val value = readCharacteristic(gatt, char)
            if (value != null) {
                // Update the characteristic in our service tree
                val serviceIdx = updatedServices.indexOfFirst { it.uuid == pair.first.uuid }
                if (serviceIdx >= 0) {
                    val svc = updatedServices[serviceIdx]
                    val charIdx = svc.characteristics.indexOfFirst { it.uuid == char.uuid }
                    if (charIdx >= 0) {
                        val updatedChars = svc.characteristics.toMutableList()
                        updatedChars[charIdx] = updatedChars[charIdx].copy(
                            value = value,
                            stringValue = tryDecodeValue(value)
                        )
                        updatedServices[serviceIdx] = svc.copy(characteristics = updatedChars)
                    }
                }
            }

            delay(100) // Small delay between reads to avoid overwhelming the device
        }

        _state.value = _state.value.copy(
            connectionState = ConnectionState.READY,
            services = updatedServices,
            isReadingCharacteristics = false
        )
    }

    /**
     * Asynchronously reads a GATT characteristic value.
     * Uses a [Mutex] to ensure only one read request is active at a time, preventing protocol overlaps.
     */
    private suspend fun readCharacteristic(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic
    ): ByteArray? {
        // Mutex ensures that we don't start a second read before the first one completes.
        // BLE stacks typically allow only one pending GATT operation per connection.
        return readCharLock.withLock {
            try {
                withTimeout(3000L) {
                    val deferred = CompletableDeferred<ByteArray?>()
                    readCharContinuation = deferred
                    @Suppress("DEPRECATION")
                    gatt.readCharacteristic(characteristic)
                    deferred.await()
                }
            } catch (_: Exception) {
                null
            } finally {
                readCharContinuation = null
            }
        }
    }

    /**
     * Attempts to decode a raw [ByteArray] into a human-readable string.
     * Supports UTF-8 strings, single bytes (as integers), and 16/32-bit little-endian integers.
     * Falls back to a hex string if no common pattern is detected.
     */
    private fun tryDecodeValue(bytes: ByteArray): String? {
        if (bytes.isEmpty()) return null

        // Try UTF-8 string — accept all printable ASCII
        val str = bytes.toString(Charsets.UTF_8).trim('\u0000', ' ')
        if (str.isNotEmpty()) {
            // Count printable characters
            val printable = str.count { c -> c.code in 32..126 }
            val ratio = printable.toFloat() / str.length
            // If >80% printable ASCII, treat as string
            if (ratio > 0.8f && str.length > 1) {
                // Clean: keep only printable chars, replace others with '.'
                return str.map { c -> if (c.code in 32..126) c else '.' }.joinToString("").trim()
            }
            // If all printable, return as-is
            if (ratio == 1f) return str
        }

        // Single byte: show as int
        if (bytes.size == 1) {
            return "${bytes[0].toInt() and 0xFF}"
        }

        // 2 bytes: show as uint16 LE
        if (bytes.size == 2) {
            val value = (bytes[0].toInt() and 0xFF) or ((bytes[1].toInt() and 0xFF) shl 8)
            return "$value"
        }

        // 4 bytes: could be uint32 LE
        if (bytes.size == 4) {
            val value = (bytes[0].toLong() and 0xFF) or
                    ((bytes[1].toLong() and 0xFF) shl 8) or
                    ((bytes[2].toLong() and 0xFF) shl 16) or
                    ((bytes[3].toLong() and 0xFF) shl 24)
            return "$value"
        }

        // Fallback: hex
        return bytes.joinToString(" ") { "%02X".format(it) }
    }
}
