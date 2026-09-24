package com.anightdazingzoroark.squirrellauncher.ui;

import com.anightdazingzoroark.squirrellauncher.launcher.InstanceAdditionRequest;
import com.anightdazingzoroark.squirrellauncher.launcher.InstanceNames;
import com.anightdazingzoroark.squirrellauncher.minecraft.instance.InstanceType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.net.URL;
import java.nio.file.Path;

public final class AddInstanceDialog extends JDialog {
    @NotNull
    private final JTextField nameField = new JTextField("", 26);
    @NotNull
    private final JTabbedPane tabs = new SelectedContentTabbedPane();
    @NotNull
    private final JLabel loaderLabel = new JLabel(Localization.text("add.label.loader"));
    @NotNull
    private final JTextField loaderField = new JTextField(22);
    @NotNull
    private final JTextField archiveField = new JTextField(28);
    @NotNull
    private final JButton addButton = new JButton(Localization.text("add.button.add"));
    @NotNull
    private final JButton iconButton = new JButton(Localization.text("add.button.choose_image"));
    @Nullable
    private InstanceAdditionRequest result;
    @NotNull
    private InstanceType selectedType = InstanceType.VANILLA;
    @Nullable
    private Path selectedArchive;
    @Nullable
    private Path selectedIcon;

    public AddInstanceDialog(@NotNull JFrame owner) {
        super(owner, Localization.text("add.title"), true);
        this.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        this.setLayout(new BorderLayout(0, 12));

        JPanel namePanel = new JPanel(new GridBagLayout());
        namePanel.setBorder(BorderFactory.createEmptyBorder(16, 16, 0, 16));
        this.addRow(namePanel, 0, new JLabel(Localization.text("add.label.name")), this.nameField);
        JButton clearIconButton = new JButton(Localization.text("main.menu.reset_icon"));
        clearIconButton.setEnabled(false);
        this.iconButton.addActionListener(event -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle(Localization.text("add.dialog.choose_icon"));
            chooser.setFileFilter(new FileNameExtensionFilter(
                    Localization.text("file_filter.images"),
                    "png", "jpg", "jpeg", "gif", "bmp"
            ));
            if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
            this.selectedIcon = chooser.getSelectedFile().toPath();
            this.iconButton.setText(this.selectedIcon.getFileName().toString());
            clearIconButton.setEnabled(true);
            this.resizeToContent();
        });
        clearIconButton.addActionListener(event -> {
            this.selectedIcon = null;
            this.iconButton.setText(Localization.text("add.button.choose_image"));
            clearIconButton.setEnabled(false);
            this.resizeToContent();
        });
        JPanel iconControls = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        iconControls.add(this.iconButton);
        iconControls.add(clearIconButton);
        this.addRow(namePanel, 1, new JLabel(Localization.text("add.label.icon")), iconControls);
        this.add(namePanel, BorderLayout.NORTH);

        ButtonGroup typeGroup = new ButtonGroup();
        JPanel typePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        JToggleButton vanillaButton = this.createTypeButton(
                LauncherFrame.displayName(InstanceType.VANILLA),
                InstanceType.VANILLA,
                InstanceType.VANILLA.getIconPath(),
                typeGroup
        );
        typePanel.add(vanillaButton);
        typePanel.add(this.createTypeButton(
                LauncherFrame.displayName(InstanceType.FORGE),
                InstanceType.FORGE,
                InstanceType.FORGE.getIconPath(),
                typeGroup
        ));
        typePanel.add(this.createTypeButton(
                LauncherFrame.displayName(InstanceType.CLEANROOM),
                InstanceType.CLEANROOM,
                InstanceType.CLEANROOM.getIconPath(),
                typeGroup
        ));
        vanillaButton.setSelected(true);

        JPanel creationPanel = new JPanel(new GridBagLayout());
        creationPanel.setBorder(BorderFactory.createEmptyBorder(12, 8, 12, 8));
        this.addRow(creationPanel, 0, new JLabel(Localization.text("add.label.type")), typePanel);
        this.addRow(creationPanel, 1, this.loaderLabel, this.loaderField);
        this.tabs.addTab(Localization.text("add.tab.create"), creationPanel);

        JPanel importPanel = new JPanel(new GridBagLayout());
        importPanel.setBorder(BorderFactory.createEmptyBorder(14, 8, 14, 8));

        GridBagConstraints instructions = new GridBagConstraints();
        instructions.gridx = 0;
        instructions.gridy = 0;
        instructions.gridwidth = 2;
        instructions.weightx = 1;
        instructions.anchor = GridBagConstraints.LINE_START;
        instructions.insets = new Insets(0, 0, 14, 0);
        importPanel.add(new JLabel(Localization.text("add.import.instructions")), instructions);

