package com.anightdazingzoroark.squirrellauncher.launcher;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public enum GarbageCollector {
    DEFAULT(null),
    G1("-XX:+UseG1GC"),
    PARALLEL("-XX:+UseParallelGC"),
    SERIAL("-XX:+UseSerialGC"),
    CUSTOM(null);

    @NotNull
    private static final List<String> COLLECTOR_ARGUMENTS = List.of(
            "-XX:+UseSerialGC",
            "-XX:-UseSerialGC",
            "-XX:+UseParallelGC",
            "-XX:-UseParallelGC",
            "-XX:+UseParallelOldGC",
            "-XX:-UseParallelOldGC",
            "-XX:+UseConcMarkSweepGC",
            "-XX:-UseConcMarkSweepGC",
            "-XX:+UseG1GC",
            "-XX:-UseG1GC",
            "-XX:+UseZGC",
            "-XX:-UseZGC",
            "-XX:+UseShenandoahGC",
            "-XX:-UseShenandoahGC",
            "-XX:+UseEpsilonGC",
            "-XX:-UseEpsilonGC"
    );
    @Nullable
    private final String jvmArgument;

    GarbageCollector(@Nullable String jvmArgument) {
        this.jvmArgument = jvmArgument;
    }

    public boolean matches(@NotNull List<String> arguments) {
        GarbageCollector selected = GarbageCollector.DEFAULT;
        for (String argument : arguments) {
            if (!argument.startsWith("-XX:+") || !GarbageCollector.COLLECTOR_ARGUMENTS.contains(argument)) continue;
            GarbageCollector argumentCollector = switch (argument) {
                case "-XX:+UseG1GC" -> GarbageCollector.G1;
                case "-XX:+UseParallelGC", "-XX:+UseParallelOldGC" -> GarbageCollector.PARALLEL;
                case "-XX:+UseSerialGC" -> GarbageCollector.SERIAL;
                default -> GarbageCollector.CUSTOM;
            };
            if (selected != GarbageCollector.DEFAULT && selected != argumentCollector) {
                selected = GarbageCollector.CUSTOM;
                break;
            }
            selected = argumentCollector;
        }
        return this == selected;
    }

    @NotNull
    public List<String> applyTo(@NotNull List<String> arguments) {
        if (this == GarbageCollector.CUSTOM) return List.copyOf(arguments);
        List<String> updatedArguments = new ArrayList<>(arguments.size() + 1);
        for (String argument : arguments) {
            if (!GarbageCollector.COLLECTOR_ARGUMENTS.contains(argument)) updatedArguments.add(argument);
        }
        if (this.jvmArgument != null) updatedArguments.add(this.jvmArgument);
        return List.copyOf(updatedArguments);
    }
}
