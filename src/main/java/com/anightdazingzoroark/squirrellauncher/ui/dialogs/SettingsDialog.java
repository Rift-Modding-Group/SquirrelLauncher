package com.anightdazingzoroark.squirrellauncher.ui.dialogs;

import com.anightdazingzoroark.squirrellauncher.launcher.LauncherService;
import com.anightdazingzoroark.squirrellauncher.ui.Localization;
import com.anightdazingzoroark.squirrellauncher.ui.dialogs.settingsDialogTabs.AboutSettingsTab;
import com.anightdazingzoroark.squirrellauncher.ui.dialogs.settingsDialogTabs.AccountSettingsTab;
import com.anightdazingzoroark.squirrellauncher.ui.dialogs.settingsDialogTabs.GameSettingsTab;
import com.anightdazingzoroark.squirrellauncher.ui.dialogs.settingsDialogTabs.LauncherSettingsTab;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public final class SettingsDialog extends AbstractDialog<Void> {
    @NotNull
    private final JTabbedPane tabs;
    @NotNull
    private final GameSettingsTab gameSettingsTab;
    private boolean accountOperationInProgress;

    public SettingsDialog(@NotNull JFrame owner, @NotNull LauncherService launcherService, @NotNull SettingsTab selectedTab) {
        super(owner, Localization.text("settings.title"));
        this.tabs = new JTabbedPane();
        this.gameSettingsTab = new GameSettingsTab(launcherService);
        this.setLayout(new BorderLayout(0, 10));

        this.tabs.addTab(Localization.text("settings.tab.game"), this.gameSettingsTab);
        this.tabs.addTab(Localization.text("settings.tab.launcher"), new LauncherSettingsTab(launcherService));
        this.tabs.addTab(
                Localization.text("settings.tab.accounts"),
                new AccountSettingsTab(launcherService, busy -> {
                    this.accountOperationInProgress = busy;
                    this.tabs.setEnabledAt(SettingsTab.GAME.ordinal(), !busy);
                    this.tabs.setEnabledAt(SettingsTab.LAUNCHER.ordinal(), !busy);
                    this.tabs.setEnabledAt(SettingsTab.ABOUT.ordinal(), !busy);
                })
        );
        this.tabs.addTab(Localization.text("settings.tab.about"), new AboutSettingsTab(launcherService));

        this.tabs.setSelectedIndex(selectedTab.ordinal());
        this.tabs.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        this.add(this.tabs, BorderLayout.CENTER);

        this.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        this.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(@NotNull WindowEvent event) {
                SettingsDialog.this.attemptClose();
            }
        });
        this.getRootPane().registerKeyboardAction(
                event -> this.attemptClose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW
        );

        this.setMinimumSize(new Dimension(680, 520));
        this.resizeToContent();
        this.setSize(this.getMinimumSize().width, this.getHeight());
        this.setResizable(false);
        this.setLocationRelativeTo(owner);
    }

    private void attemptClose() {
        if (this.accountOperationInProgress || !this.gameSettingsTab.confirmDiscardUnsavedChanges()) return;
        this.dispose();
    }

    public enum SettingsTab {
        GAME,
        LAUNCHER,
        ACCOUNTS,
        ABOUT;
    }
}
