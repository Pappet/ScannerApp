package com.scanner.app.util

import com.scanner.app.data.WifiNetwork
import com.scanner.app.data.BluetoothDevice
import com.scanner.app.data.BondState

/**
 * Represents a specific security vulnerability or observation.
 */
data class SecurityFinding(
    val severity: FindingSeverity,
    val category: FindingCategory,
    val title: String,
    val description: String,
    val target: String,           // IP, MAC, SSID
    val recommendation: String
)

/**
 * Severity levels for [SecurityFinding]s, including risk score and UI color.
 */
enum class FindingSeverity(val label: String, val score: Int, val color: Long) {
    CRITICAL("Kritisch", 10, 0xFFD32F2F),
    HIGH("Hoch", 7, 0xFFE64A19),
    MEDIUM("Mittel", 4, 0xFFF57C00),
    LOW("Niedrig", 2, 0xFFFBC02D),
    INFO("Info", 0, 0xFF42A5F5)
}

/**
 * Categories for grouping [SecurityFinding]s in the audit report.
 */
enum class FindingCategory(val label: String) {
    WIFI("WLAN"),
    BLUETOOTH("Bluetooth"),
    NETWORK("Netzwerk"),
    PORTS("Ports & Dienste"),
    ENCRYPTION("Verschlüsselung")
}

/**
 * Summary report of a completed security audit, containing scores and findings.
 */
data class SecurityAuditReport(
    val overallScore: Int,               // 0 (unsicher) - 100 (sicher)
    val grade: String,                   // A, B, C, D, F
    val findings: List<SecurityFinding>,
    val criticalCount: Int,
    val highCount: Int,
    val mediumCount: Int,
    val lowCount: Int,
    val infoCount: Int,
    val wifiFindings: List<SecurityFinding>,
    val btFindings: List<SecurityFinding>,
    val portFindings: List<SecurityFinding>,
    val networkFindings: List<SecurityFinding>,
    val auditedDevices: Int,
    val auditedNetworks: Int
)

/**
 * Analyzes discovered networks and devices for security risks and vulnerabilities.
 * Implements heuristics for WiFi encryption, Bluetooth visibility, and open port risks.
 */
object SecurityAuditor {

    /**
     * Executes a comprehensive security audit on the provided network and device data.
     * Starts with a maximum score of 100 and deducts based on found vulnerabilities.
     *
     * @param wifiNetworks List of discovered WiFi networks.
     * @param btDevices List of discovered Bluetooth devices.
     * @param openPorts List of discovered open TCP ports.
     * @param connectedSsid Currently connected SSID if applicable.
     * @return A [SecurityAuditReport] with summarized findings and a letter grade.
     */
    fun audit(
        wifiNetworks: List<WifiNetwork> = emptyList(),
        btDevices: List<BluetoothDevice> = emptyList(),
        openPorts: List<PortScanResult> = emptyList(),
        connectedSsid: String? = null
    ): SecurityAuditReport {
        val findings = mutableListOf<SecurityFinding>()

        // WiFi checks
        findings.addAll(auditWifi(wifiNetworks, connectedSsid))

        // Bluetooth checks
        findings.addAll(auditBluetooth(btDevices))

        // Port/Service checks
        findings.addAll(auditPorts(openPorts))

        // Sort by severity
        findings.sortByDescending { it.severity.score }

        val criticalCount = findings.count { it.severity == FindingSeverity.CRITICAL }
        val highCount = findings.count { it.severity == FindingSeverity.HIGH }
        val mediumCount = findings.count { it.severity == FindingSeverity.MEDIUM }
        val lowCount = findings.count { it.severity == FindingSeverity.LOW }
        val infoCount = findings.count { it.severity == FindingSeverity.INFO }

        // Calculate score: start at 100, deduct for findings
        val deductions = criticalCount * 15 + highCount * 10 + mediumCount * 5 + lowCount * 2
        val score = (100 - deductions).coerceIn(0, 100)

        val grade = when {
            score >= 90 -> "A"
            score >= 75 -> "B"
            score >= 60 -> "C"
            score >= 40 -> "D"
            else -> "F"
        }

        return SecurityAuditReport(
            overallScore = score,
            grade = grade,
            findings = findings,
            criticalCount = criticalCount,
            highCount = highCount,
            mediumCount = mediumCount,
            lowCount = lowCount,
            infoCount = infoCount,
            wifiFindings = findings.filter { it.category == FindingCategory.WIFI },
            btFindings = findings.filter { it.category == FindingCategory.BLUETOOTH },
            portFindings = findings.filter { it.category == FindingCategory.PORTS },
            networkFindings = findings.filter { it.category == FindingCategory.NETWORK },
            auditedDevices = btDevices.size + openPorts.map { it.ip }.distinct().size,
            auditedNetworks = wifiNetworks.size
        )
    }



