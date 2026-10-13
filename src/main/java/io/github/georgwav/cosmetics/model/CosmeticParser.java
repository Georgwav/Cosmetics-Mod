package io.github.georgwav.cosmetics.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Parses cosmetic JSON files. See {@code docs/COSMETIC_FORMAT.md} for the format.
 */
public final class CosmeticParser {
    private CosmeticParser() {
    }

    public static CosmeticDefinition parse(String id, Reader reader) {
        JsonElement root = JsonParser.parseReader(reader);
        if (!root.isJsonObject()) {
            throw new JsonParseException("Cosmetic " + id + " must be a JSON object");
        }
        return parse(id, root.getAsJsonObject());
    }

    public static CosmeticDefinition parse(String id, JsonObject json) {
        String namespace = id.substring(0, id.indexOf(':'));
        CosmeticSlot slot = CosmeticSlot.byId(requireString(json, "slot"));
        String texture = qualify(namespace, requireString(json, "texture"));
        int[] textureSize = {64, 64};
        if (json.has("texture_size")) {
            float[] size = floats(json.get("texture_size"), 2, "texture_size");
            textureSize = new int[]{(int) size[0], (int) size[1]};
            if (textureSize[0] <= 0 || textureSize[1] <= 0) {
                throw new JsonParseException("texture_size must be positive");
            }
        }

        // A cosmetic-level animation applies to every part that doesn't set its own.
        List<CosmeticDefinition.Animation> defaultAnimations = json.has("animation")
                ? parseAnimations(json.get("animation"), "animation") : List.of();
        List<CosmeticDefinition.Part> parts = new ArrayList<>();
        JsonArray partsJson = requireArray(json, "parts");
        for (int i = 0; i < partsJson.size(); i++) {
            JsonObject partJson = partsJson.get(i).getAsJsonObject();
            String name = partJson.has("name") ? partJson.get("name").getAsString() : "part" + i;
            RenderMode mode = partJson.has("mode") ? RenderMode.byId(partJson.get("mode").getAsString()) : RenderMode.CUTOUT;
            String partTexture = partJson.has("texture") ? qualify(namespace, partJson.get("texture").getAsString()) : texture;
            List<CosmeticDefinition.Cube> cubes = new ArrayList<>();
            JsonArray cubesJson = requireArray(partJson, "cubes");
            for (int c = 0; c < cubesJson.size(); c++) {
                cubes.add(parseCube(cubesJson.get(c).getAsJsonObject(), name + "[" + c + "]"));
            }
            List<CosmeticDefinition.Animation> animations = partJson.has("animation")
                    ? parseAnimations(partJson.get("animation"), name + ".animation") : defaultAnimations;
            parts.add(new CosmeticDefinition.Part(name, mode, partTexture, List.copyOf(cubes), animations));
        }
        if (parts.isEmpty()) {
            throw new JsonParseException("Cosmetic " + id + " has no parts");
        }
        String name = json.has("name") ? json.get("name").getAsString() : prettify(id);
        return new CosmeticDefinition(id, name, slot, texture, textureSize[0], textureSize[1], List.copyOf(parts));
    }

