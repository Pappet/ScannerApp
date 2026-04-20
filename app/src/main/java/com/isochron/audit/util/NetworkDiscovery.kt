package com.scanner.app.util

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.time.Instant

/**
 * Represents a device discovered on the Local Area Network (LAN).
 */
data class LanDevice(
    val ip: String,
    val mac: String?,
    val hostname: String?,
    val vendor: String?,
    val isGateway: Boolean = false,
    val isOwnDevice: Boolean = false,
    val latencyMs: Float? = null,
    val discoveredVia: DiscoveryMethod,
    val services: List<LanService> = emptyList(),
    val firstSeen: Instant = Instant.now(),
    val upnpInfo: UpnpDeviceInfo? = null
)

/**
 * Detailed information about a device discovered via UPnP/SSDP.
 */
data class UpnpDeviceInfo(
    val friendlyName: String?,
    val manufacturer: String?,
    val modelName: String?,
    val modelDescription: String?,
    val deviceType: String?,
    val services: List<String> = emptyList()
)

/**
 * Represents a service (e.g., HTTP, SSH) discovered on a LAN device.
 */
data class LanService(
    val name: String,
    val type: String,
    val port: Int,
    val host: String?
)

/**
 * Methods used to discover devices on the network.
 */
enum class DiscoveryMethod {
    ARP,
    PING_SWEEP,
    MDNS,
    SSDP;

    fun displayName(): String = when (this) {
        ARP -> "ARP"
        PING_SWEEP -> "Ping"
        MDNS -> "mDNS"
        SSDP -> "UPnP/SSDP"
    }
}

/**
 * UI state for tracking the progress of a LAN scan.
 */
data class LanScanProgress(
    val phase: String,
    val current: Int,
    val total: Int,
    val devicesFound: Int
)

/**
 * Core engine for discovering devices on the local network.
 * Employs multiple techniques: ARP table reading, ICMP ping sweeps, mDNS (Bonjour), and SSDP (UPnP).
 */
class NetworkDiscovery(private val context: Context) {

    private val pingUtil = PingUtil(context)
    private val discoveredDevices = mutableMapOf<String, LanDevice>()
    private var nsdManager: NsdManager? = null
    private val activeListeners = mutableListOf<NsdManager.DiscoveryListener>()

    /**
     * Performs a comprehensive multi-phase LAN scan.
     * Phases include: ARP table analysis, subnet ping sweep, NetBIOS resolution, mDNS discovery, and UPnP device lookup.
     *
     * @param onProgress Callback for UI progress updates.
     * @param onDeviceFound Callback invoked whenever new devices are discovered or enriched with metadata.
     * @return The final list of all discovered [LanDevice]s.
     */
    suspend fun fullScan(
        onProgress: (LanScanProgress) -> Unit,
        onDeviceFound: (List<LanDevice>) -> Unit
    ): List<LanDevice> = withContext(Dispatchers.IO) {
        discoveredDevices.clear()

        try {
            val networkInfo = pingUtil.getNetworkInfo()

            // Phase 1: Read ARP table (instant)
            onProgress(LanScanProgress("ARP-Tabelle lesen...", 0, 4, 0))
            readArpTable(networkInfo)
            onDeviceFound(getDeviceList())

            // Phase 2: Ping sweep
            val subnet = networkInfo.gatewayIp?.let { extractSubnet(it) }
            if (subnet != null) {
                pingSweep(subnet, onProgress, onDeviceFound)
                // Small delay — gives the kernel time to populate ARP entries after pings
                delay(500)
                // Re-read ARP/neighbor table — pings create new ARP entries
                readArpTable(networkInfo)
                onDeviceFound(getDeviceList())
            }

            // Phase 3: NetBIOS name resolution
            onProgress(LanScanProgress("NetBIOS-Namen auflösen...", 3, 4, discoveredDevices.size))
            resolveNetBiosNames()
            onDeviceFound(getDeviceList())

            // Phase 4: mDNS discovery
            onProgress(LanScanProgress("mDNS-Dienste suchen...", 4, 5, discoveredDevices.size))
            try {
                discoverMdnsServices(onDeviceFound)
            } catch (e: Exception) {
                Log.e("NetworkDiscovery", "mDNS error", e)
            }

            // Phase 5: UPnP/SSDP discovery
            onProgress(LanScanProgress("UPnP-Geräte suchen...", 5, 5, discoveredDevices.size))
            try {
                discoverUpnpDevices()
                onDeviceFound(getDeviceList())
            } catch (e: Exception) {
                Log.e("NetworkDiscovery", "UPnP error", e)
            }

            // Mark gateway and own device
            markSpecialDevices(networkInfo)
            onDeviceFound(getDeviceList())
        } catch (e: Exception) {
            Log.e("NetworkDiscovery", "Error in fullScan", e)
        }

        getDeviceList()
    }

