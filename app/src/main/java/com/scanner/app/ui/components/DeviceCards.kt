package com.scanner.app.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import com.scanner.app.data.*
import com.scanner.app.ui.theme.ScannerAppTheme
import com.scanner.app.util.SignalHelper

// ─── Signal Strength Bar ───────────────────────────────────────

@Composable
fun SignalBar(
    fraction: Float,
    modifier: Modifier = Modifier
) {
    val color = SignalHelper.signalColor(fraction)
    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "signal"
    )

    Box(
        modifier = modifier
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(animatedFraction)
                .clip(RoundedCornerShape(3.dp))
                .background(color)
        )
    }
}

// ─── WiFi Network Card ─────────────────────────────────────────

@Composable
fun WifiNetworkCard(network: WifiNetwork) {
    var expanded by remember { mutableStateOf(false) }
    val fraction = SignalHelper.signalFraction(network.signalStrength)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(
            containerColor = if (network.isConnected)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Icon
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (network.isConnected)
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            else
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                        )
                ) {
                    Icon(
                        imageVector = if (network.securityType == "Offen" || network.securityType.contains("OWE"))
                            Icons.Outlined.WifiOff else Icons.Outlined.Wifi,
                        contentDescription = null,
                        tint = if (network.isConnected)
                            MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = network.ssid,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        if (network.isConnected) {
                            Spacer(modifier = Modifier.width(8.dp))
                            StatusChip("Verbunden", MaterialTheme.colorScheme.primary)
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${network.band} · Kanal ${network.channel}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        SecurityChip(network.securityType)
                        if (network.wpsEnabled) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Surface(
                                color = Color(0xFFF57C00).copy(alpha = 0.15f),
                                shape = RoundedCornerShape(3.dp)
                            ) {
                                Text(
                                    text = "WPS",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFF57C00),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                }

                // Signal indicator
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${network.signalStrength} dBm",
                        style = MaterialTheme.typography.labelSmall,
                        color = SignalHelper.signalColor(fraction)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SignalBar(
                        fraction = fraction,
                        modifier = Modifier.width(48.dp)
                    )
                }
            }

            // Expanded details
            if (expanded) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(12.dp))

                DetailRow("BSSID", network.bssid)
                network.vendor?.let { DetailRow("Hersteller", it) }
                network.wifiStandard?.let {
                    val bw = if (network.channelWidth != null) " (${network.channelWidth})" else ""
                    DetailRow("WLAN Standard", "$it$bw")
                }
                DetailRow("Frequenz", "${network.frequency} MHz")
                DetailRow("Signalqualität", SignalHelper.wifiQuality(network.signalStrength))
                network.distance?.let {
                    DetailRow("Distanz (Schätzung)", java.lang.String.format(java.util.Locale.getDefault(), "ca. %.1f m", it))
                }
                DetailRow("Sicherheit", network.securityType)
            }
        }
    }
}

// ─── Bluetooth Device Card ──────────────────────────────────────

@Composable
fun BluetoothDeviceCard(
    device: BluetoothDevice,
    onGattExplore: ((String) -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(
            containerColor = if (device.isConnected)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Icon based on device class
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (device.isConnected)
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            else
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                        )
                ) {
                    Icon(
                        imageVector = deviceIcon(device.deviceClass),
                        contentDescription = null,
                        tint = if (device.isConnected)
                            MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = device.displayName(),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (device.isConnected) {
                            Spacer(modifier = Modifier.width(8.dp))
                            StatusChip("Verbunden", MaterialTheme.colorScheme.primary)
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = device.subtitle(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // RSSI + bond state
                Column(horizontalAlignment = Alignment.End) {
                    device.rssi?.let { rssi ->
                        val fraction = SignalHelper.signalFraction(rssi)
                        Text(
                            text = "$rssi dBm",
                            style = MaterialTheme.typography.labelSmall,
                            color = SignalHelper.signalColor(fraction)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        SignalBar(
                            fraction = fraction,
                            modifier = Modifier.width(48.dp)
                        )
                    }
                    if (device.bondState == BondState.BONDED) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Gekoppelt",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Expanded details
            if (expanded) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(12.dp))

                DetailRow("MAC-Adresse", device.address)
                device.vendor?.let { DetailRow("Hersteller", it) }
                DetailRow("Typ", device.type.displayName())
                device.deviceClass?.let { DetailRow("Geräteklasse", it) }
                device.minorClass?.let { DetailRow("Geräteart", it) }
                DetailRow("Kopplungsstatus", device.bondState.displayName())
                device.rssi?.let {
                    DetailRow("Signalstärke", "${it} dBm (${SignalHelper.bluetoothQuality(it)})")
                }
                device.txPower?.let { DetailRow("TX Power", "$it dBm") }
                if (device.serviceUuids.isNotEmpty()) {
                    DetailRow("Services", "${device.serviceUuids.size} erkannt")
                }

                // GATT Explorer button for BLE/DUAL devices
                if (onGattExplore != null &&
                    (device.type == com.scanner.app.data.DeviceType.BLE ||
                     device.type == com.scanner.app.data.DeviceType.DUAL)) {
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    Spacer(modifier = Modifier.height(10.dp))
                    FilledTonalButton(
                        onClick = { onGattExplore(device.address) },
                        modifier = Modifier.fillMaxWidth().height(36.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Bluetooth,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("GATT Explorer — Verbinden & Services lesen")
                    }
                }
            }
        }
    }
}

// ─── Helper Composables ─────────────────────────────────────────

@Composable
fun StatusChip(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun SecurityChip(security: String) {
    val color = when {
        security == "Offen" -> Color(0xFFF44336)
        security.contains("OWE") -> Color(0xFFFF9800)
        else -> MaterialTheme.colorScheme.outline
    }
    val icon = if (security == "Offen" || security.contains("OWE")) Icons.Outlined.LockOpen else Icons.Outlined.Lock
    Icon(
        imageVector = icon,
        contentDescription = security,
        tint = color,
        modifier = Modifier.size(14.dp)
    )
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun deviceIcon(deviceClass: String?): ImageVector = when (deviceClass) {
    "Computer" -> Icons.Outlined.Computer
    "Telefon" -> Icons.Outlined.PhoneAndroid
    "Audio/Video" -> Icons.Outlined.Headphones
    "Peripherie" -> Icons.Outlined.Keyboard
    "Bildgebung" -> Icons.Outlined.Print
    "Wearable" -> Icons.Outlined.Watch
    "Gesundheit" -> Icons.Outlined.MonitorHeart
    "Netzwerk" -> Icons.Outlined.Router
    else -> Icons.Outlined.Bluetooth
}

@Preview(showBackground = true)
@Composable
fun PreviewWifiNetworkCard() {
    ScannerAppTheme {
        WifiNetworkCard(
            network = WifiNetwork(
                ssid = "Home_Network",
                bssid = "00:11:22:33:44:55",
                signalStrength = -55,
                frequency = 5240,
                channel = 48,
                securityType = "WPA3",
                isConnected = true,
                band = "5 GHz",
                wpsEnabled = true
            )
        )
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewBluetoothDeviceCard() {
    ScannerAppTheme {
        BluetoothDeviceCard(
            device = BluetoothDevice(
                address = "AA:BB:CC:DD:EE:FF",
                name = "My Headphones",
                rssi = -60,
                isConnected = true,
                bondState = BondState.BONDED,
                type = com.scanner.app.data.DeviceType.CLASSIC,
                deviceClass = "Audio/Video",
                minorClass = "Headphones"
            )
        )
    }
}
