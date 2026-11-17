package at.oida;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.SQLSyntaxErrorException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Übersetzt OIDA-SQL in normales MySQL-SQL.
 *
 * Prüft, ob der Befehl mit dem Endwort aufhört, und ersetzt die Dialekt-Wörter
 * durch die MySQL-Wörter. Texte in Anführungszeichen, `Namen` und Kommentare
 * bleiben unverändert.
 */
public final class Uebersetzer {

    enum Art { WORT, LEER, KOMMENTAR, TEXT, SONST }

    static final class Token {
        final Art art;
        final String text;

        Token(Art art, String text) {
            this.art = art;
            this.text = text;
        }
    }

    /** Eine Wortfolge aus dem Wörterbuch (meist nur ein Wort). */
    static final class Phrase {
        final String[] woerter;   // normalisiert (Großbuchstaben)
        final String englisch;    // MySQL-Seite, so wie sie in der Datei steht
        final String dialekt;     // Dialekt-Seite, so wie sie in der Datei steht

        Phrase(String[] woerter, String englisch, String dialekt) {
            this.woerter = woerter;
            this.englisch = englisch;
            this.dialekt = dialekt;
        }
    }

    private final Map<String, List<Phrase>> dialektPhrasen = new HashMap<>();
    private final Map<String, List<Phrase>> englischPhrasen = new HashMap<>();
    /** Alle MySQL-Wörter mit Übersetzung (auch die, die englisch erlaubt bleiben) - für den Rückweg. */
    private final Map<String, List<Phrase>> rueckPhrasen = new HashMap<>();
    private final List<Pattern> ausnahmen = new ArrayList<>();
    private final List<String> ohneUebersetzung = new ArrayList<>();
    private int anzahlUebersetzt;

    private String endwort = "OIDA";
    private boolean englischErlaubt = false;
    // Jeweils mehrere Varianten möglich, es wird zufällig eine genommen.
    private String[] fehlerKeinEndwort = {"Heast, wo is des OIDA?"};
    private String[] fehlerEnglisch = {"Des haaßt {dialekt}, ned {englisch}!"};
    private String[] fehlerMysql = {};   // Vorspann vor echten MySQL-Fehlern, leer = aus

    // ------------------------------------------------------------------ Laden

    private static volatile Uebersetzer ausJar;

    /** Lädt Wörterbuch, Einstellungen und Ausnahmen, die in der Jar mitgeliefert werden. */
    public static Uebersetzer ausJar() {
        Uebersetzer u = ausJar;
        if (u == null) {
            synchronized (Uebersetzer.class) {
                u = ausJar;
                if (u == null) {
                    u = new Uebersetzer(ressource("woerterbuch.txt"), ressource("einstellungen.txt"),
                            ressource("ausnahmen.txt"));
                    ausJar = u;
                }
            }
        }
        return u;
    }

