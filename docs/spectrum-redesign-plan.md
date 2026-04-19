# Spectrum Redesign — Handoff Plan

A concrete plan for porting the remaining screens from the Claude Design
handoff (Variation A · Spectrum) to the native Kotlin app. Written so an
agent can pick a screen off the list and ship it without re-reading the
full JSX tree.

---

## Status

| Area | State |
| --- | --- |
| Theme (palette, typography, shapes) | **done** — `ui/theme/Theme.kt` |
| Fonts (Inter + JetBrains Mono) | **done** — `res/font/` |
| Shared Spectrum primitives | **done** — `ui/components/SpectrumComponents.kt` |
| Nav shell (8-tab Spectrum bottom nav + pager) | **done** — `MainActivity.kt` |
| **MAP** screen | **done** — `ui/screens/MapScreen.kt` |
| **CHANNEL ANALYSIS** screen | **done** — `ui/screens/ChannelAnalysisScreen.kt` |
| WIFI, BT, LAN, MON, SEC, INV | **not started** (Material / old design) |
| WiFi detail, BLE GATT, Export dialog, Onboarding | **not started** (don't exist yet) |

The remaining work is per-screen porting. The theme + primitives layer is
stable; do not modify it unless you find a concrete shortcoming.

---

## Source of truth

All reference designs live in the extracted handoff bundle (gitignored,
local only — unzip `docs/Scanner App-handoff.zip` if missing):

- `docs/handoff-extracted/scanner-app/project/variant-a.jsx` — main screens
- `docs/handoff-extracted/scanner-app/project/variant-a-extra.jsx` — modals + onboarding + (obsolete) fake map
- `docs/handoff-extracted/scanner-app/project/primitives.jsx` — icon set, helper fns
- `docs/handoff-extracted/scanner-app/project/data.jsx` — mock data shapes (useful to understand field names)

The JSX is React-in-browser (not React Native). Read it as a visual
spec, not a code port. Keep all real data wiring (Room queries, scan
controllers, repositories) — only the UI layer changes.

---

## Spectrum primitives already available

Import from `com.scanner.app.ui.components` and `com.scanner.app.ui.theme`.

| Primitive | Maps to JSX | Use for |
| --- | --- | --- |
| `SpectrumHeader(kicker, subtitle, scanning, onScan, stats, trailing)` | `AHeader(...)` | Every top-of-screen block |
| `HeaderStat(value, label)` | `{ value, label }` in the stats row | Header stats |
| `SpectrumScanButton(scanning, onClick)` | the pill button in `AHeader` | Scan toggles (auto-included in header) |
| `SpectrumFilterChip(label, selected, onClick, count?)` | the filter chips | Band / kind / risk filters |
| `SpectrumBottomNav(...)` / `SpectrumTab` | `ANav` | Already wired in `MainActivity` |
| `SignalTrace(rssi, Modifier)` | the tiny waveform | Per-network RSSI glyph |
| `rssiColor(rssi)`, `utilColor(util)` | color helpers | Thresholded colors |
| `HairlineHorizontal()` | the 1-px `borderBottom` dividers | Row / section dividers |
| `SpectrumKicker(text)` / `SpectrumSectionLabel(text)` | uppercase mono labels | Section labels like `NEARBY · 12` |
| `Spectrum.*` (Theme.kt) | `skinA.*` colors | Every color reference |
| `JetBrainsMonoFamily`, `InterFamily` | `fontMono` / `fontBody` | Typography |

Mapping rule: `skinA.accent` → `Spectrum.Accent`, `skinA.danger` → `Spectrum.Danger`, `skinA.gridLine` → `Spectrum.GridLine`, etc. (names match 1:1.)

---

## Per-screen specs

Line ranges below are into the two JSX files. The JSX is the single source
of truth for visual layout; these notes only call out Kotlin-specific
wiring, gotchas, and data-field mapping.

### 1. WIFI — `WifiScreen.kt`

- **Reference:** `variant-a.jsx` lines 92–172 (`AWifi`)
- **Layout:** `SpectrumHeader` with stats `[found, 2.4GHz, 5GHz, risks]`, a 4-chip filter row (`ALL / 2.4GHZ / 5GHZ / ⚠ RISK`), then a scrollable list.
- **Row anatomy (per network):** left 66dp column = large RSSI number in `rssiColor` + `SignalTrace` below it. Middle = SSID (italic + dim if hidden, accent dot if connected) and a mono meta line `${band}GHz · CH${channel} · ${standard} · ${vendor}`. Right = security badge (`⚠` or `•` prefix, `rssiColor`-style tone if risk) and `~${distance}m` below.
- **Data:** from the existing `DeviceRepository.observeDevicesByCategory(WIFI)`; parse `metadata` JSON for `band`, `channel`, `standard`, `vendor`, `security`, `distance`, `wps`, `frequency`, `width`. Risk = `security in {Open, WEP}` or `wps == true`.
- **Interaction:** tap a row → navigate to WiFi detail (see screen 8). Scan button toggles the existing scan controller.
- **German copy:** keep the old German strings from the current screen where they exist (e.g., "Verbunden", "WLAN-Scan starten") — only restyle. Lift English labels (`ALL`, `⚠ RISK`, `found`, `risks`) verbatim from the JSX.

### 2. CHANNEL ANALYSIS — `ChannelAnalysisScreen.kt`

- **Reference:** `variant-a.jsx` lines 176–278 (`AChannel`)
- **Layout:** `SpectrumHeader` stats `[CH<best>, <util>%, <count> channels]`, 2-chip band toggle (`2.4 GHZ BAND` / `5 GHZ BAND`), then an oscilloscope-style bar chart in a bordered card with a grid background (`linear-gradient` lines — replicate with Canvas or nested Boxes), a recommendation card (big `56sp` accent-colored channel number), and a tiny color legend.
- **Bar chart:** per-channel vertical bar, tone = `utilColor`, height ∝ util%. AP count badge above each bar (small circle with border). Channel label below in mono, accent-tinted for the recommended channel.
- **Data:** reuse the existing channel utilization calculation. Don't invent new data.
- **Gotcha:** the grid background is cosmetic. A Canvas with `drawLine` in `Spectrum.GridLine` on a 10×4 grid is simplest.

### 3. BLUETOOTH — `BluetoothScreen.kt`

- **Reference:** `variant-a.jsx` lines 282–407 (`ABluetooth`)
- **Layout:** `SpectrumHeader` stats `[devices, bonded, BLE]`. Main area is a **radar** (1:1 aspect ratio circle) with 3 dashed rings, crosshair, sweeping chartreuse line (rotating animation), a center "you" dot, and one dot per device positioned by `angle = (index * 137.5°) % 360` and `distance = 1 - rssiPct(rssi)` from center.
- **Dot style:** bonded = `Accent`, unbonded = `Accent2` cyan. Selected = 1.5× size with a soft accent halo.
- **Interaction:** tap dot → swap the list below for a detail card (name, vendor, minor class, 2×2 info grid: ADDR, RSSI, BOND, TX) with an `EXPLORE GATT →` CTA. Close button returns to the list.
- **List fallback:** when no selection, show `NEARBY · ${count}` section label and a list sorted by RSSI desc. Each row = small icon (`btTypeIcon`), name (italic dim if `—`), mono meta `${addr} · ${type}`, RSSI number on right in `rssiColor`.
- **Gotcha:** the sweeping line is a 4-second linear loop. Use Compose `rememberInfiniteTransition` + `animateFloat` for the rotation angle. Don't overthink — a single rotating `Canvas` gradient stroke is fine.
- **Interaction:** CTA → navigate to BLE GATT screen (screen 9).

### 4. LAN — `LanScreen.kt`

- **Reference:** `variant-a.jsx` lines 411–447 (`ALan`)
- **Layout:** `SpectrumHeader` stats `[hosts, ports, subnet]`. Flat list, one row per host.
- **Row anatomy:** 32dp square icon tile (`SurfaceRaised` bg, `GridLine` border, `lanRoleIcon(role)` in `Accent`), middle = IP in 14sp mono + meta line `${name} · ${vendor}` in dim mono, right = port chips (first 4, then `+N`). Ports 22/23/80 get `Warning` border+text.
- **Data:** reuse existing LAN scanner results. Map `role` → icon via a lookup table (see `primitives.jsx` `lanRoleIcon` for the role→glyph mapping).

### 5. MONITOR — `MonitorScreen.kt`

- **Reference:** `variant-a.jsx` lines 451–500 (`AMonitor`)
- **Layout:** `SpectrumHeader` stats `[interval, samples, service]`. Below: three cards in a vertical stack, each with a sparkline.
- **Card anatomy:** `SurfaceRaised` + `GridLine` border, rounded 6dp. Top row = mono label on left, big current value (26sp, color-coded) + mono unit on right. Below = a Canvas sparkline (horizontal dashed grid lines every 15 of 60 height units + a stroke path + a 0.08-alpha fill under it). Footer: mono `AVG ${avg}${unit}` left, `n=${count}` right.
- **Metrics:** SIGNAL (dBm, `Accent`), GATEWAY LATENCY (ms, `Accent2`), INTERNET 8.8.8.8 (ms, `Warning`).
- **Data:** reuse the existing foreground-service monitor samples. Keep the same 5s interval.

### 6. SECURITY AUDIT — `SecurityAuditScreen.kt`

- **Reference:** `variant-a.jsx` lines 504–554 (`ASecurity`)
- **Layout:** `SpectrumHeader` stats `[findings, actionable]`. Below: a 96dp grade donut (conic-gradient in `Warning`, centered letter grade in the current issue tone) + descriptor text (`GRADE`, `Moderate exposure`, `2 critical · 2 high · 3 info`). Then one row per finding.
- **Finding row:** 54dp severity badge (colored 1px border + colored text, mono 10sp) on left; title (14sp onSurface), mono target identifier, note paragraph on right.
- **Severity palette:** CRIT → `Danger`, HIGH → `SeverityHigh` (already in theme), MED → `Warning`, LOW → `SeverityLow` (already in theme), INFO → `OnSurfaceDim`.
- **Donut:** a Canvas with `drawArc`. Angle = grade→percent mapping from the existing audit logic.

### 7. INVENTORY — `InventoryScreen.kt`

- **Reference:** `variant-a-extra.jsx` lines 84–174 (`AInventory`)
- **Layout:** custom header (kicker + "Catalog" subtitle + `EXPORT` pill on right — use `SpectrumHeader` with a `trailing` slot instead of the scan button). Below: search input (mono placeholder, close-X when non-empty), then a horizontally-scrollable filter row with chips `ALL / WIFI / BT / LAN` and a `★ FAVS` toggle.
- **Row anatomy:** 34dp square icon tile, middle = name (+ `★` star if favorite) + optional accent-colored label line `"${label}"` + mono `${id} · ${meta}`, right = mono `${sessions}× seen` over `${last}` in `OnSurfaceFaint`.
- **Empty state:** centered mono `— NO MATCHES —` in `OnSurfaceDim`.
- **Export pill:** launches the Export dialog (screen 10).

### 8. WIFI DETAIL — `WifiDetailScreen.kt` (new file)

- **Reference:** `variant-a-extra.jsx` lines 6–80 (`AWifiDetail`)
- **Layout:** custom top bar with close-X on left, `WIFI / DETAIL` kicker center, star (favorite) on right. Below: hero block — SSID (italic+dim if hidden, accent `● CONNECTED` tag when connected), then a 56sp RSSI number in `rssiColor` (with mono `dBm · SIGNAL` label) + a full-width spark waveform svg rendered in accent. Then a 2-column grid of spec tiles (BSSID, CHANNEL, FREQUENCY, WIDTH, STANDARD, VENDOR, SECURITY, WPS, DISTANCE, CAPS) — use a `Grid`-like layout with 1px gaps on a `GridLine`-colored background to get the dividers.
- **Risk panel:** if Open/WEP/WPS → red-bordered card below the grid with a `Danger`-colored kicker and the matching copy (text is in the JSX).
- **Navigation:** launched from WiFi row tap. Can be a separate Compose screen inside the WIFI pager page, or a proper `NavHost` destination — either works; a simple Boolean state switch in `MainActivity` is fine for now.

### 9. BLE GATT — `BleDetailScreen.kt` (exists; rewrite or replace)

- **Reference:** `variant-a-extra.jsx` lines 178–257 (`AGatt`)
- **Layout:** custom header (close-X, `BT / GATT` kicker + device name subtitle, `● LINK` blinking indicator). Stats strip under: `${addr} · ${rssi} dBm · ${svc_count} svc · ${char_count} chr`. Body: accordion-style service list. Expanded service shows its characteristics in a raised panel. Selecting a characteristic pops a bottom sheet with UUID + value.
- **Characteristic row:** `0x${uuid}` mono handle on left, name/value stack center, property tag chips (READ/WRITE/NOTIFY/INDICATE) on right.
- **Data:** reuse the existing GATT discovery result. The JSX uses a mock `GATT_TREE` — map it to whatever shape your discovery returns.

### 10. EXPORT DIALOG — `ExportDialog.kt` (new file, or reuse existing export code)

- **Reference:** `variant-a-extra.jsx` lines 386–460 (`AExport`)
- **Layout:** bottom-sheet modal (rounded top corners, accent top border) over a 0.85 alpha dark scrim. Header: `EXPORT` kicker + "Package data" subtitle + close-X. Sections: FORMAT (3 tile picker: CSV / JSON / PDF), INCLUDE (3 chip toggles: WIFI / BT / LAN), `★ FAVORITES ONLY` checkbox. Bottom: full-width accent action button `EXPORT ${count} ITEMS · ${FMT} →`.
- **Behavior:** tapping outside the sheet closes it. Count updates live based on toggles.
- **Wiring:** existing export code should plug into the action button onClick.

### 11. ONBOARDING — `OnboardingScreen.kt` (new file)

- **Reference:** `variant-a-extra.jsx` lines 464–520 (`AOnboarding`)
- **Layout:** full-screen, 3 steps with thin segmented progress ticks at top. Each step: mono accent kicker (`SCANNER / 01`, `PERMISSIONS / 02`, `READY / 03`), a 38sp display title, optional body paragraph, optional permissions list (icon + name + description rows). Bottom: full-width accent CTA. Intermediate steps get a dim `← BACK` link below the CTA.
- **Permissions step:** a list of 4 rows (Nearby WiFi, Bluetooth scan, Location fine, Notifications). `GRANT ALL` triggers the normal Android permission flow on the corresponding manifest permissions.
- **Show condition:** first-run only. Gate on a `SharedPreferences`/`DataStore` boolean set after `onDone`.

---

## Implementation order (suggested)

Low-risk → high-risk, so each PR stays reviewable.

1. **LAN** — simplest list, proves the header + row pattern on fresh data.
2. **INVENTORY** — adds search + filter chips + trailing header slot.
3. **WIFI** — adds `SignalTrace`, risk flags, connected state, deep link to detail.
4. **WIFI DETAIL** — new screen, spec grid + risk panel.
5. **CHANNEL** — adds Canvas bar chart with grid background.
6. **MONITOR** — adds Canvas sparklines.
7. **SECURITY AUDIT** — adds donut Canvas + severity badges.
8. **BLUETOOTH** — adds the animated radar, hardest visual piece.
9. **BLE GATT** — rewrite the existing detail screen.
10. **EXPORT DIALOG** — bottom-sheet modal.
11. **ONBOARDING** — new flow + first-run gate.

---

## Per-screen acceptance checklist

Copy-paste this into each PR description and tick before merging.

- [ ] Header matches the JSX: kicker, subtitle, stats row (order + labels + values).
- [ ] Scan button (if present) uses `SpectrumScanButton`, reflects the real scanning state, triggers the real scan controller.
- [ ] All colors come from `Spectrum.*` — no hardcoded hex strings in the screen file.
- [ ] Monospaced copy (filter chips, stats, meta lines) uses `JetBrainsMonoFamily` at the JSX font sizes (10/11/12 sp).
- [ ] Dividers are `HairlineHorizontal`, not `Modifier.border` hacks.
- [ ] German user-facing copy preserved from the existing Kotlin screen; new English labels (section kickers, filter chip text) lifted verbatim from the JSX.
- [ ] Empty / loading / permission-denied states handled with Spectrum styling (not default Material).
- [ ] No changes to the theme, shared components, or nav shell (unless adding a new primitive — in which case add it to `SpectrumComponents.kt` and justify in the PR).
- [ ] `./gradlew assembleDebug` passes.
- [ ] Visual check on a real device or emulator — screenshot in the PR.

---

## Conventions & gotchas

- **osmdroid / AndroidView:** any embedded Android View needs `Modifier.clipToBounds()` on its container and an opaque background — hard-learned in the Map screen (see commit `7901ac5`).
- **Pager bleed:** `HorizontalPager` in `MainActivity` uses `beyondBoundsPageCount = 1` + per-page `clipToBounds()`. Do not bump either without testing all adjacent tabs.
- **Density-aware bitmaps:** any marker or icon bitmap drawn to a Canvas must scale by `context.resources.displayMetrics.density`. Raw pixel sizes are invisible targets on 3× screens.
- **Data shape:** mock data in `data.jsx` often has fields the real repository doesn't carry (e.g., `standard`, `width`, `vendor`). Map what you have; fall back to `"—"` (mono, `OnSurfaceDim`) for missing fields rather than hiding the row.
- **German copy:** the app ships in German. Lift JSX copy ONLY for short English technical labels (CH, RSSI, WIFI, SCAN, etc.). Keep full-sentence German copy from the current screens.
- **Branch:** work happens on `spectrum-design`. Merge to `main` only once the full set lands.
- **Small commits:** one screen per commit, message format `Spectrum <SCREEN>: <short note>` (see `7901ac5` for template).

---

## How to pick up a screen

1. Open the JSX file at the referenced line range. Read the visible layout.
2. Open the current Kotlin file (if it exists). Note the real data it already reads — keep all of that wiring.
3. Rewrite the Composable body using Spectrum primitives. Replace Material components with raw Boxes + Rows where necessary.
4. Build (`./gradlew compileDebugKotlin`) as you go.
5. Install on device, check the tab visually, tick the acceptance list, commit.

You do not need to read `variant-b.jsx` or `android-frame.jsx` — those are
alternative designs / web-only scaffolding and are not in scope.
