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
            (instance, installRoot) -> LaunchDefinition.vanilla()
    ),
    FORGE(
            new ForgeInstaller(),
            MinecraftInstance::loaderVersion,
            (instance, installRoot) -> LaunchDefinition.forge(instance)
    ),
    CLEANROOM(
            new CleanroomInstaller(),
            MinecraftInstance::loaderVersion,
            LaunchDefinition::cleanroom
    );

    @NotNull
    public final BiFunction<MinecraftAccount, MinecraftInstance, Process> instanceCreator;

    InstanceType(
            @NotNull AbstractInstaller installer,
            @NotNull Function<MinecraftInstance, String> versionResolver,
            @NotNull LaunchDefinitionFactory definitionFactory
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
    }

    @FunctionalInterface
    private interface LaunchDefinitionFactory {
        LaunchDefinition create(MinecraftInstance instance, Path installRoot) throws Exception;
    }
}
