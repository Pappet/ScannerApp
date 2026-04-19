package com.scanner.app.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.scanner.app.BuildConfig
import com.scanner.app.data.db.DeviceCategory
import com.scanner.app.data.repository.DeviceRepository
import com.scanner.app.ui.components.HairlineHorizontal
import com.scanner.app.ui.components.HeaderStat
import com.scanner.app.ui.components.SpectrumHeader
import com.scanner.app.ui.components.SpectrumKicker
import com.scanner.app.ui.theme.JetBrainsMonoFamily
import com.scanner.app.ui.theme.Spectrum
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline

/** One persisted WiFi network enriched with its geo / signal / distance snapshot. */
private data class GeoDevice(
        val bssid: String,
        val name: String,
        val lat: Double,
        val lon: Double,
        val security: String,
        val rssi: Int,
        val distanceMeters: Double?,
)

/**
 * A cluster of networks scanned from effectively the same spot. The centroid is the shared
 * scan-point; devices fan out around it so each is individually clickable.
 */
private data class ScanPoint(
        val centroidLat: Double,
        val centroidLon: Double,
        val devices: List<GeoDevice>,
)

// ── Geometry helpers ─────────────────────────────────────────

// Cluster granularity: 4 decimal places ≈ 11m at equator. Networks scanned from
// the same couch will collapse into a single cluster; two rooms apart stay separate.
private fun scanPointKey(lat: Double, lon: Double): Pair<Long, Long> =
        (lat * 10_000).roundToInt().toLong() to (lon * 10_000).roundToInt().toLong()

// Convert a radius in meters to degrees of latitude / longitude at the given latitude.
private fun metersToLatDeg(meters: Double): Double = meters / 111_000.0

private fun metersToLonDeg(meters: Double, atLat: Double): Double =
        meters / (111_000.0 * cos(atLat * PI / 180.0))

private fun offsetGeo(lat: Double, lon: Double, bearingDeg: Double, meters: Double): GeoPoint {
    val br = bearingDeg * PI / 180.0
    val dLat = metersToLatDeg(meters * cos(br))
    val dLon = metersToLonDeg(meters * sin(br), lat)
    return GeoPoint(lat + dLat, lon + dLon)
}

private fun circlePoints(
        centerLat: Double,
        centerLon: Double,
        radiusMeters: Double,
        segments: Int = 64,
): List<GeoPoint> {
    val latDeg = metersToLatDeg(radiusMeters)
    val lonDeg = metersToLonDeg(radiusMeters, centerLat)
    return (0..segments).map { i ->
        val a = (i.toDouble() / segments) * 2 * PI
        GeoPoint(centerLat + latDeg * sin(a), centerLon + lonDeg * cos(a))
    }
}

// Stable angle per BSSID so a device always sits at the same clock-position
// around its scan-point regardless of collection order.
private fun bssidAngle(bssid: String): Double {
    var h = 0
    for (c in bssid) h = (h * 31 + c.code) and 0x7fffffff
    return (h % 360).toDouble()
}

// ── Security → Spectrum color ────────────────────────────────
private fun securityColor(security: String): Color {
    val s = security.lowercase()
    return when {
        "wpa3" in s -> Spectrum.Accent
        "offen" in s || s == "wep" || "wep" in s.split(" ") -> Spectrum.Danger
        "owe" in s -> Spectrum.Warning
        "wpa2" in s || "wpa" in s -> Spectrum.Warning
        else -> Spectrum.OnSurfaceDim
    }
}

// ── Marker icon factories ────────────────────────────────────
// Bitmap pixels are displayed 1:1 on screen, so sizes must account for device density
// (3x on most phones). 44dp is the Material tap target; use 32dp for the smaller glyph
// inside a 48dp bitmap so the whole bitmap area stays tappable.
private fun Context.dp(dp: Float): Float = dp * resources.displayMetrics.density

private fun scanPointDot(ctx: Context): BitmapDrawable {
    val sizePx = ctx.dp(40f).toInt()
    val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val ringPaint =
            Paint().apply {
                color = Spectrum.Accent.toArgb()
                style = Paint.Style.STROKE
                strokeWidth = ctx.dp(1.5f)
                isAntiAlias = true
            }
    val fillPaint =
            Paint().apply {
                color = Spectrum.OnSurface.toArgb()
                style = Paint.Style.FILL
                isAntiAlias = true
            }
    val r = sizePx / 2f
    val glyphR = ctx.dp(6f)
    c.drawCircle(r, r, glyphR, ringPaint)
    c.drawCircle(r, r, glyphR * 0.4f, fillPaint)
    return BitmapDrawable(ctx.resources, bmp)
}

