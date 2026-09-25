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
import javax.swing.DropMode;
import javax.swing.Icon;
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
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.OverlayLayout;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.TransferHandler;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.BasicStroke;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

public final class LauncherFrame extends JFrame {
    private static final int MAX_ACTIVITY_CHARACTERS = 250_000;
    private static final int INSTANCE_GEAR_WIDTH = 36;
    private static final int INSTANCE_SIDEBAR_WIDTH = 400;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final String EMPTY_INSTANCE_CARD = "empty";
    private static final String INSTANCE_DETAILS_CARD = "details";
    private static final String INSTANCE_HEADER_CARD = "header";
    private static final String INSTANCE_SEARCH_CARD = "search";

    private final DefaultListModel<MinecraftInstance> instanceModel = new DefaultListModel<>();
    private final JList<MinecraftInstance> instanceList = new JList<>(this.instanceModel);
    @NotNull
    private final List<MinecraftInstance> instances = new ArrayList<>();
    private final JButton addInstanceButton;
    private final JButton refreshInstancesButton;
    @NotNull
    private final JButton searchInstancesButton;
    @NotNull
    private final JButton sortInstancesButton;
    @NotNull
    private final JButton closeInstanceSearchButton;
    @NotNull
    private final JTextField instanceSearchField = new JTextField();
    @NotNull
    private final CardLayout instanceHeaderLayout = new CardLayout();
    @NotNull
    private final JPanel instanceHeaderCards = new JPanel(this.instanceHeaderLayout);
    @NotNull
    private final JPopupMenu instanceSortMenu = new JPopupMenu();
    private final JPopupMenu instanceActionsMenu = new JPopupMenu();
    @NotNull
    private final JPopupMenu emptyInstanceActionsMenu = new JPopupMenu();
    @NotNull
    private final JMenuItem addInstanceMenuItem;
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

    @NotNull
    private final JLabel instanceNameLabel;
    @NotNull
    private final JLabel instanceIconLabel = new JLabel();
    @NotNull
    private final JLabel instanceNamePencilLabel = new JLabel(SidebarIcon.PENCIL);
    @NotNull
    private final JLabel instanceIconPencilLabel = new JLabel(SidebarIcon.PENCIL);
    @NotNull
    private final JPanel instanceNameEditorPanel = new JPanel(new BorderLayout(6, 0));
    @NotNull
    private final JPanel instanceIconEditorPanel = new JPanel();
    private final JLabel instanceTypeLabel;
    private final JLabel loaderVersionLabel;
    @NotNull
    private final JLabel instancePlaytimeLabel;
    @NotNull
    private final JLabel emptyInstanceMessage;
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
        this.searchInstancesButton = new JButton(SidebarIcon.SEARCH);
        this.sortInstancesButton = new JButton(SidebarIcon.SORT);
        this.closeInstanceSearchButton = new JButton(SidebarIcon.CLOSE);
        this.searchInstancesButton.setToolTipText(Localization.text("main.button.search_instances"));
        this.sortInstancesButton.setToolTipText(Localization.text("main.button.sort_instances"));
        this.closeInstanceSearchButton.setToolTipText(Localization.text("main.button.close_search"));
        this.searchInstancesButton.getAccessibleContext().setAccessibleName(Localization.text("main.button.search_instances"));
        this.sortInstancesButton.getAccessibleContext().setAccessibleName(Localization.text("main.button.sort_instances"));
        this.closeInstanceSearchButton.getAccessibleContext().setAccessibleName(Localization.text("main.button.close_search"));
        this.instanceSearchField.setToolTipText(Localization.text("main.prompt.search_instances"));
        this.instanceSearchField.getAccessibleContext().setAccessibleName(Localization.text("main.dialog.search_instances"));
        this.addInstanceMenuItem = new JMenuItem(Localization.text("main.button.add_instance"));
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
        this.instanceNameEditorPanel.setToolTipText(Localization.text("main.tooltip.rename_instance"));
        this.instanceIconEditorPanel.setToolTipText(Localization.text("main.tooltip.manage_instance_icon"));
        this.instanceNameEditorPanel.getAccessibleContext().setAccessibleName(
                Localization.text("main.tooltip.rename_instance")
        );
        this.instanceIconEditorPanel.getAccessibleContext().setAccessibleName(
                Localization.text("main.tooltip.manage_instance_icon")
        );
        this.instanceTypeLabel = new JLabel(Localization.text("main.instance.type", "—"));
        this.loaderVersionLabel = new JLabel(Localization.text("main.instance.loader", "—"));
        this.instancePlaytimeLabel = new JLabel(LauncherFrame.playtimeText(0));
        this.emptyInstanceMessage = new JLabel(Localization.text("main.instances.empty"));
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

