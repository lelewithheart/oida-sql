package at.oida.datagrip;

import com.intellij.codeInsight.highlighting.HighlightErrorFilter;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * Blendet die Syntaxfehler aus, die DataGrip in OIDA-SQL-Dateien anzeigt
 * ("&lt;statement&gt; or DELIMITER expected, got 'NIMM'"), weil es den Dialekt nicht kennt.
 * Normale SQL-Dateien ohne das Endwort werden weiter ganz normal geprüft.
 */
public final class OidaFehlerFilter extends HighlightErrorFilter {

    /** Gemerktes Ergebnis pro Datei: {Änderungsstand, ist OIDA-Datei}. */
    private static final Key<long[]> GEMERKT = Key.create("oida.sql.istOida");

    @Override
    public boolean shouldHighlightErrorElement(@NotNull PsiErrorElement element) {
        PsiFile datei = element.getContainingFile();
        return datei == null || !istOidaDatei(datei);
    }

    static boolean istOidaDatei(@NotNull PsiFile datei) {
        if (!datei.getLanguage().isKindOf("SQL")) {
            return false;
        }
        long stand = datei.getModificationStamp();
        long[] gemerkt = datei.getUserData(GEMERKT);
        if (gemerkt != null && gemerkt[0] == stand) {
            return gemerkt[1] == 1;
        }
        boolean oida = OidaText.istOida(datei.getViewProvider().getContents());
        datei.putUserData(GEMERKT, new long[] {stand, oida ? 1 : 0});
        if (oida) {
            // Falls das Plugin ohne Neustart installiert wurde, ist der Start-Hook nicht gelaufen.
            OidaStart.treiberBereitstellen();
        }
        return oida;
    }
}
