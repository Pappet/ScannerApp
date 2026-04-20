package com.scanner.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.scanner.app.R
import com.scanner.app.util.ChannelInfo

/**
 * Safe drawText wrapper for [DrawScope].
 * Skips drawing if position would cause negative constraints or out-of-bounds errors,
 * preventing crashes during rapid UI scaling or animation.
 */
@OptIn(ExperimentalGraphicsApi::class)
private fun DrawScope.safeDrawText(
    textMeasurer: TextMeasurer,
    text: String,
    topLeft: Offset,
    style: TextStyle
) {
    if (topLeft.x < -50f || topLeft.y < -50f) return
    if (topLeft.x > size.width + 50f || topLeft.y > size.height + 50f) return
    try {
        drawText(textMeasurer = textMeasurer, text = text, topLeft = topLeft, style = style)
    } catch (_: Exception) {
        // Skip text that can't be drawn (e.g. negative constraints)
    }
}

/**
 * Bar chart visualizing WiFi network density per channel.
 * Uses the overlap score to colorize bars from green (free) to red (highly congested).
 * Highlights the currently connected channel if provided.
 */
@Composable
fun ChannelBarChart(
    channels: List<ChannelInfo>,
    connectedChannel: Int?,
    modifier: Modifier = Modifier,
    title: String = ""
) {
    val textMeasurer = rememberTextMeasurer()
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val connectedColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)

    Column(modifier = modifier) {
        if (title.isNotBlank()) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(surfaceColor)
                .padding(4.dp)
        ) {
            val maxNetworks = (channels.maxOfOrNull { it.networkCount } ?: 1).coerceAtLeast(1)

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 28.dp, end = 8.dp, top = 12.dp, bottom = 28.dp)
            ) {
                val chartWidth = size.width
                val chartHeight = size.height
                if (chartWidth <= 0f || chartHeight <= 0f) return@Canvas

                val barCount = channels.size
                if (barCount == 0) return@Canvas
                val barSpacing = 2.dp.toPx()
                val barWidth = ((chartWidth / barCount) - barSpacing).coerceAtLeast(1f)

                // Grid lines
                for (i in 0..maxNetworks) {
                    val y = chartHeight * (1f - i.toFloat() / maxNetworks)
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(chartWidth, y),
                        strokeWidth = 1f
                    )
                    if (i > 0) {
                        safeDrawText(
                            textMeasurer = textMeasurer,
                            text = "$i",
                            topLeft = Offset(-24.dp.toPx(), y - 5.dp.toPx()),
                            style = TextStyle(color = labelColor, fontSize = 8.sp)
                        )
                    }
                }


                channels.forEachIndexed { index, ch ->
                    val x = index * (barWidth + barSpacing)
                    val barHeight = if (maxNetworks > 0) {
                        chartHeight * ch.networkCount.toFloat() / maxNetworks
                    } else 0f

                    val isConnected = ch.channel == connectedChannel

                    // Bar color based on overlap score
                    val barColor = if (isConnected) connectedColor
                    else overlapColor(ch.overlapScore)

                    // Draw bar
                    if (barHeight > 0) {
                        drawRoundRect(
                            color = barColor,
                            topLeft = Offset(x, chartHeight - barHeight),
                            size = Size(barWidth, barHeight),
                            cornerRadius = CornerRadius(3f, 3f)
                        )

                        // Overlap heatmap underlay
                        drawRoundRect(
                            color = barColor.copy(alpha = 0.15f),
                            topLeft = Offset(x, 0f),
                            size = Size(barWidth, chartHeight),
                            cornerRadius = CornerRadius(2f, 2f)
                        )
                    }


                    if (ch.networkCount > 0) {
                        safeDrawText(
                            textMeasurer = textMeasurer,
                            text = "${ch.networkCount}",
                            topLeft = Offset(
                                x + barWidth / 2 - 4.dp.toPx(),
                                chartHeight - barHeight - 12.dp.toPx()
                            ),
                            style = TextStyle(
                                color = barColor,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }


                    val labelText = "${ch.channel}"
                    safeDrawText(
                        textMeasurer = textMeasurer,
                        text = labelText,
                        topLeft = Offset(
                            x + barWidth / 2 - (labelText.length * 2.5f).dp.toPx(),
                            chartHeight + 4.dp.toPx()
                        ),
                        style = TextStyle(
                            color = if (isConnected) connectedColor else labelColor,
                            fontSize = 7.sp,
                            fontWeight = if (isConnected) FontWeight.Bold else FontWeight.Normal
                        )
                    )


                    if (isConnected) {
                        drawCircle(
                            color = connectedColor,
                            radius = 2.5f,
                            center = Offset(x + barWidth / 2, chartHeight + 16.dp.toPx())
                        )
                    }
                }
            }
        }
    }
}

/**
 * Spectrum visualization rendering WiFi networks as bell curves on the frequency axis.
 * Uses a cosine approximation to simulate standard signal envelopes and colorizes
 * different networks for visual differentiation.
 */
