package at.oida.datagrip;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;

/** Menüpunkt unter Tools: das automatische Übersetzen beim Tippen und Einfügen ein- und ausschalten. */
public final class OidaSchalter extends ToggleAction implements DumbAware {

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
        return OidaEinstellung.uebersetzen();
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean an) {
        OidaEinstellung.uebersetzen(an);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}
