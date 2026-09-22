package com.anightdazingzoroark.squirrellauncher.launcher;

import com.anightdazingzoroark.squirrellauncher.minecraft.instance.InstanceType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record InstanceCreationRequest(
        @NotNull String name,
        @NotNull InstanceType type,
        @Nullable String loaderVersion
) {}
