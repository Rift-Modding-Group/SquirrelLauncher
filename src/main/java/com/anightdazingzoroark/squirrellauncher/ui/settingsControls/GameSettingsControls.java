package com.anightdazingzoroark.squirrellauncher.ui.settingsControls;

import com.anightdazingzoroark.squirrellauncher.launcher.GameSettings;
import com.anightdazingzoroark.squirrellauncher.launcher.JvmArguments;
import com.anightdazingzoroark.squirrellauncher.minecraft.instance.InstanceLaunchSettings;
import com.anightdazingzoroark.squirrellauncher.ui.Localization;
import org.jetbrains.annotations.NotNull;

import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import java.awt.BorderLayout;
import java.util.List;
import java.util.function.Consumer;

//widgets used in SettingsDialog and InstanceSettingsTab
public final class GameSettingsControls extends JPanel {
    @NotNull
    private final BasicGameSettingsControls basicControls;
    @NotNull
    private final AdvancedGameSettingsControls advancedControls;
    @NotNull
    private final JTabbedPane settingsTabs = new JTabbedPane();
    @NotNull
    private GameSettings globalSettings = GameSettings.defaults();
    @NotNull
    private List<String> savedJvmArguments = this.globalSettings.jvmArguments();
    @NotNull
    private Runnable changeListener = () -> {};
    @NotNull
    private Consumer<Boolean> advancedDisplayedListener = displayed -> {};
    private int selectedSettingsTab;
    private boolean updatingControls;
    private boolean changingSettingsTab;

    public GameSettingsControls(boolean showDefaultSelectors) {
        super(new BorderLayout());
        this.basicControls = new BasicGameSettingsControls(showDefaultSelectors);
        this.advancedControls = new AdvancedGameSettingsControls(showDefaultSelectors);
        this.settingsTabs.addTab(Localization.text("settings.game.tab.friendly"), this.basicControls);
        this.settingsTabs.addTab(Localization.text("settings.game.tab.advanced"), this.advancedControls);
        this.add(this.settingsTabs, BorderLayout.CENTER);

        this.basicControls.setChangeListener(() -> {
            if (this.updatingControls) return;
            this.updateJvmArgumentsFromBasicControls();
        });
        this.advancedControls.setSaveListener(arguments -> {
            this.updatingControls = true;
            this.savedJvmArguments = List.copyOf(arguments);
            this.basicControls.applyAdvancedArguments(arguments);
            this.updatingControls = false;
            this.changeListener.run();
        });
        this.advancedControls.setUseDefaultsListener(useDefaults -> {
            if (this.updatingControls) return;
            this.updatingControls = true;
            this.basicControls.setUseDefaultJvmArguments(useDefaults);
            this.updatingControls = false;
            this.updateJvmArgumentsFromBasicControls();
        });
        this.settingsTabs.addChangeListener(event -> {
            if (this.changingSettingsTab) return;
            int selectedIndex = this.settingsTabs.getSelectedIndex();
            if (this.selectedSettingsTab == 1
                    && selectedIndex != 1
                    && !this.advancedControls.confirmDiscardUnsavedChanges()) {
                this.changingSettingsTab = true;
                this.settingsTabs.setSelectedIndex(1);
                this.changingSettingsTab = false;
                return;
            }
            this.selectedSettingsTab = selectedIndex;
            this.advancedDisplayedListener.accept(selectedIndex == 1);
        });
        this.showGlobalSettings(this.globalSettings);
    }

    public void setChangeListener(@NotNull Runnable changeListener) {
        this.changeListener = changeListener;
    }

    public void showGlobalSettings(@NotNull GameSettings settings) {
        this.updatingControls = true;
        this.globalSettings = settings;
        this.basicControls.showGlobalSettings(settings);
        this.advancedControls.setUseDefaults(false);
        this.setJvmValues(settings.jvmArguments());
        this.updatingControls = false;
    }