private fun networkTick(
        ctx: Context,
        color: Int,
        selected: Boolean,
): BitmapDrawable {
    val sizePx = ctx.dp(48f).toInt()
    val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val bgPaint =
            Paint().apply {
                this.color = Spectrum.Surface.toArgb()
                style = Paint.Style.FILL
                isAntiAlias = true
            }
    val ringPaint =
            Paint().apply {
                this.color = color
                style = Paint.Style.STROKE
                strokeWidth = ctx.dp(if (selected) 2f else 1.25f)
                isAntiAlias = true
            }
    val dotPaint =
            Paint().apply {
                this.color = color
                style = Paint.Style.FILL
                isAntiAlias = true
            }
    val r = sizePx / 2f
    val glyphR = ctx.dp(if (selected) 8f else 6.5f)
    c.drawCircle(r, r, glyphR, bgPaint)
    c.drawCircle(r, r, glyphR, ringPaint)
    c.drawCircle(r, r, glyphR * if (selected) 0.6f else 0.45f, dotPaint)
    return BitmapDrawable(ctx.resources, bmp)
}

// Invert + slightly desaturate tiles so MAPNIK feels at home in Spectrum.
private val DarkTilesFilter: ColorMatrixColorFilter by lazy {
    val invert =
            ColorMatrix(
                    floatArrayOf(
                            -1f,
                            0f,
                            0f,
                            0f,
                            255f,
                            0f,
                            -1f,
                            0f,
                            0f,
                            255f,
                            0f,
                            0f,
                            -1f,
                            0f,
                            255f,
                            0f,
                            0f,
                            0f,
                            1f,
                            0f,
                    )
            )
    val desat = ColorMatrix().apply { setSaturation(0.55f) }
    invert.postConcat(desat)
    ColorMatrixColorFilter(invert)
}

// ── Main screen ──────────────────────────────────────────────
@Composable
fun MapScreen() {
    val context = LocalContext.current
    val repository = remember { DeviceRepository(context) }

    val devices by
            repository
                    .observeDevicesByCategory(DeviceCategory.WIFI)
                    .map { list -> list.mapNotNull { parseGeoDevice(it) } }
                    .collectAsState(initial = emptyList())

    val scanPoints: List<ScanPoint> =
            remember(devices) {
                devices.groupBy { scanPointKey(it.lat, it.lon) }.map { (_, group) ->
                    val lat = group.sumOf { it.lat } / group.size
                    val lon = group.sumOf { it.lon } / group.size
                    ScanPoint(lat, lon, group.sortedByDescending { it.rssi })
                }
            }

    var selectedBssid by remember { mutableStateOf<String?>(null) }
    val selected =
            remember(devices, selectedBssid) { devices.firstOrNull { it.bssid == selectedBssid } }

    val totalDevices = devices.size
    val pointCount = scanPoints.size

    Column(Modifier.fillMaxSize().background(Spectrum.Surface)) {
        SpectrumHeader(
                kicker = "WARDRIVING",
                subtitle = "Geotag",
                stats =
                        listOf(
                                HeaderStat(totalDevices.toString(), "pinned"),
                                HeaderStat(pointCount.toString(), "points"),
                                HeaderStat(if (totalDevices > 0) "ON" else "OFF", "gps"),
                        ),
        )

        Box(
                Modifier.fillMaxWidth().weight(1f).clipToBounds(),
        ) {
            if (devices.isEmpty()) {
                MapEmptyState()
            } else {
                SpectrumMapView(
                        scanPoints = scanPoints,
                        selectedBssid = selectedBssid,
                        onSelect = { selectedBssid = it },
                )
                MapLegendOverlay(
                        modifier = Modifier.align(Alignment.TopStart).padding(14.dp),
                )
                selected?.let { sel ->
                    MapDetailPanel(
                            device = sel,
                            onClose = { selectedBssid = null },
                            modifier =
                                    Modifier.align(Alignment.BottomCenter)
                                            .padding(14.dp)
                                            .fillMaxWidth(),
                    )
                }
            }
        }
    }
}

