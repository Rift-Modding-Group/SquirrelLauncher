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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
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
        String versionId = ForgeConstants.versionId(version);
        JsonObject forgeMetadata = InstallUtils.readJson(installDirectory.resolve(versionId + ".json"));
        return this.repairForgeLibraries(forgeMetadata) ? Optional.of(installDirectory) : Optional.empty();
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

        Process process = builder.start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("[Forge Installer] " + line);
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("Forge installer exited with code " + exitCode);
        }

        if (!Files.exists(forgeVersionJson)) {
            throw new IOException("Forge installer completed, but the Forge version JSON was not created:\n" + forgeVersionJson);
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

    private boolean repairForgeLibraries(@NotNull JsonObject metadata) throws IOException, InterruptedException {
        if (!metadata.has("libraries")) return false;

        JsonArray libraries = metadata.getAsJsonArray("libraries");
        for (JsonElement element : libraries) {
            JsonObject library = element.getAsJsonObject();
            if (!this.repairLibrary(library)) return false;
        }

        return true;
    }
}
