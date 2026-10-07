package com.anightdazingzoroark.squirrellauncher.ui.dialogs;

import com.anightdazingzoroark.squirrellauncher.minecraft.runtime.JavaPackage;
import com.anightdazingzoroark.squirrellauncher.minecraft.runtime.JavaRuntime;
import com.anightdazingzoroark.squirrellauncher.minecraft.runtime.JavaRuntimeDownloadManager;
import com.anightdazingzoroark.squirrellauncher.minecraft.runtime.JavaVersion;
import com.anightdazingzoroark.squirrellauncher.ui.Localization;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

public final class JavaDownloadDialog extends AbstractDialog<Void> {
    @NotNull
    private final JavaRuntimeDownloadManager downloadManager;
    @NotNull
    private final Runnable installedListener;
    @NotNull
    private final JComboBox<JavaVersion> versionSelector;
    @NotNull
    private final DefaultTableModel packageModel;
    @NotNull
    private final JTable packageTable;
    @NotNull
    private final JButton refreshButton;
    @NotNull
    private final JButton downloadButton;
    @NotNull
    private final JButton closeButton;
    @NotNull
    private final JLabel statusLabel;
    @NotNull
    private final JProgressBar progressBar;
    @NotNull
    private List<JavaPackage> packages;
    @Nullable
    private SwingWorker<List<JavaPackage>, Void> packageWorker;
    @Nullable
    private SwingWorker<JavaRuntime, Void> installWorker;
    private int packageGeneration;

    public JavaDownloadDialog(@NotNull Window owner, @NotNull Runnable installedListener) {
        super(owner, Localization.text("settings.java.download.title"));
        this.downloadManager = new JavaRuntimeDownloadManager();
        this.installedListener = installedListener;
        this.versionSelector = new JComboBox<>(JavaVersion.values());
        this.packageModel = new DefaultTableModel(new Object[]{
                Localization.text("settings.java.download.column.vendor"),
                Localization.text("settings.java.download.column.release"),
                Localization.text("settings.java.download.column.size")
        }, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        this.packageTable = new JTable(this.packageModel);
        this.refreshButton = new JButton(Localization.text("settings.java.button.refresh"));
        this.downloadButton = new JButton(Localization.text("settings.java.download.button.install"));
        this.closeButton = new JButton(Localization.text("settings.java.download.button.close"));
        this.statusLabel = new JLabel(" ");
        this.progressBar = new JProgressBar(0, 100);
        this.packages = List.of();

        this.setLayout(new BorderLayout(0, 12));
        JPanel content = new JPanel(new GridBagLayout());
        content.setBorder(javax.swing.BorderFactory.createEmptyBorder(16, 16, 0, 16));
        JLabel headingLabel = new JLabel(Localization.text("settings.java.download.heading"));
        headingLabel.setFont(headingLabel.getFont().deriveFont(Font.BOLD, 20f));
        GridBagConstraints heading = new GridBagConstraints();
        heading.gridx = 0;
        heading.gridy = 0;
        heading.gridwidth = 2;
        heading.weightx = 1;
        heading.anchor = GridBagConstraints.LINE_START;
        heading.insets = new Insets(0, 0, 12, 0);
        content.add(headingLabel, heading);

        this.versionSelector.setRenderer(new DefaultListCellRenderer() {
            @Override
            @NotNull
            public Component getListCellRendererComponent(
                    @NotNull JList<?> list, @Nullable Object value, int index,
                    boolean selected, boolean focused
            ) {
                JLabel label = (JLabel) super.getListCellRendererComponent(
                        list, value, index, selected, focused
                );
                if (value instanceof JavaVersion javaVersion) {
                    label.setText(Localization.text("settings.java.download.java_value", javaVersion.major()));
                }
                return label;
            }
        });
        this.addRow(
                content,
                1,
                new JLabel(Localization.text("settings.java.download.java_version")),
                this.versionSelector
        );

        this.packageTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        this.packageTable.setFillsViewportHeight(true);
        this.packageTable.setRowHeight(25);
        this.packageTable.setAutoCreateRowSorter(true);
        this.packageTable.getTableHeader().setResizingAllowed(false);
        this.packageTable.getTableHeader().setReorderingAllowed(false);
        this.packageTable.getColumnModel().getColumn(0).setPreferredWidth(220);
        this.packageTable.getColumnModel().getColumn(1).setPreferredWidth(150);
        this.packageTable.getColumnModel().getColumn(2).setPreferredWidth(90);
        JScrollPane packagesScroll = new JScrollPane(this.packageTable);
        packagesScroll.setPreferredSize(new Dimension(650, 220));
        GridBagConstraints packagesConstraints = new GridBagConstraints();
        packagesConstraints.gridx = 0;
        packagesConstraints.gridy = 2;
        packagesConstraints.gridwidth = 2;
        packagesConstraints.weightx = 1;
        packagesConstraints.weighty = 1;
        packagesConstraints.fill = GridBagConstraints.BOTH;
        content.add(packagesScroll, packagesConstraints);

        this.progressBar.setStringPainted(true);
        this.progressBar.setVisible(false);
        GridBagConstraints progress = new GridBagConstraints();
        progress.gridx = 0;
        progress.gridy = 3;
        progress.gridwidth = 2;
        progress.weightx = 1;
        progress.fill = GridBagConstraints.HORIZONTAL;
        progress.insets = new Insets(10, 0, 0, 0);
        content.add(this.progressBar, progress);

        GridBagConstraints status = new GridBagConstraints();
        status.gridx = 0;
        status.gridy = 4;
        status.gridwidth = 2;
        status.weightx = 1;
        status.fill = GridBagConstraints.HORIZONTAL;
        status.anchor = GridBagConstraints.LINE_START;
        status.insets = new Insets(8, 0, 0, 0);
        content.add(this.statusLabel, status);
        this.add(content, BorderLayout.CENTER);

        JPanel actions = new JPanel(new BorderLayout(10, 0));
        actions.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 12, 12, 12));
        actions.add(this.refreshButton, BorderLayout.WEST);
        JPanel primaryActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        primaryActions.add(this.downloadButton);
        primaryActions.add(this.closeButton);
        actions.add(primaryActions, BorderLayout.EAST);
        this.add(actions, BorderLayout.SOUTH);

