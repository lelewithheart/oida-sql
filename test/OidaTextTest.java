import at.oida.datagrip.OidaText;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/**
 * Test für die Logik des DataGrip-Plugins: Welcher Befehl steht unter dem Cursor?
 * Optional: Pfade von OIDA-SQL-Dateien als Argumente, dann wird dort die Anzahl der Befehle ausgegeben.
 */
public class OidaTextTest {
    static int ok, kaputt;

    static void pruef(String was, boolean gut) {
        if (gut) { ok++; } else { kaputt++; System.out.println("KAPUTT: " + was); }
    }

    /** Cursor steht an der Stelle von | im Text; erwartet = der markierte Befehl (oder null). */
    static void bei(String mitCursor, String erwartet) {
        int cursor = mitCursor.indexOf('|');
        String text = mitCursor.replace("|", "");
        OidaText.Befehl b = OidaText.befehlBei(text, cursor);
        String ist = b == null ? null : text.substring(b.anfang, b.ende);
        pruef(mitCursor.replace("\n", "\\n") + "\n   erwartet: " + erwartet + "\n   ist:      " + ist,
                erwartet == null ? ist == null : erwartet.equals(ist));
    }

    public static void main(String[] args) throws Exception {
        String zwei = "-- erster\nGIB MA 1 OIDA;\n\n-- zweiter\nERSTÖN TABÖN x (\n  id ZOI\n) OIDA;\n";

        bei("GIB MA |1 OIDA;", "GIB MA 1 OIDA");
        bei("|GIB MA 1 OIDA;", "GIB MA 1 OIDA");
        bei("GIB MA 1 OIDA;|", "GIB MA 1 OIDA");
        bei("GIB MA 1 OIDA|", "GIB MA 1 OIDA");
        bei("GIB MA 1 OIDA;   -- fertig|", "GIB MA 1 OIDA");
        bei(zwei.replace("GIB MA", "GIB |MA"), "GIB MA 1 OIDA");
        bei(zwei.replace("-- erster", "-- er|ster"), "GIB MA 1 OIDA");
        bei(zwei.replace("-- zweiter", "-- zwei|ter"), "ERSTÖN TABÖN x (\n  id ZOI\n) OIDA");
        bei(zwei.replace("id ZOI", "id |ZOI"), "ERSTÖN TABÖN x (\n  id ZOI\n) OIDA");
        bei(zwei.replace(";\n\n", ";\n|\n"), "ERSTÖN TABÖN x (\n  id ZOI\n) OIDA");
        bei(zwei + "\n|", null);

        // OIDA in Texten und Kommentaren zählt nicht als Ende
        bei("GIB MA 'OIDA; x' VON |t OIDA;", "GIB MA 'OIDA; x' VON t OIDA");
        bei("GIB MA 1 -- OIDA;\n VON |t OIDA;", "GIB MA 1 -- OIDA;\n VON t OIDA");
        bei("GIB MA 1 /* OIDA; */ VON |t OIDA;", "GIB MA 1 /* OIDA; */ VON t OIDA");
        bei("GIB MA oida_spalte, `OIDA` VON |t OIDA;", "GIB MA oida_spalte, `OIDA` VON t OIDA");

        // Trigger: die Strichpunkte im Inneren trennen nicht, nur OIDA
        String trigger = "DELIMITER //\nERSTÖN AUSLÖSA t VURM EINEHAUN AUF x FIA JEDE ZEILN\nAUF GEHTS\n  SETZN NEW.a = 1;\nAUFHÖRN OIDA //\nDELIMITER ;\nGIB MA 2 OIDA;";
        bei(trigger.replace("NEW.a", "NEW|.a"),
                "ERSTÖN AUSLÖSA t VURM EINEHAUN AUF x FIA JEDE ZEILN\nAUF GEHTS\n  SETZN NEW.a = 1;\nAUFHÖRN OIDA");
        bei(trigger.replace("GIB MA 2", "GIB MA |2"), "GIB MA 2 OIDA");

        // Ohne Strichpunkt am Ende, Kleinschreibung
        bei("gib ma |1 oida", "gib ma 1 oida");
        bei("GIB MA 1 OIDA;\nGIB MA |2 OIDA", "GIB MA 2 OIDA");

        // Normales SQL ist keine OIDA-Datei
        pruef("normales SQL", !OidaText.istOida("SELECT * FROM t; -- OIDA\nSELECT 'OIDA';"));
        pruef("OIDA-Datei", OidaText.istOida("-- x\nGIB MA 1 OIDA;"));

        for (String datei : args) {
            String text = new String(Files.readAllBytes(Paths.get(datei)), StandardCharsets.UTF_8);
            List<OidaText.Befehl> alle = OidaText.befehle(text, false);
            System.out.println(alle.size() + " Befehle in " + Paths.get(datei).getFileName());
        }
        System.out.println(ok + " ok, " + kaputt + " kaputt");
        if (kaputt > 0) System.exit(1);
    }
}
