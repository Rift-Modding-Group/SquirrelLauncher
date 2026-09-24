package com.anightdazingzoroark.squirrellauncher.ui;

import com.anightdazingzoroark.squirrellauncher.minecraft.instance.InstanceIconManager;
import com.anightdazingzoroark.squirrellauncher.minecraft.instance.InstanceType;
import com.anightdazingzoroark.squirrellauncher.minecraft.instance.MinecraftInstance;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.swing.ImageIcon;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.net.URL;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public final class InstanceIconProvider {
    private static final int ICON_SIZE = 48;
    private static final int MAX_CACHED_ICONS = 64;
    @NotNull
    public static final InstanceIconProvider INSTANCE = new InstanceIconProvider();

    @NotNull
    private final EnumMap<InstanceType, ImageIcon> typeIcons = new EnumMap<>(InstanceType.class);
    @NotNull
    private final Map<Path, ImageIcon> customIcons = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(@NotNull Map.Entry<Path, ImageIcon> eldest) {
            return this.size() > MAX_CACHED_ICONS;
        }
    };

    private InstanceIconProvider() {
        for (InstanceType type : InstanceType.values()) {
            URL resource = InstanceIconProvider.class.getResource(type.getIconPath());
            if (resource == null) throw new IllegalStateException("Missing instance icon: " + type.getIconPath());
            try {
                BufferedImage image = ImageIO.read(resource);
                if (image == null) throw new IllegalStateException("Could not decode " + type.getIconPath() + ".");
                this.typeIcons.put(type, this.scale(image));
            }
            catch (Exception exception) {
                throw new IllegalStateException("Could not load " + type.getIconPath() + ".", exception);
            }
        }
    }

    @NotNull
    public synchronized ImageIcon iconFor(@NotNull MinecraftInstance instance) {
        Path customIcon = InstanceIconManager.customIcon(instance);
        if (customIcon == null) return this.typeIcons.get(instance.type());
        customIcon = customIcon.toAbsolutePath().normalize();
        ImageIcon cached = this.customIcons.get(customIcon);
        if (cached != null) return cached;

        try (ImageInputStream input = ImageIO.createImageInputStream(customIcon.toFile())) {
            if (input == null) return this.typeIcons.get(instance.type());
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) return this.typeIcons.get(instance.type());
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > 1024 || height > 1024) {
                    return this.typeIcons.get(instance.type());
                }
                BufferedImage image = reader.read(0);
                if (image == null) return this.typeIcons.get(instance.type());
                ImageIcon icon = this.scale(image);
                this.customIcons.put(customIcon, icon);
                return icon;
            }
            finally {
                reader.dispose();
            }
        }
        catch (Exception exception) {
            return this.typeIcons.get(instance.type());
        }
    }

    @NotNull
    private ImageIcon scale(@NotNull BufferedImage source) {
        BufferedImage target = new BufferedImage(ICON_SIZE, ICON_SIZE, BufferedImage.TYPE_INT_ARGB);
        double scale = Math.min((double) ICON_SIZE / source.getWidth(), (double) ICON_SIZE / source.getHeight());
        int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
        int x = (ICON_SIZE - width) / 2;
        int y = (ICON_SIZE - height) / 2;
        Graphics2D graphics = target.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.drawImage(source, x, y, width, height, null);
        graphics.dispose();
        return new ImageIcon(target);
    }
}
