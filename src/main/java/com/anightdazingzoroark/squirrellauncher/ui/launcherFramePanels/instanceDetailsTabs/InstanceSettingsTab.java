package com.anightdazingzoroark.squirrellauncher.ui.launcherFramePanels.instanceDetailsTabs;

import com.anightdazingzoroark.squirrellauncher.launcher.GameSettings;
import com.anightdazingzoroark.squirrellauncher.minecraft.instance.InstanceLaunchSettings;
import com.anightdazingzoroark.squirrellauncher.minecraft.instance.InstanceType;
import com.anightdazingzoroark.squirrellauncher.minecraft.instance.MinecraftInstance;
import com.anightdazingzoroark.squirrellauncher.minecraft.runtime.JavaRuntime;
import com.anightdazingzoroark.squirrellauncher.minecraft.runtime.JavaRuntimeManager;
import com.anightdazingzoroark.squirrellauncher.minecraft.runtime.JavaVersion;
import com.anightdazingzoroark.squirrellauncher.ui.GameSettingsControls;
import com.anightdazingzoroark.squirrellauncher.ui.LauncherActions;
import com.anightdazingzoroark.squirrellauncher.ui.Localization;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Dimension;
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
    private final JTextField javaPathField = new JTextField();
    @NotNull
    private final JButton browseJavaButton = new JButton(Localization.text("instance.settings.java.browse"));
    @NotNull
    private final JButton detectJavaButton = new JButton(Localization.text("instance.settings.java.detect"));
    @NotNull
    private final JButton resetDefaultsButton = new JButton(Localization.text("settings.button.reset_defaults"));
    @NotNull
    private final GameSettingsControls gameSettingsControls = new GameSettingsControls(true);
    @NotNull
    private final LauncherActions launcherActions;
    @NotNull
    private final Map<String, InstanceLaunchSettings> queuedSettings = new LinkedHashMap<>();
    @NotNull
    private GameSettings globalSettings = GameSettings.defaults();
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
    private boolean controlsAvailable;
    private boolean updatingControls;
    private boolean saveInProgress;

    public InstanceSettingsTab(@NotNull LauncherActions launcherActions) {
        super(new BorderLayout(0, 8));
        this.launcherActions = launcherActions;
        this.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        this.javaPathField.setEditable(false);

        JPanel form = new JPanel(new GridBagLayout());
        JPanel javaPanel = new JPanel(new BorderLayout(8, 0));
        javaPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(Localization.text("instance.settings.java.heading")),
                BorderFactory.createEmptyBorder(6, 10, 10, 10)
        ));
        javaPanel.add(this.javaPathField, BorderLayout.CENTER);
        JPanel javaActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        javaActions.add(this.browseJavaButton);
        javaActions.add(this.detectJavaButton);
        javaPanel.add(javaActions, BorderLayout.EAST);
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

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        actions.add(this.resetDefaultsButton);
        this.add(actions, BorderLayout.SOUTH);

        this.gameSettingsControls.setChangeListener(this::scheduleSave);
        this.browseJavaButton.addActionListener(event -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle(Localization.text("instance.settings.java.choose"));
            chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
            if (this.selectedJavaExecutable != null) {
                Path initialDirectory = Files.isDirectory(this.selectedJavaExecutable)
                        ? this.selectedJavaExecutable
                        : this.selectedJavaExecutable.getParent();
                if (initialDirectory != null) chooser.setCurrentDirectory(initialDirectory.toFile());
            }
            if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
            this.javaDetectionGeneration++;
            this.detectingJavaRuntimes = false;
            this.selectedJavaExecutable = chooser.getSelectedFile().toPath().toAbsolutePath().normalize();
            this.javaPathField.setText(this.selectedJavaExecutable.toString());
            this.javaPathField.setToolTipText(this.selectedJavaExecutable.toString());
            this.updateControlState();
            this.detectJavaRuntimes(false, true);
        });
        this.detectJavaButton.addActionListener(event -> this.detectJavaRuntimes(true, true));
        this.resetDefaultsButton.addActionListener(event -> {
            this.javaDetectionGeneration++;
            this.detectingJavaRuntimes = false;
            this.selectedJavaExecutable = null;
            this.javaPathField.setText(Localization.text(
                    "instance.settings.java.detecting",
                    this.requiredJavaVersion.major()
            ));
            this.javaPathField.setToolTipText(this.javaPathField.getText());
            this.gameSettingsControls.resetToDefaults();
            this.updateControlState();
            this.detectJavaRuntimes(false, false);
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
        this.globalSettings = globalSettings;
        this.requiredJavaVersion = instance.type() == InstanceType.CLEANROOM
                ? JavaVersion.JAVA_25
                : JavaVersion.JAVA_8;
        this.savedSettings = instance.launchSettings();
        this.selectedJavaExecutable = this.savedSettings.javaExecutable();
        this.javaDetectionGeneration++;
        this.detectingJavaRuntimes = false;
        this.javaPathField.setText(this.selectedJavaExecutable == null
                ? Localization.text("instance.settings.java.detecting", this.requiredJavaVersion.major())
                : this.selectedJavaExecutable.toString());
        this.javaPathField.setToolTipText(this.javaPathField.getText());
        this.gameSettingsControls.showInstanceSettings(this.savedSettings, globalSettings);
        this.updatingControls = false;
        this.updateControlState();
        this.detectJavaRuntimes(false, false);
    }

    public void clearDisplayedInstance() {
        this.updatingControls = true;
        this.displayedInstanceId = null;
        this.selectedJavaExecutable = null;
        this.javaDetectionGeneration++;
        this.detectingJavaRuntimes = false;
        this.controlsAvailable = false;
        this.javaPathField.setText("");
        this.javaPathField.setToolTipText(null);
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
                if (normalizedJavaPath) this.detectJavaRuntimes(false, false);
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

    private void updateControlState() {
        this.javaPathField.setEnabled(this.controlsAvailable);
        this.browseJavaButton.setEnabled(this.controlsAvailable);
        this.detectJavaButton.setEnabled(this.controlsAvailable && !this.detectingJavaRuntimes);
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
                this.gameSettingsControls.lowMemoryWarning()
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

    private void detectJavaRuntimes(boolean showChooser, boolean saveResolvedRuntime) {
        if (this.displayedInstanceId == null) return;
        int generation = ++this.javaDetectionGeneration;
        JavaVersion requiredVersion = this.requiredJavaVersion;
        Path configuredRuntime = this.selectedJavaExecutable;
        this.detectingJavaRuntimes = true;
        if (!showChooser && configuredRuntime == null) {
            this.javaPathField.setText(Localization.text(
                    "instance.settings.java.detecting",
                    requiredVersion.major()
            ));
            this.javaPathField.setToolTipText(this.javaPathField.getText());
        }
        this.updateControlState();
        new SwingWorker<List<JavaRuntime>, Void>() {
            @NotNull
            @Override
            protected List<JavaRuntime> doInBackground() throws Exception {
                if (showChooser || configuredRuntime == null) return JavaRuntimeManager.detect(requiredVersion);
                return List.of(JavaRuntimeManager.resolve(requiredVersion, configuredRuntime));
            }

            @Override
            protected void done() {
                if (generation != InstanceSettingsTab.this.javaDetectionGeneration) return;
                InstanceSettingsTab.this.detectingJavaRuntimes = false;
                List<JavaRuntime> runtimes;
                try {
                    runtimes = this.get();
                }
                catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    InstanceSettingsTab.this.updateControlState();
                    return;
                }
                catch (ExecutionException exception) {
                    InstanceSettingsTab.this.updateControlState();
                    if (!showChooser && configuredRuntime != null) {
                        String unavailableRuntime = Localization.text(
                                "instance.settings.java.invalid_runtime",
                                configuredRuntime
                        );
                        InstanceSettingsTab.this.javaPathField.setText(unavailableRuntime);
                        InstanceSettingsTab.this.javaPathField.setToolTipText(unavailableRuntime);
                        return;
                    }
                    JOptionPane.showMessageDialog(
                            InstanceSettingsTab.this,
                            exception.getCause().getMessage(),
                            Localization.text("instance.settings.java.detect_error"),
                            JOptionPane.ERROR_MESSAGE
                    );
                    return;
                }

                if (runtimes.isEmpty()) {
                    String noRuntime = Localization.text(
                            "instance.settings.java.none_detected",
                            requiredVersion.major()
                    );
                    if (showChooser) {
                        JOptionPane.showMessageDialog(
                                InstanceSettingsTab.this,
                                noRuntime,
                                Localization.text("instance.settings.java.detected_heading"),
                                JOptionPane.INFORMATION_MESSAGE
                        );
                    }
                    else {
                        InstanceSettingsTab.this.javaPathField.setText(noRuntime);
                        InstanceSettingsTab.this.javaPathField.setToolTipText(noRuntime);
                    }
                    InstanceSettingsTab.this.updateControlState();
                    return;
                }

                if (!showChooser) {
                    JavaRuntime runtime = runtimes.getFirst();
                    if (saveResolvedRuntime) InstanceSettingsTab.this.selectedJavaExecutable = runtime.executable();
                    String runtimeText = InstanceSettingsTab.this.javaRuntimeText(runtime, configuredRuntime == null);
                    InstanceSettingsTab.this.javaPathField.setText(runtimeText);
                    InstanceSettingsTab.this.javaPathField.setToolTipText(runtimeText);
                    InstanceSettingsTab.this.javaPathField.setCaretPosition(0);
                    InstanceSettingsTab.this.updateControlState();
                    if (saveResolvedRuntime) InstanceSettingsTab.this.scheduleSave();
                    return;
                }

                String[] runtimeLabels = new String[runtimes.size()];
                for (int index = 0; index < runtimes.size(); index++) {
                    runtimeLabels[index] = InstanceSettingsTab.this.javaRuntimeText(runtimes.get(index), false);
                }
                JList<String> runtimeSelector = new JList<>(runtimeLabels);
                runtimeSelector.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
                runtimeSelector.setSelectedIndex(0);
                runtimeSelector.setVisibleRowCount(Math.min(8, runtimes.size()));
                JScrollPane runtimeList = new JScrollPane(runtimeSelector);
                runtimeList.setPreferredSize(new Dimension(700, Math.min(260, 28 + runtimes.size() * 24)));
                int result = JOptionPane.showConfirmDialog(
                        InstanceSettingsTab.this,
                        runtimeList,
                        Localization.text("instance.settings.java.detected_heading"),
                        JOptionPane.OK_CANCEL_OPTION,
                        JOptionPane.PLAIN_MESSAGE
                );
                if (result == JOptionPane.OK_OPTION && runtimeSelector.getSelectedIndex() >= 0) {
                    JavaRuntime runtime = runtimes.get(runtimeSelector.getSelectedIndex());
                    InstanceSettingsTab.this.selectedJavaExecutable = runtime.executable();
                    String runtimeText = InstanceSettingsTab.this.javaRuntimeText(runtime, false);
                    InstanceSettingsTab.this.javaPathField.setText(runtimeText);
                    InstanceSettingsTab.this.javaPathField.setToolTipText(runtimeText);
                    InstanceSettingsTab.this.javaPathField.setCaretPosition(0);
                    InstanceSettingsTab.this.scheduleSave();
                }
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
}
