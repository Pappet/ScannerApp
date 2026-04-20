package com.scanner.app.util

import android.util.Log
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Represents the result of a scan on a specific TCP port.
 */
data class PortScanResult(
    val ip: String,
    val port: Int,
    val state: PortState,
    val serviceName: String,        // Best guess: detected > well-known > "Port N"
    val banner: String? = null,
    val latencyMs: Float? = null,
    val detectedProtocol: DetectedProtocol = DetectedProtocol.UNKNOWN
)

/**
 * Connectivity state of a scanned port.
 */
enum class PortState(val label: String) {
    OPEN("Offen"),
    CLOSED("Geschlossen"),
    FILTERED("Gefiltert");
}

/**
 * Protocols identifiable via banner grabbing or port number hints.
 */
enum class DetectedProtocol(val label: String) {
    HTTP("HTTP"),
    HTTPS_LIKELY("HTTPS (vermutet)"),
    SSH("SSH"),
    FTP("FTP"),
    SMTP("SMTP"),
    IMAP("IMAP"),
    POP3("POP3"),
    MYSQL("MySQL"),
    REDIS("Redis"),
    MONGODB("MongoDB"),
    RTSP("RTSP"),
    MQTT("MQTT"),
    TELNET("Telnet"),
    DNS("DNS"),
    SMB("SMB"),
    VNC("VNC"),
    RDP("RDP"),
    UNKNOWN("Unbekannt");

    val isBrowsable: Boolean get() = this == HTTP
}

/**
 * UI state for tracking the progress of an ongoing port scan.
 */
data class PortScanProgress(
    val scanned: Int,
    val total: Int,
    val openPorts: Int,
    val currentPort: Int
)

/**
 * A database of well-known TCP ports and their associated services.
 * Provides lookup tables for common scan ranges and risk assessment.
 */
object WellKnownPorts {

    /** Top 50 most commonly scanned ports */
    val TOP_50 = listOf(
        21, 22, 23, 25, 53, 80, 110, 111, 135, 139,
        143, 443, 445, 993, 995, 1433, 1521, 1723, 3306, 3389,
        5432, 5900, 5901, 6379, 8000, 8008, 8080, 8443, 8888, 9090,
        9200, 9443, 27017, 554, 1883, 5353, 8123, 8181,
        62078, 49152, 515, 631, 9100, 548, 2049, 5060, 1900, 10000,
        3000, 5672
    ).distinct().sorted()

    /** Top 200 ports — covers most real-world services */
    val TOP_200 = (TOP_50 + listOf(
        20, 67, 68, 69, 81, 88, 102, 106, 113, 119, 123, 137, 138,
        161, 162, 179, 194, 389, 464, 465, 500, 514, 515, 520, 521,
        543, 544, 546, 547, 587, 593, 636, 691, 860, 873, 902, 989,
        990, 992, 993, 995, 1025, 1080, 1194, 1214, 1241, 1311, 1337,
        1434, 1512, 1589, 1701, 1720, 1812, 1813, 1863, 1985, 2000,
        2049, 2082, 2083, 2086, 2087, 2095, 2096, 2181, 2222, 2375,
        2376, 2483, 2484, 3128, 3268, 3269, 3372, 3690, 4000, 4040,
        4443, 4444, 4567, 4711, 4712, 4848, 4993, 5000, 5001, 5050,
        5060, 5222, 5269, 5280, 5357, 5632, 5666, 5800, 5938, 5984,
        5985, 5986, 6000, 6001, 6443, 6660, 6661, 6662, 6663, 6664,
        6665, 6666, 6667, 6668, 6669, 7000, 7001, 7002, 7070, 7396,
        7443, 7474, 7547, 7777, 7778, 8000, 8001, 8002, 8010, 8042,
        8069, 8081, 8082, 8083, 8084, 8085, 8086, 8087, 8088, 8089,
        8090, 8091, 8100, 8200, 8222, 8280, 8281, 8333, 8400, 8500,
        8834, 8880, 8887, 8983, 9000, 9001, 9042, 9043, 9060, 9080,
        9091, 9100, 9160, 9300, 9418, 9999, 10001, 10010, 10243,
        11211, 11311, 12345, 15672, 16080, 18080, 20000, 27018, 28017,
        32400, 33060, 44818, 47808, 50000, 50070, 61616
    )).distinct().sorted()

