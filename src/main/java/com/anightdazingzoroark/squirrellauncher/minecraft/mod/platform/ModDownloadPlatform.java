package com.anightdazingzoroark.squirrellauncher.minecraft.mod.platform;

import org.jetbrains.annotations.NotNull;

public enum ModDownloadPlatform {
    MODRINTH(new ModrinthModPlatformHandler()),
    CURSEFORGE(new CurseforgeModPlatformHandler()),
    GITHUB(new GithubModPlatformHandler());
    //CHIZU //:trollface: we're patiently waiting...

    @NotNull
    private final AbstractModPlatformHandler modPlatformHandler;

    ModDownloadPlatform(@NotNull AbstractModPlatformHandler modPlatformHandler) {
        this.modPlatformHandler = modPlatformHandler;
    }

    @NotNull
    public AbstractModPlatformHandler getModPlatformHandler() {
        return this.modPlatformHandler;
    }
}
