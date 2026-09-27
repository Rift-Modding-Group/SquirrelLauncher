package com.anightdazingzoroark.squirrellauncher.minecraft.instance;

import com.anightdazingzoroark.squirrellauncher.launcher.GameSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

public record InstanceLaunchSettings(
        @Nullable Path javaExecutable,
        boolean overrideWindowSettings,
        boolean fullscreen,
        int windowWidth,
        int windowHeight,
        boolean overrideMemory,
        int allocatedMemoryGigabytes,
        boolean lowMemoryWarning
) {
    public InstanceLaunchSettings {
        if (javaExecutable != null) javaExecutable = javaExecutable.toAbsolutePath().normalize();
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
    public static InstanceLaunchSettings defaults() {
        return new InstanceLaunchSettings(
                null,
                false,
                false,
                854,
                480,
                false,
                GameSettings.DEFAULT_ALLOCATED_MEMORY_GIGABYTES,
                true
        );
    }
}
