import at.oida.Uebersetzer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.SQLException;

/** Tests für den Übersetzer. Start: siehe README.md. */
public class UebersetzerTest {
    static int ok, kaputt;
    static Uebersetzer u;

    static String lies(String name) throws Exception {
        return new String(Files.readAllBytes(Paths.get("test/woerter/at/oida", name)), StandardCharsets.UTF_8);
    }

    static void gleich(String oida, String erwartet) {
        String ist;
        try { ist = u.uebersetzen(oida); } catch (SQLException e) { ist = "FEHLER: " + e.getMessage(); }
        if (erwartet.equals(ist)) { ok++; } else { kaputt++; System.out.println("KAPUTT: " + oida + "\n   erwartet: " + erwartet + "\n   ist:      " + ist); }
    }

    static void fehler(String oida, String teil) {
        try {
            String ist = u.uebersetzen(oida);
            kaputt++; System.out.println("KAPUTT: " + oida + "\n   erwartet Fehler mit: " + teil + "\n   ist:      " + ist);
        } catch (SQLException e) {
            if (e.getMessage().contains(teil)) { ok++; } else { kaputt++; System.out.println("KAPUTT: " + oida + "\n   falscher Fehler: " + e.getMessage()); }
        }
    }

    static void ladeFehler(String woerterbuch, String teil) {
        try { new Uebersetzer(woerterbuch, null, null); kaputt++; System.out.println("KAPUTT: kein Ladefehler bei: " + woerterbuch); }
        catch (IllegalStateException e) {
            if (e.getMessage().contains(teil)) { ok++; } else { kaputt++; System.out.println("KAPUTT: falscher Ladefehler: " + e.getMessage()); }
        }
    }

