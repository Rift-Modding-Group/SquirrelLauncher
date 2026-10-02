package com.anightdazingzoroark.squirrellauncher.ui.settingsControls;

import com.anightdazingzoroark.squirrellauncher.launcher.GameSettings;
import com.anightdazingzoroark.squirrellauncher.launcher.GarbageCollector;
import com.anightdazingzoroark.squirrellauncher.launcher.JvmArguments;
import com.anightdazingzoroark.squirrellauncher.minecraft.instance.InstanceLaunchSettings;
import com.anightdazingzoroark.squirrellauncher.ui.Localization;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Hashtable;
import java.util.List;

public final class BasicGameSettingsControls extends JPanel {
    @Nullable
    private final JCheckBox useDefaultWindowCheckBox;
    @Nullable
    private final JCheckBox useDefaultMemoryCheckBox;
    @Nullable
    private final JCheckBox useDefaultJvmCheckBox;
    @NotNull
    private final JCheckBox fullscreenCheckBox = new JCheckBox(Localization.text("settings.game.fullscreen"));
    @NotNull
    private final JSpinner windowWidthSpinner = new JSpinner(new SpinnerNumberModel(854, 320, 7680, 1));
    @NotNull
    private final JSpinner windowHeightSpinner = new JSpinner(new SpinnerNumberModel(480, 240, 4320, 1));
    @NotNull
    private final JSlider allocatedMemorySlider = new JSlider(
            GameSettings.MINIMUM_ALLOCATED_MEMORY_GIGABYTES,
            GameSettings.MAXIMUM_ALLOCATED_MEMORY_GIGABYTES,
            GameSettings.DEFAULT_ALLOCATED_MEMORY_GIGABYTES
    );
    @NotNull
    private final JLabel allocatedMemoryValueLabel = new JLabel();
    @NotNull
    private final JCheckBox lowMemoryWarningCheckBox = new JCheckBox(
            Localization.text("settings.game.low_memory_warning")
    );
    @NotNull
    private final JComboBox<GarbageCollector> garbageCollectorSelector = new JComboBox<>(new GarbageCollector[] {
            GarbageCollector.DEFAULT,
            GarbageCollector.G1,
            GarbageCollector.PARALLEL,
            GarbageCollector.SERIAL
    });
    @NotNull
    private GameSettings globalSettings = GameSettings.defaults();
    @NotNull
    private Runnable changeListener = () -> {};
    private boolean controlsAvailable = true;
    private boolean updatingControls;

