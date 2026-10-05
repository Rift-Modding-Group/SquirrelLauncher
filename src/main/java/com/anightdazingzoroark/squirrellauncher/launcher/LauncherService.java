package com.anightdazingzoroark.squirrellauncher.launcher;

import com.anightdazingzoroark.squirrellauncher.minecraft.MinecraftPaths;
import com.anightdazingzoroark.squirrellauncher.minecraft.GitHubUtils;
import com.anightdazingzoroark.squirrellauncher.minecraft.auth.AccountManager;
import com.anightdazingzoroark.squirrellauncher.minecraft.auth.MicrosoftAuthenticator;
import com.anightdazingzoroark.squirrellauncher.minecraft.auth.MinecraftAccount;
import com.anightdazingzoroark.squirrellauncher.minecraft.instance.InstanceManager;
import com.anightdazingzoroark.squirrellauncher.minecraft.instance.InstanceLaunchSettings;
import com.anightdazingzoroark.squirrellauncher.minecraft.instance.InstanceIconManager;
import com.anightdazingzoroark.squirrellauncher.minecraft.instance.MinecraftInstance;
import com.anightdazingzoroark.squirrellauncher.minecraft.instance.InstanceType;
import com.anightdazingzoroark.squirrellauncher.minecraft.mod.ManagedMod;
import com.anightdazingzoroark.squirrellauncher.minecraft.mod.ModDownloadFile;
import com.anightdazingzoroark.squirrellauncher.minecraft.mod.ModDownloadManager;
import com.anightdazingzoroark.squirrellauncher.minecraft.mod.ModDownloadPlatform;
import com.anightdazingzoroark.squirrellauncher.minecraft.mod.ModDownloadProject;
import com.anightdazingzoroark.squirrellauncher.minecraft.mod.ModDownloadProjectDescription;
import com.anightdazingzoroark.squirrellauncher.minecraft.mod.ModDownloadSearchPage;
import com.anightdazingzoroark.squirrellauncher.minecraft.mod.ModManager;
import com.anightdazingzoroark.squirrellauncher.minecraft.modpack.MMCPackManager;
import com.anightdazingzoroark.squirrellauncher.minecraft.runtime.JavaRuntime;
import com.anightdazingzoroark.squirrellauncher.minecraft.runtime.JavaRuntimeManager;
import com.anightdazingzoroark.squirrellauncher.minecraft.runtime.JavaVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.image.BufferedImage;
import java.lang.management.ManagementFactory;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final GameSettingsManager settingsManager = new GameSettingsManager();
    @NotNull
    private final InstanceOrderManager instanceOrderManager = new InstanceOrderManager();
    @NotNull
    private final ModDownloadManager modDownloadManager = new ModDownloadManager();

    public LauncherService(@NotNull Consumer<String> outputListener) {
        this.outputBridge = new LauncherOutputBridge(outputListener);
        GitHubUtils.setPersonalAccessToken(this.settingsManager.settings().githubPAT());
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
    public GameSettings settings() {
        return this.settingsManager.settings();
    }

    public void updateSettings(@NotNull GameSettings settings) throws Exception {
        this.settingsManager.update(settings);
        GitHubUtils.setPersonalAccessToken(settings.githubPAT());
    }

    public void dismissLinuxJavaPackageManagerReminder() throws Exception {
        GameSettings savedSettings = this.settingsManager.settings();
        if (!savedSettings.showLinuxJavaPackageManagerReminder()) return;
        this.settingsManager.update(new GameSettings(
                savedSettings.fullscreen(),
                savedSettings.windowWidth(),
                savedSettings.windowHeight(),
                savedSettings.allocatedMemoryGigabytes(),
                savedSettings.lowMemoryWarning(),
                savedSettings.jvmArguments(),
                savedSettings.language(),
                savedSettings.launchBehavior(),
                false,
                savedSettings.javaRuntimePaths(),
                savedSettings.githubModRepositories(),
                savedSettings.githubPAT()
        ));
    }

    public void addJavaRuntime(@NotNull Path executable) throws Exception {
        GameSettings savedSettings = this.settingsManager.settings();
        Path normalizedExecutable = executable.toAbsolutePath().normalize();
        if (savedSettings.javaRuntimePaths().contains(normalizedExecutable)) return;
        List<Path> javaRuntimePaths = new ArrayList<>(savedSettings.javaRuntimePaths());
        javaRuntimePaths.add(normalizedExecutable);
        this.settingsManager.update(new GameSettings(
                savedSettings.fullscreen(),
                savedSettings.windowWidth(),
                savedSettings.windowHeight(),
                savedSettings.allocatedMemoryGigabytes(),
                savedSettings.lowMemoryWarning(),
                savedSettings.jvmArguments(),
                savedSettings.language(),
                savedSettings.launchBehavior(),
                savedSettings.showLinuxJavaPackageManagerReminder(),
                javaRuntimePaths,
                savedSettings.githubModRepositories(),
                savedSettings.githubPAT()
        ));
    }

    @Nullable
    public LowMemoryWarning lowMemoryWarning(@NotNull MinecraftInstance instance) {
        InstanceLaunchSettings instanceSettings = instance.launchSettings();
        GameSettings launcherSettings = this.settingsManager.settings();
        boolean warningEnabled = instanceSettings.overrideMemory()
                ? instanceSettings.lowMemoryWarning()
                : launcherSettings.lowMemoryWarning();
        if (!warningEnabled) return null;

        java.lang.management.OperatingSystemMXBean operatingSystem = ManagementFactory.getOperatingSystemMXBean();
        if (!(operatingSystem instanceof com.sun.management.OperatingSystemMXBean memoryOperatingSystem)) {
            return null;
        }
        long availableMemoryMiB = memoryOperatingSystem.getFreeMemorySize() / (1024L * 1024L);
        long totalMemoryMiB = memoryOperatingSystem.getTotalMemorySize() / (1024L * 1024L);
        Path linuxMemoryInformation = Path.of("/proc/meminfo");
        if (Files.isRegularFile(linuxMemoryInformation)) {
            try {
                for (String line : Files.readAllLines(linuxMemoryInformation)) {
                    String[] parts = line.trim().split("\\s+");
                    if (parts.length < 2) continue;
                    if (parts[0].equals("MemAvailable:")) {
                        availableMemoryMiB = Long.parseLong(parts[1]) / 1024L;
                    }
                    else if (parts[0].equals("MemTotal:")) {
                        totalMemoryMiB = Long.parseLong(parts[1]) / 1024L;
                    }
                }
            }
            catch (Exception ignored) {}
        }
        if (availableMemoryMiB <= 0L) return null;
        int allocatedMemoryMiB = (instanceSettings.overrideMemory()
                ? instanceSettings.allocatedMemoryGigabytes()
                : launcherSettings.allocatedMemoryGigabytes()) * 1024;
        if ((long) allocatedMemoryMiB * 7L <= availableMemoryMiB * 10L) return null;
        return new LowMemoryWarning(allocatedMemoryMiB, availableMemoryMiB, totalMemoryMiB);
    }

    //---instance stuff---
    @NotNull
    public List<MinecraftInstance> listInstances() throws Exception {
        return this.instanceOrderManager.apply(InstanceManager.list());
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
        if (request.importsInstance()) {
            InstanceManager.setConfigValue(
                    instance.directory(),
                    "SquirrelLauncherCreatedTime",
                    Long.toString(System.currentTimeMillis())
            );
            instance = InstanceManager.load(instance.id());
        }
        if (request.icon() != null) {
            try {
                instance = InstanceIconManager.assign(instance, request.icon());
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
        this.instanceOrderManager.moveToTop(instance.id());
        return instance;
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
            InstanceManager.setConfigValue(staging, "SquirrelLauncherCreatedTime", Long.toString(System.currentTimeMillis()));
            InstanceManager.setConfigValue(staging, "lastLaunchTime", "0");
            InstanceManager.setConfigValue(staging, "lastTimePlayed", "0");
            InstanceManager.setConfigValue(staging, "totalTimePlayed", "0");
            InstanceManager.loadFromDirectory(instanceId, staging);
            try {
                Files.move(staging, destination, StandardCopyOption.ATOMIC_MOVE);
            }
            catch (AtomicMoveNotSupportedException exception) {
                Files.move(staging, destination);
            }
            moved = true;
            MinecraftInstance duplicate = InstanceManager.load(instanceId);
            this.instanceOrderManager.moveToTop(duplicate.id());
            return duplicate;
        }
        finally {
            if (!moved && Files.exists(staging)) this.deleteDirectory(staging);
        }
    }

    @NotNull
    public MinecraftInstance updateInstanceLaunchSettings(
            @NotNull String instanceId,
            @NotNull InstanceLaunchSettings settings
    ) throws Exception {
        MinecraftInstance instance = InstanceManager.load(instanceId);
        InstanceLaunchSettings validatedSettings = settings;
        if (settings.javaExecutable() != null) {
            JavaVersion requiredVersion = instance.type() == InstanceType.CLEANROOM
                    ? JavaVersion.JAVA_25
                    : JavaVersion.JAVA_8;
            JavaRuntime runtime = JavaRuntimeManager.resolve(requiredVersion, settings.javaExecutable());
            validatedSettings = new InstanceLaunchSettings(
                    runtime.executable(),
                    settings.overrideWindowSettings(),
                    settings.fullscreen(),
                    settings.windowWidth(),
                    settings.windowHeight(),
                    settings.overrideMemory(),
                    settings.allocatedMemoryGigabytes(),
                    settings.lowMemoryWarning(),
                    settings.overrideJvmArguments(),
                    settings.jvmArguments()
            );
        }
        Map<String, String> values = new LinkedHashMap<>();
        values.put("OverrideJavaLocation", Boolean.toString(validatedSettings.javaExecutable() != null));
        values.put(
                "JavaPath",
                validatedSettings.javaExecutable() == null ? null : validatedSettings.javaExecutable().toString()
        );
        values.put("OverrideWindow", Boolean.toString(validatedSettings.overrideWindowSettings()));
        values.put("LaunchMaximized", Boolean.toString(validatedSettings.fullscreen()));
        values.put("SquirrelLauncherFullscreen", Boolean.toString(validatedSettings.fullscreen()));
        values.put("MinecraftWinWidth", Integer.toString(validatedSettings.windowWidth()));
        values.put("MinecraftWinHeight", Integer.toString(validatedSettings.windowHeight()));
        values.put("OverrideMemory", Boolean.toString(validatedSettings.overrideMemory()));
        values.put(
                "MinMemAlloc",
                Integer.toString(JvmArguments.minimumMemoryMegabytes(validatedSettings.jvmArguments()))
        );
        values.put("MaxMemAlloc", Integer.toString(validatedSettings.allocatedMemoryGigabytes() * 1024));
        values.put("LowMemWarning", Boolean.toString(validatedSettings.lowMemoryWarning()));
        values.put("OverrideJavaArgs", Boolean.toString(validatedSettings.overrideJvmArguments()));
        List<String> additionalJvmArguments = new ArrayList<>();
        for (String argument : validatedSettings.jvmArguments()) {
            if (!JvmArguments.isMemoryArgument(argument)) additionalJvmArguments.add(argument);
        }
        values.put("JvmArgs", JvmArguments.format(additionalJvmArguments));
        InstanceManager.setConfigValues(instance.directory(), values);
        return InstanceManager.load(instanceId);
    }

    @NotNull
    public MinecraftInstance updateInstanceNotes(@NotNull String instanceId, @NotNull String notes)
            throws Exception {
        MinecraftInstance instance = InstanceManager.load(instanceId);
        InstanceManager.setConfigValue(instance.directory(), "notes", notes);
        return InstanceManager.load(instanceId);
    }

    @NotNull
    public MinecraftInstance convertInstance(@NotNull MinecraftInstance instance, @NotNull InstanceType type, @Nullable String loaderVersion) throws Exception {
        return InstanceManager.convert(instance, type, loaderVersion);
    }

    @NotNull
    public MinecraftInstance changeLoaderVersion(
            @NotNull MinecraftInstance instance,
            @NotNull String loaderVersion
    ) throws Exception {
        if (!instance.type().hasMods) throw new IllegalArgumentException("Vanilla instances do not have a loader version.");
        return InstanceManager.convert(instance, instance.type(), loaderVersion);
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
        MinecraftInstance renamed = InstanceManager.load(instanceId);
        this.instanceOrderManager.replace(instance.id(), renamed.id());
        return renamed;
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
        this.instanceOrderManager.remove(instance.id());
    }

    public void reorderInstances(@NotNull List<MinecraftInstance> instances) throws Exception {
        List<String> instanceIds = new ArrayList<>(instances.size());
        for (MinecraftInstance instance : instances) instanceIds.add(instance.id());
        this.instanceOrderManager.save(instanceIds);
    }

    @NotNull
    public MinecraftInstance recordPlaytime(@NotNull MinecraftInstance instance, long elapsedSeconds, long launchTimeMillis) throws Exception {
        MinecraftInstance current = InstanceManager.load(instance.id());
        long safeElapsedSeconds = Math.max(0, elapsedSeconds);
        long totalTimePlayed = current.totalTimePlayedSeconds();
        if (Long.MAX_VALUE - totalTimePlayed < safeElapsedSeconds) totalTimePlayed = Long.MAX_VALUE;
        else totalTimePlayed += safeElapsedSeconds;
        InstanceManager.setConfigValue(
                current.directory(),
                "lastLaunchTime",
                Long.toString(Math.max(0, launchTimeMillis))
        );
        InstanceManager.setConfigValue(
                current.directory(),
                "totalTimePlayed",
                Long.toString(totalTimePlayed)
        );
        InstanceManager.setConfigValue(
                current.directory(),
                "lastTimePlayed",
                Long.toString(safeElapsedSeconds)
        );
        return InstanceManager.load(current.id());
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

    @NotNull
    public ModDownloadSearchPage searchMods(
            @NotNull ModDownloadPlatform platform,
            @NotNull String searchText,
            int offset
    ) throws Exception {
        return this.modDownloadManager.search(
                platform,
                searchText,
                offset,
                this.settingsManager.settings().githubModRepositories()
        );
    }

    @NotNull
    public List<ModDownloadFile> modFiles(@NotNull ModDownloadProject project) throws Exception {
        return this.modDownloadManager.files(project);
    }

    @NotNull
    public ModDownloadProjectDescription modDescription(@NotNull ModDownloadProject project) throws Exception {
        return this.modDownloadManager.description(project);
    }

    @Nullable
    public BufferedImage modIcon(@NotNull ModDownloadProject project) throws Exception {
        return this.modDownloadManager.icon(project);
    }

    @NotNull
    public List<ModDownloadFile> modDependencies(@NotNull MinecraftInstance instance, @NotNull List<ModDownloadFile> files) throws Exception {
        return this.modDownloadManager.dependencies(instance, files);
    }

    public boolean toggleModFavorite(@NotNull ModDownloadProject project) throws Exception {
        return this.modDownloadManager.toggleFavorite(project);
    }

    @Nullable
    public ModDownloadFile modUpdate(@NotNull ManagedMod mod) throws Exception {
        return this.modDownloadManager.updateFor(mod);
    }

    public void installMod(@NotNull MinecraftInstance instance, @NotNull ModDownloadFile file) throws Exception {
        this.modDownloadManager.install(instance, file);
    }

    @NotNull
    public ManagedMod setModEnabled(@NotNull MinecraftInstance instance, @NotNull ManagedMod mod, boolean enabled) throws Exception {
        ModManager manager = new ModManager(instance);
        return enabled ? manager.enable(mod.fileName()) : manager.disable(mod.fileName());
    }

    public void removeMod(@NotNull MinecraftInstance instance, @NotNull ManagedMod mod) throws Exception {
        new ModManager(instance).remove(mod.fileName());
    }

    //---game stuff---
    @NotNull
    public Process launch(@NotNull MinecraftInstance instance) throws Exception {
        MinecraftAccount account = this.accountManager.prepareSelectedAccount();
        MinecraftInstance storedInstance = InstanceManager.load(instance.id());
        return storedInstance.type().launch(account, storedInstance, this.settingsManager.settings());
    }

    @Override
    public void close() {
        this.outputBridge.close();
    }

    public record LowMemoryWarning(int allocatedMemoryMiB, long availableMemoryMiB, long totalMemoryMiB) {}
}
