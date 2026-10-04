package com.anightdazingzoroark.squirrellauncher.ui.dialogs.modDownloadDialog;

import com.anightdazingzoroark.squirrellauncher.launcher.LauncherService;
import com.anightdazingzoroark.squirrellauncher.minecraft.instance.MinecraftInstance;
import com.anightdazingzoroark.squirrellauncher.minecraft.mod.ManagedMod;
import com.anightdazingzoroark.squirrellauncher.minecraft.mod.ModDownloadFile;
import com.anightdazingzoroark.squirrellauncher.minecraft.mod.ModDownloadPlatform;
import com.anightdazingzoroark.squirrellauncher.minecraft.mod.ModDownloadProject;
import com.anightdazingzoroark.squirrellauncher.minecraft.mod.ModDownloadProjectDescription;
import com.anightdazingzoroark.squirrellauncher.minecraft.mod.ModDownloadSearchPage;
import com.anightdazingzoroark.squirrellauncher.ui.Localization;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JDialog;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.KeyStroke;
import javax.swing.WindowConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.net.HttpRetryException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

public final class ModDownloadDialog extends JDialog {
    @NotNull
    private final LauncherService launcherService;
    @NotNull
    private final MinecraftInstance instance;
    @NotNull
    private final Runnable installedListener;
    @NotNull
    private final JComboBox<ModDownloadPlatform> providerSelector;
    @NotNull
    private final JTextField searchField = new JTextField(32);
    @NotNull
    private final Timer searchTimer;
    @NotNull
    private final ModDownloadProjectTable projectTable = new ModDownloadProjectTable();
    @NotNull
    private final ModDownloadProjectDescriptionPanel descriptionPanel = new ModDownloadProjectDescriptionPanel();
    @NotNull
    private final JLabel versionLabel = new JLabel(Localization.text("mod.download.version"));
    @NotNull
    private final JComboBox<ModDownloadFile> fileSelector = new JComboBox<>();
    @NotNull
    private final JButton selectButton = new JButton(Localization.text("mod.download.button.select"));
    @NotNull
    private final JProgressBar progressBar = new JProgressBar();
    @NotNull
    private final JLabel statusLabel = new JLabel(" ");
    @NotNull
    private final JButton downloadButton = new JButton(Localization.text("mod.download.button.download_selected"));
    @NotNull
    private final JButton closeButton = new JButton(Localization.text("mod.download.button.close"));
    @NotNull
    private List<ModDownloadFile> files = List.of();
    @NotNull
    private final Map<String, ModDownloadFile> selectedFiles = new LinkedHashMap<>();
    @NotNull
    private final Set<String> installedProjectKeys = new HashSet<>();
    @NotNull
    private final List<ModDownloadProject> pendingIconProjects = new ArrayList<>();
    @NotNull
    private final Set<String> requestedIconProjectKeys = new HashSet<>();
    @Nullable
    private SwingWorker<ModDownloadSearchPage, Void> searchWorker;
    @Nullable
    private SwingWorker<List<ModDownloadFile>, Void> fileWorker;
    @Nullable
    private SwingWorker<String, Void> descriptionWorker;
    @Nullable
    private SwingWorker<Void, LoadedProjectIcon> iconWorker;
    @Nullable
    private SwingWorker<List<ModDownloadFile>, Void> dependencyWorker;
    @Nullable
    private SwingWorker<Boolean, Void> favoriteWorker;
    @Nullable
    private SwingWorker<Void, Void> installWorker;
    private int searchGeneration;
    private int fileGeneration;
    private int nextSearchOffset;
    private boolean hasMoreSearchResults;
    private boolean loadingMoreSearchResults;
    private boolean initialSearchComplete;

