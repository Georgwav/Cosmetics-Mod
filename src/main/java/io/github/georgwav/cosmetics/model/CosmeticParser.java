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
    static final int MAX_OBJ_BYTES = 16 << 20;
    static final int MAX_BBMODEL_BYTES = 16 << 20;
    static final int MAX_MD5MESH_BYTES = 8 << 20;
    static final int MAX_MD5ANIM_BYTES = 16 << 20;

    /**
     * Everything a cosmetic file may need besides itself.
     *
     * @param maxMeshTriangles the most mesh triangles (a quad is two) one cosmetic may have; more is an error
     */
    public record Context(ModelSource models, FileSource files, Consumer<String> warnings, int maxMeshTriangles) {
        public static final int DEFAULT_MAX_MESH_TRIANGLES = 100_000;

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
        // A knife is drawn from its melee block by the knife renderer: no parts of its own.
        CosmeticDefinition.Melee melee = null;
        if (slot == CosmeticSlot.MELEE) {
            if (!json.has("melee") || !json.get("melee").isJsonObject()) {
                throw new JsonParseException("A melee cosmetic needs a \"melee\" object");
            }
            melee = parseMelee(json.getAsJsonObject("melee"), namespace);
        } else if (json.has("melee")) {
            throw new JsonParseException("\"melee\" is only for the melee slot");
        }
        // Particles alone (an aura) need no parts and no texture of their own.
        boolean particlesOnly = json.has("particles") && !json.has("parts") && !json.has("model");
        String texture = particlesOnly && !json.has("texture") ? "" : qualify(namespace, requireString(json, "texture"));
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
        // A cosmetic-level texture animation applies to every part drawn with the cosmetic's own texture.
        CosmeticDefinition.TextureAnimation textureAnimation = json.has("texture_animation")
                ? parseTextureAnimation(json.get("texture_animation"), namespace, "texture_animation") : null;
        List<CosmeticDefinition.Part> parts = new ArrayList<>();
        BoneRig boneRig = null;
        if (json.has("model")) {
            float[] placement = json.has("model_placement") ? modelPlacement(json.get("model_placement")) : null;
            BedrockGeometry.Model model = loadModel(json, namespace, texture, defaultAnimations, models, placement,
                    context.warnings());
            boneRig = boneRig(json, namespace, model, models, context.warnings(), placement);
            for (CosmeticDefinition.Part part : model.parts()) {
                // Parts that move share a layer per moving bone; without bone animations the model is one piece.
                CosmeticDefinition.Part placed = part.withBone(boneRig != null ? boneRig.driver(part.bone()) : -1);
                parts.add(textureAnimation != null && placed.texture().equals(texture)
                        ? placed.withTextureAnimation(textureAnimation) : placed);
            }
            if (!json.has("texture_size")) {
                textureSize = new int[]{model.textureWidth(), model.textureHeight()};
            }
        }
        JsonArray partsJson = (json.has("model") || particlesOnly || melee != null) && !json.has("parts") ? new JsonArray()
                : requireArray(json, "parts");
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
            CosmeticDefinition.TextureAnimation partTextureAnimation;
            if (partJson.has("texture_animation")) {
                partTextureAnimation = parseTextureAnimation(partJson.get("texture_animation"), namespace,
                        name + ".texture_animation");
            } else {
                partTextureAnimation = partTexture.equals(texture) ? textureAnimation : null;
            }
            boolean tinted = !partJson.has("tint") || bool(partJson.get("tint"), name + ".tint");
            CosmeticDefinition.WingPart wing = null;
            if (partJson.has("wing")) {
                int side = switch (partJson.get("wing").getAsString()) {
                    case "left" -> 1;
                    case "right" -> -1;
                    default -> throw new JsonParseException(name + ".wing must be 'left' or 'right'");
                };
                float[] pivot = partJson.has("wing_pivot") ? floats(partJson.get("wing_pivot"), 3, name + ".wing_pivot")
                        : new float[3];
                wing = new CosmeticDefinition.WingPart(side, pivot[0], pivot[1], pivot[2]);
            } else if (partJson.has("wing_pivot")) {
                throw new JsonParseException(name + ": 'wing_pivot' needs 'wing'");
            }
            parts.add(new CosmeticDefinition.Part(name, mode, partTexture, List.copyOf(cubes), animations,
                    List.copyOf(meshes), partTextureAnimation, tinted, wing));
        }
        List<CosmeticDefinition.ParticleEmitter> particles = json.has("particles")
                ? parseParticles(json.get("particles"), namespace) : List.of();
        if (parts.isEmpty() && particles.isEmpty() && melee == null) {
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
        // A knife has none of the model options (colour, size, place): its pack draws it as it is.
        // A knife takes colour (its texture recoloured), not size or place (its pack's animation places it).
        int options = json.has("options") ? parseOptions(json.get("options"))
                : melee != null ? CosmeticOption.TINT.bit() | CosmeticOption.RAINBOW.bit() | CosmeticOption.SATURATION.bit()
                : CosmeticOption.ALL;
        int defaultColor = json.has("default_color") ? color(json.get("default_color"), "default_color")
                : CosmeticDefinition.WHITE;
        String defaultStyle = null;
        if (json.has("default_style")) {
            defaultStyle = json.get("default_style").getAsString();
            if (!styles(parts).contains(defaultStyle)) {
                throw new JsonParseException("default_style '" + defaultStyle + "' is not one of the cosmetic's styles "
                        + styles(parts));
            }
        }
        CosmeticDefinition.Wings wings = json.has("wings") ? parseWings(json.get("wings")) : null;
        int sides = slot.defaultSides();
        if (json.has("sides")) {
            sides = switch (json.get("sides").getAsString()) {
                case "left" -> CosmeticDefinition.LEFT;
                case "right" -> CosmeticDefinition.RIGHT;
                case "both" -> CosmeticDefinition.LEFT | CosmeticDefinition.RIGHT;
                default -> throw new JsonParseException("sides must be 'left', 'right' or 'both'");
            };
        }
        // A prop is only shown while an emote that brings it plays; the Wardrobe doesn't list it.
        boolean prop = json.has("prop") && bool(json.get("prop"), "prop");
        PetPosition petPosition = PetPosition.LEFT_SHOULDER;
        if (json.has("pet_position")) {
            petPosition = PetPosition.byName(json.get("pet_position").getAsString());
            if (petPosition == null) {
                throw new JsonParseException("pet_position must be left_shoulder, right_shoulder, head or floating");
            }
        }
        // What it counts as when you wear one at a time ("hat", "ears", ...); by default its slot, or "wings".
        String kind = null;
        if (json.has("kind")) {
            kind = json.get("kind").getAsString().trim().toLowerCase(java.util.Locale.ROOT);
            if (!kind.matches("[a-z0-9_]{1,32}")) {
                throw new JsonParseException("kind must be 1 to 32 letters, digits or _");
            }
        }
        // A held cosmetic (a shield) made to face the other way round in first person than in third.
        boolean firstPersonTurned = json.has("first_person_turned")
                && bool(json.get("first_person_turned"), "first_person_turned");
        String icon = json.has("icon") ? qualify(namespace, json.get("icon").getAsString()) : null;
        String group = json.has("group") ? json.get("group").getAsString().strip() : null;
        if (group != null && group.isEmpty()) {
            group = null;
        }
        return new CosmeticDefinition(id, name, slot, texture, textureSize[0], textureSize[1], List.copyOf(parts),
                options, defaultColor, defaultStyle, wings, particles, sides, prop, petPosition, kind, boneRig,
                firstPersonTurned, icon, group, melee);
    }

    /** A knife's {@code melee} block. */
    private static CosmeticDefinition.Melee parseMelee(JsonObject json, String namespace) {
        String model = qualifyFile(namespace, requireString(json, "model"), "melee.model");
        String animations = qualifyFile(namespace, requireString(json, "animations"), "melee.animations");
        String sounds = json.has("sounds") ? json.get("sounds").getAsString() : namespace;
        if (!sounds.matches("[a-z0-9_.-]+")) {
            throw new JsonParseException("melee.sounds must be a namespace");
        }
        String pack = json.has("pack") ? json.get("pack").getAsString() : "";
        float handScale = json.has("hand_scale") ? json.get("hand_scale").getAsFloat() : 1f;
        if (!(handScale >= 0.25f && handScale <= 4f)) {
            throw new JsonParseException("melee.hand_scale must be between 0.25 and 4");
        }
        float[] camera = json.has("camera") ? floats(json.get("camera"), 3, "melee.camera") : null;
        String angel = json.has("angel") ? json.get("angel").getAsString() : CosmeticDefinition.Melee.RANDOM;
        if (!angel.equals(CosmeticDefinition.Melee.RANDOM) && !angel.equals(CosmeticDefinition.Melee.DEVIL)
                && !angel.equals(CosmeticDefinition.Melee.ANGEL)) {
            throw new JsonParseException("melee.angel must be random, devil or angel");
        }
        float[] grip = json.has("grip") ? floats(json.get("grip"), 2, "melee.grip") : null;
        float[] right = null, left = null;
        if (json.has("transforms")) {
            if (!json.get("transforms").isJsonObject()) {
                throw new JsonParseException("melee.transforms must be an object");
            }
            JsonObject transforms = json.getAsJsonObject("transforms");
            right = transform(transforms, "thirdperson_righthand");
            left = transform(transforms, "thirdperson_lefthand");
        }
        return new CosmeticDefinition.Melee(model, animations, sounds, pack, handScale, camera, angel, grip, right, left);
    }

    /** A display transform as rotation, translation and scale (nine floats), or null when the file has none. */
    private static float[] transform(JsonObject transforms, String key) {
        if (!transforms.has(key) || !transforms.get(key).isJsonObject()) {
            return null;
        }
        JsonObject t = transforms.getAsJsonObject(key);
        String where = "melee.transforms." + key;
        float[] out = {0, 0, 0, 0, 0, 0, 1, 1, 1};
        String[] parts = {"rotation", "translation", "scale"};
        for (int i = 0; i < 3; i++) {
            if (t.has(parts[i])) {
                System.arraycopy(floats(t.get(parts[i]), 3, where + "." + parts[i]), 0, out, i * 3, 3);
            }
        }
        return out;
    }

    /** Most particles one emitter may give off per second, so a typo can't flood the particle engine. */
    static final float MAX_PARTICLE_RATE = 200f;

    private static List<CosmeticDefinition.ParticleEmitter> parseParticles(JsonElement element, String namespace) {
        JsonArray array;
        if (element.isJsonArray()) {
            array = element.getAsJsonArray();
        } else if (element.isJsonObject()) {
            array = new JsonArray();
            array.add(element);
        } else {
            throw new JsonParseException("particles must be an object or an array of objects");
        }
        List<CosmeticDefinition.ParticleEmitter> emitters = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            String where = "particles[" + i + "]";
            if (!array.get(i).isJsonObject()) {
                throw new JsonParseException(where + " must be an object");
            }
            emitters.add(parseEmitter(array.get(i).getAsJsonObject(), namespace, where));
        }
        return List.copyOf(emitters);
    }

    private static CosmeticDefinition.ParticleEmitter parseEmitter(JsonObject json, String namespace, String where) {
        List<String> sprites = new ArrayList<>();
        if (json.has("texture")) {
            sprites.add(qualify(namespace, json.get("texture").getAsString()));
        } else if (json.has("textures")) {
            for (JsonElement sprite : requireArray(json, "textures")) {
                sprites.add(qualify(namespace, sprite.getAsString()));
            }
        }
        if (sprites.isEmpty()) {
            throw new JsonParseException(where + " needs 'texture' or 'textures' (particle textures, e.g. \"minecraft:heart\")");
        }
        float rate = number(json, "rate", 10f, where);
        if (rate <= 0f || rate > MAX_PARTICLE_RATE) {
            throw new JsonParseException(where + ".rate must be above 0 and at most " + (int) MAX_PARTICLE_RATE);
        }
        float[] life = range(json, "lifetime", 1f, 2f, where);
        if (life[0] <= 0f) {
            throw new JsonParseException(where + ".lifetime must be positive");
        }
        float[] size = range(json, "size", 2f, 2f, where);
        float[] alpha = range(json, "alpha", 1f, 0f, where);
        if (size[0] < 0f || size[1] < 0f || alpha[0] < 0f || alpha[0] > 1f || alpha[1] < 0f || alpha[1] > 1f) {
            throw new JsonParseException(where + ": size can't be negative and alpha must be from 0 to 1");
        }
        float drag = number(json, "drag", 0f, where);
        if (drag < 0f || drag > 1f) {
            throw new JsonParseException(where + ".drag must be from 0 to 1");
        }
        int[] colors;
        if (json.has("colors")) {
            JsonArray colorsJson = requireArray(json, "colors");
            colors = new int[colorsJson.size()];
            for (int c = 0; c < colors.length; c++) {
                colors[c] = color(colorsJson.get(c), where + ".colors[" + c + "]");
            }
            if (colors.length == 0) {
                throw new JsonParseException(where + ".colors can't be empty");
            }
        } else {
            colors = new int[]{json.has("color") ? color(json.get("color"), where + ".color") : CosmeticDefinition.WHITE};
        }
        CosmeticDefinition.ParticleTrigger when = CosmeticDefinition.ParticleTrigger.ALWAYS;
        if (json.has("when")) {
            try {
                when = CosmeticDefinition.ParticleTrigger.valueOf(json.get("when").getAsString().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new JsonParseException(where + ".when must be 'always', 'moving' or 'still'");
            }
        }
        return new CosmeticDefinition.ParticleEmitter(List.copyOf(sprites), rate, Math.min(life[0], life[1]),
                Math.max(life[0], life[1]),
                vector(json, "origin", new float[]{0f, 16f, 0f}, where),
                vector(json, "area", new float[]{6f, 12f, 6f}, where),
                vector(json, "velocity", new float[]{0f, 8f, 0f}, where),
                vector(json, "spread", new float[]{2f, 2f, 2f}, where),
                number(json, "gravity", 0f, where), drag, size[0], size[1], alpha[0], alpha[1], colors,
                json.has("glow") && bool(json.get("glow"), where + ".glow"), Math.abs(number(json, "spin", 0f, where)),
                when);
    }

    /** A number or a [from, to] pair. */
    private static float[] range(JsonObject json, String key, float from, float to, String where) {
        if (!json.has(key)) {
            return new float[]{from, to};
        }
        JsonElement element = json.get(key);
        if (element.isJsonArray()) {
            return floats(element, 2, where + "." + key);
        }
        float value = number(json, key, from, where);
        return new float[]{value, value};
    }

    private static float[] vector(JsonObject json, String key, float[] fallback, String where) {
        return json.has(key) ? floats(json.get(key), 3, where + "." + key) : fallback.clone();
    }

    /** Defaults for each state when a cosmetic doesn't set them: base angle, amplitude, flaps per second. */
    static final CosmeticDefinition.WingState WING_IDLE = new CosmeticDefinition.WingState(0f, 6f, 0.35f);
    static final CosmeticDefinition.WingState WING_MOVING = new CosmeticDefinition.WingState(0f, 18f, 1.0f);
    static final CosmeticDefinition.WingState WING_FLYING = new CosmeticDefinition.WingState(0f, 32f, 2.0f);
    static final CosmeticDefinition.WingState WING_SNEAKING = new CosmeticDefinition.WingState(-10f, 3f, 0.25f);

    private static CosmeticDefinition.Wings parseWings(JsonElement element) {
        if (!element.isJsonObject()) {
            throw new JsonParseException("wings must be an object");
        }
        JsonObject json = element.getAsJsonObject();
        String axisName = json.has("axis") ? json.get("axis").getAsString().toLowerCase(Locale.ROOT) : "y";
        int axis = switch (axisName) {
            case "x" -> 0;
            case "y" -> 1;
            case "z" -> 2;
            default -> throw new JsonParseException("wings.axis must be x, y or z");
        };
        // "base" sets the resting angle of every state that doesn't set its own.
        Float base = json.has("base") ? number(json, "base", 0f, "wings") : null;
        return new CosmeticDefinition.Wings(axis,
                wingState(json, "idle", WING_IDLE, base),
                wingState(json, "moving", WING_MOVING, base),
                wingState(json, "flying", WING_FLYING, base),
                wingState(json, "sneaking", WING_SNEAKING, base == null ? null : base + WING_SNEAKING.base()),
                json.has("offset") ? floats(json.get("offset"), 3, "wings.offset") : new float[3],
                json.has("sneak_offset") ? floats(json.get("sneak_offset"), 3, "wings.sneak_offset") : new float[3],
                json.has("armor_offset") ? floats(json.get("armor_offset"), 3, "wings.armor_offset") : new float[3],
                number(json, "spread", 0f, "wings"));
    }

    private static CosmeticDefinition.WingState wingState(JsonObject wings, String key, CosmeticDefinition.WingState fallback,
                                                          Float base) {
        float defaultBase = base != null ? base : fallback.base();
        if (!wings.has(key)) {
            return new CosmeticDefinition.WingState(defaultBase, fallback.amplitude(), fallback.speed());
        }
        if (!wings.get(key).isJsonObject()) {
            throw new JsonParseException("wings." + key + " must be an object like {\"amplitude\": 20, \"speed\": 1}");
        }
        JsonObject json = wings.getAsJsonObject(key);
        float speed = number(json, "speed", fallback.speed(), "wings." + key);
        if (speed < 0f || speed > 20f) {
            throw new JsonParseException("wings." + key + ".speed must be 0 to 20 flaps per second");
        }
        return new CosmeticDefinition.WingState(number(json, "base", defaultBase, "wings." + key),
                number(json, "amplitude", fallback.amplitude(), "wings." + key), speed);
    }

    /** The names of every skeletal animation in the cosmetic, in the order they first appear. */
    static List<String> styles(List<CosmeticDefinition.Part> parts) {
        List<String> styles = new ArrayList<>();
        for (CosmeticDefinition.Part part : parts) {
            for (CosmeticDefinition.Mesh mesh : part.meshes()) {
                if (mesh.rigged() == null) {
                    continue;
                }
                MeshRig rig = mesh.rigged().rig();
                for (int c = 0; c < rig.clipCount(); c++) {
                    if (!styles.contains(rig.clip(c).name())) {
                        styles.add(rig.clip(c).name());
                    }
                }
            }
        }
        return styles;
    }

    /** {@code {"rainbow": false, "size": false}}: options left out stay allowed. */
    private static int parseOptions(JsonElement element) {
        if (!element.isJsonObject()) {
            throw new JsonParseException("options must be an object like {\"rainbow\": false}");
        }
        int options = CosmeticOption.ALL;
        for (var entry : element.getAsJsonObject().entrySet()) {
            CosmeticOption option = null;
            for (CosmeticOption candidate : CosmeticOption.values()) {
                if (candidate.id().equals(entry.getKey())) {
                    option = candidate;
                }
            }
            if (option == null) {
                throw new JsonParseException("options: unknown option '" + entry.getKey() + "' (known: tint, rainbow, "
                        + "saturation, size, position, glow, animation, style)");
            }
            if (!bool(entry.getValue(), "options." + entry.getKey())) {
                options &= ~option.bit();
            }
        }
        return options;
    }

    /** {@code "#RRGGBB"} or a number. */
    static int color(JsonElement element, String where) {
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            String text = element.getAsString();
            if (text.matches("#[0-9a-fA-F]{6}")) {
                return Integer.parseInt(text.substring(1), 16);
            }
        } else if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            double value = element.getAsDouble();
            if (value == Math.rint(value) && value >= 0 && value <= 0xFFFFFF) {
                return (int) value;
            }
        }
        throw new JsonParseException(where + " must be a colour like \"#FF8800\" or a number from 0 to 16777215");
    }

    private static boolean bool(JsonElement element, String where) {
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
            throw new JsonParseException(where + " must be true or false");
        }
        return element.getAsBoolean();
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

        boolean flipV = json.has("flip_v") && bool(json.get("flip_v"), where + ".flip_v");
        MeshBuilder.Raw raw;
        String source;
        if (json.has("model")) {
            if (json.has("vertices") || json.has("faces") || json.has("uvs")) {
                throw new JsonParseException(where + ": use either 'model' or inline 'vertices'/'uvs'/'faces', not both");
            }
            String location = qualifyFile(namespace, json.get("model").getAsString(), where);
            if (!location.endsWith(".obj") && json.has("group")) {
                throw new JsonParseException(where + ": 'group' only applies to .obj files");
            }
            if (!location.endsWith(".md5mesh") && (json.has("bone_animation") || json.has("bone_animations")
                    || json.has("submesh") || json.has("winding"))) {
                throw new JsonParseException(where + ": 'bone_animation(s)', 'submesh' and 'winding' only apply to "
                        + ".md5mesh files");
            }
            if (location.endsWith(".obj")) {
                String group = json.has("group") ? json.get("group").getAsString() : null;
                raw = ObjParser.parse(readText(context.files(), location, MAX_OBJ_BYTES), location, texW, texH, flipV, group);
            } else if (location.endsWith(".md5mesh")) {
                return md5Mesh(json, where, namespace, location, flipV, placement, smooth, texW, texH, context);
            } else if (location.endsWith(".bbmodel")) {
                raw = BbmodelParser.parse(readText(context.files(), location, MAX_BBMODEL_BYTES), location, texW, texH,
                        context.warnings());
            } else {
                throw new JsonParseException(where + ": model must be a .obj, .md5mesh or .bbmodel file");
            }
            source = location;
        } else {
            raw = inlineMesh(json, where);
            source = where;
        }
        return MeshBuilder.build(raw, placement, smooth, texW, texH, source, context.warnings());
    }

    /**
     * An {@code .md5mesh}: a still mesh in its bind pose, or with {@code "bone_animation"} (one) or
     * {@code "bone_animations"} (several, by name) a skeletal mesh played from {@code .md5anim} files.
     */
    private static CosmeticDefinition.Mesh md5Mesh(JsonObject json, String where, String namespace, String location,
                                                   boolean flipV, MeshBuilder.Placement placement, boolean smooth,
                                                   int texW, int texH, Context context) {
        Md5Parser.MeshFile file = Md5Parser.parseMesh(readText(context.files(), location, MAX_MD5MESH_BYTES), location);
        RigBuilder.Winding winding = json.has("winding")
                ? RigBuilder.Winding.byId(json.get("winding").getAsString(), where) : RigBuilder.Winding.AUTO;
        String submesh = json.has("submesh") ? json.get("submesh").getAsString() : null;
        RigBuilder.Options options = new RigBuilder.Options(submesh, flipV, winding, placement, smooth, texW, texH);
        if (json.has("bone_animation") && json.has("bone_animations")) {
            throw new JsonParseException(where + ": use 'bone_animation' or 'bone_animations', not both");
        }
        List<RigBuilder.NamedAnim> anims = new ArrayList<>();
        if (json.has("bone_animation")) {
            anims.add(boneAnimation(DEFAULT_CLIP, json.get("bone_animation"), where + ".bone_animation", namespace, context));
        } else if (json.has("bone_animations")) {
            if (!json.get("bone_animations").isJsonObject() || json.getAsJsonObject("bone_animations").isEmpty()) {
                throw new JsonParseException(where + ".bone_animations must map names to animations");
            }
            for (var entry : json.getAsJsonObject("bone_animations").entrySet()) {
                if (!entry.getKey().matches("[a-z0-9_.-]+")) {
                    throw new JsonParseException(where + ".bone_animations: name '" + entry.getKey()
                            + "' may only use a-z, 0-9, _, . and -");
                }
                anims.add(boneAnimation(entry.getKey(), entry.getValue(), where + ".bone_animations." + entry.getKey(),
                        namespace, context));
            }
        }
        if (anims.isEmpty()) {
            return RigBuilder.still(file, options, location, context.warnings());
        }
        return RigBuilder.rigged(file, anims, options, location, context.warnings());
    }

    /** Name of the clip given with {@code "bone_animation"}. */
    static final String DEFAULT_CLIP = "default";

    /** {@code {"file": "ns:path.md5anim", "loop": true, "speed": 1}}, or just the file location. */
    private static RigBuilder.NamedAnim boneAnimation(String name, JsonElement element, String where, String namespace,
                                                      Context context) {
        JsonObject json;
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            json = new JsonObject();
            json.add("file", element);
        } else if (element.isJsonObject()) {
            json = element.getAsJsonObject();
        } else {
            throw new JsonParseException(where + " must be an .md5anim location or an object with a 'file'");
        }
        if (!json.has("file")) {
            throw new JsonParseException(where + " needs a 'file' (an .md5anim)");
        }
        String location = qualifyFile(namespace, json.get("file").getAsString(), where);
        if (!location.endsWith(".md5anim")) {
            throw new JsonParseException(where + ".file must be an .md5anim file");
        }
        boolean loop = !json.has("loop") || bool(json.get("loop"), where + ".loop");
        float speed = number(json, "speed", 1f, where);
        if (!(speed > 0f && speed <= 100f)) {
            throw new JsonParseException(where + ".speed must be above 0 and at most 100");
        }
        Md5Parser.AnimFile anim = Md5Parser.parseAnim(readText(context.files(), location, MAX_MD5ANIM_BYTES), location);
        return new RigBuilder.NamedAnim(name, anim, loop, speed, location);
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
                                                   ModelSource models, float[] placement, Consumer<String> warnings) {
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
                new BedrockGeometry.BoneOptions(modes, animations, textures), defaultAnimations, placement, warnings);
    }

    /**
     * {@code "model_placement": {"offset": [x, y, z], "rotation": [x, y, z], "scale": s or [x, y, z]}}: moves the whole
     * model around the anchor's pivot (pixels, degrees; scaled, then turned, then moved). As an affine transform.
     */
    private static float[] modelPlacement(JsonElement element) {
        if (!element.isJsonObject()) {
            throw new JsonParseException("model_placement must be an object");
        }
        JsonObject json = element.getAsJsonObject();
        float[] offset = json.has("offset") ? floats(json.get("offset"), 3, "model_placement.offset") : new float[3];
        float[] rotation = json.has("rotation") ? floats(json.get("rotation"), 3, "model_placement.rotation") : new float[3];
        float[] scale = {1f, 1f, 1f};
        if (json.has("scale")) {
            JsonElement s = json.get("scale");
            scale = s.isJsonArray() ? floats(s, 3, "model_placement.scale")
                    : new float[]{s.getAsFloat(), s.getAsFloat(), s.getAsFloat()};
        }
        for (float v : scale) {
            if (!(Math.abs(v) >= 0.01f && Math.abs(v) <= 100f)) {
                throw new JsonParseException("model_placement.scale must be 0.01 to 100 (negative mirrors)");
            }
        }
        float[] m = CosmeticBaker.rotationMatrix(rotation);
        float[] result = new float[12];
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                result[row * 3 + col] = m[row * 3 + col] * scale[col];
            }
            result[9 + row] = offset[row];
        }
        return result;
    }

    /**
     * The model's bones when they move or follow the player: {@code "model_animations"} (a Bedrock / GeckoLib
     * animation file, {@code namespace:name} for {@code cosmetic_models/name.json}), {@code "model_states"} (which
     * animation plays when) and {@code "skeleton": "player"}. Null when the cosmetic has none of them.
     */
    private static BoneRig boneRig(JsonObject json, String namespace, BedrockGeometry.Model model, ModelSource models,
                                   Consumer<String> warnings, float[] placement) {
        boolean skeleton = false;
        if (json.has("skeleton")) {
            String kind = requireString(json, "skeleton");
            if (!kind.equals("player") && !kind.equals("none")) {
                throw new JsonParseException("skeleton must be \"player\" or \"none\"");
            }
            skeleton = kind.equals("player");
        }
        if (!json.has("model_animations") && !skeleton) {
            if (json.has("model_states")) {
                warnings.accept("model_states without model_animations does nothing");
            }
            return null;
        }
        List<BoneRig.Clip> clips = List.of();
        if (json.has("model_animations")) {
            String animationsId = requireString(json, "model_animations");
            if (animationsId.indexOf(':') < 0) {
                animationsId = namespace + ":" + animationsId;
            }
            JsonElement file;
            try (Reader reader = models.open(animationsId)) {
                file = JsonParser.parseReader(reader);
            } catch (IOException e) {
                throw new JsonParseException("Can't read model_animations '" + animationsId + "': " + e.getMessage());
            }
            if (!file.isJsonObject()) {
                throw new JsonParseException("model_animations '" + animationsId + "' must be a JSON object");
            }
            clips = BoneAnimationParser.parse(file.getAsJsonObject(), model.bones(),
                    "model_animations '" + animationsId + "'", warnings);
        }
        List<List<BoneRig.State>> controllers = new ArrayList<>();
        if (json.has("model_states")) {
            JsonArray list = requireArray(json, "model_states");
            boolean flat = list.isEmpty() || list.get(0).isJsonObject();
            for (int c = 0; c < (flat ? 1 : list.size()); c++) {
                JsonArray statesJson = flat ? list : list.get(c).getAsJsonArray();
                List<BoneRig.State> states = new ArrayList<>();
                for (int s = 0; s < statesJson.size(); s++) {
                    String where = "model_states[" + (flat ? s : c + "][" + s) + "]";
                    JsonObject state = statesJson.get(s).getAsJsonObject();
                    int clip = -1;
                    if (state.has("animation") && !state.get("animation").isJsonNull()) {
                        String name = state.get("animation").getAsString();
                        clip = BoneAnimationParser.clipNamed(clips, name);
                        if (clip < 0) {
                            warnings.accept(where + ": no animation '" + name + "'; skipped");
                            continue;
                        }
                    }
                    Molang.Expr when = null;
                    if (state.has("when")) {
                        String condition = state.get("when").getAsString().trim();
                        try {
                            when = condition.isEmpty() || condition.equals("1") || condition.equals("true") ? null
                                    : Molang.compile(condition);
                        } catch (IllegalArgumentException e) {
                            warnings.accept(where + ": can't read '" + condition + "' (" + e.getMessage() + "); never plays");
                            when = Molang.constant(0);
                        }
                    }
                    float blend = state.has("blend") ? state.get("blend").getAsFloat() : 0f;
                    if (!(blend >= 0f) || blend > 60f) {
                        throw new JsonParseException(where + ": blend must be 0 to 60 seconds");
                    }
                    states.add(new BoneRig.State(clip, when, blend));
                }
                controllers.add(List.copyOf(states));
            }
        }
        return new BoneRig(model.bones(), clips, controllers, skeleton, placement);
    }

    /** Most frames a texture animation may have. */
    static final int MAX_TEXTURE_FRAMES = 1024;

    /**
     * {@code {"frames": 8, "frame_time": 100}} for a vertical strip, optionally with per-frame
     * {@code "frame_times"}, or {@code "textures": [...]} for one file per frame.
     */
    private static CosmeticDefinition.TextureAnimation parseTextureAnimation(JsonElement element, String namespace,
                                                                             String where) {
        if (!element.isJsonObject()) {
            throw new JsonParseException(where + " must be an object");
        }
        JsonObject json = element.getAsJsonObject();
        List<String> textures = null;
        if (json.has("textures")) {
            JsonArray array = requireArray(json, "textures");
            List<String> list = new ArrayList<>();
            for (JsonElement entry : array) {
                if (!entry.isJsonPrimitive()) {
                    throw new JsonParseException(where + ".textures must list texture locations");
                }
                list.add(qualify(namespace, entry.getAsString()));
            }
            textures = List.copyOf(list);
        }
        int frames;
        if (json.has("frames")) {
            frames = wholeNumber(json.get("frames"), where + ".frames");
            if (textures != null && frames != textures.size()) {
                throw new JsonParseException(where + ": frames is " + frames + " but " + textures.size()
                        + " textures are listed");
            }
        } else if (textures != null) {
            frames = textures.size();
        } else {
            throw new JsonParseException(where + " needs 'frames' (a vertical strip) or 'textures' (one file per frame)");
        }
        if (frames < 1 || frames > MAX_TEXTURE_FRAMES) {
            throw new JsonParseException(where + ": frames must be 1 to " + MAX_TEXTURE_FRAMES);
        }
        List<Integer> times = new ArrayList<>();
        if (json.has("frame_times")) {
            JsonArray array = requireArray(json, "frame_times");
            if (array.size() != frames) {
                throw new JsonParseException(where + ".frame_times needs one entry per frame (" + frames + " frames, "
                        + array.size() + " entries)");
            }
            for (int i = 0; i < array.size(); i++) {
                times.add(frameTime(array.get(i), where + ".frame_times[" + i + "]"));
            }
        } else {
            int time = json.has("frame_time") ? frameTime(json.get("frame_time"), where + ".frame_time") : 100;
            for (int i = 0; i < frames; i++) {
                times.add(time);
            }
        }
        return new CosmeticDefinition.TextureAnimation(frames, List.copyOf(times), textures);
    }

    /** A frame's duration: whole milliseconds, 1 ms to 10 minutes. */
    private static int frameTime(JsonElement element, String where) {
        int millis = wholeNumber(element, where);
        if (millis < 1 || millis > 600_000) {
            throw new JsonParseException(where + " must be 1 to 600000 milliseconds");
        }
        return millis;
    }

    private static int wholeNumber(JsonElement element, String where) {
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new JsonParseException(where + " must be a whole number");
        }
        double value = element.getAsDouble();
        if (value != Math.rint(value) || Math.abs(value) > Integer.MAX_VALUE) {
            throw new JsonParseException(where + " must be a whole number");
        }
        return (int) value;
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
