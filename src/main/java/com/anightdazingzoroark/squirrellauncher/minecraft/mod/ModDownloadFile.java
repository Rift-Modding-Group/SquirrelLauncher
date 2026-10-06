package com.anightdazingzoroark.squirrellauncher.minecraft.mod;

import com.anightdazingzoroark.squirrellauncher.minecraft.mod.platform.ModDownloadPlatform;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

//representation of a mod that is about to be downloaded from the launcher
public record ModDownloadFile(
        @NotNull ModDownloadPlatform platform,
        @NotNull String projectId,
        @NotNull String providerFileId,
        @NotNull String projectName,
        @Nullable String projectUrl,
        @NotNull String versionName,
        @NotNull String releaseType,
        @NotNull String fileName,
        @NotNull String downloadUrl,
        @Nullable String sha1,
        long fileSize
) {}
