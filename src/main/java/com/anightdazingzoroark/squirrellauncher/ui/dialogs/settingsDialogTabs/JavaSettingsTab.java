package com.anightdazingzoroark.squirrellauncher.ui.dialogs.settingsDialogTabs;

import com.anightdazingzoroark.squirrellauncher.launcher.LauncherService;
import com.anightdazingzoroark.squirrellauncher.minecraft.MinecraftPaths;
import com.anightdazingzoroark.squirrellauncher.minecraft.runtime.JavaRuntime;
import com.anightdazingzoroark.squirrellauncher.minecraft.runtime.JavaRuntimeManager;
import com.anightdazingzoroark.squirrellauncher.minecraft.runtime.JavaVersion;
import com.anightdazingzoroark.squirrellauncher.ui.Localization;
import com.anightdazingzoroark.squirrellauncher.ui.dialogs.JavaDownloadDialog;
import com.anightdazingzoroark.squirrellauncher.ui.dialogs.LinuxJavaReminderDialog;
import org.jetbrains.annotations.NotNull;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

public final class JavaSettingsTab extends AbstractSettingsTab {
    @NotNull
    private final DefaultTableModel runtimeModel;
    @NotNull
    private final JTable runtimeTable;
    @NotNull
    private final JButton refreshButton;
    @NotNull
    private final JButton browseButton;
    @NotNull
    private final JButton downloadButton;
    @NotNull
    private final JLabel statusLabel;
    @NotNull
    private final JProgressBar progressBar;
    private int detectionGeneration;
    private boolean detecting;

