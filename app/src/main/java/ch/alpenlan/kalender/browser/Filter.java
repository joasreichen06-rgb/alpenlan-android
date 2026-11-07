package ch.alpenlan.kalender.browser;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Der Filter-Kern des Alpenlan Browsers. Reines Java ohne Android-Abhängigkeiten,
 * damit er sich leicht testen lässt.
 *
 * Er prüft jede Adresse, die der Browser lädt (Seiten, Bilder, Skripte, Videos):
 *  1. Gesperrte Domains (Kernliste im Code + eigene Liste aus den Einstellungen)
 *  2. Gesperrte Wörter irgendwo in der Adresse (z. B. in einer Suchanfrage)
 *  3. Suchmaschinen: SafeSearch wird erzwungen, unbekannte Suchmaschinen werden gesperrt
 *
 * Weil das alles in der App selbst passiert, wirkt es unabhängig von VPN oder DNS.
 */
public final class Filter {

    /** Ergebnis einer Prüfung. */
    public static final class Decision {
        public enum Kind { ALLOW, BLOCK, REDIRECT }
        public final Kind kind;
        public final String reason;   // bei BLOCK: lesbarer Grund
        public final String url;      // bei REDIRECT: neue, sichere Adresse

        private Decision(Kind kind, String reason, String url) {
            this.kind = kind; this.reason = reason; this.url = url;
        }
        static Decision allow() { return new Decision(Kind.ALLOW, null, null); }
        static Decision block(String reason) { return new Decision(Kind.BLOCK, reason, null); }
        static Decision redirect(String url) { return new Decision(Kind.REDIRECT, null, url); }
        public boolean blocked() { return kind == Kind.BLOCK; }
        @Override public String toString() {
            return kind + (reason != null ? " (" + reason + ")" : "") + (url != null ? " -> " + url : "");
        }
    }

    // ---------------------------------------------------------------- Kernlisten
    // Diese Listen sind fest eingebaut. Sie lassen sich in den Einstellungen NICHT
    // entfernen, auch nicht nach dem Löschen der App-Daten.

    static final List<String> CORE_DOMAINS = Collections.unmodifiableList(Arrays.asList(
        // YouTube
        "youtube.com", "youtu.be", "youtube-nocookie.com", "ytimg.com", "googlevideo.com",
        "youtubei.googleapis.com", "youtubekids.com", "yt.be",
        // Instagram
        "instagram.com", "cdninstagram.com", "ig.me", "instagr.am",
        // Social Media mit Endlos-Feed (Doomscroll)
        "tiktok.com", "tiktokcdn.com", "tiktokcdn-us.com", "tiktokv.com", "tiktokw.us", "vm.tiktok.com",
        "facebook.com", "fb.com", "fb.watch", "fbcdn.net", "fbsbx.com", "facebook.net",
        "x.com", "twitter.com", "twimg.com", "t.co", "threads.net", "threads.com",
        "snapchat.com", "snap.com", "sc-cdn.net", "snapkit.com",
        "reddit.com", "redd.it", "redditmedia.com", "redditstatic.com", "reddituploads.com",
        "pinterest.com", "pinimg.com", "pin.it", "tumblr.com", "bsky.app", "bsky.social",
        "9gag.com", "imgur.com", "ifunny.co", "vsco.co", "lemon8-app.com", "bereal.com",
        "likee.video", "triller.co", "twitch.tv", "ttvnw.net", "jtvnw.net", "kick.com",
        "rumble.com", "dailymotion.com", "dai.ly", "bilibili.com", "bilibili.tv", "douyin.com",
        "kuaishou.com", "kwai.com", "weibo.com", "vk.com", "vk.ru", "ok.ru", "truthsocial.com",
        "mastodon.social", "mastodon.online", "joinmastodon.org", "gettr.com", "gab.com",
        // Erwachsenen-Seiten
        "pornhub.com", "pornhub.org", "phncdn.com", "xvideos.com", "xvideos-cdn.com", "xnxx.com",
        "xhamster.com", "xhamster.desi", "xhcdn.com", "redtube.com", "youporn.com", "tube8.com",
        "spankbang.com", "eporner.com", "beeg.com", "tnaflix.com", "txxx.com", "hqporner.com",
        "porntrex.com", "4tube.com", "porn.com", "motherless.com", "brazzers.com", "bangbros.com",
        "onlyfans.com", "fansly.com", "chaturbate.com", "stripchat.com", "bongacams.com",
        "livejasmin.com", "cam4.com", "camsoda.com", "myfreecams.com", "manyvids.com",
        "nhentai.net", "hanime.tv", "rule34.xxx", "rule34.paheal.net", "e-hentai.org",
        "exhentai.org", "f95zone.to", "erome.com", "thothub.to", "fapello.com", "coomer.su",
        "kemono.su", "redgifs.com", "iwara.tv", "literotica.com", "sex.com", "xxx.com",
        // Umgehungswege: Übersetzungs-Proxys, Archive, Web-Proxys
        "translate.goog", "web.archive.org", "archive.org", "archive.ph", "archive.is",
        "archive.today", "archive.li", "archive.vn", "croxyproxy.com", "croxyproxy.rocks",
        "proxysite.com", "hide.me", "hidester.com", "kproxy.com", "4everproxy.com",
        "proxyium.com", "blockaway.net", "proxyscrape.com",
        // Suchmaschinen ohne erzwingbares SafeSearch
        "startpage.com", "search.brave.com", "yandex.com", "yandex.ru", "ya.ru",
        "search.yahoo.com", "baidu.com", "qwant.com", "ecosia.org", "mojeek.com",
        "presearch.com", "you.com", "metager.org", "searx.be", "search.aol.com",
        "yep.com", "kagi.com", "perplexity.ai"
    ));

