package com.anightdazingzoroark.squirrellauncher.minecraft.download;

import com.anightdazingzoroark.squirrellauncher.SquirrelLauncher;
import com.anightdazingzoroark.squirrellauncher.minecraft.GitHubAuthentication;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.HttpRetryException;
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
import java.util.Map;

public final class Downloader {
    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MILLIS = 400L;
    @NotNull
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);
    @NotNull
    private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(3);
    @NotNull
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Downloader.CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private Downloader() {}

    @NotNull
    public static String getString(@NotNull String url) throws IOException, InterruptedException {
        HttpRequest request = Downloader.createRequest(url, Map.of());
        IOException failure = new IOException("Could not request " + url);
        for (int attempt = 1; attempt <= Downloader.MAX_ATTEMPTS; attempt++) {
            try {
                HttpResponse<String> response = Downloader.CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() / 100 == 2) return response.body();
                failure = response.statusCode() == 403
                        ? new HttpRetryException("HTTP 403 while requesting " + url, response.statusCode())
                        : new IOException("HTTP " + response.statusCode() + " while requesting " + url);
                if (Downloader.isTerminalStatus(response.statusCode())) break;
            }
            catch (IOException exception) {
                failure = exception;
            }
            if (attempt < Downloader.MAX_ATTEMPTS) Downloader.waitBeforeRetry(url, attempt);
        }
        throw failure;
    }

    /**
     * Downloads a file without integrity verification. An existing regular file is retained.
     */
    public static void download(@Nullable String url, @NotNull Path destination) throws IOException, InterruptedException {
        if (Files.isRegularFile(destination)) return;
        if (url == null || url.isBlank()) throw new IOException("No download URL was supplied.");

        Path temporary = Downloader.prepareTemporaryFile(destination);
        try {
            Downloader.downloadTo(url, temporary, destination.getFileName().toString(), Map.of());
            Downloader.replace(temporary, destination);
        }
        finally {
            Files.deleteIfExists(temporary);
        }
    }

    /**
     * Downloads or repairs a file and verifies it when an expected SHA-1 is available.
     */
    public static void downloadVerified(
            @NotNull String url, @NotNull Path destination, @Nullable String expectedSha1
    ) throws IOException, InterruptedException {
        Downloader.downloadVerified(url, destination, expectedSha1, Map.of());
    }

    /**
     * Downloads or repairs a file using additional HTTP request headers.
     */
    public static void downloadVerified(
            @NotNull String url,
            @NotNull Path destination,
            @Nullable String expectedSha1,
            @NotNull Map<String, String> requestHeaders
    ) throws IOException, InterruptedException {
        boolean existingFile = Files.isRegularFile(destination);
        if (existingFile && Downloader.sha1Matches(destination, expectedSha1)) return;

        System.out.println((existingFile
                ? "Repairing corrupt file: "
                : "Downloading missing file: ") + destination.getFileName());
        Path temporary = Downloader.prepareTemporaryFile(destination);
        try {
            Downloader.downloadTo(url, temporary, destination.getFileName().toString(), requestHeaders);
            if (expectedSha1 != null && !expectedSha1.isBlank()) {
                String actualSha1 = Downloader.sha1(temporary);
                if (!actualSha1.equalsIgnoreCase(expectedSha1)) {
                    throw new IOException(
                            "SHA-1 verification failed for " + destination
                                    + "\nExpected: " + expectedSha1
                                    + "\nActual:   " + actualSha1
                    );
                }
            }
            Downloader.replace(temporary, destination);
        }
        finally {
            Files.deleteIfExists(temporary);
        }
    }

    public static boolean sha1Matches(@NotNull Path file, @Nullable String expectedSha1) throws IOException {
        if (!Files.isRegularFile(file)) return false;
        return expectedSha1 == null || expectedSha1.isBlank() || Downloader.sha1(file).equalsIgnoreCase(expectedSha1);
    }

    @NotNull
    public static String sha1(@NotNull Path file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-1");
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-1 unavailable", exception);
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

    @NotNull
    private static Path prepareTemporaryFile(@NotNull Path destination) throws IOException {
        Path parent = destination.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        Path temporary = destination.resolveSibling(destination.getFileName() + ".part");
        Files.deleteIfExists(temporary);
        return temporary;
    }

    private static void downloadTo(
            @NotNull String url,
            @NotNull Path destination,
            @NotNull String displayName,
            @NotNull Map<String, String> requestHeaders
    ) throws IOException, InterruptedException {
        HttpRequest request = Downloader.createRequest(url, requestHeaders);
        IOException failure = new IOException("Could not download " + url);
        for (int attempt = 1; attempt <= Downloader.MAX_ATTEMPTS; attempt++) {
            Files.deleteIfExists(destination);
            try {
                HttpResponse<Path> response = Downloader.CLIENT.send(
                        request, HttpResponse.BodyHandlers.ofFile(destination)
                );
                if (response.statusCode() / 100 == 2) return;
                failure = new IOException("HTTP " + response.statusCode() + " while downloading " + url);
                if (Downloader.isTerminalStatus(response.statusCode())) break;
            }
            catch (IOException exception) {
                failure = exception;
            }
            if (attempt < Downloader.MAX_ATTEMPTS) Downloader.waitBeforeRetry(displayName, attempt);
        }
        throw failure;
    }

    @NotNull
    private static HttpRequest createRequest(@NotNull String url, @NotNull Map<String, String> requestHeaders) throws IOException {
        try {
            URI uri = URI.create(url);
            HttpRequest.Builder request = HttpRequest.newBuilder(uri)
                    .timeout(Downloader.REQUEST_TIMEOUT)
                    .header("User-Agent", SquirrelLauncher.NAME);
            GitHubAuthentication.authenticate(request, uri);
            for (Map.Entry<String, String> header : requestHeaders.entrySet()) {
                request.header(header.getKey(), header.getValue());
            }
            return request.GET().build();
        }
        catch (IllegalArgumentException exception) {
            throw new IOException("Invalid download URL or request header.", exception);
        }
    }

    private static void waitBeforeRetry(@NotNull String displayName, int attempt) throws InterruptedException {
        System.out.println("[RETRY " + attempt + "/" + Downloader.MAX_ATTEMPTS + "] " + displayName);
        Thread.sleep(Downloader.RETRY_DELAY_MILLIS * attempt);
    }

    private static boolean isTerminalStatus(int statusCode) {
        return statusCode != 408 && statusCode != 425 && statusCode != 429 && statusCode / 100 != 5;
    }

    private static void replace(@NotNull Path source, @NotNull Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        }
        catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
