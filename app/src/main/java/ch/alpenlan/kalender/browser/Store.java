package ch.alpenlan.kalender.browser;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import java.security.SecureRandom;
import java.security.spec.KeySpec;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Speichert Einstellungen und den PIN (nur als Hash, nie im Klartext). */
final class Store {
    private static final String FILE = "alpenlan_browser";
    private static final int ITER = 120_000;

    private final SharedPreferences p;

    Store(Context c) { p = c.getSharedPreferences(FILE, Context.MODE_PRIVATE); }

    // ---------------------------------------------------------------- Filter

    String extraDomains()  { return p.getString("domains", Filter.DEFAULT_EXTRA_DOMAINS); }
    String extraKeywords() { return p.getString("keywords", Filter.DEFAULT_EXTRA_KEYWORDS); }
    String allowList()     { return p.getString("allow", ""); }
    Filter.Engine engine() { return Filter.Engine.byName(p.getString("engine", "GOOGLE")); }
    boolean checkTitles()  { return p.getBoolean("titles", true); }
    boolean blockMediaSearch() { return p.getBoolean("media", true); }
    boolean scanPages()    { return p.getBoolean("scan", true); }
    boolean whitelistOnly() { return p.getBoolean("wl_on", false); }
    String whitelist()     { return p.getString("wl", Filter.DEFAULT_WHITELIST); }

    void saveFilter(String domains, String keywords, String allow, Filter.Engine engine, boolean titles,
                    boolean media, boolean scan, boolean whitelistOn, String whitelist) {
        p.edit().putString("domains", domains).putString("keywords", keywords)
            .putString("allow", allow).putString("engine", engine.name())
            .putBoolean("titles", titles).putBoolean("media", media).putBoolean("scan", scan)
            .putBoolean("wl_on", whitelistOn).putString("wl", whitelist).apply();
    }

    Filter buildFilter() {
        return new Filter(extraDomains(), extraKeywords(), allowList(), blockMediaSearch(),
            whitelistOnly(), whitelist());
    }

    // ---------------------------------------------------------------- PIN

    boolean hasPin() { return p.contains("pin_hash"); }

    void setPin(String pin) {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        p.edit().putString("pin_salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString("pin_hash", hash(pin, salt))
            .putInt("pin_fails", 0).putLong("pin_lock_until", 0).apply();
    }

    /** Wartezeit in Sekunden nach zu vielen Fehlversuchen, sonst 0. */
    long lockSecondsLeft() {
        long until = p.getLong("pin_lock_until", 0);
        long now = System.currentTimeMillis();
        // Schutz gegen Zurückdrehen der Uhr: maximal 1 Stunde Sperre
        if (until - now > 3_600_000L) { until = now + 3_600_000L; }
        return until > now ? (until - now + 999) / 1000 : 0;
    }

    boolean checkPin(String pin) {
        if (!hasPin() || lockSecondsLeft() > 0) return false;
        byte[] salt = Base64.decode(p.getString("pin_salt", ""), Base64.NO_WRAP);
        boolean ok = constantEquals(hash(pin, salt), p.getString("pin_hash", ""));
        if (ok) {
            p.edit().putInt("pin_fails", 0).apply();
        } else {
            int fails = p.getInt("pin_fails", 0) + 1;
            long wait = fails < 3 ? 0 : Math.min(3600, 30L << Math.min(fails - 3, 7)); // 30 s, 60 s, ... max 1 h
            p.edit().putInt("pin_fails", fails)
                .putLong("pin_lock_until", wait > 0 ? System.currentTimeMillis() + wait * 1000 : 0).apply();
        }
        return ok;
    }

    private static String hash(String pin, byte[] salt) {
        try {
            KeySpec spec = new PBEKeySpec(pin.toCharArray(), salt, ITER, 256);
            byte[] h = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return Base64.encodeToString(h, Base64.NO_WRAP);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean constantEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int r = 0;
        for (int i = 0; i < a.length(); i++) r |= a.charAt(i) ^ b.charAt(i);
        return r == 0;
    }
}
