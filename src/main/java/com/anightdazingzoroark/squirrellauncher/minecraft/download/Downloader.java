package com.anightdazingzoroark.squirrellauncher.minecraft.download;

import com.anightdazingzoroark.squirrellauncher.SquirrelLauncher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

public final class Downloader {
    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MILLIS = 400L;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(3);
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private Downloader() {}

    @NotNull
    public static String getString(@NotNull String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url))
                        .timeout(REQUEST_TIMEOUT)
                        .header("User-Agent", SquirrelLauncher.NAME)
                        .GET().build();

        IOException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() / 100 == 2) return response.body();

                IOException failure = new IOException("HTTP " + response.statusCode() + " while requesting " + url);
                lastFailure = failure;
                if (!retryable(response.statusCode()) || attempt == MAX_ATTEMPTS) break;
            }
            catch (IOException exception) {
                if (attempt == MAX_ATTEMPTS) throw exception;
                lastFailure = exception;
            }

            System.out.println("[RETRY " + attempt + "/" + MAX_ATTEMPTS + "] " + url);
            Thread.sleep(RETRY_DELAY_MILLIS * attempt);
        }

        throw lastFailure == null ? new IOException("Could not request " + url) : lastFailure;
    }

    /**
     * Download a file without integrity verification.
     *
     * If the destination already exists, it is trusted.
     */
    public static void download(@Nullable String url, @NotNull Path destination) throws IOException, InterruptedException {
        if (Files.isRegularFile(destination)) return;

        Path parent = destination.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);

        Path temporary = destination.resolveSibling(destination.getFileName() + ".part");
        Files.deleteIfExists(temporary);

        if (url == null) throw new IOException("No URL has been supplied!");

        try {
            downloadTo(url, temporary, destination.getFileName().toString());
            replace(temporary, destination);
        }
        finally {
            Files.deleteIfExists(temporary);
        }
    }

    /**
     * Download or repair a file using its expected SHA-1.
     *
     * Existing valid files are kept.
     * Missing files are downloaded.
     * Corrupt files are replaced only after the replacement
     * has successfully passed SHA-1 verification.
     */
    public static void downloadVerified(@NotNull String url, @NotNull Path destination, @NotNull String expectedSha1) throws IOException, InterruptedException {
        //---existing destination---
        if (Files.isRegularFile(destination)) {
            if (sha1Matches(destination, expectedSha1)) return;

            System.out.println("Repairing corrupt file: " + destination.getFileName());
        }
        else System.out.println("Downloading missing file: " + destination.getFileName());

        //---destination directory---
        Path parent = destination.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);

        //---temporary download---
        Path temporary = destination.resolveSibling(destination.getFileName() + ".part");
        Files.deleteIfExists(temporary);

        try {
            downloadTo(url, temporary, destination.getFileName().toString());

            //---verify replacement---
            if (!sha1Matches(temporary, expectedSha1)) {
                String actualSha1 = sha1(temporary);
                throw new IOException("SHA-1 verification failed for " + destination
                                + "\nExpected: " + expectedSha1
                                + "\nActual:   " + actualSha1
                );
            }

            //---replacement passed verification, replace the existing destination---
            replace(temporary, destination);
        }
        finally {
            Files.deleteIfExists(temporary);
        }
    }

    /**
     * Check whether a file matches the supplied SHA-1.
     */
    public static boolean sha1Matches(@NotNull Path file, @Nullable String expectedSha1) throws IOException {
        if (!Files.isRegularFile(file)) return false;

        //No hash is available, but file exists
        if (expectedSha1 == null || expectedSha1.isBlank()) return true;

        String actualSha1 = sha1(file);
        return actualSha1.equalsIgnoreCase(expectedSha1);
    }

    /**
     * Calculate SHA-1 for a file.
     */
    @NotNull
    public static String sha1(@NotNull Path file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-1");
        }
        catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 unavailable", e);
        }

        try (InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];

            int count;
            while ((count = input.read(buffer)) != -1) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedIOException("Integrity check was stopped.");
                }
                digest.update(buffer, 0, count);
            }
        }

        return HexFormat.of().formatHex(digest.digest());
    }

    private static void downloadTo(
            @NotNull String url,
            @NotNull Path destination,
            @NotNull String displayName
    ) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("User-Agent", SquirrelLauncher.NAME)
                .GET().build();
        IOException lastFailure = null;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            Files.deleteIfExists(destination);
            try {
                HttpResponse<Path> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofFile(destination));
                if (response.statusCode() / 100 == 2) return;

                IOException failure = new IOException("HTTP " + response.statusCode() + " while downloading " + url);
                lastFailure = failure;
                if (!retryable(response.statusCode()) || attempt == MAX_ATTEMPTS) break;
            }
            catch (IOException exception) {
                if (attempt == MAX_ATTEMPTS) throw exception;
                lastFailure = exception;
            }

            System.out.println("[RETRY " + attempt + "/" + MAX_ATTEMPTS + "] " + displayName);
            Thread.sleep(RETRY_DELAY_MILLIS * attempt);
        }

        throw lastFailure == null ? new IOException("Could not download " + url) : lastFailure;
    }

    private static boolean retryable(int statusCode) {
        return statusCode == 408 || statusCode == 425 || statusCode == 429 || statusCode / 100 == 5;
    }

    /**
     * Replace a file atomically when supported by the
     * underlying filesystem.
     */
    private static void replace(@NotNull Path source, @NotNull Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        }
        catch (AtomicMoveNotSupportedException e) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