    public ModDownloadDialog(
            @NotNull Window owner,
            @NotNull LauncherService launcherService,
            @NotNull MinecraftInstance instance,
            @NotNull List<ManagedMod> installedMods,
            @NotNull Runnable installedListener
    ) {
        super(owner, Localization.text("mod.download.title"), ModalityType.APPLICATION_MODAL);
        this.launcherService = launcherService;
        this.instance = instance;
        this.installedListener = installedListener;
        this.providerSelector = new JComboBox<>(ModDownloadPlatform.values());
        for (ManagedMod installedMod : installedMods) {
            if (installedMod.provider() == null || installedMod.providerProjectId() == null) continue;
            this.installedProjectKeys.add(
                    installedMod.provider().name() + ':' + installedMod.providerProjectId()
            );
        }
        this.projectTable.setInstalledProjectKeys(this.installedProjectKeys);
        this.searchTimer = new Timer(300, event -> this.search());
        this.searchTimer.setRepeats(false);

        this.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        this.setLayout(new BorderLayout(0, 12));
        this.setResizable(false);
        this.getRootPane().registerKeyboardAction(
                event -> this.closeDialog(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW
        );
        JPanel headingPanel = new JPanel(new BorderLayout(0, 12));
        headingPanel.setBorder(BorderFactory.createEmptyBorder(16, 16, 0, 16));
        JLabel heading = new JLabel(Localization.text("mod.download.heading", instance.name()));
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 20f));
        headingPanel.add(heading, BorderLayout.NORTH);
        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        filters.add(new JLabel(Localization.text("mod.download.provider")));
        ImageIcon modrinthSourceIcon = new ImageIcon(
                ModDownloadDialog.class.getResource("/icons/modrinth.png")
        );
        ImageIcon curseForgeSourceIcon = new ImageIcon(
                ModDownloadDialog.class.getResource("/icons/curseforge.png")
        );
        ImageIcon githubSourceIcon = new ImageIcon(
                ModDownloadDialog.class.getResource("/icons/github.png")
        );
        Icon modrinthIcon = new ImageIcon(modrinthSourceIcon.getImage().getScaledInstance(
                20, 20, Image.SCALE_SMOOTH
        ));
        Icon curseForgeIcon = new ImageIcon(curseForgeSourceIcon.getImage().getScaledInstance(
                20, 20, Image.SCALE_SMOOTH
        ));
        Icon githubIcon = new ImageIcon(githubSourceIcon.getImage().getScaledInstance(
                20, 20, Image.SCALE_SMOOTH
        ));
        this.providerSelector.setRenderer(new DefaultListCellRenderer() {
            @Override
            @NotNull
            public Component getListCellRendererComponent(
                    @NotNull JList<?> list,
                    @Nullable Object value,
                    int index,
                    boolean selected,
                    boolean focused
            ) {
                JLabel label = (JLabel) super.getListCellRendererComponent(
                        list, value, index, selected, focused
                );
                if (value instanceof ModDownloadPlatform platform) {
                    label.setText(Localization.text(
                            "mod.download.provider." + platform.name().toLowerCase()
                    ));
                    label.setIcon(switch (platform) {
                        case MODRINTH -> modrinthIcon;
                        case CURSEFORGE -> curseForgeIcon;
                        case GITHUB -> githubIcon;
                    });
                    label.setIconTextGap(6);
                }
                return label;
            }
        });
        filters.add(this.providerSelector);
        filters.add(new JLabel(Localization.text("mod.download.search")));
        filters.add(this.searchField);
        headingPanel.add(filters, BorderLayout.SOUTH);
        this.add(headingPanel, BorderLayout.NORTH);

