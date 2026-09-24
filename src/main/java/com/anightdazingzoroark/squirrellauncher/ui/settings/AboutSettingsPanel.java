package com.anightdazingzoroark.squirrellauncher.ui.settings;

import javax.swing.BorderFactory;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.event.HyperlinkEvent;
import java.awt.Desktop;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.net.URI;

public final class AboutSettingsPanel extends JPanel {
    public AboutSettingsPanel() {
        super(new GridBagLayout());
        this.setBorder(BorderFactory.createEmptyBorder(28, 28, 28, 28));

        JLabel heading = new JLabel("About");
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 24f));
        GridBagConstraints title = new GridBagConstraints();
        title.gridx = 0;
        title.gridy = 0;
        title.weightx = 1;
        title.anchor = GridBagConstraints.LINE_START;
        title.insets = new Insets(0, 0, 16, 0);
        this.add(heading, title);

        JEditorPane description = new JEditorPane(
                "text/html",
                """
                <html>
                    <body style='font-family:sans-serif'>
                        It's SquirrelLauncher! Made by ANightDazingZoroark.
                        <br /><br />
                        <a href='https://github.com/Rift-Modding-Group/SquirrelLauncher'>GitHub repository</a>
                        <br /><br />
                        <a href='https://anightdazingzoroark.github.io/'>My website</a>
                    </body>
                </html>
                """
        );
        description.setEditable(false);
        description.setOpaque(false);
        description.addHyperlinkListener(event -> {
            if (event.getEventType() != HyperlinkEvent.EventType.ACTIVATED) return;
            try {
                if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    throw new IllegalStateException("Opening web description is not supported on this computer.");
                }
                Desktop.getDesktop().browse(URI.create(event.getURL().toString()));
            }
            catch (Exception exception) {
                String message = exception.getMessage();
                if (message == null || message.isBlank()) message = exception.getClass().getSimpleName();
                JOptionPane.showMessageDialog(this, message, "Could not open link", JOptionPane.ERROR_MESSAGE);
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
}