@Composable
fun SpectrumView(
    channels: List<ChannelInfo>,
    band: String,
    connectedChannel: Int?,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val connectedColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)

    // Color palette for different networks
    val palette = listOf(
        Color(0xFF4CAF50), Color(0xFF2196F3), Color(0xFFFF9800),
        Color(0xFFE91E63), Color(0xFF9C27B0), Color(0xFF00BCD4),
        Color(0xFFFF5722), Color(0xFF607D8B), Color(0xFF795548),
        Color(0xFF8BC34A), Color(0xFF3F51B5), Color(0xFFFFC107)
    )

    val allNetworks = channels.flatMap { it.networks }.distinctBy { it.bssid }

    Column(modifier = modifier) {
        Text(
            text = if (band == "2.4 GHz") stringResource(R.string.spectrum_24_ghz) else stringResource(R.string.spectrum_5_ghz),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(surfaceColor)
                .padding(4.dp)
        ) {
            if (allNetworks.isEmpty()) {
                Text(
                    text = stringResource(R.string.empty_band, band),
                    style = MaterialTheme.typography.bodySmall,
                    color = labelColor,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 24.dp)
                ) {
                    val chartWidth = size.width
                    val chartHeight = size.height
                    if (chartWidth <= 0f || chartHeight <= 0f) return@Canvas

                    val (minFreq, maxFreq) = if (band == "2.4 GHz") {
                        2400f to 2485f
                    } else {
                        5170f to 5835f
                    }
                    val freqRange = maxFreq - minFreq

                    // Grid and channel labels
                    val labelChannels = if (band == "2.4 GHz") {
                        listOf(1, 3, 5, 7, 9, 11, 13)
                    } else {
                        listOf(36, 48, 60, 100, 116, 132, 149, 165)
                    }

                    for (ch in labelChannels) {
                        val freq = channelToFreqFloat(ch)
                        val x = (freq - minFreq) / freqRange * chartWidth

                        drawLine(
                            color = gridColor,
                            start = Offset(x, 0f),
                            end = Offset(x, chartHeight),
                            strokeWidth = 1f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 3f))
                        )

                        safeDrawText(
                            textMeasurer = textMeasurer,
                            text = "$ch",
                            topLeft = Offset(x - 4.dp.toPx(), chartHeight + 4.dp.toPx()),
                            style = TextStyle(
                                color = if (ch == connectedChannel) connectedColor else labelColor,
                                fontSize = 7.sp,
                                fontWeight = if (ch == connectedChannel) FontWeight.Bold
                                    else FontWeight.Normal
                            )
                        )
                    }

                    // Draw each network as a bell curve
                    allNetworks.forEachIndexed { idx, network ->
                        val color = if (network.isConnected) connectedColor
                        else palette[idx % palette.size]

                        val centerFreq = network.frequency.toFloat()
                        val bandwidth = if (band == "2.4 GHz") 22f else 20f
                        val halfBw = bandwidth / 2f

                        // Signal height: stronger signal = taller curve
                        val signalNorm = ((network.signalStrength + 100f) / 70f).coerceIn(0.1f, 1f)
                        val peakHeight = chartHeight * signalNorm * 0.85f

                        val path = Path()
                        val fillPath = Path()
                        val steps = 40

                        fillPath.moveTo(
                            ((centerFreq - halfBw - minFreq) / freqRange * chartWidth)
                                .coerceIn(0f, chartWidth),
                            chartHeight
                        )

                        for (i in 0..steps) {
                            val t = i.toFloat() / steps
                            val freq = centerFreq - halfBw + t * bandwidth
                            val x = ((freq - minFreq) / freqRange * chartWidth)
                                .coerceIn(0f, chartWidth)

                            // Bell curve shape (cosine approximation)
                            val distFromCenter = (freq - centerFreq) / halfBw
                            val bellValue = if (kotlin.math.abs(distFromCenter) <= 1f) {
                                (kotlin.math.cos(distFromCenter * Math.PI).toFloat() + 1f) / 2f
                            } else 0f

                            val y = chartHeight - bellValue * peakHeight

                            if (i == 0) path.moveTo(x, y)
                            else path.lineTo(x, y)
                            fillPath.lineTo(x, y)
                        }

                        fillPath.lineTo(
                            ((centerFreq + halfBw - minFreq) / freqRange * chartWidth)
                                .coerceIn(0f, chartWidth),
                            chartHeight
                        )
                        fillPath.close()

                        // Fill
                        drawPath(
                            path = fillPath,
                            color = color.copy(alpha = 0.12f)
                        )

                        // Outline
                        drawPath(
                            path = path,
                            color = color.copy(alpha = 0.7f),
                            style = Stroke(
                                width = if (network.isConnected) 2.5f else 1.5f,
                                cap = StrokeCap.Round
                            )
                        )

                        // SSID label at peak
                        val peakX = ((centerFreq - minFreq) / freqRange * chartWidth)
                            .coerceIn(20f, chartWidth - 40f)
                        val peakY = chartHeight - peakHeight

                        if (network.ssid.length <= 15) {
                            safeDrawText(
                                textMeasurer = textMeasurer,
                                text = network.ssid.take(12),
                                topLeft = Offset(peakX - 15.dp.toPx(), peakY - 10.dp.toPx()),
                                style = TextStyle(
                                    color = color,
                                    fontSize = 7.sp,
                                    fontWeight = if (network.isConnected) FontWeight.Bold
                                        else FontWeight.Normal
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}



private fun overlapColor(score: Float): Color = when {
    score <= 0.2f -> Color(0xFF4CAF50)   // Green - free
    score <= 0.4f -> Color(0xFF8BC34A)   // Light green
    score <= 0.6f -> Color(0xFFFF9800)   // Orange
    score <= 0.8f -> Color(0xFFFF5722)   // Deep orange
    else -> Color(0xFFF44336)            // Red - congested
}

private fun channelToFreqFloat(channel: Int): Float = when {
    channel in 1..13 -> 2412f + (channel - 1) * 5f
    channel == 14 -> 2484f
    channel in 36..64 -> 5180f + (channel - 36) * 5f
    channel in 100..144 -> 5500f + (channel - 100) * 5f
    channel in 149..165 -> 5745f + (channel - 149) * 5f
    else -> 0f
}
