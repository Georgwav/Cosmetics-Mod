package io.github.georgwav.cosmetics.sol.styles;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Every file a pack's model needs (B.2.4): its parent chain, the textures it and its parents name (with their
 * animation .mcmeta), and from 1.21.4 the item definition's models. Only files the pack itself has come along; the
 * rest fall through to the packs under it, and a texture no pack would have is listed as missing. Plain Java, so it is
 * unit-tested.
 */
public final class ModelDeps {
    /** What a model needs from its pack, and the textures it names that the pack doesn't have. */
    public record Needs(Set<String> files, Set<String> missing) {
    }

    private ModelDeps() {
    }

    /**
     * Walks from {@code start} (a model or item definition path under assets/minecraft/). {@code read} gives a pack
     * file's text, or null when the pack hasn't it; {@code exists} says whether anything (the pack or the game) has a
     * file.
     */
    public static Needs walk(String start, Function<String, String> read, Function<String, Boolean> exists) {
        Set<String> files = new LinkedHashSet<>(), missing = new LinkedHashSet<>(), seen = new LinkedHashSet<>();
        Deque<String> todo = new ArrayDeque<>();
        todo.add(start);
        while (!todo.isEmpty() && seen.size() < 256) {
            String path = todo.poll();
            if (!seen.add(path)) {
                continue;
            }
            String text = read.apply(path);
            if (text == null) {
                continue;
            }
            files.add(path);
            JsonObject o;
            try {
                JsonElement e = JsonParser.parseString(text);
                if (!e.isJsonObject()) {
                    continue;
                }
                o = e.getAsJsonObject();
            } catch (RuntimeException e) {
                continue;
            }
            if (path.startsWith("items/")) {
                // An item definition: every "model" it names, however deep.
                collectModels(o, todo);
                continue;
            }
            if (o.has("parent") && o.get("parent").isJsonPrimitive()) {
                String parent = o.get("parent").getAsString();
                String p = modelPath(parent);
                if (p != null) {
                    todo.add(p);
                }
            }
            if (o.has("textures") && o.get("textures").isJsonObject()) {
                for (Map.Entry<String, JsonElement> t : o.getAsJsonObject("textures").entrySet()) {
                    if (!t.getValue().isJsonPrimitive()) {
                        continue;
                    }
                    String tex = texturePath(t.getValue().getAsString());
                    if (tex == null) {
                        continue;
                    }
                    if (read.apply(tex) != null) {
                        files.add(tex);
                        if (read.apply(tex + ".mcmeta") != null) {
                            files.add(tex + ".mcmeta");
                        }
                    } else if (!Boolean.TRUE.equals(exists.apply(tex))) {
                        missing.add(tex);
                    }
                }
            }
        }
        return new Needs(files, missing);
    }

    private static void collectModels(JsonElement e, Deque<String> todo) {
        if (e.isJsonObject()) {
            for (Map.Entry<String, JsonElement> m : e.getAsJsonObject().entrySet()) {
                if (m.getKey().equals("model") && m.getValue().isJsonPrimitive()) {
                    String p = modelPath(m.getValue().getAsString());
                    if (p != null) {
                        todo.add(p);
                    }
                } else {
                    collectModels(m.getValue(), todo);
                }
            }
        } else if (e.isJsonArray()) {
            for (JsonElement x : e.getAsJsonArray()) {
                collectModels(x, todo);
            }
        }
    }

    /** "minecraft:item/x" or "item/x" to "models/item/x.json"; null for builtins and other namespaces. */
    static String modelPath(String id) {
        String p = local(id);
        if (p == null || p.startsWith("builtin/")) {
            return null;
        }
        return "models/" + p + ".json";
    }

    /** "minecraft:item/x" to "textures/item/x.png"; null for a variable ("#layer0") or another namespace. */
    static String texturePath(String id) {
        if (id.startsWith("#")) {
            return null;
        }
        String p = local(id);
        return p == null ? null : "textures/" + p + ".png";
    }

    private static String local(String id) {
        int colon = id.indexOf(':');
        if (colon >= 0) {
            if (!id.substring(0, colon).equals("minecraft")) {
                return null;
            }
            id = id.substring(colon + 1);
        }
        return id.isEmpty() ? null : id;
    }
}
