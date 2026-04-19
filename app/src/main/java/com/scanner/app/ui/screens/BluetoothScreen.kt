package com.scanner.app.ui.screens

import android.Manifest
import android.os.Build
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.BluetoothDisabled
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.scanner.app.data.BluetoothDevice
import com.scanner.app.data.BondState
import com.scanner.app.data.repository.DeviceRepository
import com.scanner.app.ui.components.BluetoothDeviceCard
import com.scanner.app.util.BluetoothScanner
import com.scanner.app.util.GattExplorer
import kotlinx.coroutines.launch

/**
 * Main screen for discovered Bluetooth devices. Orchestrates scanning for both Classic and BLE
 * devices via [BluetoothScanner], and deep GATT exploration via [GattExplorer]. Manages runtime
 * permissions for Bluetooth (Scan/Connect) and location across different API levels.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun BluetoothScreen() {
    val context = LocalContext.current
    val btScanner = remember { BluetoothScanner(context) }
    val repository = remember { DeviceRepository(context) }
    val gattExplorer = remember { GattExplorer(context) }
    val scope = rememberCoroutineScope()

    var devices by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    var isScanning by remember { mutableStateOf(false) }
    var hasScanned by remember { mutableStateOf(false) }
    var gattAddress by remember { mutableStateOf<String?>(null) }
    val gattState by gattExplorer.state.collectAsState()

    val permissions = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            add(Manifest.permission.BLUETOOTH)
            add(Manifest.permission.BLUETOOTH_ADMIN)
        }
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    val permissionState = rememberMultiplePermissionsState(permissions)

    LaunchedEffect(gattState.connectionState, gattAddress) {
        if (gattState.connectionState == com.scanner.app.util.ConnectionState.READY &&
                        gattAddress != null
        ) {
            val json = buildGattJson(gattState)
            repository.persistGattData(gattAddress!!, json)
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            btScanner.cleanup()
            gattExplorer.disconnect()
        }
    }

    /**
     * Triggers a Bluetooth scan using the [BluetoothScanner]. Captures results in real-time,
     * updates local state, and persists the final set to the repository.
     */
    fun doScan() {
        if (!btScanner.isBluetoothEnabled()) return
        if (!permissionState.allPermissionsGranted) return
        isScanning = true
        val startTime = System.currentTimeMillis()
        btScanner.startScan(
                onProgress = { results ->
                    try {
                        devices = results
                    } catch (_: Exception) {}
                },
                onComplete = { results ->
                    try {
                        devices = results
                        isScanning = false
                        hasScanned = true
                    } catch (_: Exception) {}

                    try {
                        scope.launch {
                            try {
                                repository.persistBluetoothScan(
                                        devices = results,
                                        durationMs = System.currentTimeMillis() - startTime
                                )
                            } catch (e: Exception) {
                                android.util.Log.e("BluetoothScreen", "Error persisting scan", e)
                            }
                        }
                    } catch (_: Exception) {}
                }
        )
    }

    if (gattAddress != null) {
        GattDetailView(
                state = gattState,
                onDisconnect = {
                    gattExplorer.disconnect()
                    gattAddress = null
                }
        )
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                    modifier =
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                            text = "Bluetooth-Geräte",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                    )
                    if (hasScanned || devices.isNotEmpty()) {
                        val connected = devices.count { it.isConnected }
                        val bonded = devices.count { it.bondState == BondState.BONDED }
                        Text(
                                text =
                                        "${devices.size} gefunden" +
                                                (if (connected > 0) " · $connected verbunden"
                                                else "") +
                                                (if (bonded > 0) " · $bonded gekoppelt" else ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                FilledTonalButton(
                        onClick = {
                            if (!permissionState.allPermissionsGranted) {
                                permissionState.launchMultiplePermissionRequest()
                            } else {
                                doScan()
                            }
                        },
                        enabled = !isScanning
                ) {
                    if (isScanning) {
                        CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = "Scannen")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isScanning) "Scanne..." else "Scannen")
                }
            }

            if (!permissionState.allPermissionsGranted) {
                Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        colors =
                                CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer
                                )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                                text = "Berechtigungen erforderlich",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                                text =
                                        "Bluetooth- und Standort-Berechtigungen werden für den Scan benötigt.",
                                style = MaterialTheme.typography.bodySmall,
                                color =
                                        MaterialTheme.colorScheme.onErrorContainer.copy(
                                                alpha = 0.7f
                                        )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                                onClick = { permissionState.launchMultiplePermissionRequest() },
                                colors =
                                        ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.error
                                        )
                        ) { Text("Berechtigungen erteilen") }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (!btScanner.isBluetoothEnabled()) {
                Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        colors =
                                CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                                )
                ) {
                    Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                                Icons.Outlined.BluetoothDisabled,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                                text = "Bluetooth ist deaktiviert. Bitte Bluetooth einschalten.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (devices.isEmpty() && hasScanned) {
                Box(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        contentAlignment = Alignment.Center
                ) {
                    Text(
                            text = "Keine Geräte gefunden.\nVersuche es erneut.",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (devices.isEmpty()) {
                Box(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        contentAlignment = Alignment.Center
                ) {
                    Text(
                            text = "Tippe auf \"Scannen\" um\nBluetooth-Geräte zu finden.",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val connected = devices.filter { it.isConnected }
                    val bonded =
                            devices.filter { !it.isConnected && it.bondState == BondState.BONDED }
                    val others =
                            devices.filter { !it.isConnected && it.bondState != BondState.BONDED }

                    if (connected.isNotEmpty()) {
                        item {
                            Text(
                                    text = "VERBUNDEN",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                        items(connected, key = { it.address }) { device ->
                            BluetoothDeviceCard(
                                    device = device,
                                    onGattExplore = { address ->
                                        gattAddress = address
                                        gattExplorer.connect(address)
                                    }
                            )
                        }
                    }

                    if (bonded.isNotEmpty()) {
                        item {
                            Text(
                                    text = "GEKOPPELT",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                        items(bonded, key = { it.address }) { device ->
                            BluetoothDeviceCard(
                                    device = device,
                                    onGattExplore = { address ->
                                        gattAddress = address
                                        gattExplorer.connect(address)
                                    }
                            )
                        }
                    }

                    if (others.isNotEmpty()) {
                        item {
                            Text(
                                    text = "IN REICHWEITE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                        items(others, key = { it.address }) { device ->
                            BluetoothDeviceCard(
                                    device = device,
                                    onGattExplore = { address ->
                                        gattAddress = address
                                        gattExplorer.connect(address)
                                    }
                            )
                        }
                    }

                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }
    }
}