    public BasicGameSettingsControls(boolean showDefaultSelectors) {
        super(new GridBagLayout());
        this.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        this.useDefaultWindowCheckBox = showDefaultSelectors
                ? new JCheckBox(Localization.text("instance.settings.use_default"))
                : null;
        this.useDefaultMemoryCheckBox = showDefaultSelectors
                ? new JCheckBox(Localization.text("instance.settings.use_default"))
                : null;
        this.useDefaultJvmCheckBox = showDefaultSelectors
                ? new JCheckBox(Localization.text("instance.settings.use_default"))
                : null;

        JPanel windowPanel = new JPanel(new GridBagLayout());
        windowPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(Localization.text("settings.game.window")),
                BorderFactory.createEmptyBorder(4, 10, 8, 10)
        ));
        int windowRow = 0;
        if (this.useDefaultWindowCheckBox != null) {
            GridBagConstraints useDefaultWindow = this.constraints(
                    0, windowRow++, 2, 0, GridBagConstraints.NONE
            );
            useDefaultWindow.anchor = GridBagConstraints.LINE_START;
            useDefaultWindow.insets = new Insets(0, 0, 10, 0);
            windowPanel.add(this.useDefaultWindowCheckBox, useDefaultWindow);
        }
        GridBagConstraints fullscreen = this.constraints(0, windowRow++, 2, 0, GridBagConstraints.NONE);
        fullscreen.anchor = GridBagConstraints.LINE_START;
        fullscreen.insets = new Insets(0, 0, 8, 0);
        windowPanel.add(this.fullscreenCheckBox, fullscreen);

        GridBagConstraints widthLabel = this.constraints(0, windowRow, 1, 0, GridBagConstraints.NONE);
        widthLabel.anchor = GridBagConstraints.LINE_START;
        widthLabel.insets = new Insets(4, 0, 4, 12);
        windowPanel.add(new JLabel(Localization.text("settings.game.width")), widthLabel);
        GridBagConstraints width = this.constraints(1, windowRow++, 1, 0, GridBagConstraints.NONE);
        width.weightx = 1;
        width.anchor = GridBagConstraints.LINE_START;
        width.insets = new Insets(4, 0, 4, 0);
        this.windowWidthSpinner.setPreferredSize(new Dimension(110, 28));
        windowPanel.add(this.windowWidthSpinner, width);

        GridBagConstraints heightLabel = this.constraints(0, windowRow, 1, 0, GridBagConstraints.NONE);
        heightLabel.anchor = GridBagConstraints.LINE_START;
        heightLabel.insets = new Insets(4, 0, 4, 12);
        windowPanel.add(new JLabel(Localization.text("settings.game.height")), heightLabel);
        GridBagConstraints height = this.constraints(1, windowRow, 1, 0, GridBagConstraints.NONE);
        height.weightx = 1;
        height.anchor = GridBagConstraints.LINE_START;
        height.insets = new Insets(4, 0, 4, 0);
        this.windowHeightSpinner.setPreferredSize(new Dimension(110, 28));
        windowPanel.add(this.windowHeightSpinner, height);
        this.add(windowPanel, this.constraints(0, 0, 1, 0, GridBagConstraints.HORIZONTAL));

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
                GameSettings.MINIMUM_ALLOCATED_MEMORY_GIGABYTES,
                new JLabel(Integer.toString(GameSettings.MINIMUM_ALLOCATED_MEMORY_GIGABYTES))
        );
        memoryLabels.put(
                GameSettings.DEFAULT_ALLOCATED_MEMORY_GIGABYTES,
                new JLabel(Integer.toString(GameSettings.DEFAULT_ALLOCATED_MEMORY_GIGABYTES))
        );
        memoryLabels.put(
                GameSettings.MAXIMUM_ALLOCATED_MEMORY_GIGABYTES,
                new JLabel(Integer.toString(GameSettings.MAXIMUM_ALLOCATED_MEMORY_GIGABYTES))
        );
        this.allocatedMemorySlider.setLabelTable(memoryLabels);
        this.allocatedMemorySlider.setMajorTickSpacing(Math.max(
                1,
                GameSettings.MAXIMUM_ALLOCATED_MEMORY_GIGABYTES / 8
        ));
        this.allocatedMemorySlider.setMinorTickSpacing(1);
        this.allocatedMemorySlider.setPaintTicks(true);
        this.allocatedMemorySlider.setPaintLabels(true);
        this.allocatedMemorySlider.setSnapToTicks(true);
        this.allocatedMemorySlider.setPreferredSize(new Dimension(500, 64));
        memoryControl.add(this.allocatedMemorySlider, BorderLayout.CENTER);
        memoryControl.add(this.lowMemoryWarningCheckBox, BorderLayout.SOUTH);
        memoryPanel.add(memoryControl, BorderLayout.CENTER);
        this.add(memoryPanel, this.constraints(0, 1, 1, 0, GridBagConstraints.HORIZONTAL));

        JPanel garbageCollectorPanel = new JPanel(new GridBagLayout());
        garbageCollectorPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(Localization.text("settings.game.garbage_collector")),
                BorderFactory.createEmptyBorder(4, 10, 8, 10)
        ));
        int garbageCollectorRow = 0;
        if (this.useDefaultJvmCheckBox != null) {
            GridBagConstraints useDefaultJvm = this.constraints(
                    0, garbageCollectorRow++, 2, 0, GridBagConstraints.HORIZONTAL
            );
            useDefaultJvm.anchor = GridBagConstraints.LINE_START;
            useDefaultJvm.insets = new Insets(0, 0, 8, 0);
            garbageCollectorPanel.add(this.useDefaultJvmCheckBox, useDefaultJvm);
        }
        GridBagConstraints garbageCollectorLabel = this.constraints(
                0, garbageCollectorRow, 1, 0, GridBagConstraints.NONE
        );
        garbageCollectorLabel.anchor = GridBagConstraints.LINE_START;
        garbageCollectorLabel.insets = new Insets(4, 0, 4, 12);
        garbageCollectorPanel.add(
                new JLabel(Localization.text("settings.game.garbage_collector.choice")),
                garbageCollectorLabel
        );
        GridBagConstraints garbageCollector = this.constraints(
                1, garbageCollectorRow, 1, 0, GridBagConstraints.HORIZONTAL
        );
        garbageCollector.weightx = 1;
        this.garbageCollectorSelector.setRenderer(new DefaultListCellRenderer() {
            @Override
            @NotNull
            public Component getListCellRendererComponent(
                    @NotNull JList<?> list,
                    @Nullable Object value,
                    int index,
                    boolean selected,
                    boolean focused
            ) {
                JLabel renderer = (JLabel) super.getListCellRendererComponent(
                        list, value, index, selected, focused
                );
                if (value instanceof GarbageCollector selectedCollector) {
                    renderer.setText(Localization.text(
                            "settings.game.garbage_collector."
                                    + selectedCollector.name().toLowerCase(java.util.Locale.ROOT)
                    ));
                }
                return renderer;
            }
        });
        garbageCollectorPanel.add(this.garbageCollectorSelector, garbageCollector);
        this.add(garbageCollectorPanel, this.constraints(0, 2, 1, 0, GridBagConstraints.HORIZONTAL));
        this.add(new JPanel(), this.constraints(0, 3, 1, 1, GridBagConstraints.VERTICAL));
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
        if (this.useDefaultJvmCheckBox != null) {
            this.useDefaultJvmCheckBox.addActionListener(event -> {
                if (this.updatingControls) return;
                if (this.useDefaultJvmCheckBox.isSelected()) this.setJvmArguments(this.globalSettings.jvmArguments());
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
        this.garbageCollectorSelector.addActionListener(event -> {
            if (!this.updatingControls) this.changeListener.run();
        });
        this.showGlobalSettings(this.globalSettings);
    }

    public void setChangeListener(@NotNull Runnable changeListener) {
        this.changeListener = changeListener;
    }

    public void showGlobalSettings(@NotNull GameSettings settings) {
        this.updatingControls = true;
        this.globalSettings = settings;
        if (this.useDefaultWindowCheckBox != null) this.useDefaultWindowCheckBox.setSelected(false);
        if (this.useDefaultMemoryCheckBox != null) this.useDefaultMemoryCheckBox.setSelected(false);
        if (this.useDefaultJvmCheckBox != null) this.useDefaultJvmCheckBox.setSelected(false);
        this.setWindowValues(settings.fullscreen(), settings.windowWidth(), settings.windowHeight());
        this.setMemoryValues(settings.allocatedMemoryGigabytes(), settings.lowMemoryWarning());
        this.setJvmArguments(settings.jvmArguments());
        this.updatingControls = false;
        this.updateControlState();
    }

    public void showInstanceSettings(
            @NotNull InstanceLaunchSettings settings,
            @NotNull GameSettings globalSettings
    ) {
        if (this.useDefaultWindowCheckBox == null
                || this.useDefaultMemoryCheckBox == null
                || this.useDefaultJvmCheckBox == null) {
            throw new IllegalStateException("Instance settings require default selectors.");
        }
        this.updatingControls = true;
        this.globalSettings = globalSettings;
        this.useDefaultWindowCheckBox.setSelected(!settings.overrideWindowSettings());
        this.useDefaultMemoryCheckBox.setSelected(!settings.overrideMemory());
        this.useDefaultJvmCheckBox.setSelected(!settings.overrideJvmArguments());
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
        this.setJvmArguments(settings.overrideJvmArguments() ? settings.jvmArguments() : globalSettings.jvmArguments());
        this.updatingControls = false;
        this.updateControlState();
    }

    public void setAvailable(boolean available) {
        this.controlsAvailable = available;
        this.updateControlState();
    }

    public void resetToDefaults() {
        this.updatingControls = true;
        if (this.useDefaultWindowCheckBox == null
                || this.useDefaultMemoryCheckBox == null
                || this.useDefaultJvmCheckBox == null) {
            GameSettings defaults = GameSettings.defaults();
            this.setWindowValues(defaults.fullscreen(), defaults.windowWidth(), defaults.windowHeight());
            this.setMemoryValues(defaults.allocatedMemoryGigabytes(), defaults.lowMemoryWarning());
            this.setJvmArguments(defaults.jvmArguments());
        }
        else {
            this.useDefaultWindowCheckBox.setSelected(true);
            this.useDefaultMemoryCheckBox.setSelected(true);
            this.useDefaultJvmCheckBox.setSelected(true);
            this.setWindowValues(
                    this.globalSettings.fullscreen(),
                    this.globalSettings.windowWidth(),
                    this.globalSettings.windowHeight()
            );
            this.setMemoryValues(
                    this.globalSettings.allocatedMemoryGigabytes(),
                    this.globalSettings.lowMemoryWarning()
            );
            this.setJvmArguments(this.globalSettings.jvmArguments());
        }
        this.updatingControls = false;
        this.updateControlState();
        this.changeListener.run();
    }

    public void applyAdvancedArguments(@NotNull List<String> arguments) {
        this.updatingControls = true;
        if (this.useDefaultMemoryCheckBox != null) this.useDefaultMemoryCheckBox.setSelected(false);
        if (this.useDefaultJvmCheckBox != null) this.useDefaultJvmCheckBox.setSelected(false);
        this.setMemoryValues(
                JvmArguments.maximumMemoryGigabytes(arguments),
                this.lowMemoryWarningCheckBox.isSelected()
        );
        this.setJvmArguments(arguments);
        this.updatingControls = false;
        this.updateControlState();
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

    public boolean overrideJvmArguments() {
        return this.useDefaultJvmCheckBox == null || !this.useDefaultJvmCheckBox.isSelected();
    }

    public void setUseDefaultJvmArguments(boolean useDefaults) {
        if (this.useDefaultJvmCheckBox == null) {
            if (useDefaults) throw new IllegalStateException("Global settings cannot use inherited JVM arguments.");
            return;
        }
        this.updatingControls = true;
        this.useDefaultJvmCheckBox.setSelected(useDefaults);
        if (useDefaults) this.setJvmArguments(this.globalSettings.jvmArguments());
        this.updatingControls = false;
        this.updateControlState();
    }

    @NotNull
    public GarbageCollector garbageCollector() {
        GarbageCollector selected = (GarbageCollector) this.garbageCollectorSelector.getSelectedItem();
        return selected == null ? GarbageCollector.DEFAULT : selected;
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

    private void setJvmArguments(@NotNull List<String> arguments) {
        boolean previousUpdating = this.updatingControls;
        this.updatingControls = true;
        for (GarbageCollector garbageCollector : GarbageCollector.values()) {
            if (!garbageCollector.matches(arguments)) continue;
            this.garbageCollectorSelector.setSelectedItem(garbageCollector);
            break;
        }
        this.updatingControls = previousUpdating;
    }

    private void updateControlState() {
        boolean customWindow = this.controlsAvailable && this.overrideWindowSettings();
        boolean customMemory = this.controlsAvailable && this.overrideMemory();
        boolean customJvmArguments = this.controlsAvailable && this.overrideJvmArguments();
        if (this.useDefaultWindowCheckBox != null) this.useDefaultWindowCheckBox.setEnabled(this.controlsAvailable);
        if (this.useDefaultMemoryCheckBox != null) this.useDefaultMemoryCheckBox.setEnabled(this.controlsAvailable);
        if (this.useDefaultJvmCheckBox != null) this.useDefaultJvmCheckBox.setEnabled(this.controlsAvailable);
        this.fullscreenCheckBox.setEnabled(customWindow);
        this.windowWidthSpinner.setEnabled(customWindow && !this.fullscreenCheckBox.isSelected());
        this.windowHeightSpinner.setEnabled(customWindow && !this.fullscreenCheckBox.isSelected());
        this.allocatedMemorySlider.setEnabled(customMemory);
        this.lowMemoryWarningCheckBox.setEnabled(customMemory);
        this.garbageCollectorSelector.setEnabled(customJvmArguments);
    }

    @NotNull
    private GridBagConstraints constraints(int x, int y, int width, double weightY, int fill) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = x;
        constraints.gridy = y;
        constraints.gridwidth = width;
        constraints.weightx = 1;
        constraints.weighty = weightY;
        constraints.fill = fill;
        constraints.anchor = GridBagConstraints.FIRST_LINE_START;
        constraints.insets = new Insets(0, 0, 12, 0);
        return constraints;
    }
}
