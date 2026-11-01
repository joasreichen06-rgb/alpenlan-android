package ch.alpenlan.kalender.light;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Choreographer;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

/**
 * Vollbild-Licht. Live: Zustände vom Server per WebSocket, synchron über abgeglichene Uhr.
 * Test: lokale Demo aller Effekte, ohne Server.
 */
public class LightShowActivity extends Activity implements Choreographer.FrameCallback {
    public static final String EXTRA_TEST = "test", EXTRA_URL = "url", EXTRA_ZONE = "zone", EXTRA_TORCH = "torch";
    private static final int CH = 7;

    // Testmodus: Name, Dimmer, R, G, B, Strobe, Taschenlampe, Modus (Modus -1 = Regenbogen)
    private static final Object[][] TEST_STEPS = {
            {"Rot", 255, 255, 0, 0, 0, 0, 0},
            {"Grün", 255, 0, 255, 0, 0, 0, 0},
            {"Blau", 255, 0, 0, 255, 0, 0, 0},
            {"Weiss, volle Helligkeit", 255, 255, 255, 255, 0, 0, 0},
            {"Dimmer 25 %", 64, 255, 255, 255, 0, 0, 0},
            {"Strobe ca. 8 Hz", 255, 255, 255, 255, 80, 0, 0},
            {"Funkeln Gold", 255, 255, 190, 60, 0, 0, 64},
            {"Pulsieren Violett", 255, 140, 60, 255, 0, 0, 128},
            {"Gruppen-Blink Cyan", 255, 0, 200, 255, 0, 0, 192},
            {"Taschenlampe an", 255, 255, 255, 255, 0, 255, 0},
            {"Taschenlampe + Strobe", 255, 255, 255, 255, 40, 255, 0},
            {"Regenbogen", 255, 0, 0, 0, 0, 0, -1},
    };
    private static final long TEST_STEP_MS = 4000;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ConcurrentLinkedQueue<State> queue = new ConcurrentLinkedQueue<>();
    private final Random rnd = new Random();

    private View light;
    private TextView hud;
    private Torch torch;
    private boolean testMode, torchEnabled, running, hudVisible;
    private int fixedZone, zone = 1, zones = 8, lastColor = -1;
    private int seed;
    private String wsUrl;
    private int[] current = new int[8 * CH];

    // Uhrenabgleich
    private volatile double offset = 0, rtt = 0;
    private double bestRtt = Double.MAX_VALUE;

    // Verbindung
    private OkHttpClient client;
    private WebSocket ws;
    private volatile boolean connected;
    private int retry = 0;

    // Testmodus
    private int testStep = 0;
    private long testStepStart;
    private long lastTap;

    private static final class State {
        final double at;
        final int[] z;
        State(double at, int[] z) { this.at = at; this.z = z; }
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        testMode = getIntent().getBooleanExtra(EXTRA_TEST, true);
        fixedZone = getIntent().getIntExtra(EXTRA_ZONE, 0);
        torchEnabled = getIntent().getBooleanExtra(EXTRA_TORCH, false);
        wsUrl = toWsUrl(getIntent().getStringExtra(EXTRA_URL));

        SharedPreferences prefs = getSharedPreferences(LightActivity.PREFS, MODE_PRIVATE);
        seed = prefs.getInt("seed", -1);
        if (seed < 0) { seed = new Random().nextInt(Integer.MAX_VALUE); prefs.edit().putInt("seed", seed).apply(); }

        // Bildschirm an, volle Helligkeit
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = 1f;
        getWindow().setAttributes(lp);

        FrameLayout root = new FrameLayout(this);
        light = new View(this);
        light.setBackgroundColor(Color.BLACK);
        root.addView(light, new FrameLayout.LayoutParams(-1, -1));

        hud = new TextView(this);
        hud.setTextColor(Color.WHITE);
        hud.setShadowLayer(6, 0, 0, Color.BLACK);
        hud.setTextSize(14);
        hud.setTypeface(android.graphics.Typeface.MONOSPACE);
        int pad = Math.round(20 * getResources().getDisplayMetrics().density);
        hud.setPadding(pad, pad, pad, pad * 2);
        FrameLayout.LayoutParams hp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        root.addView(hud, hp);
        setContentView(root);

        torch = new Torch(this);
        light.setOnClickListener(v -> onTap());

