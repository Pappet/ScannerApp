package com.scanner.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material.icons.outlined.DeveloperBoard
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.Speaker
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.scanner.app.R
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.scanner.app.ui.components.HairlineHorizontal
import com.scanner.app.ui.components.HeaderStat
import com.scanner.app.ui.components.SpectrumHeader
import com.scanner.app.ui.components.SpectrumKicker
import com.scanner.app.ui.components.SpectrumScanButton
import com.scanner.app.ui.theme.JetBrainsMonoFamily
import com.scanner.app.ui.theme.Spectrum
import com.scanner.app.ui.viewmodel.LanViewModel
import com.scanner.app.util.LanDevice
import com.scanner.app.util.LanScanProgress
import com.scanner.app.util.LanService
import com.scanner.app.util.MacVendorLookup
import com.scanner.app.util.PortRisk
import com.scanner.app.util.PortScanProgress
import com.scanner.app.util.PortScanResult
import com.scanner.app.util.WellKnownPorts

private val WARN_PORTS = setOf(22, 23, 80)

@Composable
fun LanScreen(vm: LanViewModel = viewModel()) {
    val devices = vm.devices
    val isScanning = vm.isScanning
    val hasScanned = vm.hasScanned
    val progress = vm.progress
    val networkInfo = vm.networkInfo
    val portScanResults = vm.portScanResults
    val portScanningIp = vm.portScanningIp
    val portScanProgress = vm.portScanProgress

    val favorites by vm.repository.observeFavorites().collectAsState(initial = emptyList())
    val favoriteAddresses = favorites.map { it.address }.toSet()

    val totalOpenPorts = portScanResults.values.sumOf { it.size }
    val subnet = networkInfo?.deviceIp
        ?.substringBeforeLast(".")
        ?.let { "$it.0/24" } ?: "—"

    Column(Modifier.fillMaxSize().background(Spectrum.Surface)) {
        SpectrumHeader(
            kicker = "LAN",
            subtitle = stringResource(R.string.lan_subtitle),
            stats = listOf(
                HeaderStat(devices.size.toString(), stringResource(R.string.stat_hosts)),
                HeaderStat(if (totalOpenPorts > 0) totalOpenPorts.toString() else "—", stringResource(R.string.stat_ports)),
                HeaderStat(subnet, stringResource(R.string.stat_subnet)),
            ),
            trailing = {
                SpectrumScanButton(scanning = isScanning, onClick = { vm.scan() })
            },
        )

        if (isScanning && progress != null) {
            LanProgressBar(progress!!)
        }

        when {
            devices.isEmpty() && !hasScanned -> LanEmptyState(hasScanned = false)
            devices.isEmpty() && hasScanned -> LanEmptyState(hasScanned = true)
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(devices, key = { it.ip }) { device ->
                    val address = device.mac ?: "lan:${device.ip}"
                    val isFavorite = address in favoriteAddresses
                    LanDeviceRow(
                        device = device,
                        portResults = portScanResults[device.ip] ?: emptyList(),
                        hasBeenPortScanned = device.ip in portScanResults,
                        isPortScanning = portScanningIp == device.ip,
                        portProgress = if (portScanningIp == device.ip) portScanProgress else null,
                        isFavorite = isFavorite,
                        onToggleFavorite = { vm.toggleFavorite(address) },
                        onPortScan = { ports -> vm.startPortScan(device.ip, ports) },
                    )
                    HairlineHorizontal()
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun LanProgressBar(progress: LanScanProgress) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Spectrum.SurfaceRaised)
            .padding(horizontal = 18.dp, vertical = 8.dp),
    ) {
        val pct = progress.current.toFloat() / progress.total.coerceAtLeast(1)
        Box(Modifier.fillMaxWidth().height(1.dp).background(Spectrum.GridLine)) {
            Box(Modifier.fillMaxWidth(pct).height(1.dp).background(Spectrum.Accent))
        }
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.lan_progress, progress.phase, progress.devicesFound),
            fontFamily = JetBrainsMonoFamily,
            fontSize = 10.sp,
            color = Spectrum.OnSurfaceDim,
            letterSpacing = 0.1.em,
        )
    }
}

