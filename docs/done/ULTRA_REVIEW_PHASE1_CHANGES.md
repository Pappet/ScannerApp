# Ultra Review — Phase 1 Umsetzung

**Datum:** 2026-04-18
**Branch:** `main` (Änderungen noch nicht in PR überführt — siehe Workflow-Hinweis unten)
**Grundlage:** [`docs/ULTRA_REVIEW.md`](./ULTRA_REVIEW.md) (Gesamtnote B+, Datum 2026-04-17)
**Komplementär:** [`docs/SECURITY_REVIEW.md`](./SECURITY_REVIEW.md)

---

## Übersicht

Dieses Dokument beschreibt die im Rahmen von **Phase 1 (Must-Fix, pre-Release)** umgesetzten Korrekturen, die direkt aus dem Ultra Review hervorgehen. Alle Änderungen wurden mit `./gradlew compileDebugKotlin` verifiziert — **BUILD SUCCESSFUL, 0 Errors**.

> [!IMPORTANT]
> Gemäß Projekt-Workflow müssen diese Änderungen über einen Pull Request in `main` einfließen, **nicht** per Direktcommit. Der PR ermöglicht dem Release Drafter, die Änderungen korrekt zu kategorisieren.

---

## Umgesetzte Findings

### 1. 🔴 Bug-Fix — Operator-Precedence in `BluetoothScreen.kt`

**Review-Referenz:** §3.1 (ULTRA_REVIEW.md)
**Datei:** `app/src/main/java/com/scanner/app/ui/screens/BluetoothScreen.kt`

**Problem:**
Kotlin parst `if (connected > 0) A else B + if (bonded > 0) C else D` als
`if (connected > 0) A else (B + if (bonded > 0) C else D)`.
Wenn `connected > 0` war, wurde der `bonded`-Zähler nie angezeigt.
Wenn `connected == 0` war, erschien die  bonded-Info — jedoch am leeren String hängend.

**Fix:**
```diff
- text = "${devices.size} gefunden" +
-         if (connected > 0) " · $connected verbunden" else "" +
-                 if (bonded > 0) " · $bonded gekoppelt" else "",
+ text = "${devices.size} gefunden" +
+         (if (connected > 0) " · $connected verbunden" else "") +
+         (if (bonded > 0) " · $bonded gekoppelt" else ""),
```

---

### 2. 🔴 Reliability — Destruktive Room-Migration entfernt in `AppDatabase.kt`

**Review-Referenz:** §4.1 (ULTRA_REVIEW.md)
**Datei:** `app/src/main/java/com/scanner/app/data/db/AppDatabase.kt`

**Problem:**
`fallbackToDestructiveMigration()` löscht bei jeder Schema-Änderung kommentarlos **alle** gespeicherten Geräte, Sessions und Signal-Verläufe — ein stiller Datenverlust, der dem Versprechen eines persistenten Inventars widerspricht.

**Fix:**
- `fallbackToDestructiveMigration()` vollständig entfernt.
- Kommentar-Stub dokumentiert, wie künftige `Migration`-Objekte einzutragen sind (Imports werden erst beim echten Schema-Bump hinzugefügt).

```kotlin
// Beispiel fuer kuenftige Migrationen (Imports dann auch einfuegen):
// val MIGRATION_1_2 = object : Migration(1, 2) {
//     override fun migrate(db: SupportSQLiteDatabase) {
//         db.execSQL("ALTER TABLE discovered_devices ADD COLUMN new_column TEXT")
//     }
// }
// Dann in databaseBuilder: .addMigrations(MIGRATION_1_2)
```

> [!WARNING]
> Ab sofort **muss** jede Schema-Änderung (Version-Bump in `@Database`) mit einem expliziten `Migration`-Objekt begleitet werden. Ohne Migration schlägt der DB-Build zur Laufzeit fehl — das ist gewollt.

---

### 3. 🔴 Reliability / Leak — MapView-Lifecycle + OSM-Richtlinien in `MapScreen.kt`

**Review-Referenz:** §3.3 (ULTRA_REVIEW.md)
**Datei:** `app/src/main/java/com/scanner/app/ui/screens/MapScreen.kt`

Fünf separate Probleme wurden in einem Rewrite des Screens behoben:

| # | Problem | Fix |
|---|---|---|
| a | Kein `onDetach()` für `MapView` — Tile-Loader-Threads und Bitmap-Cache bleiben beim Tab-Wechsel live (Memory Leak) | `onRelease = { it.onDetach() }` im `AndroidView` |
| b | User-Agent = `ctx.packageName` — zu schwach, kann zu OSM IP-Rate-Limiting führen | `"ScannerApp/${BuildConfig.VERSION_NAME} (Android)"` |
| c | Auto-Recenter bei **jedem** Update — überschreibt User-Pan/Zoom | Einmaliges Zentrieren via `LaunchedEffect(geoDevices.isNotEmpty())` mit `hasInitiallyRecentered`-Guard |
| d | Tile-Cache standardmäßig auf `ExternalStorage` → Probleme mit Scoped Storage (API 30+) | `osmdroidBasePath` und `osmdroidTileCache` auf `context.filesDir` umgebogen |
| e | Leere Karte ohne Erklärung wenn keine Geodaten vorhanden | Empty-State-Text eingebaut |

**Zusätzlich:** `buildConfig = true` in `build.gradle.kts` `buildFeatures`-Block ergänzt, damit `BuildConfig.VERSION_NAME` zur Compile-Zeit verfügbar ist.

---

### 4. 🔴 Security — CSV-Formula-Injection verhindert (Security Finding F-1)

