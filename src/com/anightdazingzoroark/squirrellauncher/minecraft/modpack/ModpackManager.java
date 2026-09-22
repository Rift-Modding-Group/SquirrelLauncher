package com.anightdazingzoroark.squirrellauncher.minecraft.modpack;

import com.anightdazingzoroark.squirrellauncher.minecraft.instance.InstanceManager;
import com.anightdazingzoroark.squirrellauncher.minecraft.instance.MinecraftInstance;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public final class ModpackManager {
    private static final int FORMAT_VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ModpackManager() {}

    public static void exportPack(@NotNull MinecraftInstance instance, @NotNull Path destination) throws IOException {
        if (!destination.getFileName().toString().endsWith(".squirrelpack")) {
            destination = destination.resolveSibling(destination.getFileName() + ".squirrelpack");
        }

        Path parent = destination.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);

        ModpackManifest manifest = new ModpackManifest(
                FORMAT_VERSION,
                instance.name(),
                instance.type(),
                instance.loaderVersion()
        );

        try (OutputStream output = Files.newOutputStream(destination); ZipOutputStream zip = new ZipOutputStream(output)) {
            writeManifest(manifest, zip);

            //enabled mods
            addDirectory(instance.modsDirectory(), "mods/", zip);

            //disabled mods
            addDirectory(instance.disabledModsDirectory(), "disabled-mods/", zip);

            //instance configuration
            Path config = instance.gameDirectory().resolve("config");
            addDirectory(config, "overrides/config/", zip);

            //for crafttweaker scripts
            //todo: make it so that users can include any folder, not just crafttweaker scripts
            Path scripts = instance.gameDirectory().resolve("scripts");
            addDirectory(scripts, "overrides/scripts/", zip);

            //optional user settings
            Path options = instance.gameDirectory().resolve("options.txt");
            if (Files.isRegularFile(options)) {
                addFile(options, "overrides/options.txt", zip);
            }
        }

        System.out.println("Exported modpack: " + destination);
    }

    private static void writeManifest(@NotNull ModpackManifest manifest, @NotNull ZipOutputStream zip) throws IOException {
        ZipEntry entry = new ZipEntry("manifest.json");
        zip.putNextEntry(entry);
        byte[] data = GSON.toJson(manifest).getBytes(StandardCharsets.UTF_8);
        zip.write(data);
        zip.closeEntry();
    }

    private static void addDirectory(@NotNull Path directory, @NotNull String prefix, @NotNull ZipOutputStream zip) throws IOException {
        if (!Files.isDirectory(directory)) return;

        List<Path> files;
        try (java.util.stream.Stream<Path> stream = Files.walk(directory)) {
            files = stream.filter(Files::isRegularFile).toList();
        }

        for (Path file : files) {
            Path relative = directory.relativize(file);
            String entryName = prefix + relative.toString().replace('\\', '/');
            addFile(file, entryName, zip);
        }
    }

    private static void addFile(@NotNull Path file, @NotNull String entryName, @NotNull ZipOutputStream zip) throws IOException {
        ZipEntry entry = new ZipEntry(entryName);
        zip.putNextEntry(entry);
        Files.copy(file, zip);
        zip.closeEntry();
    }

    public static MinecraftInstance importPack(@NotNull Path packFile, @NotNull String instanceId) throws IOException {
        if (!Files.isRegularFile(packFile)) {
            throw new IOException("Modpack does not exist: " + packFile);
        }

        ModpackManifest manifest = readManifest(packFile);
        if (manifest.formatVersion() != FORMAT_VERSION) {
            throw new IOException("Unsupported Squirrel pack format: " + manifest.formatVersion());
        }

        if (InstanceManager.exists(instanceId)) {
            throw new IOException("Instance already exists: " + instanceId);
        }

        MinecraftInstance instance = InstanceManager.create(instanceId, manifest.name(), manifest.type(), manifest.loaderVersion());
        extractPack(packFile, instance);
        System.out.println("Imported modpack as instance: " + instance.name());
        return instance;
    }

    private static ModpackManifest readManifest(@NotNull Path packFile) throws IOException {
        try (ZipFile zip = new ZipFile(packFile.toFile())) {
            ZipEntry entry = zip.getEntry("manifest.json");
            if (entry == null) {
                throw new IOException("Not a valid Squirrel modpack: manifest.json is missing.");
            }

            try (InputStream input = zip.getInputStream(entry)) {
                String json = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                return GSON.fromJson(json, ModpackManifest.class);
            }
        }
    }

    private static void extractPack(@NotNull Path packFile, @NotNull MinecraftInstance instance) throws IOException {
        try (ZipFile zip = new ZipFile(packFile.toFile())) {
            java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) continue;

                String name = entry.getName().replace('\\', '/');
                if (name.equals("manifest.json")) continue;

                Path destination = destinationFor(instance, name);
                if (destination == null) continue;

                Files.createDirectories(destination.getParent());

                try (InputStream input = zip.getInputStream(entry)) {
                    Files.copy(input, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static Path destinationFor(@NotNull MinecraftInstance instance, @NotNull String entryName) throws IOException {
        Path destination;
        if (entryName.startsWith("mods/")) {
            destination = instance.modsDirectory().resolve(entryName.substring("mods/".length()));
        }
        else if (entryName.startsWith("disabled-mods/")) {
            destination = instance.disabledModsDirectory().resolve(entryName.substring("disabled-mods/".length()));
        }
        else if (entryName.startsWith("overrides/")) {
            destination = instance.gameDirectory().resolve(entryName.substring("overrides/".length()));
        }
        else {
            //unknown
            return null;
        }

        Path root = instance.directory().toAbsolutePath().normalize();
        destination = destination.toAbsolutePath().normalize();
        //ZIP traversal protection
        if (!destination.startsWith(root)) {
            throw new IOException("Illegal modpack entry: " + entryName);
        }

        return destination;
    }
}