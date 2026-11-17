package at.oida;

import at.oida.Uebersetzer.Art;
import at.oida.Uebersetzer.Phrase;
import at.oida.Uebersetzer.Token;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Der Rückweg: schreibt ein normales MySQL-Skript nach OIDA-SQL um.
 *
 * Jedes MySQL-Wort mit Übersetzung wird durch die erste Dialekt-Variante aus dem
 * Wörterbuch ersetzt, und vor jedem Befehlsende kommt das Endwort. Jeder umgeschriebene
 * Befehl wird sofort wieder vorwärts übersetzt und mit dem Original verglichen.
 */
public final class Rueckwaerts {

    /** Ergebnis für ein ganzes Skript. */
    public static final class Ergebnis {
        public final StringBuilder text = new StringBuilder();
        public int befehle;
        /** Namen, die wie ein Dialekt-Wort heißen und deshalb in Backticks gesetzt wurden. */
        public final Map<String, Integer> inBackticks = new TreeMap<>();
        /** Wörter, die englisch geblieben sind (keine Übersetzung im Wörterbuch), mit Häufigkeit. */
        public final Map<String, Integer> englischGeblieben = new TreeMap<>();
        /** Befehle, bei denen die Gegenprobe nicht das Original ergeben hat. */
        public final List<String> probleme = new ArrayList<>();
    }

    private static final Set<String> ROUTINEN = new HashSet<>(Arrays.asList("TRIGGER", "PROCEDURE", "FUNCTION", "EVENT"));
    private static final Set<String> BLOCK_ENDEN = new HashSet<>(Arrays.asList("IF", "WHILE", "LOOP", "REPEAT"));

    private final Uebersetzer u;
    private final String sql;
    private final List<Token> tokens;
    private final int[] pos;
    private final Ergebnis ergebnis = new Ergebnis();
    /** Für eingefügten Text: Strichpunkt wird zum Endwort, Reste ohne Strichpunkt bleiben ohne Endwort, keine Backticks. */
    private final boolean einfuegen;

    private Rueckwaerts(Uebersetzer u, String sql, boolean einfuegen) {
        this.u = u;
        this.sql = sql;
        this.einfuegen = einfuegen;
        this.tokens = Uebersetzer.zerlegen(sql);
        this.pos = new int[tokens.size() + 1];
        for (int i = 0; i < tokens.size(); i++) {
            pos[i + 1] = pos[i] + tokens.get(i).text.length();
        }
    }

    public static Ergebnis umschreiben(Uebersetzer u, String mysql) {
        Rueckwaerts r = new Rueckwaerts(u, mysql, false);
        r.lauf();
        return r.ergebnis;
    }

    /**
     * Für Text, der in eine OIDA-SQL-Datei eingefügt wird: MySQL-Wörter werden übersetzt, der
     * Strichpunkt am Ende eines Befehls wird zum Endwort. Ein Rest ohne Strichpunkt bekommt kein
     * Endwort (es könnte nur ein Stück sein), und was schon Dialekt ist, bleibt unverändert.
     */
    static String fuersEinfuegen(Uebersetzer u, String text) {
        Rueckwaerts r = new Rueckwaerts(u, text, true);
        r.lauf();
        return r.ergebnis.text.toString();
    }

    // ------------------------------------------------------- Befehle trennen

