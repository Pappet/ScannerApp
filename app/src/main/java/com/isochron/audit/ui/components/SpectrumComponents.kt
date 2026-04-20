package com.scanner.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import com.scanner.app.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.scanner.app.ui.theme.JetBrainsMonoFamily
import com.scanner.app.ui.theme.Spectrum
import kotlin.math.sin

// ── Kicker (uppercase mono label) ────────────────────────────
@Composable
fun SpectrumKicker(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Spectrum.OnSurfaceDim,
) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        color = color,
        fontFamily = JetBrainsMonoFamily,
        fontSize = 10.sp,
        letterSpacing = 0.18.em,
    )
}

// ── Section label, like "NEARBY · 12" ────────────────────────
@Composable
fun SpectrumSectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        color = Spectrum.OnSurfaceDim,
        fontFamily = JetBrainsMonoFamily,
        fontSize = 10.sp,
        letterSpacing = 0.2.em,
    )
}

// ── Header: kicker + big display subtitle + scan button + stats ──
data class HeaderStat(val value: String, val label: String)

@Composable
fun SpectrumHeader(
    kicker: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    scanning: Boolean? = null,
    onScan: (() -> Unit)? = null,
    stats: List<HeaderStat> = emptyList(),
    trailing: @Composable (() -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth().background(Spectrum.Surface)) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.weight(1f)) {
                    SpectrumKicker(stringResource(R.string.scanner_kicker, kicker.uppercase()))
                    Spacer(Modifier.height(2.dp))
                    Text(
                        subtitle,
                        color = Spectrum.OnSurface,
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = (-0.02).em,
                    )
                }
                when {
                    trailing != null -> trailing()
                    onScan != null -> SpectrumScanButton(
                        scanning = scanning == true,
                        onClick = onScan,
                    )
                }
            }
            if (stats.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    stats.forEach { s ->
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                s.value,
                                color = Spectrum.OnSurface,
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 14.sp,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                s.label,
                                color = Spectrum.OnSurfaceDim,
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 11.sp,
                            )
                        }
                    }
                }
            }
        }
        HairlineHorizontal()
    }
}

// ── Scan button: pill, blinking dot, fills accent when scanning ──
@Composable
fun SpectrumScanButton(
    scanning: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    val displayLabel = label ?: if (scanning) stringResource(R.string.btn_scanning) else stringResource(R.string.btn_scan)
    val bg = if (scanning) Spectrum.Accent else Color.Transparent
    val fg = if (scanning) Spectrum.Surface else Spectrum.Accent
    val borderColor = if (scanning) Spectrum.Accent else Spectrum.AccentDim

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .clip(CircleShape)
            .background(bg)
            .border(1.dp, borderColor, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        BlinkingDot(color = fg, blink = scanning, size = 7.dp)
        Text(
            displayLabel,
            color = fg,
            fontFamily = JetBrainsMonoFamily,
            fontSize = 11.sp,
            letterSpacing = 0.12.em,
        )
    }
}

@Composable
fun BlinkingDot(
    color: Color,
    blink: Boolean,
    size: Dp = 7.dp,
) {
    val tr = rememberInfiniteTransition(label = "blink")
    val alpha by if (blink) {
        tr.animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing)),
            label = "blink-a",
        )
    } else {
        remember { mutableStateOf(1f) }
    }
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha)),
    )
}

// ── Filter chip (pill, accent-filled when selected) ──────────
@Composable
fun SpectrumFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    count: Int? = null,
) {
    val bg = if (selected) Spectrum.Accent else Color.Transparent
    val fg = if (selected) Spectrum.Surface else Spectrum.OnSurfaceDim
    val borderColor = if (selected) Spectrum.Accent else Spectrum.GridLine
    val shape = RoundedCornerShape(6.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .clip(shape)
            .background(bg)
            .border(1.dp, borderColor, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            label,
            color = fg,
            fontFamily = JetBrainsMonoFamily,
            fontSize = 10.sp,
            letterSpacing = 0.1.em,
        )
        if (count != null) {
            Text(
                count.toString(),
                color = fg.copy(alpha = 0.6f),
                fontFamily = JetBrainsMonoFamily,
                fontSize = 10.sp,
            )
        }
    }
}

// ── Bottom nav ───────────────────────────────────────────────
data class SpectrumTab(
    val key: String,
    val icon: ImageVector,
    val label: String,
)

@Composable
fun SpectrumBottomNav(
    tabs: List<SpectrumTab>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        HairlineHorizontal()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Spectrum.Surface),
        ) {
            tabs.forEach { t ->
                val isSel = t.key == selected
                val color = if (isSel) Spectrum.Accent else Spectrum.OnSurfaceDim
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelect(t.key) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSel) {
                        Box(
                            Modifier
                                .align(Alignment.TopCenter)
                                .fillMaxWidth(0.5f)
                                .height(2.dp)
                                .background(Spectrum.Accent),
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = t.icon,
                            contentDescription = t.label,
                            tint = color,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            t.label,
                            color = color,
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 8.sp,
                            letterSpacing = 0.1.em,
                        )
                    }
                }
            }
        }
    }
}

// ── Oscilloscope-style RSSI trace ────────────────────────────
@Composable
fun SignalTrace(
    rssi: Int,
    modifier: Modifier = Modifier,
) {
    val pct = ((rssi.coerceIn(-95, -30) + 95) / 65f).coerceIn(0f, 1f)
    val color = when {
        pct > 0.6f -> Spectrum.Accent
        pct > 0.3f -> Spectrum.Warning
        else -> Spectrum.Danger
    }
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val amp = (pct * 9f).coerceAtLeast(1f)
        val steps = 20
        val path = Path()
        for (i in 0..steps) {
            val x = (i / steps.toFloat()) * w
            val y = h / 2f + sin(i * 0.9f + rssi) * amp * 0.5f
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}

// ── RSSI / utilization → color helpers ───────────────────────
fun rssiColor(rssi: Int): Color = when {
    rssi > -60 -> Spectrum.Accent
    rssi > -75 -> Spectrum.Warning
    else -> Spectrum.Danger
}

fun utilColor(utilPct: Int): Color = when {
    utilPct > 70 -> Spectrum.Danger
    utilPct > 40 -> Spectrum.Warning
    else -> Spectrum.Accent
}

// ── 1-dp divider ─────────────────────────────────────────────
@Composable
fun HairlineHorizontal(color: Color = Spectrum.GridLine, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(color),
    )
}
