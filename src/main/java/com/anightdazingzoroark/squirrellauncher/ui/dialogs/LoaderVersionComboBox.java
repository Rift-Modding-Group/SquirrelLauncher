package com.anightdazingzoroark.squirrellauncher.ui.dialogs;

import com.anightdazingzoroark.squirrellauncher.minecraft.install.LoaderVersionCatalog;
import com.anightdazingzoroark.squirrellauncher.minecraft.instance.InstanceType;
import com.anightdazingzoroark.squirrellauncher.ui.Localization;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.io.IOException;
import java.io.Serial;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

public final class LoaderVersionComboBox extends JPanel {
    @Serial
    private static final long serialVersionUID = 1L;
    @NotNull
    private final Runnable selectionChanged;
    @NotNull
    private final Runnable contentSizeChanged;
    @NotNull
    private final JComboBox<LoaderVersionCatalog.Version> versions;
    @NotNull
    private final JPanel fetchStatus;
    @NotNull
    private final JProgressBar fetchProgress;
    @NotNull
    private final JLabel fetchStatusLabel;
    @Nullable
    private SwingWorker<List<LoaderVersionCatalog.Version>, Void> versionWorker;
    private int requestNumber;
    private boolean updating;
    private boolean userSelectedVersion;

    public LoaderVersionComboBox(@NotNull Runnable selectionChanged, @NotNull Runnable contentSizeChanged) {
        super(new BorderLayout(0, 4));
        this.selectionChanged = selectionChanged;
        this.contentSizeChanged = contentSizeChanged;
        this.versions = new JComboBox<>();
        this.fetchStatus = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 0));
        this.fetchProgress = new JProgressBar();
        this.fetchStatusLabel = new JLabel(Localization.text("loader.versions.loading"));

        this.versions.setPrototypeDisplayValue(new LoaderVersionCatalog.Version("14.23.5.2859-alpha", true));
        this.versions.addActionListener(event -> {
            if (!this.updating) {
                this.userSelectedVersion = true;
                this.selectionChanged.run();
            }
        });
        this.fetchProgress.setIndeterminate(false);
        this.fetchStatus.add(this.fetchProgress);
        this.fetchStatus.add(this.fetchStatusLabel);
        this.add(this.versions, BorderLayout.CENTER);
        this.add(this.fetchStatus, BorderLayout.SOUTH);
    }

    public void load(@Nullable InstanceType type, @Nullable String preferredVersion) {
        this.cancelLoading();
        this.userSelectedVersion = false;

        List<LoaderVersionCatalog.Version> discoveredVersions;
        try {
            discoveredVersions = type == null
                    ? List.of()
                    : LoaderVersionCatalog.installedVersions(type);
        }
        catch (IOException exception) {
            discoveredVersions = List.of();
        }
        List<LoaderVersionCatalog.Version> installedVersions = discoveredVersions;

        this.updating = true;
        this.versions.removeAllItems();
        for (LoaderVersionCatalog.Version version : installedVersions) {
            this.versions.addItem(version);
            if (version.value().equals(preferredVersion)) this.versions.setSelectedItem(version);
        }
        this.updating = false;
        this.versions.setEnabled(!installedVersions.isEmpty());
        boolean hasLoader = type != null && type.hasMods;
        this.fetchStatus.setVisible(hasLoader);
        this.fetchProgress.setVisible(hasLoader);
        this.fetchProgress.setIndeterminate(hasLoader);
        this.fetchStatusLabel.setText(Localization.text("loader.versions.loading"));
        this.versions.setToolTipText(hasLoader
                ? Localization.text("loader.versions.loading")
                : null);
        this.selectionChanged.run();
        this.contentSizeChanged.run();
        if (!hasLoader) return;

        int currentRequest = this.requestNumber;
        this.versionWorker = new SwingWorker<>() {
            @Override
            @NotNull
            protected List<LoaderVersionCatalog.Version> doInBackground() throws Exception {
                return LoaderVersionCatalog.availableVersions(type);
            }

            @Override
            protected void done() {
                if (currentRequest != LoaderVersionComboBox.this.requestNumber || this.isCancelled()) return;
                LoaderVersionComboBox.this.versionWorker = null;
                try {
                    List<LoaderVersionCatalog.Version> fetchedVersions = this.get();
                    List<LoaderVersionCatalog.Version> mergedVersions = new ArrayList<>(fetchedVersions);
                    Set<String> versionNames = new HashSet<>();
                    for (LoaderVersionCatalog.Version version : fetchedVersions) {
                        versionNames.add(version.value());
                    }
                    for (LoaderVersionCatalog.Version version : installedVersions) {
                        if (versionNames.add(version.value())) mergedVersions.add(version);
                    }
                    LoaderVersionCatalog.sortVersions(mergedVersions);

                    String selectedVersion = LoaderVersionComboBox.this.userSelectedVersion
                            ? LoaderVersionComboBox.this.selectedVersion()
                            : preferredVersion;
                    if (selectedVersion == null) selectedVersion = LoaderVersionComboBox.this.selectedVersion();
                    LoaderVersionComboBox.this.updating = true;
                    LoaderVersionComboBox.this.versions.removeAllItems();
                    for (LoaderVersionCatalog.Version version : mergedVersions) {
                        LoaderVersionComboBox.this.versions.addItem(version);
                        if (version.value().equals(selectedVersion)) {
                            LoaderVersionComboBox.this.versions.setSelectedItem(version);
                        }
                    }
                    LoaderVersionComboBox.this.updating = false;
                    LoaderVersionComboBox.this.versions.setEnabled(!mergedVersions.isEmpty());
                    LoaderVersionComboBox.this.versions.setToolTipText(null);
                    LoaderVersionComboBox.this.fetchProgress.setIndeterminate(false);
                    LoaderVersionComboBox.this.fetchStatus.setVisible(false);
                    LoaderVersionComboBox.this.selectionChanged.run();
                    LoaderVersionComboBox.this.contentSizeChanged.run();
                }
                catch (CancellationException ignored) {}
                catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
                catch (ExecutionException exception) {
                    LoaderVersionComboBox.this.fetchProgress.setIndeterminate(false);
                    LoaderVersionComboBox.this.fetchProgress.setVisible(false);
                    LoaderVersionComboBox.this.fetchStatusLabel.setText(
                            Localization.text("loader.versions.unavailable")
                    );
                    LoaderVersionComboBox.this.versions.setToolTipText(
                            Localization.text("loader.versions.load_failed")
                    );
                    LoaderVersionComboBox.this.contentSizeChanged.run();
                }
            }
        };
        this.versionWorker.execute();
    }

    @Nullable
    public String selectedVersion() {
        Object selection = this.versions.getSelectedItem();
        if (!(selection instanceof LoaderVersionCatalog.Version version)) return null;
        return version.value();
    }

    public int dropdownPreferredHeight() {
        return this.versions.getPreferredSize().height;
    }

    public void cancelLoading() {
        this.requestNumber++;
        if (this.versionWorker != null) this.versionWorker.cancel(true);
        this.versionWorker = null;
        this.fetchProgress.setIndeterminate(false);
    }
}
