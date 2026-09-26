package com.anightdazingzoroark.squirrellauncher.ui;

import com.anightdazingzoroark.squirrellauncher.SquirrelLauncher;
import com.anightdazingzoroark.squirrellauncher.launcher.InstanceAdditionRequest;
import com.anightdazingzoroark.squirrellauncher.launcher.InstanceNames;
import com.anightdazingzoroark.squirrellauncher.launcher.LauncherService;
import com.anightdazingzoroark.squirrellauncher.minecraft.auth.MinecraftAccount;
import com.anightdazingzoroark.squirrellauncher.minecraft.instance.InstanceType;
import com.anightdazingzoroark.squirrellauncher.minecraft.instance.MinecraftInstance;
import com.anightdazingzoroark.squirrellauncher.minecraft.mod.ManagedMod;
import com.anightdazingzoroark.squirrellauncher.minecraft.mod.ModState;
import com.anightdazingzoroark.squirrellauncher.ui.dialogs.AddInstanceDialog;
import com.anightdazingzoroark.squirrellauncher.ui.dialogs.ConvertInstanceDialog;
import com.anightdazingzoroark.squirrellauncher.ui.dialogs.SettingsDialog;
import com.anightdazingzoroark.squirrellauncher.ui.launcherFramePanels.InstanceDetailsPanel;
import com.anightdazingzoroark.squirrellauncher.ui.launcherFramePanels.InstanceSidebarPanel;
import com.anightdazingzoroark.squirrellauncher.ui.launcherFramePanels.instanceDetailsTabs.ModsTab;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

//the main ui users will deal with
public final class LauncherFrame extends JFrame {
    @NotNull
    private final JComboBox<MinecraftAccount> accountSelector = new JComboBox<>();
    @NotNull
    private final JButton manageAccountsButton;
    @NotNull
    private final JButton settingsButton;
    @NotNull
    private final JLabel statusLabel;
    @NotNull
    private final JProgressBar progressBar = new JProgressBar();
    @NotNull
    private final JButton stopButton;
    @NotNull
    private final JButton launchButton;
    @NotNull
    private final LauncherService launcherService;
    @NotNull
    private final InstanceSidebarPanel instanceSidebarPanel;
    @NotNull
    private final InstanceDetailsPanel instanceDetailsPanel;
    private boolean busy;
    private boolean updatingAccountSelector;
    @Nullable
    private volatile String taskActivityInstanceId;
    @Nullable
    private volatile Object taskActivityToken;
    @Nullable
    private volatile String runningActivityInstanceId;
    @Nullable
    private volatile String runningActivityPrefix;
    @Nullable
    private Process runningProcess;

    public LauncherFrame() {
        super(SquirrelLauncher.NAME);
        this.launcherService = new LauncherService(this::appendBackendOutput);
        Localization.configure(this.launcherService.settings().language());
        this.manageAccountsButton = new JButton(Localization.text("main.button.manage_accounts"));
        this.settingsButton = new JButton(Localization.text("main.button.settings"));
        this.statusLabel = new JLabel(Localization.text("main.status.ready"));
        this.stopButton = new JButton(Localization.text("main.button.stop"));
        this.launchButton = new JButton(Localization.text("main.button.launch"));
        this.instanceSidebarPanel = new InstanceSidebarPanel(this.createSidebarListener());
        this.instanceDetailsPanel = new InstanceDetailsPanel(this.createDetailsListener(), this.createModsListener());

        this.setDefaultCloseOperation(EXIT_ON_CLOSE);
        this.setMinimumSize(new Dimension(820, 560));
        this.setExtendedState(JFrame.MAXIMIZED_BOTH);
        this.setLocationByPlatform(true);
        this.setLayout(new BorderLayout(0, 0));
        this.add(this.createAccountBar(), BorderLayout.NORTH);
        this.add(this.createMainContent(), BorderLayout.CENTER);
        this.add(this.createStatusBar(), BorderLayout.SOUTH);

        this.configureListeners();
        this.refreshAccountSelector();
        this.updateControlState();
        this.refreshInstances(null);
        SwingUtilities.invokeLater(() -> {
            if (this.launcherService.accounts().isEmpty()) {
                this.showSettings(SettingsDialog.SettingsTab.ACCOUNTS);
            }
        });
    }

