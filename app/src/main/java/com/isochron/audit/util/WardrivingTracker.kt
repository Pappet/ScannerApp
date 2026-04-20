package com.scanner.app.util

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.util.Log
import com.scanner.app.data.WifiNetwork
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Represents a single WiFi network observation with geographic coordinates.
 */
data class WardrivingEntry(
        val ssid: String,
        val bssid: String,
        val signalStrength: Int,
        val frequency: Int,
        val channel: Int,
        val securityType: String,
        val latitude: Double,
        val longitude: Double,
        val altitude: Double?,
        val accuracy: Float?,
        val timestamp: Instant
)

/**
 * Manages GPS-tagged WiFi scanning sessions (wardriving).
 * Records geographic coordinates for discovered networks and exports data in common formats.
 */
@SuppressLint("MissingPermission")
class WardrivingTracker(private val context: Context) {

    companion object {
        private const val TAG = "WardrivingTracker"
    }

    private val locationManager: LocationManager? =
            try {
                context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            } catch (_: Exception) {
                null
            }

    private var lastLocation: Location? = null
    private var locationListener: LocationListener? = null
    private val entries = mutableListOf<WardrivingEntry>()

    var isTracking: Boolean = false
        private set

    /**
     * Initializes the [LocationListener] and requests periodic updates from GPS or Network providers.
     * Starts tracking the device's geographical position.
     */
    fun startTracking() {
        if (isTracking) return
        if (locationManager == null) {
            Log.w(TAG, "LocationManager not available")
            return
        }

        locationListener =
                object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        lastLocation = location
                    }
                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {}
                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                }

        try {
            // Try GPS first, then network
            val provider =
                    when {
                        locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ->
                                LocationManager.GPS_PROVIDER
                        locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ->
                                LocationManager.NETWORK_PROVIDER
                        else -> null
                    }

            if (provider != null) {
                locationManager.requestLocationUpdates(
                        provider,
                        2000L,
                        1f,
                        locationListener!!,
                        Looper.getMainLooper()
                )
                // Get last known location as starting point
                lastLocation = locationManager.getLastKnownLocation(provider)
                isTracking = true
                Log.d(TAG, "Wardriving started with provider: $provider")
            } else {
                Log.w(TAG, "No location provider available")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission denied", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting location tracking", e)
        }
    }

    /**
     * Stops location updates and releases the [LocationListener].
     * Should be called when the wardriving session ends.
     */
    fun stopTracking() {
        locationListener?.let { listener ->
            try {
                locationManager?.removeUpdates(listener)
            } catch (_: Exception) {}
        }
        locationListener = null
        isTracking = false
    }

    /**
     * Correlates a list of discovered WiFi networks with the most recent GPS location.
     * New entries are added to the internal [entries] list.
     */
    fun recordNetworks(networks: List<WifiNetwork>) {
        val location = lastLocation ?: return

        val now = Instant.now()
        for (network in networks) {
            entries.add(
                    WardrivingEntry(
                            ssid = network.ssid,
                            bssid = network.bssid,
                            signalStrength = network.signalStrength,
                            frequency = network.frequency,
                            channel = network.channel,
                            securityType = network.securityType,
                            latitude = location.latitude,
                            longitude = location.longitude,
                            altitude = if (location.hasAltitude()) location.altitude else null,
                            accuracy = if (location.hasAccuracy()) location.accuracy else null,
                            timestamp = now
                    )
            )
        }
    }

    fun getEntries(): List<WardrivingEntry> = entries.toList()
    fun getEntryCount(): Int = entries.size
    fun getUniqueNetworks(): Int = entries.map { it.bssid }.distinct().size
    fun getCurrentLocation(): Location? = lastLocation

    fun clearEntries() {
        entries.clear()
    }



    /**
     * Exports all collected observations to a CSV file compatible with the WiGLE.net database format.
     */
    fun exportWigleCsv(file: File) {
        FileOutputStream(file).bufferedWriter(Charsets.UTF_8).use { writer ->
            // WiGLE header
            writer.write(
                    "WigleWifi-1.4,appRelease=1.0,model=Android,release=14,device=ScannerApp,display=ScannerApp,board=,brand="
            )
            writer.newLine()
            writer.write(
                    "MAC,SSID,AuthMode,FirstSeen,Channel,RSSI,CurrentLatitude,CurrentLongitude,AltitudeMeters,AccuracyMeters,Type"
            )
            writer.newLine()

            val formatter =
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                            .withZone(ZoneId.systemDefault())

            for (entry in entries) {
                val row =
                        listOf(
                                        entry.bssid,
                                        CsvEscape.escape(entry.ssid, separator = ','),
                                        "[${entry.securityType}]",
                                        formatter.format(entry.timestamp),
                                        entry.channel.toString(),
                                        entry.signalStrength.toString(),
                                        "%.6f".format(entry.latitude),
                                        "%.6f".format(entry.longitude),
                                        entry.altitude?.let { "%.1f".format(it) } ?: "",
                                        entry.accuracy?.let { "%.1f".format(it) } ?: "",
                                        "WIFI"
                                )
                                .joinToString(",")
                writer.write(row)
                writer.newLine()
            }
        }
    }

    /**
     * Exports the strongest observation for each unique BSSID to a KML file for use in Google Earth/Maps.
     * Color-codes placemarks based on security type (Open, WEP, WPA).
     */
    fun exportKml(file: File) {
        val uniqueByBssid =
                entries.groupBy { it.bssid }.mapValues { (_, v) ->
                    // Take entry with strongest signal
                    v.maxByOrNull { it.signalStrength }!!
                }

        FileOutputStream(file).bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write(
                    """<?xml version="1.0" encoding="UTF-8"?>
<kml xmlns="http://www.opengis.net/kml/2.2">
<Document>
    <name>Netzwerk-Scanner Wardriving</name>
    <description>Exportiert am ${DateTimeFormatter.ISO_INSTANT.format(Instant.now())}</description>
    
    <Style id="open"><IconStyle><color>ff0000ff</color><scale>0.8</scale></IconStyle></Style>
    <Style id="wep"><IconStyle><color>ff0080ff</color><scale>0.8</scale></IconStyle></Style>
    <Style id="wpa"><IconStyle><color>ff00ff00</color><scale>0.8</scale></IconStyle></Style>
    <Style id="wpa2"><IconStyle><color>ff00cc00</color><scale>0.8</scale></IconStyle></Style>
    <Style id="wpa3"><IconStyle><color>ff009900</color><scale>0.8</scale></IconStyle></Style>
"""
            )

            for ((bssid, entry) in uniqueByBssid) {
                val style =
                        when (entry.securityType) {
                            "Offen" -> "open"
                            "WEP" -> "wep"
                            "WPA" -> "wpa"
                            "WPA2" -> "wpa2"
                            "WPA3" -> "wpa3"
                            else -> "wpa2"
                        }

                writer.write(
                        """
    <Placemark>
        <name>${escapeXml(entry.ssid)}</name>
        <description>BSSID: $bssid
Signal: ${entry.signalStrength} dBm
Kanal: ${entry.channel} (${entry.frequency} MHz)
Sicherheit: ${entry.securityType}</description>
        <styleUrl>#$style</styleUrl>
        <Point>
            <coordinates>${entry.longitude},${entry.latitude}${entry.altitude?.let { ",$it" } ?: ""}</coordinates>
        </Point>
    </Placemark>
"""
                )
            }

            writer.write("""</Document>
</kml>""")
        }
    }

    /**
     * Stops any active tracking. Should be called during lifecycle teardown.
     */
    fun cleanup() {
        stopTracking()
    }

    private fun escapeXml(value: String): String {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
    }
}
