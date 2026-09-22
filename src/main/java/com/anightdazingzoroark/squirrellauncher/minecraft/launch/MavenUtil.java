package com.anightdazingzoroark.squirrellauncher.minecraft.launch;

import org.jetbrains.annotations.NotNull;

public final class MavenUtil {
    private MavenUtil() {}

    @NotNull
    public static String artifactKey(@NotNull String coordinate) {
        String value = coordinate.split("@")[0];
        String[] parts = value.split(":");
        if (parts.length < 2) return coordinate;
        return parts[0] + ":" + parts[1];
    }

    @NotNull
    public static String classpathKey(@NotNull String coordinate) {
        String value = coordinate.split("@")[0];

        String[] parts = value.split(":");
        if (parts.length < 2) return coordinate;

        //ordinary artifact (group:artifact)
        if (parts.length < 4) return parts[0] + ":" + parts[1];

        //classifier artifact (group:artifact:classifier), must be different from normal jar
        return parts[0] + ":" + parts[1] + ":" + parts[3];
    }

    @NotNull
    public static String path(@NotNull String coordinate) {
        String extension = "jar";

        int at = coordinate.indexOf('@');
        if (at >= 0) {
            extension = coordinate.substring(at + 1);
            coordinate = coordinate.substring(0, at);
        }

        String[] parts = coordinate.split(":");
        if (parts.length < 3) {
            throw new IllegalArgumentException("Invalid Maven coordinate: " + coordinate);
        }

        String group = parts[0];
        String artifact = parts[1];
        String version = parts[2];

        String classifier = parts.length >= 4 ? parts[3] : null;
        String file = artifact + "-" + version + (classifier == null ? "" : "-" + classifier) + "." + extension;

        return group.replace('.', '/') + "/" + artifact + "/" + version + "/" + file;
    }
}