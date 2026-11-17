package at.oida.datagrip;

import at.oida.Uebersetzer;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * Färbt die Dialekt-Wörter (ERSTÖN, GIB MA, OIDA ...) so ein wie SQL-Schlüsselwörter.
 * DataGrip kennt sie nicht und zeigt sie sonst wie gewöhnliche Namen an.
 * Das Wörterbuch ist in der Plugin-Datei mit eingepackt (dasselbe wie im Treiber).
 */
public final class OidaFarben implements Annotator {

    /** Gemerkte Stellen pro Datei: {Änderungsstand als long[1], Bereiche}. */
    private static final Key<Object[]> GEMERKT = Key.create("oida.sql.dialektStellen");

    private static volatile Uebersetzer woerterbuch;
    private static volatile boolean woerterbuchKaputt;

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (element.getFirstChild() != null) {
            return;   // nur die kleinsten Stücke (einzelne Wörter) einfärben
        }
        PsiFile datei = element.getContainingFile();
        if (datei == null || !OidaFehlerFilter.istOidaDatei(datei)) {
            return;
        }
        int[][] stellen = stellen(datei);
        if (stellen.length == 0) {
            return;
        }
        TextRange bereich = element.getTextRange();
        int von = bereich.getStartOffset();
        int bis = bereich.getEndOffset();
        if (von >= bis) {
            return;
        }
        // Erste Stelle suchen, die nach dem Anfang dieses Stücks endet
        int links = 0;
        int rechts = stellen.length;
        while (links < rechts) {
            int mitte = (links + rechts) >>> 1;
            if (stellen[mitte][1] <= von) {
                links = mitte + 1;
            } else {
                rechts = mitte;
            }
        }
        for (int i = links; i < stellen.length && stellen[i][0] < bis; i++) {
            int a = Math.max(von, stellen[i][0]);
            int e = Math.min(bis, stellen[i][1]);
            if (a < e) {
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                        .range(new TextRange(a, e))
                        .textAttributes(farbe())
                        .create();
            }
        }
    }

    private static int[][] stellen(PsiFile datei) {
        long stand = datei.getModificationStamp();
        Object[] gemerkt = datei.getUserData(GEMERKT);
        if (gemerkt != null && ((long[]) gemerkt[0])[0] == stand) {
            return (int[][]) gemerkt[1];
        }
        int[][] stellen = new int[0][];
        Uebersetzer u = woerterbuch();
        if (u != null) {
            List<int[]> liste = u.dialektStellen(datei.getViewProvider().getContents().toString());
            stellen = liste.toArray(new int[0][]);
        }
        datei.putUserData(GEMERKT, new Object[] {new long[] {stand}, stellen});
        return stellen;
    }

    private static Uebersetzer woerterbuch() {
        if (woerterbuch == null && !woerterbuchKaputt) {
            try {
                woerterbuch = Uebersetzer.ausJar();
            } catch (RuntimeException e) {
                woerterbuchKaputt = true;   // dann eben ohne Farben
            }
        }
        return woerterbuch;
    }

    /** Die Farbe, die DataGrip für SQL-Schlüsselwörter eingestellt hat (sonst die allgemeine Schlüsselwort-Farbe). */
    private static TextAttributesKey farbe() {
        TextAttributesKey sql = TextAttributesKey.find("SQL_KEYWORD");
        TextAttributes eingestellt = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(sql);
        return eingestellt != null && !eingestellt.isEmpty() ? sql : DefaultLanguageHighlighterColors.KEYWORD;
    }
}