    public void showInstanceSettings(
            @NotNull InstanceLaunchSettings settings,
            @NotNull GameSettings globalSettings
    ) {
        this.updatingControls = true;
        this.globalSettings = globalSettings;
        this.basicControls.showInstanceSettings(settings, globalSettings);
        this.advancedControls.setUseDefaults(!settings.overrideJvmArguments());
        List<String> configuredArguments = settings.overrideJvmArguments()
                ? settings.jvmArguments()
                : globalSettings.jvmArguments();
        List<String> memoryArguments = settings.overrideMemory()
                ? settings.jvmArguments()
                : globalSettings.jvmArguments();
        this.setJvmValues(JvmArguments.withMemory(
                configuredArguments,
                JvmArguments.minimumMemoryMegabytes(memoryArguments),
                JvmArguments.maximumMemoryGigabytes(memoryArguments)
        ));
        this.updatingControls = false;
    }

    public void setAvailable(boolean available) {
        this.basicControls.setAvailable(available);
        this.advancedControls.setAvailable(available);
    }

    public void resetToDefaults() {
        this.basicControls.resetToDefaults();
    }

    public boolean confirmDiscardUnsavedChanges() {
        return this.advancedControls.confirmDiscardUnsavedChanges();
    }

    public void setAdvancedActionsAvailableListener(@NotNull Consumer<Boolean> listener) {
        this.advancedControls.setActionsAvailableListener(listener);
    }

    public void setAdvancedDisplayedListener(@NotNull Consumer<Boolean> listener) {
        this.advancedDisplayedListener = listener;
        this.advancedDisplayedListener.accept(this.settingsTabs.getSelectedIndex() == 1);
    }

    public void saveAdvancedChanges() {
        this.advancedControls.saveChanges();
    }

    public void cancelAdvancedChanges() {
        this.advancedControls.cancelChanges();
    }

    public boolean overrideWindowSettings() {
        return this.basicControls.overrideWindowSettings();
    }

    public boolean fullscreen() {
        return this.basicControls.fullscreen();
    }

    public int windowWidth() {
        return this.basicControls.windowWidth();
    }

    public int windowHeight() {
        return this.basicControls.windowHeight();
    }

    public boolean overrideMemory() {
        return this.basicControls.overrideMemory();
    }

    public int allocatedMemoryGigabytes() {
        return this.basicControls.allocatedMemoryGigabytes();
    }

    public boolean lowMemoryWarning() {
        return this.basicControls.lowMemoryWarning();
    }

    public boolean overrideJvmArguments() {
        return this.basicControls.overrideJvmArguments();
    }

    @NotNull
    public List<String> jvmArguments() {
        return this.savedJvmArguments;
    }

    private void setJvmValues(@NotNull List<String> arguments) {
        this.savedJvmArguments = List.copyOf(arguments);
        this.advancedControls.showArguments(this.savedJvmArguments);
    }

    private void updateJvmArgumentsFromBasicControls() {
        List<String> updatedArguments = this.basicControls.overrideJvmArguments()
                ? this.savedJvmArguments
                : this.globalSettings.jvmArguments();
        List<String> memoryArguments = this.basicControls.overrideMemory()
                ? this.savedJvmArguments
                : this.globalSettings.jvmArguments();
        int maximumMemoryGigabytes = this.basicControls.allocatedMemoryGigabytes();
        int minimumMemoryMegabytes = (int) Math.min(
                JvmArguments.minimumMemoryMegabytes(memoryArguments),
                (long) maximumMemoryGigabytes * 1024L
        );
        updatedArguments = JvmArguments.withMemory(
                updatedArguments,
                minimumMemoryMegabytes,
                maximumMemoryGigabytes
        );
        updatedArguments = this.basicControls.garbageCollector().applyTo(updatedArguments);
        this.advancedControls.setUseDefaults(!this.basicControls.overrideJvmArguments());
        this.setJvmValues(updatedArguments);
        this.changeListener.run();
    }
}
