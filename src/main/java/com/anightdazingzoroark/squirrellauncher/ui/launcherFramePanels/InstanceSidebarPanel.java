package com.anightdazingzoroark.squirrellauncher.ui.launcherFramePanels;

import com.anightdazingzoroark.squirrellauncher.minecraft.instance.MinecraftInstance;
import com.anightdazingzoroark.squirrellauncher.ui.InstanceIconProvider;
import com.anightdazingzoroark.squirrellauncher.ui.LauncherActions;
import com.anightdazingzoroark.squirrellauncher.ui.LauncherFrame;
import com.anightdazingzoroark.squirrellauncher.ui.Localization;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.DropMode;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class InstanceSidebarPanel extends JPanel {
    private static final int INSTANCE_GEAR_WIDTH = 36;
    private static final int INSTANCE_SIDEBAR_WIDTH = 400;
    @NotNull
    private static final String INSTANCE_HEADER_CARD = "header";
    @NotNull
    private static final String INSTANCE_SEARCH_CARD = "search";

    @NotNull
    private final LauncherActions launcherActions;
    @NotNull
    private final List<MinecraftInstance> instances = new ArrayList<>();
    @NotNull
    private final DefaultListModel<MinecraftInstance> instanceModel = new DefaultListModel<>();
    @NotNull
    private final JList<MinecraftInstance> instanceList = new JList<>(this.instanceModel);
    @NotNull
    private final JButton addInstanceButton = new JButton(Localization.text("main.button.add_instance"));
    @NotNull
    private final JButton refreshInstancesButton = new JButton(Localization.text("main.button.refresh"));
    @NotNull
    private final JButton searchInstancesButton = new JButton(SidebarIcon.SEARCH);
    @NotNull
    private final JButton sortInstancesButton = new JButton(SidebarIcon.SORT);
    @NotNull
    private final JButton closeInstanceSearchButton = new JButton(SidebarIcon.CLOSE);
    @NotNull
    private final JTextField instanceSearchField = new JTextField();
    @NotNull
    private final CardLayout instanceHeaderLayout = new CardLayout();
    @NotNull
    private final JPanel instanceHeaderCards = new JPanel(this.instanceHeaderLayout);
    @NotNull
    private final JPopupMenu instanceSortMenu = new JPopupMenu();
    @NotNull
    private final JPopupMenu instanceActionsMenu = new JPopupMenu();
    @NotNull
    private final JPopupMenu emptyInstanceActionsMenu = new JPopupMenu();
    @NotNull
    private final JMenuItem addInstanceMenuItem = new JMenuItem(Localization.text("main.button.add_instance"));
    @NotNull
    private final JMenuItem renameInstanceItem = new JMenuItem(Localization.text("main.menu.rename"));
    @NotNull
    private final JMenuItem duplicateInstanceItem = new JMenuItem(Localization.text("main.menu.duplicate"));
    @NotNull
    private final JMenuItem convertInstanceItem = new JMenuItem(Localization.text("main.menu.convert"));
    @NotNull
    private final JMenu instanceIconMenu = new JMenu(Localization.text("main.menu.icon"));
    @NotNull
    private final JMenuItem chooseInstanceIconItem = new JMenuItem(Localization.text("main.menu.choose_icon"));
    @NotNull
    private final JMenuItem resetInstanceIconItem = new JMenuItem(Localization.text("main.menu.reset_icon"));
    @NotNull
    private final JMenuItem openInFilesItem = new JMenuItem(Localization.text("main.menu.open_in_files"));
    @NotNull
    private final JMenuItem exportInstanceItem = new JMenuItem(Localization.text("main.menu.export"));
    @NotNull
    private final JMenuItem deleteInstanceItem = new JMenuItem(Localization.text("main.menu.delete"));
    private boolean busy;

    public InstanceSidebarPanel(@NotNull LauncherActions launcherActions) {
        super(new BorderLayout(0, 8));
        this.launcherActions = launcherActions;
        this.setPreferredSize(new Dimension(INSTANCE_SIDEBAR_WIDTH, 0));
        this.setMinimumSize(new Dimension(INSTANCE_SIDEBAR_WIDTH, 0));
        this.setMaximumSize(new Dimension(INSTANCE_SIDEBAR_WIDTH, Integer.MAX_VALUE));
        this.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 1, this.getBackground().darker()),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));

        this.configureHeader();
        this.configureInstanceList();
        this.configureMenus();

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        actions.add(this.addInstanceButton);
        actions.add(this.refreshInstancesButton);
        this.add(actions, BorderLayout.SOUTH);
    }

    @Nullable
    public MinecraftInstance selectedInstance() {
        return this.instanceList.getSelectedValue();
    }

    @Nullable
    public String selectedInstanceId() {
        MinecraftInstance instance = this.selectedInstance();
        return instance == null ? null : instance.id();
    }

    public void setInstances(@NotNull List<MinecraftInstance> instances, @Nullable String selectedId) {
        this.instances.clear();
        this.instances.addAll(instances);
        this.rebuildInstanceList(selectedId);
    }

    public boolean hasInstances() {
        return !this.instances.isEmpty();
    }

    public void replaceInstance(@NotNull MinecraftInstance instance) {
        String selectedId = this.selectedInstanceId();
        for (int index = 0; index < this.instances.size(); index++) {
            if (instance.id().equals(this.instances.get(index).id())) {
                this.instances.set(index, instance);
                break;
            }
        }
        this.rebuildInstanceList(selectedId);
    }

    public void clearSearch() {
        this.instanceSearchField.setText("");
    }

    public void showIconMenu(@NotNull Component source) {
        if (!this.instanceIconMenu.isEnabled()) return;
        this.instanceIconMenu.getPopupMenu().show(source, 0, source.getHeight());
    }

    public void updateControlState(boolean available, boolean instanceRunning) {
        MinecraftInstance instance = this.selectedInstance();
        boolean canRenameInstance = available && instance != null && !instanceRunning;
        boolean canManageInstanceIcon = available && instance != null;
        this.busy = !available;
        this.addInstanceButton.setEnabled(available);
        this.addInstanceMenuItem.setEnabled(available);
        this.searchInstancesButton.setEnabled(available && !this.instances.isEmpty());
        this.sortInstancesButton.setEnabled(available && this.instances.size() > 1);
        this.instanceSearchField.setEnabled(available);
        this.closeInstanceSearchButton.setEnabled(available);
        this.renameInstanceItem.setEnabled(canRenameInstance);
        this.duplicateInstanceItem.setEnabled(available && instance != null && !instanceRunning);
        this.convertInstanceItem.setEnabled(available && instance != null && !instanceRunning);
        this.instanceIconMenu.setEnabled(canManageInstanceIcon);
        this.chooseInstanceIconItem.setEnabled(canManageInstanceIcon);
        this.resetInstanceIconItem.setEnabled(available && instance != null && instance.iconKey() != null);
        this.openInFilesItem.setEnabled(available && instance != null);
        this.exportInstanceItem.setEnabled(available && instance != null);
        this.deleteInstanceItem.setEnabled(available && instance != null && !instanceRunning);
        this.refreshInstancesButton.setEnabled(available);
        this.instanceList.setEnabled(available);
    }

    private void configureHeader() {
        this.searchInstancesButton.setToolTipText(Localization.text("main.button.search_instances"));
        this.sortInstancesButton.setToolTipText(Localization.text("main.button.sort_instances"));
        this.closeInstanceSearchButton.setToolTipText(Localization.text("main.button.close_search"));
        this.searchInstancesButton.getAccessibleContext().setAccessibleName(Localization.text("main.button.search_instances"));
        this.sortInstancesButton.getAccessibleContext().setAccessibleName(Localization.text("main.button.sort_instances"));
        this.closeInstanceSearchButton.getAccessibleContext().setAccessibleName(Localization.text("main.button.close_search"));
        this.instanceSearchField.setToolTipText(Localization.text("main.prompt.search_instances"));
        this.instanceSearchField.getAccessibleContext().setAccessibleName(Localization.text("main.dialog.search_instances"));

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
        this.add(this.instanceHeaderCards, BorderLayout.NORTH);

        this.searchInstancesButton.addActionListener(event -> {
            this.instanceHeaderLayout.show(this.instanceHeaderCards, INSTANCE_SEARCH_CARD);
            this.instanceSearchField.requestFocusInWindow();
            this.instanceSearchField.selectAll();
        });
        this.closeInstanceSearchButton.addActionListener(event -> {
            this.clearSearch();
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
                InstanceSidebarPanel.this.rebuildInstanceList(InstanceSidebarPanel.this.selectedInstanceId());
            }

            @Override
            public void removeUpdate(@NotNull DocumentEvent event) {
                InstanceSidebarPanel.this.rebuildInstanceList(InstanceSidebarPanel.this.selectedInstanceId());
            }

            @Override
            public void changedUpdate(@NotNull DocumentEvent event) {
                InstanceSidebarPanel.this.rebuildInstanceList(InstanceSidebarPanel.this.selectedInstanceId());
            }
        });
    }

    private void configureInstanceList() {
        this.instanceList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        this.instanceList.setCellRenderer(new InstanceCellRenderer());
        this.instanceList.setDragEnabled(true);
        this.instanceList.setDropMode(DropMode.INSERT);
        this.instanceList.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) this.launcherActions.selectionChanged();
        });
        this.instanceList.setTransferHandler(new TransferHandler() {
            @Nullable
            private String draggedInstanceId;

            @Override
            @Nullable
            protected Transferable createTransferable(@NotNull JComponent component) {
                MinecraftInstance instance = InstanceSidebarPanel.this.selectedInstance();
                this.draggedInstanceId = instance == null ? null : instance.id();
                return this.draggedInstanceId == null ? null : new StringSelection(this.draggedInstanceId);
            }

            @Override
            public int getSourceActions(@NotNull JComponent component) {
                return MOVE;
            }

            @Override
            public boolean canImport(@NotNull TransferSupport support) {
                return support.isDrop()
                        && !InstanceSidebarPanel.this.busy
                        && InstanceSidebarPanel.this.instanceSearchField.getText().isBlank()
                        && support.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.stringFlavor);
            }

            @Override
            public boolean importData(@NotNull TransferSupport support) {
                if (!this.canImport(support) || this.draggedInstanceId == null) return false;
                int sourceIndex = -1;
                for (int index = 0; index < InstanceSidebarPanel.this.instances.size(); index++) {
                    if (this.draggedInstanceId.equals(InstanceSidebarPanel.this.instances.get(index).id())) {
                        sourceIndex = index;
                        break;
                    }
                }
                if (sourceIndex < 0) return false;

                JList.DropLocation drop = (JList.DropLocation) support.getDropLocation();
                int destinationIndex = drop.getIndex();
                if (destinationIndex > sourceIndex) destinationIndex--;
                if (destinationIndex == sourceIndex) return false;

                List<MinecraftInstance> reordered = new ArrayList<>(InstanceSidebarPanel.this.instances);
                MinecraftInstance moved = reordered.remove(sourceIndex);
                destinationIndex = Math.clamp(destinationIndex, 0, reordered.size());
                reordered.add(destinationIndex, moved);
                InstanceSidebarPanel.this.launcherActions.reorderRequested(reordered, moved.id());
                return true;
            }
        });
        this.instanceList.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(@NotNull MouseEvent event) {
                int index = InstanceSidebarPanel.this.instanceList.locationToIndex(event.getPoint());
                Rectangle bounds = index < 0 ? null : InstanceSidebarPanel.this.instanceList.getCellBounds(index, index);
                if (SwingUtilities.isRightMouseButton(event) && (bounds == null || !bounds.contains(event.getPoint()))) {
                    InstanceSidebarPanel.this.emptyInstanceActionsMenu.show(
                            InstanceSidebarPanel.this.instanceList,
                            event.getX(),
                            event.getY()
                    );
                    return;
                }
                if (bounds == null || !bounds.contains(event.getPoint())) return;
                boolean gearClick = SwingUtilities.isLeftMouseButton(event)
                        && event.getX() >= bounds.x + bounds.width - INSTANCE_GEAR_WIDTH;
                if ((!SwingUtilities.isRightMouseButton(event) && !gearClick) || InstanceSidebarPanel.this.busy) return;

                InstanceSidebarPanel.this.instanceList.setSelectedIndex(index);
                InstanceSidebarPanel.this.instanceActionsMenu.show(
                        InstanceSidebarPanel.this.instanceList,
                        event.getX(),
                        event.getY()
                );
            }

            @Override
            public void mouseClicked(@NotNull MouseEvent event) {
                if (InstanceSidebarPanel.this.busy
                        || event.getClickCount() != 2
                        || !SwingUtilities.isLeftMouseButton(event)) return;
                int index = InstanceSidebarPanel.this.instanceList.locationToIndex(event.getPoint());
                Rectangle bounds = index < 0 ? null : InstanceSidebarPanel.this.instanceList.getCellBounds(index, index);
                if (bounds == null || !bounds.contains(event.getPoint())) return;
                InstanceSidebarPanel.this.instanceList.setSelectedIndex(index);
                InstanceSidebarPanel.this.launcherActions.launchRequested();
            }
        });
        this.add(new JScrollPane(this.instanceList), BorderLayout.CENTER);
    }

    private void configureMenus() {
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
                this.launcherActions.reorderRequested(List.copyOf(sorted), selectedId);
            });
            this.instanceSortMenu.add(sortItem);
        }
        this.sortInstancesButton.addActionListener(event -> this.instanceSortMenu.show(
                this.sortInstancesButton,
                0,
                this.sortInstancesButton.getHeight()
        ));

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
        this.emptyInstanceActionsMenu.add(this.addInstanceMenuItem);

        this.addInstanceButton.addActionListener(event -> this.launcherActions.addRequested());
        this.addInstanceMenuItem.addActionListener(event -> this.launcherActions.addRequested());
        this.refreshInstancesButton.addActionListener(event -> this.launcherActions.refreshRequested());
        this.renameInstanceItem.addActionListener(event -> this.launcherActions.renameRequested());
        this.duplicateInstanceItem.addActionListener(event -> this.launcherActions.duplicateRequested());
        this.convertInstanceItem.addActionListener(event -> this.launcherActions.convertRequested());
        this.chooseInstanceIconItem.addActionListener(event -> this.launcherActions.chooseIconRequested());
        this.resetInstanceIconItem.addActionListener(event -> this.launcherActions.resetIconRequested());
        this.openInFilesItem.addActionListener(event -> this.launcherActions.openFolderRequested());
        this.exportInstanceItem.addActionListener(event -> this.launcherActions.exportRequested());
        this.deleteInstanceItem.addActionListener(event -> this.launcherActions.deleteRequested());
    }

    private void rebuildInstanceList(@Nullable String selectedId) {
        this.instanceModel.clear();
        String query = this.instanceSearchField.getText().trim().toLowerCase(Locale.ROOT);
        for (MinecraftInstance instance : this.instances) {
            if (query.isEmpty()
                    || instance.name().toLowerCase(Locale.ROOT).contains(query)
                    || instance.id().toLowerCase(Locale.ROOT).contains(query)) {
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
        this.instanceList.setSelectedValue(selection, true);
        if (selection == null) this.launcherActions.selectionChanged();
        this.launcherActions.sidebarStateChanged();
    }

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
        CLOSE;

        private static final int ICON_SIZE = 16;

        @Override
        public void paintIcon(@Nullable Component component, @NotNull Graphics graphics, int x, int y) {
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
}
