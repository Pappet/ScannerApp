package com.scanner.app.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.scanner.app.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.scanner.app.ui.components.HairlineHorizontal
import com.scanner.app.ui.theme.InterFamily
import com.scanner.app.ui.theme.JetBrainsMonoFamily
import com.scanner.app.ui.theme.Spectrum
import com.scanner.app.ui.viewmodel.BluetoothViewModel
import com.scanner.app.util.BleUuidDatabase
import com.scanner.app.util.CharacteristicProperty
import com.scanner.app.util.ConnectionState
import com.scanner.app.util.GattCharacteristicInfo
import com.scanner.app.util.GattExplorerState
import com.scanner.app.util.GattServiceInfo

/**
 * Spectrum-styled GATT explorer. Matches AGatt handoff:
 * header with LINK indicator, stats strip, accordion service list,
 * bottom sheet on characteristic tap.
 */
@Composable
fun GattDetailView(
    state: GattExplorerState,
    onDisconnect: () -> Unit,
    vm: BluetoothViewModel = viewModel()
) {
    // Auto-open first service once discovery completes
    if (vm.openSvc == null && state.services.isNotEmpty()) {
        vm.openSvc = state.services.first().uuid.toString()
    }

    Box(Modifier.fillMaxSize().background(Spectrum.Surface)) {
        Column(Modifier.fillMaxSize()) {
            GattHeader(state, onDisconnect)

            when (state.connectionState) {
                ConnectionState.CONNECTING,
                ConnectionState.DISCOVERING,
                ConnectionState.CONNECTED -> GattBusy(state.connectionState.label)
                else -> GattServiceList(
                    state = state,
                    openSvc = vm.openSvc,
                    onToggleSvc = { uuid ->
                        vm.openSvc = if (vm.openSvc == uuid) null else uuid
                    },
                    onSelectChar = { vm.selChar = it },
                )
            }
        }

        vm.selChar?.let { char ->
            CharacteristicSheet(
                char = char,
                onClose = { vm.selChar = null },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun GattHeader(state: GattExplorerState, onDisconnect: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Spectrum.Surface)
            .padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconSquareButton(
                icon = Icons.Outlined.Close,
                contentDescription = stringResource(R.string.btn_disconnect),
                onClick = onDisconnect,
            )
            Column(Modifier.weight(1f)) {
                Text(
                    "BT / GATT",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 10.sp,
                    color = Spectrum.OnSurfaceDim,
                    letterSpacing = 0.18.em,
                )
                val nameEmpty = state.deviceName.isNullOrBlank()
                Text(
                    if (nameEmpty) state.deviceAddress else state.deviceName!!,
                    fontFamily = InterFamily,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = (-0.02).em,
                    color = if (nameEmpty) Spectrum.OnSurfaceDim else Spectrum.OnSurface,
                    fontStyle = if (nameEmpty) FontStyle.Italic else FontStyle.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            LinkBadge(state.connectionState)
        }

        if (state.connectionState == ConnectionState.READY ||
            state.connectionState == ConnectionState.READING
        ) {
            Row(
                modifier = Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                StatPair(state.deviceAddress.ifBlank { "—" }, null)
                StatPair(state.rssi?.toString() ?: "—", "dBm")
                StatPair(state.services.size.toString(), "svc")
                StatPair(state.services.sumOf { it.characteristics.size }.toString(), "chr")
            }
        }

        if (state.isReadingCharacteristics && state.readTotal > 0) {
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { state.readProgress.toFloat() / state.readTotal },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = Spectrum.Accent,
                trackColor = Spectrum.GridLine,
            )
        }

        state.error?.let { err ->
            Spacer(Modifier.height(10.dp))
            Text(
                "⚠ $err",
                fontFamily = JetBrainsMonoFamily,
                fontSize = 11.sp,
                color = Spectrum.Danger,
                letterSpacing = 0.1.em,
            )
        }
    }
    HairlineHorizontal()
}

@Composable
private fun StatPair(value: String, unit: String?) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            value,
            fontFamily = JetBrainsMonoFamily,
            fontSize = 11.sp,
            color = Spectrum.OnSurface,
        )
        if (unit != null) {
            Spacer(Modifier.width(4.dp))
            Text(
                unit,
                fontFamily = JetBrainsMonoFamily,
                fontSize = 11.sp,
                color = Spectrum.OnSurfaceDim,
            )
        }
    }
}

