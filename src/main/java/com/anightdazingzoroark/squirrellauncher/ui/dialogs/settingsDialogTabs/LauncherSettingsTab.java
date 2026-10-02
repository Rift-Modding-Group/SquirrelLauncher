package com.anightdazingzoroark.squirrellauncher.ui.dialogs.settingsDialogTabs;

import com.anightdazingzoroark.squirrellauncher.launcher.GameSettings;
import com.anightdazingzoroark.squirrellauncher.launcher.LaunchBehavior;
import com.anightdazingzoroark.squirrellauncher.launcher.LauncherLanguage;
import com.anightdazingzoroark.squirrellauncher.launcher.LauncherService;
import com.anightdazingzoroark.squirrellauncher.ui.Localization;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Locale;

public final class LauncherSettingsTab extends AbstractSettingsTab {
    @NotNull
    private final JComboBox<LaunchBehavior> launchBehaviorSelector;
    @NotNull
    private final JComboBox<LauncherLanguage> languageSelector;

    public LauncherSettingsTab(@NotNull LauncherService launcherService) {
        super(new BorderLayout(), launcherService);
        this.launchBehaviorSelector = new JComboBox<>(LaunchBehavior.values());
        this.languageSelector = new JComboBox<>(LauncherLanguage.values());

        this.launchBehaviorSelector.setSelectedItem(this.launcherService.settings().launchBehavior());
        this.launchBehaviorSelector.setPreferredSize(new Dimension(280, 28));
        this.launchBehaviorSelector.setRenderer(new DefaultListCellRenderer() {
            @Override
            @NotNull
            public Component getListCellRendererComponent(
                    @NotNull JList<?> list, @Nullable Object value, int index,
                    boolean selected, boolean focused
            ) {
                JLabel label = (JLabel) super.getListCellRendererComponent(
                        list, value, index,
                        selected, focused
                );
                if (value instanceof LaunchBehavior behavior) {
                    label.setText(Localization.text(
                            "settings.launcher.launch_behavior." + behavior.name().toLowerCase(Locale.ROOT)
                    ));
                }
                return label;
            }
        });

        this.languageSelector.setSelectedItem(this.launcherService.settings().language());
        this.languageSelector.setPreferredSize(new Dimension(240, 28));
        this.languageSelector.setRenderer(new DefaultListCellRenderer() {
            @Override
            @NotNull
            public Component getListCellRendererComponent(
                    @NotNull JList<?> list, @Nullable Object value, int index,
                    boolean selected, boolean focused
            ) {
                JLabel label = (JLabel) super.getListCellRendererComponent(
                        list, value, index,
                        selected, focused
                );
                if (value instanceof LauncherLanguage language) {
                    label.setText(Localization.text("settings.launcher.language." + language.code()));
                }
                return label;
            }
        });

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints title = new GridBagConstraints();
        title.gridx = 0;
        title.gridy = 0;
        title.weightx = 1;
        title.anchor = GridBagConstraints.LINE_START;
        title.insets = new Insets(0, 0, 16, 0);
        form.add(this.createHeader(), title);

        JPanel launchBehaviorPanel = new JPanel(new GridBagLayout());
        launchBehaviorPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(Localization.text("settings.launcher.launch_section")),
                BorderFactory.createEmptyBorder(4, 10, 8, 10)
        ));
        GridBagConstraints launchBehaviorLabel = new GridBagConstraints();
        launchBehaviorLabel.gridx = 0;
        launchBehaviorLabel.gridy = 0;
        launchBehaviorLabel.anchor = GridBagConstraints.LINE_START;
        launchBehaviorLabel.insets = new Insets(5, 0, 5, 14);
        launchBehaviorPanel.add(
                new JLabel(Localization.text("settings.launcher.launch_behavior")),
                launchBehaviorLabel
        );
        GridBagConstraints launchBehavior = new GridBagConstraints();
        launchBehavior.gridx = 1;
        launchBehavior.gridy = 0;
        launchBehavior.weightx = 1;
        launchBehavior.anchor = GridBagConstraints.LINE_START;
        launchBehavior.insets = new Insets(5, 0, 5, 0);
        launchBehaviorPanel.add(this.launchBehaviorSelector, launchBehavior);

        GridBagConstraints launchSection = new GridBagConstraints();
        launchSection.gridx = 0;
        launchSection.gridy = 1;
        launchSection.weightx = 1;
        launchSection.fill = GridBagConstraints.HORIZONTAL;
        launchSection.insets = new Insets(0, 0, 12, 0);
        form.add(launchBehaviorPanel, launchSection);

        JPanel languagePanel = new JPanel(new GridBagLayout());
        languagePanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(Localization.text("settings.launcher.language_section")),
                BorderFactory.createEmptyBorder(4, 10, 8, 10)
        ));
        GridBagConstraints languageLabel = new GridBagConstraints();
        languageLabel.gridx = 0;
        languageLabel.gridy = 0;
        languageLabel.anchor = GridBagConstraints.LINE_START;
        languageLabel.insets = new Insets(5, 0, 5, 14);
        languagePanel.add(new JLabel(Localization.text("settings.launcher.language")), languageLabel);

        GridBagConstraints language = new GridBagConstraints();
        language.gridx = 1;
        language.gridy = 0;
        language.weightx = 1;
        language.anchor = GridBagConstraints.LINE_START;
        language.insets = new Insets(5, 0, 5, 0);
        languagePanel.add(this.languageSelector, language);

        GridBagConstraints restart = new GridBagConstraints();
        restart.gridx = 0;
        restart.gridy = 1;
        restart.gridwidth = 2;
        restart.weightx = 1;
        restart.anchor = GridBagConstraints.LINE_START;
        restart.insets = new Insets(12, 0, 0, 0);
        languagePanel.add(new JLabel(Localization.text("settings.launcher.language_restart")), restart);

        GridBagConstraints languageSection = new GridBagConstraints();
        languageSection.gridx = 0;
        languageSection.gridy = 2;
        languageSection.weightx = 1;
        languageSection.fill = GridBagConstraints.HORIZONTAL;
        form.add(languagePanel, languageSection);

        GridBagConstraints filler = new GridBagConstraints();
        filler.gridx = 0;
        filler.gridy = 3;
        filler.weighty = 1;
        filler.fill = GridBagConstraints.VERTICAL;
        form.add(new JPanel(), filler);
        this.add(form, BorderLayout.CENTER);

        this.launchBehaviorSelector.addActionListener(event -> this.saveSettings());
        this.languageSelector.addActionListener(event -> this.saveSettings());
    }

    @Override
    @NotNull
    public String header() {
        return Localization.text("settings.launcher.heading");
    }

    private void saveSettings() {
        LaunchBehavior selectedLaunchBehavior = (LaunchBehavior) this.launchBehaviorSelector.getSelectedItem();
        LauncherLanguage selectedLanguage = (LauncherLanguage) this.languageSelector.getSelectedItem();
        if (selectedLaunchBehavior == null || selectedLanguage == null) return;

        GameSettings savedSettings = this.launcherService.settings();
        GameSettings settings = new GameSettings(
                savedSettings.fullscreen(),
                savedSettings.windowWidth(),
                savedSettings.windowHeight(),
                savedSettings.allocatedMemoryGigabytes(),
                savedSettings.lowMemoryWarning(),
                savedSettings.jvmArguments(),
                selectedLanguage,
                selectedLaunchBehavior,
                savedSettings.showLinuxJavaPackageManagerReminder(),
                savedSettings.javaRuntimePaths()
        );
        if (settings.equals(savedSettings)) return;
        try {
            this.launcherService.updateSettings(settings);
        }
        catch (Exception exception) {
            this.showSaveError(exception);
            this.launchBehaviorSelector.setSelectedItem(savedSettings.launchBehavior());
            this.languageSelector.setSelectedItem(savedSettings.language());
        }
    }
}
