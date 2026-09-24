package com.anightdazingzoroark.squirrellauncher.launcher;

import com.anightdazingzoroark.squirrellauncher.minecraft.MinecraftPaths;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Loads and atomically saves launcher settings. */
public final class LauncherSettingsManager {
    private static final int FORMAT_VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @NotNull
    private volatile LauncherSettings settings = LauncherSettings.defaults();

    public LauncherSettingsManager() {
        if (!Files.exists(MinecraftPaths.SETTINGS)) return;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(MinecraftPaths.SETTINGS)).getAsJsonObject();
            int formatVersion = root.has("formatVersion") ? root.get("formatVersion").getAsInt() : 0;
            if (formatVersion != FORMAT_VERSION) {
                throw new IOException("Unsupported settings file version: " + formatVersion);
            }
            this.settings = new LauncherSettings(
                    root.has("fullscreen") && root.get("fullscreen").getAsBoolean(),
                    root.has("windowWidth") ? root.get("windowWidth").getAsInt() : 1280,
                    root.has("windowHeight") ? root.get("windowHeight").getAsInt() : 720,
                    root.has("language")
                            ? LauncherLanguage.fromCode(root.get("language").getAsString())
                            : LauncherLanguage.systemDefault()
            );
        }
        catch (Exception exception) {
            throw new IllegalStateException(
                    "Could not load launcher settings from " + MinecraftPaths.SETTINGS + ".",
                    exception
            );
        }
    }

    @NotNull
    public LauncherSettings settings() {
        return this.settings;
    }

    public synchronized void update(@NotNull LauncherSettings settings) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("formatVersion", FORMAT_VERSION);
        root.addProperty("fullscreen", settings.fullscreen());
        root.addProperty("windowWidth", settings.windowWidth());
        root.addProperty("windowHeight", settings.windowHeight());
        root.addProperty("language", settings.language().code());

        Path settingsFile = MinecraftPaths.SETTINGS;
        Files.createDirectories(settingsFile.getParent());
        Path temporaryFile = settingsFile.resolveSibling(settingsFile.getFileName() + ".tmp");
        Files.writeString(temporaryFile, LauncherSettingsManager.GSON.toJson(root));
        try {
            Files.move(
                    temporaryFile,
                    settingsFile,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
            );
        }
        catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporaryFile, settingsFile, StandardCopyOption.REPLACE_EXISTING);
        }
        this.settings = settings;
    }
}
