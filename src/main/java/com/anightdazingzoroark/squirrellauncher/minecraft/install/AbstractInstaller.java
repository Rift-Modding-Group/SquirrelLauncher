package com.anightdazingzoroark.squirrellauncher.minecraft.install;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
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
        version = this.requireVersion(version, this.installationName());
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

}
