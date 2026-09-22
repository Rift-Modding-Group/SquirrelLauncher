package com.anightdazingzoroark.squirrellauncher.minecraft.auth;

import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public record MinecraftAccount(
        String username, String uuid,
        String accessToken, String refreshToken, AccountType type
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
                AccountType.OFFLINE
        );
    }

    @NotNull
    public String userType() {
        return this.type == AccountType.MICROSOFT ? "msa" : "legacy";
    }
}