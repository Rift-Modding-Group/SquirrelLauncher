package com.anightdazingzoroark.squirrellauncher.minecraft.instance;

import com.anightdazingzoroark.squirrellauncher.minecraft.MinecraftPaths;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public final class InstanceManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private InstanceManager() {}

    @NotNull
    public static MinecraftInstance create(@NotNull String id, @NotNull String name, @NotNull InstanceType type, @Nullable String loaderVersion) throws IOException {
        validateId(id);

        Path instanceDirectory = MinecraftPaths.INSTANCES.resolve(id);
        if (Files.exists(instanceDirectory)) {
            throw new IOException("Instance already exists: " + id);
        }

        MinecraftInstance instance = new MinecraftInstance(id, name, type, loaderVersion);
        Files.createDirectories(instance.gameDirectory());
        Files.createDirectories(instance.nativesDirectory());
        save(instance);

        return instance;
    }

    public static void save(@NotNull MinecraftInstance instance) throws IOException {
        Files.createDirectories(instance.directory());

        try (Writer writer = Files.newBufferedWriter(instance.configFile())) {
            GSON.toJson(instance, writer);
        }
    }

    @NotNull
    public static MinecraftInstance load(@NotNull String id) throws IOException {
        Path config = MinecraftPaths.INSTANCES.resolve(id).resolve("instance.json");

        if (!Files.exists(config)) {
            throw new IOException("Instance does not exist: " + id);
        }

        try (Reader reader = Files.newBufferedReader(config)) {
            return GSON.fromJson(reader, MinecraftInstance.class);
        }
    }

    @NotNull
    public static List<MinecraftInstance> list() throws IOException {
        Files.createDirectories(MinecraftPaths.INSTANCES);

        List<MinecraftInstance> result = new ArrayList<>();
        try (Stream<Path> directories = Files.list(MinecraftPaths.INSTANCES)) {
            List<Path> paths = directories.filter(Files::isDirectory).sorted().toList();

            for (Path directory : paths) {
                Path config = directory.resolve("instance.json");
                if (!Files.exists(config)) continue;

                try (Reader reader = Files.newBufferedReader(config)) {
                    MinecraftInstance instance = GSON.fromJson(reader, MinecraftInstance.class);
                    result.add(instance);
                }
            }
        }
        result.sort(Comparator.comparing(MinecraftInstance::name, String.CASE_INSENSITIVE_ORDER));

        return result;
    }

    public static boolean exists(@NotNull String id) {
        return Files.exists(MinecraftPaths.INSTANCES.resolve(id).resolve("instance.json"));
    }

    private static void validateId(@Nullable String id) {
        if (id == null || !id.matches("[a-zA-Z0-9._-]+")) {
            throw new IllegalArgumentException("Invalid instance ID: " + id);
        }
    }
}