    public JavaSettingsTab(@NotNull LauncherService launcherService) {
        super(new BorderLayout(), launcherService);
        this.runtimeModel = new DefaultTableModel(new Object[]{
                Localization.text("settings.java.column.java"),
                Localization.text("settings.java.column.version"),
                Localization.text("settings.java.column.source"),
                Localization.text("settings.java.column.path")
        }, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        this.runtimeTable = new JTable(this.runtimeModel);
        this.refreshButton = new JButton(Localization.text("settings.java.button.refresh"));
        this.browseButton = new JButton(Localization.text("settings.java.button.browse"));
        this.downloadButton = new JButton(Localization.text("settings.java.button.download"));
        this.statusLabel = new JLabel(" ");
        this.progressBar = new JProgressBar();

        JPanel content = new JPanel(new GridBagLayout());
        GridBagConstraints heading = new GridBagConstraints();
        heading.gridx = 0;
        heading.gridy = 0;
        heading.weightx = 1;
        heading.anchor = GridBagConstraints.LINE_START;
        heading.insets = new Insets(0, 0, 6, 0);
        content.add(this.createHeader(), heading);

        GridBagConstraints description = new GridBagConstraints();
        description.gridx = 0;
        description.gridy = 1;
        description.weightx = 1;
        description.fill = GridBagConstraints.HORIZONTAL;
        description.anchor = GridBagConstraints.LINE_START;
        description.insets = new Insets(0, 0, 12, 0);
        content.add(new JLabel(Localization.text("settings.java.description")), description);

        this.runtimeTable.setFillsViewportHeight(true);
        this.runtimeTable.setRowHeight(24);
        this.runtimeTable.setRowSelectionAllowed(false);
        this.runtimeTable.getTableHeader().setResizingAllowed(false);
        this.runtimeTable.getTableHeader().setReorderingAllowed(false);
        this.runtimeTable.getColumnModel().getColumn(0).setPreferredWidth(60);
        this.runtimeTable.getColumnModel().getColumn(1).setPreferredWidth(120);
        this.runtimeTable.getColumnModel().getColumn(2).setPreferredWidth(90);
        this.runtimeTable.getColumnModel().getColumn(3).setPreferredWidth(520);
        JScrollPane runtimeScroll = new JScrollPane(this.runtimeTable);
        runtimeScroll.setPreferredSize(new Dimension(850, 280));
        GridBagConstraints runtimes = new GridBagConstraints();
        runtimes.gridx = 0;
        runtimes.gridy = 2;
        runtimes.weightx = 1;
        runtimes.weighty = 1;
        runtimes.fill = GridBagConstraints.BOTH;
        content.add(runtimeScroll, runtimes);

        JPanel actions = new JPanel(new BorderLayout(12, 0));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        buttons.add(this.refreshButton);
        buttons.add(this.browseButton);
        buttons.add(this.downloadButton);
        actions.add(buttons, BorderLayout.WEST);
        this.progressBar.setIndeterminate(true);
        this.progressBar.setPreferredSize(new Dimension(130, 18));
        actions.add(this.progressBar, BorderLayout.EAST);
        GridBagConstraints actionConstraints = new GridBagConstraints();
        actionConstraints.gridx = 0;
        actionConstraints.gridy = 3;
        actionConstraints.weightx = 1;
        actionConstraints.fill = GridBagConstraints.HORIZONTAL;
        actionConstraints.insets = new Insets(10, 0, 0, 0);
        content.add(actions, actionConstraints);

        GridBagConstraints status = new GridBagConstraints();
        status.gridx = 0;
        status.gridy = 4;
        status.weightx = 1;
        status.fill = GridBagConstraints.HORIZONTAL;
        status.anchor = GridBagConstraints.LINE_START;
        status.insets = new Insets(8, 0, 0, 0);
        content.add(this.statusLabel, status);
        this.add(content, BorderLayout.CENTER);

        this.refreshButton.addActionListener(event -> this.refreshRuntimes());
        this.browseButton.addActionListener(event -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle(Localization.text("settings.java.browse.choose"));
            chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
            if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
            Path selectedRuntime = chooser.getSelectedFile().toPath().toAbsolutePath().normalize();
            int generation = ++this.detectionGeneration;
            this.detecting = true;
            this.statusLabel.setText(Localization.text("settings.java.status.validating", selectedRuntime));
            this.updateControlState();
            new SwingWorker<JavaRuntime, Void>() {
                @Override
                @NotNull
                protected JavaRuntime doInBackground() throws Exception {
                    JavaRuntime runtime;
                    try {
                        runtime = JavaRuntimeManager.resolve(JavaVersion.JAVA_8, selectedRuntime);
                    }
                    catch (IOException java8Exception) {
                        try {
                            runtime = JavaRuntimeManager.resolve(JavaVersion.JAVA_25, selectedRuntime);
                        }
                        catch (IOException java25Exception) {
                            java25Exception.addSuppressed(java8Exception);
                            throw new IOException(
                                    "The selected location is not a Java 8 or Java 25 runtime.",
                                    java25Exception
                            );
                        }
                    }
                    JavaSettingsTab.this.launcherService.addJavaRuntime(runtime.executable());
                    return runtime;
                }

                @Override
                protected void done() {
                    if (generation != JavaSettingsTab.this.detectionGeneration) return;
                    JavaSettingsTab.this.detecting = false;
                    try {
                        this.get();
                        JavaSettingsTab.this.refreshRuntimes();
                        return;
                    }
                    catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    }
                    catch (ExecutionException exception) {
                        Throwable cause = exception.getCause();
                        String message = cause == null || cause.getMessage() == null
                                ? exception.getClass().getSimpleName()
                                : cause.getMessage();
                        JavaSettingsTab.this.statusLabel.setText(Localization.text("settings.java.status.browse_failed"));
                        JOptionPane.showMessageDialog(
                                JavaSettingsTab.this,
                                message,
                                Localization.text("settings.java.error.browse"),
                                JOptionPane.ERROR_MESSAGE
                        );
                    }
                    JavaSettingsTab.this.updateControlState();
                }
            }.execute();
        });
        this.downloadButton.addActionListener(event -> {
            Runnable showVendorDownloads = () -> {
                JavaDownloadDialog dialog = new JavaDownloadDialog(
                        SwingUtilities.getWindowAncestor(this),
                        this::refreshRuntimes
                );
                dialog.showModal();
            };
            boolean linux = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux");
            if (!linux || !this.launcherService.settings().showLinuxJavaPackageManagerReminder()) {
                showVendorDownloads.run();
                return;
            }

            //show a special dialog for linux users
            LinuxJavaReminderDialog reminderDialog = new LinuxJavaReminderDialog(SwingUtilities.getWindowAncestor(this));
            LinuxJavaReminderDialog.Result result = reminderDialog.showModal();
            if (result == null) return;
            if (result.hideReminder()) {
                try {
                    this.launcherService.dismissLinuxJavaPackageManagerReminder();
                }
                catch (Exception exception) {
                    this.showSaveError(exception);
                }
            }
            if (result.downloadFromVendor()) showVendorDownloads.run();
        });
        this.refreshRuntimes();
    }

    @Override
    @NotNull
    public String header() {
        return Localization.text("settings.java.heading");
    }

    private void refreshRuntimes() {
        int generation = ++this.detectionGeneration;
        List<Path> javaRuntimePaths = this.launcherService.settings().javaRuntimePaths();
        this.detecting = true;
        this.statusLabel.setText(Localization.text("settings.java.status.detecting"));
        this.updateControlState();
        new SwingWorker<List<JavaRuntime>, Void>() {
            @Override
            @NotNull
            protected List<JavaRuntime> doInBackground() {
                List<JavaRuntime> runtimes = new ArrayList<>();
                runtimes.addAll(JavaRuntimeManager.detect(JavaVersion.JAVA_8, javaRuntimePaths));
                runtimes.addAll(JavaRuntimeManager.detect(JavaVersion.JAVA_25, javaRuntimePaths));
                return runtimes;
            }

            @Override
            protected void done() {
                if (generation != JavaSettingsTab.this.detectionGeneration) return;
                JavaSettingsTab.this.detecting = false;
                try {
                    List<JavaRuntime> runtimes = this.get();
                    JavaSettingsTab.this.runtimeModel.setRowCount(0);
                    Path managedRoot = MinecraftPaths.RUNTIMES.toAbsolutePath().normalize();
                    for (JavaRuntime runtime : runtimes) {
                        Path executable = runtime.executable().toAbsolutePath().normalize();
                        JavaSettingsTab.this.runtimeModel.addRow(new Object[]{
                                runtime.version().major(),
                                runtime.detectedVersion(),
                                executable.startsWith(managedRoot)
                                        ? Localization.text("settings.java.source.managed")
                                        : Localization.text("settings.java.source.system"),
                                executable
                        });
                    }
                    JavaSettingsTab.this.statusLabel.setText(runtimes.isEmpty()
                            ? Localization.text("settings.java.status.none")
                            : Localization.text("settings.java.status.found", runtimes.size()));
                }
                catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
                catch (ExecutionException exception) {
                    Throwable cause = exception.getCause();
                    String message = cause == null || cause.getMessage() == null ? exception.getClass().getSimpleName() : cause.getMessage();
                    JavaSettingsTab.this.statusLabel.setText(Localization.text("settings.java.status.error"));
                    JOptionPane.showMessageDialog(
                            JavaSettingsTab.this,
                            message,
                            Localization.text("settings.java.error.detect"),
                            JOptionPane.ERROR_MESSAGE
                    );
                }
                JavaSettingsTab.this.updateControlState();
            }
        }.execute();
    }

    private void updateControlState() {
        this.refreshButton.setEnabled(!this.detecting);
        this.browseButton.setEnabled(!this.detecting);
        this.downloadButton.setEnabled(!this.detecting);
        this.progressBar.setVisible(this.detecting);
    }
}
