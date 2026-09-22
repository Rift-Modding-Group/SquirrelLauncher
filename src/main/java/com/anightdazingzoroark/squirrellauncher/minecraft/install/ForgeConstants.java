package com.anightdazingzoroark.squirrellauncher.minecraft.install;

import com.anightdazingzoroark.squirrellauncher.SquirrelLauncher;

public final class ForgeConstants {
    private ForgeConstants() {}

    public static String fullVersion(String forgeVersion) {
        return SquirrelLauncher.VERSION+ "-" + forgeVersion;
    }

    public static String versionId(String forgeVersion) {
        return SquirrelLauncher.VERSION + "-forge-" + forgeVersion;
    }

    public static String installerUrl(String forgeVersion) {
        String fullVersion = fullVersion(forgeVersion);
        return "https://maven.minecraftforge.net/"
                + "net/minecraftforge/forge/"
                + fullVersion
                + "/forge-"
                + fullVersion
                + "-installer.jar";
    }
}