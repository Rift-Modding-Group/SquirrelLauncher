package com.anightdazingzoroark.squirrellauncher.ui.dialogs.settingsDialogTabs;

import com.anightdazingzoroark.squirrellauncher.launcher.LauncherService;
import com.anightdazingzoroark.squirrellauncher.launcher.GameSettings;
import com.anightdazingzoroark.squirrellauncher.ui.settingsControls.GameSettingsControls;
import com.anightdazingzoroark.squirrellauncher.ui.Localization;
import org.jetbrains.annotations.NotNull;

import javax.swing.JButton;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

public final class GameSettingsTab extends AbstractSettingsTab {
    @NotNull
    private final GameSettingsControls gameSettingsControls = new GameSettingsControls(false);
    @NotNull
    private final JButton resetDefaultsButton = new JButton(Localization.text("settings.button.reset_defaults"));
    @NotNull
    private final JButton cancelJvmArgumentsButton = new JButton(Localization.text("settings.button.cancel"));
    @NotNull
    private final JButton saveJvmArgumentsButton = new JButton(Localization.text("settings.button.save"));

    public GameSettingsTab(@NotNull LauncherService launcherService) {
        super(new BorderLayout(0, 12), launcherService);
        @NotNull Runnable saveSettings = () -> {
            GameSettings savedSettings = this.launcherService.settings();
            GameSettings settings = new GameSettings(
                    this.gameSettingsControls.fullscreen(),
                    this.gameSettingsControls.windowWidth(),
                    this.gameSettingsControls.windowHeight(),
                    this.gameSettingsControls.allocatedMemoryGigabytes(),
                    this.gameSettingsControls.lowMemoryWarning(),
                    this.gameSettingsControls.jvmArguments(),
                    savedSettings.language(),
                    savedSettings.launchBehavior(),
                    savedSettings.showLinuxJavaPackageManagerReminder(),
                    savedSettings.javaRuntimePaths(),
                    savedSettings.githubModRepositories(),
                    savedSettings.githubPAT()
            );
            if (settings.equals(savedSettings)) return;
            try {
                this.launcherService.updateSettings(settings);
            } catch (Exception exception) {
                this.showSaveError(exception);
            }
        };
        this.gameSettingsControls.showGlobalSettings(launcherService.settings());
        this.gameSettingsControls.setChangeListener(saveSettings);

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints title = new GridBagConstraints();
        title.gridx = 0;
        title.gridy = 0;
        title.weightx = 1;
        title.anchor = GridBagConstraints.LINE_START;
        title.insets = new Insets(0, 0, 16, 0);
        form.add(this.createHeader(), title);

        GridBagConstraints controls = new GridBagConstraints();
        controls.gridx = 0;
        controls.gridy = 1;
        controls.weightx = 1;
        controls.weighty = 1;
        controls.fill = GridBagConstraints.BOTH;
        form.add(this.gameSettingsControls, controls);
        this.add(form, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        actions.add(this.resetDefaultsButton);
        actions.add(this.cancelJvmArgumentsButton);
        actions.add(this.saveJvmArgumentsButton);
        this.add(actions, BorderLayout.SOUTH);
        this.gameSettingsControls.setAdvancedActionsAvailableListener(available -> {
            this.cancelJvmArgumentsButton.setEnabled(available);
            this.saveJvmArgumentsButton.setEnabled(available);
        });
        this.gameSettingsControls.setAdvancedDisplayedListener(displayed -> {
            this.cancelJvmArgumentsButton.setVisible(displayed);
            this.saveJvmArgumentsButton.setVisible(displayed);
        });
        this.resetDefaultsButton.addActionListener(event -> this.gameSettingsControls.resetToDefaults());
        this.cancelJvmArgumentsButton.addActionListener(event -> this.gameSettingsControls.cancelAdvancedChanges());
        this.saveJvmArgumentsButton.addActionListener(event -> this.gameSettingsControls.saveAdvancedChanges());
    }

    @Override
    @NotNull
    public String header() {
        return Localization.text("settings.game.heading");
    }

    public boolean confirmDiscardUnsavedChanges() {
        return this.gameSettingsControls.confirmDiscardUnsavedChanges();
    }
}
