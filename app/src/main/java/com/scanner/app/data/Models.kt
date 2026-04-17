package com.scanner.app.data

/**
 * Represents a discovered WiFi network.
 */
data class WifiNetwork(
    val ssid: String,
    val bssid: String,
    val signalStrength: Int,       // dBm
    val frequency: Int,            // MHz
    val channel: Int,
    val securityType: String,
    val isConnected: Boolean = false,
    val band: String,              // "2.4 GHz" or "5 GHz"
    val wpsEnabled: Boolean = false,
    val rawCapabilities: String = "",
    val vendor: String? = null,
    val wifiStandard: String? = null,
    val channelWidth: String? = null,
    val distance: Double? = null
)

/**
 * Represents a discovered Bluetooth device.
 */
data class BluetoothDevice(
    val name: String,
    val address: String,
    val rssi: Int?,                // dBm, null if not available
    val type: DeviceType,
    val bondState: BondState,
    val isConnected: Boolean = false,
    val deviceClass: String?,
    val vendor: String? = null,         // MAC OUI vendor
    val minorClass: String? = null,     // e.g. "Smartphone", "Laptop", "Headphones"
    val serviceUuids: List<String> = emptyList(),
    val txPower: Int? = null
) {
    /**
     * Best available display name: name > vendor > address
     */
    fun displayName(): String = when {
        name != "(Unbekannt)" && name.isNotBlank() -> name
        vendor != null -> vendor
        else -> address
    }

    /**
     * Subtitle info line
     */
    fun subtitle(): String = buildString {
        if (name != "(Unbekannt)" && vendor != null) append(vendor)
        if (minorClass != null) {
            if (isNotEmpty()) append(" · ")
            append(minorClass)
        }
        if (isEmpty()) append(type.displayName())
    }
}

enum class DeviceType {
    CLASSIC,
    BLE,
    DUAL,
    UNKNOWN;

    fun displayName(): String = when (this) {
        CLASSIC -> "Classic"
        BLE -> "BLE"
        DUAL -> "Dual"
        UNKNOWN -> "Unbekannt"
    }
}

enum class BondState {
    BONDED,
    BONDING,
    NOT_BONDED;

    fun displayName(): String = when (this) {
        BONDED -> "Gekoppelt"
        BONDING -> "Kopplung..."
        NOT_BONDED -> "Nicht gekoppelt"
    }
}
