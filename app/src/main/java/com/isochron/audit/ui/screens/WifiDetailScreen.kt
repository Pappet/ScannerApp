package com.scanner.app.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.scanner.app.data.WifiNetwork
import com.scanner.app.ui.components.rssiColor
import com.scanner.app.ui.theme.InterFamily
import com.scanner.app.ui.theme.JetBrainsMonoFamily
import com.scanner.app.ui.theme.Spectrum
import kotlin.math.cos
import kotlin.math.sin

/**
 * Full-screen WiFi network detail view.
 * Matches Spectrum design from variant-a-extra.jsx (AWifiDetail).
 */
@Composable
fun WifiDetailScreen(
    network: WifiNetwork,
    isFavorite: Boolean = false,
    onClose: () -> Unit,
    onToggleFavorite: () -> Unit = {},
) {
    val risk = network.isRiskFlagged()

    Column(Modifier.fillMaxSize().background(Spectrum.Surface)) {
        // Top bar
        Row(
            Modifier
                .fillMaxWidth()
                .background(Spectrum.Surface)
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconSquareButton(Icons.Outlined.Close, "Schließen", onClose)
            Column(Modifier.weight(1f)) {
                Text(
                    "WIFI / DETAIL",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 10.sp,
                    color = Spectrum.OnSurfaceDim,
                    letterSpacing = 0.18.em,
                )
            }
            IconSquareButton(
                if (isFavorite) Icons.Outlined.Star else Icons.Outlined.StarOutline,
                "Favorit",
                onToggleFavorite,
                tint = if (isFavorite) Spectrum.Accent else Spectrum.OnSurface,
            )
        }
        HairlineRow()

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            // Hero
            Column(Modifier.fillMaxWidth().padding(22.dp)) {
                val isHidden = network.ssid.isBlank() || network.ssid == "(hidden)"
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (isHidden) "hidden network" else network.ssid,
                        fontFamily = InterFamily,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = (-0.02).em,
                        color = if (isHidden) Spectrum.OnSurfaceDim else Spectrum.OnSurface,
                        fontStyle = if (isHidden) FontStyle.Italic else FontStyle.Normal,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (network.isConnected) {
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "● CONNECTED",
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 11.sp,
                            color = Spectrum.Accent,
                            letterSpacing = 0.14.em,
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Column {
                        Text(
                            network.signalStrength.toString(),
                            fontFamily = InterFamily,
                            fontSize = 56.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = (-0.04).em,
                            color = rssiColor(network.signalStrength),
                            lineHeight = 56.sp,
                        )
                        Text(
                            "dBm · SIGNAL",
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 10.sp,
                            color = Spectrum.OnSurfaceDim,
                            letterSpacing = 0.2.em,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    SignalWaveform(
                        rssi = network.signalStrength,
                        modifier = Modifier.weight(1f).height(70.dp),
                    )
                }
            }
            HairlineRow()

            // Spec grid
            Column(Modifier.fillMaxWidth().padding(18.dp)) {
                Text(
                    "SPECIFICATIONS",
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 10.sp,
                    color = Spectrum.OnSurfaceDim,
                    letterSpacing = 0.2.em,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                SpecGrid(network)
            }

            // Risk panel
            if (risk) {
                RiskPanel(network)
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SpecGrid(n: WifiNetwork) {
    val specs = buildList {
        add("BSSID" to n.bssid)
        add("CHANNEL" to "${n.channel} (${n.band})")
        add("FREQUENCY" to "${n.frequency} MHz")
        add("WIDTH" to (n.channelWidth ?: "—"))
        add("STANDARD" to (n.wifiStandard ?: "—"))
        add("VENDOR" to (n.vendor ?: "—"))
        add("SECURITY" to n.securityType)
        add("WPS" to if (n.wpsEnabled) "⚠ ENABLED" else "OFF")
        add("DISTANCE" to (n.distance?.let { "~${"%.1f".format(it)}m" } ?: "—"))
        add("CAPS" to (n.rawCapabilities.ifBlank { "—" }))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Spectrum.GridLine),
    ) {
        specs.chunked(2).forEachIndexed { rowIdx, pair ->
            if (rowIdx > 0) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(Spectrum.GridLine))
            }
            Row(Modifier.fillMaxWidth()) {
                SpecCell(pair[0].first, pair[0].second, Modifier.weight(1f))
                Box(Modifier.width(1.dp).height(54.dp).background(Spectrum.GridLine))
                if (pair.size > 1) {
                    SpecCell(pair[1].first, pair[1].second, Modifier.weight(1f))
                } else {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SpecCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(Spectrum.Surface)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            label,
            fontFamily = JetBrainsMonoFamily,
            fontSize = 9.sp,
            color = Spectrum.OnSurfaceDim,
            letterSpacing = 0.18.em,
        )
        Text(
            value,
            fontFamily = JetBrainsMonoFamily,
            fontSize = 13.sp,
            color = if (value.startsWith("⚠")) Spectrum.Danger else Spectrum.OnSurface,
            modifier = Modifier.padding(top = 3.dp),
            maxLines = 2,
        )
    }
}

@Composable
private fun RiskPanel(n: WifiNetwork) {
    val sec = n.securityType
    val message = when {
        sec.equals("Open", ignoreCase = true) ->
            "Open network — no encryption. Anyone can read traffic in transit."
        sec.contains("WEP", ignoreCase = true) ->
            "WEP encryption is broken — can be cracked in minutes with airodump."
        n.wpsEnabled ->
            "WPS is enabled — vulnerable to Pixie-Dust attack."
        else -> return
    }

    Column(
        modifier = Modifier
            .padding(horizontal = 18.dp)
            .fillMaxWidth()
            .background(Spectrum.Danger.copy(alpha = 0.06f), RoundedCornerShape(4.dp))
            .border(1.dp, Spectrum.Danger, RoundedCornerShape(4.dp))
            .padding(14.dp),
    ) {
        Text(
            "⚠ RISK FLAG",
            fontFamily = JetBrainsMonoFamily,
            fontSize = 10.sp,
            color = Spectrum.Danger,
            letterSpacing = 0.2.em,
        )
        Text(
            message,
            fontSize = 13.sp,
            color = Spectrum.OnSurface,
            modifier = Modifier.padding(top = 4.dp),
            lineHeight = 18.sp,
        )
    }
}

@Composable
private fun SignalWaveform(rssi: Int, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Dashed grid lines (4 horizontal, spaced every h/4)
        val dash = PathEffect.dashPathEffect(floatArrayOf(4f, 6f))
        for (g in 0..3) {
            val y = g * h / 4f
            drawLine(
                color = Spectrum.GridLine,
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1f,
                pathEffect = dash,
            )
        }

        // Waveform path
        val points = 30
        val path = Path()
        for (i in 0 until points) {
            val x = (i.toFloat() / (points - 1)) * w
            val y = h * 0.5f +
                    sin(i * 0.5 + rssi * 0.1).toFloat() * (h * 0.15f) +
                    cos(i * 0.3).toFloat() * (h * 0.1f)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = Spectrum.Accent,
            style = Stroke(width = 1.5f),
        )
    }
}

@Composable
private fun IconSquareButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: androidx.compose.ui.graphics.Color = Spectrum.OnSurface,
) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(4.dp))
            .border(1.dp, Spectrum.GridLine, RoundedCornerShape(4.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun HairlineRow() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Spectrum.GridLine))
}

private fun WifiNetwork.isRiskFlagged(): Boolean =
    securityType.equals("Open", ignoreCase = true) ||
            securityType.contains("WEP", ignoreCase = true) ||
            wpsEnabled
