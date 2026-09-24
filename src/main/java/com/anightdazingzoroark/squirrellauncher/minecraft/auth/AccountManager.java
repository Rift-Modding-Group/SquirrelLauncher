package com.anightdazingzoroark.squirrellauncher.minecraft.auth;

import com.anightdazingzoroark.squirrellauncher.minecraft.MinecraftPaths;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Consumer;

/** Owns the launcher's saved accounts and active account selection. */
public final class AccountManager {
    private static final int FORMAT_VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @NotNull
    private final List<MinecraftAccount> accounts = new ArrayList<>();
    @NotNull
    private final MicrosoftAuthenticator microsoftAuthenticator = new MicrosoftAuthenticator();
    @Nullable
    private String selectedAccountKey;

    public AccountManager() {
        Path accountsFile = MinecraftPaths.ACCOUNTS;
        if (!Files.exists(accountsFile)) return;

        try {
            JsonObject root = JsonParser.parseString(Files.readString(accountsFile)).getAsJsonObject();
            int formatVersion = root.has("formatVersion") ? root.get("formatVersion").getAsInt() : 0;
            if (formatVersion != FORMAT_VERSION) {
                throw new IOException("Unsupported account file version: " + formatVersion);
            }

            if (root.has("selectedAccount") && !root.get("selectedAccount").isJsonNull()) {
                this.selectedAccountKey = root.get("selectedAccount").getAsString();
            }
            JsonArray savedAccounts = root.has("accounts") ? root.getAsJsonArray("accounts") : new JsonArray();
            for (JsonElement element : savedAccounts) {
                MinecraftAccount account = AccountManager.GSON.fromJson(element, MinecraftAccount.class);
                if (account == null || account.username() == null || account.uuid() == null
                        || account.accessToken() == null || account.type() == null) {
                    throw new IOException("The account file contains an incomplete account.");
                }
                if (account.type() == MinecraftAccount.AccountType.MICROSOFT
                        && (account.refreshToken() == null || account.refreshToken().isBlank())) {
                    throw new IOException("A saved Microsoft account is missing its refresh token.");
                }
                this.accounts.add(account);
            }

            if (this.selectedAccount() == null) {
                this.selectedAccountKey = this.accounts.isEmpty() ? null : this.accounts.getFirst().key();
            }
        }
        catch (Exception exception) {
            throw new IllegalStateException("Could not load saved accounts from " + accountsFile + ".", exception);
        }
    }

    @NotNull
    public synchronized List<MinecraftAccount> accounts() {
        return List.copyOf(this.accounts);
    }

    @Nullable
    public synchronized MinecraftAccount selectedAccount() {
        if (this.selectedAccountKey == null) return null;
        for (MinecraftAccount account : this.accounts) {
            if (this.selectedAccountKey.equals(account.key())) return account;
        }
        return null;
    }

    @NotNull
    public synchronized MinecraftAccount addOfflineAccount(@NotNull String username) throws IOException {
        String normalized = username.trim();
        if (!normalized.matches("[A-Za-z0-9_]{1,16}")) {
            throw new IllegalArgumentException(
                    "Offline username must contain 1-16 letters, numbers, or underscores."
            );
        }

        MinecraftAccount account = MinecraftAccount.offline(normalized);
        this.upsert(account);
        return account;
    }

    @NotNull
    public MinecraftAccount addMicrosoftAccount(
            @NotNull Consumer<MicrosoftAuthenticator.DeviceCode> deviceCodeConsumer
    ) throws Exception {
        MinecraftAccount account = this.microsoftAuthenticator.login(deviceCodeConsumer);
        synchronized (this) {
            this.upsert(account);
        }
        return account;
    }

    public synchronized void select(@NotNull MinecraftAccount account) throws IOException {
        for (MinecraftAccount savedAccount : this.accounts) {
            if (savedAccount.key().equals(account.key())) {
                this.selectedAccountKey = savedAccount.key();
                this.save();
                return;
            }
        }
        throw new IllegalArgumentException("That account is not managed by SquirrelLauncher.");
    }

    public synchronized void remove(@NotNull MinecraftAccount account) throws IOException {
        if (!this.accounts.removeIf(savedAccount -> savedAccount.key().equals(account.key()))) return;
        if (account.key().equals(this.selectedAccountKey)) {
            this.selectedAccountKey = this.accounts.isEmpty() ? null : this.accounts.getFirst().key();
        }
        this.save();
    }

    @NotNull
    public MinecraftAccount prepareSelectedAccount() throws Exception {
        MinecraftAccount account = this.selectedAccount();
        if (account == null) throw new IllegalStateException("Add or select an account before launching Minecraft.");
        if (account.type() == MinecraftAccount.AccountType.OFFLINE) return account;

        MinecraftAccount refreshed = this.microsoftAuthenticator.refresh(account);
        synchronized (this) {
            this.upsert(refreshed);
        }
        return refreshed;
    }

    private void upsert(@NotNull MinecraftAccount account) throws IOException {
        int existingIndex = -1;
        for (int index = 0; index < this.accounts.size(); index++) {
            if (this.accounts.get(index).key().equals(account.key())) {
                existingIndex = index;
                break;
            }
        }
        if (existingIndex < 0) this.accounts.add(account);
        else this.accounts.set(existingIndex, account);
        this.selectedAccountKey = account.key();
        this.save();
    }

    private void save() throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("formatVersion", FORMAT_VERSION);
        if (this.selectedAccountKey == null) root.add("selectedAccount", null);
        else root.addProperty("selectedAccount", this.selectedAccountKey);
        root.add("accounts", AccountManager.GSON.toJsonTree(this.accounts));

        Path accountsFile = MinecraftPaths.ACCOUNTS;
        Files.createDirectories(accountsFile.getParent());
        Path temporaryFile = accountsFile.resolveSibling(accountsFile.getFileName() + ".tmp");
        Files.writeString(temporaryFile, AccountManager.GSON.toJson(root));
        try {
            Files.setPosixFilePermissions(temporaryFile, EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE
            ));
        }
        catch (UnsupportedOperationException ignored) {}

        try {
            Files.move(
                    temporaryFile,
                    accountsFile,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
            );
        }
        catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporaryFile, accountsFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