    private fun auditWifi(
        networks: List<WifiNetwork>,
        @Suppress("UNUSED_PARAMETER") connectedSsid: String?
    ): List<SecurityFinding> {
        val findings = mutableListOf<SecurityFinding>()

        for (network in networks) {
            val isConnected = network.isConnected

            // Open network (no encryption)
            if (network.securityType == "Offen") {
                findings.add(SecurityFinding(
                    severity = if (isConnected) FindingSeverity.CRITICAL else FindingSeverity.HIGH,
                    category = FindingCategory.WIFI,
                    title = "Offenes WLAN${if (isConnected) " (verbunden!)" else ""}",
                    description = "\"${network.ssid}\" hat keine Verschlüsselung. Jeglicher Traffic kann mitgelesen werden.",
                    target = network.ssid,
                    recommendation = "WPA3 oder mindestens WPA2 aktivieren. Nicht mit offenen Netzwerken verbinden."
                ))
            }

            // WEP encryption
            if (network.securityType == "WEP") {
                findings.add(SecurityFinding(
                    severity = FindingSeverity.CRITICAL,
                    category = FindingCategory.WIFI,
                    title = "WEP-Verschlüsselung (unsicher)",
                    description = "\"${network.ssid}\" nutzt WEP. Diese Verschlüsselung kann in Minuten geknackt werden.",
                    target = network.ssid,
                    recommendation = "Sofort auf WPA2 oder WPA3 umstellen."
                ))
            }

            // WPA (v1) — deprecated
            if (network.securityType == "WPA" && !network.securityType.contains("WPA2")) {
                findings.add(SecurityFinding(
                    severity = FindingSeverity.HIGH,
                    category = FindingCategory.WIFI,
                    title = "WPA (veraltet)",
                    description = "\"${network.ssid}\" nutzt WPA1. Anfällig für TKIP-Angriffe.",
                    target = network.ssid,
                    recommendation = "Auf WPA2-AES oder WPA3 upgraden."
                ))
            }

            // WPS detection
            if (network.wpsEnabled) {
                findings.add(SecurityFinding(
                    severity = FindingSeverity.MEDIUM,
                    category = FindingCategory.WIFI,
                    title = "WPS aktiviert",
                    description = "\"${network.ssid}\" hat WPS (Wi-Fi Protected Setup) aktiv. Anfällig für Pixie Dust und Brute-Force.",
                    target = network.ssid,
                    recommendation = "WPS im Router deaktivieren."
                ))
            }

            // Hidden network connected
            if (network.ssid == "(Verstecktes Netzwerk)" && isConnected) {
                findings.add(SecurityFinding(
                    severity = FindingSeverity.LOW,
                    category = FindingCategory.WIFI,
                    title = "Verstecktes Netzwerk",
                    description = "Verbunden mit einem versteckten Netzwerk. SSID-Hiding bietet keinen echten Schutz.",
                    target = network.bssid,
                    recommendation = "SSID-Hiding bietet keine Security. Stattdessen starke Verschlüsselung verwenden."
                ))
            }
        }

        // Check if connected network uses WPA3
        val connectedNetwork = networks.find { it.isConnected }
        if (connectedNetwork != null && connectedNetwork.securityType == "WPA2") {
            findings.add(SecurityFinding(
                severity = FindingSeverity.INFO,
                category = FindingCategory.WIFI,
                title = "WPA3 nicht aktiv",
                description = "\"${connectedNetwork.ssid}\" nutzt WPA2. WPA3 bietet besseren Schutz gegen Offline-Wörterbuch-Angriffe.",
                target = connectedNetwork.ssid,
                recommendation = "WPA3 aktivieren falls Router und Clients es unterstützen."
            ))
        }

        return findings
    }



