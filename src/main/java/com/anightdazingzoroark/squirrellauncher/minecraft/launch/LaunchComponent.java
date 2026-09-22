package com.anightdazingzoroark.squirrellauncher.minecraft.launch;

import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record LaunchComponent(
        @NotNull String id, @Nullable JsonObject metadata, boolean legacyMavenFallback,
        @NotNull Set<String> removeLibraries, @NotNull Map<String, Path> replaceLibraries,
        @NotNull List<String> jvmArguments, @NotNull List<String> gameArguments,
        @Nullable String mainClassOverride, @Nullable String versionTypeOverride
) {
    public LaunchComponent {
        removeLibraries = Set.copyOf(removeLibraries);
        replaceLibraries = Map.copyOf(replaceLibraries);
        jvmArguments = List.copyOf(jvmArguments);
        gameArguments = List.copyOf(gameArguments);
    }

    @NotNull
    public static LaunchComponent metadata(@NotNull String id, @NotNull JsonObject metadata, boolean legacyMavenFallback) {
        return new LaunchComponent(
                id, metadata, legacyMavenFallback,
                Set.of(), Map.of(),
                List.of(), List.of(),
                null, null
        );
    }
}