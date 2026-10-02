package com.anightdazingzoroark.squirrellauncher.minecraft.mod;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.image.BufferedImage;
import java.nio.file.Path;

public record ManagedMod(
        @NotNull String fileName,
        @NotNull String name,
        @NotNull String version,
        @NotNull String description,
        long lastModifiedMillis,
        @Nullable BufferedImage icon,
        @NotNull Path path,
        @NotNull ModState state
) {}
