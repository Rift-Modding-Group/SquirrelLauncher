package com.anightdazingzoroark.squirrellauncher.minecraft.runtime;

import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

public record JavaRuntime(@NotNull JavaVersion version, @NotNull Path executable, @NotNull String detectedVersion) {}