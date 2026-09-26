package com.anightdazingzoroark.squirrellauncher.ui;

import com.anightdazingzoroark.squirrellauncher.launcher.InstanceAdditionRequest;
import com.anightdazingzoroark.squirrellauncher.launcher.InstanceNames;
import com.anightdazingzoroark.squirrellauncher.launcher.LauncherService;
import com.anightdazingzoroark.squirrellauncher.minecraft.auth.MinecraftAccount;
import com.anightdazingzoroark.squirrellauncher.minecraft.instance.MinecraftInstance;
import com.anightdazingzoroark.squirrellauncher.minecraft.mod.ManagedMod;
import com.anightdazingzoroark.squirrellauncher.minecraft.mod.ModState;
import com.anightdazingzoroark.squirrellauncher.ui.dialogs.AddInstanceDialog;
import com.anightdazingzoroark.squirrellauncher.ui.dialogs.ConvertInstanceDialog;
import com.anightdazingzoroark.squirrellauncher.ui.launcherFramePanels.InstanceDetailsPanel;
import com.anightdazingzoroark.squirrellauncher.ui.launcherFramePanels.InstanceSidebarPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.Component;
import java.awt.Desktop;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;

public final class LauncherActions {
    @NotNull
    private final LauncherFrame launcherFrame;
    @NotNull
    private final LauncherService launcherService;
    @Nullable
    private InstanceSidebarPanel instanceSidebarPanel;
    @Nullable
    private InstanceDetailsPanel instanceDetailsPanel;

    public LauncherActions(@NotNull LauncherFrame launcherFrame, @NotNull LauncherService launcherService) {
        this.launcherFrame = launcherFrame;
        this.launcherService = launcherService;
    }

    void connectPanels(@NotNull InstanceSidebarPanel instanceSidebarPanel, @NotNull InstanceDetailsPanel instanceDetailsPanel) {
        if (this.instanceSidebarPanel != null || this.instanceDetailsPanel != null) {
            throw new IllegalStateException("Launcher panels are already connected.");
        }
        this.instanceSidebarPanel = instanceSidebarPanel;
        this.instanceDetailsPanel = instanceDetailsPanel;
    }

    //---sidebar actions---
    public void selectionChanged() {
        MinecraftInstance instance = this.selectedInstance();
        this.detailsPanel().modsTab().setMods(List.of());
        if (instance == null) {
            this.detailsPanel().showEmpty(!this.sidebarPanel().hasInstances());
            this.launcherFrame.updateControlState();
            return;
        }
        this.detailsPanel().showInstance(instance);
        this.launcherFrame.updateControlState();
        if (instance.type().hasMods) this.refreshMods(instance);
    }

    public void sidebarStateChanged() {
        this.launcherFrame.updateControlState();
    }

    public void addRequested() {
        InstanceAdditionRequest request = new AddInstanceDialog(this.launcherFrame).showModal();
        if (request == null) return;
        this.launcherFrame.runTask(
                null,
                Localization.text(request.importsInstance() ? "main.status.importing" : "main.status.creating", request.name()),
                () -> this.launcherService.addInstance(request),
                instance -> {
                    this.launcherFrame.appendActivity(instance.id(), Localization.text(
                            request.importsInstance() ? "main.activity.imported" : "main.activity.created",
                            instance.name()
                    ));
                    this.sidebarPanel().clearSearch();
                    this.refreshInstances(instance.id());
                }
        );
    }

    public void refreshRequested() {
        this.refreshInstances(this.sidebarPanel().selectedInstanceId());
    }

    public void reorderRequested(@NotNull List<MinecraftInstance> instances, @Nullable String selectedId) {
        this.launcherFrame.runTask(
                null,
                Localization.text("main.status.sorting_instances"),
                () -> {
                    this.launcherService.reorderInstances(instances);
                    return instances;
                },
                reordered -> {
                    this.sidebarPanel().setInstances(reordered, selectedId);
                    this.launcherFrame.setStatus(Localization.text("main.status.instances_reordered"));
                }
        );
    }

