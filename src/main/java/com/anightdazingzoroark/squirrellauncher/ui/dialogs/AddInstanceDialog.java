package com.anightdazingzoroark.squirrellauncher.ui.dialogs;

import com.anightdazingzoroark.squirrellauncher.launcher.InstanceAdditionRequest;
import com.anightdazingzoroark.squirrellauncher.launcher.InstanceNames;
import com.anightdazingzoroark.squirrellauncher.minecraft.instance.InstanceType;
import com.anightdazingzoroark.squirrellauncher.ui.LauncherFrame;
import com.anightdazingzoroark.squirrellauncher.ui.Localization;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.Serial;
import java.net.URL;
import java.nio.file.Path;

public final class AddInstanceDialog extends AbstractDialog<InstanceAdditionRequest> {
    @NotNull
    private final JTextField nameField;
    @NotNull
    private final JTabbedPane tabs;
    @NotNull
    private final JLabel loaderLabel;
    @NotNull
    private final LoaderVersionComboBox loaderVersions;
    @NotNull
    private final JTextField archiveField;
    @NotNull
    private final JButton addButton;
    @NotNull
    private final JButton iconButton;
    @NotNull
    private InstanceType selectedType;
    @Nullable
    private Path selectedArchive;
    @Nullable
    private Path selectedIcon;

    public AddInstanceDialog(@NotNull JFrame owner) {
        super(owner, Localization.text("add.title"));
        this.nameField = new JTextField("", 26);
        this.tabs = new SelectedContentTabbedPane();
        this.loaderLabel = new JLabel(Localization.text("add.label.loader"));
        this.archiveField = new JTextField(28);
        this.addButton = new JButton(Localization.text("add.button.add"));
        this.loaderVersions = new LoaderVersionComboBox(this::updateAddButton, this::resizeToContent);
        this.iconButton = new JButton(Localization.text("add.button.choose_image"));
        this.selectedType = InstanceType.VANILLA;
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
        JToggleButton vanillaButton = this.createTypeButton(InstanceType.VANILLA, typeGroup);
        typePanel.add(vanillaButton);
        typePanel.add(this.createTypeButton(InstanceType.FORGE, typeGroup));
        typePanel.add(this.createTypeButton(InstanceType.CLEANROOM, typeGroup));
        vanillaButton.setSelected(true);

        JLabel typeLabel = new JLabel(Localization.text("add.label.type"));
        Dimension typeLabelSize = typeLabel.getPreferredSize();
        typeLabelSize.width = Math.max(typeLabelSize.width, this.loaderLabel.getPreferredSize().width);
        typeLabel.setPreferredSize(typeLabelSize);
        Dimension typePanelSize = typePanel.getPreferredSize();
        typePanelSize.width = Math.max(typePanelSize.width, this.loaderVersions.getPreferredSize().width);
        typePanel.setPreferredSize(typePanelSize);

        JPanel creationPanel = new JPanel(new GridBagLayout());
        creationPanel.setBorder(BorderFactory.createEmptyBorder(12, 8, 12, 8));
        this.addRow(creationPanel, 0, typeLabel, typePanel);
        this.addRow(
                creationPanel,
                1,
                this.loaderLabel,
                this.loaderVersions,
                this.loaderVersions.dropdownPreferredHeight()
        );
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
            chooser.setFileFilter(new FileNameExtensionFilter(
                    Localization.text("file_filter.mmc_archives"),
                    "zip"
            ));
            if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

            this.selectedArchive = chooser.getSelectedFile().toPath();
            this.archiveField.setText(this.selectedArchive.toString());
            if (this.nameField.getText().isBlank()) {
                this.nameField.setText(
                        this.selectedArchive.getFileName().toString().replaceFirst("(?i)\\.zip$", "")
                );
            }
            this.updateAddButton();
        });

        JPanel archiveControls = new JPanel(new BorderLayout(8, 0));
        archiveControls.add(this.archiveField, BorderLayout.CENTER);
        archiveControls.add(browseButton, BorderLayout.EAST);
        this.addRow(
                importPanel,
                1,
                new JLabel(Localization.text("add.label.archive")),
                archiveControls
        );
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
                this.complete(new InstanceAdditionRequest(
                        name,
                        null,
                        null,
                        this.selectedArchive,
                        this.selectedIcon
                ));
            }
            else {
                String loaderVersion = this.loaderVersions.selectedVersion();
                if (this.selectedType.hasMods && loaderVersion == null) return;
                this.complete(new InstanceAdditionRequest(
                        name,
                        this.selectedType,
                        this.selectedType.hasMods ? loaderVersion : null,
                        null,
                        this.selectedIcon
                ));
            }
        });
        this.getRootPane().setDefaultButton(this.addButton);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));
        actions.add(cancelButton);
        actions.add(this.addButton);
        this.add(actions, BorderLayout.SOUTH);

        this.nameField.getDocument().addDocumentListener(new FieldListener(this::updateAddButton));
        this.tabs.addChangeListener(event -> {
            this.updateAddButton();
            this.resizeToContent();
        });
        this.updateLoaderField();
        this.updateAddButton();
        this.resizeToContent();
        this.setLocationRelativeTo(owner);
    }

    @NotNull
    public String getTitle() {
        return Localization.text("add.title");
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
        this.loaderVersions.setVisible(hasLoader);
        this.loaderVersions.load(this.selectedType, null);
    }

    private void updateAddButton() {
        boolean validName = InstanceNames.isValid(this.nameField.getText().trim());
        boolean validSelection = this.tabs.getSelectedIndex() == 1
                ? this.selectedArchive != null
                : !this.selectedType.hasMods || this.loaderVersions.selectedVersion() != null;
        this.addButton.setEnabled(validName && validSelection);
    }

    //exclusive JTabbedPane override that allows for autoresizin pane size
    private static final class SelectedContentTabbedPane extends JTabbedPane {
        @Serial
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
