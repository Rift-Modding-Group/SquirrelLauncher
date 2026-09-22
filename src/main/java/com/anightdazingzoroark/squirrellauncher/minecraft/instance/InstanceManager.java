package com.anightdazingzoroark.squirrellauncher.minecraft.instance;

import com.anightdazingzoroark.squirrellauncher.SquirrelLauncher;
import com.anightdazingzoroark.squirrellauncher.minecraft.InstallUtils;
import com.anightdazingzoroark.squirrellauncher.minecraft.MinecraftPaths;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.stream.Stream;

public final class InstanceManager {
    private static final int MMC_FORMAT_VERSION = 1;
    @NotNull
    private static final String MINECRAFT_COMPONENT = "net.minecraft";
    @NotNull
    private static final String FORGE_COMPONENT = "net.minecraftforge";
    @NotNull
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private InstanceManager() {}

    @NotNull
    public static MinecraftInstance create(
            @NotNull String id,
            @NotNull String name,
            @NotNull InstanceType type,
            @Nullable String loaderVersion
    ) throws IOException {
        InstanceManager.validateId(id);
        if (type.hasMods && (loaderVersion == null || loaderVersion.isBlank())) {
            throw new IllegalArgumentException("A loader version is required for " + type + ".");
        }

        Path instanceDirectory = MinecraftPaths.INSTANCES.resolve(id);
        if (Files.exists(instanceDirectory)) throw new IOException("Instance already exists: " + id);

        MinecraftInstance instance = new MinecraftInstance(id, name, type, loaderVersion);
        Files.createDirectories(instance.gameDirectory());
        Files.createDirectories(instance.nativesDirectory());
        InstanceManager.save(instance);
        return instance;
    }

    public static void save(@NotNull MinecraftInstance instance) throws IOException {
        Files.createDirectories(instance.directory());
        Files.writeString(
                instance.configFile(),
                "InstanceType=OneSix\nname=" + InstanceManager.escapeCfgValue(instance.name()) + "\n",
                StandardCharsets.UTF_8
        );

        JsonArray components = new JsonArray();
        JsonObject minecraft = new JsonObject();
        minecraft.addProperty("cachedName", "Minecraft");
        minecraft.addProperty("cachedVersion", SquirrelLauncher.VERSION);
        minecraft.addProperty("important", true);
        minecraft.addProperty("uid", MINECRAFT_COMPONENT);
        minecraft.addProperty("version", SquirrelLauncher.VERSION);
        components.add(minecraft);

        if (instance.type().hasMods) {
            JsonObject loader = new JsonObject();
            loader.addProperty("cachedName", instance.type() == InstanceType.CLEANROOM ? "Cleanroom" : "Forge");
            loader.addProperty("cachedVersion", instance.loaderVersion());
            loader.addProperty("uid", FORGE_COMPONENT);
            loader.addProperty("version", instance.loaderVersion());
            components.add(loader);
        }

        JsonObject pack = new JsonObject();
        pack.add("components", components);
        pack.addProperty("formatVersion", MMC_FORMAT_VERSION);
        Files.writeString(instance.componentFile(), GSON.toJson(pack), StandardCharsets.UTF_8);
    }

    @NotNull
    public static MinecraftInstance load(@NotNull String id) throws IOException {
        InstanceManager.validateId(id);
        Path directory = MinecraftPaths.INSTANCES.resolve(id);
        if (!Files.isRegularFile(directory.resolve("instance.cfg"))
                || !Files.isRegularFile(directory.resolve("mmc-pack.json"))) {
            throw new IOException("MMC instance does not exist: " + id);
        }
        return InstanceManager.loadFromDirectory(id, directory);
    }

