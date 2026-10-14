package io.github.georgwav.cosmetics.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads a Bedrock geometry file ({@code .geo.json}, what Blockbench exports for "Bedrock Entity" models)
 * and turns its bones into cosmetic parts.
 *
 * <p>Bedrock models are measured from the player's feet with +Y up; ours are measured from the anchor
 * pivot (the neck, 24 px up) with +Y down, like vanilla Java entity models. The conversion is the one
 * Blockbench itself does between its Bedrock and Java entity formats (its Bedrock codec mirrors X and
 * negates X/Y rotations on import, its Java entity export mirrors X and Y and negates X/Y rotations):
 * <ul>
 *   <li>a point {@code (x, y, z)} becomes {@code (x, 24 - y, z)} (so the vanilla Bedrock head
 *       {@code origin [-4, 24, -4], size 8} is the Java head {@code addBox(-4, -8, -4, 8, 8, 8)});</li>
 *   <li>rotations {@code [x, y, z]} stay as they are;</li>
 *   <li>per-face UVs: {@code up} goes on our top face (DOWN), {@code down} on our bottom face (UP),
 *       {@code east} on WEST and {@code west} on EAST, each with the same UV rectangle.</li>
 * </ul>
 *
 * <p>Pure Java with no Minecraft types, so it is unit-tested without a game instance.
 */
public final class BedrockGeometry {
    /** Height of the anchor pivot (the neck) above the feet, in pixels. */
    static final float PIVOT_HEIGHT = 24f;

    private BedrockGeometry() {
    }

    /** What the cosmetic file says about the model's bones. */
    public record BoneOptions(Map<String, RenderMode> modes, Map<String, List<CosmeticDefinition.Animation>> animations,
                              Map<String, String> textures) {
    }

    /**
     * A converted model: its texture size, one part per bone with cubes (each part's {@code bone} is its bone's
     * index in {@code bones}), and every bone, parents before children.
     */
    public record Model(int textureWidth, int textureHeight, List<CosmeticDefinition.Part> parts,
                        List<BoneRig.Bone> bones) {
    }

    /**
     * @param geometryJson     the parsed {@code .geo.json}
     * @param texture          default texture for the parts
     * @param defaultAnimations movement for bones without their own
     */
    public static Model convert(JsonObject geometryJson, String where, String texture, BoneOptions options,
                                List<CosmeticDefinition.Animation> defaultAnimations) {
        return convert(geometryJson, where, texture, options, defaultAnimations, null);
    }

    /**
     * @param root placement of the whole model around the anchor's pivot ({@code [m00..m22, tx, ty, tz]}, pixels),
     *             or null to keep it where it was modelled
     */
    public static Model convert(JsonObject geometryJson, String where, String texture, BoneOptions options,
                                List<CosmeticDefinition.Animation> defaultAnimations, float[] root) {
        JsonObject geometry = firstGeometry(geometryJson, where);
        JsonObject description = geometry.has("description") ? geometry.getAsJsonObject("description") : new JsonObject();
        int textureWidth = description.has("texture_width") ? description.get("texture_width").getAsInt() : 64;
        int textureHeight = description.has("texture_height") ? description.get("texture_height").getAsInt() : 64;
        if (textureWidth <= 0 || textureHeight <= 0) {
            throw new JsonParseException(where + ": texture_width and texture_height must be positive");
        }

        JsonArray bonesJson = geometry.has("bones") ? geometry.getAsJsonArray("bones") : new JsonArray();
        Map<String, JsonObject> bones = new HashMap<>();
        for (JsonElement element : bonesJson) {
            JsonObject bone = element.getAsJsonObject();
            bones.put(key(bone), bone);
        }
        Map<JsonObject, Integer> order = new IdentityHashMap<>();
        List<BoneRig.Bone> rigBones = orderBones(bonesJson, bones, order, where);

        List<CosmeticDefinition.Part> parts = new ArrayList<>();
        for (JsonElement element : bonesJson) {
            JsonObject bone = element.getAsJsonObject();
            if (!bone.has("cubes") || bone.getAsJsonArray("cubes").isEmpty()) {
                continue;
            }
            String name = bone.has("name") ? bone.get("name").getAsString() : "bone";
            String key = key(bone);
            float[] boneTransform = chain(bone, bones, where, 0);
            if (root != null) {
                boneTransform = CosmeticBaker.compose(root, boneTransform);
            }
            boolean boneMirror = bone.has("mirror") && bone.get("mirror").getAsBoolean();
            float boneInflate = bone.has("inflate") ? bone.get("inflate").getAsFloat() : 0f;

            List<CosmeticDefinition.Cube> cubes = new ArrayList<>();
            JsonArray cubesJson = bone.getAsJsonArray("cubes");
            for (int i = 0; i < cubesJson.size(); i++) {
                cubes.add(cube(cubesJson.get(i).getAsJsonObject(), boneTransform, boneMirror, boneInflate,
                        where + " bone '" + name + "' cube " + i));
            }
            RenderMode mode = options.modes().getOrDefault(key, RenderMode.CUTOUT);
            String partTexture = options.textures().getOrDefault(key, texture);
            List<CosmeticDefinition.Animation> animations = options.animations().containsKey(key)
                    ? withBonePivot(options.animations().get(key), bone)
                    : defaultAnimations;
            parts.add(new CosmeticDefinition.Part(name, mode, partTexture, List.copyOf(cubes), animations)
                    .withBone(order.get(bone)));
        }
        if (parts.isEmpty()) {
            throw new JsonParseException(where + " has no cubes");
        }
        return new Model(textureWidth, textureHeight, List.copyOf(parts), rigBones);
    }

