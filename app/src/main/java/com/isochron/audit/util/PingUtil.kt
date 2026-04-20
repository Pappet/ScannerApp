package com.scanner.app.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL

/**
 * Represents the result of an ICMP [PingUtil.ping] or HTTP latency check.
 */
data class PingResult(
    val host: String,
    val latencyMs: Float?,       // Latency in milliseconds, null if timeout
    val isReachable: Boolean,
    val packetLoss: Float = 0f,  // 0.0 (no loss) to 1.0 (total loss)
    val ttl: Int? = null         // Time-to-Live value from the response
)

/**
 * Snapshot of the current network connection status.
 */
data class NetworkInfo(
    val gatewayIp: String?,      // Default gateway (router) IP
    val deviceIp: String?,       // Device's local IP address
    val dns: String?,            // Primary DNS server IP
    val ssid: String?,           // Connected WiFi SSID
    val linkSpeed: Int?,         // Link speed in Mbps
    val signalStrength: Int?     // Signal strength in dBm
)

/**
 * Utility for performing network diagnostics like ICMP pings and HTTP latency checks.
 * Also provides information about the current network configuration.
 */
class PingUtil(private val context: Context) {

    private val wifiManager: WifiManager? =
        try {
            context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        } catch (_: Exception) { null }

    /**
     * Pings a host using the system's native 'ping' command.
     * Parses the command-line output to extract latency, packet loss, and TTL.
     *
     * @param host IP address or hostname to ping.
     * @param count Number of echo requests to send.
     * @param timeoutSec Timeout in seconds for each request.
     * @return A [PingResult] containing diagnostic data.
     */
    suspend fun ping(host: String, count: Int = 3, timeoutSec: Int = 5): PingResult =
        withContext(Dispatchers.IO) {
            try {
                val process = Runtime.getRuntime().exec(
                    arrayOf("ping", "-c", count.toString(), "-W", timeoutSec.toString(), host)
                )

                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val output = reader.readText()
                reader.close()
                process.waitFor()

                parsePingOutput(host, output)
            } catch (e: Exception) {
                PingResult(
                    host = host,
                    latencyMs = null,
                    isReachable = false,
                    packetLoss = 1f
                )
            }
        }

    /**
     * Performs a fast reachability check using [InetAddress.isReachable].
     * Note: This may fail if ICMP is blocked or restricted by the OS.
     */
    suspend fun isReachable(host: String, timeoutMs: Int = 3000): Boolean =
        withContext(Dispatchers.IO) {
            try {
                InetAddress.getByName(host).isReachable(timeoutMs)
            } catch (_: Exception) {
                false
            }
        }

    /**
     * Measures HTTP ROUND-TRIP latency to a specific URL using a HEAD request.
     * Useful for verifying internet connectivity when ICMP (ping) is blocked.
     */
    suspend fun httpLatency(urlString: String = "https://www.google.com"): PingResult =
        withContext(Dispatchers.IO) {
            try {
                val url = URL(urlString)
                val start = System.nanoTime()
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "HEAD"
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.connect()
                val latency = (System.nanoTime() - start) / 1_000_000f
                conn.disconnect()

                PingResult(
                    host = url.host,
                    latencyMs = latency,
                    isReachable = true
                )
            } catch (_: Exception) {
                PingResult(
                    host = urlString,
                    latencyMs = null,
                    isReachable = false,
                    packetLoss = 1f
                )
            }
        }

    /**
     * Retrieves current WiFi network details including gateway, local IP, and DNS.
     * Uses [WifiManager.getDhcpInfo] and [WifiManager.getConnectionInfo].
     */
    @Suppress("DEPRECATION")
    fun getNetworkInfo(): NetworkInfo {
        return try {
            val mgr = wifiManager ?: return NetworkInfo(null, null, null, null, null, null)
            val dhcpInfo = mgr.dhcpInfo
            val wifiInfo = mgr.connectionInfo

            val gatewayIp = dhcpInfo?.gateway?.takeIf { it != 0 }?.let { intToIp(it) }
            val deviceIp = dhcpInfo?.ipAddress?.takeIf { it != 0 }?.let { intToIp(it) }
            val dns = dhcpInfo?.dns1?.takeIf { it != 0 }?.let { intToIp(it) }
            val ssid = wifiInfo?.ssid?.removeSurrounding("\"")
                ?.takeIf { it != "<unknown ssid>" && it.isNotBlank() }

            NetworkInfo(
                gatewayIp = gatewayIp,
                deviceIp = deviceIp,
                dns = dns,
                ssid = ssid,
                linkSpeed = wifiInfo?.linkSpeed?.takeIf { it > 0 },
                signalStrength = wifiInfo?.rssi?.takeIf { it != 0 && it > -127 }
            )
        } catch (e: Exception) {
            NetworkInfo(null, null, null, null, null, null)
        }
    }

    /**
     * Checks for verified internet access using [ConnectivityManager].
     */
    fun hasInternetConnection(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val network = cm.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } catch (_: Exception) {
            false
        }
    }

    // ─── Private helpers ────────────────────────────────────────

    private fun parsePingOutput(host: String, output: String): PingResult {
        // Parse packet loss: "3 packets transmitted, 3 received, 0% packet loss"
        val lossRegex = """(\d+)% packet loss""".toRegex()
        val lossMatch = lossRegex.find(output)
        val packetLoss = lossMatch?.groupValues?.get(1)?.toFloatOrNull()?.div(100f) ?: 1f

        // Parse avg latency: "rtt min/avg/max/mdev = 1.234/5.678/9.012/1.234 ms"
        val rttRegex = """rtt min/avg/max/mdev = [\d.]+/([\d.]+)/[\d.]+/[\d.]+ ms""".toRegex()
        val rttMatch = rttRegex.find(output)
        val avgLatency = rttMatch?.groupValues?.get(1)?.toFloatOrNull()

        // Parse TTL from first reply: "ttl=64"
        val ttlRegex = """ttl=(\d+)""".toRegex()
        val ttl = ttlRegex.find(output)?.groupValues?.get(1)?.toIntOrNull()

        return PingResult(
            host = host,
            latencyMs = avgLatency,
            isReachable = packetLoss < 1f,
            packetLoss = packetLoss,
            ttl = ttl
        )
    }

    private fun intToIp(ip: Int): String {
        return "${ip and 0xFF}.${ip shr 8 and 0xFF}.${ip shr 16 and 0xFF}.${ip shr 24 and 0xFF}"
    }
}
