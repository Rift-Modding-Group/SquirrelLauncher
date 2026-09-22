package com.anightdazingzoroark.squirrellauncher.minecraft.integrity;

import com.anightdazingzoroark.squirrellauncher.minecraft.download.Downloader;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class IntegrityManager {
    private IntegrityManager() {}

    @NotNull
    public static IntegrityState check(@NotNull FileRequirement requirement) throws IOException {
        Path file = requirement.destination();
        if (!Files.isRegularFile(file)) return IntegrityState.MISSING;

        //size check before hashing
        if (requirement.size() != null) {
            long actualSize = Files.size(file);
            if (actualSize != requirement.size()) {
                return IntegrityState.CORRUPT;
            }
        }

        //SHA-1 is authoritative when supplied
        if (requirement.sha1() != null && !requirement.sha1().isBlank()) {
            String actualSha1 = HashUtil.sha1(file);
            if (!actualSha1.equalsIgnoreCase(requirement.sha1())) {
                return IntegrityState.CORRUPT;
            }
        }

        return IntegrityState.VALID;
    }

    public static boolean ensure(@NotNull FileRequirement requirement) throws Exception {
        IntegrityState state = check(requirement);
        if (state == IntegrityState.VALID) return false;

        if (requirement.url() == null|| requirement.url().isBlank()) {
            throw new IOException("Cannot repair " + requirement.destination() + ": no download URL is available.");
        }

        String missingFileNotice = "Downloading missing file: " + requirement.destination().getFileName();
        String corruptFileNotice = "Repairing corrupt file: " + requirement.destination().getFileName();
        System.out.println(state == IntegrityState.MISSING ? missingFileNotice : corruptFileNotice);
        downloadVerified(requirement);

        return true;
    }

    private static void downloadVerified(@NotNull FileRequirement requirement) throws Exception {
        Path destination = requirement.destination();
        Path parent = destination.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);

        Path temporary = destination.resolveSibling(destination.getFileName() + ".part");
        Files.deleteIfExists(temporary);
        try {
            Downloader.download(requirement.url(), temporary);
            //verify the temporary download before replacing the existing file
            FileRequirement temporaryRequirement = new FileRequirement(
                    temporary, requirement.url(),
                    requirement.sha1(), requirement.size()
            );

            IntegrityState downloadedState = check(temporaryRequirement);
            if (downloadedState != IntegrityState.VALID) {
                throw new IOException("Downloaded file failed integrity verification: " + destination);
            }

            replace(temporary, destination);
        }
        finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void replace(@NotNull Path source, @NotNull Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        }
        catch (AtomicMoveNotSupportedException e) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}