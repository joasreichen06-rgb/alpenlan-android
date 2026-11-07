package ch.alpenlan.kalender.browser;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.app.SearchManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.RouteInfo;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
import android.webkit.ServiceWorkerClient;
import android.webkit.SslErrorHandler;
import android.webkit.ServiceWorkerController;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import ch.alpenlan.kalender.BuildConfig;

public class BrowserActivity extends Activity {

    // Alpenlan-Farben
    static final int BG = Color.rgb(7, 11, 18);        // #070b12
    static final int CARD = Color.rgb(16, 26, 42);     // #101a2a
    static final int LINE = Color.rgb(28, 42, 64);     // #1c2a40
    static final int FG = Color.rgb(238, 242, 247);    // #eef2f7
    static final int MUTED = Color.rgb(138, 150, 168); // #8a96a8
    static final int BLUE = Color.rgb(77, 163, 255);   // #4da3ff

    private static final String START = "alpenlan://start";
    private static final String BLOCKED = "alpenlan://blocked";
    private static final int REQ_FILE = 7;
    private static final Pattern SCHEME = Pattern.compile(
        "^([a-zA-Z][a-zA-Z0-9+.-]*://|(mailto|tel|sms|smsto|intent|javascript|data|about|file|market|blob):).*",
        Pattern.CASE_INSENSITIVE);
    /** Einzelne Namen mit Port, z. B. "proxmox:8006", oder lokale Namen wie "router.local". */
    private static final Pattern LOCALNAME = Pattern.compile("^[a-zA-Z0-9-]+(:\\d+)([/?#].*)?$");
    private static final Pattern HOSTLIKE = Pattern.compile(
        "^(localhost|\\d{1,3}(\\.\\d{1,3}){3}|[^\\s/]+\\.[a-zA-Z]{2,63}|[^\\s/]+\\.[a-zA-Z]{2,63}\\.?)(:\\d+)?([/?#].*)?$");

    private Store store;
    private volatile Filter filter;
    private Filter.Engine engine;
    private boolean checkTitles;
    private boolean scanPages;

    private LinearLayout root;
    private WebView web;
    private EditText bar;
    private TextView lock;
    private View progress;
    private FrameLayout content;
    private View customView;
    private WebChromeClient.CustomViewCallback customCallback;
    private ValueCallback<Uri[]> fileCallback;

    private String startTemplate, blockedTemplate;
    private String lastRedirect;
    private long lastRedirectAt;
    private long backFromBlockedAt;
    private boolean clearHistoryOnLoad;
    private boolean refreshStartOnResume;
    private volatile String lastInterceptBlocked;
    /** Heimnetz-Geräte, deren eigenes Zertifikat in dieser Sitzung bestätigt wurde. */
    private final Set<String> trustedLocal = new HashSet<>();

    // ================================================================ Aufbau

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        store = new Store(this);
        reloadFilter();
        startTemplate = asset("browser/start.html");
        blockedTemplate = asset("browser/blocked.html");

        buildUi();
        setupWebView();
        setContentView(root);

