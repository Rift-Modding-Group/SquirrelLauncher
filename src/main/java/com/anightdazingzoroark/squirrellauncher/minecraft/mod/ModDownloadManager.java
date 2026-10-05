package com.anightdazingzoroark.squirrellauncher.minecraft.mod;

import com.anightdazingzoroark.squirrellauncher.SquirrelLauncher;
import com.anightdazingzoroark.squirrellauncher.minecraft.GitHubUtils;
import com.anightdazingzoroark.squirrellauncher.minecraft.download.Downloader;
import com.anightdazingzoroark.squirrellauncher.minecraft.instance.MinecraftInstance;
import com.anightdazingzoroark.squirrellauncher.minecraft.MinecraftPaths;
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
import java.net.HttpRetryException;
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

//download manager for curseforge and modrinth
public final class ModDownloadManager {
    @NotNull
    private static final String MODRINTH_API = "https://api.modrinth.com/v2";
    @NotNull
    private static final String CURSEFORGE_API = "https://api.curseforge.com/v1";
    @NotNull
    private static final String MODRINTH_DOWNLOAD_HOST = "cdn.modrinth.com";
    @NotNull
    private static final String CURSEFORGE_DOWNLOAD_DOMAIN = "forgecdn.net";
    @NotNull
    private static final String USER_AGENT = SquirrelLauncher.NAME + "/" + SquirrelLauncher.LAUNCHER_VERSION + " (https://github.com/Rift-Modding-Group/SquirrelLauncher)";
    private static final int CURSEFORGE_MINECRAFT_GAME_ID = 432;
    private static final int CURSEFORGE_MOD_CLASS_ID = 6;
    private static final int CURSEFORGE_FORGE_LOADER = 1;
    private static final int RESULT_LIMIT = 50;
    private static final int MAX_DEPENDENCIES = 100;
    private static final int MOD_ICON_SIZE = 48;
    private static final int MAX_ICON_DOWNLOAD_BYTES = 2 * 1024 * 1024;
    @NotNull
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);
    @NotNull
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(45);
    @NotNull
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(ModDownloadManager.CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    @Nullable
    private final String curseForgeApiKey;
    @NotNull
    private final Map<String, ModDownloadProject> favoriteProjects = new LinkedHashMap<>();

    public ModDownloadManager() {
        String apiKey = System.getProperty("squirrellauncher.curseforgeApiKey");
        if (apiKey == null || apiKey.isBlank()) apiKey = System.getenv("SQUIRREL_CURSEFORGE_API_KEY");
        this.curseForgeApiKey = apiKey == null || apiKey.isBlank() ? null : apiKey.trim();
        try {
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
            @NotNull String searchText,
            int offset,
            @NotNull List<String> githubRepositoryUrls
    ) throws IOException, InterruptedException {
        String trimmedSearch = searchText.trim();
        List<ModDownloadProject> projects = new ArrayList<>();
        Set<String> configuredGitHubProjects = new HashSet<>();
        long totalResults = -1L;
        if (platform == ModDownloadPlatform.MODRINTH) {
            String facets = "[[\"project_type:mod\"],[\"versions:" + SquirrelLauncher.GAME_VERSION
                    + "\"],[\"categories:forge\"]]";
            String url = ModDownloadManager.MODRINTH_API
                    + "/search?query=" + ModDownloadManager.encode(trimmedSearch)
                    + "&facets=" + ModDownloadManager.encode(facets)
                    + (trimmedSearch.isEmpty() ? "&index=downloads" : "")
                    + "&limit=" + ModDownloadManager.RESULT_LIMIT
                    + "&offset=" + Math.max(0, offset);
            JsonObject response = this.requestJson(url, platform).getAsJsonObject();
            totalResults = ModDownloadManager.longValue(response, "total_hits");
            JsonArray hits = response.getAsJsonArray("hits");
            for (JsonElement element : hits == null ? new JsonArray() : hits) {
                if (!element.isJsonObject()) continue;
                JsonObject hit = element.getAsJsonObject();
                String projectId = ModDownloadManager.string(hit, "project_id");
                String name = ModDownloadManager.string(hit, "title");
                if (projectId.isBlank() || name.isBlank()) continue;
                String iconUrl = ModDownloadManager.string(hit, "icon_url");
                String slug = ModDownloadManager.string(hit, "slug");
                projects.add(new ModDownloadProject(
                        platform,
                        projectId,
                        name,
                        ModDownloadManager.string(hit, "author"),
                        ModDownloadManager.string(hit, "description"),
                        iconUrl.isBlank() ? null : iconUrl,
                        "https://modrinth.com/mod/" + ModDownloadManager.encodePathSegment(
                                slug.isBlank() ? projectId : slug
                        ),
                        ModDownloadManager.longValue(hit, "downloads"),
                        this.favoriteProjects.containsKey(ModDownloadManager.favoriteKey(platform, projectId))
                ));
            }
        }
        else if (platform == ModDownloadPlatform.CURSEFORGE) {
            String url = ModDownloadManager.CURSEFORGE_API
                    + "/mods/search?gameId=" + ModDownloadManager.CURSEFORGE_MINECRAFT_GAME_ID
                    + "&classId=" + ModDownloadManager.CURSEFORGE_MOD_CLASS_ID
                    + "&gameVersion=" + ModDownloadManager.encode(SquirrelLauncher.GAME_VERSION)
                    + "&modLoaderType=" + ModDownloadManager.CURSEFORGE_FORGE_LOADER
                    + "&searchFilter=" + ModDownloadManager.encode(trimmedSearch)
                    + "&sortField=2&sortOrder=desc&pageSize=" + ModDownloadManager.RESULT_LIMIT
                    + "&index=" + Math.max(0, offset);
            JsonObject response = this.requestJson(url, platform).getAsJsonObject();
            JsonArray data = response.getAsJsonArray("data");
            JsonObject pagination = response.getAsJsonObject("pagination");
            if (pagination != null) totalResults = ModDownloadManager.longValue(pagination, "totalCount");
            for (JsonElement element : data == null ? new JsonArray() : data) {
                if (!element.isJsonObject()) continue;
                JsonObject project = element.getAsJsonObject();
                String projectId = ModDownloadManager.string(project, "id");
                String name = ModDownloadManager.string(project, "name");
                if (projectId.isBlank() || name.isBlank()) continue;
                String author = "";
                JsonArray authors = project.getAsJsonArray("authors");
                if (authors != null) {
                    StringBuilder authorNames = new StringBuilder();
                    for (JsonElement authorElement : authors) {
                        if (!authorElement.isJsonObject()) continue;
                        String authorName = ModDownloadManager.string(authorElement.getAsJsonObject(), "name");
                        if (authorName.isBlank()) continue;
                        if (!authorNames.isEmpty()) authorNames.append(", ");
                        authorNames.append(authorName);
                    }
                    author = authorNames.toString();
                }
                JsonElement logoElement = project.get("logo");
                JsonObject logo = logoElement != null && logoElement.isJsonObject()
                        ? logoElement.getAsJsonObject() : null;
                String iconUrl = logo == null ? "" : ModDownloadManager.string(logo, "thumbnailUrl");
                JsonElement linksElement = project.get("links");
                JsonObject links = linksElement != null && linksElement.isJsonObject()
                        ? linksElement.getAsJsonObject() : null;
                String pageUrl = links == null ? "" : ModDownloadManager.string(links, "websiteUrl");
                projects.add(new ModDownloadProject(
                        platform,
                        projectId,
                        name,
                        author,
                        ModDownloadManager.string(project, "summary"),
                        iconUrl.isBlank() ? null : iconUrl,
                        pageUrl.isBlank() ? null : pageUrl,
                        ModDownloadManager.longValue(project, "downloadCount"),
                        this.favoriteProjects.containsKey(ModDownloadManager.favoriteKey(platform, projectId))
                ));
            }
        }
        else if (offset <= 0) {
            String normalizedSearch = trimmedSearch.toLowerCase(Locale.ROOT);
            for (String repositoryUrl : githubRepositoryUrls) {
                GitHubModRepository repository = GitHubModRepository.parse(repositoryUrl);
                configuredGitHubProjects.add(repository.projectId());
                ModDownloadProject project = this.githubProject(repository);
                if (!normalizedSearch.isEmpty()
                        && !project.name().toLowerCase(Locale.ROOT).contains(normalizedSearch)
                        && !project.author().toLowerCase(Locale.ROOT).contains(normalizedSearch)
                        && !project.description().toLowerCase(Locale.ROOT).contains(normalizedSearch)
                        && !project.projectId().toLowerCase(Locale.ROOT).contains(normalizedSearch)) continue;
                projects.add(project);
            }
            totalResults = projects.size();
        }
        int nextOffset = Math.max(0, offset) + projects.size();
        boolean hasMore = totalResults >= 0L
                ? nextOffset < totalResults
                : projects.size() == ModDownloadManager.RESULT_LIMIT;
        if (offset == 0 && trimmedSearch.isEmpty()) {
            List<ModDownloadProject> favorites = new ArrayList<>();
            Set<String> favoriteKeys = new HashSet<>();
            for (ModDownloadProject favorite : this.favoriteProjects.values()) {
                if (favorite.platform() != platform) continue;
                if (platform == ModDownloadPlatform.GITHUB
                        && !configuredGitHubProjects.contains(favorite.projectId())) continue;
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
        if (project.platform() == ModDownloadPlatform.MODRINTH) {
            JsonObject response = this.requestJson(
                    ModDownloadManager.MODRINTH_API + "/project/"
                            + ModDownloadManager.encodePathSegment(project.projectId()),
                    project.platform()
            ).getAsJsonObject();
            String description = ModDownloadManager.string(response, "body");
            return new ModDownloadProjectDescription(
                    description.isBlank() ? project.description() : description,
                    false
            );
        }
        if (project.platform() == ModDownloadPlatform.GITHUB) {
            GitHubModRepository repository = GitHubModRepository.parse(
                    "https://github.com/" + project.projectId()
            );
            URI uri = URI.create(repository.apiUrl() + "/readme");
            HttpRequest.Builder request = HttpRequest.newBuilder(uri)
                    .timeout(ModDownloadManager.REQUEST_TIMEOUT)
                    .header("Accept", "application/vnd.github.raw+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .header("User-Agent", ModDownloadManager.USER_AGENT);
            GitHubUtils.authenticate(request, uri);
            HttpResponse<String> response = this.httpClient.send(
                    request.GET().build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            if (response.statusCode() == 404) {
                return new ModDownloadProjectDescription(project.description(), false);
            }
            if (response.statusCode() / 100 != 2) {
                throw ModDownloadManager.requestFailure(
                        response.statusCode(),
                        project.platform(),
                        "GitHub README"
                );
            }
            return new ModDownloadProjectDescription(
                    response.body().isBlank() ? project.description() : response.body(),
                    false
            );
        }
        JsonObject response = this.requestJson(
                ModDownloadManager.CURSEFORGE_API + "/mods/"
                        + ModDownloadManager.encodePathSegment(project.projectId()) + "/description",
                project.platform()
        ).getAsJsonObject();
        String description = ModDownloadManager.string(response, "data");
        return new ModDownloadProjectDescription(
                description.isBlank() ? project.description() : description,
                !description.isBlank()
        );
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
        boolean trustedIconHost = switch (project.platform()) {
            case MODRINTH -> iconHost.equalsIgnoreCase(ModDownloadManager.MODRINTH_DOWNLOAD_HOST);
            case CURSEFORGE -> iconHost.equalsIgnoreCase(ModDownloadManager.CURSEFORGE_DOWNLOAD_DOMAIN)
                    || iconHost.toLowerCase(Locale.ROOT).endsWith(
                            "." + ModDownloadManager.CURSEFORGE_DOWNLOAD_DOMAIN
                    );
            case GITHUB -> iconHost.equalsIgnoreCase("avatars.githubusercontent.com")
                    || iconHost.toLowerCase(Locale.ROOT).endsWith(".githubusercontent.com");
        };
        if (!trustedIconHost) return null;

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
                    drawing.setRenderingHint(
                            RenderingHints.KEY_INTERPOLATION,
                            RenderingHints.VALUE_INTERPOLATION_BILINEAR
                    );
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

    @NotNull
    public List<ModDownloadFile> files(@NotNull ModDownloadProject project) throws IOException, InterruptedException {
        List<ModDownloadFile> files = new ArrayList<>();
        if (project.platform() == ModDownloadPlatform.MODRINTH) {
            String loaders = "[\"forge\"]";
            String gameVersions = "[\"" + SquirrelLauncher.GAME_VERSION + "\"]";
            String url = ModDownloadManager.MODRINTH_API
                    + "/project/" + ModDownloadManager.encode(project.projectId()) + "/version"
                    + "?loaders=" + ModDownloadManager.encode(loaders)
                    + "&game_versions=" + ModDownloadManager.encode(gameVersions)
                    + "&include_changelog=false";
            JsonArray versions = this.requestJson(url, project.platform()).getAsJsonArray();
            files.addAll(this.modrinthFiles(project, versions));
        }
        else if (project.platform() == ModDownloadPlatform.CURSEFORGE) {
            String url = ModDownloadManager.CURSEFORGE_API
                    + "/mods/" + ModDownloadManager.encode(project.projectId()) + "/files"
                    + "?gameVersion=" + ModDownloadManager.encode(SquirrelLauncher.GAME_VERSION)
                    + "&modLoaderType=" + ModDownloadManager.CURSEFORGE_FORGE_LOADER
                    + "&pageSize=" + ModDownloadManager.RESULT_LIMIT;
            JsonObject response = this.requestJson(url, project.platform()).getAsJsonObject();
            JsonArray data = response.getAsJsonArray("data");
            if (data == null) return List.of();
            for (JsonElement element : data) {
                if (!element.isJsonObject()) continue;
                JsonObject file = element.getAsJsonObject();
                String downloadUrl = ModDownloadManager.string(file, "downloadUrl");
                String fileName = ModDownloadManager.string(file, "fileName");
                String fileId = ModDownloadManager.string(file, "id");
                if (downloadUrl.isBlank() || fileId.isBlank()
                        || !fileName.toLowerCase(Locale.ROOT).endsWith(".jar")) continue;
                String sha1 = "";
                JsonArray hashes = file.getAsJsonArray("hashes");
                if (hashes != null) {
                    for (JsonElement hashElement : hashes) {
                        if (!hashElement.isJsonObject()) continue;
                        JsonObject hash = hashElement.getAsJsonObject();
                        if (ModDownloadManager.longValue(hash, "algo") == 1L) {
                            sha1 = ModDownloadManager.string(hash, "value");
                            break;
                        }
                    }
                }
                String releaseType = switch ((int) ModDownloadManager.longValue(file, "releaseType")) {
                    case 1 -> "release";
                    case 2 -> "beta";
                    case 3 -> "alpha";
                    default -> "unknown";
                };
                files.add(new ModDownloadFile(
                        project.platform(),
                        project.projectId(),
                        fileId,
                        project.name(),
                        project.pageUrl(),
                        ModDownloadManager.string(file, "displayName"),
                        releaseType,
                        fileName,
                        downloadUrl,
                        sha1.isBlank() ? null : sha1,
                        ModDownloadManager.longValue(file, "fileLength")
                ));
            }
        }
        else {
            GitHubModRepository repository = GitHubModRepository.parse(
                    "https://github.com/" + project.projectId()
            );
            JsonArray releases = this.requestJson(
                    repository.apiUrl() + "/releases?per_page=100",
                    project.platform()
            ).getAsJsonArray();
            for (JsonElement releaseElement : releases) {
                if (!releaseElement.isJsonObject()) continue;
                JsonObject release = releaseElement.getAsJsonObject();
                JsonElement draft = release.get("draft");
                if (draft != null && draft.isJsonPrimitive() && draft.getAsBoolean()) continue;
                JsonArray assets = release.getAsJsonArray("assets");
                if (assets == null) continue;
                String versionName = ModDownloadManager.string(release, "name");
                if (versionName.isBlank()) versionName = ModDownloadManager.string(release, "tag_name");
                JsonElement prerelease = release.get("prerelease");
                String releaseType = prerelease != null && prerelease.isJsonPrimitive()
                        && prerelease.getAsBoolean() ? "beta" : "release";
                for (JsonElement assetElement : assets) {
                    if (!assetElement.isJsonObject()) continue;
                    JsonObject asset = assetElement.getAsJsonObject();
                    String fileName = ModDownloadManager.string(asset, "name");
                    if (!fileName.toLowerCase(Locale.ROOT).endsWith(".jar")) continue;
                    String fileId = ModDownloadManager.string(asset, "id");
                    String downloadUrl = ModDownloadManager.string(asset, "browser_download_url");
                    if (fileId.isBlank() || downloadUrl.isBlank()) continue;
                    files.add(new ModDownloadFile(
                            project.platform(),
                            project.projectId(),
                            fileId,
                            project.name(),
                            project.pageUrl(),
                            versionName,
                            releaseType,
                            fileName,
                            downloadUrl,
                            null,
                            ModDownloadManager.longValue(asset, "size")
                    ));
                }
            }
        }
        return List.copyOf(files);
    }

    @Nullable
    public ModDownloadFile updateFor(@NotNull ManagedMod mod) throws IOException, InterruptedException {
        if (mod.provider() == null || mod.providerProjectId() == null || mod.providerFileId() == null) return null;
        ModDownloadProject project = this.project(mod.provider(), mod.providerProjectId());
        List<ModDownloadFile> compatibleFiles = this.files(project);
        if (compatibleFiles.isEmpty()
                || compatibleFiles.getFirst().providerFileId().equals(mod.providerFileId())) return null;
        return compatibleFiles.getFirst();
    }

    @NotNull
    public List<ModDownloadFile> dependencies(
            @NotNull MinecraftInstance instance,
            @NotNull List<ModDownloadFile> rootFiles
    ) throws IOException, InterruptedException {
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
            if (currentFile.platform() == ModDownloadPlatform.MODRINTH) {
                JsonObject version = this.requestJson(
                        ModDownloadManager.MODRINTH_API + "/version/"
                                + ModDownloadManager.encodePathSegment(currentFile.providerFileId()),
                        currentFile.platform()
                ).getAsJsonObject();
                JsonArray requiredDependencies = version.getAsJsonArray("dependencies");
                if (requiredDependencies != null) {
                    for (JsonElement dependencyElement : requiredDependencies) {
                        if (!dependencyElement.isJsonObject()) continue;
                        JsonObject dependency = dependencyElement.getAsJsonObject();
                        if (!"required".equals(ModDownloadManager.string(dependency, "dependency_type"))) continue;
                        String dependencyVersionId = ModDownloadManager.string(dependency, "version_id");
                        String dependencyProjectId = ModDownloadManager.string(dependency, "project_id");
                        JsonObject dependencyVersion = null;
                        if (!dependencyVersionId.isBlank()) {
                            dependencyVersion = this.requestJson(
                                    ModDownloadManager.MODRINTH_API + "/version/"
                                            + ModDownloadManager.encodePathSegment(dependencyVersionId),
                                    currentFile.platform()
                            ).getAsJsonObject();
                            if (dependencyProjectId.isBlank()) {
                                dependencyProjectId = ModDownloadManager.string(dependencyVersion, "project_id");
                            }
                        }
                        if (dependencyProjectId.isBlank()) continue;
                        String dependencyKey = currentFile.platform().name() + ':' + dependencyProjectId;
                        if (visitedProjects.contains(dependencyKey)) continue;
                        ModDownloadProject dependencyProject = this.project(
                                currentFile.platform(),
                                dependencyProjectId
                        );
                        if (dependencyVersion == null) {
                            List<ModDownloadFile> compatibleFiles = this.files(dependencyProject);
                            if (compatibleFiles.isEmpty()) {
                                throw new IOException(
                                        "No compatible Minecraft " + SquirrelLauncher.GAME_VERSION
                                                + " Forge file was found for required dependency "
                                                + dependencyProject.name() + '.'
                                );
                            }
                            requiredFiles.add(compatibleFiles.getFirst());
                        }
                        else {
                            JsonArray exactVersion = new JsonArray();
                            exactVersion.add(dependencyVersion);
                            List<ModDownloadFile> exactFiles = this.modrinthFiles(
                                    dependencyProject,
                                    exactVersion
                            );
                            if (exactFiles.isEmpty()) {
                                throw new IOException(
                                        "The required dependency version for " + dependencyProject.name()
                                                + " has no downloadable JAR."
                                );
                            }
                            requiredFiles.add(exactFiles.getFirst());
                        }
                    }
                }
            }
            else if (currentFile.platform() == ModDownloadPlatform.CURSEFORGE) {
                JsonObject response = this.requestJson(
                        ModDownloadManager.CURSEFORGE_API + "/mods/"
                                + ModDownloadManager.encodePathSegment(currentFile.projectId())
                                + "/files/" + ModDownloadManager.encodePathSegment(currentFile.providerFileId()),
                        currentFile.platform()
                ).getAsJsonObject();
                JsonObject file = response.getAsJsonObject("data");
                JsonArray requiredDependencies = file == null ? null : file.getAsJsonArray("dependencies");
                if (requiredDependencies != null) {
                    for (JsonElement dependencyElement : requiredDependencies) {
                        if (!dependencyElement.isJsonObject()) continue;
                        JsonObject dependency = dependencyElement.getAsJsonObject();
                        if (ModDownloadManager.longValue(dependency, "relationType") != 3L) continue;
                        String dependencyProjectId = ModDownloadManager.string(dependency, "modId");
                        if (dependencyProjectId.isBlank()) continue;
                        String dependencyKey = currentFile.platform().name() + ':' + dependencyProjectId;
                        if (visitedProjects.contains(dependencyKey)) continue;
                        ModDownloadProject dependencyProject = this.project(
                                currentFile.platform(),
                                dependencyProjectId
                        );
                        List<ModDownloadFile> compatibleFiles = this.files(dependencyProject);
                        if (compatibleFiles.isEmpty()) {
                            throw new IOException(
                                    "No compatible Minecraft " + SquirrelLauncher.GAME_VERSION
                                            + " Forge file was found for required dependency "
                                            + dependencyProject.name() + '.'
                            );
                        }
                        requiredFiles.add(compatibleFiles.getFirst());
                    }
                }
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
    private List<ModDownloadFile> modrinthFiles(
            @NotNull ModDownloadProject project,
            @NotNull JsonArray versions
    ) {
        List<ModDownloadFile> files = new ArrayList<>();
        for (JsonElement element : versions) {
            if (!element.isJsonObject()) continue;
            JsonObject version = element.getAsJsonObject();
            JsonArray versionFiles = version.getAsJsonArray("files");
            if (versionFiles == null) continue;
            JsonObject selectedFile = null;
            for (JsonElement fileElement : versionFiles) {
                if (!fileElement.isJsonObject()) continue;
                JsonObject candidate = fileElement.getAsJsonObject();
                String fileName = ModDownloadManager.string(candidate, "filename");
                if (!fileName.toLowerCase(Locale.ROOT).endsWith(".jar")) continue;
                if (selectedFile == null) selectedFile = candidate;
                JsonElement primary = candidate.get("primary");
                if (primary != null && primary.isJsonPrimitive() && primary.getAsBoolean()) {
                    selectedFile = candidate;
                    break;
                }
            }
            if (selectedFile == null) continue;
            JsonObject hashes = selectedFile.getAsJsonObject("hashes");
            String sha1 = hashes == null ? "" : ModDownloadManager.string(hashes, "sha1");
            String downloadUrl = ModDownloadManager.string(selectedFile, "url");
            String fileName = ModDownloadManager.string(selectedFile, "filename");
            String versionId = ModDownloadManager.string(version, "id");
            if (downloadUrl.isBlank() || fileName.isBlank() || versionId.isBlank()) continue;
            files.add(new ModDownloadFile(
                    project.platform(),
                    project.projectId(),
                    versionId,
                    project.name(),
                    project.pageUrl(),
                    ModDownloadManager.string(version, "version_number"),
                    ModDownloadManager.string(version, "version_type"),
                    fileName,
                    downloadUrl,
                    sha1.isBlank() ? null : sha1,
                    ModDownloadManager.longValue(selectedFile, "size")
            ));
        }
        return List.copyOf(files);
    }

    @NotNull
    private ModDownloadProject project(
            @NotNull ModDownloadPlatform platform,
            @NotNull String projectId
    ) throws IOException, InterruptedException {
        if (platform == ModDownloadPlatform.MODRINTH) {
            JsonObject project = this.requestJson(
                    ModDownloadManager.MODRINTH_API + "/project/"
                            + ModDownloadManager.encodePathSegment(projectId),
                    platform
            ).getAsJsonObject();
            String name = ModDownloadManager.string(project, "title");
            if (name.isBlank()) name = projectId;
            String iconUrl = ModDownloadManager.string(project, "icon_url");
            String slug = ModDownloadManager.string(project, "slug");
            return new ModDownloadProject(
                    platform,
                    projectId,
                    name,
                    "",
                    ModDownloadManager.string(project, "description"),
                    iconUrl.isBlank() ? null : iconUrl,
                    "https://modrinth.com/mod/" + ModDownloadManager.encodePathSegment(
                            slug.isBlank() ? projectId : slug
                    ),
                    ModDownloadManager.longValue(project, "downloads"),
                    this.favoriteProjects.containsKey(ModDownloadManager.favoriteKey(platform, projectId))
            );
        }
        if (platform == ModDownloadPlatform.GITHUB) {
            return this.githubProject(GitHubModRepository.parse("https://github.com/" + projectId));
        }
        JsonObject response = this.requestJson(
                ModDownloadManager.CURSEFORGE_API + "/mods/"
                        + ModDownloadManager.encodePathSegment(projectId),
                platform
        ).getAsJsonObject();
        JsonObject project = response.getAsJsonObject("data");
        if (project == null) throw new IOException("CurseForge did not return required dependency " + projectId + '.');
        String name = ModDownloadManager.string(project, "name");
        if (name.isBlank()) name = projectId;
        String author = "";
        JsonArray authors = project.getAsJsonArray("authors");
        if (authors != null) {
            StringBuilder authorNames = new StringBuilder();
            for (JsonElement authorElement : authors) {
                if (!authorElement.isJsonObject()) continue;
                String authorName = ModDownloadManager.string(authorElement.getAsJsonObject(), "name");
                if (authorName.isBlank()) continue;
                if (!authorNames.isEmpty()) authorNames.append(", ");
                authorNames.append(authorName);
            }
            author = authorNames.toString();
        }
        JsonElement logoElement = project.get("logo");
        JsonObject logo = logoElement != null && logoElement.isJsonObject()
                ? logoElement.getAsJsonObject() : null;
        String iconUrl = logo == null ? "" : ModDownloadManager.string(logo, "thumbnailUrl");
        JsonElement linksElement = project.get("links");
        JsonObject links = linksElement != null && linksElement.isJsonObject()
                ? linksElement.getAsJsonObject() : null;
        String pageUrl = links == null ? "" : ModDownloadManager.string(links, "websiteUrl");
        return new ModDownloadProject(
                platform,
                projectId,
                name,
                author,
                ModDownloadManager.string(project, "summary"),
                iconUrl.isBlank() ? null : iconUrl,
                pageUrl.isBlank() ? null : pageUrl,
                ModDownloadManager.longValue(project, "downloadCount"),
                this.favoriteProjects.containsKey(ModDownloadManager.favoriteKey(platform, projectId))
        );
    }

    @NotNull
    private ModDownloadProject githubProject(@NotNull GitHubModRepository repository)
            throws IOException, InterruptedException {
        JsonObject project = this.requestJson(repository.apiUrl(), ModDownloadPlatform.GITHUB).getAsJsonObject();
        String name = ModDownloadManager.string(project, "name");
        if (name.isBlank()) name = repository.repository();
        JsonObject owner = project.getAsJsonObject("owner");
        String author = owner == null ? repository.owner() : ModDownloadManager.string(owner, "login");
        if (author.isBlank()) author = repository.owner();
        String iconUrl = owner == null ? "" : ModDownloadManager.string(owner, "avatar_url");
        String projectId = repository.projectId();
        return new ModDownloadProject(
                ModDownloadPlatform.GITHUB,
                projectId,
                name,
                author,
                ModDownloadManager.string(project, "description"),
                iconUrl.isBlank() ? null : iconUrl,
                repository.releasesUrl(),
                0L,
                this.favoriteProjects.containsKey(
                        ModDownloadManager.favoriteKey(ModDownloadPlatform.GITHUB, projectId)
                )
        );
    }

    @NotNull
    private static String favoriteKey(@NotNull ModDownloadPlatform platform, @NotNull String projectId) {
        return platform.name() + ':' + projectId;
    }

    public void install(@NotNull MinecraftInstance instance, @NotNull ModDownloadFile file) throws IOException, InterruptedException {
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
        boolean trustedDownloadHost = switch (file.platform()) {
            case MODRINTH -> downloadHost.equalsIgnoreCase(ModDownloadManager.MODRINTH_DOWNLOAD_HOST);
            case CURSEFORGE -> downloadHost.equalsIgnoreCase(ModDownloadManager.CURSEFORGE_DOWNLOAD_DOMAIN)
                    || downloadHost.toLowerCase(Locale.ROOT).endsWith(
                            "." + ModDownloadManager.CURSEFORGE_DOWNLOAD_DOMAIN
                    );
            case GITHUB -> downloadHost.equalsIgnoreCase("github.com");
        };
        if (!trustedDownloadHost) {
            throw new IOException("The provider returned an unexpected mod download host: " + downloadHost);
        }
        ModManager modManager = new ModManager(instance);
        modManager.initialize();
        if (modManager.contains(file.fileName())) {
            throw new IOException("Mod is already managed by this instance: " + file.fileName());
        }
        Path stagingDirectory = Files.createTempDirectory(modManager.modsDirectory(), ".mod-download-");
        Path downloadedFile = stagingDirectory.resolve(file.fileName());
        try {
            Map<String, String> headers = file.platform() == ModDownloadPlatform.CURSEFORGE
                    ? Map.of("x-api-key", this.requiredCurseForgeApiKey())
                    : Map.of();
            Downloader.downloadVerified(
                    file.downloadUrl(),
                    downloadedFile,
                    file.sha1(),
                    headers
            );
            modManager.install(downloadedFile, file);
        }
        finally {
            Files.deleteIfExists(downloadedFile);
            Files.deleteIfExists(stagingDirectory);
        }
    }

    @NotNull
    private JsonElement requestJson(@NotNull String url, @NotNull ModDownloadPlatform platform) throws IOException, InterruptedException {
        URI uri = URI.create(url);
        HttpRequest.Builder request = HttpRequest.newBuilder(uri)
                .timeout(ModDownloadManager.REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .header("User-Agent", ModDownloadManager.USER_AGENT);
        if (platform == ModDownloadPlatform.CURSEFORGE) {
            request.header("x-api-key", this.requiredCurseForgeApiKey());
        }
        else if (platform == ModDownloadPlatform.GITHUB) {
            request.setHeader("Accept", "application/vnd.github+json");
            request.header("X-GitHub-Api-Version", "2022-11-28");
            GitHubUtils.authenticate(request, uri);
        }
        HttpResponse<String> response = this.httpClient.send(
                request.GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );
        if (response.statusCode() / 100 != 2) {
            throw ModDownloadManager.requestFailure(response.statusCode(), platform, platform.name());
        }
        try {
            return JsonParser.parseString(response.body());
        }
        catch (JsonParseException exception) {
            throw new IOException("The " + platform + " response was not valid JSON.", exception);
        }
    }

    @NotNull
    private String requiredCurseForgeApiKey() throws IOException {
        if (this.curseForgeApiKey != null) return this.curseForgeApiKey;
        throw new IOException(
                "CurseForge requires a SquirrelLauncher API key. Set SQUIRREL_CURSEFORGE_API_KEY "
                        + "or -Dsquirrellauncher.curseforgeApiKey before starting the launcher."
        );
    }

    @NotNull
    private static IOException requestFailure(
            int statusCode,
            @NotNull ModDownloadPlatform platform,
        @NotNull String requestName
    ) {
        if (platform == ModDownloadPlatform.GITHUB && statusCode == 403) {
            return new HttpRetryException("GitHub API request forbidden", statusCode);
        }
        return new IOException("HTTP " + statusCode + " while requesting " + requestName + '.');
    }

    @NotNull
    private static String encode(@NotNull String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    @NotNull
    private static String encodePathSegment(@NotNull String value) {
        return ModDownloadManager.encode(value).replace("+", "%20");
    }

    @NotNull
    private static String string(@NotNull JsonObject object, @NotNull String key) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() || !value.isJsonPrimitive() ? "" : value.getAsString();
    }

    private static long longValue(@NotNull JsonObject object, @NotNull String key) {
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
