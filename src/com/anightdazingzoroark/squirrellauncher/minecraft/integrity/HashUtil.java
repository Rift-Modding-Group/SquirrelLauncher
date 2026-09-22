package com.anightdazingzoroark.squirrellauncher.minecraft.integrity;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class HashUtil {
    private HashUtil() {}

    @NotNull
    public static String sha1(@NotNull Path file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-1");
        }
        catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 is unavailable.", e);
        }

        try (InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];

            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }

        byte[] hash = digest.digest();
        StringBuilder result = new StringBuilder(hash.length * 2);
        for (byte value : hash) {
            result.append(String.format("%02x", value & 0xff));
        }
        return result.toString();
    }
}