package com.anightdazingzoroark.squirrellauncher.ui.dialogs.modDownloadDialog;

import com.anightdazingzoroark.squirrellauncher.minecraft.mod.ModDownloadProject;
import com.anightdazingzoroark.squirrellauncher.ui.Localization;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import javax.swing.table.TableCellRenderer;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GridLayout;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ModDownloadProjectTable extends JTable {
    @NotNull
    private final DefaultTableModel projectModel;
    @NotNull
    private final List<ModDownloadProject> projects = new ArrayList<>();
    @NotNull
    private final Set<String> projectKeys = new HashSet<>();
    @NotNull
    private final Set<String> installedProjectKeys = new HashSet<>();

    public ModDownloadProjectTable() {
        this.projectModel = new DefaultTableModel(new Object[]{
                "",
                Localization.text("mod.download.column.icon"),
                Localization.text("mod.download.column.name"),
                Localization.text("mod.download.column.author"),
                Localization.text("mod.download.column.downloads")
        }, 0) {
            @Override
            @NotNull
            public Class<?> getColumnClass(int column) {
                return column == 1 ? Icon.class : String.class;
            }

            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        this.setModel(this.projectModel);
        this.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        this.setFillsViewportHeight(true);
        this.setRowHeight(52);
        this.setAutoCreateRowSorter(true);
        if (this.getRowSorter() instanceof TableRowSorter<?> rowSorter) {
            rowSorter.setSortable(0, false);
            rowSorter.setSortable(1, false);
        }
        this.getTableHeader().setResizingAllowed(false);
        this.getTableHeader().setReorderingAllowed(false);
        DefaultTableCellRenderer favoriteRenderer = new DefaultTableCellRenderer();
        favoriteRenderer.setHorizontalAlignment(SwingConstants.CENTER);
        favoriteRenderer.setFont(favoriteRenderer.getFont().deriveFont(18f));
        this.getColumnModel().getColumn(0).setCellRenderer(favoriteRenderer);
        this.getColumnModel().getColumn(0).setMinWidth(34);
        this.getColumnModel().getColumn(0).setMaxWidth(34);
        this.getColumnModel().getColumn(0).setPreferredWidth(34);
        this.getColumnModel().getColumn(1).setMinWidth(56);
        this.getColumnModel().getColumn(1).setMaxWidth(56);
        this.getColumnModel().getColumn(1).setPreferredWidth(56);
        this.getColumnModel().getColumn(2).setCellRenderer(new ProjectNameRenderer());
        this.getColumnModel().getColumn(2).setPreferredWidth(330);
        this.getColumnModel().getColumn(3).setPreferredWidth(170);
        this.getColumnModel().getColumn(4).setPreferredWidth(110);
    }

    public void setProjects(@NotNull List<ModDownloadProject> projects) {
        this.projects.clear();
        this.projectKeys.clear();
        this.projectModel.setRowCount(0);
        this.appendProjects(projects);
    }

    public void appendProjects(@NotNull List<ModDownloadProject> projects) {
        NumberFormat numberFormat = NumberFormat.getIntegerInstance();
        for (ModDownloadProject project : projects) {
            if (!this.projectKeys.add(ModDownloadProjectTable.projectKey(project))) continue;
            this.projects.add(project);
            this.projectModel.addRow(new Object[]{
                    project.favorite() ? "★" : "☆",
                    null,
                    project.name(),
                    project.author(),
                    numberFormat.format(project.downloads())
            });
        }
    }

    @Nullable
    public ModDownloadProject selectedProject() {
        int modelRow = this.selectedModelRow();
        return modelRow < 0 || modelRow >= this.projects.size() ? null : this.projects.get(modelRow);
    }

    public int selectedModelRow() {
        int selectedRow = this.getSelectedRow();
        return selectedRow < 0 ? -1 : this.convertRowIndexToModel(selectedRow);
    }

    @Nullable
    public Icon selectedProjectIcon() {
        int modelRow = this.selectedModelRow();
        if (modelRow < 0 || modelRow >= this.projectModel.getRowCount()) return null;
        Object icon = this.projectModel.getValueAt(modelRow, 1);
        return icon instanceof Icon projectIcon ? projectIcon : null;
    }

    public void setProjectIcon(@NotNull ModDownloadProject project, @NotNull Icon icon) {
        String iconProjectKey = ModDownloadProjectTable.projectKey(project);
        for (int row = 0; row < this.projects.size(); row++) {
            if (!iconProjectKey.equals(ModDownloadProjectTable.projectKey(this.projects.get(row)))) continue;
            this.projectModel.setValueAt(icon, row, 1);
            return;
        }
    }

    public void setFavorite(@NotNull ModDownloadProject project, boolean favorite, boolean prioritize) {
        String selectedProjectKey = ModDownloadProjectTable.projectKey(project);
        int sourceRow = -1;
        for (int row = 0; row < this.projects.size(); row++) {
            if (selectedProjectKey.equals(ModDownloadProjectTable.projectKey(this.projects.get(row)))) {
                sourceRow = row;
                break;
            }
        }
        if (sourceRow < 0) return;
        ModDownloadProject current = this.projects.get(sourceRow);
        ModDownloadProject updated = new ModDownloadProject(
                current.platform(),
                current.projectId(),
                current.name(),
                current.author(),
                current.description(),
                current.iconUrl(),
                current.pageUrl(),
                current.downloads(),
                favorite
        );
        this.projects.set(sourceRow, updated);
        this.projectModel.setValueAt(favorite ? "★" : "☆", sourceRow, 0);
        if (!prioritize) return;
        int targetRow = 0;
        for (int row = 0; row < this.projects.size(); row++) {
            if (row != sourceRow && this.projects.get(row).favorite()) targetRow++;
        }
        if (targetRow == sourceRow) return;
        this.projects.remove(sourceRow);
        this.projects.add(targetRow, updated);
        this.projectModel.moveRow(sourceRow, sourceRow, targetRow);
        int viewRow = this.convertRowIndexToView(targetRow);
        if (viewRow >= 0) this.setRowSelectionInterval(viewRow, viewRow);
    }

    @NotNull
    public ModDownloadProject projectAtModelRow(int modelRow) {
        return this.projects.get(modelRow);
    }

    public int projectCount() {
        return this.projects.size();
    }

    public void setInstalledProjectKeys(@NotNull Set<String> installedProjectKeys) {
        this.installedProjectKeys.clear();
        this.installedProjectKeys.addAll(installedProjectKeys);
        this.repaint();
    }

    @Override
    @NotNull
    public Component prepareRenderer(@NotNull TableCellRenderer renderer, int row, int column) {
        Component component = super.prepareRenderer(renderer, row, column);
        int modelRow = this.convertRowIndexToModel(row);
        boolean installed = modelRow >= 0
                && modelRow < this.projects.size()
                && this.installedProjectKeys.contains(ModDownloadProjectTable.projectKey(this.projects.get(modelRow)));
        component.setEnabled(!installed || this.isRowSelected(row));
        if (component instanceof ProjectNameRenderer projectNameRenderer) {
            projectNameRenderer.nameLabel.setEnabled(component.isEnabled());
            projectNameRenderer.descriptionLabel.setEnabled(component.isEnabled());
        }
        return component;
    }

    @NotNull
    private static String projectKey(@NotNull ModDownloadProject project) {
        return project.platform().name() + ':' + project.projectId();
    }

    private static final class ProjectNameRenderer extends JPanel implements TableCellRenderer {
        @NotNull
        private final JLabel nameLabel = new JLabel();
        @NotNull
        private final JLabel descriptionLabel = new JLabel();

        private ProjectNameRenderer() {
            super(new GridLayout(2, 1));
            this.setBorder(BorderFactory.createEmptyBorder(3, 4, 3, 4));
            this.nameLabel.setFont(this.nameLabel.getFont().deriveFont(Font.BOLD));
            this.nameLabel.putClientProperty("html.disable", Boolean.TRUE);
            this.descriptionLabel.putClientProperty("html.disable", Boolean.TRUE);
            this.add(this.nameLabel);
            this.add(this.descriptionLabel);
        }

        @Override
        @NotNull
        public Component getTableCellRendererComponent(
                @NotNull JTable table, @Nullable Object value,
                boolean selected, boolean focused, int row, int column
        ) {
            int modelRow = table.convertRowIndexToModel(row);
            ModDownloadProject project = ((ModDownloadProjectTable) table).projects.get(modelRow);
            this.nameLabel.setText(project.name());
            String description = project.description()
                    .replace('\n', ' ')
                    .replace('\r', ' ')
                    .replace('\t', ' ')
                    .trim();
            FontMetrics metrics = this.descriptionLabel.getFontMetrics(this.descriptionLabel.getFont());
            int availableWidth = Math.max(0, table.getColumnModel().getColumn(column).getWidth() - 10);
            if (metrics.stringWidth(description) > availableWidth) {
                String suffix = "...";
                int suffixWidth = metrics.stringWidth(suffix);
                int low = 0;
                int high = description.length();
                while (low < high) {
                    int middle = (low + high + 1) / 2;
                    if (metrics.stringWidth(description.substring(0, middle)) + suffixWidth <= availableWidth) {
                        low = middle;
                    }
                    else {
                        high = middle - 1;
                    }
                }
                description = description.substring(0, low).stripTrailing() + suffix;
            }
            this.descriptionLabel.setText(description.isEmpty() ? " " : description);
            this.setBackground(selected ? table.getSelectionBackground() : table.getBackground());
            this.nameLabel.setForeground(selected ? table.getSelectionForeground() : table.getForeground());
            this.descriptionLabel.setForeground(selected ? table.getSelectionForeground() : table.getForeground());
            return this;
        }
    }
}
