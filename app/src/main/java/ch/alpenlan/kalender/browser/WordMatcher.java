package ch.alpenlan.kalender.browser;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Findet gesperrte Wörter, auch wenn sie verschleiert geschrieben sind.
 *
 * Erkennt unter anderem:
 *  - Leerzeichen, Punkte, Bindestriche dazwischen: «bay watch», «b.a.y.w.a.t.c.h», «bay-watch»
 *  - Zusammensetzungen: «bikinifigur», «baywatchserie»
 *  - Zahlen und Zeichen statt Buchstaben: «b1k1n1», «p0rn», «$exy», «b!kini»
 *  - Akzente und fremde Schriftzeichen: «bíkíní», kyrillisches «а» statt «a»
 *  - Wiederholte Buchstaben: «biiiikini», «baaaywatch», «biikini»
 *  - Tippfehler bei langen Wörtern (ein Buchstabe zu viel, zu wenig, falsch oder vertauscht):
 *    «baywatsch», «lingeire»
 *
 * Reines Java ohne Android, damit es sich leicht testen lässt.
 */
final class WordMatcher {

    /** Harmlose Wörter, die ein gesperrtes Wort enthalten oder ihm ähneln. */
    private static final List<String> HARMLESS = Arrays.asList(
        "nacktschneck", "nacktmull", "nacktsamer", "nacktkatze", "nacktheit der zahlen",
        "thorny", "zecchin", "tanganjika", "tanganyika", "exotisch", "exotic",
        "stripped", "brother", "dessus", "assist", "passwort"
    );

    private static final class Kw {
        final String original;   // wie in der Liste
        final String compact;    // normalisiert, ohne Leerzeichen
        final boolean shortWord; // muss als eigenes Wort vorkommen
        final boolean hasDouble; // enthält doppelte Buchstaben
        Kw(String original, String compact) {
            this.original = original;
            this.compact = compact;
            this.shortWord = compact.length() <= 4 && !compact.equals("porn");
            boolean d = false;
            for (int i = 1; i < compact.length(); i++) if (compact.charAt(i) == compact.charAt(i - 1)) d = true;
            this.hasDouble = d;
        }
    }

    private final List<Kw> words = new ArrayList<>();
    private final List<String> harmless = new ArrayList<>();

    WordMatcher(List<String> keywords) {
        Set<String> seen = new HashSet<>();
        for (String k : keywords) {
            String c = compact(k);
            if (c.length() < 2 || !seen.add(c)) continue;
            words.add(new Kw(k.trim(), c));
        }
        for (String h : HARMLESS) harmless.add(compact(h));
    }

    int size() { return words.size(); }

    // ================================================================ Abfragen

    /** Erstes gefundenes Wort oder null. Für Adressen, Suchanfragen und Titel (inkl. Tippfehler). */
    String find(String text) {
        if (text == null || text.isEmpty()) return null;
        List<String> tokens = tokens(text);
        if (tokens.isEmpty()) return null;
        Kw k = firstExact(tokens, false);
        if (k == null) {
            boolean triple = false;
            for (String t : tokens) if (hasRun(t, 3)) { triple = true; break; }
            if (triple) {
                k = firstExact(collapse(tokens, 3, 1), false);          // «biiiikini»
                if (k == null) k = firstExact(collapse(tokens, 3, 2), false); // «boooobs»
            }
        }
        if (k == null) k = firstExact(collapse(tokens, 2, 1), true);    // «biikini»
        if (k == null) k = firstFuzzy(tokens);                           // «baywatsch»
        return k == null ? null : k.original;
    }

    /** Zählt Treffer pro Wort (für den Seiteninhalt; ohne Tippfehler-Suche). */
    Map<String, Integer> count(String text, Set<String> ignore) {
        Map<String, Integer> hits = new LinkedHashMap<>();
        if (text == null || text.isEmpty()) return hits;
        List<String> tokens = tokens(text);
        Seq base = new Seq(tokens);
        Seq collapsed = new Seq(collapse(tokens, 2, 1));
        for (Kw k : words) {
            if (ignore != null && ignore.contains(k.original)) continue;
            int n = countIn(base, k);
            if (n == 0 && !k.hasDouble) n = countIn(collapsed, k);
            if (n > 0) hits.put(k.original, n);
        }
        return hits;
    }

    // ================================================================ Normalisieren

