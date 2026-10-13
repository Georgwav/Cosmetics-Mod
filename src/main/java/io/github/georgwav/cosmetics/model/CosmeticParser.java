package io.github.georgwav.cosmetics.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Parses cosmetic JSON files. See {@code docs/COSMETIC_FORMAT.md} for the format.
 */
public final class CosmeticParser {
    private CosmeticParser() {
    }

    /** Opens a model file referenced by a cosmetic ({@code namespace:path}, without the file extension). */
    @FunctionalInterface
    public interface ModelSource {
        Reader open(String modelId) throws IOException;
    }

    /** Opens a mesh file referenced by a cosmetic, by its full location ({@code namespace:path/name.obj}). */
    @FunctionalInterface
    public interface FileSource {
        InputStream open(String location) throws IOException;
    }

    private static final ModelSource NO_MODELS = modelId -> {
        throw new IOException("model files are not available here");
    };
    static final FileSource NO_FILES = location -> {
        throw new IOException("mesh files are not available here");
    };

    /** Largest mesh files read, in bytes. */
    static final int MAX_OBJ_BYTES = 4 << 20;
    static final int MAX_BBMODEL_BYTES = 16 << 20;

    /**
     * Everything a cosmetic file may need besides itself.
     *
     * @param maxMeshTriangles the most mesh triangles (a quad is two) one cosmetic may have; more is an error
     */
    public record Context(ModelSource models, FileSource files, Consumer<String> warnings, int maxMeshTriangles) {
        public static final int DEFAULT_MAX_MESH_TRIANGLES = 20_000;

        static Context withModels(ModelSource models) {
            return new Context(models, NO_FILES, warning -> { }, DEFAULT_MAX_MESH_TRIANGLES);
        }
    }

    public static CosmeticDefinition parse(String id, Reader reader) {
        return parse(id, reader, NO_MODELS);
    }

    public static CosmeticDefinition parse(String id, Reader reader, ModelSource models) {
        return parse(id, reader, Context.withModels(models));
    }

    public static CosmeticDefinition parse(String id, Reader reader, Context context) {
        JsonElement root = JsonParser.parseReader(reader);
        if (!root.isJsonObject()) {
            throw new JsonParseException("Cosmetic " + id + " must be a JSON object");
        }
        return parse(id, root.getAsJsonObject(), context);
    }

    public static CosmeticDefinition parse(String id, JsonObject json) {
        return parse(id, json, Context.withModels(NO_MODELS));
    }

    public static CosmeticDefinition parse(String id, JsonObject json, ModelSource models) {
        return parse(id, json, Context.withModels(models));
    }

    public static CosmeticDefinition parse(String id, JsonObject json, Context context) {
        ModelSource models = context.models();
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
        if (json.has("model")) {
            BedrockGeometry.Model model = loadModel(json, namespace, texture, defaultAnimations, models);
            parts.addAll(model.parts());
            if (!json.has("texture_size")) {
                textureSize = new int[]{model.textureWidth(), model.textureHeight()};
            }
        }
        JsonArray partsJson = json.has("model") && !json.has("parts") ? new JsonArray() : requireArray(json, "parts");
        for (int i = 0; i < partsJson.size(); i++) {
            JsonObject partJson = partsJson.get(i).getAsJsonObject();
            String name = partJson.has("name") ? partJson.get("name").getAsString() : "part" + i;
            RenderMode mode = partJson.has("mode") ? RenderMode.byId(partJson.get("mode").getAsString()) : RenderMode.CUTOUT;
            String partTexture = partJson.has("texture") ? qualify(namespace, partJson.get("texture").getAsString()) : texture;
            if (!partJson.has("cubes") && !partJson.has("meshes")) {
                throw new JsonParseException(name + ": a part needs 'cubes', 'meshes' or both");
            }
            List<CosmeticDefinition.Cube> cubes = new ArrayList<>();
            if (partJson.has("cubes")) {
                JsonArray cubesJson = requireArray(partJson, "cubes");
                for (int c = 0; c < cubesJson.size(); c++) {
                    cubes.add(parseCube(cubesJson.get(c).getAsJsonObject(), name + "[" + c + "]"));
                }
            }
            List<CosmeticDefinition.Mesh> meshes = new ArrayList<>();
            if (partJson.has("meshes")) {
                JsonArray meshesJson = requireArray(partJson, "meshes");
                for (int m = 0; m < meshesJson.size(); m++) {
                    String where = id + " " + name + ".meshes[" + m + "]";
                    if (!meshesJson.get(m).isJsonObject()) {
                        throw new JsonParseException(where + " must be an object");
                    }
                    meshes.add(parseMesh(meshesJson.get(m).getAsJsonObject(), where, namespace,
                            textureSize[0], textureSize[1], context));
                }
            }
            List<CosmeticDefinition.Animation> animations = partJson.has("animation")
                    ? parseAnimations(partJson.get("animation"), name + ".animation") : defaultAnimations;
            parts.add(new CosmeticDefinition.Part(name, mode, partTexture, List.copyOf(cubes), animations, List.copyOf(meshes)));
        }
        if (parts.isEmpty()) {
            throw new JsonParseException("Cosmetic " + id + " has no parts");
        }
        int triangles = 0;
        for (CosmeticDefinition.Part part : parts) {
            for (CosmeticDefinition.Mesh mesh : part.meshes()) {
                triangles += mesh.triangles();
            }
        }
        if (triangles > context.maxMeshTriangles()) {
            throw new JsonParseException("Cosmetic " + id + " has " + triangles + " mesh triangles, more than the limit of "
                    + context.maxMeshTriangles() + " (maxMeshTriangles in config/cosmeticsmod.json)");
        }
        String name = json.has("name") ? json.get("name").getAsString() : prettify(id);
        return new CosmeticDefinition(id, name, slot, texture, textureSize[0], textureSize[1], List.copyOf(parts));
    }

