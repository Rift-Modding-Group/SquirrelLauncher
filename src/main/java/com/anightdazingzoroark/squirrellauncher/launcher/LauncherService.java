package com.anightdazingzoroark.squirrellauncher.launcher;

import com.anightdazingzoroark.squirrellauncher.minecraft.MinecraftPaths;
import com.anightdazingzoroark.squirrellauncher.minecraft.auth.AccountManager;
import com.anightdazingzoroark.squirrellauncher.minecraft.auth.MicrosoftAuthenticator;
import com.anightdazingzoroark.squirrellauncher.minecraft.auth.MinecraftAccount;
import com.anightdazingzoroark.squirrellauncher.minecraft.instance.InstanceManager;
import com.anightdazingzoroark.squirrellauncher.minecraft.instance.InstanceIconManager;
import com.anightdazingzoroark.squirrellauncher.minecraft.instance.MinecraftInstance;
import com.anightdazingzoroark.squirrellauncher.minecraft.instance.InstanceType;
import com.anightdazingzoroark.squirrellauncher.minecraft.mod.ManagedMod;
import com.anightdazingzoroark.squirrellauncher.minecraft.mod.ModManager;
import com.anightdazingzoroark.squirrellauncher.minecraft.modpack.MMCPackManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * UI-independent orchestration for the launcher workflows.
 * */
public final class LauncherService implements AutoCloseable {
    @NotNull
    private final LauncherOutputBridge outputBridge;
    @NotNull
    private final AccountManager accountManager = new AccountManager();
    @NotNull
    private final LauncherSettingsManager settingsManager = new LauncherSettingsManager();

    public LauncherService(@NotNull Consumer<String> outputListener) {
        this.outputBridge = new LauncherOutputBridge(outputListener);
    }

    //---account stuff---
    @Nullable
    public MinecraftAccount account() {
        return this.accountManager.selectedAccount();
    }

    @NotNull
    public List<MinecraftAccount> accounts() {
        return this.accountManager.accounts();
    }

    @NotNull
    public MinecraftAccount addOfflineAccount(@NotNull String username) throws Exception {
        return this.accountManager.addOfflineAccount(username);
    }

    @NotNull
    public MinecraftAccount addMicrosoftAccount(@NotNull Consumer<MicrosoftAuthenticator.DeviceCode> deviceCodeConsumer) throws Exception {
        return this.accountManager.addMicrosoftAccount(deviceCodeConsumer);
    }

    public void selectAccount(@NotNull MinecraftAccount account) throws Exception {
        this.accountManager.select(account);
    }

    public void removeAccount(@NotNull MinecraftAccount account) throws Exception {
        this.accountManager.remove(account);
    }

    //---settings stuff---
    @NotNull
    public LauncherSettings settings() {
        return this.settingsManager.settings();
    }

    public void updateSettings(@NotNull LauncherSettings settings) throws Exception {
        this.settingsManager.update(settings);
    }

    //---instance stuff---
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
        MinecraftInstance instance;
        if (request.importsInstance()) {
            instance = MMCPackManager.importPack(request.archive(), instanceId, name);
        }
        else {
            if (request.type() == null) throw new IllegalArgumentException("Instance type is missing.");
            String loaderVersion = request.type().hasMods ? request.loaderVersion() : null;
            if (loaderVersion != null) loaderVersion = loaderVersion.trim();
            if (request.type().hasMods && (loaderVersion == null || loaderVersion.isEmpty())) {
                throw new IllegalArgumentException("A loader version is required for " + request.type() + ".");
            }
            instance = InstanceManager.create(instanceId, name, request.type(), loaderVersion);
        }
        if (request.icon() == null) return instance;
        try {
            return InstanceIconManager.assign(instance, request.icon());
        }
        catch (Exception exception) {
            try {
                this.deleteDirectory(instance.directory());
            }
            catch (Exception rollbackException) {
                exception.addSuppressed(rollbackException);
            }
            throw exception;
        }
    }

    @NotNull
    public MinecraftInstance duplicateInstance(@NotNull MinecraftInstance instance, @NotNull String name) throws Exception {
        if (!InstanceNames.isValid(name)) {
            throw new IllegalArgumentException("Instance name cannot be empty or contain control characters.");
        }
        MinecraftInstance sourceInstance = InstanceManager.load(instance.id());
        String instanceId = this.availableInstanceId(name, null);
        Path destination = MinecraftPaths.INSTANCES.resolve(instanceId);
        Files.createDirectories(MinecraftPaths.INSTANCES);
        Path staging = Files.createTempDirectory(MinecraftPaths.INSTANCES, ".copy-");
        boolean moved = false;
        try {
            try (Stream<Path> paths = Files.walk(sourceInstance.directory())) {
                java.util.Iterator<Path> iterator = paths.iterator();
                while (iterator.hasNext()) {
                    Path source = iterator.next();
                    Path relative = sourceInstance.directory().relativize(source);
                    if (relative.toString().isEmpty()) continue;
                    Path target = staging.resolve(relative);
                    if (Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)) {
                        Files.createDirectories(target);
                    }
                    else {
                        Files.createDirectories(target.getParent());
                        Files.copy(source, target, LinkOption.NOFOLLOW_LINKS, StandardCopyOption.COPY_ATTRIBUTES);
                    }
                }
            }
            InstanceManager.setName(staging, name);
            InstanceManager.loadFromDirectory(instanceId, staging);
            try {
                Files.move(staging, destination, StandardCopyOption.ATOMIC_MOVE);
            }
            catch (AtomicMoveNotSupportedException exception) {
                Files.move(staging, destination);
            }
            moved = true;
            return InstanceManager.load(instanceId);
        }
        finally {
            if (!moved && Files.exists(staging)) this.deleteDirectory(staging);
        }
    }

    @NotNull
    public MinecraftInstance convertInstance(@NotNull MinecraftInstance instance, @NotNull InstanceType type, @Nullable String loaderVersion) throws Exception {
        return InstanceManager.convert(instance, type, loaderVersion);
    }

    @NotNull
    public MinecraftInstance setInstanceIcon(@NotNull MinecraftInstance instance, @NotNull Path source) throws Exception {
        return InstanceIconManager.assign(instance, source);
    }

    @NotNull
    public MinecraftInstance resetInstanceIcon(@NotNull MinecraftInstance instance) throws Exception {
        return InstanceIconManager.reset(instance);
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

        this.deleteDirectory(instanceDirectory);
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

    private void deleteDirectory(@NotNull Path directory) throws Exception {
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
        }
    }

    //---mod stuff---
    @NotNull
    public List<ManagedMod> listMods(@NotNull MinecraftInstance instance) throws Exception {
        return new ModManager(instance).list();
    }

    public void installMod(@NotNull MinecraftInstance instance, @NotNull Path source) throws Exception {
        new ModManager(instance).install(source);
    }

    public void setModEnabled(@NotNull MinecraftInstance instance, @NotNull ManagedMod mod, boolean enabled) throws Exception {
        ModManager manager = new ModManager(instance);
        if (enabled) manager.enable(mod.fileName());
        else manager.disable(mod.fileName());
    }

    public void removeMod(@NotNull MinecraftInstance instance, @NotNull ManagedMod mod) throws Exception {
        new ModManager(instance).remove(mod.fileName());
    }

    //---game stuff---
    @NotNull
    public Process launch(@NotNull MinecraftInstance instance) throws Exception {
        MinecraftAccount account = this.accountManager.prepareSelectedAccount();
        return instance.type().launch(account, instance, this.settingsManager.settings());
    }

    @Override
    public void close() {
        this.outputBridge.close();
    }
}
