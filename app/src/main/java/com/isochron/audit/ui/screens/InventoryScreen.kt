package com.scanner.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.scanner.app.R
import com.scanner.app.data.db.DeviceCategory
import com.scanner.app.data.db.DiscoveredDeviceEntity
import com.scanner.app.data.repository.DeviceRepository
import com.scanner.app.ui.components.ExportDialog
import com.scanner.app.ui.components.HairlineHorizontal
import com.scanner.app.ui.components.HeaderStat
import com.scanner.app.ui.components.SpectrumFilterChip
import com.scanner.app.ui.components.SpectrumHeader
import com.scanner.app.ui.components.SpectrumKicker
import com.scanner.app.ui.theme.JetBrainsMonoFamily
import com.scanner.app.ui.theme.Spectrum
import com.scanner.app.ui.viewmodel.InventoryViewModel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val BT_CATEGORIES = setOf(
    DeviceCategory.BT_CLASSIC,
    DeviceCategory.BT_BLE,
    DeviceCategory.BT_DUAL,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InventoryScreen(
    vm: InventoryViewModel = viewModel(),
    onNavigateToDevice: ((Long) -> Unit)? = null,
) {
    val context = LocalContext.current
    val repository = vm.repository
    val scope = rememberCoroutineScope()

    val kind = vm.kind
    val favOnly = vm.favOnly
    val searchQuery = vm.searchQuery

    val baseDevices by remember(searchQuery, kind) {
        when {
            searchQuery.isNotBlank() -> repository.searchDevices(searchQuery)
            kind == "wifi" -> repository.observeDevicesByCategory(DeviceCategory.WIFI)
            kind == "lan" -> repository.observeDevicesByCategory(DeviceCategory.LAN)
            else -> repository.observeAllDevices()
        }
    }.collectAsState(initial = emptyList())

    val filtered = remember(baseDevices, kind, favOnly) {
        baseDevices
            .let { if (kind == "bt") it.filter { d -> d.deviceCategory in BT_CATEGORIES } else it }
            .let { if (favOnly) it.filter { d -> d.isFavorite } else it }
    }

    val totalCount by repository.observeTotalDeviceCount().collectAsState(initial = 0)
    val wifiCount by repository.observeWifiCount().collectAsState(initial = 0)
    val btCount by repository.observeBluetoothCount().collectAsState(initial = 0)
    val lanCount by repository.observeDevicesByCategory(DeviceCategory.LAN)
        .map { it.size }.collectAsState(initial = 0)

    val editDialogDevice = vm.editDialogDevice
    val showExportDialog = vm.showExportDialog

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().background(Spectrum.Surface)) {

        SpectrumHeader(
            kicker = "INVENTORY",
            subtitle = stringResource(R.string.inventory_catalog),
            trailing = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .border(1.dp, Spectrum.AccentDim, RoundedCornerShape(999.dp))
                        .clickable { vm.showExportDialog = true }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Icon(
                        Icons.Outlined.Download,
                        contentDescription = null,
                        tint = Spectrum.Accent,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        stringResource(R.string.btn_export),
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 11.sp,
                        color = Spectrum.Accent,
                        letterSpacing = 0.12.em,
                    )
                }
            },
        )

        // Inline search
        InvSearchBar(query = searchQuery, onQueryChange = { vm.searchQuery = it })
        HairlineHorizontal()

        // Filter chips
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SpectrumFilterChip(stringResource(R.string.filter_all), kind == "all", { vm.kind = "all" }, count = totalCount)
            SpectrumFilterChip("WIFI", kind == "wifi", { vm.kind = "wifi" }, count = wifiCount)
            SpectrumFilterChip("BT", kind == "bt", { vm.kind = "bt" }, count = btCount)
            SpectrumFilterChip("LAN", kind == "lan", { vm.kind = "lan" }, count = lanCount)
            SpectrumFilterChip(stringResource(R.string.filter_favs), favOnly, { vm.favOnly = !favOnly })
        }
        HairlineHorizontal()

        if (filtered.isEmpty()) {
            InvEmptyState(searchQuery)
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(filtered, key = { it.id }) { device ->
                    InvDeviceRow(
                        device = device,
                        onToggleFavorite = { scope.launch { repository.toggleFavorite(device.id) } },
                        onEdit = { vm.editDialogDevice = device },
                        onDelete = { scope.launch { repository.deleteDevice(device.id) } },
                    )
                    HairlineHorizontal()
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    editDialogDevice?.let { device ->
        EditDeviceDialog(
            device = device,
            onDismiss = { vm.editDialogDevice = null },
            onSave = { label, notes ->
                scope.launch {
                    repository.setCustomLabel(device.id, label.ifBlank { null })
                    repository.setNotes(device.id, notes.ifBlank { null })
                }
                vm.editDialogDevice = null
            },
        )
    }

    if (showExportDialog) {
        ExportDialog(onDismiss = { vm.showExportDialog = false })
    }
    } // Box
}

