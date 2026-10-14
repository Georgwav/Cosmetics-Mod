package io.github.georgwav.cosmetics.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import io.github.georgwav.cosmetics.emote.Ease;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * Reads a Bedrock / GeckoLib animation file ({@code .animation.json}, what Blockbench exports with "Export Bedrock
 * Animations") into {@link BoneRig.Clip}s for a model's bones. Values may be numbers or Molang; keyframes may be
 * Bedrock's ({@code "0.5": [x, y, z]}, {@code {"pre", "post", "lerp_mode"}}) or GeckoLib's
 * ({@code {"vector": [...], "easing": "easeInOutSine"}}).
 *
 * <p>Pure Java with no Minecraft types, so it is unit-tested without a game instance.
 */
public final class BoneAnimationParser {
    static final int MAX_CLIPS = 64;
    static final int MAX_KEYFRAMES = 4096;
    static final float MAX_TIME = 3600f;

    private BoneAnimationParser() {
    }

    /**
     * @param bones    the model's bones; animations of bones it doesn't have are skipped
     * @param warnings told about Molang it can't read (those values are 0) and skipped bones
     */
    public static List<BoneRig.Clip> parse(JsonObject file, List<BoneRig.Bone> bones, String where,
                                           Consumer<String> warnings) {
        JsonElement animationsJson = file.get("animations");
        if (animationsJson == null || !animationsJson.isJsonObject()) {
            throw new JsonParseException(where + " is not a Bedrock animation file (no \"animations\")");
        }
        Map<String, Integer> boneIndex = new HashMap<>();
        for (int b = 0; b < bones.size(); b++) {
            boneIndex.putIfAbsent(bones.get(b).name().toLowerCase(Locale.ROOT), b);
        }
        JsonObject animations = animationsJson.getAsJsonObject();
        if (animations.size() > MAX_CLIPS) {
            throw new JsonParseException(where + ": more than " + MAX_CLIPS + " animations");
        }
        List<BoneRig.Clip> clips = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : animations.entrySet()) {
            String name = entry.getKey();
            if (!entry.getValue().isJsonObject()) {
                throw new JsonParseException(where + ": animation '" + name + "' must be an object");
            }
            clips.add(clip(name, entry.getValue().getAsJsonObject(), boneIndex, bones.size(),
                    where + " animation '" + name + "'", warnings, missing));
        }
        if (!missing.isEmpty()) {
            warnings.accept(where + ": the model has no bone " + String.join(", ", missing) + "; skipped");
        }
        return List.copyOf(clips);
    }

    /** Index of the clip called {@code name} (case-insensitive), or -1. */
    public static int clipNamed(List<BoneRig.Clip> clips, String name) {
        for (int i = 0; i < clips.size(); i++) {
            if (clips.get(i).name().equalsIgnoreCase(name)) {
                return i;
            }
        }
        return -1;
    }

    private static BoneRig.Clip clip(String name, JsonObject json, Map<String, Integer> boneIndex, int boneCount,
                                     String where, Consumer<String> warnings, List<String> missing) {
        BoneRig.Loop loop = BoneRig.Loop.ONCE;
        JsonElement loopJson = json.get("loop");
        if (loopJson != null && loopJson.isJsonPrimitive()) {
            JsonPrimitive p = loopJson.getAsJsonPrimitive();
            if (p.isBoolean()) {
                loop = p.getAsBoolean() ? BoneRig.Loop.LOOP : BoneRig.Loop.ONCE;
            } else if (p.isString()) {
                String mode = p.getAsString().toLowerCase(Locale.ROOT);
                loop = mode.equals("true") || mode.equals("loop") ? BoneRig.Loop.LOOP
                        : mode.equals("hold_on_last_frame") ? BoneRig.Loop.HOLD : BoneRig.Loop.ONCE;
            }
        }
        BoneRig.Track[] tracks = new BoneRig.Track[boneCount];
        float end = 0f;
        JsonElement bonesJson = json.get("bones");
        if (bonesJson != null && bonesJson.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : bonesJson.getAsJsonObject().entrySet()) {
                Integer bone = boneIndex.get(entry.getKey().toLowerCase(Locale.ROOT));
                if (bone == null) {
                    if (!missing.contains(entry.getKey()) && missing.size() < 8) {
                        missing.add(entry.getKey());
                    }
                    continue;
                }
                if (!entry.getValue().isJsonObject()) {
                    throw new JsonParseException(where + ": bone '" + entry.getKey() + "' must be an object");
                }
                JsonObject channels = entry.getValue().getAsJsonObject();
                String at = where + " bone '" + entry.getKey() + "'";
                BoneRig.Timeline rotation = channels.has("rotation")
                        ? timeline(channels.get("rotation"), 0f, at + " rotation", warnings) : null;
                BoneRig.Timeline position = channels.has("position")
                        ? timeline(channels.get("position"), 0f, at + " position", warnings) : null;
                BoneRig.Timeline scale = channels.has("scale")
                        ? timeline(channels.get("scale"), 1f, at + " scale", warnings) : null;
                if (rotation == null && position == null && scale == null) {
                    continue;
                }
                for (BoneRig.Timeline t : new BoneRig.Timeline[]{rotation, position, scale}) {
                    if (t != null) {
                        end = Math.max(end, t.end());
                    }
                }
                tracks[bone] = new BoneRig.Track(rotation, position, scale);
            }
        }
        float length = end;
        if (json.has("animation_length")) {
            JsonElement l = json.get("animation_length");
            if (l.isJsonPrimitive() && l.getAsJsonPrimitive().isNumber()) {
                length = l.getAsFloat();
            }
        }
        if (!(length >= 0f) || length > MAX_TIME) {
            throw new JsonParseException(where + ": animation_length is out of range");
        }
        return new BoneRig.Clip(name, loop, length, tracks);
    }

    /** A rotation, position or scale: one value for all time, or keyframes by time in seconds. */
    private static BoneRig.Timeline timeline(JsonElement json, float neutral, String where, Consumer<String> warnings) {
        TreeMap<Float, JsonElement> keys = new TreeMap<>();
        if (json.isJsonObject() && !isKeyframe(json.getAsJsonObject())) {
            for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject().entrySet()) {
                float time;
                try {
                    time = Float.parseFloat(entry.getKey().trim());
                } catch (NumberFormatException e) {
                    throw new JsonParseException(where + ": '" + entry.getKey() + "' is not a time in seconds");
                }
                if (!(time >= 0f) || time > MAX_TIME) {
                    throw new JsonParseException(where + ": time " + entry.getKey() + " is out of range");
                }
                keys.put(time, entry.getValue());
            }
            if (keys.size() > MAX_KEYFRAMES) {
                throw new JsonParseException(where + ": more than " + MAX_KEYFRAMES + " keyframes");
            }
        } else {
            keys.put(0f, json);
        }
        if (keys.isEmpty()) {
            return null;
        }
        int n = keys.size();
        float[] times = new float[n];
        Molang.Expr[] pre = new Molang.Expr[n * 3];
        Molang.Expr[] post = new Molang.Expr[n * 3];
        Ease[] eases = new Ease[n];
        Ease previous = Ease.LINEAR;
        int k = 0;
        for (Map.Entry<Float, JsonElement> entry : keys.entrySet()) {
            times[k] = entry.getKey();
            String at = where + " at " + entry.getKey();
            JsonElement value = entry.getValue();
            Ease ease = null;
            JsonElement preJson = value;
            JsonElement postJson = value;
            if (value.isJsonObject()) {
                JsonObject object = value.getAsJsonObject();
                String mode = object.has("lerp_mode") ? object.get("lerp_mode").getAsString()
                        : object.has("easing") ? object.get("easing").getAsString() : null;
                if (mode != null) {
                    ease = Ease.byName(mode);
                    if (ease == null) {
                        warnings.accept(at + ": unknown easing '" + mode + "'; linear");
                    }
                }
                JsonElement vector = object.get("vector");
                preJson = object.has("pre") ? object.get("pre") : object.has("post") ? object.get("post") : vector;
                postJson = object.has("post") ? object.get("post") : preJson;
                if (preJson == null) {
                    throw new JsonParseException(at + ": a keyframe needs \"pre\", \"post\" or \"vector\"");
                }
            }
            vector(preJson, neutral, at, warnings, pre, k * 3);
            vector(postJson, neutral, at, warnings, post, k * 3);
            // Blockbench: a segment is smooth when either end is catmullrom, a step when it starts on one.
            eases[k] = k == 0 ? Ease.LINEAR
                    : previous == Ease.STEP ? Ease.STEP
                    : previous == Ease.CATMULLROM || ease == Ease.CATMULLROM ? Ease.CATMULLROM
                    : ease != null && ease != Ease.STEP ? ease : Ease.LINEAR;
            previous = ease == null ? Ease.LINEAR : ease;
            k++;
        }
        return new BoneRig.Timeline(times, pre, post, eases);
    }

    private static boolean isKeyframe(JsonObject json) {
        return json.has("pre") || json.has("post") || json.has("vector") || json.has("lerp_mode");
    }

    /** {@code [x, y, z]} or one value for all three; each a number or Molang. */
    private static void vector(JsonElement json, float neutral, String where, Consumer<String> warnings,
                               Molang.Expr[] out, int at) {
        if (json.isJsonArray()) {
            JsonArray array = json.getAsJsonArray();
            if (array.isEmpty() || array.size() > 3) {
                throw new JsonParseException(where + ": expected [x, y, z]");
            }
            for (int axis = 0; axis < 3; axis++) {
                out[at + axis] = axis < array.size() ? value(array.get(axis), neutral, where, warnings)
                        : Molang.constant(neutral);
            }
            return;
        }
        Molang.Expr value = value(json, neutral, where, warnings);
        out[at] = value;
        out[at + 1] = value;
        out[at + 2] = value;
    }

    private static Molang.Expr value(JsonElement json, float neutral, String where, Consumer<String> warnings) {
        if (json.isJsonPrimitive()) {
            JsonPrimitive p = json.getAsJsonPrimitive();
            if (p.isNumber()) {
                float v = p.getAsFloat();
                if (!Float.isFinite(v)) {
                    throw new JsonParseException(where + ": not a finite number");
                }
                return Molang.constant(v);
            }
            if (p.isString()) {
                String text = p.getAsString().trim();
                if (text.isEmpty()) {
                    return Molang.constant(neutral);
                }
                try {
                    return Molang.compile(text);
                } catch (IllegalArgumentException e) {
                    warnings.accept(where + ": can't read Molang '" + shorten(text) + "' (" + e.getMessage() + "); uses "
                            + neutral);
                    return Molang.constant(neutral);
                }
            }
            if (p.isBoolean()) {
                return Molang.constant(p.getAsBoolean() ? 1 : 0);
            }
        }
        throw new JsonParseException(where + ": expected a number or Molang");
    }

    private static String shorten(String text) {
        return text.length() > 60 ? text.substring(0, 57) + "..." : text;
    }
}