@Composable
private fun LanEmptyState(hasScanned: Boolean) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SpectrumKicker(
                if (hasScanned) stringResource(R.string.kicker_no_devices) else stringResource(R.string.kicker_no_scan),
                color = Spectrum.OnSurfaceDim,
            )
            Text(
                if (hasScanned)
                    stringResource(R.string.lan_empty_scanned)
                else
                    stringResource(R.string.lan_empty_unscanned),
                color = Spectrum.OnSurfaceDim,
                fontFamily = JetBrainsMonoFamily,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun LanDeviceRow(
    device: LanDevice,
    portResults: List<PortScanResult>,
    hasBeenPortScanned: Boolean,
    isPortScanning: Boolean,
    portProgress: PortScanProgress?,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onPortScan: (List<Int>) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    val icon: ImageVector = when {
        device.isGateway -> Icons.Outlined.Router
        device.isOwnDevice -> Icons.Outlined.PhoneAndroid
        device.services.any { it.type.contains("printer") || it.type.contains("ipp") } -> Icons.Outlined.Print
        device.services.any { it.type.contains("airplay") || it.type.contains("raop") } -> Icons.Outlined.Speaker
        device.services.any { it.type.contains("googlecast") } -> Icons.Outlined.Cast
        device.services.any { it.type.contains("smb") } -> Icons.Outlined.Storage
        device.services.any { it.type.contains("ssh") } -> Icons.Outlined.Terminal
        device.services.any { it.type.contains("http") } -> Icons.Outlined.Language
        device.vendor?.contains("Raspberry", ignoreCase = true) == true -> Icons.Outlined.DeveloperBoard
        device.vendor?.contains("ESP", ignoreCase = true) == true -> Icons.Outlined.Memory
        else -> Icons.Outlined.Devices
    }

    // Port scan results take priority; fall back to mDNS service ports
    val displayPorts: List<Int> = if (portResults.isNotEmpty()) {
        portResults.map { it.port }
    } else {
        device.services.map { it.port }.filter { it > 0 }
    }

    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // 32dp icon tile
            Box(
                Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Spectrum.SurfaceRaised)
                    .border(1.dp, Spectrum.GridLine, RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Spectrum.Accent,
                    modifier = Modifier.size(16.dp),
                )
            }

            // IP + name/vendor meta
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (isFavorite) {
                        Icon(
                            imageVector = Icons.Outlined.Star,
                            contentDescription = stringResource(R.string.cd_favorite),
                            tint = Spectrum.Accent,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                    Text(
                        text = device.ip,
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 14.sp,
                        color = Spectrum.OnSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val meta = buildString {
                    append(device.hostname ?: device.vendor ?: "—")
                    if (device.hostname != null && device.vendor != null) {
                        append(" · ${device.vendor}")
                    }
                }
                Text(
                    text = meta,
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 10.sp,
                    color = Spectrum.OnSurfaceDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Port chips: first 4, then overflow count
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                displayPorts.take(4).forEach { port -> LanPortChip(port) }
                if (displayPorts.size > 4) {
                    Text(
                        "+${displayPorts.size - 4}",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 9.sp,
                        color = Spectrum.OnSurfaceDim,
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                }
            }
        }

        if (expanded) {
            LanDeviceDetail(
                device = device,
                portResults = portResults,
                hasBeenPortScanned = hasBeenPortScanned,
                isPortScanning = isPortScanning,
                portProgress = portProgress,
                isFavorite = isFavorite,
                onToggleFavorite = onToggleFavorite,
                onPortScan = onPortScan,
            )
        }
    }
}

@Composable
private fun LanPortChip(port: Int) {
    val risk = port in WARN_PORTS
    Text(
        text = port.toString(),
        modifier = Modifier
            .clip(RoundedCornerShape(2.dp))
            .background(Spectrum.SurfaceRaised)
            .border(
                1.dp,
                if (risk) Spectrum.Warning else Spectrum.GridLine,
                RoundedCornerShape(2.dp),
            )
            .padding(horizontal = 5.dp, vertical = 3.dp),
        color = if (risk) Spectrum.Warning else Spectrum.OnSurfaceDim,
        fontFamily = JetBrainsMonoFamily,
        fontSize = 9.sp,
        letterSpacing = 0.04.em,
    )
}

@Composable
private fun LanDeviceDetail(
    device: LanDevice,
    portResults: List<PortScanResult>,
    hasBeenPortScanned: Boolean,
    isPortScanning: Boolean,
    portProgress: PortScanProgress?,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onPortScan: (List<Int>) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Spectrum.SurfaceRaised)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        device.mac?.let { LanDetailRow("MAC", it) }
        device.mac?.let { mac ->
            MacVendorLookup.lookup(mac)?.let { LanDetailRow(stringResource(R.string.detail_manufacturer), it) }
        }
        device.hostname?.let { LanDetailRow(stringResource(R.string.detail_hostname), it) }
        LanDetailRow(stringResource(R.string.detail_discovered_via), device.discoveredVia.displayName())
        device.latencyMs?.let { LanDetailRow(stringResource(R.string.detail_latency), "${"%.1f".format(it)} ms") }
        if (device.isGateway) LanDetailRow(stringResource(R.string.detail_role), stringResource(R.string.role_gateway))
        if (device.isOwnDevice) LanDetailRow(stringResource(R.string.detail_role), stringResource(R.string.role_this_device))

        Row(
            Modifier.fillMaxWidth().padding(top = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.detail_favorite), fontFamily = JetBrainsMonoFamily, fontSize = 10.sp, color = Spectrum.OnSurfaceDim)
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .border(1.dp, Spectrum.GridLine, RoundedCornerShape(4.dp))
                    .clickable { onToggleFavorite() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Outlined.Star else Icons.Outlined.StarOutline,
                    contentDescription = stringResource(R.string.cd_favorite),
                    tint = if (isFavorite) Spectrum.Accent else Spectrum.OnSurface,
                    modifier = Modifier.size(14.dp),
                )
            }
        }

        if (device.services.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            SpectrumKicker(stringResource(R.string.kicker_services, device.services.size), color = Spectrum.OnSurfaceDim)
            Spacer(Modifier.height(4.dp))
            device.services.forEach { LanServiceRow(it) }
        }

        device.upnpInfo?.let { upnp ->
            Spacer(Modifier.height(4.dp))
            SpectrumKicker("UPNP", color = Spectrum.Accent2)
            Spacer(Modifier.height(4.dp))
            upnp.friendlyName?.let { LanDetailRow(stringResource(R.string.detail_name), it) }
            upnp.manufacturer?.let { LanDetailRow(stringResource(R.string.detail_manufacturer), it) }
            upnp.modelName?.let { LanDetailRow(stringResource(R.string.detail_model), it) }
            upnp.modelDescription?.let { LanDetailRow(stringResource(R.string.detail_description), it) }
            upnp.deviceType?.let { LanDetailRow(stringResource(R.string.detail_device_type), it) }
            if (upnp.services.isNotEmpty()) {
                LanDetailRow(stringResource(R.string.detail_upnp_services), upnp.services.joinToString(", "))
            }
        }

        Spacer(Modifier.height(6.dp))
        HairlineHorizontal()
        Spacer(Modifier.height(8.dp))

        SpectrumKicker(
            text = if (hasBeenPortScanned) stringResource(R.string.kicker_port_scan_open, portResults.size) else stringResource(R.string.kicker_port_scan),
            color = Spectrum.OnSurfaceDim,
        )
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                stringResource(R.string.chip_top20) to WellKnownPorts.QUICK_20,
                stringResource(R.string.chip_top50) to WellKnownPorts.TOP_50,
                stringResource(R.string.chip_top200) to WellKnownPorts.TOP_200,
                stringResource(R.string.chip_all_ports) to WellKnownPorts.ALL_PORTS,
            ).forEach { (label, ports) ->
                LanActionChip(
                    label = label,
                    enabled = !isPortScanning,
                    danger = label == stringResource(R.string.chip_all_ports),
                    onClick = { onPortScan(ports) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (isPortScanning && portProgress != null) {
            Spacer(Modifier.height(6.dp))
            val pct = portProgress.scanned.toFloat() / portProgress.total.coerceAtLeast(1)
            Box(Modifier.fillMaxWidth().height(1.dp).background(Spectrum.GridLine)) {
                Box(Modifier.fillMaxWidth(pct).height(1.dp).background(Spectrum.Accent))
            }
            Spacer(Modifier.height(3.dp))
            Text(
                stringResource(R.string.lan_port_scan_progress, portProgress.currentPort, (pct * 100).toInt(), portProgress.openPorts),
                fontFamily = JetBrainsMonoFamily,
                fontSize = 9.sp,
                color = Spectrum.OnSurfaceDim,
            )
        }

        if (portResults.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            portResults.forEach { LanOpenPortRow(it) }
        } else if (hasBeenPortScanned) {
            Spacer(Modifier.height(2.dp))
            Text(
                stringResource(R.string.lan_no_open_ports),
                fontFamily = JetBrainsMonoFamily,
                fontSize = 10.sp,
                color = Spectrum.OnSurfaceDim,
            )
        }
    }
}

@Composable
private fun LanDetailRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            fontFamily = JetBrainsMonoFamily,
            fontSize = 10.sp,
            color = Spectrum.OnSurfaceDim,
        )
        Text(
            value,
            fontFamily = JetBrainsMonoFamily,
            fontSize = 10.sp,
            color = Spectrum.OnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}

