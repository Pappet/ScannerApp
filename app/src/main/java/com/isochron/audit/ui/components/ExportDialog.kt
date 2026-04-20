package com.scanner.app.ui.components

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.scanner.app.R
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.scanner.app.data.db.DeviceCategory
import com.scanner.app.data.repository.DeviceRepository
import com.scanner.app.ui.theme.InterFamily
import com.scanner.app.ui.theme.JetBrainsMonoFamily
import com.scanner.app.ui.theme.Spectrum
import com.scanner.app.util.ExportFilter
import com.scanner.app.util.ExportFormat
import com.scanner.app.util.ExportManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Composable
fun ExportDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val exportManager = remember { ExportManager(context) }
    val repository = remember { DeviceRepository(context) }

    var selectedFormat by remember { mutableStateOf(ExportFormat.CSV) }
    var wifiEnabled by remember { mutableStateOf(true) }
    var btEnabled by remember { mutableStateOf(true) }
    var lanEnabled by remember { mutableStateOf(true) }
    var favOnly by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }

    val wifiCount by repository.observeWifiCount().collectAsState(initial = 0)
    val btCount by repository.observeBluetoothCount().collectAsState(initial = 0)
    val lanCount by repository.observeDevicesByCategory(DeviceCategory.LAN)
        .map { it.size }.collectAsState(initial = 0)

    val totalCount = (if (wifiEnabled) wifiCount else 0) +
            (if (btEnabled) btCount else 0) +
            (if (lanEnabled) lanCount else 0)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xD907090A))
            .clickable { onDismiss() },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) {}
                .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                .background(Spectrum.Surface),
        ) {
            // Accent top border
            Spacer(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Spectrum.Accent),
            )

            Column(Modifier.padding(20.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            "EXPORT",
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 10.sp,
                            color = Spectrum.Accent,
                            letterSpacing = 0.2.em,
                        )
                        Text(
                            stringResource(R.string.export_package_data),
                            fontFamily = InterFamily,
                            fontSize = 22.sp,
                            letterSpacing = (-0.02).em,
                            color = Spectrum.OnSurface,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .border(1.dp, Spectrum.GridLine, RoundedCornerShape(4.dp))
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.btn_close),
                            tint = Spectrum.OnSurface,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }

                // FORMAT
                Text(
                    "FORMAT",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 10.sp,
                    color = Spectrum.OnSurfaceDim,
                    letterSpacing = 0.18.em,
                    modifier = Modifier.padding(top = 16.dp),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    listOf(
                        Triple(ExportFormat.CSV, "CSV", stringResource(R.string.export_desc_csv)),
                        Triple(ExportFormat.JSON, "JSON", stringResource(R.string.export_desc_json)),
                        Triple(ExportFormat.PDF, "PDF", stringResource(R.string.export_desc_pdf)),
                    ).forEach { (fmt, label, desc) ->
                        ExportFormatTile(
                            label = label,
                            description = desc,
                            selected = selectedFormat == fmt,
                            onClick = { selectedFormat = fmt },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                // INCLUDE
                Text(
                    "INCLUDE",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 10.sp,
                    color = Spectrum.OnSurfaceDim,
                    letterSpacing = 0.18.em,
                    modifier = Modifier.padding(top = 18.dp),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    listOf(
                        Triple("WIFI", wifiEnabled, { wifiEnabled = !wifiEnabled }),
                        Triple("BT", btEnabled, { btEnabled = !btEnabled }),
                        Triple("LAN", lanEnabled, { lanEnabled = !lanEnabled }),
                    ).forEach { (label, enabled, toggle) ->
                        ExportIncludeChip(
                            label = label,
                            enabled = enabled,
                            onClick = toggle,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                // FAVORITES ONLY
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .fillMaxWidth()
                        .background(
                            if (favOnly) Spectrum.Accent.copy(alpha = 0.08f) else Color.Transparent,
                            RoundedCornerShape(4.dp),
                        )
                        .border(
                            1.dp,
                            if (favOnly) Spectrum.Accent else Spectrum.GridLine,
                            RoundedCornerShape(4.dp),
                        )
                        .clickable { favOnly = !favOnly }
                        .padding(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .border(
                                1.dp,
                                if (favOnly) Spectrum.Accent else Spectrum.OnSurfaceDim,
                                RoundedCornerShape(2.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (favOnly) {
                            Text(
                                "✓",
                                color = Spectrum.Accent,
                                fontSize = 10.sp,
                                lineHeight = 14.sp,
                            )
                        }
                    }
                    Text(
                        stringResource(R.string.export_favorites_only),
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 11.sp,
                        letterSpacing = 0.12.em,
                        color = Spectrum.OnSurface,
                    )
                }

                // Action button
                val canExport = !isExporting && (wifiEnabled || btEnabled || lanEnabled)
                Box(
                    modifier = Modifier
                        .padding(top = 20.dp)
                        .fillMaxWidth()
                        .background(
                            if (canExport) Spectrum.Accent else Spectrum.AccentDim,
                            RoundedCornerShape(4.dp),
                        )
                        .clickable(enabled = canExport) {
                            doExport(
                                context = context,
                                scope = scope,
                                exportManager = exportManager,
                                format = selectedFormat,
                                wifiEnabled = wifiEnabled,
                                btEnabled = btEnabled,
                                lanEnabled = lanEnabled,
                                favOnly = favOnly,
                                onStart = { isExporting = true },
                                onDone = { isExporting = false; onDismiss() },
                                onError = { msg ->
                                    isExporting = false
                                    Toast.makeText(context, context.getString(R.string.export_failed, msg), Toast.LENGTH_LONG).show()
                                },
                            )
                        }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isExporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Spectrum.Surface,
                        )
                    } else {
                        Text(
                            stringResource(R.string.export_btn_action, totalCount, selectedFormat.name),
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 12.sp,
                            letterSpacing = 0.2.em,
                            color = Spectrum.Surface,
                        )
                    }
                }

                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
        }
    }
}

@Composable
private fun ExportFormatTile(
    label: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(
                if (selected) Spectrum.SurfaceHi else Spectrum.SurfaceRaised,
                RoundedCornerShape(4.dp),
            )
            .border(
                1.dp,
                if (selected) Spectrum.Accent else Spectrum.GridLine,
                RoundedCornerShape(4.dp),
            )
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 12.dp),
    ) {
        Text(
            label,
            fontFamily = InterFamily,
            fontSize = 16.sp,
            color = if (selected) Spectrum.Accent else Spectrum.OnSurface,
        )
        Text(
            description,
            fontFamily = JetBrainsMonoFamily,
            fontSize = 9.sp,
            color = Spectrum.OnSurfaceDim,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}

@Composable
private fun ExportIncludeChip(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(
                if (enabled) Spectrum.Accent else Color.Transparent,
                RoundedCornerShape(4.dp),
            )
            .border(
                1.dp,
                if (enabled) Spectrum.Accent else Spectrum.GridLine,
                RoundedCornerShape(4.dp),
            )
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (enabled) "✓ $label" else label,
            fontFamily = JetBrainsMonoFamily,
            fontSize = 11.sp,
            letterSpacing = 0.14.em,
            color = if (enabled) Spectrum.Surface else Spectrum.OnSurfaceDim,
        )
    }
}

private fun doExport(
    context: Context,
    scope: CoroutineScope,
    exportManager: ExportManager,
    format: ExportFormat,
    wifiEnabled: Boolean,
    btEnabled: Boolean,
    lanEnabled: Boolean,
    favOnly: Boolean,
    onStart: () -> Unit,
    onDone: () -> Unit,
    onError: (String) -> Unit,
) {
    onStart()
    scope.launch {
        try {
            val categories = buildSet {
                if (wifiEnabled) add(DeviceCategory.WIFI)
                if (btEnabled) {
                    add(DeviceCategory.BT_CLASSIC)
                    add(DeviceCategory.BT_BLE)
                    add(DeviceCategory.BT_DUAL)
                }
                if (lanEnabled) add(DeviceCategory.LAN)
            }.takeIf { it.isNotEmpty() }

            val filter = ExportFilter(
                categories = categories,
                favoritesOnly = favOnly,
            )
            val result = exportManager.export(format, filter)
            val shareIntent = exportManager.share(result)
            context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.export_share_title)))
            onDone()
        } catch (e: Exception) {
            onError(e.message ?: context.getString(R.string.export_unknown_error))
        }
    }
}
