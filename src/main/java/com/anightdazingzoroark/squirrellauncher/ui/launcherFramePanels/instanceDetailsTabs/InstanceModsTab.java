package com.anightdazingzoroark.squirrellauncher.ui.launcherFramePanels.instanceDetailsTabs;

import com.anightdazingzoroark.squirrellauncher.minecraft.mod.ManagedMod;
import com.anightdazingzoroark.squirrellauncher.minecraft.mod.ModState;
import com.anightdazingzoroark.squirrellauncher.ui.LauncherActions;
import com.anightdazingzoroark.squirrellauncher.ui.Localization;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class InstanceModsTab extends JPanel {
    @NotNull
    private static final DateTimeFormatter MODIFIED_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    @NotNull
    private final LauncherActions launcherActions;
    @NotNull
    private final ModTableModel modTableModel = new ModTableModel();
    @NotNull
    private final JTable modTable = new JTable(this.modTableModel);
    @NotNull
    private final SelectedModInfo selectedModInfo = new SelectedModInfo();
    @NotNull
    private final JButton downloadModsButton = new JButton(Localization.text("main.button.download_mods"));
    @NotNull
    private final JButton openModsFolderButton = new JButton(Localization.text("main.button.open_mods_folder"));
    @NotNull
    private final JButton openConfigsFolderButton = new JButton(Localization.text("main.button.open_configs_folder"));
    @NotNull
    private final JPopupMenu modActionsMenu = new JPopupMenu();
    @NotNull
    private final JMenuItem activateModItem = new JMenuItem(Localization.text("instance.mods.menu.activate_mod"));
    @NotNull
    private final JMenuItem viewModPageItem = new JMenuItem(Localization.text("instance.mods.menu.view_page"));
    @NotNull
    private final JMenuItem checkModUpdatesItem = new JMenuItem(Localization.text("instance.mods.menu.check_updates"));
    @NotNull
    private final JMenuItem deleteModItem = new JMenuItem(Localization.text("instance.mods.menu.remove"));

    public InstanceModsTab(@NotNull LauncherActions launcherActions) {
        super(new BorderLayout(0, 8));
        this.launcherActions = launcherActions;
        this.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        this.configureMenu();

        this.modTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        this.modTable.setAutoCreateRowSorter(true);
        this.modTable.setFillsViewportHeight(true);
        this.modTable.setRowHeight(52);
        this.modTable.getTableHeader().setResizingAllowed(false);
        this.modTable.getTableHeader().setReorderingAllowed(false);
        TableColumn activeColumn = this.modTable.getColumnModel().getColumn(0);
        Component activeHeader = this.modTable.getTableHeader().getDefaultRenderer().getTableCellRendererComponent(
                this.modTable,
                activeColumn.getHeaderValue(),
                false,
                false,
                -1,
                0
        );
        int activeColumnWidth = activeHeader.getPreferredSize().width + 8;
        activeColumn.setMinWidth(activeColumnWidth);
        activeColumn.setMaxWidth(activeColumnWidth);
        activeColumn.setPreferredWidth(activeColumnWidth);
        this.modTable.getColumnModel().getColumn(1).setPreferredWidth(56);
        this.modTable.getColumnModel().getColumn(2).setPreferredWidth(320);
        this.modTable.getColumnModel().getColumn(3).setPreferredWidth(100);
        this.modTable.getColumnModel().getColumn(4).setPreferredWidth(140);
        this.modTable.getColumnModel().getColumn(5).setPreferredWidth(100);
        this.modTable.getSelectionModel().addListSelectionListener(event -> {
            if (event.getValueIsAdjusting()) return;

            List<ManagedMod> selectedMods = this.selectedMods();
            this.selectedModInfo.update(selectedMods.size() == 1 ? selectedMods.getFirst() : null);
            this.launcherActions.modSelectionChanged();
        });
        this.modTable.addMouseListener(new MouseAdapter() {
            private void showPopup(MouseEvent e) {
                if (!e.isPopupTrigger()) return;

                int row = InstanceModsTab.this.modTable.rowAtPoint(e.getPoint());
                if (row < 0) return;

                if (!InstanceModsTab.this.modTable.isRowSelected(row)) {
                    InstanceModsTab.this.modTable.setRowSelectionInterval(row, row);
                }
                List<ManagedMod> selectedMods = InstanceModsTab.this.selectedMods();
                boolean multipleMods = selectedMods.size() > 1;
                ManagedMod mod = selectedMods.size() == 1 ? selectedMods.getFirst() : null;
                InstanceModsTab.this.viewModPageItem.setEnabled(mod != null
                        && mod.providerPageUrl() != null
                        && !mod.providerPageUrl().isBlank());
                InstanceModsTab.this.checkModUpdatesItem.setEnabled(selectedMods.stream().anyMatch(selectedMod ->
                        selectedMod.provider() != null
                                && selectedMod.providerProjectId() != null
                                && selectedMod.providerFileId() != null
                ));
                InstanceModsTab.this.modActionsMenu.removeAll();
                if (multipleMods) {
                    InstanceModsTab.this.modActionsMenu.add(InstanceModsTab.this.activateModItem);
                    InstanceModsTab.this.modActionsMenu.add(InstanceModsTab.this.checkModUpdatesItem);
                    InstanceModsTab.this.modActionsMenu.add(InstanceModsTab.this.deleteModItem);
                }
                else {
                    InstanceModsTab.this.modActionsMenu.add(InstanceModsTab.this.activateModItem);
                    InstanceModsTab.this.modActionsMenu.addSeparator();
                    InstanceModsTab.this.modActionsMenu.add(InstanceModsTab.this.viewModPageItem);
                    InstanceModsTab.this.modActionsMenu.add(InstanceModsTab.this.checkModUpdatesItem);
                    InstanceModsTab.this.modActionsMenu.addSeparator();
                    InstanceModsTab.this.modActionsMenu.add(InstanceModsTab.this.deleteModItem);
                }
                InstanceModsTab.this.modActionsMenu.show(InstanceModsTab.this.modTable, e.getX(), e.getY());
            }

            @Override
            public void mousePressed(MouseEvent e) {
                this.showPopup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                this.showPopup(e);
            }
        });
        this.add(new JScrollPane(this.modTable), BorderLayout.CENTER);

        JPanel actions = new JPanel(new BorderLayout());
        actions.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 8));
        JPanel leftActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        leftActions.add(this.downloadModsButton);
        actions.add(leftActions, BorderLayout.WEST);
        JPanel rightActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        rightActions.add(this.openModsFolderButton);
        rightActions.add(this.openConfigsFolderButton);
        actions.add(rightActions, BorderLayout.EAST);

        JPanel bottomContent = new JPanel(new BorderLayout(0, 8));
        bottomContent.add(this.selectedModInfo, BorderLayout.CENTER);
        bottomContent.add(actions, BorderLayout.SOUTH);
        this.add(bottomContent, BorderLayout.SOUTH);

        this.downloadModsButton.addActionListener(event -> this.launcherActions.downloadModsRequested());
        this.openModsFolderButton.addActionListener(event -> this.launcherActions.openModsFolderRequested());
        this.openConfigsFolderButton.addActionListener(event -> this.launcherActions.openConfigsFolderRequested());
    }

    private void configureMenu() {
        this.activateModItem.addActionListener(event -> this.launcherActions.setModEnabledRequested());
        this.viewModPageItem.addActionListener(event -> this.launcherActions.openModPageRequested());
        this.checkModUpdatesItem.addActionListener(event -> this.launcherActions.checkModUpdateRequested());

        this.deleteModItem.addActionListener(event -> this.launcherActions.removeModRequested());
    }

    public void setMods(@NotNull List<ManagedMod> mods) {
        this.modTableModel.setMods(mods);
    }

    @NotNull
    public List<ManagedMod> mods() {
        return List.copyOf(this.modTableModel.mods);
    }

    public void replaceMod(@NotNull ManagedMod previous, @NotNull ManagedMod updated) {
        for (int row = 0; row < this.modTableModel.mods.size(); row++) {
            if (!previous.fileName().equals(this.modTableModel.mods.get(row).fileName())) continue;
            this.modTableModel.mods.set(row, updated);
            this.modTableModel.icons.set(row, updated.icon() == null ? null : new ImageIcon(updated.icon()));
            this.modTableModel.fireTableRowsUpdated(row, row);
            return;
        }
    }

    public void setModTogglePending(@NotNull String fileName, boolean pending) {
        if (pending) this.modTableModel.pendingModFileNames.add(fileName);
        else this.modTableModel.pendingModFileNames.remove(fileName);
        for (int row = 0; row < this.modTableModel.mods.size(); row++) {
            if (!fileName.equals(this.modTableModel.mods.get(row).fileName())) continue;
            this.modTableModel.fireTableCellUpdated(row, 0);
            return;
        }
    }

    @NotNull
    public List<ManagedMod> selectedMods() {
        int[] selectedRows = this.modTable.getSelectedRows();
        List<ManagedMod> selectedMods = new ArrayList<>(selectedRows.length);
        for (int selectedRow : selectedRows) {
            selectedMods.add(this.modTableModel.modAt(this.modTable.convertRowIndexToModel(selectedRow)));
        }
        return List.copyOf(selectedMods);
    }

    public void updateControlState(boolean available, boolean supportsMods) {
        this.modTable.setEnabled(available && supportsMods);
        this.modTableModel.editable = available && supportsMods;
        this.downloadModsButton.setEnabled(available && supportsMods);
        this.openModsFolderButton.setEnabled(available && supportsMods);
        this.openConfigsFolderButton.setEnabled(available && supportsMods);
    }

    private final class ModTableModel extends AbstractTableModel {
        @NotNull
        private final List<ManagedMod> mods = new ArrayList<>();
        @NotNull
        private final List<@Nullable Icon> icons = new ArrayList<>();
        @NotNull
        private final Set<String> pendingModFileNames = new HashSet<>();
        private boolean editable;

        private void setMods(@NotNull List<ManagedMod> mods) {
            this.mods.clear();
            this.mods.addAll(mods);
            this.icons.clear();
            int iconColumnWidth = 56;
            for (ManagedMod mod : mods) {
                Icon icon = mod.icon() == null ? null : new ImageIcon(mod.icon());
                this.icons.add(icon);
                if (icon != null) iconColumnWidth = Math.max(iconColumnWidth, icon.getIconWidth() + 8);
            }
            InstanceModsTab.this.modTable.getColumnModel().getColumn(1).setPreferredWidth(iconColumnWidth);
            this.fireTableDataChanged();
        }

        @NotNull
        private ManagedMod modAt(int row) {
            return this.mods.get(row);
        }

        @Override
        public int getRowCount() {
            return this.mods.size();
        }

        @Override
        public int getColumnCount() {
            return 6;
        }

        @Override
        @NotNull
        public String getColumnName(int column) {
            return Localization.text(switch (column) {
                case 0 -> "table.mod.active";
                case 1 -> "table.mod.icon";
                case 2 -> "table.mod.name";
                case 3 -> "table.mod.version";
                case 4 -> "table.mod.last_modified";
                default -> "table.mod.provider";
            });
        }

        @Override
        @NotNull
        public Class<?> getColumnClass(int column) {
            return switch (column) {
                case 0 -> Boolean.class;
                case 1 -> Icon.class;
                default -> String.class;
            };
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return this.editable
                    && column == 0
                    && !this.pendingModFileNames.contains(this.mods.get(row).fileName());
        }

        @Override
        @Nullable
        public Object getValueAt(int row, int column) {
            ManagedMod mod = this.mods.get(row);
            return switch (column) {
                case 0 -> mod.state() == ModState.ENABLED;
                case 1 -> this.icons.get(row);
                case 2 -> mod.name();
                case 3 -> mod.version().isBlank() ? "—" : mod.version();
                case 4 -> MODIFIED_TIME_FORMAT.format(
                        Instant.ofEpochMilli(mod.lastModifiedMillis()).atZone(ZoneId.systemDefault())
                );
                default -> mod.provider() == null
                        ? Localization.text("mod.download.provider.unknown")
                        : Localization.text("mod.download.provider." + mod.provider().name().toLowerCase());
            };
        }

        @Override
        public void setValueAt(@Nullable Object value, int row, int column) {
            if (!this.editable || column != 0 || !(value instanceof Boolean enabled)) return;
            ManagedMod mod = this.mods.get(row);
            if (enabled == (mod.state() == ModState.ENABLED)) return;
            InstanceModsTab.this.launcherActions.setModEnabledRequested(mod, enabled);
        }
    }

    private static final class SelectedModInfo extends JPanel {
        @NotNull
        private final JLabel iconLabel = new JLabel();
        @NotNull
        private final JLabel nameLabel = new JLabel();
        @NotNull
        private final JTextArea descriptionArea = new JTextArea(2, 0);

        private SelectedModInfo() {
            super(new BorderLayout(8, 0));

            this.nameLabel.setText(" ");
            this.nameLabel.setFont(this.nameLabel.getFont().deriveFont(Font.BOLD));

            this.descriptionArea.setEditable(false);
            this.descriptionArea.setOpaque(false);
            this.descriptionArea.setLineWrap(true);
            this.descriptionArea.setWrapStyleWord(true);
            this.descriptionArea.setFocusable(false);
            this.descriptionArea.setBorder(null);

            this.add(this.iconLabel, BorderLayout.WEST);
            JPanel textSide = new JPanel(new BorderLayout(0, 4));
            textSide.add(this.nameLabel, BorderLayout.NORTH);
            textSide.add(this.descriptionArea, BorderLayout.CENTER);
            this.add(textSide, BorderLayout.CENTER);
        }

        private void update(@Nullable ManagedMod mod) {
            if (mod == null) {
                this.iconLabel.setIcon(null);
                this.nameLabel.setText(" ");
                this.descriptionArea.setText("");
            }
            else {
                this.iconLabel.setIcon(mod.icon() == null ? null : new ImageIcon(mod.icon()));
                this.nameLabel.setText(mod.name());
                this.descriptionArea.setText(mod.description());
                this.descriptionArea.setCaretPosition(0);
            }
        }
    }
}
