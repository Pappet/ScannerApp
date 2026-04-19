# Implementation Plan: Ultra Review Findings

This plan outlines the systematic resolution of the technical debt, bug fixes, and security enhancements identified in the `ULTRA_REVIEW.md` and `SECURITY_REVIEW.md` documents. The work is prioritized based on the urgency indicated in the review.

## User Review Required

> [!WARNING]
> Database Migration: Replacing the destructive migration strategy will mean users' existing data will actually be preserved in future version updates. We will set up a robust migration path if schema changes occur.
> Please review the proposed scope below. Let me know if you would like me to tackle just Phase 1 & 2 (Must-Fix and Should-Fix), or if I should include Phase 3 (Nice-To-Have) as well.

## Proposed Changes

### Phase 1: Must-Fix Items (Pre-Release)

#### [MODIFY] app/src/main/java/com/scanner/app/ui/screens/BluetoothScreen.kt
- **Goal:** Fix the Kotlin string concatenation operator precedence bug that prevents "gekoppelt" (bonded) devices from displaying correctly.
- **Change:** Add parentheses around each `if` branch condition in the string concatenation for `connected` and `bonded` device counts.

#### [MODIFY] app/src/main/java/com/scanner/app/data/db/AppDatabase.kt
- **Goal:** Remove destructive migrations to protect user app data from deletion.
- **Change:** Remove `fallbackToDestructiveMigration()` and set up a proper `Migration` mechanism, or handle schema versions correctly.

#### [MODIFY] app/src/main/java/com/scanner/app/ui/screens/MapScreen.kt
- **Goal:** Bind OSMDroid `MapView` lifecycle correctly to avoid memory leaks.
- **Change:** Add `onDetach()` callback inside the `DisposableEffect`'s `onDispose` block.
- **Change:** Add a proper User-Agent string to the `Configuration` per OSM usage policies.

#### [NEW] app/src/main/java/com/scanner/app/util/CsvEscape.kt (or within StringExt.kt)
#### [MODIFY] app/src/main/java/com/scanner/app/util/ExportManager.kt
- **Goal:** Mitigate CSV-Formula-Injection (Finding F-1).
- **Change:** Introduce a utility function that prepends `'` or strips leading `=`/`+`/`-`/`@` characters for user-controlled strings exported to CSV. Update CSV export logic to utilize this.

---

### Phase 2: Should-Fix Items (Current Sprint)

#### [MODIFY] app/src/main/java/com/scanner/app/util/PortScanner.kt
- **Goal:** Prevent IO Dispatcher thread starvation during scanning.
- **Change:** Replace `Thread.sleep(50)` with Coroutines `delay(50)`.

#### [MODIFY] app/src/main/java/com/scanner/app/MainActivity.kt
- **Goal:** Resolve `beyondBoundsPageCount` inconsistencies in the `HorizontalPager`.
- **Change:** Update the value from 6 to 7 or update the comment to match the reality of having 8 pages.

#### [MODIFY] AGENTS.md & app/build.gradle.kts
- **Goal:** Resolve documentation drift regarding `targetSdkVersion` and other noted discrepancies (e.g. comment language sync).

#### [MODIFY] app/src/main/java/com/scanner/app/ui/screens/MapScreen.kt
- **Goal:** Improve Map Pan/Zoom UX.
- **Change:** Replace continuous auto-recenter behavior with a one-time recenter operation on the first load of data via `LaunchedEffect`.

---

### Phase 3: Nice-to-Have (Backlog / Tech Debt)
*(Will implement if approved for this cycle)*
- **Minification:** Enable ProGuard/R8 in `build.gradle.kts` for shrinking and optimization (`isMinifyEnabled = true`).
- **Linter:** Add ktlint to Gradle for code style enforcement.
- **Concurrency:** Introduce `ConcurrentHashMap` or per-request deferred for `GattExplorer.kt`'s continuation state to prevent future concurrency issues.
- **UI Performance:** Migrate `lazy-loading` issues (e.g. `Filter` chips in `InventoryScreen.kt` from a rigid `Row` to `LazyRow(Modifier.horizontalScroll)`).
- **Unit Tests:** Scaffold basic JUnit tests for utilities (`CsvEscape`, `MacVendorLookup`).

## Open Questions

1. **Scope:** Shall we execute Phase 1 and Phase 2 immediately, and include any Phase 3 tasks if time permits?
2. **CsvEscape:** Should I introduce the CSV sanitization as a standalone utility object or as an extension function on `String`?

## Verification Plan

### Automated Tests
- Check if ktlint formatting passes (if installed).
- Trigger Unit tests for `CsvEscape` (if implemented).

### Manual Verification
- Review the `BluetoothScreen` UI to confirm that bonded devices appear properly in the device counter.
- Manually run the Port Scan to ensure the scan completes fast without freezing the UI or blocking Coroutines.
- Validate CSV Export output to verify that spreadsheet applications (Excel, Calc) treat values beginning with `=` or `+` as text, avoiding injection exploits.
