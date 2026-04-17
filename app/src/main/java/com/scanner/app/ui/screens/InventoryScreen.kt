package com.scanner.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scanner.app.data.db.*
import com.scanner.app.data.repository.DeviceRepository
import com.scanner.app.ui.components.SignalBar
import com.scanner.app.ui.components.StatusChip
import com.scanner.app.util.SignalHelper
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

// ─── Filter Enum ────────────────────────────────────────────────

enum class InventoryFilter(val label: String) {
    ALL("Alle"),
    WIFI("WLAN"),
    BLUETOOTH("Bluetooth"),
    LAN("LAN"),
    FAVORITES("Favoriten"),
    RECENT("Letzte 24h")
}

// ─── Inventory Screen ───────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    onNavigateToDevice: ((Long) -> Unit)? = null
) {
    val context = LocalContext.current
    val repository = remember { DeviceRepository(context) }
    val scope = rememberCoroutineScope()

    var activeFilter by remember { mutableStateOf(InventoryFilter.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    // Observe devices based on filter
    val devices by when {
        searchQuery.isNotBlank() -> repository.searchDevices(searchQuery)
        activeFilter == InventoryFilter.WIFI ->
            repository.observeDevicesByCategory(DeviceCategory.WIFI)
        activeFilter == InventoryFilter.LAN ->
            repository.observeDevicesByCategory(DeviceCategory.LAN)
        activeFilter == InventoryFilter.BLUETOOTH ->
            repository.observeAllDevices()
        activeFilter == InventoryFilter.FAVORITES ->
            repository.observeFavorites()
        activeFilter == InventoryFilter.RECENT ->
            repository.observeRecentDevices(24)
        else -> repository.observeAllDevices()
    }.collectAsState(initial = emptyList())

    // Filter BT devices in-memory when BT filter is active
    val filteredDevices = remember(devices, activeFilter) {
        when (activeFilter) {
            InventoryFilter.BLUETOOTH -> devices.filter {
                it.deviceCategory in listOf(
                    DeviceCategory.BT_CLASSIC,
                    DeviceCategory.BT_BLE,
                    DeviceCategory.BT_DUAL
                )
            }
            else -> devices
        }
    }

    val totalCount by repository.observeTotalDeviceCount().collectAsState(initial = 0)
    val wifiCount by repository.observeWifiCount().collectAsState(initial = 0)
    val btCount by repository.observeBluetoothCount().collectAsState(initial = 0)

    // Edit dialog state
    var editDialogDevice by remember { mutableStateOf<DiscoveredDeviceEntity?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        // ─── Header with search ─────────────────────────────────
        if (isSearchActive) {
            SearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                onSearch = { },
                active = false,
                onActiveChange = { },
                leadingIcon = {
                    IconButton(onClick = {
                        isSearchActive = false
                        searchQuery = ""
                    }) {
                        @Suppress("DEPRECATION")
                        Icon(Icons.Default.ArrowBack, contentDescription = "Zurück")
                    }
                },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Löschen")
                        }
                    }
                },
                placeholder = { Text("Name, Label, MAC suchen...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {}
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Geräte-Inventar",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "$totalCount Geräte · $wifiCount WiFi · $btCount BT",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { isSearchActive = true }) {
                    Icon(Icons.Default.Search, contentDescription = "Suchen")
                }
            }
        }

        // ─── Filter chips ───────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            InventoryFilter.entries.forEach { filter ->
                FilterChip(
                    selected = activeFilter == filter,
                    onClick = { activeFilter = filter },
                    label = { Text(filter.label, style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.height(32.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ─── Device list ────────────────────────────────────────
        if (filteredDevices.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Outlined.Inventory2,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (searchQuery.isNotBlank())
                            "Keine Geräte für \"$searchQuery\" gefunden."
                        else
                            "Noch keine Geräte gespeichert.\nStarte einen WLAN- oder Bluetooth-Scan.",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filteredDevices, key = { it.id }) { device ->
                    InventoryDeviceCard(
                        device = device,
                        onToggleFavorite = {
                            scope.launch { repository.toggleFavorite(device.id) }
                        },
                        onEdit = { editDialogDevice = device },
                        onDelete = {
                            scope.launch { repository.deleteDevice(device.id) }
                        }
                    )
                }
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }

    // ─── Edit Dialog ────────────────────────────────────────────
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
            }
        )
    }
}

