package com.anightdazingzoroark.squirrellauncher.minecraft.mod;

import org.jetbrains.annotations.NotNull;

import java.net.URI;

//representation of a github repo of a mod
public record GitHubModRepository(@NotNull String owner, @NotNull String repository) {
    public GitHubModRepository {
        if (owner.isBlank() || repository.isBlank()
                || !owner.matches("[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?")
                || !repository.matches("[A-Za-z0-9_.-]+")
        ) {
            throw new IllegalArgumentException("Invalid GitHub repository name: " + owner + '/' + repository);
        }
    }

    @NotNull
    public static GitHubModRepository parse(@NotNull String repositoryUrl) {
        URI uri;
        try {
            uri = URI.create(repositoryUrl.trim());
        }
        catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid GitHub repository URL: " + repositoryUrl, exception);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || uri.getHost() == null
                || !uri.getHost().equalsIgnoreCase("github.com")
                || uri.getQuery() != null
                || uri.getFragment() != null
        ) {
            throw new IllegalArgumentException("GitHub repository URLs must use https://github.com.");
        }
        String[] pathParts = uri.getPath().split("/");
        if (pathParts.length < 3 || pathParts.length > 4
                || pathParts[1].isBlank() || pathParts[2].isBlank()
                || (pathParts.length == 4 && !pathParts[3].equals("releases"))
        ) {
            throw new IllegalArgumentException(
                    "GitHub repository URLs must end after the repository name or /releases."
            );
        }
        String repository = pathParts[2].endsWith(".git")
                ? pathParts[2].substring(0, pathParts[2].length() - 4)
                : pathParts[2];
        return new GitHubModRepository(pathParts[1], repository);
    }

    @NotNull
    public String projectId() {
        return this.owner + '/' + this.repository;
    }

    @NotNull
    public String apiUrl() {
        return "https://api.github.com/repos/" + this.owner + '/' + this.repository;
    }

    @NotNull
    public String releasesUrl() {
        return "https://github.com/" + this.owner + '/' + this.repository + "/releases";
    }
}
