package com.anightdazingzoroark.squirrellauncher.ui.dialogs;

import com.anightdazingzoroark.squirrellauncher.minecraft.mod.ManagedMod;
import com.anightdazingzoroark.squirrellauncher.minecraft.mod.ModDownloadFile;
import com.anightdazingzoroark.squirrellauncher.minecraft.mod.platform.ModDownloadPlatform;
import com.anightdazingzoroark.squirrellauncher.ui.Localization;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ModUpdateDialog extends AbstractDialog<Map<ManagedMod, ModDownloadFile>> {
    public ModUpdateDialog(
            @NotNull Window owner,
            @NotNull Map<ManagedMod, List<ModDownloadFile>> availableUpdates
    ) {
        super(owner, Localization.text("instance.mods.dialog.update"));
        if (availableUpdates.isEmpty() || availableUpdates.values().stream().anyMatch(List::isEmpty)) {
            throw new IllegalArgumentException("At least one download file is required for each mod.");
        }
        this.setLayout(new BorderLayout());

        boolean multipleMods = availableUpdates.size() > 1;
        Map.Entry<ManagedMod, List<ModDownloadFile>> firstUpdate = availableUpdates.entrySet().iterator().next();
        ModDownloadFile firstFile = firstUpdate.getValue().getFirst();
        JLabel introductionLabel = new JLabel(Localization.text(
                multipleMods
                        ? "instance.mods.update.available_multiple"
                        : firstFile.platform() == ModDownloadPlatform.GITHUB
                                ? "instance.mods.update.available_files"
                                : "instance.mods.update.available",
                multipleMods ? availableUpdates.size() : firstUpdate.getKey().name()
        ));
        Dimension introductionSize = new Dimension(700, introductionLabel.getPreferredSize().height);
        introductionLabel.setMinimumSize(introductionSize);
        introductionLabel.setPreferredSize(introductionSize);
        introductionLabel.setMaximumSize(introductionSize);

        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setBorder(BorderFactory.createEmptyBorder(16, 16, multipleMods ? 0 : 5, 16));
        content.add(introductionLabel, BorderLayout.NORTH);

        JPanel updateRows = new JPanel(new GridBagLayout());
        Map<ManagedMod, JComboBox<ModDownloadFile>> fileSelectors = new LinkedHashMap<>();
        Map<ManagedMod, JCheckBox> updateSelections = new LinkedHashMap<>();
        int fileComponentWidth = 556;
        DefaultListCellRenderer fileRenderer = new DefaultListCellRenderer() {
            @Override
            @NotNull
            public Component getListCellRendererComponent(
                    @NotNull JList<?> list,
                    @Nullable Object value,
                    int index,
                    boolean selected,
                    boolean focused
            ) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, selected, focused);
                if (value instanceof ModDownloadFile file) label.setText(ModUpdateDialog.fileDescription(file));
                return label;
            }
        };
        int row = 0;
        for (Map.Entry<ManagedMod, List<ModDownloadFile>> update : availableUpdates.entrySet()) {
            ModDownloadFile defaultFile = update.getValue().getFirst();
            JComboBox<ModDownloadFile> fileSelector = defaultFile.platform() == ModDownloadPlatform.GITHUB
                    ? new JComboBox<>(update.getValue().toArray(ModDownloadFile[]::new))
                    : null;
            JComponent fileComponent;
            if (fileSelector == null) {
                fileComponent = new JLabel(ModUpdateDialog.fileDescription(defaultFile));
            }
            else {
                fileSelector.setRenderer(fileRenderer);
                fileSelectors.put(update.getKey(), fileSelector);
                fileComponent = fileSelector;
            }
            Dimension fileComponentSize = new Dimension(
                    fileComponentWidth,
                    fileComponent.getPreferredSize().height
            );
            fileComponent.setMinimumSize(fileComponentSize);
            fileComponent.setPreferredSize(fileComponentSize);
            fileComponent.setMaximumSize(fileComponentSize);

            JCheckBox updateSelection = new JCheckBox(update.getKey().name(), true);
            Dimension updateSelectionSize = new Dimension(160, updateSelection.getPreferredSize().height);
            updateSelection.setMinimumSize(updateSelectionSize);
            updateSelection.setPreferredSize(updateSelectionSize);
            updateSelection.setMaximumSize(updateSelectionSize);
            updateSelection.setToolTipText(update.getKey().name());
            updateSelections.put(update.getKey(), updateSelection);
            JPanel fileComponentContainer = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            fileComponentContainer.add(fileComponent);
            this.addRow(updateRows, row++, updateSelection, fileComponentContainer);
        }

        if (multipleMods) {
            JScrollPane updateScroll = new JScrollPane(
                    updateRows,
                    ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                    ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
            );
            updateScroll.setBorder(null);
            updateScroll.getVerticalScrollBar().setUnitIncrement(16);
            updateScroll.setPreferredSize(new Dimension(700, Math.min(420, 12 + availableUpdates.size() * 40)));
            content.add(updateScroll, BorderLayout.CENTER);
        }
        else content.add(updateRows, BorderLayout.CENTER);

        JButton downloadButton = new JButton(Localization.text("instance.mods.update.button.download"));
        downloadButton.addActionListener(event -> {
            Map<ManagedMod, ModDownloadFile> selectedUpdates = new LinkedHashMap<>();
            for (Map.Entry<ManagedMod, List<ModDownloadFile>> update : availableUpdates.entrySet()) {
                JCheckBox updateSelection = updateSelections.get(update.getKey());
                if (updateSelection == null || !updateSelection.isSelected()) continue;
                JComboBox<ModDownloadFile> fileSelector = fileSelectors.get(update.getKey());
                ModDownloadFile selectedFile = fileSelector == null
                        ? update.getValue().getFirst()
                        : (ModDownloadFile) fileSelector.getSelectedItem();
                if (selectedFile != null) selectedUpdates.put(update.getKey(), selectedFile);
            }
            this.complete(Collections.unmodifiableMap(selectedUpdates));
        });
        JButton closeButton = new JButton(Localization.text("mod.download.button.close"));
        closeButton.addActionListener(event -> this.closeDialog());
        this.getRootPane().setDefaultButton(downloadButton);

        if (!multipleMods) {
            Box singleModActions = Box.createHorizontalBox();
            singleModActions.add(Box.createHorizontalGlue());
            singleModActions.add(downloadButton);
            singleModActions.add(Box.createHorizontalStrut(6));
            singleModActions.add(closeButton);
            Dimension singleModActionsSize = new Dimension(
                    fileComponentWidth,
                    Math.max(downloadButton.getPreferredSize().height, closeButton.getPreferredSize().height)
            );
            singleModActions.setMinimumSize(singleModActionsSize);
            singleModActions.setPreferredSize(singleModActionsSize);
            singleModActions.setMaximumSize(singleModActionsSize);
            GridBagConstraints singleModActionsConstraints = new GridBagConstraints();
            singleModActionsConstraints.gridx = 1;
            singleModActionsConstraints.gridy = row;
            singleModActionsConstraints.anchor = GridBagConstraints.LINE_START;
            singleModActionsConstraints.insets = new Insets(10, 0, 5, 0);
            updateRows.add(singleModActions, singleModActionsConstraints);
        }
        else {
            JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
            actions.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));
            actions.add(downloadButton);
            actions.add(closeButton);
            this.add(actions, BorderLayout.SOUTH);
        }

        this.add(content, BorderLayout.CENTER);

        this.resizeToContent();
        int packedHeight = this.getHeight();
        this.setMinimumSize(new Dimension(760, packedHeight));
        this.setSize(this.getMinimumSize().width, packedHeight);
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
