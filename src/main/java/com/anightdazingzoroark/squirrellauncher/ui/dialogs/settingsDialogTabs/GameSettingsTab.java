package com.anightdazingzoroark.squirrellauncher.ui.dialogs.settingsDialogTabs;

import com.anightdazingzoroark.squirrellauncher.launcher.LauncherService;
import com.anightdazingzoroark.squirrellauncher.launcher.GameSettings;
import com.anightdazingzoroark.squirrellauncher.ui.GameSettingsControls;
import com.anightdazingzoroark.squirrellauncher.ui.Localization;
import org.jetbrains.annotations.NotNull;

import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

public final class GameSettingsTab extends AbstractSettingsTab {
    @NotNull
    private final GameSettingsControls gameSettingsControls = new GameSettingsControls(false);

    public GameSettingsTab(@NotNull LauncherService launcherService) {
        super(new BorderLayout(), launcherService);
        @NotNull Runnable saveSettings = () -> {
            GameSettings savedSettings = this.launcherService.settings();
            GameSettings settings = new GameSettings(
                    this.gameSettingsControls.fullscreen(),
                    this.gameSettingsControls.windowWidth(),
                    this.gameSettingsControls.windowHeight(),
                    this.gameSettingsControls.allocatedMemoryGigabytes(),
                    this.gameSettingsControls.lowMemoryWarning(),
                    savedSettings.language()
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
    }

    @Override
    @NotNull
    public String header() {
        return Localization.text("settings.game.heading");
    }
}
