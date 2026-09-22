package com.anightdazingzoroark.squirrellauncher.minecraft.launch;

import com.anightdazingzoroark.squirrellauncher.SquirrelLauncher;
import com.anightdazingzoroark.squirrellauncher.minecraft.MinecraftPaths;
import com.anightdazingzoroark.squirrellauncher.minecraft.auth.MinecraftAccount;
import com.anightdazingzoroark.squirrellauncher.minecraft.instance.MinecraftInstance;
import com.anightdazingzoroark.squirrellauncher.minecraft.integrity.LibraryIntegrity;
import com.anightdazingzoroark.squirrellauncher.minecraft.runtime.JavaRuntime;
import com.anightdazingzoroark.squirrellauncher.minecraft.runtime.JavaRuntimeManager;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public final class MinecraftLauncher {
    private MinecraftLauncher() {}

    @NotNull
    public static Process launch(@NotNull LaunchDefinition definition, @NotNull MinecraftAccount account, @NotNull MinecraftInstance instance) throws Exception {
        //ensure integrety
        for (LaunchComponent component : definition.components()) {
            LibraryIntegrity.repair(component);
        }

        JavaRuntime runtime = JavaRuntimeManager.resolve(definition.javaVersion());
        ResolvedLaunchPlan plan = LaunchResolver.resolve(definition);
        Path gameDir = instance.gameDirectory();
        Path nativesDir = instance.nativesDirectory();
        Files.createDirectories(gameDir);
        NativeManager.rebuild(definition.components(), nativesDir);

        String classpath = String.join(
                File.pathSeparator,
                plan.classpath().stream()
                        .map(path -> path.toAbsolutePath().toString())
                        .toList()
        );

        String assetIndex = plan.assetIndexName();

        Map<String, String> replacements = Map.ofEntries(
                Map.entry("${auth_player_name}", account.username()),
                Map.entry("${version_name}", plan.versionName()),
                Map.entry("${game_directory}", gameDir.toAbsolutePath().toString()),
                Map.entry("${assets_root}", MinecraftPaths.ASSETS.toAbsolutePath().toString()),
                Map.entry("${assets_index_name}", assetIndex),
                Map.entry("${auth_uuid}", account.uuid()),
                Map.entry("${auth_access_token}", account.accessToken()),
                Map.entry("${user_type}", account.userType()),
                Map.entry("${version_type}", plan.versionType()),
                Map.entry("${user_properties}", "{}")
        );

        List<String> command = new ArrayList<>();
        command.add(runtime.executable().toString());
        command.add("-Xms512M");
        command.add("-Xmx2G");
        command.add("-Djava.library.path=" + nativesDir.toAbsolutePath());
        command.add("-Dminecraft.launcher.brand=" + SquirrelLauncher.NAME);
        command.add("-Dminecraft.launcher.version=0.1");
        //component-provided JVM args
        command.addAll(plan.jvmArguments());
        command.add("-cp");
        command.add(classpath);
        command.add(plan.mainClass());

        //resolve placeholders only now
        for (String raw : plan.gameArguments()) {
            String argument = raw;
            for (Map.Entry<String, String> replacement : replacements.entrySet()) {
                argument = argument.replace(replacement.getKey(), replacement.getValue());
            }
            command.add(argument);
        }

        //size
        command.add("--width");
        command.add("1280");

        command.add("--height");
        command.add("720");

        //final launch
        System.out.println();
        System.out.println("=== Launching " + plan.name() + " ===");
        System.out.println("Instance: " + instance.name());
        System.out.println("Java: " + runtime.detectedVersion() + " [" + runtime.executable() + "]");
        System.out.println("Main class: " + plan.mainClass());
        System.out.println("Classpath entries: " + plan.classpath().size());

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(gameDir.toFile());
        builder.redirectErrorStream(true);

        Process process = builder.start();
        Thread outputThread = new Thread(() -> forwardOutput(process, instance.name()), "minecraft-output");
        outputThread.setDaemon(true);
        outputThread.start();

        return process;
    }

    private static void forwardOutput(@NotNull Process process, @NotNull String source) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("[" + source + "] " + line);
            }
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }
}