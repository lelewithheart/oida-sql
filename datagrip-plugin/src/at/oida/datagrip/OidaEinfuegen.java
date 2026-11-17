package at.oida.datagrip;

import at.oida.Tippen;
import at.oida.Uebersetzer;
import com.intellij.codeInsight.editorActions.CopyPastePreProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RawText;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Wird MySQL in eine OIDA-SQL-Datei eingefügt, kommt es gleich als OIDA-SQL an:
 * die Wörter übersetzt, der Strichpunkt am Befehlsende durch das Endwort ersetzt.
 * Was schon Dialekt ist, bleibt unverändert.
 */
public final class OidaEinfuegen implements CopyPastePreProcessor {

    @Override
    public @Nullable String preprocessOnCopy(PsiFile file, int[] startOffsets, int[] endOffsets, String text) {
        return null;   // beim Kopieren nichts ändern
    }

    @Override
    public @NotNull String preprocessOnPaste(Project project, PsiFile file, Editor editor, String text, RawText rawText) {
        try {
            if (text == null || text.isEmpty() || !OidaEinstellung.uebersetzen() || !OidaFehlerFilter.istOidaDatei(file)) {
                return text;
            }
            Uebersetzer u = OidaFarben.woerterbuch();
            if (u == null) {
                return text;
            }
            // Nicht mitten in einem Text in Anführungszeichen oder in einem Kommentar
            CharSequence dokument = editor.getDocument().getImmutableCharSequence();
            int cursor = Math.min(editor.getSelectionModel().getSelectionStart(), dokument.length());
            int anfang = OidaTippen.befehlsAnfang(dokument, cursor);
            if (Tippen.inTextOderKommentar(dokument.subSequence(anfang, cursor).toString())) {
                return text;
            }
            return Tippen.einfuegen(u, text);
        } catch (RuntimeException e) {
            return text;   // im Zweifel unverändert einfügen
        }
    }
}
