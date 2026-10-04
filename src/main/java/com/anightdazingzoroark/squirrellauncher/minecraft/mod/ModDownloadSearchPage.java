package com.anightdazingzoroark.squirrellauncher.minecraft.mod;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public record ModDownloadSearchPage(
        @NotNull List<ModDownloadProject> projects,
        int nextOffset,
        boolean hasMore
) {}
