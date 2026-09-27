package com.anightdazingzoroark.squirrellauncher.minecraft.runtime;

import com.anightdazingzoroark.squirrellauncher.minecraft.MinecraftPaths;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class JavaRuntimeManager {
    private static final Pattern VERSION_PATTERN = Pattern.compile("(?:java|openjdk) version \"([^\"]+)\"");

    private JavaRuntimeManager() {}

    @NotNull
    public static JavaRuntime resolve(@NotNull JavaVersion requiredVersion) throws IOException {
        List<Path> candidates = findCandidates(requiredVersion);
        List<String> rejected = new ArrayList<>();
        for (Path candidate : candidates) {
            Path executable = normalizeExecutable(candidate);
            if (!Files.isRegularFile(executable) || !Files.isExecutable(executable)) continue;

            JavaVersionResult result;
            try {
                result = inspect(executable);
            }
            catch (IOException e) {
                rejected.add(executable + " -> " + e.getMessage());
                continue;
            }

            if (result.major() == requiredVersion.major()) {
                return new JavaRuntime(requiredVersion, executable.toAbsolutePath().normalize(), result.version());
            }

            rejected.add(executable + " -> Java " + result.version());
        }

        StringBuilder message = new StringBuilder();
        message.append("Could not find Java ");
        message.append(requiredVersion.major());
        message.append(".\n\n");
        message.append("Set ");
        message.append(requiredVersion.environmentVariable());
        message.append(" to either the Java executable " + "or Java home directory.\n");

        if (!rejected.isEmpty()) {
            message.append("\nJava installations checked:\n");
            for (String rejectedRuntime : rejected) {
                message.append("  ");
                message.append(rejectedRuntime);
                message.append('\n');
            }
        }

        throw new IOException(message.toString());
    }

    @NotNull
    public static JavaRuntime resolve(
            @NotNull JavaVersion requiredVersion,
            @Nullable Path configuredRuntime
    ) throws IOException {
        if (configuredRuntime == null) return JavaRuntimeManager.resolve(requiredVersion);

        Path executable = JavaRuntimeManager.normalizeExecutable(configuredRuntime);
        if (!Files.isRegularFile(executable) || !Files.isExecutable(executable)) {
            throw new IOException("The configured Java executable is not usable: " + executable);
        }
        JavaVersionResult result = JavaRuntimeManager.inspect(executable);
        if (result.major() != requiredVersion.major()) {
            throw new IOException(
                    "This instance requires Java " + requiredVersion.major()
                            + ", but the configured runtime is Java " + result.version() + "."
            );
        }
        return new JavaRuntime(
                requiredVersion,
                executable.toAbsolutePath().normalize(),
                result.version()
        );
    }

    @NotNull
    public static List<JavaRuntime> detect(@NotNull JavaVersion requiredVersion) {
        Set<Path> detectedExecutables = new LinkedHashSet<>();
        List<JavaRuntime> runtimes = new ArrayList<>();
        for (Path candidate : JavaRuntimeManager.findCandidates(requiredVersion)) {
            Path executable = JavaRuntimeManager.normalizeExecutable(candidate);
            if (!Files.isRegularFile(executable) || !Files.isExecutable(executable)) continue;
            try {
                Path detectedExecutable = executable.toRealPath();
                if (!detectedExecutables.add(detectedExecutable)) continue;
                JavaVersionResult result = JavaRuntimeManager.inspect(detectedExecutable);
                if (result.major() != requiredVersion.major()) continue;
                runtimes.add(new JavaRuntime(requiredVersion, detectedExecutable, result.version()));
            }
            catch (IOException | RuntimeException ignored) {}
        }
        return List.copyOf(runtimes);
    }

    @NotNull
    private static List<Path> findCandidates(@NotNull JavaVersion version) {
        Set<Path> candidates = new LinkedHashSet<>();

        //---explicit squirrellauncher override---
        String configured = System.getenv(version.environmentVariable());
        if (configured != null && !configured.isBlank()) {
            candidates.add(Paths.get(configured));
        }

        //---launcher-managed runtime---
        candidates.add(MinecraftPaths.RUNTIMES.resolve(version.directoryName()));

        //---get JAVA_HOME---
        String javaHome = System.getenv("JAVA_HOME");
        if (javaHome != null && !javaHome.isBlank()) {
            candidates.add(Paths.get(javaHome));
        }

        //---JVM currently running SquirrelLauncher---
        String currentJavaHome = System.getProperty("java.home");
        if (currentJavaHome != null && !currentJavaHome.isBlank()) {
            candidates.add(Paths.get(currentJavaHome));
        }

        //---Linux system locations---
        if (version == JavaVersion.JAVA_8) {
            candidates.add(Paths.get("/usr/lib/jvm/java-8-openjdk-amd64"));
            candidates.add(Paths.get("/usr/lib/jvm/java-8-openjdk-amd64/jre"));
            candidates.add(Paths.get("/usr/lib/jvm/java-8-openjdk"));
        }
        if (version == JavaVersion.JAVA_25) {
            candidates.add(Paths.get("/usr/lib/jvm/java-25-openjdk-amd64"));
            candidates.add(Paths.get("/usr/lib/jvm/java-25-openjdk"));
        }

        // /usr/bin/java may point to either runtime, inspect() determines whether it is usable
        candidates.add(Paths.get("/usr/bin/java"));

        JavaRuntimeManager.addJavaHomes(candidates, Paths.get("/usr/lib/jvm"), 1);
        JavaRuntimeManager.addJavaHomes(candidates, Paths.get("/Library/Java/JavaVirtualMachines"), 3);
        String userHome = System.getProperty("user.home");
        if (userHome != null && !userHome.isBlank()) {
            JavaRuntimeManager.addJavaHomes(candidates, Paths.get(userHome, ".sdkman", "candidates", "java"), 2);
            JavaRuntimeManager.addJavaHomes(candidates, Paths.get(userHome, ".asdf", "installs", "java"), 2);
            JavaRuntimeManager.addJavaHomes(candidates, Paths.get(userHome, ".jdks"), 2);
        }
        String programFiles = System.getenv("ProgramFiles");
        if (programFiles != null && !programFiles.isBlank()) {
            for (String vendorDirectory : List.of(
                    "Java", "Eclipse Adoptium", "Microsoft", "Amazon Corretto", "BellSoft", "Zulu", "Semeru"
            )) {
                JavaRuntimeManager.addJavaHomes(candidates, Paths.get(programFiles, vendorDirectory), 2);
            }
        }
        String programFilesX86 = System.getenv("ProgramFiles(x86)");
        if (programFilesX86 != null && !programFilesX86.isBlank()) {
            for (String vendorDirectory : List.of(
                    "Java", "Eclipse Adoptium", "Microsoft", "Amazon Corretto", "BellSoft", "Zulu", "Semeru"
            )) {
                JavaRuntimeManager.addJavaHomes(candidates, Paths.get(programFilesX86, vendorDirectory), 2);
            }
        }

        return new ArrayList<>(candidates);
    }

    private static void addJavaHomes(@NotNull Set<Path> candidates, @NotNull Path root, int maximumDepth) {
        if (!Files.isDirectory(root)) return;
        try (Stream<Path> paths = Files.walk(root, maximumDepth)) {
            paths.filter(Files::isDirectory)
                    .filter(path -> Files.isRegularFile(
                            path.resolve("bin").resolve(JavaRuntimeManager.executableName())
                    ))
                    .forEach(candidates::add);
        }
        catch (IOException | SecurityException ignored) {}
    }

    @NotNull
    private static Path normalizeExecutable(@NotNull Path path) {
        if (Files.isDirectory(path)) {
            return path.resolve("bin").resolve(executableName());
        }
        return path;
    }

    @NotNull
    private static String executableName() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) return "java.exe";
        return "java";
    }

    @NotNull
    private static JavaVersionResult inspect(@NotNull Path executable) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(executable.toAbsolutePath().toString(), "-version");
        builder.redirectErrorStream(true);

        Process process = builder.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line);
                output.append('\n');
            }
        }

        boolean finished;
        try {
            finished = process.waitFor(10, TimeUnit.SECONDS);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while checking Java runtime.", e);
        }

        if (!finished) {
            process.destroyForcibly();
            throw new IOException("Timed out checking Java runtime: " + executable);
        }

        Matcher matcher = VERSION_PATTERN.matcher(output);
        if (!matcher.find()) {
            throw new IOException("Could not determine Java version from:\n" + output);
        }

        String version = matcher.group(1);
        int major = parseMajorVersion(version);

        return new JavaVersionResult(major, version);
    }

    private static int parseMajorVersion(@NotNull String version) {
        //Java 8: 1.8.0_502
        if (version.startsWith("1.")) {
            String[] parts = version.split("\\.");
            if (parts.length < 2) {
                throw new IllegalArgumentException("Invalid Java version: " + version);
            }
            return Integer.parseInt(parts[1]);
        }

        //Java 9+: (17.0.15, 21.0.8, and 25.0.4)
        int dot = version.indexOf('.');
        String major = dot >= 0 ? version.substring(0, dot) : version;
        int dash = major.indexOf('-');
        if (dash >= 0) major = major.substring(0, dash);

        return Integer.parseInt(major);
    }

    private record JavaVersionResult(int major, @NotNull String version) {}
}
