package com.scanner.app.util

import java.util.UUID

/**
 * Database of known BLE GATT Service and Characteristic UUIDs.
 * Based on Bluetooth SIG assigned numbers.
 */
object BleUuidDatabase {

    /**
     * Resolves a BLE service UUID to a human-readable name.
     * Supports standard Bluetooth SIG services and common vendor-registered member UUIDs.
     */
    fun serviceName(uuid: UUID): String {
        val short = extractShortUuid(uuid)
        return SERVICES[short]
            ?: if (short in 0xFD00..0xFDFF) "Registrierter Dienst (0x${"%04X".format(short)})"
            else if (short in 0xFE00..0xFEFF) "Vendor-Dienst (0x${"%04X".format(short)})"
            else "Unbekannter Dienst (${formatUuid(uuid)})"
    }

    /** Resolves a BLE characteristic UUID to its official name (e.g., "Battery Level"). */
    fun characteristicName(uuid: UUID): String {
        val short = extractShortUuid(uuid)
        return CHARACTERISTICS[short] ?: "Unbekannt (${formatUuid(uuid)})"
    }

    fun descriptorName(uuid: UUID): String {
        val short = extractShortUuid(uuid)
        return DESCRIPTORS[short] ?: formatUuid(uuid)
    }

    /**
     * Get a short, user-friendly category for a service UUID.
     */
    fun serviceCategory(uuid: UUID): ServiceCategory {
        val short = extractShortUuid(uuid)
        return SERVICE_CATEGORIES[short] ?: ServiceCategory.OTHER
    }

    /**
     * Check if a UUID is a standard Bluetooth SIG UUID (base UUID).
     */
    fun isStandardUuid(uuid: UUID): Boolean {
        val uuidStr = uuid.toString().lowercase()
        return uuidStr.endsWith("-0000-1000-8000-00805f9b34fb")
    }

    fun formatUuid(uuid: UUID): String {
        val str = uuid.toString().uppercase()
        return if (isStandardUuid(uuid)) {
            "0x${str.substring(4, 8)}"
        } else {
            str.substring(0, 8) + "…"
        }
    }

    private fun extractShortUuid(uuid: UUID): Int {
        return ((uuid.mostSignificantBits shr 32) and 0xFFFF).toInt()
    }

    // ─── Service Categories ─────────────────────────────────────

    enum class ServiceCategory(val label: String, val emoji: String) {
        HEALTH("Gesundheit", "❤"),
        FITNESS("Fitness", "🏃"),
        DEVICE_INFO("Geräteinfo", "ℹ"),
        BATTERY("Batterie", "🔋"),
        ENVIRONMENTAL("Umgebung", "🌡"),
        CONNECTIVITY("Verbindung", "📡"),
        INPUT("Eingabe", "⌨"),
        AUDIO("Audio", "🔊"),
        AUTOMATION("Automation", "🏠"),
        LOCATION("Standort", "📍"),
        SECURITY("Sicherheit", "🔒"),
        OTHER("Sonstige", "•")
    }

    // ─── Standard GATT Services (Bluetooth SIG) ─────────────────

