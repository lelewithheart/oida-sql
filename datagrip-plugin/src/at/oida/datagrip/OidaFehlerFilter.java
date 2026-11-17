package at.oida.datagrip;

import com.intellij.codeInsight.highlighting.HighlightErrorFilter;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * Blendet die Syntaxfehler aus, die DataGrip in OIDA-SQL-Dateien anzeigt
 * ("&lt;statement&gt; or DELIMITER expected, got 'NIMM'"), weil es den Dialekt nicht kennt.
 * Normale SQL-Dateien werden weiter ganz normal geprüft.
 */
public final class OidaFehlerFilter extends HighlightErrorFilter {

    /** Gemerktes Ergebnis pro Datei: {Änderungsstand, ist OIDA-Datei, Zeitpunkt}. */
    private static final Key<long[]> GEMERKT = Key.create("oida.sql.istOida");
    /** Hängt die Datei inzwischen an einer OIDA-Verbindung? Das höchstens alle zwei Sekunden neu nachschauen. */
    private static final long NEU_NACHSCHAUEN_MS = 2000;

    @Override
    public boolean shouldHighlightErrorElement(@NotNull PsiErrorElement element) {
        PsiFile datei = element.getContainingFile();
        return datei == null || !istOidaDatei(datei);
    }

    /**
     * Eine SQL-Datei zählt als OIDA-SQL, wenn darin ein Befehl mit dem Endwort aufhört
     * oder wenn sie an einer OIDA-SQL-Verbindung hängt (dann gilt es auch für eine leere Konsole).
     */
    static boolean istOidaDatei(@NotNull PsiFile datei) {
        if (!datei.getLanguage().isKindOf("SQL")) {
            return false;
        }
        long stand = datei.getModificationStamp();
        long jetzt = System.currentTimeMillis();
        long[] gemerkt = datei.getUserData(GEMERKT);
        if (gemerkt != null && gemerkt[0] == stand && (gemerkt[1] == 1 || jetzt - gemerkt[2] < NEU_NACHSCHAUEN_MS)) {
            return gemerkt[1] == 1;
        }
        boolean oida = OidaText.istOida(datei.getViewProvider().getContents()) || OidaVerbindung.istOida(datei);
        datei.putUserData(GEMERKT, new long[] {stand, oida ? 1 : 0, jetzt});
        if (oida) {
            // Falls das Plugin ohne Neustart installiert wurde, ist der Start-Hook nicht gelaufen.
            OidaStart.treiberBereitstellen();
        }
        return oida;
    }
}