@Composable
private fun InvSearchBar(query: String, onQueryChange: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Spectrum.Surface)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(Spectrum.SurfaceRaised)
                .border(1.dp, Spectrum.GridLine, RoundedCornerShape(4.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                Icons.Outlined.Search,
                contentDescription = null,
                tint = Spectrum.OnSurfaceDim,
                modifier = Modifier.size(14.dp),
            )
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 12.sp,
                    color = Spectrum.OnSurface,
                ),
                cursorBrush = SolidColor(Spectrum.Accent),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    if (query.isEmpty()) {
                        Text(
                            stringResource(R.string.inv_search_placeholder),
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 12.sp,
                            color = Spectrum.OnSurfaceDim,
                        )
                    }
                    inner()
                },
            )
            if (query.isNotEmpty()) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.cd_clear),
                    tint = Spectrum.OnSurfaceDim,
                    modifier = Modifier.size(14.dp).clickable { onQueryChange("") },
                )
            }
        }
    }
}

@Composable
private fun InvEmptyState(query: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SpectrumKicker(stringResource(R.string.inv_no_matches_kicker), color = Spectrum.OnSurfaceDim)
            Text(
                if (query.isNotBlank())
                    stringResource(R.string.inv_no_matches_query, query)
                else
                    stringResource(R.string.inv_no_devices),
                color = Spectrum.OnSurfaceDim,
                fontFamily = JetBrainsMonoFamily,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun InvDeviceRow(
    device: DiscoveredDeviceEntity,
    onToggleFavorite: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    val meta = remember(device.metadata) {
        try { device.metadata?.let { org.json.JSONObject(it) } } catch (_: Exception) { null }
    }

    val categoryLabel = device.deviceCategory.shortName()
    val icon: ImageVector = when (device.deviceCategory) {
        DeviceCategory.WIFI -> Icons.Outlined.Wifi
        DeviceCategory.BT_CLASSIC, DeviceCategory.BT_BLE, DeviceCategory.BT_DUAL -> Icons.Outlined.Bluetooth
        DeviceCategory.LAN -> Icons.Outlined.Lan
    }

    val context = LocalContext.current

    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = { expanded = !expanded }, onLongClick = { showMenu = true })
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 34dp icon tile
            Box(
                Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Spectrum.SurfaceRaised)
                    .border(1.dp, Spectrum.GridLine, RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = Spectrum.Accent, modifier = Modifier.size(16.dp))
            }

            // Name + label + address/meta
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = device.displayName(),
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 14.sp,
                        color = Spectrum.OnSurface,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Icon(
                        imageVector = if (device.isFavorite) Icons.Outlined.Star else Icons.Outlined.StarOutline,
                        contentDescription = if (device.isFavorite) stringResource(R.string.cd_remove_fav) else stringResource(R.string.cd_add_fav),
                        tint = if (device.isFavorite) Spectrum.Accent else Spectrum.OnSurfaceFaint,
                        modifier = Modifier
                            .size(16.dp)
                            .clickable(onClick = onToggleFavorite),
                    )
                }
                device.customLabel?.let { label ->
                    Text(
                        "\"$label\"",
                        color = Spectrum.Accent,
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 10.sp,
                        letterSpacing = 0.08.em,
                        maxLines = 1,
                    )
                }
                Text(
                    buildString {
                        append(device.address)
                        append(" · $categoryLabel")
                        when (device.deviceCategory) {
                            DeviceCategory.WIFI -> meta?.optString("security")?.takeIf { it.isNotBlank() }?.let { append(" · $it") }
                            DeviceCategory.LAN -> meta?.optString("ip")?.takeIf { it.isNotBlank() }?.let { append(" · $it") }
                            else -> meta?.optString("deviceClass")?.takeIf { it.isNotBlank() }?.let { append(" · $it") }
                        }
                    },
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 10.sp,
                    color = Spectrum.OnSurfaceDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Sessions × last seen
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    stringResource(R.string.inv_times_seen, device.timesSeen),
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 10.sp,
                    color = Spectrum.OnSurfaceDim,
                )
                Text(
                    formatRelativeTime(device.lastSeen, context),
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 9.sp,
                    color = Spectrum.OnSurfaceFaint,
                )
            }

            Box {
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(if (device.isFavorite) stringResource(R.string.cd_remove_fav) else stringResource(R.string.cd_add_fav)) },
                        onClick = { onToggleFavorite(); showMenu = false },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.str_edit)) },
                        onClick = { onEdit(); showMenu = false },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.str_delete), color = Spectrum.Danger) },
                        onClick = { onDelete(); showMenu = false },
                    )
                }
            }
        }

        if (expanded) {
            InvDeviceDetail(device, meta)
        }
    }
}