    public void launchRequested() {
        MinecraftInstance instance = this.selectedInstance();
        MinecraftAccount account = this.launcherService.account();
        if (instance == null || account == null || this.launcherFrame.isMinecraftRunning()) return;
        this.detailsPanel().showActivityTab();
        this.launcherFrame.appendActivity(instance.id(), Localization.text(
                "main.activity.launching", instance.name(), account.username()
        ));
        this.launcherFrame.runTask(
                instance.id(),
                Localization.text("main.status.preparing"),
                () -> this.launcherService.launch(instance),
                process -> {
                    this.launcherFrame.refreshAccountSelector();
                    this.launcherFrame.monitorProcess(instance, process);
                }
        );
    }

    public void renameRequested() {
        MinecraftInstance instance = this.selectedInstance();
        if (instance == null || this.launcherFrame.isMinecraftRunning()) return;
        String name = this.promptForInstanceName(
                Localization.text("main.dialog.rename", instance.name()),
                Localization.text("main.prompt.new_name"),
                instance.name()
        );
        if (name == null) return;
        this.launcherFrame.runTask(
                instance.id(),
                Localization.text("main.status.renaming", instance.name()),
                () -> this.launcherService.renameInstance(instance, name),
                renamed -> {
                    this.detailsPanel().activityTab().moveLog(instance.id(), renamed.id());
                    this.launcherFrame.appendActivity(
                            renamed.id(),
                            Localization.text("main.activity.renamed", renamed.name())
                    );
                    this.refreshInstances(renamed.id());
                }
        );
    }

    public void duplicateRequested() {
        MinecraftInstance instance = this.selectedInstance();
        if (instance == null || this.launcherFrame.isMinecraftRunning()) return;
        String name = this.promptForInstanceName(
                Localization.text("main.dialog.duplicate", instance.name()),
                Localization.text("main.prompt.duplicate_name"),
                Localization.text("main.copy_suffix", instance.name())
        );
        if (name == null) return;
        this.launcherFrame.runTask(
                instance.id(),
                Localization.text("main.status.duplicating", instance.name()),
                () -> this.launcherService.duplicateInstance(instance, name),
                duplicate -> {
                    this.launcherFrame.appendActivity(duplicate.id(), Localization.text(
                            "main.activity.duplicated", instance.name(), duplicate.name()
                    ));
                    this.sidebarPanel().clearSearch();
                    this.refreshInstances(duplicate.id());
                }
        );
    }

    public void convertRequested() {
        MinecraftInstance instance = this.selectedInstance();
        if (instance == null || this.launcherFrame.isMinecraftRunning()) return;
        ConvertInstanceDialog.InstanceConversion conversion =
                new ConvertInstanceDialog(this.launcherFrame, instance).showModal();
        if (conversion == null) return;
        this.launcherFrame.runTask(
                instance.id(),
                Localization.text("main.status.converting", instance.name()),
                () -> this.launcherService.convertInstance(instance, conversion.type(), conversion.loaderVersion()),
                converted -> {
                    this.launcherFrame.appendActivity(converted.id(), Localization.text(
                            "main.activity.converted", converted.name(), LauncherFrame.displayName(converted.type())
                    ));
                    this.refreshInstances(converted.id());
                }
        );
    }

    public void chooseIconRequested() {
        MinecraftInstance instance = this.selectedInstance();
        if (instance == null) return;
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(Localization.text("main.dialog.choose_icon", instance.name()));
        chooser.setFileFilter(new FileNameExtensionFilter(
                Localization.text("file_filter.images"), "png", "jpg", "jpeg", "gif", "bmp"
        ));
        if (chooser.showOpenDialog(this.launcherFrame) != JFileChooser.APPROVE_OPTION) return;
        Path source = chooser.getSelectedFile().toPath();
        this.launcherFrame.runTask(
                instance.id(),
                Localization.text("main.status.updating_icon", instance.name()),
                () -> this.launcherService.setInstanceIcon(instance, source),
                updated -> {
                    this.launcherFrame.appendActivity(
                            updated.id(),
                            Localization.text("main.activity.updated_icon", updated.name())
                    );
                    this.refreshInstances(updated.id());
                }
        );
    }

    public void resetIconRequested() {
        MinecraftInstance instance = this.selectedInstance();
        if (instance == null || instance.iconKey() == null) return;
        this.launcherFrame.runTask(
                instance.id(),
                Localization.text("main.status.resetting_icon", instance.name()),
                () -> this.launcherService.resetInstanceIcon(instance),
                updated -> {
                    this.launcherFrame.appendActivity(
                            updated.id(),
                            Localization.text("main.activity.reset_icon", updated.name())
                    );
                    this.refreshInstances(updated.id());
                }
        );
    }

