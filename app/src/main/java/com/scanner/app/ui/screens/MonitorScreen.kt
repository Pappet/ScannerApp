package com.scanner.app.ui.screens

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.scanner.app.service.MonitoringState
import com.scanner.app.service.ScanService
import com.scanner.app.ui.components.*
import com.scanner.app.ui.theme.JetBrainsMonoFamily
import com.scanner.app.ui.theme.Spectrum
import java.time.Instant

@Composable
fun MonitorScreen() {
    val context = LocalContext.current
    var service by remember { mutableStateOf<ScanService?>(null) }
    var bound by remember { mutableStateOf(false) }

    val connection = remember {
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                service = (binder as ScanService.LocalBinder).getService()
                bound = true
            }
            override fun onServiceDisconnected(name: ComponentName?) {
                service = null
                bound = false
            }
        }
    }

    DisposableEffect(Unit) {
        val intent = Intent(context, ScanService::class.java)
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        onDispose { if (bound) context.unbindService(connection) }
    }

    val state by service?.state?.collectAsState()
        ?: remember { mutableStateOf(MonitoringState()) }

    var selectedInterval by remember { mutableStateOf(10) }

    fun toggle() {
        if (state.isRunning) {
            context.startService(
                Intent(context, ScanService::class.java).apply { action = ScanService.ACTION_STOP }
            )
        } else {
            context.startForegroundService(
                Intent(context, ScanService::class.java).apply {
                    action = ScanService.ACTION_START
                    putExtra(ScanService.EXTRA_INTERVAL, selectedInterval)
                }
            )
        }
    }

    val headerStats = listOf(
        HeaderStat("${state.intervalSeconds}s", "interval"),
        HeaderStat("${state.wifiSignalHistory.size}×", "samples"),
        HeaderStat(if (state.isRunning) "FG" else "OFF", "service"),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Spectrum.Surface)
            .verticalScroll(rememberScrollState()),
    ) {
        SpectrumHeader(
            kicker = "MON",
            subtitle = "Live",
            stats = headerStats,
            trailing = { MonStartStopPill(running = state.isRunning, onClick = ::toggle) },
        )

        if (!state.isRunning) {
            Spacer(Modifier.height(12.dp))
            MonIntervalRow(
                selected = selectedInterval,
                onSelect = { selectedInterval = it },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        Spacer(Modifier.height(12.dp))

        val signalValues = remember(state.wifiSignalHistory) {
            state.wifiSignalHistory.map { it.first.toFloat() }
        }
        val gatewayValues = remember(state.gatewayLatency) {
            state.gatewayLatency.map { it.first }
        }
        val internetValues = remember(state.internetLatency) {
            state.internetLatency.map { it.first }
        }

        val metrics = listOf(
            MonMetric(
                label = "SIGNAL",
                unit = "dBm",
                values = signalValues,
                color = Spectrum.Accent,
                currentStr = state.currentSignal?.toString() ?: signalValues.lastOrNull()?.let { "%.0f".format(it) } ?: "—",
            ),
            MonMetric(
                label = "GATEWAY LATENCY",
                unit = "ms",
                values = gatewayValues,
                color = Spectrum.Accent2,
                currentStr = gatewayValues.lastOrNull()?.let { "%.1f".format(it) } ?: "—",
            ),
            MonMetric(
                label = "INTERNET (8.8.8.8)",
                unit = "ms",
                values = internetValues,
                color = Spectrum.Warning,
                currentStr = internetValues.lastOrNull()?.let { "%.0f".format(it) } ?: "—",
            ),
        )

        metrics.forEach { m ->
            MonMetricCard(
                metric = m,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(10.dp))
        }

        // Session stats
        if (state.scanCount > 0) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .border(1.dp, Spectrum.GridLine, RoundedCornerShape(4.dp))
                    .clip(RoundedCornerShape(4.dp))
                    .background(Spectrum.SurfaceRaised)
                    .padding(14.dp),
            ) {
                Column {
                    SpectrumKicker("SITZUNG")
                    Spacer(Modifier.height(8.dp))
                    MonStatRow("Scans", "${state.scanCount}")
                    MonStatRow("Neue Geräte", "${state.newDeviceCount}")
                    signalValues.takeIf { it.isNotEmpty() }?.average()?.let {
                        MonStatRow("Ø Signal", "${"%.0f".format(it)} dBm")
                    }
                    gatewayValues.takeIf { it.isNotEmpty() }?.average()?.let {
                        MonStatRow("Ø Gateway", "${"%.1f".format(it)} ms")
                    }
                    internetValues.takeIf { it.isNotEmpty() }?.average()?.let {
                        MonStatRow("Ø Internet", "${"%.1f".format(it)} ms")
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        Spacer(Modifier.height(100.dp))
    }
}

private data class MonMetric(
    val label: String,
    val unit: String,
    val values: List<Float>,
    val color: Color,
    val currentStr: String,
)

@Composable
private fun MonMetricCard(metric: MonMetric, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .border(1.dp, Spectrum.GridLine, RoundedCornerShape(6.dp))
            .clip(RoundedCornerShape(6.dp))
            .background(Spectrum.SurfaceRaised)
            .padding(14.dp),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    metric.label,
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 10.sp,
                    color = Spectrum.OnSurfaceDim,
                    letterSpacing = 0.18.em,
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        metric.currentStr,
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 26.sp,
                        color = metric.color,
                        letterSpacing = (-0.02).em,
                        lineHeight = 28.sp,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        metric.unit,
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 11.sp,
                        color = Spectrum.OnSurfaceDim,
                        modifier = Modifier.padding(bottom = 3.dp),
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            MonSparkline(
                values = metric.values,
                color = metric.color,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
            )

            Spacer(Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                val avg = metric.values.takeIf { it.isNotEmpty() }?.average()
                Text(
                    text = avg?.let { "AVG ${"%.1f".format(it)}${metric.unit}" } ?: "AVG —",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 10.sp,
                    color = Spectrum.OnSurfaceDim,
                )
                Text(
                    "n=${metric.values.size}",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 10.sp,
                    color = Spectrum.OnSurfaceDim,
                )
            }
        }
    }
}

@Composable
private fun MonSparkline(
    values: List<Float>,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val gridColor = Spectrum.GridLine
    val dashEffect = remember { PathEffect.dashPathEffect(floatArrayOf(4f, 6f), 0f) }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Dashed grid lines at 0, 25%, 50%, 75%
        listOf(0f, 0.25f, 0.5f, 0.75f).forEach { frac ->
            val y = frac * h
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1f,
                pathEffect = dashEffect,
            )
        }

        if (values.size < 2) return@Canvas

        val minV = values.minOrNull()!!
        val maxV = values.maxOrNull()!!
        val range = (maxV - minV).coerceAtLeast(0.001f)

        fun xAt(i: Int) = (i.toFloat() / (values.size - 1)) * w
        fun yAt(v: Float) = h - ((v - minV) / range) * h

        // Fill path
        val fillPath = Path().apply {
            moveTo(xAt(0), yAt(values[0]))
            for (i in 1 until values.size) lineTo(xAt(i), yAt(values[i]))
            lineTo(xAt(values.size - 1), h)
            lineTo(xAt(0), h)
            close()
        }
        drawPath(fillPath, color = color.copy(alpha = 0.08f), style = Fill)

        // Stroke path
        val strokePath = Path().apply {
            moveTo(xAt(0), yAt(values[0]))
            for (i in 1 until values.size) lineTo(xAt(i), yAt(values[i]))
        }
        drawPath(
            strokePath,
            color = color,
            style = Stroke(
                width = 1.4f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}

@Composable
private fun MonStartStopPill(running: Boolean, onClick: () -> Unit) {
    val bg = if (running) Spectrum.Danger else Color.Transparent
    val fg = if (running) Spectrum.Surface else Spectrum.Accent
    val border = if (running) Spectrum.Danger else Spectrum.AccentDim
    val shape = RoundedCornerShape(20.dp)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clip(shape)
            .background(bg)
            .border(1.dp, border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        if (running) BlinkingDot(color = fg, blink = true, size = 6.dp)
        Text(
            if (running) "STOPP" else "START",
            fontFamily = JetBrainsMonoFamily,
            fontSize = 11.sp,
            color = fg,
            letterSpacing = 0.08.em,
        )
    }
}

@Composable
private fun MonIntervalRow(
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        SpectrumKicker("SCAN-INTERVALL")
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(5 to "5 SEK", 10 to "10 SEK", 30 to "30 SEK", 60 to "1 MIN").forEach { (s, label) ->
                SpectrumFilterChip(
                    label = label,
                    selected = selected == s,
                    onClick = { onSelect(s) },
                )
            }
        }
    }
}

@Composable
private fun MonStatRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
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
            fontWeight = FontWeight.Medium,
        )
    }
}
