package com.anightdazingzoroark.squirrellauncher.ui;

import com.anightdazingzoroark.squirrellauncher.SquirrelLauncher;
import com.anightdazingzoroark.squirrellauncher.launcher.InstanceAdditionRequest;
import com.anightdazingzoroark.squirrellauncher.launcher.InstanceNames;
import com.anightdazingzoroark.squirrellauncher.launcher.LauncherService;
import com.anightdazingzoroark.squirrellauncher.minecraft.auth.MinecraftAccount;
import com.anightdazingzoroark.squirrellauncher.minecraft.instance.MinecraftInstance;
import com.anightdazingzoroark.squirrellauncher.minecraft.instance.InstanceType;
import com.anightdazingzoroark.squirrellauncher.minecraft.mod.ManagedMod;
import com.anightdazingzoroark.squirrellauncher.minecraft.mod.ModState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
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
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Desktop;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

public final class LauncherFrame extends JFrame {
    private static final int MAX_ACTIVITY_CHARACTERS = 250_000;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final String EMPTY_INSTANCE_CARD = "empty";
    private static final String INSTANCE_DETAILS_CARD = "details";

    private final DefaultListModel<MinecraftInstance> instanceModel = new DefaultListModel<>();
    private final JList<MinecraftInstance> instanceList = new JList<>(this.instanceModel);
    private final JButton addInstanceButton;
    private final JButton refreshInstancesButton;
    private final JPopupMenu instanceActionsMenu = new JPopupMenu();
    private final JMenuItem renameInstanceItem;
    private final JMenuItem duplicateInstanceItem;
    private final JMenuItem convertInstanceItem;
    private final JMenu instanceIconMenu;
    private final JMenuItem chooseInstanceIconItem;
    private final JMenuItem resetInstanceIconItem;
    private final JMenuItem openInFilesItem;
    private final JMenuItem exportInstanceItem;
    private final JMenuItem deleteInstanceItem;

    private final JComboBox<MinecraftAccount> accountSelector = new JComboBox<>();
    private final JButton manageAccountsButton;
    private final JButton settingsButton;

    private final JLabel instanceNameLabel;
    private final JLabel instanceTypeLabel;
    private final JLabel loaderVersionLabel;
    private final ModTableModel modTableModel = new ModTableModel();
    private final JTable modTable;
    private final JButton installModButton;
    private final JButton toggleModButton;
    private final JButton removeModButton;

    private final JTextArea activityArea = new JTextArea();
    @NotNull
    private final Map<String, StringBuilder> instanceActivityLogs = new HashMap<>();
    private final JTabbedPane tabs = new JTabbedPane();
    private final CardLayout instanceContentLayout = new CardLayout();
    private final JPanel instanceContentCards = new JPanel(this.instanceContentLayout);
    private JPanel modsPanel;
    private JPanel activityPanel;
    private final JLabel statusLabel;
    private final JProgressBar progressBar = new JProgressBar();
    private final JButton stopButton;
    private final JButton launchButton;