    @NotNull
    public static MinecraftInstance loadFromDirectory(@NotNull String id, @NotNull Path directory) throws IOException {
        InstanceManager.validateId(id);
        Path configFile = directory.resolve("instance.cfg");
        Path componentFile = directory.resolve("mmc-pack.json");
        if (!Files.isRegularFile(configFile) || !Files.isRegularFile(componentFile)) {
            throw new IOException("Instance must contain instance.cfg and mmc-pack.json.");
        }

        Properties config = new Properties();
        try (Reader reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
            config.load(reader);
        }
        String name = config.getProperty("name", id).trim();
        if (name.isEmpty()) name = id;

        JsonObject pack = InstallUtils.readJson(componentFile);
        if (!pack.has("formatVersion") || pack.get("formatVersion").getAsInt() != MMC_FORMAT_VERSION) {
            throw new IOException("Unsupported MMC component format in " + componentFile + ".");
        }
        if (!pack.has("components") || !pack.get("components").isJsonArray()) {
            throw new IOException("MMC component file does not contain a components array: " + componentFile);
        }

        String minecraftVersion = null;
        String loaderVersion = null;
        boolean cleanroom = false;
        JsonArray components = pack.getAsJsonArray("components");
        for (JsonElement element : components) {
            if (!element.isJsonObject()) continue;
            JsonObject component = element.getAsJsonObject();
            if (!component.has("uid") || !component.has("version")) continue;

            String uid = component.get("uid").getAsString();
            String version = component.get("version").getAsString();
            if (MINECRAFT_COMPONENT.equals(uid)) minecraftVersion = version;
            if (FORGE_COMPONENT.equals(uid)) {
                loaderVersion = version;
                if (component.has("cachedName")) {
                    cleanroom = component.get("cachedName").getAsString().toLowerCase(Locale.ROOT).contains("cleanroom");
                }
            }
            if (uid.equals("net.neoforged")
                    || uid.equals("net.fabricmc.fabric-loader")
                    || uid.equals("org.quiltmc.quilt-loader")
                    || uid.equals("com.mumfrey.liteloader")) {
                throw new IOException("Unsupported MMC loader component: " + uid);
            }
        }

        if (!SquirrelLauncher.VERSION.equals(minecraftVersion)) {
            throw new IOException(
                    "SquirrelLauncher only supports Minecraft " + SquirrelLauncher.VERSION
                            + "; this instance uses " + (minecraftVersion == null ? "no Minecraft component" : minecraftVersion) + "."
            );
        }

        Path forgePatch = directory.resolve("patches").resolve(FORGE_COMPONENT + ".json");
        if (Files.isRegularFile(forgePatch)) {
            String patchText = Files.readString(forgePatch, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
            cleanroom = cleanroom || patchText.contains("cleanroom") || patchText.contains("com.cleanroommc");
        }

        InstanceType type = loaderVersion == null
                ? InstanceType.VANILLA
                : cleanroom ? InstanceType.CLEANROOM : InstanceType.FORGE;
        if (type == InstanceType.FORGE && loaderVersion.startsWith(SquirrelLauncher.VERSION + "-")) {
            loaderVersion = loaderVersion.substring((SquirrelLauncher.VERSION + "-").length());
        }
        return new MinecraftInstance(id, name, type, loaderVersion);
    }

    public static void setName(@NotNull Path directory, @NotNull String name) throws IOException {
        Path config = directory.resolve("instance.cfg");
        List<String> lines = new ArrayList<>(Files.readAllLines(config, StandardCharsets.UTF_8));
        boolean replaced = false;
        for (int index = 0; index < lines.size(); index++) {
            if (lines.get(index).startsWith("name=")) {
                lines.set(index, "name=" + InstanceManager.escapeCfgValue(name));
                replaced = true;
                break;
            }
        }
        if (!replaced) lines.add("name=" + InstanceManager.escapeCfgValue(name));
        Files.write(config, lines, StandardCharsets.UTF_8);
    }

    @NotNull
    public static List<MinecraftInstance> list() throws IOException {
        Files.createDirectories(MinecraftPaths.INSTANCES);
        List<MinecraftInstance> result = new ArrayList<>();
        try (Stream<Path> directories = Files.list(MinecraftPaths.INSTANCES)) {
            for (Path directory : directories.filter(Files::isDirectory).sorted().toList()) {
                if (directory.getFileName().toString().startsWith(".import-")) continue;
                if (!Files.isRegularFile(directory.resolve("instance.cfg"))
                        || !Files.isRegularFile(directory.resolve("mmc-pack.json"))) continue;
                result.add(InstanceManager.loadFromDirectory(directory.getFileName().toString(), directory));
            }
        }
        result.sort(Comparator.comparing(MinecraftInstance::name, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(result);
    }

    public static boolean exists(@NotNull String id) {
        Path directory = MinecraftPaths.INSTANCES.resolve(id);
        return Files.isRegularFile(directory.resolve("instance.cfg"))
                && Files.isRegularFile(directory.resolve("mmc-pack.json"));
    }

    private static void validateId(@Nullable String id) {
        if (id == null || id.isBlank() || id.equals(".") || id.equals("..")) {
            throw new IllegalArgumentException("Invalid instance ID: " + id);
        }
        try {
            Path path = Path.of(id);
            if (path.isAbsolute() || path.getNameCount() != 1 || !id.equals(path.getFileName().toString())) {
                throw new IllegalArgumentException("Invalid instance ID: " + id);
            }
        }
        catch (InvalidPathException exception) {
            throw new IllegalArgumentException("Invalid instance ID: " + id, exception);
        }
    }

    @NotNull
    private static String escapeCfgValue(@NotNull String value) {
        return value.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r");
    }
}