    /** Kleinbuchstaben, Akzente/Ersatzzeichen vereinheitlicht, alles andere wird Trennzeichen. */
    static String norm(String s) {
        String t = s.toLowerCase(Locale.ROOT)
            .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss");
        t = Normalizer.normalize(t, Normalizer.Form.NFKD).replaceAll("\\p{M}+", "");
        StringBuilder b = new StringBuilder(t.length());
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            char m = map(c);
            b.append(m);
        }
        return b.toString();
    }

    private static char map(char c) {
        switch (c) {
            // Zahlen und Zeichen als Buchstaben
            case '0': return 'o';
            case '1': return 'i';
            case '3': return 'e';
            case '4': return 'a';
            case '5': return 's';
            case '7': return 't';
            case '@': return 'a';
            case '$': return 's';
            case '!': case '¡': return 'i';
            case '€': return 'e';
            // Kyrillische Buchstaben, die wie lateinische aussehen
            case 'а': return 'a'; case 'в': return 'b'; case 'е': return 'e'; case 'ё': return 'e';
            case 'к': return 'k'; case 'м': return 'm'; case 'н': return 'h'; case 'о': return 'o';
            case 'р': return 'p'; case 'с': return 'c'; case 'т': return 't'; case 'у': return 'y';
            case 'х': return 'x'; case 'і': return 'i'; case 'ї': return 'i'; case 'ј': return 'j';
            case 'ѕ': return 's'; case 'ԁ': return 'd'; case 'ɡ': return 'g'; case 'ı': return 'i';
            // Griechische
            case 'α': return 'a'; case 'β': return 'b'; case 'ε': return 'e'; case 'η': return 'n';
            case 'ι': return 'i'; case 'κ': return 'k'; case 'ν': return 'v'; case 'ο': return 'o';
            case 'ρ': return 'p'; case 'τ': return 't'; case 'υ': return 'u'; case 'χ': return 'x';
            case 'ω': return 'w';
            default:
                if (c >= 'a' && c <= 'z') return c;
                if (c >= '0' && c <= '9') return c;
                return ' ';
        }
    }

    static List<String> tokens(String text) {
        List<String> out = new ArrayList<>();
        for (String t : norm(text).split(" +")) if (!t.isEmpty()) out.add(t);
        return out;
    }

    static String compact(String s) {
        return norm(s).replace(" ", "");
    }

    // ================================================================ Varianten

    private static boolean hasRun(String t, int len) {
        int run = 1;
        for (int i = 1; i < t.length(); i++) {
            run = t.charAt(i) == t.charAt(i - 1) ? run + 1 : 1;
            if (run >= len) return true;
        }
        return false;
    }

    /** Folgen von mindestens {@code min} gleichen Buchstaben werden auf {@code to} gekürzt. */
    private static List<String> collapse(List<String> tokens, int min, int to) {
        List<String> out = new ArrayList<>(tokens.size());
        for (String t : tokens) {
            StringBuilder b = new StringBuilder(t.length());
            int i = 0;
            while (i < t.length()) {
                int j = i;
                while (j < t.length() && t.charAt(j) == t.charAt(i)) j++;
                int run = j - i;
                int keep = run >= min ? Math.min(to, run) : run;
                for (int k = 0; k < keep; k++) b.append(t.charAt(i));
                i = j;
            }
            out.add(b.toString());
        }
        return out;
    }

    // ================================================================ Exakte Suche

    /** Text ohne Leerzeichen, mit Wissen, wo die Wörter beginnen und enden. */
    private static final class Seq {
        final String compact;
        final int[] tokenOf;  // Zeichen -> Wortnummer
        final int[] start, end; // Wort -> erstes / letztes Zeichen
        final int[] len;
        Seq(List<String> tokens) {
            StringBuilder b = new StringBuilder();
            start = new int[tokens.size()];
            end = new int[tokens.size()];
            len = new int[tokens.size()];
            for (int i = 0; i < tokens.size(); i++) {
                start[i] = b.length();
                b.append(tokens.get(i));
                end[i] = b.length() - 1;
                len[i] = tokens.get(i).length();
            }
            compact = b.toString();
            tokenOf = new int[compact.length()];
            for (int i = 0; i < tokens.size(); i++) {
                for (int p = start[i]; p <= end[i]; p++) tokenOf[p] = i;
            }
        }
    }

    private Kw firstExact(List<String> tokens, boolean doubleCollapsed) {
        Seq s = new Seq(tokens);
        for (Kw k : words) {
            if (doubleCollapsed && k.hasDouble) continue;
            if (countIn(s, k, 1) > 0) return k;
        }
        return null;
    }

    private int countIn(Seq s, Kw k) { return countIn(s, k, Integer.MAX_VALUE); }

    private int countIn(Seq s, Kw k, int limit) {
        int n = 0;
        int from = 0;
        while (n < limit) {
            int at = s.compact.indexOf(k.compact, from);
            if (at < 0) break;
            from = at + 1;
            if (valid(s, k, at) && !harmlessAt(s, at, k.compact.length())) n++;
        }
        return n;
    }

    /**
     * Gilt ein Fund? Regeln:
     *  - Innerhalb eines Wortes: lange Wörter immer (auch als Teil, z. B. «bikinifigur»),
     *    kurze Wörter nur als ganzes Wort (damit «Sussex» oder «Klasse» nicht gesperrt werden).
     *  - Über mehrere Wörter verteilt («bay watch», «b i k i n i»): Das gesperrte Wort muss
     *    am Anfang eines Wortes beginnen. Im letzten Teil dürfen höchstens 3 Buchstaben folgen
     *    (Mehrzahl wie «bi kinis»), sonst würde z. B. «bus typ» als «busty» zählen.
     *    Kurze Wörter nur aus Einzelbuchstaben («s e x»).
     */
    private static boolean valid(Seq s, Kw k, int at) {
        int last = at + k.compact.length() - 1;
        int ts = s.tokenOf[at], te = s.tokenOf[last];
        if (ts == te) {
            if (!k.shortWord) return true;
            return at == s.start[ts] && last == s.end[ts];
        }
        if (at != s.start[ts]) return false;
        if (k.shortWord) {
            if (last != s.end[te]) return false;
            for (int i = ts; i <= te; i++) if (s.len[i] > 2) return false;
            return true;
        }
        int consumed = last - s.start[te] + 1;
        int rest = s.end[te] - last;
        if (rest == 0) return true;
        return consumed >= 3 && rest <= 3;
    }

    private boolean harmlessAt(Seq s, int at, int length) {
        if (harmless.isEmpty()) return false;
        // Umgebende Wörter betrachten
        int ts = s.tokenOf[at], te = s.tokenOf[at + length - 1];
        int a = s.start[Math.max(0, ts - 1)];
        int b = s.end[Math.min(s.start.length - 1, te + 1)];
        String around = s.compact.substring(a, b + 1);
        int rel = at - a;
        for (String h : harmless) {
            int from = 0;
            while (true) {
                int p = around.indexOf(h, from);
                if (p < 0) break;
                if (p <= rel && p + h.length() >= rel + length) return true;
                from = p + 1;
            }
        }
        return false;
    }

    // ================================================================ Tippfehler

    /**
     * Lange Wörter (ab 8 Buchstaben) werden auch mit einem Tippfehler erkannt, wenn der Fehler
     * nicht in den ersten 3 Buchstaben liegt. Geprüft werden einzelne Wörter und Paare
     * («bay watsch»).
     */
    private Kw firstFuzzy(List<String> tokens) {
        List<String> cands = new ArrayList<>(tokens);
        for (int i = 0; i + 1 < tokens.size(); i++) cands.add(tokens.get(i) + tokens.get(i + 1));
        for (String t : cands) {
            if (t.length() < 7 || t.length() > 30) continue;
            if (isHarmless(t)) continue;
            for (Kw k : words) {
                if (k.compact.length() < 8) continue;
                if (Math.abs(k.compact.length() - t.length()) > 1) continue;
                if (oneEdit(k.compact, t)) return k;
            }
        }
        return null;
    }

    private boolean isHarmless(String t) {
        for (String h : harmless) if (t.contains(h)) return true;
        return false;
    }

    /** Genau ein Unterschied (einfügen, löschen, ersetzen, zwei vertauschen), nicht vorne. */
    static boolean oneEdit(String a, String b) {
        if (a.equals(b)) return false;
        int la = a.length(), lb = b.length();
        int i = 0;
        while (i < la && i < lb && a.charAt(i) == b.charAt(i)) i++;
        if (i < 3) return false;
        if (la == lb) {
            if (a.substring(i + 1).equals(b.substring(i + 1))) return true;                // ersetzt
            return i + 1 < la && a.charAt(i) == b.charAt(i + 1) && a.charAt(i + 1) == b.charAt(i)
                && a.substring(i + 2).equals(b.substring(i + 2));                           // vertauscht
        }
        if (la + 1 == lb) return a.substring(i).equals(b.substring(i + 1));                 // eingefügt
        if (la == lb + 1) return a.substring(i + 1).equals(b.substring(i));                 // gelöscht
        return false;
    }
}
