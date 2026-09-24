package com.anightdazingzoroark.squirrellauncher.ui.settings;

import com.anightdazingzoroark.squirrellauncher.ui.Localization;
import org.jetbrains.annotations.NotNull;

import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.event.HyperlinkEvent;
import java.awt.Desktop;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.net.URI;

public final class AboutSettingsPanel extends AbstractSettingsPanel {
    public AboutSettingsPanel() {
        super(new GridBagLayout());

        JLabel heading = this.createHeader();
        GridBagConstraints title = new GridBagConstraints();
        title.gridx = 0;
        title.gridy = 0;
        title.weightx = 1;
        title.anchor = GridBagConstraints.LINE_START;
        title.insets = new Insets(0, 0, 16, 0);
        this.add(heading, title);

        JEditorPane description = new JEditorPane("text/html", Localization.text("about.description"));
        description.setEditable(false);
        description.setOpaque(false);
        description.addHyperlinkListener(event -> {
            if (event.getEventType() != HyperlinkEvent.EventType.ACTIVATED) return;
            try {
                if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    throw new IllegalStateException(Localization.text("about.error.unsupported"));
                }
                Desktop.getDesktop().browse(URI.create(event.getURL().toString()));
            }
            catch (Exception exception) {
                String message = exception.getMessage();
                if (message == null || message.isBlank()) message = exception.getClass().getSimpleName();
                JOptionPane.showMessageDialog(
                        this,
                        message,
                        Localization.text("about.error.open_link"),
                        JOptionPane.ERROR_MESSAGE
                );
            }
        });

        GridBagConstraints linkConstraints = new GridBagConstraints();
        linkConstraints.gridx = 0;
        linkConstraints.gridy = 1;
        linkConstraints.weightx = 1;
        linkConstraints.weighty = 1;
        linkConstraints.fill = GridBagConstraints.BOTH;
        linkConstraints.anchor = GridBagConstraints.FIRST_LINE_START;
        this.add(description, linkConstraints);
    }

    @Override
    @NotNull
    public String header() {
        return Localization.text("about.heading");
    }
}
