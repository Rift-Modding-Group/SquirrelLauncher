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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class LauncherSettingsTab extends AbstractSettingsTab {
    @NotNull
    private final JComboBox<LaunchBehavior> launchBehaviorSelector;
    @NotNull
    private final JComboBox<LauncherLanguage> languageSelector;
    @NotNull
    private final JPanel githubRepositoriesPanel = new JPanel(new GridBagLayout());
    @NotNull
    private final List<JTextField> githubRepositoryFields = new ArrayList<>();
    @NotNull
    private final JPasswordField githubPATField = new JPasswordField();

    public LauncherSettingsTab(@NotNull LauncherService launcherService) {
        super(new BorderLayout(), launcherService);
        this.launchBehaviorSelector = new JComboBox<>(LaunchBehavior.values());
        this.languageSelector = new JComboBox<>(LauncherLanguage.values());
        this.showGitHubRepositories(this.launcherService.settings().githubModRepositories());

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

        this.githubPATField.setText(this.launcherService.settings().githubPAT());
        this.githubPATField.setColumns(32);

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints title = new GridBagConstraints();
        title.gridx = 0;
        title.gridy = 0;
        title.weightx = 1;
        title.anchor = GridBagConstraints.LINE_START;
        title.insets = new Insets(0, 0, 16, 0);
        form.add(this.createHeader(), title);

        //---launch behavior---
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

        //---language---
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
        languageSection.insets = new Insets(0, 0, 12, 0);
        form.add(languagePanel, languageSection);

        //---github mod repositories---
        JPanel githubPanel = new JPanel(new GridBagLayout());
        githubPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(Localization.text("settings.launcher.github.section")),
                BorderFactory.createEmptyBorder(4, 10, 8, 10)
        ));
        GridBagConstraints githubInstructions = new GridBagConstraints();
        githubInstructions.gridx = 0;
        githubInstructions.gridy = 0;
        githubInstructions.weightx = 1;
        githubInstructions.fill = GridBagConstraints.HORIZONTAL;
        githubInstructions.anchor = GridBagConstraints.LINE_START;
        githubInstructions.insets = new Insets(5, 0, 8, 0);
        JTextArea githubInstructionsText = new JTextArea(
                Localization.text("settings.launcher.github.instructions"),
                2,
                48
        );
        githubInstructionsText.setEditable(false);
        githubInstructionsText.setFocusable(false);
        githubInstructionsText.setOpaque(false);
        githubInstructionsText.setLineWrap(true);
        githubInstructionsText.setWrapStyleWord(true);
        githubPanel.add(githubInstructionsText, githubInstructions);

        GridBagConstraints githubRepositories = new GridBagConstraints();
        githubRepositories.gridx = 0;
        githubRepositories.gridy = 1;
        githubRepositories.weightx = 1;
        githubRepositories.weighty = 1;
        githubRepositories.fill = GridBagConstraints.BOTH;
        JScrollPane githubRepositoriesScrollPane = new JScrollPane(this.githubRepositoriesPanel);
        githubRepositoriesScrollPane.setPreferredSize(new Dimension(580, 148));
        githubRepositoriesScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        githubPanel.add(githubRepositoriesScrollPane, githubRepositories);

        GridBagConstraints saveGithubRepositories = new GridBagConstraints();
        saveGithubRepositories.gridx = 0;
        saveGithubRepositories.gridy = 2;
        saveGithubRepositories.anchor = GridBagConstraints.LINE_END;
        saveGithubRepositories.insets = new Insets(8, 0, 0, 0);
        JButton saveGithubRepositoriesButton = new JButton(Localization.text("settings.launcher.github.save"));
        githubPanel.add(saveGithubRepositoriesButton, saveGithubRepositories);

        GridBagConstraints githubSection = new GridBagConstraints();
        githubSection.gridx = 0;
        githubSection.gridy = 3;
        githubSection.weightx = 1;
        githubSection.weighty = 1;
        githubSection.fill = GridBagConstraints.BOTH;
        form.add(githubPanel, githubSection);

        GridBagConstraints filler = new GridBagConstraints();
        filler.gridx = 0;
        filler.gridy = 5;
        filler.weighty = 1;
        filler.fill = GridBagConstraints.VERTICAL;
        form.add(new JPanel(), filler);

        //---github personal access token---
        JPanel githubPATPanel = new JPanel(new GridBagLayout());
        githubPATPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(Localization.text("settings.launcher.github_pat")),
                BorderFactory.createEmptyBorder(4, 10, 8, 10)
        ));

        GridBagConstraints githubPATLabel = new GridBagConstraints();
        githubPATLabel.gridx = 0;
        githubPATLabel.gridy = 0;
        githubPATLabel.gridwidth = 2;
        githubPATLabel.weightx = 1;
        githubPATLabel.anchor = GridBagConstraints.LINE_START;
        githubPATLabel.insets = new Insets(5, 0, 5, 0);
        githubPATPanel.add(new JLabel(Localization.text("settings.launcher.github_pat.instructions")), githubPATLabel);

        GridBagConstraints githubPAT = new GridBagConstraints();
        githubPAT.gridx = 0;
        githubPAT.gridy = 1;
        githubPAT.weightx = 1;
        githubPAT.anchor = GridBagConstraints.LINE_START;
        githubPAT.insets = new Insets(5, 0, 5, 0);
        githubPATPanel.add(this.githubPATField, githubPAT);

        GridBagConstraints saveGithubPAT = new GridBagConstraints();
        saveGithubPAT.gridx = 1;
        saveGithubPAT.gridy = 1;
        saveGithubPAT.anchor = GridBagConstraints.LINE_END;
        saveGithubPAT.insets = new Insets(5, 8, 5, 0);
        JButton saveGithubPATButton = new JButton(Localization.text("settings.launcher.github_pat.save"));
        githubPATPanel.add(saveGithubPATButton, saveGithubPAT);

        GridBagConstraints githubPATSection = new GridBagConstraints();
        githubPATSection.gridx = 0;
        githubPATSection.gridy = 4;
        githubPATSection.weightx = 1;
        githubPATSection.fill = GridBagConstraints.HORIZONTAL;
        githubPATSection.insets = new Insets(12, 0, 0, 0);
        form.add(githubPATPanel, githubPATSection);

        this.add(form, BorderLayout.CENTER);
        this.launchBehaviorSelector.addActionListener(event -> this.saveSettings());
        this.languageSelector.addActionListener(event -> this.saveSettings());
        saveGithubRepositoriesButton.addActionListener(event -> this.saveSettings());
        saveGithubPATButton.addActionListener(event -> this.saveSettings());
        this.githubPATField.addActionListener(event -> this.saveSettings());
    }

    @Override
    @NotNull
    public String header() {
        return Localization.text("settings.launcher.heading");
    }

    private void showGitHubRepositories(@NotNull List<String> repositories) {
        this.githubRepositoryFields.clear();
        for (String repository : repositories) {
            this.githubRepositoryFields.add(new JTextField(repository));
        }
        this.rebuildGitHubRepositoriesPanel();
    }

    private void rebuildGitHubRepositoriesPanel() {
        this.githubRepositoriesPanel.removeAll();
        int row = 0;
        for (JTextField repositoryField : this.githubRepositoryFields) {
            GridBagConstraints labelConstraints = new GridBagConstraints();
            labelConstraints.gridx = 0;
            labelConstraints.gridy = row;
            labelConstraints.anchor = GridBagConstraints.LINE_START;
            labelConstraints.insets = new Insets(5, 8, 5, 10);
            this.githubRepositoriesPanel.add(
                    new JLabel(Localization.text("settings.launcher.github.repository")),
                    labelConstraints
            );

            GridBagConstraints fieldConstraints = new GridBagConstraints();
            fieldConstraints.gridx = 1;
            fieldConstraints.gridy = row;
            fieldConstraints.weightx = 1;
            fieldConstraints.fill = GridBagConstraints.HORIZONTAL;
            fieldConstraints.insets = new Insets(5, 0, 5, 8);
            repositoryField.setColumns(1);
            this.githubRepositoriesPanel.add(repositoryField, fieldConstraints);

            JButton removeButton = new JButton("−");
            removeButton.setToolTipText(Localization.text("settings.launcher.github.remove"));
            removeButton.setMargin(new Insets(2, 9, 2, 9));
            removeButton.addActionListener(event -> {
                this.githubRepositoryFields.remove(repositoryField);
                this.rebuildGitHubRepositoriesPanel();
            });
            GridBagConstraints removeConstraints = new GridBagConstraints();
            removeConstraints.gridx = 2;
            removeConstraints.gridy = row;
            removeConstraints.insets = new Insets(5, 0, 5, 8);
            this.githubRepositoriesPanel.add(removeButton, removeConstraints);
            row++;
        }

        GridBagConstraints spacerConstraints = new GridBagConstraints();
        spacerConstraints.gridx = 0;
        spacerConstraints.gridy = row;
        spacerConstraints.gridwidth = 2;
        spacerConstraints.weightx = 1;
        spacerConstraints.fill = GridBagConstraints.HORIZONTAL;
        this.githubRepositoriesPanel.add(Box.createHorizontalGlue(), spacerConstraints);

        JButton addButton = new JButton("+");
        addButton.setToolTipText(Localization.text("settings.launcher.github.add"));
        addButton.setMargin(new Insets(2, 8, 2, 8));
        addButton.addActionListener(event -> {
            JTextField repositoryField = new JTextField();
            this.githubRepositoryFields.add(repositoryField);
            this.rebuildGitHubRepositoriesPanel();
            SwingUtilities.invokeLater(repositoryField::requestFocusInWindow);
        });
        GridBagConstraints addConstraints = new GridBagConstraints();
        addConstraints.gridx = 2;
        addConstraints.gridy = row;
        addConstraints.anchor = GridBagConstraints.LINE_END;
        addConstraints.insets = new Insets(3, 0, 6, 8);
        this.githubRepositoriesPanel.add(addButton, addConstraints);

        this.githubRepositoriesPanel.revalidate();
        this.githubRepositoriesPanel.repaint();
    }

    private void saveSettings() {
        LaunchBehavior selectedLaunchBehavior = (LaunchBehavior) this.launchBehaviorSelector.getSelectedItem();
        LauncherLanguage selectedLanguage = (LauncherLanguage) this.languageSelector.getSelectedItem();
        if (selectedLaunchBehavior == null || selectedLanguage == null) return;
        List<String> githubRepositories = this.githubRepositoryFields.stream()
                .map(JTextField::getText)
                .map(String::trim)
                .filter(repository -> !repository.isBlank())
                .toList();
        char[] githubPATCharacters = this.githubPATField.getPassword();
        String githubPAT;
        try {
            githubPAT = new String(githubPATCharacters).trim();
        }
        finally {
            Arrays.fill(githubPATCharacters, '\0');
        }

        GameSettings savedSettings = this.launcherService.settings();
        try {
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
                    savedSettings.javaRuntimePaths(),
                    githubRepositories,
                    githubPAT
            );
            if (settings.equals(savedSettings)) {
                this.showGitHubRepositories(settings.githubModRepositories());
                this.githubPATField.setText(settings.githubPAT());
                return;
            }
            this.launcherService.updateSettings(settings);
            this.showGitHubRepositories(settings.githubModRepositories());
            this.githubPATField.setText(settings.githubPAT());
        }
        catch (Exception exception) {
            this.showSaveError(exception);
            this.showGitHubRepositories(savedSettings.githubModRepositories());
            this.launchBehaviorSelector.setSelectedItem(savedSettings.launchBehavior());
            this.languageSelector.setSelectedItem(savedSettings.language());
            this.githubPATField.setText(savedSettings.githubPAT());
        }
    }
}
