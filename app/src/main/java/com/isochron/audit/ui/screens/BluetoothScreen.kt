package com.scanner.app.ui.screens

import android.Manifest
import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Mouse
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material.icons.outlined.Watch
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import com.scanner.app.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.scanner.app.data.BluetoothDevice
import com.scanner.app.data.BondState
import com.scanner.app.data.DeviceType
import com.scanner.app.ui.components.HairlineHorizontal
import com.scanner.app.ui.components.HeaderStat
import com.scanner.app.ui.components.SpectrumHeader
import com.scanner.app.ui.components.SpectrumKicker
import com.scanner.app.ui.components.rssiColor
import com.scanner.app.ui.theme.InterFamily
import com.scanner.app.ui.theme.JetBrainsMonoFamily
import com.scanner.app.ui.theme.Spectrum
import com.scanner.app.ui.viewmodel.BluetoothViewModel
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun BluetoothScreen(vm: BluetoothViewModel = viewModel()) {
    val devices = vm.devices
    val isScanning = vm.isScanning
    val hasScanned = vm.hasScanned
    val gattState by vm.gattExplorer.state.collectAsState()

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

    LaunchedEffect(gattState.connectionState, vm.gattAddress) {
        val addr = vm.gattAddress
        if (gattState.connectionState == com.scanner.app.util.ConnectionState.READY && addr != null) {
            val json = buildGattJson(gattState)
            vm.persistGattData(addr, json)
        }
    }

    // GATT detail takes over the screen
    if (vm.gattAddress != null) {
        GattDetailView(
            state = gattState,
            onDisconnect = { vm.closeGatt() },
            vm = vm
        )
        return
    }

    val bondedCount = devices.count { it.bondState == BondState.BONDED }
    val bleCount = devices.count { it.type == DeviceType.BLE || it.type == DeviceType.DUAL }
    val selected = devices.firstOrNull { it.address == vm.selectedAddress }

    val favorites by vm.repository.observeFavorites().collectAsState(initial = emptyList())
    val favoriteAddresses = favorites.map { it.address }.toSet()

    Column(Modifier.fillMaxSize().background(Spectrum.Surface)) {
        SpectrumHeader(
            kicker = "BLUETOOTH",
            subtitle = stringResource(R.string.bt_radar_title),
            scanning = isScanning,
            onScan = {
                if (!permissionState.allPermissionsGranted)
                    permissionState.launchMultiplePermissionRequest()
                else if (permissionState.allPermissionsGranted) vm.scan()
            },
            stats = if (hasScanned) listOf(
                HeaderStat(devices.size.toString(), stringResource(R.string.stat_devices)),
                HeaderStat(bondedCount.toString(), stringResource(R.string.stat_bonded)),
                HeaderStat(bleCount.toString(), "BLE"),
            ) else emptyList(),
        )

        // Permission / disabled banners
        if (!permissionState.allPermissionsGranted) {
            BtBanner(
                text = stringResource(R.string.bt_perm_required),
                color = Spectrum.Danger,
                action = stringResource(R.string.btn_allow),
                onAction = { permissionState.launchMultiplePermissionRequest() },
            )
        }
        if (!vm.isBluetoothEnabled()) {
            BtBanner(
                text = stringResource(R.string.bt_disabled),
                color = Spectrum.Warning,
            )
        }

        LazyColumn(Modifier.fillMaxSize()) {
            item {
                BtRadar(
                    devices = devices,
                    selectedAddress = vm.selectedAddress,
                    isScanning = isScanning,
                    onSelect = { addr -> vm.selectedAddress = addr },
                )
                HairlineHorizontal()
            }

            if (selected != null) {
                item {
                    val favEntity by vm.repository.observeDeviceByAddress(selected.address).collectAsState(initial = null)
                    BtSelectedPanel(
                        device = selected,
                        isFavorite = favEntity?.isFavorite == true,
                        onToggleFavorite = { vm.toggleFavorite(selected.address) },
                        onClose = { vm.selectedAddress = null },
                        onOpenGatt = { vm.openGatt(selected.address) },
                    )
                }
            } else {
                item {
                    Text(
                        stringResource(R.string.nearby_count, devices.size),
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 10.sp,
                        color = Spectrum.OnSurfaceDim,
                        letterSpacing = 0.18.em,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                    )
                }
                val sorted = devices.sortedByDescending { it.rssi ?: -120 }
                items(sorted, key = { it.address }) { d ->
                    BtListRow(device = d, isFavorite = d.address in favoriteAddresses, onClick = { vm.selectedAddress = d.address })
                    HairlineHorizontal()
                }
            }

            if (devices.isEmpty() && !hasScanned) {
                item { BtEmptyState(stringResource(R.string.empty_bt_scan_prompt)) }
            } else if (devices.isEmpty() && hasScanned) {
                item { BtEmptyState(stringResource(R.string.empty_no_devices)) }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun BtRadar(
    devices: List<BluetoothDevice>,
    selectedAddress: String?,
    isScanning: Boolean,
    onSelect: (String) -> Unit,
) {
    val sweep by rememberInfiniteTransition(label = "radar-sweep").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "sweep-angle",
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .padding(18.dp),
    ) {
        val side = min(maxWidth.value, maxHeight.value)
        val radiusDp = side / 2f

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Spectrum.SurfaceRaised, Spectrum.Surface),
                        radius = radiusDp * 2f,
                    ),
                )
                .border(1.dp, Spectrum.GridLine, CircleShape),
        ) {
            // Rings + crosshair + sweep (Canvas)
            Canvas(Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val radius = min(cx, cy)
                val dash = PathEffect.dashPathEffect(floatArrayOf(4f, 6f))

                // 3 dashed rings at 0.33, 0.66, 1.0
                for (r in listOf(0.33f, 0.66f, 1.0f)) {
                    drawCircle(
                        color = Spectrum.GridLine,
                        radius = radius * r,
                        center = Offset(cx, cy),
                        style = Stroke(1f, pathEffect = dash),
                    )
                }

                // Crosshair
                drawLine(
                    color = Spectrum.GridLine,
                    start = Offset(0f, cy),
                    end = Offset(size.width, cy),
                    strokeWidth = 1f,
                )
                drawLine(
                    color = Spectrum.GridLine,
                    start = Offset(cx, 0f),
                    end = Offset(cx, size.height),
                    strokeWidth = 1f,
                )

                // Sweep line (with trailing fade)
                if (isScanning || devices.isNotEmpty()) {
                    val rad = Math.toRadians(sweep.toDouble())
                    val endX = cx + cos(rad).toFloat() * radius
                    val endY = cy + sin(rad).toFloat() * radius
                    drawLine(
                        brush = Brush.linearGradient(
                            colors = listOf(Spectrum.Accent, Color.Transparent),
                            start = Offset(cx, cy),
                            end = Offset(endX, endY),
                        ),
                        start = Offset(cx, cy),
                        end = Offset(endX, endY),
                        strokeWidth = 1.5f,
                    )
                }
            }

            // Device dots as composables for tappability
            devices.forEachIndexed { i, d ->
                val angleDeg = (i * 137.5f) % 360f
                val dist = 1f - rssiPct(d.rssi ?: -95)
                val rad = Math.toRadians(angleDeg.toDouble())
                val dxDp = (cos(rad).toFloat() * dist * 0.42f * side).dp
                val dyDp = (sin(rad).toFloat() * dist * 0.42f * side).dp
                val isSel = d.address == selectedAddress
                val bonded = d.bondState == BondState.BONDED
                val baseColor = if (bonded) Spectrum.Accent else Spectrum.Accent2

                val diff = (sweep - angleDeg + 360f) % 360f
                val lightUpAlpha = if (diff < 45f) 1f - (diff / 45f) else 0f
                val activeColor = androidx.compose.ui.graphics.lerp(baseColor, Color.White, lightUpAlpha)

                val sizeBoost = if (diff < 45f) (lightUpAlpha * 4).dp else 0.dp
                val baseBoxSize = if (isSel) 26.dp else 22.dp

                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(x = dxDp, y = dyDp)
                        .size(baseBoxSize + sizeBoost)
                        .clip(CircleShape)
                        .clickable { onSelect(d.address) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSel) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(Spectrum.Accent.copy(alpha = 0.25f)),
                        )
                    }

                    val iconVector = btTypeIcon(d.minorClass)
                    if (iconVector == Icons.Outlined.Bluetooth && !bonded) {
                        Box(
                            modifier = Modifier
                                .size(if (isSel) 12.dp + sizeBoost else 6.dp + sizeBoost)
                                .clip(CircleShape)
                                .background(activeColor),
                        )
                    } else {
                        Icon(
                            imageVector = iconVector,
                            contentDescription = null,
                            tint = activeColor,
                            modifier = Modifier.size(if (isSel) 18.dp + sizeBoost else 14.dp + sizeBoost)
                        )
                    }
                }
            }

            // Center "you" dot
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(Spectrum.OnSurface)
                    .border(2.dp, Spectrum.Surface, CircleShape),
            )
        }
    }
}