    private JPanel createMainContent() {
        JPanel panel = new JPanel(new BorderLayout());
        JPanel instanceSidebar = this.createInstanceSidebar();
        instanceSidebar.setPreferredSize(new Dimension(INSTANCE_SIDEBAR_WIDTH, 0));
        instanceSidebar.setMinimumSize(new Dimension(INSTANCE_SIDEBAR_WIDTH, 0));
        instanceSidebar.setMaximumSize(new Dimension(INSTANCE_SIDEBAR_WIDTH, Integer.MAX_VALUE));
        panel.add(instanceSidebar, BorderLayout.WEST);
        panel.add(this.createInstanceArea(), BorderLayout.CENTER);
        return panel;
    }

    private JPanel createInstanceArea() {
        JPanel emptyPanel = new JPanel(new GridBagLayout());
        this.emptyInstanceMessage.setFont(this.emptyInstanceMessage.getFont().deriveFont(Font.BOLD, 22f));
        emptyPanel.add(this.emptyInstanceMessage);

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
        JPanel headingBar = new JPanel(new BorderLayout(6, 0));
        headingBar.add(heading, BorderLayout.WEST);
        JPanel headingActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        headingActions.add(this.searchInstancesButton);
        headingActions.add(this.sortInstancesButton);
        headingBar.add(headingActions, BorderLayout.EAST);

        JPanel searchBar = new JPanel(new BorderLayout(4, 0));
        searchBar.add(this.instanceSearchField, BorderLayout.CENTER);
        searchBar.add(this.closeInstanceSearchButton, BorderLayout.EAST);
        this.instanceHeaderCards.add(headingBar, INSTANCE_HEADER_CARD);
        this.instanceHeaderCards.add(searchBar, INSTANCE_SEARCH_CARD);
        this.instanceHeaderLayout.show(this.instanceHeaderCards, INSTANCE_HEADER_CARD);
        panel.add(this.instanceHeaderCards, BorderLayout.NORTH);

        this.instanceList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        this.instanceList.setCellRenderer(new InstanceCellRenderer());
        this.instanceList.setDragEnabled(true);
        this.instanceList.setDropMode(DropMode.INSERT);
        this.instanceList.setTransferHandler(new TransferHandler() {
            @Nullable
            private String draggedInstanceId;

            @Override
            @Nullable
            protected Transferable createTransferable(JComponent component) {
                MinecraftInstance instance = LauncherFrame.this.selectedInstance();
                this.draggedInstanceId = instance == null ? null : instance.id();
                return this.draggedInstanceId == null ? null : new StringSelection(this.draggedInstanceId);
            }

            @Override
            public int getSourceActions(JComponent component) {
                return MOVE;
            }

            @Override
            public boolean canImport(TransferSupport support) {
                return support.isDrop()
                        && !LauncherFrame.this.busy
                        && LauncherFrame.this.instanceSearchField.getText().isBlank()
                        && support.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.stringFlavor);
            }

            @Override
            public boolean importData(TransferSupport support) {
                if (!this.canImport(support) || this.draggedInstanceId == null) return false;
                int sourceIndex = -1;
                for (int index = 0; index < LauncherFrame.this.instances.size(); index++) {
                    if (this.draggedInstanceId.equals(LauncherFrame.this.instances.get(index).id())) {
                        sourceIndex = index;
                        break;
                    }
                }
                if (sourceIndex < 0) return false;

                JList.DropLocation drop = (JList.DropLocation) support.getDropLocation();
                int destinationIndex = drop.getIndex();
                if (destinationIndex > sourceIndex) destinationIndex--;
                if (destinationIndex == sourceIndex) return false;

                List<MinecraftInstance> reordered = new ArrayList<>(LauncherFrame.this.instances);
                MinecraftInstance moved = reordered.remove(sourceIndex);
                destinationIndex = Math.clamp(destinationIndex, 0, reordered.size());
                reordered.add(destinationIndex, moved);
                try {
                    LauncherFrame.this.launcherService.reorderInstances(reordered);
                    LauncherFrame.this.instances.clear();
                    LauncherFrame.this.instances.addAll(reordered);
                    LauncherFrame.this.rebuildInstanceList(moved.id());
                    LauncherFrame.this.setStatus(Localization.text("main.status.instances_reordered"));
                    return true;
                }
                catch (Exception exception) {
                    LauncherFrame.this.showError(
                            Localization.text("main.error.reorder_instances"),
                            exception,
                            null
                    );
                    return false;
                }
            }
        });
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
        this.instanceNamePencilLabel.setVisible(false);
        this.instanceIconPencilLabel.setVisible(false);
        this.instanceNameEditorPanel.setOpaque(false);
        this.instanceIconEditorPanel.setOpaque(false);
        this.instanceNameEditorPanel.setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));
        this.instanceIconEditorPanel.setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));
        this.instanceNameEditorPanel.add(this.instanceNameLabel, BorderLayout.CENTER);
        this.instanceNameEditorPanel.add(this.instanceNamePencilLabel, BorderLayout.EAST);
        this.instanceIconEditorPanel.setLayout(new OverlayLayout(this.instanceIconEditorPanel));
        this.instanceIconLabel.setAlignmentX(0.5f);
        this.instanceIconLabel.setAlignmentY(0.5f);
        this.instanceIconPencilLabel.setAlignmentX(0.0f);
        this.instanceIconPencilLabel.setAlignmentY(1.0f);
        this.instanceIconEditorPanel.add(this.instanceIconLabel);
        this.instanceIconEditorPanel.add(this.instanceIconPencilLabel);
        this.instanceIconEditorPanel.setComponentZOrder(this.instanceIconPencilLabel, 0);

        GridBagConstraints icon = new GridBagConstraints();
        icon.gridx = 0;
        icon.gridy = 0;
        icon.gridheight = 2;
        icon.anchor = GridBagConstraints.FIRST_LINE_START;
        icon.insets = new Insets(0, 0, 0, 12);
        panel.add(this.instanceIconEditorPanel, icon);

        GridBagConstraints name = new GridBagConstraints();
        name.gridx = 1;
        name.gridy = 0;
        name.gridwidth = 3;
        name.weightx = 1;
        name.anchor = GridBagConstraints.LINE_START;
        name.insets = new Insets(0, 0, 5, 0);
        panel.add(this.instanceNameEditorPanel, name);

        GridBagConstraints type = new GridBagConstraints();
        type.gridx = 1;
        type.gridy = 1;
        type.anchor = GridBagConstraints.LINE_START;
        type.insets = new Insets(0, 0, 0, 18);
        panel.add(this.instanceTypeLabel, type);

        GridBagConstraints loader = new GridBagConstraints();
        loader.gridx = 2;
        loader.gridy = 1;
        loader.weightx = 1;
        loader.anchor = GridBagConstraints.LINE_START;
        panel.add(this.loaderVersionLabel, loader);

        GridBagConstraints playtime = new GridBagConstraints();
        playtime.gridx = 3;
        playtime.gridy = 1;
        playtime.anchor = GridBagConstraints.LINE_END;
        panel.add(this.instancePlaytimeLabel, playtime);
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

        this.searchInstancesButton.addActionListener(event -> {
            this.instanceHeaderLayout.show(this.instanceHeaderCards, INSTANCE_SEARCH_CARD);
            this.instanceSearchField.requestFocusInWindow();
            this.instanceSearchField.selectAll();
        });
        this.closeInstanceSearchButton.addActionListener(event -> {
            this.instanceSearchField.setText("");
            this.instanceHeaderLayout.show(this.instanceHeaderCards, INSTANCE_HEADER_CARD);
        });
        this.instanceHeaderCards.registerKeyboardAction(
                event -> {
                    if (this.instanceSearchField.isShowing()) this.closeInstanceSearchButton.doClick();
                },
                KeyStroke.getKeyStroke("ESCAPE"),
                JComponent.WHEN_IN_FOCUSED_WINDOW
        );
        this.instanceSearchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(@NotNull DocumentEvent event) {
                LauncherFrame.this.updateInstanceSearch();
            }

            @Override
            public void removeUpdate(@NotNull DocumentEvent event) {
                LauncherFrame.this.updateInstanceSearch();
            }

            @Override
            public void changedUpdate(@NotNull DocumentEvent event) {
                LauncherFrame.this.updateInstanceSearch();
            }
        });
        for (InstanceSortMode mode : InstanceSortMode.values()) {
            JMenuItem sortItem = new JMenuItem(Localization.text(mode.localizationKey));
            sortItem.addActionListener(event -> {
                String selectedId = this.selectedInstanceId();
                List<MinecraftInstance> sorted = new ArrayList<>(this.instances);
                Comparator<MinecraftInstance> nameAscending = Comparator
                        .comparing(MinecraftInstance::name, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(MinecraftInstance::id);
                Comparator<MinecraftInstance> comparator = switch (mode) {
                    case NAME_ASCENDING -> nameAscending;
                    case NAME_DESCENDING -> nameAscending.reversed();
                    case PLAYTIME -> Comparator.comparingLong(MinecraftInstance::totalTimePlayedSeconds)
                            .reversed()
                            .thenComparing(nameAscending);
                    case OLDEST -> Comparator.comparingLong(MinecraftInstance::createdTimeMillis)
                            .thenComparing(nameAscending);
                    case NEWEST -> Comparator.comparingLong(MinecraftInstance::createdTimeMillis)
                            .reversed()
                            .thenComparing(nameAscending);
                };
                sorted.sort(comparator);
                List<MinecraftInstance> savedOrder = List.copyOf(sorted);
                this.runTask(
                        null,
                        Localization.text("main.status.sorting_instances"),
                        () -> {
                            this.launcherService.reorderInstances(savedOrder);
                            return savedOrder;
                        },
                        reordered -> {
                            this.instances.clear();
                            this.instances.addAll(reordered);
                            this.rebuildInstanceList(selectedId);
                            this.setStatus(Localization.text("main.status.instances_reordered"));
                        }
                );
            });
            this.instanceSortMenu.add(sortItem);
        }
        this.sortInstancesButton.addActionListener(event -> this.instanceSortMenu.show(
                this.sortInstancesButton,
                0,
                this.sortInstancesButton.getHeight()
        ));

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
        new EditHoverListener(
                this.instanceNameEditorPanel,
                this.instanceNamePencilLabel,
                () -> {
                    if (this.renameInstanceItem.isEnabled()) this.renameInstanceItem.doClick();
                },
                this.instanceNameLabel
        );
        new EditHoverListener(
                this.instanceIconEditorPanel,
                this.instanceIconPencilLabel,
                () -> {
                    if (this.instanceIconMenu.isEnabled()) {
                        this.instanceIconMenu.getPopupMenu().show(
                                this.instanceIconEditorPanel,
                                0,
                                this.instanceIconEditorPanel.getHeight()
                        );
                    }
                },
                this.instanceIconLabel
        );
        this.emptyInstanceActionsMenu.add(this.addInstanceMenuItem);
        this.addInstanceMenuItem.addActionListener(event -> this.addInstanceButton.doClick());
        this.instanceList.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(@NotNull MouseEvent event) {
                int index = LauncherFrame.this.instanceList.locationToIndex(event.getPoint());
                Rectangle bounds = index < 0 ? null : LauncherFrame.this.instanceList.getCellBounds(index, index);
                if (SwingUtilities.isRightMouseButton(event) && (bounds == null || !bounds.contains(event.getPoint()))) {
                    LauncherFrame.this.emptyInstanceActionsMenu.show(LauncherFrame.this.instanceList, event.getX(), event.getY());
                    return;
                }
                if (bounds == null || !bounds.contains(event.getPoint())) return;
                boolean gearClick = SwingUtilities.isLeftMouseButton(event) && event.getX() >= bounds.x + bounds.width - INSTANCE_GEAR_WIDTH;
                boolean actionClick = SwingUtilities.isRightMouseButton(event) || gearClick;
                if (!actionClick || LauncherFrame.this.busy) return;

                LauncherFrame.this.instanceList.setSelectedIndex(index);
                LauncherFrame.this.updateControlState();
                LauncherFrame.this.instanceActionsMenu.show(LauncherFrame.this.instanceList, event.getX(), event.getY());
            }

            @Override
            public void mouseClicked(@NotNull MouseEvent event) {
                if (LauncherFrame.this.busy || event.getClickCount() != 2 || !SwingUtilities.isLeftMouseButton(event)) return;
                int index = LauncherFrame.this.instanceList.locationToIndex(event.getPoint());
                Rectangle bounds = index < 0 ? null : LauncherFrame.this.instanceList.getCellBounds(index, index);
                if (bounds == null || !bounds.contains(event.getPoint())) return;
                LauncherFrame.this.instanceList.setSelectedIndex(index);
                if (LauncherFrame.this.launchButton.isEnabled()) LauncherFrame.this.launchButton.doClick();
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
                        this.instanceSearchField.setText("");
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
                        this.instanceSearchField.setText("");
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

                    this.instances.clear();
                    this.instances.addAll(instances);
                    this.rebuildInstanceList(selectedId);
                    setStatus(Localization.text(instances.isEmpty() ? "main.status.add_instance" : "main.status.ready"));
                }
        );
    }

    private void updateInstanceSearch() {
        String selectedId = this.selectedInstanceId();
        this.rebuildInstanceList(selectedId);
    }

    private void rebuildInstanceList(@Nullable String selectedId) {
        this.instanceModel.clear();
        String query = this.instanceSearchField.getText().trim().toLowerCase(java.util.Locale.ROOT);
        for (MinecraftInstance instance : this.instances) {
            if (query.isEmpty()
                    || instance.name().toLowerCase(java.util.Locale.ROOT).contains(query)
                    || instance.id().toLowerCase(java.util.Locale.ROOT).contains(query)) {
                this.instanceModel.addElement(instance);
            }
        }

        MinecraftInstance selection = null;
        if (selectedId != null) {
            for (int index = 0; index < this.instanceModel.size(); index++) {
                MinecraftInstance candidate = this.instanceModel.get(index);
                if (selectedId.equals(candidate.id())) {
                    selection = candidate;
                    break;
                }
            }
        }
        if (selection == null && !this.instanceModel.isEmpty()) selection = this.instanceModel.getElementAt(0);
        this.emptyInstanceMessage.setText(Localization.text(
                this.instances.isEmpty() ? "main.instances.empty" : "main.instances.no_matches"
        ));
        this.instanceList.setSelectedValue(selection, true);
        if (selection == null) this.showSelectedInstance();
        this.updateControlState();
    }

    private void showSelectedInstance() {
        MinecraftInstance instance = this.selectedInstance();
        this.modTableModel.setMods(List.of());
        if (instance == null) {
            this.instanceContentLayout.show(this.instanceContentCards, EMPTY_INSTANCE_CARD);
            this.updateControlState();
            return;
        }

        this.instanceContentLayout.show(this.instanceContentCards, INSTANCE_DETAILS_CARD);
        StringBuilder activityLog = this.instanceActivityLogs.get(instance.id());
        this.activityArea.setText(activityLog == null ? "" : activityLog.toString());
        this.activityArea.setCaretPosition(this.activityArea.getDocument().getLength());
        this.showActivityTab();
        this.instanceNameLabel.setText(instance.name());
        this.instanceIconLabel.setIcon(InstanceIconProvider.INSTANCE.iconFor(instance));
        this.instanceTypeLabel.setText(Localization.text("main.instance.type", LauncherFrame.displayName(instance.type())));
        this.loaderVersionLabel.setText(instance.loaderVersion() == null
                ? "Minecraft " + SquirrelLauncher.VERSION
                : Localization.text("main.instance.loader", instance.loaderVersion()));
        this.instancePlaytimeLabel.setText(LauncherFrame.playtimeText(instance.totalTimePlayedSeconds()));

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
        long launchTimeMillis = System.currentTimeMillis();
        long launchTimeNanos = System.nanoTime();
        this.runningProcess = process;
        this.runningActivityInstanceId = instance.id();
        this.runningActivityPrefix = "[" + instance.name() + "] ";
        this.setStatus(Localization.text("main.status.minecraft_running"));
        this.updateControlState();

        new SwingWorker<GameExit, Void>() {
            @Override
            protected GameExit doInBackground() throws Exception {
                int exitCode = process.waitFor();
                long elapsedNanos = System.nanoTime() - launchTimeNanos;
                long elapsedSeconds = elapsedNanos <= 0 ? 0 : elapsedNanos / 1_000_000_000L;
                try {
                    MinecraftInstance updated = LauncherFrame.this.launcherService.recordPlaytime(
                            instance,
                            elapsedSeconds,
                            launchTimeMillis
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
                    String selectedId = LauncherFrame.this.selectedInstanceId();
                    for (int index = 0; index < LauncherFrame.this.instances.size(); index++) {
                        if (instance.id().equals(LauncherFrame.this.instances.get(index).id())) {
                            LauncherFrame.this.instances.set(index, result.instance());
                            break;
                        }
                    }
                    LauncherFrame.this.rebuildInstanceList(selectedId);
                    if (result.playtimeError() != null) {
                        LauncherFrame.this.showError(
                                Localization.text("main.error.save_playtime"),
                                result.playtimeError(),
                                instance.id()
                        );
                    }
                }
                catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    LauncherFrame.this.showError(
                            Localization.text("main.error.waiting_interrupted"),
                            exception,
                            instance.id()
                    );
                }
                catch (ExecutionException exception) {
                    LauncherFrame.this.showError(
                            Localization.text("main.error.monitor_minecraft"),
                            exception.getCause(),
                            instance.id()
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
        boolean canRenameInstance = available && instance != null && this.runningProcess == null;
        boolean canManageInstanceIcon = available && instance != null;

        this.accountSelector.setEnabled(available && this.accountSelector.getItemCount() > 0);
        this.manageAccountsButton.setEnabled(available);
        this.settingsButton.setEnabled(available);
        this.addInstanceButton.setEnabled(available);
        this.addInstanceMenuItem.setEnabled(available);
        this.searchInstancesButton.setEnabled(available && !this.instances.isEmpty());
        this.sortInstancesButton.setEnabled(available && this.instances.size() > 1);
        this.instanceSearchField.setEnabled(available);
        this.closeInstanceSearchButton.setEnabled(available);
        this.renameInstanceItem.setEnabled(canRenameInstance);
        this.duplicateInstanceItem.setEnabled(available && instance != null && this.runningProcess == null);
        this.convertInstanceItem.setEnabled(available && instance != null && this.runningProcess == null);
        this.instanceIconMenu.setEnabled(canManageInstanceIcon);
        this.chooseInstanceIconItem.setEnabled(canManageInstanceIcon);
        this.instanceNameEditorPanel.setEnabled(canRenameInstance);
        this.instanceIconEditorPanel.setEnabled(canManageInstanceIcon);
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

    @NotNull
    private static String playtimeText(long totalTimePlayedSeconds) {
        long safeTotalTimePlayedSeconds = Math.max(0, totalTimePlayedSeconds);
        if (safeTotalTimePlayedSeconds < 3_600) {
            long minutes = safeTotalTimePlayedSeconds / 60;
            return Localization.text(
                    minutes == 1 ? "main.instance.playtime.minute" : "main.instance.playtime.minutes",
                    minutes
            );
        }

        long tenthsOfAnHour = safeTotalTimePlayedSeconds / 360;
        if (safeTotalTimePlayedSeconds % 360 >= 180) tenthsOfAnHour++;
        return Localization.text(
                "main.instance.playtime",
                (tenthsOfAnHour / 10) + "." + (tenthsOfAnHour % 10)
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

    private enum InstanceSortMode {
        NAME_ASCENDING("main.sort.name_ascending"),
        NAME_DESCENDING("main.sort.name_descending"),
        PLAYTIME("main.sort.playtime"),
        OLDEST("main.sort.oldest"),
        NEWEST("main.sort.newest");

        @NotNull
        private final String localizationKey;

        InstanceSortMode(@NotNull String localizationKey) {
            this.localizationKey = localizationKey;
        }
    }

    private enum SidebarIcon implements Icon {
        SEARCH,
        SORT,
        CLOSE,
        PENCIL;

        private static final int ICON_SIZE = 16;

        @Override
        public void paintIcon(
                @Nullable Component component,
                @NotNull Graphics graphics,
                int x,
                int y
        ) {
            if (!(graphics instanceof Graphics2D)) return;
            Graphics2D drawing = (Graphics2D) graphics.create();
            try {
                Color color = component == null
                        ? Color.DARK_GRAY
                        : component.isEnabled() ? component.getForeground() : Color.GRAY;
                drawing.setColor(color);
                drawing.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                drawing.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                drawing.translate(x, y);
                switch (this) {
                    case SEARCH -> {
                        drawing.drawOval(1, 1, 10, 10);
                        drawing.drawLine(10, 10, 15, 15);
                    }
                    case SORT -> {
                        drawing.drawLine(1, 3, 9, 3);
                        drawing.drawLine(1, 7, 7, 7);
                        drawing.drawLine(1, 11, 5, 11);
                        drawing.drawLine(13, 1, 13, 14);
                        drawing.drawLine(10, 11, 13, 14);
                        drawing.drawLine(13, 14, 15, 11);
                    }
                    case CLOSE -> {
                        drawing.drawLine(3, 3, 13, 13);
                        drawing.drawLine(13, 3, 3, 13);
                    }
                    case PENCIL -> {
                        drawing.drawLine(3, 12, 11, 4);
                        drawing.drawLine(5, 14, 13, 6);
                        drawing.drawLine(3, 12, 2, 15);
                        drawing.drawLine(2, 15, 5, 14);
                        drawing.drawLine(11, 4, 13, 6);
                    }
                }
            }
            finally {
                drawing.dispose();
            }
        }

        @Override
        public int getIconWidth() {
            return ICON_SIZE;
        }

        @Override
        public int getIconHeight() {
            return ICON_SIZE;
        }
    }

    private static final class EditHoverListener extends MouseAdapter {
        @NotNull
        private final JPanel targetPanel;
        @NotNull
        private final JLabel pencilLabel;
        @NotNull
        private final Runnable editAction;

        private EditHoverListener(
                @NotNull JPanel targetPanel,
                @NotNull JLabel pencilLabel,
                @NotNull Runnable editAction,
                @NotNull Component... interactiveComponents
        ) {
            this.targetPanel = targetPanel;
            this.pencilLabel = pencilLabel;
            this.editAction = editAction;
            this.targetPanel.addMouseListener(this);
            this.pencilLabel.addMouseListener(this);
            for (Component interactiveComponent : interactiveComponents) interactiveComponent.addMouseListener(this);
        }

        @Override
        public void mouseEntered(@NotNull MouseEvent event) {
            this.setHovered(true);
        }

        @Override
        public void mouseExited(@NotNull MouseEvent event) {
            Point point = SwingUtilities.convertPoint(
                    (Component) event.getSource(),
                    event.getPoint(),
                    this.targetPanel
            );
            if (!this.targetPanel.contains(point)) this.setHovered(false);
        }

        @Override
        public void mouseClicked(@NotNull MouseEvent event) {
            if (this.targetPanel.isEnabled() && SwingUtilities.isLeftMouseButton(event)) this.editAction.run();
        }

        private void setHovered(boolean hovered) {
            boolean showEditState = hovered && this.targetPanel.isEnabled();
            this.targetPanel.setBorder(showEditState
                    ? BorderFactory.createLineBorder(this.targetPanel.getForeground())
                    : BorderFactory.createEmptyBorder(1, 1, 1, 1));
            this.pencilLabel.setVisible(showEditState);
        }
    }

    private static final class InstanceCellRenderer extends JPanel implements ListCellRenderer<MinecraftInstance> {
        @NotNull
        private final JLabel iconLabel = new JLabel();
        @NotNull
        private final JLabel textLabel = new JLabel();
        @NotNull
        private final JLabel gearLabel = new JLabel("⚙", JLabel.CENTER);

        private InstanceCellRenderer() {
            super(new BorderLayout(10, 0));
            this.setOpaque(true);
            this.iconLabel.setOpaque(false);
            this.textLabel.setOpaque(false);
            this.gearLabel.setOpaque(false);
            this.gearLabel.setFont(this.gearLabel.getFont().deriveFont(18f));
            this.gearLabel.setPreferredSize(new Dimension(INSTANCE_GEAR_WIDTH, 32));
            this.gearLabel.setToolTipText(Localization.text("main.tooltip.instance_options"));
            this.add(this.iconLabel, BorderLayout.WEST);
            this.add(this.textLabel, BorderLayout.CENTER);
            this.add(this.gearLabel, BorderLayout.EAST);
        }

        @Override
        @NotNull
        public Component getListCellRendererComponent(
                @NotNull JList<? extends MinecraftInstance> list,
                @NotNull MinecraftInstance instance,
                int index,
                boolean selected,
                boolean focused
        ) {
            String name = instance.name().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
            this.textLabel.setText(
                    "<html><b>" + name + "</b><br><small>"
                            + LauncherFrame.displayName(instance.type()) + " • "
                            + LauncherFrame.playtimeText(instance.totalTimePlayedSeconds())
                            + "</small></html>"
            );
            this.iconLabel.setIcon(InstanceIconProvider.INSTANCE.iconFor(instance));
            this.setBackground(selected ? list.getSelectionBackground() : list.getBackground());
            this.textLabel.setForeground(selected ? list.getSelectionForeground() : list.getForeground());
            this.gearLabel.setForeground(selected ? list.getSelectionForeground() : list.getForeground());
            this.setBorder(BorderFactory.createEmptyBorder(6, 7, 6, 0));
            return this;
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