    static final List<String> CORE_KEYWORDS = Collections.unmodifiableList(Arrays.asList(
        "porn", "xxx", "nsfw", "hentai", "nackt", "nude", "naked", "nudes", "bikini", "lingerie",
        "dessous", "unterwäsche", "erotik", "erotic", "sexy", "sex video", "sexvideo", "camgirl",
        "onlyfans", "fansly", "rule34", "r34", "milf", "boobs", "titten", "brüste", "busen",
        "stripper", "striptease", "playboy", "nipple", "nippel", "booty", "twerk", "thong",
        "string tanga", "upskirt", "hot girl", "youtube", "instagram",
        // Bademode und Strand
        "baywatch", "badeanzug", "badeanzuege", "bademode", "swimsuit", "swimwear", "beachwear",
        "monokini", "tankini", "bikinimodel", "beach babe", "wet t shirt", "wet tshirt",
        // Unterwäsche und Dessous
        "reizwaesche", "negligee", "strapse", "panties", "panty", "hoeschen",
        "victoria s secret", "victorias secret", "savage x fenty", "corsage",
        "sports illustrated swimsuit", "yoga pants", "tanga", "g string", "hotpants", "hot pants",
        "unterhose", "bralette", "sport bh", "bh", "braless", "no bra", "underwear", "camel toe",
        "sideboob", "underboob", "minirock", "strumpfhose", "nylons", "bathing suit", "string bikini",
        "culotte", "sous vetement", "maillot de bain", "costume da bagno", "reggiseno", "mutande",
        // Freizügig
        "topless", "oben ohne", "nacktbaden", "fkk", "nudist", "naturist", "freikoerperkultur",
        "cleavage", "dekollete", "busty", "hottie", "hotties", "pinup", "pin up", "glamour model",
        "thirst trap", "lap dance", "stripclub", "poledance girl", "heisse frau", "heisse girl",
        "heisse maedchen", "sexy girl", "babe", "babes", "baddie", "thicc", "spicy pics",
        "nue", "nues", "seins", "sexe", "nuda", "nudo", "tette", "desnuda", "tetas",
        // Sexuell
        "sex", "sexual", "erotisch", "erotica", "porno", "pron", "fetish", "fetisch", "bdsm",
        "kinky", "horny", "lewd", "ecchi", "ahegao", "escort", "callgirl", "hooker", "bordell",
        "brothel", "seitensprung", "sugar daddy", "sugardaddy", "camsex", "webcam girl",
        "feet pics", "foot fetish", "deepnude", "undress ai", "nudify", "clothoff",
        "masturb", "orgasm", "dildo", "blowjob", "handjob", "cumshot", "creampie", "gangbang",
        "threesome", "anal", "ficken", "gefickt", "fick", "bumsen", "nutte", "hure", "schlampe",
        "muschi", "sexting", "gooning", "jerk off", "wichsen", "leaked nudes",
        // Körper
        "ass", "butt", "pawg", "titties", "tits", "nackte", "nacktbilder", "nacktfotos",
        // Plattformen (auch in Suchanfragen, Teilen-Knöpfen und Länder-Adressen wie pinterest.ch)
        "tiktok", "facebook", "twitter", "snapchat", "reddit", "pinterest", "tumblr", "9gag",
        "bereal", "doomscroll"
    ));

