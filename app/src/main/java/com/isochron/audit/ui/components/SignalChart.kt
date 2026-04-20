package com.scanner.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.scanner.app.R
import com.scanner.app.util.SignalHelper
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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
    } catch (_: Exception) {}
}

/**
 * Data structure representing a single measurement point for signal strength.
 */
data class SignalDataPoint(
    val value: Int,           // dBm
    val timestamp: Instant
)

/**
 * Real-time signal strength chart with smooth curve interpolation and gradient fill.
 * Supports configurable display ranges and grid lines.
 */
@Composable
fun SignalChart(
    dataPoints: List<SignalDataPoint>,
    modifier: Modifier = Modifier,
    label: String = "",
    minDbm: Int = -100,
    maxDbm: Int = -20,
    showGrid: Boolean = true
) {
    val textMeasurer = rememberTextMeasurer()
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)

    val timeFormatter = remember {
        DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())
    }

    Column(modifier = modifier) {
        if (label.isNotBlank()) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(surfaceColor)
                .padding(4.dp)
        ) {
            if (dataPoints.isEmpty()) {
                Text(
                    text = stringResource(R.string.waiting_for_data),
                    style = MaterialTheme.typography.bodySmall,
                    color = labelColor,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                Canvas(modifier = Modifier.fillMaxSize().padding(
                    start = 40.dp, end = 8.dp, top = 8.dp, bottom = 24.dp
                )) {
                    val chartWidth = size.width
                    val chartHeight = size.height
                    if (chartWidth <= 0f || chartHeight <= 0f) return@Canvas
                    val dbmRange = (maxDbm - minDbm).toFloat().coerceAtLeast(1f)


                    if (showGrid) {
                        val gridLevels = listOf(-30, -50, -70, -90)
                        for (level in gridLevels) {
                            if (level in minDbm..maxDbm) {
                                val y = chartHeight * (1f - (level - minDbm) / dbmRange)
                                drawLine(
                                    color = gridColor,
                                    start = Offset(0f, y),
                                    end = Offset(chartWidth, y),
                                    strokeWidth = 1f,
                                    pathEffect = PathEffect.dashPathEffect(
                                        floatArrayOf(6f, 4f)
                                    )
                                )
                                // Label
                                safeDrawText(
                                    textMeasurer = textMeasurer,
                                    text = "${level}",
                                    topLeft = Offset(-38.dp.toPx(), y - 6.dp.toPx()),
                                    style = TextStyle(
                                        color = labelColor,
                                        fontSize = 9.sp
                                    )
                                )
                            }
                        }
                    }


                    if (dataPoints.size >= 2) {
                        val path = Path()
                        val fillPath = Path()

                        val points = dataPoints.mapIndexed { index, dp ->
                            val x = chartWidth * index / (dataPoints.size - 1).coerceAtLeast(1)
                            val y = chartHeight * (1f - (dp.value - minDbm) / dbmRange)
                            Offset(x, y.coerceIn(0f, chartHeight))
                        }

                        // Build line path
                        path.moveTo(points.first().x, points.first().y)
                        fillPath.moveTo(points.first().x, chartHeight)
                        fillPath.lineTo(points.first().x, points.first().y)

                        for (i in 1 until points.size) {
                            // Smooth curve using cubic bezier
                            val prev = points[i - 1]
                            val curr = points[i]
                            val midX = (prev.x + curr.x) / 2

                            path.cubicTo(midX, prev.y, midX, curr.y, curr.x, curr.y)
                            fillPath.cubicTo(midX, prev.y, midX, curr.y, curr.x, curr.y)
                        }

                        fillPath.lineTo(points.last().x, chartHeight)
                        fillPath.close()

                        // Gradient fill
                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    lineColor.copy(alpha = 0.3f),
                                    lineColor.copy(alpha = 0.05f),
                                    Color.Transparent
                                )
                            )
                        )

                        // Line
                        drawPath(
                            path = path,
                            color = lineColor,
                            style = Stroke(
                                width = 2.5f,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )

                        // Current value dot
                        val lastPoint = points.last()
                        val lastFraction = SignalHelper.signalFraction(
                            dataPoints.last().value, minDbm, maxDbm
                        )
                        val dotColor = SignalHelper.signalColor(lastFraction)

                        drawCircle(
                            color = dotColor.copy(alpha = 0.3f),
                            radius = 8f,
                            center = lastPoint
                        )
                        drawCircle(
                            color = dotColor,
                            radius = 4f,
                            center = lastPoint
                        )
                    }


                    if (dataPoints.size >= 2) {
                        val indices = listOf(0, dataPoints.size / 2, dataPoints.lastIndex)
                        for (idx in indices) {
                            val x = chartWidth * idx / (dataPoints.size - 1).coerceAtLeast(1)
                            val timeText = timeFormatter.format(dataPoints[idx].timestamp)
                            safeDrawText(
                                textMeasurer = textMeasurer,
                                text = timeText,
                                topLeft = Offset(
                                    x - 20.dp.toPx(),
                                    chartHeight + 4.dp.toPx()
                                ),
                                style = TextStyle(
                                    color = labelColor,
                                    fontSize = 8.sp
                                )
                            )
                        }
                    }
                }
            }

            // Current value overlay
            if (dataPoints.isNotEmpty()) {
                val lastValue = dataPoints.last().value
                val fraction = SignalHelper.signalFraction(lastValue, minDbm, maxDbm)
                val color = SignalHelper.signalColor(fraction)

                Surface(
                    color = color.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                ) {
                    Text(
                        text = "$lastValue dBm",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = color,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

/**
 * Real-time latency chart for visualizing ping results.
 * Optmized for ms values with dynamic Y-axis scaling based on maximum observed latency.
 */
@Composable
fun LatencyChart(
    dataPoints: List<Pair<Float, Instant>>,  // latencyMs, timestamp
    modifier: Modifier = Modifier,
    label: String? = null
) {
    val displayLabel = label ?: stringResource(R.string.label_latency)
    val textMeasurer = rememberTextMeasurer()
    val lineColor = MaterialTheme.colorScheme.secondary
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)

    val timeFormatter = remember {
        DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())
    }

    Column(modifier = modifier) {
        Text(
            text = displayLabel,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(surfaceColor)
                .padding(4.dp)
        ) {
            if (dataPoints.isEmpty()) {
                Text(
                    text = stringResource(R.string.waiting_for_data),
                    style = MaterialTheme.typography.bodySmall,
                    color = labelColor,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                val maxLatency = (dataPoints.maxOfOrNull { it.first } ?: 100f)
                    .coerceAtLeast(20f) * 1.2f

                Canvas(modifier = Modifier.fillMaxSize().padding(
                    start = 36.dp, end = 8.dp, top = 8.dp, bottom = 20.dp
                )) {
                    val chartWidth = size.width
                    val chartHeight = size.height
                    if (chartWidth <= 0f || chartHeight <= 0f) return@Canvas

                    // Grid
                    val gridLevels = listOf(0f, maxLatency / 3, maxLatency * 2 / 3, maxLatency)
                    for (level in gridLevels) {
                        val y = chartHeight * (1f - level / maxLatency)
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, y),
                            end = Offset(chartWidth, y),
                            strokeWidth = 1f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))
                        )
                        safeDrawText(
                            textMeasurer = textMeasurer,
                            text = "${level.toInt()}ms",
                            topLeft = Offset(-34.dp.toPx(), y - 5.dp.toPx()),
                            style = TextStyle(color = labelColor, fontSize = 8.sp)
                        )
                    }

                    // Line
                    if (dataPoints.size >= 2) {
                        val path = Path()
                        val points = dataPoints.mapIndexed { index, (latency, _) ->
                            val x = chartWidth * index / (dataPoints.size - 1).coerceAtLeast(1)
                            val y = chartHeight * (1f - latency / maxLatency)
                            Offset(x, y.coerceIn(0f, chartHeight))
                        }

                        path.moveTo(points.first().x, points.first().y)
                        for (i in 1 until points.size) {
                            val prev = points[i - 1]
                            val curr = points[i]
                            val midX = (prev.x + curr.x) / 2
                            path.cubicTo(midX, prev.y, midX, curr.y, curr.x, curr.y)
                        }

                        drawPath(
                            path = path,
                            color = lineColor,
                            style = Stroke(width = 2f, cap = StrokeCap.Round)
                        )

                        // Endpoint dot
                        drawCircle(
                            color = lineColor,
                            radius = 4f,
                            center = points.last()
                        )
                    }
                }
            }

            // Current value
            if (dataPoints.isNotEmpty()) {
                val lastLatency = dataPoints.last().first
                val color = when {
                    lastLatency < 30f -> SignalHelper.signalColor(0.9f)
                    lastLatency < 100f -> SignalHelper.signalColor(0.5f)
                    else -> SignalHelper.signalColor(0.1f)
                }
                Surface(
                    color = color.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                ) {
                    Text(
                        text = "${"%.0f".format(lastLatency)} ms",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = color,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}
