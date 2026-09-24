package com.anightdazingzoroark.squirrellauncher.minecraft.auth;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

public record MinecraftAccount(
        @NotNull String username,
        @NotNull String uuid,
        @NotNull String accessToken,
        @Nullable String refreshToken,
        @Nullable String skinUrl,
        @NotNull AccountType type
) {
    public enum AccountType {
        OFFLINE,
        MICROSOFT
    }

    @NotNull
    public static MinecraftAccount offline(@NotNull String username) {
        UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));
        return new MinecraftAccount(
                username,
                uuid.toString().replace("-", ""),
                "0",
                null,
                null,
                AccountType.OFFLINE
        );
    }

    @NotNull
    public String userType() {
        return this.type == AccountType.MICROSOFT ? "msa" : "legacy";
    }

    @NotNull
    public String key() {
        return this.type.name().toLowerCase(Locale.ROOT) + ":" + this.uuid;
    }
}