    /** Every bone, parents before children, with pivots in our space; fills {@code order} with each one's index. */
    private static List<BoneRig.Bone> orderBones(JsonArray bonesJson, Map<String, JsonObject> byName,
                                                 Map<JsonObject, Integer> order, String where) {
        List<JsonObject> pending = new ArrayList<>();
        for (JsonElement element : bonesJson) {
            pending.add(element.getAsJsonObject());
        }
        List<BoneRig.Bone> result = new ArrayList<>();
        while (!pending.isEmpty()) {
            boolean progress = false;
            for (java.util.Iterator<JsonObject> it = pending.iterator(); it.hasNext(); ) {
                JsonObject bone = it.next();
                JsonObject parent = bone.has("parent") ? byName.get(bone.get("parent").getAsString().toLowerCase(Locale.ROOT)) : null;
                if (parent == bone) {
                    parent = null;
                }
                if (parent != null && !order.containsKey(parent)) {
                    continue;
                }
                String name = bone.has("name") ? bone.get("name").getAsString() : "bone";
                float[] pivot = bone.has("pivot") ? point(floats(bone.get("pivot"), 3, where + " bone '" + name + "'.pivot"))
                        : new float[3];
                float[] rotation = bone.has("rotation") ? floats(bone.get("rotation"), 3, where + " bone '" + name + "'.rotation")
                        : new float[3];
                order.put(bone, result.size());
                result.add(new BoneRig.Bone(name, parent == null ? -1 : order.get(parent), pivot, rotation));
                it.remove();
                progress = true;
            }
            if (!progress) {
                throw new JsonParseException(where + ": bone parents form a loop");
            }
        }
        return List.copyOf(result);
    }

    private static JsonObject firstGeometry(JsonObject json, String where) {
        JsonElement geometries = json.get("minecraft:geometry");
        if (geometries == null || !geometries.isJsonArray() || geometries.getAsJsonArray().isEmpty()) {
            throw new JsonParseException(where + " is not a Bedrock geometry file (no \"minecraft:geometry\"); "
                    + "export it from Blockbench as a Bedrock Entity model");
        }
        return geometries.getAsJsonArray().get(0).getAsJsonObject();
    }

    private static String key(JsonObject bone) {
        return bone.has("name") ? bone.get("name").getAsString().toLowerCase(Locale.ROOT) : "";
    }

    /** The bone's placement including all its parents, in our space. */
    private static float[] chain(JsonObject bone, Map<String, JsonObject> bones, String where, int depth) {
        if (depth > 64) {
            throw new JsonParseException(where + ": bone parents form a loop");
        }
        float[] own = rotation(bone, where);
        if (!bone.has("parent")) {
            return own;
        }
        JsonObject parent = bones.get(bone.get("parent").getAsString().toLowerCase(Locale.ROOT));
        return parent == null ? own : CosmeticBaker.compose(chain(parent, bones, where, depth + 1), own);
    }

    /** A bone's or cube's own rotation around its pivot, in our space; identity when it has none. */
    private static float[] rotation(JsonObject json, String where) {
        float[] rotation = json.has("rotation") ? floats(json.get("rotation"), 3, where + ".rotation") : new float[3];
        float[] pivot = json.has("pivot") ? point(floats(json.get("pivot"), 3, where + ".pivot")) : new float[3];
        return CosmeticBaker.affine(CosmeticBaker.rotationMatrix(rotation), pivot);
    }