@Composable
private fun LanServiceRow(service: LanService) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            service.name.ifBlank { service.type },
            fontFamily = JetBrainsMonoFamily,
            fontSize = 10.sp,
            color = Spectrum.OnSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            ":${service.port}",
            fontFamily = JetBrainsMonoFamily,
            fontSize = 10.sp,
            color = Spectrum.OnSurfaceDim,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun LanActionChip(
    label: String,
    enabled: Boolean,
    danger: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = when {
        !enabled -> Spectrum.GridLine
        danger -> Spectrum.Danger.copy(alpha = 0.5f)
        else -> Spectrum.GridLine
    }
    val textColor = when {
        !enabled -> Spectrum.OnSurfaceFaint
        danger -> Spectrum.Danger
        else -> Spectrum.OnSurfaceDim
    }
    Box(
        modifier
            .clip(RoundedCornerShape(2.dp))
            .background(Spectrum.Surface)
            .border(1.dp, borderColor, RoundedCornerShape(2.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontFamily = JetBrainsMonoFamily,
            fontSize = 9.sp,
            color = textColor,
            letterSpacing = 0.1.em,
        )
    }
}

@Composable
private fun LanOpenPortRow(port: PortScanResult) {
    val context = LocalContext.current
    val riskColor = when (WellKnownPorts.riskLevel(port.port)) {
        PortRisk.CRITICAL -> Spectrum.Danger
        PortRisk.HIGH -> Spectrum.SeverityHigh
        PortRisk.MEDIUM -> Spectrum.Warning
        PortRisk.LOW -> Spectrum.SeverityLow
        PortRisk.INFO -> Spectrum.OnSurfaceDim
    }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(5.dp).clip(CircleShape).background(riskColor))
        Text(
            "${port.port}",
            fontFamily = JetBrainsMonoFamily,
            fontSize = 11.sp,
            color = riskColor,
            modifier = Modifier.width(40.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.lan_port_service, port.serviceName ?: "", WellKnownPorts.riskLevel(port.port).label),
                fontFamily = JetBrainsMonoFamily,
                fontSize = 10.sp,
                color = Spectrum.OnSurface,
            )
            port.banner?.let {
                Text(
                    it.take(60),
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 9.sp,
                    color = Spectrum.OnSurfaceDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        port.latencyMs?.let {
            Text(
                "${"%.0f".format(it)}ms",
                fontFamily = JetBrainsMonoFamily,
                fontSize = 9.sp,
                color = Spectrum.OnSurfaceDim,
            )
        }
        WellKnownPorts.browseUrl(port)?.let { url ->
            Box(
                Modifier
                    .clip(RoundedCornerShape(2.dp))
                    .border(1.dp, Spectrum.GridLine, RoundedCornerShape(2.dp))
                    .clickable {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        } catch (_: Exception) {}
                    }
                    .padding(horizontal = 5.dp, vertical = 3.dp),
            ) {
                Text("↗", fontFamily = JetBrainsMonoFamily, fontSize = 9.sp, color = Spectrum.OnSurfaceDim)
            }
        }
    }
}