@Composable
private fun LinkBadge(conn: ConnectionState) {
    val linked = conn == ConnectionState.READY ||
            conn == ConnectionState.READING ||
            conn == ConnectionState.DISCOVERING
    val color = if (linked) Spectrum.Accent else Spectrum.OnSurfaceDim
    val label = when (conn) {
        ConnectionState.READY, ConnectionState.READING -> stringResource(R.string.val_link)
        ConnectionState.DISCOVERING -> stringResource(R.string.val_scan)
        ConnectionState.CONNECTING -> stringResource(R.string.val_wait)
        ConnectionState.FAILED -> stringResource(R.string.val_fail)
        else -> stringResource(R.string.val_off)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        BlinkingDot(color = color, blink = linked)
        Text(
            label,
            fontFamily = JetBrainsMonoFamily,
            fontSize = 10.sp,
            color = color,
            letterSpacing = 0.14.em,
        )
    }
}

@Composable
private fun BlinkingDot(color: Color, blink: Boolean) {
    val tr = androidx.compose.animation.core.rememberInfiniteTransition(label = "gatt-blink")
    val alpha by if (blink) {
        tr.animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing)),
            label = "gatt-blink-a",
        )
    } else {
        remember { mutableStateOf(1f) }
    }
    Box(
        Modifier
            .size(7.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha)),
    )
}

@Composable
private fun GattBusy(label: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            PulseRing()
            Spacer(Modifier.height(14.dp))
            Text(
                label.uppercase(),
                fontFamily = JetBrainsMonoFamily,
                fontSize = 11.sp,
                color = Spectrum.OnSurfaceDim,
                letterSpacing = 0.2.em,
            )
        }
    }
}

@Composable
private fun PulseRing() {
    val tr = androidx.compose.animation.core.rememberInfiniteTransition(label = "gatt-pulse")
    val scale by tr.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
        label = "pulse-s",
    )
    val a by tr.animateFloat(
        initialValue = 0.6f,
        targetValue = 0.1f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
        label = "pulse-a",
    )
    Box(
        Modifier
            .size((48f * scale).dp)
            .clip(CircleShape)
            .border(1.dp, Spectrum.Accent.copy(alpha = a), CircleShape),
    )
}

@Composable
private fun GattServiceList(
    state: GattExplorerState,
    openSvc: String?,
    onToggleSvc: (String) -> Unit,
    onSelectChar: (GattCharacteristicInfo) -> Unit,
) {
    if (state.services.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.empty_no_services),
                fontFamily = JetBrainsMonoFamily,
                fontSize = 11.sp,
                color = Spectrum.OnSurfaceDim,
                letterSpacing = 0.2.em,
            )
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(state.services, key = { it.uuid.toString() }) { svc ->
            ServiceAccordion(
                svc = svc,
                open = openSvc == svc.uuid.toString(),
                onToggle = { onToggleSvc(svc.uuid.toString()) },
                onSelectChar = onSelectChar,
            )
        }
    }
}

@Composable
private fun ServiceAccordion(
    svc: GattServiceInfo,
    open: Boolean,
    onToggle: () -> Unit,
    onSelectChar: (GattCharacteristicInfo) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                BleUuidDatabase.formatUuid(svc.uuid),
                fontFamily = JetBrainsMonoFamily,
                fontSize = 11.sp,
                color = Spectrum.Accent,
                modifier = Modifier.width(58.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    svc.name,
                    fontFamily = InterFamily,
                    fontSize = 13.sp,
                    color = Spectrum.OnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(R.string.kicker_chars, svc.category.label.uppercase(), svc.characteristics.size),
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 10.sp,
                    color = Spectrum.OnSurfaceDim,
                    letterSpacing = 0.12.em,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Icon(
                Icons.Outlined.ExpandMore,
                contentDescription = if (open) stringResource(R.string.cd_collapse) else stringResource(R.string.cd_expand),
                tint = Spectrum.OnSurfaceDim,
                modifier = Modifier
                    .size(14.dp)
                    .rotate(if (open) 180f else 0f),
            )
        }
        HairlineHorizontal()
        if (open) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Spectrum.SurfaceRaised),
            ) {
                svc.characteristics.forEach { c ->
                    CharacteristicRow(c, onClick = { onSelectChar(c) })
                    HairlineHorizontal()
                }
            }
        }
    }
}

