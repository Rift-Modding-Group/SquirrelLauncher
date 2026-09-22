package com.anightdazingzoroark.squirrellauncher.minecraft.install;

import com.anightdazingzoroark.squirrellauncher.minecraft.InstallUtils;
import com.anightdazingzoroark.squirrellauncher.minecraft.MinecraftPaths;
import com.anightdazingzoroark.squirrellauncher.minecraft.download.Downloader;
import com.google.gson.*;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class CleanroomInstaller extends AbstractInstaller {
    //-----abstract overrides-----
    @Override
    @NotNull
    protected String installationName() {
        return "Cleanroom";
    }

    @Override
    @NotNull
    protected Path installationDirectory(@NotNull String version) {
        return MinecraftPaths.CLEANROOM.resolve(version);
    }

    @Override
    @NotNull
    protected Optional<Path> verifyInstallation(@NotNull String version, @NotNull Path installDirectory) throws IOException, InterruptedException {
        Path packRoot = findMmcPackRoot(installDirectory);
        if (packRoot == null) return Optional.empty();

        this.installLibraries(packRoot);
        return Optional.of(packRoot);
    }

    @Override
    @NotNull
    protected Path installFresh(@NotNull String version, @NotNull Path installDir) throws IOException, InterruptedException {
        Files.createDirectories(installDir);

        //download from github releases
        String releaseApi = "https://api.github.com/repos/CleanroomMC/Cleanroom/releases/tags/" + URLEncoder.encode(version, StandardCharsets.UTF_8);
        System.out.println("Checking Cleanroom release: " + version);

        String releaseJson = Downloader.getString(releaseApi);
        JsonElement releaseElement = JsonParser.parseString(releaseJson);
        if (!releaseElement.isJsonObject()) {
            throw new IOException("GitHub returned an invalid response for Cleanroom release " + version);
        }

        JsonObject release = releaseElement.getAsJsonObject();

        //github api error
        if (!release.has("assets")) {
            String message = release.has("message") ? release.get("message").getAsString() : "Unknown GitHub response";
            throw new IOException("Could not obtain Cleanroom release " + version + ":\n" + message);
        }

        //---find the actual mmc package---
        MmcAsset mmcAsset = this.downloadMmcAsset(release, installDir);
        Path archive = mmcAsset.archive();
        System.out.println("Using Cleanroom MMC package: " + mmcAsset.name());

        //---extract package---
        System.out.println("Extracting Cleanroom package...");
        this.extractZip(archive, installDir);

        //---find mmc-pack.json after extraction---
        Path packRoot = findMmcPackRoot(installDir);
        if (packRoot == null) {throw new IOException("Cleanroom package was extracted, but mmc-pack.json could not be found.");}
        System.out.println("Cleanroom package root: " + packRoot.toAbsolutePath());

        //---install libraries declared by Cleanroom's MMC patches---
        System.out.println("Installing Cleanroom libraries...");
        installLibraries(packRoot);

        return packRoot;
    }

    //-----non abstract methods-----
    private void extractZip(@NotNull Path archive, @NotNull Path destination) throws IOException {
        Path root = destination.toAbsolutePath().normalize();

        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive))) {
            ZipEntry entry;

            while ((entry = zip.getNextEntry()) != null) {
                Path output = root.resolve(entry.getName()).normalize();
                if (!output.startsWith(root)) {
                    throw new IOException("Illegal ZIP entry: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(output);
                    continue;
                }

                Files.createDirectories(output.getParent());
                Files.copy(zip, output, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private void installLibraries(@NotNull Path root) throws IOException, InterruptedException {
        JsonObject pack = InstallUtils.readJson(root.resolve("mmc-pack.json"));

        for (JsonElement element : pack.getAsJsonArray("components")) {
            JsonObject component = element.getAsJsonObject();
            String uid = component.get("uid").getAsString();
            Path patchFile = root.resolve("patches").resolve(uid + ".json");

            if (!Files.exists(patchFile)) continue;

            JsonObject patch = InstallUtils.readJson(patchFile);

            installLibrariesFrom(patch, "libraries");
            installLibrariesFrom(patch, "+libraries");
        }
    }

    private void installLibrariesFrom(@NotNull JsonObject patch, @NotNull String field) throws IOException, InterruptedException {
        if (!patch.has(field)) return;

        for (JsonElement element : patch.getAsJsonArray(field)) {
            JsonObject library = element.getAsJsonObject();
            this.installLibrary(library, "Cleanroom library");
        }
    }

    private Path findMmcPackRoot(@NotNull Path root) throws IOException {
        if (!Files.exists(root)) return null;

        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equals("mmc-pack.json"))
                    .map(Path::getParent)
                    .findFirst()
                    .orElse(null);
        }
    }

    private MmcAsset downloadMmcAsset(@NotNull JsonObject release, @NotNull Path installDir) throws IOException, InterruptedException {
        JsonArray assets = release.getAsJsonArray("assets");
        List<String> availableAssets = new ArrayList<>();

        for (JsonElement element : assets) {
            JsonObject asset = element.getAsJsonObject();
            String name = asset.get("name").getAsString();
            availableAssets.add(name);

            if (!name.toLowerCase(Locale.ROOT).endsWith(".zip")) continue;

            String url = asset.get("browser_download_url").getAsString();
            Path candidate = installDir.resolve(name.replace('/', '_'));
            System.out.println("Checking Cleanroom asset: " + name);

            if (Files.exists(candidate) && !InstallUtils.isValidArchive(candidate)) {
                System.out.println("[CORRUPT] Cleanroom package: " + candidate.getFileName());
                Files.delete(candidate);
            }

            Downloader.download(url, candidate);
            if (this.zipContainsMmcPack(candidate)) return new MmcAsset(name, candidate);

            Files.deleteIfExists(candidate);
        }

        throw new IOException(
                "No Cleanroom release ZIP contained mmc-pack.json.\n Available assets:\n  "
                        + String.join("\n  ", availableAssets)
        );
    }

    private boolean zipContainsMmcPack(@NotNull Path archive) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;

                String name = entry.getName().replace('\\', '/');
                if (name.equals("mmc-pack.json") || name.endsWith("/mmc-pack.json")) return true;
            }
        }

        return false;
    }

    private record MmcAsset(@NotNull String name, @NotNull Path archive) {}
}
