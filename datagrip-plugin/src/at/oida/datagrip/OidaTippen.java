package at.oida.datagrip;

import at.oida.Tippen;
import at.oida.Uebersetzer;
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * Übersetzt beim Tippen: Sobald nach einem Wort ein Leerzeichen, eine Klammer, ein Beistrich
 * oder Ähnliches kommt, wird das MySQL-Wort davor durch das Dialekt-Wort ersetzt
 * (SELECT wird zu GIB MA). Die eigentliche Logik steht in {@link Tippen}.
 */
public final class OidaTippen extends TypedHandlerDelegate {

    /** So weit zurück wird höchstens nach dem Anfang des Befehls gesucht. */
    private static final int FENSTER = 20000;

    @Override
    public @NotNull Result charTyped(char c, @NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
        try {
            if (wortZeichen(c) || !OidaEinstellung.uebersetzen()) {
                return Result.CONTINUE;
            }
            CharSequence text = editor.getDocument().getImmutableCharSequence();
            int nachZeichen = editor.getCaretModel().getOffset();
            int wortEnde = nachZeichen - 1;
            if (wortEnde < 1 || wortEnde > text.length() || !wortZeichen(text.charAt(wortEnde - 1))) {
                return Result.CONTINUE;
            }
            if (OidaFehlerFilter.istOidaDatei(file)) {
                uebersetzeVor(project, editor, wortEnde, c == ' ' || c == '\t');
            }
        } catch (RuntimeException e) {
            // Tippen muss immer gehen
        }
        return Result.CONTINUE;
    }

    /**
     * Übersetzt das Wort, das bei wortEnde aufhört (und Wörter davor, die noch gewartet haben).
     * Die Änderung passiert als eigener Schritt gleich nach dem Tippen, damit Strg+Z nur die
     * Übersetzung zurücknimmt und nicht auch das getippte Zeichen.
     */
    static void uebersetzeVor(Project project, Editor editor, int wortEnde, boolean leerzeichen) {
        Uebersetzer u = OidaFarben.woerterbuch();
        if (u == null) {
            return;
        }
        Document dokument = editor.getDocument();
        CharSequence text = dokument.getImmutableCharSequence();
        int anfang = befehlsAnfang(text, wortEnde);
        List<Tippen.Ersatz> ersetzen = Tippen.beimTippen(u, text.subSequence(anfang, wortEnde).toString(), leerzeichen);
        if (ersetzen.isEmpty()) {
            return;
        }
        long stand = dokument.getModificationStamp();
        ApplicationManager.getApplication().invokeLater(() -> {
            if (project.isDisposed() || editor.isDisposed() || dokument.getModificationStamp() != stand) {
                return;   // inzwischen wurde weitergetippt: lieber nichts anfassen
            }
            WriteCommandAction.runWriteCommandAction(project, "OIDA-SQL Übersetzen", null, () -> {
                for (Tippen.Ersatz e : ersetzen) {   // von hinten nach vorne, die Stellen davor bleiben gültig
                    dokument.replaceString(anfang + e.von, anfang + e.bis, e.neu);
                }
            });
        });
    }

    /**
     * Wo fängt der Befehl an, in dem der Cursor steht? Gleich nach dem letzten Endwort davor.
     * Gibt es im Suchfenster keines, dann am Anfang einer Zeile.
     */
    static int befehlsAnfang(CharSequence text, int bis) {
        int unten = Math.max(0, bis - FENSTER);
        String endwort = OidaText.ENDWORT;
        for (int i = bis - endwort.length(); i >= unten; i--) {
            boolean passt = true;
            for (int k = 0; k < endwort.length() && passt; k++) {
                passt = Character.toUpperCase(text.charAt(i + k)) == endwort.charAt(k);
            }
            if (passt && (i == 0 || !wortZeichen(text.charAt(i - 1)))
                    && (i + endwort.length() >= text.length() || !wortZeichen(text.charAt(i + endwort.length())))) {
                return i + endwort.length();
            }
        }
        int i = unten;
        while (i > 0 && i < bis && text.charAt(i - 1) != '\n') {
            i++;
        }
        return Math.min(i, bis);
    }

    static boolean wortZeichen(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }
}
