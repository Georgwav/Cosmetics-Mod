package io.github.georgwav.cosmetics.melee;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * One of a knife's animations ({@code animations/melee/*.animation.json}), sampled the way TACZ does, since the packs
 * were tuned against it:
 * <ul>
 *   <li>every channel per axis, linearly (rotation too: Euler angles, no slerp);</li>
 *   <li>a linear segment runs from its start key's {@code post} to its end key's {@code pre};</li>
 *   <li>a catmullrom segment (either key {@code catmullrom}) starts from its start key's <b>{@code pre}</b>, not its
 *       {@code post} (TACZ's quirk; it matters where the two differ), through the keys either side;</li>
 *   <li>a {@code step} key holds its {@code post} until the next key.</li>
 * </ul>
 * Rotation is in degrees as the file has it; position in pixels; scale as is (0 hides the bone). Sounds fire at their
 * times ({@code sound_effects}). Pure Java; nothing is allocated when sampling.
 */
public final class KnifeClip {
    public static final int LINEAR = 0, STEP = 1, CATMULLROM = 2;

    public final String name;
    /** Its length in seconds (the file's {@code animation_length}, else its last key). */
    public final float length;
    /** Whether it loops ({@code loop: true}). */
    public final boolean loop;
    /** Per bone of the knife ({@link KnifeGeo#bones} order): its channels, or null. */
    public final Channel[] rotation, position, scale;
    /** Its sounds: when (seconds), and which ({@code namespace:path}). */
    public final float[] soundTimes;
    public final String[] sounds;

    /** One channel of one bone: its keys, or one value throughout. */
    public static final class Channel {
        final float[] times;
        final float[] pre, post;
        final int[] modes;

        Channel(float[] times, float[] pre, float[] post, int[] modes) {
            this.times = times;
            this.pre = pre;
            this.post = post;
            this.modes = modes;
        }

        /** The last key's time (0 for a constant). */
        public float end() {
            return times.length == 0 ? 0f : times[times.length - 1];
        }

        /** The value at {@code t} seconds into {@code out[0..2]}. */
        public void sample(float t, float[] out) {
            int n = times.length;
            if (n == 1 || t <= times[0]) {
                put(n == 1 && t > times[0] ? post : pre, 0, out);
                return;
            }
            if (t >= times[n - 1]) {
                put(post, n - 1, out);
                return;
            }
            int i = 0;
            while (times[i + 1] < t) {
                i++;
            }
            float s = (t - times[i]) / (times[i + 1] - times[i]);
            if (modes[i] == STEP) {
                put(post, i, out);
                return;
            }
            if (modes[i] == CATMULLROM || modes[i + 1] == CATMULLROM) {
                for (int a = 0; a < 3; a++) {
                    float p0 = i > 0 ? post[(i - 1) * 3 + a] : post[i * 3 + a];
                    // TACZ starts a catmullrom segment from its start key's pre.
                    float p1 = pre[i * 3 + a];
                    float p2 = pre[(i + 1) * 3 + a];
                    float p3 = i + 2 < n ? pre[(i + 2) * 3 + a] : p2;
                    float s2 = s * s, s3 = s2 * s;
                    out[a] = 0.5f * (2f * p1 + (-p0 + p2) * s + (2f * p0 - 5f * p1 + 4f * p2 - p3) * s2
                            + (-p0 + 3f * p1 - 3f * p2 + p3) * s3);
                }
                return;
            }
            for (int a = 0; a < 3; a++) {
                float from = post[i * 3 + a], to = pre[(i + 1) * 3 + a];
                out[a] = from + (to - from) * s;
            }
        }

        private static void put(float[] v, int i, float[] out) {
            out[0] = v[i * 3];
            out[1] = v[i * 3 + 1];
            out[2] = v[i * 3 + 2];
        }
    }

    private KnifeClip(String name, float length, boolean loop, Channel[] rotation, Channel[] position, Channel[] scale,
                      float[] soundTimes, String[] sounds) {
        this.name = name;
        this.length = length;
        this.loop = loop;
        this.rotation = rotation;
        this.position = position;
        this.scale = scale;
        this.soundTimes = soundTimes;
        this.sounds = sounds;
    }

    /** Whether this clip moves {@code bone} at all. */
    public boolean has(int bone) {
        return rotation[bone] != null || position[bone] != null || scale[bone] != null;
    }

    /** Every animation in a file ({@code {"animations": {name: {...}}}}), its bones matched to {@code geo}'s. */
    public static Map<String, KnifeClip> parseAll(JsonObject json, KnifeGeo geo) {
        Map<String, KnifeClip> out = new java.util.LinkedHashMap<>();
        JsonObject animations = json.has("animations") && json.get("animations").isJsonObject() ? json.getAsJsonObject("animations") : new JsonObject();
        for (String name : animations.keySet()) {
            if (animations.get(name).isJsonObject()) {
                out.put(name, parse(name, animations.getAsJsonObject(name), geo));
            }
        }
        return out;
    }

    /** One animation. */
    public static KnifeClip parse(String name, JsonObject a, KnifeGeo geo) {
        int n = geo.bones.length;
        Channel[] rot = new Channel[n], pos = new Channel[n], scl = new Channel[n];
        float end = 0f;
        JsonObject bones = a.has("bones") && a.get("bones").isJsonObject() ? a.getAsJsonObject("bones") : new JsonObject();
        for (String boneName : bones.keySet()) {
            int b = geo.bone(boneName);
            if (b < 0 || !bones.get(boneName).isJsonObject()) {
                continue;
            }
            JsonObject ch = bones.getAsJsonObject(boneName);
            rot[b] = channel(ch.get("rotation"), 0f);
            pos[b] = channel(ch.get("position"), 0f);
            scl[b] = channel(ch.get("scale"), 1f);
            for (Channel c : new Channel[]{rot[b], pos[b], scl[b]}) {
                if (c != null) {
                    end = Math.max(end, c.end());
                }
            }
        }
        float length = a.has("animation_length") && a.get("animation_length").isJsonPrimitive()
                && a.get("animation_length").getAsJsonPrimitive().isNumber() ? a.get("animation_length").getAsFloat() : end;
        boolean loop = a.has("loop") && a.get("loop").isJsonPrimitive() && (a.get("loop").getAsJsonPrimitive().isBoolean()
                ? a.get("loop").getAsBoolean() : "loop".equals(a.get("loop").getAsString()));
        List<Float> times = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        if (a.has("sound_effects") && a.get("sound_effects").isJsonObject()) {
            TreeMap<Float, JsonElement> sorted = new TreeMap<>();
            JsonObject effects = a.getAsJsonObject("sound_effects");
            for (String t : effects.keySet()) {
                sorted.put(number(t), effects.get(t));
            }
            for (var e : sorted.entrySet()) {
                JsonElement v = e.getValue();
                for (JsonElement one : v.isJsonArray() ? v.getAsJsonArray() : single(v)) {
                    if (one.isJsonObject() && one.getAsJsonObject().has("effect")) {
                        times.add(e.getKey());
                        keys.add(one.getAsJsonObject().get("effect").getAsString());
                    }
                }
            }
        }
        float[] st = new float[times.size()];
        for (int i = 0; i < st.length; i++) {
            st[i] = times.get(i);
        }
        return new KnifeClip(name, length, loop, rot, pos, scl, st, keys.toArray(new String[0]));
    }

    private static JsonArray single(JsonElement e) {
        JsonArray a = new JsonArray();
        a.add(e);
        return a;
    }

    /** A channel: a value for all time, or keys by time. */
    static Channel channel(JsonElement e, float fallback) {
        if (e == null || e.isJsonNull()) {
            return null;
        }
        if (!e.isJsonObject() || isValue(e.getAsJsonObject())) {
            float[] v = vec(e, fallback);
            return new Channel(new float[]{0f}, v.clone(), v.clone(), new int[]{LINEAR});
        }
        JsonObject keys = e.getAsJsonObject();
        TreeMap<Float, JsonElement> sorted = new TreeMap<>();
        for (String t : keys.keySet()) {
            sorted.put(number(t), keys.get(t));
        }
        int n = sorted.size();
        float[] times = new float[n], pre = new float[n * 3], post = new float[n * 3];
        int[] modes = new int[n];
        int i = 0;
        for (var k : sorted.entrySet()) {
            times[i] = k.getKey();
            JsonElement v = k.getValue();
            float[] a, b;
            int mode = LINEAR;
            if (v.isJsonObject() && !isValue(v.getAsJsonObject())) {
                JsonObject o = v.getAsJsonObject();
                JsonElement pe = o.get("pre"), po = o.get("post");
                a = vec(pe != null ? pe : po, fallback);
                b = vec(po != null ? po : pe, fallback);
                if (o.has("lerp_mode")) {
                    String m = o.get("lerp_mode").getAsString();
                    mode = m.equals("catmullrom") ? CATMULLROM : m.equals("step") ? STEP : LINEAR;
                }
            } else {
                a = vec(v, fallback);
                b = a;
            }
            System.arraycopy(a, 0, pre, i * 3, 3);
            System.arraycopy(b, 0, post, i * 3, 3);
            modes[i] = mode;
            i++;
        }
        return new Channel(times, pre, post, modes);
    }

    /** Whether an object is a value ({@code {"vector": [...]}}) rather than keys. */
    private static boolean isValue(JsonObject o) {
        return o.has("vector");
    }

    /** A value: a number for all three, a list of three, or {@code {"vector": [...]}}; non-numbers count as 0. */
    static float[] vec(JsonElement e, float fallback) {
        if (e == null || e.isJsonNull()) {
            return new float[]{fallback, fallback, fallback};
        }
        if (e.isJsonObject() && e.getAsJsonObject().has("vector")) {
            return vec(e.getAsJsonObject().get("vector"), fallback);
        }
        if (e.isJsonArray()) {
            JsonArray a = e.getAsJsonArray();
            float[] out = new float[3];
            for (int i = 0; i < 3; i++) {
                out[i] = i < a.size() ? number(a.get(i)) : fallback;
            }
            return out;
        }
        float v = number(e);
        return new float[]{v, v, v};
    }

    private static float number(JsonElement e) {
        if (e != null && e.isJsonPrimitive()) {
            return e.getAsJsonPrimitive().isNumber() ? e.getAsFloat() : number(e.getAsString());
        }
        return 0f;
    }

    private static float number(String s) {
        try {
            return Float.parseFloat(s.trim());
        } catch (NumberFormatException e) {
            return 0f;
        }
    }
}
