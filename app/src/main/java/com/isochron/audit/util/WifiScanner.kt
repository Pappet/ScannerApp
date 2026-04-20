package com.scanner.app.util

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.scanner.app.data.WifiNetwork

/**
 * Utility class for performing WiFi scans and retrieving information about the current connection.
 * Handles scanning timeouts, permission guarding, and parsing of [ScanResult] into [WifiNetwork].
 *
 * @property context The application context used for OS service access and receiver registration.
 */
@SuppressLint("MissingPermission")
class WifiScanner(private val context: Context) {

    companion object {
        private const val TAG = "WifiScanner"
        private const val SCAN_TIMEOUT_MS = 10_000L
    }

    private val wifiManager: WifiManager? =
        try {
            context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get WifiManager", e)
            null
        }

    private var scanReceiver: BroadcastReceiver? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var timeoutRunnable: Runnable? = null

    /**
     * Initiates an asynchronous WiFi scan and returns the results via a callback.
     * Use [cleanup] or [cleanupCurrentScan] to cancel a pending scan or unregister receivers.
     *
     * @param onResults Callback invoked with the list of discovered [WifiNetwork]s.
     *                  Returns an empty list if services are unavailable or permissions are denied.
     */
    fun startScan(onResults: (List<WifiNetwork>) -> Unit) {
        if (wifiManager == null) {
            Log.w(TAG, "WifiManager not available")
            onResults(emptyList())
            return
        }

        // Cleanup previous scan
        cleanupCurrentScan()

        try {
            scanReceiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    cancelTimeout()
                    try {
                        val results = parseScanResults()
                        onResults(results)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing scan results", e)
                        onResults(emptyList())
                    }
                    cleanupCurrentScan()
                }
            }