    private static CosmeticDefinition.Cube cube(JsonObject json, float[] boneTransform, boolean boneMirror,
                                                float boneInflate, String where) {
        float[] origin = floats(json.get("origin"), 3, where + ".origin");
        float[] size = floats(json.get("size"), 3, where + ".size");
        for (int axis = 0; axis < 3; axis++) {
            // A cube dragged inside out in Blockbench has a negative size: it is the same box from the other corner.
            if (size[axis] < 0) {
                origin[axis] += size[axis];
                size[axis] = -size[axis];
            }
        }
        float[] from = {origin[0], PIVOT_HEIGHT - (origin[1] + size[1]), origin[2]};
        float[] to = {origin[0] + size[0], PIVOT_HEIGHT - origin[1], origin[2] + size[2]};
        float inflate = json.has("inflate") ? json.get("inflate").getAsFloat() : boneInflate;
        boolean mirror = json.has("mirror") ? json.get("mirror").getAsBoolean() : boneMirror;

        float[] boxUv = null;
        float[][] faces = null;
        JsonElement uv = json.get("uv");
        if (uv == null || uv.isJsonArray()) {
            boxUv = uv == null ? new float[2] : floats(uv, 2, where + ".uv");
        } else {
            faces = new float[6][];
            JsonObject facesJson = uv.getAsJsonObject();
            for (String name : facesJson.keySet()) {
                CosmeticDefinition.Face face = switch (name.toLowerCase(Locale.ROOT)) {
                    case "north" -> CosmeticDefinition.Face.NORTH;
                    case "south" -> CosmeticDefinition.Face.SOUTH;
                    // Bedrock's east/west name the opposite side from Java's (X is mirrored between them).
                    case "east" -> CosmeticDefinition.Face.WEST;
                    case "west" -> CosmeticDefinition.Face.EAST;
                    // +Y is flipped, so the top face is our DOWN face and the bottom our UP face.
                    case "up" -> CosmeticDefinition.Face.DOWN;
                    case "down" -> CosmeticDefinition.Face.UP;
                    default -> throw new JsonParseException(where + ": unknown face '" + name + "'");
                };
                JsonObject faceJson = facesJson.getAsJsonObject(name);
                float[] start = floats(faceJson.get("uv"), 2, where + ".uv." + name + ".uv");
                float[] extent = faceJson.has("uv_size") ? floats(faceJson.get("uv_size"), 2, where + ".uv." + name + ".uv_size")
                        : new float[]{size[face == CosmeticDefinition.Face.EAST || face == CosmeticDefinition.Face.WEST ? 2 : 0],
                                      size[face == CosmeticDefinition.Face.UP || face == CosmeticDefinition.Face.DOWN ? 2 : 1]};
                faces[face.ordinal()] = new float[]{start[0], start[1], start[0] + extent[0], start[1] + extent[1]};
            }
        }
        float[] transform = CosmeticBaker.compose(boneTransform, rotation(json, where));
        return new CosmeticDefinition.Cube(from, to, inflate, mirror, boxUv, faces, new float[3], new float[3], transform);
    }

    /** Bone animations turn around the bone's own pivot unless the cosmetic file gives one. */
    private static List<CosmeticDefinition.Animation> withBonePivot(List<CosmeticDefinition.Animation> animations, JsonObject bone) {
        if (!bone.has("pivot")) {
            return animations;
        }
        float[] pivot = point(floats(bone.get("pivot"), 3, "pivot"));
        List<CosmeticDefinition.Animation> result = new ArrayList<>();
        for (CosmeticDefinition.Animation a : animations) {
            boolean unset = a.pivotX() == 0f && a.pivotY() == 0f && a.pivotZ() == 0f;
            result.add(unset ? new CosmeticDefinition.Animation(a.type(), a.axis(), pivot[0], pivot[1], pivot[2],
                    a.amplitude(), a.speed(), a.phase()) : a);
        }
        return List.copyOf(result);
    }

    /** A Bedrock point (pixels from the feet, +Y up) in our space. */
    static float[] point(float[] bedrock) {
        return new float[]{bedrock[0], PIVOT_HEIGHT - bedrock[1], bedrock[2]};
    }

    private static float[] floats(JsonElement element, int count, String where) {
        if (element == null || !element.isJsonArray() || element.getAsJsonArray().size() != count) {
            throw new JsonParseException(where + " must be an array of " + count + " numbers");
        }
        float[] result = new float[count];
        for (int i = 0; i < count; i++) {
            result[i] = element.getAsJsonArray().get(i).getAsFloat();
            if (!Float.isFinite(result[i])) {
                throw new JsonParseException(where + " contains a non-finite number");
            }
        }
        return result;
    }
}
