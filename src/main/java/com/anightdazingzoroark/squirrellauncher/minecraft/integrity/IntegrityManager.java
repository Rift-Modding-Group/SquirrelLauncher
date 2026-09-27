package com.anightdazingzoroark.squirrellauncher.minecraft.integrity;

import com.anightdazingzoroark.squirrellauncher.minecraft.download.Downloader;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class IntegrityManager {
    private static final int DOWNLOAD_WORKERS = 6;
    private static final int MAX_VERIFICATION_CACHE_ENTRIES = 512;
    private static final Map<Path, VerifiedFile> VERIFIED_FILES = new ConcurrentHashMap<>();

    private IntegrityManager() {}

    @NotNull
    public static IntegrityState check(@NotNull FileRequirement requirement) throws IOException {
        Path file = requirement.destination().toAbsolutePath().normalize();
        if (!Files.isRegularFile(file)) {
            VERIFIED_FILES.remove(file);
            return IntegrityState.MISSING;
        }

        //size check before hashing
        long actualSize = Files.size(file);
        if (requirement.size() != null) {
            if (actualSize != requirement.size()) {
                VERIFIED_FILES.remove(file);
                return IntegrityState.CORRUPT;
            }
        }

        //SHA-1 is authoritative when supplied
        if (requirement.sha1() != null && !requirement.sha1().isBlank()) {
            FileTime modifiedTime = Files.getLastModifiedTime(file);
            VerifiedFile verified = VERIFIED_FILES.get(file);
            if (verified != null
                    && verified.size() == actualSize
                    && verified.modifiedTime().equals(modifiedTime)
                    && verified.sha1().equalsIgnoreCase(requirement.sha1())) {
                return IntegrityState.VALID;
            }

            String actualSha1 = HashUtil.sha1(file);
            if (!actualSha1.equalsIgnoreCase(requirement.sha1())) {
                VERIFIED_FILES.remove(file);
                return IntegrityState.CORRUPT;
            }
            if (VERIFIED_FILES.size() >= MAX_VERIFICATION_CACHE_ENTRIES) VERIFIED_FILES.clear();
            VERIFIED_FILES.put(file, new VerifiedFile(actualSize, modifiedTime, requirement.sha1()));
        }

        return IntegrityState.VALID;
    }

    public static void ensureAll(@NotNull List<FileRequirement> requirements, @NotNull String progressName) throws Exception {
        Map<Path, FileRequirement> unique = new LinkedHashMap<>();
        for (FileRequirement requirement : requirements) {
            Path destination = requirement.destination().toAbsolutePath().normalize();
            FileRequirement normalized = new FileRequirement(
                    destination, requirement.url(), requirement.sha1(), requirement.size()
            );
            FileRequirement existing = unique.get(destination);
            if (existing == null) {
                unique.put(destination, normalized);
                continue;
            }

            boolean conflictingHash = existing.sha1() != null && normalized.sha1() != null
                    && !existing.sha1().equalsIgnoreCase(normalized.sha1());
            boolean conflictingSize = existing.size() != null && normalized.size() != null
                    && !existing.size().equals(normalized.size());
            boolean unverifiableConflict = (existing.sha1() == null || normalized.sha1() == null)
                    && !Objects.equals(existing.url(), normalized.url());
            if (conflictingHash || conflictingSize || unverifiableConflict) {
                throw new IOException("Conflicting library metadata for " + destination);
            }
            if ((existing.sha1() == null && normalized.sha1() != null)
                    || (existing.size() == null && normalized.size() != null)
                    || (existing.url() == null && normalized.url() != null)) {
                unique.put(destination, normalized);
            }
        }

        if (unique.isEmpty()) return;

        List<FileRequirement> downloads = new ArrayList<>(unique.values());
        long totalBytes = 0L;
        boolean sizesKnown = true;
        for (FileRequirement requirement : downloads) {
            if (requirement.size() != null && requirement.size() > 0L) totalBytes += requirement.size();
            else sizesKnown = false;
        }

        int workerCount = Math.min(DOWNLOAD_WORKERS, downloads.size());
        ExecutorService executor = Executors.newFixedThreadPool(workerCount);
        CompletionService<Long> completion = new ExecutorCompletionService<>(executor);
        List<Future<Long>> futures = new ArrayList<>(downloads.size());
        for (FileRequirement requirement : downloads) {
            futures.add(completion.submit(() -> {
                ensure(requirement);
                if (requirement.size() != null && requirement.size() > 0L) return requirement.size();
                return Files.size(requirement.destination());
            }));
        }

        long completedBytes = 0L;
        try {
            for (int completed = 1; completed <= downloads.size(); completed++) {
                try {
                    completedBytes += completion.take().get();
                }
                catch (ExecutionException exception) {
                    Throwable cause = exception.getCause();
                    if (cause instanceof IOException ioException) throw ioException;
                    if (cause instanceof InterruptedException interruptedException) throw interruptedException;
                    if (cause instanceof RuntimeException runtimeException) throw runtimeException;
                    throw new IOException("Could not prepare libraries.", cause);
                }
                if (completed % 5 != 0 && completed != downloads.size()) continue;
                if (sizesKnown) {
                    System.out.printf(
                            "%s: %d / %d (%.1f / %.1f MiB)%n",
                            progressName,
                            completed,
                            downloads.size(),
                            completedBytes / 1_048_576.0,
                            totalBytes / 1_048_576.0
                    );
                }
                else System.out.printf("%s: %d / %d%n", progressName, completed, downloads.size());
            }
        }
        finally {
            for (Future<Long> future : futures) future.cancel(true);
            executor.shutdownNow();
            try {
                executor.awaitTermination(5L, TimeUnit.SECONDS);
            }
            catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static boolean ensure(@NotNull FileRequirement requirement) throws Exception {
        IntegrityState state = check(requirement);
        if (state == IntegrityState.VALID) return false;

        if (requirement.url() == null || requirement.url().isBlank()) {
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
            Path temporaryKey = temporary.toAbsolutePath().normalize();
            VerifiedFile verified = VERIFIED_FILES.remove(temporaryKey);
            if (verified != null) {
                Path destinationKey = destination.toAbsolutePath().normalize();
                VERIFIED_FILES.put(
                        destinationKey,
                        new VerifiedFile(verified.size(), Files.getLastModifiedTime(destinationKey), verified.sha1())
                );
            }
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

    private record VerifiedFile(long size, @NotNull FileTime modifiedTime, @NotNull String sha1) {}
}
