package com.anightdazingzoroark.squirrellauncher.launcher;

import org.jetbrains.annotations.NotNull;

import java.lang.management.ManagementFactory;

/**
 * Settings that affect game instances
 * */
public record GameSettings(
        boolean fullscreen,
        int windowWidth,
        int windowHeight,
        int allocatedMemoryGigabytes,
        boolean lowMemoryWarning,
        @NotNull LauncherLanguage language
) {
    public static final int MINIMUM_ALLOCATED_MEMORY_GIGABYTES = 1;
    public static final int MAXIMUM_ALLOCATED_MEMORY_GIGABYTES;
    public static final int DEFAULT_ALLOCATED_MEMORY_GIGABYTES;

    static {
        long totalMemoryBytes = Runtime.getRuntime().maxMemory();
        java.lang.management.OperatingSystemMXBean operatingSystem = ManagementFactory.getOperatingSystemMXBean();
        if (operatingSystem instanceof com.sun.management.OperatingSystemMXBean memoryOperatingSystem) {
            totalMemoryBytes = memoryOperatingSystem.getTotalMemorySize();
        }
        long totalMemoryGigabytes = Math.max(
                GameSettings.MINIMUM_ALLOCATED_MEMORY_GIGABYTES,
                totalMemoryBytes / (1024L * 1024L * 1024L)
        );
        MAXIMUM_ALLOCATED_MEMORY_GIGABYTES = (int) Math.min(
                Integer.MAX_VALUE,
                totalMemoryGigabytes
        );
        DEFAULT_ALLOCATED_MEMORY_GIGABYTES = Math.clamp(
                GameSettings.MAXIMUM_ALLOCATED_MEMORY_GIGABYTES / 4,
                GameSettings.MINIMUM_ALLOCATED_MEMORY_GIGABYTES, 4
        );
    }

    public GameSettings {
        if (windowWidth < 320 || windowWidth > 7680) {
            throw new IllegalArgumentException("Game window width must be between 320 and 7680 pixels.");
        }
        if (windowHeight < 240 || windowHeight > 4320) {
            throw new IllegalArgumentException("Game window height must be between 240 and 4320 pixels.");
        }
        if (allocatedMemoryGigabytes < GameSettings.MINIMUM_ALLOCATED_MEMORY_GIGABYTES
                || allocatedMemoryGigabytes > GameSettings.MAXIMUM_ALLOCATED_MEMORY_GIGABYTES) {
            throw new IllegalArgumentException(
                    "Allocated memory must be between 1 and "
                            + GameSettings.MAXIMUM_ALLOCATED_MEMORY_GIGABYTES + " GB."
            );
        }
    }

    @NotNull
    public static GameSettings defaults() {
        return new GameSettings(
                false,
                854,
                480,
                GameSettings.DEFAULT_ALLOCATED_MEMORY_GIGABYTES,
                true,
                LauncherLanguage.systemDefault()
        );
    }
}