    public static void main(String[] a) throws Exception {
        u = new Uebersetzer(lies("woerterbuch.txt"), lies("einstellungen.txt"), lies("ausnahmen.txt"));

        // Grundfunktion
        gleich("ERSTÖN TABÖ leit (name VARCHAR(50)) OIDA", "CREATE TABLE leit (name VARCHAR(50))");
        gleich("erstön tabö leit (id INT) oida;", "CREATE TABLE leit (id INT);");
        gleich("GIMMA * VO leit WOS id = 1 OIDA", "SELECT * FROM leit WHERE id = 1");
        gleich("ZAG MA * VO leit OIDA", "SELECT * FROM leit");
        gleich("zag   ma * VO leit OIDA", "SELECT * FROM leit");
        gleich("SCHLEICH DI VO leit WOS id = 2 UND name = 'x' OIDA", "DELETE FROM leit WHERE id = 2 AND name = 'x'");
        gleich("GIMMA * VO leit SORTIER NOCH name OIDA", "SELECT * FROM leit ORDER BY name");
        gleich("GIMMA ZÖH(*) VO leit OIDA", "SELECT COUNT(*) FROM leit");
        gleich("GIMMA 1 OIDA  ;  ", "SELECT 1  ;  ");
        gleich("GIMMA 1 OIDA -- fertig", "SELECT 1 -- fertig");

        // Texte, Namen und Kommentare bleiben unverändert
        gleich("EINEHAUN EINE leit WERTE ('ERSTÖN vo OIDA') OIDA", "INSERT INTO leit VALUES ('ERSTÖN vo OIDA')");
        gleich("GIMMA `vo`, \"gimma\" VO leit OIDA", "SELECT `vo`, \"gimma\" FROM leit");
        gleich("GIMMA 'it''s vo', 'a\\'vo' VO leit OIDA", "SELECT 'it''s vo', 'a\\'vo' FROM leit");
        gleich("GIMMA 1 /* vo gimma */ VO leit OIDA", "SELECT 1 /* vo gimma */ FROM leit");
        gleich("GIMMA 1 # vo\n VO leit OIDA", "SELECT 1 # vo\n FROM leit");
        gleich("GIMMA vo.name, t.vo VO leit OIDA", "SELECT vo.name, t.vo FROM leit");
        gleich("GIMMA @vo, @@version OIDA", "SELECT @vo, @@version");
        gleich("GIMMA vorname VO leit OIDA", "SELECT vorname FROM leit");

        // Endwort fehlt
        fehler("GIMMA * VO leit", "wo is des OIDA");
        fehler("GIMMA 'OIDA'", "wo is des OIDA");
        fehler("GIMMA * VO leit -- OIDA", "wo is des OIDA");
        fehler("ERSTÖN TABÖ oida (id INT)", "wo is des OIDA");

        // Englisch ist verboten, wenn es eine Übersetzung gibt
        fehler("CREATE TABLE x (id INT) OIDA", "ERSTÖN, ned CREATE");
        fehler("GIMMA * FROM leit OIDA", "VO, ned FROM");
        fehler("GIMMA * VO leit order by name OIDA", "SORTIER NOCH, ned ORDER BY");
        gleich("GIMMA `select`, 'CREATE' VO leit OIDA", "SELECT `select`, 'CREATE' FROM leit");
        gleich("GIMMA t.select VO t OIDA", "SELECT t.select FROM t");

        // Leeres und Kommentare gehen durch
        gleich("", "");
        gleich("  -- nur ein Kommentar", "  -- nur ein Kommentar");

        // DataGrip-interne Befehle (Ausnahmen) gehen ohne Endwort durch
        gleich("SHOW TABLES", "SHOW TABLES");
        gleich("/* ApplicationName=DataGrip */ select database()", "/* ApplicationName=DataGrip */ select database()");
        gleich("SET autocommit=1", "SET autocommit=1");
        gleich("select table_name from information_schema.tables where table_schema = 'x'", "select table_name from information_schema.tables where table_schema = 'x'");
        gleich("SELECT t.* FROM test.leit t LIMIT 501", "SELECT t.* FROM test.leit t LIMIT 501");
        gleich("UPDATE test.leit t SET t.name = 'x' WHERE t.id = 1", "UPDATE test.leit t SET t.name = 'x' WHERE t.id = 1");
        gleich("INSERT INTO test.leit (name) VALUES ('x')", "INSERT INTO test.leit (name) VALUES ('x')");
        gleich("DELETE FROM test.leit WHERE id = 1", "DELETE FROM test.leit WHERE id = 1");
        fehler("INSERT INTO leit VALUES ('x')", "wo is des OIDA");
        fehler("SELECT * FROM leit", "wo is des OIDA");

        // Einstellungen
        Uebersetzer locker = new Uebersetzer("CREATE = ERSTÖN", "englisch_erlaubt = ja\nendwort = HEAST\nfehler_kein_endwort = Na!", null);
        Uebersetzer alt = u; u = locker;
        gleich("CREATE TABLE x (id INT) HEAST", "CREATE TABLE x (id INT)");
        gleich("ERSTÖN TABLE x (id INT) heast", "CREATE TABLE x (id INT)");
        fehler("ERSTÖN TABLE x (id INT) OIDA", "Na!");
        fehler("SHOW TABLES", "Na!");
        u = alt;

        // Steht rechts auch das englische Wort, bleibt es erlaubt
        u = new Uebersetzer("XOR = XOR, ENTWEDA ODA\nOR = ODA\nIS = IS", null, null);
        gleich("a XOR b ENTWEDA ODA c ODA d IS e OIDA", "a XOR b XOR c OR d IS e");
        fehler("a OR b OIDA", "ODA, ned OR");

        // Mehrere Fehlermeldungen mit | : es kommt immer eine davon
        u = new Uebersetzer("CREATE = ERSTÖN", "fehler_kein_endwort = Eins! | Zwei!", null);
        boolean eins = false, zwei = false;
        for (int i = 0; i < 200; i++) {
            try { u.uebersetzen("ERSTÖN x"); } catch (SQLException e) {
                eins |= e.getMessage().equals("Eins!");
                zwei |= e.getMessage().equals("Zwei!");
            }
        }
        if (eins && zwei) { ok++; } else { kaputt++; System.out.println("KAPUTT: Zufalls-Meldungen " + eins + "/" + zwei); }
        u = alt;

        // Fehler im Wörterbuch werden beim Laden gemeldet
        ladeFehler("AS = OIS\nALL = OIS", "'OIS' steht schon bei AS");
        ladeFehler("SELECT = GIB'S", "darf nur aus Wörtern bestehen");
        ladeFehler("SELECT GIMMA", "'=' fehlt");
        ladeFehler("SELECT = OIDA", "ist schon das Endwort");
        new Uebersetzer("﻿IN = IN\nSELECT = gimma, GIMMA", null, null); ok++;

        System.out.println(ok + " ok, " + kaputt + " kaputt");
        if (kaputt > 0) System.exit(1);
    }
}
