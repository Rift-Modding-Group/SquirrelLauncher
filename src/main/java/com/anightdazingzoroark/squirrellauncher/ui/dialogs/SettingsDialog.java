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

public final class SettingsDialog extends AbstractDialog<Void> {
    @NotNull
    private final JTabbedPane tabs;

    public SettingsDialog(@NotNull JFrame owner, @NotNull LauncherService launcherService, @NotNull SettingsTab selectedTab) {
        super(owner, Localization.text("settings.title"));
        this.tabs = new JTabbedPane();
        this.setLayout(new BorderLayout(0, 10));

        this.tabs.addTab(Localization.text("settings.tab.game"), new GameSettingsTab(launcherService));
        this.tabs.addTab(Localization.text("settings.tab.launcher"), new LauncherSettingsTab(launcherService));
        this.tabs.addTab(
                Localization.text("settings.tab.accounts"),
                new AccountSettingsTab(launcherService, busy -> {
                    this.setDefaultCloseOperation(
                            busy ? WindowConstants.DO_NOTHING_ON_CLOSE : WindowConstants.DISPOSE_ON_CLOSE
                    );
                    this.tabs.setEnabledAt(SettingsTab.GAME.ordinal(), !busy);
                    this.tabs.setEnabledAt(SettingsTab.LAUNCHER.ordinal(), !busy);
                    this.tabs.setEnabledAt(SettingsTab.ABOUT.ordinal(), !busy);
                })
        );
        this.tabs.addTab(Localization.text("settings.tab.about"), new AboutSettingsTab(launcherService));

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

    public enum SettingsTab {
        GAME,
        LAUNCHER,
        ACCOUNTS,
        ABOUT;
    }
}
