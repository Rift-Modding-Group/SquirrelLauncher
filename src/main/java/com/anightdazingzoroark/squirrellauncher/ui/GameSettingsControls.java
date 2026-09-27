package com.anightdazingzoroark.squirrellauncher.ui;

import com.anightdazingzoroark.squirrellauncher.launcher.LauncherSettings;
import com.anightdazingzoroark.squirrellauncher.minecraft.instance.InstanceLaunchSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Hashtable;

//widgets used in SettingsDialog and InstanceSettingsTab
public final class GameSettingsControls extends JPanel {
    @Nullable
    private final JCheckBox useDefaultWindowCheckBox;
    @Nullable
    private final JCheckBox useDefaultMemoryCheckBox;
    @NotNull
    private final JCheckBox fullscreenCheckBox = new JCheckBox(Localization.text("settings.game.fullscreen"));
    @NotNull
    private final JSpinner windowWidthSpinner = new JSpinner(new SpinnerNumberModel(854, 320, 7680, 1));
    @NotNull
    private final JSpinner windowHeightSpinner = new JSpinner(new SpinnerNumberModel(480, 240, 4320, 1));
    @NotNull
    private final JSlider allocatedMemorySlider = new JSlider(
            LauncherSettings.MINIMUM_ALLOCATED_MEMORY_GIGABYTES,
            LauncherSettings.MAXIMUM_ALLOCATED_MEMORY_GIGABYTES,
            LauncherSettings.DEFAULT_ALLOCATED_MEMORY_GIGABYTES
    );
    @NotNull
    private final JLabel allocatedMemoryValueLabel = new JLabel();
    @NotNull
    private final JCheckBox lowMemoryWarningCheckBox = new JCheckBox(
            Localization.text("settings.game.low_memory_warning")
    );
    @NotNull
    private final JButton resetDefaultsButton = new JButton(Localization.text("settings.button.reset_defaults"));
    @NotNull
    private LauncherSettings globalSettings = LauncherSettings.defaults();
    @NotNull
    private Runnable changeListener = () -> {};
    private boolean controlsAvailable = true;
    private boolean updatingControls;