    private val SERVICES = mapOf<Int, String>(
        0x1800 to "Generic Access",
        0x1801 to "Generic Attribute",
        0x1802 to "Immediate Alert",
        0x1803 to "Link Loss",
        0x1804 to "Tx Power",
        0x1805 to "Current Time",
        0x1806 to "Reference Time Update",
        0x1807 to "Next DST Change",
        0x1808 to "Glucose",
        0x1809 to "Health Thermometer",
        0x180A to "Device Information",
        0x180D to "Heart Rate",
        0x180E to "Phone Alert Status",
        0x180F to "Battery Service",
        0x1810 to "Blood Pressure",
        0x1811 to "Alert Notification",
        0x1812 to "Human Interface Device",
        0x1813 to "Scan Parameters",
        0x1814 to "Running Speed and Cadence",
        0x1815 to "Automation IO",
        0x1816 to "Cycling Speed and Cadence",
        0x1818 to "Cycling Power",
        0x1819 to "Location and Navigation",
        0x181A to "Environmental Sensing",
        0x181B to "Body Composition",
        0x181C to "User Data",
        0x181D to "Weight Scale",
        0x181E to "Bond Management",
        0x181F to "Continuous Glucose Monitoring",
        0x1820 to "Internet Protocol Support",
        0x1821 to "Indoor Positioning",
        0x1822 to "Pulse Oximeter",
        0x1823 to "HTTP Proxy",
        0x1824 to "Transport Discovery",
        0x1825 to "Object Transfer",
        0x1826 to "Fitness Machine",
        0x1827 to "Mesh Provisioning",
        0x1828 to "Mesh Proxy",
        0x1829 to "Reconnection Configuration",
        0x183A to "Insulin Delivery",
        0x183B to "Binary Sensor",
        0x183C to "Emergency Configuration",
        0x183E to "Physical Activity Monitor",
        0x1843 to "Audio Input Control",
        0x1844 to "Volume Control",
        0x1845 to "Volume Offset Control",
        0x1846 to "Coordinated Set Identification",
        0x1847 to "Device Time",
        0x1848 to "Media Control",
        0x1849 to "Generic Media Control",
        0x184A to "Constant Tone Extension",
        0x184B to "Telephone Bearer",
        0x184C to "Generic Telephone Bearer",
        0x184D to "Microphone Control",
        0x184E to "Audio Stream Control",
        0x184F to "Broadcast Audio Scan",
        0x1850 to "Published Audio Capabilities",
        0x1851 to "Basic Audio Profile",
        0x1852 to "Broadcast Audio Announcement",
        0x1853 to "Common Audio",
        0x1854 to "Hearing Access",
        0x1856 to "Public Broadcast Announcement",
        0x1858 to "Gaming Audio",

        // Common vendor-specific
        0xFE95 to "Xiaomi Mi",
        0xFE9F to "Google Fast Pair",
        0xFEAA to "Google Eddystone",
        0xFEE7 to "Tencent",
        0xFFF0 to "Vendor Specific (0xFFF0)",

        // ─── Registered Member UUIDs (FD00-FDFF) ─────────────────
        // Source: Bluetooth SIG Assigned Numbers / Nordic Semiconductor DB
        0xFD01 to "Withings (Health Mate)",
        0xFD02 to "Withings (ScanWatch)",
        0xFD05 to "Razer Inc.",
        0xFD06 to "Grünbeck Wasseraufbereitung",
        0xFD07 to "Apple (HomeKit Accessory)",
        0xFD08 to "Motorola Solutions",
        0xFD0D to "Xiaomi (Mi Home)",
        0xFD0F to "Starkey Laboratories",
        0xFD2B to "The Access Technologies",
        0xFD2D to "Xiaomi (FIMI)",
        0xFD43 to "Amazon Lab126",
        0xFD44 to "Dish Network",
        0xFD4F to "LifePlus (Health)",
        0xFD50 to "Hangzhou Tuya",
        0xFD5A to "Samsung Electronics",
        0xFD5B to "Hitachi (Industrial)",
        0xFD6F to "Apple/Google (Exposure Notification)",
        0xFD72 to "Logitech International",
        0xFD76 to "Insulet Corporation (OmniPod)",
        0xFD7A to "Withings (Body+)",
        0xFD81 to "SharkNinja (Robot Vacuum)",
        0xFD83 to "iRobot (Roomba)",
        0xFD8C to "Google (Chromecast)",
        0xFD8E to "Ubiquiti Networks",
        0xFD92 to "Qualcomm Technologies",
        0xFD97 to "June Life (Smart Oven)",
        0xFD9E to "Xiaomi (Yeelight)",
        0xFD9F to "VLC Media Player",
        0xFDA6 to "OPPO (Find My)",
        0xFDAA to "Xiaomi (Redmi)",
        0xFDB5 to "Philips Hue",
        0xFDB6 to "GN Hearing (ReSound)",
        0xFDBB to "Amazon (Echo)",
        0xFDC7 to "Eli Lilly (Insulin Pen)",
        0xFDCE to "Espressif (ESP-BLE-MESH)",
        0xFDD2 to "Bose Corporation",
        0xFDD4 to "LG Electronics",
        0xFDD8 to "Jiangsu Teranovo (Smart Home)",
        0xFDE2 to "Google (Pixel Buds)",
        0xFDF0 to "Google (Nearby Share)",
        0xFE07 to "Sonos Inc.",
        0xFE08 to "Microsoft (Surface)",
        0xFE1C to "Tymtix Technologies (NetLock)",
        0xFE26 to "Google (On-hub)",
        0xFE2C to "Google (Cast)",
        0xFE34 to "SmallMedia (Ledger Nano)",
        0xFE41 to "Hewlett-Packard (Print)",
        0xFE50 to "Google (Android Wear)",
        0xFE59 to "Nordic Semiconductor (DFU)",
        0xFE5A to "Chronologics (Smartwatch)",
        0xFE61 to "Logitech (Logi Options)",
        0xFE6B to "Apple (AirDrop)",
        0xFE78 to "Hewlett-Packard (Sprocket)",
        0xFE7D to "Arlo Technologies",
        0xFE88 to "SALTO Systems (Smart Lock)",
        0xFE8A to "Apple (AirPods)",
        0xFE8B to "Apple (Watch)",
        0xFE8C to "LIFX Labs (Smart Bulb)",
        0xFE94 to "Apple (iBeacon)",
        0xFEA0 to "Google (Physical Web)",
        0xFEB2 to "Microsoft (Windows)",
        0xFEB9 to "Tile Inc. (Tracker)",
        0xFEC7 to "Apple (Handoff)",
        0xFEC8 to "Apple (AirPlay)",
        0xFEC9 to "Apple (Continuity)",
        0xFECA to "Apple (BTLE)",
        0xFECB to "Apple (Nearby)",
        0xFECC to "Apple (Pairing)",
        0xFED4 to "Apple (Accessory)",
        0xFED8 to "Google (Android TV)",
        0xFEDF to "Design SHIFT (Measurement)",
    )

