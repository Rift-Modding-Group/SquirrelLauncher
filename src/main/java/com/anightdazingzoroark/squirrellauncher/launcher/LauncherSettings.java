package com.anightdazingzoroark.squirrellauncher.launcher;

import org.jetbrains.annotations.NotNull;

/** Settings that affect how Minecraft starts. */
public record LauncherSettings(boolean fullscreen, int windowWidth, int windowHeight) {
    public LauncherSettings {
        if (windowWidth < 320 || windowWidth > 7680) {
            throw new IllegalArgumentException("Game window width must be between 320 and 7680 pixels.");
        }
        if (windowHeight < 240 || windowHeight > 4320) {
            throw new IllegalArgumentException("Game window height must be between 240 and 4320 pixels.");
        }
    }

    @NotNull
    public static LauncherSettings defaults() {
        return new LauncherSettings(false, 1280, 720);
    }
}
