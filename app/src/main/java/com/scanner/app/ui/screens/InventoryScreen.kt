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
    onNavigateToDevice: ((Long) -> Unit)? = null,
) {
    val context = LocalContext.current
    val repository = remember { DeviceRepository(context) }
    val scope = rememberCoroutineScope()

    var kind by remember { mutableStateOf("all") }
    var favOnly by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

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

    var editDialogDevice by remember { mutableStateOf<DiscoveredDeviceEntity?>(null) }
    var showExportDialog by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().background(Spectrum.Surface)) {

        SpectrumHeader(
            kicker = "INVENTORY",
            subtitle = "Catalog",
            trailing = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .border(1.dp, Spectrum.AccentDim, RoundedCornerShape(999.dp))
                        .clickable { showExportDialog = true }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Icon(
                        Icons.Outlined.Download,
                        contentDescription = null,
                        tint = Spectrum.Accent,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        "EXPORT",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 11.sp,
                        color = Spectrum.Accent,
                        letterSpacing = 0.12.em,
                    )
                }
            },
        )

        // Inline search
        InvSearchBar(query = searchQuery, onQueryChange = { searchQuery = it })
        HairlineHorizontal()

        // Filter chips
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SpectrumFilterChip("ALL", kind == "all", { kind = "all" }, count = totalCount)
            SpectrumFilterChip("WIFI", kind == "wifi", { kind = "wifi" }, count = wifiCount)
            SpectrumFilterChip("BT", kind == "bt", { kind = "bt" }, count = btCount)
            SpectrumFilterChip("LAN", kind == "lan", { kind = "lan" }, count = lanCount)
            SpectrumFilterChip("★ FAVS", favOnly, { favOnly = !favOnly })
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
                        onEdit = { editDialogDevice = device },
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
            onDismiss = { editDialogDevice = null },
            onSave = { label, notes ->
                scope.launch {
                    repository.setCustomLabel(device.id, label.ifBlank { null })
                    repository.setNotes(device.id, notes.ifBlank { null })
                }
                editDialogDevice = null
            },
        )
    }

    if (showExportDialog) {
        ExportDialog(onDismiss = { showExportDialog = false })
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
                            "search · name · mac · label",
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
                    contentDescription = "Löschen",
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
            SpectrumKicker("— NO MATCHES —", color = Spectrum.OnSurfaceDim)
            Text(
                if (query.isNotBlank())
                    "Keine Geräte für \"$query\" gefunden."
                else
                    "Noch keine Geräte gespeichert.\nStarte einen WLAN- oder Bluetooth-Scan.",
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
                    if (device.isFavorite) {
                        Text("★", color = Spectrum.Accent, fontSize = 11.sp)
                    }
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
                    "${device.timesSeen}× gesehen",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 10.sp,
                    color = Spectrum.OnSurfaceDim,
                )
                Text(
                    formatRelativeTime(device.lastSeen),
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 9.sp,
                    color = Spectrum.OnSurfaceFaint,
                )
            }

            Box {
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(if (device.isFavorite) "Aus Favoriten entfernen" else "Als Favorit") },
                        onClick = { onToggleFavorite(); showMenu = false },
                    )
                    DropdownMenuItem(
                        text = { Text("Bearbeiten") },
                        onClick = { onEdit(); showMenu = false },
                    )
                    DropdownMenuItem(
                        text = { Text("Löschen", color = Spectrum.Danger) },
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
        InvDetailRow("Adresse", device.address)
        InvDetailRow("Kategorie", device.deviceCategory.displayName())
        InvDetailRow("Zuerst gesehen", timeFormatter.format(device.firstSeen))
        InvDetailRow("Zuletzt gesehen", timeFormatter.format(device.lastSeen))
        InvDetailRow("Anzahl Sichtungen", "${device.timesSeen}")
        device.lastSignalStrength?.let { InvDetailRow("Letzte Signalstärke", "$it dBm") }
        device.customLabel?.let { InvDetailRow("Label", it) }
        device.notes?.let { InvDetailRow("Notizen", it) }

        if (meta != null) {
            Spacer(Modifier.height(6.dp))
            HairlineHorizontal()
            Spacer(Modifier.height(6.dp))

            when (device.deviceCategory) {
                DeviceCategory.WIFI -> {
                    SpectrumKicker("WLAN-DETAILS", color = Spectrum.Accent)
                    Spacer(Modifier.height(4.dp))
                    meta.optString("vendor").takeIf { it.isNotBlank() }?.let { InvDetailRow("Hersteller", it) }
                    meta.optString("wifiStandard").takeIf { it.isNotBlank() }?.let { std ->
                        val bw = meta.optString("channelWidth")
                        InvDetailRow("WLAN Standard", if (bw.isNotBlank()) "$std ($bw)" else std)
                    }
                    meta.optInt("frequency", 0).takeIf { it > 0 }?.let { InvDetailRow("Frequenz", "$it MHz") }
                    meta.optInt("channel", 0).takeIf { it > 0 }?.let { InvDetailRow("Kanal", "$it") }
                    meta.optString("band").takeIf { it.isNotBlank() }?.let { InvDetailRow("Band", it) }
                    meta.optString("security").takeIf { it.isNotBlank() }?.let { InvDetailRow("Sicherheit", it) }
                    val dist = meta.optDouble("distance", Double.NaN)
                    if (!dist.isNaN()) InvDetailRow("Distanz (Schätzung)", "ca. %.1fm".format(dist))
                    if (meta.optBoolean("wpsEnabled")) InvDetailRow("WPS", "Aktiviert")
                    val lat = meta.optDouble("latitude", Double.NaN)
                    val lon = meta.optDouble("longitude", Double.NaN)
                    if (!lat.isNaN() && !lon.isNaN()) {
                        InvDetailRow("GPS", "%.6f, %.6f".format(lat, lon))
                        val alt = meta.optDouble("altitude", Double.NaN)
                        if (!alt.isNaN()) InvDetailRow("Höhe", "%.1f m".format(alt))
                    }
                    if (meta.optBoolean("isConnected")) InvDetailRow("Status", "Verbunden")
                }

                DeviceCategory.BT_CLASSIC, DeviceCategory.BT_BLE, DeviceCategory.BT_DUAL -> {
                    SpectrumKicker("BLUETOOTH-DETAILS", color = Spectrum.Accent)
                    Spacer(Modifier.height(4.dp))
                    meta.optString("deviceClass").takeIf { it.isNotBlank() }?.let { InvDetailRow("Geräteklasse", it) }
                    meta.optString("bondState").takeIf { it.isNotBlank() }?.let {
                        InvDetailRow("Kopplung", when (it) {
                            "BONDED" -> "Gekoppelt"; "BONDING" -> "Kopplung..."; else -> "Nicht gekoppelt"
                        })
                    }
                    if (meta.optBoolean("isConnected")) InvDetailRow("Status", "Verbunden")
                    val gattServices = meta.optJSONObject("gattData")?.optJSONArray("services")
                    if (gattServices != null && gattServices.length() > 0) {
                        Spacer(Modifier.height(4.dp))
                        SpectrumKicker("GATT-DIENSTE · ${gattServices.length()}", color = Spectrum.Accent2)
                        Spacer(Modifier.height(4.dp))
                        for (i in 0 until gattServices.length()) {
                            val svc = gattServices.optJSONObject(i) ?: continue
                            InvDetailRow(
                                svc.optString("name", "Unbekannt"),
                                "${svc.optJSONArray("characteristics")?.length() ?: 0} CHRs",
                            )
                        }
                    }
                }

                DeviceCategory.LAN -> {
                    SpectrumKicker("LAN-DETAILS", color = Spectrum.Accent)
                    Spacer(Modifier.height(4.dp))
                    meta.optString("ip").takeIf { it.isNotBlank() }?.let { InvDetailRow("IP-Adresse", it) }
                    meta.optString("mac").takeIf { it.isNotBlank() }?.let { InvDetailRow("MAC-Adresse", it) }
                    (meta.optString("vendorFull").takeIf { it.isNotBlank() }
                        ?: meta.optString("vendor").takeIf { it.isNotBlank() })?.let { InvDetailRow("Hersteller", it) }
                    meta.optString("hostname").takeIf { it.isNotBlank() }?.let { InvDetailRow("Hostname / NetBIOS", it) }
                    meta.optDouble("latencyMs", -1.0).takeIf { it >= 0 }?.let { InvDetailRow("Latenz", "${"%.1f".format(it)} ms") }
                    if (meta.optBoolean("isGateway")) InvDetailRow("Rolle", "Gateway / Router")
                    if (meta.optBoolean("isOwnDevice")) InvDetailRow("Rolle", "Eigenes Gerät")
                    val services = meta.optJSONArray("services")
                    if (services != null && services.length() > 0) {
                        Spacer(Modifier.height(4.dp))
                        SpectrumKicker("DIENSTE · ${services.length()}", color = Spectrum.Accent2)
                        Spacer(Modifier.height(4.dp))
                        for (i in 0 until services.length()) {
                            val svc = services.optJSONObject(i) ?: continue
                            InvDetailRow(svc.optString("type", "Dienst"), "${svc.optString("name", "")} :${svc.optInt("port", 0)}")
                        }
                    }
                    val openPorts = meta.optJSONArray("openPorts")
                    if (openPorts != null && openPorts.length() > 0) {
                        Spacer(Modifier.height(4.dp))
                        SpectrumKicker("OFFENE PORTS · ${openPorts.length()}", color = Spectrum.SeverityHigh)
                        Spacer(Modifier.height(4.dp))
                        for (i in 0 until openPorts.length()) {
                            val p = openPorts.optJSONObject(i) ?: continue
                            InvDetailRow(
                                "Port ${p.optInt("port", 0)} (${p.optString("service", "")})",
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
        title = { Text("Gerät bearbeiten") },
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
                    label = { Text("Eigenes Label") },
                    placeholder = { Text("z.B. Drucker 2. OG") },
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
                    label = { Text("Notizen") },
                    placeholder = { Text("Freitext-Notizen...") },
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
                Text("Speichern", color = Spectrum.Accent)
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("Abbrechen", color = Spectrum.OnSurfaceDim)
            }
        },
    )
}

private fun formatRelativeTime(instant: Instant): String {
    val now = Instant.now()
    val minutes = ChronoUnit.MINUTES.between(instant, now)
    val hours = ChronoUnit.HOURS.between(instant, now)
    val days = ChronoUnit.DAYS.between(instant, now)
    return when {
        minutes < 1 -> "gerade eben"
        minutes < 60 -> "vor ${minutes}min"
        hours < 24 -> "vor ${hours}h"
        days < 7 -> "vor ${days}d"
        else -> DateTimeFormatter.ofPattern("dd.MM.yy").withZone(ZoneId.systemDefault()).format(instant)
    }
}
