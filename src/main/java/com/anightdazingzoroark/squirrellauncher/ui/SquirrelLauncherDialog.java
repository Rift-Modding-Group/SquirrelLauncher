package com.anightdazingzoroark.squirrellauncher.ui;

import com.anightdazingzoroark.squirrellauncher.launcher.InstanceAdditionRequest;
import com.anightdazingzoroark.squirrellauncher.launcher.InstanceNames;
import com.anightdazingzoroark.squirrellauncher.launcher.LauncherService;
import com.anightdazingzoroark.squirrellauncher.minecraft.instance.InstanceType;
import com.anightdazingzoroark.squirrellauncher.minecraft.instance.MinecraftInstance;
import com.anightdazingzoroark.squirrellauncher.ui.settings.AboutSettingsPanel;
import com.anightdazingzoroark.squirrellauncher.ui.settings.AccountSettingsPanel;
import com.anightdazingzoroark.squirrellauncher.ui.settings.GameSettingsPanel;
import com.anightdazingzoroark.squirrellauncher.ui.settings.LauncherSettingsPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.WindowConstants;
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
import java.io.Serial;
import java.net.URL;
import java.nio.file.Path;

/**
 * Shared base and entry points for the launcher's modal dialogs.
 * */
public abstract class SquirrelLauncherDialog<T> extends JDialog {
    @Serial
    private static final long serialVersionUID = 1L;
    @Nullable
    private T result;

    protected SquirrelLauncherDialog(@NotNull JFrame owner, @NotNull String title) {
        super(owner, title, true);
        this.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        this.initializeDialog();
    }

    //----public static stuff to show instance----
    @Nullable
    public static InstanceAdditionRequest showAddInstanceDialog(@NotNull JFrame owner) {
        SquirrelLauncherDialog<InstanceAdditionRequest> dialog = new SquirrelLauncherDialog<>(
                owner, Localization.text("add.title")
        ) {
            private JTextField nameField;
            private JTabbedPane tabs;
            private JLabel loaderLabel;
            private JTextField loaderField;
            private JTextField archiveField;
            private JButton addButton;
            private JButton iconButton;
            private InstanceType selectedType;
            @Nullable
            private Path selectedArchive;
            @Nullable
            private Path selectedIcon;

            @Override
            protected void initializeDialog() {
                this.nameField = new JTextField("", 26);
                this.tabs = new SelectedContentTabbedPane();
                this.loaderLabel = new JLabel(Localization.text("add.label.loader"));
                this.loaderField = new JTextField(22);
                this.archiveField = new JTextField(28);
                this.addButton = new JButton(Localization.text("add.button.add"));
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

                JPanel creationPanel = new JPanel(new GridBagLayout());
                creationPanel.setBorder(BorderFactory.createEmptyBorder(12, 8, 12, 8));
                this.addRow(
                        creationPanel,
                        0,
                        new JLabel(Localization.text("add.label.type")),
                        typePanel
                );
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
                        String loaderVersion = this.loaderField.getText().trim();
                        if (this.selectedType.hasMods && loaderVersion.isEmpty()) return;
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

            @NotNull
            private JToggleButton createTypeButton(@NotNull InstanceType type, @NotNull ButtonGroup group) {
                URL resource = SquirrelLauncherDialog.class.getResource(type.getIconPath());
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
        };
        return dialog.showModal();
    }

    @Nullable
    public static InstanceConversion showConvertInstanceDialog(@NotNull JFrame owner, @NotNull MinecraftInstance instance) {
        SquirrelLauncherDialog<InstanceConversion> dialog = new SquirrelLauncherDialog<>(
                owner, Localization.text("convert.title", instance.name())
        ) {
            private JLabel loaderLabel;
            private JTextField loaderField;
            private JButton convertButton;
            private InstanceType selectedType;

            @Override
            protected void initializeDialog() {
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
                URL resource = SquirrelLauncherDialog.class.getResource(type.getIconPath());
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
                this.convertButton.setEnabled(
                        this.selectedType != null && (!this.selectedType.hasMods || !this.loaderField.getText().isBlank())
                );
            }
        };
        return dialog.showModal();
    }

    public static void showSettingsDialog(@NotNull JFrame owner, @NotNull LauncherService launcherService, @NotNull SettingsTab selectedTab) {
        SquirrelLauncherDialog<Void> dialog = new SquirrelLauncherDialog<>(owner, Localization.text("settings.title")) {
            private JTabbedPane tabs;

            @Override
            protected void initializeDialog() {
                this.tabs = new JTabbedPane();
                this.setLayout(new BorderLayout(0, 10));

                this.tabs.addTab(
                        Localization.text("settings.tab.game"),
                        new GameSettingsPanel(launcherService)
                );
                this.tabs.addTab(
                        Localization.text("settings.tab.launcher"),
                        new LauncherSettingsPanel(launcherService)
                );
                this.tabs.addTab(
                        Localization.text("settings.tab.accounts"),
                        new AccountSettingsPanel(launcherService, busy -> {
                            this.setDefaultCloseOperation(
                                    busy ? WindowConstants.DO_NOTHING_ON_CLOSE : WindowConstants.DISPOSE_ON_CLOSE
                            );
                            this.tabs.setEnabledAt(SettingsTab.GAME.ordinal(), !busy);
                            this.tabs.setEnabledAt(SettingsTab.LAUNCHER.ordinal(), !busy);
                            this.tabs.setEnabledAt(SettingsTab.ABOUT.ordinal(), !busy);
                        })
                );
                this.tabs.addTab(Localization.text("settings.tab.about"), new AboutSettingsPanel());

                this.tabs.setSelectedIndex(selectedTab.ordinal());
                this.tabs.setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 10));
                this.add(this.tabs, BorderLayout.CENTER);

                JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
                actions.setBorder(BorderFactory.createEmptyBorder(0, 12, 12, 12));
                this.add(actions, BorderLayout.SOUTH);

                this.setMinimumSize(new Dimension(680, 520));
                this.resizeToContent();
                this.setLocationRelativeTo(owner);
            }
        };
        dialog.showModal();
    }

    //----abstract methods----
    protected abstract void initializeDialog();

    //----methods common to the dialogs----
    protected final void complete(@NotNull T result) {
        this.result = result;
        this.dispose();
    }

    protected final void resizeToContent() {
        this.pack();
    }

    protected final void addRow(@NotNull JPanel panel, int row, @NotNull JLabel label, @NotNull Component component) {
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

    @Nullable
    private T showModal() {
        this.setVisible(true);
        return this.result;
    }

    public record InstanceConversion(@NotNull InstanceType type, @Nullable String loaderVersion) {}

    public enum SettingsTab {
        GAME,
        LAUNCHER,
        ACCOUNTS,
        ABOUT;
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