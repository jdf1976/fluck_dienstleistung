# JDF Aufträge – Android-App für Elektro-Dienstleistungen

Persönliche App zur Verwaltung und Dokumentation von Aufträgen. Alles läuft offline auf dem Handy.

## Funktionen
- **Kunden**: Adresse, Telefon, E-Mail, Notizen; Anrufen / Route / Mail direkt aus der App
- **Aufträge**: Titel, Kunde, Einsatzort, Beschreibung, Status (Angefragt → Geplant → In Arbeit → Erledigt → Abgerechnet), Suche und Statusfilter
- **Zeiten**: Datum, von/bis, Pause, Tätigkeit; Summen pro Auftrag
- **Notizen** pro Auftrag mit Zeitstempel
- **Material**: Bezeichnung, Menge, Einheit
- **Fotos**: direkt mit der Kamera aufnehmen oder aus der Galerie übernehmen, dem Auftrag zugeordnet
- **Prüfprotokolle**: PDF/Dateien importieren (z. B. Export aus der Messgeräte-Software) oder Papierprotokoll abfotografieren; Öffnen und Teilen
- **Backup**: komplette Sicherung als ZIP (Daten + Fotos + Protokolle) an einen beliebigen Ort (Nextcloud, Drive, USB) und Wiederherstellung

## APK installieren
1. Jeder Push auf `main` baut die APK automatisch (GitHub Actions).
2. Die APK liegt unter **Releases** (neueste = „Latest“) bzw. unter **Actions → Lauf → Artifacts**.
3. APK auf dem Handy öffnen → Installation aus unbekannten Quellen für Browser/Dateimanager einmalig erlauben.

## Signaturschlüssel einrichten (einmalig, wichtig für Updates)
Damit neue APKs über die alte installiert werden können (Daten bleiben erhalten), braucht es einen festen Schlüssel.
Der liegt **nicht** im Repo, sondern in den GitHub Secrets.

1. Schlüssel erzeugen (Linux/WSL/Proxmox-Shell, Java nötig):
   ```bash
   keytool -genkeypair -keystore release.keystore -alias jdf -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=JDF Dienstleistung, C=DE"
   base64 -w0 release.keystore > release.keystore.b64
   ```
2. GitHub → Repo → **Settings → Secrets and variables → Actions → New repository secret**:
   - `KEYSTORE_BASE64` = Inhalt von `release.keystore.b64`
   - `SIGNING_STORE_PASSWORD` = Keystore-Passwort
   - `SIGNING_KEY_ALIAS` = `jdf`
   - `SIGNING_KEY_PASSWORD` = Schlüssel-Passwort (bei keytool-Standard = Keystore-Passwort)
3. `release.keystore` sicher aufbewahren (Passwortmanager/NAS) – ohne ihn keine Updates mehr.

Ohne Secrets baut der Workflow trotzdem, signiert aber mit einem wechselnden Debug-Schlüssel.

## Technik
Kotlin, Jetpack Compose (Material 3), Room (SQLite), Coil. minSdk 26 (Android 8), targetSdk 35.
Dateien liegen im privaten App-Speicher (`files/attachments`).

Lokal bauen: Android Studio öffnen oder `./gradlew assembleRelease`.