    public GameSettingsControls(boolean showDefaultSelectors) {
        super(new GridBagLayout());
        this.useDefaultWindowCheckBox = showDefaultSelectors
                ? new JCheckBox(Localization.text("instance.settings.use_default"))
                : null;
        this.useDefaultMemoryCheckBox = showDefaultSelectors
                ? new JCheckBox(Localization.text("instance.settings.use_default"))
                : null;

        JPanel windowPanel = new JPanel(new GridBagLayout());
        windowPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(Localization.text("settings.game.window")),
                BorderFactory.createEmptyBorder(4, 10, 8, 10)
        ));
        int windowRow = 0;
        if (this.useDefaultWindowCheckBox != null) {
            GridBagConstraints useDefaultWindow = new GridBagConstraints();
            useDefaultWindow.gridx = 0;
            useDefaultWindow.gridy = windowRow++;
            useDefaultWindow.gridwidth = 2;
            useDefaultWindow.anchor = GridBagConstraints.LINE_START;
            useDefaultWindow.insets = new Insets(0, 0, 10, 0);
            windowPanel.add(this.useDefaultWindowCheckBox, useDefaultWindow);
        }

        GridBagConstraints fullscreen = new GridBagConstraints();
        fullscreen.gridx = 0;
        fullscreen.gridy = windowRow++;
        fullscreen.gridwidth = 2;
        fullscreen.anchor = GridBagConstraints.LINE_START;
        fullscreen.insets = new Insets(0, 0, 8, 0);
        windowPanel.add(this.fullscreenCheckBox, fullscreen);

        GridBagConstraints widthLabel = new GridBagConstraints();
        widthLabel.gridx = 0;
        widthLabel.gridy = windowRow;
        widthLabel.anchor = GridBagConstraints.LINE_START;
        widthLabel.insets = new Insets(4, 0, 4, 12);
        windowPanel.add(new JLabel(Localization.text("settings.game.width")), widthLabel);
        GridBagConstraints width = new GridBagConstraints();
        width.gridx = 1;
        width.gridy = windowRow++;
        width.weightx = 1;
        width.anchor = GridBagConstraints.LINE_START;
        width.insets = new Insets(4, 0, 4, 0);
        this.windowWidthSpinner.setPreferredSize(new Dimension(110, 28));
        windowPanel.add(this.windowWidthSpinner, width);

        GridBagConstraints heightLabel = new GridBagConstraints();
        heightLabel.gridx = 0;
        heightLabel.gridy = windowRow;
        heightLabel.anchor = GridBagConstraints.LINE_START;
        heightLabel.insets = new Insets(4, 0, 4, 12);
        windowPanel.add(new JLabel(Localization.text("settings.game.height")), heightLabel);
        GridBagConstraints height = new GridBagConstraints();
        height.gridx = 1;
        height.gridy = windowRow;
        height.weightx = 1;
        height.anchor = GridBagConstraints.LINE_START;
        height.insets = new Insets(4, 0, 4, 0);
        this.windowHeightSpinner.setPreferredSize(new Dimension(110, 28));
        windowPanel.add(this.windowHeightSpinner, height);

        GridBagConstraints window = new GridBagConstraints();
        window.gridx = 0;
        window.gridy = 0;
        window.weightx = 1;
        window.fill = GridBagConstraints.HORIZONTAL;
        window.insets = new Insets(0, 0, 12, 0);
        this.add(windowPanel, window);

        JPanel memoryPanel = new JPanel(new BorderLayout(0, 6));
        memoryPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(Localization.text("settings.game.memory")),
                BorderFactory.createEmptyBorder(4, 10, 8, 10)
        ));
        if (this.useDefaultMemoryCheckBox != null) memoryPanel.add(this.useDefaultMemoryCheckBox, BorderLayout.NORTH);
        JPanel memoryControl = new JPanel(new BorderLayout(0, 4));
        this.allocatedMemoryValueLabel.setFont(
                this.allocatedMemoryValueLabel.getFont().deriveFont(Font.BOLD, 16f)
        );
        this.allocatedMemoryValueLabel.setHorizontalAlignment(SwingConstants.CENTER);
        memoryControl.add(this.allocatedMemoryValueLabel, BorderLayout.NORTH);
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
        this.allocatedMemorySlider.setMajorTickSpacing(Math.max(
                1,
                LauncherSettings.MAXIMUM_ALLOCATED_MEMORY_GIGABYTES / 8
        ));
        this.allocatedMemorySlider.setMinorTickSpacing(1);
        this.allocatedMemorySlider.setPaintTicks(true);
        this.allocatedMemorySlider.setPaintLabels(true);
        this.allocatedMemorySlider.setSnapToTicks(true);
        this.allocatedMemorySlider.setPreferredSize(new Dimension(500, 64));
        memoryControl.add(this.allocatedMemorySlider, BorderLayout.CENTER);
        memoryControl.add(this.lowMemoryWarningCheckBox, BorderLayout.SOUTH);
        memoryPanel.add(memoryControl, BorderLayout.CENTER);

        GridBagConstraints memory = new GridBagConstraints();
        memory.gridx = 0;
        memory.gridy = 1;
        memory.weightx = 1;
        memory.fill = GridBagConstraints.HORIZONTAL;
        this.add(memoryPanel, memory);

        int nextRow = 2;
        GridBagConstraints filler = new GridBagConstraints();
        filler.gridx = 0;
        filler.gridy = nextRow++;
        filler.weighty = 1;
        filler.fill = GridBagConstraints.VERTICAL;
        this.add(new JPanel(), filler);

        if (!showDefaultSelectors) {
            JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
            actions.add(this.resetDefaultsButton);
            GridBagConstraints resetDefaults = new GridBagConstraints();
            resetDefaults.gridx = 0;
            resetDefaults.gridy = nextRow;
            resetDefaults.weightx = 1;
            resetDefaults.fill = GridBagConstraints.HORIZONTAL;
            resetDefaults.insets = new Insets(12, 0, 0, 0);
            this.add(actions, resetDefaults);
        }

        if (this.useDefaultWindowCheckBox != null) {
            this.useDefaultWindowCheckBox.addActionListener(event -> {
                if (this.updatingControls) return;
                if (this.useDefaultWindowCheckBox.isSelected()) {
                    this.setWindowValues(
                            this.globalSettings.fullscreen(),
                            this.globalSettings.windowWidth(),
                            this.globalSettings.windowHeight()
                    );
                }
                this.updateControlState();
                this.changeListener.run();
            });
        }
        if (this.useDefaultMemoryCheckBox != null) {
            this.useDefaultMemoryCheckBox.addActionListener(event -> {
                if (this.updatingControls) return;
                if (this.useDefaultMemoryCheckBox.isSelected()) {
                    this.setMemoryValues(
                            this.globalSettings.allocatedMemoryGigabytes(),
                            this.globalSettings.lowMemoryWarning()
                    );
                }
                this.updateControlState();
                this.changeListener.run();
            });
        }
        this.fullscreenCheckBox.addActionListener(event -> {
            if (this.updatingControls) return;
            this.updateControlState();
            this.changeListener.run();
        });
        this.windowWidthSpinner.addChangeListener(event -> {
            if (!this.updatingControls) this.changeListener.run();
        });
        this.windowHeightSpinner.addChangeListener(event -> {
            if (!this.updatingControls) this.changeListener.run();
        });
        this.allocatedMemorySlider.addChangeListener(event -> {
            this.allocatedMemoryValueLabel.setText(Localization.text(
                    "settings.game.memory_value",
                    this.allocatedMemorySlider.getValue()
            ));
            if (!this.updatingControls && !this.allocatedMemorySlider.getValueIsAdjusting()) {
                this.changeListener.run();
            }
        });
        this.lowMemoryWarningCheckBox.addActionListener(event -> {
            if (!this.updatingControls) this.changeListener.run();
        });
        this.resetDefaultsButton.addActionListener(event -> this.resetToDefaults());
        this.showGlobalSettings(this.globalSettings);
    }

    public void setChangeListener(@NotNull Runnable changeListener) {
        this.changeListener = changeListener;
    }

    public void showGlobalSettings(@NotNull LauncherSettings settings) {
        this.updatingControls = true;
        this.globalSettings = settings;
        if (this.useDefaultWindowCheckBox != null) this.useDefaultWindowCheckBox.setSelected(false);
        if (this.useDefaultMemoryCheckBox != null) this.useDefaultMemoryCheckBox.setSelected(false);
        this.setWindowValues(settings.fullscreen(), settings.windowWidth(), settings.windowHeight());
        this.setMemoryValues(settings.allocatedMemoryGigabytes(), settings.lowMemoryWarning());
        this.updatingControls = false;
        this.updateControlState();
    }

    public void showInstanceSettings(
            @NotNull InstanceLaunchSettings settings,
            @NotNull LauncherSettings globalSettings
    ) {
        if (this.useDefaultWindowCheckBox == null || this.useDefaultMemoryCheckBox == null) {
            throw new IllegalStateException("Instance settings require default selectors.");
        }
        this.updatingControls = true;
        this.globalSettings = globalSettings;
        this.useDefaultWindowCheckBox.setSelected(!settings.overrideWindowSettings());
        this.useDefaultMemoryCheckBox.setSelected(!settings.overrideMemory());
        this.setWindowValues(
                settings.overrideWindowSettings() ? settings.fullscreen() : globalSettings.fullscreen(),
                settings.overrideWindowSettings() ? settings.windowWidth() : globalSettings.windowWidth(),
                settings.overrideWindowSettings() ? settings.windowHeight() : globalSettings.windowHeight()
        );
        this.setMemoryValues(
                settings.overrideMemory()
                        ? settings.allocatedMemoryGigabytes()
                        : globalSettings.allocatedMemoryGigabytes(),
                settings.overrideMemory() ? settings.lowMemoryWarning() : globalSettings.lowMemoryWarning()
        );
        this.updatingControls = false;
        this.updateControlState();
    }

    public void setAvailable(boolean available) {
        this.controlsAvailable = available;
        this.updateControlState();
    }

    public void resetToDefaults() {
        this.updatingControls = true;
        if (this.useDefaultWindowCheckBox == null || this.useDefaultMemoryCheckBox == null) {
            LauncherSettings defaults = LauncherSettings.defaults();
            this.setWindowValues(defaults.fullscreen(), defaults.windowWidth(), defaults.windowHeight());
            this.setMemoryValues(defaults.allocatedMemoryGigabytes(), defaults.lowMemoryWarning());
        }
        else {
            this.useDefaultWindowCheckBox.setSelected(true);
            this.useDefaultMemoryCheckBox.setSelected(true);
            this.setWindowValues(
                    this.globalSettings.fullscreen(),
                    this.globalSettings.windowWidth(),
                    this.globalSettings.windowHeight()
            );
            this.setMemoryValues(
                    this.globalSettings.allocatedMemoryGigabytes(),
                    this.globalSettings.lowMemoryWarning()
            );
        }
        this.updatingControls = false;
        this.updateControlState();
        this.changeListener.run();
    }

    public boolean overrideWindowSettings() {
        return this.useDefaultWindowCheckBox == null || !this.useDefaultWindowCheckBox.isSelected();
    }

    public boolean fullscreen() {
        return this.fullscreenCheckBox.isSelected();
    }

    public int windowWidth() {
        return (Integer) this.windowWidthSpinner.getValue();
    }

    public int windowHeight() {
        return (Integer) this.windowHeightSpinner.getValue();
    }

    public boolean overrideMemory() {
        return this.useDefaultMemoryCheckBox == null || !this.useDefaultMemoryCheckBox.isSelected();
    }

    public int allocatedMemoryGigabytes() {
        return this.allocatedMemorySlider.getValue();
    }

    public boolean lowMemoryWarning() {
        return this.lowMemoryWarningCheckBox.isSelected();
    }

    private void setWindowValues(boolean fullscreen, int width, int height) {
        boolean previousUpdating = this.updatingControls;
        this.updatingControls = true;
        this.fullscreenCheckBox.setSelected(fullscreen);
        this.windowWidthSpinner.setValue(width);
        this.windowHeightSpinner.setValue(height);
        this.updatingControls = previousUpdating;
    }

    private void setMemoryValues(int allocatedMemoryGigabytes, boolean lowMemoryWarning) {
        boolean previousUpdating = this.updatingControls;
        this.updatingControls = true;
        this.allocatedMemorySlider.setValue(allocatedMemoryGigabytes);
        this.allocatedMemoryValueLabel.setText(Localization.text(
                "settings.game.memory_value",
                allocatedMemoryGigabytes
        ));
        this.lowMemoryWarningCheckBox.setSelected(lowMemoryWarning);
        this.updatingControls = previousUpdating;
    }

    private void updateControlState() {
        boolean customWindow = this.controlsAvailable && this.overrideWindowSettings();
        boolean customMemory = this.controlsAvailable && this.overrideMemory();
        if (this.useDefaultWindowCheckBox != null) this.useDefaultWindowCheckBox.setEnabled(this.controlsAvailable);
        if (this.useDefaultMemoryCheckBox != null) this.useDefaultMemoryCheckBox.setEnabled(this.controlsAvailable);
        this.fullscreenCheckBox.setEnabled(customWindow);
        this.windowWidthSpinner.setEnabled(customWindow && !this.fullscreenCheckBox.isSelected());
        this.windowHeightSpinner.setEnabled(customWindow && !this.fullscreenCheckBox.isSelected());
        this.allocatedMemorySlider.setEnabled(customMemory);
        this.lowMemoryWarningCheckBox.setEnabled(customMemory);
        this.resetDefaultsButton.setEnabled(this.controlsAvailable);
    }
}
