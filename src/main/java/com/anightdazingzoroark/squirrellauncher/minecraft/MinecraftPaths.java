package com.anightdazingzoroark.squirrellauncher.minecraft;

import java.nio.file.Path;

/**
 * helper w paths leading to important directories
 * */
public final class MinecraftPaths {
    public static final Path ROOT = Path.of(System.getProperty("user.home"), ".squirrellauncher");
    public static final Path VERSIONS = ROOT.resolve("versions");
    public static final Path LIBRARIES = ROOT.resolve("libraries");
    public static final Path ASSETS = ROOT.resolve("assets");
    public static final Path ASSET_INDEXES = ASSETS.resolve("indexes");
    public static final Path ASSET_OBJECTS = ASSETS.resolve("objects");
    public static final Path INSTANCES = ROOT.resolve("instances");
    public static final Path INSTALLERS = ROOT.resolve("installers");
    public static final Path CLEANROOM = ROOT.resolve("loaders").resolve("cleanroom");
    public static final Path RUNTIMES = ROOT.resolve("runtimes");

    private MinecraftPaths() {}
}