package com.anightdazingzoroark.squirrellauncher.minecraft.instance;

import com.anightdazingzoroark.squirrellauncher.minecraft.MinecraftPaths;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

public record MinecraftInstance(@NotNull String id, @NotNull String name, @NotNull InstanceType type, @Nullable String loaderVersion) {
    @NotNull
    public Path directory() {
        return MinecraftPaths.INSTANCES.resolve(id);
    }

    @NotNull
    public Path gameDirectory() {
        return directory().resolve("minecraft");
    }

    @NotNull
    public Path nativesDirectory() {
        return directory().resolve("natives");
    }

    @NotNull
    public Path configFile() {
        return directory().resolve("instance.json");
    }

    @NotNull
    public Path modsDirectory() {
        return this.gameDirectory().resolve("mods");
    }

    @NotNull
    public Path disabledModsDirectory() {
        return this.directory().resolve("disabled-mods");
    }
}