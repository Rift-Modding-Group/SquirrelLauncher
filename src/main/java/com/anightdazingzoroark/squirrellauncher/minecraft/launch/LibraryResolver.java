package com.anightdazingzoroark.squirrellauncher.minecraft.launch;

import com.anightdazingzoroark.squirrellauncher.minecraft.MinecraftPaths;
import com.google.gson.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;

public final class LibraryResolver {
    private final LinkedHashMap<String, Path> libraries = new LinkedHashMap<>();

    public void apply(@NotNull LaunchComponent component) throws IOException {
        //first remove anything this component explicitly patches out
        for (String key : component.removeLibraries()) {
            this.libraries.remove(key);
        }

        //apply libraries from metadata
        JsonObject metadata = component.metadata();
        if (metadata != null && metadata.has("libraries")) {
            for (JsonElement element : metadata.getAsJsonArray("libraries")) {
                JsonObject library = element.getAsJsonObject();
                if (!PlatformRules.isLibraryAllowed(library)) continue;

                String coordinate = library.get("name").getAsString();
                Path path = resolveLibrary(library, component.legacyMavenFallback());
                if (path == null) continue;

                if (!Files.exists(path)) {
                    throw new IOException("Missing library:\n" + path + "\nCoordinate: " + coordinate);
                }

                //later components override earlier components
                this.libraries.put(MavenUtil.classpathKey(coordinate), path);
            }
        }

        //explicit replacements/additions override metadata.
        this.libraries.putAll(component.replaceLibraries());
    }

    public void add(@NotNull LaunchComponent component) throws IOException {
        JsonObject version = component.metadata();
        if (version == null) throw new IOException("Missing version for component with id " + component.id());
        if (!version.has("libraries")) return;

        for (JsonElement element : version.getAsJsonArray("libraries")) {
            JsonObject library = element.getAsJsonObject();
            if (!PlatformRules.isLibraryAllowed(library)) continue;

            String name = library.get("name").getAsString();
            Path path = resolveLibrary(library, component.legacyMavenFallback());

            //native-only artifact
            if (path == null) continue;

            if (!Files.exists(path)) {
                throw new IOException("Missing library:\n" + path + "\nCoordinate: " + name);
            }

            this.libraries.putIfAbsent(artifactKey(name), path);
        }
    }

    public void addClientJar(@NotNull Path clientJar) throws IOException {
        if (!Files.exists(clientJar)) {
            throw new IOException("Missing Minecraft client:\n" + clientJar);
        }

        this.libraries.put("minecraft-client", clientJar);
    }

    @NotNull
    public String classpath() {
        return String.join(
                File.pathSeparator,
                this.libraries.values()
                        .stream()
                        .map(path -> path.toAbsolutePath().toString())
                        .toList()
        );
    }

    @NotNull
    public List<Path> paths() {
        return List.copyOf(this.libraries.values());
    }

    public int size() {
        return this.libraries.size();
    }

    @Nullable
    private Path resolveLibrary(@NotNull JsonObject library, boolean legacyFallback) {
        if (!library.has("name")) return null;

        String coordinate = library.get("name").getAsString();

        //Mojang/MMC-style explicit download metadata
        if (library.has("downloads")) {
            JsonObject downloads = library.getAsJsonObject("downloads");

            //classifier/native-only entry
            if (!downloads.has("artifact")) return null;

            JsonObject artifact = downloads.getAsJsonObject("artifact");

            /*
             * Mojang metadata normally gives us:
             *
             * "path": "group/artifact/version/file.jar"
             *
             * Cleanroom/MMC metadata may omit it.
             * In that case derive the path from the
             * Maven coordinate.
             */
            String relativePath;
            if (artifact.has("path")) {
                relativePath = artifact.get("path").getAsString();
            }
            else {
                relativePath = MavenUtil.path(coordinate);
            }

            return MinecraftPaths.LIBRARIES.resolve(relativePath);
        }

        /*
         * Legacy Forge/MMC Maven declaration.
         */
        if (legacyFallback) {
            return MinecraftPaths.LIBRARIES.resolve(MavenUtil.path(coordinate));
        }

        return null;
    }

    @NotNull
    private static String artifactKey(@NotNull String coordinate) {
        String coordinateWithoutExtension = coordinate.split("@")[0];
        String[] parts = coordinateWithoutExtension.split(":");

        if (parts.length < 2) return coordinate;
        return parts[0] + ":" + parts[1];
    }

    @NotNull
    private static String mavenPath(@NotNull String coordinate) {
        String extension = "jar";

        int at = coordinate.indexOf('@');
        if (at >= 0) {
            extension = coordinate.substring(at + 1);
            coordinate = coordinate.substring(0, at);
        }

        String[] parts = coordinate.split(":");

        if (parts.length < 3) {
            throw new IllegalArgumentException("Invalid Maven coordinate: " + coordinate);
        }

        String group = parts[0];
        String artifact = parts[1];
        String version = parts[2];

        String classifier = parts.length >= 4 ? parts[3] : null;

        String filename = artifact + "-" + version + (classifier == null ? "" : "-" + classifier) + "." + extension;

        return group.replace('.', '/')
                + "/" + artifact
                + "/" + version
                + "/" + filename;
    }
}