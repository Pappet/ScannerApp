package com.scanner.app.ui.screens

import android.Manifest
import android.os.Build
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.launch
import com.scanner.app.util.*

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BleDetailScreen() {
    val context = LocalContext.current
    val btScanner = remember { BluetoothScanner(context) }
    val gattExplorer = remember { GattExplorer(context) }
    val repository = remember { com.scanner.app.data.repository.DeviceRepository(context) }

    var scannedDevices by remember { mutableStateOf<List<com.scanner.app.data.BluetoothDevice>>(emptyList()) }
    var isScanning by remember { mutableStateOf(false) }
    var selectedAddress by remember { mutableStateOf<String?>(null) }

    val gattState by gattExplorer.state.collectAsState()
    val scope = rememberCoroutineScope()

    val permissions = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        add(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    val permissionState = rememberMultiplePermissionsState(permissions)

    LaunchedEffect(gattState.connectionState, selectedAddress) {
        if (gattState.connectionState == ConnectionState.READY && selectedAddress != null) {
            val json = buildGattJson(gattState)
            repository.persistGattData(selectedAddress!!, json)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            btScanner.cleanup()
            gattExplorer.cleanup()
        }
    }

    // If connected to a device, show GATT tree
    if (selectedAddress != null && gattState.connectionState != ConnectionState.DISCONNECTED) {
        GattDetailView(
            state = gattState,
            onDisconnect = {
                gattExplorer.disconnect()
                selectedAddress = null
            }
        )
        return
    }

    // Otherwise show BLE scanner with connect buttons
    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "BLE Explorer",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isScanning) "Scanne BLE-Geräte..."
                    else "${scannedDevices.size} BLE-Geräte gefunden",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            FilledTonalButton(
                onClick = {
                    if (!permissionState.allPermissionsGranted) {
                        permissionState.launchMultiplePermissionRequest()
                    } else {
                        try {
                            isScanning = true
                            btScanner.startScan(
                                durationMs = 8000L,
                                onProgress = { try { scannedDevices = it } catch (_: Exception) {} },
                                onComplete = {
                                    try {
                                        scannedDevices = it
                                        isScanning = false
                                        scope.launch { repository.persistBluetoothScan(it, 8000L) }
                                    } catch (_: Exception) {}
                                }
                            )
                        } catch (e: Exception) {
                            android.util.Log.e("BleDetail", "Error starting scan", e)
                            isScanning = false
                        }
                    }
                },
                enabled = !isScanning
            ) {
                if (isScanning) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Bluetooth, contentDescription = null)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isScanning) "Scanne..." else "BLE Scan")
            }
        }

        if (scannedDevices.isEmpty() && !isScanning) {
            Box(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Outlined.Bluetooth,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Starte einen BLE-Scan um\nGATT-Dienste zu erkunden.",
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
                items(scannedDevices, key = { it.address }) { device ->
                    BleConnectCard(
                        device = device,
                        onConnect = {
                            selectedAddress = device.address
                            gattExplorer.connect(device.address)
                        }
                    )
                }
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }
}

// ─── BLE Connect Card ───────────────────────────────────────────