    @NotNull
    private final LauncherService launcherService;
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
        this.addInstanceButton = new JButton(Localization.text("main.button.add_instance"));
        this.refreshInstancesButton = new JButton(Localization.text("main.button.refresh"));
        this.renameInstanceItem = new JMenuItem(Localization.text("main.menu.rename"));
        this.duplicateInstanceItem = new JMenuItem(Localization.text("main.menu.duplicate"));
        this.convertInstanceItem = new JMenuItem(Localization.text("main.menu.convert"));
        this.instanceIconMenu = new JMenu(Localization.text("main.menu.icon"));
        this.chooseInstanceIconItem = new JMenuItem(Localization.text("main.menu.choose_icon"));
        this.resetInstanceIconItem = new JMenuItem(Localization.text("main.menu.reset_icon"));
        this.openInFilesItem = new JMenuItem(Localization.text("main.menu.open_in_files"));
        this.exportInstanceItem = new JMenuItem(Localization.text("main.menu.export"));
        this.deleteInstanceItem = new JMenuItem(Localization.text("main.menu.delete"));
        this.manageAccountsButton = new JButton(Localization.text("main.button.manage_accounts"));
        this.settingsButton = new JButton(Localization.text("main.button.settings"));
        this.instanceNameLabel = new JLabel(Localization.text("main.instance.select"));
        this.instanceTypeLabel = new JLabel(Localization.text("main.instance.type", "—"));
        this.loaderVersionLabel = new JLabel(Localization.text("main.instance.loader", "—"));
        this.modTable = new JTable(this.modTableModel);
        this.installModButton = new JButton(Localization.text("main.button.install_mod"));
        this.toggleModButton = new JButton(Localization.text("main.button.enable"));
        this.removeModButton = new JButton(Localization.text("main.button.remove"));
        this.statusLabel = new JLabel(Localization.text("main.status.ready"));
        this.stopButton = new JButton(Localization.text("main.button.stop"));
        this.launchButton = new JButton(Localization.text("main.button.launch"));

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
                this.showSettings(SquirrelLauncherDialog.SettingsTab.ACCOUNTS);
            }
        });
    }

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
            public Component getListCellRendererComponent(
                    JList<?> list,
                    Object value,
                    int index,
                    boolean selected,
                    boolean focused
            ) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, selected, focused);
                if (value instanceof MinecraftAccount account) {
                    String accountNameString = account.username();
                    if (account.type() == MinecraftAccount.AccountType.OFFLINE) {
                        accountNameString = accountNameString + " (" + displayName(MinecraftAccount.AccountType.OFFLINE) + ")";
                    }
                    label.setText(accountNameString);
                    label.setIcon(AccountIconProvider.INSTANCE.iconFor(
                            account,
                            LauncherFrame.this.accountSelector::repaint
                    ));
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

    private JSplitPane createMainContent() {
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, this.createInstanceSidebar(), this.createInstanceArea());
        splitPane.setResizeWeight(0.3);
        splitPane.setDividerLocation(310);
        splitPane.setBorder(null);
        return splitPane;
    }

    private JPanel createInstanceArea() {
        JPanel emptyPanel = new JPanel(new GridBagLayout());
        JLabel emptyMessage = new JLabel(Localization.text("main.instances.empty"));
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

        JLabel heading = new JLabel(Localization.text("main.instances.heading"));
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
        this.tabs.addTab(Localization.text("main.tab.activity"), this.activityPanel);
        this.tabs.addTab(Localization.text("main.tab.mods"), this.modsPanel);
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
        this.instanceActionsMenu.add(this.duplicateInstanceItem);
        this.instanceActionsMenu.add(this.convertInstanceItem);
        this.instanceIconMenu.add(this.chooseInstanceIconItem);
        this.instanceIconMenu.add(this.resetInstanceIconItem);
        this.instanceActionsMenu.add(this.instanceIconMenu);
        this.instanceActionsMenu.add(this.openInFilesItem);
        this.instanceActionsMenu.addSeparator();
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
        this.manageAccountsButton.addActionListener(event -> this.showSettings(SquirrelLauncherDialog.SettingsTab.ACCOUNTS));
        this.settingsButton.addActionListener(event -> this.showSettings(SquirrelLauncherDialog.SettingsTab.GAME));
        this.addInstanceButton.addActionListener(event -> {
            InstanceAdditionRequest request = SquirrelLauncherDialog.showAddInstanceDialog(this);
            if (request == null) return;

            this.runTask(
                    null,
                    Localization.text(
                            request.importsInstance() ? "main.status.importing" : "main.status.creating",
                            request.name()
                    ),
                    () -> this.launcherService.addInstance(request),
                    instance -> {
                        this.appendActivity(instance.id(), Localization.text(
                                request.importsInstance() ? "main.activity.imported" : "main.activity.created",
                                instance.name()
                        ));
                        this.refreshInstances(instance.id());
                    }
            );
        });
        this.renameInstanceItem.addActionListener(event -> {
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
                        StringBuilder activityLog = this.instanceActivityLogs.remove(instance.id());
                        if (activityLog != null) this.instanceActivityLogs.put(renamed.id(), activityLog);
                        this.appendActivity(
                                renamed.id(),
                                Localization.text("main.activity.renamed", renamed.name())
                        );
                        this.refreshInstances(renamed.id());
                    }
            );
        });
        this.duplicateInstanceItem.addActionListener(event -> {
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
                                "main.activity.duplicated",
                                instance.name(),
                                duplicate.name()
                        ));
                        this.refreshInstances(duplicate.id());
                    }
            );
        });
        this.convertInstanceItem.addActionListener(event -> {
            MinecraftInstance instance = this.selectedInstance();
            if (instance == null || this.runningProcess != null) return;
            SquirrelLauncherDialog.InstanceConversion conversion =
                    SquirrelLauncherDialog.showConvertInstanceDialog(this, instance);
            if (conversion == null) return;

            this.runTask(
                    instance.id(),
                    Localization.text("main.status.converting", instance.name()),
                    () -> this.launcherService.convertInstance(
                            instance,
                            conversion.type(),
                            conversion.loaderVersion()
                    ),
                    converted -> {
                        this.appendActivity(converted.id(), Localization.text(
                                "main.activity.converted",
                                converted.name(),
                                LauncherFrame.displayName(converted.type())
                        ));
                        this.refreshInstances(converted.id());
                    }
            );
        });
        this.chooseInstanceIconItem.addActionListener(event -> {
            MinecraftInstance instance = this.selectedInstance();
            if (instance == null) return;
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle(Localization.text("main.dialog.choose_icon", instance.name()));
            chooser.setFileFilter(new FileNameExtensionFilter(
                    Localization.text("file_filter.images"),
                    "png", "jpg", "jpeg", "gif", "bmp"
            ));
            if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
            Path source = chooser.getSelectedFile().toPath();
            this.runTask(
                    instance.id(),
                    Localization.text("main.status.updating_icon", instance.name()),
                    () -> this.launcherService.setInstanceIcon(instance, source),
                    updated -> {
                        this.appendActivity(
                                updated.id(),
                                Localization.text("main.activity.updated_icon", updated.name())
                        );
                        this.refreshInstances(updated.id());
                    }
            );
        });
        this.resetInstanceIconItem.addActionListener(event -> {
            MinecraftInstance instance = this.selectedInstance();
            if (instance == null || instance.iconKey() == null) return;
            this.runTask(
                    instance.id(),
                    Localization.text("main.status.resetting_icon", instance.name()),
                    () -> this.launcherService.resetInstanceIcon(instance),
                    updated -> {
                        this.appendActivity(
                                updated.id(),
                                Localization.text("main.activity.reset_icon", updated.name())
                        );
                        this.refreshInstances(updated.id());
                    }
            );
        });
        this.openInFilesItem.addActionListener(event -> {
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
        });
        this.exportInstanceItem.addActionListener(event -> {
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
                        this.appendActivity(
                                instance.id(),
                                Localization.text("main.activity.exported", instance.name())
                        );
                    }
            );
        });
        this.deleteInstanceItem.addActionListener(event -> {
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
                        this.instanceActivityLogs.remove(instance.id());
                        this.refreshInstances(null);
                    }
            );
        });
        this.refreshInstancesButton.addActionListener(event -> this.refreshInstances(this.selectedInstanceId()));
        this.installModButton.addActionListener(event -> {
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
        });
        this.toggleModButton.addActionListener(event -> {
            MinecraftInstance instance = this.selectedInstance();
            ManagedMod mod = selectedMod();
            if (instance == null || mod == null) return;
            boolean enable = mod.state() == ModState.DISABLED;

            this.runTask(
                    instance.id(),
                    Localization.text(
                            enable ? "main.status.enabling_mod" : "main.status.disabling_mod",
                            mod.fileName()
                    ),
                    () -> {
                        this.launcherService.setModEnabled(instance, mod, enable);
                        return null;
                    },
                    ignored -> {
                        this.setStatus(Localization.text("main.status.ready"));
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
                        setStatus(Localization.text("main.status.ready"));
                        refreshMods(instance);
                    }
            );
        });
        this.stopButton.addActionListener(event -> {
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
        });
        this.launchButton.addActionListener(event -> {
            MinecraftInstance instance = this.selectedInstance();
            MinecraftAccount account = this.launcherService.account();
            if (instance == null || account == null || this.runningProcess != null) return;

            this.showActivityTab();
            this.appendActivity(instance.id(), Localization.text(
                    "main.activity.launching",
                    instance.name(),
                    account.username()
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
        });
    }

    private void refreshInstances(@Nullable String selectedId) {
        this.runTask(
                null,
                Localization.text("main.status.loading_instances"),
                this.launcherService::listInstances,
                instances -> {
                    Set<String> instanceIds = new HashSet<>();
                    for (MinecraftInstance instance : instances) instanceIds.add(instance.id());
                    this.instanceActivityLogs.keySet().removeIf(id -> !instanceIds.contains(id));

                    this.instanceModel.clear();
                    for (MinecraftInstance instance : instances) this.instanceModel.addElement(instance);

                    MinecraftInstance selection = findInstance(instances, selectedId);
                    if (selection == null && !instances.isEmpty()) selection = instances.getFirst();
                    this.instanceList.setSelectedValue(selection, true);
                    if (selection == null) showSelectedInstance();
                    setStatus(Localization.text(
                            instances.isEmpty() ? "main.status.add_instance" : "main.status.ready"
                    ));
                }
        );
    }

    private void showSelectedInstance() {
        MinecraftInstance instance = this.selectedInstance();
        this.modTableModel.setMods(List.of());
        if (instance == null) {
            this.instanceContentLayout.show(this.instanceContentCards, EMPTY_INSTANCE_CARD);
            updateControlState();
            return;
        }

        this.instanceContentLayout.show(this.instanceContentCards, INSTANCE_DETAILS_CARD);
        StringBuilder activityLog = this.instanceActivityLogs.get(instance.id());
        this.activityArea.setText(activityLog == null ? "" : activityLog.toString());
        this.activityArea.setCaretPosition(this.activityArea.getDocument().getLength());
        this.showActivityTab();
        this.instanceNameLabel.setText(instance.name());
        this.instanceNameLabel.setIcon(InstanceIconProvider.INSTANCE.iconFor(instance));
        this.instanceNameLabel.setIconTextGap(12);
        this.instanceTypeLabel.setText(Localization.text(
                "main.instance.type",
                LauncherFrame.displayName(instance.type())
        ));
        this.loaderVersionLabel.setText(instance.loaderVersion() == null
                ? "Minecraft " + SquirrelLauncher.VERSION
                : Localization.text("main.instance.loader", instance.loaderVersion()));

        if (!instance.type().hasMods) {
            this.setModsTabVisible(false);
            this.updateControlState();
        }
        else {
            this.setModsTabVisible(true);
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
                    showError(Localization.text("main.error.loading_mods_interrupted"), exception, instance.id());
                }
                catch (ExecutionException exception) {
                    showError(Localization.text("main.error.load_mods"), exception.getCause(), instance.id());
                }
            }
        }.execute();
    }

    private void monitorProcess(MinecraftInstance instance, Process process) {
        this.runningProcess = process;
        this.runningActivityInstanceId = instance.id();
        this.runningActivityPrefix = "[" + instance.name() + "] ";
        this.setStatus(Localization.text("main.status.minecraft_running"));
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
                    setStatus(Localization.text("main.status.minecraft_exited", exitCode));
                    appendActivity(
                            instance.id(),
                            Localization.text("main.activity.minecraft_exited", instance.name(), exitCode)
                    );
                }
                catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    showError(Localization.text("main.error.waiting_interrupted"), exception, instance.id());
                }
                catch (ExecutionException exception) {
                    showError(Localization.text("main.error.monitor_minecraft"), exception.getCause(), instance.id());
                }
                runningActivityPrefix = null;
                runningActivityInstanceId = null;
                updateControlState();
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
                setBusy(false);
                try {
                    onSuccess.accept(get());
                }
                catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    showError(Localization.text("main.error.operation_interrupted"), exception, activityInstanceId);
                }
                catch (ExecutionException exception) {
                    showError(Localization.text("main.error.operation_failed"), exception.getCause(), activityInstanceId);
                }
                catch (RuntimeException exception) {
                    showError(Localization.text("main.error.operation_failed"), exception, activityInstanceId);
                }
                finally {
                    if (taskActivityToken == activityToken) {
                        taskActivityToken = null;
                        taskActivityInstanceId = null;
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
        MinecraftInstance instance = selectedInstance();
        boolean supportsMods = instance != null && instance.type().hasMods;
        ManagedMod mod = this.selectedMod();

        this.accountSelector.setEnabled(available && this.accountSelector.getItemCount() > 0);
        this.manageAccountsButton.setEnabled(available);
        this.settingsButton.setEnabled(available);
        this.addInstanceButton.setEnabled(available);
        this.renameInstanceItem.setEnabled(available && instance != null && this.runningProcess == null);
        this.duplicateInstanceItem.setEnabled(available && instance != null && this.runningProcess == null);
        this.convertInstanceItem.setEnabled(available && instance != null && this.runningProcess == null);
        this.instanceIconMenu.setEnabled(available && instance != null);
        this.chooseInstanceIconItem.setEnabled(available && instance != null);
        this.resetInstanceIconItem.setEnabled(available && instance != null && instance.iconKey() != null);
        this.openInFilesItem.setEnabled(available && instance != null);
        this.exportInstanceItem.setEnabled(available && instance != null);
        this.deleteInstanceItem.setEnabled(available && instance != null && this.runningProcess == null);
        this.refreshInstancesButton.setEnabled(available);
        this.instanceList.setEnabled(available);
        this.modTable.setEnabled(available && supportsMods);
        this.installModButton.setEnabled(available && supportsMods);
        this.toggleModButton.setEnabled(available && supportsMods && mod != null);
        this.removeModButton.setEnabled(available && supportsMods && mod != null);
        this.stopButton.setEnabled(available && this.runningProcess != null && this.runningProcess.isAlive());
        this.toggleModButton.setText(Localization.text(
                mod != null && mod.state() == ModState.ENABLED ? "main.button.disable" : "main.button.enable"
        ));
        this.launchButton.setEnabled(
                available && instance != null && this.launcherService.account() != null && this.runningProcess == null
        );
        this.launchButton.setText(Localization.text(
                this.runningProcess == null ? "main.button.launch" : "main.button.running"
        ));
    }

    private void refreshAccountSelector() {
        this.updatingAccountSelector = true;
        this.accountSelector.removeAllItems();
        for (MinecraftAccount account : this.launcherService.accounts()) this.accountSelector.addItem(account);
        this.accountSelector.setSelectedItem(this.launcherService.account());
        this.updatingAccountSelector = false;
        this.updateControlState();
    }

    private void showSettings(@NotNull SquirrelLauncherDialog.SettingsTab selectedTab) {
        SquirrelLauncherDialog.showSettingsDialog(this, this.launcherService, selectedTab);
        this.refreshAccountSelector();
        MinecraftAccount account = this.launcherService.account();
        if (account != null) {
            this.setStatus(Localization.text("main.status.using_account", account.username()));
        }
    }

    @Nullable
    private String promptForInstanceName(@NotNull String title, @NotNull String prompt, @NotNull String initialValue) {
        String name = (String) JOptionPane.showInputDialog(
                this, prompt, title,
                JOptionPane.PLAIN_MESSAGE, null, null, initialValue
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

    private void setModsTabVisible(boolean visible) {
        boolean currentlyVisible = this.tabs.indexOfComponent(this.modsPanel) >= 0;
        if (visible && !currentlyVisible) {
            this.tabs.insertTab(Localization.text("main.tab.mods"), null, this.modsPanel, null, 1);
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
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> appendActivity(instanceId, message));
            return;
        }

        String line = "[" + TIME_FORMAT.format(LocalTime.now()) + "] " + message + System.lineSeparator();
        StringBuilder activityLog = this.instanceActivityLogs.computeIfAbsent(instanceId, ignored -> new StringBuilder());
        activityLog.append(line);
        int extra = activityLog.length() - MAX_ACTIVITY_CHARACTERS;
        if (extra > 0) activityLog.delete(0, extra);

        if (instanceId.equals(this.selectedInstanceId())) {
            this.activityArea.append(line);
            extra = this.activityArea.getDocument().getLength() - MAX_ACTIVITY_CHARACTERS;
            if (extra > 0) this.activityArea.replaceRange("", 0, extra);
            this.activityArea.setCaretPosition(this.activityArea.getDocument().getLength());
        }
    }

    private void showError(
            @NotNull String title,
            @NotNull Throwable throwable,
            @Nullable String activityInstanceId
    ) {
        Throwable cause = rootCause(throwable);
        String message = cause.getMessage();
        if (message == null || message.isBlank()) message = cause.getClass().getSimpleName();
        setStatus(title + ".");
        if (activityInstanceId != null) {
            this.appendActivity(activityInstanceId, title + ": " + message.replace('\n', ' '));
        }
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

    @NotNull
    public static String displayName(@NotNull Enum<?> value) {
        return switch (value) {
            case InstanceType type -> Localization.text("instance.type." + type.name().toLowerCase());
            case ModState state -> Localization.text("mod.state." + state.name().toLowerCase());
            case MinecraftAccount.AccountType accountType -> Localization.text("account.type." + accountType.name().toLowerCase());
            default -> {
                String name = value.name().toLowerCase().replace('_', ' ');
                yield Character.toUpperCase(name.charAt(0)) + name.substring(1);
            }
        };
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
                label.setText(
                        "<html><b>" + InstanceCellRenderer.escapeHtml(instance.name()) + "</b><br><small>"
                                + LauncherFrame.displayName(instance.type()) + "</small></html>"
                );
                label.setIcon(InstanceIconProvider.INSTANCE.iconFor(instance));
                label.setIconTextGap(10);
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

        private void setMods(List<ManagedMod> mods) {
            this.mods.clear();
            this.mods.addAll(mods);
            this.fireTableDataChanged();
        }

        private ManagedMod modAt(int row) {
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
            return Localization.text(column == 0 ? "table.mod.name" : "table.mod.state");
        }

        @Override
        public Object getValueAt(int row, int column) {
            ManagedMod mod = this.mods.get(row);
            return column == 0 ? mod.fileName() : LauncherFrame.displayName(mod.state());
        }
    }
}
