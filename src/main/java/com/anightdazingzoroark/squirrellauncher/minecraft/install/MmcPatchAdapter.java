package com.anightdazingzoroark.squirrellauncher.minecraft.install;

import com.anightdazingzoroark.squirrellauncher.minecraft.launch.LaunchComponent;
import com.anightdazingzoroark.squirrellauncher.minecraft.launch.MavenUtil;
import com.google.gson.*;

import java.util.*;

public final class MmcPatchAdapter {
    private MmcPatchAdapter() {}

    public static LaunchComponent convert(String id, JsonObject patch) {
        //metadata that our normal resolver already knows how to consume
        JsonObject metadata = new JsonObject();
        if (patch.has("mainClass")) {
            metadata.add("mainClass", patch.get("mainClass"));
        }
        if (patch.has("minecraftArguments")) {
            metadata.add("minecraftArguments", patch.get("minecraftArguments"));
        }

        //combine both ordinary and additive MMC library declarations
        JsonArray libraries = new JsonArray();
        appendArray(libraries, patch, "libraries");
        appendArray(libraries, patch, "+libraries");
        if (!libraries.isEmpty()) metadata.add("libraries", libraries);

        //library removals
        Set<String> removeLibraries = new LinkedHashSet<>();
        if (patch.has("-libraries")) {
            for (JsonElement element : patch.getAsJsonArray("-libraries")) {
                String coordinate;
                if (element.isJsonPrimitive()) coordinate = element.getAsString();
                else coordinate = element.getAsJsonObject().get("name").getAsString();

                removeLibraries.add(MavenUtil.artifactKey(coordinate));
            }
        }

        //JVM additions
        List<String> jvmArguments = readStrings(patch, "+jvmArgs");

         //MMC expresses legacy tweak classes separately.
         //Translate them into normal Minecraft game args.
        List<String> gameArguments =
                new ArrayList<>();

        for (String tweaker : readStrings(patch, "+tweakers")) {
            gameArguments.add("--tweakClass");
            gameArguments.add(tweaker);
        }

        return new LaunchComponent(
                id, metadata, true,
                removeLibraries,Map.of(),
                jvmArguments, gameArguments,
                null, null
        );
    }

    private static void appendArray(JsonArray result, JsonObject source, String name) {
        if (!source.has(name)) return;
        for (JsonElement element : source.getAsJsonArray(name)) result.add(element.deepCopy());
    }

    private static List<String> readStrings(JsonObject object, String name) {
        if (!object.has(name)) return List.of();

        List<String> result = new ArrayList<>();
        for (JsonElement element : object.getAsJsonArray(name)) {
            result.add(element.getAsString());
        }

        return result;
    }
}