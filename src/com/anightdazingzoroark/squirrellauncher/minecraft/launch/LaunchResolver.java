package com.anightdazingzoroark.squirrellauncher.minecraft.launch;

import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public final class LaunchResolver {
    private LaunchResolver() {}

    @NotNull
    public static ResolvedLaunchPlan resolve(@NotNull LaunchDefinition definition) throws IOException {
        LibraryResolver libraries = new LibraryResolver();
        List<JsonObject> nativeMetadata = new ArrayList<>();
        List<String> jvmArguments = new ArrayList<>();
        List<String> extraGameArguments = new ArrayList<>();

        String mainClass = null;
        String rawGameArguments = null;

        String versionType = definition.versionType();

        //components are processed in declaration order
        for (LaunchComponent component : definition.components()) {
            libraries.apply(component);

            JsonObject metadata = component.metadata();
            if (metadata != null) {
                nativeMetadata.add(metadata);

                //latest component containing a mainClass becomes the launch component
                if (metadata.has("mainClass")) {
                    mainClass = metadata.get("mainClass").getAsString();
                }

                //same idea for legacy game arguments
                if (metadata.has("minecraftArguments")) {
                    rawGameArguments = metadata.get("minecraftArguments").getAsString();
                }
            }

            if (component.mainClassOverride() != null) {
                mainClass = component.mainClassOverride();
            }

            if (component.versionTypeOverride() != null) {
                versionType = component.versionTypeOverride();
            }

            jvmArguments.addAll(component.jvmArguments());

            extraGameArguments.addAll(component.gameArguments());
        }

        if (mainClass == null) {
            throw new IllegalStateException("No component supplied a main class.");
        }

        if (rawGameArguments == null) {
            throw new IllegalStateException("No component supplied Minecraft arguments.");
        }

        if (!Files.exists(definition.clientJar())) {
            throw new IOException("Minecraft client JAR missing:\n" + definition.clientJar());
        }

        List<java.nio.file.Path> classpath = new ArrayList<>(libraries.paths());
        classpath.add(definition.clientJar());

        //Convert legacy argument string into individual tokens while placeholders are still intact
        List<String> gameArguments = new ArrayList<>(List.of(rawGameArguments.trim().split("\\s+")));
        gameArguments.addAll(extraGameArguments);

        return new ResolvedLaunchPlan(
                definition.name(), definition.versionName(), versionType, definition.assetIndexName(),
                mainClass,
                List.copyOf(classpath), List.copyOf(nativeMetadata),
                List.copyOf(jvmArguments), List.copyOf(gameArguments)
        );
    }
}