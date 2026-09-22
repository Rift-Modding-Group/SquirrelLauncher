package com.anightdazingzoroark.squirrellauncher.ui;

import com.anightdazingzoroark.squirrellauncher.launcher.InstanceCreationRequest;
import com.anightdazingzoroark.squirrellauncher.launcher.InstanceNames;
import com.anightdazingzoroark.squirrellauncher.minecraft.instance.InstanceType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.net.URL;

public final class CreateInstanceDialog extends JDialog {
    @NotNull
    private final JTextField nameField = new JTextField("", 22);
    @NotNull
    private final JLabel loaderLabel = new JLabel("Loader version:");
    @NotNull
    private final JTextField loaderField = new JTextField(22);
    @NotNull
    private final JButton createButton = new JButton("Create");
    @Nullable
    private InstanceCreationRequest result;
    @Nullable
    private InstanceType selectedType;

    private CreateInstanceDialog(@NotNull JFrame owner) {
        super(owner, "Create instance", true);
        this.setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        this.setLayout(new BorderLayout(0, 12));

        ButtonGroup typeGroup = new ButtonGroup();
        JPanel typePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        typePanel.add(this.createTypeButton(
                "Vanilla", InstanceType.VANILLA, "/icons/vanilla.png", typeGroup
        ));
        typePanel.add(this.createTypeButton(
                "Forge", InstanceType.FORGE, "/icons/forge.png", typeGroup
        ));
        typePanel.add(this.createTypeButton(
                "Cleanroom", InstanceType.CLEANROOM, "/icons/cleanroom.png", typeGroup
        ));

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(16, 16, 0, 16));
        this.addRow(form, 0, new JLabel("Instance name:"), this.nameField);
        this.addRow(form, 1, new JLabel("Instance type:"), typePanel);
        this.addRow(form, 2, this.loaderLabel, this.loaderField);
        this.add(form, BorderLayout.CENTER);

        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(event -> this.dispose());
        this.createButton.addActionListener(event -> this.accept());
        this.getRootPane().setDefaultButton(this.createButton);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));
        actions.add(cancelButton);
        actions.add(this.createButton);
        this.add(actions, BorderLayout.SOUTH);

        ((AbstractDocument) this.nameField.getDocument()).setDocumentFilter(new FolderNameFilter());
        this.nameField.getDocument().addDocumentListener(new FieldListener(this::updateCreateButton));
        this.loaderField.getDocument().addDocumentListener(new FieldListener(this::updateCreateButton));
        this.updateLoaderField();
        this.updateCreateButton();
        this.pack();
        this.setResizable(false);
        this.setLocationRelativeTo(owner);
    }

    @Nullable
    public static InstanceCreationRequest showDialog(@NotNull JFrame owner) {
        CreateInstanceDialog dialog = new CreateInstanceDialog(owner);
        dialog.setVisible(true);
        return dialog.result;
    }

    @NotNull
    private JToggleButton createTypeButton(
            @NotNull String label,
            @NotNull InstanceType type,
            @NotNull String iconPath,
            @NotNull ButtonGroup group
    ) {
        JToggleButton button = new JToggleButton(label, this.loadIcon(iconPath));
        button.setHorizontalTextPosition(JToggleButton.CENTER);
        button.setVerticalTextPosition(JToggleButton.BOTTOM);
        button.setIconTextGap(8);
        button.setToolTipText("Create a " + label + " instance");
        button.addActionListener(event -> {
            this.selectedType = type;
            this.updateLoaderField();
        });
        group.add(button);
        return button;
    }

    @NotNull
    private ImageIcon loadIcon(@NotNull String iconPath) {
        URL resource = CreateInstanceDialog.class.getResource(iconPath);
        if (resource == null) throw new IllegalStateException("Missing instance icon: " + iconPath);
        return new ImageIcon(new ImageIcon(resource).getImage());
    }

    private void updateLoaderField() {
        InstanceType type = this.selectedType;
        boolean hasLoader = type != null && type.hasMods;
        this.loaderLabel.setVisible(hasLoader);
        this.loaderField.setVisible(hasLoader);

        if (type == InstanceType.FORGE) this.loaderField.setText("14.23.5.2859");
        else if (type == InstanceType.CLEANROOM) this.loaderField.setText("0.6.13-alpha");
        else this.loaderField.setText("");
        this.updateCreateButton();
        if (this.isDisplayable()) this.pack();
    }

    private void accept() {
        String name = this.nameField.getText();
        InstanceType type = this.selectedType;
        String loaderVersion = this.loaderField.getText().trim();

        if (!InstanceNames.isValid(name)) return;
        if (type == null) return;
        if (type.hasMods && loaderVersion.isEmpty()) return;

        this.result = new InstanceCreationRequest(
                name, type,
                type.hasMods ? loaderVersion : null
        );
        this.dispose();
    }

    private void updateCreateButton() {
        InstanceType type = this.selectedType;
        boolean hasLoaderVersion = type != null && (!type.hasMods || !this.loaderField.getText().isBlank());
        this.createButton.setEnabled(
                type != null && InstanceNames.isValid(this.nameField.getText()) && hasLoaderVersion
        );
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

    private static final class FolderNameFilter extends DocumentFilter {
        @Override
        public void insertString(FilterBypass bypass, int offset, String text, AttributeSet attributes) throws BadLocationException {
            this.replace(bypass, offset, 0, text, attributes);
        }

        @Override
        public void replace(
                FilterBypass bypass,
                int offset, int length,
                String text, AttributeSet attributes
        ) throws BadLocationException {
            String current = bypass.getDocument().getText(0, bypass.getDocument().getLength());
            String replacement = text == null ? "" : text;
            String candidate = current.substring(0, offset) + replacement + current.substring(offset + length);
            if (InstanceNames.canType(candidate)) {
                bypass.replace(offset, length, replacement, attributes);
            }
        }

        @Override
        public void remove(FilterBypass bypass, int offset, int length) throws BadLocationException {
            replace(bypass, offset, length, "", null);
        }
    }

    private record FieldListener(Runnable callback) implements DocumentListener {
        @Override
        public void insertUpdate(DocumentEvent event) {
                this.callback.run();
            }

        @Override
        public void removeUpdate(DocumentEvent event) {
                this.callback.run();
            }

        @Override
        public void changedUpdate(DocumentEvent event) {
                this.callback.run();
            }
    }
}
