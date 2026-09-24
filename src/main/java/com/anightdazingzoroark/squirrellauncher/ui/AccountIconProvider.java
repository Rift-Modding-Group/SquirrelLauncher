package com.anightdazingzoroark.squirrellauncher.ui;

import com.anightdazingzoroark.squirrellauncher.minecraft.MinecraftPaths;
import com.anightdazingzoroark.squirrellauncher.minecraft.auth.MinecraftAccount;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.SwingUtilities;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class AccountIconProvider {
    private static final int ICON_SIZE = 32;
    private static final long PROFILE_CACHE_MILLIS = 24L * 60L * 60L * 1000L;
    @NotNull
    public static final AccountIconProvider INSTANCE = new AccountIconProvider();

    @NotNull
    private final ImageIcon steveIcon;
    @NotNull
    private final ConcurrentHashMap<String, ImageIcon> skinIcons = new ConcurrentHashMap<>();

    private AccountIconProvider() {
        URL resource = AccountIconProvider.class.getResource("/icons/steve.png");
        if (resource == null) throw new IllegalStateException("Missing account icon: /icons/steve.png");
        try {
            BufferedImage source = ImageIO.read(resource);
            BufferedImage scaled = new BufferedImage(ICON_SIZE, ICON_SIZE, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = scaled.createGraphics();
            graphics.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
            );
            graphics.drawImage(source, 0, 0, ICON_SIZE, ICON_SIZE, null);
            graphics.dispose();
            this.steveIcon = new ImageIcon(scaled);
        }
        catch (Exception exception) {
            throw new IllegalStateException("Could not load /icons/steve.png.", exception);
        }
    }

    @NotNull
    public ImageIcon iconFor(@NotNull MinecraftAccount account, @NotNull Runnable repaint) {
        String skinUrl = account.skinUrl();
        if (account.type() == MinecraftAccount.AccountType.OFFLINE) return this.steveIcon;

        String uuid = account.uuid().replace("-", "");
        if (!uuid.matches("[0-9a-fA-F]{32}")) return this.steveIcon;
        String cacheKey = account.key();
        ImageIcon cachedIcon = this.skinIcons.get(cacheKey);
        if (cachedIcon != null) return cachedIcon;

        Path faceFile = MinecraftPaths.ACCOUNT_ICONS.resolve(uuid + ".png");
        Path sourceFile = MinecraftPaths.ACCOUNT_ICONS.resolve(uuid + ".url");
        ImageIcon initialIcon = this.steveIcon;
        boolean hasCachedFace = Files.isRegularFile(faceFile);
        if (hasCachedFace) {
            try {
                BufferedImage cachedFace = ImageIO.read(faceFile.toFile());
                if (cachedFace != null && cachedFace.getWidth() == ICON_SIZE
                        && cachedFace.getHeight() == ICON_SIZE) {
                    initialIcon = new ImageIcon(cachedFace);
                }
                else hasCachedFace = false;
            }
            catch (Exception ignored) {
                hasCachedFace = false;
            }
        }

        ImageIcon existingIcon = this.skinIcons.putIfAbsent(cacheKey, initialIcon);
        if (existingIcon != null) return existingIcon;

        boolean refreshNeeded = !hasCachedFace;
        boolean profileLookupNeeded = skinUrl == null || skinUrl.isBlank();
        try {
            if (!refreshNeeded && skinUrl != null && !skinUrl.isBlank()) {
                refreshNeeded = !Files.isRegularFile(sourceFile)
                        || !skinUrl.equals(Files.readString(sourceFile).trim());
            }
            if (!refreshNeeded) {
                long cacheAge = System.currentTimeMillis() - Files.getLastModifiedTime(faceFile).toMillis();
                refreshNeeded = cacheAge >= PROFILE_CACHE_MILLIS;
                if (refreshNeeded) profileLookupNeeded = true;
            }
        }
        catch (Exception ignored) {
            refreshNeeded = true;
        }

        if (refreshNeeded) {
            boolean lookupProfile = profileLookupNeeded;
            CompletableFuture.runAsync(() -> {
                try {
                    String resolvedSkinUrl = lookupProfile ? null : skinUrl;
                    if (lookupProfile) {
                        URLConnection profileConnection = URI.create(
                                "https://sessionserver.mojang.com/session/minecraft/profile/" + uuid
                        ).toURL().openConnection();
                        profileConnection.setConnectTimeout(10_000);
                        profileConnection.setReadTimeout(20_000);
                        JsonObject profile;
                        try (InputStream stream = profileConnection.getInputStream()) {
                            profile = JsonParser.parseString(
                                    new String(stream.readAllBytes(), StandardCharsets.UTF_8)
                            ).getAsJsonObject();
                        }

                        if (!profile.has("properties")) return;
                        for (JsonElement element : profile.getAsJsonArray("properties")) {
                            JsonObject property = element.getAsJsonObject();
                            if (!property.has("name") || !property.has("value")
                                    || !"textures".equals(property.get("name").getAsString())) {
                                continue;
                            }
                            String decoded = new String(
                                    Base64.getDecoder().decode(property.get("value").getAsString()),
                                    StandardCharsets.UTF_8
                            );
                            JsonObject textures = JsonParser.parseString(decoded)
                                    .getAsJsonObject()
                                    .getAsJsonObject("textures");
                            if (textures != null && textures.has("SKIN")) {
                                JsonObject skin = textures.getAsJsonObject("SKIN");
                                if (skin.has("url") && !skin.get("url").isJsonNull()) {
                                    resolvedSkinUrl = skin.get("url").getAsString();
                                }
                            }
                            break;
                        }
                        if (resolvedSkinUrl == null || resolvedSkinUrl.isBlank()) return;
                    }

                    if (Files.isRegularFile(faceFile) && Files.isRegularFile(sourceFile)
                            && resolvedSkinUrl.equals(Files.readString(sourceFile).trim())) {
                        Files.setLastModifiedTime(faceFile, FileTime.fromMillis(System.currentTimeMillis()));
                        return;
                    }

                    URI uri = URI.create(resolvedSkinUrl);
                    if ("http".equalsIgnoreCase(uri.getScheme())
                            && "textures.minecraft.net".equalsIgnoreCase(uri.getHost())) {
                        uri = URI.create("https://" + resolvedSkinUrl.substring("http://".length()));
                    }
                    if (!"https".equalsIgnoreCase(uri.getScheme())) return;
                    URLConnection connection = uri.toURL().openConnection();
                    connection.setConnectTimeout(10_000);
                    connection.setReadTimeout(20_000);
                    BufferedImage skin;
                    try (InputStream stream = connection.getInputStream()) {
                        skin = ImageIO.read(stream);
                    }
                    if (skin == null || skin.getWidth() < 64) return;

                    int unit = skin.getWidth() / 8;
                    if (skin.getHeight() < unit * 2) return;
                    BufferedImage face = new BufferedImage(ICON_SIZE, ICON_SIZE, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D graphics = face.createGraphics();
                    graphics.setRenderingHint(
                            RenderingHints.KEY_INTERPOLATION,
                            RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
                    );
                    graphics.drawImage(
                            skin,
                            0, 0, ICON_SIZE, ICON_SIZE,
                            unit, unit, unit * 2, unit * 2,
                            null
                    );
                    if (skin.getWidth() >= unit * 6 && skin.getHeight() >= unit * 2) {
                        graphics.drawImage(
                                skin,
                                0, 0, ICON_SIZE, ICON_SIZE,
                                unit * 5, unit, unit * 6, unit * 2,
                                null
                        );
                    }
                    graphics.dispose();

                    Files.createDirectories(MinecraftPaths.ACCOUNT_ICONS);
                    Path temporaryFace = MinecraftPaths.ACCOUNT_ICONS.resolve(uuid + ".png.tmp");
                    if (!ImageIO.write(face, "png", temporaryFace.toFile())) return;
                    try {
                        Files.move(
                                temporaryFace,
                                faceFile,
                                StandardCopyOption.ATOMIC_MOVE,
                                StandardCopyOption.REPLACE_EXISTING
                        );
                    }
                    catch (AtomicMoveNotSupportedException exception) {
                        Files.move(temporaryFace, faceFile, StandardCopyOption.REPLACE_EXISTING);
                    }

                    Path temporarySource = MinecraftPaths.ACCOUNT_ICONS.resolve(uuid + ".url.tmp");
                    Files.writeString(temporarySource, resolvedSkinUrl);
                    try {
                        Files.move(
                                temporarySource,
                                sourceFile,
                                StandardCopyOption.ATOMIC_MOVE,
                                StandardCopyOption.REPLACE_EXISTING
                        );
                    }
                    catch (AtomicMoveNotSupportedException exception) {
                        Files.move(temporarySource, sourceFile, StandardCopyOption.REPLACE_EXISTING);
                    }

                    this.skinIcons.put(cacheKey, new ImageIcon(face));
                    SwingUtilities.invokeLater(repaint);
                }
                catch (Exception ignored) {}
            });
        }
        return initialIcon;
    }
}
