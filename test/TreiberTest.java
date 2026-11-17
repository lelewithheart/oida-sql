import at.oida.datagrip.Treiber;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Driver;
import java.net.URL;
import java.net.URLClassLoader;

/**
 * Test: Das Plugin legt den eingepackten Treiber an den festen Platz und ersetzt eine alte Datei.
 * Start mit der fertigen Plugin-Datei auf dem Klassenpfad:
 *   java -cp oida-sql-datagrip-plugin.jar test/TreiberTest.java
 */
public class TreiberTest {
    static int ok, kaputt;

    static void pruef(String was, boolean gut) {
        if (gut) { ok++; System.out.println("ok      " + was); } else { kaputt++; System.out.println("KAPUTT  " + was); }
    }

    public static void main(String[] a) throws Exception {
        Method bereitstellen = Treiber.class.getDeclaredMethod("bereitstellen", Path.class);
        bereitstellen.setAccessible(true);
        Path ordner = Files.createTempDirectory("oida-treiber");
        Path ziel = ordner.resolve(".oida-sql").resolve("oida-sql.jar");

        String m1 = (String) bereitstellen.invoke(null, ziel);
        pruef("neu angelegt: " + m1.substring(0, m1.indexOf(':')), m1.startsWith("ANGELEGT") && Files.size(ziel) > 10000);

        String m2 = (String) bereitstellen.invoke(null, ziel);
        pruef("zweiter Aufruf aendert nichts: " + m2.substring(0, m2.indexOf(':')), m2.startsWith("AKTUELL"));

        Files.write(ziel, "alte Version".getBytes());
        String m3 = (String) bereitstellen.invoke(null, ziel);
        pruef("alte Datei wird ersetzt: " + m3.substring(0, m3.indexOf(':')), m3.startsWith("AKTUALISIERT") && Files.size(ziel) > 10000);

        // Die abgelegte Datei ist ein richtiger Treiber
        try (URLClassLoader lader = new URLClassLoader(new URL[] {ziel.toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
            Driver d = (Driver) Class.forName("at.oida.OidaDriver", true, lader).getDeclaredConstructor().newInstance();
            pruef("abgelegte Datei enthaelt den Treiber", d.acceptsURL("jdbc:mysql://localhost/x"));
        }

        pruef("fester Platz liegt im Benutzerordner", Treiber.ziel().startsWith(System.getProperty("user.home"))
                && Treiber.ziel().endsWith(Path.of(".oida-sql", "oida-sql.jar")));

        Files.delete(ziel);
        Files.delete(ziel.getParent());
        Files.delete(ordner);
        System.out.println(ok + " ok, " + kaputt + " kaputt");
        if (kaputt > 0) System.exit(1);
    }
}
