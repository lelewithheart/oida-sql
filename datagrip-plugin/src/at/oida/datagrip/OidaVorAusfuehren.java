package at.oida.datagrip;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * DataGrip findet in OIDA-SQL-Dateien den Befehl unter dem Cursor nicht ("Nothing to run"),
 * führt eine Markierung aber aus. Deshalb: Kurz bevor DataGrips "Execute" läuft, den
 * OIDA-Befehl unter dem Cursor markieren. Gilt für Strg+Enter und für den grünen Knopf.
 */
public final class OidaVorAusfuehren implements AnActionListener {

    @Override
    public void beforeActionPerformed(@NotNull AnAction action, @NotNull AnActionEvent event) {
        String id = ActionManager.getInstance().getId(action);
        if (id == null || !(id.equals("Console.Jdbc.Execute") || id.equals("Console.Jdbc.Execute.2")
                || id.equals("Console.Jdbc.Execute.3"))) {
            return;
        }
        Editor editor = event.getData(CommonDataKeys.EDITOR);
        PsiFile datei = event.getData(CommonDataKeys.PSI_FILE);
        if (editor == null || datei == null || editor.getSelectionModel().hasSelection()) {
            return;   // eine eigene Markierung vom Benutzer hat Vorrang
        }
        CharSequence text = editor.getDocument().getImmutableCharSequence();
        if (!datei.getLanguage().isKindOf("SQL") || !OidaText.istOida(text)) {
            return;
        }
        OidaText.Befehl befehl = OidaText.befehlBei(text, editor.getCaretModel().getOffset());
        if (befehl != null) {
            editor.getSelectionModel().setSelection(befehl.anfang, befehl.ende);
        }
    }
}
