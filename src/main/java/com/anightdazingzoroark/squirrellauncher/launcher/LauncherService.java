package com.anightdazingzoroark.squirrellauncher.launcher;

import com.anightdazingzoroark.squirrellauncher.minecraft.MinecraftPaths;
import com.anightdazingzoroark.squirrellauncher.minecraft.auth.MicrosoftAuthenticator;
import com.anightdazingzoroark.squirrellauncher.minecraft.auth.MinecraftAccount;
import com.anightdazingzoroark.squirrellauncher.minecraft.instance.InstanceManager;
import com.anightdazingzoroark.squirrellauncher.minecraft.instance.MinecraftInstance;
import com.anightdazingzoroark.squirrellauncher.minecraft.mod.ManagedMod;
import com.anightdazingzoroark.squirrellauncher.minecraft.mod.ModManager;
import com.anightdazingzoroark.squirrellauncher.minecraft.modpack.MMCPackManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

/** UI-independent orchestration for the launcher workflows. */
public final class LauncherService implements AutoCloseable {
    private final LauncherOutputBridge outputBridge;
    private volatile MinecraftAccount account = MinecraftAccount.offline("Squirrel");

    public LauncherService(@NotNull Consumer<String> outputListener) {
        this.outputBridge = new LauncherOutputBridge(outputListener);
    }

    @NotNull
    public MinecraftAccount account() {
        return this.account;
    }

    @NotNull
    public MinecraftAccount useOfflineAccount(@NotNull String username) {
        String normalized = username.trim();
        if (normalized.isEmpty()) normalized = "Squirrel";
        if (!normalized.matches("[A-Za-z0-9_]{1,16}")) {
            throw new IllegalArgumentException(
                    "Offline username must contain 1-16 letters, numbers, or underscores."
            );
        }
        this.account = MinecraftAccount.offline(normalized);
        return this.account;
    }

    @NotNull
    public MinecraftAccount signInWithMicrosoft() throws Exception {
        MinecraftAccount signedIn = MicrosoftAuthenticator.login();
        this.account = signedIn;
        return signedIn;
    }

    @NotNull
    public List<MinecraftInstance> listInstances() throws Exception {
        return InstanceManager.list();
    }

    @NotNull
    public MinecraftInstance addInstance(@NotNull InstanceAdditionRequest request) throws Exception {
        String name = request.name();
        if (!InstanceNames.isValid(name)) {
            throw new IllegalArgumentException("Instance name cannot be empty or contain control characters.");
        }
        String instanceId = this.availableInstanceId(name, null);
        if (request.importsInstance()) {
            return MMCPackManager.importPack(request.archive(), instanceId, name);
        }

        if (request.type() == null) throw new IllegalArgumentException("Instance type is missing.");
        String loaderVersion = request.type().hasMods ? request.loaderVersion() : null;
        if (loaderVersion != null) loaderVersion = loaderVersion.trim();
        if (request.type().hasMods && (loaderVersion == null || loaderVersion.isEmpty())) {
            throw new IllegalArgumentException("A loader version is required for " + request.type() + ".");
        }
        return InstanceManager.create(instanceId, name, request.type(), loaderVersion);
    }

    @NotNull
    public MinecraftInstance renameInstance(@NotNull MinecraftInstance instance, @NotNull String name) throws Exception {
        if (!InstanceNames.isValid(name)) {
            throw new IllegalArgumentException("Instance name cannot be empty or contain control characters.");
        }

        String instanceId = this.availableInstanceId(name, instance.id());
        Path source = instance.directory();
        Path destination = MinecraftPaths.INSTANCES.resolve(instanceId);
        boolean moved = !source.equals(destination);
        if (moved) {
            try {
                Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
            }
            catch (AtomicMoveNotSupportedException exception) {
                Files.move(source, destination);
            }
        }

        try {
            InstanceManager.setName(destination, name);
        }
        catch (Exception exception) {
            if (moved) {
                try {
                    Files.move(destination, source);
                }
                catch (Exception rollbackException) {
                    exception.addSuppressed(rollbackException);
                }
            }
            throw exception;
        }
        return InstanceManager.load(instanceId);
    }

    public void exportInstance(@NotNull MinecraftInstance instance, @NotNull Path destination) throws Exception {
        MMCPackManager.exportPack(instance, destination);
    }

    public void deleteInstance(@NotNull MinecraftInstance instance) throws Exception {
        Path instancesRoot = MinecraftPaths.INSTANCES.toAbsolutePath().normalize();
        Path instanceDirectory = instance.directory().toAbsolutePath().normalize();
        if (!instanceDirectory.getParent().equals(instancesRoot)) {
            throw new IllegalArgumentException("Invalid instance directory: " + instanceDirectory);
        }
        if (!Files.exists(instanceDirectory)) {
            throw new IllegalArgumentException("Instance does not exist: " + instance.name());
        }

        try (Stream<Path> paths = Files.walk(instanceDirectory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    @NotNull
    public List<ManagedMod> listMods(@NotNull MinecraftInstance instance) throws Exception {
        return new ModManager(instance).list();
    }

    public void installMod(@NotNull MinecraftInstance instance, @NotNull Path source) throws Exception {
        new ModManager(instance).install(source);
    }

    public void setModEnabled(
            @NotNull MinecraftInstance instance,
            @NotNull ManagedMod mod,
            boolean enabled
    ) throws Exception {
        ModManager manager = new ModManager(instance);
        if (enabled) manager.enable(mod.fileName());
        else manager.disable(mod.fileName());
    }

    public void removeMod(@NotNull MinecraftInstance instance, @NotNull ManagedMod mod) throws Exception {
        new ModManager(instance).remove(mod.fileName());
    }

    @NotNull
    public Process launch(@NotNull MinecraftInstance instance) throws Exception {
        try {
            return instance.type().instanceCreator.apply(this.account, instance);
        }
        catch (RuntimeException exception) {
            if (exception.getCause() instanceof Exception cause) throw cause;
            throw exception;
        }
    }

    @Override
    public void close() {
        this.outputBridge.close();
    }

    @NotNull
    private String availableInstanceId(@NotNull String name, @Nullable String currentId) {
        String baseName = InstanceNames.folderName(name);
        String candidate = baseName;
        int suffix = 0;
        while (!candidate.equals(currentId) && Files.exists(MinecraftPaths.INSTANCES.resolve(candidate))) {
            suffix++;
            candidate = baseName + "(" + suffix + ")";
        }
        return candidate;
    }
}
