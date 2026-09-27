package com.anightdazingzoroark.squirrellauncher.ui.dialogs.settingsDialogTabs;

import com.anightdazingzoroark.squirrellauncher.launcher.LauncherService;
import com.anightdazingzoroark.squirrellauncher.launcher.LauncherSettings;
import com.anightdazingzoroark.squirrellauncher.ui.Localization;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Hashtable;

public final class GameSettingsTab extends AbstractSettingsTab {
    @NotNull
    private final JCheckBox fullscreenCheckBox;
    @NotNull
    private final JSpinner windowWidthSpinner;
    @NotNull
    private final JSpinner windowHeightSpinner;
    @NotNull
    private final JSlider allocatedMemorySlider;
    @NotNull
    private final JLabel allocatedMemoryValueLabel;
    @NotNull
    private final JButton saveButton;

    public GameSettingsTab(@NotNull LauncherService launcherService) {
        super(new BorderLayout(), launcherService);
        this.fullscreenCheckBox = new JCheckBox(Localization.text("settings.game.fullscreen"));
        this.windowWidthSpinner = new JSpinner(new SpinnerNumberModel(854, 320, 7680, 1));
        this.windowHeightSpinner = new JSpinner(new SpinnerNumberModel(480, 240, 4320, 1));
        this.allocatedMemorySlider = new JSlider(
                LauncherSettings.MINIMUM_ALLOCATED_MEMORY_GIGABYTES,
                LauncherSettings.MAXIMUM_ALLOCATED_MEMORY_GIGABYTES,
                LauncherSettings.DEFAULT_ALLOCATED_MEMORY_GIGABYTES
        );
        this.allocatedMemoryValueLabel = new JLabel();
        this.saveButton = new JButton(Localization.text("settings.button.save"));

        LauncherSettings settings = launcherService.settings();
        this.fullscreenCheckBox.setSelected(settings.fullscreen());
        this.windowWidthSpinner.setValue(settings.windowWidth());
        this.windowHeightSpinner.setValue(settings.windowHeight());
        this.allocatedMemorySlider.setValue(settings.allocatedMemoryGigabytes());
        this.allocatedMemorySlider.setMajorTickSpacing(Math.max(
                1,
                LauncherSettings.MAXIMUM_ALLOCATED_MEMORY_GIGABYTES / 8
        ));
        this.allocatedMemorySlider.setMinorTickSpacing(1);
        Hashtable<Integer, JComponent> memoryLabels = new Hashtable<>();
        memoryLabels.put(
                LauncherSettings.MINIMUM_ALLOCATED_MEMORY_GIGABYTES,
                new JLabel(Integer.toString(LauncherSettings.MINIMUM_ALLOCATED_MEMORY_GIGABYTES))
        );
        memoryLabels.put(
                LauncherSettings.DEFAULT_ALLOCATED_MEMORY_GIGABYTES,
                new JLabel(Integer.toString(LauncherSettings.DEFAULT_ALLOCATED_MEMORY_GIGABYTES))
        );
        memoryLabels.put(
                LauncherSettings.MAXIMUM_ALLOCATED_MEMORY_GIGABYTES,
                new JLabel(Integer.toString(LauncherSettings.MAXIMUM_ALLOCATED_MEMORY_GIGABYTES))
        );
        this.allocatedMemorySlider.setLabelTable(memoryLabels);
        this.allocatedMemorySlider.setPaintTicks(true);
        this.allocatedMemorySlider.setPaintLabels(true);
        this.allocatedMemorySlider.setSnapToTicks(true);
        this.allocatedMemorySlider.setPreferredSize(new Dimension(520, 64));
        this.allocatedMemoryValueLabel.setFont(
                this.allocatedMemoryValueLabel.getFont().deriveFont(Font.BOLD, 16f)
        );
        this.allocatedMemoryValueLabel.setHorizontalAlignment(SwingConstants.CENTER);
        this.allocatedMemoryValueLabel.setText(
                Localization.text("settings.game.memory_value", this.allocatedMemorySlider.getValue())
        );

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints title = new GridBagConstraints();
        title.gridx = 0;
        title.gridy = 0;
        title.gridwidth = 2;
        title.weightx = 1;
        title.anchor = GridBagConstraints.LINE_START;
        title.insets = new Insets(0, 0, 16, 0);
        form.add(this.createHeader(), title);

        JPanel windowPanel = new JPanel(new GridBagLayout());
        windowPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(Localization.text("settings.game.window")),
                BorderFactory.createEmptyBorder(4, 10, 8, 10)
        ));

        GridBagConstraints fullscreen = new GridBagConstraints();
        fullscreen.gridx = 0;
        fullscreen.gridy = 0;
        fullscreen.gridwidth = 2;
        fullscreen.anchor = GridBagConstraints.LINE_START;
        fullscreen.insets = new Insets(0, 0, 14, 0);
        windowPanel.add(this.fullscreenCheckBox, fullscreen);

        GridBagConstraints widthLabel = new GridBagConstraints();
        widthLabel.gridx = 0;
        widthLabel.gridy = 1;
        widthLabel.anchor = GridBagConstraints.LINE_START;
        widthLabel.insets = new Insets(5, 0, 5, 14);
        windowPanel.add(new JLabel(Localization.text("settings.game.width")), widthLabel);

        GridBagConstraints width = new GridBagConstraints();
        width.gridx = 1;
        width.gridy = 1;
        width.weightx = 1;
        width.anchor = GridBagConstraints.LINE_START;
        width.insets = new Insets(5, 0, 5, 0);
        this.windowWidthSpinner.setPreferredSize(new Dimension(110, 28));
        windowPanel.add(this.windowWidthSpinner, width);

        GridBagConstraints heightLabel = new GridBagConstraints();
        heightLabel.gridx = 0;
        heightLabel.gridy = 2;
        heightLabel.anchor = GridBagConstraints.LINE_START;
        heightLabel.insets = new Insets(5, 0, 5, 14);
        windowPanel.add(new JLabel(Localization.text("settings.game.height")), heightLabel);

        GridBagConstraints height = new GridBagConstraints();
        height.gridx = 1;
        height.gridy = 2;
        height.weightx = 1;
        height.anchor = GridBagConstraints.LINE_START;
        height.insets = new Insets(5, 0, 5, 0);
        this.windowHeightSpinner.setPreferredSize(new Dimension(110, 28));
        windowPanel.add(this.windowHeightSpinner, height);

        GridBagConstraints window = new GridBagConstraints();
        window.gridx = 0;
        window.gridy = 1;
        window.gridwidth = 2;
        window.weightx = 1;
        window.fill = GridBagConstraints.HORIZONTAL;
        window.insets = new Insets(0, 0, 16, 0);
        form.add(windowPanel, window);

        JPanel memoryPanel = new JPanel(new BorderLayout(0, 6));
        memoryPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(Localization.text("settings.game.memory")),
                BorderFactory.createEmptyBorder(4, 10, 8, 10)
        ));
        memoryPanel.add(this.allocatedMemoryValueLabel, BorderLayout.NORTH);
        memoryPanel.add(this.allocatedMemorySlider, BorderLayout.CENTER);

        GridBagConstraints memory = new GridBagConstraints();
        memory.gridx = 0;
        memory.gridy = 2;
        memory.gridwidth = 2;
        memory.weightx = 1;
        memory.fill = GridBagConstraints.HORIZONTAL;
        memory.insets = new Insets(0, 0, 16, 0);
        form.add(memoryPanel, memory);

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

        this.fullscreenCheckBox.addActionListener(event -> this.updateControlState());
        this.windowWidthSpinner.addChangeListener(event -> this.updateControlState());
        this.windowHeightSpinner.addChangeListener(event -> this.updateControlState());
        this.allocatedMemorySlider.addChangeListener(event -> {
            this.allocatedMemoryValueLabel.setText(
                    Localization.text("settings.game.memory_value", this.allocatedMemorySlider.getValue())
            );
            this.updateControlState();
        });
        this.saveButton.addActionListener(event -> {
            try {
                launcherService.updateSettings(new LauncherSettings(
                        this.fullscreenCheckBox.isSelected(),
                        (Integer) this.windowWidthSpinner.getValue(),
                        (Integer) this.windowHeightSpinner.getValue(),
                        this.allocatedMemorySlider.getValue(),
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
                || savedSettings.windowHeight() != (Integer) this.windowHeightSpinner.getValue()
                || savedSettings.allocatedMemoryGigabytes() != this.allocatedMemorySlider.getValue();
        boolean windowed = !this.fullscreenCheckBox.isSelected();
        this.windowWidthSpinner.setEnabled(windowed);
        this.windowHeightSpinner.setEnabled(windowed);
        this.saveButton.setEnabled(changed);
    }
}
