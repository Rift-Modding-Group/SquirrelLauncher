package com.anightdazingzoroark.squirrellauncher.minecraft.mod;

import com.anightdazingzoroark.squirrellauncher.minecraft.mod.platform.ModDownloadPlatform;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.image.BufferedImage;
import java.nio.file.Path;

//representation of a downloaded mod in the launcher
public record ManagedMod(
        @NotNull String fileName,
        @NotNull String name,
        @NotNull String version,
        @NotNull String description,
        long lastModifiedMillis,
        @Nullable BufferedImage icon,
        @NotNull Path path,
        @NotNull ModState state,
        @Nullable ModDownloadPlatform provider,
        @Nullable String providerProjectId,
        @Nullable String providerFileId,
        @Nullable String providerPageUrl
) {}