    private void lauf() {
        String trenner = ";";
        int anfang = 0;          // erstes Token des aktuellen Befehls
        int tiefe = 0;           // Verschachtelung von BEGIN ... END in Triggern/Prozeduren
        boolean routine = false;
        String erstesWort = null;

        int i = 0;
        while (i < tokens.size()) {
            Token t = tokens.get(i);

            // DELIMITER ist ein Befehl für das Programm, nicht für MySQL: Zeile unverändert lassen.
            if (t.art == Art.WORT && t.text.equalsIgnoreCase("DELIMITER") && amZeilenanfang(i)) {
                befehl(anfang, i, "");
                int j = i;
                while (j < tokens.size() && tokens.get(j).text.indexOf('\n') < 0) {
                    j++;
                }
                String zeile = sql.substring(pos[i], pos[j]);
                String neu = zeile.substring("DELIMITER".length()).trim();
                if (!neu.isEmpty()) {
                    trenner = neu;
                }
                ergebnis.text.append(zeile);
                anfang = j;
                i = j;
                tiefe = 0;
                routine = false;
                erstesWort = null;
                continue;
            }

            if (t.art == Art.WORT) {
                String w = Uebersetzer.norm(t.text);
                if (erstesWort == null) {
                    erstesWort = w;
                }
                if ("CREATE".equals(erstesWort) && tiefe == 0 && ROUTINEN.contains(w)) {
                    routine = true;
                }
                if (routine && !Uebersetzer.istNameMitPunkt(tokens, i)) {
                    if (w.equals("BEGIN") || w.equals("CASE")) {
                        tiefe++;
                    } else if (w.equals("END")) {
                        int n = naechstesWort(i);
                        String danach = n < 0 ? "" : Uebersetzer.norm(tokens.get(n).text);
                        if (danach.equals("CASE")) {
                            tiefe = Math.max(0, tiefe - 1);
                            i = n + 1;      // das CASE von END CASE nicht nochmal zählen
                            continue;
                        }
                        if (!BLOCK_ENDEN.contains(danach)) {
                            tiefe = Math.max(0, tiefe - 1);
                        }
                    }
                }
            }

            if (tiefe == 0 && (t.art == Art.SONST || t.art == Art.WORT) && sql.startsWith(trenner, pos[i])) {
                int k = i;
                while (k < tokens.size() && pos[k] < pos[i] + trenner.length()) {
                    k++;
                }
                befehl(anfang, i, sql.substring(pos[i], pos[k]));
                anfang = k;
                i = k;
                routine = false;
                erstesWort = null;
                continue;
            }
            i++;
        }
        befehl(anfang, tokens.size(), "");
    }

    private boolean amZeilenanfang(int i) {
        if (i == 0) {
            return true;
        }
        Token davor = tokens.get(i - 1);
        return davor.art == Art.LEER && davor.text.indexOf('\n') >= 0;
    }

    private int naechstesWort(int i) {
        for (int j = i + 1; j < tokens.size(); j++) {
            Art a = tokens.get(j).art;
            if (a == Art.LEER || a == Art.KOMMENTAR) {
                continue;
            }
            return a == Art.WORT ? j : -1;
        }
        return -1;
    }

    private static boolean unwichtig(Token t) {
        return t.art == Art.LEER || t.art == Art.KOMMENTAR;
    }

    // ------------------------------------------------- Einen Befehl schreiben

    /** Schreibt die Tokens [von, bis) als einen Befehl um und hängt den Trenner an. */
    private void befehl(int von, int bis, String trenner) {
        int letztes = bis - 1;
        while (letztes >= von && unwichtig(tokens.get(letztes))) {
            letztes--;
        }
        // Ein übrig gebliebenes // oder $$ am Ende (Skript ohne passendes DELIMITER) nicht mitnehmen.
        if (trenner.isEmpty() && !einfuegen) {
            while (letztes >= von && (unwichtig(tokens.get(letztes)) || tokens.get(letztes).text.equals("/")
                    || tokens.get(letztes).text.equals("$$"))) {
                letztes--;
            }
        }
        if (letztes < von) {   // nur Leerraum und Kommentare
            ergebnis.text.append(sql, pos[von], pos[bis]).append(trenner);
            return;
        }

        if (einfuegen) {
            Token ende = tokens.get(letztes);
            boolean hatSchonEndwort = ende.art == Art.WORT && Uebersetzer.norm(ende.text).equals(u.endwortGross());
            ergebnis.text.append(dialekt(von, letztes));
            if (!trenner.isEmpty() && !hatSchonEndwort) {
                ergebnis.text.append(' ').append(u.endwortGross());
            }
            ergebnis.text.append(sql, pos[letztes + 1], pos[bis]);
            if (!trenner.equals(";")) {
                ergebnis.text.append(trenner);   // nur der Strichpunkt ist überflüssig, // oder $$ bleiben
            }
            ergebnis.befehle++;
            return;
        }

        String oida = dialekt(von, letztes) + " " + u.endwortGross();
        ergebnis.text.append(oida).append(sql, pos[letztes + 1], pos[bis]).append(trenner);
        ergebnis.befehle++;

        // Gegenprobe: vorwärts übersetzt muss wieder das Original herauskommen.
        String original = sql.substring(pos[von], pos[letztes + 1]);
        try {
            String zurueck = u.uebersetzen(oida);
            if (!gleich(original, zurueck)) {
                ergebnis.probleme.add("Gegenprobe weicht ab: " + kurz(original) + "\n      wurde zu: " + kurz(zurueck));
            }
        } catch (SQLException e) {
            ergebnis.probleme.add("Gegenprobe: " + e.getMessage() + " bei: " + kurz(original));
        }
    }

