package com.anightdazingzoroark.squirrellauncher.launcher;

import com.anightdazingzoroark.squirrellauncher.minecraft.MinecraftPaths;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads and atomically saves instance specific settings.
 * */
public final class GameSettingsManager {
    private static final int FORMAT_VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @NotNull
    private volatile GameSettings settings = GameSettings.defaults();

    public GameSettingsManager() {
        if (!Files.exists(MinecraftPaths.SETTINGS)) return;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(MinecraftPaths.SETTINGS)).getAsJsonObject();
            int formatVersion = root.has("formatVersion") ? root.get("formatVersion").getAsInt() : 0;
            if (formatVersion != FORMAT_VERSION) {
                throw new IOException("Unsupported settings file version: " + formatVersion);
            }
            int allocatedMemoryGigabytes = root.has("allocatedMemoryGigabytes")
                    ? root.get("allocatedMemoryGigabytes").getAsInt()
                    : GameSettings.DEFAULT_ALLOCATED_MEMORY_GIGABYTES;
            allocatedMemoryGigabytes = Math.clamp(
                    allocatedMemoryGigabytes,
                    GameSettings.MINIMUM_ALLOCATED_MEMORY_GIGABYTES,
                    GameSettings.MAXIMUM_ALLOCATED_MEMORY_GIGABYTES
            );
            List<String> jvmArguments = new ArrayList<>();
            if (root.has("jvmArguments") && root.get("jvmArguments").isJsonArray()) {
                JsonArray savedArguments = root.getAsJsonArray("jvmArguments");
                for (JsonElement savedArgument : savedArguments) jvmArguments.add(savedArgument.getAsString());
            }
            int minimumMemoryMegabytes = 512;
            for (String argument : jvmArguments) {
                if (!argument.startsWith("-Xms")) continue;
                minimumMemoryMegabytes = JvmArguments.minimumMemoryMegabytes(jvmArguments);
                break;
            }
            minimumMemoryMegabytes = (int) Math.clamp(
                    minimumMemoryMegabytes,
                    1L,
                    (long) allocatedMemoryGigabytes * 1024L
            );
            jvmArguments = JvmArguments.withMemory(
                    jvmArguments,
                    minimumMemoryMegabytes,
                    allocatedMemoryGigabytes
            );
            this.settings = new GameSettings(
                    root.has("fullscreen") && root.get("fullscreen").getAsBoolean(),
                    root.has("windowWidth") ? root.get("windowWidth").getAsInt() : 854,
                    root.has("windowHeight") ? root.get("windowHeight").getAsInt() : 480,
                    allocatedMemoryGigabytes,
                    !root.has("lowMemoryWarning") || root.get("lowMemoryWarning").getAsBoolean(),
                    jvmArguments,
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
    public GameSettings settings() {
        return this.settings;
    }

    public synchronized void update(@NotNull GameSettings settings) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("formatVersion", FORMAT_VERSION);
        root.addProperty("fullscreen", settings.fullscreen());
        root.addProperty("windowWidth", settings.windowWidth());
        root.addProperty("windowHeight", settings.windowHeight());
        root.addProperty("allocatedMemoryGigabytes", settings.allocatedMemoryGigabytes());
        root.addProperty("lowMemoryWarning", settings.lowMemoryWarning());
        JsonArray jvmArguments = new JsonArray();
        for (String argument : settings.jvmArguments()) jvmArguments.add(argument);
        root.add("jvmArguments", jvmArguments);
        root.addProperty("language", settings.language().code());

        Path settingsFile = MinecraftPaths.SETTINGS;
        Files.createDirectories(settingsFile.getParent());
        Path temporaryFile = settingsFile.resolveSibling(settingsFile.getFileName() + ".tmp");
        Files.writeString(temporaryFile, GameSettingsManager.GSON.toJson(root));
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
