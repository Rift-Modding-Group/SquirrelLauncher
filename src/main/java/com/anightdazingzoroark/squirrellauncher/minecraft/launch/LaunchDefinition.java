package com.anightdazingzoroark.squirrellauncher.minecraft.launch;

import com.anightdazingzoroark.squirrellauncher.SquirrelLauncher;
import com.anightdazingzoroark.squirrellauncher.minecraft.InstallUtils;
import com.anightdazingzoroark.squirrellauncher.minecraft.MinecraftPaths;
import com.anightdazingzoroark.squirrellauncher.minecraft.install.MmcPatchAdapter;
import com.anightdazingzoroark.squirrellauncher.minecraft.install.ForgeConstants;
import com.anightdazingzoroark.squirrellauncher.minecraft.instance.MinecraftInstance;
import com.anightdazingzoroark.squirrellauncher.minecraft.runtime.JavaVersion;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public record LaunchDefinition(
        @NotNull String name,
        @NotNull String versionName,
        @NotNull String versionType,
        @NotNull Path clientJar,
        @NotNull JavaVersion javaVersion,
        @NotNull String assetIndexName,
        @NotNull List<LaunchComponent> components
) {
    public LaunchDefinition {
        components = List.copyOf(components);
    }

    /**
     * Create a Vanilla 1.12.2 launch definition.
     */
    @NotNull
    public static LaunchDefinition vanilla() throws IOException {
        Path versionDir = MinecraftPaths.VERSIONS.resolve(SquirrelLauncher.VERSION);
        JsonObject vanilla = InstallUtils.readJson(versionDir.resolve(SquirrelLauncher.VERSION + ".json"));
        LaunchComponent minecraft = LaunchComponent.metadata("minecraft", vanilla, false);

        return new LaunchDefinition(
                "Vanilla",
                SquirrelLauncher.VERSION,
                SquirrelLauncher.NAME,
                versionDir.resolve(SquirrelLauncher.VERSION + ".jar"),
                JavaVersion.JAVA_8,
                assetIndexName(vanilla),
                List.of(minecraft)
        );
    }

    /**
     * Create a Forge 1.12.2 launch definition.
     */
    @NotNull
    public static LaunchDefinition forge(@NotNull MinecraftInstance instance) throws IOException {
        String forgeVersion = instance.loaderVersion();
        if (forgeVersion == null || forgeVersion.isBlank()) {
            throw new IllegalStateException("Forge instance " + instance.id() + " has no loaderVersion.");
        }

        Path vanillaDir = MinecraftPaths.VERSIONS.resolve(SquirrelLauncher.VERSION);
        JsonObject vanilla = InstallUtils.readJson(vanillaDir.resolve(SquirrelLauncher.VERSION + ".json"));

        String forgeId = ForgeConstants.versionId(forgeVersion);

        JsonObject forge = InstallUtils.readJson(MinecraftPaths.VERSIONS.resolve(forgeId).resolve(forgeId + ".json"));

        LaunchComponent minecraftComponent = LaunchComponent.metadata("minecraft", vanilla, false);
        LaunchComponent forgeComponent = LaunchComponent.metadata("forge", forge, true);

        return new LaunchDefinition(
                "Forge "+ forgeVersion,
                forgeId,
                "Forge",
                vanillaDir.resolve(SquirrelLauncher.VERSION + ".jar"),
                JavaVersion.JAVA_8,
                assetIndexName(vanilla),
                List.of(minecraftComponent, forgeComponent)
        );
    }

    /**
     * Create a Cleanroom 1.12.2 launch definition.
     */
    @NotNull
    public static LaunchDefinition cleanroom(@NotNull MinecraftInstance instance, @NotNull Path cleanroomRoot) throws IOException {
        String cleanroomVersion = instance.loaderVersion();
        if (cleanroomVersion == null || cleanroomVersion.isBlank()) {
            throw new IllegalStateException("Cleanroom instance " + instance.id() + " has no loaderVersion.");
        }

        //Minecraft 1.12.2 base directory.
        Path vanillaDir = MinecraftPaths.VERSIONS.resolve(SquirrelLauncher.VERSION);

        /*
         * We still need Mojang's vanilla JSON for
         * Minecraft-owned information such as the asset index.
         *
         * IMPORTANT:
         *
         * We do NOT turn this into a LaunchComponent for
         * Cleanroom.
         *
         * Doing that would reintroduce vanilla's old LWJGL 2
         * libraries into the Cleanroom classpath.
         */
        JsonObject vanilla = InstallUtils.readJson(vanillaDir.resolve(SquirrelLauncher.VERSION + ".json"));
        Path clientJar = vanillaDir.resolve(SquirrelLauncher.VERSION + ".jar");
        List<LaunchComponent> components = new ArrayList<>();

        //Read Cleanroom's MMC component declaration
        JsonObject pack = InstallUtils.readJson(cleanroomRoot.resolve("mmc-pack.json"));
        if (!pack.has("components")) {
            throw new IOException("Cleanroom mmc-pack.json does not contain components.");
        }

        //Convert each Cleanroom/MMC patch into our declarative LaunchComponent format
        for (JsonElement element : pack.getAsJsonArray("components")) {
            JsonObject entry = element.getAsJsonObject();
            if (!entry.has("uid")) continue;

            String uid = entry.get("uid").getAsString();
            Path patchFile = cleanroomRoot.resolve("patches").resolve(uid + ".json");
            if (!Files.exists(patchFile)) continue;

            JsonObject patch = InstallUtils.readJson(patchFile);
            LaunchComponent component = MmcPatchAdapter.convert(uid, patch);
            components.add(component);
        }

        if (components.isEmpty()) {
            throw new IOException("Cleanroom did not provide any usable launch components.");
        }

        return new LaunchDefinition(
                "Cleanroom " + cleanroomVersion,
                cleanroomVersion,
                "Cleanroom",
                clientJar,
                JavaVersion.JAVA_25,
                assetIndexName(vanilla),
                components
        );
    }

    /**
     * Read Minecraft's asset index from its vanilla version metadata.
     */
    @NotNull
    private static String assetIndexName(@NotNull JsonObject vanilla) {
        if (vanilla.has("assetIndex")) {
            JsonObject assetIndex = vanilla.getAsJsonObject("assetIndex");
            if (assetIndex.has("id")) {
                return assetIndex.get("id").getAsString();
            }
        }

        /*
         * Older Minecraft metadata can use "assets".
         */
        if (vanilla.has("assets")) {
            return vanilla.get("assets").getAsString();
        }

        throw new IllegalStateException("Minecraft metadata has no asset index.");
    }
}
