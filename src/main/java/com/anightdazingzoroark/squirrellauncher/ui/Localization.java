package com.anightdazingzoroark.squirrellauncher.ui;

import com.anightdazingzoroark.squirrellauncher.launcher.LauncherLanguage;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public final class Localization {
    private static volatile Map<String, String> translations = Localization.loadTranslations(
            LauncherLanguage.systemDefault()
    );

    private Localization() {}

    /**
     * Returns a translated value and replaces numbered placeholders such as {@code {0}}.
     */
    @NotNull
    public static String text(@NotNull String key, @NotNull Object... arguments) {
        String value = Localization.translations.getOrDefault(key, key);
        for (int index = 0; index < arguments.length; index++) {
            value = value.replace("{" + index + "}", String.valueOf(arguments[index]));
        }
        return value;
    }

    public static synchronized void configure(@NotNull LauncherLanguage language) {
        Localization.translations = Localization.loadTranslations(language);
    }

    @NotNull
    private static Map<String, String> loadTranslations(@NotNull LauncherLanguage language) {
        Map<String, String> translations = new HashMap<>();
        Localization.loadLanguage(LauncherLanguage.EN_US.code(), translations);
        if (language != LauncherLanguage.EN_US) Localization.loadLanguage(language.code(), translations);
        return Map.copyOf(translations);
    }

    private static void loadLanguage(@NotNull String language, @NotNull Map<String, String> translations) {
        String resourcePath = "/lang/" + language + ".lang";
        InputStream stream = Localization.class.getResourceAsStream(resourcePath);
        if (stream == null) throw new IllegalStateException("Missing language file: " + resourcePath);

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                String trimmedLine = line.trim();
                if (trimmedLine.isEmpty() || trimmedLine.startsWith("#")) continue;
                int separator = line.indexOf('=');
                if (separator <= 0) {
                    throw new IllegalStateException(resourcePath + ":" + lineNumber + " is not a key=value entry.");
                }
                String key = line.substring(0, separator).trim();
                String value = line.substring(separator + 1).trim();
                translations.put(key, value);
            }
        }
        catch (IOException exception) {
            throw new IllegalStateException("Could not load language file: " + resourcePath, exception);
        }
    }
}
