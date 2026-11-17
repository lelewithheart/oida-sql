import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Test gegen eine echte MySQL-Datenbank mit der fertigen oida-sql.jar (echtes Wörterbuch):
 * mehrere Befehle in einem Aufruf, am Endwort getrennt, ganz ohne Strichpunkt.
 * Erwartet OIDA_TEST_URL (host:port/db), OIDA_TEST_USER, OIDA_TEST_PW.
 */
public class MehrfachTest {
    static int ok, kaputt;

    static void pruef(String was, boolean gut) {
        if (gut) { ok++; System.out.println("ok      " + was); } else { kaputt++; System.out.println("KAPUTT  " + was); }
    }

    /** Führt alles aus und sammelt je Ergebnis entweder "Zeilen:n" oder "Geändert:n". */
    static List<String> alle(Statement s, String sql) throws SQLException {
        List<String> ergebnisse = new ArrayList<>();
        boolean abfrage = s.execute(sql);
        while (true) {
            if (abfrage) {
                try (ResultSet r = s.getResultSet()) {
                    int n = 0;
                    String erstes = null;
                    while (r.next()) { if (n++ == 0) erstes = r.getString(1); }
                    ergebnisse.add("Zeilen:" + n + (erstes != null ? "(" + erstes + ")" : ""));
                }
            } else if (s.getUpdateCount() < 0) {
                break;
            } else {
                ergebnisse.add("Geaendert:" + s.getUpdateCount());
            }
            abfrage = s.getMoreResults();
        }
        return ergebnisse;
    }

    public static void main(String[] a) throws Exception {
        Properties p = new Properties();
        p.setProperty("user", System.getenv("OIDA_TEST_USER"));
        p.setProperty("password", System.getenv("OIDA_TEST_PW"));
        Class.forName("at.oida.OidaDriver");
        try (Connection c = DriverManager.getConnection("jdbc:oida://" + System.getenv("OIDA_TEST_URL"), p);
             Statement s = c.createStatement()) {

            List<String> e = alle(s, "GUSCH DI TABÖN WENNS DES GIBT m OIDA\n"
                    + "ERSTÖN TABÖN m (id ZOI) OIDA\n"
                    + "EINEHAUN IN m WERTE (1), (2), (3) OIDA\n"
                    + "GIB MA WIAVÜ(*) VON m OIDA\n"
                    + "SCHLEICH DI VON m WO id = 1 OIDA\n"
                    + "GIB MA id VON m DA REIH NOCH id OIDA");
            pruef("sechs Befehle ohne Strichpunkt: " + e,
                    e.toString().equals("[Geaendert:0, Geaendert:0, Geaendert:3, Zeilen:1(3), Geaendert:1, Zeilen:2(2)]"));

            e = alle(s, "GIB MA 1 OIDA; GIB MA 2 OIDA;");
            pruef("mit Strichpunkt geht es weiterhin: " + e, e.toString().equals("[Zeilen:1(1), Zeilen:1(2)]"));

            e = alle(s, "GIB MA 7 OIDA");
            pruef("ein einzelner Befehl ohne Strichpunkt: " + e, e.toString().equals("[Zeilen:1(7)]"));

            String fehler = null;
            try { alle(s, "GIB MA 1 OIDA GIB MA 2"); } catch (SQLException x) { fehler = x.getMessage(); }
            pruef("Rest ohne Endwort wird abgelehnt: " + fehler, fehler != null && fehler.contains("OIDA"));

            fehler = null;
            try { alle(s, "GIB MA 1 OIDA GIB MA * VON gibtsned OIDA"); } catch (SQLException x) { fehler = x.getMessage(); }
            pruef("Fehler im zweiten Befehl kommt an: " + fehler, fehler != null && fehler.contains("gibtsned"));

            // Die ganze Beispiel-Datei in einem einzigen Aufruf
            String datei = new String(Files.readAllBytes(Paths.get("beispiele.sql")), StandardCharsets.UTF_8);
            e = alle(s, datei);
            pruef("beispiele.sql als ein Block: " + e.size() + " Ergebnisse", e.size() >= 40);

            // Dieselbe Datei ganz ohne Strichpunkte
            e = alle(s, datei.replace("OIDA;", "OIDA"));
            pruef("beispiele.sql ohne Strichpunkte: " + e.size() + " Ergebnisse", e.size() >= 40);

            s.execute("GUSCH DI TABÖN m OIDA");
        }
        System.out.println(ok + " ok, " + kaputt + " kaputt");
        if (kaputt > 0) System.exit(1);
    }
}
