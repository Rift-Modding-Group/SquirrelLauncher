package com.anightdazingzoroark.squirrellauncher.minecraft.mod;

import com.anightdazingzoroark.squirrellauncher.SquirrelLauncher;
import com.anightdazingzoroark.squirrellauncher.minecraft.download.Downloader;
import com.anightdazingzoroark.squirrellauncher.minecraft.instance.MinecraftInstance;
import com.anightdazingzoroark.squirrellauncher.minecraft.MinecraftPaths;
import com.anightdazingzoroark.squirrellauncher.minecraft.mod.platform.AbstractModPlatformHandler;
import com.anightdazingzoroark.squirrellauncher.minecraft.mod.platform.CurseforgeModPlatformHandler;
import com.anightdazingzoroark.squirrellauncher.minecraft.mod.platform.ModDownloadPlatform;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

//download manager for all mod platforms
public final class ModDownloadManager {
    @NotNull
    public static final String USER_AGENT = SquirrelLauncher.NAME + "/" + SquirrelLauncher.LAUNCHER_VERSION + " (https://github.com/Rift-Modding-Group/SquirrelLauncher)";
    public static final int RESULT_LIMIT = 50;
    private static final int MAX_DEPENDENCIES = 100;
    private static final int MOD_ICON_SIZE = 48;
    private static final int MAX_ICON_DOWNLOAD_BYTES = 2 * 1024 * 1024;
    @NotNull
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);
    @NotNull
    public static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(45);
    @NotNull
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(ModDownloadManager.CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    @NotNull
    private final Map<String, ModDownloadProject> favoriteProjects = new LinkedHashMap<>();

    public ModDownloadManager() {
        try {
            //initialize the favorite projects map
            if (Files.isRegularFile(MinecraftPaths.MOD_FAVORITES)) {
                JsonElement storedFavorites = JsonParser.parseString(Files.readString(MinecraftPaths.MOD_FAVORITES));
                if (storedFavorites.isJsonArray()) {
                    for (JsonElement favoriteElement : storedFavorites.getAsJsonArray()) {
                        if (!favoriteElement.isJsonObject()) continue;
                        JsonObject favorite = favoriteElement.getAsJsonObject();
                        ModDownloadPlatform platform;
                        try {
                            platform = ModDownloadPlatform.valueOf(ModDownloadManager.string(favorite, "platform"));
                        }
                        catch (IllegalArgumentException exception) {
                            continue;
                        }
                        String projectId = ModDownloadManager.string(favorite, "projectId");
                        String name = ModDownloadManager.string(favorite, "name");
                        if (projectId.isBlank() || name.isBlank()) continue;
                        String iconUrl = ModDownloadManager.string(favorite, "iconUrl");
                        String pageUrl = ModDownloadManager.string(favorite, "pageUrl");
                        ModDownloadProject project = new ModDownloadProject(
                                platform, projectId, name,
                                ModDownloadManager.string(favorite, "author"),
                                ModDownloadManager.string(favorite, "description"),
                                iconUrl.isBlank() ? null : iconUrl,
                                pageUrl.isBlank() ? null : pageUrl,
                                ModDownloadManager.longValue(favorite, "downloads"),
                                true
                        );
                        this.favoriteProjects.put(ModDownloadManager.favoriteKey(platform, projectId), project);
                    }
                }
            }
        }
        catch (IOException | JsonParseException ignored) {}
    }

    @NotNull
    public synchronized ModDownloadSearchPage search(
            @NotNull ModDownloadPlatform platform,
            @NotNull String searchText, int offset,
            @NotNull List<String> githubRepositoryUrls
    ) throws IOException, InterruptedException {
        String trimmedSearch = searchText.trim();
        Set<String> configuredGitHubProjects = new HashSet<>();

        AbstractModPlatformHandler.ModSearchResult modSearchResult = platform.getModPlatformHandler().getSearchModResults(
                trimmedSearch, offset, githubRepositoryUrls, this.favoriteProjects, configuredGitHubProjects
        );
        List<ModDownloadProject> projects = modSearchResult.projectList();
        long totalResults = modSearchResult.totalResults();

        int nextOffset = Math.max(0, offset) + projects.size();
        boolean hasMore = totalResults >= 0L ? nextOffset < totalResults : projects.size() == ModDownloadManager.RESULT_LIMIT;
        if (offset == 0 && trimmedSearch.isEmpty()) {
            List<ModDownloadProject> favorites = new ArrayList<>();
            Set<String> favoriteKeys = new HashSet<>();
            for (ModDownloadProject favorite : this.favoriteProjects.values()) {
                if (favorite.platform() != platform) continue;
                if (platform == ModDownloadPlatform.GITHUB && !configuredGitHubProjects.contains(favorite.projectId())) continue;
                favorites.add(favorite);
                favoriteKeys.add(ModDownloadManager.favoriteKey(platform, favorite.projectId()));
            }
            projects.removeIf(project -> favoriteKeys.contains(
                    ModDownloadManager.favoriteKey(project.platform(), project.projectId())
            ));
            favorites.addAll(projects);
            projects = favorites;
        }
        return new ModDownloadSearchPage(List.copyOf(projects), nextOffset, hasMore);
    }

    public synchronized boolean toggleFavorite(@NotNull ModDownloadProject project) throws IOException {
        String projectKey = ModDownloadManager.favoriteKey(project.platform(), project.projectId());
        boolean favorite;
        if (this.favoriteProjects.containsKey(projectKey)) {
            this.favoriteProjects.remove(projectKey);
            favorite = false;
        }
        else {
            this.favoriteProjects.put(projectKey, new ModDownloadProject(
                    project.platform(),
                    project.projectId(),
                    project.name(),
                    project.author(),
                    project.description(),
                    project.iconUrl(),
                    project.pageUrl(),
                    project.downloads(),
                    true
            ));
            favorite = true;
        }
        JsonArray storedFavorites = new JsonArray();
        for (ModDownloadProject storedProject : this.favoriteProjects.values()) {
            JsonObject storedFavorite = new JsonObject();
            storedFavorite.addProperty("platform", storedProject.platform().name());
            storedFavorite.addProperty("projectId", storedProject.projectId());
            storedFavorite.addProperty("name", storedProject.name());
            storedFavorite.addProperty("author", storedProject.author());
            storedFavorite.addProperty("description", storedProject.description());
            if (storedProject.iconUrl() != null) storedFavorite.addProperty("iconUrl", storedProject.iconUrl());
            if (storedProject.pageUrl() != null) storedFavorite.addProperty("pageUrl", storedProject.pageUrl());
            storedFavorite.addProperty("downloads", storedProject.downloads());
            storedFavorites.add(storedFavorite);
        }
        Files.createDirectories(MinecraftPaths.MOD_FAVORITES.getParent());
        Path temporaryFile = MinecraftPaths.MOD_FAVORITES.resolveSibling(
                MinecraftPaths.MOD_FAVORITES.getFileName() + ".tmp"
        );
        Files.writeString(temporaryFile, storedFavorites.toString(), StandardCharsets.UTF_8);
        try {
            Files.move(
                    temporaryFile,
                    MinecraftPaths.MOD_FAVORITES,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
            );
        }
        catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporaryFile, MinecraftPaths.MOD_FAVORITES, StandardCopyOption.REPLACE_EXISTING);
        }
        return favorite;
    }

    @NotNull
    public ModDownloadProjectDescription description(@NotNull ModDownloadProject project) throws IOException, InterruptedException {
        return project.platform().getModPlatformHandler().getModProjectDescription(project);
    }

    @Nullable
    public BufferedImage icon(@NotNull ModDownloadProject project) throws IOException, InterruptedException {
        if (project.iconUrl() == null) return null;
        URI iconUri;
        try {
            iconUri = URI.create(project.iconUrl());
        }
        catch (IllegalArgumentException exception) {
            throw new IOException("The provider returned an invalid mod icon URL.", exception);
        }

        String iconHost = iconUri.getHost();
        if (!"https".equalsIgnoreCase(iconUri.getScheme()) || iconHost == null) return null;
        if (!project.platform().getModPlatformHandler().isIconHostTrusted(iconHost)) return null;

        HttpRequest.Builder request = HttpRequest.newBuilder(iconUri)
                .timeout(ModDownloadManager.REQUEST_TIMEOUT)
                .header("Accept", "image/*")
                .header("User-Agent", ModDownloadManager.USER_AGENT);
        HttpResponse<InputStream> response = this.httpClient.send(
                request.GET().build(),
                HttpResponse.BodyHandlers.ofInputStream()
        );
        try (InputStream responseBody = response.body()) {
            if (response.statusCode() / 100 != 2) return null;
            byte[] encodedImage = responseBody.readNBytes(ModDownloadManager.MAX_ICON_DOWNLOAD_BYTES + 1);
            if (encodedImage.length > ModDownloadManager.MAX_ICON_DOWNLOAD_BYTES) return null;
            try (ImageInputStream imageInput = ImageIO.createImageInputStream(
                    new ByteArrayInputStream(encodedImage)
            )) {
                if (imageInput == null) return null;
                Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);
                if (!readers.hasNext()) return null;
                ImageReader imageReader = readers.next();
                BufferedImage sourceIcon;
                int sourceWidth;
                int sourceHeight;
                try {
                    imageReader.setInput(imageInput, true, true);
                    sourceWidth = imageReader.getWidth(0);
                    sourceHeight = imageReader.getHeight(0);
                    ImageReadParam readParameters = imageReader.getDefaultReadParam();
                    int sample = Math.max(
                            1,
                            Math.max(sourceWidth, sourceHeight) / ModDownloadManager.MOD_ICON_SIZE
                    );
                    readParameters.setSourceSubsampling(sample, sample, 0, 0);
                    sourceIcon = imageReader.read(0, readParameters);
                }
                finally {
                    imageReader.dispose();
                }
                if (sourceIcon == null || sourceWidth <= 0 || sourceHeight <= 0 || sourceIcon.getWidth() <= 0
                        || sourceIcon.getHeight() <= 0) {
                    if (sourceIcon != null) sourceIcon.flush();
                    return null;
                }
                int iconWidth;
                int iconHeight;
                if (sourceWidth >= sourceHeight) {
                    iconWidth = ModDownloadManager.MOD_ICON_SIZE;
                    iconHeight = Math.max(
                            1,
                            (int) ((long) sourceHeight * ModDownloadManager.MOD_ICON_SIZE / sourceWidth)
                    );
                }
                else {
                    iconWidth = Math.max(
                            1,
                            (int) ((long) sourceWidth * ModDownloadManager.MOD_ICON_SIZE / sourceHeight)
                    );
                    iconHeight = ModDownloadManager.MOD_ICON_SIZE;
                }
                BufferedImage icon = new BufferedImage(iconWidth, iconHeight, BufferedImage.TYPE_INT_ARGB);
                Graphics2D drawing = icon.createGraphics();
                try {
                    drawing.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    drawing.drawImage(sourceIcon, 0, 0, iconWidth, iconHeight, null);
                }
                finally {
                    drawing.dispose();
                    sourceIcon.flush();
                }
                return icon;
            }
        }
    }

    //fetch every possible file for a given project
    @NotNull
    public List<ModDownloadFile> files(@NotNull ModDownloadProject project) throws IOException, InterruptedException {
        return this.files(project, false);
    }

    //same but with param for checking newest only
    @NotNull
    public List<ModDownloadFile> files(@NotNull ModDownloadProject project, boolean newestOnly) throws IOException, InterruptedException {
        return project.platform().getModPlatformHandler().getAvailableModList(project, newestOnly);
    }

    //find updates for a specific mod
    @NotNull
    public List<ModDownloadFile> updateFor(@NotNull ManagedMod mod) throws IOException, InterruptedException {
        if (mod.provider() == null || mod.providerProjectId() == null || mod.providerFileId() == null) return List.of();
        ModDownloadProject project = mod.provider().getModPlatformHandler().getModDownloadProject(mod.providerProjectId(), this.favoriteProjects);

        List<ModDownloadFile> compatibleFiles = this.files(project, true);
        for (ModDownloadFile compatibleFile : compatibleFiles) {
            if (compatibleFile.providerFileId().equals(mod.providerFileId())) return List.of();
        }
        return compatibleFiles;
    }

    @NotNull
    public List<ModDownloadFile> dependencies(@NotNull MinecraftInstance instance, @NotNull List<ModDownloadFile> rootFiles) throws IOException, InterruptedException {
        ModManager modManager = new ModManager(instance);
        modManager.initialize();
        List<ModDownloadFile> pending = new ArrayList<>();
        List<ModDownloadFile> dependencies = new ArrayList<>();
        Set<String> visitedProjects = new HashSet<>();
        Set<String> dependencyFileNames = new HashSet<>();
        for (ModDownloadFile rootFile : rootFiles) {
            String rootProjectKey = ModDownloadManager.favoriteKey(rootFile.platform(), rootFile.projectId());
            if (!visitedProjects.add(rootProjectKey)) continue;
            pending.add(rootFile);
            dependencyFileNames.add(rootFile.fileName().toLowerCase(Locale.ROOT));
        }
        for (int pendingIndex = 0; pendingIndex < pending.size(); pendingIndex++) {
            ModDownloadFile currentFile = pending.get(pendingIndex);
            List<ModDownloadFile> requiredFiles = new ArrayList<>();
            if (currentFile.platform() == ModDownloadPlatform.MODRINTH || currentFile.platform() == ModDownloadPlatform.CURSEFORGE) {
                currentFile.platform().getModPlatformHandler().setModDependencies(
                        currentFile, requiredFiles, visitedProjects, this.favoriteProjects
                );
            }

            for (ModDownloadFile requiredFile : requiredFiles) {
                String dependencyKey = requiredFile.platform().name() + ':' + requiredFile.projectId();
                if (!visitedProjects.add(dependencyKey)) continue;
                if (visitedProjects.size() > ModDownloadManager.MAX_DEPENDENCIES + rootFiles.size()) {
                    throw new IOException("The selected mods have too many required dependencies.");
                }
                pending.add(requiredFile);
                String normalizedFileName = requiredFile.fileName().toLowerCase(Locale.ROOT);
                if (!modManager.contains(requiredFile.fileName())) {
                    if (!dependencyFileNames.add(normalizedFileName)) {
                        throw new IOException(
                                "Required mods use the same filename: " + requiredFile.fileName()
                        );
                    }
                    dependencies.add(requiredFile);
                }
            }
        }
        return List.copyOf(dependencies);
    }

    @NotNull
    public static String favoriteKey(@NotNull ModDownloadPlatform platform, @NotNull String projectId) {
        return platform.name() + ':' + projectId;
    }

    @NotNull
    public ManagedMod install(@NotNull MinecraftInstance instance, @NotNull ModDownloadFile file) throws IOException, InterruptedException {
        return this.downloadAndInstall(instance, null, file);
    }

    @NotNull
    public ManagedMod update(
            @NotNull MinecraftInstance instance,
            @NotNull ManagedMod currentMod,
            @NotNull ModDownloadFile file
    ) throws IOException, InterruptedException {
        if (currentMod.provider() != file.platform()
                || currentMod.providerProjectId() == null
                || !currentMod.providerProjectId().equals(file.projectId())) {
            throw new IOException("The selected update does not belong to the installed mod.");
        }
        return this.downloadAndInstall(instance, currentMod, file);
    }

    @NotNull
    private ManagedMod downloadAndInstall(
            @NotNull MinecraftInstance instance,
            @Nullable ManagedMod currentMod,
            @NotNull ModDownloadFile file
    ) throws IOException, InterruptedException {
        Path suppliedFileName = Path.of(file.fileName());
        if (suppliedFileName.getNameCount() != 1 || !suppliedFileName.getFileName().toString().equals(file.fileName())) {
            throw new IOException("The provider returned an invalid mod filename: " + file.fileName());
        }
        URI downloadUri;
        try {
            downloadUri = URI.create(file.downloadUrl());
        }
        catch (IllegalArgumentException exception) {
            throw new IOException("The provider returned an invalid mod download URL.", exception);
        }
        String downloadHost = downloadUri.getHost();
        if (!"https".equalsIgnoreCase(downloadUri.getScheme()) || downloadHost == null) {
            throw new IOException("The provider returned an insecure mod download URL.");
        }
        if (!file.platform().getModPlatformHandler().isTrustedDownloadHost(downloadHost)) {
            throw new IOException("The provider returned an unexpected mod download host: " + downloadHost);
        }
        ModManager modManager = new ModManager(instance);
        modManager.initialize();
        if (currentMod == null && modManager.contains(file.fileName())) {
            throw new IOException("Mod is already managed by this instance: " + file.fileName());
        }
        Path stagingDirectory = Files.createTempDirectory(modManager.modsDirectory(), ".mod-download-");
        Path downloadedFile = stagingDirectory.resolve(file.fileName());
        try {
            Map<String, String> headers = Map.of();
            if (file.platform() == ModDownloadPlatform.CURSEFORGE) {
                //special case for curseforge
                headers = Map.of(
                        "x-api-key",
                        ((CurseforgeModPlatformHandler) file.platform().getModPlatformHandler()).requiredCurseForgeApiKey()
                );
            }
            Downloader.downloadVerified(
                    file.downloadUrl(),
                    downloadedFile,
                    file.sha1(),
                    headers
            );
            return currentMod == null
                    ? modManager.install(downloadedFile, file)
                    : modManager.update(currentMod, downloadedFile, file);
        }
        finally {
            Files.deleteIfExists(downloadedFile);
            Files.deleteIfExists(stagingDirectory);
        }
    }

    @NotNull
    public static String encode(@NotNull String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    @NotNull
    public static String encodePathSegment(@NotNull String value) {
        return ModDownloadManager.encode(value).replace("+", "%20");
    }

    @NotNull
    public static String string(@NotNull JsonObject object, @NotNull String key) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() || !value.isJsonPrimitive() ? "" : value.getAsString();
    }

    public static long longValue(@NotNull JsonObject object, @NotNull String key) {
        JsonElement value = object.get(key);
        if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) return 0L;
        try {
            return value.getAsLong();
        }
        catch (NumberFormatException exception) {
            return 0L;
        }
    }
}