    private static String ressource(String name) {
        try (InputStream in = Uebersetzer.class.getResourceAsStream(name)) {
            if (in == null) {
                throw new IllegalStateException("OIDA-SQL: " + name + " fehlt in der Jar. Bitte neu bauen (bauen.bat).");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("OIDA-SQL: " + name + " kann nicht gelesen werden.", e);
        }
    }

    /**
     * @param woerterbuch   Inhalt von woerterbuch.txt
     * @param einstellungen Inhalt von einstellungen.txt (darf null sein)
     * @param ausnahmen     Inhalt von ausnahmen.txt (darf null sein)
     * @throws IllegalStateException wenn eine der Dateien einen Fehler enthält
     */
    public Uebersetzer(String woerterbuch, String einstellungen, String ausnahmen) {
        ladeEinstellungen(einstellungen);
        ladeWoerterbuch(woerterbuch);
        ladeAusnahmen(ausnahmen);
    }

    private static List<String> zeilen(String inhalt) {
        List<String> zeilen = new ArrayList<>();
        if (inhalt == null) {
            return zeilen;
        }
        if (inhalt.startsWith("﻿")) {   // BOM vom Windows-Editor
            inhalt = inhalt.substring(1);
        }
        try (BufferedReader r = new BufferedReader(new StringReader(inhalt))) {
            String z;
            while ((z = r.readLine()) != null) {
                zeilen.add(z);
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return zeilen;
    }

    private void ladeEinstellungen(String inhalt) {
        int nr = 0;
        for (String roh : zeilen(inhalt)) {
            nr++;
            String z = roh.trim();
            if (z.isEmpty() || z.startsWith("#")) {
                continue;
            }
            int gleich = z.indexOf('=');
            if (gleich < 0) {
                throw new IllegalStateException("einstellungen.txt, Zeile " + nr + ": '=' fehlt: " + z);
            }
            String name = z.substring(0, gleich).trim().toLowerCase(Locale.ROOT);
            String wert = z.substring(gleich + 1).trim();
            switch (name) {
                case "endwort":
                    List<Token> t = zerlegen(wert);
                    if (t.size() != 1 || t.get(0).art != Art.WORT) {
                        throw new IllegalStateException("einstellungen.txt, Zeile " + nr
                                + ": endwort muss ein einzelnes Wort sein: " + wert);
                    }
                    endwort = norm(wert);
                    break;
                case "englisch_erlaubt":
                    englischErlaubt = wert.equalsIgnoreCase("ja") || wert.equalsIgnoreCase("true");
                    break;
                case "fehler_kein_endwort":
                    fehlerKeinEndwort = varianten(wert, nr, true);
                    break;
                case "fehler_englisch":
                    fehlerEnglisch = varianten(wert, nr, true);
                    break;
                case "fehler_mysql":
                    fehlerMysql = varianten(wert, nr, false);
                    break;
                default:
                    throw new IllegalStateException("einstellungen.txt, Zeile " + nr + ": unbekannte Einstellung: " + name);
            }
        }
    }

    /** Zerlegt "Meldung eins | Meldung zwei" in die einzelnen Varianten. */
    private static String[] varianten(String wert, int zeile, boolean pflicht) {
        List<String> v = new ArrayList<>();
        for (String teil : wert.split("\\|")) {
            if (!teil.trim().isEmpty()) {
                v.add(teil.trim());
            }
        }
        if (pflicht && v.isEmpty()) {
            throw new IllegalStateException("einstellungen.txt, Zeile " + zeile + ": hier muss eine Meldung stehen.");
        }
        return v.toArray(new String[0]);
    }

    private static String zufall(String[] varianten) {
        return varianten[ThreadLocalRandom.current().nextInt(varianten.length)];
    }

    /**
     * Stellt vor die häufigsten echten MySQL-Fehler (Tippfehler, Tabelle gibt es nicht,
     * doppelter Schlüssel ...) einen Vorspann aus den Einstellungen. Alles andere bleibt unverändert.
     */
    SQLException verziert(SQLException e) {
        if (fehlerMysql.length == 0 || e.getMessage() == null) {
            return e;
        }
        String text = zufall(fehlerMysql) + " " + e.getMessage();
        SQLException neu;
        if (e instanceof SQLSyntaxErrorException) {
            neu = new SQLSyntaxErrorException(text, e.getSQLState(), e.getErrorCode(), e);
        } else if (e instanceof SQLIntegrityConstraintViolationException) {
            neu = new SQLIntegrityConstraintViolationException(text, e.getSQLState(), e.getErrorCode(), e);
        } else {
            return e;
        }
        neu.setNextException(e.getNextException());
        return neu;
    }

    private void ladeWoerterbuch(String inhalt) {
        Map<String, Phrase> schonVergeben = new LinkedHashMap<>();
        int nr = 0;
        for (String roh : zeilen(inhalt)) {
            nr++;
            String z = roh.trim();
            if (z.isEmpty() || z.startsWith("#")) {
                continue;
            }
            int gleich = z.indexOf('=');
            if (gleich < 0) {
                throw new IllegalStateException("woerterbuch.txt, Zeile " + nr + ": '=' fehlt: " + z);
            }
            String englisch = z.substring(0, gleich).trim().replaceAll("\\s+", " ");
            String[] englischWoerter = woerter(englisch, nr);
            String rechts = z.substring(gleich + 1).trim();
            if (rechts.isEmpty()) {
                ohneUebersetzung.add(englisch);
                continue;
            }
            boolean echteUebersetzung = false;
            boolean englischBleibt = false;   // rechts steht auch das englische Wort selbst
            String ersteVariante = null;
            for (String variante : rechts.split(",")) {
                String dialekt = variante.trim().replaceAll("\\s+", " ");
                if (dialekt.isEmpty()) {
                    continue;
                }
                String[] dialektWoerter = woerter(dialekt, nr);
                String schluessel = String.join(" ", dialektWoerter);
                if (schluessel.equals(String.join(" ", englischWoerter))) {
                    englischBleibt = true;   // z.B. IS = IS: nichts zu übersetzen, bleibt erlaubt
                    continue;
                }
                if (dialektWoerter.length == 1 && dialektWoerter[0].equals(endwort)) {
                    throw new IllegalStateException("woerterbuch.txt, Zeile " + nr + ": '" + dialekt
                            + "' ist schon das Endwort und kann nicht auch " + englisch + " heißen.");
                }
                Phrase vorher = schonVergeben.get(schluessel);
                if (vorher != null) {
                    if (vorher.englisch.equalsIgnoreCase(englisch)) {
                        continue;
                    }
                    throw new IllegalStateException("woerterbuch.txt, Zeile " + nr + ": '" + dialekt
                            + "' steht schon bei " + vorher.englisch + " und kann nicht auch " + englisch + " heißen.");
                }
                Phrase p = new Phrase(dialektWoerter, englisch, dialekt);
                schonVergeben.put(schluessel, p);
                dialektPhrasen.computeIfAbsent(dialektWoerter[0], k -> new ArrayList<>()).add(p);
                if (ersteVariante == null) {
                    ersteVariante = dialekt;
                }
                echteUebersetzung = true;
            }
            if (echteUebersetzung) {
                anzahlUebersetzt++;
                rueckPhrasen.computeIfAbsent(englischWoerter[0], k -> new ArrayList<>())
                        .add(new Phrase(englischWoerter, englisch, ersteVariante));
            }
            if (echteUebersetzung && !englischBleibt) {
                englischPhrasen.computeIfAbsent(englischWoerter[0], k -> new ArrayList<>())
                        .add(new Phrase(englischWoerter, englisch, ersteVariante));
            }
        }
        Comparator<Phrase> laengsteZuerst = (a, b) -> b.woerter.length - a.woerter.length;
        dialektPhrasen.values().forEach(l -> l.sort(laengsteZuerst));
        englischPhrasen.values().forEach(l -> l.sort(laengsteZuerst));
        rueckPhrasen.values().forEach(l -> l.sort(laengsteZuerst));
    }

    /**
     * Alle Wortpaare aus dem Wörterbuch, für die Vorschläge im Editor.
     *
     * @return je Dialekt-Variante ein Paar {Dialekt, MySQL}
     */
    public List<String[]> alleWoerter() {
        List<String[]> paare = new ArrayList<>();
        for (List<Phrase> liste : dialektPhrasen.values()) {
            for (Phrase p : liste) {
                paare.add(new String[] {p.dialekt, p.englisch});
            }
        }
        paare.sort((a, b) -> a[0].compareTo(b[0]));
        return paare;
    }

    /** Ist die Wortfolge tokens[von..bis] (nur durch Leerraum getrennt) der Anfang einer längeren MySQL-Phrase? */
    boolean istAnfangLaengererMysqlPhrase(List<Token> tokens, int von, int bis) {
        List<String> woerter = new ArrayList<>();
        for (int i = von; i <= bis; i++) {
            Token t = tokens.get(i);
            if (t.art == Art.WORT) {
                woerter.add(norm(t.text));
            } else if (t.art != Art.LEER) {
                return false;
            }
        }
        List<Phrase> kandidaten = woerter.isEmpty() ? null : rueckPhrasen.get(woerter.get(0));
        if (kandidaten == null) {
            return false;
        }
        for (Phrase p : kandidaten) {
            if (p.woerter.length > woerter.size()) {
                boolean passt = true;
                for (int w = 0; w < woerter.size(); w++) {
                    passt &= p.woerter[w].equals(woerter.get(w));
                }
                if (passt) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Dialekt-Phrase, die an Position i beginnt (oder null). bis[0] = Index des letzten Tokens. */
    Phrase findeDialekt(List<Token> tokens, int i, int[] bis) {
        return finde(dialektPhrasen, tokens, i, bis);
    }

    /** MySQL-Phrase mit Übersetzung, die an Position i beginnt (oder null). */
    Phrase findeMysql(List<Token> tokens, int i, int[] bis) {
        return finde(rueckPhrasen, tokens, i, bis);
    }

    String endwortGross() {
        return endwort;
    }

    /** Zerlegt einen Wörterbuch-Eintrag in normalisierte Wörter und prüft, dass es wirklich nur Wörter sind. */
    private static String[] woerter(String eintrag, int zeile) {
        List<String> w = new ArrayList<>();
        for (Token t : zerlegen(eintrag)) {
            if (t.art == Art.LEER) {
                continue;
            }
            if (t.art != Art.WORT) {
                throw new IllegalStateException("woerterbuch.txt, Zeile " + zeile + ": '" + eintrag
                        + "' darf nur aus Wörtern bestehen (Buchstaben, Ziffern, _), gefunden: " + t.text);
            }
            w.add(norm(t.text));
        }
        if (w.isEmpty()) {
            throw new IllegalStateException("woerterbuch.txt, Zeile " + zeile + ": links vom '=' fehlt das MySQL-Wort.");
        }
        return w.toArray(new String[0]);
    }

    private void ladeAusnahmen(String inhalt) {
        int nr = 0;
        for (String roh : zeilen(inhalt)) {
            nr++;
            String z = roh.trim();
            if (z.isEmpty() || z.startsWith("#")) {
                continue;
            }
            try {
                ausnahmen.add(Pattern.compile(z, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.DOTALL));
            } catch (PatternSyntaxException e) {
                throw new IllegalStateException("ausnahmen.txt, Zeile " + nr + ": ungültiges Muster: " + z
                        + " (" + e.getDescription() + ")");
            }
        }
    }

    // ------------------------------------------------------------ Übersetzen

    /**
     * Übersetzt einen Befehl nach MySQL.
     *
     * @throws SQLSyntaxErrorException wenn das Endwort fehlt oder (je nach Einstellung)
     *                                 ein englisches Wort statt dem Dialekt-Wort verwendet wurde
     */
    public String uebersetzen(String sql) throws SQLException {
        if (sql == null) {
            return null;
        }
        List<Token> tokens = zerlegen(sql);

        int letztes = -1;
        for (int i = tokens.size() - 1; i >= 0; i--) {
            Token t = tokens.get(i);
            boolean unwichtig = t.art == Art.LEER || t.art == Art.KOMMENTAR
                    || (t.art == Art.SONST && t.text.equals(";"));
            if (!unwichtig) {
                letztes = i;
                break;
            }
        }
        if (letztes < 0) {
            return sql;   // leer oder nur Kommentare
        }

        // Jedes Endwort beendet einen Befehl. Ein Strichpunkt danach ist erlaubt, aber überflüssig.
        List<Integer> enden = new ArrayList<>();
        for (int i = 0; i < tokens.size(); i++) {
            Token t = tokens.get(i);
            if (t.art == Art.WORT && norm(t.text).equals(endwort) && !istNameMitPunkt(tokens, i)) {
                enden.add(i);
            }
        }

        if (enden.isEmpty() || enden.get(enden.size() - 1) != letztes) {
            if (enden.isEmpty()) {
                Pattern ausnahme = passendeAusnahme(tokens);
                if (ausnahme != null) {
                    Protokoll.schreibe("DURCHGELASSEN (Ausnahme " + ausnahme.pattern() + "): " + sql);
                    return sql;
                }
            }
            Protokoll.schreibe("BLOCKIERT (Endwort fehlt): " + sql);
            throw new SQLSyntaxErrorException(zufall(fehlerKeinEndwort), "42000");
        }

        if (enden.size() == 1) {
            tokens.remove(letztes);
            if (letztes > 0 && tokens.get(letztes - 1).art == Art.LEER) {
                tokens.remove(letztes - 1);
            }
            return woerter(tokens);
        }

        // Mehrere Befehle auf einmal: am Endwort trennen und für MySQL mit Strichpunkten verbinden.
        // (Der Treiber schaltet dafür bei der Verbindung allowMultiQueries ein.)
        StringBuilder alle = new StringBuilder(sql.length());
        int von = 0;
        for (int ende : enden) {
            List<Token> teil = new ArrayList<>(tokens.subList(von, ende));
            boolean wichtig = false;
            for (Token t : teil) {
                wichtig |= t.art != Art.LEER && t.art != Art.KOMMENTAR;
            }
            if (wichtig) {
                if (alle.length() > 0) {
                    alle.append(";\n");
                }
                alle.append(woerter(teil).trim());
            }
            von = ende + 1;
            while (von < tokens.size() && tokens.get(von).art == Art.LEER) {
                von++;
            }
            if (von < tokens.size() && tokens.get(von).text.equals(";")) {
                von++;
            }
        }
        return alle.toString();
    }

    /** Ersetzt in einem Befehl (ohne Endwort) die Dialekt-Wörter durch die MySQL-Wörter. */
    private String woerter(List<Token> tokens) throws SQLException {
        StringBuilder mysql = new StringBuilder();
        for (int i = 0; i < tokens.size(); i++) {
            Token t = tokens.get(i);
            if (t.art != Art.WORT || istNameMitPunkt(tokens, i)) {
                mysql.append(t.text);
                continue;
            }
            int[] bis = new int[1];
            Phrase p = finde(dialektPhrasen, tokens, i, bis);
            if (p != null) {
                mysql.append(p.englisch);
                i = bis[0];
                continue;
            }
            if (!englischErlaubt) {
                p = finde(englischPhrasen, tokens, i, bis);
                if (p != null) {
                    throw new SQLSyntaxErrorException(zufall(fehlerEnglisch)
                            .replace("{englisch}", p.englisch)
                            .replace("{dialekt}", p.dialekt), "42000");
                }
            }
            mysql.append(t.text);
        }
        return mysql.toString();
    }

    /** Ein Wort direkt vor oder nach einem Punkt (db.tabelle, t.spalte) ist ein Name, kein Schlüsselwort. */
    static boolean istNameMitPunkt(List<Token> tokens, int i) {
        return (i > 0 && tokens.get(i - 1).text.equals("."))
                || (i + 1 < tokens.size() && tokens.get(i + 1).text.equals("."));
    }

    /** Sucht die längste Wörterbuch-Phrase, die an Position i beginnt. bis[0] = Index des letzten Tokens. */
    private static Phrase finde(Map<String, List<Phrase>> phrasen, List<Token> tokens, int i, int[] bis) {
        List<Phrase> kandidaten = phrasen.get(norm(tokens.get(i).text));
        if (kandidaten == null) {
            return null;
        }
        for (Phrase p : kandidaten) {
            int j = i;
            boolean passt = true;
            for (int w = 1; w < p.woerter.length; w++) {
                if (j + 2 >= tokens.size() || tokens.get(j + 1).art != Art.LEER
                        || tokens.get(j + 2).art != Art.WORT
                        || !norm(tokens.get(j + 2).text).equals(p.woerter[w])) {
                    passt = false;
                    break;
                }
                j += 2;
            }
            if (passt) {
                bis[0] = j;
                return p;
            }
        }
        return null;
    }

    private Pattern passendeAusnahme(List<Token> tokens) {
        StringBuilder s = new StringBuilder();
        boolean begonnen = false;
        for (Token t : tokens) {
            if (!begonnen && (t.art == Art.LEER || t.art == Art.KOMMENTAR)) {
                continue;
            }
            begonnen = true;
            s.append(t.text);
        }
        String befehl = s.toString().trim();
        for (Pattern p : ausnahmen) {
            if (p.matcher(befehl).find()) {
                return p;
            }
        }
        return null;
    }

    // -------------------------------------------------------------- Zerlegen

    static String norm(String wort) {
        return wort.toUpperCase(Locale.ROOT);
    }

    private static boolean wortAnfang(char c) {
        return Character.isLetter(c) || c == '_' || c == '$';
    }

    private static boolean wortZeichen(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }

    /** Zerlegt SQL in Wörter, Leerraum, Kommentare, Texte ('...', "...", `...`) und Sonstiges. */
    static List<Token> zerlegen(String sql) {
        List<Token> tokens = new ArrayList<>();
        int n = sql.length();
        int i = 0;
        while (i < n) {
            int start = i;
            char c = sql.charAt(i);
            char danach = i + 1 < n ? sql.charAt(i + 1) : '\0';
            Art art;
            if (Character.isWhitespace(c)) {
                while (i < n && Character.isWhitespace(sql.charAt(i))) {
                    i++;
                }
                art = Art.LEER;
            } else if (c == '\'' || c == '"' || c == '`') {
                i++;
                while (i < n) {
                    char d = sql.charAt(i);
                    if (d == '\\' && c != '`') {
                        i += 2;
                    } else if (d == c) {
                        if (i + 1 < n && sql.charAt(i + 1) == c) {
                            i += 2;   // verdoppeltes Anführungszeichen
                        } else {
                            i++;
                            break;
                        }
                    } else {
                        i++;
                    }
                }
                i = Math.min(i, n);
                art = Art.TEXT;
            } else if (c == '#' || (c == '-' && danach == '-'
                    && (i + 2 >= n || Character.isWhitespace(sql.charAt(i + 2))))) {
                while (i < n && sql.charAt(i) != '\n') {
                    i++;
                }
                art = Art.KOMMENTAR;
            } else if (c == '/' && danach == '*') {
                int zu = sql.indexOf("*/", i + 2);
                i = zu < 0 ? n : zu + 2;
                art = Art.KOMMENTAR;
            } else if (c == '@') {
                // @variable, @@system.variable: nie übersetzen
                while (i < n && sql.charAt(i) == '@') {
                    i++;
                }
                while (i < n && (wortZeichen(sql.charAt(i)) || sql.charAt(i) == '.')) {
                    i++;
                }
                art = Art.SONST;
            } else if (wortAnfang(c)) {
                while (i < n && wortZeichen(sql.charAt(i))) {
                    i++;
                }
                art = Art.WORT;
            } else if (Character.isDigit(c)) {
                // Zahlen (1.5, 0x1F) und Namen, die mit einer Ziffer beginnen
                while (i < n && (wortZeichen(sql.charAt(i)) || sql.charAt(i) == '.')) {
                    i++;
                }
                art = Art.SONST;
            } else {
                i++;
                art = Art.SONST;
            }
            tokens.add(new Token(art, sql.substring(start, i)));
        }
        return tokens;
    }

    /**
     * Wo im Text stehen Dialekt-Wörter (und das Endwort)? Für das Einfärben im Editor.
     * Texte in Anführungszeichen, Kommentare und Namen mit Punkt (t.von) zählen nicht.
     *
     * @return Bereiche {anfang, ende}, aufsteigend sortiert, ende ist exklusiv
     */
    public List<int[]> dialektStellen(String text) {
        List<Token> tokens = zerlegen(text);
        List<int[]> stellen = new ArrayList<>();
        int[] anfang = new int[tokens.size() + 1];
        for (int i = 0; i < tokens.size(); i++) {
            anfang[i + 1] = anfang[i] + tokens.get(i).text.length();
        }
        int[] bis = new int[1];
        for (int i = 0; i < tokens.size(); i++) {
            Token t = tokens.get(i);
            if (t.art != Art.WORT || istNameMitPunkt(tokens, i)) {
                continue;
            }
            if (norm(t.text).equals(endwort)) {
                stellen.add(new int[] {anfang[i], anfang[i + 1]});
            } else if (finde(dialektPhrasen, tokens, i, bis) != null) {
                stellen.add(new int[] {anfang[i], anfang[bis[0] + 1]});
                i = bis[0];
            }
        }
        return stellen;
    }

    // ------------------------------------------------------------------ Infos

    public String endwort() {
        return endwort;
    }

    public boolean englischErlaubt() {
        return englischErlaubt;
    }

    public int anzahlUebersetzt() {
        return anzahlUebersetzt;
    }

    public List<String> ohneUebersetzung() {
        return ohneUebersetzung;
    }

    public int anzahlAusnahmen() {
        return ausnahmen.size();
    }
}
