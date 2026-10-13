package io.github.georgwav.cosmetics.emote;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * Reads emote files. Two formats are understood:
 *
 * <ul>
 *   <li><b>Bedrock animations</b>, as Blockbench exports them ({@code "animations": {...}}): every animation in
 *       the file is an emote. Bones are found by name (head, body, right_arm / rightArm, ..., waist, root).
 *       Rotations are in degrees with the Bedrock player's signs, which are the Java model's; positions in
 *       pixels with +Y up.</li>
 *   <li><b>Emotecraft emotes</b> ({@code "emote": {"moves": [...]}}): ticks, per-move easing, parts head, torso,
 *       rightArm, leftArm, rightLeg, leftLeg and body (the whole player), axes x, y, z, pitch, yaw, roll.</li>
 * </ul>
 *
 * See {@code docs/COSMETIC_FORMAT.md} for both.
 */
public final class EmoteParser {
    /** Longest emote, in seconds. */
    static final float MAX_LENGTH = 600f;
    /** Most keyframes per channel. */
    static final int MAX_KEYFRAMES = 10_000;
    private static final float TICKS_PER_SECOND = 20f;
    private static final float DEFAULT_FADE_IN = 0.15f;
    private static final float DEFAULT_FADE_OUT = 0.25f;
    /** Blockbench's root bone turns around the feet; Emotecraft's whole body around the hips. */
    private static final float BEDROCK_ROOT_PIVOT = 24f;
    private static final float EMOTECRAFT_ROOT_PIVOT = 12f;

    private EmoteParser() {
    }

    /**
     * The emotes in a file. A file with one animation gives the emote {@code id}; a file with several gives
     * {@code id/<animation name>} for each.
     */
    public static List<Emote> parse(String id, Reader reader, Consumer<String> warnings) {
        JsonElement root = JsonParser.parseReader(reader);
        if (!root.isJsonObject()) {
            throw new JsonParseException("an emote file must be a JSON object");
        }
        JsonObject json = root.getAsJsonObject();
        if (json.has("animations")) {
            return bedrock(id, json.getAsJsonObject("animations"), warnings);
        }
        if (json.has("emote")) {
            return List.of(emotecraft(id, json, warnings));
        }
        throw new JsonParseException("not an emote: expected \"animations\" (Blockbench/Bedrock) or \"emote\" (Emotecraft)");
    }

    // ---------------------------------------------------------------- Bedrock / Blockbench

