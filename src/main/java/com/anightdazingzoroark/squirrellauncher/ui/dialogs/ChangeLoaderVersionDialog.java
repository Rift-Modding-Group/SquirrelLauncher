package com.anightdazingzoroark.squirrellauncher.ui.dialogs;

import com.anightdazingzoroark.squirrellauncher.minecraft.instance.MinecraftInstance;
import com.anightdazingzoroark.squirrellauncher.ui.LauncherFrame;
import com.anightdazingzoroark.squirrellauncher.ui.Localization;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

public final class ChangeLoaderVersionDialog extends AbstractDialog<String> {
    @NotNull
    private final LoaderVersionComboBox loaderVersions;
    @NotNull
    private final JButton changeButton;
    @NotNull
    private final String currentVersion;

    public ChangeLoaderVersionDialog(@NotNull JFrame owner, @NotNull MinecraftInstance instance) {
        super(owner, Localization.text("version.title", instance.name()));
        if (!instance.type().hasMods || instance.loaderVersion() == null) {
            throw new IllegalArgumentException("A modded instance with a loader version is required.");
        }

        this.currentVersion = instance.loaderVersion();
        this.changeButton = new JButton(Localization.text("version.button.change"));
        this.loaderVersions = new LoaderVersionComboBox(this::updateChangeButton, this::resizeToContent);
        this.setLayout(new BorderLayout(0, 12));

        JPanel fields = new JPanel(new GridBagLayout());
        fields.setBorder(BorderFactory.createEmptyBorder(16, 16, 0, 16));
        GridBagConstraints message = new GridBagConstraints();
        message.gridx = 0;
        message.gridy = 0;
        message.gridwidth = 2;
        message.weightx = 1;
        message.anchor = GridBagConstraints.LINE_START;
        message.insets = new Insets(0, 0, 12, 0);
        fields.add(new JLabel(Localization.text(
                "version.message",
                LauncherFrame.displayName(instance.type()),
                instance.name()
        )), message);
        this.addLoaderVersionRow(
                fields,
                1,
                new JLabel(Localization.text("version.label.loader")),
                this.loaderVersions
        );
        this.add(fields, BorderLayout.CENTER);

        JButton cancelButton = new JButton(Localization.text("version.button.cancel"));
        cancelButton.addActionListener(event -> this.dispose());
        this.changeButton.addActionListener(event -> {
            String selectedVersion = this.loaderVersions.selectedVersion();
            if (selectedVersion == null || selectedVersion.equals(this.currentVersion)) return;
            this.complete(selectedVersion);
        });
        this.getRootPane().setDefaultButton(this.changeButton);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));
        actions.add(cancelButton);
        actions.add(this.changeButton);
        this.add(actions, BorderLayout.SOUTH);

        this.loaderVersions.load(instance.type(), this.currentVersion);
        this.updateChangeButton();
        this.resizeToContent();
        this.setResizable(false);
        this.setLocationRelativeTo(owner);
    }

    @Override
    public void dispose() {
        this.loaderVersions.cancelLoading();
        super.dispose();
    }

    private void updateChangeButton() {
        String selectedVersion = this.loaderVersions.selectedVersion();
        this.changeButton.setEnabled(selectedVersion != null && !selectedVersion.equals(this.currentVersion));
    }
}
