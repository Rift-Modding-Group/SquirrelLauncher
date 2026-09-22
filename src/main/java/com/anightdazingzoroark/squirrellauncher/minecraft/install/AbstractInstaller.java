package com.anightdazingzoroark.squirrellauncher.minecraft.install;

import com.anightdazingzoroark.squirrellauncher.minecraft.InstallUtils;
import com.anightdazingzoroark.squirrellauncher.minecraft.MinecraftPaths;
import com.anightdazingzoroark.squirrellauncher.minecraft.download.Downloader;
import com.anightdazingzoroark.squirrellauncher.minecraft.launch.MavenUtil;
import com.anightdazingzoroark.squirrellauncher.minecraft.launch.PlatformRules;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

public abstract class AbstractInstaller {
    //-----abstract methods-----
    @NotNull
    protected abstract String installationName();

    @NotNull
    protected abstract Path installationDirectory(@NotNull String version);

    @NotNull
    protected abstract Optional<Path> verifyInstallation(@NotNull String version, @NotNull Path installDirectory) throws IOException, InterruptedException;

    @NotNull
    protected abstract Path installFresh(@NotNull String version, @NotNull Path installDirectory) throws IOException, InterruptedException;

    //-----non abstract methods-----
    /**
     * Install or repair a version using the lifecycle shared by every installer.
     */
    @NotNull
    public final Path install(@NotNull String version) throws IOException, InterruptedException {
        version = this.requireVersion(version, installationName());
        Path installDirectory = this.installationDirectory(version);
        String displayName = this.installationName() + " " + version;

        if (installationExists(version, installDirectory)) {
            try {
                Optional<Path> installedRoot = verifyInstallation(version, installDirectory);
                if (installedRoot.isPresent()) {
                    System.out.println(displayName + " is installed and verified.");
                    return installedRoot.get();
                }

                System.out.println(displayName + " installation is incomplete. Reinstalling...");
            }
            catch (IOException | RuntimeException e) {
                System.out.println(displayName + " installation is damaged. Reinstalling...");
            }

            this.removeInstallation(version, installDirectory);
        }

        System.out.println();
        System.out.println("Installing " + displayName + "...");

        Path installedRoot = installFresh(version, installDirectory);

        System.out.println();
        System.out.println(displayName + " installation complete.");
        return installedRoot;
    }

    protected boolean installationExists(@NotNull String version, @NotNull Path installDirectory) {
        return Files.exists(installDirectory);
    }

    protected void removeInstallation(@NotNull String version, @NotNull Path installDirectory) throws IOException {
        if (!Files.exists(installDirectory)) return;

        List<Path> paths;
        try (Stream<Path> stream = Files.walk(installDirectory)) {
            paths = stream.sorted(Comparator.reverseOrder()).toList();
        }

        for (Path path : paths) Files.deleteIfExists(path);
    }

    /**
     * validation for instance type
     * */
    protected final String requireVersion(String version, String distribution) {
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException(distribution + " version is missing.");
        }

        return version;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    protected final boolean isLibraryAllowed(JsonObject library) {
        return PlatformRules.isLibraryAllowed(library);
    }

    protected final void installLibrary(JsonObject library, String logPrefix) throws IOException, InterruptedException {
        if (!library.has("name") || !isLibraryAllowed(library)) return;

        String coordinate = library.get("name").getAsString();
        String defaultRelativePath = MavenUtil.path(coordinate);

        if (library.has("downloads")) {
            JsonObject downloads = library.getAsJsonObject("downloads");

            if (downloads.has("artifact")) {
                JsonObject artifact = downloads.getAsJsonObject("artifact");
                String relativePath = artifact.has("path")
                        ? artifact.get("path").getAsString()
                        : defaultRelativePath;

                installArtifact(library, artifact, relativePath, coordinate, logPrefix);
            }

            if (downloads.has("classifiers") && library.has("natives")) {
                installCurrentNative(library, downloads.getAsJsonObject("classifiers"));
            }

            return;
        }

        if (library.has("url")) {
            installArtifact(library, null, defaultRelativePath, coordinate, logPrefix);
            return;
        }

        throw new IOException("Library has no usable download information:\n" + coordinate);
    }

