package com.anightdazingzoroark.squirrellauncher.launcher;

import com.anightdazingzoroark.squirrellauncher.minecraft.MinecraftPaths;
import com.anightdazingzoroark.squirrellauncher.minecraft.instance.MinecraftInstance;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Persists the user-defined order of instances independently of MMC instance archives. */
public final class InstanceOrderManager {
    private static final int FORMAT_VERSION = 1;
    @NotNull
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @NotNull
    public synchronized List<MinecraftInstance> apply(@NotNull List<MinecraftInstance> instances) throws IOException {
        List<String> order = this.readOrder();
        Map<String, Integer> positions = new HashMap<>();
        for (int index = 0; index < order.size(); index++) positions.putIfAbsent(order.get(index), index);

        List<MinecraftInstance> result = new ArrayList<>(instances);
        result.sort(Comparator
                .comparingInt((MinecraftInstance instance) -> positions.getOrDefault(instance.id(), Integer.MAX_VALUE))
                .thenComparing(MinecraftInstance::name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(MinecraftInstance::id));
        return List.copyOf(result);
    }

    public synchronized void moveToTop(@NotNull String instanceId) throws IOException {
        List<String> order = this.readOrder();
        order.remove(instanceId);
        order.addFirst(instanceId);
        this.writeOrder(order);
    }

    public synchronized void replace(@NotNull String oldInstanceId, @NotNull String newInstanceId) throws IOException {
        List<String> order = this.readOrder();
        int index = order.indexOf(oldInstanceId);
        order.remove(oldInstanceId);
        order.remove(newInstanceId);
        if (index >= 0) order.add(Math.min(index, order.size()), newInstanceId);
        this.writeOrder(order);
    }

    public synchronized void remove(@NotNull String instanceId) throws IOException {
        List<String> order = this.readOrder();
        if (order.remove(instanceId)) this.writeOrder(order);
    }

    public synchronized void save(@NotNull List<String> instanceIds) throws IOException {
        this.writeOrder(new ArrayList<>(instanceIds));
    }

    @NotNull
    private List<String> readOrder() throws IOException {
        Path orderFile = MinecraftPaths.INSTANCE_ORDER;
        if (!Files.isRegularFile(orderFile)) return new ArrayList<>();

        JsonObject root = JsonParser.parseString(Files.readString(orderFile)).getAsJsonObject();
        int formatVersion = root.has("formatVersion") ? root.get("formatVersion").getAsInt() : 0;
        if (formatVersion != FORMAT_VERSION) {
            throw new IOException("Unsupported instance order format: " + formatVersion);
        }
        List<String> result = new ArrayList<>();
        if (!root.has("instances") || !root.get("instances").isJsonArray()) return result;
        JsonArray instances = root.getAsJsonArray("instances");
        for (JsonElement element : instances) {
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                String instanceId = element.getAsString();
                if (!instanceId.isBlank() && !result.contains(instanceId)) result.add(instanceId);
            }
        }
        return result;
    }

    private void writeOrder(@NotNull List<String> instanceIds) throws IOException {
        JsonArray instances = new JsonArray();
        for (String instanceId : instanceIds) instances.add(instanceId);
        JsonObject root = new JsonObject();
        root.addProperty("formatVersion", FORMAT_VERSION);
        root.add("instances", instances);

        Path orderFile = MinecraftPaths.INSTANCE_ORDER;
        Files.createDirectories(orderFile.getParent());
        Path temporary = orderFile.resolveSibling(orderFile.getFileName() + ".tmp");
        Files.writeString(temporary, GSON.toJson(root));
        try {
            Files.move(temporary, orderFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        }
        catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, orderFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
