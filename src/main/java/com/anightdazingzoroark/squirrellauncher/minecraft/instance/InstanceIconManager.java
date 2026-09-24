package com.anightdazingzoroark.squirrellauncher.minecraft.instance;

import com.anightdazingzoroark.squirrellauncher.minecraft.MinecraftPaths;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.Locale;
import java.util.UUID;

public final class InstanceIconManager {
    private static final int MAX_ICON_DIMENSION = 1024;
    @NotNull
    private static final String[] SUPPORTED_EXTENSIONS = { ".png", ".jpg", ".jpeg", ".gif", ".bmp" };

    private InstanceIconManager() {}

    @NotNull
    public static MinecraftInstance assign(@NotNull MinecraftInstance instance, @NotNull Path source) throws IOException {
        if (!Files.isRegularFile(source)) throw new IOException("Icon file does not exist: " + source);

        BufferedImage image;
        try (ImageInputStream input = ImageIO.createImageInputStream(source.toFile())) {
            if (input == null) throw new IOException("Could not read the selected icon.");
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("The selected file is not a supported image.");

            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > MAX_ICON_DIMENSION || height > MAX_ICON_DIMENSION) {
                    throw new IOException("Instance icons must be no larger than 1024 × 1024 pixels.");
                }
                image = reader.read(0);
            }
            finally {
                reader.dispose();
            }
        }
        if (image == null) throw new IOException("Could not decode the selected icon.");

        Files.createDirectories(MinecraftPaths.INSTANCE_ICONS);
        String iconKey = "squirrel-" + UUID.randomUUID().toString().toLowerCase(Locale.ROOT);
        Path destination = MinecraftPaths.INSTANCE_ICONS.resolve(iconKey + ".png");
        Path temporary = Files.createTempFile(MinecraftPaths.INSTANCE_ICONS, ".icon-", ".png");
        boolean moved = false;
        try {
            if (!ImageIO.write(image, "png", temporary.toFile())) {
                throw new IOException("Could not encode the selected icon.");
            }
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
            }
            catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, destination);
            }
            moved = true;
            InstanceManager.setIconKey(instance.directory(), iconKey);
        }
        catch (Exception exception) {
            if (moved) Files.deleteIfExists(destination);
            throw exception;
        }
        finally {
            Files.deleteIfExists(temporary);
        }
        return InstanceManager.load(instance.id());
    }

    @NotNull
    public static MinecraftInstance reset(@NotNull MinecraftInstance instance) throws IOException {
        InstanceManager.setIconKey(instance.directory(), null);
        return InstanceManager.load(instance.id());
    }

    @Nullable
    public static Path customIcon(@NotNull MinecraftInstance instance) {
        String iconKey = instance.iconKey();
        if (iconKey == null || iconKey.isBlank()) return null;
        try {
            Path keyPath = Path.of(iconKey);
            if (keyPath.isAbsolute() || keyPath.getNameCount() != 1) return null;
        }
        catch (RuntimeException exception) {
            return null;
        }

        Path directGlobal = MinecraftPaths.INSTANCE_ICONS.resolve(iconKey);
        Path directInstance = instance.directory().resolve(iconKey);
        if (Files.isRegularFile(directGlobal)) return directGlobal;
        if (Files.isRegularFile(directInstance)) return directInstance;
        for (String extension : SUPPORTED_EXTENSIONS) {
            Path global = MinecraftPaths.INSTANCE_ICONS.resolve(iconKey + extension);
            if (Files.isRegularFile(global)) return global;
            Path local = instance.directory().resolve(iconKey + extension);
            if (Files.isRegularFile(local)) return local;
        }
        return null;
    }
}