    /** Full port range 1-65535 */
    val ALL_PORTS = (1..65535).toList()

    /** Quick scan: most critical ports */
    val QUICK_20 = listOf(
        21, 22, 23, 25, 53, 80, 110, 139, 143, 443,
        445, 993, 3306, 3389, 5432, 5900, 8080, 8443, 9090, 62078
    )

    /** Service name lookup */
    fun serviceName(port: Int): String = when (port) {
        20 -> "FTP-Data"
        21 -> "FTP"
        22 -> "SSH"
        23 -> "Telnet"
        25 -> "SMTP"
        53 -> "DNS"
        67 -> "DHCP"
        68 -> "DHCP"
        80 -> "HTTP"
        110 -> "POP3"
        111 -> "RPC"
        123 -> "NTP"
        135 -> "MS-RPC"
        137 -> "NetBIOS"
        138 -> "NetBIOS"
        139 -> "NetBIOS/SMB"
        143 -> "IMAP"
        161 -> "SNMP"
        162 -> "SNMP-Trap"
        389 -> "LDAP"
        443 -> "HTTPS"
        445 -> "SMB"
        465 -> "SMTPS"
        514 -> "Syslog"
        515 -> "LPD (Drucker)"
        548 -> "AFP"
        554 -> "RTSP"
        587 -> "SMTP (Submission)"
        631 -> "IPP (Drucker)"
        636 -> "LDAPS"
        873 -> "Rsync"
        993 -> "IMAPS"
        995 -> "POP3S"
        1080 -> "SOCKS"
        1433 -> "MS-SQL"
        1521 -> "Oracle DB"
        1723 -> "PPTP VPN"
        1883 -> "MQTT"
        1900 -> "UPnP/SSDP"
        2049 -> "NFS"
        2181 -> "ZooKeeper"
        3000 -> "Dev-Server"
        3306 -> "MySQL"
        3389 -> "RDP"
        4443 -> "Pharos"
        5060 -> "SIP"
        5222 -> "XMPP"
        5353 -> "mDNS"
        5432 -> "PostgreSQL"
        5672 -> "AMQP"
        5900 -> "VNC"
        5901 -> "VNC-1"
        6379 -> "Redis"
        6667 -> "IRC"
        8000 -> "HTTP-Alt"
        8008 -> "HTTP-Alt"
        8080 -> "HTTP-Proxy"
        8123 -> "Home Assistant"
        8181 -> "HTTP-Alt"
        8443 -> "HTTPS-Alt"
        8883 -> "MQTT-TLS"
        8888 -> "HTTP-Alt"
        9090 -> "Web-Management"
        9100 -> "JetDirect (Drucker)"
        9200 -> "Elasticsearch"
        9443 -> "HTTPS-Alt"
        10000 -> "Webmin"
        27017 -> "MongoDB"
        49152 -> "Dynamic"
        62078 -> "iPhone-Sync"
        else -> "Port $port"
    }