    private fun auditBluetooth(devices: List<BluetoothDevice>): List<SecurityFinding> {
        val findings = mutableListOf<SecurityFinding>()

        for (device in devices) {
            // Device without name — potential for tracking
            if (device.name == "(Unbekannt)" && device.bondState == BondState.NOT_BONDED) {
                // Too many unknowns = noise, skip unless it's BLE with service UUIDs
                if (device.serviceUuids.isNotEmpty()) {
                    findings.add(SecurityFinding(
                        severity = FindingSeverity.INFO,
                        category = FindingCategory.BLUETOOTH,
                        title = "Unbekanntes BLE-Gerät sendet Services",
                        description = "Gerät ${device.address} (${device.vendor ?: "unbekannter Hersteller"}) " +
                                "sendet ${device.serviceUuids.size} Service-UUIDs ohne sich zu identifizieren.",
                        target = device.address,
                        recommendation = "Prüfen ob das Gerät zum eigenen Netzwerk gehört."
                    ))
                }
                continue
            }

            // Classic BT discoverable without bonding
            if (device.type == com.scanner.app.data.DeviceType.CLASSIC &&
                device.bondState == BondState.NOT_BONDED
            ) {
                findings.add(SecurityFinding(
                    severity = FindingSeverity.LOW,
                    category = FindingCategory.BLUETOOTH,
                    title = "Bluetooth-Gerät sichtbar & nicht gekoppelt",
                    description = "\"${device.displayName()}\" ist für alle sichtbar und nicht gekoppelt.",
                    target = device.displayName(),
                    recommendation = "Bluetooth-Sichtbarkeit nur bei Bedarf aktivieren."
                ))
            }
        }

        // General: many BT devices around
        val unknownBt = devices.count {
            it.bondState == BondState.NOT_BONDED && it.name != "(Unbekannt)"
        }
        if (unknownBt > 10) {
            findings.add(SecurityFinding(
                severity = FindingSeverity.INFO,
                category = FindingCategory.BLUETOOTH,
                title = "$unknownBt fremde Bluetooth-Geräte in Reichweite",
                description = "Viele nicht-gekoppelte Bluetooth-Geräte in der Umgebung. Bluetooth-Tracking ist möglich.",
                target = "Umgebung",
                recommendation = "Bluetooth deaktivieren wenn nicht benötigt."
            ))
        }

        return findings
    }



    private fun auditPorts(openPorts: List<PortScanResult>): List<SecurityFinding> {
        val findings = mutableListOf<SecurityFinding>()

        for (result in openPorts) {
            if (result.state != PortState.OPEN) continue

            val risk = WellKnownPorts.riskLevel(result.port)
            val severity = when (risk) {
                PortRisk.CRITICAL -> FindingSeverity.CRITICAL
                PortRisk.HIGH -> FindingSeverity.HIGH
                PortRisk.MEDIUM -> FindingSeverity.MEDIUM
                PortRisk.LOW -> FindingSeverity.LOW
                PortRisk.INFO -> FindingSeverity.INFO
            }

            val description = buildString {
                append("Port ${result.port} (${result.serviceName}) ist offen auf ${result.ip}.")
                result.banner?.let {
                    append(" Banner: \"${it.take(100)}\"")
                }
            }

            val recommendation = when (result.port) {
                23 -> "Telnet sofort deaktivieren und durch SSH ersetzen."
                21 -> "FTP durch SFTP oder SCP ersetzen."
                6379 -> "Redis: AUTH-Passwort setzen und Bind-Adresse einschränken."
                27017 -> "MongoDB: Authentication aktivieren und Bind-Adresse einschränken."
                3306 -> "MySQL: Nur auf localhost binden oder Firewall-Regel setzen."
                5432 -> "PostgreSQL: pg_hba.conf prüfen, nur autorisierte IPs erlauben."
                3389 -> "RDP: NLA aktivieren, starke Passwörter erzwingen, VPN vorschalten."
                5900 -> "VNC: Starkes Passwort setzen, idealerweise nur über SSH-Tunnel."
                445 -> "SMB: Nicht ins Internet exponieren, SMBv1 deaktivieren."
                139 -> "NetBIOS: Falls nicht benötigt, deaktivieren."
                1883 -> "MQTT: TLS und Authentication aktivieren."
                9200 -> "Elasticsearch: X-Pack Security aktivieren oder Zugriff einschränken."
                80 -> "HTTP: HTTPS erzwingen, HTTP auf 301-Redirect umstellen."
                110 -> "POP3: Auf POP3S (Port 995) umstellen."
                143 -> "IMAP: Auf IMAPS (Port 993) umstellen."
                else -> "Prüfen ob der Dienst benötigt wird. Nicht benötigte Ports schließen."
            }

            findings.add(SecurityFinding(
                severity = severity,
                category = FindingCategory.PORTS,
                title = "${result.serviceName} offen (${risk.label})",
                description = description,
                target = "${result.ip}:${result.port}",
                recommendation = recommendation
            ))

            // Extra finding for banner with version info
            result.banner?.let { banner ->
                if (banner.contains(Regex("\\d+\\.\\d+"))) {
                    findings.add(SecurityFinding(
                        severity = FindingSeverity.INFO,
                        category = FindingCategory.PORTS,
                        title = "Versions-Info exponiert",
                        description = "${result.serviceName} auf ${result.ip} gibt Versionsinformationen preis: \"${banner.take(80)}\"",
                        target = "${result.ip}:${result.port}",
                        recommendation = "Server-Banner unterdrücken um Information Disclosure zu minimieren."
                    ))
                }
            }
        }

        return findings
    }
}
