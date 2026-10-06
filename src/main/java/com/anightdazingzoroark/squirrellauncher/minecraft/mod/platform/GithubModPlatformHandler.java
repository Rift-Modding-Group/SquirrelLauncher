package com.anightdazingzoroark.squirrellauncher.minecraft.mod.platform;

import com.anightdazingzoroark.squirrellauncher.minecraft.GitHubAuthentication;
import com.anightdazingzoroark.squirrellauncher.minecraft.mod.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class GithubModPlatformHandler extends AbstractModPlatformHandler {
    @Override
    @NotNull
    public ModDownloadPlatform modDownloadPlatform() {
        return ModDownloadPlatform.GITHUB;
    }

    public void additionalRequestVerification(@NotNull HttpRequest.Builder request, @NotNull URI uri) throws IOException {
        request.setHeader("Accept", "application/vnd.github+json");
        request.header("X-GitHub-Api-Version", "2022-11-28");
        GitHubAuthentication.authenticate(request, uri);
    }

    @Override
    @NotNull
    public ModDownloadProjectDescription getModProjectDescription(@NotNull ModDownloadProject project) throws IOException, InterruptedException {
        GitHubModRepository repository = GitHubModRepository.parse("https://github.com/" + project.projectId());
        URI uri = URI.create(repository.apiUrl() + "/readme");
        HttpRequest.Builder request = HttpRequest.newBuilder(uri)
                .timeout(ModDownloadManager.REQUEST_TIMEOUT)
                .header("Accept", "application/vnd.github.raw+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", ModDownloadManager.USER_AGENT);
        GitHubAuthentication.authenticate(request, uri);
        HttpResponse<String> response = httpClient.send(
                request.GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );
        if (response.statusCode() == 404) {
            return new ModDownloadProjectDescription(project.description(), false);
        }
        if (response.statusCode() / 100 != 2) {
            throw this.requestFailure(response.statusCode(), project.platform(), "GitHub README");
        }
        return new ModDownloadProjectDescription(
                response.body().isBlank() ? project.description() : response.body(),
                false
        );
    }

    @Override
    public boolean isIconHostTrusted(@NotNull String iconHost) {
        return iconHost.equalsIgnoreCase("avatars.githubusercontent.com") || iconHost.toLowerCase(Locale.ROOT).endsWith(".githubusercontent.com");
    }

    @Override
    public boolean isTrustedDownloadHost(@NotNull String downloadHost) {
        return downloadHost.equalsIgnoreCase("github.com");
    }

    @Override
    @NotNull
    public ModDownloadProject getModDownloadProject(@NotNull String projectId, @NotNull Map<String, ModDownloadProject> favoriteProjects) throws IOException, InterruptedException {
        GitHubModRepository repository = GitHubModRepository.parse("https://github.com/" + projectId);

        JsonObject project = this.requestJson(repository.apiUrl(), ModDownloadPlatform.GITHUB).getAsJsonObject();
        String name = ModDownloadManager.string(project, "name");
        if (name.isBlank()) name = repository.repository();
        JsonObject owner = project.getAsJsonObject("owner");
        String author = owner == null ? repository.owner() : ModDownloadManager.string(owner, "login");
        if (author.isBlank()) author = repository.owner();
        String iconUrl = owner == null ? "" : ModDownloadManager.string(owner, "avatar_url");

        String repositoryId = repository.projectId();
        return new ModDownloadProject(
                ModDownloadPlatform.GITHUB,
                repositoryId,
                name,
                author,
                ModDownloadManager.string(project, "description"),
                iconUrl.isBlank() ? null : iconUrl,
                repository.releasesUrl(),
                0L,
                favoriteProjects.containsKey(ModDownloadManager.favoriteKey(ModDownloadPlatform.GITHUB, repositoryId))
        );
    }

    @Override
    @NotNull
    public JsonArray getAllProjectVersions(@NotNull ModDownloadProject project) throws IOException, InterruptedException {
        GitHubModRepository repository = GitHubModRepository.parse("https://github.com/" + project.projectId());
        return this.requestJson(repository.apiUrl() + "/releases?per_page=100", project.platform()).getAsJsonArray();
    }

    @Override
    @NotNull
    public List<ModDownloadFile> getAvailableModList(@NotNull ModDownloadProject project, @NotNull JsonArray versions, boolean newestOnly) {
        List<ModDownloadFile> toReturn = new ArrayList<>();
        for (JsonElement releaseElement : versions) {
            if (!releaseElement.isJsonObject()) continue;
            JsonObject release = releaseElement.getAsJsonObject();
            JsonElement draft = release.get("draft");
            if (draft != null && draft.isJsonPrimitive() && draft.getAsBoolean()) continue;
            JsonArray assets = release.getAsJsonArray("assets");
            if (assets == null) continue;
            String versionName = ModDownloadManager.string(release, "name");
            if (versionName.isBlank()) versionName = ModDownloadManager.string(release, "tag_name");
            JsonElement prerelease = release.get("prerelease");
            String releaseType = prerelease != null && prerelease.isJsonPrimitive() && prerelease.getAsBoolean() ? "beta" : "release";
            for (JsonElement assetElement : assets) {
                if (!assetElement.isJsonObject()) continue;
                JsonObject asset = assetElement.getAsJsonObject();
                String fileName = ModDownloadManager.string(asset, "name");
                if (!fileName.toLowerCase(Locale.ROOT).endsWith(".jar")) continue;
                String fileId = ModDownloadManager.string(asset, "id");
                String downloadUrl = ModDownloadManager.string(asset, "browser_download_url");
                if (fileId.isBlank() || downloadUrl.isBlank()) continue;
                toReturn.add(new ModDownloadFile(
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
    ) throws IOException, InterruptedException {}

    @Override
    @NotNull
    public ModSearchResult getSearchModResults(
            @NotNull String searchText, int offset,
            @NotNull List<String> githubRepositoryUrls,
            @NotNull Map<String, ModDownloadProject> favoriteProjects,
            @NotNull Set<String> configuredGitHubProjects
    ) throws IOException, InterruptedException {
        if (offset > 0) return new ModSearchResult(List.of(), -1L);
        String normalizedSearch = searchText.toLowerCase(Locale.ROOT);

        List<ModDownloadProject> projectList = new ArrayList<>();
        for (String repositoryUrl : githubRepositoryUrls) {
            GitHubModRepository repository = GitHubModRepository.parse(repositoryUrl);
            configuredGitHubProjects.add(repository.projectId());
            ModDownloadProject project = this.githubProject(repository, favoriteProjects);
            if (!normalizedSearch.isEmpty()
                    && !project.name().toLowerCase(Locale.ROOT).contains(normalizedSearch)
                    && !project.author().toLowerCase(Locale.ROOT).contains(normalizedSearch)
                    && !project.description().toLowerCase(Locale.ROOT).contains(normalizedSearch)
                    && !project.projectId().toLowerCase(Locale.ROOT).contains(normalizedSearch)) continue;
            projectList.add(project);
        }
        return new ModSearchResult(projectList, projectList.size());
    }

    private ModDownloadProject githubProject(
            @NotNull GitHubModRepository repository,
            @NotNull Map<String, ModDownloadProject> favoriteProjects
    ) throws IOException, InterruptedException {
        JsonObject project = this.requestJson(repository.apiUrl(), this.modDownloadPlatform()).getAsJsonObject();

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
                favoriteProjects.containsKey(ModDownloadManager.favoriteKey(ModDownloadPlatform.GITHUB, projectId))
        );
    }
}