    /**
     * Generates a browsable URL (http/https) based on the detected protocol and port.
     * Use this for ports identified as web services.
     */
    fun browseUrl(result: PortScanResult): String? {
        val ip = result.ip
        val port = result.port

        // Protocol-detected HTTP — works on ANY port
        if (result.detectedProtocol == DetectedProtocol.HTTP) {
            return if (port == 443 || port == 8443 || port == 9443) {
                "https://$ip" + if (port != 443) ":$port" else ""
            } else {
                "http://$ip" + if (port != 80) ":$port" else ""
            }
        }

        // HTTPS likely (TLS handshake detected or well-known HTTPS port)
        if (result.detectedProtocol == DetectedProtocol.HTTPS_LIKELY) {
            return "https://$ip" + if (port != 443) ":$port" else ""
        }

        // Fallback: well-known HTTP ports even without banner detection
        return when (port) {
            80 -> "http://$ip"
            443 -> "https://$ip"
            8080 -> "http://$ip:8080"
            8443 -> "https://$ip:8443"
            else -> null
        }
    }

    /**
     * Legacy overload for port-only lookup (used in inventory metadata).
     */
    fun browseUrl(ip: String, port: Int): String? {
        return browseUrl(PortScanResult(
            ip = ip, port = port, state = PortState.OPEN,
            serviceName = serviceName(port)
        ))
    }

    /** Performs a risk assessment for historically vulnerable or sensitive ports. */
    fun riskLevel(port: Int): PortRisk = when (port) {
        23 -> PortRisk.CRITICAL     // Telnet — unverschlüsselt
        21 -> PortRisk.HIGH         // FTP — oft unverschlüsselt
        25 -> PortRisk.MEDIUM       // SMTP — kann für Spam missbraucht werden
        80 -> PortRisk.LOW          // HTTP — normal, aber unverschlüsselt
        110 -> PortRisk.HIGH        // POP3 — Klartext-Passwörter
        135 -> PortRisk.HIGH        // MS-RPC — häufig exploited
        139 -> PortRisk.HIGH        // NetBIOS — Informationsleck
        143 -> PortRisk.MEDIUM      // IMAP — oft unverschlüsselt
        161 -> PortRisk.HIGH        // SNMP — community strings
        445 -> PortRisk.HIGH        // SMB — EternalBlue etc.
        514 -> PortRisk.MEDIUM      // Syslog — Informationsleck
        1433 -> PortRisk.HIGH       // MS-SQL — DB exponiert
        1521 -> PortRisk.HIGH       // Oracle — DB exponiert
        1883 -> PortRisk.MEDIUM     // MQTT — oft ohne Auth
        3306 -> PortRisk.HIGH       // MySQL — DB exponiert
        3389 -> PortRisk.HIGH       // RDP — Brute-Force Ziel
        5432 -> PortRisk.HIGH       // PostgreSQL — DB exponiert
        5900 -> PortRisk.HIGH       // VNC — oft schwach gesichert
        6379 -> PortRisk.CRITICAL   // Redis — default ohne Auth
        8080 -> PortRisk.LOW        // HTTP-Proxy — normal
        9200 -> PortRisk.HIGH       // Elasticsearch — oft ohne Auth
        27017 -> PortRisk.CRITICAL  // MongoDB — default ohne Auth
        else -> PortRisk.INFO
    }
}

enum class PortRisk(val label: String, val score: Int) {
    CRITICAL("Kritisch", 10),
    HIGH("Hoch", 7),
    MEDIUM("Mittel", 4),
    LOW("Niedrig", 2),
    INFO("Info", 0)
}

/**
 * A robust TCP port scanner with service identification (banner grabbing).
 * Employs adaptive strategies based on port count to optimize for speed and overhead.
 */
class PortScanner {

    companion object {
        private const val TAG = "PortScanner"
    }

