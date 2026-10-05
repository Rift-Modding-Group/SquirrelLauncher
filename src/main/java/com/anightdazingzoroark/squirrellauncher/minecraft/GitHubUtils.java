package com.anightdazingzoroark.squirrellauncher.minecraft;

import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.net.http.HttpRequest;

/**
 * mostly for allowing authentication w a personal access token
 */
public final class GitHubUtils {
    @NotNull
    private static volatile String personalAccessToken = "";

    private GitHubUtils() {}

    public static void setPersonalAccessToken(@NotNull String personalAccessToken) {
        GitHubUtils.personalAccessToken = personalAccessToken.trim();
    }

    public static void authenticate(@NotNull HttpRequest.Builder request, @NotNull URI uri) {
        String host = uri.getHost();
        if (host == null || !host.equalsIgnoreCase("api.github.com")) return;
        String personalAccessToken = GitHubUtils.personalAccessToken;
        if (!personalAccessToken.isEmpty()) {
            request.header("Authorization", "Bearer " + personalAccessToken);
        }
    }
}
