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
    private final JTextField loaderField;
    @NotNull
    private final JButton convertButton;
    @Nullable
    private InstanceType selectedType;

    public ConvertInstanceDialog(@NotNull JFrame owner, @NotNull MinecraftInstance instance) {
        super(owner, Localization.text("convert.title", instance.name()));
        this.loaderLabel = new JLabel(Localization.text("convert.label.loader"));
        this.loaderField = new JTextField(22);
        this.convertButton = new JButton(Localization.text("convert.button.convert"));
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
        this.addRow(fields, 2, this.loaderLabel, this.loaderField);
        this.add(fields, BorderLayout.CENTER);

        JButton cancelButton = new JButton(Localization.text("convert.button.cancel"));
        cancelButton.addActionListener(event -> this.dispose());
        this.convertButton.addActionListener(event -> {
            if (this.selectedType == null) return;
            String loaderVersion = this.loaderField.getText().trim();
            if (this.selectedType.hasMods && loaderVersion.isEmpty()) return;
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

        this.loaderField.getDocument().addDocumentListener(new FieldListener(this::updateConvertButton));
        this.updateLoaderField();
        this.resizeToContent();
        this.setResizable(false);
        this.setLocationRelativeTo(owner);
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
        this.loaderField.setVisible(hasLoader);
        if (this.selectedType == InstanceType.FORGE) this.loaderField.setText("14.23.5.2859");
        else if (this.selectedType == InstanceType.CLEANROOM) this.loaderField.setText("0.6.13-alpha");
        else this.loaderField.setText("");
        this.updateConvertButton();
        this.resizeToContent();
    }

    private void updateConvertButton() {
        this.convertButton.setEnabled(this.selectedType != null && (!this.selectedType.hasMods || !this.loaderField.getText().isBlank()));
    }

    public record InstanceConversion(@NotNull InstanceType type, @Nullable String loaderVersion) {}
}
