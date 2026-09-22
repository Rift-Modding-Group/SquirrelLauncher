package com.anightdazingzoroark.squirrellauncher.minecraft.launch;

import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.List;

public record ResolvedLaunchPlan(
        @NotNull String name, @NotNull String versionName, @NotNull String versionType, @NotNull String assetIndexName,
        @NotNull String mainClass,
        @NotNull List<Path> classpath, @NotNull List<JsonObject> nativeMetadata,
        @NotNull List<String> jvmArguments, @NotNull List<String> gameArguments
) {}