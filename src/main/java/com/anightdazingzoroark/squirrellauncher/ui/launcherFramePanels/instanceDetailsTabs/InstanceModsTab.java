package com.anightdazingzoroark.squirrellauncher.ui.launcherFramePanels.instanceDetailsTabs;

import com.anightdazingzoroark.squirrellauncher.minecraft.mod.ManagedMod;
import com.anightdazingzoroark.squirrellauncher.minecraft.mod.ModState;
import com.anightdazingzoroark.squirrellauncher.ui.LauncherActions;
import com.anightdazingzoroark.squirrellauncher.ui.Localization;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumn;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
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
    private final JButton installModButton = new JButton(Localization.text("main.button.install_mod"));
    @NotNull
    private final JButton removeModButton = new JButton(Localization.text("main.button.remove"));
    @NotNull
    private final JButton openModsFolderButton = new JButton(Localization.text("main.button.open_mods_folder"));

    public InstanceModsTab(@NotNull LauncherActions launcherActions) {
        super(new BorderLayout(0, 8));
        this.launcherActions = launcherActions;
        this.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));

        this.modTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
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
        this.modTable.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) this.launcherActions.modSelectionChanged();
        });
        this.add(new JScrollPane(this.modTable), BorderLayout.CENTER);

        JPanel actions = new JPanel(new BorderLayout());
        actions.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 8));
        JPanel modActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        modActions.add(this.installModButton);
        modActions.add(this.removeModButton);
        actions.add(modActions, BorderLayout.WEST);
        actions.add(this.openModsFolderButton, BorderLayout.EAST);
        this.add(actions, BorderLayout.SOUTH);

        this.installModButton.addActionListener(event -> this.launcherActions.installModRequested());
        this.removeModButton.addActionListener(event -> this.launcherActions.removeModRequested());
        this.openModsFolderButton.addActionListener(event -> this.launcherActions.openModsFolderRequested());
    }

    public void setMods(@NotNull List<ManagedMod> mods) {
        this.modTableModel.setMods(mods);
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

    @Nullable
    public ManagedMod selectedMod() {
        int row = this.modTable.getSelectedRow();
        return row < 0 ? null : this.modTableModel.modAt(row);
    }

    public void updateControlState(boolean available, boolean supportsMods) {
        ManagedMod mod = this.selectedMod();
        this.modTable.setEnabled(available && supportsMods);
        this.modTableModel.editable = available && supportsMods;
        this.installModButton.setEnabled(available && supportsMods);
        this.removeModButton.setEnabled(available && supportsMods && mod != null);
        this.openModsFolderButton.setEnabled(available && supportsMods);
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
            return 5;
        }

        @Override
        @NotNull
        public String getColumnName(int column) {
            return Localization.text(switch (column) {
                case 0 -> "table.mod.active";
                case 1 -> "table.mod.icon";
                case 2 -> "table.mod.name";
                case 3 -> "table.mod.version";
                default -> "table.mod.last_modified";
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
                default -> MODIFIED_TIME_FORMAT.format(
                        Instant.ofEpochMilli(mod.lastModifiedMillis()).atZone(ZoneId.systemDefault())
                );
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
}
