package com.anightdazingzoroark.squirrellauncher.minecraft.instance;

import com.anightdazingzoroark.squirrellauncher.SquirrelLauncher;
import com.anightdazingzoroark.squirrellauncher.minecraft.auth.MinecraftAccount;
import com.anightdazingzoroark.squirrellauncher.minecraft.install.AbstractInstaller;
import com.anightdazingzoroark.squirrellauncher.minecraft.install.CleanroomInstaller;
import com.anightdazingzoroark.squirrellauncher.minecraft.install.ForgeInstaller;
import com.anightdazingzoroark.squirrellauncher.minecraft.install.VanillaInstaller;
import com.anightdazingzoroark.squirrellauncher.minecraft.launch.LaunchDefinition;
import com.anightdazingzoroark.squirrellauncher.minecraft.launch.MinecraftLauncher;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.function.BiFunction;
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
    public final BiFunction<MinecraftAccount, MinecraftInstance, Process> instanceCreator;
    public final boolean hasMods;

    InstanceType(
            @NotNull AbstractInstaller installer,
            @NotNull Function<MinecraftInstance, String> versionResolver,
            @NotNull LaunchDefinitionFactory definitionFactory,
            boolean hasMods
    ) {
        this.instanceCreator = (account, instance) -> {
            try {
                Path installRoot = installer.install(versionResolver.apply(instance));
                LaunchDefinition definition = definitionFactory.create(instance, installRoot);
                return MinecraftLauncher.launch(definition, account, instance);
            }
            catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
        this.hasMods = hasMods;
    }

    @FunctionalInterface
    private interface LaunchDefinitionFactory {
        LaunchDefinition create(MinecraftInstance instance, Path installRoot) throws Exception;
    }
}