    private static List<Emote> bedrock(String id, JsonObject animations, Consumer<String> warnings) {
        if (animations.isEmpty()) {
            throw new JsonParseException("\"animations\" is empty");
        }
        List<Emote> emotes = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : animations.entrySet()) {
            String emoteId = animations.size() == 1 ? id : id + "/" + idPart(entry.getKey());
            String where = "animation '" + entry.getKey() + "'";
            if (!entry.getValue().isJsonObject()) {
                throw new JsonParseException(where + " must be an object");
            }
            emotes.add(bedrockAnimation(emoteId, entry.getValue().getAsJsonObject(), where, warnings));
        }
        return emotes;
    }

    private static Emote bedrockAnimation(String id, JsonObject json, String where, Consumer<String> warnings) {
        Emote.Ending ending = Emote.Ending.STOP;
        JsonElement loop = json.get("loop");
        if (loop != null && loop.isJsonPrimitive()) {
            JsonPrimitive p = loop.getAsJsonPrimitive();
            if (p.isBoolean() ? p.getAsBoolean() : p.getAsString().equalsIgnoreCase("true")) {
                ending = Emote.Ending.LOOP;
            } else if (p.isString() && p.getAsString().equalsIgnoreCase("hold_on_last_frame")) {
                ending = Emote.Ending.HOLD;
            }
        }
        Map<Emote.Bone, Channel[]> bones = new EnumMap<>(Emote.Bone.class);
        float lastKey = 0f;
        JsonObject bonesJson = json.has("bones") ? json.getAsJsonObject("bones") : new JsonObject();
        for (Map.Entry<String, JsonElement> bone : bonesJson.entrySet()) {
            Emote.Bone target = bone(bone.getKey(), false);
            if (target == null) {
                warnings.accept(where + ": bone '" + bone.getKey() + "' isn't a player body part; ignored");
                continue;
            }
            if (!bone.getValue().isJsonObject()) {
                throw new JsonParseException(where + ": bone '" + bone.getKey() + "' must be an object");
            }
            JsonObject boneJson = bone.getValue().getAsJsonObject();
            Channel[] channels = bones.computeIfAbsent(target, b -> new Channel[Emote.CHANNELS]);
            for (String key : boneJson.keySet()) {
                String w = where + "." + bone.getKey() + "." + key;
                switch (key) {
                    case "rotation" -> lastKey = Math.max(lastKey, bedrockTimeline(boneJson.get(key), channels, true, w));
                    case "position" -> lastKey = Math.max(lastKey, bedrockTimeline(boneJson.get(key), channels, false, w));
                    case "scale" -> warnings.accept(w + ": scaling body parts isn't supported; ignored");
                    default -> { }
                }
            }
        }
        // A bone with only unsupported keys (scale) moves nothing.
        bones.values().removeIf(channels -> Arrays.stream(channels).allMatch(Objects::isNull));
        float length = json.has("animation_length") ? json.get("animation_length").getAsFloat() : lastKey;
        length = checkLength(length, where);
        if (length <= 0f && ending == Emote.Ending.STOP) {
            // A still pose: keep it until the player moves.
            ending = Emote.Ending.HOLD;
        }
        return new Emote(id, string(json, "name"), string(json, "author"), length, ending, 0f, DEFAULT_FADE_IN,
                DEFAULT_FADE_OUT, BEDROCK_ROOT_PIVOT, bones);
    }

    /**
     * Reads a rotation or position: a fixed vector, or keyframes by time. Fills the three channels and returns
     * the last keyframe's time.
     */
    private static float bedrockTimeline(JsonElement json, Channel[] channels, boolean rotation, String where) {
        int first = rotation ? Emote.X_ROT : Emote.X;
        TreeMap<Float, float[][]> keys = new TreeMap<>();
        TreeMap<Float, Ease> eases = new TreeMap<>();
        if (json.isJsonObject() && !isKeyframe(json.getAsJsonObject())) {
            for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject().entrySet()) {
                float time;
                try {
                    time = Float.parseFloat(entry.getKey());
                } catch (NumberFormatException e) {
                    throw new JsonParseException(where + ": '" + entry.getKey() + "' is not a time in seconds");
                }
                if (!(time >= 0f) || time > MAX_LENGTH) {
                    throw new JsonParseException(where + ": time " + entry.getKey() + " is out of range");
                }
                keys.put(time, keyframe(entry.getValue(), rotation, where + "." + entry.getKey(), eases, time));
            }
            if (keys.size() > MAX_KEYFRAMES) {
                throw new JsonParseException(where + ": more than " + MAX_KEYFRAMES + " keyframes");
            }
        } else {
            keys.put(0f, keyframe(json, rotation, where, eases, 0f));
        }
        if (keys.isEmpty()) {
            return 0f;
        }
        for (int axis = 0; axis < 3; axis++) {
            List<Float> times = new ArrayList<>();
            List<Float> values = new ArrayList<>();
            List<Ease> axisEases = new ArrayList<>();
            Ease previous = Ease.LINEAR;
            for (Map.Entry<Float, float[][]> key : keys.entrySet()) {
                float[][] prePost = key.getValue();
                Ease ease = eases.get(key.getKey());
                // Blockbench: a segment is smooth when either end is catmullrom, a step when it starts on one.
                Ease into = previous == Ease.STEP ? Ease.STEP
                        : previous == Ease.CATMULLROM || ease == Ease.CATMULLROM ? Ease.CATMULLROM
                        : ease != null && ease != Ease.LINEAR && ease != Ease.STEP ? ease : Ease.LINEAR;
                times.add(key.getKey());
                values.add(prePost[0][axis]);
                axisEases.add(into);
                if (prePost[1][axis] != prePost[0][axis]) {
                    times.add(key.getKey());
                    values.add(prePost[1][axis]);
                    axisEases.add(Ease.STEP);
                }
                previous = ease == null ? Ease.LINEAR : ease;
            }
            channels[first + axis] = channel(times, values, axisEases, false);
        }
        return keys.lastKey();
    }

    private static boolean isKeyframe(JsonObject json) {
        return json.has("pre") || json.has("post") || json.has("vector") || json.has("lerp_mode");
    }

    /** One keyframe as {pre, post}, each an {x, y, z} in our units; its easing goes into {@code eases}. */
    private static float[][] keyframe(JsonElement json, boolean rotation, String where, Map<Float, Ease> eases,
                                      float time) {
        if (json.isJsonObject()) {
            JsonObject object = json.getAsJsonObject();
            String mode = object.has("lerp_mode") ? object.get("lerp_mode").getAsString()
                    : object.has("easing") ? object.get("easing").getAsString() : null;
            if (mode != null) {
                Ease ease = Ease.byName(mode);
                if (ease == null) {
                    throw new JsonParseException(where + ": unknown easing '" + mode + "'");
                }
                eases.put(time, ease);
            }
            JsonElement vector = object.has("vector") ? object.get("vector") : null;
            JsonElement pre = object.has("pre") ? object.get("pre") : object.has("post") ? object.get("post") : vector;
            JsonElement post = object.has("post") ? object.get("post") : pre;
            if (pre == null) {
                throw new JsonParseException(where + ": a keyframe needs \"pre\", \"post\" or \"vector\"");
            }
            return new float[][]{vector(pre, rotation, where), vector(post, rotation, where)};
        }
        float[] v = vector(json, rotation, where);
        return new float[][]{v, v};
    }

    /** A Bedrock vector in our units: rotations to radians, positions with Y flipped (Bedrock's +Y is up). */
    private static float[] vector(JsonElement json, boolean rotation, String where) {
        float[] v = new float[3];
        if (json.isJsonArray()) {
            JsonArray array = json.getAsJsonArray();
            if (array.size() != 3) {
                throw new JsonParseException(where + ": expected [x, y, z]");
            }
            for (int i = 0; i < 3; i++) {
                v[i] = number(array.get(i), where);
            }
        } else {
            float n = number(json, where);
            v[0] = n;
            v[1] = n;
            v[2] = n;
        }
        if (rotation) {
            for (int i = 0; i < 3; i++) {
                v[i] = (float) Math.toRadians(v[i]);
            }
        } else {
            v[1] = -v[1];
        }
        return v;
    }

    private static float number(JsonElement json, String where) {
        if (json.isJsonPrimitive()) {
            JsonPrimitive p = json.getAsJsonPrimitive();
            if (p.isNumber()) {
                return finite(p.getAsFloat(), where);
            }
            if (p.isString()) {
                try {
                    return finite(Float.parseFloat(p.getAsString().trim()), where);
                } catch (NumberFormatException e) {
                    throw new JsonParseException(where + ": '" + p.getAsString()
                            + "' is a Molang expression; emotes need plain numbers (bake the animation in Blockbench)");
                }
            }
        }
        throw new JsonParseException(where + ": expected a number");
    }

    // ---------------------------------------------------------------- Emotecraft

    private static Emote emotecraft(String id, JsonObject json, Consumer<String> warnings) {
        JsonElement emoteJson = json.get("emote");
        if (!emoteJson.isJsonObject()) {
            throw new JsonParseException("\"emote\" must be an object");
        }
        JsonObject emote = emoteJson.getAsJsonObject();
        boolean degrees = emote.has("degrees") && bool(emote.get("degrees"));
        float begin = ticks(emote, "beginTick", 0f);
        JsonArray moves = emote.has("moves") ? emote.getAsJsonArray("moves") : new JsonArray();
        if (moves.size() > MAX_KEYFRAMES * 6) {
            throw new JsonParseException("more than " + (MAX_KEYFRAMES * 6) + " moves");
        }
        // Per bone and channel: tick -> {value, ease}; a later move at the same tick wins.
        Map<Emote.Bone, List<TreeMap<Float, Object[]>>> keys = new EnumMap<>(Emote.Bone.class);
        float lastTick = 0f;
        for (int i = 0; i < moves.size(); i++) {
            String where = "moves[" + i + "]";
            JsonObject move = moves.get(i).getAsJsonObject();
            float tick = move.has("tick") ? move.get("tick").getAsFloat() : 0f;
            if (!(tick >= 0f) || tick > MAX_LENGTH * TICKS_PER_SECOND) {
                throw new JsonParseException(where + ": tick out of range");
            }
            lastTick = Math.max(lastTick, tick);
            Ease ease = Ease.LINEAR;
            if (move.has("easing")) {
                ease = Ease.byName(move.get("easing").getAsString());
                if (ease == null) {
                    warnings.accept(where + ": unknown easing '" + move.get("easing").getAsString() + "', using linear");
                    ease = Ease.LINEAR;
                }
            }
            int turn = move.has("turn") ? move.get("turn").getAsInt() : 0;
            for (Map.Entry<String, JsonElement> part : move.entrySet()) {
                String key = part.getKey();
                if (key.equals("tick") || key.equals("easing") || key.equals("turn") || key.equals("comment")) {
                    continue;
                }
                Emote.Bone bone = bone(key, true);
                if (bone == null || !part.getValue().isJsonObject()) {
                    warnings.accept(where + ": '" + key + "' isn't a player body part; ignored");
                    continue;
                }
                List<TreeMap<Float, Object[]>> channels = keys.computeIfAbsent(bone, b -> {
                    List<TreeMap<Float, Object[]>> list = new ArrayList<>();
                    for (int c = 0; c < Emote.CHANNELS; c++) {
                        list.add(new TreeMap<>());
                    }
                    return list;
                });
                for (Map.Entry<String, JsonElement> axis : part.getValue().getAsJsonObject().entrySet()) {
                    int channel = switch (axis.getKey()) {
                        case "x" -> Emote.X;
                        case "y" -> Emote.Y;
                        case "z" -> Emote.Z;
                        case "pitch" -> Emote.X_ROT;
                        case "yaw" -> Emote.Y_ROT;
                        case "roll" -> Emote.Z_ROT;
                        default -> -1;
                    };
                    if (channel < 0) {
                        // "bend" and "axis" bend the limbs in Emotecraft; our limbs don't bend.
                        continue;
                    }
                    float value = number(axis.getValue(), where + "." + key + "." + axis.getKey());
                    if (channel >= Emote.X_ROT) {
                        value = (degrees ? (float) Math.toRadians(value) : value) + turn * (float) (2 * Math.PI);
                    }
                    channels.get(channel).put(tick / TICKS_PER_SECOND, new Object[]{value, ease});
                }
            }
        }
        Map<Emote.Bone, Channel[]> bones = new EnumMap<>(Emote.Bone.class);
        for (Map.Entry<Emote.Bone, List<TreeMap<Float, Object[]>>> bone : keys.entrySet()) {
            Channel[] channels = new Channel[Emote.CHANNELS];
            for (int c = 0; c < Emote.CHANNELS; c++) {
                TreeMap<Float, Object[]> axis = bone.getValue().get(c);
                if (axis.isEmpty()) {
                    continue;
                }
                List<Float> times = new ArrayList<>();
                List<Float> values = new ArrayList<>();
                List<Ease> eases = new ArrayList<>();
                for (Map.Entry<Float, Object[]> key : axis.entrySet()) {
                    times.add(key.getKey());
                    values.add((Float) key.getValue()[0]);
                    eases.add((Ease) key.getValue()[1]);
                }
                channels[c] = channel(times, values, eases, true);
            }
            bones.put(bone.getKey(), channels);
        }
        float end = ticks(emote, "endTick", lastTick);
        float stop = ticks(emote, "stopTick", end + DEFAULT_FADE_OUT);
        float length = checkLength(end, "endTick");
        boolean loop = emote.has("isLoop") && bool(emote.get("isLoop"));
        float loopStart = Math.max(0f, Math.min(length, ticks(emote, "returnTick", begin)));
        return new Emote(id, text(json.get("name")), text(json.get("author")), length,
                loop ? Emote.Ending.LOOP : length <= 0f ? Emote.Ending.HOLD : Emote.Ending.STOP, loopStart,
                begin, Math.max(0f, stop - end), EMOTECRAFT_ROOT_PIVOT, bones);
    }

    /** A tick field in seconds. */
    private static float ticks(JsonObject json, String key, float fallbackSeconds) {
        return json.has(key) ? json.get(key).getAsFloat() / TICKS_PER_SECOND : fallbackSeconds;
    }

    // ---------------------------------------------------------------- shared

    /** The bone a file's name means; {@code emotecraft} for its names (its "body" is the whole player). */
    static Emote.Bone bone(String name, boolean emotecraft) {
        String key = name.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "").replace(" ", "");
        if (emotecraft && key.equals("body")) {
            return Emote.Bone.ROOT;
        }
        return switch (key) {
            case "root", "player", "all", "whole", "wholebody", "entity" -> Emote.Bone.ROOT;
            case "waist", "hips", "hip" -> Emote.Bone.WAIST;
            case "body", "torso", "chest", "upperbody" -> Emote.Bone.BODY;
            case "head" -> Emote.Bone.HEAD;
            case "rightarm", "armright", "rarm" -> Emote.Bone.RIGHT_ARM;
            case "leftarm", "armleft", "larm" -> Emote.Bone.LEFT_ARM;
            case "rightleg", "legright", "rleg" -> Emote.Bone.RIGHT_LEG;
            case "leftleg", "legleft", "lleg" -> Emote.Bone.LEFT_LEG;
            default -> null;
        };
    }

    private static Channel channel(List<Float> times, List<Float> values, List<Ease> eases, boolean leadIn) {
        float[] t = new float[times.size()];
        float[] v = new float[values.size()];
        for (int i = 0; i < t.length; i++) {
            t[i] = times.get(i);
            v[i] = values.get(i);
        }
        return new Channel(t, v, eases.toArray(new Ease[0]), leadIn);
    }

    private static float checkLength(float length, String where) {
        if (!(length >= 0f) || length > MAX_LENGTH) {
            throw new JsonParseException(where + ": length must be 0 to " + (int) MAX_LENGTH + " seconds");
        }
        return length;
    }

    private static float finite(float value, String where) {
        if (!Float.isFinite(value)) {
            throw new JsonParseException(where + ": not a finite number");
        }
        return value;
    }

    private static boolean bool(JsonElement json) {
        JsonPrimitive p = json.getAsJsonPrimitive();
        return p.isBoolean() ? p.getAsBoolean() : p.getAsString().equalsIgnoreCase("true");
    }

    private static String string(JsonObject json, String key) {
        return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsString() : null;
    }

    /** A name or author: a string, or a text component's "text". */
    private static String text(JsonElement json) {
        if (json == null || json.isJsonNull()) {
            return null;
        }
        if (json.isJsonPrimitive()) {
            return json.getAsString();
        }
        if (json.isJsonObject() && json.getAsJsonObject().has("text")) {
            return json.getAsJsonObject().get("text").getAsString();
        }
        return null;
    }

    /** An animation name as part of an id: "animation.emote.wave" gives "wave". */
    static String idPart(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        for (String prefix : new String[]{"animation.", "emote.", "player."}) {
            if (key.startsWith(prefix) && key.length() > prefix.length()) {
                key = key.substring(prefix.length());
            }
        }
        return key.replaceAll("[^a-z0-9_.-]", "_").replace('.', '_');
    }
}
