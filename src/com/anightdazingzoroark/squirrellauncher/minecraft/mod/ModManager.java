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
    private final MinecraftInstance instance;
    @NotNull
    private final Path enabledDirectory;
    @NotNull
    private final Path disabledDirectory;

    public ModManager(@NotNull MinecraftInstance instance) {
        this.instance = instance;
        this.enabledDirectory = instance.modsDirectory();
        this.disabledDirectory = instance.disabledModsDirectory();
    }

    public void initialize() throws IOException {
        if (this.instance.type() == InstanceType.VANILLA) {
            throw new IllegalStateException("Vanilla instances do not support mods.");
        }
        Files.createDirectories(this.enabledDirectory);
        Files.createDirectories(this.disabledDirectory);
    }

    public ManagedMod install(@NotNull Path source) throws IOException {
        this.initialize();
        validateModFile(source);

        String fileName = source.getFileName().toString();
        Path enabledTarget = this.enabledDirectory.resolve(fileName);
        Path disabledTarget = this.disabledDirectory.resolve(fileName);
        if (Files.exists(enabledTarget) || Files.exists(disabledTarget)) {
            throw new IOException("Mod is already managed by this instance: " + fileName);
        }

        Files.copy(source, enabledTarget);
        System.out.println("Installed mod: "+ fileName);

        return new ManagedMod(fileName, enabledTarget, ModState.ENABLED);
    }

    @NotNull
    public ManagedMod disable(@NotNull String fileName) throws IOException {
        this.initialize();

        Path source = resolveSafe(this.enabledDirectory, fileName);
        if (!Files.isRegularFile(source)) {
            throw new IOException("Enabled mod not found: " + fileName);
        }

        Path destination = resolveSafe(this.disabledDirectory, fileName);
        if (Files.exists(destination)) {
            throw new IOException("Disabled mod already exists: " + fileName);
        }

        Files.move(source, destination);
        System.out.println("Disabled mod: " + fileName);

        return new ManagedMod(fileName, destination, ModState.DISABLED);
    }

    @NotNull
    public ManagedMod enable(@NotNull String fileName) throws IOException {
        this.initialize();

        Path source = resolveSafe(this.disabledDirectory, fileName);
        if (!Files.isRegularFile(source)) {
            throw new IOException("Disabled mod not found: " + fileName);
        }

        Path destination = resolveSafe(this.enabledDirectory, fileName);
        if (Files.exists(destination)) {
            throw new IOException("Enabled mod already exists: " + fileName);
        }

        Files.move(source, destination);
        System.out.println("Enabled mod: " + fileName);

        return new ManagedMod(fileName, destination, ModState.ENABLED);
    }

    public void remove(@NotNull String fileName) throws IOException {
        this.initialize();

        Path enabled = resolveSafe(this.enabledDirectory, fileName);
        Path disabled = resolveSafe(this.disabledDirectory, fileName);
        if (Files.deleteIfExists(enabled)) {
            System.out.println("Removed mod: " + fileName);
            return;
        }

        if (Files.deleteIfExists(disabled)) {
            System.out.println("Removed mod: " + fileName);
            return;
        }

        throw new IOException("Mod not found: " + fileName);
    }

    @NotNull
    public List<ManagedMod> list() throws IOException {
        this.initialize();

        List<ManagedMod> mods = new ArrayList<>();
        this.readDirectory(this.enabledDirectory, ModState.ENABLED, mods);
        this.readDirectory(this.disabledDirectory, ModState.DISABLED, mods);
        mods.sort(Comparator.comparing(ManagedMod::fileName, String.CASE_INSENSITIVE_ORDER));

        return List.copyOf(mods);
    }

    public boolean contains(@NotNull String fileName) throws IOException {
        this.initialize();
        return Files.exists(resolveSafe(this.enabledDirectory, fileName))
                || Files.exists(resolveSafe(this.disabledDirectory, fileName));
    }

    @NotNull
    public Path enabledDirectory() {
        return this.enabledDirectory;
    }

    @NotNull
    public Path disabledDirectory() {
        return this.disabledDirectory;
    }

    private void readDirectory(Path directory, ModState state, List<ManagedMod> result) throws IOException {
        if (!Files.exists(directory)) return;

        try (Stream<Path> paths = Files.list(directory)) {
            paths.filter(Files::isRegularFile)
                    .filter(ModManager::isJar)
                    .forEach(path ->result.add(new ManagedMod(path.getFileName().toString(), path, state)));
        }
    }

    private static void validateModFile(@NotNull Path source) throws IOException {
        if (!Files.isRegularFile(source)) throw new IOException("Mod file does not exist: " + source);
        if (!isJar(source)) throw new IOException("Mod must be a .jar file: " + source);

        //make sure this is at least a structurally valid JAR/ZIP.
        //does not require mcmod.info.
        try (ZipFile ignored = new ZipFile(source.toFile())) {
            // Opening the archive is enough for this check.
        }
        catch (IOException e) {
            throw new IOException("Invalid mod JAR: " + source, e);
        }
    }

    private static boolean isJar(@NotNull Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".jar");
    }

    @NotNull
    private static Path resolveSafe(@NotNull Path directory, @Nullable String fileName) throws IOException {
        if (fileName == null || fileName.isBlank()) throw new IOException("Mod filename is empty.");

        Path root = directory.toAbsolutePath().normalize();
        Path result = root.resolve(fileName).normalize();

        //prevent names such as "../../something.jar"
        if (!result.getParent().equals(root)) {
            throw new IOException("Invalid mod filename: " + fileName);
        }

        return result;
    }
}