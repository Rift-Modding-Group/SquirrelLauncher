package com.anightdazingzoroark.squirrellauncher.launcher;

import org.jetbrains.annotations.NotNull;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Locale;

/** Platform-aware validation for instance names used as directory names. */
public final class InstanceNames {
    private static final boolean WINDOWS = System.getProperty("os.name")
            .toLowerCase(Locale.ROOT)
            .contains("windows");

    private InstanceNames() {}

    /**
     * Returns whether the text can still become a valid folder name. Empty and
     * temporarily incomplete names remain typeable so editing behaves normally.
     */
    public static boolean canType(@NotNull String name) {
        if (name.isEmpty()) return true;
        try {
            Path path = Path.of(name);
            return !path.isAbsolute()
                    && path.getNameCount() == 1
                    && name.equals(path.getFileName().toString());
        }
        catch (InvalidPathException exception) {
            return false;
        }
    }

    public static boolean isValid(@NotNull String name) {
        if (name.isBlank() || name.equals(".") || name.equals("..") || !canType(name)) return false;
        if (!WINDOWS) return true;

        if (name.endsWith(" ") || name.endsWith(".")) return false;
        String baseName = name.split("\\.", 2)[0].stripTrailing().toUpperCase(Locale.ROOT);
        return !baseName.matches("CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9]");
    }
}