    /**
     * Scans a host for open ports and identifies services.
     *
     * @param ip Target IP address.
     * @param ports List of ports to scan.
     * @param grabBanners If true, attempts to read service banners from open ports.
     * @param onProgress Callback for scan progress updates.
     * @param onPortFound Callback invoked immediately for each open port found.
     * @return List of discovered open ports with service metadata.
     */
    suspend fun scan(
        ip: String,
        ports: List<Int> = WellKnownPorts.QUICK_20,
        grabBanners: Boolean = true,
        onProgress: (PortScanProgress) -> Unit = {},
        onPortFound: (PortScanResult) -> Unit = {}
    ): List<PortScanResult> = withContext(Dispatchers.IO) {

        val totalPorts = ports.size
        val isLargeScan = totalPorts > 200

        // Adaptive timeouts + batch sizes
        val connectTimeout = when {
            totalPorts > 10000 -> 300
            totalPorts > 1000 -> 500
            totalPorts > 200 -> 800
            else -> 1500
        }
        val batchSize = when {
            totalPorts > 10000 -> 100
            totalPorts > 1000 -> 60
            totalPorts > 200 -> 30
            else -> 15
        }

        if (isLargeScan) {
            twoPassScan(ip, ports, connectTimeout, batchSize, onProgress, onPortFound)
        } else {
            singlePassScan(ip, ports, connectTimeout, batchSize, grabBanners, onProgress, onPortFound)
        }
    }

    /**
     * Single pass: full probe per port. For ≤200 ports.
     */
    private suspend fun singlePassScan(
        ip: String, ports: List<Int>, connectTimeout: Int, batchSize: Int,
        grabBanners: Boolean,
        onProgress: (PortScanProgress) -> Unit,
        onPortFound: (PortScanResult) -> Unit
    ): List<PortScanResult> = coroutineScope {
        val results = mutableListOf<PortScanResult>()
        var scanned = 0
        var openCount = 0

        for (batchStart in ports.indices step batchSize) {
            val batch = ports.subList(batchStart, minOf(batchStart + batchSize, ports.size))
            val jobs = batch.map { port -> async { scanPortFull(ip, port, connectTimeout, grabBanners) } }
            for (result in jobs.awaitAll()) {
                if (result.state == PortState.OPEN) { openCount++; onPortFound(result) }
                results.add(result)
                scanned++
            }
            onProgress(PortScanProgress(scanned, ports.size, openCount, batch.last()))
        }

        results.filter { it.state == PortState.OPEN }.sortedBy { it.port }
    }

    /**
     * Two pass: fast connect scan → banner grab on open ports only. For >200 ports.
     */
    private suspend fun twoPassScan(
        ip: String, ports: List<Int>, connectTimeout: Int, batchSize: Int,
        onProgress: (PortScanProgress) -> Unit,
        onPortFound: (PortScanResult) -> Unit
    ): List<PortScanResult> = coroutineScope {
        // Pass 1: connect-only
        val openPorts = mutableListOf<Int>()
        var scanned = 0

        for (batchStart in ports.indices step batchSize) {
            val batch = ports.subList(batchStart, minOf(batchStart + batchSize, ports.size))
            val jobs = batch.map { port -> async { quickConnect(ip, port, connectTimeout) } }
            for ((port, isOpen) in jobs.awaitAll()) {
                if (isOpen) openPorts.add(port)
                scanned++
            }
            onProgress(PortScanProgress(scanned, ports.size, openPorts.size, batch.last()))
        }

        if (openPorts.isEmpty()) return@coroutineScope emptyList()

        // Pass 2: full probe on open ports
        val results = mutableListOf<PortScanResult>()
        for (batchStart in openPorts.indices step 10) {
            val batch = openPorts.subList(batchStart, minOf(batchStart + 10, openPorts.size))
            val jobs = batch.map { port -> async { scanPortFull(ip, port, 1500, true) } }
            for (result in jobs.awaitAll()) {
                if (result.state == PortState.OPEN) { results.add(result); onPortFound(result) }
            }
            onProgress(PortScanProgress(ports.size, ports.size, results.size, batch.last()))
        }

        results.sortedBy { it.port }
    }