        this.archiveField.setEditable(false);
        JButton browseButton = new JButton(Localization.text("add.button.browse"));
        browseButton.addActionListener(event -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle(Localization.text("add.dialog.import"));
            chooser.setFileFilter(new FileNameExtensionFilter(Localization.text("file_filter.mmc_archives"), "zip"));
            if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

            this.selectedArchive = chooser.getSelectedFile().toPath();
            this.archiveField.setText(this.selectedArchive.toString());
            if (this.nameField.getText().isBlank()) {
                this.nameField.setText(this.selectedArchive.getFileName().toString().replaceFirst("(?i)\\.zip$", ""));
            }
            this.updateAddButton();
        });

        JPanel archiveControls = new JPanel(new BorderLayout(8, 0));
        archiveControls.add(this.archiveField, BorderLayout.CENTER);
        archiveControls.add(browseButton, BorderLayout.EAST);
        this.addRow(importPanel, 1, new JLabel(Localization.text("add.label.archive")), archiveControls);
        this.tabs.addTab(Localization.text("add.tab.import"), importPanel);
        this.tabs.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));
        this.add(this.tabs, BorderLayout.CENTER);

        JButton cancelButton = new JButton(Localization.text("add.button.cancel"));
        cancelButton.addActionListener(event -> this.dispose());
        this.addButton.addActionListener(event -> {
            String name = this.nameField.getText().trim();
            if (!InstanceNames.isValid(name)) return;

            if (this.tabs.getSelectedIndex() == 1) {
                if (this.selectedArchive == null) return;
                this.result = new InstanceAdditionRequest(name, null, null, this.selectedArchive, this.selectedIcon);
            }
            else {
                String loaderVersion = this.loaderField.getText().trim();
                if (this.selectedType.hasMods && loaderVersion.isEmpty()) return;
                this.result = new InstanceAdditionRequest(
                        name,
                        this.selectedType,
                        this.selectedType.hasMods ? loaderVersion : null,
                        null,
                        this.selectedIcon
                );
            }
            this.dispose();
        });
        this.getRootPane().setDefaultButton(this.addButton);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));
        actions.add(cancelButton);
        actions.add(this.addButton);
        this.add(actions, BorderLayout.SOUTH);

        this.nameField.getDocument().addDocumentListener(new FieldListener(this::updateAddButton));
        this.loaderField.getDocument().addDocumentListener(new FieldListener(this::updateAddButton));
        this.tabs.addChangeListener(event -> {
            this.updateAddButton();
            this.resizeToContent();
        });
        this.updateLoaderField();
        this.updateAddButton();
        this.resizeToContent();
        this.setResizable(false);
        this.setLocationRelativeTo(owner);
    }

    @Nullable
    public InstanceAdditionRequest result() {
        return this.result;
    }

    @NotNull
    private JToggleButton createTypeButton(
            @NotNull String label,
            @NotNull InstanceType type,
            @NotNull String iconPath,
            @NotNull ButtonGroup group
    ) {
        URL resource = AddInstanceDialog.class.getResource(iconPath);
        if (resource == null) throw new IllegalStateException("Missing instance icon: " + iconPath);

        JToggleButton button = new JToggleButton(label, new ImageIcon(resource));
        button.setHorizontalTextPosition(JToggleButton.CENTER);
        button.setVerticalTextPosition(JToggleButton.BOTTOM);
        button.setIconTextGap(8);
        button.setToolTipText(Localization.text("add.tooltip.create", label));
        button.addActionListener(event -> {
            this.selectedType = type;
            this.updateLoaderField();
        });
        group.add(button);
        return button;
    }

    private void updateLoaderField() {
        boolean hasLoader = this.selectedType.hasMods;
        this.loaderLabel.setVisible(hasLoader);
        this.loaderField.setVisible(hasLoader);
        if (this.selectedType == InstanceType.FORGE) this.loaderField.setText("14.23.5.2859");
        else if (this.selectedType == InstanceType.CLEANROOM) this.loaderField.setText("0.6.13-alpha");
        else this.loaderField.setText("");
        this.updateAddButton();
        this.resizeToContent();
    }

    private void updateAddButton() {
        boolean validName = InstanceNames.isValid(this.nameField.getText().trim());
        boolean validSelection = this.tabs.getSelectedIndex() == 1
                ? this.selectedArchive != null
                : !this.selectedType.hasMods || !this.loaderField.getText().isBlank();
        this.addButton.setEnabled(validName && validSelection);
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

    private static final class SelectedContentTabbedPane extends JTabbedPane {
        private static final long serialVersionUID = 1L;

        @Override
        @NotNull
        public Dimension getPreferredSize() {
            Dimension preferredSize = super.getPreferredSize();
            Component selectedComponent = this.getSelectedComponent();
            if (selectedComponent == null) return preferredSize;

            int maximumContentHeight = 0;
            for (int index = 0; index < this.getTabCount(); index++) {
                maximumContentHeight = Math.max(
                        maximumContentHeight,
                        this.getComponentAt(index).getPreferredSize().height
                );
            }
            int selectedContentHeight = selectedComponent.getPreferredSize().height;
            return new Dimension(
                    preferredSize.width,
                    preferredSize.height - maximumContentHeight + selectedContentHeight
            );
        }
    }
}
