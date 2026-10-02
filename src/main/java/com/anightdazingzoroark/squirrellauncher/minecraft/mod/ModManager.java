package com.anightdazingzoroark.squirrellauncher.minecraft.mod;

import com.anightdazingzoroark.squirrellauncher.minecraft.instance.InstanceType;
import com.anightdazingzoroark.squirrellauncher.minecraft.instance.MinecraftInstance;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class ModManager {
    @NotNull
    private static final String DISABLED_SUFFIX = ".disabled";
    private static final int MOD_ICON_SIZE = 48;
    @NotNull
    private final MinecraftInstance instance;
    @NotNull
    private final Path modsDirectory;

    public ModManager(@NotNull MinecraftInstance instance) {
        this.instance = instance;
        this.modsDirectory = instance.modsDirectory();
    }

    public void initialize() throws IOException {
        if (this.instance.type() == InstanceType.VANILLA) {
            throw new IllegalStateException("Vanilla instances do not support mods.");
        }
        Files.createDirectories(this.modsDirectory);
    }

    @NotNull
    public ManagedMod install(@NotNull Path source) throws IOException {
        this.initialize();
        ModManager.validateModFile(source);

        String fileName = source.getFileName().toString();
        Path enabledTarget = this.resolveSafe(fileName);
        Path disabledTarget = this.resolveSafe(fileName + DISABLED_SUFFIX);
        if (Files.exists(enabledTarget) || Files.exists(disabledTarget)) {
            throw new IOException("Mod is already managed by this instance: " + fileName);
        }

        Files.copy(source, enabledTarget);
        System.out.println("Installed mod: " + fileName);
        return this.describe(fileName, enabledTarget, ModState.ENABLED);
    }

    @NotNull
    public ManagedMod disable(@NotNull String fileName) throws IOException {
        this.initialize();
        Path source = this.resolveSafe(fileName);
        if (!Files.isRegularFile(source)) throw new IOException("Enabled mod not found: " + fileName);

        Path destination = this.resolveSafe(fileName + DISABLED_SUFFIX);
        if (Files.exists(destination)) throw new IOException("Disabled mod already exists: " + fileName);

        Files.move(source, destination);
        return this.describe(fileName, destination, ModState.DISABLED);
    }

    @NotNull
    public ManagedMod enable(@NotNull String fileName) throws IOException {
        this.initialize();
        Path source = this.resolveSafe(fileName + DISABLED_SUFFIX);
        if (!Files.isRegularFile(source)) throw new IOException("Disabled mod not found: " + fileName);

        Path destination = this.resolveSafe(fileName);
        if (Files.exists(destination)) throw new IOException("Enabled mod already exists: " + fileName);

        Files.move(source, destination);
        return this.describe(fileName, destination, ModState.ENABLED);
    }

    public void remove(@NotNull String fileName) throws IOException {
        this.initialize();
        if (Files.deleteIfExists(this.resolveSafe(fileName)) || Files.deleteIfExists(this.resolveSafe(fileName + DISABLED_SUFFIX))) {
            return;
        }
        throw new IOException("Mod not found: " + fileName);
    }

    @NotNull
    public List<ManagedMod> list() throws IOException {
        this.initialize();
        List<ManagedMod> mods = new ArrayList<>();
        try (Stream<Path> paths = Files.list(this.modsDirectory)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                String storedName = path.getFileName().toString();
                String lowerName = storedName.toLowerCase(Locale.ROOT);
                if (lowerName.endsWith(".jar")) {
                    mods.add(this.describe(storedName, path, ModState.ENABLED));
                }
                else if (lowerName.endsWith(".jar" + DISABLED_SUFFIX)) {
                    String fileName = storedName.substring(0, storedName.length() - DISABLED_SUFFIX.length());
                    mods.add(this.describe(fileName, path, ModState.DISABLED));
                }
            }
        }
        mods.sort(Comparator.comparing(ManagedMod::name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(ManagedMod::fileName, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(mods);
    }

    public boolean contains(@NotNull String fileName) throws IOException {
        this.initialize();
        return Files.exists(this.resolveSafe(fileName)) || Files.exists(this.resolveSafe(fileName + DISABLED_SUFFIX));
    }

    @NotNull
    public Path modsDirectory() {
        return this.modsDirectory;
    }

    @NotNull
    private ManagedMod describe(@NotNull String fileName, @NotNull Path path, @NotNull ModState state) throws IOException {
        String name = fileName;
        String version = "";
        String description = "";
        BufferedImage icon = null;
        try (ZipFile archive = new ZipFile(path.toFile())) {
            ZipEntry metadataEntry = archive.getEntry("mcmod.info");
            if (metadataEntry != null) {
                try (InputStreamReader reader = new InputStreamReader(archive.getInputStream(metadataEntry), StandardCharsets.UTF_8)) {
                    JsonElement metadata = JsonParser.parseReader(reader);
                    JsonObject modInformation = null;
                    if (metadata.isJsonArray()) {
                        JsonArray mods = metadata.getAsJsonArray();
                        if (!mods.isEmpty() && mods.get(0).isJsonObject()) {
                            modInformation = mods.get(0).getAsJsonObject();
                        }
                    }
                    else if (metadata.isJsonObject()) {
                        JsonObject metadataObject = metadata.getAsJsonObject();
                        JsonElement modList = metadataObject.get("modList");
                        if (modList != null && modList.isJsonArray() && !modList.getAsJsonArray().isEmpty()
                                && modList.getAsJsonArray().get(0).isJsonObject()) {
                            modInformation = modList.getAsJsonArray().get(0).getAsJsonObject();
                        }
                        else {
                            modInformation = metadataObject;
                        }
                    }
                    if (modInformation != null) {
                        JsonElement metadataName = modInformation.get("name");
                        if (metadataName != null && metadataName.isJsonPrimitive() && !metadataName.getAsString().isBlank()) {
                            name = metadataName.getAsString();
                        }
                        JsonElement metadataVersion = modInformation.get("version");
                        if (metadataVersion != null && metadataVersion.isJsonPrimitive()
                                && !metadataVersion.getAsString().isBlank()
                                && !metadataVersion.getAsString().startsWith("${")
                        ) {
                            version = metadataVersion.getAsString();
                        }
                        JsonElement metadataDescription = modInformation.get("description");
                        if (metadataDescription != null && metadataDescription.isJsonPrimitive()) {
                            description = metadataDescription.getAsString();
                        }
                        JsonElement metadataLogo = modInformation.get("logoFile");
                        if (metadataLogo != null && metadataLogo.isJsonPrimitive()
                                && !metadataLogo.getAsString().isBlank()) {
                            String logoPath = metadataLogo.getAsString().replace('\\', '/');
                            ZipEntry logoEntry = archive.getEntry(
                                    logoPath.startsWith("/") ? logoPath.substring(1) : logoPath
                            );
                            if (logoEntry != null) {
                                try (InputStream logoStream = archive.getInputStream(logoEntry)) {
                                    ImageInputStream imageInput = ImageIO.createImageInputStream(logoStream);
                                    if (imageInput != null) {
                                        try (imageInput) {
                                            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);
                                            if (readers.hasNext()) {
                                                ImageReader imageReader = readers.next();
                                                BufferedImage sourceIcon;
                                                int sourceWidth;
                                                int sourceHeight;
                                                try {
                                                    imageReader.setInput(imageInput, true, true);
                                                    sourceWidth = imageReader.getWidth(0);
                                                    sourceHeight = imageReader.getHeight(0);
                                                    ImageReadParam readParameters = imageReader.getDefaultReadParam();
                                                    int sample = Math.max(
                                                            1,
                                                            sourceHeight / MOD_ICON_SIZE
                                                    );
                                                    readParameters.setSourceSubsampling(sample, sample, 0, 0);
                                                    sourceIcon = imageReader.read(0, readParameters);
                                                }
                                                finally {
                                                    imageReader.dispose();
                                                }
                                                if (sourceIcon.getWidth() > 0 && sourceIcon.getHeight() > 0) {
                                                    int iconWidth = Math.max(
                                                            1,
                                                            (int) ((long) sourceWidth * MOD_ICON_SIZE / sourceHeight)
                                                    );
                                                    icon = new BufferedImage(
                                                            iconWidth,
                                                            MOD_ICON_SIZE,
                                                            BufferedImage.TYPE_INT_ARGB
                                                    );
                                                    Graphics2D drawing = icon.createGraphics();
                                                    try {
                                                        drawing.setRenderingHint(
                                                                RenderingHints.KEY_INTERPOLATION,
                                                                RenderingHints.VALUE_INTERPOLATION_BILINEAR
                                                        );
                                                        drawing.drawImage(
                                                                sourceIcon,
                                                                0,
                                                                0,
                                                                iconWidth,
                                                                MOD_ICON_SIZE,
                                                                null
                                                        );
                                                    }
                                                    finally {
                                                        drawing.dispose();
                                                    }
                                                    sourceIcon.flush();
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        catch (IOException | RuntimeException ignored) {
            // A mod without readable metadata remains manageable by its file name.
        }
        return new ManagedMod(
                fileName,
                name,
                version,
                description,
                Files.getLastModifiedTime(path).toMillis(),
                icon,
                path,
                state
        );
    }

    private static void validateModFile(@NotNull Path source) throws IOException {
        if (!Files.isRegularFile(source)) throw new IOException("Mod file does not exist: " + source);
        if (!source.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")) {
            throw new IOException("Mod must be a .jar file: " + source);
        }
        try (ZipFile ignored = new ZipFile(source.toFile())) {
            // Opening the archive is sufficient for structural validation.
        }
        catch (IOException exception) {
            throw new IOException("Invalid mod JAR: " + source, exception);
        }
    }

    @NotNull
    private Path resolveSafe(@Nullable String fileName) throws IOException {
        if (fileName == null || fileName.isBlank()) throw new IOException("Mod filename is empty.");
        Path root = this.modsDirectory.toAbsolutePath().normalize();
        Path result = root.resolve(fileName).normalize();
        if (!result.getParent().equals(root)) throw new IOException("Invalid mod filename: " + fileName);
        return result;
    }
}
