import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

/** Test gegen eine echte MySQL-Datenbank. Erwartet OIDA_TEST_URL (host:port/db), OIDA_TEST_USER, OIDA_TEST_PW. */
public class DatenbankTest {
    static int ok, kaputt;

    static void pruef(String was, boolean gut) {
        if (gut) { ok++; System.out.println("ok      " + was); } else { kaputt++; System.out.println("KAPUTT  " + was); }
    }

    static String fehler(Statement s, String sql) {
        try { s.execute(sql); return null; } catch (SQLException e) { return e.getMessage(); }
    }

    public static void main(String[] a) throws Exception {
        String ziel = System.getenv("OIDA_TEST_URL");
        Path log = Files.createTempFile("oida", ".log");
        Properties p = new Properties();
        p.setProperty("user", System.getenv("OIDA_TEST_USER"));
        p.setProperty("password", System.getenv("OIDA_TEST_PW"));
        p.setProperty("oidaLog", log.toString());

        Class.forName("at.oida.OidaDriver");
        try (Connection c = DriverManager.getConnection("jdbc:oida://" + ziel, p); Statement s = c.createStatement()) {
            pruef("Verbindung ist die Huelle", c.toString().startsWith("OIDA-SQL["));
            pruef("Statement.getConnection() liefert die Huelle", s.getConnection() == c);
            pruef("Metadaten gehen", c.getMetaData().getDatabaseProductName().equals("MySQL"));

            s.execute("WEGHAUN TABÖ IF EXISTS leit OIDA");
            s.execute("ERSTÖN TABÖ leit (id INT PRIMARY KEY AUTO_INCREMENT, name VARCHAR(50)) OIDA");
            pruef("EINEHAUN", s.executeUpdate("EINEHAUN EINE leit (name) WERTE ('Franz'), ('Vo OIDA') OIDA;") == 2);

            try (PreparedStatement ps = c.prepareStatement("GIMMA name VO leit WOS id = ? OIDA")) {
                ps.setInt(1, 2);
                try (ResultSet r = ps.executeQuery()) {
                    pruef("PreparedStatement + Text bleibt unveraendert", r.next() && r.getString(1).equals("Vo OIDA"));
                }
            }
            try (ResultSet r = s.executeQuery("ZAG MA ZÖH(*) VO leit OIDA")) {
                pruef("ZAG MA ZÖH(*)", r.next() && r.getInt(1) == 2);
            }
            s.addBatch("EINEHAUN EINE leit (name) WERTE ('Sepp') OIDA");
            s.addBatch("SCHLEICH DI VO leit WOS name = 'Franz' OIDA");
            pruef("Batch", s.executeBatch().length == 2);

            String f = fehler(s, "GIMMA * VO leit");
            pruef("ohne OIDA: " + f, f != null && f.contains("wo is des OIDA"));
            f = fehler(s, "SELECT * FROM leit OIDA");
            pruef("englisch: " + f, f != null && f.contains("ned SELECT"));
            f = fehler(s, "GIMMA * VO gibtsned OIDA");
            pruef("echter MySQL-Fehler kommt durch: " + f, f != null && f.contains("gibtsned"));

            // So etwas schickt DataGrip selbst
            pruef("SHOW TABLES ohne OIDA", fehler(s, "SHOW TABLES") == null);
            pruef("information_schema ohne OIDA", fehler(s, "select table_name from information_schema.tables where table_schema = database()") == null);
            pruef("Tabellenansicht ohne OIDA", fehler(s, "SELECT t.* FROM leit t LIMIT 501") == null);

            s.execute("WEGHAUN TABÖ leit OIDA");
        }

        // DataGrip-Weg: Treiberklasse direkt, ganz normale jdbc:mysql-URL
        Driver d = (Driver) Class.forName("at.oida.OidaDriver").getDeclaredConstructor().newInstance();
        try (Connection c = d.connect("jdbc:mysql://" + ziel, p); Statement s = c.createStatement()) {
            String f = fehler(s, "SELECT 2");
            pruef("jdbc:mysql-URL geht auch durch den Uebersetzer: " + f, f != null && f.contains("wo is des OIDA"));
            try (ResultSet r = s.executeQuery("GIMMA 2 OIDA")) { pruef("GIMMA 2 OIDA", r.next() && r.getInt(1) == 2); }
        }

        String protokoll = new String(Files.readAllBytes(log), "UTF-8");
        pruef("Protokoll: BLOCKIERT", protokoll.contains("BLOCKIERT (Endwort fehlt): GIMMA * VO leit"));
        pruef("Protokoll: DURCHGELASSEN", protokoll.contains("DURCHGELASSEN (Ausnahme ^SHOW\\b): SHOW TABLES"));
        Files.delete(log);

        System.out.println(ok + " ok, " + kaputt + " kaputt");
        if (kaputt > 0) System.exit(1);
    }
}
