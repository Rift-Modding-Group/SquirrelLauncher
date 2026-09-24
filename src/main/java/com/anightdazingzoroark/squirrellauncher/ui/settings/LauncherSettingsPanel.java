package com.anightdazingzoroark.squirrellauncher.ui.settings;

import com.anightdazingzoroark.squirrellauncher.launcher.LauncherLanguage;
import com.anightdazingzoroark.squirrellauncher.launcher.LauncherService;
import com.anightdazingzoroark.squirrellauncher.launcher.LauncherSettings;
import com.anightdazingzoroark.squirrellauncher.ui.Localization;
import org.jetbrains.annotations.NotNull;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

/** Settings that control the launcher itself. */
public final class LauncherSettingsPanel extends JPanel {
    @NotNull
    private final LauncherService launcherService;
    @NotNull
    private final JComboBox<LauncherLanguage> languageSelector = new JComboBox<>(LauncherLanguage.values());
    @NotNull
    private final JButton saveButton = new JButton(Localization.text("settings.button.save"));

    public LauncherSettingsPanel(@NotNull LauncherService launcherService) {
        super(new BorderLayout());
        this.launcherService = launcherService;
        this.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));
        this.languageSelector.setSelectedItem(this.launcherService.settings().language());
        this.languageSelector.setPreferredSize(new Dimension(240, 28));
        this.languageSelector.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(
                    JList<?> list,
                    Object value,
                    int index,
                    boolean selected,
                    boolean focused
            ) {
                JLabel label = (JLabel) super.getListCellRendererComponent(
                        list,
                        value,
                        index,
                        selected,
                        focused
                );
                if (value instanceof LauncherLanguage language) {
                    label.setText(Localization.text("settings.launcher.language." + language.code()));
                }
                return label;
            }
        });

        JPanel form = new JPanel(new GridBagLayout());
        JLabel heading = new JLabel(Localization.text("settings.launcher.heading"));
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 20f));
        GridBagConstraints title = new GridBagConstraints();
        title.gridx = 0;
        title.gridy = 0;
        title.gridwidth = 2;
        title.weightx = 1;
        title.anchor = GridBagConstraints.LINE_START;
        title.insets = new Insets(0, 0, 16, 0);
        form.add(heading, title);

        GridBagConstraints languageLabel = new GridBagConstraints();
        languageLabel.gridx = 0;
        languageLabel.gridy = 1;
        languageLabel.anchor = GridBagConstraints.LINE_START;
        languageLabel.insets = new Insets(5, 0, 5, 14);
        form.add(new JLabel(Localization.text("settings.launcher.language")), languageLabel);

        GridBagConstraints language = new GridBagConstraints();
        language.gridx = 1;
        language.gridy = 1;
        language.weightx = 1;
        language.anchor = GridBagConstraints.LINE_START;
        language.insets = new Insets(5, 0, 5, 0);
        form.add(this.languageSelector, language);

        GridBagConstraints restart = new GridBagConstraints();
        restart.gridx = 0;
        restart.gridy = 2;
        restart.gridwidth = 2;
        restart.weightx = 1;
        restart.anchor = GridBagConstraints.LINE_START;
        restart.insets = new Insets(12, 0, 0, 0);
        form.add(new JLabel(Localization.text("settings.launcher.language_restart")), restart);

        GridBagConstraints filler = new GridBagConstraints();
        filler.gridx = 0;
        filler.gridy = 3;
        filler.gridwidth = 2;
        filler.weighty = 1;
        filler.fill = GridBagConstraints.VERTICAL;
        form.add(new JPanel(), filler);
        this.add(form, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        actions.add(this.saveButton);
        this.add(actions, BorderLayout.SOUTH);

        this.languageSelector.addActionListener(event -> this.updateControlState());
        this.saveButton.addActionListener(event -> {
            LauncherLanguage selectedLanguage = (LauncherLanguage) this.languageSelector.getSelectedItem();
            if (selectedLanguage == null) return;
            LauncherSettings settings = this.launcherService.settings();
            try {
                this.launcherService.updateSettings(new LauncherSettings(
                        settings.fullscreen(),
                        settings.windowWidth(),
                        settings.windowHeight(),
                        selectedLanguage
                ));
                this.updateControlState();
            }
            catch (Exception exception) {
                String message = exception.getMessage();
                if (message == null || message.isBlank()) message = exception.getClass().getSimpleName();
                JOptionPane.showMessageDialog(
                        this,
                        message,
                        Localization.text("settings.error.save"),
                        JOptionPane.ERROR_MESSAGE
                );
            }
        });
        this.updateControlState();
    }

    private void updateControlState() {
        this.saveButton.setEnabled(
                this.languageSelector.getSelectedItem() != this.launcherService.settings().language()
        );
    }
}
