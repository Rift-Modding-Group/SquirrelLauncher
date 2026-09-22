package com.anightdazingzoroark.squirrellauncher.ui;

import com.anightdazingzoroark.squirrellauncher.SquirrelLauncher;
import com.anightdazingzoroark.squirrellauncher.launcher.InstanceAdditionRequest;
import com.anightdazingzoroark.squirrellauncher.launcher.InstanceNames;
import com.anightdazingzoroark.squirrellauncher.launcher.LauncherService;
import com.anightdazingzoroark.squirrellauncher.minecraft.auth.MinecraftAccount;
import com.anightdazingzoroark.squirrellauncher.minecraft.instance.MinecraftInstance;
import com.anightdazingzoroark.squirrellauncher.minecraft.mod.ManagedMod;
import com.anightdazingzoroark.squirrellauncher.minecraft.mod.ModState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

public final class LauncherFrame extends JFrame {
    private static final int MAX_ACTIVITY_CHARACTERS = 250_000;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final String EMPTY_INSTANCE_CARD = "empty";
    private static final String INSTANCE_DETAILS_CARD = "details";

    private final DefaultListModel<MinecraftInstance> instanceModel = new DefaultListModel<>();
    private final JList<MinecraftInstance> instanceList = new JList<>(this.instanceModel);
    private final JButton addInstanceButton = new JButton("Add new instance");
    private final JButton refreshInstancesButton = new JButton("Refresh");
    private final JPopupMenu instanceActionsMenu = new JPopupMenu();
    private final JMenuItem renameInstanceItem = new JMenuItem("Rename…");
    private final JMenuItem exportInstanceItem = new JMenuItem("Export…");
    private final JMenuItem deleteInstanceItem = new JMenuItem("Delete…");

    private final JLabel accountLabel = new JLabel();
    private final JTextField usernameField = new JTextField("Squirrel", 12);
    private final JButton offlineButton = new JButton("Use offline");
    private final JButton microsoftButton = new JButton("Sign in with Microsoft");

    private final JLabel instanceNameLabel = new JLabel("Select an instance");
    private final JLabel instanceTypeLabel = new JLabel("Type: —");
    private final JLabel loaderVersionLabel = new JLabel("Loader: —");
    private final JLabel modsHintLabel = new JLabel("Select a Forge or Cleanroom instance to manage mods.");
    private final ModTableModel modTableModel = new ModTableModel();
    private final JTable modTable = new JTable(this.modTableModel);
    private final JButton installModButton = new JButton("Install JAR…");
    private final JButton toggleModButton = new JButton("Enable");
    private final JButton removeModButton = new JButton("Remove");

    private final JTextArea activityArea = new JTextArea();
    private final JTabbedPane tabs = new JTabbedPane();
    private final CardLayout instanceContentLayout = new CardLayout();
    private final JPanel instanceContentCards = new JPanel(this.instanceContentLayout);
    private JPanel modsPanel;
    private JPanel activityPanel;
    private final JLabel statusLabel = new JLabel("Ready");
    private final JProgressBar progressBar = new JProgressBar();
    private final JButton launchButton = new JButton("Launch Minecraft");

    @NotNull
    private final LauncherService launcherService;
    private boolean busy;
    @Nullable
    private Process runningProcess;

    public LauncherFrame() {
        super(SquirrelLauncher.NAME);
        this.launcherService = new LauncherService(this::appendBackendOutput);

        this.setDefaultCloseOperation(EXIT_ON_CLOSE);
        this.setMinimumSize(new Dimension(820, 560));
        this.setExtendedState(JFrame.MAXIMIZED_BOTH);
        this.setLocationByPlatform(true);
        this.setLayout(new BorderLayout(0, 0));
        this.add(this.createAccountBar(), BorderLayout.NORTH);
        this.add(this.createMainContent(), BorderLayout.CENTER);
        this.add(this.createStatusBar(), BorderLayout.SOUTH);

        this.configureListeners();
        this.updateAccount(this.launcherService.account());
        this.updateControlState();
        this.appendActivity("SquirrelLauncher is ready.");
        this.refreshInstances(null);
    }