// ── MapView with cluster + range-ring overlays ───────────────
@Composable
private fun SpectrumMapView(
        scanPoints: List<ScanPoint>,
        selectedBssid: String?,
        onSelect: (String?) -> Unit,
) {
    var hasRecentered by remember { mutableStateOf(false) }

    AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                Configuration.getInstance().apply {
                    userAgentValue =
                            "ScannerApp/${BuildConfig.VERSION_NAME} (Android; +https://github.com/TODO_REPLACE/ScannerApp)"
                    osmdroidBasePath = ctx.filesDir
                    osmdroidTileCache = ctx.filesDir.resolve("osmdroid/tiles")
                }
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    controller.setZoom(17.5)
                    overlayManager.tilesOverlay.setColorFilter(DarkTilesFilter)
                    setBackgroundColor(Spectrum.Surface.toArgb())
                }
            },
            update = { map ->
                rebuildOverlays(map, scanPoints, selectedBssid, onSelect)

                if (!hasRecentered && scanPoints.isNotEmpty()) {
                    val first = scanPoints.first()
                    map.controller.setCenter(GeoPoint(first.centroidLat, first.centroidLon))
                    hasRecentered = true
                }
                map.invalidate()
            },
            onRelease = { map ->
                map.overlays.clear()
                map.onDetach()
            },
    )
}

// Fan-out ring radius in meters. Scales with cluster size so tap targets don't
// collide: ~48dp icons need roughly 1 icon-width of arc-spacing per network.
private fun tickRingMeters(count: Int): Double =
        when {
            count <= 1 -> 0.0
            count <= 3 -> 8.0
            count <= 6 -> 12.0
            count <= 12 -> 18.0
            else -> 24.0
        }

private fun rebuildOverlays(
        map: MapView,
        scanPoints: List<ScanPoint>,
        selectedBssid: String?,
        onSelect: (String?) -> Unit,
) {
    map.overlays.clear()

    scanPoints.forEach { sp ->
        val ringRadius = tickRingMeters(sp.devices.size)
        val centroid = GeoPoint(sp.centroidLat, sp.centroidLon)

        // 1. Dotted connector from centroid to each fanned tick. Drawn before
        //    the markers so the ticks sit on top of the line ends.
        if (ringRadius > 0) {
            sp.devices.forEach { d ->
                val angle = bssidAngle(d.bssid)
                val end = offsetGeo(sp.centroidLat, sp.centroidLon, angle, ringRadius)
                val connector =
                        Polyline(map).apply {
                            setPoints(listOf(centroid, end))
                            outlinePaint.apply {
                                color = Spectrum.OnSurfaceDim.toArgb()
                                strokeWidth = map.context.dp(1f)
                                style = Paint.Style.STROKE
                                pathEffect = DashPathEffect(floatArrayOf(4f, 6f), 0f)
                                isAntiAlias = true
                            }
                        }
                map.overlays.add(connector)
            }
        }

        // 2. Phone-location dot at the cluster centroid.
        val centerMarker =
                Marker(map).apply {
                    position = centroid
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    icon = scanPointDot(map.context)
                    setOnMarkerClickListener { _, _ ->
                        onSelect(null)
                        true
                    }
                    isDraggable = false
                    title = "Scan point · ${sp.devices.size} networks"
                }
        map.overlays.add(centerMarker)

        // 3. One tick per scanned network, fanned around the centroid on a
        //    ring so each is individually clickable. Solo networks sit on the
        //    centroid (no fan-out needed).
        sp.devices.forEach { d ->
            val angle = bssidAngle(d.bssid)
            val pos =
                    if (ringRadius > 0) {
                        offsetGeo(sp.centroidLat, sp.centroidLon, angle, ringRadius)
                    } else {
                        centroid
                    }
            val sel = d.bssid == selectedBssid
            val tick =
                    Marker(map).apply {
                        position = pos
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        icon =
                                networkTick(
                                        map.context,
                                        color = securityColor(d.security).toArgb(),
                                        selected = sel,
                                )
                        title = d.name.ifBlank { "WLAN" }
                        snippet = "${d.security} · ${d.rssi} dBm"
                        setOnMarkerClickListener { _, _ ->
                            onSelect(if (sel) null else d.bssid)
                            true
                        }
                    }
            map.overlays.add(tick)
        }
    }

    // 3. Range ring for the selected network (dashed chartreuse circle at
    //    FSPL-estimated distance around its scan-point).
    if (selectedBssid != null) {
        val sp = scanPoints.firstOrNull { p -> p.devices.any { it.bssid == selectedBssid } }
        val d = sp?.devices?.firstOrNull { it.bssid == selectedBssid }
        val radius = d?.distanceMeters
        if (sp != null && radius != null && radius > 0.5 && radius.isFinite()) {
            val ring =
                    Polygon(map).apply {
                        points = circlePoints(sp.centroidLat, sp.centroidLon, radius)
                        outlinePaint.apply {
                            color = Spectrum.Accent.toArgb()
                            strokeWidth = 3f
                            style = Paint.Style.STROKE
                            pathEffect = DashPathEffect(floatArrayOf(14f, 10f), 0f)
                            isAntiAlias = true
                        }
                        fillPaint.color = android.graphics.Color.TRANSPARENT
                        setOnClickListener { _, _, _ ->
                            onSelect(null)
                            true
                        }
                    }
            map.overlays.add(0, ring)
        }
    }
}

