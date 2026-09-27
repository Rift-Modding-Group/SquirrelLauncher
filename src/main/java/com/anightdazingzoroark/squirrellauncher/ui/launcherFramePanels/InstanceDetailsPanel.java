package com.anightdazingzoroark.squirrellauncher.ui.launcherFramePanels;

import com.anightdazingzoroark.squirrellauncher.SquirrelLauncher;
import com.anightdazingzoroark.squirrellauncher.launcher.GameSettings;
import com.anightdazingzoroark.squirrellauncher.minecraft.instance.MinecraftInstance;
import com.anightdazingzoroark.squirrellauncher.ui.InstanceIconProvider;
import com.anightdazingzoroark.squirrellauncher.ui.LauncherActions;
import com.anightdazingzoroark.squirrellauncher.ui.LauncherFrame;
import com.anightdazingzoroark.squirrellauncher.ui.Localization;
import com.anightdazingzoroark.squirrellauncher.ui.launcherFramePanels.instanceDetailsTabs.InstanceActivityTab;
import com.anightdazingzoroark.squirrellauncher.ui.launcherFramePanels.instanceDetailsTabs.InstanceSettingsTab;
import com.anightdazingzoroark.squirrellauncher.ui.launcherFramePanels.instanceDetailsTabs.InstanceModsTab;
import com.anightdazingzoroark.squirrellauncher.ui.launcherFramePanels.instanceDetailsTabs.InstanceNotesTab;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.OverlayLayout;
import javax.swing.SwingUtilities;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public final class InstanceDetailsPanel extends JPanel {
    @NotNull
    private static final String EMPTY_INSTANCE_CARD = "empty";
    @NotNull
    private static final String INSTANCE_DETAILS_CARD = "details";

    @NotNull
    private final CardLayout contentLayout = new CardLayout();
    @NotNull
    private final JLabel emptyInstanceMessage = new JLabel(Localization.text("main.instances.empty"));
    @NotNull
    private final JLabel instanceNameLabel = new JLabel(Localization.text("main.instance.select"));
    @NotNull
    private final JLabel instanceIconLabel = new JLabel();
    @NotNull
    private final JLabel instanceNamePencilLabel = new JLabel(DetailsIcon.PENCIL);
    @NotNull
    private final JLabel instanceIconPencilLabel = new JLabel(DetailsIcon.PENCIL);
    @NotNull
    private final JLabel instanceTypePencilLabel = new JLabel(DetailsIcon.PENCIL);
    @NotNull
    private final JLabel loaderVersionPencilLabel = new JLabel(DetailsIcon.PENCIL);
    @NotNull
    private final JPanel instanceNameEditorPanel = new JPanel(new BorderLayout(6, 0));
    @NotNull
    private final JPanel instanceIconEditorPanel = new JPanel();
    @NotNull
    private final JPanel instanceTypeEditorPanel = new JPanel(new BorderLayout(6, 0));
    @NotNull
    private final JPanel loaderVersionEditorPanel = new JPanel(new BorderLayout(6, 0));
    @NotNull
    private final JLabel instanceTypeLabel = new JLabel(Localization.text("main.instance.type", "—"));
    @NotNull
    private final JLabel loaderVersionLabel = new JLabel(Localization.text("main.instance.version", "—"));
    @NotNull
    private final JLabel instancePlaytimeLabel = new JLabel(LauncherFrame.playtimeText(0));
    @NotNull
    private final JTabbedPane tabs = new JTabbedPane();
    @NotNull
    private final InstanceActivityTab activityTab = new InstanceActivityTab();
    @NotNull
    private final InstanceModsTab modsTab;
    @NotNull
    private final InstanceSettingsTab instanceSettingsTab;
    @NotNull
    private final InstanceNotesTab notesTab;
    private boolean selectedInstanceHasMods;

    public InstanceDetailsPanel(@NotNull LauncherActions launcherActions) {
        super();
        this.setLayout(this.contentLayout);
        this.modsTab = new InstanceModsTab(launcherActions);
        this.instanceSettingsTab = new InstanceSettingsTab(launcherActions);
        this.notesTab = new InstanceNotesTab(launcherActions);

        JPanel emptyPanel = new JPanel(new GridBagLayout());
        this.emptyInstanceMessage.setFont(this.emptyInstanceMessage.getFont().deriveFont(Font.BOLD, 22f));
        emptyPanel.add(this.emptyInstanceMessage);
        this.add(emptyPanel, EMPTY_INSTANCE_CARD);

        JPanel detailsPanel = new JPanel(new BorderLayout(0, 10));
        detailsPanel.setBorder(BorderFactory.createEmptyBorder(14, 16, 10, 16));
        detailsPanel.add(this.createInstanceHeader(launcherActions), BorderLayout.NORTH);
        this.tabs.addTab(Localization.text("main.tab.activity"), this.activityTab);
        this.tabs.addTab(Localization.text("main.tab.mods"), this.modsTab);
        this.tabs.addTab(Localization.text("main.tab.settings"), this.instanceSettingsTab);
        this.tabs.addTab(Localization.text("main.tab.notes"), this.notesTab);
        detailsPanel.add(this.tabs, BorderLayout.CENTER);
        this.add(detailsPanel, INSTANCE_DETAILS_CARD);
        this.contentLayout.show(this, EMPTY_INSTANCE_CARD);
    }

    @NotNull
    public InstanceActivityTab activityTab() {
        return this.activityTab;
    }

    @NotNull
    public InstanceModsTab modsTab() {
        return this.modsTab;
    }

    @NotNull
    public InstanceNotesTab notesTab() {
        return this.notesTab;
    }

    @NotNull
    public InstanceSettingsTab instanceSettingsTab() {
        return this.instanceSettingsTab;
    }

    public void showEmpty(boolean noInstances) {
        this.emptyInstanceMessage.setText(Localization.text(
                noInstances ? "main.instances.empty" : "main.instances.no_matches"
        ));
        this.activityTab.clearDisplayedInstance();
        this.modsTab.setMods(java.util.List.of());
        this.instanceSettingsTab.clearDisplayedInstance();
        this.notesTab.clearDisplayedInstance();
        this.selectedInstanceHasMods = false;
        this.contentLayout.show(this, EMPTY_INSTANCE_CARD);
    }

    public void showInstance(@NotNull MinecraftInstance instance, @NotNull GameSettings launcherSettings) {
        this.contentLayout.show(this, INSTANCE_DETAILS_CARD);
        this.activityTab.showInstance(instance.id());
        this.showActivityTab();
        this.instanceNameLabel.setText(instance.name());
        this.instanceIconLabel.setIcon(InstanceIconProvider.INSTANCE.iconFor(instance));
        this.instanceTypeLabel.setText(Localization.text(
                "main.instance.type",
                LauncherFrame.displayName(instance.type())
        ));
        this.loaderVersionLabel.setText(instance.loaderVersion() == null
                ? "Minecraft " + SquirrelLauncher.VERSION
                : Localization.text("main.instance.version", instance.loaderVersion()));
        this.selectedInstanceHasMods = instance.type().hasMods;
        this.instancePlaytimeLabel.setText(LauncherFrame.playtimeText(instance.totalTimePlayedSeconds()));
        this.instanceSettingsTab.showInstance(instance, launcherSettings);
        this.notesTab.showInstance(instance);
        this.setModsTabVisible(instance.type().hasMods);
    }

    public void showActivityTab() {
        this.tabs.setSelectedComponent(this.activityTab);
    }

    public void updateControlState(boolean available, boolean hasInstance, boolean instanceRunning) {
        this.setEditorEnabled(
                this.instanceNameEditorPanel,
                this.instanceNamePencilLabel,
                available && hasInstance && !instanceRunning
        );
        this.setEditorEnabled(
                this.instanceIconEditorPanel,
                this.instanceIconPencilLabel,
                available && hasInstance
        );
        this.setEditorEnabled(
                this.instanceTypeEditorPanel,
                this.instanceTypePencilLabel,
                available && hasInstance && !instanceRunning
        );
        this.setEditorEnabled(
                this.loaderVersionEditorPanel,
                this.loaderVersionPencilLabel,
                available && hasInstance && this.selectedInstanceHasMods && !instanceRunning
        );
        this.modsTab.updateControlState(available, hasInstance && this.tabs.indexOfComponent(this.modsTab) >= 0);
        this.instanceSettingsTab.updateControlState(available && hasInstance && !instanceRunning);
        this.notesTab.updateControlState(available && hasInstance);
    }

    private void setEditorEnabled(@NotNull JPanel editorPanel, @NotNull JLabel pencilLabel, boolean enabled) {
        editorPanel.setEnabled(enabled);
        if (enabled) return;
        editorPanel.setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));
        pencilLabel.setIcon(null);
    }

    @NotNull
    private JPanel createInstanceHeader(@NotNull LauncherActions launcherActions) {
        JPanel panel = new JPanel(new GridBagLayout());

        this.instanceNameLabel.setFont(this.instanceNameLabel.getFont().deriveFont(Font.BOLD, 22f));
        this.configurePencilLabel(this.instanceNamePencilLabel);
        this.configurePencilLabel(this.instanceIconPencilLabel);
        this.configurePencilLabel(this.instanceTypePencilLabel);
        this.configurePencilLabel(this.loaderVersionPencilLabel);
        this.instanceNameEditorPanel.setOpaque(false);
        this.instanceIconEditorPanel.setOpaque(false);
        this.instanceTypeEditorPanel.setOpaque(false);
        this.loaderVersionEditorPanel.setOpaque(false);
        this.instanceNameEditorPanel.setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));
        this.instanceIconEditorPanel.setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));
        this.instanceTypeEditorPanel.setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));
        this.loaderVersionEditorPanel.setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));
        this.configureEditorTooltip(
                this.instanceNameEditorPanel,
                "main.tooltip.rename_instance",
                this.instanceNameLabel,
                this.instanceNamePencilLabel
        );
        this.configureEditorTooltip(
                this.instanceIconEditorPanel,
                "main.tooltip.manage_instance_icon",
                this.instanceIconLabel,
                this.instanceIconPencilLabel
        );
        this.configureEditorTooltip(
                this.instanceTypeEditorPanel,
                "main.tooltip.convert_instance",
                this.instanceTypeLabel,
                this.instanceTypePencilLabel
        );
        this.configureEditorTooltip(
                this.loaderVersionEditorPanel,
                "main.tooltip.change_loader_version",
                this.loaderVersionLabel,
                this.loaderVersionPencilLabel
        );
        this.instanceNameEditorPanel.add(this.instanceNameLabel, BorderLayout.CENTER);
        this.instanceNameEditorPanel.add(this.instanceNamePencilLabel, BorderLayout.EAST);
        this.instanceTypeEditorPanel.add(this.instanceTypeLabel, BorderLayout.CENTER);
        this.instanceTypeEditorPanel.add(this.instanceTypePencilLabel, BorderLayout.EAST);
        this.loaderVersionEditorPanel.add(this.loaderVersionLabel, BorderLayout.CENTER);
        this.loaderVersionEditorPanel.add(this.loaderVersionPencilLabel, BorderLayout.EAST);
        this.instanceIconEditorPanel.setLayout(new OverlayLayout(this.instanceIconEditorPanel));
        this.instanceIconLabel.setAlignmentX(0.5f);
        this.instanceIconLabel.setAlignmentY(0.5f);
        this.instanceIconPencilLabel.setAlignmentX(0.0f);
        this.instanceIconPencilLabel.setAlignmentY(1.0f);
        this.instanceIconEditorPanel.add(this.instanceIconLabel);
        this.instanceIconEditorPanel.add(this.instanceIconPencilLabel);
        this.instanceIconEditorPanel.setComponentZOrder(this.instanceIconPencilLabel, 0);

        new EditHoverListener(
                this.instanceNameEditorPanel,
                this.instanceNamePencilLabel,
                launcherActions::renameRequested,
                this.instanceNameLabel
        );
        new EditHoverListener(
                this.instanceIconEditorPanel,
                this.instanceIconPencilLabel,
                () -> launcherActions.manageIconRequested(this.instanceIconEditorPanel),
                this.instanceIconLabel
        );
        new EditHoverListener(
                this.instanceTypeEditorPanel,
                this.instanceTypePencilLabel,
                launcherActions::convertRequested,
                this.instanceTypeLabel
        );
        new EditHoverListener(
                this.loaderVersionEditorPanel,
                this.loaderVersionPencilLabel,
                launcherActions::changeLoaderVersionRequested,
                this.loaderVersionLabel
        );

        GridBagConstraints icon = new GridBagConstraints();
        icon.gridx = 0;
        icon.gridy = 0;
        icon.gridheight = 2;
        icon.anchor = GridBagConstraints.FIRST_LINE_START;
        icon.insets = new Insets(0, 0, 0, 12);
        panel.add(this.instanceIconEditorPanel, icon);

        GridBagConstraints name = new GridBagConstraints();
        name.gridx = 1;
        name.gridy = 0;
        name.gridwidth = 3;
        name.weightx = 1;
        name.anchor = GridBagConstraints.LINE_START;
        name.insets = new Insets(0, 0, 5, 0);
        panel.add(this.instanceNameEditorPanel, name);

        GridBagConstraints type = new GridBagConstraints();
        type.gridx = 1;
        type.gridy = 1;
        type.anchor = GridBagConstraints.LINE_START;
        type.insets = new Insets(0, 0, 0, 18);
        panel.add(this.instanceTypeEditorPanel, type);

        GridBagConstraints loader = new GridBagConstraints();
        loader.gridx = 2;
        loader.gridy = 1;
        loader.weightx = 1;
        loader.anchor = GridBagConstraints.LINE_START;
        panel.add(this.loaderVersionEditorPanel, loader);

        GridBagConstraints playtime = new GridBagConstraints();
        playtime.gridx = 3;
        playtime.gridy = 1;
        playtime.anchor = GridBagConstraints.LINE_END;
        panel.add(this.instancePlaytimeLabel, playtime);
        return panel;
    }

    private void configurePencilLabel(@NotNull JLabel pencilLabel) {
        pencilLabel.setIcon(null);
        pencilLabel.setPreferredSize(new Dimension(
                DetailsIcon.PENCIL.getIconWidth(),
                DetailsIcon.PENCIL.getIconHeight()
        ));
    }

    private void configureEditorTooltip(
            @NotNull JPanel editorPanel,
            @NotNull String localizationKey,
            @NotNull JComponent... interactiveComponents
    ) {
        String tooltip = Localization.text(localizationKey);
        editorPanel.setToolTipText(tooltip);
        editorPanel.getAccessibleContext().setAccessibleName(tooltip);
        for (JComponent interactiveComponent : interactiveComponents) {
            interactiveComponent.setToolTipText(tooltip);
        }
    }

    private void setModsTabVisible(boolean visible) {
        boolean currentlyVisible = this.tabs.indexOfComponent(this.modsTab) >= 0;
        if (visible && !currentlyVisible) {
            this.tabs.insertTab(Localization.text("main.tab.mods"), null, this.modsTab, null, 1);
        }
        else if (!visible && currentlyVisible) {
            this.tabs.remove(this.modsTab);
        }
    }

    private enum DetailsIcon implements Icon {
        PENCIL;

        private static final int ICON_SIZE = 16;

        @Override
        public void paintIcon(@Nullable Component component, @NotNull Graphics graphics, int x, int y) {
            if (!(graphics instanceof Graphics2D)) return;
            Graphics2D drawing = (Graphics2D) graphics.create();
            try {
                drawing.setColor(component == null
                        ? Color.DARK_GRAY
                        : component.isEnabled() ? component.getForeground() : Color.GRAY);
                drawing.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                drawing.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                drawing.translate(x, y);
                drawing.drawLine(3, 12, 11, 4);
                drawing.drawLine(5, 14, 13, 6);
                drawing.drawLine(3, 12, 2, 15);
                drawing.drawLine(2, 15, 5, 14);
                drawing.drawLine(11, 4, 13, 6);
            }
            finally {
                drawing.dispose();
            }
        }

        @Override
        public int getIconWidth() {
            return ICON_SIZE;
        }

        @Override
        public int getIconHeight() {
            return ICON_SIZE;
        }
    }

    private static final class EditHoverListener extends MouseAdapter {
        @NotNull
        private final JPanel targetPanel;
        @NotNull
        private final JLabel pencilLabel;
        @NotNull
        private final Runnable editAction;

        private EditHoverListener(
                @NotNull JPanel targetPanel,
                @NotNull JLabel pencilLabel,
                @NotNull Runnable editAction,
                @NotNull Component... interactiveComponents
        ) {
            this.targetPanel = targetPanel;
            this.pencilLabel = pencilLabel;
            this.editAction = editAction;
            this.targetPanel.addMouseListener(this);
            this.pencilLabel.addMouseListener(this);
            for (Component interactiveComponent : interactiveComponents) interactiveComponent.addMouseListener(this);
        }

        @Override
        public void mouseEntered(@NotNull MouseEvent event) {
            this.setHovered(true);
        }

        @Override
        public void mouseExited(@NotNull MouseEvent event) {
            Point point = SwingUtilities.convertPoint(
                    (Component) event.getSource(),
                    event.getPoint(),
                    this.targetPanel
            );
            if (!this.targetPanel.contains(point)) this.setHovered(false);
        }

        @Override
        public void mouseClicked(@NotNull MouseEvent event) {
            if (this.targetPanel.isEnabled() && SwingUtilities.isLeftMouseButton(event)) this.editAction.run();
        }

        private void setHovered(boolean hovered) {
            boolean showEditState = hovered && this.targetPanel.isEnabled();
            this.targetPanel.setBorder(showEditState
                    ? BorderFactory.createLineBorder(this.targetPanel.getForeground())
                    : BorderFactory.createEmptyBorder(1, 1, 1, 1));
            this.pencilLabel.setIcon(showEditState ? DetailsIcon.PENCIL : null);
        }
    }
}