// ─── Device Card for Inventory ──────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InventoryDeviceCard(
    device: DiscoveredDeviceEntity,
    onToggleFavorite: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    val timeFormatter = remember {
        DateTimeFormatter.ofPattern("dd.MM.yy HH:mm")
            .withZone(ZoneId.systemDefault())
    }

    // Parse metadata JSON
    val meta = remember(device.metadata) {
        try {
            device.metadata?.let { org.json.JSONObject(it) }
        } catch (_: Exception) { null }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .combinedClickable(
                onClick = { expanded = !expanded },
                onLongClick = { showMenu = true }
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Category icon
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        )
                ) {
                    Icon(
                        imageVector = categoryIcon(device.deviceCategory),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Device info
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = device.displayName(),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (device.isFavorite) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                Icons.Default.Star,
                                contentDescription = "Favorit",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        StatusChip(
                            device.deviceCategory.shortName(),
                            MaterialTheme.colorScheme.tertiary
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    // Quick info line
                    Text(
                        text = buildString {
                            append(device.address)
                            // Add a quick metadata preview
                            when (device.deviceCategory) {
                                DeviceCategory.WIFI -> {
                                    meta?.optString("band")?.takeIf { it.isNotBlank() }?.let { append(" · $it") }
                                    meta?.optString("security")?.takeIf { it.isNotBlank() }?.let { append(" · $it") }
                                }
                                DeviceCategory.LAN -> {
                                    meta?.optString("ip")?.takeIf { it.isNotBlank() }?.let { append(" · $it") }
                                }
                                else -> {
                                    meta?.optString("deviceClass")?.takeIf { it.isNotBlank() }?.let { append(" · $it") }
                                }
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "Zuletzt: ${formatRelativeTime(device.lastSeen)} · Erstmals: ${timeFormatter.format(device.firstSeen)} · ${device.timesSeen}×",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }

                // Signal + menu
                Column(horizontalAlignment = Alignment.End) {
                    device.lastSignalStrength?.let { dbm ->
                        val fraction = SignalHelper.signalFraction(dbm)
                        Text(
                            text = "$dbm dBm",
                            style = MaterialTheme.typography.labelSmall,
                            color = SignalHelper.signalColor(fraction)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        SignalBar(fraction = fraction, modifier = Modifier.width(40.dp))
                    }

                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "Optionen",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (device.isFavorite) "Aus Favoriten entfernen"
                                        else "Als Favorit"
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        if (device.isFavorite) Icons.Default.StarBorder
                                        else Icons.Default.Star,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                onClick = { onToggleFavorite(); showMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Bearbeiten") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                onClick = { onEdit(); showMenu = false }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Löschen", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                onClick = { onDelete(); showMenu = false }
                            )
                        }
                    }
                }
            }

            // ─── Expanded Details ───────────────────────────────
            if (expanded) {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                Spacer(modifier = Modifier.height(10.dp))

                // Basic fields always shown
                MetaRow("Adresse", device.address)
                MetaRow("Kategorie", device.deviceCategory.displayName())
                MetaRow("Zuerst gesehen", timeFormatter.format(device.firstSeen))
                MetaRow("Zuletzt gesehen", timeFormatter.format(device.lastSeen))
                MetaRow("Anzahl Sichtungen", "${device.timesSeen}")
                device.lastSignalStrength?.let { MetaRow("Letzte Signalstärke", "$it dBm") }
                device.customLabel?.let { MetaRow("Label", it) }
                device.notes?.let { MetaRow("Notizen", it) }

                // ─── Category-specific metadata ─────────────────
                if (meta != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                    Spacer(modifier = Modifier.height(8.dp))

                    when (device.deviceCategory) {
                        DeviceCategory.WIFI -> {
                            Text(
                                text = "WLAN-DETAILS",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            meta.optString("vendor").takeIf { it.isNotBlank() }?.let { MetaRow("Hersteller", it) }
                            meta.optString("wifiStandard").takeIf { it.isNotBlank() }?.let { std ->
                                val bw = meta.optString("channelWidth")
                                val widthStr = if (bw.isNotBlank()) " ($bw)" else ""
                                MetaRow("WLAN Standard", "$std$widthStr")
                            }
                            meta.optInt("frequency", 0).takeIf { it > 0 }?.let { MetaRow("Frequenz", "$it MHz") }
                            meta.optInt("channel", 0).takeIf { it > 0 }?.let { MetaRow("Kanal", "$it") }
                            meta.optString("band").takeIf { it.isNotBlank() }?.let { MetaRow("Band", it) }
                            meta.optString("security").takeIf { it.isNotBlank() }?.let { MetaRow("Sicherheit", it) }
                            val dist = meta.optDouble("distance", Double.NaN)
                            if (!dist.isNaN()) MetaRow("Distanz (Schätzung)", java.lang.String.format(java.util.Locale.getDefault(), "ca. %.1f m", dist))
                            if (meta.optBoolean("wpsEnabled")) MetaRow("WPS", "Aktiviert")
                            meta.optString("rawCapabilities").takeIf { it.isNotBlank() }?.let { MetaRow("Fähigkeiten", it) }

                            val lat = meta.optDouble("latitude", Double.NaN)
                            val lon = meta.optDouble("longitude", Double.NaN)
                            if (!lat.isNaN() && !lon.isNaN()) {
                                MetaRow("GPS", "%.6f, %.6f".format(lat, lon))
                                val alt = meta.optDouble("altitude", Double.NaN)
                                if (!alt.isNaN()) MetaRow("Höhe", "%.1f m".format(alt))
                            }

                            if (meta.optBoolean("isConnected")) MetaRow("Status", "Verbunden")
                        }

                        DeviceCategory.BT_CLASSIC, DeviceCategory.BT_BLE, DeviceCategory.BT_DUAL -> {
                            Text(
                                text = "BLUETOOTH-DETAILS",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            meta.optString("deviceClass").takeIf { it.isNotBlank() }?.let { MetaRow("Geräteklasse", it) }
                            meta.optString("bondState").takeIf { it.isNotBlank() }?.let {
                                MetaRow("Kopplung", when (it) {
                                    "BONDED" -> "Gekoppelt"
                                    "BONDING" -> "Kopplung..."
                                    else -> "Nicht gekoppelt"
                                })
                            }
                            if (meta.optBoolean("isConnected")) MetaRow("Status", "Verbunden")

                            val gattData = meta.optJSONObject("gattData")
                            if (gattData != null) {
                                val services = gattData.optJSONArray("services")
                                if (services != null && services.length() > 0) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "GATT-DIENSTE (${services.length()})",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    for (i in 0 until services.length()) {
                                        val svc = services.optJSONObject(i) ?: continue
                                        val name = svc.optString("name", "Unbekannt")
                                        val charsArr = svc.optJSONArray("characteristics")
                                        val charsCount = charsArr?.length() ?: 0
                                        MetaRow(name, "$charsCount CHRs")
                                    }
                                }
                            }
                        }

                        DeviceCategory.LAN -> {
                            Text(
                                text = "LAN-DETAILS",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            meta.optString("ip").takeIf { it.isNotBlank() }?.let { MetaRow("IP-Adresse", it) }
                            meta.optString("mac").takeIf { it.isNotBlank() }?.let { MetaRow("MAC-Adresse", it) }
                            meta.optString("vendorFull").takeIf { it.isNotBlank() }?.let { MetaRow("Hersteller", it) }
                                ?: meta.optString("vendor").takeIf { it.isNotBlank() }?.let { MetaRow("Hersteller", it) }
                            meta.optString("hostname").takeIf { it.isNotBlank() }?.let { MetaRow("Hostname / NetBIOS", it) }
                            meta.optString("discoveredVia").takeIf { it.isNotBlank() }?.let {
                                MetaRow("Entdeckt via", when (it) {
                                    "ARP" -> "ARP-Tabelle"
                                    "PING_SWEEP" -> "Ping-Sweep"
                                    "MDNS" -> "mDNS/Bonjour"
                                    else -> it
                                })
                            }
                            meta.optDouble("latencyMs", -1.0).takeIf { it >= 0 }?.let { MetaRow("Latenz", "${"%.1f".format(it)} ms") }
                            if (meta.optBoolean("isGateway")) MetaRow("Rolle", "Gateway / Router")
                            if (meta.optBoolean("isOwnDevice")) MetaRow("Rolle", "Eigenes Gerät")

                            // Services
                            val services = meta.optJSONArray("services")
                            if (services != null && services.length() > 0) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "DIENSTE (${services.length()})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                for (i in 0 until services.length()) {
                                    val svc = services.optJSONObject(i) ?: continue
                                    MetaRow(
                                        svc.optString("type", "Dienst"),
                                        "${svc.optString("name", "")} :${svc.optInt("port", 0)}"
                                    )
                                }
                            }

                            // UPnP info
                            val hasUpnp = meta.optString("upnpName").isNotBlank() ||
                                    meta.optString("upnpManufacturer").isNotBlank()
                            if (hasUpnp) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "UPnP",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF42A5F5)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                meta.optString("upnpName").takeIf { it.isNotBlank() }?.let { MetaRow("UPnP-Name", it) }
                                meta.optString("upnpManufacturer").takeIf { it.isNotBlank() }?.let { MetaRow("UPnP-Hersteller", it) }
                                meta.optString("upnpModel").takeIf { it.isNotBlank() }?.let { MetaRow("UPnP-Modell", it) }
                                meta.optString("upnpDescription").takeIf { it.isNotBlank() }?.let { MetaRow("Beschreibung", it) }
                                meta.optString("upnpDeviceType").takeIf { it.isNotBlank() }?.let { MetaRow("Gerätetyp", it) }
                                val upnpServices = meta.optJSONArray("upnpServices")
                                if (upnpServices != null && upnpServices.length() > 0) {
                                    val svcList = (0 until upnpServices.length()).map { upnpServices.optString(it) }
                                    MetaRow("UPnP-Dienste", svcList.joinToString(", "))
                                }
                            }

                            // Open ports from port scan
                            val openPorts = meta.optJSONArray("openPorts")
                            if (openPorts != null && openPorts.length() > 0) {
                                Spacer(modifier = Modifier.height(6.dp))
                                val scanTime = meta.optString("portScanTime")
                                Text(
                                    text = "OFFENE PORTS (${openPorts.length()})" +
                                            if (scanTime.isNotBlank()) " · Scan: ${scanTime.take(19)}" else "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFE64A19)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                for (i in 0 until openPorts.length()) {
                                    val port = openPorts.optJSONObject(i) ?: continue
                                    val portNum = port.optInt("port", 0)
                                    val service = port.optString("service", "")
                                    val banner = port.optString("banner", "")
                                    val state = port.optString("state", "")

                                    MetaRow(
                                        "Port $portNum ($service)",
                                        if (banner.isNotBlank()) banner.take(50) else state
                                    )
                                }
                            }
                        }
                    }
                }

                // Raw metadata (for data junkies)
                device.metadata?.let { raw ->
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                    Spacer(modifier = Modifier.height(6.dp))
                    var showRaw by remember { mutableStateOf(false) }
                    Text(
                        text = if (showRaw) "RAW METADATA ▼" else "RAW METADATA ▶",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.clickable { showRaw = !showRaw }
                    )
                    if (showRaw) {
                        Spacer(modifier = Modifier.height(4.dp))
                        val formatted = try {
                            org.json.JSONObject(raw).toString(2)
                        } catch (_: Exception) { raw }
                        Text(
                            text = formatted,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            lineHeight = 14.sp
                        )
                    }
                }
            }
        }
    }
}