            val intentFilter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)

            // API 33+ requires receiver export flag
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(scanReceiver, intentFilter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(scanReceiver, intentFilter)
            }

            // Set timeout — if scan never completes, return cached results
            timeoutRunnable = Runnable {
                Log.w(TAG, "Scan timed out, returning cached results")
                try {
                    val cached = parseScanResults()
                    onResults(cached)
                } catch (e: Exception) {
                    onResults(emptyList())
                }
                cleanupCurrentScan()
            }
            mainHandler.postDelayed(timeoutRunnable!!, SCAN_TIMEOUT_MS)

            @Suppress("DEPRECATION")
            val scanStarted = wifiManager.startScan()
            if (!scanStarted) {
                Log.w(TAG, "startScan() returned false, using cached results")
                cancelTimeout()
                try {
                    onResults(parseScanResults())
                } catch (e: Exception) {
                    onResults(emptyList())
                }
                cleanupCurrentScan()
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied for WiFi scan", e)
            cancelTimeout()
            onResults(emptyList())
            cleanupCurrentScan()
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error starting scan", e)
            cancelTimeout()
            onResults(emptyList())
            cleanupCurrentScan()
        }
    }

    /**
     * Retrieves the SSID of the currently connected WiFi network.
     * Performs a check for transport type to ensure the active network is indeed WiFi.
     *
     * @return The SSID without surrounding quotes, or null if not connected or unavailable.
     */
    fun getConnectedSsid(): String? {
        return try {
            val connectivityManager =
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                    ?: return null
            val network = connectivityManager.activeNetwork ?: return null
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return null

            if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return null

            @Suppress("DEPRECATION")
            val wifiInfo = wifiManager?.connectionInfo ?: return null
            val ssid = wifiInfo.ssid?.removeSurrounding("\"")
            if (ssid == "<unknown ssid>" || ssid.isNullOrBlank()) null else ssid
        } catch (e: Exception) {
            Log.e(TAG, "Error getting connected SSID", e)
            null
        }
    }

    /**
     * Checks if the WiFi adapter is currently enabled.
     *
     * @return True if WiFi is on, false otherwise.
     */
    fun isWifiEnabled(): Boolean {
        return try {
            wifiManager?.isWifiEnabled == true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Unregisters any active [BroadcastReceiver] and cancels pending timeout callbacks.
     * Should be called when the scanner is no longer needed (e.g., in a Composable's onDispose).
     */
    fun cleanup() {
        cleanupCurrentScan()
    }

    private fun cleanupCurrentScan() {
        cancelTimeout()
        scanReceiver?.let { receiver ->
            try {
                context.unregisterReceiver(receiver)
            } catch (_: IllegalArgumentException) {
                // Already unregistered
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering receiver", e)
            }
        }
        scanReceiver = null
    }

    private fun cancelTimeout() {
        timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        timeoutRunnable = null
    }

    private fun parseScanResults(): List<WifiNetwork> {
        val mgr = wifiManager ?: return emptyList()

        val scanResults: List<ScanResult> = try {
            mgr.scanResults ?: emptyList()
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied reading scan results", e)
            emptyList()
        }

        val connectedBssid: String? = try {
            @Suppress("DEPRECATION")
            mgr.connectionInfo?.bssid
        } catch (_: Exception) { null }

        return scanResults.mapNotNull { result ->
            try {
                val ssid = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    result.wifiSsid?.toString()?.removeSurrounding("\"") ?: ""
                } else {
                    @Suppress("DEPRECATION")
                    result.SSID ?: ""
                }

                val capabilities = result.capabilities ?: ""

                val vendor = MacVendorLookup.lookup(result.BSSID ?: "")

                val standard = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    when (result.wifiStandard) {
                        8 -> "Wi-Fi 7" // WIFI_STANDARD_11BE
                        6 -> "Wi-Fi 6" // WIFI_STANDARD_11AX
                        5 -> "Wi-Fi 5" // WIFI_STANDARD_11AC
                        4 -> "Wi-Fi 4" // WIFI_STANDARD_11N
                        2 -> "Wi-Fi Legacy (a/b/g)" // WIFI_STANDARD_LEGACY
                        else -> null
                    }
                } else null

                val width = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    when (result.channelWidth) {
                        ScanResult.CHANNEL_WIDTH_20MHZ -> "20 MHz"
                        ScanResult.CHANNEL_WIDTH_40MHZ -> "40 MHz"
                        ScanResult.CHANNEL_WIDTH_80MHZ -> "80 MHz"
                        ScanResult.CHANNEL_WIDTH_160MHZ -> "160 MHz"
                        ScanResult.CHANNEL_WIDTH_80MHZ_PLUS_MHZ -> "80+80 MHz"
                        else -> null
                    }
                } else null

                // Free-space path loss (FSPL) based distance estimation
                // Formula: d = 10 ^ ((27.55 - (20 * log10(freq)) + |signal|) / 20)
                // Note: This is a rough estimate and doesn't account for walls/obstructions.
                val exp = (27.55 - (20 * Math.log10(result.frequency.toDouble())) + kotlin.math.abs(result.level)) / 20.0
                val distance = Math.pow(10.0, exp)

                WifiNetwork(
                    ssid = ssid.ifBlank { "(Verstecktes Netzwerk)" },
                    bssid = result.BSSID ?: return@mapNotNull null,
                    signalStrength = result.level,
                    frequency = result.frequency,
                    channel = frequencyToChannel(result.frequency),
                    securityType = getSecurityType(result),
                    isConnected = result.BSSID == connectedBssid,
                    band = if (result.frequency > 4900) "5 GHz" else "2.4 GHz",
                    wpsEnabled = capabilities.contains("WPS", ignoreCase = true),
                    rawCapabilities = capabilities,
                    vendor = vendor,
                    wifiStandard = standard,
                    channelWidth = width,
                    distance = distance
                )
            } catch (e: Exception) {
                Log.w(TAG, "Error parsing scan result", e)
                null
            }
        }.sortedByDescending { it.signalStrength }
    }

    /**
     * Converts a frequency in MHz to its corresponding channel number.
     * Supports 2.4 GHz (1-14) and 5 GHz bands.
     */
    private fun frequencyToChannel(freq: Int): Int = when {
        freq in 2412..2484 -> (freq - 2412) / 5 + 1
        freq in 5170..5825 -> (freq - 5170) / 5 + 34
        freq == 2484 -> 14
        else -> -1
    }

    /**
     * Parses the capabilities string of a [ScanResult] into a human-readable security type.
     */
    private fun getSecurityType(result: ScanResult): String {
        val capabilities = result.capabilities ?: return "Unbekannt"
        
        return when {
            capabilities.contains("WPA3-Enterprise") -> "WPA3 Enterprise"
            capabilities.contains("WPA3-Personal") || capabilities.contains("WPA3") -> "WPA3 (SAE)"
            capabilities.contains("WPA2-Enterprise") || capabilities.contains("WPA2-EAP") -> "WPA2 Enterprise"
            capabilities.contains("WPA2") -> {
                val cipher = if (capabilities.contains("CCMP")) "CCMP" else if (capabilities.contains("TKIP")) "TKIP" else "PSK"
                "WPA2 ($cipher)"
            }
            capabilities.contains("WPA-") || capabilities.contains("WPA]") || capabilities.contains("WPA") -> "WPA"
            capabilities.contains("WEP") -> "WEP"
            capabilities.contains("OWE") -> "OWE (Enhanced Open)"
            else -> "Offen"
        }
    }
}
