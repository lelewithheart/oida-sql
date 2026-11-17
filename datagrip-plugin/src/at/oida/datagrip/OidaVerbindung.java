package at.oida.datagrip;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Findet heraus, ob eine Datei oder Konsole an einer OIDA-SQL-Verbindung hängt
 * (also an einer Datenquelle, deren Treiberklasse at.oida.OidaDriver ist).
 *
 * Das braucht DataGrips eigene Klassen. Sie werden absichtlich über Reflection angesprochen:
 * Ändert JetBrains dort etwas, fällt nur diese Erkennung aus, und das Plugin erkennt
 * OIDA-Dateien weiter daran, dass ein Befehl mit dem Endwort aufhört.
 */
final class OidaVerbindung {

    private static final Logger LOG = Logger.getInstance(OidaVerbindung.class);
    private static final String TREIBER = "at.oida.OidaDriver";
    private static final AtomicBoolean GEMELDET = new AtomicBoolean();

    private OidaVerbindung() {
    }

    static boolean istOida(PsiFile datei) {
        try {
            VirtualFile vf = datei.getViewProvider().getVirtualFile();
            Project projekt = datei.getProject();
            ClassLoader[] lader = {
                    OidaVerbindung.class.getClassLoader(),
                    datei.getLanguage().getClass().getClassLoader(),
                    datei.getClass().getClassLoader(),
            };
            String treiber = null;
            String weg = null;
            for (ClassLoader l : lader) {
                treiber = ueberKonsole(l, projekt, vf);
                if (treiber != null) {
                    weg = "Konsole";
                    break;
                }
                treiber = ueberDatenquelle(l, projekt, vf);
                if (treiber != null) {
                    weg = "Datenquelle";
                    break;
                }
            }
            if (treiber != null && GEMELDET.compareAndSet(false, true)) {
                LOG.info("OIDA-SQL: Verbindung erkannt ueber " + weg + ", Treiber " + treiber);
            }
            return TREIBER.equals(treiber);
        } catch (Throwable t) {
            if (GEMELDET.compareAndSet(false, true)) {
                LOG.info("OIDA-SQL: Verbindung laesst sich nicht erkennen (" + t + "), es zaehlt nur das Endwort im Text");
            }
            return false;
        }
    }

    /** Datei mit laufender Sitzung: JdbcConsoleProvider.getValidConsole(projekt, datei).getDataSource().getDriverClass() */
    private static String ueberKonsole(ClassLoader lader, Project projekt, VirtualFile vf) {
        try {
            Class<?> anbieter = Class.forName("com.intellij.database.console.JdbcConsoleProvider", true, lader);
            Object konsole = rufe(anbieter, null, "getValidConsole", projekt, vf);
            Object quelle = konsole == null ? null : rufe(konsole.getClass(), konsole, "getDataSource");
            return quelle == null ? null : (String) rufe(quelle.getClass(), quelle, "getDriverClass");
        } catch (Throwable t) {
            return null;
        }
    }

    /** Konsole ohne laufende Sitzung: DbImplUtil.findDataSource(projekt, datei) und davon die lokale Datenquelle. */
    private static String ueberDatenquelle(ClassLoader lader, Project projekt, VirtualFile vf) {
        try {
            Class<?> helfer = Class.forName("com.intellij.database.util.DbImplUtil", true, lader);
            Object quelle = rufe(helfer, null, "findDataSource", projekt, vf);
            Object lokal = quelle == null ? null : rufe(helfer, null, "getMaybeLocalDataSource", quelle);
            return lokal == null ? null : (String) rufe(lokal.getClass(), lokal, "getDriverClass");
        } catch (Throwable t) {
            return null;
        }
    }

    /** Ruft die erste öffentliche Methode mit diesem Namen auf, zu der die Argumente passen. */
    private static Object rufe(Class<?> klasse, Object ziel, String name, Object... args) throws Exception {
        for (Method m : klasse.getMethods()) {
            if (!m.getName().equals(name) || m.getParameterCount() != args.length) {
                continue;
            }
            Class<?>[] typen = m.getParameterTypes();
            boolean passt = true;
            for (int i = 0; i < args.length; i++) {
                passt &= args[i] == null || typen[i].isInstance(args[i]);
            }
            if (passt) {
                m.setAccessible(true);
                return m.invoke(ziel, args);
            }
        }
        throw new NoSuchMethodException(klasse.getName() + "." + name);
    }
}
