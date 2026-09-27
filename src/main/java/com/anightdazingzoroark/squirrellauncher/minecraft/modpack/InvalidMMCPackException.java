package com.anightdazingzoroark.squirrellauncher.minecraft.modpack;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public final class InvalidMMCPackException extends IOException {
    public InvalidMMCPackException(@NotNull String message) {
        super(message);
    }

    public InvalidMMCPackException(@NotNull String message, @NotNull Throwable cause) {
        super(message, cause);
    }
}
