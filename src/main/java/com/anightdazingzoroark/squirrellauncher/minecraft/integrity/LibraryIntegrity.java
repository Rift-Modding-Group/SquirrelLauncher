package com.anightdazingzoroark.squirrellauncher.minecraft.integrity;

import com.anightdazingzoroark.squirrellauncher.minecraft.MinecraftPaths;
import com.anightdazingzoroark.squirrellauncher.minecraft.launch.LaunchComponent;
import com.anightdazingzoroark.squirrellauncher.minecraft.launch.MavenUtil;
import com.anightdazingzoroark.squirrellauncher.minecraft.launch.PlatformRules;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;

public final class LibraryIntegrity {
    private LibraryIntegrity() {}

    public static void repair(@NotNull LaunchComponent component) throws Exception {
        JsonObject metadata = component.metadata();
        if (metadata == null || !metadata.has("libraries")) {
            return;
        }

        JsonArray libraries = metadata.getAsJsonArray("libraries");
        for (JsonElement element : libraries) {
            JsonObject library = element.getAsJsonObject();
            if (!PlatformRules.isLibraryAllowed(library)) continue;

            repairArtifact(
                    library,
                    component.legacyMavenFallback()
            );

            repairNative(
                    library
            );
        }
    }

    private static void repairArtifact(@NotNull JsonObject library, boolean legacyMavenFallback) throws Exception {
        if (!library.has("name")) return;

        String coordinate = library.get("name").getAsString();

        if (library.has("downloads")) {
            JsonObject downloads = library.getAsJsonObject("downloads");

            //Native-only libraries such as lwjgl-platform have no ordinary artifact
            if (!downloads.has("artifact")) return;

            JsonObject artifact = downloads.getAsJsonObject("artifact");
            String relativePath = artifact.has("path") ? artifact.get("path").getAsString() : MavenUtil.path(coordinate);

            String url = resolveUrl(library, artifact, relativePath);
            String sha1 = stringOrNull(artifact, "sha1");
            Long size = longOrNull(artifact, "size");

            IntegrityManager.ensure(new FileRequirement(MinecraftPaths.LIBRARIES.resolve(relativePath), url, sha1, size));

            return;
        }

        //Legacy Forge/MMC Maven metadata.
        if (!legacyMavenFallback) return;

        String relativePath = MavenUtil.path(coordinate);
        String repository = library.has("url") ? library.get("url").getAsString() : "https://libraries.minecraft.net/";
        if (!repository.endsWith("/")) repository += "/";

        IntegrityManager.ensure(new FileRequirement(
                MinecraftPaths.LIBRARIES.resolve(relativePath),
                repository + relativePath,
                stringOrNull(library, "sha1"),
                null
        ));
    }

    private static void repairNative(@NotNull JsonObject library) throws Exception {
        if (!library.has("natives") || !library.has("downloads")) return;

        JsonObject downloads = library.getAsJsonObject("downloads");
        if (!downloads.has("classifiers")) return;

        JsonObject natives = library.getAsJsonObject("natives");

        String os = PlatformRules.osName();
        if (!natives.has(os)) return;

        String classifier = natives.get(os).getAsString();
        String arch = System.getProperty("os.arch").contains("64") ? "64" : "32";
        classifier = classifier.replace("${arch}", arch);

        JsonObject classifiers = downloads.getAsJsonObject("classifiers");
        if (!classifiers.has(classifier)) return;

        JsonObject artifact = classifiers.getAsJsonObject(classifier);

        String coordinate = library.get("name").getAsString();

        String relativePath;
        if (artifact.has("path")) relativePath = artifact.get("path").getAsString();
        else relativePath = classifierPath(coordinate, classifier);

        String url = resolveUrl(library, artifact, relativePath);

        IntegrityManager.ensure(new FileRequirement(
                MinecraftPaths.LIBRARIES.resolve(relativePath),
                url,
                stringOrNull(artifact, "sha1"),
                longOrNull(artifact,"size")
        ));
    }

    @Nullable
    private static String resolveUrl(@NotNull JsonObject library, @NotNull JsonObject artifact, @NotNull String relativePath) {
        if (artifact.has("url")) {
            String url = artifact.get("url").getAsString();
            if (!url.isBlank()) return url;
        }

        if (library.has("url")) {
            String repository = library.get("url").getAsString();
            if (!repository.endsWith("/")) repository += "/";

            return repository + relativePath;
        }

        return null;
    }

    @NotNull
    private static String classifierPath(@NotNull String coordinate, @NotNull String classifier) {
        String extension = null;
        int extensionSeparator = coordinate.indexOf('@');
        if (extensionSeparator >= 0) {
            extension = coordinate.substring(extensionSeparator);
            coordinate = coordinate.substring(0, extensionSeparator);
        }

        String classifierCoordinate = coordinate + ":" + classifier;
        if (extension != null) classifierCoordinate += extension;

        return MavenUtil.path(
                classifierCoordinate
        );
    }

    @Nullable
    private static String stringOrNull(@NotNull JsonObject object, @NotNull String field) {
        if (!object.has(field) || object.get(field).isJsonNull()) {
            return null;
        }

        return object.get(field).getAsString();
    }

    @Nullable
    private static Long longOrNull(@NotNull JsonObject object, @NotNull String field) {
        if (!object.has(field) || object.get(field).isJsonNull()) {
            return null;
        }

        return object.get(field).getAsLong();
    }
}