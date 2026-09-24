package com.anightdazingzoroark.squirrellauncher.ui.settings;

import com.anightdazingzoroark.squirrellauncher.launcher.LauncherService;
import com.anightdazingzoroark.squirrellauncher.ui.Localization;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;

public final class SettingsDialog extends JDialog {
    @NotNull
    private final JTabbedPane tabs = new JTabbedPane();

    public SettingsDialog(@NotNull JFrame owner, @NotNull LauncherService launcherService, @NotNull Tab selectedTab) {
        super(owner, Localization.text("settings.title"), true);
        this.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        this.setLayout(new BorderLayout(0, 10));

        this.tabs.addTab(Localization.text("settings.tab.game"), new GameSettingsPanel(launcherService));
        this.tabs.addTab(Localization.text("settings.tab.launcher"), new LauncherSettingsPanel(launcherService));
        this.tabs.addTab(Localization.text("settings.tab.accounts"), new AccountSettingsPanel(launcherService, busy -> {
            this.setDefaultCloseOperation(busy ? WindowConstants.DO_NOTHING_ON_CLOSE : WindowConstants.DISPOSE_ON_CLOSE);
            this.tabs.setEnabledAt(Tab.GAME.ordinal(), !busy);
            this.tabs.setEnabledAt(Tab.LAUNCHER.ordinal(), !busy);
            this.tabs.setEnabledAt(Tab.ABOUT.ordinal(), !busy);
        }));
        this.tabs.addTab(Localization.text("settings.tab.about"), new AboutSettingsPanel());

        this.tabs.setSelectedIndex(selectedTab.ordinal());
        this.tabs.setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 10));
        this.add(this.tabs, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        actions.setBorder(BorderFactory.createEmptyBorder(0, 12, 12, 12));
        this.add(actions, BorderLayout.SOUTH);

        this.setMinimumSize(new Dimension(680, 520));
        this.pack();
        this.setLocationRelativeTo(owner);
    }

    public enum Tab {
        GAME,
        LAUNCHER,
        ACCOUNTS,
        ABOUT;
    }
}
