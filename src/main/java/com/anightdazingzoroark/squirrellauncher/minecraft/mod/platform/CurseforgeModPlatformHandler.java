package com.anightdazingzoroark.squirrellauncher.minecraft.mod.platform;

import com.anightdazingzoroark.squirrellauncher.SquirrelLauncher;
import com.anightdazingzoroark.squirrellauncher.minecraft.mod.ModDownloadFile;
import com.anightdazingzoroark.squirrellauncher.minecraft.mod.ModDownloadManager;
import com.anightdazingzoroark.squirrellauncher.minecraft.mod.ModDownloadProject;
import com.anightdazingzoroark.squirrellauncher.minecraft.mod.ModDownloadProjectDescription;
import com.google.gson.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.util.*;

public final class CurseforgeModPlatformHandler extends AbstractModPlatformHandler {
    @NotNull
    private static final String CURSEFORGE_API = "https://api.curseforge.com/v1";
    @NotNull
    private static final String CURSEFORGE_DOWNLOAD_DOMAIN = "forgecdn.net";
    private static final int CURSEFORGE_MINECRAFT_GAME_ID = 432;
    private static final int CURSEFORGE_MOD_CLASS_ID = 6;
    private static final int CURSEFORGE_FORGE_LOADER = 1;

    @Nullable
    private final String curseForgeApiKey;

    public CurseforgeModPlatformHandler() {
        String apiKey = System.getProperty("squirrellauncher.curseforgeApiKey");
        if (apiKey == null || apiKey.isBlank()) apiKey = System.getenv("SQUIRREL_CURSEFORGE_API_KEY");
        this.curseForgeApiKey = apiKey == null || apiKey.isBlank() ? null : apiKey.trim();
    }

    @Override
    @NotNull
    public ModDownloadPlatform modDownloadPlatform() {
        return ModDownloadPlatform.CURSEFORGE;
    }

    @Override
    public void additionalRequestVerification(@NotNull HttpRequest.Builder request, @NotNull URI uri) throws IOException {
        request.header("x-api-key", this.requiredCurseForgeApiKey().trim());
    }

    @Override
    @NotNull
    public ModDownloadProjectDescription getModProjectDescription(@NotNull ModDownloadProject project) throws IOException, InterruptedException {
        JsonObject response = this.requestJson(
                CURSEFORGE_API + "/mods/" + ModDownloadManager.encodePathSegment(project.projectId()) + "/description",
                project.platform()
        ).getAsJsonObject();
        String description = ModDownloadManager.string(response, "data");
        return new ModDownloadProjectDescription(
                description.isBlank() ? project.description() : description,
                !description.isBlank()
        );
    }

    @Override
    public boolean isIconHostTrusted(@NotNull String iconHost) {
        return iconHost.equalsIgnoreCase(CURSEFORGE_DOWNLOAD_DOMAIN)
                || iconHost.toLowerCase(Locale.ROOT).endsWith("." + CURSEFORGE_DOWNLOAD_DOMAIN);
    }

    @Override
    public boolean isTrustedDownloadHost(@NotNull String downloadHost) {
        return downloadHost.equalsIgnoreCase(CURSEFORGE_DOWNLOAD_DOMAIN)
                || downloadHost.toLowerCase(Locale.ROOT).endsWith("." + CURSEFORGE_DOWNLOAD_DOMAIN);
    }

