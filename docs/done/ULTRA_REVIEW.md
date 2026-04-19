# Ultra Review — ScannerApp

**Datum:** 2026-04-17
**Branch:** `main` @ `e5daf16`
**Scope:** gesamtes Projekt (Architektur, Code-Qualität, Korrektheit, UX, Performance, Tech Debt).
**Komplement:** `docs/SECURITY_REVIEW.md` (gleiches Datum) — Security-Findings sind dort abgedeckt und werden hier **nicht dupliziert**, nur referenziert.

---

## 1. Executive Summary

ScannerApp ist für eine Single-Developer-Solo-Codebase bemerkenswert sauber strukturiert: klare Trennung `util/` ↔ `data/` ↔ `ui/`, reaktive Datenflüsse via Room + Flow, moderne Compose/Material-3-UI, sinnvolle Foreground-Service-Architektur für das Monitoring. Bei ~11 kLOC Kotlin in ~34 Quelldateien ist die Kohärenz hoch, es gibt keine sichtbaren "Leichen" oder tote Abstraktionen.

Die Gesamtnote ist **B+** — solide, wartbar, feature-reich. Die Abzüge kommen aus drei Richtungen:

1. **Ein konkreter UI-Bug** in `BluetoothScreen.kt` (Kotlin-Operator-Precedence — Counter „gekoppelt" wird nie angezeigt, sobald „verbunden" > 0).
2. **Reliability-Fallen** (destruktive Room-Migration, `Thread.sleep` in Coroutine, fragile `GattExplorer`-Continuation, `MapView`-Lifecycle).
3. **Fundament fehlt**: keine Tests, kein Linter, kein ProGuard/R8 im Release-Build. Das ist bei einer feature-kompletten App mit 11 kLOC die größte Hypothek.

Priorisierte Empfehlungen siehe §9.

---

## 2. Architektur — Stärken

| Aspekt | Bewertung | Anmerkung |
|---|---|---|
| Paket-Layout | ✅ | `data/`, `service/`, `ui/`, `util/` sauber getrennt; keine Zirkelabhängigkeiten gefunden. |
| Datenzugriff | ✅ | Repository-Pattern + Room DAO + `TypeConverter` für `Instant`/Enums; alle Queries parametrisiert (siehe `DeviceDao.kt`), kein SQLi-Risiko. |
| Nebenläufigkeit | ✅ | Coroutines + Flow/StateFlow, `viewModelScope`-äquivalent via `rememberCoroutineScope()`. Keine manuelle Thread-Handhabung in der UI. |
| Service-Design | ✅ | `ScanService` korrekt als Foreground-Service mit `foregroundServiceType="connectedDevice"` und `exported=false`. LocalBinder-Pattern. |
| Permissions | ✅ | Accompanist-Permissions in allen relevanten Screens, `maxSdkVersion=30` für legacy BT-Perms, `neverForLocation`-Flag für BT_SCAN und NEARBY_WIFI_DEVICES. |
| Manifest-Hygiene | ✅ | FileProvider `exported=false`, Service `exported=false`, keine unnötig breiten Intent-Filter. |
| UI-Schichtung | ✅ | Screen-Composables sind stateful-light, schwere Arbeit in `util/`. Gemeinsame Komponenten (`SignalBar`, `StatusChip`, `MetaRow`) korrekt extrahiert. |

---

## 3. Defekte / Bugs (konkret)

### 3.1 🔴 `BluetoothScreen.kt:142-147` — Operator-Precedence-Bug

```kotlin
text = "${devices.size} gefunden" +
        if (connected > 0) " · $connected verbunden" else "" +
                if (bonded > 0) " · $bonded gekoppelt" else "",
```

Kotlin parst `if (connected > 0) A else B + if (bonded > 0) C else D` als `if (connected > 0) A else (B + if (bonded > 0) C else D)`. Ergebnis:

- `connected > 0` → Anzeige ist **nur** „X verbunden", „Y gekoppelt" wird unterschlagen.
- `connected == 0` → die bonded-Info erscheint, aber angehängt an den leeren String.

