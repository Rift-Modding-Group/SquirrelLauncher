package com.anightdazingzoroark.squirrellauncher.minecraft.mod;

import com.anightdazingzoroark.squirrellauncher.minecraft.mod.platform.ModDownloadPlatform;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record ModDownloadProject(
        @NotNull ModDownloadPlatform platform,
        @NotNull String projectId,
        @NotNull String name,
        @NotNull String author,
        @NotNull String description,
        @Nullable String iconUrl,
        @Nullable String pageUrl,
        long downloads,
        boolean favorite
) {}
