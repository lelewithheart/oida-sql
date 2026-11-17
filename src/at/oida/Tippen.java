package at.oida;

import at.oida.Uebersetzer.Art;
import at.oida.Uebersetzer.Phrase;
import at.oida.Uebersetzer.Token;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Übersetzt beim Tippen und beim Einfügen von MySQL nach OIDA-SQL.
 *
 * Beim Tippen: Sobald ein Wort fertig ist (Leerzeichen, Klammer, Beistrich, Enter), wird das
 * MySQL-Wort davor durch das Dialekt-Wort ersetzt. Wörter, mit denen ein längerer Ausdruck
 * anfangen kann (ORDER ... BY, NOT ... NULL, IF ... NOT ... EXISTS), bleiben nach einem
 * Leerzeichen kurz stehen, bis klar ist, was daraus wird.
 *
 * Diese Klasse hat nichts mit DataGrip zu tun, damit man sie alleine testen kann.
 */
public final class Tippen {

    /** Ersetze im Text den Bereich [von, bis) durch neu. */
    public static final class Ersatz {
        public final int von;
        public final int bis;
        public final String neu;

        Ersatz(int von, int bis, String neu) {
            this.von = von;
            this.bis = bis;
            this.neu = neu;
        }
    }

    private Tippen() {
    }

    /**
     * @param text         der Text vom Anfang des Befehls bis zum Ende des gerade fertig getippten Wortes
     * @param leerzeichen  true, wenn danach ein Leerzeichen getippt wurde (dann darf ein Wort noch warten)
     * @return die nötigen Ersetzungen, von hinten nach vorne sortiert (so kann man sie der Reihe nach anwenden)
     */
    public static List<Ersatz> beimTippen(Uebersetzer u, String text, boolean leerzeichen) {
        List<Ersatz> ergebnis = new ArrayList<>();
        List<Token> tokens = Uebersetzer.zerlegen(text);
        int letztes = tokens.size() - 1;
        if (letztes < 0 || tokens.get(letztes).art != Art.WORT) {
            return ergebnis;   // in einem Text, einem Kommentar oder nach einem Zeichen
        }
        int[] pos = new int[tokens.size() + 1];
        for (int i = 0; i < tokens.size(); i++) {
            pos[i + 1] = pos[i] + tokens.get(i).text.length();
        }

        // Die Wörter direkt davor (nur durch Leerraum getrennt), höchstens drei.
        List<Integer> reihe = new ArrayList<>();
        reihe.add(letztes);
        int j = letztes;
        while (reihe.size() < 4 && j - 2 >= 0 && tokens.get(j - 1).art == Art.LEER && tokens.get(j - 2).art == Art.WORT) {
            j -= 2;
            reihe.add(0, j);
        }
        int n = reihe.size() - 1;

        // Welche Wörter davor haben noch gewartet? Nur die, die der Anfang eines längeren Ausdrucks sind.
        int start = n;
        for (int q = n - 1; q >= Math.max(0, n - 2); q--) {
            if (u.istAnfangLaengererMysqlPhrase(tokens, reihe.get(q), reihe.get(n - 1))) {
                start = q;
            }
        }

        // Soll das Ende jetzt selbst noch warten?
        int warteAb = Integer.MAX_VALUE;
        if (leerzeichen) {
            for (int q = start; q <= n; q++) {
                if (u.istAnfangLaengererMysqlPhrase(tokens, reihe.get(q), letztes)) {
                    warteAb = q;
                    break;
                }
            }
        }

        int[] bis = new int[1];
        for (int p = start; p <= n && p < warteAb; p++) {
            int i = reihe.get(p);
            Token t = tokens.get(i);
            if (Uebersetzer.istNameMitPunkt(tokens, i)) {
                continue;
            }
            Phrase phrase = u.findeMysql(tokens, i, bis);
            if (phrase != null && phrase.woerter.length > 1 && phrase.woerter[0].equals("NOT") && nachIs(tokens, i)) {
                phrase = einzelwort(u, tokens, i, bis);   // IS NOT NULL -> IS NED NUI
            }
            if (phrase == null) {
                continue;
            }
            // Bis zum Ende reichen darf der Ausdruck nur, wenn dort nichts mehr warten soll.
            int endeP = reihe.indexOf(bis[0]);
            if (endeP < 0 || endeP >= warteAb) {
                phrase = einzelwort(u, tokens, i, bis);
                if (phrase == null) {
                    continue;
                }
                endeP = p;
            }
            // Ein Wort, das auch ein Dialekt-Wort ist (IN = INTO), nach einem Dialekt-Wort stehen lassen:
            // "EINEHAUN IN" ist schon Dialekt. Nach einem Namen ("WO x IN") wird es übersetzt.
            if (phrase.woerter.length == 1 && u.findeDialekt(tokens, i, new int[1]) != null
                    && p > 0 && p - 1 < start && u.findeDialekt(tokens, reihe.get(p - 1), new int[1]) != null) {
                continue;
            }
            boolean klein = t.text.equals(t.text.toLowerCase(Locale.ROOT));
            String neu = klein ? phrase.dialekt.toLowerCase(Locale.ROOT) : phrase.dialekt;
            ergebnis.add(0, new Ersatz(pos[i], pos[bis[0] + 1], neu));
            p = endeP;
        }
        return ergebnis;
    }

    /**
     * Für eingefügten Text: MySQL-Wörter übersetzen und jeden Strichpunkt am Befehlsende durch das Endwort ersetzen.
     * Was schon Dialekt ist, bleibt wie es ist. Ein Rest ohne Strichpunkt bekommt kein Endwort (es könnte nur ein Stück sein).
     */
    public static String einfuegen(Uebersetzer u, String text) {
        return Rueckwaerts.fuersEinfuegen(u, text);
    }

    /**
     * Endet der Text mitten in einem Text in Anführungszeichen oder in einem Kommentar?
     * Dort wird nichts übersetzt und nichts vorgeschlagen.
     */
    public static boolean inTextOderKommentar(String textBisCursor) {
        List<Token> tokens = Uebersetzer.zerlegen(textBisCursor);
        if (tokens.isEmpty()) {
            return false;
        }
        String t = tokens.get(tokens.size() - 1).text;
        switch (tokens.get(tokens.size() - 1).art) {
            case TEXT:
                return t.length() < 2 || t.charAt(t.length() - 1) != t.charAt(0);   // noch nicht geschlossen
            case KOMMENTAR:
                return !t.startsWith("/*") || !t.endsWith("*/") || t.length() < 4;  // Zeilenkommentar oder offener Block
            default:
                return false;
        }
    }

    private static boolean nachIs(List<Token> tokens, int i) {
        for (int j = i - 1; j >= 0; j--) {
            Art a = tokens.get(j).art;
            if (a == Art.LEER || a == Art.KOMMENTAR) {
                continue;
            }
            return a == Art.WORT && tokens.get(j).text.equalsIgnoreCase("IS");
        }
        return false;
    }

    private static Phrase einzelwort(Uebersetzer u, List<Token> tokens, int i, int[] bis) {
        List<Token> allein = new ArrayList<>();
        allein.add(tokens.get(i));
        Phrase p = u.findeMysql(allein, 0, new int[1]);
        bis[0] = i;
        return p;
    }
}