@Composable
fun BleConnectCard(
    device: com.scanner.app.data.BluetoothDevice,
    onConnect: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
            ) {
                Icon(
                    Icons.Outlined.Bluetooth,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${device.address} · ${device.type.displayName()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            device.rssi?.let { rssi ->
                Text(
                    text = "$rssi dBm",
                    style = MaterialTheme.typography.labelSmall,
                    color = SignalHelper.signalColor(SignalHelper.signalFraction(rssi)),
                    modifier = Modifier.padding(end = 8.dp)
                )
            }

            FilledTonalButton(
                onClick = onConnect,
                modifier = Modifier.height(32.dp),
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) {
                Text("Verbinden", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

// ─── GATT Detail View ───────────────────────────────────────────

@Composable
fun GattDetailView(
    state: GattExplorerState,
    onDisconnect: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Connection header
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                ) {
                    Icon(
                        Icons.Outlined.BluetoothConnected,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.deviceName ?: state.deviceAddress,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val stateColor = when (state.connectionState) {
                            ConnectionState.READY -> Color(0xFF4CAF50)
                            ConnectionState.FAILED -> Color(0xFFF44336)
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(stateColor)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = state.connectionState.label,
                            style = MaterialTheme.typography.bodySmall,
                            color = stateColor
                        )
                        state.rssi?.let { rssi ->
                            Text(
                                text = " · $rssi dBm",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                IconButton(onClick = onDisconnect) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Trennen",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            // Reading progress
            if (state.isReadingCharacteristics && state.readTotal > 0) {
                LinearProgressIndicator(
                    progress = { state.readProgress.toFloat() / state.readTotal },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 8.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                )
            }
        }

        // Error
        state.error?.let { error ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(12.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Summary + Copy button
        if (state.services.isNotEmpty()) {
            val context = LocalContext.current
            val clipboardManager = remember {
                context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${state.services.size} Dienste · ${state.services.sumOf { it.characteristics.size }} Characteristics",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FilledTonalButton(
                    onClick = {
                        val json = buildGattJson(state)
                        val clip = android.content.ClipData.newPlainText("GATT Data", json)
                        clipboardManager.setPrimaryClip(clip)
                        android.widget.Toast.makeText(context, "GATT-Daten kopiert!", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.height(28.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp)
                ) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Kopieren", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        // Service tree
        if (state.connectionState == ConnectionState.CONNECTING ||
            state.connectionState == ConnectionState.DISCOVERING
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = state.connectionState.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.services, key = { it.uuid.toString() }) { service ->
                    ServiceCard(service)
                }

                // ─── Structured Data Section ─────────────────────
                item {
                    var showRaw by remember { mutableStateOf(false) }

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showRaw = !showRaw },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (showRaw) "STRUKTURIERTE DATEN ▼" else "STRUKTURIERTE DATEN ▶",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }

                    if (showRaw) {
                        Spacer(modifier = Modifier.height(6.dp))
                        val json = remember(state.services) { buildGattJson(state) }

                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = json,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                lineHeight = 14.sp,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }
}

// ─── Service Card ───────────────────────────────────────────────

@Composable
fun ServiceCard(service: GattServiceInfo) {
    var expanded by remember { mutableStateOf(service.isStandard) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Category icon
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                ) {
                    Text(
                        text = service.category.emoji,
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = service.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = BleUuidDatabase.formatUuid(service.uuid),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "${service.characteristics.size}",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Characteristics
            if (expanded && service.characteristics.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))

                service.characteristics.forEach { char ->
                    CharacteristicRow(char)
                }
            }
        }
    }
}

// ─── Characteristic Row ─────────────────────────────────────────

@Composable
fun CharacteristicRow(char: GattCharacteristicInfo) {
    var showDetails by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showDetails = !showDetails }
            .padding(vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Indent indicator
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(32.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = char.name,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Properties as chips
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    char.properties.forEach { prop ->
                        val color = when (prop) {
                            CharacteristicProperty.READ -> Color(0xFF4CAF50)
                            CharacteristicProperty.WRITE,
                            CharacteristicProperty.WRITE_NO_RESPONSE -> Color(0xFF2196F3)
                            CharacteristicProperty.NOTIFY,
                            CharacteristicProperty.INDICATE -> Color(0xFFFF9800)
                            else -> MaterialTheme.colorScheme.outline
                        }
                        Surface(
                            color = color.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(3.dp)
                        ) {
                            Text(
                                text = prop.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = color,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                fontSize = 9.sp
                            )
                        }
                    }
                }
            }

            // Value
            char.stringValue?.let { value ->
                Text(
                    text = value.take(20),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1
                )
            }
        }

        // Expanded details
        if (showDetails) {
            Column(modifier = Modifier.padding(start = 13.dp, top = 4.dp)) {
                Text(
                    text = "UUID: ${char.uuid}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                char.value?.let { bytes ->
                    Text(
                        text = "Hex: ${bytes.joinToString(" ") { "%02X".format(it) }}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Bytes: ${bytes.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (char.descriptors.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    char.descriptors.forEach { desc ->
                        Text(
                            text = "↳ ${desc.name}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

// ─── Structured JSON Builder ────────────────────────────────────

internal fun buildGattJson(state: GattExplorerState): String {
    val root = org.json.JSONObject()

    root.put("device", org.json.JSONObject().apply {
        put("name", state.deviceName ?: "Unknown")
        put("address", state.deviceAddress)
        state.rssi?.let { put("rssi", it) }
        put("connectionState", state.connectionState.name)
    })

    root.put("serviceCount", state.services.size)
    root.put("characteristicCount", state.services.sumOf { it.characteristics.size })

    val servicesArr = org.json.JSONArray()
    for (service in state.services) {
        val svcObj = org.json.JSONObject()
        svcObj.put("uuid", service.uuid.toString())
        svcObj.put("name", service.name)
        svcObj.put("isStandard", service.isStandard)
        svcObj.put("category", service.category.name)

        val charsArr = org.json.JSONArray()
        for (char in service.characteristics) {
            val charObj = org.json.JSONObject()
            charObj.put("uuid", char.uuid.toString())
            charObj.put("name", char.name)
            charObj.put("isStandard", char.isStandard)
            charObj.put("properties", org.json.JSONArray(char.properties.map { it.name }))

            char.value?.let { bytes ->
                charObj.put("valueHex", bytes.joinToString(" ") { "%02X".format(it) })
                charObj.put("valueBytes", bytes.size)
                val ascii = bytes.map { b ->
                    val c = b.toInt().toChar()
                    if (c.isLetterOrDigit() || c in " .-_@:;,!?/()[]{}") c else '.'
                }.joinToString("")
                charObj.put("valueAscii", ascii)
            }
            char.stringValue?.let { charObj.put("valueString", it) }

            if (char.descriptors.isNotEmpty()) {
                val descArr = org.json.JSONArray()
                for (desc in char.descriptors) {
                    val descObj = org.json.JSONObject()
                    descObj.put("uuid", desc.uuid.toString())
                    descObj.put("name", desc.name)
                    desc.value?.let { bytes ->
                        descObj.put("valueHex", bytes.joinToString(" ") { "%02X".format(it) })
                    }
                    descArr.put(descObj)
                }
                charObj.put("descriptors", descArr)
            }

            charsArr.put(charObj)
        }
        svcObj.put("characteristics", charsArr)
        servicesArr.put(svcObj)
    }
    root.put("services", servicesArr)

    return root.toString(2)
}