    /** {@code cosmeticsmod:top_hat} becomes {@code Top Hat}. */
    static String prettify(String id) {
        String path = id.substring(id.indexOf(':') + 1);
        path = path.substring(path.lastIndexOf('/') + 1);
        StringBuilder out = new StringBuilder();
        for (String word : path.split("[_\\-]+")) {
            if (word.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.length() > 0 ? out.toString() : id;
    }

    /** One animation object, or an array of them applied in order. */
    private static List<CosmeticDefinition.Animation> parseAnimations(JsonElement element, String where) {
        List<CosmeticDefinition.Animation> result = new ArrayList<>();
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            for (int i = 0; i < array.size(); i++) {
                result.add(parseAnimation(array.get(i), where + "[" + i + "]"));
            }
        } else {
            result.add(parseAnimation(element, where));
        }
        return List.copyOf(result);
    }

    private static CosmeticDefinition.Animation parseAnimation(JsonElement element, String where) {
        if (!element.isJsonObject()) {
            throw new JsonParseException(where + " must be an object");
        }
        JsonObject json = element.getAsJsonObject();
        CosmeticDefinition.AnimationType type;
        try {
            type = CosmeticDefinition.AnimationType.valueOf(requireString(json, "type").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new JsonParseException(where + ": type must be spin, bob or swing");
        }
        String axisName = json.has("axis") ? json.get("axis").getAsString().toLowerCase(Locale.ROOT) : "y";
        int axis = switch (axisName) {
            case "x" -> 0;
            case "y" -> 1;
            case "z" -> 2;
            default -> throw new JsonParseException(where + ": axis must be x, y or z");
        };
        float[] pivot = json.has("pivot") ? floats(json.get("pivot"), 3, where + ".pivot") : new float[3];
        float defaultAmplitude = switch (type) {
            case SPIN -> 0f;
            case BOB -> 0.5f;
            case SWING -> 15f;
        };
        float amplitude = number(json, "amplitude", defaultAmplitude, where);
        float speed = number(json, "speed", type == CosmeticDefinition.AnimationType.SPIN ? 0.25f : 0.5f, where);
        float phase = number(json, "phase", 0f, where);
        return new CosmeticDefinition.Animation(type, axis, pivot[0], pivot[1], pivot[2], amplitude, speed, phase);
    }

    private static float number(JsonObject json, String key, float fallback, String where) {
        if (!json.has(key)) {
            return fallback;
        }
        float value = json.get(key).getAsFloat();
        if (!Float.isFinite(value)) {
            throw new JsonParseException(where + "." + key + " must be a finite number");
        }
        return value;
    }

    private static CosmeticDefinition.Cube parseCube(JsonObject json, String where) {
        float[] from = floats(json.get("from"), 3, where + ".from");
        float[] to = floats(json.get("to"), 3, where + ".to");
        for (int axis = 0; axis < 3; axis++) {
            if (to[axis] < from[axis]) {
                throw new JsonParseException(where + ": 'to' must be >= 'from' on every axis");
            }
        }
        float inflate = json.has("inflate") ? json.get("inflate").getAsFloat() : 0f;
        boolean mirror = json.has("mirror") && json.get("mirror").getAsBoolean();
        float[] pivot = json.has("pivot") ? floats(json.get("pivot"), 3, where + ".pivot") : new float[3];
        float[] rotation = json.has("rotation") ? floats(json.get("rotation"), 3, where + ".rotation") : new float[3];

        float[] boxUv = null;
        float[][] faces = null;
        if (json.has("uv")) {
            boxUv = floats(json.get("uv"), 2, where + ".uv");
        } else if (json.has("faces")) {
            faces = new float[6][];
            JsonObject facesJson = json.getAsJsonObject("faces");
            for (String key : facesJson.keySet()) {
                CosmeticDefinition.Face face;
                try {
                    face = CosmeticDefinition.Face.valueOf(key.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    throw new JsonParseException(where + ": unknown face '" + key + "'");
                }
                faces[face.ordinal()] = floats(facesJson.get(key), 4, where + ".faces." + key);
            }
        } else {
            throw new JsonParseException(where + ": needs either 'uv' (box UV) or 'faces' (per-face UV)");
        }
        return new CosmeticDefinition.Cube(from, to, inflate, mirror, boxUv, faces, pivot, rotation);
    }

    private static String qualify(String namespace, String location) {
        String qualified = location.indexOf(':') < 0 ? namespace + ":" + location : location;
        // Same character rules as vanilla resource locations, checked here so a typo fails at load time
        // with a clear message instead of on the render thread.
        if (!qualified.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            throw new JsonParseException("Invalid texture location '" + location + "'");
        }
        return qualified;
    }

    private static float[] floats(JsonElement element, int count, String where) {
        if (element == null || !element.isJsonArray() || element.getAsJsonArray().size() != count) {
            throw new JsonParseException(where + " must be an array of " + count + " numbers");
        }
        JsonArray array = element.getAsJsonArray();
        float[] result = new float[count];
        for (int i = 0; i < count; i++) {
            result[i] = array.get(i).getAsFloat();
            if (!Float.isFinite(result[i])) {
                throw new JsonParseException(where + " contains a non-finite number");
            }
        }
        return result;
    }

    private static String requireString(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive()) {
            throw new JsonParseException("Missing string '" + key + "'");
        }
        return json.get(key).getAsString();
    }

    private static JsonArray requireArray(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonArray()) {
            throw new JsonParseException("Missing array '" + key + "'");
        }
        return json.getAsJsonArray(key);
    }
}