// ─── Edit Dialog ────────────────────────────────────────────────

@Composable
fun EditDeviceDialog(
    device: DiscoveredDeviceEntity,
    onDismiss: () -> Unit,
    onSave: (label: String, notes: String) -> Unit
) {
    var label by remember { mutableStateOf(device.customLabel ?: "") }
    var notes by remember { mutableStateOf(device.notes ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Gerät bearbeiten") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "${device.name} (${device.address})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Eigenes Label") },
                    placeholder = { Text("z.B. Drucker 2. OG") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notizen") },
                    placeholder = { Text("Freitext-Notizen...") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(label, notes) }) {
                Text("Speichern")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        }
    )
}

// ─── Helpers ────────────────────────────────────────────────────

private fun categoryIcon(category: DeviceCategory): ImageVector = when (category) {
    DeviceCategory.WIFI -> Icons.Outlined.Wifi
    DeviceCategory.BT_CLASSIC -> Icons.Outlined.Bluetooth
    DeviceCategory.BT_BLE -> Icons.Outlined.Bluetooth
    DeviceCategory.BT_DUAL -> Icons.Outlined.Bluetooth
    DeviceCategory.LAN -> Icons.Outlined.Lan
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
        days < 7 -> "vor ${days} Tagen"
        else -> {
            DateTimeFormatter.ofPattern("dd.MM.yy")
                .withZone(ZoneId.systemDefault())
                .format(instant)
        }
    }
}

@Composable
private fun MetaRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(0.6f),
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
