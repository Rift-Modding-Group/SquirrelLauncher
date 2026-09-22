package com.anightdazingzoroark.squirrellauncher.minecraft.modpack;

import com.anightdazingzoroark.squirrellauncher.minecraft.MinecraftPaths;
import com.anightdazingzoroark.squirrellauncher.minecraft.instance.InstanceManager;
import com.anightdazingzoroark.squirrellauncher.minecraft.instance.MinecraftInstance;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public final class MMCPackManager {
    private MMCPackManager() {}

    @NotNull
    public static MinecraftInstance importPack(
            @NotNull Path packFile,
            @NotNull String instanceId,
            @NotNull String instanceName
    ) throws IOException {
        if (!Files.isRegularFile(packFile)) throw new IOException("MMC instance archive does not exist: " + packFile);
        if (Files.exists(MinecraftPaths.INSTANCES.resolve(instanceId))) {
            throw new IOException("Instance already exists: " + instanceId);
        }

        Files.createDirectories(MinecraftPaths.INSTANCES);
        Path staging = Files.createTempDirectory(MinecraftPaths.INSTANCES, ".import-");
        boolean moved = false;
        try {
            try (ZipFile zip = new ZipFile(packFile.toFile())) {
                Map<String, ZipEntry> entries = new HashMap<>();
                Enumeration<? extends ZipEntry> archivedEntries = zip.entries();
                while (archivedEntries.hasMoreElements()) {
                    ZipEntry entry = archivedEntries.nextElement();
                    String entryName = entry.getName().replace('\\', '/');
                    while (entryName.startsWith("./")) entryName = entryName.substring(2);
                    if (entries.put(entryName, entry) != null) {
                        throw new IOException("Archive contains duplicate entry: " + entryName);
                    }
                }

                List<String> roots = new ArrayList<>();
                for (String entryName : entries.keySet()) {
                    if (!entryName.endsWith("instance.cfg")) continue;
                    int separator = entryName.length() - "instance.cfg".length();
                    if (separator > 0 && entryName.charAt(separator - 1) != '/') continue;
                    String root = entryName.substring(0, separator);
                    if (entries.containsKey(root + "mmc-pack.json")) roots.add(root);
                }
                if (roots.isEmpty()) {
                    throw new IOException("Archive is not an MMC instance: instance.cfg and mmc-pack.json were not found together.");
                }
                if (roots.size() > 1) throw new IOException("Archive contains more than one MMC instance.");

                String archiveRoot = roots.getFirst();
                for (Map.Entry<String, ZipEntry> archived : entries.entrySet()) {
                    String entryName = archived.getKey();
                    if (!entryName.startsWith(archiveRoot)) continue;
                    String relativeName = entryName.substring(archiveRoot.length());
                    if (relativeName.isEmpty()) continue;

                    Path destination = staging.resolve(relativeName).normalize();
                    if (!destination.startsWith(staging)) throw new IOException("Illegal ZIP entry: " + entryName);
                    if (archived.getValue().isDirectory()) {
                        Files.createDirectories(destination);
                        continue;
                    }

                    Files.createDirectories(destination.getParent());
                    try (InputStream input = zip.getInputStream(archived.getValue())) {
                        Files.copy(input, destination, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }

            InstanceManager.setName(staging, instanceName);
            InstanceManager.loadFromDirectory(instanceId, staging);
            Path destination = MinecraftPaths.INSTANCES.resolve(instanceId);
            try {
                Files.move(staging, destination, StandardCopyOption.ATOMIC_MOVE);
            }
            catch (AtomicMoveNotSupportedException exception) {
                Files.move(staging, destination);
            }
            moved = true;
            MinecraftInstance instance = InstanceManager.load(instanceId);
            System.out.println("Imported MMC instance: " + instance.name());
            return instance;
        }
        finally {
            if (!moved && Files.exists(staging)) {
                try (java.util.stream.Stream<Path> paths = Files.walk(staging)) {
                    for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
                }
            }
        }
    }

    public static void exportPack(@NotNull MinecraftInstance instance, @NotNull Path destination) throws IOException {
        if (!destination.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".zip")) {
            destination = destination.resolveSibling(destination.getFileName() + ".zip");
        }
        InstanceManager.load(instance.id());

        Path instanceRoot = instance.directory().toAbsolutePath().normalize();
        Path outputFile = destination.toAbsolutePath().normalize();
        if (outputFile.startsWith(instanceRoot)) {
            throw new IOException("Choose an export location outside the instance directory.");
        }
        Path parent = outputFile.getParent();
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, ".mmc-export-", ".zip");
        boolean moved = false;
        try {
            try (OutputStream output = Files.newOutputStream(temporary); ZipOutputStream zip = new ZipOutputStream(output);
                 java.util.stream.Stream<Path> paths = Files.walk(instanceRoot)) {
                for (Path file : paths.filter(Files::isRegularFile).sorted().toList()) {
                    Path relative = instanceRoot.relativize(file);
                    if (relative.startsWith("natives")
                            || relative.startsWith("minecraft/logs")
                            || relative.startsWith(".minecraft/logs")
                            || relative.startsWith("minecraft/crash-reports")
                            || relative.startsWith(".minecraft/crash-reports")) continue;

                    String entryName = relative.toString().replace('\\', '/');
                    zip.putNextEntry(new ZipEntry(entryName));
                    Files.copy(file, zip);
                    zip.closeEntry();
                }
            }
            try {
                Files.move(temporary, outputFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            }
            catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, outputFile, StandardCopyOption.REPLACE_EXISTING);
            }
            moved = true;
        }
        finally {
            if (!moved) Files.deleteIfExists(temporary);
        }
        System.out.println("Exported MMC instance: " + outputFile);
    }
}
