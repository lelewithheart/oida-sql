package at.oida;

import java.io.BufferedReader;
import java.io.Console;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Kleines Kommandozeilen-Werkzeug:
 *
 *   java -jar oida-sql.jar --pruefen                    Wörterbuch prüfen und Übersicht ausgeben
 *   java -jar oida-sql.jar                              Befehle eintippen und die Übersetzung sehen
 *   java -jar oida-sql.jar --umschreiben EIN AUS ...    MySQL-Skript EIN nach OIDA-SQL umschreiben, Ergebnis in AUS
 *                                                       (mehrere Paare EIN AUS hintereinander möglich)
 */
public final class Werkzeug {

    public static void main(String[] args) throws Exception {
        Uebersetzer u;
        try {
            u = Uebersetzer.ausJar();
        } catch (IllegalStateException e) {
            System.out.println("FEHLER: " + e.getMessage());
            System.exit(1);
            return;
        }

        if (args.length > 0 && args[0].equals("--umschreiben")) {
            System.exit(umschreiben(u, args) ? 0 : 1);
            return;
        }

        System.out.println("Endwort:           " + u.endwort());
        System.out.println("Englisch erlaubt:  " + (u.englischErlaubt() ? "ja" : "nein"));
        System.out.println("Uebersetzt:        " + u.anzahlUebersetzt() + " Woerter");
        System.out.println("Noch englisch:     " + u.ohneUebersetzung().size() + " Woerter");
        System.out.println("Ausnahmen:         " + u.anzahlAusnahmen() + " Muster");

        if (args.length > 0 && args[0].equals("--pruefen")) {
            return;
        }

        System.out.println();
        System.out.println("Befehl eintippen, Enter. Leere Zeile = Ende.");
        Console konsole = System.console();
        BufferedReader ein = konsole == null
                ? new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)) : null;
        while (true) {
            String zeile = konsole != null ? konsole.readLine("oida> ") : ein.readLine();
            if (zeile == null || zeile.trim().isEmpty()) {
                return;
            }
            try {
                System.out.println("  -> " + u.uebersetzen(zeile));
            } catch (SQLException e) {
                System.out.println("  !! " + e.getMessage());
            }
        }
    }

    /** Schreibt MySQL-Skripte nach OIDA-SQL um. Liefert false, wenn bei einer Gegenprobe etwas nicht gestimmt hat. */
    private static boolean umschreiben(Uebersetzer u, String[] args) throws Exception {
        if (args.length < 3 || args.length % 2 == 0) {
            System.out.println("Aufruf: --umschreiben EIN.sql AUS.sql [EIN2.sql AUS2.sql ...]");
            return false;
        }
        boolean allesGut = true;
        int befehle = 0;
        Map<String, Integer> englisch = new TreeMap<>();
        Map<String, Integer> backticks = new TreeMap<>();
        for (int i = 1; i + 1 < args.length; i += 2) {
            Path ein = Paths.get(args[i]);
            Path aus = Paths.get(args[i + 1]);
            String mysql = new String(Files.readAllBytes(ein), StandardCharsets.UTF_8);
            Rueckwaerts.Ergebnis e = Rueckwaerts.umschreiben(u, mysql);
            if (aus.getParent() != null) {
                Files.createDirectories(aus.getParent());
            }
            Files.write(aus, e.text.toString().getBytes(StandardCharsets.UTF_8));
            befehle += e.befehle;
            e.englischGeblieben.forEach((w, n) -> englisch.merge(w, n, Integer::sum));
            e.inBackticks.forEach((w, n) -> backticks.merge(w, n, Integer::sum));
            System.out.println((e.probleme.isEmpty() ? "ok      " : "KAPUTT  ") + ein.getFileName() + ": "
                    + e.befehle + " Befehle");
            for (String p : e.probleme) {
                allesGut = false;
                System.out.println("    " + p);
            }
        }
        System.out.println();
        System.out.println(befehle + " Befehle umgeschrieben.");
        if (!backticks.isEmpty()) {
            System.out.println("Namen in Backticks gesetzt (heissen wie ein Dialekt-Wort): " + backticks);
        }
        List<Map.Entry<String, Integer>> haeufig = new ArrayList<>(englisch.entrySet());
        haeufig.sort((a, b) -> b.getValue() - a.getValue());
        StringBuilder s = new StringBuilder();
        for (Map.Entry<String, Integer> w : haeufig) {
            s.append(w.getKey()).append('(').append(w.getValue()).append(") ");
        }
        System.out.println("Nicht im Woerterbuch (Namen und englisch gebliebene Woerter): " + s.toString().trim());
        return allesGut;
    }
}
