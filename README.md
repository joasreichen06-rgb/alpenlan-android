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

## Alpenlan Browser

Fünftes App-Symbol in derselben APK: ein Browser mit fest eingebautem Filter. Weil alles in der App passiert, wirkt er gleich über WLAN, Mobilfunk und VPN.

- **Jede Anfrage wird geprüft** (Seite, Bilder, Videos, Skripte, Service Worker). über 160 Seiten sind fest gesperrt (YouTube, Instagram, TikTok, Facebook, X, Reddit, Snapchat, Erwachsenen-Seiten, Übersetzungs-Proxys, Web-Archive, Suchmaschinen ohne SafeSearch), dazu rund 190 Wörter wie «bikini», «baywatch», «bademode», «topless», «youtube» – auch in Schreibweisen wie «B1k1n1».
- **Worterkennung gegen Umgehung** (`WordMatcher.java`): erkennt Wörter auch mit Leerzeichen, Punkten oder Bindestrichen dazwischen («bay watch», «b.i.k.i.n.i»), mit Zahlen/Zeichen («b1k1n1», «$exy»), Akzenten und kyrillischen Doppelgängern, wiederholten Buchstaben («biiiikini») und einem Tippfehler bei langen Wörtern («baywatsch»). Harmlose Wörter wie «Sussex», «Nacktschnecke» oder «exotisch» bleiben erlaubt. Zufällige Kennungen in Adressen werden übersprungen, Suchbegriffe (`q=` usw.) immer vollständig geprüft.
- **Seiteninhalt** wird nach dem Laden (und nach 2,5 s und 8 s nochmals) geprüft, inklusive Bildbeschreibungen: ein «starkes» Wort, 2 verschiedene Wörter oder 3 Treffer sperren die Seite. Plattform-Namen in Fusszeilen zählen nicht.
- **Suchseiten ohne Bilder:** Vorschaubilder von Google, Bing und DuckDuckGo werden blockiert und ausgeblendet.
- **Positivliste** (strengster Modus, mit PIN): nur aufgeführte Seiten plus Suchmaschinen und Heimnetz.
- **SafeSearch erzwungen** bei Google (`safe=active`), Bing (`adlt=strict`) und DuckDuckGo (`kp=1`).
- **Einstellungen hinter PIN** (⋮ → Filter-Einstellungen): eigene Seiten, Wörter, Ausnahmen, Suchmaschine. Die fest eingebauten Listen lassen sich nicht entfernen.
- **Bilder- und Videosuche gesperrt** (Google, Bing, DuckDuckGo), abschaltbar mit PIN. Alle grossen Social-Media-Plattformen mit Endlos-Feed (TikTok, Facebook, X, Threads, Reddit, Snapchat, Pinterest, Tumblr, Bluesky, Twitch, 9GAG …) sind fest gesperrt.
- **Heimnetz:** Router, Proxmox, NAS usw. lassen sich per IP oder lokalem Namen öffnen (`192.168.1.1`, `proxmox:8006`, `fritz.box`). Unverschlüsselt erlaubt sind nur übliche Heimnetz-Bereiche (siehe `network_security_config.xml`), alle anderen `http://`-Adressen werden auf `https://` umgestellt. Selbst signierte Zertifikate werden nur im Heimnetz und nur nach Rückfrage akzeptiert.
- **Netzwerk-Karte** auf der Startseite: Verbindungsart, eigene IP, Router (antippbar), DNS, privates DNS, VPN-Status und -IP, öffentliche IP auf Knopfdruck.
- Keine APK-Downloads, keine Links in andere Apps ausser Telefon/E-Mail/SMS, kein Kamera-/Mikrofon-/Standortzugriff für Webseiten.

Code unter `browser/` (Java), Start- und Sperrseite unter `assets/browser/`. Der Filter-Kern (`Filter.java`) hat keine Android-Abhängigkeiten und lässt sich mit reinem Java testen.
