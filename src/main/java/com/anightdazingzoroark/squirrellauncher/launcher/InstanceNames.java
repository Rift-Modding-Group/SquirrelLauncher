package com.anightdazingzoroark.squirrellauncher.launcher;

import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/** MMC-compatible display-name validation and instance-folder sanitizing. */
public final class InstanceNames {
    @NotNull
    private static final String INVALID_FOLDER_CHARACTERS = "<>:\"|?*!\\/";

    private InstanceNames() {}

    public static boolean isValid(@NotNull String name) {
        if (name.isBlank()) return false;
        return name.codePoints().noneMatch(Character::isISOControl);
    }

    @NotNull
    public static String folderName(@NotNull String name) {
        StringBuilder result = new StringBuilder(name.length());
        name.codePoints().forEach(character -> {
            boolean invalid = Character.isISOControl(character)
                    || INVALID_FOLDER_CHARACTERS.indexOf(character) >= 0;
            result.appendCodePoint(invalid ? '-' : character);
        });

        while (!result.isEmpty()) {
            int last = result.length() - 1;
            if (result.charAt(last) != ' ' && result.charAt(last) != '.') break;
            result.setCharAt(last, '-');
        }

        String folderName = result.toString();
        if (folderName.equals(".") || folderName.equals("..")) folderName = folderName.replace('.', '-');

        String baseName = folderName.split("\\.", 2)[0].stripTrailing().toUpperCase(Locale.ROOT);
        if (baseName.matches("CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9]")) folderName += "-";
        return folderName;
    }
}
