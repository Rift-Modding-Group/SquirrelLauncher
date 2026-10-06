package com.anightdazingzoroark.squirrellauncher.minecraft.mod.platform;

import com.anightdazingzoroark.squirrellauncher.minecraft.mod.ModDownloadFile;
import com.anightdazingzoroark.squirrellauncher.minecraft.mod.ModDownloadManager;
import com.anightdazingzoroark.squirrellauncher.minecraft.mod.ModDownloadProject;
import com.anightdazingzoroark.squirrellauncher.minecraft.mod.ModDownloadProjectDescription;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.HttpRetryException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Helper class that handles download and dependency operations for each mod platform
 * Mostly exists to debloat ModDownloadManager
 * */
public abstract class AbstractModPlatformHandler {
    @NotNull
    protected static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /**
     * lol
     * */
    @NotNull
    public abstract ModDownloadPlatform modDownloadPlatform();

    /**
     * Request information from a mod platform
     * */
    @NotNull
    public final JsonElement requestJson(@NotNull String url, @NotNull ModDownloadPlatform platform) throws IOException, InterruptedException {
        URI uri = URI.create(url);
        HttpRequest.Builder request = HttpRequest.newBuilder(uri)
                .timeout(ModDownloadManager.REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .header("User-Agent", ModDownloadManager.USER_AGENT);

        //additional info to send for verifying
        this.additionalRequestVerification(request, uri);

        //now get a response
        HttpResponse<String> response = httpClient.send(
                request.GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );
        if (response.statusCode() / 100 != 2) {
            throw this.requestFailure(response.statusCode(), platform, platform.name());
        }
        try {
            return JsonParser.parseString(response.body());
        }
        catch (JsonParseException exception) {
            throw new IOException("The " + platform + " response was not valid JSON.", exception);
        }
    }

    @NotNull
    protected IOException requestFailure(
            int statusCode,
            @NotNull ModDownloadPlatform platform,
            @NotNull String requestName
    ) {
        if (platform == ModDownloadPlatform.GITHUB && statusCode == 403) {
            return new HttpRetryException("GitHub API request forbidden", statusCode);
        }
        return new IOException("HTTP " + statusCode + " while requesting " + requestName + '.');
    }

    /**
     * Additional attachments to add to a request to a mod platform
     * @param request The HttpRequest Builder to which one may add additional params
     * */
    public abstract void additionalRequestVerification(@NotNull HttpRequest.Builder request, @NotNull URI uri) throws IOException;

    /**
     * Get a project description straight from the description page
     * */
    @NotNull
    public abstract ModDownloadProjectDescription getModProjectDescription(@NotNull ModDownloadProject project) throws IOException, InterruptedException;

    /**
     * check if url where icon is hosted is trustable
     * */
    public abstract boolean isIconHostTrusted(@NotNull String iconHost);

    public abstract boolean isTrustedDownloadHost(@NotNull String downloadHost);

    /**
     * Get a mod download project for download
     * @param projectId Project ID
     * @param favoriteProjects A map of favorite projects to set
     * */
    @NotNull
    public abstract ModDownloadProject getModDownloadProject(@NotNull String projectId, @NotNull Map<String, ModDownloadProject> favoriteProjects) throws IOException, InterruptedException;

    /**
     * fetch all available versions for a project
     * @param project A mod project
     * */
    @NotNull
    public abstract JsonArray getAllProjectVersions(@NotNull ModDownloadProject project) throws IOException, InterruptedException;

    /**
     * fetch all mod download files and all available versions for a project
     * @param project A mod project
     * @param newestOnly Return only the newest version and its files. Otherwise, return all versions and their files
     * */
    public List<ModDownloadFile> getAvailableModList(@NotNull ModDownloadProject project, boolean newestOnly) throws IOException, InterruptedException {
        return this.getAvailableModList(project, this.getAllProjectVersions(project), newestOnly);
    }

    /**
     * fetch all mod download files for a project
     * @param project A mod project
     * @param versions JSON array containing available versions
     * @param newestOnly Return only the newest version and its files. Otherwise, return all versions and their files
     * */
    @NotNull
    public abstract List<ModDownloadFile> getAvailableModList(@NotNull ModDownloadProject project, @NotNull JsonArray versions, boolean newestOnly);

    /**
     * fetch all dependencies of all supplied mods
     * @param dependentFile The mod project whose dependencies are to be found
     * @param dependencyFiles The list of dependencies that must be filled up
     * */
    public abstract void setModDependencies(
            @NotNull ModDownloadFile dependentFile,
            @NotNull List<ModDownloadFile> dependencyFiles,
            @NotNull Set<String> visitedProjects,
            @NotNull Map<String, @NotNull ModDownloadProject> favoriteProjects
    ) throws IOException, InterruptedException;

    /**
     * returns search results when using search function on the platform
     * @param searchText Search text!
     * @param githubRepositoryUrls reserved for use in GithubModPlatformHandler
     * */
    @NotNull
    public abstract ModSearchResult getSearchModResults(
            @NotNull String searchText, int offset,
            @NotNull List<String> githubRepositoryUrls,
            @NotNull Map<String, ModDownloadProject> favoriteProjects,
            @NotNull Set<String> configuredGitHubProjects
    ) throws IOException, InterruptedException;

    public record ModSearchResult(@NotNull List<ModDownloadProject> projectList, long totalResults) {}
}