    @NotNull
    @Override
    public ModDownloadProject getModDownloadProject(@NotNull String projectId, @NotNull Map<String, ModDownloadProject> favoriteProjects) throws IOException, InterruptedException {
        JsonObject response = this.requestJson(
                CURSEFORGE_API + "/mods/" + ModDownloadManager.encodePathSegment(projectId),
                this.modDownloadPlatform()
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
        JsonObject logo = logoElement != null && logoElement.isJsonObject() ? logoElement.getAsJsonObject() : null;
        String iconUrl = logo == null ? "" : ModDownloadManager.string(logo, "thumbnailUrl");
        JsonElement linksElement = project.get("links");
        JsonObject links = linksElement != null && linksElement.isJsonObject() ? linksElement.getAsJsonObject() : null;
        String pageUrl = links == null ? "" : ModDownloadManager.string(links, "websiteUrl");
        return new ModDownloadProject(
                this.modDownloadPlatform(),
                projectId,
                name,
                author,
                ModDownloadManager.string(project, "summary"),
                iconUrl.isBlank() ? null : iconUrl,
                pageUrl.isBlank() ? null : pageUrl,
                ModDownloadManager.longValue(project, "downloadCount"),
                favoriteProjects.containsKey(ModDownloadManager.favoriteKey(this.modDownloadPlatform(), projectId))
        );
    }

    @Override
    @NotNull
    public JsonArray getAllProjectVersions(@NotNull ModDownloadProject project) throws IOException, InterruptedException {
        String url = CURSEFORGE_API + "/mods/" + ModDownloadManager.encode(project.projectId()) + "/files"
                + "?gameVersion=" + ModDownloadManager.encode(SquirrelLauncher.GAME_VERSION)
                + "&modLoaderType=" + CURSEFORGE_FORGE_LOADER
                + "&pageSize=" + ModDownloadManager.RESULT_LIMIT;
        JsonObject response = this.requestJson(url, project.platform()).getAsJsonObject();
        return response.getAsJsonArray("data");
    }

    @Override
    @NotNull
    public List<ModDownloadFile> getAvailableModList(@NotNull ModDownloadProject project, @NotNull JsonArray versions, boolean newestOnly) {
        List<ModDownloadFile> toReturn = new ArrayList<>();
        for (JsonElement element : versions) {
            if (!element.isJsonObject()) continue;
            JsonObject file = element.getAsJsonObject();
            String downloadUrl = ModDownloadManager.string(file, "downloadUrl");
            String fileName = ModDownloadManager.string(file, "fileName");
            String fileId = ModDownloadManager.string(file, "id");
            if (downloadUrl.isBlank() || fileId.isBlank() || !fileName.toLowerCase(Locale.ROOT).endsWith(".jar")) continue;
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
            toReturn.add(new ModDownloadFile(
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

            //premature break if newest only
            if (newestOnly) break;
        }
        return toReturn;
    }

    @Override
    public void setModDependencies(
            @NotNull ModDownloadFile dependentFile,
            @NotNull List<ModDownloadFile> dependencyFiles,
            @NotNull Set<String> visitedProjects,
            @NotNull Map<String, @NotNull ModDownloadProject> favoriteProjects
    ) throws IOException, InterruptedException {
        if (dependentFile.platform() != this.modDownloadPlatform()) throw new IOException("Platform of supplied dependent file does not correspond with selected platform!");

        JsonObject response = this.requestJson(
                CURSEFORGE_API + "/mods/" + ModDownloadManager.encodePathSegment(dependentFile.projectId())
                        + "/files/" + ModDownloadManager.encodePathSegment(dependentFile.providerFileId()),
                dependentFile.platform()
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

                String dependencyKey = dependentFile.platform().name() + ':' + dependencyProjectId;
                if (visitedProjects.contains(dependencyKey)) continue;

                ModDownloadProject dependencyProject = this.getModDownloadProject(dependencyProjectId, favoriteProjects);
                List<ModDownloadFile> compatibleFiles = this.getAvailableModList(dependencyProject, false);
                if (compatibleFiles.isEmpty()) {
                    throw new IOException(
                            "No compatible Minecraft " + SquirrelLauncher.GAME_VERSION
                                    + " Forge file was found for required dependency "
                                    + dependencyProject.name() + '.'
                    );
                }
                dependencyFiles.add(compatibleFiles.getFirst());
            }
        }
    }

    @Override
    @NotNull
    public ModSearchResult getSearchModResults(
            @NotNull String searchText, int offset,
            @NotNull List<String> githubRepositoryUrls,
            @NotNull Map<String, ModDownloadProject> favoriteProjects,
            @NotNull Set<String> configuredGitHubProjects
    ) throws IOException, InterruptedException {
        String url = CURSEFORGE_API + "/mods/search?gameId=" + CURSEFORGE_MINECRAFT_GAME_ID
                + "&classId=" + CURSEFORGE_MOD_CLASS_ID
                + "&gameVersion=" + ModDownloadManager.encode(SquirrelLauncher.GAME_VERSION)
                + "&modLoaderType=" + CURSEFORGE_FORGE_LOADER
                + "&searchFilter=" + ModDownloadManager.encode(searchText)
                + "&sortField=2&sortOrder=desc&pageSize=" + ModDownloadManager.RESULT_LIMIT
                + "&index=" + Math.max(0, offset);
        JsonObject response = this.requestJson(url, this.modDownloadPlatform()).getAsJsonObject();

        long totalResults = -1L;
        JsonObject pagination = response.getAsJsonObject("pagination");
        if (pagination != null) totalResults = ModDownloadManager.longValue(pagination, "totalCount");

        JsonArray data = response.getAsJsonArray("data");
        if (data == null) data = new JsonArray();

        List<ModDownloadProject> projectList = new ArrayList<>();
        for (JsonElement element : data) {
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
            projectList.add(new ModDownloadProject(
                    this.modDownloadPlatform(),
                    projectId,
                    name,
                    author,
                    ModDownloadManager.string(project, "summary"),
                    iconUrl.isBlank() ? null : iconUrl,
                    pageUrl.isBlank() ? null : pageUrl,
                    ModDownloadManager.longValue(project, "downloadCount"),
                    favoriteProjects.containsKey(ModDownloadManager.favoriteKey(this.modDownloadPlatform(), projectId))
            ));
        }
        return new ModSearchResult(projectList, totalResults);
    }

    @NotNull
    public String requiredCurseForgeApiKey() throws IOException {
        if (this.curseForgeApiKey != null) return this.curseForgeApiKey;
        throw new IOException(
                "CurseForge requires a SquirrelLauncher API key. Set SQUIRREL_CURSEFORGE_API_KEY "
                        + "or -Dsquirrellauncher.curseforgeApiKey before starting the launcher."
        );
    }
}
