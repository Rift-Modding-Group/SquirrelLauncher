package com.anightdazingzoroark.squirrellauncher.launcher;

import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/** Languages supported by the launcher interface. */
public enum LauncherLanguage {
    EN_US("en_us"),
    ES_MX("es_mx");

    @NotNull
    private final String code;

    LauncherLanguage(@NotNull String code) {
        this.code = code;
    }

    @NotNull
    public String code() {
        return this.code;
    }

    @NotNull
    public static LauncherLanguage fromCode(@NotNull String code) {
        String normalizedCode = code.replace('-', '_').toLowerCase(Locale.ROOT);
        for (LauncherLanguage language : LauncherLanguage.values()) {
            if (language.code.equals(normalizedCode)) return language;
        }
        throw new IllegalArgumentException("Unsupported launcher language: " + code);
    }

    @NotNull
    public static LauncherLanguage systemDefault() {
        String configuredLanguage = System.getProperty("squirrellauncher.language");
        if (configuredLanguage != null) {
            try {
                return LauncherLanguage.fromCode(configuredLanguage);
            }
            catch (IllegalArgumentException ignored) {}
        }
        Locale locale = Locale.getDefault();
        if (locale.getLanguage().equalsIgnoreCase("es") && locale.getCountry().equalsIgnoreCase("MX")) {
            return LauncherLanguage.ES_MX;
        }
        return LauncherLanguage.EN_US;
    }
}
