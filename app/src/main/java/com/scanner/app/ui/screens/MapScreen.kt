package com.scanner.app.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.scanner.app.data.db.DeviceCategory
import com.scanner.app.data.repository.DeviceRepository
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

@Composable
fun MapScreen() {
    val context = LocalContext.current
    val repository = remember { DeviceRepository(context) }
    
    // Wir beobachten alle gespeicherten WLANs aus der Datenbank
    val devices by repository.observeDevicesByCategory(DeviceCategory.WIFI).collectAsState(initial = emptyList())
    
    // Wir filtern diejenigen heraus, die geolocated sind
    val geoDevices = remember(devices) {
        devices.filter { device ->
            try {
                if (!device.metadata.isNullOrBlank()) {
                    val meta = JSONObject(device.metadata)
                    meta.has("latitude") && meta.has("longitude")
                } else false
            } catch (e: Exception) { false }
        }
    }

    // Einfache Hilfsfunktion, um Punkte auf der Karte in verschiedenen Farben zu zeichnen
    val createMarkerIcon = { ctx: Context, sec: String ->
        val color = when {
            sec.contains("Offen", ignoreCase = true) -> android.graphics.Color.RED
            sec.contains("OWE", ignoreCase = true) -> android.graphics.Color.rgb(255, 152, 0)
            else -> android.graphics.Color.GREEN
        }
        val size = 50
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint().apply {
            this.color = color
            isAntiAlias = true
        }
        val borderPaint = Paint().apply {
            this.color = android.graphics.Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 4f
            isAntiAlias = true
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 4f, paint)
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 4f, borderPaint)
        BitmapDrawable(ctx.resources, bmp)
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            Configuration.getInstance().userAgentValue = ctx.packageName
            val map = MapView(ctx)
            map.setTileSource(TileSourceFactory.MAPNIK)
            map.setMultiTouchControls(true)
            map.controller.setZoom(16.0)
            map
        },
        update = { map ->
            map.overlays.clear()
            
            var sumLat = 0.0
            var sumLon = 0.0
            var count = 0
            
            geoDevices.forEach { device ->
                try {
                    val meta = JSONObject(device.metadata!!)
                    val lat = meta.getDouble("latitude")
                    val lon = meta.getDouble("longitude")
                    val sec = meta.optString("security", "Unbekannt")
                    
                    sumLat += lat
                    sumLon += lon
                    count++
                    
                    val marker = Marker(map)
                    marker.position = GeoPoint(lat, lon)
                    marker.title = device.name.ifBlank { "WLAN Netzwerk" }
                    marker.snippet = "BSSID: ${device.address}\nSicherheit: $sec"
                    marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    marker.icon = createMarkerIcon(map.context, sec)
                    
                    map.overlays.add(marker)
                } catch (e: Exception) {
                    // Falls JSON korrupt ist, einfach ignorieren
                }
            }
            
            // Karte auf den Durchschnitt der Punkte zentrieren
            if (count > 0) {
                map.controller.setCenter(GeoPoint(sumLat / count, sumLon / count))
            }
            
            map.invalidate()
        }
    )
}
