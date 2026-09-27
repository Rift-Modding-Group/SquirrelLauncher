package com.anightdazingzoroark.squirrellauncher.minecraft.install;

import com.anightdazingzoroark.squirrellauncher.minecraft.MinecraftPaths;
import com.anightdazingzoroark.squirrellauncher.minecraft.download.Downloader;
import com.anightdazingzoroark.squirrellauncher.minecraft.instance.InstanceType;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LoaderVersionCatalog {
    public static final String FORGE_RECOMMENDED = "14.23.5.2859";
    private static final String FORGE_METADATA_URL =
            "https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml";
    private static final String CLEANROOM_RELEASES_API =
            "https://api.github.com/repos/CleanroomMC/Cleanroom/releases";
    private static final int GITHUB_PAGE_SIZE = 100;
    private static final Pattern FORGE_VERSION_PATTERN = Pattern.compile(
            "<version>1\\.12\\.2-([^<]+)</version>"
    );
    private static final Pattern VERSION_NUMBER_PATTERN = Pattern.compile("\\d+");
    private static final Map<InstanceType, List<Version>> CACHE = new EnumMap<>(InstanceType.class);

    private LoaderVersionCatalog() {}

    @NotNull
    public static List<Version> installedVersions(@NotNull InstanceType type) throws IOException {
        List<Version> versions = new ArrayList<>();
        if (type == InstanceType.FORGE) {
            String directoryPrefix = ForgeConstants.versionId("");
            if (!Files.isDirectory(MinecraftPaths.VERSIONS)) return List.of();

            try (DirectoryStream<Path> directories = Files.newDirectoryStream(MinecraftPaths.VERSIONS)) {
                for (Path directory : directories) {
                    String directoryName = directory.getFileName().toString();
                    if (!directoryName.startsWith(directoryPrefix)) continue;
                    if (!Files.isRegularFile(directory.resolve(directoryName + ".json"))) continue;

                    String version = directoryName.substring(directoryPrefix.length());
                    if (!version.isBlank()) {
                        versions.add(new Version(
                                version,
                                version.equals(LoaderVersionCatalog.FORGE_RECOMMENDED)
                        ));
                    }
                }
            }
        }
        else if (type == InstanceType.CLEANROOM) {
            if (!Files.isDirectory(MinecraftPaths.CLEANROOM)) return List.of();

            try (DirectoryStream<Path> directories = Files.newDirectoryStream(MinecraftPaths.CLEANROOM)) {
                for (Path directory : directories) {
                    String version = directory.getFileName().toString();
                    if (Files.isDirectory(directory) && !version.isBlank()) {
                        versions.add(new Version(version, false));
                    }
                }
            }
        }

        LoaderVersionCatalog.sortVersions(versions);
        return List.copyOf(versions);
    }

    @NotNull
    public static List<Version> availableVersions(@NotNull InstanceType type) throws IOException, InterruptedException {
        synchronized (LoaderVersionCatalog.CACHE) {
            List<Version> cachedVersions = LoaderVersionCatalog.CACHE.get(type);
            if (cachedVersions != null) return cachedVersions;
        }

        Set<String> versionNames = new LinkedHashSet<>();
        String recommendedVersion;
        if (type == InstanceType.FORGE) {
            Matcher versionMatcher = LoaderVersionCatalog.FORGE_VERSION_PATTERN.matcher(
                    Downloader.getString(LoaderVersionCatalog.FORGE_METADATA_URL)
            );
            while (versionMatcher.find()) versionNames.add(versionMatcher.group(1));
            recommendedVersion = LoaderVersionCatalog.FORGE_RECOMMENDED;
        }
        else if (type == InstanceType.CLEANROOM) {
            JsonElement latestElement = JsonParser.parseString(
                    Downloader.getString(LoaderVersionCatalog.CLEANROOM_RELEASES_API + "/latest")
            );
            if (!latestElement.isJsonObject()
                    || !latestElement.getAsJsonObject().has("tag_name")) {
                throw new IOException("GitHub returned an invalid latest Cleanroom release.");
            }
            recommendedVersion = latestElement.getAsJsonObject().get("tag_name").getAsString();

            for (int page = 1; ; page++) {
                String releasesJson = Downloader.getString(
                        LoaderVersionCatalog.CLEANROOM_RELEASES_API
                                + "?per_page=" + LoaderVersionCatalog.GITHUB_PAGE_SIZE
                                + "&page=" + page
                );
                JsonElement releasesElement = JsonParser.parseString(releasesJson);
                if (!releasesElement.isJsonArray()) {
                    throw new IOException("GitHub returned an invalid Cleanroom release list.");
                }

                JsonArray releases = releasesElement.getAsJsonArray();
                for (JsonElement releaseElement : releases) {
                    if (!releaseElement.isJsonObject()) continue;
                    JsonObject release = releaseElement.getAsJsonObject();
                    if (release.has("draft") && release.get("draft").getAsBoolean()) continue;
                    if (release.has("tag_name")) versionNames.add(release.get("tag_name").getAsString());
                }
                if (releases.size() < LoaderVersionCatalog.GITHUB_PAGE_SIZE) break;
            }
        }
        else {
            return List.of();
        }

        versionNames.add(recommendedVersion);
        List<Version> versions = new ArrayList<>(versionNames.size());
        for (String versionName : versionNames) {
            if (!versionName.isBlank()) {
                versions.add(new Version(versionName, versionName.equals(recommendedVersion)));
            }
        }
        LoaderVersionCatalog.sortVersions(versions);
        List<Version> immutableVersions = List.copyOf(versions);

        synchronized (LoaderVersionCatalog.CACHE) {
            LoaderVersionCatalog.CACHE.put(type, immutableVersions);
        }
        return immutableVersions;
    }

    public static void sortVersions(@NotNull List<Version> versions) {
        versions.sort(Comparator
                .comparing(Version::recommended).reversed()
                .thenComparing(Comparator.reverseOrder()));
    }

    public record Version(@NotNull String value, boolean recommended) implements Comparable<Version> {
        @Override
        public int compareTo(@NotNull Version other) {
            Matcher ownNumbers = LoaderVersionCatalog.VERSION_NUMBER_PATTERN.matcher(this.value);
            Matcher otherNumbers = LoaderVersionCatalog.VERSION_NUMBER_PATTERN.matcher(other.value);
            while (true) {
                boolean hasOwnNumber = ownNumbers.find();
                boolean hasOtherNumber = otherNumbers.find();
                if (!hasOwnNumber || !hasOtherNumber) {
                    if (hasOwnNumber) return 1;
                    if (hasOtherNumber) return -1;
                    return this.value.compareToIgnoreCase(other.value);
                }

                int comparison = Integer.compare(
                        Integer.parseInt(ownNumbers.group()),
                        Integer.parseInt(otherNumbers.group())
                );
                if (comparison != 0) return comparison;
            }
        }

        @Override
        @NotNull
        public String toString() {
            return this.recommended ? "★ " + this.value : this.value;
        }
    }
}
