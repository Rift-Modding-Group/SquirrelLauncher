package com.anightdazingzoroark.squirrellauncher.ui.dialogs.settingsDialogTabs;

import com.anightdazingzoroark.squirrellauncher.launcher.LauncherService;
import com.anightdazingzoroark.squirrellauncher.ui.Localization;
import org.jetbrains.annotations.NotNull;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import java.awt.Font;
import java.awt.LayoutManager;

/**
 * Shared layout and behavior for panels displayed in the settings dialog.
 * */
public abstract class AbstractSettingsTab extends JPanel {
    @NotNull
    protected final LauncherService launcherService;

    protected AbstractSettingsTab(@NotNull LayoutManager layout, @NotNull LauncherService launcherService) {
        super(layout);
        this.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));
        this.launcherService = launcherService;
    }

    @NotNull
    public abstract String header();

    //----methods common to the tabs----
    @NotNull
    protected final JLabel createHeader() {
        JLabel headerLabel = new JLabel(this.header());
        headerLabel.setFont(headerLabel.getFont().deriveFont(Font.BOLD, 20f));
        return headerLabel;
    }

    protected final void showSaveError(@NotNull Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) message = exception.getClass().getSimpleName();
        JOptionPane.showMessageDialog(
                this, message,
                Localization.text("settings.error.save"),
                JOptionPane.ERROR_MESSAGE
        );
    }
}
