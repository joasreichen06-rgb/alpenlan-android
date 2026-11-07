package ch.alpenlan.kalender.browser;

import android.app.Activity;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import static ch.alpenlan.kalender.browser.BrowserActivity.BG;
import static ch.alpenlan.kalender.browser.BrowserActivity.BLUE;
import static ch.alpenlan.kalender.browser.BrowserActivity.CARD;
import static ch.alpenlan.kalender.browser.BrowserActivity.FG;
import static ch.alpenlan.kalender.browser.BrowserActivity.LINE;
import static ch.alpenlan.kalender.browser.BrowserActivity.MUTED;
import static ch.alpenlan.kalender.browser.BrowserActivity.round;

/**
 * Filter-Einstellungen hinter einem PIN.
 * Beim ersten Öffnen legt dein Kollege den PIN fest. Danach braucht jede Änderung diesen PIN.
 * Sobald man die Einstellungen verlässt, sind sie wieder gesperrt.
 */
public class BrowserSettingsActivity extends Activity {

    private static final int MIN_PIN = 6;
    private static final int MUTED_DIM = android.graphics.Color.rgb(84, 96, 114);

    private Store store;
    private LinearLayout page;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        // Keine Bildschirmfotos / keine Vorschau in der App-Übersicht (PIN-Eingabe)
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        store = new Store(this);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        scroll.setFillViewport(true);
        scroll.setOnApplyWindowInsetsListener((v, insets) -> {
            Insets i = BrowserActivity.systemInsets(insets);
            v.setPadding(i.left, i.top, i.right, i.bottom);
            return BrowserActivity.consumed(insets);
        });
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(20), dp(20), dp(20), dp(32));
        scroll.addView(page, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);

        if (!store.hasPin()) showSetPin(false); else showUnlock();
    }

    /** Verlassen = wieder gesperrt. */
    @Override
    protected void onStop() {
        super.onStop();
        if (!isChangingConfigurations()) finish();
    }

    // ================================================================ Bildschirme

    private void header(String title, String sub) {
        page.removeAllViews();
        TextView back = new TextView(this);
        back.setText("‹  Zurück zum Browser");
        back.setTextColor(BLUE);
        back.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        back.setPadding(0, dp(4), 0, dp(20));
        back.setOnClickListener(v -> finish());
        page.addView(back);

        TextView brand = new TextView(this);
        brand.setText("ALPENLAN BROWSER");
        brand.setTextColor(BLUE);
        brand.setLetterSpacing(0.18f);
        brand.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        brand.setTypeface(Typeface.DEFAULT_BOLD);
        page.addView(brand);

        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(FG);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        t.setPadding(0, dp(4), 0, dp(6));
        page.addView(t);

        if (sub != null) {
            TextView s = text(sub, MUTED, 15);
            s.setPadding(0, 0, 0, dp(18));
            page.addView(s);
        }
    }

    private void showSetPin(boolean change) {
        header(change ? "PIN ändern" : "PIN festlegen",
            change ? "Neuen PIN zweimal eingeben."
                   : "Gib das Handy jetzt deinem Kollegen. Er wählt einen PIN mit mindestens "
                     + MIN_PIN + " Ziffern. Ohne diesen PIN lassen sich die Filter nicht mehr ändern.");
        EditText a = pinField("Neuer PIN");
        EditText b = pinField("PIN wiederholen");
        page.addView(a);
        page.addView(space(10));
        page.addView(b);
        page.addView(space(18));
        Button save = button("PIN speichern", true);
        save.setOnClickListener(v -> {
            String p1 = a.getText().toString(), p2 = b.getText().toString();
            if (p1.length() < MIN_PIN) { toast("Mindestens " + MIN_PIN + " Ziffern"); return; }
            if (!p1.equals(p2)) { toast("Die beiden PINs stimmen nicht überein"); b.setText(""); return; }
            if (isWeak(p1)) { toast("Zu einfach (z. B. 123456 oder 111111). Bitte anderen PIN wählen."); return; }
            store.setPin(p1);
            toast("PIN gespeichert");
            showSettings();
        });
        page.addView(save);
    }

    private void showUnlock() {
        header("Gesperrt", "Die Filter-Einstellungen sind mit einem PIN geschützt.");
        EditText pin = pinField("PIN");
        page.addView(pin);
        page.addView(space(18));
        Button open = button("Entsperren", true);
        TextView info = text("", MUTED, 14);
        info.setPadding(0, dp(14), 0, 0);
        Runnable refresh = new Runnable() {
            @Override public void run() {
                long wait = store.lockSecondsLeft();
                open.setEnabled(wait == 0);
                open.setAlpha(wait == 0 ? 1f : 0.4f);
                info.setText(wait == 0 ? "" : "Zu viele Fehlversuche. Warte noch " + wait + " Sekunden.");
                if (wait > 0) info.postDelayed(this, 1000);
            }
        };
        open.setOnClickListener(v -> {
            String p = pin.getText().toString();
            pin.setText("");
            if (store.checkPin(p)) {
                showSettings();
            } else {
                toast("Falscher PIN");
                refresh.run();
            }
        });
        page.addView(open);
        page.addView(info);
        refresh.run();
    }

    private void showSettings() {
        header("Filter", "Diese Regeln gelten für jede Seite, jedes Bild und jede Suche im Browser – "
            + "egal ob WLAN, Mobilfunk oder VPN.");

        // --- Suchmaschine
        page.addView(label("Suchmaschine (SafeSearch immer an)"));
        RadioGroup engines = new RadioGroup(this);
        engines.setPadding(dp(12), dp(4), dp(12), dp(4));
        engines.setBackground(round(CARD, dp(14), LINE));
        Filter.Engine current = store.engine();
        for (Filter.Engine e : Filter.Engine.values()) {
            RadioButton r = new RadioButton(this);
            r.setText(e.label);
            r.setTextColor(FG);
            r.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            r.setId(View.generateViewId());
            r.setTag(e);
            r.setPadding(dp(6), dp(10), 0, dp(10));
            engines.addView(r);
            if (e == current) r.setChecked(true);
        }
        page.addView(engines);
        page.addView(space(18));

        // --- Titel prüfen
        Switch titles = new Switch(this);
        titles.setText("Auch Seitentitel auf gesperrte Wörter prüfen");
        titles.setTextColor(FG);
        titles.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        titles.setChecked(store.checkTitles());
        titles.setPadding(dp(14), dp(12), dp(14), dp(12));
        titles.setBackground(round(CARD, dp(14), LINE));
        page.addView(titles);
        page.addView(space(10));

        Switch media = new Switch(this);
        media.setText("Bilder- und Videosuche sperren (Google, Bing, DuckDuckGo)");
        media.setTextColor(FG);
        media.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        media.setChecked(store.blockMediaSearch());
        media.setPadding(dp(14), dp(12), dp(14), dp(12));
        media.setBackground(round(CARD, dp(14), LINE));
        page.addView(media);
        page.addView(space(18));

        // --- Listen
        page.addView(label("Zusätzlich gesperrte Seiten"));
        EditText domains = area(store.extraDomains(), "eine pro Zeile, z. B.\nnetflix.com\n20min.ch");
        page.addView(domains);
        page.addView(space(18));

        page.addView(label("Zusätzlich gesperrte Wörter"));
        EditText words = area(store.extraKeywords(), "eins pro Zeile, z. B.\nbadeanzug\nmodel");
        page.addView(words);
        page.addView(space(18));

        page.addView(label("Ausnahmen für Wörter"));
        TextView allowInfo = text("Auf diesen Seiten werden Wörter nicht geprüft (z. B. ein Online-Shop, "
            + "der fälschlich gesperrt wird). Gesperrte Seiten und Suchmaschinen bleiben trotzdem gesperrt.", MUTED, 13);
        allowInfo.setPadding(0, 0, 0, dp(8));
        page.addView(allowInfo);
        EditText allow = area(store.allowList(), "z. B.\ndigitec.ch");
        page.addView(allow);
        page.addView(space(18));

        // --- Fest eingebaut
        TextView coreInfo = text("Fest eingebaut und nicht abschaltbar: " + Filter.CORE_DOMAINS.size()
            + " Seiten (YouTube, Instagram, TikTok, Facebook, X, Reddit, Snapchat und weitere Social Media, Erwachsenen-Seiten, Umgehungs-Proxys, Suchmaschinen ohne SafeSearch) und "
            + Filter.CORE_KEYWORDS.size() + " Wörter.", MUTED, 13);
        page.addView(coreInfo);
        TextView showCore = text("Liste anzeigen", BLUE, 14);
        showCore.setPadding(0, dp(8), 0, 0);
        TextView coreList = text("Seiten:\n" + TextUtils.join(", ", Filter.CORE_DOMAINS)
            + "\n\nWörter:\n" + TextUtils.join(", ", Filter.CORE_KEYWORDS), MUTED, 12);
        coreList.setVisibility(View.GONE);
        coreList.setPadding(0, dp(8), 0, 0);
        showCore.setOnClickListener(v -> {
            boolean show = coreList.getVisibility() != View.VISIBLE;
            coreList.setVisibility(show ? View.VISIBLE : View.GONE);
            showCore.setText(show ? "Liste ausblenden" : "Liste anzeigen");
        });
        page.addView(showCore);
        page.addView(coreList);
        page.addView(space(24));

        Button save = button("Speichern", true);
        save.setOnClickListener(v -> {
            Filter.Engine e = Filter.Engine.GOOGLE;
            View checked = engines.findViewById(engines.getCheckedRadioButtonId());
            if (checked != null && checked.getTag() instanceof Filter.Engine) e = (Filter.Engine) checked.getTag();
            store.saveFilter(domains.getText().toString(), words.getText().toString(),
                allow.getText().toString(), e, titles.isChecked(), media.isChecked());
            Filter f = store.buildFilter();
            toast("Gespeichert: " + f.domainCount() + " Seiten, " + f.keywordCount() + " Wörter gesperrt");
            finish();
        });
        page.addView(save);
        page.addView(space(10));

        Button changePin = button("PIN ändern", false);
        changePin.setOnClickListener(v -> showSetPin(true));
        page.addView(changePin);
        page.addView(space(10));

        Button lockNow = button("Sperren", false);
        lockNow.setOnClickListener(v -> finish());
        page.addView(lockNow);
    }

    // ================================================================ Bausteine

    private static boolean isWeak(String p) {
        boolean same = true, up = true, down = true;
        for (int i = 1; i < p.length(); i++) {
            if (p.charAt(i) != p.charAt(0)) same = false;
            if (p.charAt(i) != p.charAt(i - 1) + 1) up = false;
            if (p.charAt(i) != p.charAt(i - 1) - 1) down = false;
        }
        return same || up || down;
    }

    private EditText pinField(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(MUTED);
        e.setTextColor(FG);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        e.setLetterSpacing(0.3f);
        e.setGravity(Gravity.CENTER);
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        e.setBackground(round(CARD, dp(14), LINE));
        e.setPadding(dp(14), dp(14), dp(14), dp(14));
        e.setSingleLine(true);
        return e;
    }

    private EditText area(String value, String hint) {
        EditText e = new EditText(this);
        e.setText(value);
        e.setHint(hint);
        e.setHintTextColor(MUTED_DIM);
        e.setTextColor(FG);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
            | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        e.setMinLines(3);
        e.setGravity(Gravity.TOP | Gravity.START);
        e.setBackground(round(CARD, dp(14), LINE));
        e.setPadding(dp(14), dp(12), dp(14), dp(12));
        return e;
    }

    private TextView label(String s) {
        TextView t = text(s.toUpperCase(), BLUE, 12);
        t.setLetterSpacing(0.1f);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, 0, 0, dp(8));
        return t;
    }

    private TextView text(String s, int color, int sp) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setLineSpacing(0, 1.2f);
        return t;
    }

    private Button button(String s, boolean primary) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(primary ? android.graphics.Color.rgb(4, 16, 31) : FG);
        b.setBackground(round(primary ? BLUE : CARD, dp(14), primary ? 0 : LINE));
        b.setStateListAnimator(null);
        b.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
        return b;
    }

    private View space(int dpHeight) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(dpHeight)));
        return v;
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
