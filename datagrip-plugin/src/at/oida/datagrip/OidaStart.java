package at.oida.datagrip;

import com.intellij.ide.AppLifecycleListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;

/** Legt beim Start von DataGrip den mitgebrachten Treiber an seinen festen Platz (siehe {@link Treiber}). */
public final class OidaStart implements AppLifecycleListener {

    private static final Logger LOG = Logger.getInstance(OidaStart.class);

    @Override
    public void appStarted() {
        treiberBereitstellen();
    }

    /** Im Hintergrund, damit der Start von DataGrip nicht auf die Festplatte warten muss. */
    static void treiberBereitstellen() {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            String meldung = Treiber.einmal();
            if (meldung != null) {
                LOG.info("OIDA-SQL Treiber: " + meldung);
            }
        });
    }
}