// ── Parsing ──────────────────────────────────────────────────
private fun parseGeoDevice(
        d: com.scanner.app.data.db.DiscoveredDeviceEntity,
): GeoDevice? {
    return try {
        if (d.metadata.isNullOrBlank()) return null
        val meta = JSONObject(d.metadata)
        if (!meta.has("latitude") || !meta.has("longitude")) return null
        GeoDevice(
                bssid = d.address,
                name = d.name,
                lat = meta.getDouble("latitude"),
                lon = meta.getDouble("longitude"),
                security = meta.optString("security", "Unbekannt"),
                rssi = d.lastSignalStrength ?: -100,
                distanceMeters = if (meta.has("distance")) meta.getDouble("distance") else null,
        )
    } catch (_: Exception) {
        null
    }
}

// ── Overlay composables ──────────────────────────────────────

@Composable
private fun MapEmptyState() {
    Box(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center,
    ) {
        Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SpectrumKicker("NO FIXES · ENABLE GPS", color = Spectrum.Accent)
            Text(
                    "Führe einen WLAN-Scan mit aktiviertem GPS durch, um Netzwerke zu geotaggen.",
                    color = Spectrum.OnSurfaceDim,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun MapLegendOverlay(modifier: Modifier = Modifier) {
    Column(
            modifier =
                    modifier.clip(RoundedCornerShape(4.dp))
                            .background(Color(0xE607090A))
                            .border(1.dp, Spectrum.GridLine, RoundedCornerShape(4.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        LegendRow(Spectrum.Accent, "WPA3")
        LegendRow(Spectrum.Warning, "WPA2 / WPA")
        LegendRow(Spectrum.Danger, "OPEN / WEP")
    }
}

@Composable
private fun LegendRow(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
                Modifier.size(8.dp).clip(CircleShape).background(color),
        )
        Spacer(Modifier.width(6.dp))
        Text(
                label,
                color = Spectrum.OnSurfaceDim,
                fontFamily = JetBrainsMonoFamily,
                fontSize = 10.sp,
        )
    }
}

@Composable
private fun MapDetailPanel(
        device: GeoDevice,
        onClose: () -> Unit,
        modifier: Modifier = Modifier,
) {
    Column(
            modifier =
                    modifier.clip(RoundedCornerShape(4.dp))
                            .background(Color(0xF007090A))
                            .border(1.dp, Spectrum.AccentDim, RoundedCornerShape(4.dp))
                            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.fillMaxWidth().padding(end = 24.dp)) {
                SpectrumKicker("PINNED · TAP RING TO CLOSE")
                Spacer(Modifier.height(4.dp))
                Text(
                        device.name.ifBlank { "WLAN" },
                        color = Spectrum.OnSurface,
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 16.sp,
                        maxLines = 1,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        HairlineHorizontal()
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            DetailStat("RSSI", "${device.rssi} dBm")
            DetailStat(
                    "RANGE",
                    device.distanceMeters?.let { "~%.1fm".format(it) } ?: "—",
            )
            DetailStat("SEC", device.security)
        }
        val hasRange = device.distanceMeters?.let { it.isFinite() && it > 0.5 } == true
        if (hasRange) {
            Spacer(Modifier.height(8.dp))
            Text(
                    "AP is somewhere on the chartreuse ring.",
                    color = Spectrum.OnSurfaceDim,
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 10.sp,
            )
        }
        Spacer(Modifier.height(10.dp))
        Box(
                Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(2.dp))
                        .background(Spectrum.SurfaceRaised)
                        .border(1.dp, Spectrum.GridLine, RoundedCornerShape(2.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                        .clickable { onClose() },
        ) {
            Text(
                    "CLOSE",
                    color = Spectrum.OnSurfaceDim,
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 10.sp,
            )
        }
    }
}

@Composable
private fun DetailStat(label: String, value: String) {
    Column {
        Text(
                label,
                color = Spectrum.OnSurfaceDim,
                fontFamily = JetBrainsMonoFamily,
                fontSize = 9.sp,
        )
        Spacer(Modifier.height(2.dp))
        Text(
                value,
                color = Spectrum.OnSurface,
                fontFamily = JetBrainsMonoFamily,
                fontSize = 12.sp,
        )
    }
}
