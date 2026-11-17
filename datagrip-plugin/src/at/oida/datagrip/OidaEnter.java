package at.oida.datagrip;

import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/** Bei Enter das letzte Wort der Zeile davor übersetzen (das Leerzeichen dahinter fehlt ja). */
public final class OidaEnter implements EnterHandlerDelegate {

    @Override
    public Result preprocessEnter(@NotNull PsiFile file, @NotNull Editor editor, @NotNull Ref<Integer> caretOffset,
                                  @NotNull Ref<Integer> caretAdvance, @NotNull DataContext dataContext,
                                  EditorActionHandler originalHandler) {
        return Result.Continue;
    }

    @Override
    public Result postProcessEnter(@NotNull PsiFile file, @NotNull Editor editor, @NotNull DataContext dataContext) {
        try {
            if (OidaEinstellung.uebersetzen() && OidaFehlerFilter.istOidaDatei(file)) {
                CharSequence text = editor.getDocument().getImmutableCharSequence();
                int i = Math.min(editor.getCaretModel().getOffset(), text.length());
                while (i > 0 && Character.isWhitespace(text.charAt(i - 1))) {
                    i--;
                }
                if (i > 0 && OidaTippen.wortZeichen(text.charAt(i - 1))) {
                    OidaTippen.uebersetzeVor(file.getProject(), editor, i, false);
                }
            }
        } catch (RuntimeException e) {
            // Enter muss immer gehen
        }
        return Result.Continue;
    }
}
