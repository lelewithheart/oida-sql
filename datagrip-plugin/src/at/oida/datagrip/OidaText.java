package at.oida.datagrip;

import java.util.ArrayList;
import java.util.List;

/**
 * Findet in einem Text die OIDA-SQL-Befehle: Ein Befehl hört mit dem Wort OIDA auf
 * (außerhalb von Texten in Anführungszeichen und Kommentaren), danach kommt meist ein Strichpunkt.
 *
 * Diese Klasse hat absichtlich nichts mit DataGrip zu tun, damit man sie alleine testen kann.
 */
public final class OidaText {

    static final String ENDWORT = "OIDA";

    /** Ein Befehl: [anfang, ende) ist der Text bis einschließlich OIDA, bereichEnde das Zeilenende danach. */
    public static final class Befehl {
        public final int anfang;
        public final int ende;
        public final int bereichEnde;

        Befehl(int anfang, int ende, int bereichEnde) {
            this.anfang = anfang;
            this.ende = ende;
            this.bereichEnde = bereichEnde;
        }
    }

    private OidaText() {
    }

    /** Kommt im Text mindestens ein OIDA-Befehl vor? */
    public static boolean istOida(CharSequence text) {
        return !befehle(text, true).isEmpty();
    }

    /**
     * Der Befehl, in dem der Cursor steht. Steht der Cursor zwischen zwei Befehlen
     * (Leerzeile, Kommentar), ist es der nächste darunter. Nach dem letzten Befehl: null.
     */
    public static Befehl befehlBei(CharSequence text, int cursor) {
        for (Befehl b : befehle(text, false)) {
            if (cursor <= b.bereichEnde) {
                return b;
            }
        }
        return null;
    }

    public static List<Befehl> befehle(CharSequence text, boolean nurErster) {
        List<Befehl> liste = new ArrayList<>();
        int n = text.length();
        int anfang = -1;          // erstes wichtiges Zeichen des aktuellen Befehls
        int i = 0;
        while (i < n) {
            char c = text.charAt(i);
            char danach = i + 1 < n ? text.charAt(i + 1) : '\0';

            if (Character.isWhitespace(c)) {
                i++;
            } else if (c == '#' || (c == '-' && danach == '-' && (i + 2 >= n || Character.isWhitespace(text.charAt(i + 2))))) {
                while (i < n && text.charAt(i) != '\n') {
                    i++;
                }
            } else if (c == '/' && danach == '*') {
                int zu = indexOf(text, "*/", i + 2);
                i = zu < 0 ? n : zu + 2;
            } else if (c == '\'' || c == '"' || c == '`') {
                if (anfang < 0) {
                    anfang = i;
                }
                i++;
                while (i < n) {
                    char d = text.charAt(i);
                    if (d == '\\' && c != '`') {
                        i += 2;
                    } else if (d == c) {
                        if (i + 1 < n && text.charAt(i + 1) == c) {
                            i += 2;
                        } else {
                            i++;
                            break;
                        }
                    } else {
                        i++;
                    }
                }
                i = Math.min(i, n);
            } else if (wortZeichen(c)) {
                int start = i;
                while (i < n && wortZeichen(text.charAt(i))) {
                    i++;
                }
                String wort = text.subSequence(start, i).toString();
                if (anfang < 0 && wort.equalsIgnoreCase("DELIMITER")) {
                    while (i < n && text.charAt(i) != '\n') {   // DELIMITER-Zeile gehört zu keinem Befehl
                        i++;
                    }
                    continue;
                }
                if (anfang < 0) {
                    anfang = start;
                }
                if (wort.equalsIgnoreCase(ENDWORT)) {
                    int ende = i;
                    // Trenner danach (; oder // oder $$) und den Rest der Zeile zum Bereich zählen
                    int j = i;
                    while (j < n && (text.charAt(j) == ' ' || text.charAt(j) == '\t')) {
                        j++;
                    }
                    if (j < n && text.charAt(j) == ';') {
                        j++;
                    } else if (j + 1 < n && ((text.charAt(j) == '/' && text.charAt(j + 1) == '/')
                            || (text.charAt(j) == '$' && text.charAt(j + 1) == '$'))) {
                        j += 2;
                    }
                    i = j;
                    while (j < n && text.charAt(j) != '\n') {
                        j++;
                    }
                    liste.add(new Befehl(anfang, ende, j));
                    if (nurErster) {
                        return liste;
                    }
                    anfang = -1;
                }
            } else {
                if (anfang < 0 && c != ';') {
                    anfang = i;
                }
                i++;
            }
        }
        return liste;
    }

    private static boolean wortZeichen(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }

    private static int indexOf(CharSequence text, String gesucht, int ab) {
        for (int i = ab; i + gesucht.length() <= text.length(); i++) {
            boolean passt = true;
            for (int k = 0; k < gesucht.length(); k++) {
                if (text.charAt(i + k) != gesucht.charAt(k)) {
                    passt = false;
                    break;
                }
            }
            if (passt) {
                return i;
            }
        }
        return -1;
    }
}
