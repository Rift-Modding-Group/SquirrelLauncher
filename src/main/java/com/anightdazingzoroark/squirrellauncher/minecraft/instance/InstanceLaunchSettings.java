package com.anightdazingzoroark.squirrellauncher.minecraft.instance;

import com.anightdazingzoroark.squirrellauncher.launcher.GameSettings;
import com.anightdazingzoroark.squirrellauncher.launcher.JvmArguments;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;

public record InstanceLaunchSettings(
        @Nullable Path javaExecutable,
        boolean overrideWindowSettings,
        boolean fullscreen,
        int windowWidth,
        int windowHeight,
        boolean overrideMemory,
        int allocatedMemoryGigabytes,
        boolean lowMemoryWarning,
        boolean overrideJvmArguments,
        @NotNull List<String> jvmArguments
) {
    public InstanceLaunchSettings {
        if (javaExecutable != null) javaExecutable = javaExecutable.toAbsolutePath().normalize();
        jvmArguments = List.copyOf(jvmArguments);
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
        if (JvmArguments.maximumMemoryGigabytes(jvmArguments) != allocatedMemoryGigabytes) {
            throw new IllegalArgumentException("The -Xmx argument must match the allocated memory setting.");
        }
        if (JvmArguments.minimumMemoryMegabytes(jvmArguments) > (long) allocatedMemoryGigabytes * 1024L) {
            throw new IllegalArgumentException("Initial JVM memory cannot exceed maximum JVM memory.");
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
                true,
                false,
                JvmArguments.withMemory(List.of(), 512, GameSettings.DEFAULT_ALLOCATED_MEMORY_GIGABYTES)
        );
    }
}