    public void manageIconRequested(@NotNull Component source) {
        this.sidebarPanel().showIconMenu(source);
    }

    public void openFolderRequested() {
        MinecraftInstance instance = this.selectedInstance();
        if (instance == null) return;
        Path instanceDirectory = instance.directory();
        try {
            if (!Files.isDirectory(instanceDirectory)) {
                throw new IllegalStateException(Localization.text("main.error.instance_folder_missing", instanceDirectory));
            }
            if (!Desktop.isDesktopSupported()) {
                throw new UnsupportedOperationException(Localization.text("main.error.file_explorer_unsupported"));
            }
            Desktop desktop = Desktop.getDesktop();
            if (!desktop.isSupported(Desktop.Action.OPEN)) {
                throw new UnsupportedOperationException(Localization.text("main.error.file_explorer_unsupported"));
            }
            desktop.open(instanceDirectory.toFile());
        }
        catch (Exception exception) {
            this.launcherFrame.showError(
                    Localization.text("main.error.open_instance_folder"),
                    exception,
                    instance.id()
            );
        }
    }

    public void exportRequested() {
        MinecraftInstance instance = this.selectedInstance();
        if (instance == null) return;
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(Localization.text("main.dialog.export", instance.name()));
        chooser.setFileFilter(new FileNameExtensionFilter(Localization.text("file_filter.mmc_archives"), "zip"));
        chooser.setSelectedFile(new java.io.File(InstanceNames.folderName(instance.name()) + ".zip"));
        if (chooser.showSaveDialog(this.launcherFrame) != JFileChooser.APPROVE_OPTION) return;
        Path destination = chooser.getSelectedFile().toPath();
        this.launcherFrame.runTask(
                instance.id(),
                Localization.text("main.status.exporting", instance.name()),
                () -> {
                    this.launcherService.exportInstance(instance, destination);
                    return null;
                },
                ignored -> {
                    this.launcherFrame.setStatus(Localization.text("main.status.ready"));
                    this.launcherFrame.appendActivity(
                            instance.id(),
                            Localization.text("main.activity.exported", instance.name())
                    );
                }
        );
    }