    /** One entry of a part's {@code meshes}: inline geometry or a {@code .obj} / {@code .bbmodel} file. */
    private static CosmeticDefinition.Mesh parseMesh(JsonObject json, String where, String namespace,
                                                     int texW, int texH, Context context) {
        boolean smooth = false;
        if (json.has("shading")) {
            String shading = json.get("shading").getAsString();
            switch (shading) {
                case "flat" -> smooth = false;
                case "smooth" -> smooth = true;
                default -> throw new JsonParseException(where + ": shading must be 'flat' or 'smooth', not '" + shading + "'");
            }
        }
        float[] scale = {1, 1, 1};
        if (json.has("scale")) {
            JsonElement value = json.get("scale");
            scale = value.isJsonArray() ? floats(value, 3, where + ".scale") : new float[]{
                    value.getAsFloat(), value.getAsFloat(), value.getAsFloat()};
        }
        MeshBuilder.Placement placement = new MeshBuilder.Placement(
                json.has("mirror") && json.get("mirror").getAsBoolean(),
                scale,
                json.has("pivot") ? floats(json.get("pivot"), 3, where + ".pivot") : new float[3],
                json.has("rotation") ? floats(json.get("rotation"), 3, where + ".rotation") : new float[3],
                json.has("offset") ? floats(json.get("offset"), 3, where + ".offset") : new float[3]);

        MeshBuilder.Raw raw;
        String source;
        if (json.has("model")) {
            if (json.has("vertices") || json.has("faces") || json.has("uvs")) {
                throw new JsonParseException(where + ": use either 'model' or inline 'vertices'/'uvs'/'faces', not both");
            }
            String location = qualifyFile(namespace, json.get("model").getAsString(), where);
            if (location.endsWith(".obj")) {
                raw = ObjParser.parse(readText(context.files(), location, MAX_OBJ_BYTES), location, texW, texH);
            } else if (location.endsWith(".bbmodel")) {
                raw = BbmodelParser.parse(readText(context.files(), location, MAX_BBMODEL_BYTES), location, texW, texH,
                        context.warnings());
            } else {
                throw new JsonParseException(where + ": model must be a .obj or .bbmodel file");
            }
            source = location;
        } else {
            raw = inlineMesh(json, where);
            source = where;
        }
        return MeshBuilder.build(raw, placement, smooth, texW, texH, source, context.warnings());
    }

