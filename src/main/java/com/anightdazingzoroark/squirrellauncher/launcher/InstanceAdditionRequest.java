package com.anightdazingzoroark.squirrellauncher.launcher;

import com.anightdazingzoroark.squirrellauncher.minecraft.instance.InstanceType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

public record InstanceAdditionRequest(
        @NotNull String name,
        @Nullable InstanceType type,
        @Nullable String loaderVersion,
        @Nullable Path archive,
        @Nullable Path icon
) {
    public boolean importsInstance() {
        return this.archive != null;
    }
}
