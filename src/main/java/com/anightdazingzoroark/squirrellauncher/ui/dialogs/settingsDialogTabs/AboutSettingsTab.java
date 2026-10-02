package com.anightdazingzoroark.squirrellauncher.ui.dialogs.settingsDialogTabs;

import com.anightdazingzoroark.squirrellauncher.SquirrelLauncher;
import com.anightdazingzoroark.squirrellauncher.launcher.LauncherService;
import com.anightdazingzoroark.squirrellauncher.ui.Localization;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.net.URI;
import java.net.URL;

public final class AboutSettingsTab extends AbstractSettingsTab {
    public AboutSettingsTab(@NotNull LauncherService launcherService) {
        super(new GridBagLayout(), launcherService);

        GridBagConstraints title = new GridBagConstraints();
        title.gridx = 0;
        title.gridy = 0;
        title.weightx = 1;
        title.anchor = GridBagConstraints.LINE_START;
        title.insets = new Insets(0, 0, 16, 0);
        this.add(this.createHeader(), title);

        JLabel description = new JLabel(Localization.text("about.description"));
        GridBagConstraints descriptionConstraints = new GridBagConstraints();
        descriptionConstraints.gridx = 0;
        descriptionConstraints.gridy = 1;
        descriptionConstraints.weightx = 1;
        descriptionConstraints.fill = GridBagConstraints.HORIZONTAL;
        descriptionConstraints.anchor = GridBagConstraints.FIRST_LINE_START;
        descriptionConstraints.insets = new Insets(0, 0, 18, 0);
        this.add(description, descriptionConstraints);

        JLabel version = new JLabel(Localization.text("about.version", SquirrelLauncher.LAUNCHER_VERSION));
        GridBagConstraints versionConstraints = new GridBagConstraints();
        versionConstraints.gridx = 0;
        versionConstraints.gridy = 2;
        versionConstraints.weightx = 1;
        versionConstraints.fill = GridBagConstraints.HORIZONTAL;
        versionConstraints.anchor = GridBagConstraints.FIRST_LINE_START;
        versionConstraints.insets = new Insets(0, 0, 18, 0);
        this.add(version, versionConstraints);

        JPanel links = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        links.add(this.createLinkButton(
                "about.button.github",
                "/icons/github.png",
                "https://github.com/Rift-Modding-Group/SquirrelLauncher"
        ));
        links.add(this.createLinkButton(
                "about.button.website",
                "/icons/web.png",
                "https://anightdazingzoroark.github.io/"
        ));
        links.add(this.createLinkButton(
                "about.button.discord",
                "/icons/discord.png",
                "https://discord.gg/JnjQtkVt8R"
        ));

        GridBagConstraints linkConstraints = new GridBagConstraints();
        linkConstraints.gridx = 0;
        linkConstraints.gridy = 3;
        linkConstraints.weightx = 1;
        linkConstraints.weighty = 1;
        linkConstraints.fill = GridBagConstraints.HORIZONTAL;
        linkConstraints.anchor = GridBagConstraints.LAST_LINE_START;
        linkConstraints.insets = new Insets(0, 0, 12, 0);
        this.add(links, linkConstraints);
    }

    @Override
    @NotNull
    public String header() {
        return Localization.text("about.heading");
    }

    @NotNull
    private JButton createLinkButton(@NotNull String labelKey, @NotNull String iconPath, @NotNull String uri) {
        URL iconResource = AboutSettingsTab.class.getResource(iconPath);
        if (iconResource == null) throw new IllegalStateException("Missing about link icon: " + iconPath);

        String label = Localization.text(labelKey);
        ImageIcon sourceIcon = new ImageIcon(iconResource);
        Image scaledIcon = sourceIcon.getImage().getScaledInstance(48, 48, Image.SCALE_SMOOTH);
        JButton button = new JButton(new ImageIcon(scaledIcon));
        button.setPreferredSize(new Dimension(64, 64));
        button.setToolTipText(label);
        button.getAccessibleContext().setAccessibleName(label);
        button.addActionListener(event -> this.openLink(uri));
        return button;
    }

    private void openLink(@NotNull String uri) {
        try {
            if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                throw new IllegalStateException(Localization.text("about.error.unsupported"));
            }
            Desktop.getDesktop().browse(URI.create(uri));
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
    }
}
