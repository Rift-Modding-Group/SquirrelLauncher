package com.anightdazingzoroark.squirrellauncher.minecraft.integrity;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

public record FileRequirement(@NotNull Path destination, @Nullable String url, @Nullable String sha1, Long size) {}