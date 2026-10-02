package com.anightdazingzoroark.squirrellauncher.minecraft.runtime;

import org.jetbrains.annotations.NotNull;

import java.net.URI;

public record JavaPackage(
        @NotNull String id,
        @NotNull JavaVersion javaVersion,
        @NotNull String distribution,
        @NotNull String vendor,
        @NotNull String release,
        @NotNull String packageType,
        @NotNull String archiveType,
        long size,
        @NotNull URI detailsUri,
        @NotNull URI downloadUri
) {}
