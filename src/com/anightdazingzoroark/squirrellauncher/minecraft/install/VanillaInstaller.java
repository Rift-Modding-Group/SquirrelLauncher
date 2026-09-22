package com.anightdazingzoroark.squirrellauncher.minecraft.install;

import com.anightdazingzoroark.squirrellauncher.minecraft.MinecraftPaths;
import com.anightdazingzoroark.squirrellauncher.minecraft.download.Downloader;
import com.google.gson.*;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public final class VanillaInstaller extends AbstractInstaller {
    //-----abstract overrides-----
    @Override
    @NotNull
    protected String installationName() {
        return "Minecraft";
    }

    @Override
    @NotNull
    protected Path installationDirectory(@NotNull String version) {
        return MinecraftPaths.VERSIONS.resolve(version);
    }

    @Override
    @NotNull
    protected Optional<Path> verifyInstallation(@NotNull String version, @NotNull Path installDirectory) throws IOException, InterruptedException {
        return Optional.of(this.installVersion(version, installDirectory, VerificationMode.QUICK));
    }

    @Override
    @NotNull
    protected Path installFresh(@NotNull String version, @NotNull Path installDirectory) throws IOException, InterruptedException {
        return this.installVersion(version, installDirectory, VerificationMode.QUICK);
    }

    //-----non abstract methods-----
    private Path installVersion(String version, Path versionDirectory, VerificationMode verificationMode) throws IOException, InterruptedException {

        //step 1: find 1.12.2
        String manifestText = Downloader.getString("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json");
        JsonObject manifest = JsonParser.parseString(manifestText).getAsJsonObject();

        JsonObject versionEntry = null;
        for (JsonElement element : manifest.getAsJsonArray("versions")) {
            JsonObject candidate = element.getAsJsonObject();
            if (version.equals(candidate.get("id").getAsString())) {
                versionEntry = candidate;
                break;
            }
        }

        if (versionEntry == null) {
            throw new IllegalStateException("Minecraft " + version + " is missing from the version manifest.");
        }

        //step 2: downloadVerified version json
        Path versionJson = versionDirectory.resolve(version + ".json");
        Files.createDirectories(versionDirectory);
        Downloader.downloadVerified(
                versionEntry.get("url").getAsString(),
                versionJson,
                versionEntry.get("sha1").getAsString()
        );
        String versionText = Files.readString(versionJson);
        JsonObject versionMetadata = JsonParser.parseString(versionText).getAsJsonObject();

        //step 3: downloadVerified client jar
        JsonObject client = versionMetadata.getAsJsonObject("downloads").getAsJsonObject("client");
        Path clientJar = versionDirectory.resolve(version + ".jar");
        System.out.println("Downloading Minecraft client...");
        Downloader.downloadVerified(
                client.get("url").getAsString(),
                clientJar,
                client.get("sha1").getAsString()
        );

        //step 4: downloadVerified libaries
        downloadLibraries(versionMetadata);

        //step 5: downloadVerified assets
        downloadAssets(versionMetadata, verificationMode);

        return versionDirectory;
    }

    @Override
    protected boolean installationExists(@NotNull String version, @NotNull Path installDirectory) {
        return Files.isRegularFile(installDirectory.resolve(version + ".json"))
                && Files.isRegularFile(installDirectory.resolve(version + ".jar"));
    }

    private void downloadLibraries(JsonObject version) throws IOException, InterruptedException {
        JsonArray libraries = version.getAsJsonArray("libraries");

        int count = 0;
        for (JsonElement element : libraries) {
            JsonObject library = element.getAsJsonObject();
            if (!isLibraryAllowed(library)) continue;

            JsonObject downloads = library.getAsJsonObject("downloads");
            if (downloads == null) continue;

            //normal library
            if (downloads.has("artifact")) {
                JsonObject artifact = downloads.getAsJsonObject("artifact");
                installArtifact(library, artifact);
                count++;
            }

            //native library
            if (library.has("natives") && downloads.has("classifiers")) {
                installCurrentNative(library, downloads.getAsJsonObject("classifiers"));
            }
        }

        System.out.println("Libraries installed: " + count);
    }

    private void downloadAssets(@NotNull JsonObject version, @NotNull VerificationMode verificationMode) throws IOException, InterruptedException {
        JsonObject assetIndex = version.getAsJsonObject("assetIndex");

        String assetId = assetIndex.get("id").getAsString();
        String assetIndexUrl = assetIndex.get("url").getAsString();
        Path indexPath = MinecraftPaths.ASSET_INDEXES.resolve(assetId + ".json");

        System.out.println("Downloading asset index...");

        /*
         * Always verify the asset index itself.
         */
        Downloader.downloadVerified(
                assetIndexUrl,
                indexPath,
                assetIndex.get("sha1").getAsString()
        );

        JsonObject index = JsonParser.parseString(Files.readString(indexPath)).getAsJsonObject();

        JsonObject objects = index.getAsJsonObject("objects");

        int total = objects.size();
        int current = 0;
        int checked = 0;
        for (String logicalName : objects.keySet()) {
            JsonObject object = objects.getAsJsonObject(logicalName);

            String hash = object.get("hash").getAsString();
            String prefix = hash.substring(0, 2);
            String url = "https://resources.download.minecraft.net/" + prefix + "/" + hash;
            Path destination = MinecraftPaths.ASSET_OBJECTS.resolve(prefix).resolve(hash);

            //SHA-1 verify every asset, including existing ones.
            if (verificationMode == VerificationMode.FULL) {
                Downloader.downloadVerified(url, destination, hash);
                checked++;
            }
            //assumes quick verification mode
            //download and verify missing assets, auto trust existing ones
            else if (!Files.isRegularFile(destination)) {
                Downloader.downloadVerified(url, destination, hash);
                checked++;
            }
            current++;

            if (current % 100 == 0) {
                System.out.printf("Assets: %d / %d%n", current, total);
            }
        }

        System.out.printf("Assets: %d / %d%n", total, total);

        if (verificationMode == VerificationMode.FULL) {
            System.out.println("Asset integrity check complete: " + checked + " verified.");
        }
    }

    public Path repair(String version) throws IOException, InterruptedException {
        version = this.requireVersion(version, installationName());
        System.out.println();
        System.out.println("Performing full Minecraft " + version + " integrity check...");
        return installVersion(version, installationDirectory(version), VerificationMode.FULL);
    }
}
