package com.anightdazingzoroark.squirrellauncher.ui.launcherFramePanels.instanceDetailsTabs;

import com.anightdazingzoroark.squirrellauncher.minecraft.mod.ManagedMod;
import com.anightdazingzoroark.squirrellauncher.minecraft.mod.ModState;
import com.anightdazingzoroark.squirrellauncher.ui.LauncherActions;
import com.anightdazingzoroark.squirrellauncher.ui.LauncherFrame;
import com.anightdazingzoroark.squirrellauncher.ui.Localization;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;

public final class ModsTab extends JPanel {
    @NotNull
    private final ModTableModel modTableModel = new ModTableModel();
    @NotNull
    private final JTable modTable = new JTable(this.modTableModel);
    @NotNull
    private final JButton installModButton = new JButton(Localization.text("main.button.install_mod"));
    @NotNull
    private final JButton toggleModButton = new JButton(Localization.text("main.button.enable"));
    @NotNull
    private final JButton removeModButton = new JButton(Localization.text("main.button.remove"));

    public ModsTab(@NotNull LauncherActions launcherActions) {
        super(new BorderLayout(0, 8));
        this.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));

        this.modTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        this.modTable.setFillsViewportHeight(true);
        this.modTable.getColumnModel().getColumn(0).setPreferredWidth(420);
        this.modTable.getColumnModel().getColumn(1).setPreferredWidth(100);
        this.modTable.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) launcherActions.modSelectionChanged();
        });
        this.add(new JScrollPane(this.modTable), BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        actions.add(this.installModButton);
        actions.add(this.toggleModButton);
        actions.add(this.removeModButton);
        this.add(actions, BorderLayout.SOUTH);

        this.installModButton.addActionListener(event -> launcherActions.installModRequested());
        this.toggleModButton.addActionListener(event -> launcherActions.toggleModRequested());
        this.removeModButton.addActionListener(event -> launcherActions.removeModRequested());
    }

    public void setMods(@NotNull List<ManagedMod> mods) {
        this.modTableModel.setMods(mods);
    }

    @Nullable
    public ManagedMod selectedMod() {
        int row = this.modTable.getSelectedRow();
        return row < 0 ? null : this.modTableModel.modAt(row);
    }

    public void updateControlState(boolean available, boolean supportsMods) {
        ManagedMod mod = this.selectedMod();
        this.modTable.setEnabled(available && supportsMods);
        this.installModButton.setEnabled(available && supportsMods);
        this.toggleModButton.setEnabled(available && supportsMods && mod != null);
        this.removeModButton.setEnabled(available && supportsMods && mod != null);
        this.toggleModButton.setText(Localization.text(
                mod != null && mod.state() == ModState.ENABLED ? "main.button.disable" : "main.button.enable"
        ));
    }

    private static final class ModTableModel extends AbstractTableModel {
        @NotNull
        private final List<ManagedMod> mods = new ArrayList<>();

        private void setMods(@NotNull List<ManagedMod> mods) {
            this.mods.clear();
            this.mods.addAll(mods);
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
            return 2;
        }

        @Override
        @NotNull
        public String getColumnName(int column) {
            return Localization.text(column == 0 ? "table.mod.name" : "table.mod.state");
        }

        @Override
        @NotNull
        public Object getValueAt(int row, int column) {
            ManagedMod mod = this.mods.get(row);
            return column == 0 ? mod.fileName() : LauncherFrame.displayName(mod.state());
        }
    }
}
