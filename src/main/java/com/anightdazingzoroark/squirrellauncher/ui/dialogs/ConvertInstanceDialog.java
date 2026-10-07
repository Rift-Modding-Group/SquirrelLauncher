package com.anightdazingzoroark.squirrellauncher.ui.dialogs;

import com.anightdazingzoroark.squirrellauncher.minecraft.instance.InstanceType;
import com.anightdazingzoroark.squirrellauncher.minecraft.instance.MinecraftInstance;
import com.anightdazingzoroark.squirrellauncher.ui.LauncherFrame;
import com.anightdazingzoroark.squirrellauncher.ui.Localization;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.net.URL;

public final class ConvertInstanceDialog extends AbstractDialog<ConvertInstanceDialog.InstanceConversion>  {
    @NotNull
    private final JLabel loaderLabel;
    @NotNull
    private final LoaderVersionComboBox loaderVersions;
    @NotNull
    private final JButton convertButton;
    @Nullable
    private InstanceType selectedType;

    public ConvertInstanceDialog(@NotNull JFrame owner, @NotNull MinecraftInstance instance) {
        super(owner, Localization.text("convert.title", instance.name()));
        this.loaderLabel = new JLabel(Localization.text("convert.label.loader"));
        this.convertButton = new JButton(Localization.text("convert.button.convert"));
        this.loaderVersions = new LoaderVersionComboBox(this::updateConvertButton, this::resizeToContent);
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
        fields.add(new JLabel(Localization.text("convert.message")), message);

        ButtonGroup typeGroup = new ButtonGroup();
        JPanel typePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        JToggleButton firstButton = null;
        for (InstanceType type : InstanceType.values()) {
            if (type == instance.type()) continue;
            JToggleButton button = this.createTypeButton(type, typeGroup);
            typePanel.add(button);
            if (firstButton == null) {
                firstButton = button;
                this.selectedType = type;
            }
        }
        if (firstButton != null) firstButton.setSelected(true);
        this.addRow(
                fields,
                1,
                new JLabel(Localization.text("convert.label.type")),
                typePanel
        );
        this.addRow(
                fields,
                2,
                this.loaderLabel,
                this.loaderVersions,
                this.loaderVersions.dropdownPreferredHeight()
        );
        this.add(fields, BorderLayout.CENTER);

        JButton cancelButton = new JButton(Localization.text("convert.button.cancel"));
        cancelButton.addActionListener(event -> this.dispose());
        this.convertButton.addActionListener(event -> {
            if (this.selectedType == null) return;
            String loaderVersion = this.loaderVersions.selectedVersion();
            if (this.selectedType.hasMods && loaderVersion == null) return;
            this.complete(new InstanceConversion(
                    this.selectedType,
                    this.selectedType.hasMods ? loaderVersion : null
            ));
        });
        this.getRootPane().setDefaultButton(this.convertButton);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));
        actions.add(cancelButton);
        actions.add(this.convertButton);
        this.add(actions, BorderLayout.SOUTH);

        this.updateLoaderField();
        this.resizeToContent();
        this.setLocationRelativeTo(owner);
    }

    @Override
    public void dispose() {
        this.loaderVersions.cancelLoading();
        super.dispose();
    }

    @NotNull
    private JToggleButton createTypeButton(@NotNull InstanceType type, @NotNull ButtonGroup group) {
        URL resource = AbstractDialog.class.getResource(type.getIconPath());
        if (resource == null) {
            throw new IllegalStateException("Missing instance icon: " + type.getIconPath());
        }

        String label = LauncherFrame.displayName(type);
        JToggleButton button = new JToggleButton(label, new ImageIcon(resource));
        button.setHorizontalTextPosition(JToggleButton.CENTER);
        button.setVerticalTextPosition(JToggleButton.BOTTOM);
        button.setIconTextGap(8);
        button.setToolTipText(Localization.text("convert.tooltip", label));
        button.addActionListener(event -> {
            this.selectedType = type;
            this.updateLoaderField();
        });
        group.add(button);
        return button;
    }

    private void updateLoaderField() {
        boolean hasLoader = this.selectedType != null && this.selectedType.hasMods;
        this.loaderLabel.setVisible(hasLoader);
        this.loaderVersions.setVisible(hasLoader);
        this.loaderVersions.load(this.selectedType, null);
    }

    private void updateConvertButton() {
        this.convertButton.setEnabled(this.selectedType != null
                && (!this.selectedType.hasMods || this.loaderVersions.selectedVersion() != null));
    }

    public record InstanceConversion(@NotNull InstanceType type, @Nullable String loaderVersion) {}
}