        if (saved != null && web.restoreState(saved) != null) {
            // Zustand wiederhergestellt
        } else if (!handleIntent(getIntent())) {
            showStart();
        }
    }

    private void buildUi() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            if (customView != null) {
                v.setPadding(0, 0, 0, 0);
            } else {
                Insets i = systemInsets(insets);
                v.setPadding(i.left, i.top, i.right, i.bottom);
            }
            return consumed(insets);
        });

        // --- Adressleiste
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(6), dp(6), dp(6), dp(6));

        TextView home = iconButton("⌂", 22);
        home.setContentDescription("Startseite");
        home.setOnClickListener(v -> showStart());
        top.addView(home);

        LinearLayout field = new LinearLayout(this);
        field.setOrientation(LinearLayout.HORIZONTAL);
        field.setGravity(Gravity.CENTER_VERTICAL);
        field.setBackground(round(CARD, dp(22), LINE));
        field.setPadding(dp(12), 0, dp(6), 0);
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(0, dp(44), 1);
        flp.setMargins(dp(4), 0, dp(4), 0);
        top.addView(field, flp);

        lock = new TextView(this);
        lock.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        lock.setTextColor(BLUE);
        lock.setPadding(0, 0, dp(6), 0);
        field.addView(lock);

        bar = new EditText(this);
        bar.setBackground(null);
        bar.setSingleLine(true);
        bar.setTextColor(FG);
        bar.setHintTextColor(MUTED);
        bar.setHint("Sicher suchen oder Adresse eingeben");
        bar.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        bar.setPadding(0, 0, 0, 0);
        bar.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI
            | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        bar.setImeOptions(EditorInfo.IME_ACTION_GO | EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        bar.setSelectAllOnFocus(true);
        bar.setOnEditorActionListener((v, actionId, ev) -> {
            boolean go = actionId == EditorInfo.IME_ACTION_GO
                || (ev != null && ev.getKeyCode() == KeyEvent.KEYCODE_ENTER && ev.getAction() == KeyEvent.ACTION_DOWN);
            if (go) {
                String text = bar.getText().toString();
                hideKeyboard();
                navigate(text);
                return true;
            }
            return false;
        });
        bar.setOnFocusChangeListener((v, focus) -> { if (!focus) showUrl(web.getUrl()); });
        field.addView(bar, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));

        TextView menu = iconButton("⋮", 22);
        menu.setContentDescription("Menü");
        menu.setOnClickListener(this::showMenu);
        top.addView(menu);

        root.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));

        // --- Fortschrittslinie
        progress = new View(this);
        progress.setBackgroundColor(BLUE);
        progress.setPivotX(0);
        progress.setScaleX(0);
        root.addView(progress, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(2)));

        // --- Inhalt
        content = new FrameLayout(this);
        web = new WebView(this);
        web.setBackgroundColor(BG);
        content.addView(web, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
    }

    private void setupWebView() {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setSupportMultipleWindows(false);           // target=_blank öffnet im selben Fenster
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setSafeBrowsingEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setGeolocationEnabled(false);
        // Als normaler Chrome auftreten, damit Seiten nicht die eingeschränkte App-Ansicht liefern
        s.setUserAgentString(s.getUserAgentString().replace("; wv)", ")").replaceAll("Version/\\d+\\.\\d+ ", ""));

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, false);

        web.setWebViewClient(new Client());
        web.setWebChromeClient(new Chrome());
        web.setDownloadListener(this::download);

        // Auch Service Worker (Hintergrund-Skripte von Webseiten) laufen durch den Filter
        ServiceWorkerController.getInstance().setServiceWorkerClient(new ServiceWorkerClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebResourceRequest request) {
                Filter.Decision d = filter.check(request.getUrl().toString(), false);
                return d.blocked() ? emptyBlocked() : null;
            }
        });
        ServiceWorkerController.getInstance().getServiceWorkerWebSettings().setAllowFileAccess(false);
        ServiceWorkerController.getInstance().getServiceWorkerWebSettings().setAllowContentAccess(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        reloadFilter();
        web.onResume();
        if (refreshStartOnResume) {
            // Zurück aus den Einstellungen: Startseite mit neuen Zahlen, offene Seite neu prüfen
            refreshStartOnResume = false;
            if (START.equals(web.getUrl())) showStart();
            else recheckCurrent();
        }
    }

    @Override
    protected void onPause() {
        web.onPause();
        CookieManager.getInstance().flush();
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    protected void onDestroy() {
        if (web != null) { web.stopLoading(); web.destroy(); }
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    /** Links aus anderen Apps und Suchanfragen. */
    private boolean handleIntent(Intent i) {
        if (i == null) return false;
        String action = i.getAction();
        if (Intent.ACTION_VIEW.equals(action) && i.getData() != null) {
            navigate(i.getData().toString());
            return true;
        }
        if (Intent.ACTION_WEB_SEARCH.equals(action) || Intent.ACTION_SEARCH.equals(action)) {
            String q = i.getStringExtra(SearchManager.QUERY);
            if (!TextUtils.isEmpty(q)) { navigate(q); return true; }
        }
        if (Intent.ACTION_SEND.equals(action)) {
            String t = i.getStringExtra(Intent.EXTRA_TEXT);
            if (!TextUtils.isEmpty(t)) { navigate(t.trim()); return true; }
        }
        return false;
    }

    private void reloadFilter() {
        filter = store.buildFilter();
        engine = store.engine();
        checkTitles = store.checkTitles();
        scanPages = store.scanPages();
    }

    /** Nach Änderungen in den Einstellungen die offene Seite nochmals prüfen. */
    private void recheckCurrent() {
        String url = web.getUrl();
        if (url == null || url.startsWith("alpenlan:")) return;
        Filter.Decision d = filter.check(url, true);
        if (d.blocked()) showBlocked(url, d.reason);
        else if (d.kind == Filter.Decision.Kind.REDIRECT) web.loadUrl(d.url);
    }

    // ================================================================ Navigation

    /** Eingabe aus der Adressleiste, einem Link oder einer anderen App. */
    void navigate(String input) {
        if (input == null) return;
        String text = input.trim();
        if (text.isEmpty()) return;
        String url;
        if (SCHEME.matcher(text).matches() && !text.contains(" ")) {
            url = text;
        } else if (HOSTLIKE.matcher(text).matches() || LOCALNAME.matcher(text).matches()) {
            url = (isLocalInput(text) ? localScheme(text) : "https://") + text;
        } else {
            url = engine.searchUrl(text);
        }
        open(url);
    }

    private void open(String url) {
        if (url.startsWith("alpenlan:")) { internal(url); return; }
        url = upgradeHttp(url);
        if (openExternal(url)) return;
        Filter.Decision d = filter.check(url, true);
        if (d.blocked()) showBlocked(url, d.reason);
        else web.loadUrl(d.kind == Filter.Decision.Kind.REDIRECT ? d.url : url);
    }

    /** Interne Adressen der Startseite. */
    private void internal(String url) {
        Uri u = Uri.parse(url);
        String what = u.getHost() == null ? "" : u.getHost();
        switch (what) {
            case "search": {
                String q = u.getQueryParameter("q");
                if (!TextUtils.isEmpty(q)) navigate(q);
                break;
            }
            case "go": {
                String to = u.getQueryParameter("url");
                if (!TextUtils.isEmpty(to)) open(to);
                break;
            }
            case "settings":
                openSettings();
                break;
            case "back":
                if (BLOCKED.equals(web.getUrl())) backFromBlockedAt = SystemClock.elapsedRealtime();
                if (web.canGoBack()) web.goBack(); else showStart();
                break;
            default:
                showStart();
        }
    }

    /** Telefon, E-Mail und SMS dürfen in die passenden Apps. Alles andere wird nicht geöffnet. */
    private boolean openExternal(String url) {
        String l = url.toLowerCase(Locale.ROOT);
        if (l.startsWith("http://") || l.startsWith("https://") || l.startsWith("alpenlan:")) return false;
        if (l.startsWith("tel:") || l.startsWith("mailto:") || l.startsWith("sms:") || l.startsWith("smsto:")) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } catch (ActivityNotFoundException e) {
                toast("Keine App dafür vorhanden");
            }
        }
        // intent://, market://, file:// usw. bleiben gesperrt
        return true;
    }

    private void showStart() {
        Filter f = filter;
        String html = startTemplate
            .replace("{{ENGINE}}", engine.label)
            .replace("{{DOMAINS}}", String.valueOf(f.domainCount()))
            .replace("{{WORDS}}", String.valueOf(f.keywordCount()))
            .replace("{{VERSION}}", BuildConfig.VERSION_NAME)
            .replace("{{NET}}", networkHtml());
        web.loadDataWithBaseURL(START, html, "text/html", "UTF-8", START);
        showUrl(START);
    }

    private void showBlocked(String url, String reason) {
        long now = SystemClock.elapsedRealtime();
        if (now - backFromBlockedAt < 2500) {
            // Man ist von einer Sperrseite zurück auf die gesperrte Seite gekommen:
            // gleich weiter zurück, statt endlos zwischen beiden zu pendeln.
            backFromBlockedAt = now;
            web.stopLoading();
            if (web.canGoBack()) {
                web.goBack();
            } else {
                clearHistoryOnLoad = true;
                showStart();
            }
            return;
        }
        web.stopLoading();
        web.loadDataWithBaseURL(BLOCKED, blockedHtml(url, reason), "text/html", "UTF-8", BLOCKED);
        showUrl(BLOCKED);
    }

    private String blockedHtml(String url, String reason) {
        String host = "";
        try { host = Uri.parse(url).getHost(); } catch (Exception ignored) { }
        if (host == null) host = "";
        // Bei gesperrten Wörtern nicht die ganze Adresse (mit dem Suchbegriff) zeigen
        return blockedTemplate
            .replace("{{REASON}}", esc(reason == null ? "Gesperrt" : reason))
            .replace("{{HOST}}", esc(host));
    }

    private void showUrl(String url) {
        if (bar.hasFocus()) return;
        if (url == null || url.startsWith("alpenlan:")) {
            bar.setText("");
            lock.setText(BLOCKED.equals(url) ? "⛔" : "");
            lock.setVisibility(BLOCKED.equals(url) ? View.VISIBLE : View.GONE);
            return;
        }
        boolean https = url.startsWith("https://");
        lock.setText(https ? "🔒" : "⚠");
        lock.setTextColor(https ? BLUE : Color.rgb(255, 196, 77));
        lock.setVisibility(View.VISIBLE);
        String shown = url.replaceFirst("^https?://(www\\.)?", "");
        if (shown.endsWith("/")) shown = shown.substring(0, shown.length() - 1);
        bar.setText(shown);
    }

    // ================================================================ Seiteninhalt

    /** Liest den sichtbaren Text, Bildbeschreibungen und Links der Seite (max. 60'000 Zeichen). */
    private static final String PAGE_TEXT_JS = "(function(){try{"
        + "var t=document.title+'\\n'+(document.body?document.body.innerText:'');"
        + "var a=[];var im=document.images;"
        + "for(var i=0;i<im.length&&i<400;i++){if(im[i].alt)a.push(im[i].alt);if(im[i].title)a.push(im[i].title);}"
        + "var m=document.querySelector('meta[name=description]');if(m)a.push(m.content);"
        + "return (t+'\\n'+a.join('\\n')).slice(0,60000);}catch(e){return '';}})()";

    private void scanPage(WebView view, String url) {
        if (!scanPages || url == null || url.startsWith("alpenlan:")) return;
        if (!url.equals(view.getUrl())) return;
        String host = Uri.parse(url).getHost();
        if (host == null || Filter.isLocalAddress(host) || filter.allowsWordsOn(host)) return;
        view.evaluateJavascript(PAGE_TEXT_JS, value -> {
            if (value == null || !url.equals(view.getUrl())) return;
            String text;
            try {
                Object o = new org.json.JSONTokener(value).nextValue();
                text = o instanceof String ? (String) o : "";
            } catch (Exception e) {
                return;
            }
            String reason = filter.scanPage(text);
            if (reason != null) {
                view.stopLoading();
                showBlocked(url, reason);
            }
        });
    }

    /** Blendet auf Suchseiten alle Bilder und Videos aus (auch eingebettete Vorschaubilder). */
    private static final String HIDE_IMAGES_JS = "(function(){if(document.getElementById('alpenlan-noimg'))return;"
        + "var s=document.createElement('style');s.id='alpenlan-noimg';"
        + "s.textContent='img,picture,video,canvas,svg image,g-img,[role=img]{visibility:hidden!important;}"
        + "[style*=background-image]{background-image:none!important;}';"
        + "(document.head||document.documentElement).appendChild(s);})()";

    private void hideSearchImages(WebView view, String url) {
        if (url == null || !filter.blocksMediaSearch()) return;
        String host = Uri.parse(url).getHost();
        if (host == null || !filter.isSearchEngine(host)) return;
        view.evaluateJavascript(HIDE_IMAGES_JS, null);
    }

    // ================================================================ Netzwerk-Anzeige

    /** Karte mit den aktuellen IP-Einstellungen für die Startseite. */
    private String networkHtml() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            StringBuilder rows = new StringBuilder();
            Network active = cm.getActiveNetwork();
            if (active == null) return "<div class='net'><h2>NETZWERK</h2><div class='row'><span>Status</span><b>Keine Verbindung</b></div></div>";
            boolean vpn = false;
            NetworkCapabilities ac = cm.getNetworkCapabilities(active);
            if (ac != null && ac.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) vpn = true;

            @SuppressWarnings("deprecation")
            Network[] all = cm.getAllNetworks();
            Network lan = null;
            String kind = "Unbekannt";
            for (Network n : all) {
                NetworkCapabilities c = cm.getNetworkCapabilities(n);
                if (c == null || c.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) continue;
                if (!c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) continue;
                if (c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) { lan = n; kind = "WLAN"; break; }
                if (c.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) { lan = n; kind = "Ethernet"; break; }
                if (c.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) && lan == null) { lan = n; kind = "Mobilfunk"; }
            }
            if (lan == null && !vpn) lan = active;

            rows.append(row("Verbindung", esc(kind) + (vpn ? " + VPN" : "")));
            if (lan != null) {
                LinkProperties lp = cm.getLinkProperties(lan);
                if (lp != null) {
                    rows.append(row("Meine IP", esc(ipv4(lp))));
                    String gw = gateway(lp);
                    if (gw != null) {
                        rows.append(row("Router", "<a href='alpenlan://go?url=" + Uri.encode("http://" + gw)
                            + "'>" + esc(gw) + " ›</a>"));
                    }
                    String dns = dns(lp);
                    if (!dns.isEmpty()) rows.append(row("DNS", esc(dns)));
                    if (lp.getPrivateDnsServerName() != null) {
                        rows.append(row("Privates DNS", esc(lp.getPrivateDnsServerName())));
                    }
                }
            }
            if (vpn) {
                LinkProperties vp = cm.getLinkProperties(active);
                rows.append(row("VPN", "<span class='on'>aktiv</span>"));
                if (vp != null) {
                    rows.append(row("VPN-IP", esc(ipv4(vp))));
                    String dns = dns(vp);
                    if (!dns.isEmpty()) rows.append(row("VPN-DNS", esc(dns)));
                }
            } else {
                rows.append(row("VPN", "aus"));
            }
            rows.append("<div class='row'><span>Öffentliche IP</span><b id='pub'><a href='#' onclick='pubIp();return false'>anzeigen</a></b></div>");
            return "<div class='net'><h2>NETZWERK</h2>" + rows + "</div>";
        } catch (Exception e) {
            return "";
        }
    }

    private static String row(String k, String v) {
        return "<div class='row'><span>" + k + "</span><b>" + v + "</b></div>";
    }

    private static String ipv4(LinkProperties lp) {
        StringBuilder b = new StringBuilder();
        for (LinkAddress a : lp.getLinkAddresses()) {
            if (a.getAddress() instanceof Inet4Address) {
                if (b.length() > 0) b.append(", ");
                b.append(a.getAddress().getHostAddress()).append('/').append(a.getPrefixLength());
            }
        }
        if (b.length() == 0) {
            for (LinkAddress a : lp.getLinkAddresses()) {
                if (!a.getAddress().isLinkLocalAddress()) { b.append(a.getAddress().getHostAddress()); break; }
            }
        }
        return b.length() == 0 ? "–" : b.toString();
    }

    private static String gateway(LinkProperties lp) {
        for (RouteInfo r : lp.getRoutes()) {
            InetAddress g = r.getGateway();
            if (r.isDefaultRoute() && g instanceof Inet4Address && !g.isAnyLocalAddress()) return g.getHostAddress();
        }
        return null;
    }

    private static String dns(LinkProperties lp) {
        StringBuilder b = new StringBuilder();
        for (InetAddress d : lp.getDnsServers()) {
            if (b.length() > 0) b.append(", ");
            b.append(d.getHostAddress());
        }
        return b.toString();
    }

    // ================================================================ WebView-Hooks

    private final class Client extends WebViewClient {

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
            String url = req.getUrl().toString();
            if (url.startsWith("alpenlan:")) { internal(url); return true; }
            if (openExternal(url)) return true;
            boolean main = req.isForMainFrame();
            String https = upgradeHttp(url);
            if (main && !https.equals(url)) { open(https); return true; }
            Filter.Decision d = filter.check(url, main);
            if (d.blocked()) {
                if (main) showBlocked(url, d.reason);
                return true;
            }
            if (main && d.kind == Filter.Decision.Kind.REDIRECT) {
                view.loadUrl(d.url);
                return true;
            }
            return false;
        }

        /** Jede einzelne Anfrage (Seite, Bild, Skript, Video, Hintergrundabruf) läuft hier durch. */
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
            String url = req.getUrl().toString();
            if (url.startsWith("alpenlan:")) return null;
            boolean main = req.isForMainFrame();
            Filter.Decision d = filter.check(url, main);
            if (d.blocked()) {
                if (!main) return emptyBlocked();
                lastInterceptBlocked = url;
                return html(blockedHtml(url, d.reason));
            }
            if (main && d.kind == Filter.Decision.Kind.REDIRECT) {
                return html(redirectHtml(d.url));
            }
            return null;
        }

        /** Seiten, die die Adresse per Skript ändern (ohne neu zu laden), werden auch geprüft. */
        @Override
        public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
            if (url == null || url.startsWith("alpenlan:")) return;
            Filter.Decision d = filter.check(url, true);
            if (d.blocked()) {
                // Schon beim Laden durch die Sperrseite ersetzt? Dann nichts weiter tun.
                if (!url.equals(lastInterceptBlocked)) showBlocked(url, d.reason);
            } else if (d.kind == Filter.Decision.Kind.REDIRECT) {
                long now = SystemClock.elapsedRealtime();
                if (!d.url.equals(lastRedirect) || now - lastRedirectAt > 3000) {
                    lastRedirect = d.url;
                    lastRedirectAt = now;
                    view.loadUrl(d.url);
                }
            }
        }

        /** Selbst signierte Zertifikate: nur bei Geräten im Heimnetz, und nur nach Rückfrage. */
        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
            String host = Uri.parse(error.getUrl()).getHost();
            if (host == null || !Filter.isLocalAddress(host)) {
                handler.cancel();
                toast("Unsichere Verbindung abgebrochen");
                return;
            }
            if (trustedLocal.contains(host)) { handler.proceed(); return; }
            new AlertDialog.Builder(BrowserActivity.this)
                .setTitle("Gerät im Heimnetz")
                .setMessage(host + " hat ein eigenes Zertifikat (normal bei Router, Proxmox, NAS). "
                    + "Trotzdem verbinden?")
                .setPositiveButton("Verbinden", (d, w) -> { trustedLocal.add(host); handler.proceed(); })
                .setNegativeButton("Abbrechen", (d, w) -> handler.cancel())
                .setOnCancelListener(d -> handler.cancel())
                .show();
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            showUrl(url);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            showUrl(url);
            if (clearHistoryOnLoad) {
                clearHistoryOnLoad = false;
                view.clearHistory();
            }
            hideSearchImages(view, url);
            scanPage(view, url);
            // Nachgeladene Inhalte (Endlos-Listen, Galerien) später nochmals prüfen
            view.postDelayed(() -> scanPage(view, url), 2500);
            view.postDelayed(() -> scanPage(view, url), 8000);
        }

        @Override
        public void onPageCommitVisible(WebView view, String url) {
            hideSearchImages(view, url);
        }
    }

    private final class Chrome extends WebChromeClient {

        @Override
        public void onProgressChanged(WebView view, int p) {
            if (p >= 15) hideSearchImages(view, view.getUrl());
            progress.animate().cancel();
            if (p >= 100) {
                progress.setScaleX(1f);
                progress.animate().alpha(0f).setDuration(250).start();
            } else {
                progress.setAlpha(1f);
                progress.setScaleX(Math.max(0.05f, p / 100f));
            }
        }

        /** Seitentitel mit gesperrten Wörtern: Seite verlassen. */
        @Override
        public void onReceivedTitle(WebView view, String title) {
            if (!checkTitles) return;
            String url = view.getUrl();
            if (url == null || url.startsWith("alpenlan:")) return;
            String host = Uri.parse(url).getHost();
            if (host != null && filter.allowsWordsOn(host)) return;
            String kw = filter.keywordInText(title);
            if (kw != null) {
                view.stopLoading();
                showBlocked(url, "Gesperrtes Wort im Seitentitel: «" + kw + "»");
            }
        }

        @Override
        public void onShowCustomView(View view, CustomViewCallback callback) {
            if (customView != null) { callback.onCustomViewHidden(); return; }
            customView = view;
            customCallback = callback;
            content.addView(view, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
            web.setVisibility(View.GONE);
            setBarsHidden(true);
        }

        @Override
        public void onHideCustomView() {
            exitFullscreen();
        }

        @Override
        public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> cb, FileChooserParams params) {
            if (fileCallback != null) fileCallback.onReceiveValue(null);
            fileCallback = cb;
            try {
                startActivityForResult(params.createIntent(), REQ_FILE);
            } catch (ActivityNotFoundException e) {
                fileCallback = null;
                toast("Keine Datei-Auswahl verfügbar");
                return false;
            }
            return true;
        }

        @Override
        public void onPermissionRequest(PermissionRequest request) {
            request.deny(); // Kamera/Mikrofon für Webseiten aus
        }

        @Override
        public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
            callback.invoke(origin, false, false);
        }
    }

    private void exitFullscreen() {
        if (customView == null) return;
        content.removeView(customView);
        customView = null;
        web.setVisibility(View.VISIBLE);
        if (customCallback != null) customCallback.onCustomViewHidden();
        customCallback = null;
        setBarsHidden(false);
    }

    private void setBarsHidden(boolean hidden) {
        View top = root.getChildAt(0);
        top.setVisibility(hidden ? View.GONE : View.VISIBLE);
        progress.setVisibility(hidden ? View.GONE : View.VISIBLE);
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                if (hidden) {
                    c.hide(WindowInsets.Type.systemBars());
                    c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                } else {
                    c.show(WindowInsets.Type.systemBars());
                }
            }
        } else {
            legacyFullscreen(hidden);
        }
        root.requestApplyInsets();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_FILE) {
            if (fileCallback != null) {
                fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
                fileCallback = null;
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (customView != null) { exitFullscreen(); return; }
        if (bar.hasFocus()) { hideKeyboard(); return; }
        if (BLOCKED.equals(web.getUrl())) backFromBlockedAt = SystemClock.elapsedRealtime();
        if (web.canGoBack()) { web.goBack(); return; }
        if (!START.equals(web.getUrl())) { showStart(); return; }
        super.onBackPressed();
    }

    // ================================================================ Downloads

    private void download(String url, String userAgent, String disposition, String mime, long length) {
        String name = URLUtil.guessFileName(url, disposition, mime);
        String lname = name.toLowerCase(Locale.ROOT);
        if (lname.endsWith(".apk") || lname.endsWith(".apks") || lname.endsWith(".xapk")
                || "application/vnd.android.package-archive".equals(mime)) {
            toast("App-Dateien (APK) können nicht heruntergeladen werden");
            return;
        }
        Filter.Decision d = filter.check(url, true);
        if (d.blocked() || filter.keywordInText(name) != null) {
            toast("Download gesperrt");
            return;
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            toast("Dieser Download wird nicht unterstützt");
            return;
        }
        try {
            DownloadManager.Request r = new DownloadManager.Request(Uri.parse(url));
            String cookies = CookieManager.getInstance().getCookie(url);
            if (cookies != null) r.addRequestHeader("Cookie", cookies);
            r.addRequestHeader("User-Agent", userAgent);
            r.setMimeType(mime);
            r.setTitle(name);
            r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name);
            ((DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE)).enqueue(r);
            toast("Download gestartet: " + name);
        } catch (Exception e) {
            toast("Download fehlgeschlagen");
        }
    }

    // ================================================================ Menü

    private void showMenu(View anchor) {
        PopupMenu m = new PopupMenu(this, anchor);
        Menu menu = m.getMenu();
        menu.add(0, 1, 0, "Neu laden");
        menu.add(0, 2, 1, "Vorwärts").setEnabled(web.canGoForward());
        menu.add(0, 3, 2, "Startseite");
        menu.add(0, 4, 3, "Link teilen");
        menu.add(0, 5, 4, "Browserdaten löschen");
        menu.add(0, 6, 5, "Filter-Einstellungen 🔒");
        m.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1:
                    if (START.equals(web.getUrl())) showStart(); else web.reload();
                    return true;
                case 2: web.goForward(); return true;
                case 3: showStart(); return true;
                case 4: share(); return true;
                case 5: clearData(); return true;
                case 6: openSettings(); return true;
            }
            return false;
        });
        m.show();
    }

    private void openSettings() {
        refreshStartOnResume = true;
        startActivity(new Intent(this, BrowserSettingsActivity.class));
    }

    private void share() {
        String url = web.getUrl();
        if (url == null || url.startsWith("alpenlan:")) { toast("Nichts zu teilen"); return; }
        Intent i = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url);
        startActivity(Intent.createChooser(i, "Link teilen"));
    }

    private void clearData() {
        web.clearCache(true);
        web.clearHistory();
        web.clearFormData();
        CookieManager.getInstance().removeAllCookies(null);
        CookieManager.getInstance().flush();
        WebStorage.getInstance().deleteAllData();
        clearHistoryOnLoad = true;
        showStart();
        toast("Verlauf, Cookies und Cache gelöscht");
    }

    // ================================================================ Hilfen

    private static boolean isLocalInput(String text) {
        String host = text.split("[/:?#]", 2)[0];
        return Filter.isLocalAddress(host) || !host.contains(".");
    }

    /** Proxmox (8006) und andere typische HTTPS-Ports bekommen https, sonst http. */
    private static String localScheme(String text) {
        java.util.regex.Matcher m = Pattern.compile("^[^/:?#]+:(\\d+)").matcher(text);
        if (m.find()) {
            String port = m.group(1);
            if (port.equals("443") || port.equals("8006") || port.equals("8443") || port.equals("9443")
                    || port.equals("5001") || port.equals("8007")) return "https://";
        }
        return "http://";
    }

    /** Systemleisten + Tastatur (+ Kamera-Aussparung), auch auf Android 10. */
    static Insets systemInsets(WindowInsets insets) {
        if (Build.VERSION.SDK_INT >= 30) {
            return insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime()
                | WindowInsets.Type.displayCutout());
        }
        return legacyInsets(insets);
    }

    @SuppressWarnings("deprecation")
    private static Insets legacyInsets(WindowInsets insets) {
        return insets.getSystemWindowInsets();
    }

    static WindowInsets consumed(WindowInsets insets) {
        if (Build.VERSION.SDK_INT >= 30) return WindowInsets.CONSUMED;
        return legacyConsume(insets);
    }

    @SuppressWarnings("deprecation")
    private static WindowInsets legacyConsume(WindowInsets insets) {
        return insets.consumeSystemWindowInsets();
    }

    @SuppressWarnings("deprecation")
    private void legacyFullscreen(boolean hidden) {
        getWindow().getDecorView().setSystemUiVisibility(hidden
            ? View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
              | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            : View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    /**
     * Die App erlaubt aus Sicherheitsgründen nur HTTPS (network_security_config).
     * Darum werden http://-Adressen automatisch auf https:// umgestellt.
     */
    static String upgradeHttp(String url) {
        if (url == null || !url.regionMatches(true, 0, "http://", 0, 7)) return url;
        String rest = url.substring(7);
        String host = rest.split("[/:?#]", 2)[0].toLowerCase(Locale.ROOT);
        if (Filter.isLocalAddress(host)) return url;   // Router, Proxmox & Co. im Heimnetz
        return "https://" + rest;
    }

    private static final Map<String, String> NO_CACHE = new HashMap<>();
    static { NO_CACHE.put("Cache-Control", "no-store"); }

    private static WebResourceResponse html(String body) {
        return new WebResourceResponse("text/html", "UTF-8", 200, "OK", NO_CACHE,
            new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
    }

    static WebResourceResponse emptyBlocked() {
        return new WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", NO_CACHE,
            new ByteArrayInputStream(new byte[0]));
    }

    private static String redirectHtml(String url) {
        String js = url.replace("\\", "\\\\").replace("'", "\\'").replace("<", "\\x3c").replace("\n", "");
        return "<!doctype html><meta name=viewport content='width=device-width'>"
            + "<body style='background:#070b12'><script>location.replace('" + js + "')</script>";
    }

    static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private String asset(String name) {
        try (InputStream in = getAssets().open(name)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } catch (Exception e) {
            return "<html><body style='background:#070b12;color:#eef2f7'>" + name + "</body></html>";
        }
    }

    private TextView iconButton(String symbol, int sp) {
        TextView b = new TextView(this);
        b.setText(symbol);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        b.setTextColor(FG);
        b.setGravity(Gravity.CENTER);
        TypedValue tv = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, tv, true);
        b.setBackgroundResource(tv.resourceId);
        b.setLayoutParams(new LinearLayout.LayoutParams(dp(44), dp(44)));
        return b;
    }

    static GradientDrawable round(int color, int radius, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        if (stroke != 0) g.setStroke(1, stroke);
        return g;
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(bar.getWindowToken(), 0);
        bar.clearFocus();
        web.requestFocus();
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