**Fix:** jeden `if`-Ast klammern:

```kotlin
text = "${devices.size} gefunden" +
        (if (connected > 0) " · $connected verbunden" else "") +
        (if (bonded > 0) " · $bonded gekoppelt" else ""),
```

### 3.2 🟠 `MainActivity.kt:158` — `beyondBoundsPageCount` inkonsistent

```kotlin
beyondBoundsPageCount = 6,  // Keep all 7 pages alive in memory
```

Der `HorizontalPager` hat **8 Seiten** (`pageCount = { 8 }` in Zeile 42) — Bottom-Tabs 0–4, Channel Analysis (5), Security Audit (6), Map (7). Der Wert `6` hält nur 1 + 2·6 = 13 Seiten jenseits der aktuellen im Layout-Cache; bei 8 Seiten reicht das **zwar**, aber der Kommentar („7 pages") ist durch den Map-Zusatz veraltet. Entweder auf `7` erhöhen (alle Seiten immer warm) oder reduzieren und den Kommentar streichen — aktueller Zustand ist „funktioniert durch Zufall", sollte explizit sein.

### 3.3 🟠 `MapScreen.kt` — Mehrere Lifecycle-/Policy-Probleme

1. **Kein `onDetach()`** für `MapView` — osmdroid hält Tile-Loader-Threads + Bitmap-Cache, die per DisposableEffect freigegeben werden müssen, sonst Leak beim Tab-Wechsel im HorizontalPager:
   ```kotlin
   AndroidView(..., onRelease = { it.onDetach() })
   ```
2. **User-Agent** (Zeile 70): `Configuration.getInstance().userAgentValue = ctx.packageName` — die OSM-Foundation-Tile-Usage-Policy verlangt einen aussagekräftigen UA; reiner Package-Name kann zu IP-Bans oder Rate-Limiting führen. Empfehlung: `"ScannerApp/${BuildConfig.VERSION_NAME} (+contact)"`.
3. **Auto-Recenter auf jedem Update** (Zeile 109-111): der Map-Viewport springt bei jeder neuen/aktualisierten Entity auf den Durchschnitt zurück — das übergeht den User-Pan/Zoom. Zentrierung sollte nur einmalig beim ersten Datenladen geschehen (z. B. `LaunchedEffect(geoDevices.isNotEmpty())`).
4. **Overlay-Rebuild**: `map.overlays.clear()` + vollständige Neuerstellung aller Marker bei jedem Update — bei >200 geotagten Netzen merklicher Lag. Besser: Diff über BSSID als Key.
5. **Kein Empty-State / kein Loading-Indicator** — leere Karte ohne Erklärung, wenn noch keine Geodaten vorhanden sind.
6. **Tile-Cache-Pfad nicht explizit gesetzt** — osmdroid fällt auf `Environment.getExternalStorageDirectory()` zurück, was auf modernen Androids (API 30+) mit Scoped Storage Probleme macht. `Configuration.getInstance().osmdroidBasePath` / `.osmdroidTileCache` sollten in `context.filesDir` umgebogen werden.

### 3.4 🟡 `NetworkDiscovery` — Progress-Total inkonsistent

Der UI-seitige Fortschritt kann zwischen `total = 4` (wenn NetBIOS übersprungen wird) und `total = 5` springen. Siehe `NetworkDiscovery.kt` — in der UI ruckt die Progressbar. Kein Datenfehler, aber polish-würdig.

### 3.5 🟡 `ChannelAnalysisScreen.kt:146` — `return` aus Composable-Lambda

`return` innerhalb einer `Column { ... }`-Lambda macht einen non-local return aus dem umschließenden `@Composable` `ChannelAnalysisScreen()`. Funktioniert, weil Compose-Compiler es als inline-ähnlich behandelt, ist aber stilistisch unüblich und schlecht lesbar. Besser: `if (analysis == null) { EmptyState() } else { ... }` oder frühes `return@ChannelAnalysisScreen` mit benanntem Label.

---

## 4. Reliability / Correctness — Risiken

### 4.1 🔴 `AppDatabase` — `fallbackToDestructiveMigration()`

Jede Schema-Änderung (z. B. neue Spalte) löscht kommentarlos **alle** gespeicherten Geräte, Sessions und Signal-Verläufe. Das widerspricht dem Nutzer-Versprechen eines persistenten „Inventars". Mit `exportSchema = true` liegt bereits ein Schema-JSON vor — `Migration`-Objekte sollten genutzt werden. Zumindest vor dem ersten Play-Store-Release fixen.

### 4.2 🟠 `PortScanner.waitForData` — `Thread.sleep` in Coroutine

```kotlin
// util/PortScanner.kt — Auszug
Thread.sleep(50)
```

Blockiert den IO-Dispatcher-Thread. Bei 64er-Thread-Pool und 200-Port-Scan mit Banner-Grabbing kann das zu Dispatcher-Starvation führen. Ersatz: `delay(50)` im `suspend`-Kontext.

### 4.3 🟠 `GattExplorer` — geteiltes Continuation-Feld

`readCharContinuation: CompletableDeferred<ByteArray?>` ist ein Single-Shared-Field und wird bei jedem neuen Read überschrieben. Solange die Aufrufe **strikt sequenziell** sind, funktioniert das. Eine spätere Parallelisierung (etwa „alle Characteristics parallel lesen") würde lautlos falsche Ergebnisse liefern. Empfehlung: `ConcurrentHashMap<UUID, CompletableDeferred<...>>` oder per-Request-Continuation.

### 4.4 🟡 `ScanService` — hardgecodetes `8.8.8.8`

Internet-Reachability via Ping auf `"8.8.8.8"`. In China/Firmennetzen geblockt → User sieht dauerhaft „offline". Besser: konfigurierbar oder fallback-Chain (Gateway → 8.8.8.8 → 1.1.1.1).

### 4.5 🟡 `ScanService.updateInterval()` — `wasRunning`-Race

Der Job wird mit `.cancel()` beendet und danach neu gestartet — aber `isActive` kann zwischen Check und Restart kippen (sehr kurzes Fenster). In der Praxis kein beobachteter Bug, aber brüchig.

### 4.6 🟡 Deprecated APIs weiterhin in Benutzung

- `WifiManager.connectionInfo` (`WifiScanner.kt:126,178`, `PingUtil.kt`) — auf API 31+ deprecated zugunsten `NetworkCallback` + `TransportInfo`. Funktioniert noch, wird aber irgendwann entfernt.
- `DhcpInfo` (`PingUtil.kt`) — dto.
- `Icons.Default.ArrowBack` (`InventoryScreen.kt:119`) — bereits `@Suppress("DEPRECATION")` annotiert, sollte auf `AutoMirrored.Filled.ArrowBack` migrieren.

---

## 5. Performance & UX

| Stelle | Beobachtung | Hebel |
|---|---|---|
| `InventoryScreen.kt:161-175` | 6 Filter-Chips in einer starren `Row` ohne Scroll. Auf 360dp-Geräten läuft das über die Bildschirmbreite hinaus und wird clipped. | Auf `LazyRow` oder `Row(Modifier.horizontalScroll(...))` umstellen. |
| `MapScreen.kt:77-114` | Bei jedem `devices`-Update wird `overlays.clear()` + voller Rebuild. Bei 500+ geotagten APs → spürbarer Ruck. | Diff via `remember { mutableStateMapOf<String, Marker>() }` keyed auf BSSID. |
| `ChannelAnalysisScreen.kt` | `verticalScroll(rememberScrollState())` + ChannelBarChart + SpectrumView — beides Canvas-heavy. Bei Recomposition wird alles neu gezeichnet. | `derivedStateOf` für `channels`/`recommendations`, damit nicht jeder Recompose das Chart neu rendert. |
| `ExportManager` (PDF/CSV) | Läuft im Repository-Scope, scheint bereits off-main — gut. | Nur verifizieren, dass der Progress-Callback nicht die UI jitter macht. |
| `MonitorScreen` | History-Listen wachsen auf 120 Punkte, das ist fein. | Kein Handlungsbedarf. |
| `PortScanner` Quick-Scan | 20 Ports, adaptiv — gut dimensioniert. | `Thread.sleep` → `delay` (siehe §4.2). |

### 5.1 Screens nutzen inkonsistente Scroll-Container

- `WifiScreen`, `InventoryScreen`, `SecurityAuditScreen` → `LazyColumn` ✅
- `ChannelAnalysisScreen` → `verticalScroll(rememberScrollState())` mit vielen Elementen → komplettes Layout immer im Baum

Einheitlich auf LazyColumn wäre speichersparender.

---

## 6. Code-Qualität & Tech-Debt

### 6.1 Fehlendes Fundament

| Fehlt | Auswirkung |
|---|---|
| **Keine Tests** (weder Unit noch Instrumented) | Jede Änderung riskiert Regression. Besonders kritisch bei BLE-State-Machines, Port-Scanner-Batching, MAC-Vendor-Lookup-Parsing. |
| **Kein Linter** (ktlint/detekt nicht konfiguriert) | Der Bug aus §3.1 wäre durch jede statische Analyse gefangen worden. |
| **`isMinifyEnabled = false`** im Release-Build | APK größer als nötig, kein Class-/Method-Shrinking, keine Obfuskation, keine tote-Code-Elimination. |
| **Keine CI** | Keine automatische Build-Verifikation auf PR-Ebene. |

Konkrete Minimalschritte:
- `ktlint-gradle` in `build.gradle.kts` aufnehmen.
- JUnit-4-Tests für pure Util-Klassen (`MacVendorLookup`, `ChannelAnalyzer`, `SecurityAuditor`, `frequencyToChannel`, CSV-Escape) — das sind alles deterministische Funktionen ohne Android-Kontext.
- R8 aktivieren; ProGuard-Regeln für Room/Compose/osmdroid pflegen.

### 6.2 `@SuppressLint("MissingPermission")` flächig

`WifiScanner`, `BluetoothScanner`, `NetworkDiscovery` setzen den Suppression class-weit. Das ist pragmatisch, weil die UI die Permissions garantiert — aber jede neue Util-Methode, die ohne Permission-Check aufgerufen würde, wird lautlos crashen. Lösung: kleinere Scope-Suppressions (pro Methode) oder `PermissionChecker.hasPermission(...)`-Wrapper.

### 6.3 Inkonsistenzen & Doku-Drift

| Stelle | Problem |
|---|---|
| `AGENTS.md` | Sagt „Target SDK 34", `build.gradle.kts` nutzt 35. |
| `README.md` | Erwähnt 34 Dateien / 10.400 Zeilen — aktuelle Zahl ~11.060 Zeilen, Zähler ist veraltet nach dem Map-Patch. |
| `MainActivity.kt:158` | Kommentar „7 pages" vs. 8 tatsächlich (siehe §3.2). |
| `NetworkDiscovery` progress | 4 vs. 5 Phasen — UI-Kommentar vs. Logik. |
| Sprach-Mix | Code-Kommentare teils Deutsch (`MapScreen.kt`), teils Englisch (`WifiScanner.kt`). Nicht kritisch, aber für eine Open-Source-Veröffentlichung vereinheitlichen. |

### 6.4 Magische Zahlen / Duplikate

- Signal-Bucket-Schwellen (`-50`, `-67`, `-80`) sind in mehreren Stellen (`SignalHelper`, `SecurityAuditor`, `BluetoothScreen`) verteilt. Sollten in einer `SignalThresholds`-Konstantenklasse liegen.
- CSV-Separator `;` und BOM sind in `ExportManager` und `WardrivingTracker` separat definiert.

---

## 7. Cross-Reference zum `SECURITY_REVIEW.md`

Ergänzend — die dortigen Findings werden **nicht wiederholt**, nur verlinkt:

| Finding dort | Relevanz hier |
|---|---|
| F-1 (CSV-Formula-Injection) | Gleiche Stelle, die auch in §6.4 als Duplikat-Quelle genannt ist — Fix in einer zentralen `CsvEscape`-Utility schlägt zwei Fliegen. |
| H-1 (`allowBackup=true`) | Koppelt sich mit §4.1: wenn das DB-Schema bricht, könnte ein Backup-Restore die App in einen inkonsistenten Zustand versetzen. Beide Themen zusammen angehen. |
| H-3 (`isMinifyEnabled=false`) | Gleiches Finding wie hier in §6.1 — nur aus anderer Perspektive. |
| H-5 (UPnP Host-Scoping) | In diesem Review nicht erneut diskutiert. |

---

## 8. Was richtig gut gemacht ist

Damit nicht nur Kritik im Protokoll steht:

- **`DeviceDao.upsertDevice`** — das `@Transaction`-basierte „INSERT oder UPDATE-mit-Merge" inklusive „Name nur überschreiben wenn neuer nicht-leer" ist eine der saubersten Lösungen, die ich für dieses Problem gesehen habe.
- **`WifiScanner.startScan`** hat vollständiges Cleanup: Receiver-Unregister, Timeout-Cancellation, SecurityException-Handling, Fallback auf `scanResults`-Cache bei `false` aus `startScan()`. Fast lehrbuchhaft.
- **`SecurityAuditor`** produziert strukturierte, i18n-fähige Findings mit Severity-Farben und Remediation-Text — nicht nur ein Score.
- **MAC-Vendor-DB** und **BLE-UUID-DB** als internes Lookup statt Online-API = offline-fähig und keine Datenschutz-Leaks.
- **FileProvider + `cache-path`** für Exports statt MediaStore-Hacks = korrekt.
- **Distance-Schätzung** via Log-Distance-Pfadverlust (`WifiScanner.kt:217`) — seriös umgesetzt, wenn auch nur grob.

---

## 9. Priorisierte Empfehlungen

### Must-fix vor nächstem Release
1. **§3.1** — BluetoothScreen operator-precedence bug (2-Zeilen-Fix).
2. **§4.1** — Room-Destructive-Migration ersetzen (auch wenn Version 1 ist: jetzt das Gerüst bauen, bevor Schema bricht).
3. **§3.3** — `MapView.onDetach()` im DisposableEffect + User-Agent korrigieren.
4. **F-1** aus `SECURITY_REVIEW.md` (CSV-Formula-Injection).

### Should-fix (ein Sprint)
5. **§4.2** — `Thread.sleep` → `delay` in PortScanner.
6. **§6.1** — ktlint + ein paar Unit-Tests für die Util-Schicht.
7. **§3.2 / §3.3 Pkt. 3** — `beyondBoundsPageCount` & Map-Auto-Recenter gerade ziehen.
8. **§6.3** — Doku-Drift beheben (AGENTS.md Target-SDK, MainActivity-Kommentar).

### Nice-to-have (Tech-Debt-Sprint)
9. **§4.3** — GattExplorer-Continuation pro-Request.
10. **§6.1** — R8/ProGuard aktivieren; Regeln für Room/osmdroid testen.
11. **§5** — InventoryScreen Filter → LazyRow, Map-Diff statt Clear+Rebuild.
12. **§4.6** — Migration der deprecated WifiManager-APIs.

---

## 10. Gesamtnote

| Dimension | Note |
|---|---|
| Architektur | A |
| Code-Stil | B+ |
| Korrektheit | B (wegen §3.1 + §4.1 + §4.2) |
| Reliability | B |
| Test-Abdeckung | F |
| Dokumentation | B |
| Security | B (siehe `SECURITY_REVIEW.md`) |
| **Gesamt** | **B+** |

Mit den vier „Must-fix"-Punkten wird daraus ein solides A-.
