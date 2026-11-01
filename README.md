# Alpenlan Kalender & Kontakte (Android)

Kalender- und Aufgaben-App im Alpenlan-Look für die eigene Nextcloud – ohne Google-Dienste, GrapheneOS-tauglich.

## Wie es funktioniert

```
Nextcloud (cloud.alpenlan.com)  ⇄  DAVx⁵  ⇄  Android-Speicher  ⇄  Alpenlan Kalender
          CalDAV                    Sync        Kalender: CalendarContract
                                                Aufgaben: Tasks.org / OpenTasks
```

- **DAVx⁵** übernimmt Login (Nextcloud-Login-Flow über Authentik), Synchronisation und Konflikte.
- **Alpenlan Kalender** liest und schreibt nur die lokalen Speicher. Änderungen werden von DAVx⁵ hochgeladen.
- **Aufgaben** (Nextcloud Tasks / VTODO) laufen über die OpenTasks-Schnittstelle, die Tasks.org und OpenTasks anbieten.
- **Erinnerungen** für Termine zeigt die App selbst an (Android-Broadcast `EVENT_REMINDER`).

## Funktionen

- Monat (ganze Höhe, Tagesübersicht auf Tipp), Woche und Tag mit Stundenraster
- Termine anlegen, bearbeiten, löschen (mit Rückgängig), Erinnerungen
- Aufgaben mit Listen, Fälligkeit, Priorität, Unteraufgaben – auch im Kalender sichtbar
- Kalender und Aufgabenlisten ein-/ausblenden
- Einstellungen: Startansicht, Wochenbeginn, KW, Rasterbeginn, Stundenhöhe, Standardkalender/-liste/-dauer/-erinnerung

## Installation

1. DAVx⁵ und Tasks.org (oder OpenTasks) von F-Droid installieren.
2. In DAVx⁵ ein Konto für `https://cloud.alpenlan.com` anlegen, Kalender und Aufgabenlisten aktivieren.
3. APK über [Obtainium](https://github.com/ImranR98/Obtainium) mit der URL dieses Repos installieren – Updates kommen automatisch aus den GitHub-Releases.

## Build

GitHub Actions baut bei jedem Push eine APK. Ein Release (für Obtainium) entsteht, sobald `version.txt` eine neue Versionsnummer enthält.
Signiert wird mit den Secrets `KEYSTORE_B64` und `KEYSTORE_PASSWORD` (Alias `alpenlan`).
Lokal: `./gradlew assembleDebug` (Android SDK nötig).

## Alpenlan Kontakte

Zweites App-Symbol in derselben APK. Liest und schreibt die Android-Kontakte, die DAVx⁵ per CardDAV mit Nextcloud abgleicht
(Kontotyp `bitfire.at.davdroid.address_book`): Suche, Favoriten, Geburtstage, A–Z-Leiste, Anrufen/SMS/E-Mail/Karte,
Erstellen/Bearbeiten/Löschen mit Rückgängig.

## Alpenlan Light

Viertes App-Symbol in derselben APK: das Handy als Lichtquelle im Publikum, gesteuert vom Lichtpult (Art-Net) über den Alpenlan-Light-Server (`light.alpenlan.com`, im Heimnetz `192.168.1.212:8090`).

- **Live verbinden:** Server und Zone (1–8 oder Zufall) wählen. Bildschirm und Taschenlampe folgen dem Pult, synchron über eine abgeglichene Uhr. Ein Doppeltipp zeigt Verbindung und Verzögerung.
- **Testmodus:** spielt alle Effekte ohne Server ab (Farben, Dimmer, Strobe, Funkeln, Pulsieren, Gruppen-Blink, Taschenlampe, Regenbogen). Tippen springt weiter.
- **Taschenlampe kurz testen.**

Code unter `light/`. Die Einstellungsseite ist in Compose, das Vollbild-Licht (`LightShowActivity`) ist bewusst schlicht gehalten, für eine ruhige Bildrate. Für unverschlüsselte Verbindungen ist nur `192.168.1.212` freigegeben (`res/xml/network_security_config.xml`).