    private JPanel createAccountBar() {
        JPanel panel = new JPanel(new BorderLayout(12, 0));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));

        JLabel title = new JLabel(SquirrelLauncher.NAME);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        panel.add(title, BorderLayout.WEST);

        JPanel accountControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        accountControls.add(this.accountLabel);
        accountControls.add(this.usernameField);
        accountControls.add(this.offlineButton);
        accountControls.add(this.microsoftButton);
        panel.add(accountControls, BorderLayout.EAST);
        return panel;
    }

    private JSplitPane createMainContent() {
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, this.createInstanceSidebar(), this.createInstanceArea());
        splitPane.setResizeWeight(0.3);
        splitPane.setDividerLocation(310);
        splitPane.setBorder(null);
        return splitPane;
    }

    private JPanel createInstanceArea() {
        JPanel emptyPanel = new JPanel(new GridBagLayout());
        JLabel emptyMessage = new JLabel("You have no instances :(");
        emptyMessage.setFont(emptyMessage.getFont().deriveFont(Font.BOLD, 22f));
        emptyPanel.add(emptyMessage);

        this.instanceContentCards.add(emptyPanel, EMPTY_INSTANCE_CARD);
        this.instanceContentCards.add(createInstanceContent(), INSTANCE_DETAILS_CARD);
        this.instanceContentLayout.show(this.instanceContentCards, EMPTY_INSTANCE_CARD);
        return this.instanceContentCards;
    }

    private JPanel createInstanceSidebar() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 1, panel.getBackground().darker()),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));

        JLabel heading = new JLabel("Instances");
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 15f));
        panel.add(heading, BorderLayout.NORTH);

        this.instanceList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        this.instanceList.setCellRenderer(new InstanceCellRenderer());
        panel.add(new JScrollPane(this.instanceList), BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        actions.add(this.addInstanceButton);
        actions.add(this.refreshInstancesButton);
        panel.add(actions, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel createInstanceContent() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(14, 16, 10, 16));
        panel.add(createInstanceHeader(), BorderLayout.NORTH);

        this.modsPanel = createModsPanel();
        this.activityPanel = this.createActivityPanel();
        this.tabs.addTab("Mods", this.modsPanel);
        this.tabs.addTab("Activity", this.activityPanel);
        panel.add(this.tabs, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createInstanceHeader() {
        JPanel panel = new JPanel(new GridBagLayout());

        this.instanceNameLabel.setFont(this.instanceNameLabel.getFont().deriveFont(Font.BOLD, 22f));
        GridBagConstraints name = new GridBagConstraints();
        name.gridx = 0;
        name.gridy = 0;
        name.gridwidth = 2;
        name.weightx = 1;
        name.anchor = GridBagConstraints.LINE_START;
        name.insets = new Insets(0, 0, 5, 0);
        panel.add(this.instanceNameLabel, name);

        GridBagConstraints type = new GridBagConstraints();
        type.gridx = 0;
        type.gridy = 1;
        type.anchor = GridBagConstraints.LINE_START;
        type.insets = new Insets(0, 0, 0, 18);
        panel.add(this.instanceTypeLabel, type);

        GridBagConstraints loader = new GridBagConstraints();
        loader.gridx = 1;
        loader.gridy = 1;
        loader.weightx = 1;
        loader.anchor = GridBagConstraints.LINE_START;
        panel.add(this.loaderVersionLabel, loader);
        return panel;
    }

    private JPanel createModsPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        panel.add(this.modsHintLabel, BorderLayout.NORTH);

        this.modTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        this.modTable.setFillsViewportHeight(true);
        this.modTable.getColumnModel().getColumn(0).setPreferredWidth(420);
        this.modTable.getColumnModel().getColumn(1).setPreferredWidth(100);
        panel.add(new JScrollPane(this.modTable), BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        actions.add(this.installModButton);
        actions.add(this.toggleModButton);
        actions.add(this.removeModButton);
        panel.add(actions, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel createActivityPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        this.activityArea.setEditable(false);
        this.activityArea.setLineWrap(true);
        this.activityArea.setWrapStyleWord(true);
        this.activityArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        panel.add(new JScrollPane(this.activityArea), BorderLayout.CENTER);
        return panel;
    }

    private JPanel createStatusBar() {
        JPanel panel = new JPanel(new BorderLayout(10, 0));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, panel.getBackground().darker()),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)
        ));

        this.progressBar.setIndeterminate(true);
        this.progressBar.setVisible(false);
        this.progressBar.setPreferredSize(new Dimension(90, 16));
        JPanel status = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        status.add(this.progressBar);
        status.add(this.statusLabel);
        panel.add(status, BorderLayout.CENTER);

        this.launchButton.setFont(this.launchButton.getFont().deriveFont(Font.BOLD));
        panel.add(this.launchButton, BorderLayout.EAST);
        return panel;
    }

    private void configureListeners() {
        this.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                launcherService.close();
            }
        });

        this.instanceList.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) showSelectedInstance();
        });
        this.instanceActionsMenu.add(this.renameInstanceItem);
        this.instanceActionsMenu.add(this.exportInstanceItem);
        this.instanceActionsMenu.addSeparator();
        this.instanceActionsMenu.add(this.deleteInstanceItem);
        this.instanceList.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(@NotNull MouseEvent event) {
                boolean actionClick = SwingUtilities.isRightMouseButton(event);
                if (!actionClick || LauncherFrame.this.busy) return;

                int index = LauncherFrame.this.instanceList.locationToIndex(event.getPoint());
                Rectangle bounds = index < 0 ? null : LauncherFrame.this.instanceList.getCellBounds(index, index);
                if (bounds == null || !bounds.contains(event.getPoint())) return;

                LauncherFrame.this.instanceList.setSelectedIndex(index);
                LauncherFrame.this.updateControlState();
                LauncherFrame.this.instanceActionsMenu.show(
                        LauncherFrame.this.instanceList, event.getX(), event.getY()
                );
            }
        });
        this.modTable.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) updateControlState();
        });

        this.offlineButton.addActionListener(event -> this.useOfflineAccount());
        this.usernameField.addActionListener(event -> this.useOfflineAccount());
        this.microsoftButton.addActionListener(event -> {
            this.showActivityTab();
            this.appendActivity("Starting Microsoft sign in. Complete the browser prompt when it opens.");
            this.runTask(
                    "Waiting for Microsoft sign in…",
                    this.launcherService::signInWithMicrosoft,
                    account -> {
                        this.updateAccount(account);
                        this.setStatus("Signed in as " + account.username() + ".");
                        this.appendActivity("Microsoft sign in completed for " + account.username() + ".");
                    }
            );
        });
        this.addInstanceButton.addActionListener(event -> {
            AddInstanceDialog dialog = new AddInstanceDialog(this);
            dialog.setVisible(true);
            InstanceAdditionRequest request = dialog.result();
            if (request == null) return;

            this.runTask(
                    (request.importsInstance() ? "Importing " : "Creating ") + request.name() + "…",
                    () -> this.launcherService.addInstance(request),
                    instance -> {
                        this.appendActivity(
                                (request.importsInstance() ? "Imported MMC instance " : "Created instance ")
                                        + instance.name() + "."
                        );
                        this.refreshInstances(instance.id());
                    }
            );
        });
        this.renameInstanceItem.addActionListener(event -> {
            MinecraftInstance instance = this.selectedInstance();
            if (instance == null || this.runningProcess != null) return;
            String name = (String) JOptionPane.showInputDialog(
                    this,
                    "New instance name:",
                    "Rename " + instance.name(),
                    JOptionPane.PLAIN_MESSAGE,
                    null,
                    null,
                    instance.name()
            );
            if (name == null) return;
            name = name.trim();
            if (!InstanceNames.isValid(name)) {
                JOptionPane.showMessageDialog(
                        this,
                        "Instance names cannot be empty or contain control characters.",
                        "Invalid instance name",
                        JOptionPane.ERROR_MESSAGE
                );
                return;
            }

            String renamedName = name;
            this.runTask(
                    "Renaming " + instance.name() + "…",
                    () -> this.launcherService.renameInstance(instance, renamedName),
                    renamed -> {
                        this.appendActivity("Renamed instance to " + renamed.name() + ".");
                        this.refreshInstances(renamed.id());
                    }
            );
        });
        this.exportInstanceItem.addActionListener(event -> {
            MinecraftInstance instance = this.selectedInstance();
            if (instance == null) return;

            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Export " + instance.name() + " as an MMC instance");
            chooser.setFileFilter(new FileNameExtensionFilter("MMC instance archives (*.zip)", "zip"));
            chooser.setSelectedFile(new java.io.File(InstanceNames.folderName(instance.name()) + ".zip"));
            if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
            Path destination = chooser.getSelectedFile().toPath();

            this.runTask(
                    "Exporting " + instance.name() + "…",
                    () -> {
                        this.launcherService.exportInstance(instance, destination);
                        return null;
                    },
                    ignored -> {
                        this.setStatus("Ready");
                        this.appendActivity("Exported MMC instance " + instance.name() + ".");
                    }
            );
        });
        this.deleteInstanceItem.addActionListener(event -> {
            MinecraftInstance instance = this.selectedInstance();
            if (instance == null || this.runningProcess != null) return;

            int choice = JOptionPane.showConfirmDialog(
                    this,
                    "Are you sure you want to delete " + instance.name() + "? You cannot undo this!",
                    "Delete instance",
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );
            if (choice != JOptionPane.OK_OPTION) return;

            this.runTask(
                    "Deleting " + instance.name() + "…",
                    () -> {
                        this.launcherService.deleteInstance(instance);
                        return null;
                    },
                    ignored -> {
                        this.appendActivity("Deleted instance " + instance.name() + ".");
                        this.refreshInstances(null);
                    }
            );
        });
        this.refreshInstancesButton.addActionListener(event -> this.refreshInstances(this.selectedInstanceId()));
        this.installModButton.addActionListener(event -> {
            MinecraftInstance instance = this.selectedInstance();
            if (instance == null) return;

            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Install mod JAR");
            chooser.setFileFilter(new FileNameExtensionFilter("Java archives (*.jar)", "jar"));
            if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
            Path selectedFile = chooser.getSelectedFile().toPath();

            runTask(
                    "Installing " + selectedFile.getFileName() + "…",
                    () -> {
                        this.launcherService.installMod(instance, selectedFile);
                        return null;
                    },
                    ignored -> {
                        this.setStatus("Ready");
                        this.refreshMods(instance);
                    }
            );
        });
        this.toggleModButton.addActionListener(event -> {
            MinecraftInstance instance = this.selectedInstance();
            ManagedMod mod = selectedMod();
            if (instance == null || mod == null) return;
            boolean enable = mod.state() == ModState.DISABLED;

            this.runTask(
                    (enable ? "Enabling " : "Disabling ") + mod.fileName() + "…",
                    () -> {
                        this.launcherService.setModEnabled(instance, mod, enable);
                        return null;
                    },
                    ignored -> {
                        this.setStatus("Ready");
                        this.refreshMods(instance);
                    }
            );
        });
        this.removeModButton.addActionListener(event -> {
            MinecraftInstance instance = this.selectedInstance();
            ManagedMod mod = selectedMod();
            if (instance == null || mod == null) return;

            int choice = JOptionPane.showConfirmDialog(
                    this,
                    "Remove " + mod.fileName() + " from this instance?",
                    "Remove mod",
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );
            if (choice != JOptionPane.OK_OPTION) return;

            this.runTask(
                    "Removing " + mod.fileName() + "…",
                    () -> {
                        this.launcherService.removeMod(instance, mod);
                        return null;
                    },
                    ignored -> {
                        setStatus("Ready");
                        refreshMods(instance);
                    }
            );
        });
        this.launchButton.addActionListener(event -> {
            MinecraftInstance instance = this.selectedInstance();
            if (instance == null || this.runningProcess != null) return;

            this.showActivityTab();
            this.appendActivity("Launching " + instance.name() + " as " + this.launcherService.account().username() + ".");
            this.runTask(
                    "Installing and preparing Minecraft…",
                    () -> this.launcherService.launch(instance),
                    process -> monitorProcess(instance, process)
            );
        });
    }

    private void useOfflineAccount() {
        try {
            MinecraftAccount account = this.launcherService.useOfflineAccount(this.usernameField.getText());
            updateAccount(account);
            setStatus("Using offline account " + account.username() + ".");
            appendActivity("Switched to offline account " + account.username() + ".");
        }
        catch (RuntimeException exception) {
            showError("Could not use offline account", exception);
        }
    }

    private void refreshInstances(@Nullable String selectedId) {
        runTask(
                "Loading instances…",
                this.launcherService::listInstances,
                instances -> {
                    this.instanceModel.clear();
                    for (MinecraftInstance instance : instances) this.instanceModel.addElement(instance);

                    MinecraftInstance selection = findInstance(instances, selectedId);
                    if (selection == null && !instances.isEmpty()) selection = instances.getFirst();
                    this.instanceList.setSelectedValue(selection, true);
                    if (selection == null) showSelectedInstance();
                    setStatus(instances.isEmpty() ? "Add an instance to get started." : "Ready");
                }
        );
    }

    private void showSelectedInstance() {
        MinecraftInstance instance = selectedInstance();
        this.modTableModel.setMods(List.of());
        if (instance == null) {
            this.instanceContentLayout.show(this.instanceContentCards, EMPTY_INSTANCE_CARD);
            updateControlState();
            return;
        }

        this.instanceContentLayout.show(this.instanceContentCards, INSTANCE_DETAILS_CARD);
        this.instanceNameLabel.setText(instance.name());
        this.instanceTypeLabel.setText("Type: " + displayName(instance.type()));
        this.loaderVersionLabel.setText(instance.loaderVersion() == null
                ? "Minecraft " + SquirrelLauncher.VERSION
                : "Loader: " + instance.loaderVersion());

        if (!instance.type().hasMods) {
            this.setModsTabVisible(false);
            this.updateControlState();
        }
        else {
            this.setModsTabVisible(true);
            this.modsHintLabel.setText("Installed mods for " + instance.name());
            this.updateControlState();
            this.refreshMods(instance);
        }
    }

    private void refreshMods(MinecraftInstance instance) {
        new SwingWorker<List<ManagedMod>, Void>() {
            @Override
            protected List<ManagedMod> doInBackground() throws Exception {
                return launcherService.listMods(instance);
            }

            @Override
            protected void done() {
                if (!instance.equals(selectedInstance())) return;
                try {
                    modTableModel.setMods(get());
                    updateControlState();
                }
                catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    showError("Loading mods was interrupted", exception);
                }
                catch (ExecutionException exception) {
                    showError("Could not load mods", exception.getCause());
                }
            }
        }.execute();
    }

    private void monitorProcess(MinecraftInstance instance, Process process) {
        this.runningProcess = process;
        this.setStatus("Minecraft is running.");
        this.updateControlState();

        new SwingWorker<Integer, Void>() {
            @Override
            protected Integer doInBackground() throws Exception {
                return process.waitFor();
            }

            @Override
            protected void done() {
                runningProcess = null;
                try {
                    int exitCode = get();
                    setStatus("Minecraft exited with code " + exitCode + ".");
                    appendActivity(instance.name() + " exited with code " + exitCode + ".");
                }
                catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    showError("Waiting for Minecraft was interrupted", exception);
                }
                catch (ExecutionException exception) {
                    showError("Could not monitor Minecraft", exception.getCause());
                }
                updateControlState();
            }
        }.execute();
    }

    private <T> void runTask(String status, BackgroundTask<T> task, Consumer<T> onSuccess) {
        if (this.busy) return;
        this.setStatus(status);
        this.setBusy(true);

        new SwingWorker<T, Void>() {
            @Override
            protected T doInBackground() throws Exception {
                return task.run();
            }

            @Override
            protected void done() {
                setBusy(false);
                try {
                    onSuccess.accept(get());
                }
                catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    showError("Operation interrupted", exception);
                }
                catch (ExecutionException exception) {
                    showError("Operation failed", exception.getCause());
                }
                catch (RuntimeException exception) {
                    showError("Operation failed", exception);
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
        MinecraftInstance instance = selectedInstance();
        boolean supportsMods = instance != null && instance.type().hasMods;
        ManagedMod mod = selectedMod();

        this.usernameField.setEnabled(available);
        this.offlineButton.setEnabled(available);
        this.microsoftButton.setEnabled(available);
        this.addInstanceButton.setEnabled(available);
        this.renameInstanceItem.setEnabled(available && instance != null && this.runningProcess == null);
        this.exportInstanceItem.setEnabled(available && instance != null);
        this.deleteInstanceItem.setEnabled(available && instance != null && this.runningProcess == null);
        this.refreshInstancesButton.setEnabled(available);
        this.instanceList.setEnabled(available);
        this.modTable.setEnabled(available && supportsMods);
        this.installModButton.setEnabled(available && supportsMods);
        this.toggleModButton.setEnabled(available && supportsMods && mod != null);
        this.removeModButton.setEnabled(available && supportsMods && mod != null);
        this.toggleModButton.setText(mod != null && mod.state() == ModState.ENABLED ? "Disable" : "Enable");
        this.launchButton.setEnabled(available && instance != null && this.runningProcess == null);
        this.launchButton.setText(this.runningProcess == null ? "Launch Minecraft" : "Minecraft running");
    }

    private void updateAccount(MinecraftAccount account) {
        this.accountLabel.setText("Playing as " + account.username() + " (" + displayName(account.type()) + ")");
        if (account.type() == MinecraftAccount.AccountType.OFFLINE) {
            this.usernameField.setText(account.username());
        }
    }

    private void setModsTabVisible(boolean visible) {
        boolean currentlyVisible = this.tabs.indexOfComponent(this.modsPanel) >= 0;
        if (visible && !currentlyVisible) {
            this.tabs.insertTab("Mods", null, this.modsPanel, null, 0);
            this.tabs.setSelectedComponent(this.modsPanel);
        }
        else if (!visible && currentlyVisible) {
            this.tabs.remove(this.modsPanel);
        }
    }

    private void showActivityTab() {
        this.tabs.setSelectedComponent(this.activityPanel);
    }

    private void setStatus(String status) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> setStatus(status));
            return;
        }
        this.statusLabel.setText(status);
    }

    private void appendBackendOutput(String line) {
        if (!line.isBlank()) appendActivity(line);
    }

    private void appendActivity(String message) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> appendActivity(message));
            return;
        }

        String line = "[" + TIME_FORMAT.format(LocalTime.now()) + "] " + message + System.lineSeparator();
        this.activityArea.append(line);
        int extra = this.activityArea.getDocument().getLength() - MAX_ACTIVITY_CHARACTERS;
        if (extra > 0) this.activityArea.replaceRange("", 0, extra);
        this.activityArea.setCaretPosition(this.activityArea.getDocument().getLength());
    }

    private void showError(String title, Throwable throwable) {
        Throwable cause = rootCause(throwable);
        String message = cause.getMessage();
        if (message == null || message.isBlank()) message = cause.getClass().getSimpleName();
        setStatus(title + ".");
        appendActivity(title + ": " + message.replace('\n', ' '));
        JOptionPane.showMessageDialog(this, message, title, JOptionPane.ERROR_MESSAGE);
    }

    @Nullable
    private MinecraftInstance selectedInstance() {
        return this.instanceList.getSelectedValue();
    }

    @Nullable
    private String selectedInstanceId() {
        MinecraftInstance instance = selectedInstance();
        return instance == null ? null : instance.id();
    }

    @Nullable
    private ManagedMod selectedMod() {
        int row = this.modTable.getSelectedRow();
        return row < 0 ? null : this.modTableModel.modAt(row);
    }

    @Nullable
    private static MinecraftInstance findInstance(@NotNull List<MinecraftInstance> instances, @Nullable String id) {
        if (id == null) return null;
        for (MinecraftInstance instance : instances) {
            if (id.equals(instance.id())) return instance;
        }
        return null;
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable result = throwable;
        while (result.getCause() != null && result.getCause() != result) result = result.getCause();
        return result;
    }

    private static String displayName(Enum<?> value) {
        String name = value.name().toLowerCase().replace('_', ' ');
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    @FunctionalInterface
    private interface BackgroundTask<T> {
        T run() throws Exception;
    }

    private static final class InstanceCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof MinecraftInstance instance) {
                label.setText("<html><b>" + escapeHtml(instance.name()) + "</b><br><small>" + displayName(instance.type()) + "</small></html>");
                label.setBorder(BorderFactory.createEmptyBorder(6, 7, 6, 7));
            }
            return label;
        }

        private static String escapeHtml(String value) {
            return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        }
    }

    private static final class ModTableModel extends AbstractTableModel {
        private final List<ManagedMod> mods = new ArrayList<>();

        void setMods(List<ManagedMod> mods) {
            this.mods.clear();
            this.mods.addAll(mods);
            this.fireTableDataChanged();
        }

        ManagedMod modAt(int row) {
            return this.mods.get(row);
        }

        @Override
        public int getRowCount() {
            return this.mods.size();
        }

        @Override
        public int getColumnCount() {
            return 2;
        }

        @Override
        public String getColumnName(int column) {
            return column == 0 ? "Mod" : "State";
        }

        @Override
        public Object getValueAt(int row, int column) {
            ManagedMod mod = this.mods.get(row);
            return column == 0 ? mod.fileName() : displayName(mod.state());
        }
    }
}