    private String dialekt(int von, int bis) {
        StringBuilder s = new StringBuilder();
        int[] ende = new int[1];
        for (int i = von; i <= bis; i++) {
            Token t = tokens.get(i);
            if (t.art != Art.WORT || Uebersetzer.istNameMitPunkt(tokens, i)) {
                s.append(t.text);
                continue;
            }
            Phrase p = u.findeMysql(tokens, i, ende);
            if (p != null && p.woerter.length > 1 && p.woerter[0].equals("NOT") && nachIs(i)) {
                p = einzelwort(i, ende);   // "IS NOT NULL" soll "IS NED NUI" werden, nicht "IS MUASS SEI"
            }
            if (einfuegen && p != null && p.woerter.length == 1 && u.findeDialekt(tokens, i, new int[1]) != null
                    && nachDialektWort(i)) {
                p = null;   // "EINEHAUN IN" ist schon Dialekt (IN = INTO) und darf nicht zu DRINNA werden
            }
            if (p != null && ende[0] <= bis) {
                boolean klein = t.text.equals(t.text.toLowerCase(Locale.ROOT));
                s.append(klein ? p.dialekt.toLowerCase(Locale.ROOT) : p.dialekt);
                i = ende[0];
                continue;
            }
            String gross = Uebersetzer.norm(t.text);
            if (einfuegen) {
                s.append(t.text);   // könnte schon Dialekt sein: nicht anfassen
                continue;
            }
            if (u.findeDialekt(tokens, i, ende) != null || gross.equals(u.endwortGross())) {
                // Ein Name, der zufällig wie ein Dialekt-Wort heißt: schützen.
                s.append('`').append(t.text).append('`');
                ergebnis.inBackticks.merge(t.text, 1, Integer::sum);
                continue;
            }
            s.append(t.text);
            ergebnis.englischGeblieben.merge(gross, 1, Integer::sum);
        }
        return s.toString();
    }

    /** Steht direkt davor (nur durch Leerraum getrennt) ein Dialekt-Wort? */
    private boolean nachDialektWort(int i) {
        if (i < 2 || tokens.get(i - 1).art != Art.LEER || tokens.get(i - 2).art != Art.WORT) {
            return false;
        }
        return u.findeDialekt(tokens, i - 2, new int[1]) != null;
    }

    private boolean nachIs(int i) {
        for (int j = i - 1; j >= 0; j--) {
            if (unwichtig(tokens.get(j))) {
                continue;
            }
            return tokens.get(j).art == Art.WORT && tokens.get(j).text.equalsIgnoreCase("IS");
        }
        return false;
    }

    /** Übersetzung nur für das einzelne Wort an Position i (ohne Mehrwort-Ausdrücke). */
    private Phrase einzelwort(int i, int[] ende) {
        List<Token> allein = new ArrayList<>();
        allein.add(tokens.get(i));
        Phrase p = u.findeMysql(allein, 0, new int[1]);
        ende[0] = i;
        return p;
    }

    // ---------------------------------------------------------- Vergleichen

    /** Gleich bis auf Leerraum, Groß-/Kleinschreibung von Wörtern und Backticks um einfache Namen. */
    private static boolean gleich(String a, String b) {
        return normal(a).equals(normal(b));
    }

    private static List<String> normal(String s) {
        List<String> n = new ArrayList<>();
        for (Token t : Uebersetzer.zerlegen(s)) {
            if (t.art == Art.LEER) {
                continue;
            }
            if (t.art == Art.WORT) {
                n.add(Uebersetzer.norm(t.text));
            } else if (t.art == Art.TEXT && t.text.startsWith("`") && t.text.length() > 2) {
                n.add(Uebersetzer.norm(t.text.substring(1, t.text.length() - 1)));
            } else {
                n.add(t.text);
            }
        }
        return n;
    }

    private static String kurz(String s) {
        String eins = s.replaceAll("\\s+", " ").trim();
        return eins.length() > 160 ? eins.substring(0, 160) + " ..." : eins;
    }
}