        pickZone();
        if (testMode) {
            hudVisible = true;
            testStepStart = System.currentTimeMillis();
        } else {
            hud.setVisibility(View.GONE);
            client = new OkHttpClient.Builder().pingInterval(10, TimeUnit.SECONDS).build();
            connect();
            main.postDelayed(periodicSync, 20000);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUi();
        running = true;
        Choreographer.getInstance().postFrameCallback(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        running = false;
        torch.off();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        main.removeCallbacksAndMessages(null);
        if (ws != null) ws.close(1000, "bye");
        if (client != null) client.dispatcher().executorService().shutdown();
        torch.off();
    }

    @SuppressWarnings("deprecation")
    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    private void onTap() {
        long t = System.currentTimeMillis();
        if (testMode) { // nächster Testschritt
            testStep = (testStep + 1) % TEST_STEPS.length;
            testStepStart = t;
        } else if (t - lastTap < 350) { // Doppeltipp: Diagnose
            hudVisible = !hudVisible;
            hud.setVisibility(hudVisible ? View.VISIBLE : View.GONE);
        }
        lastTap = t;
    }

    private void pickZone() {
        zone = fixedZone > 0 ? Math.min(fixedZone, zones) : (seed % zones) + 1;
    }

    // ---------------- Verbindung ----------------

    static String toWsUrl(String in) {
        if (in == null) return "";
        String s = in.trim();
        if (s.isEmpty()) return "";
        if (s.startsWith("https://")) s = "wss://" + s.substring(8);
        else if (s.startsWith("http://")) s = "ws://" + s.substring(7);
        else if (!s.startsWith("ws://") && !s.startsWith("wss://")) s = "ws://" + s;
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        if (!s.endsWith("/ws")) s = s + "/ws";
        return s;
    }

    private void connect() {
        Request req;
        try { req = new Request.Builder().url(wsUrl).build(); }
        catch (IllegalArgumentException e) { showError("Ungültige Server-Adresse:\n" + wsUrl); return; }
        ws = client.newWebSocket(req, new WebSocketListener() {
            @Override public void onOpen(WebSocket w, Response r) {
                connected = true; retry = 0;
                main.post(LightShowActivity.this::syncBurst);
            }
            @Override public void onMessage(WebSocket w, String text) { handle(text); }
            @Override public void onMessage(WebSocket w, ByteString bytes) { handleBinary(bytes.toByteArray()); }
            @Override public void onClosed(WebSocket w, int code, String reason) { reconnect(); }
            @Override public void onFailure(WebSocket w, Throwable t, Response r) { reconnect(); }
        });
    }

    private void reconnect() {
        connected = false;
        if (isFinishing()) return;
        // Backoff mit Zufall, damit nicht alle Handys gleichzeitig neu verbinden
        long delay = (long) (Math.min(15000, 500L << Math.min(retry++, 5)) * (0.5 + rnd.nextDouble()));
        main.postDelayed(this::connect, delay);
    }

    /** Binär: [1][Float64 Ausführungszeit, little endian][je Zone 7 Bytes]. */
    private void handleBinary(byte[] b) {
        if (b.length < 9 || b[0] != 1) return;
        double at = ByteBuffer.wrap(b, 1, 8).order(ByteOrder.LITTLE_ENDIAN).getDouble();
        int[] z = new int[b.length - 9];
        for (int i = 0; i < z.length; i++) z[i] = b[9 + i] & 0xff;
        queue.add(new State(at, z));
        while (queue.size() > 50) queue.poll();
    }

    private void handle(String text) {
        try {
            JSONObject m = new JSONObject(text);
            String t = m.optString("t");
            if ("s".equals(t)) {
                JSONArray a = m.getJSONArray("z");
                int[] z = new int[a.length()];
                for (int i = 0; i < z.length; i++) z[i] = a.getInt(i);
                queue.add(new State(m.getDouble("at"), z));
                while (queue.size() > 50) queue.poll();
            } else if ("P".equals(t)) {
                double now = System.currentTimeMillis();
                double r = now - m.getDouble("c");
                synchronized (this) {
                    if (r < bestRtt) { bestRtt = r; rtt = r; offset = m.getDouble("s") + r / 2 - now; }
                }
            } else if ("hello".equals(t)) {
                zones = m.optInt("zones", 8);
                main.post(() -> {
                    pickZone();
                    send("{\"t\":\"hi\",\"zone\":" + zone + "}");
                });
            }
        } catch (Exception ignored) { }
    }

    private void send(String s) { if (ws != null && connected) ws.send(s); }

    private void syncBurst() {
        synchronized (this) { bestRtt = Double.MAX_VALUE; }
        for (int i = 0; i < 6; i++) {
            main.postDelayed(() -> send("{\"t\":\"p\",\"c\":" + System.currentTimeMillis() + "}"), i * 200L);
        }
    }

    private final Runnable periodicSync = new Runnable() {
        @Override public void run() { syncBurst(); main.postDelayed(this, 20000); }
    };

    private void showError(String msg) {
        hudVisible = true;
        hud.setVisibility(View.VISIBLE);
        hud.setText(msg);
    }

    // ---------------- Rendern ----------------

    private static double hash(int a, long b) { // deterministischer Zufall 0..1
        int h = (int) (a * 374761393L + b * 668265263L);
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0xffffffffL) / 4294967296.0;
    }