    /** Suchmaschinen, bei denen SafeSearch per Adresse erzwungen wird. */
    public enum Engine {
        GOOGLE("Google", "https://www.google.com/search?safe=active&q="),
        DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?kp=1&q="),
        BING("Bing", "https://www.bing.com/search?adlt=strict&q=");

        public final String label;
        final String prefix;
        Engine(String label, String prefix) { this.label = label; this.prefix = prefix; }

        public String searchUrl(String query) {
            try {
                return prefix + java.net.URLEncoder.encode(query, "UTF-8");
            } catch (java.io.UnsupportedEncodingException e) {
                throw new IllegalStateException(e);
            }
        }
        public static Engine byName(String name) {
            for (Engine e : values()) if (e.name().equals(name)) return e;
            return GOOGLE;
        }
    }

    // ---------------------------------------------------------------- Zustand

    private final Set<String> domains = new LinkedHashSet<>();
    private final List<String> keywords = new ArrayList<>();
    private final Set<String> allowDomains = new LinkedHashSet<>();
    private final boolean blockMediaSearch;
    private final boolean whitelistOnly;
    private final Set<String> whitelist = new LinkedHashSet<>();
    private final WordMatcher matcher;

    /** Plattform-Namen: in Suchanfragen gesperrt, im Seiteninhalt aber normal (Fusszeilen, Teilen-Knöpfe). */
    static final Set<String> PAGE_SCAN_IGNORE = new java.util.HashSet<>(Arrays.asList(
        "youtube", "instagram", "tiktok", "facebook", "twitter", "snapchat", "reddit", "pinterest",
        "tumblr", "9gag", "bereal", "doomscroll", "bh", "babe", "babes", "spicy pics", "butt", "ass",
        "nue", "nues", "seins", "nudo", "anal", "escort", "hure", "minirock", "nylons", "underwear"
    ));

    /** Wörter, bei denen schon ein einziger Treffer im Seiteninhalt reicht. */
    static final Set<String> PAGE_SCAN_STRONG = new java.util.HashSet<>(Arrays.asList(
        "porn", "porno", "xxx", "nsfw", "hentai", "onlyfans", "fansly", "camsex", "camgirl",
        "sexvideo", "sex video", "nacktbilder", "nacktfotos", "blowjob", "handjob", "cumshot",
        "creampie", "gangbang", "nudify", "deepnude", "leaked nudes", "rule34", "milf", "pawg"
    ));

    /** Vorschlag für die Positivliste. */
    public static final String DEFAULT_WHITELIST =
        "alpenlan.com\nsrf.ch\nwikipedia.org\nwikimedia.org\nmeteoschweiz.admin.ch\nadmin.ch\n"
        + "sbb.ch\nch.ch\npost.ch\ngithub.com\nproton.me\ngrapheneos.org\n";

    public Filter() { this(null, null, null, true); }

