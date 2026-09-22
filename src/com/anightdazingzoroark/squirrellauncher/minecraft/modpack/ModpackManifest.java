package com.anightdazingzoroark.squirrellauncher.minecraft.modpack;

import com.anightdazingzoroark.squirrellauncher.minecraft.instance.InstanceType;
import org.jetbrains.annotations.NotNull;

public record ModpackManifest(int formatVersion, @NotNull String name, @NotNull InstanceType type, @NotNull String loaderVersion) {}