@Composable
private fun InvDeviceDetail(
    device: DiscoveredDeviceEntity,
    meta: org.json.JSONObject?,
) {
    val timeFormatter = remember {
        DateTimeFormatter.ofPattern("dd.MM.yy HH:mm").withZone(ZoneId.systemDefault())
    }

    Column(
        Modifier
            .fillMaxWidth()
            .background(Spectrum.SurfaceRaised)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        InvDetailRow(stringResource(R.string.detail_address), device.address)
        InvDetailRow(stringResource(R.string.detail_category), device.deviceCategory.displayName())
        InvDetailRow(stringResource(R.string.detail_first_seen), timeFormatter.format(device.firstSeen))
        InvDetailRow(stringResource(R.string.detail_last_seen), timeFormatter.format(device.lastSeen))
        InvDetailRow(stringResource(R.string.detail_times_seen), "${device.timesSeen}")
        device.lastSignalStrength?.let { InvDetailRow(stringResource(R.string.detail_last_rssi), "$it dBm") }
        device.customLabel?.let { InvDetailRow(stringResource(R.string.detail_label), it) }
        device.notes?.let { InvDetailRow(stringResource(R.string.detail_notes), it) }

        if (meta != null) {
            Spacer(Modifier.height(6.dp))
            HairlineHorizontal()
            Spacer(Modifier.height(6.dp))

            when (device.deviceCategory) {
                DeviceCategory.WIFI -> {
                    SpectrumKicker(stringResource(R.string.kicker_wifi_details), color = Spectrum.Accent)
                    Spacer(Modifier.height(4.dp))
                    meta.optString("vendor").takeIf { it.isNotBlank() }?.let { InvDetailRow(stringResource(R.string.detail_vendor), it) }
                    meta.optString("wifiStandard").takeIf { it.isNotBlank() }?.let { std ->
                        val bw = meta.optString("channelWidth")
                        InvDetailRow(stringResource(R.string.detail_wifi_std), if (bw.isNotBlank()) "$std ($bw)" else std)
                    }
                    meta.optInt("frequency", 0).takeIf { it > 0 }?.let { InvDetailRow(stringResource(R.string.detail_freq), "$it MHz") }
                    meta.optInt("channel", 0).takeIf { it > 0 }?.let { InvDetailRow(stringResource(R.string.detail_channel), "$it") }
                    meta.optString("band").takeIf { it.isNotBlank() }?.let { InvDetailRow(stringResource(R.string.detail_band), it) }
                    meta.optString("security").takeIf { it.isNotBlank() }?.let { InvDetailRow(stringResource(R.string.detail_security), it) }
                    val dist = meta.optDouble("distance", Double.NaN)
                    if (!dist.isNaN()) InvDetailRow(stringResource(R.string.detail_distance), stringResource(R.string.val_distance_est, dist))
                    if (meta.optBoolean("wpsEnabled")) InvDetailRow("WPS", stringResource(R.string.val_enabled))
                    val lat = meta.optDouble("latitude", Double.NaN)
                    val lon = meta.optDouble("longitude", Double.NaN)
                    if (!lat.isNaN() && !lon.isNaN()) {
                        InvDetailRow("GPS", "%.6f, %.6f".format(lat, lon))
                        val alt = meta.optDouble("altitude", Double.NaN)
                        if (!alt.isNaN()) InvDetailRow(stringResource(R.string.detail_altitude), "%.1f m".format(alt))
                    }
                    if (meta.optBoolean("isConnected")) InvDetailRow(stringResource(R.string.detail_status), stringResource(R.string.val_connected))
                }

                DeviceCategory.BT_CLASSIC, DeviceCategory.BT_BLE, DeviceCategory.BT_DUAL -> {
                    SpectrumKicker(stringResource(R.string.kicker_bt_details), color = Spectrum.Accent)
                    Spacer(Modifier.height(4.dp))
                    meta.optString("deviceClass").takeIf { it.isNotBlank() }?.let { InvDetailRow(stringResource(R.string.detail_device_class), it) }
                    meta.optString("bondState").takeIf { it.isNotBlank() }?.let {
                        val stateStr = when (it) {
                            "BONDED" -> stringResource(R.string.val_bonded)
                            "BONDING" -> stringResource(R.string.val_bonding)
                            else -> stringResource(R.string.val_not_bonded)
                        }
                        InvDetailRow(stringResource(R.string.detail_bond), stateStr)
                    }
                    if (meta.optBoolean("isConnected")) InvDetailRow(stringResource(R.string.detail_status), stringResource(R.string.val_connected))
                    val gattServices = meta.optJSONObject("gattData")?.optJSONArray("services")
                    val unknownGeneral = stringResource(R.string.unknown_general)
                    if (gattServices != null && gattServices.length() > 0) {
                        Spacer(Modifier.height(4.dp))
                        SpectrumKicker(stringResource(R.string.kicker_gatt_services, gattServices.length()), color = Spectrum.Accent2)
                        Spacer(Modifier.height(4.dp))
                        for (i in 0 until gattServices.length()) {
                            val svc = gattServices.optJSONObject(i) ?: continue
                            InvDetailRow(
                                svc.optString("name", unknownGeneral),
                                "${svc.optJSONArray("characteristics")?.length() ?: 0} CHRs",
                            )
                        }
                    }
                }

                DeviceCategory.LAN -> {
                    SpectrumKicker(stringResource(R.string.kicker_lan_details), color = Spectrum.Accent)
                    Spacer(Modifier.height(4.dp))
                    meta.optString("ip").takeIf { it.isNotBlank() }?.let { InvDetailRow(stringResource(R.string.detail_ip_address), it) }
                    meta.optString("mac").takeIf { it.isNotBlank() }?.let { InvDetailRow(stringResource(R.string.detail_mac_address), it) }
                    (meta.optString("vendorFull").takeIf { it.isNotBlank() }
                        ?: meta.optString("vendor").takeIf { it.isNotBlank() })?.let { InvDetailRow(stringResource(R.string.detail_vendor), it) }
                    meta.optString("hostname").takeIf { it.isNotBlank() }?.let { InvDetailRow(stringResource(R.string.detail_hostname), it) }
                    meta.optDouble("latencyMs", -1.0).takeIf { it >= 0 }?.let { InvDetailRow(stringResource(R.string.detail_latency), "${"%.1f".format(it)} ms") }
                    if (meta.optBoolean("isGateway")) InvDetailRow(stringResource(R.string.detail_role), stringResource(R.string.val_role_gateway))
                    if (meta.optBoolean("isOwnDevice")) InvDetailRow(stringResource(R.string.detail_role), stringResource(R.string.val_role_own))
                    val services = meta.optJSONArray("services")
                    if (services != null && services.length() > 0) {
                        Spacer(Modifier.height(4.dp))
                        SpectrumKicker(stringResource(R.string.kicker_services, services.length()), color = Spectrum.Accent2)
                        Spacer(Modifier.height(4.dp))
                        val serviceUnknown = stringResource(R.string.val_service_unknown)
                        for (i in 0 until services.length()) {
                            val svc = services.optJSONObject(i) ?: continue
                            InvDetailRow(svc.optString("type", serviceUnknown), "${svc.optString("name", "")} :${svc.optInt("port", 0)}")
                        }
                    }
                    val openPorts = meta.optJSONArray("openPorts")
                    if (openPorts != null && openPorts.length() > 0) {
                        Spacer(Modifier.height(4.dp))
                        SpectrumKicker(stringResource(R.string.kicker_open_ports, openPorts.length()), color = Spectrum.SeverityHigh)
                        Spacer(Modifier.height(4.dp))
                        for (i in 0 until openPorts.length()) {
                            val p = openPorts.optJSONObject(i) ?: continue
                            InvDetailRow(
                                stringResource(R.string.val_port_service, p.optInt("port", 0), p.optString("service", "")),
                                p.optString("banner", "").ifBlank { p.optString("state", "") },
                            )
                        }
                    }
                }
            }
        }

        // Raw metadata toggle
        device.metadata?.let { raw ->
            Spacer(Modifier.height(6.dp))
            HairlineHorizontal()
            Spacer(Modifier.height(6.dp))
            var showRaw by remember { mutableStateOf(false) }
            Text(
                if (showRaw) "RAW METADATA ▼" else "RAW METADATA ▶",
                fontFamily = JetBrainsMonoFamily,
                fontSize = 9.sp,
                color = Spectrum.OnSurfaceFaint,
                modifier = Modifier.clickable { showRaw = !showRaw },
            )
            if (showRaw) {
                Spacer(Modifier.height(4.dp))
                val formatted = try { org.json.JSONObject(raw).toString(2) } catch (_: Exception) { raw }
                Text(
                    formatted,
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 9.sp,
                    color = Spectrum.OnSurfaceDim,
                    lineHeight = 14.sp,
                )
            }
        }
    }
}

