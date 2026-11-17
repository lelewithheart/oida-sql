import at.oida.Uebersetzer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Führt beispiele.sql mit der echten oida-sql.jar (also dem echten Wörterbuch) gegen MySQL aus.
 * Erwartet OIDA_TEST_URL (host:port/db), OIDA_TEST_USER, OIDA_TEST_PW.
 */
public class BeispieleTest {
    public static void main(String[] a) throws Exception {
        String datei = new String(Files.readAllBytes(Paths.get("beispiele.sql")), StandardCharsets.UTF_8);
        List<String> befehle = new ArrayList<>();
        StringBuilder b = new StringBuilder();
        for (String zeile : datei.split("\r?\n")) {
            b.append(zeile).append('\n');
            if (zeile.trim().endsWith("OIDA;")) {
                befehle.add(b.toString().trim());
                b.setLength(0);
            }
        }

        Properties p = new Properties();
        p.setProperty("user", System.getenv("OIDA_TEST_USER"));
        p.setProperty("password", System.getenv("OIDA_TEST_PW"));
        Class.forName("at.oida.OidaDriver");
        Uebersetzer u = Uebersetzer.ausJar();

        int ok = 0, kaputt = 0;
        try (Connection c = DriverManager.getConnection("jdbc:oida://" + System.getenv("OIDA_TEST_URL"), p);
             Statement s = c.createStatement()) {
            for (String befehl : befehle) {
                String mysql;
                try {
                    mysql = u.uebersetzen(befehl);
                } catch (SQLException e) {
                    mysql = "(nicht uebersetzbar)";
                }
                // Kommentarzeilen nur für die Ausgabe weglassen
                String kurz = mysql.replaceAll("(?m)^--.*$", "").replaceAll("\\s+", " ").trim();
                try {
                    s.execute(befehl);
                    ok++;
                    System.out.println("ok      " + kurz);
                } catch (SQLException e) {
                    kaputt++;
                    System.out.println("KAPUTT  " + kurz + "\n        " + e.getMessage());
                }
            }

            // Die verzierten MySQL-Fehler
            try {
                s.execute("GIB MA * VON gibtsned OIDA");
                kaputt++;
                System.out.println("KAPUTT  kein Fehler bei unbekannter Tabelle");
            } catch (SQLException e) {
                boolean gut = e.getMessage().contains("MySQL sogt:") && e.getMessage().contains("gibtsned")
                        && e.getErrorCode() == 1146;
                if (gut) { ok++; } else { kaputt++; }
                System.out.println((gut ? "ok      " : "KAPUTT  ") + "Fehlertext: " + e.getMessage());
            }
        }
        System.out.println(ok + " ok, " + kaputt + " kaputt");
        if (kaputt > 0) System.exit(1);
    }
}
