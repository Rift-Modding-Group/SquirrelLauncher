package com.anightdazingzoroark.squirrellauncher.minecraft.mod;

import com.anightdazingzoroark.squirrellauncher.minecraft.instance.InstanceType;
import com.anightdazingzoroark.squirrellauncher.minecraft.instance.MinecraftInstance;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

public final class ModManager {
    @NotNull
    private static final String DISABLED_SUFFIX = ".disabled";
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
        return new ManagedMod(fileName, enabledTarget, ModState.ENABLED);
    }

    @NotNull
    public ManagedMod disable(@NotNull String fileName) throws IOException {
        this.initialize();
        Path source = this.resolveSafe(fileName);
        if (!Files.isRegularFile(source)) throw new IOException("Enabled mod not found: " + fileName);

        Path destination = this.resolveSafe(fileName + DISABLED_SUFFIX);
        if (Files.exists(destination)) throw new IOException("Disabled mod already exists: " + fileName);

        Files.move(source, destination);
        System.out.println("Disabled mod: " + fileName);
        return new ManagedMod(fileName, destination, ModState.DISABLED);
    }

    @NotNull
    public ManagedMod enable(@NotNull String fileName) throws IOException {
        this.initialize();
        Path source = this.resolveSafe(fileName + DISABLED_SUFFIX);
        if (!Files.isRegularFile(source)) throw new IOException("Disabled mod not found: " + fileName);

        Path destination = this.resolveSafe(fileName);
        if (Files.exists(destination)) throw new IOException("Enabled mod already exists: " + fileName);

        Files.move(source, destination);
        System.out.println("Enabled mod: " + fileName);
        return new ManagedMod(fileName, destination, ModState.ENABLED);
    }

    public void remove(@NotNull String fileName) throws IOException {
        this.initialize();
        if (Files.deleteIfExists(this.resolveSafe(fileName))
                || Files.deleteIfExists(this.resolveSafe(fileName + DISABLED_SUFFIX))) {
            System.out.println("Removed mod: " + fileName);
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
                    mods.add(new ManagedMod(storedName, path, ModState.ENABLED));
                }
                else if (lowerName.endsWith(".jar" + DISABLED_SUFFIX)) {
                    String fileName = storedName.substring(0, storedName.length() - DISABLED_SUFFIX.length());
                    mods.add(new ManagedMod(fileName, path, ModState.DISABLED));
                }
            }
        }
        mods.sort(Comparator.comparing(ManagedMod::fileName, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(mods);
    }

    public boolean contains(@NotNull String fileName) throws IOException {
        this.initialize();
        return Files.exists(this.resolveSafe(fileName))
                || Files.exists(this.resolveSafe(fileName + DISABLED_SUFFIX));
    }

    @NotNull
    public Path modsDirectory() {
        return this.modsDirectory;
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