    private suspend fun quickConnect(ip: String, port: Int, timeoutMs: Int): Pair<Int, Boolean> =
        withContext(Dispatchers.IO) {
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(ip, port), timeoutMs)
                socket.close()
                port to true
            } catch (_: Exception) {
                port to false
            }
        }

    /**
     * Connects to a port and performs multi-stage service identification.
     * 1. Passive banner grab (waiting for one-way protocol greetings like SSH/FTP).
     * 2. Active HTTP probe (sending a HEAD request).
     * 3. Fallback to well-known port aliases.
     */
    private suspend fun scanPortFull(
        ip: String,
        port: Int,
        connectTimeoutMs: Int = 1500,
        grabBanner: Boolean = true
    ): PortScanResult = withContext(Dispatchers.IO) {
        val wellKnownName = WellKnownPorts.serviceName(port)
        val bannerTimeoutMs = 2000
        var socket: java.net.Socket? = null

        try {
            socket = java.net.Socket()
            val startTime = System.nanoTime()

            socket.connect(
                java.net.InetSocketAddress(ip, port),
                connectTimeoutMs
            )

            val latency = (System.nanoTime() - startTime) / 1_000_000f

            var banner: String? = null
            var detectedProtocol = DetectedProtocol.UNKNOWN

            if (grabBanner) {
                try {
                    socket.soTimeout = bannerTimeoutMs

                    val inputStream = socket.getInputStream()
                    val outputStream = socket.getOutputStream()

                    val passiveBanner = try {
                        socket.soTimeout = 1500
                        val reader = java.io.BufferedReader(java.io.InputStreamReader(inputStream))
                    if (inputStream.available() > 0 || waitForData(inputStream, 1200)) {
                            reader.readLine()?.take(300)
                        } else null
                    } catch (_: Exception) { null }

                    if (passiveBanner != null) {
                        banner = passiveBanner
                        detectedProtocol = detectProtocolFromBanner(passiveBanner)
                    }

                    if (banner == null || detectedProtocol == DetectedProtocol.UNKNOWN) {
                        try {
                            val httpProbe = "HEAD / HTTP/1.0\r\nHost: $ip\r\nConnection: close\r\n\r\n"
                            outputStream.write(httpProbe.toByteArray())
                            outputStream.flush()
                            
                            val reader = java.io.BufferedReader(java.io.InputStreamReader(inputStream))
                            val response = java.lang.StringBuilder()
                            var linesRead = 0
                            
                            while (linesRead < 10) {
                                val line = reader.readLine() ?: break
                                response.appendLine(line)
                                linesRead++
                                if (line.isBlank()) break
                            }
                            
                            if (response.isNotEmpty()) {
                                banner = response.toString().trim()
                                detectedProtocol = detectProtocolFromBanner(banner)
                            }
                        } catch (_: Exception) {}
                    }
                } catch (_: Exception) {
                    detectedProtocol = detectFromPortHint(port)
                }
            }

            PortScanResult(
                ip = ip,
                port = port,
                state = PortState.OPEN,
                serviceName = wellKnownName,
                banner = banner,
                detectedProtocol = detectedProtocol,
                latencyMs = latency
            )

        } catch (_: java.net.ConnectException) {
            PortScanResult(ip = ip, port = port, state = PortState.CLOSED, serviceName = wellKnownName)
        } catch (_: java.net.SocketTimeoutException) {
            PortScanResult(ip = ip, port = port, state = PortState.FILTERED, serviceName = wellKnownName)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            android.util.Log.w(TAG, "Error scanning $ip:$port", e)
            PortScanResult(ip = ip, port = port, state = PortState.CLOSED, serviceName = wellKnownName)
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }
    }

    /**
     * Wait for data to become available on the input stream.
     * Implemented as a suspend function so it yields the IO thread during the polling
     * interval instead of blocking it — prevents Dispatcher-Starvation on large scans.
     */
    private suspend fun waitForData(inputStream: java.io.InputStream, timeoutMs: Int): Boolean {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            if (inputStream.available() > 0) return true
            delay(50) // suspend instead of blocking the IO thread
        }
        return false
    }

    /**
     * Detect protocol from banner content.
     */
    private fun detectProtocolFromBanner(banner: String): DetectedProtocol {
        val upper = banner.uppercase()
        val trimmed = banner.trim()

        return when {
            // HTTP responses
            trimmed.startsWith("HTTP/") -> DetectedProtocol.HTTP
            upper.contains("<!DOCTYPE HTML") -> DetectedProtocol.HTTP
            upper.contains("<HTML") -> DetectedProtocol.HTTP
            upper.contains("HTTP/1.") -> DetectedProtocol.HTTP
            upper.contains("HTTP/2") -> DetectedProtocol.HTTP
            upper.contains("SERVER:") && upper.contains("CONTENT") -> DetectedProtocol.HTTP
            upper.contains("X-POWERED-BY:") -> DetectedProtocol.HTTP

            // SSH
            trimmed.startsWith("SSH-") -> DetectedProtocol.SSH

            // FTP
            trimmed.startsWith("220") && (upper.contains("FTP") || upper.contains("FILEZILLA") ||
                    upper.contains("VSFTPD") || upper.contains("PROFTPD")) -> DetectedProtocol.FTP
            trimmed.startsWith("220-") -> DetectedProtocol.FTP

            // SMTP
            trimmed.startsWith("220") && (upper.contains("SMTP") || upper.contains("ESMTP") ||
                    upper.contains("POSTFIX") || upper.contains("MAIL")) -> DetectedProtocol.SMTP

            // IMAP
            upper.contains("* OK") && upper.contains("IMAP") -> DetectedProtocol.IMAP
            trimmed.startsWith("* OK") && upper.contains("READY") -> DetectedProtocol.IMAP

            // POP3
            trimmed.startsWith("+OK") -> DetectedProtocol.POP3

            // MySQL
            upper.contains("MYSQL") -> DetectedProtocol.MYSQL
            banner.length > 4 && banner[0].code == 0 && banner.contains("mysql", ignoreCase = true) ->
                DetectedProtocol.MYSQL

            // Redis
            trimmed.startsWith("-ERR") || trimmed.startsWith("+PONG") ||
                    trimmed.startsWith("-DENIED") || upper.contains("REDIS") -> DetectedProtocol.REDIS

            // MongoDB
            upper.contains("MONGODB") || upper.contains("MONGOD") -> DetectedProtocol.MONGODB

            // RTSP
            trimmed.startsWith("RTSP/") -> DetectedProtocol.RTSP

            // Telnet
            // Telnet negotiation starts with IAC (0xFF) sequences
            banner.isNotEmpty() && banner[0].code == 255 -> DetectedProtocol.TELNET

            // VNC
            trimmed.startsWith("RFB ") -> DetectedProtocol.VNC

            else -> DetectedProtocol.UNKNOWN
        }
    }

    /**
     * Fallback: guess protocol from well-known port number.
     */
    private fun detectFromPortHint(port: Int): DetectedProtocol = when (port) {
        22 -> DetectedProtocol.SSH
        23 -> DetectedProtocol.TELNET
        25, 465, 587 -> DetectedProtocol.SMTP
        53 -> DetectedProtocol.DNS
        80, 8080, 8000, 8008, 8888, 3000, 9090 -> DetectedProtocol.HTTP
        110, 995 -> DetectedProtocol.POP3
        143, 993 -> DetectedProtocol.IMAP
        443, 8443, 9443 -> DetectedProtocol.HTTPS_LIKELY
        445, 139 -> DetectedProtocol.SMB
        554 -> DetectedProtocol.RTSP
        1883 -> DetectedProtocol.MQTT
        3306 -> DetectedProtocol.MYSQL
        3389 -> DetectedProtocol.RDP
        5900, 5901 -> DetectedProtocol.VNC
        6379 -> DetectedProtocol.REDIS
        27017 -> DetectedProtocol.MONGODB
        else -> DetectedProtocol.UNKNOWN
    }
}
