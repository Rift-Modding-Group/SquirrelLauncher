package com.anightdazingzoroark.squirrellauncher.ui;

import com.anightdazingzoroark.squirrellauncher.minecraft.instance.InstanceType;
import com.anightdazingzoroark.squirrellauncher.minecraft.instance.MinecraftInstance;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.WindowConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.Serial;
import java.net.URL;

public final class ConvertInstanceDialog extends JDialog {
    @Serial
    private static final long serialVersionUID = 1L;
    @NotNull
    private final JLabel loaderLabel = new JLabel(Localization.text("convert.label.loader"));
    @NotNull
    private final JTextField loaderField = new JTextField(22);
    @NotNull
    private final JButton convertButton = new JButton(Localization.text("convert.button.convert"));
    @Nullable
    private InstanceType selectedType;
    @Nullable
    private InstanceType resultType;
    @Nullable
    private String resultLoaderVersion;

    public ConvertInstanceDialog(@NotNull JFrame owner, @NotNull MinecraftInstance instance) {
        super(owner, Localization.text("convert.title", instance.name()), true);
        this.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
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
        this.addRow(fields, 1, new JLabel(Localization.text("convert.label.type")), typePanel);
        this.addRow(fields, 2, this.loaderLabel, this.loaderField);
        this.add(fields, BorderLayout.CENTER);

        JButton cancelButton = new JButton(Localization.text("convert.button.cancel"));
        cancelButton.addActionListener(event -> this.dispose());
        this.convertButton.addActionListener(event -> {
            if (this.selectedType == null) return;
            String loaderVersion = this.loaderField.getText().trim();
            if (this.selectedType.hasMods && loaderVersion.isEmpty()) return;
            this.resultType = this.selectedType;
            this.resultLoaderVersion = this.selectedType.hasMods ? loaderVersion : null;
            this.dispose();
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

    @Nullable
    public InstanceType selectedType() {
        return this.resultType;
    }

    @Nullable
    public String loaderVersion() {
        return this.resultLoaderVersion;
    }

    @NotNull
    private JToggleButton createTypeButton(@NotNull InstanceType type, @NotNull ButtonGroup group) {
        URL resource = ConvertInstanceDialog.class.getResource(type.getIconPath());
        if (resource == null) throw new IllegalStateException("Missing instance icon: " + type.getIconPath());

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
        this.convertButton.setEnabled(
                this.selectedType != null
                        && (!this.selectedType.hasMods || !this.loaderField.getText().isBlank())
        );
    }

    private void resizeToContent() {
        this.pack();
    }

    private void addRow(@NotNull JPanel panel, int row, @NotNull JLabel label, @NotNull Component component) {
        GridBagConstraints left = new GridBagConstraints();
        left.gridx = 0;
        left.gridy = row;
        left.anchor = GridBagConstraints.LINE_START;
        left.insets = new Insets(5, 0, 5, 12);
        panel.add(label, left);

        GridBagConstraints right = new GridBagConstraints();
        right.gridx = 1;
        right.gridy = row;
        right.weightx = 1;
        right.fill = GridBagConstraints.HORIZONTAL;
        right.insets = new Insets(5, 0, 5, 0);
        panel.add(component, right);
    }

    private record FieldListener(@NotNull Runnable callback) implements DocumentListener {
        @Override
        public void insertUpdate(@NotNull DocumentEvent event) {
            this.callback.run();
        }

        @Override
        public void removeUpdate(@NotNull DocumentEvent event) {
            this.callback.run();
        }

        @Override
        public void changedUpdate(@NotNull DocumentEvent event) {
            this.callback.run();
        }
    }
}
