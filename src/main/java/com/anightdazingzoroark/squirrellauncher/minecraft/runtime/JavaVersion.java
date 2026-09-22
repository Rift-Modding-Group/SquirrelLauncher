package com.anightdazingzoroark.squirrellauncher.minecraft.runtime;

import org.jetbrains.annotations.NotNull;

public enum JavaVersion {

    JAVA_8(
            8,
            "SQUIRREL_JAVA8",
            "java8"
    ),

    JAVA_25(
            25,
            "SQUIRREL_JAVA25",
            "java25"
    );

    private final int major;
    @NotNull
    private final String environmentVariable;
    @NotNull
    private final String directoryName;

    JavaVersion(int major, @NotNull String environmentVariable, @NotNull String directoryName) {
        this.major = major;
        this.environmentVariable = environmentVariable;
        this.directoryName = directoryName;
    }

    public int major() {
        return this.major;
    }

    @NotNull
    public String environmentVariable() {
        return this.environmentVariable;
    }

    @NotNull
    public String directoryName() {
        return this.directoryName;
    }
}