        JPanel content = new JPanel(new GridBagLayout());
        content.setBorder(BorderFactory.createEmptyBorder(0, 16, 0, 16));
        JScrollPane projectsScroll = new JScrollPane(this.projectTable);
        projectsScroll.setPreferredSize(new Dimension(590, 430));
        JSplitPane projectBrowser = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, projectsScroll, this.descriptionPanel);
        projectBrowser.setResizeWeight(0.5);
        projectBrowser.setDividerLocation(590);
        projectBrowser.setPreferredSize(new Dimension(1160, 430));
        GridBagConstraints projectsConstraints = new GridBagConstraints();
        projectsConstraints.gridx = 0;
        projectsConstraints.gridy = 0;
        projectsConstraints.gridwidth = 3;
        projectsConstraints.weightx = 1;
        projectsConstraints.weighty = 1;
        projectsConstraints.fill = GridBagConstraints.BOTH;
        content.add(projectBrowser, projectsConstraints);

        GridBagConstraints versionLabelConstraints = new GridBagConstraints();
        versionLabelConstraints.gridx = 0;
        versionLabelConstraints.gridy = 1;
        versionLabelConstraints.anchor = GridBagConstraints.LINE_START;
        versionLabelConstraints.insets = new Insets(10, 0, 0, 10);
        content.add(this.versionLabel, versionLabelConstraints);
        this.fileSelector.setRenderer(new DefaultListCellRenderer() {
            @Override
            @NotNull
            public Component getListCellRendererComponent(
                    @NotNull JList<?> list, @Nullable Object value,
                    int index, boolean selected, boolean focused
            ) {
                JLabel label = (JLabel) super.getListCellRendererComponent(
                        list, value, index, selected, focused
                );
                if (value instanceof ModDownloadFile file) {
                    String versionName = file.versionName().isBlank() ? file.fileName() : file.versionName();
                    String releaseType = Localization.text("mod.download.release." + file.releaseType());
                    String size = file.fileSize() > 0L
                            ? String.format("%.1f MiB", file.fileSize() / (1024.0 * 1024.0))
                            : Localization.text("mod.download.size.unknown");
                    label.setText(Localization.text(
                            "mod.download.file",
                            versionName,
                            releaseType,
                            size,
                            file.fileName()
                    ));
                }
                return label;
            }
        });
        GridBagConstraints version = new GridBagConstraints();
        version.gridx = 1;
        version.gridy = 1;
        version.weightx = 1;
        version.fill = GridBagConstraints.HORIZONTAL;
        version.insets = new Insets(10, 0, 0, 0);
        content.add(this.fileSelector, version);
        GridBagConstraints select = new GridBagConstraints();
        select.gridx = 2;
        select.gridy = 1;
        select.anchor = GridBagConstraints.LINE_END;
        select.insets = new Insets(10, 8, 0, 0);
        content.add(this.selectButton, select);

        this.progressBar.setIndeterminate(true);
        this.progressBar.setVisible(false);
        GridBagConstraints progress = new GridBagConstraints();
        progress.gridx = 0;
        progress.gridy = 2;
        progress.gridwidth = 3;
        progress.weightx = 1;
        progress.fill = GridBagConstraints.HORIZONTAL;
        progress.insets = new Insets(10, 0, 0, 0);
        content.add(this.progressBar, progress);
        GridBagConstraints status = new GridBagConstraints();
        status.gridx = 0;
        status.gridy = 3;
        status.gridwidth = 3;
        status.weightx = 1;
        status.fill = GridBagConstraints.HORIZONTAL;
        status.anchor = GridBagConstraints.LINE_START;
        status.insets = new Insets(8, 0, 0, 0);
        content.add(this.statusLabel, status);
        this.add(content, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        actions.setBorder(BorderFactory.createEmptyBorder(0, 12, 12, 12));
        actions.add(this.downloadButton);
        actions.add(this.closeButton);
        this.add(actions, BorderLayout.SOUTH);

        this.providerSelector.addActionListener(event -> {
            this.searchTimer.stop();
            ModDownloadPlatform platform = (ModDownloadPlatform) this.providerSelector.getSelectedItem();
            this.projectTable.setDownloadsColumnVisible(platform != ModDownloadPlatform.GITHUB);
            this.search();
        });
        this.searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(@NotNull DocumentEvent event) {
                ModDownloadDialog.this.scheduleSearch();
            }

            @Override
            public void removeUpdate(@NotNull DocumentEvent event) {
                ModDownloadDialog.this.scheduleSearch();
            }

            @Override
            public void changedUpdate(@NotNull DocumentEvent event) {
                ModDownloadDialog.this.scheduleSearch();
            }
        });
        projectsScroll.getVerticalScrollBar().addAdjustmentListener(event -> {
            int remaining = event.getAdjustable().getMaximum()
                    - event.getAdjustable().getVisibleAmount()
                    - event.getAdjustable().getValue();
            if (!event.getValueIsAdjusting()
                    && remaining <= this.projectTable.getRowHeight() * 2
                    && this.hasMoreSearchResults
                    && this.searchWorker == null
            ) {
                this.search(true);
            }
            SwingUtilities.invokeLater(() -> this.queueVisibleIcons(this.searchGeneration));
        });
        this.projectTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(@NotNull MouseEvent event) {
                int viewColumn = ModDownloadDialog.this.projectTable.columnAtPoint(event.getPoint());
                int viewRow = ModDownloadDialog.this.projectTable.rowAtPoint(event.getPoint());
                if (viewColumn < 0 || viewRow < 0
                        || ModDownloadDialog.this.projectTable.convertColumnIndexToModel(viewColumn) != 0
                        || !ModDownloadDialog.this.initialSearchComplete
                        || ModDownloadDialog.this.favoriteWorker != null) return;
                int modelRow = ModDownloadDialog.this.projectTable.convertRowIndexToModel(viewRow);
                ModDownloadProject project = ModDownloadDialog.this.projectTable.projectAtModelRow(modelRow);
                ModDownloadDialog.this.favoriteWorker = new SwingWorker<>() {
                    @Override
                    @NotNull
                    protected Boolean doInBackground() throws Exception {
                        return ModDownloadDialog.this.launcherService.toggleModFavorite(project);
                    }

                    @Override
                    protected void done() {
                        ModDownloadDialog.this.favoriteWorker = null;
                        try {
                            boolean favorite = this.get();
                            ModDownloadDialog.this.projectTable.setFavorite(
                                    project,
                                    favorite,
                                    ModDownloadDialog.this.searchField.getText().isBlank()
                            );
                            ModDownloadDialog.this.statusLabel.setText(Localization.text(favorite
                                    ? "mod.download.status.favorite_added"
                                    : "mod.download.status.favorite_removed", project.name()));
                        }
                        catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                        }
                        catch (CancellationException ignored) {}
                        catch (ExecutionException exception) {
                            ModDownloadDialog.this.showFailure(
                                    Localization.text("mod.download.error.favorite"),
                                    exception
                            );
                        }
                        ModDownloadDialog.this.updateControlState();
                    }
                };
                ModDownloadDialog.this.favoriteWorker.execute();
                ModDownloadDialog.this.updateControlState();
            }
        });
        this.projectTable.getSelectionModel().addListSelectionListener(event -> {
            if (event.getValueIsAdjusting()) return;
            ModDownloadProject project = this.selectedProject();
            this.descriptionPanel.showProject(project, null);
            this.descriptionPanel.setProjectIcon(this.projectTable.selectedProjectIcon());
            this.files = List.of();
            this.fileSelector.removeAllItems();
            int generation = ++this.fileGeneration;
            if (this.fileWorker != null) this.fileWorker.cancel(true);
            if (this.descriptionWorker != null) this.descriptionWorker.cancel(true);
            if (project == null) {
                this.fileWorker = null;
                this.descriptionWorker = null;
                this.updateControlState();
                return;
            }
            this.descriptionWorker = new SwingWorker<>() {
                @Override
                @NotNull
                protected String doInBackground() throws Exception {
                    return ModDownloadDialog.this.descriptionPanel.renderProjectDescription(
                            project,
                            ModDownloadDialog.this.launcherService.modDescription(project)
                    );
                }

                @Override
                protected void done() {
                    if (generation != ModDownloadDialog.this.fileGeneration) return;
                    ModDownloadDialog.this.descriptionWorker = null;
                    try {
                        ModDownloadDialog.this.descriptionPanel.showRenderedDescription(this.get());
                    }
                    catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    }
                    catch (CancellationException ignored) {}
                    catch (ExecutionException ignored) {
                        ModDownloadDialog.this.descriptionPanel.showProject(
                                project,
                                new ModDownloadProjectDescription(project.description(), false)
                        );
                        ModDownloadDialog.this.descriptionPanel.setProjectIcon(
                                ModDownloadDialog.this.projectTable.selectedProjectIcon()
                        );
                    }
                }
            };
            this.descriptionWorker.execute();
            this.statusLabel.setText(Localization.text("mod.download.status.loading_versions", project.name()));
            this.fileWorker = new SwingWorker<>() {
                @Override
                @NotNull
                protected List<ModDownloadFile> doInBackground() throws Exception {
                    return ModDownloadDialog.this.launcherService.modFiles(project);
                }

                @Override
                protected void done() {
                    if (generation != ModDownloadDialog.this.fileGeneration) return;
                    ModDownloadDialog.this.fileWorker = null;
                    try {
                        ModDownloadFile selectedFile = ModDownloadDialog.this.selectedFiles.get(
                                ModDownloadDialog.projectKey(project)
                        );
                        ModDownloadDialog.this.files = new ArrayList<>(this.get());
                        for (ModDownloadFile file : ModDownloadDialog.this.files) {
                            ModDownloadDialog.this.fileSelector.addItem(file);
                        }
                        if (selectedFile != null) {
                            for (int fileIndex = 0;
                                 fileIndex < ModDownloadDialog.this.fileSelector.getItemCount();
                                 fileIndex++
                            ) {
                                ModDownloadFile candidate = ModDownloadDialog.this.fileSelector.getItemAt(fileIndex);
                                if (candidate.providerFileId().equals(selectedFile.providerFileId())) {
                                    ModDownloadDialog.this.fileSelector.setSelectedIndex(fileIndex);
                                    break;
                                }
                            }
                        }
                        ModDownloadDialog.this.statusLabel.setText(ModDownloadDialog.this.files.isEmpty()
                                ? Localization.text("mod.download.status.no_versions")
                                : Localization.text(
                                        "mod.download.status.versions",
                                        ModDownloadDialog.this.files.size()
                                ));
                    }
                    catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    }
                    catch (CancellationException ignored) {}
                    catch (ExecutionException exception) {
                        ModDownloadDialog.this.statusLabel.setText(
                                Localization.text("mod.download.status.version_failed")
                        );
                        ModDownloadDialog.this.showFailure(
                                Localization.text("mod.download.error.versions"),
                                exception
                        );
                    }
                    ModDownloadDialog.this.updateControlState();
                }
            };
            this.fileWorker.execute();
            this.updateControlState();
        });
        this.fileSelector.addActionListener(event -> {
            ModDownloadProject project = this.selectedProject();
            ModDownloadFile file = this.selectedFile();
            if (project != null && file != null && this.selectedFiles.containsKey(ModDownloadDialog.projectKey(project))) {
                this.selectedFiles.put(ModDownloadDialog.projectKey(project), file);
            }
            this.updateControlState();
        });
        this.selectButton.addActionListener(event -> {
            ModDownloadProject project = this.selectedProject();
            ModDownloadFile file = this.selectedFile();
            if (project == null || file == null) return;
            String projectKey = ModDownloadDialog.projectKey(project);
            if (this.selectedFiles.containsKey(projectKey)) {
                this.selectedFiles.remove(projectKey);
            }
            else {
                this.selectedFiles.put(projectKey, file);
            }
            this.statusLabel.setText(Localization.text(
                    "mod.download.status.selected",
                    this.selectedFiles.size()
            ));
            this.updateControlState();
        });
        this.downloadButton.addActionListener(event -> {
            if (this.selectedFiles.isEmpty() || this.dependencyWorker != null || this.installWorker != null) return;
            List<ModDownloadFile> selectedDownloads = List.copyOf(this.selectedFiles.values());
            this.statusLabel.setText(Localization.text(
                    "mod.download.status.checking_dependencies",
                    selectedDownloads.size()
            ));
            this.dependencyWorker = new SwingWorker<>() {
                @Override
                @NotNull
                protected List<ModDownloadFile> doInBackground() throws Exception {
                    return ModDownloadDialog.this.launcherService.modDependencies(
                            ModDownloadDialog.this.instance,
                            selectedDownloads
                    );
                }

                @Override
                protected void done() {
                    ModDownloadDialog.this.dependencyWorker = null;
                    try {
                        List<ModDownloadFile> dependencies = this.get();
                        ModDownloadReviewDialog reviewDialog = new ModDownloadReviewDialog(
                                ModDownloadDialog.this,
                                selectedDownloads,
                                dependencies
                        );
                        if (!reviewDialog.showModal()) {
                            ModDownloadDialog.this.statusLabel.setText(
                                    Localization.text("mod.download.status.dependencies_cancelled")
                            );
                            ModDownloadDialog.this.updateControlState();
                            return;
                        }
                        List<ModDownloadFile> filesToInstall = new ArrayList<>();
                        for (ModDownloadFile dependency : dependencies) {
                            if (reviewDialog.isSelected(dependency)) filesToInstall.add(dependency);
                        }
                        for (ModDownloadFile selectedDownload : selectedDownloads) {
                            if (!reviewDialog.isSelected(selectedDownload)) continue;
                            filesToInstall.add(selectedDownload);
                        }
                        if (filesToInstall.isEmpty()) {
                            ModDownloadDialog.this.statusLabel.setText(
                                    Localization.text("mod.download.status.dependencies_cancelled")
                            );
                            ModDownloadDialog.this.updateControlState();
                            return;
                        }
                        ModDownloadDialog.this.statusLabel.setText(Localization.text(
                                "mod.download.status.downloading_selected",
                                filesToInstall.size()
                        ));
                        ModDownloadDialog.this.installWorker = new SwingWorker<>() {
                            @NotNull
                            private final List<ModDownloadFile> installedFiles = new ArrayList<>();
                            private int installedCount;

                            @Override
                            @Nullable
                            protected Void doInBackground() throws Exception {
                                for (ModDownloadFile file : filesToInstall) {
                                    ModDownloadDialog.this.launcherService.installMod(
                                            ModDownloadDialog.this.instance,
                                            file
                                    );
                                    this.installedFiles.add(file);
                                    this.installedCount++;
                                }
                                return null;
                            }

                            @Override
                            protected void done() {
                                ModDownloadDialog.this.installWorker = null;
                                try {
                                    if (this.isCancelled()) {
                                        ModDownloadDialog.this.statusLabel.setText(
                                                Localization.text("mod.download.status.cancelled")
                                        );
                                    }
                                    else {
                                        this.get();
                                        ModDownloadDialog.this.statusLabel.setText(Localization.text(
                                                "mod.download.status.installed_selected",
                                                this.installedCount
                                        ));
                                    }
                                }
                                catch (InterruptedException exception) {
                                    Thread.currentThread().interrupt();
                                }
                                catch (CancellationException ignored) {
                                    ModDownloadDialog.this.statusLabel.setText(
                                            Localization.text("mod.download.status.cancelled")
                                    );
                                }
                                catch (ExecutionException exception) {
                                    ModDownloadDialog.this.statusLabel.setText(
                                            Localization.text("mod.download.status.install_failed")
                                    );
                                    ModDownloadDialog.this.showFailure(
                                            Localization.text("mod.download.error.install"),
                                            exception
                                    );
                                }
                                for (ModDownloadFile installedFile : this.installedFiles) {
                                    String installedProjectKey = ModDownloadDialog.projectKey(installedFile);
                                    ModDownloadDialog.this.selectedFiles.remove(installedProjectKey);
                                    ModDownloadDialog.this.installedProjectKeys.add(installedProjectKey);
                                }
                                ModDownloadDialog.this.projectTable.setInstalledProjectKeys(
                                        ModDownloadDialog.this.installedProjectKeys
                                );
                                if (this.installedCount > 0) ModDownloadDialog.this.installedListener.run();
                                ModDownloadDialog.this.updateControlState();
                            }
                        };
                        ModDownloadDialog.this.installWorker.execute();
                    }
                    catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    }
                    catch (CancellationException ignored) {
                        ModDownloadDialog.this.statusLabel.setText(
                                Localization.text("mod.download.status.dependencies_cancelled")
                        );
                    }
                    catch (ExecutionException exception) {
                        ModDownloadDialog.this.statusLabel.setText(
                                Localization.text("mod.download.status.dependency_failed")
                        );
                        ModDownloadDialog.this.showFailure(
                                Localization.text("mod.download.error.dependencies"),
                                exception
                        );
                    }
                    ModDownloadDialog.this.updateControlState();
                }
            };
            this.dependencyWorker.execute();
            this.updateControlState();
        });
        this.closeButton.addActionListener(event -> this.closeDialog());
        this.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(@NotNull WindowEvent event) {
                ModDownloadDialog.this.closeDialog();
            }
        });

        this.setMinimumSize(new Dimension(1200, 700));
        this.pack();
        this.setLocationRelativeTo(owner);
        this.search();
    }

    public void showModal() {
        this.setVisible(true);
    }

    private void scheduleSearch() {
        this.searchGeneration++;
        if (this.searchWorker != null) this.searchWorker.cancel(true);
        this.searchWorker = null;
        this.loadingMoreSearchResults = false;
        this.searchTimer.restart();
        this.updateControlState();
    }

    private void search() {
        this.search(false);
    }

    private void search(boolean append) {
        ModDownloadPlatform platform = (ModDownloadPlatform) this.providerSelector.getSelectedItem();
        if (platform == null || this.dependencyWorker != null || this.installWorker != null) return;
        int generation = append ? this.searchGeneration : ++this.searchGeneration;
        int offset = append ? this.nextSearchOffset : 0;
        if (this.searchWorker != null) this.searchWorker.cancel(true);
        this.loadingMoreSearchResults = append;
        if (!append) {
            if (this.iconWorker != null) this.iconWorker.cancel(true);
            this.iconWorker = null;
            this.pendingIconProjects.clear();
            this.requestedIconProjectKeys.clear();
            if (this.fileWorker != null) this.fileWorker.cancel(true);
            if (this.descriptionWorker != null) this.descriptionWorker.cancel(true);
            this.fileWorker = null;
            this.descriptionWorker = null;
            this.fileGeneration++;
            this.files = List.of();
            this.nextSearchOffset = 0;
            this.hasMoreSearchResults = false;
            this.projectTable.setProjects(List.of());
            this.fileSelector.removeAllItems();
            this.descriptionPanel.showProject(null, null);
            this.statusLabel.setText(Localization.text(
                    "mod.download.status.searching",
                    Localization.text("mod.download.provider." + platform.name().toLowerCase())
            ));
        }
        else {
            this.statusLabel.setText(Localization.text("mod.download.status.loading_more"));
        }
        String searchText = this.searchField.getText();
        this.searchWorker = new SwingWorker<>() {
            @Override
            @NotNull
            protected ModDownloadSearchPage doInBackground() throws Exception {
                return ModDownloadDialog.this.launcherService.searchMods(platform, searchText, offset);
            }

            @Override
            protected void done() {
                if (generation != ModDownloadDialog.this.searchGeneration) return;
                ModDownloadDialog.this.searchWorker = null;
                ModDownloadDialog.this.loadingMoreSearchResults = false;
                try {
                    ModDownloadSearchPage page = this.get();
                    List<ModDownloadProject> projects = page.projects();
                    if (append) {
                        ModDownloadDialog.this.projectTable.appendProjects(projects);
                    }
                    else {
                        ModDownloadDialog.this.projectTable.setProjects(projects);
                    }
                    ModDownloadDialog.this.nextSearchOffset = page.nextOffset();
                    ModDownloadDialog.this.hasMoreSearchResults = page.hasMore();
                    int projectCount = ModDownloadDialog.this.projectTable.projectCount();
                    ModDownloadDialog.this.statusLabel.setText(projectCount == 0
                            ? Localization.text("mod.download.status.no_results")
                            : Localization.text(
                                    "mod.download.status.results",
                                    projectCount
                            ));
                    if (!projects.isEmpty()) {
                        SwingUtilities.invokeLater(() -> ModDownloadDialog.this.queueVisibleIcons(generation));
                    }
                }
                catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
                catch (CancellationException ignored) {}
                catch (ExecutionException exception) {
                    ModDownloadDialog.this.statusLabel.setText(
                            Localization.text("mod.download.status.search_failed")
                    );
                    ModDownloadDialog.this.showFailure(
                            Localization.text("mod.download.error.search"),
                            exception
                    );
                }
                if (!append) ModDownloadDialog.this.initialSearchComplete = true;
                ModDownloadDialog.this.updateControlState();
            }
        };
        this.searchWorker.execute();
        this.updateControlState();
    }

    private void queueVisibleIcons(int generation) {
        if (generation != this.searchGeneration || this.projectTable.projectCount() == 0) return;
        Rectangle visibleRectangle = this.projectTable.getVisibleRect();
        int firstViewRow = this.projectTable.rowAtPoint(new Point(
                visibleRectangle.x,
                visibleRectangle.y
        ));
        int lastViewRow = this.projectTable.rowAtPoint(new Point(
                visibleRectangle.x,
                Math.max(visibleRectangle.y, visibleRectangle.y + visibleRectangle.height - 1)
        ));
        if (firstViewRow < 0) firstViewRow = 0;
        if (lastViewRow < 0) lastViewRow = this.projectTable.getRowCount() - 1;
        for (int viewRow = firstViewRow; viewRow <= lastViewRow; viewRow++) {
            int modelRow = this.projectTable.convertRowIndexToModel(viewRow);
            ModDownloadProject project = this.projectTable.projectAtModelRow(modelRow);
            if (this.requestedIconProjectKeys.add(ModDownloadDialog.projectKey(project))) {
                this.pendingIconProjects.add(project);
            }
        }
        this.loadPendingIcons(generation);
    }

    private void loadPendingIcons(int generation) {
        if (this.iconWorker != null || this.pendingIconProjects.isEmpty()) return;
        List<ModDownloadProject> iconProjects = List.copyOf(this.pendingIconProjects);
        this.pendingIconProjects.clear();
        this.iconWorker = new SwingWorker<>() {
            @Override
            @Nullable
            protected Void doInBackground() throws Exception {
                for (ModDownloadProject project : iconProjects) {
                    if (this.isCancelled()) return null;
                    BufferedImage image;
                    try {
                        image = ModDownloadDialog.this.launcherService.modIcon(project);
                    }
                    catch (InterruptedException exception) {
                        throw exception;
                    }
                    catch (Exception ignored) {
                        continue;
                    }
                    if (image != null) this.publish(new LoadedProjectIcon(project, new ImageIcon(image)));
                }
                return null;
            }

            @Override
            protected void process(@NotNull List<LoadedProjectIcon> loadedIcons) {
                if (generation != ModDownloadDialog.this.searchGeneration) return;
                for (LoadedProjectIcon loadedIcon : loadedIcons) {
                    ModDownloadDialog.this.projectTable.setProjectIcon(
                            loadedIcon.project(), loadedIcon.icon()
                    );
                    ModDownloadProject selectedProject = ModDownloadDialog.this.selectedProject();
                    if (selectedProject != null
                            && ModDownloadDialog.projectKey(loadedIcon.project()).equals(
                                    ModDownloadDialog.projectKey(selectedProject)
                            )) {
                        ModDownloadDialog.this.descriptionPanel.setProjectIcon(loadedIcon.icon());
                    }
                }
            }

            @Override
            protected void done() {
                if (generation != ModDownloadDialog.this.searchGeneration) return;
                ModDownloadDialog.this.iconWorker = null;
                ModDownloadDialog.this.loadPendingIcons(generation);
            }
        };
        this.iconWorker.execute();
    }

    private void updateControlState() {
        boolean searching = this.searchWorker != null || this.searchTimer.isRunning();
        boolean loadingFiles = this.fileWorker != null;
        boolean checkingDependencies = this.dependencyWorker != null;
        boolean installing = this.installWorker != null;
        boolean savingFavorite = this.favoriteWorker != null;
        boolean busy = checkingDependencies || installing;
        boolean blockingSearch = searching && !this.loadingMoreSearchResults;
        this.providerSelector.setEnabled(!busy);
        this.searchField.setEnabled(!busy);
        this.projectTable.setEnabled(!blockingSearch && !busy && !savingFavorite);
        this.fileSelector.setEnabled(!loadingFiles && !busy && this.fileSelector.getItemCount() > 0);
        ModDownloadProject selectedProject = this.selectedProject();
        boolean showVersionSelection = selectedProject != null;
        this.versionLabel.setVisible(showVersionSelection);
        this.fileSelector.setVisible(showVersionSelection);
        this.selectButton.setVisible(showVersionSelection);
        boolean projectSelected = selectedProject != null
                && this.selectedFiles.containsKey(ModDownloadDialog.projectKey(selectedProject));
        this.selectButton.setText(Localization.text(projectSelected
                ? "mod.download.button.deselect"
                : "mod.download.button.select"));
        this.selectButton.setEnabled(!loadingFiles && !busy && this.selectedFile() != null);
        this.downloadButton.setText(Localization.text(
                "mod.download.button.download_selected_count",
                this.selectedFiles.size()
        ));
        this.downloadButton.setEnabled(!blockingSearch && !busy && !this.selectedFiles.isEmpty());
        this.closeButton.setText(Localization.text(installing
                ? "mod.download.button.cancel"
                : "mod.download.button.close"));
        this.progressBar.setVisible(searching || loadingFiles || checkingDependencies || installing || savingFavorite);
    }

    @Nullable
    private ModDownloadProject selectedProject() {
        return this.projectTable.selectedProject();
    }

    @Nullable
    private ModDownloadFile selectedFile() {
        return (ModDownloadFile) this.fileSelector.getSelectedItem();
    }

    private void showFailure(@NotNull String title, @NotNull ExecutionException exception) {
        Throwable cause = exception.getCause();
        String message = cause instanceof HttpRetryException retryException
                && retryException.responseCode() == 403
                ? Localization.text("common.error.github_rate_limit")
                : cause == null || cause.getMessage() == null || cause.getMessage().isBlank()
                        ? exception.getClass().getSimpleName()
                        : cause.getMessage();
        JOptionPane.showMessageDialog(this, message, title, JOptionPane.ERROR_MESSAGE);
    }

    private void closeDialog() {
        this.searchTimer.stop();
        if (this.installWorker != null) {
            this.installWorker.cancel(true);
            return;
        }
        this.searchGeneration++;
        this.fileGeneration++;
        this.pendingIconProjects.clear();
        this.requestedIconProjectKeys.clear();
        if (this.searchWorker != null) this.searchWorker.cancel(true);
        if (this.fileWorker != null) this.fileWorker.cancel(true);
        if (this.descriptionWorker != null) this.descriptionWorker.cancel(true);
        if (this.iconWorker != null) this.iconWorker.cancel(true);
        if (this.dependencyWorker != null) this.dependencyWorker.cancel(true);
        if (this.favoriteWorker != null) this.favoriteWorker.cancel(true);
        this.dispose();
    }

    @NotNull
    private static String projectKey(@NotNull ModDownloadProject project) {
        return project.platform().name() + ':' + project.projectId();
    }

    @NotNull
    private static String projectKey(@NotNull ModDownloadFile file) {
        return file.platform().name() + ':' + file.projectId();
    }

    private record LoadedProjectIcon(@NotNull ModDownloadProject project, @NotNull Icon icon) {}
}