    public void deleteRequested() {
        MinecraftInstance instance = this.selectedInstance();
        if (instance == null || this.launcherFrame.isMinecraftRunning()) return;
        int choice = JOptionPane.showConfirmDialog(
                this.launcherFrame,
                Localization.text("main.confirm.delete", instance.name()),
                Localization.text("main.dialog.delete"),
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if (choice != JOptionPane.OK_OPTION) return;
        this.launcherFrame.runTask(
                instance.id(),
                Localization.text("main.status.deleting", instance.name()),
                () -> {
                    this.launcherService.deleteInstance(instance);
                    return null;
                },
                ignored -> {
                    this.detailsPanel().activityTab().removeLog(instance.id());
                    this.refreshInstances(null);
                }
        );
    }

    //---mods actions---
    public void modSelectionChanged() {
        this.launcherFrame.updateControlState();
    }

    public void installModRequested() {
        MinecraftInstance instance = this.selectedInstance();
        if (instance == null) return;
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(Localization.text("main.dialog.install_mod"));
        chooser.setFileFilter(new FileNameExtensionFilter(Localization.text("file_filter.java_archives"), "jar"));
        if (chooser.showOpenDialog(this.launcherFrame) != JFileChooser.APPROVE_OPTION) return;
        Path selectedFile = chooser.getSelectedFile().toPath();
        this.launcherFrame.runTask(
                instance.id(),
                Localization.text("main.status.installing_mod", selectedFile.getFileName()),
                () -> {
                    this.launcherService.installMod(instance, selectedFile);
                    return null;
                },
                ignored -> {
                    this.launcherFrame.setStatus(Localization.text("main.status.ready"));
                    this.refreshMods(instance);
                }
        );
    }

    public void toggleModRequested() {
        MinecraftInstance instance = this.selectedInstance();
        ManagedMod mod = this.selectedMod();
        if (instance == null || mod == null) return;
        boolean enable = mod.state() == ModState.DISABLED;
        this.launcherFrame.runTask(
                instance.id(),
                Localization.text(enable ? "main.status.enabling_mod" : "main.status.disabling_mod", mod.fileName()),
                () -> {
                    this.launcherService.setModEnabled(instance, mod, enable);
                    return null;
                },
                ignored -> {
                    this.launcherFrame.setStatus(Localization.text("main.status.ready"));
                    this.refreshMods(instance);
                }
        );
    }

    public void removeModRequested() {
        MinecraftInstance instance = this.selectedInstance();
        ManagedMod mod = this.selectedMod();
        if (instance == null || mod == null) return;
        int choice = JOptionPane.showConfirmDialog(
                this.launcherFrame,
                Localization.text("main.confirm.remove_mod", mod.fileName()),
                Localization.text("main.dialog.remove_mod"),
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if (choice != JOptionPane.OK_OPTION) return;
        this.launcherFrame.runTask(
                instance.id(),
                Localization.text("main.status.removing_mod", mod.fileName()),
                () -> {
                    this.launcherService.removeMod(instance, mod);
                    return null;
                },
                ignored -> {
                    this.launcherFrame.setStatus(Localization.text("main.status.ready"));
                    this.refreshMods(instance);
                }
        );
    }

    //---instance data---
    public void refreshInstances(@Nullable String selectedId) {
        this.launcherFrame.runTask(
                null,
                Localization.text("main.status.loading_instances"),
                this.launcherService::listInstances,
                instances -> {
                    Set<String> instanceIds = new HashSet<>();
                    for (MinecraftInstance instance : instances) instanceIds.add(instance.id());
                    this.detailsPanel().activityTab().retainLogs(instanceIds);
                    this.sidebarPanel().setInstances(instances, selectedId);
                    this.launcherFrame.setStatus(Localization.text(
                            instances.isEmpty() ? "main.status.add_instance" : "main.status.ready"
                    ));
                }
        );
    }

    //---helpers---
    @NotNull
    private InstanceSidebarPanel sidebarPanel() {
        if (this.instanceSidebarPanel == null) throw new IllegalStateException("Instance sidebar is not connected.");
        return this.instanceSidebarPanel;
    }

    @NotNull
    private InstanceDetailsPanel detailsPanel() {
        if (this.instanceDetailsPanel == null) throw new IllegalStateException("Instance details are not connected.");
        return this.instanceDetailsPanel;
    }

    @Nullable
    private MinecraftInstance selectedInstance() {
        return this.sidebarPanel().selectedInstance();
    }

    @Nullable
    private ManagedMod selectedMod() {
        return this.detailsPanel().modsTab().selectedMod();
    }

    private void refreshMods(@NotNull MinecraftInstance instance) {
        new SwingWorker<List<ManagedMod>, Void>() {
            @Override
            @NotNull
            protected List<ManagedMod> doInBackground() throws Exception {
                return LauncherActions.this.launcherService.listMods(instance);
            }

            @Override
            protected void done() {
                if (!instance.equals(LauncherActions.this.selectedInstance())) return;
                try {
                    LauncherActions.this.detailsPanel().modsTab().setMods(this.get());
                    LauncherActions.this.launcherFrame.updateControlState();
                }
                catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    LauncherActions.this.launcherFrame.showError(
                            Localization.text("main.error.loading_mods_interrupted"),
                            exception,
                            instance.id()
                    );
                }
                catch (ExecutionException exception) {
                    LauncherActions.this.launcherFrame.showError(
                            Localization.text("main.error.load_mods"),
                            exception.getCause(),
                            instance.id()
                    );
                }
            }
        }.execute();
    }

    @Nullable
    private String promptForInstanceName(
            @NotNull String title,
            @NotNull String prompt,
            @NotNull String initialValue
    ) {
        String name = (String) JOptionPane.showInputDialog(
                this.launcherFrame,
                prompt,
                title,
                JOptionPane.PLAIN_MESSAGE,
                null,
                null,
                initialValue
        );
        if (name == null) return null;
        name = name.trim();
        if (InstanceNames.isValid(name)) return name;
        JOptionPane.showMessageDialog(
                this.launcherFrame,
                Localization.text("main.error.invalid_name"),
                Localization.text("main.dialog.invalid_name"),
                JOptionPane.ERROR_MESSAGE
        );
        return null;
    }
}
