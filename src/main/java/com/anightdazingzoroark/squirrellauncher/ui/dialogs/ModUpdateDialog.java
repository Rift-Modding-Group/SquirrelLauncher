package com.anightdazingzoroark.squirrellauncher.ui.dialogs;

import com.anightdazingzoroark.squirrellauncher.minecraft.mod.ManagedMod;
import com.anightdazingzoroark.squirrellauncher.minecraft.mod.ModDownloadFile;
import com.anightdazingzoroark.squirrellauncher.minecraft.mod.platform.ModDownloadPlatform;
import com.anightdazingzoroark.squirrellauncher.ui.Localization;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public final class ModUpdateDialog extends AbstractDialog<ModDownloadFile> {
    public ModUpdateDialog(
            @NotNull Window owner,
            @NotNull ManagedMod managedMod,
            @NotNull List<ModDownloadFile> downloadFiles
    ) {
        super(owner, Localization.text("instance.mods.dialog.update"));
        if (downloadFiles.isEmpty()) throw new IllegalArgumentException("At least one download file is required.");
        this.setLayout(new BorderLayout(0, 12));

        JPanel content = new JPanel(new GridBagLayout());
        content.setBorder(BorderFactory.createEmptyBorder(16, 16, 0, 16));

        ModDownloadFile defaultFile = downloadFiles.getFirst();
        JLabel introductionLabel = new JLabel(Localization.text(
                defaultFile.platform() == ModDownloadPlatform.GITHUB
                        ? "instance.mods.update.available_files"
                        : "instance.mods.update.available",
                managedMod.name()
        ));
        Dimension introductionSize = new Dimension(640, introductionLabel.getPreferredSize().height);
        introductionLabel.setMinimumSize(introductionSize);
        introductionLabel.setPreferredSize(introductionSize);
        introductionLabel.setMaximumSize(introductionSize);
        GridBagConstraints introduction = new GridBagConstraints();
        introduction.gridx = 0;
        introduction.gridy = 0;
        introduction.gridwidth = 2;
        introduction.anchor = GridBagConstraints.LINE_START;
        introduction.insets = new Insets(0, 0, 10, 0);
        content.add(introductionLabel, introduction);

        JComboBox<ModDownloadFile> fileSelector = defaultFile.platform() == ModDownloadPlatform.GITHUB
                ? new JComboBox<>(downloadFiles.toArray(ModDownloadFile[]::new))
                : null;
        JComponent fileComponent;
        if (fileSelector == null) {
            JLabel fileLabel = new JLabel(ModUpdateDialog.fileDescription(defaultFile));
            Dimension fileLabelSize = new Dimension(580, fileLabel.getPreferredSize().height);
            fileLabel.setMinimumSize(fileLabelSize);
            fileLabel.setPreferredSize(fileLabelSize);
            fileLabel.setMaximumSize(fileLabelSize);
            fileComponent = fileLabel;
        }
        else {
            Dimension fileSelectorSize = new Dimension(580, fileSelector.getPreferredSize().height);
            fileSelector.setMinimumSize(fileSelectorSize);
            fileSelector.setPreferredSize(fileSelectorSize);
            fileSelector.setMaximumSize(fileSelectorSize);
            fileSelector.setRenderer(new DefaultListCellRenderer() {
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
                    if (value instanceof ModDownloadFile file) {
                        label.setText(ModUpdateDialog.fileDescription(file));
                    }
                    return label;
                }
            });
            fileComponent = fileSelector;
        }
        JPanel fileComponentContainer = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        fileComponentContainer.add(fileComponent);
        this.addRow(
                content,
                1,
                new JLabel(Localization.text("instance.mods.update.file")),
                fileComponentContainer
        );

        JButton downloadButton = new JButton(Localization.text("instance.mods.update.button.download"));
        downloadButton.addActionListener(event -> {
            ModDownloadFile selectedFile = fileSelector == null
                    ? defaultFile
                    : (ModDownloadFile) fileSelector.getSelectedItem();
            if (selectedFile != null) this.complete(selectedFile);
        });
        JButton closeButton = new JButton(Localization.text("mod.download.button.close"));
        closeButton.addActionListener(event -> this.closeDialog());
        this.getRootPane().setDefaultButton(downloadButton);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));
        actions.add(downloadButton);
        actions.add(closeButton);

        this.add(content, BorderLayout.CENTER);
        this.add(actions, BorderLayout.SOUTH);

        this.setMinimumSize(new Dimension(680, 160));
        this.resizeToContent();
        this.setSize(this.getMinimumSize().width, this.getHeight());
        this.setLocationRelativeTo(owner);
    }

    @NotNull
    private static String fileDescription(@NotNull ModDownloadFile file) {
        String versionName = file.versionName().isBlank() ? file.fileName() : file.versionName();
        String releaseType = Localization.text("mod.download.release." + file.releaseType());
        String size = file.fileSize() > 0L
                ? String.format("%.1f MiB", file.fileSize() / (1024.0 * 1024.0))
                : Localization.text("mod.download.size.unknown");
        return Localization.text("mod.download.file", versionName, releaseType, size, file.fileName());
    }
}