@Composable
private fun BtSelectedPanel(
    device: BluetoothDevice,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onClose: () -> Unit,
    onOpenGatt: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(18.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                btTypeIcon(device.minorClass),
                contentDescription = null,
                tint = Spectrum.Accent,
                modifier = Modifier.size(28.dp),
            )
            Column(Modifier.weight(1f)) {
                val isUnnamed = device.name == "(Unbekannt)" || device.name.isBlank()
                val unknownVendor = stringResource(R.string.unknown_vendor)
                Text(
                    text = if (isUnnamed) stringResource(R.string.device_with_vendor, device.vendor ?: unknownVendor) else device.name,
                    fontFamily = InterFamily,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = (-0.01).em,
                    color = if (isUnnamed) Spectrum.OnSurfaceDim else Spectrum.OnSurface,
                    fontStyle = if (isUnnamed) FontStyle.Italic else FontStyle.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    buildString {
                        device.vendor?.let { append(it); append(" · ") }
                        device.minorClass?.let { append(it); append(" · ") }
                        append(device.type.displayName())
                    }.trimEnd(' ', '·'),
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 11.sp,
                    color = Spectrum.OnSurfaceDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .border(1.dp, Spectrum.GridLine, RoundedCornerShape(4.dp))
                    .clickable(onClick = onToggleFavorite),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Outlined.Star else Icons.Outlined.StarOutline,
                    contentDescription = if (isFavorite) stringResource(R.string.cd_remove_fav) else stringResource(R.string.cd_add_fav),
                    tint = if (isFavorite) Spectrum.Accent else Spectrum.OnSurface,
                    modifier = Modifier.size(14.dp),
                )
            }
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .border(1.dp, Spectrum.GridLine, RoundedCornerShape(4.dp))
                    .clickable { onClose() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.btn_close),
                    tint = Spectrum.OnSurfaceDim,
                    modifier = Modifier.size(14.dp),
                )
            }
        }

        // 2×2 info grid
        Column(Modifier.padding(top = 12.dp)) {
            val rows = listOf(
                "ADDR" to device.address,
                "RSSI" to (device.rssi?.let { "$it dBm" } ?: "—"),
                "BOND" to device.bondState.displayName(),
                "TX" to (device.txPower?.let { "$it dBm" } ?: "—"),
            )
            rows.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth()) {
                    InfoCell(pair[0].first, pair[0].second, Modifier.weight(1f))
                    Spacer(Modifier.width(18.dp))
                    if (pair.size > 1) {
                        InfoCell(pair[1].first, pair[1].second, Modifier.weight(1f))
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        // EXPLORE GATT CTA
        Box(
            modifier = Modifier
                .padding(top = 14.dp)
                .fillMaxWidth()
                .background(Spectrum.Accent, RoundedCornerShape(4.dp))
                .clickable { onOpenGatt() }
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(R.string.btn_explore_gatt),
                fontFamily = JetBrainsMonoFamily,
                fontSize = 11.sp,
                letterSpacing = 0.2.em,
                color = Spectrum.Surface,
            )
        }
    }
}

