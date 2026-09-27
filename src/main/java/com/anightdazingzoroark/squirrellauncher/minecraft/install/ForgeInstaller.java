package com.anightdazingzoroark.squirrellauncher.minecraft.install;

import com.anightdazingzoroark.squirrellauncher.minecraft.InstallUtils;
import com.anightdazingzoroark.squirrellauncher.minecraft.MinecraftPaths;
import com.anightdazingzoroark.squirrellauncher.minecraft.download.Downloader;
import com.anightdazingzoroark.squirrellauncher.minecraft.runtime.JavaRuntime;
import com.anightdazingzoroark.squirrellauncher.minecraft.runtime.JavaRuntimeManager;
import com.anightdazingzoroark.squirrellauncher.minecraft.runtime.JavaVersion;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public final class ForgeInstaller extends AbstractInstaller {
    //-----abstract overrides-----
    @Override
    @NotNull
    protected String installationName() {
        return "Forge";
    }

    @Override
    @NotNull
    protected Path installationDirectory(@NotNull String version) {
        return MinecraftPaths.VERSIONS.resolve(ForgeConstants.versionId(version));
    }

    @Override
    @NotNull
    protected Optional<Path> verifyInstallation(@NotNull String version, @NotNull Path installDirectory) throws IOException, InterruptedException {
        return this.isForgeInstallationValid(version, installDirectory)
                ? Optional.of(installDirectory)
                : Optional.empty();
    }

    @Override
    @NotNull
    protected Path installFresh(@NotNull String version, @NotNull Path forgeVersionDir) throws IOException, InterruptedException {
        String fullVersion = ForgeConstants.fullVersion(version);
        String versionId = ForgeConstants.versionId(version);
        String installerUrl = ForgeConstants.installerUrl(version);

        Path forgeVersionJson = forgeVersionDir.resolve(versionId + ".json");

        //Download official Forge installer
        Path installerDir = MinecraftPaths.INSTALLERS.resolve("forge");
        Files.createDirectories(installerDir);
        Path installer = installerDir.resolve("forge-" + fullVersion + "-installer.jar");
        Downloader.download(installerUrl, installer);

        /*
         * ------------------------------------------------------
         * Forge expects the target to look like a launcher
         * directory.
         *
         * Its client installer explicitly checks for
         * launcher_profiles.json.
         *
         * We give it a minimal SquirrelLauncher-owned one.
         * ------------------------------------------------------
         */

        Files.createDirectories(MinecraftPaths.ROOT);
        Path launcherProfiles = MinecraftPaths.ROOT.resolve("launcher_profiles.json");

        if (!Files.exists(launcherProfiles)) {
            Files.writeString(
                    launcherProfiles,
                    """
                    {
                      "profiles": {}
                    }
                    """
            );
        }

        /*
         * ------------------------------------------------------
         * Run Forge's installer headlessly
         * ------------------------------------------------------
         */

        JavaRuntime javaRuntime = JavaRuntimeManager.resolve(JavaVersion.JAVA_8);
        Path java8 = javaRuntime.executable();
        ProcessBuilder builder = new ProcessBuilder(
                java8.toString(),
                "-jar",
                installer.toAbsolutePath().toString(),
                "--installClient",
                MinecraftPaths.ROOT.toAbsolutePath().toString()
        );

        builder.directory(MinecraftPaths.ROOT.toFile());
        builder.redirectErrorStream(true);
        builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);

        System.out.println("Installing Forge package...");
        Process process = builder.start();
        int exitCode;
        try {
            exitCode = process.waitFor();
        }
        catch (InterruptedException exception) {
            process.destroyForcibly();
            throw exception;
        }

        if (exitCode != 0) {
            throw new IOException("Forge installer exited with code " + exitCode);
        }

        if (!this.isForgeInstallationValid(version, forgeVersionDir)) {
            throw new IOException(
                    "Forge installer completed, but the Forge installation is incomplete:\n" + forgeVersionJson
            );
        }

        return forgeVersionDir;
    }

    //-----non abstract methods-----
    @Override
    protected void removeInstallation(@NotNull String version, @NotNull Path installDirectory) throws IOException {
        String versionId = ForgeConstants.versionId(version);
        Files.deleteIfExists(installDirectory.resolve(versionId + ".json"));
    }

    @Override
    protected boolean installationExists(@NotNull String version, @NotNull Path installDirectory) {
        String versionId = ForgeConstants.versionId(version);
        return Files.isRegularFile(installDirectory.resolve(versionId + ".json"));
    }

    private boolean isForgeInstallationValid(@NotNull String version, @NotNull Path installDirectory) throws IOException {
        String fullVersion = ForgeConstants.fullVersion(version);
        String versionId = ForgeConstants.versionId(version);
        Path metadataFile = installDirectory.resolve(versionId + ".json");
        if (!Files.isRegularFile(metadataFile)) return false;

        JsonObject forgeMetadata = InstallUtils.readJson(metadataFile);
        if (!forgeMetadata.has("libraries")) return false;

        String forgeCoordinate = "net.minecraftforge:forge:" + fullVersion;
        JsonArray libraries = forgeMetadata.getAsJsonArray("libraries");
        for (JsonElement element : libraries) {
            JsonObject library = element.getAsJsonObject();
            if (!library.has("name") || !forgeCoordinate.equals(library.get("name").getAsString())) continue;
            if (!library.has("downloads")) return false;

            JsonObject downloads = library.getAsJsonObject("downloads");
            if (!downloads.has("artifact")) return false;

            JsonObject artifact = downloads.getAsJsonObject("artifact");
            String relativePath = artifact.has("path")
                    ? artifact.get("path").getAsString()
                    : "net/minecraftforge/forge/" + fullVersion + "/forge-" + fullVersion + ".jar";
            Path forgeJar = MinecraftPaths.LIBRARIES.resolve(relativePath);
            if (!artifact.has("sha1")) return Files.isRegularFile(forgeJar) && InstallUtils.isValidArchive(forgeJar);
            return Downloader.sha1Matches(forgeJar, artifact.get("sha1").getAsString());
        }

        return false;
    }
}
