package com.anightdazingzoroark.squirrellauncher.ui.dialogs.settingsDialogTabs;

import com.anightdazingzoroark.squirrellauncher.launcher.LauncherService;
import com.anightdazingzoroark.squirrellauncher.launcher.LauncherSettings;
import com.anightdazingzoroark.squirrellauncher.ui.Localization;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public final class GameSettingsTab extends AbstractSettingsTab {
    @NotNull
    private final JCheckBox fullscreenCheckBox;
    @NotNull
    private final JSpinner windowWidthSpinner;
    @NotNull
    private final JSpinner windowHeightSpinner;
    @NotNull
    private final JButton saveButton;

    public GameSettingsTab(@NotNull LauncherService launcherService) {
        super(new BorderLayout(), launcherService);
        this.fullscreenCheckBox = new JCheckBox(Localization.text("settings.game.fullscreen"));
        this.windowWidthSpinner = new JSpinner(new SpinnerNumberModel(1280, 320, 7680, 1));
        this.windowHeightSpinner = new JSpinner(new SpinnerNumberModel(720, 240, 4320, 1));
        this.saveButton = new JButton(Localization.text("settings.button.save"));

        LauncherSettings settings = launcherService.settings();
        this.fullscreenCheckBox.setSelected(settings.fullscreen());
        this.windowWidthSpinner.setValue(settings.windowWidth());
        this.windowHeightSpinner.setValue(settings.windowHeight());

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints title = new GridBagConstraints();
        title.gridx = 0;
        title.gridy = 0;
        title.gridwidth = 2;
        title.weightx = 1;
        title.anchor = GridBagConstraints.LINE_START;
        title.insets = new Insets(0, 0, 16, 0);
        form.add(this.createHeader(), title);

        GridBagConstraints fullscreen = new GridBagConstraints();
        fullscreen.gridx = 0;
        fullscreen.gridy = 1;
        fullscreen.gridwidth = 2;
        fullscreen.anchor = GridBagConstraints.LINE_START;
        fullscreen.insets = new Insets(0, 0, 14, 0);
        form.add(this.fullscreenCheckBox, fullscreen);

        GridBagConstraints widthLabel = new GridBagConstraints();
        widthLabel.gridx = 0;
        widthLabel.gridy = 2;
        widthLabel.anchor = GridBagConstraints.LINE_START;
        widthLabel.insets = new Insets(5, 0, 5, 14);
        form.add(new JLabel(Localization.text("settings.game.width")), widthLabel);

        GridBagConstraints width = new GridBagConstraints();
        width.gridx = 1;
        width.gridy = 2;
        width.weightx = 1;
        width.anchor = GridBagConstraints.LINE_START;
        width.insets = new Insets(5, 0, 5, 0);
        this.windowWidthSpinner.setPreferredSize(new Dimension(110, 28));
        form.add(this.windowWidthSpinner, width);

        GridBagConstraints heightLabel = new GridBagConstraints();
        heightLabel.gridx = 0;
        heightLabel.gridy = 3;
        heightLabel.anchor = GridBagConstraints.LINE_START;
        heightLabel.insets = new Insets(5, 0, 5, 14);
        form.add(new JLabel(Localization.text("settings.game.height")), heightLabel);

        GridBagConstraints height = new GridBagConstraints();
        height.gridx = 1;
        height.gridy = 3;
        height.weightx = 1;
        height.anchor = GridBagConstraints.LINE_START;
        height.insets = new Insets(5, 0, 5, 0);
        this.windowHeightSpinner.setPreferredSize(new Dimension(110, 28));
        form.add(this.windowHeightSpinner, height);

        GridBagConstraints filler = new GridBagConstraints();
        filler.gridx = 0;
        filler.gridy = 4;
        filler.gridwidth = 2;
        filler.weighty = 1;
        filler.fill = GridBagConstraints.VERTICAL;
        form.add(new JPanel(), filler);
        this.add(form, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        actions.add(this.saveButton);
        this.add(actions, BorderLayout.SOUTH);

        this.fullscreenCheckBox.addActionListener(event -> this.updateControlState());
        this.windowWidthSpinner.addChangeListener(event -> this.updateControlState());
        this.windowHeightSpinner.addChangeListener(event -> this.updateControlState());
        this.saveButton.addActionListener(event -> {
            try {
                launcherService.updateSettings(new LauncherSettings(
                        this.fullscreenCheckBox.isSelected(),
                        (Integer) this.windowWidthSpinner.getValue(),
                        (Integer) this.windowHeightSpinner.getValue(),
                        launcherService.settings().language()
                ));
                this.updateControlState();
            }
            catch (Exception exception) {
                this.showSaveError(exception);
            }
        });
        this.updateControlState();
    }

    @Override
    @NotNull
    public String header() {
        return Localization.text("settings.game.heading");
    }

    private void updateControlState() {
        LauncherSettings savedSettings = launcherService.settings();
        boolean changed = savedSettings.fullscreen() != this.fullscreenCheckBox.isSelected()
                || savedSettings.windowWidth() != (Integer) this.windowWidthSpinner.getValue()
                || savedSettings.windowHeight() != (Integer) this.windowHeightSpinner.getValue();
        boolean windowed = !this.fullscreenCheckBox.isSelected();
        this.windowWidthSpinner.setEnabled(windowed);
        this.windowHeightSpinner.setEnabled(windowed);
        this.saveButton.setEnabled(changed);
    }
}