    /** {@code vertices}, {@code uvs} (texture pixels) and optional {@code normals} are parallel arrays. */
    private static MeshBuilder.Raw inlineMesh(JsonObject json, String where) {
        JsonArray vertices = requireArray(json, "vertices");
        JsonArray uvs = requireArray(json, "uvs");
        JsonArray faces = requireArray(json, "faces");
        JsonArray normals = json.has("normals") ? requireArray(json, "normals") : null;
        if (uvs.size() != vertices.size()) {
            throw new JsonParseException(where + ": 'uvs' needs one entry per vertex (" + vertices.size()
                    + " vertices, " + uvs.size() + " uvs)");
        }
        if (normals != null && normals.size() != vertices.size()) {
            throw new JsonParseException(where + ": 'normals' needs one entry per vertex (" + vertices.size()
                    + " vertices, " + normals.size() + " normals)");
        }
        MeshBuilder.Raw raw = new MeshBuilder.Raw();
        if (normals != null) {
            raw.normals = new ArrayList<>();
        }
        for (int i = 0; i < vertices.size(); i++) {
            raw.addCorner(floats(vertices.get(i), 3, where + ".vertices[" + i + "]"),
                    floats(uvs.get(i), 2, where + ".uvs[" + i + "]"),
                    normals != null ? floats(normals.get(i), 3, where + ".normals[" + i + "]") : null, i);
        }
        for (int f = 0; f < faces.size(); f++) {
            String faceWhere = where + ".faces[" + f + "]";
            JsonElement faceJson = faces.get(f);
            if (!faceJson.isJsonArray() || faceJson.getAsJsonArray().size() < 3 || faceJson.getAsJsonArray().size() > 4) {
                throw new JsonParseException(faceWhere + " must list 3 or 4 vertex indices");
            }
            JsonArray indices = faceJson.getAsJsonArray();
            int[] face = new int[indices.size()];
            for (int i = 0; i < face.length; i++) {
                double value = indices.get(i).getAsDouble();
                if (value != Math.rint(value)) {
                    throw new JsonParseException(faceWhere + ": vertex index " + value + " is not a whole number");
                }
                face[i] = (int) value;
            }
            raw.faces.add(face);
            raw.faceSources.add(faceWhere);
        }
        return raw;
    }

    private static String qualifyFile(String namespace, String location, String where) {
        String qualified = location.indexOf(':') < 0 ? namespace + ":" + location : location;
        if (!qualified.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            throw new JsonParseException(where + ": invalid file location '" + location + "'");
        }
        return qualified;
    }

    /** Reads a whole file as UTF-8, refusing anything over {@code limit} bytes. */
    private static String readText(FileSource files, String location, int limit) {
        try (InputStream in = files.open(location)) {
            byte[] data = in.readNBytes(limit + 1);
            if (data.length > limit) {
                throw new JsonParseException(location + " is larger than " + (limit >> 20) + " MiB");
            }
            return new String(data, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new JsonParseException("Can't read " + location + ": " + e.getMessage());
        }
    }

    /**
     * A Bedrock geometry model ({@code "model": "namespace:name"} is
     * {@code assets/namespace/cosmetic_models/name.geo.json}) plus per-bone settings from {@code "bones"}.
     */
    private static BedrockGeometry.Model loadModel(JsonObject json, String namespace, String texture,
                                                   List<CosmeticDefinition.Animation> defaultAnimations,
                                                   ModelSource models) {
        String modelId = json.get("model").getAsString();
        if (modelId.indexOf(':') < 0) {
            modelId = namespace + ":" + modelId;
        }
        JsonElement geometry;
        try (Reader reader = models.open(modelId)) {
            geometry = JsonParser.parseReader(reader);
        } catch (IOException e) {
            throw new JsonParseException("Can't read model '" + modelId + "': " + e.getMessage());
        }
        if (!geometry.isJsonObject()) {
            throw new JsonParseException("Model '" + modelId + "' must be a JSON object");
        }

        Map<String, RenderMode> modes = new HashMap<>();
        Map<String, List<CosmeticDefinition.Animation>> animations = new HashMap<>();
        Map<String, String> textures = new HashMap<>();
        if (json.has("bones")) {
            JsonObject bones = json.getAsJsonObject("bones");
            for (String bone : bones.keySet()) {
                JsonObject settings = bones.getAsJsonObject(bone);
                String key = bone.toLowerCase(Locale.ROOT);
                if (settings.has("mode")) {
                    modes.put(key, RenderMode.byId(settings.get("mode").getAsString()));
                }
                if (settings.has("texture")) {
                    textures.put(key, qualify(namespace, settings.get("texture").getAsString()));
                }
                if (settings.has("animation")) {
                    animations.put(key, parseAnimations(settings.get("animation"), "bones." + bone + ".animation"));
                }
            }
        }
        return BedrockGeometry.convert(geometry.getAsJsonObject(), "model '" + modelId + "'", texture,
                new BedrockGeometry.BoneOptions(modes, animations, textures), defaultAnimations);
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
