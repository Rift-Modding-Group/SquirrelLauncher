package com.anightdazingzoroark.squirrellauncher.minecraft.mod.platform;

import com.anightdazingzoroark.squirrellauncher.SquirrelLauncher;
import com.anightdazingzoroark.squirrellauncher.minecraft.mod.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.util.*;

public final class ModrinthModPlatformHandler extends AbstractModPlatformHandler {
    @NotNull
    private static final String MODRINTH_API = "https://api.modrinth.com/v2";
    @NotNull
    private static final String MODRINTH_DOWNLOAD_HOST = "cdn.modrinth.com";

    @Override
    @NotNull
    public ModDownloadPlatform modDownloadPlatform() {
        return ModDownloadPlatform.MODRINTH;
    }

    //modrinth w
    @Override
    public void additionalRequestVerification(@NotNull HttpRequest.Builder request, @NotNull URI uri) throws IOException {}

    @Override
    @NotNull
    public ModDownloadProjectDescription getModProjectDescription(@NotNull ModDownloadProject project) throws IOException, InterruptedException {
        JsonObject response = this.requestJson(
                MODRINTH_API + "/project/" + ModDownloadManager.encodePathSegment(project.projectId()),
                project.platform()
        ).getAsJsonObject();
        String description = ModDownloadManager.string(response, "body");
        return new ModDownloadProjectDescription(
                description.isBlank() ? project.description() : description,
                false
        );
    }

    @Override
    public boolean isIconHostTrusted(@NotNull String iconHost) {
        return iconHost.equalsIgnoreCase(MODRINTH_DOWNLOAD_HOST);
    }

    @Override
    public boolean isTrustedDownloadHost(@NotNull String downloadHost) {
        return downloadHost.equalsIgnoreCase(MODRINTH_DOWNLOAD_HOST);
    }

    @Override
    @NotNull
    public ModDownloadProject getModDownloadProject(@NotNull String projectId, @NotNull Map<String, ModDownloadProject> favoriteProjects) throws IOException, InterruptedException {
        JsonObject project = this.requestJson(
                MODRINTH_API + "/project/" + ModDownloadManager.encodePathSegment(projectId),
                this.modDownloadPlatform()
        ).getAsJsonObject();
        String name = ModDownloadManager.string(project, "title");
        if (name.isBlank()) name = projectId;
        String iconUrl = ModDownloadManager.string(project, "icon_url");
        String slug = ModDownloadManager.string(project, "slug");
        return new ModDownloadProject(
                this.modDownloadPlatform(),
                projectId,
                name,
                "",
                ModDownloadManager.string(project, "description"),
                iconUrl.isBlank() ? null : iconUrl,
                "https://modrinth.com/mod/" + ModDownloadManager.encodePathSegment(
                        slug.isBlank() ? projectId : slug
                ),
                ModDownloadManager.longValue(project, "downloads"),
                favoriteProjects.containsKey(ModDownloadManager.favoriteKey(this.modDownloadPlatform(), projectId))
        );
    }

    @Override
    @NotNull
    public JsonArray getAllProjectVersions(@NotNull ModDownloadProject project) throws IOException, InterruptedException {
        String url = MODRINTH_API + "/project/" + ModDownloadManager.encode(project.projectId()) + "/version"
                + "?loaders=" + ModDownloadManager.encode("[\"forge\"]")
                + "&game_versions=" + ModDownloadManager.encode("[\"" + SquirrelLauncher.GAME_VERSION + "\"]")
                + "&include_changelog=false";
        return this.requestJson(url, project.platform()).getAsJsonArray();
    }

    @Override
    @NotNull
    public List<ModDownloadFile> getAvailableModList(@NotNull ModDownloadProject project, @NotNull JsonArray versions, boolean newestOnly) {
        List<ModDownloadFile> toReturn = new ArrayList<>();
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
            toReturn.add(new ModDownloadFile(
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

            //premature break for first only
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

        JsonObject version = this.requestJson(
                MODRINTH_API + "/version/" + ModDownloadManager.encodePathSegment(dependentFile.providerFileId()),
                dependentFile.platform()
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
                            MODRINTH_API + "/version/" + ModDownloadManager.encodePathSegment(dependencyVersionId),
                            dependentFile.platform()
                    ).getAsJsonObject();
                    if (dependencyProjectId.isBlank()) {
                        dependencyProjectId = ModDownloadManager.string(dependencyVersion, "project_id");
                    }
                }
                if (dependencyProjectId.isBlank()) continue;

                String dependencyKey = dependentFile.platform().name() + ':' + dependencyProjectId;
                if (visitedProjects.contains(dependencyKey)) continue;

                ModDownloadProject dependencyProject = this.getModDownloadProject(dependencyProjectId, favoriteProjects);
                if (dependencyVersion == null) {
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
                else {
                    JsonArray exactVersion = new JsonArray();
                    exactVersion.add(dependencyVersion);
                    List<ModDownloadFile> exactFiles = ModDownloadPlatform.MODRINTH.getModPlatformHandler().getAvailableModList(dependencyProject, exactVersion, false);
                    if (exactFiles.isEmpty()) {
                        throw new IOException("The required dependency version for " + dependencyProject.name() + " has no downloadable JAR.");
                    }
                    dependencyFiles.add(exactFiles.getFirst());
                }
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
        String facets = "[[\"project_type:mod\"],[\"versions:" + SquirrelLauncher.GAME_VERSION
                + "\"],[\"categories:forge\"]]";
        String url = MODRINTH_API + "/search?query=" + ModDownloadManager.encode(searchText)
                + "&facets=" + ModDownloadManager.encode(facets)
                + (searchText.isEmpty() ? "&index=downloads" : "")
                + "&limit=" + ModDownloadManager.RESULT_LIMIT
                + "&offset=" + Math.max(0, offset);
        JsonObject response = this.requestJson(url, this.modDownloadPlatform()).getAsJsonObject();
        long totalResults = ModDownloadManager.longValue(response, "total_hits");

        JsonArray hits = response.getAsJsonArray("hits");
        if (hits == null) hits = new JsonArray();

        List<ModDownloadProject> projectList = new ArrayList<>();
        for (JsonElement element : hits) {
            if (!element.isJsonObject()) continue;
            JsonObject hit = element.getAsJsonObject();
            String projectId = ModDownloadManager.string(hit, "project_id");
            String name = ModDownloadManager.string(hit, "title");
            if (projectId.isBlank() || name.isBlank()) continue;
            String iconUrl = ModDownloadManager.string(hit, "icon_url");
            String slug = ModDownloadManager.string(hit, "slug");
            projectList.add(new ModDownloadProject(
                    this.modDownloadPlatform(),
                    projectId,
                    name,
                    ModDownloadManager.string(hit, "author"),
                    ModDownloadManager.string(hit, "description"),
                    iconUrl.isBlank() ? null : iconUrl,
                    "https://modrinth.com/mod/" + ModDownloadManager.encodePathSegment(
                            slug.isBlank() ? projectId : slug
                    ),
                    ModDownloadManager.longValue(hit, "downloads"),
                    favoriteProjects.containsKey(ModDownloadManager.favoriteKey(this.modDownloadPlatform(), projectId))
            ));
        }
        return new ModSearchResult(projectList, totalResults);
    }
}