package com.scanner.app.ui.screens

import android.Manifest
import android.os.Build
import android.util.Log
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.scanner.app.data.WifiNetwork
import com.scanner.app.data.BluetoothDevice
import com.scanner.app.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SecurityAuditScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val wifiScanner = remember { WifiScanner(context) }
    val btScanner = remember { BluetoothScanner(context) }
    val portScanner = remember { PortScanner() }
    val pingUtil = remember { PingUtil(context) }

    var wifiNetworks by remember { mutableStateOf<List<WifiNetwork>>(emptyList()) }
    var btDevices by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    var openPorts by remember { mutableStateOf<List<PortScanResult>>(emptyList()) }
    var report by remember { mutableStateOf<SecurityAuditReport?>(null) }
    var isAuditing by remember { mutableStateOf(false) }
    var auditPhase by remember { mutableStateOf("") }
    var portScanProgress by remember { mutableStateOf<PortScanProgress?>(null) }

    val permissions = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }
    val permissionState = rememberMultiplePermissionsState(permissions)

    DisposableEffect(Unit) {
        onDispose {
            wifiScanner.cleanup()
            btScanner.cleanup()
        }
    }

    fun runAudit() {
        if (!permissionState.allPermissionsGranted) {
            permissionState.launchMultiplePermissionRequest()
            return
        }

        isAuditing = true
        openPorts = emptyList()

        scope.launch {
            try {
                // Phase 1: WiFi Scan
                auditPhase = "WLAN scannen..."
                wifiNetworks = try {
                    val deferred = kotlinx.coroutines.CompletableDeferred<List<WifiNetwork>>()
                    wifiScanner.startScan { results -> deferred.complete(results) }
                    kotlinx.coroutines.withTimeoutOrNull(10_000L) { deferred.await() } ?: emptyList()
                } catch (e: Exception) {
                    Log.e("SecurityAudit", "WiFi scan error", e)
                    emptyList()
                }

                // Phase 2: Bluetooth Scan
                auditPhase = "Bluetooth scannen..."
                btDevices = try {
                    val deferred = kotlinx.coroutines.CompletableDeferred<List<BluetoothDevice>>()
                    btScanner.startScan(
                        durationMs = 6000L,
                        onProgress = { devices ->
                            // Ensure state update on main thread
                            scope.launch(Dispatchers.Main.immediate) {
                                try { btDevices = devices } catch (_: Exception) {}
                            }
                        },
                        onComplete = { results -> deferred.complete(results) }
                    )
                    kotlinx.coroutines.withTimeoutOrNull(15_000L) { deferred.await() } ?: emptyList()
                } catch (e: Exception) {
                    Log.e("SecurityAudit", "Bluetooth scan error", e)
                    emptyList()
                }

                // Phase 3: Port scan on gateway
                try {
                    val networkInfo = pingUtil.getNetworkInfo()
                    val gateway = networkInfo.gatewayIp

                    if (gateway != null) {
                        auditPhase = "Port-Scan: $gateway..."
                        val scanResults = portScanner.scan(
                            ip = gateway,
                            ports = WellKnownPorts.QUICK_20,
                            grabBanners = true,
                            onProgress = { progress ->
                                // Ensure state update on main thread
                                scope.launch(Dispatchers.Main.immediate) {
                                    portScanProgress = progress
                                }
                            }
                        )
                        // Update state on main thread
                        withContext(Dispatchers.Main) {
                            openPorts = scanResults
                        }
                    }
                } catch (e: Exception) {
                    Log.e("SecurityAudit", "Port scan error", e)
                }

                // Phase 4: Generate report
                auditPhase = "Bericht erstellen..."
                val generatedReport = try {
                    SecurityAuditor.audit(
                        wifiNetworks = wifiNetworks,
                        btDevices = btDevices,
                        openPorts = openPorts,
                        connectedSsid = wifiScanner.getConnectedSsid()
                    )
                } catch (e: Exception) {
                    Log.e("SecurityAudit", "Report generation error", e)
                    null
                }
                report = generatedReport
            } catch (e: Exception) {
                Log.e("SecurityAudit", "Audit error", e)
            } finally {
                isAuditing = false
                auditPhase = ""
                portScanProgress = null
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 100.dp)
    ) {
        // ─── Header ─────────────────────────────────────────────
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Security Audit",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isAuditing) auditPhase else
                            report?.let { "${it.findings.size} Findings · Score ${it.overallScore}/100" }
                                ?: "WLAN · Bluetooth · Gateway-Ports",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FilledTonalButton(
                    onClick = { runAudit() },
                    enabled = !isAuditing
                ) {
                    if (isAuditing) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Outlined.Shield, contentDescription = null)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isAuditing) auditPhase.take(20) else "Audit starten")
                }
            }
        }

        // ─── Progress ───────────────────────────────────────────
        if (isAuditing) {
            item {
                portScanProgress?.let { p ->
                    LinearProgressIndicator(
                        progress = { p.scanned.toFloat() / p.total.coerceAtLeast(1) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                    )
                } ?: LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        // ─── Score Card ─────────────────────────────────────────
        report?.let { r ->
            item {
                ScoreCard(report = r, modifier = Modifier.padding(horizontal = 16.dp))
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Severity summary badges
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (r.criticalCount > 0) SeverityBadge("${r.criticalCount} Kritisch", Color(0xFFD32F2F))
                    if (r.highCount > 0) SeverityBadge("${r.highCount} Hoch", Color(0xFFE64A19))
                    if (r.mediumCount > 0) SeverityBadge("${r.mediumCount} Mittel", Color(0xFFF57C00))
                    if (r.lowCount > 0) SeverityBadge("${r.lowCount} Niedrig", Color(0xFFFBC02D))
                    if (r.infoCount > 0) SeverityBadge("${r.infoCount} Info", Color(0xFF42A5F5))
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Hint about per-device port scanning
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Port-Scans einzelner Geräte findest du im LAN-Tab. GPS-Geotagging im WLAN-Tab.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // ─── Findings List ──────────────────────────────────
            if (r.findings.isNotEmpty()) {
                item {
                    Text(
                        text = "FINDINGS",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }

                items(r.findings, key = { "${it.target}-${it.title}" }) { finding ->
                    FindingCard(
                        finding = finding,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 3.dp)
                    )
                }
            }

            // ─── Open Ports from Gateway Scan ───────────────────
            if (openPorts.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "GATEWAY-PORTS",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
                items(openPorts, key = { "${it.ip}:${it.port}" }) { port ->
                    PortCard(port, modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp))
                }
            }
        }

        // ─── Empty State ────────────────────────────────────────
        if (report == null && !isAuditing) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Outlined.Shield,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Tippe auf \"Audit starten\" für eine\nNetzwerk-Sicherheitsanalyse.",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Prüft WLAN-Verschlüsselung, WPS, Bluetooth-Sichtbarkeit\nund offene Ports am Gateway.",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}

// ─── Score Card ─────────────────────────────────────────────────

@Composable
fun ScoreCard(report: SecurityAuditReport, modifier: Modifier = Modifier) {
    val scoreColor = when {
        report.overallScore >= 90 -> Color(0xFF4CAF50)
        report.overallScore >= 75 -> Color(0xFF8BC34A)
        report.overallScore >= 60 -> Color(0xFFFF9800)
        report.overallScore >= 40 -> Color(0xFFFF5722)
        else -> Color(0xFFF44336)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = scoreColor.copy(alpha = 0.08f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(scoreColor.copy(alpha = 0.15f))
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = report.grade,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = scoreColor
                    )
                    Text(
                        text = "${report.overallScore}/100",
                        style = MaterialTheme.typography.labelSmall,
                        color = scoreColor
                    )
                }
            }
            Spacer(modifier = Modifier.width(20.dp))
            Column {
                Text(
                    text = when {
                        report.overallScore >= 90 -> "Sehr gut geschützt"
                        report.overallScore >= 75 -> "Gut geschützt"
                        report.overallScore >= 60 -> "Verbesserungsbedarf"
                        report.overallScore >= 40 -> "Mehrere Risiken"
                        else -> "Kritische Risiken"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = scoreColor
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${report.auditedNetworks} Netzwerke · ${report.auditedDevices} Geräte",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${report.findings.size} Findings",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ─── Finding Card ───────────────────────────────────────────────

@Composable
fun FindingCard(finding: SecurityFinding, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val severityColor = Color(finding.severity.color)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(severityColor)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = finding.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = severityColor.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(3.dp)
                        ) {
                            Text(
                                text = finding.severity.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = severityColor,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                fontSize = 9.sp
                            )
                        }
                    }
                    Text(
                        text = "${finding.category.label} · ${finding.target}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = finding.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.Top) {
                        Icon(
                            Icons.Outlined.Lightbulb,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = finding.recommendation,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

// ─── Port Card ──────────────────────────────────────────────────

@Composable
fun PortCard(port: PortScanResult, modifier: Modifier = Modifier) {
    val risk = WellKnownPorts.riskLevel(port.port)
    val riskColor = when (risk) {
        PortRisk.CRITICAL -> Color(0xFFD32F2F)
        PortRisk.HIGH -> Color(0xFFE64A19)
        PortRisk.MEDIUM -> Color(0xFFF57C00)
        PortRisk.LOW -> Color(0xFFFBC02D)
        PortRisk.INFO -> Color(0xFF42A5F5)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(riskColor))
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "${port.port}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.width(50.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(text = port.serviceName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                port.banner?.let {
                    Text(
                        text = it.take(60),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            port.latencyMs?.let {
                Text(
                    text = "${"%.0f".format(it)}ms",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Browser button for detected HTTP
            WellKnownPorts.browseUrl(port)?.let { url ->
                Spacer(modifier = Modifier.width(4.dp))
                val context = LocalContext.current
                IconButton(
                    onClick = {
                        try {
                            val intent = android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse(url)
                            )
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Outlined.OpenInBrowser,
                        contentDescription = "Im Browser öffnen",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SeverityBadge(text: String, color: Color) {
    Surface(color = color.copy(alpha = 0.12f), shape = RoundedCornerShape(6.dp)) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
