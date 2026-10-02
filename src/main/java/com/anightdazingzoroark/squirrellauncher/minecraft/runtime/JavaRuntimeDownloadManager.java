package com.anightdazingzoroark.squirrellauncher.minecraft.runtime;

import com.anightdazingzoroark.squirrellauncher.minecraft.MinecraftPaths;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.IntConsumer;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class JavaRuntimeDownloadManager {
    @NotNull
    private final HttpClient httpClient;
    @NotNull
    private final Map<String, String> vendors;

    public JavaRuntimeDownloadManager() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();
        this.vendors = Map.of(
                "corretto", "Amazon Corretto",
                "liberica", "BellSoft Liberica",
                "microsoft", "Microsoft OpenJDK",
                "semeru", "IBM Semeru",
                "temurin", "Eclipse Temurin",
                "zulu", "Azul Zulu"
        );
    }

    @NotNull
    public List<JavaPackage> availablePackages(@NotNull JavaVersion javaVersion) throws IOException {
        String operatingSystem;
        String archiveType;
        @Nullable String libraryType = null;
        String systemName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (systemName.contains("win")) {
            operatingSystem = "windows";
            archiveType = "zip";
        }
        else if (systemName.contains("mac")) {
            operatingSystem = "macos";
            archiveType = "tar.gz";
        }
        else if (systemName.contains("linux")) {
            boolean musl = Files.isRegularFile(Path.of("/etc/alpine-release"));
            operatingSystem = musl ? "linux_musl" : "linux";
            libraryType = musl ? "musl" : "glibc";
            archiveType = "tar.gz";
        }
        else {
            throw new IOException("Java downloads are not available for this operating system: " + systemName);
        }

        String systemArchitecture = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        String architecture;
        if (systemArchitecture.equals("amd64") || systemArchitecture.equals("x86_64")) architecture = "x64";
        else if (systemArchitecture.equals("aarch64") || systemArchitecture.equals("arm64")) architecture = "aarch64";
        else if (systemArchitecture.equals("x86") || systemArchitecture.matches("i[3-6]86")) architecture = "x86";
        else throw new IOException("Java downloads are not available for this architecture: " + systemArchitecture);

        int nextVersion = javaVersion.major() + 1;
        String uri = "https://api.foojay.io/disco/v3.0/packages"
                + "?version=" + javaVersion.major() + "..%3C" + nextVersion
                + "&distribution=corretto,liberica,microsoft,semeru,temurin,zulu"
                + "&operating_system=" + operatingSystem
                + "&architecture=" + architecture
                + "&archive_type=" + archiveType
                + "&package_type=jre"
                + (libraryType == null ? "" : "&lib_c_type=" + libraryType)
                + "&release_status=ga"
                + "&directly_downloadable=true"
                + "&free_to_use_in_production=true"
                + "&javafx_bundled=false";
        HttpRequest request = HttpRequest.newBuilder(URI.create(uri))
                .timeout(Duration.ofSeconds(45))
                .header("Accept", "application/json")
                .header("User-Agent", "SquirrelLauncher")
                .GET()
                .build();

        HttpResponse<InputStream> response;
        try {
            response = this.httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while loading Java downloads.", exception);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            response.body().close();
            throw new IOException("The Java package service returned HTTP " + response.statusCode() + ".");
        }

        Map<String, JavaPackage> packages = new HashMap<>();
        try (InputStream body = response.body();
             JsonReader reader = new JsonReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
            reader.beginObject();
            while (reader.hasNext()) {
                if (!reader.nextName().equals("result")) {
                    reader.skipValue();
                    continue;
                }
                reader.beginArray();
                while (reader.hasNext()) {
                    JsonObject packageData = JsonParser.parseReader(reader).getAsJsonObject();
                    boolean incompatibleLibrary = libraryType != null && (!packageData.has("lib_c_type")
                            || !packageData.get("lib_c_type").getAsString().equals(libraryType));
                    if (!packageData.has("major_version")
                            || packageData.get("major_version").getAsInt() != javaVersion.major()
                            || !packageData.has("latest_build_available")
                            || !packageData.get("latest_build_available").getAsBoolean()
                            || incompatibleLibrary) continue;
                    String distribution = packageData.get("distribution").getAsString();
                    String vendor = this.vendors.get(distribution);
                    if (vendor == null) continue;
                    String packageType = packageData.get("package_type").getAsString();
                    if (!packageType.equals("jre")) continue;
                    String release = packageData.get("java_version").getAsString();
                    String id = packageData.get("id").getAsString();
                    long size = packageData.has("size") ? packageData.get("size").getAsLong() : -1L;
                    JsonObject links = packageData.getAsJsonObject("links");
                    JavaPackage candidate = new JavaPackage(
                            id,
                            javaVersion,
                            distribution,
                            vendor,
                            release,
                            packageType.toUpperCase(Locale.ROOT),
                            packageData.get("archive_type").getAsString(),
                            size,
                            URI.create(links.get("pkg_info_uri").getAsString()),
                            URI.create(links.get("pkg_download_redirect").getAsString())
                    );
                    JavaPackage saved = packages.get(distribution);
                    if (saved == null || (size > 0L && (saved.size() <= 0L || size < saved.size()))) {
                        packages.put(distribution, candidate);
                    }
                }
                reader.endArray();
            }
            reader.endObject();
        }
        List<JavaPackage> result = new ArrayList<>(packages.values());
        result.sort(Comparator.comparing(JavaPackage::vendor));
        return List.copyOf(result);
    }

    @NotNull
    public JavaRuntime install(@NotNull JavaPackage javaPackage, @NotNull IntConsumer progressListener)
            throws IOException {
        Path versionDirectory = MinecraftPaths.RUNTIMES.resolve(javaPackage.javaVersion().directoryName());
        Files.createDirectories(versionDirectory);
        String directoryName = (javaPackage.distribution() + "-" + javaPackage.release() + "-"
                + javaPackage.id().substring(0, Math.min(8, javaPackage.id().length())))
                .replaceAll("[^A-Za-z0-9._-]", "_");
        Path destination = versionDirectory.resolve(directoryName);
        Path destinationExecutable = destination.resolve("bin").resolve(JavaRuntimeManager.executableName());
        if (Files.isRegularFile(destinationExecutable)) {
            return JavaRuntimeManager.resolve(javaPackage.javaVersion(), destinationExecutable);
        }
        if (Files.exists(destination)) {
            destination = versionDirectory.resolve(directoryName + "-" + System.nanoTime());
            destinationExecutable = destination.resolve("bin").resolve(JavaRuntimeManager.executableName());
        }

        Path archive = Files.createTempFile(versionDirectory, ".java-download-", "." + javaPackage.archiveType());
        Path staging = Files.createTempDirectory(versionDirectory, ".java-extract-");
        boolean installed = false;
        try {
            String expectedChecksum = null;
            HttpRequest detailsRequest = HttpRequest.newBuilder(javaPackage.detailsUri())
                    .timeout(Duration.ofSeconds(30))
                    .header("Accept", "application/json")
                    .header("User-Agent", "SquirrelLauncher")
                    .GET()
                    .build();
            try {
                HttpResponse<String> detailsResponse = this.httpClient.send(
                        detailsRequest,
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
                );
                if (detailsResponse.statusCode() >= 200 && detailsResponse.statusCode() < 300) {
                    JsonObject details = JsonParser.parseString(detailsResponse.body()).getAsJsonObject();
                    if (details.has("result") && !details.getAsJsonArray("result").isEmpty()) {
                        JsonObject packageDetails = details.getAsJsonArray("result").get(0).getAsJsonObject();
                        if (packageDetails.has("checksum_type")
                                && packageDetails.get("checksum_type").getAsString().equalsIgnoreCase("sha256")
                                && packageDetails.has("checksum")) {
                            String checksum = packageDetails.get("checksum").getAsString().trim();
                            if (!checksum.isEmpty()) expectedChecksum = checksum;
                        }
                    }
                }
            }
            catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while loading Java package details.", exception);
            }

            HttpRequest request = HttpRequest.newBuilder(javaPackage.downloadUri())
                    .timeout(Duration.ofMinutes(10))
                    .header("User-Agent", "SquirrelLauncher")
                    .GET()
                    .build();
            HttpResponse<InputStream> response;
            try {
                response = this.httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            }
            catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("Java download was interrupted.", exception);
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                response.body().close();
                throw new IOException("The Java download returned HTTP " + response.statusCode() + ".");
            }
            long expectedSize = response.headers().firstValueAsLong("Content-Length").orElse(javaPackage.size());
            MessageDigest digest;
            try {
                digest = MessageDigest.getInstance("SHA-256");
            }
            catch (NoSuchAlgorithmException exception) {
                throw new IOException("SHA-256 is unavailable.", exception);
            }
            try (InputStream input = new BufferedInputStream(response.body());
                 OutputStream output = new BufferedOutputStream(Files.newOutputStream(archive))) {
                byte[] buffer = new byte[64 * 1024];
                long downloaded = 0L;
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    if (Thread.currentThread().isInterrupted()) throw new IOException("Java download was cancelled.");
                    if (read == 0) continue;
                    output.write(buffer, 0, read);
                    digest.update(buffer, 0, read);
                    downloaded += read;
                    if (expectedSize > 0L) {
                        progressListener.accept((int) Math.min(100L, downloaded * 100L / expectedSize));
                    }
                }
            }
            if (expectedChecksum != null) {
                String actualChecksum = HexFormat.of().formatHex(digest.digest());
                if (!actualChecksum.equalsIgnoreCase(expectedChecksum)) {
                    throw new IOException("The downloaded Java archive failed its SHA-256 checksum.");
                }
            }

            if (javaPackage.archiveType().equals("zip")) {
                try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(Files.newInputStream(archive)))) {
                    ZipEntry entry;
                    while ((entry = zip.getNextEntry()) != null) {
                        Path target = staging.resolve(entry.getName()).normalize();
                        if (!target.startsWith(staging)) throw new IOException("Unsafe path in Java archive: " + entry.getName());
                        if (entry.isDirectory()) Files.createDirectories(target);
                        else {
                            Files.createDirectories(target.getParent());
                            Files.copy(zip, target, StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                }
            }
            else {
                try (InputStream file = new BufferedInputStream(Files.newInputStream(archive));
                     GzipCompressorInputStream gzip = new GzipCompressorInputStream(file);
                     TarArchiveInputStream tar = new TarArchiveInputStream(gzip)) {
                    TarArchiveEntry entry;
                    while ((entry = tar.getNextEntry()) != null) {
                        Path target = staging.resolve(entry.getName()).normalize();
                        if (!target.startsWith(staging)) throw new IOException("Unsafe path in Java archive: " + entry.getName());
                        if (entry.isDirectory()) Files.createDirectories(target);
                        else if (entry.isSymbolicLink()) {
                            Path link = Path.of(entry.getLinkName());
                            if (link.isAbsolute() || !target.getParent().resolve(link).normalize().startsWith(staging)) {
                                throw new IOException("Unsafe link in Java archive: " + entry.getName());
                            }
                            Files.createDirectories(target.getParent());
                            try {
                                Files.createSymbolicLink(target, link);
                            }
                            catch (FileAlreadyExistsException ignored) {}
                        }
                        else if (entry.isLink()) {
                            Path link = staging.resolve(entry.getLinkName()).normalize();
                            if (!link.startsWith(staging)) {
                                throw new IOException("Unsafe hard link in Java archive: " + entry.getName());
                            }
                            Files.createDirectories(target.getParent());
                            Files.createLink(target, link);
                        }
                        else if (entry.isFile()) {
                            Files.createDirectories(target.getParent());
                            Files.copy(tar, target, StandardCopyOption.REPLACE_EXISTING);
                            try {
                                Set<PosixFilePermission> permissions = EnumSet.noneOf(PosixFilePermission.class);
                                int mode = entry.getMode();
                                if ((mode & 0400) != 0) permissions.add(PosixFilePermission.OWNER_READ);
                                if ((mode & 0200) != 0) permissions.add(PosixFilePermission.OWNER_WRITE);
                                if ((mode & 0100) != 0) permissions.add(PosixFilePermission.OWNER_EXECUTE);
                                if ((mode & 0040) != 0) permissions.add(PosixFilePermission.GROUP_READ);
                                if ((mode & 0020) != 0) permissions.add(PosixFilePermission.GROUP_WRITE);
                                if ((mode & 0010) != 0) permissions.add(PosixFilePermission.GROUP_EXECUTE);
                                if ((mode & 0004) != 0) permissions.add(PosixFilePermission.OTHERS_READ);
                                if ((mode & 0002) != 0) permissions.add(PosixFilePermission.OTHERS_WRITE);
                                if ((mode & 0001) != 0) permissions.add(PosixFilePermission.OTHERS_EXECUTE);
                                Files.setPosixFilePermissions(target, permissions);
                            }
                            catch (UnsupportedOperationException ignored) {}
                        }
                    }
                }
            }

            Path extractedExecutable;
            try (Stream<Path> paths = Files.walk(staging, 8)) {
                extractedExecutable = paths
                        .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                        .filter(path -> path.getFileName().toString().equals(JavaRuntimeManager.executableName()))
                        .filter(path -> path.getParent() != null && path.getParent().getFileName().toString().equals("bin"))
                        .findFirst()
                        .orElseThrow(() -> new IOException("The downloaded archive does not contain a Java executable."));
            }
            JavaRuntimeManager.resolve(javaPackage.javaVersion(), extractedExecutable);
            Path javaHome = extractedExecutable.getParent().getParent();
            try {
                Files.move(javaHome, destination, StandardCopyOption.ATOMIC_MOVE);
            }
            catch (AtomicMoveNotSupportedException exception) {
                Files.move(javaHome, destination);
            }
            installed = true;
            progressListener.accept(100);
            return JavaRuntimeManager.resolve(javaPackage.javaVersion(), destinationExecutable);
        }
        finally {
            Files.deleteIfExists(archive);
            if (Files.exists(staging)) JavaRuntimeDownloadManager.deleteDirectory(staging);
            if (!installed && Files.exists(destination)) JavaRuntimeDownloadManager.deleteDirectory(destination);
        }
    }

    private static void deleteDirectory(@NotNull Path directory) throws IOException {
        try (Stream<Path> paths = Files.walk(directory)) {
            List<Path> removalOrder = paths.sorted(Comparator.reverseOrder()).toList();
            for (Path path : removalOrder) Files.deleteIfExists(path);
        }
    }
}
