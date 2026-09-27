package com.anightdazingzoroark.squirrellauncher.launcher;

import org.jetbrains.annotations.NotNull;

import java.lang.management.ManagementFactory;

/** User-configurable launcher settings. */
public record LauncherSettings(
        boolean fullscreen,
        int windowWidth,
        int windowHeight,
        int allocatedMemoryGigabytes,
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
                LauncherSettings.MINIMUM_ALLOCATED_MEMORY_GIGABYTES,
                totalMemoryBytes / (1024L * 1024L * 1024L)
        );
        MAXIMUM_ALLOCATED_MEMORY_GIGABYTES = (int) Math.min(
                Integer.MAX_VALUE,
                totalMemoryGigabytes
        );
        DEFAULT_ALLOCATED_MEMORY_GIGABYTES = Math.max(
                LauncherSettings.MINIMUM_ALLOCATED_MEMORY_GIGABYTES,
                Math.min(4, LauncherSettings.MAXIMUM_ALLOCATED_MEMORY_GIGABYTES / 4)
        );
    }

    public LauncherSettings {
        if (language == null) throw new IllegalArgumentException("Launcher language is missing.");
        if (windowWidth < 320 || windowWidth > 7680) {
            throw new IllegalArgumentException("Game window width must be between 320 and 7680 pixels.");
        }
        if (windowHeight < 240 || windowHeight > 4320) {
            throw new IllegalArgumentException("Game window height must be between 240 and 4320 pixels.");
        }
        if (allocatedMemoryGigabytes < LauncherSettings.MINIMUM_ALLOCATED_MEMORY_GIGABYTES
                || allocatedMemoryGigabytes > LauncherSettings.MAXIMUM_ALLOCATED_MEMORY_GIGABYTES) {
            throw new IllegalArgumentException(
                    "Allocated memory must be between 1 and "
                            + LauncherSettings.MAXIMUM_ALLOCATED_MEMORY_GIGABYTES + " GB."
            );
        }
    }

    @NotNull
    public static LauncherSettings defaults() {
        return new LauncherSettings(
                false,
                1280,
                720,
                LauncherSettings.DEFAULT_ALLOCATED_MEMORY_GIGABYTES,
                LauncherLanguage.systemDefault()
        );
    }
}