    private val SERVICE_CATEGORIES = mapOf<Int, ServiceCategory>(
        0x1808 to ServiceCategory.HEALTH,
        0x1809 to ServiceCategory.HEALTH,
        0x180D to ServiceCategory.HEALTH,
        0x1810 to ServiceCategory.HEALTH,
        0x181F to ServiceCategory.HEALTH,
        0x1822 to ServiceCategory.HEALTH,
        0x183A to ServiceCategory.HEALTH,
        0x183E to ServiceCategory.HEALTH,
        0x1854 to ServiceCategory.HEALTH,
        0x1814 to ServiceCategory.FITNESS,
        0x1816 to ServiceCategory.FITNESS,
        0x1818 to ServiceCategory.FITNESS,
        0x1826 to ServiceCategory.FITNESS,
        0x181B to ServiceCategory.FITNESS,
        0x181D to ServiceCategory.FITNESS,
        0x180A to ServiceCategory.DEVICE_INFO,
        0x1800 to ServiceCategory.DEVICE_INFO,
        0x1801 to ServiceCategory.DEVICE_INFO,
        0x180F to ServiceCategory.BATTERY,
        0x181A to ServiceCategory.ENVIRONMENTAL,
        0x1805 to ServiceCategory.ENVIRONMENTAL,
        0x1820 to ServiceCategory.CONNECTIVITY,
        0x1823 to ServiceCategory.CONNECTIVITY,
        0x1824 to ServiceCategory.CONNECTIVITY,
        0x1812 to ServiceCategory.INPUT,
        0x1813 to ServiceCategory.INPUT,
        0x1843 to ServiceCategory.AUDIO,
        0x1844 to ServiceCategory.AUDIO,
        0x1848 to ServiceCategory.AUDIO,
        0x184E to ServiceCategory.AUDIO,
        0x1858 to ServiceCategory.AUDIO,
        0x1815 to ServiceCategory.AUTOMATION,
        0x183B to ServiceCategory.AUTOMATION,
        0x1819 to ServiceCategory.LOCATION,
        0x1821 to ServiceCategory.LOCATION,
        0x1802 to ServiceCategory.SECURITY,
        0x1803 to ServiceCategory.SECURITY,
        0x181E to ServiceCategory.SECURITY,
    )

