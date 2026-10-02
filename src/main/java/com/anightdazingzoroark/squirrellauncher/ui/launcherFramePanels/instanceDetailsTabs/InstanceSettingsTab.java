package com.anightdazingzoroark.squirrellauncher.ui.launcherFramePanels.instanceDetailsTabs;

import com.anightdazingzoroark.squirrellauncher.launcher.GameSettings;
import com.anightdazingzoroark.squirrellauncher.minecraft.instance.InstanceLaunchSettings;
import com.anightdazingzoroark.squirrellauncher.minecraft.instance.InstanceType;
import com.anightdazingzoroark.squirrellauncher.minecraft.instance.MinecraftInstance;
import com.anightdazingzoroark.squirrellauncher.minecraft.runtime.JavaRuntime;
import com.anightdazingzoroark.squirrellauncher.minecraft.runtime.JavaVersion;
import com.anightdazingzoroark.squirrellauncher.ui.settingsControls.GameSettingsControls;
import com.anightdazingzoroark.squirrellauncher.ui.LauncherActions;
import com.anightdazingzoroark.squirrellauncher.ui.Localization;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public final class InstanceSettingsTab extends JPanel {
    @NotNull
    private final JComboBox<JavaRuntimeChoice> javaRuntimeSelector = new JComboBox<>();
    @NotNull
    private final JButton resetDefaultsButton = new JButton(Localization.text("settings.button.reset_defaults"));
    @NotNull
    private final JButton cancelJvmArgumentsButton = new JButton(Localization.text("settings.button.cancel"));
    @NotNull
    private final JButton saveJvmArgumentsButton = new JButton(Localization.text("settings.button.save"));
    @NotNull
    private final GameSettingsControls gameSettingsControls = new GameSettingsControls(true);
    @NotNull
    private final LauncherActions launcherActions;
    @NotNull
    private final Map<String, InstanceLaunchSettings> queuedSettings = new LinkedHashMap<>();
    @NotNull
    private InstanceLaunchSettings savedSettings = InstanceLaunchSettings.defaults();
    @Nullable
    private Path selectedJavaExecutable;
    @Nullable
    private String displayedInstanceId;
    @NotNull
    private JavaVersion requiredJavaVersion = JavaVersion.JAVA_8;
    private int javaDetectionGeneration;
    private boolean detectingJavaRuntimes;
    private boolean javaRuntimeSelectionAvailable;
    private boolean controlsAvailable;
    private boolean updatingControls;
    private boolean saveInProgress;

    public InstanceSettingsTab(@NotNull LauncherActions launcherActions) {
        super(new BorderLayout(0, 8));
        this.launcherActions = launcherActions;
        this.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        this.javaRuntimeSelector.setMaximumRowCount(10);

        JPanel form = new JPanel(new GridBagLayout());
        JPanel javaPanel = new JPanel(new BorderLayout());
        javaPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(Localization.text("instance.settings.java.heading")),
                BorderFactory.createEmptyBorder(6, 10, 10, 10)
        ));
        javaPanel.add(this.javaRuntimeSelector, BorderLayout.CENTER);
        GridBagConstraints java = new GridBagConstraints();
        java.gridx = 0;
        java.gridy = 0;
        java.weightx = 1;
        java.fill = GridBagConstraints.HORIZONTAL;
        java.anchor = GridBagConstraints.FIRST_LINE_START;
        java.insets = new Insets(0, 0, 12, 0);
        form.add(javaPanel, java);

        GridBagConstraints gameSettings = new GridBagConstraints();
        gameSettings.gridx = 0;
        gameSettings.gridy = 1;
        gameSettings.weightx = 1;
        gameSettings.weighty = 1;
        gameSettings.fill = GridBagConstraints.BOTH;
        form.add(this.gameSettingsControls, gameSettings);
        this.add(form, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        actions.add(this.resetDefaultsButton);
        actions.add(this.cancelJvmArgumentsButton);
        actions.add(this.saveJvmArgumentsButton);
        this.add(actions, BorderLayout.SOUTH);

        this.gameSettingsControls.setChangeListener(this::scheduleSave);
        this.gameSettingsControls.setAdvancedActionsAvailableListener(available -> {
            this.cancelJvmArgumentsButton.setEnabled(available);
            this.saveJvmArgumentsButton.setEnabled(available);
        });
        this.gameSettingsControls.setAdvancedDisplayedListener(displayed -> {
            this.cancelJvmArgumentsButton.setVisible(displayed);
            this.saveJvmArgumentsButton.setVisible(displayed);
        });
        this.cancelJvmArgumentsButton.addActionListener(event -> this.gameSettingsControls.cancelAdvancedChanges());
        this.saveJvmArgumentsButton.addActionListener(event -> this.gameSettingsControls.saveAdvancedChanges());
        this.javaRuntimeSelector.addActionListener(event -> {
            if (this.updatingControls || this.detectingJavaRuntimes || !this.javaRuntimeSelectionAvailable) return;
            JavaRuntimeChoice choice = (JavaRuntimeChoice) this.javaRuntimeSelector.getSelectedItem();
            if (choice == null) return;
            this.selectedJavaExecutable = choice.executable();
            this.scheduleSave();
        });
        this.resetDefaultsButton.addActionListener(event -> {
            this.selectedJavaExecutable = null;
            this.gameSettingsControls.resetToDefaults();
            this.scheduleSave();
            this.detectJavaRuntimes();
        });
        this.clearDisplayedInstance();
    }

    public void showInstance(@NotNull MinecraftInstance instance, @NotNull GameSettings globalSettings) {
        if (instance.id().equals(this.displayedInstanceId) && !this.currentSettings().equals(this.savedSettings)) return;
        if (this.displayedInstanceId != null && !this.currentSettings().equals(this.savedSettings)) {
            this.queueSave(this.displayedInstanceId, this.currentSettings());
        }
        this.updatingControls = true;
        this.displayedInstanceId = instance.id();
        this.requiredJavaVersion = instance.type() == InstanceType.CLEANROOM
                ? JavaVersion.JAVA_25
                : JavaVersion.JAVA_8;
        this.savedSettings = instance.launchSettings();
        this.selectedJavaExecutable = this.savedSettings.javaExecutable();
        this.javaDetectionGeneration++;
        this.detectingJavaRuntimes = false;
        this.javaRuntimeSelectionAvailable = false;
        this.gameSettingsControls.showInstanceSettings(this.savedSettings, globalSettings);
        this.updatingControls = false;
        this.updateControlState();
        this.detectJavaRuntimes();
    }

    public void clearDisplayedInstance() {
        this.updatingControls = true;
        this.displayedInstanceId = null;
        this.selectedJavaExecutable = null;
        this.javaDetectionGeneration++;
        this.detectingJavaRuntimes = false;
        this.javaRuntimeSelectionAvailable = false;
        this.controlsAvailable = false;
        this.javaRuntimeSelector.removeAllItems();
        this.updatingControls = false;
        this.updateControlState();
    }

    public void settingsSaveFinished(
            @NotNull String instanceId,
            @NotNull InstanceLaunchSettings attemptedSettings,
            @Nullable MinecraftInstance updatedInstance
    ) {
        this.saveInProgress = false;
        if (updatedInstance != null && instanceId.equals(this.displayedInstanceId)) {
            InstanceLaunchSettings currentSettings = this.currentSettings();
            this.savedSettings = updatedInstance.launchSettings();
            if (currentSettings.equals(attemptedSettings)) {
                boolean normalizedJavaPath = !java.util.Objects.equals(
                        this.selectedJavaExecutable,
                        this.savedSettings.javaExecutable()
                );
                this.selectedJavaExecutable = this.savedSettings.javaExecutable();
                if (normalizedJavaPath) this.detectJavaRuntimes();
            }
        }
        if (updatedInstance != null
                && this.displayedInstanceId != null
                && !this.currentSettings().equals(this.savedSettings)) {
            this.queuedSettings.put(this.displayedInstanceId, this.currentSettings());
        }
        this.startNextSave();
    }

    public void updateControlState(boolean available) {
        this.controlsAvailable = available && this.displayedInstanceId != null;
        this.updateControlState();
    }

    public boolean confirmDiscardUnsavedChanges() {
        return this.gameSettingsControls.confirmDiscardUnsavedChanges();
    }

    @Nullable
    public String displayedInstanceId() {
        return this.displayedInstanceId;
    }

    private void updateControlState() {
        this.javaRuntimeSelector.setEnabled(
                this.controlsAvailable && !this.detectingJavaRuntimes && this.javaRuntimeSelectionAvailable
        );
        this.resetDefaultsButton.setEnabled(this.controlsAvailable);
        this.gameSettingsControls.setAvailable(this.controlsAvailable);
    }

    @NotNull
    private InstanceLaunchSettings currentSettings() {
        return new InstanceLaunchSettings(
                this.selectedJavaExecutable,
                this.gameSettingsControls.overrideWindowSettings(),
                this.gameSettingsControls.fullscreen(),
                this.gameSettingsControls.windowWidth(),
                this.gameSettingsControls.windowHeight(),
                this.gameSettingsControls.overrideMemory(),
                this.gameSettingsControls.allocatedMemoryGigabytes(),
                this.gameSettingsControls.lowMemoryWarning(),
                this.gameSettingsControls.overrideJvmArguments(),
                this.gameSettingsControls.jvmArguments()
        );
    }

    private void scheduleSave() {
        if (this.updatingControls || this.displayedInstanceId == null) return;
        InstanceLaunchSettings settings = this.currentSettings();
        if (!settings.equals(this.savedSettings)) this.queueSave(this.displayedInstanceId, settings);
    }

    private void queueSave(@NotNull String instanceId, @NotNull InstanceLaunchSettings settings) {
        this.queuedSettings.put(instanceId, settings);
        this.startNextSave();
    }

    private void startNextSave() {
        if (this.saveInProgress || this.queuedSettings.isEmpty()) return;
        Map.Entry<String, InstanceLaunchSettings> pendingSave = this.queuedSettings.entrySet().iterator().next();
        String instanceId = pendingSave.getKey();
        InstanceLaunchSettings settings = pendingSave.getValue();
        this.queuedSettings.remove(instanceId);
        this.saveInProgress = true;
        this.launcherActions.saveInstanceSettingsRequested(instanceId, settings);
    }

    private void detectJavaRuntimes() {
        if (this.displayedInstanceId == null) return;
        int generation = ++this.javaDetectionGeneration;
        JavaVersion requiredVersion = this.requiredJavaVersion;
        Path configuredRuntime = this.selectedJavaExecutable;
        this.detectingJavaRuntimes = true;
        this.javaRuntimeSelectionAvailable = false;
        this.updatingControls = true;
        this.javaRuntimeSelector.removeAllItems();
        this.javaRuntimeSelector.addItem(new JavaRuntimeChoice(
                null,
                Localization.text("instance.settings.java.detecting", requiredVersion.major())
        ));
        this.updatingControls = false;
        this.updateControlState();
        new SwingWorker<List<JavaRuntime>, Void>() {
            @NotNull
            @Override
            protected List<JavaRuntime> doInBackground() {
                return InstanceSettingsTab.this.launcherActions.detectJavaRuntimes(
                        requiredVersion,
                        configuredRuntime
                );
            }

            @Override
            protected void done() {
                if (generation != InstanceSettingsTab.this.javaDetectionGeneration) return;
                InstanceSettingsTab.this.detectingJavaRuntimes = false;
                InstanceSettingsTab.this.updatingControls = true;
                InstanceSettingsTab.this.javaRuntimeSelector.removeAllItems();
                List<JavaRuntime> runtimes;
                try {
                    runtimes = this.get();
                }
                catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    InstanceSettingsTab.this.updatingControls = false;
                    InstanceSettingsTab.this.updateControlState();
                    return;
                }
                catch (ExecutionException exception) {
                    InstanceSettingsTab.this.javaRuntimeSelector.addItem(new JavaRuntimeChoice(
                            null,
                            Localization.text("instance.settings.java.detect_error")
                    ));
                    InstanceSettingsTab.this.updatingControls = false;
                    InstanceSettingsTab.this.updateControlState();
                    return;
                }

                if (runtimes.isEmpty()) {
                    InstanceSettingsTab.this.javaRuntimeSelector.addItem(new JavaRuntimeChoice(
                            configuredRuntime,
                            configuredRuntime == null
                                    ? Localization.text(
                                            "instance.settings.java.none_detected",
                                            requiredVersion.major()
                                    )
                                    : Localization.text("instance.settings.java.invalid_runtime", configuredRuntime)
                    ));
                    InstanceSettingsTab.this.updatingControls = false;
                    InstanceSettingsTab.this.updateControlState();
                    return;
                }

                int selectedIndex = -1;
                if (configuredRuntime == null) {
                    InstanceSettingsTab.this.javaRuntimeSelector.addItem(new JavaRuntimeChoice(
                            null,
                            InstanceSettingsTab.this.javaRuntimeText(runtimes.getFirst(), true)
                    ));
                    selectedIndex = 0;
                }
                else {
                    for (int index = 0; index < runtimes.size(); index++) {
                        JavaRuntime runtime = runtimes.get(index);
                        try {
                            if (!Files.isSameFile(configuredRuntime, runtime.executable())) continue;
                            selectedIndex = index;
                            break;
                        }
                        catch (java.io.IOException | SecurityException ignored) {}
                    }
                    if (selectedIndex < 0) {
                        InstanceSettingsTab.this.javaRuntimeSelector.addItem(new JavaRuntimeChoice(
                                configuredRuntime,
                                Localization.text("instance.settings.java.invalid_runtime", configuredRuntime)
                        ));
                        selectedIndex = 0;
                    }
                }
                for (JavaRuntime runtime : runtimes) {
                    InstanceSettingsTab.this.javaRuntimeSelector.addItem(new JavaRuntimeChoice(
                            runtime.executable(),
                            InstanceSettingsTab.this.javaRuntimeText(runtime, false)
                    ));
                }
                InstanceSettingsTab.this.javaRuntimeSelector.setSelectedIndex(selectedIndex);
                InstanceSettingsTab.this.javaRuntimeSelectionAvailable = true;
                InstanceSettingsTab.this.updatingControls = false;
                InstanceSettingsTab.this.updateControlState();
            }
        }.execute();
    }

    @NotNull
    private String javaRuntimeText(@NotNull JavaRuntime runtime, boolean automatic) {
        return Localization.text(
                automatic
                        ? "instance.settings.java.automatic_runtime"
                        : "instance.settings.java.selected_runtime",
                runtime.version().major(),
                runtime.detectedVersion(),
                runtime.executable()
        );
    }

    private record JavaRuntimeChoice(@Nullable Path executable, @NotNull String label) {
        @Override
        @NotNull
        public String toString() {
            return this.label;
        }
    }
}