    protected final boolean repairLibrary(JsonObject library) throws IOException, InterruptedException {
        if (!library.has("name") || !isLibraryAllowed(library)) return true;

        String coordinate = library.get("name").getAsString();
        String relativePath = MavenUtil.path(coordinate);
        JsonObject artifact = null;

        if (library.has("downloads")) {
            JsonObject downloads = library.getAsJsonObject("downloads");
            if (downloads.has("artifact")) {
                artifact = downloads.getAsJsonObject("artifact");
                if (artifact.has("path")) {
                    relativePath = artifact.get("path").getAsString();
                }
            }
        }

        String url = this.resolveArtifactUrl(library, artifact, relativePath);
        String sha1 = this.findSha1(library, artifact);
        Path destination = MinecraftPaths.LIBRARIES.resolve(relativePath);

        if (sha1 != null && !sha1.isBlank() && (url == null || url.isBlank())) {
            return Downloader.sha1Matches(destination, sha1);
        }

        if ((url == null || url.isBlank()) && !isUsableWithoutHash(destination)) return false;

        if (url != null && !url.isBlank()) this.ensureArtifact(url, destination, sha1);

        return true;
    }

    protected final void installArtifact(@NotNull JsonObject library, @NotNull JsonObject artifact) throws IOException, InterruptedException {
        String coordinate = library.get("name").getAsString();
        String relativePath = artifact.has("path") ? artifact.get("path").getAsString() : MavenUtil.path(coordinate);
        this.installArtifact(library, artifact, relativePath, coordinate, null);
    }

    protected final void installCurrentNative(@NotNull JsonObject library, @NotNull JsonObject classifiers) throws IOException, InterruptedException {
        if (!library.has("natives") || !library.has("name")) return;
        JsonObject natives = library.getAsJsonObject("natives");
        String os = PlatformRules.osName();
        if (!natives.has(os)) return;

        String classifier = natives.get(os).getAsString();
        String arch = System.getProperty("os.arch").contains("64") ? "64" : "32";
        classifier = classifier.replace("${arch}", arch);
        if (!classifiers.has(classifier)) return;

        JsonObject artifact = classifiers.getAsJsonObject(classifier);
        String coordinate = library.get("name").getAsString();
        String relativePath = artifact.has("path") ? artifact.get("path").getAsString() : classifierPath(coordinate, classifier);

        installArtifact(library, artifact, relativePath, coordinate, null);
    }

    private void installArtifact(
            @NotNull JsonObject library,
            @Nullable JsonObject artifact,
            @NotNull String relativePath,
            @NotNull String coordinate,
            @Nullable String logPrefix
    ) throws IOException, InterruptedException {
        String url = resolveArtifactUrl(library, artifact, relativePath);
        if (url == null || url.isBlank()) throw new IOException("Could not determine download URL for library:\n" + coordinate);

        if (logPrefix != null) System.out.println(logPrefix + ": " + coordinate + " -> " + relativePath);

        this.ensureArtifact(
                url, MinecraftPaths.LIBRARIES.resolve(relativePath),
                this.findSha1(library, artifact)
        );
    }

    @Nullable
    private String resolveArtifactUrl(@NotNull JsonObject library, @Nullable JsonObject artifact, @NotNull String relativePath) {
        if (artifact != null && artifact.has("url") && !artifact.get("url").isJsonNull()) {
            String url = artifact.get("url").getAsString();
            if (!url.isBlank()) return url;
        }

        if (library.has("url") && !library.get("url").isJsonNull()) {
            return ensureTrailingSlash(library.get("url").getAsString()) + relativePath;
        }

        return null;
    }

    @Nullable
    private String findSha1(@NotNull JsonObject library, @Nullable JsonObject artifact) {
        if (artifact != null && artifact.has("sha1") && !artifact.get("sha1").isJsonNull()) {
            return artifact.get("sha1").getAsString();
        }

        if (library.has("sha1") && !library.get("sha1").isJsonNull()) {
            return library.get("sha1").getAsString();
        }

        return null;
    }

    private void ensureArtifact(@NotNull String url, @NotNull Path destination, @Nullable String sha1) throws IOException, InterruptedException {
        if (sha1 != null && !sha1.isBlank()) {
            Downloader.downloadVerified(url, destination, sha1);
            return;
        }

        if (Files.isRegularFile(destination)) {
            if (!isArchive(destination) || InstallUtils.isValidArchive(destination)) return;
            System.out.println("[CORRUPT] " + destination);
        }
        else System.out.println("[MISSING] " + destination.getFileName());

        Downloader.downloadReplacing(url, destination);
        if (isArchive(destination) && !InstallUtils.isValidArchive(destination)) {
            throw new IOException("Downloaded artifact is not a valid archive: " + destination);
        }
    }

    private boolean isUsableWithoutHash(@NotNull Path destination) {
        if (!Files.isRegularFile(destination)) return false;
        return !isArchive(destination) || InstallUtils.isValidArchive(destination);
    }

    private boolean isArchive(@NotNull Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".jar") || name.endsWith(".zip");
    }

    @NotNull
    private String ensureTrailingSlash(@NotNull String url) {
        return url.endsWith("/") ? url : url + "/";
    }

    @NotNull
    private String classifierPath(@NotNull String coordinate, @NotNull String classifier) {
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
}
