package com.scanner.app.ui.screens

import android.Manifest
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.scanner.app.ui.viewmodel.ChannelAnalysisViewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.scanner.app.ui.components.HairlineHorizontal
import com.scanner.app.ui.components.HeaderStat
import com.scanner.app.ui.components.SpectrumFilterChip
import com.scanner.app.ui.components.SpectrumHeader
import com.scanner.app.ui.components.utilColor
import com.scanner.app.ui.theme.InterFamily
import com.scanner.app.ui.theme.JetBrainsMonoFamily
import com.scanner.app.ui.theme.Spectrum
import com.scanner.app.util.ChannelAnalysis
import com.scanner.app.util.ChannelAnalyzer
import com.scanner.app.util.WifiScanner

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ChannelAnalysisScreen(vm: ChannelAnalysisViewModel = viewModel()) {
    val context = LocalContext.current
    
    val analysis = vm.analysis
    val isScanning = vm.isScanning
    val selectedBand = vm.selectedBand

    val permissions = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
    }
    val permissionState = rememberMultiplePermissionsState(permissions)

    fun doScan() {
        if (!vm.wifiScanner.isWifiEnabled()) return
        if (!permissionState.allPermissionsGranted) {
            permissionState.launchMultiplePermissionRequest()
            return
        }
        vm.doScan()
    }

    val channels = if (selectedBand == "2.4") analysis?.channels24 else analysis?.channels5
    val recommendations = if (selectedBand == "2.4") analysis?.recommendations24 else analysis?.recommendations5
    val bestRec = recommendations?.firstOrNull()
    val bestCh = bestRec?.channel ?: "-"
    val bestChInfo = channels?.find { it.channel == bestCh }
    val bestUtil = bestChInfo?.let { (it.overlapScore * 100).toInt() } ?: 0

    Column(modifier = Modifier.fillMaxSize().background(Spectrum.Surface).verticalScroll(rememberScrollState())) {
        SpectrumHeader(
            kicker = "SPECTRUM",
            subtitle = "Channel Analysis",
            scanning = isScanning,
            onScan = { doScan() },
            stats = listOf(
                HeaderStat(value = "CH$bestCh", label = "recommended"),
                HeaderStat(value = "$bestUtil%", label = "utilization"),
                HeaderStat(value = "${channels?.size ?: 0}", label = "channels")
            )
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SpectrumFilterChip(
                label = "2.4 GHZ BAND",
                selected = selectedBand == "2.4",
                onClick = { vm.selectedBand = "2.4" },
                modifier = Modifier.weight(1f)
            )
            SpectrumFilterChip(
                label = "5 GHZ BAND",
                selected = selectedBand == "5",
                onClick = { vm.selectedBand = "5" },
                modifier = Modifier.weight(1f)
            )
        }

        HairlineHorizontal()

        if (channels != null && channels.isNotEmpty()) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "UTILIZATION // DB/CH",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 10.sp,
                    color = Spectrum.OnSurfaceDim,
                    letterSpacing = 0.18.sp,
                    modifier = Modifier.padding(bottom = 10.dp)
                )

                // Oscilloscope Grid
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Spectrum.SurfaceRaised, RoundedCornerShape(6.dp))
                        .border(1.dp, Spectrum.GridLine, RoundedCornerShape(6.dp))
                        .padding(start = 14.dp, end = 14.dp, top = 16.dp, bottom = 10.dp)
                ) {
                    val density = LocalDensity.current.density
                    Canvas(modifier = Modifier.matchParentSize()) {
                        // Background grid lines
                        val stepX = size.width / 10
                        val stepY = size.height / 4
                        for (i in 1..9) {
                            drawLine(
                                color = Spectrum.GridLine,
                                start = Offset(x = stepX * i, y = 0f),
                                end = Offset(x = stepX * i, y = size.height),
                                strokeWidth = 1f * density
                            )
                        }
                        for (i in 1..3) {
                            drawLine(
                                color = Spectrum.GridLine,
                                start = Offset(x = 0f, y = stepY * i),
                                end = Offset(x = size.width, y = stepY * i),
                                strokeWidth = 1f * density
                            )
                        }
                    }

                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth().height(160.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            channels.forEach { ch ->
                                val util = (ch.overlapScore * 100).toInt()
                                val tone = utilColor(util)
                                val hFraction = ch.overlapScore.coerceIn(0f, 1f)
                                val hDp = (hFraction * 150).coerceAtLeast(0f).dp

                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Bottom
                                ) {
                                    Text(
                                        text = "$util",
                                        fontFamily = JetBrainsMonoFamily,
                                        fontSize = 9.sp,
                                        color = tone,
                                        modifier = Modifier.padding(bottom = 3.dp)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(hDp)
                                            .background(tone.copy(alpha = 0.85f), RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                                    ) {
                                        if (ch.networkCount > 0) {
                                            Box(
                                                modifier = Modifier
                                                    .offset(y = (-18).dp)
                                                    .size(16.dp)
                                                    .align(Alignment.TopCenter)
                                                    .background(Spectrum.Surface, CircleShape)
                                                    .border(1.dp, tone, CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "${ch.networkCount}",
                                                    fontFamily = JetBrainsMonoFamily,
                                                    fontSize = 9.sp,
                                                    color = tone
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Channel labels
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            channels.forEach { ch ->
                                Text(
                                    text = "${ch.channel}",
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Center,
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 10.sp,
                                    color = if (ch.channel == bestCh) Spectrum.Accent else Spectrum.OnSurfaceDim
                                )
                            }
                        }
                    }
                }

                // Recommendation Card
                if (bestRec != null) {
                    val count = bestChInfo?.networkCount ?: 0
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 20.dp)
                            .border(1.dp, Spectrum.Accent.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Spectrum.Accent.copy(alpha = 0.08f),
                                        Color.Transparent
                                    )
                                ),
                                RoundedCornerShape(6.dp)
                            )
                            .padding(16.dp)
                    ) {
                        Column {
                            Text(
                                text = "RECOMMENDED",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 10.sp,
                                color = Spectrum.Accent,
                                letterSpacing = 0.2.sp
                            )
                            Row(
                                modifier = Modifier.padding(top = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${bestRec.channel}",
                                    fontFamily = InterFamily,
                                    fontSize = 56.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Spectrum.Accent,
                                    lineHeight = 56.sp,
                                    modifier = Modifier.padding(end = 12.dp).offset(y = (-4).dp)
                                )
                                Column {
                                    Text(
                                        text = "Lowest utilization on band",
                                        fontFamily = InterFamily,
                                        fontSize = 13.sp,
                                        color = Spectrum.OnSurface
                                    )
                                    Text(
                                        text = "$bestUtil% occupied · $count AP${if (count == 1) "" else "s"} · ${if (count == 0) "no overlap" else bestRec.reason}",
                                        fontFamily = JetBrainsMonoFamily,
                                        fontSize = 11.sp,
                                        color = Spectrum.OnSurfaceDim,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Legend
                Row(
                    modifier = Modifier.padding(top = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(modifier = Modifier.size(10.dp).background(Spectrum.Accent))
                        Text(text = "CLEAR", fontFamily = JetBrainsMonoFamily, fontSize = 10.sp, color = Spectrum.OnSurfaceDim)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(modifier = Modifier.size(10.dp).background(Spectrum.Warning))
                        Text(text = "MED", fontFamily = JetBrainsMonoFamily, fontSize = 10.sp, color = Spectrum.OnSurfaceDim)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(modifier = Modifier.size(10.dp).background(Spectrum.Danger))
                        Text(text = "CONGESTED", fontFamily = JetBrainsMonoFamily, fontSize = 10.sp, color = Spectrum.OnSurfaceDim)
                    }
                }
                
            }
        } else {
            // Empty state
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "—",
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 24.sp,
                        color = Spectrum.OnSurfaceDim,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    Text(
                        text = "Tippe auf \"Scannen\" für die\nWLAN-Kanalanalyse.",
                        fontFamily = InterFamily,
                        fontSize = 14.sp,
                        color = Spectrum.OnSurfaceDim,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