    // ─── Standard GATT Characteristics ──────────────────────────

    private val CHARACTERISTICS = mapOf<Int, String>(
        0x2A00 to "Gerätename",
        0x2A01 to "Erscheinungsbild",
        0x2A02 to "Peripheral Privacy Flag",
        0x2A03 to "Reconnection Address",
        0x2A04 to "Preferred Connection Parameters",
        0x2A05 to "Service Changed",
        0x2A06 to "Alert Level",
        0x2A07 to "Tx Power Level",
        0x2A08 to "Datum/Uhrzeit",
        0x2A09 to "Wochentag",
        0x2A0A to "Exakte Uhrzeit (256)",
        0x2A19 to "Batteriestand",
        0x2A1C to "Temperatur-Typ",
        0x2A1D to "Temperatur-Messung",
        0x2A1E to "Zwischen-Temperatur",
        0x2A21 to "Messintervall",
        0x2A23 to "System-ID",
        0x2A24 to "Modellnummer",
        0x2A25 to "Seriennummer",
        0x2A26 to "Firmware-Version",
        0x2A27 to "Hardware-Version",
        0x2A28 to "Software-Version",
        0x2A29 to "Hersteller",
        0x2A2A to "IEEE Regulatory Cert",
        0x2A2B to "Aktuelle Uhrzeit",
        0x2A34 to "Glucose-Messung",
        0x2A37 to "Herzfrequenz",
        0x2A38 to "Body Sensor Location",
        0x2A39 to "Heart Rate Control Point",
        0x2A3F to "Alert Status",
        0x2A40 to "Ringer Control Point",
        0x2A46 to "New Alert",
        0x2A49 to "Blutdruck Feature",
        0x2A4D to "HID Report",
        0x2A4E to "HID Protocol Mode",
        0x2A50 to "PnP ID",
        0x2A51 to "Glucose Feature",
        0x2A52 to "Record Access Control Point",
        0x2A53 to "RSC Measurement",
        0x2A54 to "RSC Feature",
        0x2A56 to "Digital",
        0x2A58 to "Analog",
        0x2A5A to "Aggregate",
        0x2A5B to "CSC Measurement",
        0x2A5C to "CSC Feature",
        0x2A5D to "Sensor Location",
        0x2A63 to "Cycling Power Measurement",
        0x2A64 to "Cycling Power Vector",
        0x2A65 to "Cycling Power Feature",
        0x2A66 to "Cycling Power Control Point",
        0x2A67 to "Location and Speed",
        0x2A68 to "Navigation",
        0x2A6C to "Höhe",
        0x2A6D to "Luftdruck",
        0x2A6E to "Temperatur",
        0x2A6F to "Luftfeuchtigkeit",
        0x2A70 to "True Wind Speed",
        0x2A71 to "True Wind Direction",
        0x2A72 to "Apparent Wind Speed",
        0x2A73 to "Apparent Wind Direction",
        0x2A74 to "Gust Factor",
        0x2A75 to "Pollen Concentration",
        0x2A76 to "UV-Index",
        0x2A77 to "Irradiance",
        0x2A78 to "Rainfall",
        0x2A79 to "Wind Chill",
        0x2A7A to "Heat Index",
        0x2A7B to "Dew Point",
        0x2A7D to "Descriptor Value Changed",
        0x2A7E to "Aerobic Heart Rate Lower",
        0x2A80 to "Alter",
        0x2A84 to "Aerobic Heart Rate Upper",
        0x2A85 to "Geburtsdatum",
        0x2A87 to "E-Mail",
        0x2A8A to "Vorname",
        0x2A8C to "Geschlecht",
        0x2A8E to "Größe",
        0x2A90 to "Nachname",
        0x2A91 to "Max Heart Rate",
        0x2A98 to "Gewicht",
        0x2A99 to "Database Change Increment",
        0x2A9D to "Weight Measurement",
        0x2A9E to "Weight Scale Feature",
        0x2AA1 to "BMI",
        0x2AA2 to "Sprache",
        0x2AA6 to "Central Address Resolution",
        0x2AAD to "Indoor Positioning Config",
        0x2AAE to "Breitengrad",
        0x2AAF to "Längengrad",
        0x2AB5 to "Location Name",
        0x2AB6 to "URI",
        0x2ABC to "TDS Control Point",

        // BLE 5.0+ Characteristics (2B00+)
        0x2B29 to "Client Supported Features",
        0x2B2A to "Database Hash",
        0x2B3A to "Server Supported Features",
        0x2B7D to "Volume State",
        0x2B7E to "Volume Control Point",
        0x2B7F to "Volume Flags",
        0x2B77 to "Audio Input State",
        0x2B84 to "Audio Stream Endpoint",
        0x2B93 to "Media Player Name",
        0x2BA3 to "Media Control Point",
        0x2BA9 to "Call Control Point",
        0x2BBD to "Hearing Aid Features",
        0x2BDA to "LE GATT Security Levels",
    )

