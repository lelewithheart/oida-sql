import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Führt OIDA-SQL-Skripte über den OIDA-Treiber gegen MySQL aus und zählt, was durchgeht.
 *
 * Aufruf: SkriptTest [schema=datei.sql | datei.sql] ...
 *   "schema=" wählt vor der Datei eine Datenbank aus (für Skripte ohne eigenes USE).
 * Erwartet OIDA_TEST_URL (host:port), OIDA_TEST_USER, OIDA_TEST_PW.
 *
 * Fehler, die MySQL selbst meldet, stehen unter "MySQL-Fehler" (das Original-SQL hat dort
 * dasselbe Problem). Fehler vom Übersetzer stehen unter "Dialekt-Fehler".
 */
public class SkriptTest {
    // Ein Befehl hört mit dem Endwort und danach ; oder // oder $$ auf.
    private static final Pattern ENDE = Pattern.compile("\\bOIDA\\s*(;|//|\\$\\$)");

    public static void main(String[] args) throws Exception {
        Properties p = new Properties();
        p.setProperty("user", System.getenv("OIDA_TEST_USER"));
        p.setProperty("password", System.getenv("OIDA_TEST_PW"));
        p.setProperty("allowPublicKeyRetrieval", "true");
        Class.forName("at.oida.OidaDriver");
        String url = "jdbc:oida://" + System.getenv("OIDA_TEST_URL") + "/";

        int dialektFehlerGesamt = 0;
        for (String arg : args) {
            String schema = null;
            String datei = arg;
            int gleich = arg.indexOf('=');
            if (gleich > 0) {
                schema = arg.substring(0, gleich);
                datei = arg.substring(gleich + 1);
            }
            String text = new String(Files.readAllBytes(Paths.get(datei)), StandardCharsets.UTF_8)
                    .replaceAll("(?m)^DELIMITER.*$", "");
            int ok = 0, mysqlFehler = 0, dialektFehler = 0;
            Map<String, String> meldungen = new LinkedHashMap<>();
            try (Connection c = DriverManager.getConnection(url, p); Statement s = c.createStatement()) {
                if (schema != null) {
                    s.execute("NIMM " + schema + " OIDA");
                }
                Matcher m = ENDE.matcher(text);
                int von = 0;
                while (m.find()) {
                    String befehl = text.substring(von, m.start(1));
                    von = m.end();
                    try {
                        s.execute(befehl);
                        ok++;
                    } catch (SQLException e) {
                        boolean vonMysql = e.getErrorCode() != 0;
                        if (vonMysql) { mysqlFehler++; } else { dialektFehler++; }
                        String kurz = befehl.replaceAll("(?m)^\\s*--.*$", "").replaceAll("\\s+", " ").trim();
                        meldungen.put((vonMysql ? "MySQL-Fehler:   " : "DIALEKT-FEHLER: ") + e.getMessage(),
                                kurz.length() > 110 ? kurz.substring(0, 110) + " ..." : kurz);
                    }
                }
            }
            dialektFehlerGesamt += dialektFehler;
            System.out.println(datei + ": " + ok + " ok, " + mysqlFehler + " MySQL-Fehler, " + dialektFehler + " Dialekt-Fehler");
            int gezeigt = 0;
            for (Map.Entry<String, String> e : meldungen.entrySet()) {
                if (gezeigt++ == 6) { System.out.println("    ..."); break; }
                System.out.println("    " + e.getKey() + "\n        bei: " + e.getValue());
            }
        }
        if (dialektFehlerGesamt > 0) System.exit(1);
    }
}
