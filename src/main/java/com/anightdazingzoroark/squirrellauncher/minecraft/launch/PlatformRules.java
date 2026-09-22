package com.anightdazingzoroark.squirrellauncher.minecraft.launch;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

public final class PlatformRules {
    private PlatformRules() {}

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public static boolean isLibraryAllowed(@NotNull JsonObject library) {
        if (!library.has("rules")) return true;

        boolean allowed = false;
        for (JsonElement element : library.getAsJsonArray("rules")) {
            JsonObject rule = element.getAsJsonObject();
            if (!matches(rule)) continue;

            allowed = "allow".equals(rule.get("action").getAsString());
        }

        return allowed;
    }

    private static boolean matches(@NotNull JsonObject rule) {
        if (!rule.has("os")) return true;

        JsonObject os = rule.getAsJsonObject("os");
        if (os.has("name")) {
            if (!os.get("name").getAsString().equals(osName())) {
                return false;
            }
        }

        if (os.has("arch")) {
            String pattern = os.get("arch").getAsString();
            return System.getProperty("os.arch").matches(pattern);
        }

        return true;
    }

    @NotNull
    public static String osName() {
        String name = System.getProperty("os.name").toLowerCase(Locale.ROOT);

        if (name.contains("win")) return "windows";
        else if (name.contains("mac")) return "osx";
        else return "linux";
    }
}