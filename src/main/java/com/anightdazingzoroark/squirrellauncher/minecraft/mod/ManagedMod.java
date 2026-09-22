package com.anightdazingzoroark.squirrellauncher.minecraft.mod;

import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

public record ManagedMod(@NotNull String fileName, @NotNull Path path, @NotNull ModState state) {}