package com.anightdazingzoroark.squirrellauncher.minecraft.instance;

import com.anightdazingzoroark.squirrellauncher.minecraft.MinecraftPaths;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

public record MinecraftInstance(
        @NotNull String id,
        @NotNull String name,
        @NotNull InstanceType type,
        @Nullable String loaderVersion,
        @Nullable String iconKey,
        long totalTimePlayedSeconds,
        long createdTimeMillis
) {
    @NotNull
    public Path directory() {
        return MinecraftPaths.INSTANCES.resolve(this.id);
    }

    @NotNull
    public Path gameDirectory() {
        Path minecraft = this.directory().resolve("minecraft");
        Path dotMinecraft = this.directory().resolve(".minecraft");
        return java.nio.file.Files.exists(dotMinecraft) && !java.nio.file.Files.exists(minecraft)
                ? dotMinecraft
                : minecraft;
    }

    @NotNull
    public Path nativesDirectory() {
        return this.directory().resolve("natives");
    }

    @NotNull
    public Path configFile() {
        return this.directory().resolve("instance.cfg");
    }

    @NotNull
    public Path componentFile() {
        return this.directory().resolve("mmc-pack.json");
    }

    @NotNull
    public Path modsDirectory() {
        return this.gameDirectory().resolve("mods");
    }

}