    /** Voreinstellung der änderbaren Liste (mit PIN entfernbar). */
    public static final String DEFAULT_EXTRA_DOMAINS = "";
    public static final String DEFAULT_EXTRA_KEYWORDS = "";

    public Filter(String extraDomains, String extraKeywords, String allowList) {
        this(extraDomains, extraKeywords, allowList, true);
    }

    /**
     * @param extraDomains  zusätzliche Domains (eine pro Zeile)
     * @param extraKeywords zusätzliche Wörter (eins pro Zeile)
     * @param allowList     Domains, auf denen Wörter NICHT geprüft werden (für Fehlalarme).
     *                      Gilt nicht für gesperrte Domains und nicht für Suchmaschinen.
     */
    public Filter(String extraDomains, String extraKeywords, String allowList, boolean blockMediaSearch) {
        this(extraDomains, extraKeywords, allowList, blockMediaSearch, false, null);
    }

    /**
     * @param whitelistOnly nur Seiten aus {@code whitelistText} (plus Suchmaschinen und Heimnetz) erlaubt
     */
    public Filter(String extraDomains, String extraKeywords, String allowList, boolean blockMediaSearch,
                  boolean whitelistOnly, String whitelistText) {
        this.blockMediaSearch = blockMediaSearch;
        this.whitelistOnly = whitelistOnly;
        for (String d : lines(whitelistText)) {
            String c = cleanDomain(d);
            if (!c.isEmpty()) whitelist.add(c);
        }
        for (String d : CORE_DOMAINS) domains.add(d);
        for (String d : lines(extraDomains)) domains.add(cleanDomain(d));
        keywords.addAll(CORE_KEYWORDS);
        for (String k : lines(extraKeywords)) {
            if (k.trim().length() >= 2 && !keywords.contains(k.trim())) keywords.add(k.trim());
        }
        matcher = new WordMatcher(keywords);
        for (String d : lines(allowList)) {
            String c = cleanDomain(d);
            if (!c.isEmpty() && !isBlockedDomain(c) && searchEngineOf(c) == null) allowDomains.add(c);
        }
        domains.remove("");
    }

    // ---------------------------------------------------------------- Hauptprüfung

    /**
     * Prüft eine Adresse.
     * @param url        die vollständige Adresse
     * @param mainFrame  true, wenn es die Seite selbst ist (nicht ein Bild/Skript darin)
     */
    public Decision check(String url, boolean mainFrame) {
        if (url == null || url.isEmpty()) return Decision.allow();
        String lower = url.trim().toLowerCase(Locale.ROOT);

        // Interne Seiten des Browsers
        if (lower.startsWith("alpenlan:") || lower.startsWith("about:blank")) return Decision.allow();
        if (lower.startsWith("data:") || lower.startsWith("blob:")) {
            // data:/blob: entstehen innerhalb einer Seite, die bereits geprüft wurde.
            // Als ganze Seite (z. B. data:text/html,...) sind sie aber ein Umgehungsweg.
            return mainFrame ? Decision.block("Interne Adresse nicht erlaubt") : Decision.allow();
        }
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            return Decision.block("Adresstyp nicht erlaubt");
        }

        URI uri;
        try {
            uri = new URI(fixForUri(url.trim()));
        } catch (Exception e) {
            return Decision.block("Ungültige Adresse");
        }
        String host = uri.getHost();
        if (host == null || host.isEmpty()) return Decision.block("Ungültige Adresse");
        host = cleanDomain(host);

        // 1. Gesperrte Domains
        String hit = blockedDomainOf(host);
        if (hit != null) return Decision.block("Gesperrte Seite: " + hit);

        // 2. Gesperrte Wörter (in Host, Pfad und Suchanfrage, dekodiert)
        if (!isAllowListed(host)) {
            String kw = keywordIn(uri);
            if (kw != null) return Decision.block("Gesperrtes Wort: «" + kw + "»");
        }

        // 3. Bilder- und Videosuche (dort ist SafeSearch am schwächsten)
        if (blockMediaSearch && isMediaSearch(uri, host)) {
            return Decision.block("Bilder- und Videosuche ist gesperrt");
        }