@Composable
private fun InfoCell(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            fontFamily = JetBrainsMonoFamily,
            fontSize = 11.sp,
            color = Spectrum.OnSurfaceDim,
        )
        Text(
            value,
            fontFamily = JetBrainsMonoFamily,
            fontSize = 11.sp,
            color = Spectrum.OnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun BtListRow(device: BluetoothDevice, isFavorite: Boolean, onClick: () -> Unit) {
    val isUnnamed = device.name == "(Unbekannt)" || device.name.isBlank()
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            btTypeIcon(device.minorClass),
            contentDescription = null,
            tint = Spectrum.OnSurfaceDim,
            modifier = Modifier.size(18.dp),
        )
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
                    text = if (isUnnamed) (device.vendor ?: "—") else device.name,
                    fontFamily = InterFamily,
                    fontSize = 14.sp,
                    color = if (isUnnamed) Spectrum.OnSurfaceDim else Spectrum.OnSurface,
                    fontStyle = if (isUnnamed) FontStyle.Italic else FontStyle.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                "${device.address} · ${device.type.displayName()}",
                fontFamily = JetBrainsMonoFamily,
                fontSize = 10.sp,
                color = Spectrum.OnSurfaceDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        device.rssi?.let { rssi ->
            Text(
                rssi.toString(),
                fontFamily = JetBrainsMonoFamily,
                fontSize = 13.sp,
                color = rssiColor(rssi),
            )
        }
    }
}

@Composable
private fun BtBanner(
    text: String,
    color: Color,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.06f))
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text,
            fontFamily = JetBrainsMonoFamily,
            fontSize = 10.sp,
            color = color,
            modifier = Modifier.weight(1f),
        )
        if (action != null && onAction != null) {
            Spacer(Modifier.width(12.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(2.dp))
                    .border(1.dp, color, RoundedCornerShape(2.dp))
                    .clickable { onAction() }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                Text(action, fontFamily = JetBrainsMonoFamily, fontSize = 9.sp, color = color)
            }
        }
    }
    HairlineHorizontal(color = color.copy(alpha = 0.2f))
}

@Composable
private fun BtEmptyState(message: String) {
    Column(
        Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SpectrumKicker(stringResource(R.string.empty_no_signal), color = Spectrum.OnSurfaceDim)
        Text(
            message,
            color = Spectrum.OnSurfaceDim,
            fontFamily = JetBrainsMonoFamily,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
        )
    }
}

private fun rssiPct(rssi: Int): Float {
    val clamped = max(-95, min(-30, rssi))
    return (clamped + 95) / 65f
}

private fun btTypeIcon(minorClass: String?): ImageVector = when (minorClass?.lowercase()) {
    "headphones", "headset", "audio" -> Icons.Outlined.Headphones
    "tv", "television" -> Icons.Outlined.Tv
    "mouse" -> Icons.Outlined.Mouse
    "tracker", "beacon" -> Icons.Outlined.Sensors
    "wearable", "watch", "smartwatch" -> Icons.Outlined.Watch
    "iot" -> Icons.Outlined.SmartToy
    "hub" -> Icons.Outlined.Hub
    else -> Icons.Outlined.Bluetooth
}