        this.versionSelector.addActionListener(event -> this.loadPackages());
        this.refreshButton.addActionListener(event -> this.loadPackages());
        this.packageTable.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) this.updateControlState();
        });
        this.downloadButton.addActionListener(event -> {
            int selectedRow = this.packageTable.getSelectedRow();
            if (selectedRow < 0 || this.installWorker != null) return;
            int modelRow = this.packageTable.convertRowIndexToModel(selectedRow);
            if (modelRow < 0 || modelRow >= this.packages.size()) return;
            JavaPackage javaPackage = this.packages.get(modelRow);
            this.progressBar.setValue(0);
            this.progressBar.setIndeterminate(false);
            this.progressBar.setVisible(true);
            this.statusLabel.setText(Localization.text(
                    "settings.java.download.status.downloading",
                    javaPackage.vendor(),
                    javaPackage.release()
            ));
            this.installWorker = new SwingWorker<>() {
                @Override
                @NotNull
                protected JavaRuntime doInBackground() throws Exception {
                    return JavaDownloadDialog.this.downloadManager.install(javaPackage, this::setProgress);
                }

                @Override
                protected void done() {
                    JavaDownloadDialog.this.installWorker = null;
                    try {
                        if (this.isCancelled()) {
                            JavaDownloadDialog.this.statusLabel.setText(
                                    Localization.text("settings.java.download.status.cancelled")
                            );
                        }
                        else {
                            JavaRuntime runtime = this.get();
                            JavaDownloadDialog.this.progressBar.setValue(100);
                            JavaDownloadDialog.this.statusLabel.setText(Localization.text(
                                    "settings.java.download.status.installed",
                                    runtime.version().major(),
                                    runtime.detectedVersion()
                            ));
                            JavaDownloadDialog.this.installedListener.run();
                        }
                    }
                    catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    }
                    catch (ExecutionException exception) {
                        Throwable cause = exception.getCause();
                        String message = cause == null || cause.getMessage() == null
                                ? exception.getClass().getSimpleName()
                                : cause.getMessage();
                        JavaDownloadDialog.this.statusLabel.setText(
                                Localization.text("settings.java.download.status.failed")
                        );
                        JOptionPane.showMessageDialog(
                                JavaDownloadDialog.this,
                                message,
                                Localization.text("settings.java.download.error.install"),
                                JOptionPane.ERROR_MESSAGE
                        );
                    }
                    JavaDownloadDialog.this.updateControlState();
                }
            };
            this.installWorker.addPropertyChangeListener(change -> {
                if (change.getPropertyName().equals("progress")) {
                    this.progressBar.setValue((Integer) change.getNewValue());
                }
            });
            this.installWorker.execute();
            this.updateControlState();
        });
        this.closeButton.addActionListener(event -> this.closeDialog());

        this.setMinimumSize(new Dimension(720, 450));
        this.resizeToContent();
        this.setLocationRelativeTo(owner);
        this.loadPackages();
    }

    @Override
    protected void closeDialog() {
        if (this.installWorker != null) {
            this.installWorker.cancel(true);
            return;
        }
        if (this.packageWorker != null) this.packageWorker.cancel(true);
        this.dispose();
    }

    private void loadPackages() {
        JavaVersion selectedVersion = (JavaVersion) this.versionSelector.getSelectedItem();
        if (selectedVersion == null) return;
        int generation = ++this.packageGeneration;
        if (this.packageWorker != null) this.packageWorker.cancel(true);
        this.packages = List.of();
        this.packageModel.setRowCount(0);
        this.progressBar.setIndeterminate(true);
        this.progressBar.setVisible(true);
        this.statusLabel.setText(Localization.text("settings.java.download.status.loading"));
        this.packageWorker = new SwingWorker<>() {
            @Override
            @NotNull
            protected List<JavaPackage> doInBackground() throws Exception {
                return JavaDownloadDialog.this.downloadManager.availablePackages(selectedVersion);
            }

            @Override
            protected void done() {
                if (generation != JavaDownloadDialog.this.packageGeneration) return;
                JavaDownloadDialog.this.packageWorker = null;
                JavaDownloadDialog.this.progressBar.setIndeterminate(false);
                JavaDownloadDialog.this.progressBar.setVisible(false);
                try {
                    JavaDownloadDialog.this.packages = new ArrayList<>(this.get());
                    for (JavaPackage javaPackage : JavaDownloadDialog.this.packages) {
                        String size = javaPackage.size() > 0L
                                ? String.format("%.1f MiB", javaPackage.size() / (1024.0 * 1024.0))
                                : Localization.text("settings.java.download.size.unknown");
                        JavaDownloadDialog.this.packageModel.addRow(new Object[]{
                                javaPackage.vendor(),
                                javaPackage.release(),
                                size
                        });
                    }
                    if (!JavaDownloadDialog.this.packages.isEmpty()) {
                        JavaDownloadDialog.this.packageTable.setRowSelectionInterval(0, 0);
                    }
                    JavaDownloadDialog.this.statusLabel.setText(JavaDownloadDialog.this.packages.isEmpty()
                            ? Localization.text("settings.java.download.status.none")
                            : Localization.text(
                                    "settings.java.download.status.available",
                                    JavaDownloadDialog.this.packages.size()
                            ));
                }
                catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
                catch (CancellationException ignored) {}
                catch (ExecutionException exception) {
                    Throwable cause = exception.getCause();
                    String message = cause == null || cause.getMessage() == null
                            ? exception.getClass().getSimpleName()
                            : cause.getMessage();
                    JavaDownloadDialog.this.statusLabel.setText(
                            Localization.text("settings.java.download.status.load_failed")
                    );
                    JOptionPane.showMessageDialog(
                            JavaDownloadDialog.this,
                            message,
                            Localization.text("settings.java.download.error.load"),
                            JOptionPane.ERROR_MESSAGE
                    );
                }
                JavaDownloadDialog.this.updateControlState();
            }
        };
        this.packageWorker.execute();
        this.updateControlState();
    }

    private void updateControlState() {
        boolean loading = this.packageWorker != null;
        boolean installing = this.installWorker != null;
        boolean selected = this.packageTable.getSelectedRow() >= 0;
        this.versionSelector.setEnabled(!loading && !installing);
        this.packageTable.setEnabled(!loading && !installing);
        this.refreshButton.setEnabled(!loading && !installing);
        this.downloadButton.setEnabled(!loading && !installing && selected);
        this.closeButton.setText(Localization.text(installing
                ? "settings.java.download.button.cancel"
                : "settings.java.download.button.close"));
    }
}