    private void loadTestState(long t) {
        if (t - testStepStart > TEST_STEP_MS) { testStep = (testStep + 1) % TEST_STEPS.length; testStepStart = t; }
        Object[] s = TEST_STEPS[testStep];
        int o = (zone - 1) * CH;
        if (current.length < o + CH) current = new int[o + CH];
        for (int c = 0; c < CH; c++) current[o + c] = (Integer) s[c + 1];
        if ((Integer) s[7] == -1) { // Regenbogen
            int col = Color.HSVToColor(new float[]{(t % 4000) / 4000f * 360f, 1f, 1f});
            current[o + 1] = Color.red(col); current[o + 2] = Color.green(col); current[o + 3] = Color.blue(col);
            current[o + 6] = 0;
        }
        hud.setText("TESTMODUS  " + (testStep + 1) + "/" + TEST_STEPS.length + "\n" + s[0]
                + (s[6].equals(255) && !torch.available() ? "\n(keine Taschenlampe gefunden)" : "")
                + "\n\nTippen = nächster Schritt · Zurück = Ende");
    }

    @Override
    public void doFrame(long frameTimeNanos) {
        if (!running) return;
        long local = System.currentTimeMillis();
        double t;

        if (testMode) {
            t = local;
            loadTestState(local);
        } else {
            t = local + offset;
            State head;
            while ((head = queue.peek()) != null && head.at <= t) current = queue.poll().z;
        }

        int o = (zone - 1) * CH;
        int dim = 0, r = 0, g = 0, b = 0, strobe = 0, flash = 0, mode = 0;
        if (current.length >= o + CH) {
            dim = current[o]; r = current[o + 1]; g = current[o + 2]; b = current[o + 3];
            strobe = current[o + 4]; flash = current[o + 5]; mode = current[o + 6];
        }
        double level = dim / 255.0;

        if (strobe > 0) { // synchroner Strobe über die gemeinsame Uhr
            double hz = 1 + (strobe / 255.0) * 19;
            double phase = (t * hz / 1000.0) % 1.0;
            if (phase > 0.35) level = 0;
        }
        if (mode >= 64 && mode < 128) { // Funkeln
            if (hash(seed, (long) Math.floor(t / 120)) > 0.3) level = 0;
        } else if (mode >= 128 && mode < 192) { // Pulsieren
            level *= 0.5 + 0.5 * Math.sin(t / 2000.0 * 2 * Math.PI);
        } else if (mode >= 192) { // Zufallsgruppen
            if (((long) Math.floor(t / 250)) % 4 != seed % 4) level = 0;
        }

        int color = Color.rgb((int) Math.round(r * level), (int) Math.round(g * level), (int) Math.round(b * level));
        if (color != lastColor) { light.setBackgroundColor(color); lastColor = color; }
        if (torchEnabled || testMode) torch.set(flash >= 128 && level > 0);

        if (!testMode && hudVisible) {
            hud.setText((connected ? "verbunden" : "verbinde …") + "  ·  Zone " + zone + (fixedZone > 0 ? "" : " (zufällig)")
                    + "\nRTT " + Math.round(rtt) + " ms  ·  Offset " + Math.round(offset) + " ms"
                    + "\nDim " + dim + "  RGB " + r + "," + g + "," + b + "  Str " + strobe + "  Mode " + mode
                    + "\n" + wsUrl);
        }
        Choreographer.getInstance().postFrameCallback(this);
    }
}
