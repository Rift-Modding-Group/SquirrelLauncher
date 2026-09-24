package com.anightdazingzoroark.squirrellauncher.ui.settings;

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
public abstract class AbstractSettingsPanel extends JPanel {
    protected AbstractSettingsPanel(@NotNull LayoutManager layout) {
        super(layout);
        this.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));
    }

    @NotNull
    public abstract String header();

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
                this,
                message,
                Localization.text("settings.error.save"),
                JOptionPane.ERROR_MESSAGE
        );
    }
}
