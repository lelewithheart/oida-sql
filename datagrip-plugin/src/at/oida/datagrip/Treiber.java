package at.oida.datagrip;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;

/**
 * Das Plugin bringt den JDBC-Treiber (oida-sql.jar) mit und legt ihn an einen festen Platz:
 *
 *     Benutzerordner/.oida-sql/oida-sql.jar
 *
 * In DataGrip zeigt der Treiber "OIDA-SQL" auf genau diese Datei. Kommt ein Plugin-Update,
 * wird die Datei beim nächsten Start ersetzt - so bekommt man mit dem Plugin auch immer
 * den neuen Treiber und das neue Wörterbuch.
 *
 * Diese Klasse hat absichtlich nichts mit DataGrip zu tun, damit man sie alleine testen kann.
 */
public final class Treiber {

    private static final String EINGEPACKT = "oida-sql.jar";   // liegt neben dieser Klasse in der Plugin-Datei
    private static volatile boolean erledigt;

    private Treiber() {
    }

    /** Der feste Platz für die Treiber-Datei. */
    public static Path ziel() {
        return Paths.get(System.getProperty("user.home"), ".oida-sql", "oida-sql.jar");
    }

    /**
     * Legt den Treiber bereit, falls das in dieser Sitzung noch nicht geklappt hat.
     *
     * @return kurze Meldung fürs Protokoll, oder null wenn nichts mehr zu tun war
     */
    public static String einmal() {
        if (erledigt) {
            return null;
        }
        synchronized (Treiber.class) {
            if (erledigt) {
                return null;
            }
            String meldung = bereitstellen(ziel());
            // Bei einer gesperrten Datei später nochmal versuchen, sonst ist es für diese Sitzung erledigt.
            erledigt = !meldung.startsWith("GESPERRT");
            return meldung;
        }
    }

    /** Schreibt den eingepackten Treiber nach ziel, wenn dort noch nichts oder etwas anderes liegt. */
    static String bereitstellen(Path ziel) {
        byte[] neu;
        try (InputStream in = Treiber.class.getResourceAsStream(EINGEPACKT)) {
            if (in == null) {
                return "FEHLT: In der Plugin-Datei ist kein Treiber eingepackt.";
            }
            neu = in.readAllBytes();
        } catch (IOException e) {
            return "FEHLER: Der eingepackte Treiber kann nicht gelesen werden: " + e;
        }
        try {
            if (Files.isRegularFile(ziel) && Arrays.equals(Files.readAllBytes(ziel), neu)) {
                return "AKTUELL: " + ziel;
            }
            boolean gabEsSchon = Files.exists(ziel);
            Files.createDirectories(ziel.getParent());
            Path zwischen = ziel.resolveSibling(ziel.getFileName() + ".neu");
            Files.write(zwischen, neu);
            Files.move(zwischen, ziel, StandardCopyOption.REPLACE_EXISTING);
            return (gabEsSchon ? "AKTUALISIERT: " : "ANGELEGT: ") + ziel;
        } catch (IOException e) {
            // Unter Windows ist die Datei gesperrt, solange eine Verbindung über den Treiber offen ist.
            return "GESPERRT: " + ziel + " kann gerade nicht ersetzt werden (" + e + ")";
        }
    }
}