@Composable
private fun InvDetailRow(label: String, value: String) {
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
            modifier = Modifier.weight(0.42f),
        )
        Text(
            value,
            fontFamily = JetBrainsMonoFamily,
            fontSize = 10.sp,
            color = Spectrum.OnSurface,
            modifier = Modifier.weight(0.58f),
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// Keep EditDeviceDialog as Material — functional dialog, no Spectrum equivalent needed yet
@Composable
fun EditDeviceDialog(
    device: DiscoveredDeviceEntity,
    onDismiss: () -> Unit,
    onSave: (label: String, notes: String) -> Unit,
) {
    var label by remember { mutableStateOf(device.customLabel ?: "") }
    var notes by remember { mutableStateOf(device.notes ?: "") }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_edit_device)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "${device.name} (${device.address})",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 11.sp,
                    color = Spectrum.OnSurfaceDim,
                )
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text(stringResource(R.string.label_custom)) },
                    placeholder = { Text(stringResource(R.string.ph_custom_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Spectrum.Accent,
                        focusedLabelColor = Spectrum.Accent,
                        cursorColor = Spectrum.Accent,
                    ),
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.label_notes)) },
                    placeholder = { Text(stringResource(R.string.ph_custom_notes)) },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Spectrum.Accent,
                        focusedLabelColor = Spectrum.Accent,
                        cursorColor = Spectrum.Accent,
                    ),
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = { onSave(label, notes) }) {
                Text(stringResource(R.string.btn_save), color = Spectrum.Accent)
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel), color = Spectrum.OnSurfaceDim)
            }
        },
    )
}

private fun formatRelativeTime(instant: Instant, context: android.content.Context): String {
    val now = Instant.now()
    val minutes = ChronoUnit.MINUTES.between(instant, now)
    val hours = ChronoUnit.HOURS.between(instant, now)
    val days = ChronoUnit.DAYS.between(instant, now)
    return when {
        minutes < 1 -> context.getString(R.string.time_just_now)
        minutes < 60 -> context.getString(R.string.time_mins_ago, minutes)
        hours < 24 -> context.getString(R.string.time_hours_ago, hours)
        days < 7 -> context.getString(R.string.time_days_ago, days)
        else -> DateTimeFormatter.ofPattern("dd.MM.yy").withZone(ZoneId.systemDefault()).format(instant)
    }
}