    //---component listeners---
    private void configureListeners() {
        this.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(@NotNull WindowEvent event) {
                LauncherFrame.this.launcherService.close();
            }
        });
        this.accountSelector.addActionListener(event -> {
            if (this.updatingAccountSelector || this.busy) return;
            MinecraftAccount account = (MinecraftAccount) this.accountSelector.getSelectedItem();
            if (account == null) return;
            try {
                this.launcherService.selectAccount(account);
                this.setStatus(Localization.text("main.status.using_account", account.username()));
                this.updateControlState();
            }
            catch (Exception exception) {
                this.showError(Localization.text("main.error.select_account"), exception, null);
                this.refreshAccountSelector();
            }
        });
        this.manageAccountsButton.addActionListener(event -> this.showSettings(SettingsDialog.SettingsTab.ACCOUNTS));
        this.settingsButton.addActionListener(event -> this.showSettings(SettingsDialog.SettingsTab.GAME));
        this.stopButton.addActionListener(event -> this.stopMinecraft());
        this.launchButton.addActionListener(event -> this.launchSelectedInstance());
    }

    @NotNull
    private InstanceSidebarPanel.Listener createSidebarListener() {
        return new InstanceSidebarPanel.Listener() {
            @Override
            public void selectionChanged() {
                LauncherFrame.this.showSelectedInstance();
            }

            @Override
            public void sidebarStateChanged() {
                LauncherFrame.this.updateControlState();
            }

            @Override
            public void addRequested() {
                LauncherFrame.this.addInstance();
            }

            @Override
            public void refreshRequested() {
                LauncherFrame.this.refreshInstances(LauncherFrame.this.selectedInstanceId());
            }

            @Override
            public void reorderRequested(@NotNull List<MinecraftInstance> instances, @Nullable String selectedId) {
                LauncherFrame.this.reorderInstances(instances, selectedId);
            }

            @Override
            public void launchRequested() {
                if (LauncherFrame.this.launchButton.isEnabled()) LauncherFrame.this.launchSelectedInstance();
            }

            @Override
            public void renameRequested() {
                LauncherFrame.this.renameSelectedInstance();
            }

            @Override
            public void duplicateRequested() {
                LauncherFrame.this.duplicateSelectedInstance();
            }

            @Override
            public void convertRequested() {
                LauncherFrame.this.convertSelectedInstance();
            }

            @Override
            public void chooseIconRequested() {
                LauncherFrame.this.chooseInstanceIcon();
            }

            @Override
            public void resetIconRequested() {
                LauncherFrame.this.resetInstanceIcon();
            }

            @Override
            public void openFolderRequested() {
                LauncherFrame.this.openInstanceFolder();
            }

            @Override
            public void exportRequested() {
                LauncherFrame.this.exportSelectedInstance();
            }

            @Override
            public void deleteRequested() {
                LauncherFrame.this.deleteSelectedInstance();
            }
        };
    }

    @NotNull
    private InstanceDetailsPanel.Listener createDetailsListener() {
        return new InstanceDetailsPanel.Listener() {
            @Override
            public void renameRequested() {
                LauncherFrame.this.renameSelectedInstance();
            }

            @Override
            public void manageIconRequested(@NotNull Component source) {
                LauncherFrame.this.instanceSidebarPanel.showIconMenu(source);
            }
        };
    }

    @NotNull
    private ModsTab.Listener createModsListener() {
        return new ModsTab.Listener() {
            @Override
            public void selectionChanged() {
                LauncherFrame.this.updateControlState();
            }

            @Override
            public void installRequested() {
                LauncherFrame.this.installMod();
            }

            @Override
            public void toggleRequested() {
                LauncherFrame.this.toggleSelectedMod();
            }

            @Override
            public void removeRequested() {
                LauncherFrame.this.removeSelectedMod();
            }
        };
    }

    //---init components---
    @NotNull
    private JPanel createAccountBar() {
        JPanel panel = new JPanel(new BorderLayout(12, 0));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        JLabel title = new JLabel(SquirrelLauncher.NAME);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        panel.add(title, BorderLayout.WEST);

        JPanel accountControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        accountControls.add(new JLabel(Localization.text("main.label.account")));
        this.accountSelector.setPreferredSize(new Dimension(220, 38));
        this.accountSelector.setRenderer(new DefaultListCellRenderer() {
            @Override
            @NotNull
            public Component getListCellRendererComponent(
                    @NotNull JList<?> list,
                    @Nullable Object value,
                    int index,
                    boolean selected,
                    boolean focused
            ) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, selected, focused);
                if (value instanceof MinecraftAccount account) {
                    String accountName = account.username();
                    if (account.type() == MinecraftAccount.AccountType.OFFLINE) {
                        accountName += " (" + LauncherFrame.displayName(MinecraftAccount.AccountType.OFFLINE) + ")";
                    }
                    label.setText(accountName);
                    label.setIcon(AccountIconProvider.INSTANCE.iconFor(account, LauncherFrame.this.accountSelector::repaint));
                    label.setIconTextGap(8);
                }
                else {
                    label.setText(Localization.text("main.account.none"));
                    label.setIcon(null);
                }
                return label;
            }
        });
        accountControls.add(this.accountSelector);
        accountControls.add(this.manageAccountsButton);
        accountControls.add(this.settingsButton);
        panel.add(accountControls, BorderLayout.EAST);
        return panel;
    }

    @NotNull
    private JPanel createMainContent() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(this.instanceSidebarPanel, BorderLayout.WEST);
        panel.add(this.instanceDetailsPanel, BorderLayout.CENTER);
        return panel;
    }

    @NotNull
    private JPanel createStatusBar() {
        JPanel panel = new JPanel(new BorderLayout(10, 0));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, panel.getBackground().darker()),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)
        ));
        this.progressBar.setIndeterminate(true);
        this.progressBar.setVisible(false);
        this.progressBar.setPreferredSize(new Dimension(90, 16));
        JPanel status = new JPanel(new GridBagLayout());
        GridBagConstraints progress = new GridBagConstraints();
        progress.gridx = 0;
        progress.anchor = GridBagConstraints.LINE_START;
        progress.insets = new Insets(0, 0, 0, 8);
        status.add(this.progressBar, progress);
        GridBagConstraints statusText = new GridBagConstraints();
        statusText.gridx = 1;
        statusText.weightx = 1;
        statusText.anchor = GridBagConstraints.LINE_START;
        status.add(this.statusLabel, statusText);
        panel.add(status, BorderLayout.CENTER);
        JPanel gameControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        gameControls.add(this.stopButton);
        this.launchButton.setFont(this.launchButton.getFont().deriveFont(Font.BOLD));
        gameControls.add(this.launchButton);
        panel.add(gameControls, BorderLayout.EAST);
        return panel;
    }

    private void addInstance() {
        InstanceAdditionRequest request = new AddInstanceDialog(this).showModal();
        if (request == null) return;
        this.runTask(
                null,
                Localization.text(request.importsInstance() ? "main.status.importing" : "main.status.creating", request.name()),
                () -> this.launcherService.addInstance(request),
                instance -> {
                    this.appendActivity(instance.id(), Localization.text(
                            request.importsInstance() ? "main.activity.imported" : "main.activity.created",
                            instance.name()
                    ));
                    this.instanceSidebarPanel.clearSearch();
                    this.refreshInstances(instance.id());
                }
        );
    }

    private void reorderInstances(@NotNull List<MinecraftInstance> instances, @Nullable String selectedId) {
        this.runTask(
                null,
                Localization.text("main.status.sorting_instances"),
                () -> {
                    this.launcherService.reorderInstances(instances);
                    return instances;
                },
                reordered -> {
                    this.instanceSidebarPanel.setInstances(reordered, selectedId);
                    this.setStatus(Localization.text("main.status.instances_reordered"));
                }
        );
    }

    private void renameSelectedInstance() {
        MinecraftInstance instance = this.selectedInstance();
        if (instance == null || this.runningProcess != null) return;
        String name = this.promptForInstanceName(
                Localization.text("main.dialog.rename", instance.name()),
                Localization.text("main.prompt.new_name"),
                instance.name()
        );
        if (name == null) return;
        this.runTask(
                instance.id(),
                Localization.text("main.status.renaming", instance.name()),
                () -> this.launcherService.renameInstance(instance, name),
                renamed -> {
                    this.instanceDetailsPanel.activityTab().moveLog(instance.id(), renamed.id());
                    this.appendActivity(renamed.id(), Localization.text("main.activity.renamed", renamed.name()));
                    this.refreshInstances(renamed.id());
                }
        );
    }

    private void duplicateSelectedInstance() {
        MinecraftInstance instance = this.selectedInstance();
        if (instance == null || this.runningProcess != null) return;
        String name = this.promptForInstanceName(
                Localization.text("main.dialog.duplicate", instance.name()),
                Localization.text("main.prompt.duplicate_name"),
                Localization.text("main.copy_suffix", instance.name())
        );
        if (name == null) return;
        this.runTask(
                instance.id(),
                Localization.text("main.status.duplicating", instance.name()),
                () -> this.launcherService.duplicateInstance(instance, name),
                duplicate -> {
                    this.appendActivity(duplicate.id(), Localization.text(
                            "main.activity.duplicated", instance.name(), duplicate.name()
                    ));
                    this.instanceSidebarPanel.clearSearch();
                    this.refreshInstances(duplicate.id());
                }
        );
    }

    private void convertSelectedInstance() {
        MinecraftInstance instance = this.selectedInstance();
        if (instance == null || this.runningProcess != null) return;
        ConvertInstanceDialog.InstanceConversion conversion = new ConvertInstanceDialog(this, instance).showModal();
        if (conversion == null) return;
        this.runTask(
                instance.id(),
                Localization.text("main.status.converting", instance.name()),
                () -> this.launcherService.convertInstance(instance, conversion.type(), conversion.loaderVersion()),
                converted -> {
                    this.appendActivity(converted.id(), Localization.text(
                            "main.activity.converted", converted.name(), LauncherFrame.displayName(converted.type())
                    ));
                    this.refreshInstances(converted.id());
                }
        );
    }

    private void chooseInstanceIcon() {
        MinecraftInstance instance = this.selectedInstance();
        if (instance == null) return;
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(Localization.text("main.dialog.choose_icon", instance.name()));
        chooser.setFileFilter(new FileNameExtensionFilter(
                Localization.text("file_filter.images"), "png", "jpg", "jpeg", "gif", "bmp"
        ));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Path source = chooser.getSelectedFile().toPath();
        this.runTask(
                instance.id(),
                Localization.text("main.status.updating_icon", instance.name()),
                () -> this.launcherService.setInstanceIcon(instance, source),
                updated -> {
                    this.appendActivity(updated.id(), Localization.text("main.activity.updated_icon", updated.name()));
                    this.refreshInstances(updated.id());
                }
        );
    }

    private void resetInstanceIcon() {
        MinecraftInstance instance = this.selectedInstance();
        if (instance == null || instance.iconKey() == null) return;
        this.runTask(
                instance.id(),
                Localization.text("main.status.resetting_icon", instance.name()),
                () -> this.launcherService.resetInstanceIcon(instance),
                updated -> {
                    this.appendActivity(updated.id(), Localization.text("main.activity.reset_icon", updated.name()));
                    this.refreshInstances(updated.id());
                }
        );
    }

    private void openInstanceFolder() {
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
            this.showError(Localization.text("main.error.open_instance_folder"), exception, instance.id());
        }
    }

    private void exportSelectedInstance() {
        MinecraftInstance instance = this.selectedInstance();
        if (instance == null) return;
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(Localization.text("main.dialog.export", instance.name()));
        chooser.setFileFilter(new FileNameExtensionFilter(Localization.text("file_filter.mmc_archives"), "zip"));
        chooser.setSelectedFile(new java.io.File(InstanceNames.folderName(instance.name()) + ".zip"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Path destination = chooser.getSelectedFile().toPath();
        this.runTask(
                instance.id(),
                Localization.text("main.status.exporting", instance.name()),
                () -> {
                    this.launcherService.exportInstance(instance, destination);
                    return null;
                },
                ignored -> {
                    this.setStatus(Localization.text("main.status.ready"));
                    this.appendActivity(instance.id(), Localization.text("main.activity.exported", instance.name()));
                }
        );
    }

    private void deleteSelectedInstance() {
        MinecraftInstance instance = this.selectedInstance();
        if (instance == null || this.runningProcess != null) return;
        int choice = JOptionPane.showConfirmDialog(
                this,
                Localization.text("main.confirm.delete", instance.name()),
                Localization.text("main.dialog.delete"),
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if (choice != JOptionPane.OK_OPTION) return;
        this.runTask(
                instance.id(),
                Localization.text("main.status.deleting", instance.name()),
                () -> {
                    this.launcherService.deleteInstance(instance);
                    return null;
                },
                ignored -> {
                    this.instanceDetailsPanel.activityTab().removeLog(instance.id());
                    this.refreshInstances(null);
                }
        );
    }

    private void installMod() {
        MinecraftInstance instance = this.selectedInstance();
        if (instance == null) return;
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(Localization.text("main.dialog.install_mod"));
        chooser.setFileFilter(new FileNameExtensionFilter(Localization.text("file_filter.java_archives"), "jar"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Path selectedFile = chooser.getSelectedFile().toPath();
        this.runTask(
                instance.id(),
                Localization.text("main.status.installing_mod", selectedFile.getFileName()),
                () -> {
                    this.launcherService.installMod(instance, selectedFile);
                    return null;
                },
                ignored -> {
                    this.setStatus(Localization.text("main.status.ready"));
                    this.refreshMods(instance);
                }
        );
    }

    private void toggleSelectedMod() {
        MinecraftInstance instance = this.selectedInstance();
        ManagedMod mod = this.selectedMod();
        if (instance == null || mod == null) return;
        boolean enable = mod.state() == ModState.DISABLED;
        this.runTask(
                instance.id(),
                Localization.text(enable ? "main.status.enabling_mod" : "main.status.disabling_mod", mod.fileName()),
                () -> {
                    this.launcherService.setModEnabled(instance, mod, enable);
                    return null;
                },
                ignored -> {
                    this.setStatus(Localization.text("main.status.ready"));
                    this.refreshMods(instance);
                }
        );
    }

    private void removeSelectedMod() {
        MinecraftInstance instance = this.selectedInstance();
        ManagedMod mod = this.selectedMod();
        if (instance == null || mod == null) return;
        int choice = JOptionPane.showConfirmDialog(
                this,
                Localization.text("main.confirm.remove_mod", mod.fileName()),
                Localization.text("main.dialog.remove_mod"),
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if (choice != JOptionPane.OK_OPTION) return;
        this.runTask(
                instance.id(),
                Localization.text("main.status.removing_mod", mod.fileName()),
                () -> {
                    this.launcherService.removeMod(instance, mod);
                    return null;
                },
                ignored -> {
                    this.setStatus(Localization.text("main.status.ready"));
                    this.refreshMods(instance);
                }
        );
    }

    private void stopMinecraft() {
        Process process = this.runningProcess;
        if (process == null || !process.isAlive()) return;
        int choice = JOptionPane.showConfirmDialog(
                this,
                Localization.text("main.confirm.stop"),
                Localization.text("main.dialog.stop"),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if (choice != JOptionPane.YES_OPTION) return;
        this.stopButton.setEnabled(false);
        this.setStatus(Localization.text("main.status.stopping_minecraft"));
        process.destroyForcibly();
    }

    private void launchSelectedInstance() {
        MinecraftInstance instance = this.selectedInstance();
        MinecraftAccount account = this.launcherService.account();
        if (instance == null || account == null || this.runningProcess != null) return;
        this.instanceDetailsPanel.showActivityTab();
        this.appendActivity(instance.id(), Localization.text(
                "main.activity.launching", instance.name(), account.username()
        ));
        this.runTask(
                instance.id(),
                Localization.text("main.status.preparing"),
                () -> this.launcherService.launch(instance),
                process -> {
                    this.refreshAccountSelector();
                    this.monitorProcess(instance, process);
                }
        );
    }

    private void refreshInstances(@Nullable String selectedId) {
        this.runTask(
                null,
                Localization.text("main.status.loading_instances"),
                this.launcherService::listInstances,
                instances -> {
                    Set<String> instanceIds = new HashSet<>();
                    for (MinecraftInstance instance : instances) instanceIds.add(instance.id());
                    this.instanceDetailsPanel.activityTab().retainLogs(instanceIds);
                    this.instanceSidebarPanel.setInstances(instances, selectedId);
                    this.setStatus(Localization.text(
                            instances.isEmpty() ? "main.status.add_instance" : "main.status.ready"
                    ));
                }
        );
    }

    private void showSelectedInstance() {
        MinecraftInstance instance = this.selectedInstance();
        this.instanceDetailsPanel.modsTab().setMods(List.of());
        if (instance == null) {
            this.instanceDetailsPanel.showEmpty(!this.instanceSidebarPanel.hasInstances());
            this.updateControlState();
            return;
        }
        this.instanceDetailsPanel.showInstance(instance);
        this.updateControlState();
        if (instance.type().hasMods) this.refreshMods(instance);
    }

    private void refreshMods(@NotNull MinecraftInstance instance) {
        new SwingWorker<List<ManagedMod>, Void>() {
            @Override
            @NotNull
            protected List<ManagedMod> doInBackground() throws Exception {
                return LauncherFrame.this.launcherService.listMods(instance);
            }

            @Override
            protected void done() {
                if (!instance.equals(LauncherFrame.this.selectedInstance())) return;
                try {
                    LauncherFrame.this.instanceDetailsPanel.modsTab().setMods(this.get());
                    LauncherFrame.this.updateControlState();
                }
                catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    LauncherFrame.this.showError(
                            Localization.text("main.error.loading_mods_interrupted"), exception, instance.id()
                    );
                }
                catch (ExecutionException exception) {
                    LauncherFrame.this.showError(
                            Localization.text("main.error.load_mods"), exception.getCause(), instance.id()
                    );
                }
            }
        }.execute();
    }

    private void monitorProcess(@NotNull MinecraftInstance instance, @NotNull Process process) {
        long launchTimeMillis = System.currentTimeMillis();
        long launchTimeNanos = System.nanoTime();
        this.runningProcess = process;
        this.runningActivityInstanceId = instance.id();
        this.runningActivityPrefix = "[" + instance.name() + "] ";
        this.setStatus(Localization.text("main.status.minecraft_running"));
        this.updateControlState();
        new SwingWorker<GameExit, Void>() {
            @Override
            @NotNull
            protected GameExit doInBackground() throws Exception {
                int exitCode = process.waitFor();
                long elapsedNanos = System.nanoTime() - launchTimeNanos;
                long elapsedSeconds = elapsedNanos <= 0 ? 0 : elapsedNanos / 1_000_000_000L;
                try {
                    MinecraftInstance updated = LauncherFrame.this.launcherService.recordPlaytime(
                            instance, elapsedSeconds, launchTimeMillis
                    );
                    return new GameExit(exitCode, updated, null);
                }
                catch (Exception exception) {
                    return new GameExit(exitCode, instance, exception);
                }
            }

            @Override
            protected void done() {
                LauncherFrame.this.runningProcess = null;
                try {
                    GameExit result = this.get();
                    LauncherFrame.this.setStatus(Localization.text("main.status.minecraft_exited", result.exitCode()));
                    LauncherFrame.this.appendActivity(
                            instance.id(),
                            Localization.text("main.activity.minecraft_exited", instance.name(), result.exitCode())
                    );
                    LauncherFrame.this.instanceSidebarPanel.replaceInstance(result.instance());
                    if (result.playtimeError() != null) {
                        LauncherFrame.this.showError(
                                Localization.text("main.error.save_playtime"), result.playtimeError(), instance.id()
                        );
                    }
                }
                catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    LauncherFrame.this.showError(
                            Localization.text("main.error.waiting_interrupted"), exception, instance.id()
                    );
                }
                catch (ExecutionException exception) {
                    LauncherFrame.this.showError(
                            Localization.text("main.error.monitor_minecraft"), exception.getCause(), instance.id()
                    );
                }
                LauncherFrame.this.runningActivityPrefix = null;
                LauncherFrame.this.runningActivityInstanceId = null;
                LauncherFrame.this.updateControlState();
            }
        }.execute();
    }

    private <T> void runTask(
            @Nullable String activityInstanceId,
            @NotNull String status,
            @NotNull BackgroundTask<T> task,
            @NotNull Consumer<T> onSuccess
    ) {
        if (this.busy) return;
        Object activityToken = new Object();
        this.taskActivityInstanceId = activityInstanceId;
        this.taskActivityToken = activityToken;
        this.setStatus(status);
        this.setBusy(true);
        new SwingWorker<T, Void>() {
            @Override
            protected T doInBackground() throws Exception {
                return task.run();
            }

            @Override
            protected void done() {
                LauncherFrame.this.setBusy(false);
                try {
                    onSuccess.accept(this.get());
                }
                catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    LauncherFrame.this.showError(
                            Localization.text("main.error.operation_interrupted"), exception, activityInstanceId
                    );
                }
                catch (ExecutionException exception) {
                    LauncherFrame.this.showError(
                            Localization.text("main.error.operation_failed"), exception.getCause(), activityInstanceId
                    );
                }
                catch (RuntimeException exception) {
                    LauncherFrame.this.showError(
                            Localization.text("main.error.operation_failed"), exception, activityInstanceId
                    );
                }
                finally {
                    if (LauncherFrame.this.taskActivityToken == activityToken) {
                        LauncherFrame.this.taskActivityToken = null;
                        LauncherFrame.this.taskActivityInstanceId = null;
                    }
                }
            }
        }.execute();
    }

    private void setBusy(boolean busy) {
        this.busy = busy;
        this.progressBar.setVisible(busy);
        this.updateControlState();
    }

    private void updateControlState() {
        boolean available = !this.busy;
        MinecraftInstance instance = this.selectedInstance();
        boolean instanceRunning = this.runningProcess != null;
        this.accountSelector.setEnabled(available && this.accountSelector.getItemCount() > 0);
        this.manageAccountsButton.setEnabled(available);
        this.settingsButton.setEnabled(available);
        this.instanceSidebarPanel.updateControlState(available, instanceRunning);
        this.instanceDetailsPanel.updateControlState(available, instance != null, instanceRunning);
        this.stopButton.setEnabled(available && instanceRunning && this.runningProcess.isAlive());
        this.launchButton.setEnabled(
                available && instance != null && this.launcherService.account() != null && !instanceRunning
        );
        this.launchButton.setText(Localization.text(instanceRunning ? "main.button.running" : "main.button.launch"));
    }

    private void refreshAccountSelector() {
        this.updatingAccountSelector = true;
        this.accountSelector.removeAllItems();
        for (MinecraftAccount account : this.launcherService.accounts()) this.accountSelector.addItem(account);
        this.accountSelector.setSelectedItem(this.launcherService.account());
        this.updatingAccountSelector = false;
        this.updateControlState();
    }

    private void showSettings(@NotNull SettingsDialog.SettingsTab selectedTab) {
        new SettingsDialog(this, this.launcherService, selectedTab).showModal();
        this.refreshAccountSelector();
        MinecraftAccount account = this.launcherService.account();
        if (account != null) this.setStatus(Localization.text("main.status.using_account", account.username()));
    }

    @Nullable
    private String promptForInstanceName(
            @NotNull String title,
            @NotNull String prompt,
            @NotNull String initialValue
    ) {
        String name = (String) JOptionPane.showInputDialog(
                this, prompt, title, JOptionPane.PLAIN_MESSAGE, null, null, initialValue
        );
        if (name == null) return null;
        name = name.trim();
        if (InstanceNames.isValid(name)) return name;
        JOptionPane.showMessageDialog(
                this,
                Localization.text("main.error.invalid_name"),
                Localization.text("main.dialog.invalid_name"),
                JOptionPane.ERROR_MESSAGE
        );
        return null;
    }

    private void setStatus(@NotNull String status) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> this.setStatus(status));
            return;
        }
        this.statusLabel.setText(status);
    }

    private void appendBackendOutput(@NotNull String line) {
        if (line.isBlank()) return;
        Object activityToken = this.taskActivityToken;
        String instanceId;
        if (activityToken != null) {
            instanceId = this.taskActivityInstanceId;
        }
        else {
            instanceId = this.runningActivityInstanceId;
            String prefix = this.runningActivityPrefix;
            if (prefix == null || !line.startsWith(prefix)) return;
        }
        if (instanceId != null) this.appendActivity(instanceId, line);
    }

    private void appendActivity(@NotNull String instanceId, @NotNull String message) {
        this.instanceDetailsPanel.activityTab().append(instanceId, message);
    }

    private void showError(
            @NotNull String title,
            @NotNull Throwable throwable,
            @Nullable String activityInstanceId
    ) {
        Throwable cause = LauncherFrame.rootCause(throwable);
        String message = cause.getMessage();
        if (message == null || message.isBlank()) message = cause.getClass().getSimpleName();
        this.setStatus(title + ".");
        if (activityInstanceId != null) {
            this.appendActivity(activityInstanceId, title + ": " + message.replace('\n', ' '));
        }
        JOptionPane.showMessageDialog(this, message, title, JOptionPane.ERROR_MESSAGE);
    }

    @Nullable
    private MinecraftInstance selectedInstance() {
        return this.instanceSidebarPanel.selectedInstance();
    }

    @Nullable
    private String selectedInstanceId() {
        return this.instanceSidebarPanel.selectedInstanceId();
    }

    @Nullable
    private ManagedMod selectedMod() {
        return this.instanceDetailsPanel.modsTab().selectedMod();
    }

    @NotNull
    private static Throwable rootCause(@NotNull Throwable throwable) {
        Throwable result = throwable;
        while (result.getCause() != null && result.getCause() != result) result = result.getCause();
        return result;
    }

    @NotNull
    public static String displayName(@NotNull Enum<?> value) {
        return switch (value) {
            case InstanceType type -> Localization.text("instance.type." + type.name().toLowerCase());
            case ModState state -> Localization.text("mod.state." + state.name().toLowerCase());
            case MinecraftAccount.AccountType accountType ->
                    Localization.text("account.type." + accountType.name().toLowerCase());
            default -> {
                String name = value.name().toLowerCase().replace('_', ' ');
                yield Character.toUpperCase(name.charAt(0)) + name.substring(1);
            }
        };
    }

    @NotNull
    public static String playtimeText(long totalTimePlayedSeconds) {
        long safeTotalTimePlayedSeconds = Math.max(0, totalTimePlayedSeconds);
        if (safeTotalTimePlayedSeconds < 3_600) {
            long minutes = safeTotalTimePlayedSeconds / 60;
            return Localization.text(
                    minutes == 1 ? "main.instance.playtime.minute" : "main.instance.playtime.minutes", minutes
            );
        }
        long tenthsOfAnHour = safeTotalTimePlayedSeconds / 360;
        if (safeTotalTimePlayedSeconds % 360 >= 180) tenthsOfAnHour++;
        return Localization.text(
                "main.instance.playtime", (tenthsOfAnHour / 10) + "." + (tenthsOfAnHour % 10)
        );
    }

    @FunctionalInterface
    private interface BackgroundTask<T> {
        T run() throws Exception;
    }

    private record GameExit(
            int exitCode,
            @NotNull MinecraftInstance instance,
            @Nullable Throwable playtimeError
    ) {}
}
