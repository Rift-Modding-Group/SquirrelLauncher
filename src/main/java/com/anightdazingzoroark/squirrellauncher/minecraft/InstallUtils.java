package com.anightdazingzoroark.squirrellauncher.minecraft;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipFile;

public final class InstallUtils {
    private InstallUtils() {}

    /**
     * json read helper
     * */
    @NotNull
    public static JsonObject readJson(@NotNull Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    public static boolean isValidArchive(@NotNull Path file) {
        if (!Files.isRegularFile(file)) return false;

        try (ZipFile ignored = new ZipFile(file.toFile())) {
            return true;
        }
        catch (IOException e) {
            return false;
        }
    }
}