        // 4. Positivliste: nur erlaubte Seiten (Suchmaschinen und Heimnetz immer)
        if (whitelistOnly && mainFrame && searchEngineOf(host) == null && !isLocalAddress(host)
                && !inWhitelist(host)) {
            return Decision.block("Nicht auf der Liste erlaubter Seiten");
        }

        // 5. Suchmaschinen: SafeSearch erzwingen
        if (mainFrame) {
            String safe = enforceSafeSearch(uri, host, url.trim());
            if (safe != null) return Decision.redirect(safe);
        }
        return Decision.allow();
    }

    /** Prüft einen Text, z. B. einen Seitentitel, auf gesperrte Wörter. */
    public String keywordInText(String text) {
        return matcher.find(text);
    }

    /**
     * Prüft den sichtbaren Text einer Seite. Gibt einen Sperrgrund zurück oder null.
     * Gesperrt wird bei einem «starken» Wort, bei 2 verschiedenen Wörtern oder 3 Treffern insgesamt.
     */
    public String scanPage(String text) {
        java.util.Map<String, Integer> hits = matcher.count(text, PAGE_SCAN_IGNORE);
        if (hits.isEmpty()) return null;
        int total = 0;
        for (java.util.Map.Entry<String, Integer> e : hits.entrySet()) {
            if (PAGE_SCAN_STRONG.contains(e.getKey())) return "Seiteninhalt: «" + e.getKey() + "»";
            total += e.getValue();
        }
        if (hits.size() >= 2 || total >= 3) {
            StringBuilder b = new StringBuilder("Seiteninhalt: ");
            int n = 0;
            for (String k : hits.keySet()) {
                if (n++ == 3) break;
                if (n > 1) b.append(", ");
                b.append('«').append(k).append('»');
            }
            return b.toString();
        }
        return null;
    }

    public boolean isSearchEngine(String host) { return host != null && searchEngineOf(cleanDomain(host)) != null; }

    public boolean blocksMediaSearch() { return blockMediaSearch; }

    private boolean inWhitelist(String host) {
        for (String d : whitelist) if (host.equals(d) || host.endsWith("." + d)) return true;
        return false;
    }

    // ---------------------------------------------------------------- Domains

    public boolean isBlockedDomain(String host) { return blockedDomainOf(host) != null; }

    private String blockedDomainOf(String host) {
        for (String d : domains) {
            if (host.equals(d) || host.endsWith("." + d)) return d;
            // Übersetzungs-Proxy-Schreibweise: pornhub-com.translate.goog
            if (host.contains(d.replace('.', '-') + ".")) return d;
        }
        return null;
    }

    /** true, wenn auf dieser Seite keine Wörter geprüft werden (Ausnahmeliste). */
    public boolean allowsWordsOn(String host) { return isAllowListed(cleanDomain(host)); }

    private boolean isAllowListed(String host) {
        for (String d : allowDomains) if (host.equals(d) || host.endsWith("." + d)) return true;
        return false;
    }

    // ---------------------------------------------------------------- Wörter

    /** Parameter, die Suchbegriffe enthalten: werden immer vollständig geprüft. */
    private static final Set<String> SEARCH_PARAMS = new java.util.HashSet<>(Arrays.asList(
        "q", "p", "query", "search", "search_query", "s", "k", "keyword", "keywords", "text", "term",
        "wd", "searchterm", "suche", "suchbegriff", "find", "oq", "as_q", "as_epq", "as_oq"));

    /**
     * Prüft eine Adresse auf gesperrte Wörter. Suchbegriffe werden vollständig geprüft;
     * im Rest der Adresse werden zufällig aussehende Kennungen (z. B. «bH_ERri9cBSG») übersprungen,
     * damit sie nicht zufällig ein gesperrtes Wort enthalten.
     */
    private String keywordIn(URI uri) {
        StringBuilder text = new StringBuilder();
        text.append(uri.getHost() == null ? "" : stripIds(uri.getHost()));
        if (uri.getRawPath() != null) text.append(' ').append(stripIds(decode(uri.getRawPath())));
        String q = uri.getRawQuery();
        if (q != null) {
            for (String part : q.split("&")) {
                int eq = part.indexOf('=');
                String name = decode(eq < 0 ? part : part.substring(0, eq)).toLowerCase(Locale.ROOT);
                String value = eq < 0 ? "" : decode(part.substring(eq + 1));
                text.append(' ').append(SEARCH_PARAMS.contains(name) ? value : stripIds(name + " " + value));
            }
        }
        if (uri.getRawFragment() != null) text.append(' ').append(stripIds(decode(uri.getRawFragment())));
        return matcher.find(text.toString());
    }

    /** Mehrfach dekodieren, um verschachtelte Kodierung (%2520 ...) aufzulösen. */
    private static String decode(String s) {
        String decoded = s;
        for (int i = 0; i < 3; i++) {
            String next;
            try {
                next = URLDecoder.decode(decoded.replace("%%", "%25%"), StandardCharsets.UTF_8.name());
            } catch (Exception e) {
                next = decoded.replace('+', ' ');
            }
            if (next.equals(decoded)) break;
            decoded = next;
        }
        return decoded;
    }

    /** Entfernt Kennungen wie «Gk0QfmFDGNuDe5Ps» oder «a3f9c0d2e1», lässt Wörter und Slugs stehen. */
    static String stripIds(String s) {
        StringBuilder out = new StringBuilder(s.length());
        for (String t : s.split("[^\\p{L}\\p{N}_\\-$@!]+")) {
            if (t.isEmpty() || isIdLike(t)) continue;
            out.append(t).append(' ');
        }
        return out.toString();
    }

    static boolean isIdLike(String t) {
        if (t.length() < 8) return false;
        int upperInside = 0, switches = 0;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (i > 0 && Character.isUpperCase(c) && t.charAt(i - 1) != '-' && t.charAt(i - 1) != '_') upperInside++;
            if (i > 0) {
                char p = t.charAt(i - 1);
                if ((Character.isDigit(c) && Character.isLetter(p)) || (Character.isLetter(c) && Character.isDigit(p))) switches++;
            }
        }
        return upperInside >= 2 || switches >= 3;
    }

    // ---------------------------------------------------------------- Bildersuche

    private static boolean isMediaSearch(URI uri, String host) {
        // Vorschaubilder der Suchmaschinen
        if (host.startsWith("encrypted-tbn") && host.endsWith(".gstatic.com")) return true;
        if (host.equals("th.bing.com") || host.endsWith(".mm.bing.net")) return true;
        if (host.equals("external-content.duckduckgo.com") || host.equals("proxy.duckduckgo.com")) return true;
        Search s = searchEngineOf(host);
        String rawHost = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        String path = uri.getRawPath() == null ? "" : uri.getRawPath();
        String q = uri.getRawQuery();
        if (s == Search.GOOGLE) {
            if (rawHost.startsWith("images.")) return true;
            if (path.startsWith("/imghp") || path.startsWith("/images")) return true;
            String tbm = param(q, "tbm"), udm = param(q, "udm");
            return "isch".equals(tbm) || "vid".equals(tbm) || "2".equals(udm) || "7".equals(udm);
        }
        if (s == Search.BING) {
            return path.startsWith("/images") || path.startsWith("/videos");
        }
        if (s == Search.DDG) {
            if (path.startsWith("/i.js") || path.startsWith("/v.js")) return true;
            String ia = param(q, "ia"), iax = param(q, "iax");
            return "images".equals(ia) || "videos".equals(ia) || "images".equals(iax) || "videos".equals(iax);
        }
        return host.equals("lens.google.com") || host.endsWith(".lens.google.com")
            || host.equals("tineye.com") || host.endsWith(".tineye.com");
    }

    private static String param(String rawQuery, String name) {
        if (rawQuery == null) return null;
        for (String p : rawQuery.split("&")) {
            int eq = p.indexOf('=');
            if ((eq < 0 ? p : p.substring(0, eq)).equals(name)) return eq < 0 ? "" : p.substring(eq + 1);
        }
        return null;
    }

    // ---------------------------------------------------------------- Heimnetz

    /** Router, Proxmox & Co.: private IP-Adressen und lokale Namen. */
    public static boolean isLocalAddress(String host) {
        if (host == null) return false;
        String h = host.toLowerCase(Locale.ROOT);
        if (h.startsWith("[")) h = h.substring(1, h.length() - 1);
        if (h.equals("localhost") || h.equals("::1") || h.startsWith("fe80:") || h.startsWith("fd")
                && h.contains(":")) return true;
        if (!h.isEmpty() && !h.contains(".") && !h.contains(":")) return true;   // "proxmox", "router"
        if (h.endsWith(".local") || h.endsWith(".lan") || h.endsWith(".home.arpa") || h.equals("fritz.box")
                || h.endsWith(".fritz.box") || h.endsWith(".localdomain") || h.endsWith(".internal")) return true;
        String[] p = h.split("\\.");
        if (p.length != 4) return false;
        int[] n = new int[4];
        try {
            for (int i = 0; i < 4; i++) { n[i] = Integer.parseInt(p[i]); if (n[i] < 0 || n[i] > 255) return false; }
        } catch (NumberFormatException e) {
            return false;
        }
        return n[0] == 10 || n[0] == 127 || (n[0] == 192 && n[1] == 168)
            || (n[0] == 172 && n[1] >= 16 && n[1] <= 31) || (n[0] == 169 && n[1] == 254)
            || (n[0] == 100 && n[1] >= 64 && n[1] <= 127);   // Tailscale/CGNAT-VPN
    }

    // ---------------------------------------------------------------- SafeSearch

    private enum Search { GOOGLE, BING, DDG }

    private static Search searchEngineOf(String host) {
        if (host.equals("duckduckgo.com") || host.endsWith(".duckduckgo.com")) return Search.DDG;
        if (host.equals("bing.com") || host.endsWith(".bing.com")) return Search.BING;
        // google.com, www.google.ch, google.co.uk, images.google.de (nicht mail./maps. usw.)
        String h = host;
        if (h.startsWith("www.")) h = h.substring(4);
        if (h.startsWith("images.")) h = h.substring(7);
        if (h.startsWith("google.") && h.substring(7).matches("[a-z]{2,3}(\\.[a-z]{2})?")) return Search.GOOGLE;
        return null;
    }

    /** Gibt eine korrigierte Adresse zurück, falls SafeSearch fehlt, sonst null. */
    private static String enforceSafeSearch(URI uri, String host, String url) {
        Search s = searchEngineOf(host);
        if (s == null) return null;
        String path = uri.getRawPath() == null ? "" : uri.getRawPath();
        String query = uri.getRawQuery();
        switch (s) {
            case GOOGLE:
                if (path.startsWith("/search") || path.startsWith("/images") || path.startsWith("/webhp")
                        || path.equals("/") || path.isEmpty() || path.startsWith("/imghp")) {
                    return withParam(url, query, "safe", "active");
                }
                return null;
            case BING:
                if (path.startsWith("/search") || path.startsWith("/images") || path.startsWith("/videos")
                        || path.equals("/") || path.isEmpty()) {
                    return withParam(url, query, "adlt", "strict");
                }
                return null;
            case DDG:
                if (path.equals("/") || path.isEmpty() || path.startsWith("/html") || path.startsWith("/lite")) {
                    return withParam(url, query, "kp", "1");
                }
                return null;
        }
        return null;
    }

    /** Setzt name=value in der Adresse. Gibt null zurück, wenn schon korrekt gesetzt. */
    static String withParam(String url, String rawQuery, String name, String value) {
        if (rawQuery != null) {
            for (String p : rawQuery.split("&")) {
                int eq = p.indexOf('=');
                String n = eq < 0 ? p : p.substring(0, eq);
                String v = eq < 0 ? "" : p.substring(eq + 1);
                if (n.equals(name) && v.equals(value)) {
                    // Korrekt gesetzt – aber nur, wenn kein zweiter, abweichender Wert folgt
                    return countParam(rawQuery, name) == 1 ? null : rebuild(url, rawQuery, name, value);
                }
            }
        }
        return rebuild(url, rawQuery, name, value);
    }

    private static int countParam(String rawQuery, String name) {
        int c = 0;
        for (String p : rawQuery.split("&")) {
            int eq = p.indexOf('=');
            if ((eq < 0 ? p : p.substring(0, eq)).equals(name)) c++;
        }
        return c;
    }

    private static String rebuild(String url, String rawQuery, String name, String value) {
        String fragment = "";
        int hash = url.indexOf('#');
        String base = url;
        if (hash >= 0) { fragment = url.substring(hash); base = url.substring(0, hash); }
        int q = base.indexOf('?');
        String head = q < 0 ? base : base.substring(0, q);
        StringBuilder nq = new StringBuilder();
        if (rawQuery != null && !rawQuery.isEmpty()) {
            for (String p : rawQuery.split("&")) {
                if (p.isEmpty()) continue;
                int eq = p.indexOf('=');
                if ((eq < 0 ? p : p.substring(0, eq)).equals(name)) continue;
                if (nq.length() > 0) nq.append('&');
                nq.append(p);
            }
        }
        if (nq.length() > 0) nq.append('&');
        nq.append(name).append('=').append(value);
        return head + "?" + nq + fragment;
    }

    // ---------------------------------------------------------------- Hilfsfunktionen

    private static List<String> lines(String s) {
        List<String> out = new ArrayList<>();
        if (s == null) return out;
        for (String l : s.split("[\\r\\n,;]+")) {
            String t = l.trim();
            if (!t.isEmpty() && !t.startsWith("#")) out.add(t);
        }
        return out;
    }

    /** "https://www.Example.com/abc" -> "example.com" */
    static String cleanDomain(String d) {
        String t = d.trim().toLowerCase(Locale.ROOT);
        int i = t.indexOf("://");
        if (i >= 0) t = t.substring(i + 3);
        int slash = t.indexOf('/');
        if (slash >= 0) t = t.substring(0, slash);
        int colon = t.lastIndexOf(':');
        if (colon >= 0 && t.indexOf(']') < colon) t = t.substring(0, colon);
        while (t.startsWith("*.") || t.startsWith(".")) t = t.substring(t.indexOf('.') + 1);
        if (t.startsWith("www.")) t = t.substring(4);
        if (t.startsWith("m.") && t.length() > 4) t = t.substring(2);
        while (t.endsWith(".")) t = t.substring(0, t.length() - 1);
        return t;
    }

    /** URI ist strenger als Browser-Adressen; ungültige Zeichen werden kodiert. */
    private static String fixForUri(String url) {
        StringBuilder b = new StringBuilder(url.length());
        for (int i = 0; i < url.length(); i++) {
            char c = url.charAt(i);
            if (c == ' ') b.append("%20");
            else if (c == '%' && (i + 2 >= url.length() || !isHex(url.charAt(i + 1)) || !isHex(url.charAt(i + 2)))) b.append("%25");
            else if ("\"<>\\^`{|}".indexOf(c) >= 0 || c > 126) {
                byte[] bytes = String.valueOf(c).getBytes(StandardCharsets.UTF_8);
                if (Character.isHighSurrogate(c) && i + 1 < url.length()) {
                    bytes = url.substring(i, i + 2).getBytes(StandardCharsets.UTF_8);
                    i++;
                }
                for (byte x : bytes) b.append('%').append(String.format("%02X", x & 0xff));
            } else b.append(c);
        }
        return b.toString();
    }

    private static boolean isHex(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    public int domainCount() { return domains.size(); }
    public int keywordCount() { return matcher.size(); }
}
