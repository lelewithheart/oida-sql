package at.oida.datagrip;

import com.intellij.ide.util.PropertiesComponent;

/** Die eine Einstellung des Plugins: Soll beim Tippen und Einfügen automatisch übersetzt werden? */
final class OidaEinstellung {

    private static final String SCHLUESSEL = "oida.sql.autoUebersetzen";

    private OidaEinstellung() {
    }

    static boolean uebersetzen() {
        return PropertiesComponent.getInstance().getBoolean(SCHLUESSEL, true);
    }

    static void uebersetzen(boolean an) {
        PropertiesComponent.getInstance().setValue(SCHLUESSEL, an, true);
    }
}
