package at.oida;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Optionales Protokoll aller Befehle, die ohne Endwort angekommen sind.
 * Ist nur aktiv, wenn eine Datei eingestellt wurde (Verbindungs-Eigenschaft
 * "oidaLog" oder Java-Option -Doida.log=...).
 */
final class Protokoll {

    private static final DateTimeFormatter ZEIT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static volatile Path datei;

    private Protokoll() {
    }

    static void einschalten(String pfad) {
        datei = (pfad == null || pfad.trim().isEmpty()) ? null : Paths.get(pfad.trim());
    }

    static synchronized void schreibe(String zeile) {
        Path d = datei;
        if (d == null) {
            return;
        }
        String text = LocalDateTime.now().format(ZEIT) + "  " + zeile.replaceAll("\\s+", " ") + System.lineSeparator();
        try {
            Files.write(d, text.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException | RuntimeException e) {
            // Das Protokoll darf nie einen Befehl kaputt machen.
        }
    }
}
