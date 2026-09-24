package com.anightdazingzoroark.squirrellauncher.minecraft.instance;

import com.anightdazingzoroark.squirrellauncher.SquirrelLauncher;
import com.anightdazingzoroark.squirrellauncher.launcher.LauncherSettings;
import com.anightdazingzoroark.squirrellauncher.minecraft.auth.MinecraftAccount;
import com.anightdazingzoroark.squirrellauncher.minecraft.install.AbstractInstaller;
import com.anightdazingzoroark.squirrellauncher.minecraft.install.CleanroomInstaller;
import com.anightdazingzoroark.squirrellauncher.minecraft.install.ForgeInstaller;
import com.anightdazingzoroark.squirrellauncher.minecraft.install.VanillaInstaller;
import com.anightdazingzoroark.squirrellauncher.minecraft.launch.LaunchDefinition;
import com.anightdazingzoroark.squirrellauncher.minecraft.launch.MinecraftLauncher;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.function.Function;

public enum InstanceType {
    VANILLA(
            new VanillaInstaller(),
            instance -> SquirrelLauncher.VERSION,
            (instance, installRoot) -> LaunchDefinition.vanilla(),
            false
    ),
    FORGE(
            new ForgeInstaller(),
            MinecraftInstance::loaderVersion,
            (instance, installRoot) -> LaunchDefinition.forge(instance),
            true
    ),
    CLEANROOM(
            new CleanroomInstaller(),
            MinecraftInstance::loaderVersion,
            LaunchDefinition::cleanroom,
            true
    );

    @NotNull
    private final AbstractInstaller installer;
    @NotNull
    private final Function<MinecraftInstance, String> versionResolver;
    @NotNull
    private final LaunchDefinitionFactory definitionFactory;
    public final boolean hasMods;

    InstanceType(
            @NotNull AbstractInstaller installer,
            @NotNull Function<MinecraftInstance, String> versionResolver,
            @NotNull LaunchDefinitionFactory definitionFactory,
            boolean hasMods
    ) {
        this.installer = installer;
        this.versionResolver = versionResolver;
        this.definitionFactory = definitionFactory;
        this.hasMods = hasMods;
    }

    @NotNull
    public Process launch(
            @NotNull MinecraftAccount account,
            @NotNull MinecraftInstance instance,
            @NotNull LauncherSettings settings
    ) throws Exception {
        Path installRoot = this.installer.install(this.versionResolver.apply(instance));
        LaunchDefinition definition = this.definitionFactory.create(instance, installRoot);
        return MinecraftLauncher.launch(definition, account, instance, settings);
    }

    @NotNull
    public String getIconPath() {
        return "/icons/"+this.name().toLowerCase()+".png";
    }

    @FunctionalInterface
    private interface LaunchDefinitionFactory {
        LaunchDefinition create(MinecraftInstance instance, Path installRoot) throws Exception;
    }
}