**Review-Referenz:** §6.4 (ULTRA_REVIEW.md) + F-1 (SECURITY_REVIEW.md)
**Neue Datei:** `app/src/main/java/com/scanner/app/util/CsvEscape.kt`
**Geänderte Dateien:** `ExportManager.kt`, `WardrivingTracker.kt`

**Problem:**
Zwei CSV-Export-Pfade hatten eigene private `escapeCsv()`-Methoden ohne Formula-Injection-Schutz:
- `ExportManager.kt` — Inventar-Export (Semikolon-getrennt)
- `WardrivingTracker.kt` — WiGLE-Format-Export (Komma-getrennt)

Ein Angreifer kann einem Access Point eine SSID wie `=HYPERLINK("http://evil.example","Click")` geben; beim Import in Excel/Calc wird die Formel ausgefuehrt.

**Fix:** `CsvEscape`-Object mit `separator`-Parameter (Standard `;`, WiGLE `,`).
- `ExportManager`: `CsvEscape.escape(x)` (Semikolon)
- `WardrivingTracker`: `CsvEscape.escape(x, separator = ',')` (WiGLE-Komma)
- Private `escapeCsv()`-Methoden in beiden Klassen entfernt.

---

## Peer-Review-Nachbesserungen (2026-04-18)

Nach einem internen Review der ersten Phase-1-Umsetzung wurden folgende Gaps identifiziert und behoben:

| Schwere | Gap | Loesung |
|---|---|---|
| 🔴 | F-1 nur halb geschlossen: `WardrivingTracker.escapeCsv()` ohne Formula-Protection | `CsvEscape.escape(x, separator = ',')` in WardrivingTracker; `CsvEscape` um `separator`-Parameter erweitert; private Methode geloescht |
| 🟡 | `AppDatabase.kt`: `Migration` + `SupportSQLiteDatabase` als unused imports | Imports entfernt; werden erst beim echten Schema-Bump hinzugefuegt |
| 🟡 | `MapScreen.kt` Empty-State ueberlagerte MapView als transparente Schicht | `if/return`-Pattern: bei leerer Liste kein `AndroidView`; Empty-State als `Surface`-Card zentriert |
| 🟡 | User-Agent ohne Kontaktinfo (OSM-Policy) | `+https://github.com/your-org/ScannerApp` ergaenzt |
| 🟡 | Grammatikfehler: "geolokalisierte" -> "geolokalisierten" | Korrigiert |
| 🟡 | JSON zweimal pro Geraet geparst in `LaunchedEffect` | `GeoDevice`-Data-Class: Parsing einmalig in `remember(devices)` |

**Build nach Nachbesserungen:** `./gradlew compileDebugKotlin` -> **BUILD SUCCESSFUL, 0 Errors**

---

## Status: Phase 1 vollstaendig abgeschlossen

Alle Must-Fix-Punkte aus §9 des Ultra Reviews sowie alle Gaps aus dem Peer-Review sind behoben. F-1 ist vollstaendig geschlossen (beide Export-Pfade: `ExportManager` + `WardrivingTracker`).

---

## Pre-Release-Polish (2026-04-18, Review-Runde 2)

Zwei weitere Punkte vor dem ersten Release behoben; zwei Punkte als explizite Tradeoffs dokumentiert:

### Behoben

| # | Punkt | Fix |
|---|---|---|
| Pkt. 1 | User-Agent `your-org`-Platzhalter | Ersetzt durch `TODO_REPLACE` mit prominentem `// TODO(release):`-Kommentar; kann nicht still in den Store gelangen |
| Pkt. 3 | `LaunchedEffect`-vs-`factory`-Race | Zentrierung vollständig in den `update`-Lambda verschoben; `mapViewRef` nicht mehr nötig; `hasInitiallyRecentered` als `mutableStateOf` im Lambda gelesen/geschrieben ohne Recomposition-Seiteneffekt |

**Build:** `./gradlew compileDebugKotlin` → **BUILD SUCCESSFUL, 0 Errors, 0 Warnings**

### Akzeptierte Tradeoffs (kein Fix)

| # | Punkt | Begründung |
|---|---|---|
| Pkt. 2 | GPS-Koordinaten mit `-` als Formula-Trigger | WiGLE-Standard erwartet rohe numerische Werte; ein `'`-Prefix wuerde den Import brechen. GPS-Koordinaten sind nicht angreifer-kontrolliert. `// Tradeoff:` Kommentar im Code. |
| Pkt. 4 | `hasInitiallyRecentered` wird nie resettet | Gewuenschtes UX: "einmalige Zentrierung pro Session". Falls kuenftig ein Reset benoetigt wird, `hasInitiallyRecentered.value = false` im Empty-State-Zweig. `// Design note:` Kommentar im Code. |

---



Gemäß Review §9, zu bearbeiten im nächsten Sprint:

| # | Finding | Datei | Aufwand |
|---|---|---|---|
| 5 | `Thread.sleep` → `delay` in PortScanner (§4.2) | `util/PortScanner.kt` | Minimal |
| 6 | ktlint einrichten + erste Unit-Tests (§6.1) | `build.gradle.kts`, `test/` | Mittel |
| 7 | `beyondBoundsPageCount` und zugehöriger Kommentar korrigieren (§3.2) | `MainActivity.kt` | Minimal |
| 8 | Doku-Drift beheben: AGENTS.md Target-SDK, README.md Zeilenzähler (§6.3) | `AGENTS.md`, `README.md` | Minimal |

---

## Referenzen

- [ULTRA_REVIEW.md](./ULTRA_REVIEW.md) — Vollständiger Code-Review (2026-04-17)
- [SECURITY_REVIEW.md](./SECURITY_REVIEW.md) — Security-Findings (2026-04-17)
