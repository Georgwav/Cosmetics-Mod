package io.github.georgwav.cosmetics.sol.styles;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Sol's own 3D models for a part (B.2.4): Java item models shipped in the mod (assets/cosmeticsmod/styles/models,
 * made by tools/sol-assets/make_style_models.py from the Blockbench sources in src/styles/models), listed per part in
 * their index.json. The Model shape puts one in place of the part's item models, so the game draws it itself, in
 * the hand, the hotbar, on the ground and in frames.
 */
public final class StyleModels {
    private static Map<String, List<String>> index;

    private StyleModels() {
    }

    private static synchronized Map<String, List<String>> index() {
        if (index == null) {
            Map<String, List<String>> out = new TreeMap<>();
            try (InputStream in = StyleModels.class.getResourceAsStream("/assets/cosmeticsmod/styles/models/index.json")) {
                if (in != null) {
                    JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                    for (Map.Entry<String, JsonElement> e : o.entrySet()) {
                        List<String> ids = new ArrayList<>();
                        e.getValue().getAsJsonArray().forEach(v -> ids.add(v.getAsString()));
                        out.put(e.getKey(), List.copyOf(ids));
                    }
                }
            } catch (java.io.IOException | RuntimeException e) {
                // No models: the Model shape is greyed out.
            }
            index = out;
        }
        return index;
    }

    /** The models offered for a part. */
    public static List<String> of(String part) {
        return index().getOrDefault(part, List.of());
    }

    public static boolean has(String part) {
        return !of(part).isEmpty();
    }

    /** Whether a model is one of a part's. */
    public static boolean known(String part, String model) {
        return of(part).contains(model);
    }

    /** The first model for a part, or null. */
    public static String first(String part) {
        List<String> l = of(part);
        return l.isEmpty() ? null : l.get(0);
    }
}