    /**
     * Stops any ongoing asynchronous discovery processes (e.g., mDNS listeners).
     */
    fun stopScan() {
        activeListeners.forEach { listener ->
            try {
                nsdManager?.stopServiceDiscovery(listener)
            } catch (_: Exception) {}
        }
        activeListeners.clear()
    }

    /**
     * Populates the [discoveredDevices] map by reading the system's neighbor table (ARP cache).
     * Attempts multiple techniques to bypass OS-level restrictions on `/proc/net/arp`.
     */
    private fun readArpTable(networkInfo: NetworkInfo) {
        val macMap = mutableMapOf<String, String>() // ip → mac

        // Method 1: Direct read from /proc/net/arp (Modern Android usually restricts this)
        try {
            val reader = BufferedReader(InputStreamReader(
                java.io.FileInputStream("/proc/net/arp")
            ))
            val lines = reader.readLines()
            reader.close()

            for (line in lines.drop(1)) {
                val parts = line.trim().split("\\s+".toRegex())
                if (parts.size >= 4) {
                    val ip = parts[0]
                    val mac = parts[3].uppercase()
                    if (mac != "00:00:00:00:00:00" && mac.contains(":")) {
                        macMap[ip] = mac
                    }
                }
            }
        } catch (e: Exception) {
            Log.d("NetworkDiscovery", "/proc/net/arp not accessible: ${e.message}")
        }

        // Method 2: `ip neigh show` — works on Android 10+ where /proc/net/arp is blocked
        if (macMap.isEmpty() || macMap.size < discoveredDevices.size / 2) {
            try {
                val process = Runtime.getRuntime().exec("ip neigh show")
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val lines = reader.readLines()
                reader.close()
                process.waitFor()

                // Format: "10.1.1.1 dev wlan0 lladdr aa:bb:cc:dd:ee:ff REACHABLE"
                for (line in lines) {
                    val parts = line.trim().split("\\s+".toRegex())
                    val ip = parts.getOrNull(0) ?: continue
                    val lladdrIdx = parts.indexOf("lladdr")
                    if (lladdrIdx >= 0 && lladdrIdx + 1 < parts.size) {
                        val mac = parts[lladdrIdx + 1].uppercase()
                        if (mac != "00:00:00:00:00:00" && mac.contains(":")) {
                            macMap.putIfAbsent(ip, mac)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d("NetworkDiscovery", "ip neigh not available: ${e.message}")
            }
        }

        // Method 3: Shell cat (last resort — sometimes file read is blocked but shell cat works)
        if (macMap.isEmpty()) {
            try {
                val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", "cat /proc/net/arp"))
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val lines = reader.readLines()
                reader.close()
                process.waitFor()

                for (line in lines.drop(1)) {
                    val parts = line.trim().split("\\s+".toRegex())
                    if (parts.size >= 4) {
                        val ip = parts[0]
                        val mac = parts[3].uppercase()
                        if (mac != "00:00:00:00:00:00" && mac.contains(":")) {
                            macMap.putIfAbsent(ip, mac)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d("NetworkDiscovery", "Shell cat /proc/net/arp failed: ${e.message}")
            }
        }

        Log.d("NetworkDiscovery", "ARP/Neighbor table: ${macMap.size} MAC addresses found")

        // Merge MAC data into discovered devices
        for ((ip, mac) in macMap) {
            val existing = discoveredDevices[ip]
            val vendor = MacVendorLookup.shortName(mac)

            discoveredDevices[ip] = LanDevice(
                ip = ip,
                mac = mac,
                hostname = existing?.hostname ?: resolveHostname(ip),
                vendor = vendor ?: existing?.vendor,
                discoveredVia = existing?.discoveredVia ?: DiscoveryMethod.ARP,
                services = existing?.services ?: emptyList(),
                isGateway = ip == networkInfo.gatewayIp,
                isOwnDevice = ip == networkInfo.deviceIp,
                latencyMs = existing?.latencyMs
            )
        }

        // Also enrich ping-only devices that now have a MAC
        for ((ip, device) in discoveredDevices.toMap()) {
            if (device.mac == null && macMap.containsKey(ip)) {
                val mac = macMap[ip]!!
                discoveredDevices[ip] = device.copy(
                    mac = mac,
                    vendor = MacVendorLookup.shortName(mac) ?: device.vendor
                )
            }
        }
    }

    /**
     * Scans the subnet using ICMP Echo Requests (pings) to find active hosts.
     * Processed in parallel batches for efficiency.
     */
    private suspend fun pingSweep(
        subnet: String,
        onProgress: (LanScanProgress) -> Unit,
        onDeviceFound: (List<LanDevice>) -> Unit
    ) = withContext(Dispatchers.IO) {
        val total = 254
        var scanned = 0

        // Parallel ping in batches of 20
        val batchSize = 20
        for (batchStart in 1..254 step batchSize) {
            val batchEnd = minOf(batchStart + batchSize - 1, 254)
            val jobs = (batchStart..batchEnd).map { i ->
                async {
                    val ip = "$subnet.$i"
                    try {
                        val reachable = InetAddress.getByName(ip).isReachable(800)
                        if (reachable) {
                            // Measure latency
                            val start = System.nanoTime()
                            InetAddress.getByName(ip).isReachable(1000)
                            val latency = (System.nanoTime() - start) / 1_000_000f

                            synchronized(discoveredDevices) {
                                val existing = discoveredDevices[ip]
                                if (existing == null) {
                                    discoveredDevices[ip] = LanDevice(
                                        ip = ip,
                                        mac = null,
                                        hostname = resolveHostname(ip),
                                        vendor = null,
                                        discoveredVia = DiscoveryMethod.PING_SWEEP,
                                        latencyMs = latency
                                    )
                                } else {
                                    discoveredDevices[ip] = existing.copy(
                                        latencyMs = latency
                                    )
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }
            }

            jobs.awaitAll()
            scanned += (batchEnd - batchStart + 1)

            onProgress(LanScanProgress(
                "Ping-Sweep: $subnet.$batchStart-$batchEnd",
                scanned, total,
                discoveredDevices.size
            ))
            onDeviceFound(getDeviceList())
        }
    }

    /**
     * Discovers services via mDNS (multicast DNS) using [NsdManager].
     * Listens for common service types like HTTP, SMB, and specialized protocols (Google Cast, AirPlay).
     */
    private suspend fun discoverMdnsServices(
        onDeviceFound: (List<LanDevice>) -> Unit
    ) = withContext(Dispatchers.Main) {
        nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return@withContext

        val serviceTypes = listOf(
            "_http._tcp.",
            "_https._tcp.",
            "_printer._tcp.",
            "_ipp._tcp.",
            "_smb._tcp.",
            "_ssh._tcp.",
            "_ftp._tcp.",
            "_airplay._tcp.",
            "_raop._tcp.",
            "_googlecast._tcp.",
            "_spotify-connect._tcp.",
            "_homekit._tcp.",
        )

        for (serviceType in serviceTypes) {
            try {
                val listener = createDiscoveryListener(serviceType, onDeviceFound)
                activeListeners.add(listener)
                nsdManager?.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
            } catch (e: Exception) {
                Log.w("NetworkDiscovery", "mDNS discovery failed for $serviceType", e)
            }
        }

        // Let mDNS discovery run for a few seconds
        delay(4000)

        stopScan()
    }

    private fun createDiscoveryListener(
        serviceType: String,
        onDeviceFound: (List<LanDevice>) -> Unit
    ): NsdManager.DiscoveryListener {
        return object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                // Resolve the service to get host/port
                nsdManager?.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(si: NsdServiceInfo, errorCode: Int) {}

                    override fun onServiceResolved(si: NsdServiceInfo) {
                        val host = si.host?.hostAddress ?: return
                        val service = LanService(
                            name = si.serviceName,
                            type = serviceType.removeSuffix("."),
                            port = si.port,
                            host = host
                        )

                        synchronized(discoveredDevices) {
                            val existing = discoveredDevices[host]
                            if (existing != null) {
                                discoveredDevices[host] = existing.copy(
                                    hostname = existing.hostname ?: si.serviceName,
                                    services = (existing.services + service).distinctBy { "${it.type}:${it.port}" }
                                )
                            } else {
                                discoveredDevices[host] = LanDevice(
                                    ip = host,
                                    mac = null,
                                    hostname = si.serviceName,
                                    vendor = null,
                                    discoveredVia = DiscoveryMethod.MDNS,
                                    services = listOf(service)
                                )
                            }
                        }
                        onDeviceFound(getDeviceList())
                    }
                })
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {}
        }
    }

    /**
     * Discovers UPnP devices via SSDP (Simple Service Discovery Protocol).
     * Sends M-SEARCH multicast packets and parses the XML description of responding devices.
     */
    private suspend fun discoverUpnpDevices() = withContext(Dispatchers.IO) {
        try {
            val ssdpAddr = InetAddress.getByName("239.255.255.250")
            val ssdpPort = 1900

            val msearch = ("M-SEARCH * HTTP/1.1\r\n" +
                    "HOST: 239.255.255.250:1900\r\n" +
                    "MAN: \"ssdp:discover\"\r\n" +
                    "MX: 3\r\n" +
                    "ST: ssdp:all\r\n" +
                    "\r\n").toByteArray()

            val socket = java.net.DatagramSocket()
            socket.soTimeout = 3000
            socket.broadcast = true

            // Send M-SEARCH
            val packet = java.net.DatagramPacket(msearch, msearch.size, ssdpAddr, ssdpPort)
            socket.send(packet)

            // Collect responses — map of IP → location URL
            val locationMap = mutableMapOf<String, String>()
            val buffer = ByteArray(4096)

            val deadline = System.currentTimeMillis() + 3000
            while (System.currentTimeMillis() < deadline) {
                try {
                    val response = java.net.DatagramPacket(buffer, buffer.size)
                    socket.receive(response)
                    val body = String(response.data, 0, response.length)
                    val ip = response.address.hostAddress ?: continue

                    // Extract LOCATION header
                    val locationLine = body.lines().find {
                        it.trim().startsWith("LOCATION:", ignoreCase = true)
                    }
                    val location = locationLine
                        ?.substringAfter(":", "")
                        ?.trim()

                    if (location != null && location.startsWith("http")) {
                        locationMap.putIfAbsent(ip, location)
                    }
                } catch (_: java.net.SocketTimeoutException) {
                    break
                } catch (_: Exception) {
                    continue
                }
            }
            socket.close()

            Log.d("NetworkDiscovery", "SSDP: ${locationMap.size} devices responded")

            // Fetch XML descriptions in parallel (max 10 at a time)
            val ips = locationMap.keys.toList()
            for (batchStart in ips.indices step 10) {
                val batch = ips.subList(batchStart, minOf(batchStart + 10, ips.size))
                val jobs = batch.map { ip ->
                    async {
                        val url = locationMap[ip] ?: return@async
                        try {
                            val upnpInfo = fetchUpnpDescription(url)
                            if (upnpInfo != null) {
                                synchronized(discoveredDevices) {
                                    val existing = discoveredDevices[ip]
                                    if (existing != null) {
                                        discoveredDevices[ip] = existing.copy(
                                            upnpInfo = upnpInfo,
                                            hostname = existing.hostname
                                                ?: upnpInfo.friendlyName
                                        )
                                    } else {
                                        discoveredDevices[ip] = LanDevice(
                                            ip = ip,
                                            mac = null,
                                            hostname = upnpInfo.friendlyName,
                                            vendor = upnpInfo.manufacturer,
                                            discoveredVia = DiscoveryMethod.SSDP,
                                            upnpInfo = upnpInfo
                                        )
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.w("NetworkDiscovery", "UPnP fetch failed for $url", e)
                        }
                    }
                }
                jobs.awaitAll()
            }
        } catch (e: Exception) {
            Log.e("NetworkDiscovery", "SSDP discovery error", e)
        }
    }

    /**
     * Fetch and parse a UPnP device description XML.
     */
    private fun fetchUpnpDescription(url: String): UpnpDeviceInfo? {
        return try {
            val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            connection.connectTimeout = 2000
            connection.readTimeout = 2000
            val xml = connection.inputStream.bufferedReader().readText()
            connection.disconnect()
            parseUpnpXml(xml)
        } catch (_: Exception) { null }
    }

    /**
     * Parse UPnP device description XML to extract device info.
     */
    private fun parseUpnpXml(xml: String): UpnpDeviceInfo? {
        try {
            fun extractTag(tag: String): String? {
                val pattern = "<$tag>([^<]*)</$tag>"
                val match = Regex(pattern, RegexOption.IGNORE_CASE).find(xml)
                return match?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
            }

            val friendlyName = extractTag("friendlyName")
            val manufacturer = extractTag("manufacturer")
            val modelName = extractTag("modelName")
            val modelDescription = extractTag("modelDescription")
            val deviceType = extractTag("deviceType")

            // Extract service types
            val serviceTypes = Regex("<serviceType>([^<]*)</serviceType>", RegexOption.IGNORE_CASE)
                .findAll(xml)
                .map { it.groupValues[1].trim() }
                .map { svc ->
                    // Shorten URN to readable name
                    svc.substringAfterLast(":")
                        .substringBefore(":")
                        .takeIf { it.isNotBlank() } ?: svc
                }
                .toList()

            if (friendlyName == null && manufacturer == null && modelName == null) return null

            return UpnpDeviceInfo(
                friendlyName = friendlyName,
                manufacturer = manufacturer,
                modelName = modelName,
                modelDescription = modelDescription,
                deviceType = deviceType?.substringAfterLast(":")?.substringBefore(":"),
                services = serviceTypes
            )
        } catch (_: Exception) { return null }
    }



    /**
     * Resolve NetBIOS names for all discovered devices via UDP port 137.
     */
    private suspend fun resolveNetBiosNames() = withContext(Dispatchers.IO) {
        val ips = discoveredDevices.keys.toList()

        // Parallel in batches of 10
        for (batchStart in ips.indices step 10) {
            val batch = ips.subList(batchStart, minOf(batchStart + 10, ips.size))
            val jobs = batch.map { ip ->
                async {
                    try {
                        val name = queryNetBiosName(ip)
                        if (name != null) {
                            synchronized(discoveredDevices) {
                                val existing = discoveredDevices[ip] ?: return@async
                                if (existing.hostname.isNullOrBlank() || existing.hostname == ip) {
                                    discoveredDevices[ip] = existing.copy(hostname = name)
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
            jobs.awaitAll()
        }
    }

    /**
     * Send a NetBIOS Node Status Request (NBSTAT) to port 137.
     * Returns the workstation name or null.
     */
    private fun queryNetBiosName(ip: String): String? {
        return try {
            val socket = java.net.DatagramSocket()
            socket.soTimeout = 1500

            // NetBIOS Node Status Request packet
            // Transaction ID (2) + Flags (2) + Questions (2) + Answers (2) + Authority (2) + Additional (2)
            // + Query: CKAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA (wildcard *) + Type (2) + Class (2)
            val query = byteArrayOf(
                0x00, 0x01,                   // Transaction ID
                0x00, 0x00,                   // Flags: query
                0x00, 0x01,                   // Questions: 1
                0x00, 0x00,                   // Answer RRs: 0
                0x00, 0x00,                   // Authority RRs: 0
                0x00, 0x00,                   // Additional RRs: 0
                // Query name: wildcard (*) encoded as NetBIOS
                0x20,                         // Name length: 32
                0x43, 0x4B, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41,
                0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41,
                0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41,
                0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41,
                0x00,                         // Name terminator
                0x00, 0x21,                   // Type: NBSTAT (33)
                0x00, 0x01                    // Class: IN
            )

            val address = InetAddress.getByName(ip)
            val packet = java.net.DatagramPacket(query, query.size, address, 137)
            socket.send(packet)

            val buffer = ByteArray(1024)
            val response = java.net.DatagramPacket(buffer, buffer.size)
            socket.receive(response)
            socket.close()

            parseNetBiosResponse(buffer, response.length)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Parse NetBIOS NBSTAT response to extract the workstation name.
     */
    private fun parseNetBiosResponse(data: ByteArray, length: Int): String? {
        if (length < 57) return null

        try {
            // Skip header (12 bytes) + query echo + answer header
            // The name table starts after the answer RR header
            val numNames = data[56].toInt() and 0xFF
            if (numNames <= 0) return null

            // First name entry starts at byte 57, each entry is 18 bytes (15 name + 1 type + 2 flags)
            val nameBytes = data.sliceArray(57 until minOf(57 + 15, length))
            val name = String(nameBytes, Charsets.US_ASCII).trim()

            return name.takeIf { it.isNotBlank() && it != "\u0000".repeat(15) }
        } catch (_: Exception) {
            return null
        }
    }

    private fun resolveHostname(ip: String): String? {
        return try {
            val addr = InetAddress.getByName(ip)
            val hostname = addr.canonicalHostName
            if (hostname != ip) hostname else null
        } catch (_: Exception) { null }
    }

    private fun extractSubnet(gatewayIp: String): String? {
        val parts = gatewayIp.split(".")
        return if (parts.size == 4) "${parts[0]}.${parts[1]}.${parts[2]}" else null
    }

    private fun markSpecialDevices(networkInfo: NetworkInfo) {
        networkInfo.gatewayIp?.let { gw ->
            discoveredDevices[gw]?.let {
                discoveredDevices[gw] = it.copy(isGateway = true)
            }
        }
        networkInfo.deviceIp?.let { own ->
            discoveredDevices[own]?.let {
                discoveredDevices[own] = it.copy(isOwnDevice = true)
            }
        }
    }

    private fun getDeviceList(): List<LanDevice> {
        return discoveredDevices.values.toList().sortedWith(
            compareByDescending<LanDevice> { it.isGateway }
                .thenByDescending { it.isOwnDevice }
                .thenByDescending { it.services.isNotEmpty() }
                .thenBy { ipSortKey(it.ip) }
        )
    }

    private fun ipSortKey(ip: String): Long {
        return try {
            ip.split(".").fold(0L) { acc, part -> acc * 256 + part.toLong() }
        } catch (_: Exception) { 0L }
    }
}
