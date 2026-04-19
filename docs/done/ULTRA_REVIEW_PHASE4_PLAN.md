# Ultra Review — Phase 4 Plan

**Datum:** 2026-04-18
**Grundlage:** Verbliebene Punkte aus Phase-2/3-Review.
**Status:** Pkt. 1 (MapScreen `markerCache`-Lifecycle) ist als Hotfix bereits umgesetzt — siehe `MapScreen.kt:184-190`.

---

## Übersicht

| # | Thema | Schwere | Aufwand | Risiko |
| --- | --- | --- | --- | --- |
| 2 | ProGuard Room-TypeConverter-Regeln greifen nicht | 🟡 | 5 min | sehr niedrig |
| 3 | SignalHelperTest nutzt `hashCode` statt `toArgb` | 🟡 | 10 min | niedrig |
| 4 | GattExplorer `readCharContinuation` nicht `@Volatile` | 🟢 | 2 min | sehr niedrig |
| 5 | MapScreen Marker-Position-Updates fehlen | 🟢 | 20 min | niedrig |

Gesamt: ~40 min. Ein einzelner Polish-Commit oder gestaffelt mit den Tests.

---

## Pkt. 2 — ProGuard Room-TypeConverter-Regeln korrigieren

**Datei:** `app/proguard-rules.pro`

**Problem:** `@androidx.room.TypeConverter` ist eine Methoden-Annotation. Die aktuellen Regeln targetieren Klassen und matchen nichts:

```pro
-keep @androidx.room.TypeConverter class * { *; }                # no-op
-keepclassmembers @androidx.room.TypeConverter class * { *; }    # no-op
```

**Fix:**

```pro
# ─── Room TypeConverters (method-level annotation) ─────────────────────────────
-keepclassmembers class * {
    @androidx.room.TypeConverter <methods>;
}
```

Die beiden alten Zeilen ersatzlos streichen. Der Catch-all `-keep class com.scanner.app.data.** { *; }` bleibt als zusätzliche Sicherheit erhalten.

**Verifikation:** `./gradlew assembleRelease` läuft durch und R8-Mapping (`build/outputs/mapping/release/mapping.txt`) zeigt, dass `Converters.kt`-Methoden erhalten bleiben.

---

## Pkt. 3 — SignalHelperTest auf `toArgb()` umstellen

**Datei:** `app/src/test/java/com/scanner/app/util/SignalHelperTest.kt`

**Problem:** Drei Tests asserten gegen `color.hashCode()`. `Color.hashCode()` ist Implementierungsdetail — ein Compose-BOM-Update könnte den Test silent brechen.

**Fix (drei Stellen, Zeilen 99-128):**

```kotlin
import androidx.compose.ui.graphics.toArgb   // neuer Import

// vorher:
assertEquals(0xFF4CAF50.toInt(), color.hashCode())
// nachher:
assertEquals(0xFF4CAF50.toInt(), color.toArgb())
```

Anwenden auf alle 5 `signalColor`-Asserts.

**Verifikation:** `./gradlew testDebugUnitTest` → 48 Tests grün.

---

## Pkt. 4 — `@Volatile` auf `readCharContinuation`

**Datei:** `app/src/main/java/com/scanner/app/util/GattExplorer.kt`, Zeile 118.

**Problem:** Das Feld wird vom Coroutine-Thread (Mutex-geschützt) und vom Android-BLE-Callback-Thread (in `BluetoothGattCallback.onCharacteristicRead`) gelesen. In der Praxis sorgen `Mutex.withLock` + die interne Synchronisation von `CompletableDeferred` für ausreichende Happens-Before-Ordnung, aber ohne `@Volatile` ist das technisch nicht sauber.

**Fix:**

```kotlin
@Volatile
private var readCharContinuation: CompletableDeferred<ByteArray?>? = null
```

**Verifikation:** kein Verhalten-Change, Compile reicht.

---

## Pkt. 5 — MapScreen Marker-Position-Updates

**Datei:** `app/src/main/java/com/scanner/app/ui/screens/MapScreen.kt`, Zeilen 146-168.

**Problem:** Das BSSID-basierte Diff erkennt nur, ob ein BSSID erschienen oder verschwunden ist. Wenn sich für ein bestehendes Gerät `lat`/`lon` ändert (verfeinerte Wardriving-Position oder Re-Scan an anderem Ort), bleibt der Marker an der alten Stelle.

**Fix:** Diff um Position-Vergleich erweitern. Vorschlag — `Marker.position` updaten wenn die Coords drift:

```kotlin
update = { map ->
    val currentByBssid = geoDevices.associateBy { it.bssid }
    val cachedBssids = markerCache.keys.toSet()

    // 1. Stale entfernen
    (cachedBssids - currentByBssid.keys).forEach { bssid ->
        map.overlays.remove(markerCache.remove(bssid))
    }

    // 2. Update bestehende + neu anlegen
    currentByBssid.forEach { (bssid, geo) ->
        val cached = markerCache[bssid]
        if (cached == null) {
            // neu
            val marker = Marker(map).apply {
                position = GeoPoint(geo.lat, geo.lon)
                title = geo.name.ifBlank { "WLAN Netzwerk" }
                snippet = "BSSID: ${geo.bssid}\nSicherheit: ${geo.security}"
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                icon = createMarkerIcon(map.context, geo.security)
            }
            map.overlays.add(marker)
            markerCache[bssid] = marker
        } else {
            // bestehend — Position bei Bedarf updaten
            val newPos = GeoPoint(geo.lat, geo.lon)
            if (cached.position.latitude != geo.lat || cached.position.longitude != geo.lon) {
                cached.position = newPos
            }
            // Snippet / Icon updaten falls Sicherheit geändert
            if (!cached.snippet.contains(geo.security)) {
                cached.snippet = "BSSID: ${geo.bssid}\nSicherheit: ${geo.security}"
                cached.icon = createMarkerIcon(map.context, geo.security)
            }
        }
    }

    // initiale Zentrierung wie gehabt …
    if (!hasInitiallyRecentered.value && geoDevices.isNotEmpty()) {
        val avgLat = geoDevices.sumOf { it.lat } / geoDevices.size
        val avgLon = geoDevices.sumOf { it.lon } / geoDevices.size
        map.controller.setCenter(GeoPoint(avgLat, avgLon))
        hasInitiallyRecentered.value = true
    }

    map.invalidate()
}
```

**Trade-off:** Etwas mehr Code, aber der Hot-Path (Marker existiert + keine Änderung) bleibt billig: zwei `Double`-Vergleiche und eine Substring-Suche. Bei stabilen Coords kein Marker-Recreate.

**Verifikation:** App auf Gerät starten, eine Wardriving-Session machen, dann ein WLAN-Eintrag in der DB händisch mit anderen Koordinaten überschreiben (oder Coords im JSON-Metadaten-Feld ändern) → Marker bewegt sich beim nächsten Scan.

---

## Empfohlene Commit-Strategie

Eine einzelne PR mit vier Commits:

1. `proguard: fix Room TypeConverter rules to target methods`
2. `tests: use Color.toArgb() instead of hashCode() for signal color asserts`
3. `gatt: mark readCharContinuation as @Volatile`
4. `map: diff marker positions, not just BSSID set membership`

Damit ist der Phase-3-Backlog vollständig abgearbeitet und der Ultra Review komplett geschlossen.
