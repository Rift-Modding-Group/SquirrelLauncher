package com.anightdazingzoroark.squirrellauncher;

import com.anightdazingzoroark.squirrellauncher.ui.LauncherFrame;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public final class SquirrelLauncher {
    public static final String NAME = "SquirrelLauncher";
    public static final String VERSION = "1.12.2";

    private SquirrelLauncher() {}

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            }
            catch (Exception ignored) {}
            new LauncherFrame().setVisible(true);
        });
    }
}