    // ─── Standard GATT Descriptors ──────────────────────────────

    private val DESCRIPTORS = mapOf<Int, String>(
        0x2900 to "Characteristic Extended Properties",
        0x2901 to "User Description",
        0x2902 to "Client Characteristic Configuration",
        0x2903 to "Server Characteristic Configuration",
        0x2904 to "Characteristic Presentation Format",
        0x2905 to "Characteristic Aggregate Format",
    )

    // ─── iBeacon / Eddystone ────────────────────────────────────

    val IBEACON_PREFIX = byteArrayOf(
        0x02, 0x15  // iBeacon type + length
    )

    val EDDYSTONE_SERVICE_UUID: UUID =
        UUID.fromString("0000FEAA-0000-1000-8000-00805F9B34FB")

    /**
     * Parse iBeacon data from manufacturer specific data.
     * Returns (uuid, major, minor, txPower) or null.
     */
    fun parseIBeacon(manufacturerData: ByteArray?): IBeaconData? {
        if (manufacturerData == null || manufacturerData.size < 23) return null

        // Check iBeacon header (Company ID 0x004C = Apple, then 0x02 0x15)
        val offset = if (manufacturerData.size >= 25 &&
            manufacturerData[0] == 0x4C.toByte() &&
            manufacturerData[1] == 0x00.toByte()
        ) 2 else 0

        if (manufacturerData.size < offset + 21) return null
        if (manufacturerData[offset] != 0x02.toByte()) return null
        if (manufacturerData[offset + 1] != 0x15.toByte()) return null

        val uuidBytes = manufacturerData.sliceArray(offset + 2 until offset + 18)
        val uuid = bytesToUuid(uuidBytes)
        val major = ((manufacturerData[offset + 18].toInt() and 0xFF) shl 8) or
                (manufacturerData[offset + 19].toInt() and 0xFF)
        val minor = ((manufacturerData[offset + 20].toInt() and 0xFF) shl 8) or
                (manufacturerData[offset + 21].toInt() and 0xFF)
        val txPower = if (manufacturerData.size > offset + 22)
            manufacturerData[offset + 22].toInt() else null

        return IBeaconData(uuid, major, minor, txPower)
    }

    /**
     * Estimates the physical distance (in meters) between the scanner and a BLE beacon.
     * Uses an empirical path-loss model based on RSSI and factory-calibrated TX Power.
     * Calculated value is an approximation and sensitive to environmental obstructions.
     */
    fun estimateDistance(rssi: Int, txPower: Int): Double {
        if (rssi == 0) return -1.0
        val ratio = rssi.toDouble() / txPower
        return if (ratio < 1.0) {
            Math.pow(ratio, 10.0)
        } else {
            0.89976 * Math.pow(ratio, 7.7095) + 0.111
        }
    }

    private fun bytesToUuid(bytes: ByteArray): UUID {
        val hex = bytes.joinToString("") { "%02x".format(it) }
        val formatted = "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20, 32)}"
        return UUID.fromString(formatted)
    }
}

data class IBeaconData(
    val uuid: UUID,
    val major: Int,
    val minor: Int,
    val txPower: Int?
)
