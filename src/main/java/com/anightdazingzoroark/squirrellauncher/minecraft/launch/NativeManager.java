package com.anightdazingzoroark.squirrellauncher.minecraft.launch;

import com.anightdazingzoroark.squirrellauncher.minecraft.MinecraftPaths;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class NativeManager {
    private NativeManager() {}

    public static void rebuild(@NotNull List<LaunchComponent> components, @NotNull Path destination) throws IOException {
        deleteDirectory(destination);
        Files.createDirectories(destination);

        for (LaunchComponent component : components) {
            JsonObject metadata = component.metadata();
            if (metadata == null || !metadata.has("libraries")) {
                continue;
            }

            for (JsonElement element : metadata.getAsJsonArray("libraries")) {
                JsonObject library = element.getAsJsonObject();

                // only cares about libraries that actually declare natives
                if (!library.has("natives")) continue;

                String name = library.has("name") ? library.get("name").getAsString() : "<unknown>";
                System.out.println("Native candidate: " + name);

                if (!PlatformRules.isLibraryAllowed(library)) {
                    System.out.println("Skipping native due to platform rules: " + name);
                    continue;
                }

                extractNativeLibrary(library, destination);
            }
        }
    }

    private static void extractNativeLibrary(@NotNull JsonObject library, @NotNull Path destination) throws IOException {
        // normal Java libraries don't have this.
        if (!library.has("natives")) return;
        if (!library.has("downloads")) return;

        JsonObject downloads = library.getAsJsonObject("downloads");

        // Minecraft 1.12.2 native-only libraries have classifiers instead of an ordinary artifact
        if (!downloads.has("classifiers")) return;

        JsonObject natives = library.getAsJsonObject("natives");

        String os = PlatformRules.osName();
        if (!natives.has(os)) return;

        String classifier = natives.get(os).getAsString();
        String arch = System.getProperty("os.arch").contains("64") ? "64" : "32";
        classifier = classifier.replace("${arch}", arch);

        JsonObject classifiers = downloads.getAsJsonObject("classifiers");
        if (!classifiers.has(classifier)) {
            throw new IOException("Native classifier " + classifier + " is missing for " + library.get("name").getAsString());
        }

        JsonObject nativeArtifact = classifiers.getAsJsonObject(classifier);

        String relativePath;
        //Mojang normally provides an explicit path
        if (nativeArtifact.has("path")) {
            relativePath = nativeArtifact.get("path").getAsString();
        }
        //Fallback for MMC-style metadata.
        else {
            relativePath = classifierPath(library.get("name").getAsString(), classifier);
        }

        Path nativeJar = MinecraftPaths.LIBRARIES.resolve(relativePath);
        if (!Files.exists(nativeJar)) {
            throw new IOException("Native library is missing:\n" + nativeJar);
        }

        List<String> exclusions = readExclusions(library);

        System.out.println("Extracting native: " + nativeJar.getFileName());

        extractJar(nativeJar, destination, exclusions);
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

        return MavenUtil.path(classifierCoordinate);
    }

    @NotNull
    private static List<String> readExclusions(@NotNull JsonObject library) {
        List<String> exclusions = new ArrayList<>();
        exclusions.add("META-INF/"); //always ignore META-INF

        if (!library.has("extract")) return exclusions;

        JsonObject extract = library.getAsJsonObject("extract");
        if (!extract.has("exclude")) return exclusions;

        for (JsonElement element : extract.getAsJsonArray("exclude")) {
            exclusions.add(element.getAsString());
        }

        return exclusions;
    }

    private static void extractJar(@NotNull Path nativeJar, @NotNull Path destination, @NotNull List<String> exclusions) throws IOException {
        Path root = destination.toAbsolutePath().normalize();
        try (InputStream input = Files.newInputStream(nativeJar); ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String entryName = entry.getName().replace('\\', '/');

                if (entry.isDirectory()) continue;

                if (isExcluded(entryName, exclusions)) continue;

                Path output = root.resolve(entryName).normalize();

                /*
                 * Protect against ZIP traversal.
                 */
                if (!output.startsWith(root)) {
                    throw new IOException("Illegal native ZIP entry: " + entryName);
                }

                Path parent = output.getParent();

                if (parent != null) Files.createDirectories(parent);

                Files.copy(zip, output, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static boolean isExcluded(@NotNull String entryName, @NotNull List<String> exclusions) {
        for (String exclusion : exclusions) {
            if (entryName.startsWith(exclusion)) {
                return true;
            }
        }

        return false;
    }

    private static void deleteDirectory(@NotNull Path directory) throws IOException {
        if (!Files.exists(directory)) return;

        try (Stream<Path> paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        }
                        catch (IOException e) {
                            throw new NativeDeleteException(e);
                        }
                    });
        }
        catch (NativeDeleteException e) {
            throw e.getCause();
        }
    }

    private static final class NativeDeleteException extends RuntimeException {
        private final IOException cause;

        private NativeDeleteException(IOException cause) {
            super(cause);
            this.cause = cause;
        }

        @Override
        public synchronized IOException getCause() {
            return cause;
        }
    }
}