@Composable
private fun CharacteristicRow(char: GattCharacteristicInfo, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 34.dp, end = 18.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            BleUuidDatabase.formatUuid(char.uuid),
            fontFamily = JetBrainsMonoFamily,
            fontSize = 10.sp,
            color = Spectrum.OnSurfaceDim,
            modifier = Modifier.width(72.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                char.name,
                fontFamily = InterFamily,
                fontSize = 12.sp,
                color = Spectrum.OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val preview = char.stringValue?.take(24)
                ?: char.value?.let { bytes ->
                    bytes.take(6).joinToString(" ") { "%02X".format(it) } +
                            if (bytes.size > 6) "…" else ""
                }
            if (!preview.isNullOrBlank()) {
                Text(
                    preview,
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 10.sp,
                    color = Spectrum.OnSurfaceDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            char.properties.take(3).forEach { prop -> PropertyChip(prop.short()) }
        }
    }
}

private fun CharacteristicProperty.short(): String = when (this) {
    CharacteristicProperty.READ -> "R"
    CharacteristicProperty.WRITE -> "W"
    CharacteristicProperty.WRITE_NO_RESPONSE -> "WN"
    CharacteristicProperty.NOTIFY -> "N"
    CharacteristicProperty.INDICATE -> "I"
    CharacteristicProperty.BROADCAST -> "B"
    CharacteristicProperty.SIGNED_WRITE -> "S"
    CharacteristicProperty.EXTENDED_PROPS -> "X"
}

@Composable
private fun PropertyChip(label: String) {
    Box(
        modifier = Modifier
            .border(1.dp, Spectrum.GridLine, RoundedCornerShape(2.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp),
    ) {
        Text(
            label,
            fontFamily = JetBrainsMonoFamily,
            fontSize = 9.sp,
            color = Spectrum.OnSurfaceDim,
            letterSpacing = 0.05.em,
        )
    }
}

@Composable
private fun CharacteristicSheet(
    char: GattCharacteristicInfo,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Spectrum.Surface)
            .border(width = 1.dp, color = Spectrum.Accent, shape = RoundedCornerShape(0.dp))
            .padding(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                "CHARACTERISTIC",
                fontFamily = JetBrainsMonoFamily,
                fontSize = 10.sp,
                color = Spectrum.Accent,
                letterSpacing = 0.2.em,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clickable(onClick = onClose),
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
        Text(
            char.name,
            fontFamily = InterFamily,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = (-0.01).em,
            color = Spectrum.OnSurface,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            "UUID ${BleUuidDatabase.formatUuid(char.uuid)}",
            fontFamily = JetBrainsMonoFamily,
            fontSize = 11.sp,
            color = Spectrum.OnSurfaceDim,
            modifier = Modifier.padding(top = 2.dp),
        )

        val valueText = char.stringValue
            ?: char.value?.joinToString(" ") { "%02X".format(it) }
            ?: stringResource(R.string.empty_no_value)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .background(Spectrum.SurfaceRaised, RoundedCornerShape(4.dp))
                .border(1.dp, Spectrum.GridLine, RoundedCornerShape(4.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    valueText,
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 12.sp,
                    color = Spectrum.Accent,
                    lineHeight = 16.sp,
                )
            }
        }

        if (char.descriptors.isNotEmpty()) {
            Text(
                stringResource(R.string.kicker_descriptors, char.descriptors.size),
                fontFamily = JetBrainsMonoFamily,
                fontSize = 10.sp,
                color = Spectrum.OnSurfaceDim,
                letterSpacing = 0.2.em,
                modifier = Modifier.padding(top = 12.dp),
            )
            char.descriptors.forEach { d ->
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        BleUuidDatabase.formatUuid(d.uuid),
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 10.sp,
                        color = Spectrum.OnSurfaceDim,
                    )
                    Text(
                        d.name,
                        fontFamily = InterFamily,
                        fontSize = 11.sp,
                        color = Spectrum.OnSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun IconSquareButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(4.dp))
            .border(1.dp, Spectrum.GridLine, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = Spectrum.OnSurface,
            modifier = Modifier.size(14.dp),
        )
    }
}

/**
 * Converts the current GATT explorer state into a structured JSON string.
 */
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
