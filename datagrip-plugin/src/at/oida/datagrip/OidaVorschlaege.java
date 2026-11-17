package at.oida.datagrip;

import at.oida.Tippen;
import at.oida.Uebersetzer;
import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * Schlägt in OIDA-SQL-Dateien die Dialekt-Wörter vor. Rechts daneben steht das MySQL-Wort,
 * und man findet einen Vorschlag auch über das MySQL-Wort: Wer SEL tippt, bekommt GIB MA angeboten.
 *
 * Tabellen- und Spaltennamen kommen weiter von DataGrip selbst, soweit es den Befehl versteht.
 */
public final class OidaVorschlaege extends CompletionContributor {

    @Override
    public void fillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
        try {
            PsiFile datei = parameters.getOriginalFile();
            if (!OidaFehlerFilter.istOidaDatei(datei)) {
                return;
            }
            Uebersetzer u = OidaFarben.woerterbuch();
            if (u == null) {
                return;
            }
            CharSequence text = parameters.getEditor().getDocument().getImmutableCharSequence();
            int cursor = Math.min(parameters.getOffset(), text.length());
            int anfang = OidaTippen.befehlsAnfang(text, cursor);
            if (Tippen.inTextOderKommentar(text.subSequence(anfang, cursor).toString())) {
                return;
            }
            for (String[] paar : u.alleWoerter()) {
                LookupElementBuilder vorschlag = LookupElementBuilder.create(paar[0])
                        .withLookupString(paar[1])
                        .withTypeText(paar[1], true)
                        .withCaseSensitivity(false)
                        .bold();
                result.addElement(PrioritizedLookupElement.withPriority(vorschlag, 100));
            }
        } catch (RuntimeException e) {
            // Dann eben ohne unsere Vorschläge
        }
    }
}
