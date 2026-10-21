package io.github.georgwav.cosmetics.melee;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A knife ready to animate: its model, animations sorted into what plays when, and what's worked out once when it
 * loads: the hands its pack hides (and the scale to show them at), where your fist should sit on the handle, and each
 * animation's impact and settle times (for fitting them to Minecraft's sword, {@link KnifeTiming}). Pure Java.
 */
public final class KnifeRig {
    /** The knife is "at rest" once its cubes are this close to its idle pose (pixels) for good. */
    static final float REST = 0.6f;
    /** Sampling step for the measurements (seconds). */
    static final float STEP = 1f / 60f;

    public final KnifeGeo geo;
    public final Map<String, KnifeClip> clips;
    /** {@code static_idle} (and {@code static_idle_1}, which some knives alternate with), {@code idle} (blended), or null. */
    public final KnifeClip staticIdle, staticIdleAlt, idle;
    public final List<KnifeClip> draws = new ArrayList<>(), inspects = new ArrayList<>();
    public final KnifeClip light1, light2, heavy;
    /** Delta Force's run (start, loop, end) and Valorant's walks, or null. */
    public final KnifeClip runStart, run, runEnd, walkForward, walkBackward, walkSideway;
    /** Whether the knife has a second set of animations ({@code *_angel}: Dark Star's Angel model). */
    public final boolean angelSet;
    /** The eye: the {@code idle_view} bone's bind position (knife space, pixels). */
    public final float[] eye = new float[3];
    /** How far to move the right arm across itself onto the handle (x, z, pixels in the arm bone's frame). */
    public final float[] grip = new float[2];
    /** Per animation: its impact (seconds into it) and when it settles back to idle. */
    public final Map<String, float[]> timing = new java.util.HashMap<>();
    /** Each clip's Angel version, where it has one. */
    private final Map<KnifeClip, KnifeClip> angels = new java.util.IdentityHashMap<>();
    /** The bones that hold nothing but arms, and the scale to show them at when an animation hides them. */
    final float[][] handFix;
    /**
     * What the hand glow's trail follows: the knife's biggest part (its blade, mostly; -1 if it draws nothing of its
     * own) with the box round its faces in its bone's frame (middle x, y, z, then half sizes; pixels), and the bone the
     * pack's other hand is drawn at (-1 for none).
     */
    public final int blade, otherHand;
    public final float[] bladeBox = new float[6];

    public KnifeRig(KnifeGeo geo, Map<String, KnifeClip> clips) {
        this.geo = geo;
        this.clips = clips;
        this.staticIdle = clips.get("static_idle");
        this.staticIdleAlt = clips.get("static_idle_1");
        this.idle = clips.get("idle");
        for (int i = 0; ; i++) {
            KnifeClip d = clips.get(i == 0 ? "draw" : "draw_" + i);
            if (d == null) {
                break;
            }
            draws.add(d);
        }
        for (int i = 0; ; i++) {
            KnifeClip d = clips.get(i == 0 ? "inspect" : "inspect_" + i);
            if (d == null) {
                break;
            }
            inspects.add(d);
        }
        this.light1 = clips.get("melee_1");
        this.light2 = clips.get("melee_2");
        this.heavy = clips.get("melee_3");
        this.runStart = clips.get("run_start");
        this.run = clips.get("run");
        this.runEnd = clips.get("run_end");
        this.walkForward = clips.get("walk_forward");
        this.walkBackward = clips.get("walk_backward");
        this.walkSideway = clips.get("walk_sideway");
        this.angelSet = clips.keySet().stream().anyMatch(n -> n.endsWith("_angel"));
        for (KnifeClip c : clips.values()) {
            KnifeClip a = clips.get(c.name + "_angel");
            if (a != null) {
                angels.put(c, a);
            }
        }
        this.handFix = handFix(geo, clips);
        this.blade = blade(geo, bladeBox);
        int other = -1;
        for (int b = 0; b < geo.bones.length && other < 0; b++) {
            if (geo.bones[b].arm < 0) {
                other = b;
            }
        }
        this.otherHand = other;
        KnifePose pose = new KnifePose(geo);
        pose.handFix = handFix;
        int view = geo.bone("idle_view");
        if (view >= 0) {
            pose.pose(null, 0f, null, 0f, null, 0f, 1f, null);
            pose.origin(view, eye);
        } else {
            eye[1] = 63f;
        }
        gripFix(pose);
        for (KnifeClip c : clips.values()) {
            timing.put(c.name, measure(pose, c));
        }
    }

    /** A clip by name, Dark Star's Angel version of it when {@code angel} and it has one. */
    public KnifeClip clip(String name, boolean angel) {
        KnifeClip a = angel ? clips.get(name + "_angel") : null;
        return a != null ? a : clips.get(name);
    }

    /** The Angel version of a clip if {@code angel} and there is one, else the clip itself. No allocation. */
    public KnifeClip angel(KnifeClip c, boolean angel) {
        if (!angel || c == null) {
            return c;
        }
        KnifeClip a = angels.get(c);
        return a != null ? a : c;
    }

    // ------------------------------------------------------------------ the trail

    /** The bone with the most cube volume of the knife's own (no arms, places or effects), and its faces' box. */
    static int blade(KnifeGeo geo, float[] box) {
        int best = -1;
        float most = 0f;
        for (int b = 0; b < geo.bones.length; b++) {
            KnifeGeo.Bone bone = geo.bones[b];
            if (bone.arm != 0 || bone.marker || bone.effect || bone.quads.length == 0) {
                continue;
            }
            float volume = 0f;
            for (int k = 0; k < bone.cubes.length; k += 4) {
                volume += bone.cubes[k];
            }
            if (best < 0 || volume > most) {
                best = b;
                most = volume;
            }
        }
        if (best < 0) {
            return -1;
        }
        float[] q = geo.bones[best].quads;
        float[] lo = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE}, hi = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for (int o = 0; o < q.length; o += KnifeGeo.QUAD) {
            for (int k = 0; k < 4; k++) {
                for (int a = 0; a < 3; a++) {
                    lo[a] = Math.min(lo[a], q[o + k * 5 + a]);
                    hi[a] = Math.max(hi[a], q[o + k * 5 + a]);
                }
            }
        }
        for (int a = 0; a < 3; a++) {
            box[a] = (lo[a] + hi[a]) / 2f;
            // At least half a pixel either side, so a flat blade still shows how it turns.
            box[3 + a] = Math.max(0.5f, (hi[a] - lo[a]) / 2f);
        }
        return best;
    }

    // ------------------------------------------------------------------ hidden hands

    /**
     * Bones named like a hand that hold nothing but arms (no cubes of their own, every child the same or an arm's
     * place): when an animation scales one to 0 (VCT Lock In hides both hands in its idle), it's shown at the scale the
     * knife's other animations give it.
     */
    static float[][] handFix(KnifeGeo geo, Map<String, KnifeClip> clips) {
        float[][] out = new float[geo.bones.length][];
        for (int b = 0; b < geo.bones.length; b++) {
            KnifeGeo.Bone bone = geo.bones[b];
            if (!bone.name.toLowerCase(java.util.Locale.ROOT).contains("hand") || bone.arm != 0 || !handOnly(geo, b)) {
                continue;
            }
            float[] shown = {1f, 1f, 1f};
            float[] v = new float[3];
            search:
            for (KnifeClip c : clips.values()) {
                KnifeClip.Channel s = c.scale[b];
                if (s == null) {
                    continue;
                }
                for (int k = 0; k < s.times.length; k++) {
                    for (float[] side : new float[][]{s.post, s.pre}) {
                        v[0] = side[k * 3];
                        v[1] = side[k * 3 + 1];
                        v[2] = side[k * 3 + 2];
                        if (Math.min(Math.abs(v[0]), Math.min(Math.abs(v[1]), Math.abs(v[2]))) > 0.01f) {
                            shown = v.clone();
                            break search;
                        }
                    }
                }
            }
            out[b] = shown;
        }
        return out;
    }

    private static boolean handOnly(KnifeGeo geo, int b) {
        KnifeGeo.Bone bone = geo.bones[b];
        if (bone.arm != 0) {
            return true;
        }
        if (bone.quads.length > 0) {
            return false;
        }
        boolean any = false;
        for (int c = 0; c < geo.bones.length; c++) {
            if (geo.bones[c].parent == b) {
                any = true;
                if (!handOnly(geo, c)) {
                    return false;
                }
            }
        }
        return any;
    }

    // ------------------------------------------------------------------ the grip

    /**
     * Where the knife's long axis crosses the right fist: over idle, draw and inspect (13 frames each), the knife's
     * cubes in the right arm bone's frame give a volume-weighted principal axis; how far it misses the fist across the
     * arm (x and z, never along it) is taken as the median, ignored under 0.4 px and capped at 2 px.
     */
    private void gripFix(KnifePose pose) {
        int rp = geo.bone("righthand_pos");
        if (rp < 0) {
            return;
        }
        List<Float> xs = new ArrayList<>(), zs = new ArrayList<>();
        float[] p = new float[3], inv = new float[12];
        float fx = -6f, fy = 12f, fz = 0f;
        for (String an : new String[]{"static_idle", "draw", "inspect"}) {
            KnifeClip a = clips.get(an);
            if (a == null) {
                continue;
            }
            for (int f = 0; f <= 12; f++) {
                float t = a.length * f / 12f;
                pose.pose(staticIdle, 0f, null, 0f, an.equals("static_idle") ? null : a, t, 1f, null);
                invert(pose.world, rp * 12, inv);
                List<float[]> pts = new ArrayList<>();
                for (int b = 0; b < geo.bones.length; b++) {
                    KnifeGeo.Bone bone = geo.bones[b];
                    if (bone.effect || bone.cubes.length == 0 || pose.scaleOf(b) < 0.05f) {
                        continue;
                    }
                    for (int k = 0; k < bone.cubes.length; k += 4) {
                        pose.transform(b, bone.cubes[k + 1], bone.cubes[k + 2], bone.cubes[k + 3], p);
                        float qx = inv[0] * p[0] + inv[1] * p[1] + inv[2] * p[2] + inv[3];
                        float qy = inv[4] * p[0] + inv[5] * p[1] + inv[6] * p[2] + inv[7];
                        float qz = inv[8] * p[0] + inv[9] * p[1] + inv[10] * p[2] + inv[11];
                        pts.add(new float[]{qx, qy, qz, bone.cubes[k]});
                    }
                }
                if (pts.size() < 3) {
                    continue;
                }
                double w = 0, cx = 0, cy = 0, cz = 0;
                for (float[] q : pts) {
                    cx += q[0] * q[3];
                    cy += q[1] * q[3];
                    cz += q[2] * q[3];
                    w += q[3];
                }
                cx /= w;
                cy /= w;
                cz /= w;
                double[] m = new double[9];
                for (float[] q : pts) {
                    double[] d = {q[0] - cx, q[1] - cy, q[2] - cz};
                    for (int i = 0; i < 3; i++) {
                        for (int j = 0; j < 3; j++) {
                            m[i * 3 + j] += q[3] * d[i] * d[j];
                        }
                    }
                }
                double ax = .3, ay = .5, az = .8;
                for (int k = 0; k < 50; k++) {
                    double nx = m[0] * ax + m[1] * ay + m[2] * az, ny = m[3] * ax + m[4] * ay + m[5] * az, nz = m[6] * ax + m[7] * ay + m[8] * az;
                    double l = Math.sqrt(nx * nx + ny * ny + nz * nz);
                    if (l < 1e-9) {
                        break;
                    }
                    ax = nx / l;
                    ay = ny / l;
                    az = nz / l;
                }
                double along = (fx - cx) * ax + (fy - cy) * ay + (fz - cz) * az;
                double ox = cx + ax * along - fx, oy = cy + ay * along - fy, oz = cz + az * along - fz;
                if (Math.sqrt(ox * ox + oy * oy + oz * oz) < 4) {
                    xs.add((float) ox);
                    zs.add((float) oz);
                }
            }
        }
        grip[0] = median(xs);
        grip[1] = median(zs);
    }

    private static float median(List<Float> v) {
        if (v.isEmpty()) {
            return 0f;
        }
        List<Float> q = new ArrayList<>(v);
        q.sort(Float::compare);
        float m = q.get(q.size() >> 1);
        return Math.max(-2f, Math.min(2f, Math.abs(m) < 0.4f ? 0f : m));
    }

    /** Inverts an affine matrix (rows of (m t), twelve floats from {@code at}) into {@code out}. */
    static void invert(float[] m, int at, float[] out) {
        float a = m[at], b = m[at + 1], c = m[at + 2], d = m[at + 4], e = m[at + 5], f = m[at + 6], g = m[at + 8], h = m[at + 9], i = m[at + 10];
        float det = a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g);
        if (Math.abs(det) < 1e-12f) {
            java.util.Arrays.fill(out, 0f);
            out[0] = out[5] = out[10] = 1f;
            return;
        }
        float k = 1f / det;
        out[0] = (e * i - f * h) * k;
        out[1] = (c * h - b * i) * k;
        out[2] = (b * f - c * e) * k;
        out[4] = (f * g - d * i) * k;
        out[5] = (a * i - c * g) * k;
        out[6] = (c * d - a * f) * k;
        out[8] = (d * h - e * g) * k;
        out[9] = (b * g - a * h) * k;
        out[10] = (a * e - b * d) * k;
        float tx = m[at + 3], ty = m[at + 7], tz = m[at + 11];
        out[3] = -(out[0] * tx + out[1] * ty + out[2] * tz);
        out[7] = -(out[4] * tx + out[5] * ty + out[6] * tz);
        out[11] = -(out[8] * tx + out[9] * ty + out[10] * tz);
    }

    // ------------------------------------------------------------------ impact and settle

    /**
     * An animation's impact and settle times. Settle: from when on the knife's cubes stay within {@link #REST} pixels
     * of the idle pose (the whole length if they never do). Impact: the first moment a slash-effect bone shows (not a
     * glint), else the knife's fastest moment in the first 60% before it settles.
     */
    private float[] measure(KnifePose pose, KnifeClip c) {
        int frames = Math.max(1, (int) Math.ceil(c.length / STEP));
        float[][] rest = knifePoints(pose, null, 0f);
        float settle = 0f;
        float impactFx = -1f;
        float[][] prev = null;
        float[] speed = new float[frames + 1];
        for (int f = 0; f <= frames; f++) {
            float t = Math.min(c.length, f * STEP);
            float[][] now = knifePoints(pose, c, t);
            float dev = 0f;
            for (int k = 0; k < now.length && k < rest.length; k++) {
                if (now[k] != null && rest[k] != null) {
                    dev = Math.max(dev, dist(now[k], rest[k]));
                }
            }
            if (dev > REST) {
                settle = Math.min(c.length, t + STEP);
            }
            if (impactFx < 0f) {
                for (int b = 0; b < geo.bones.length; b++) {
                    // "Turns on": at least a quarter of its size on every axis (its keys round 0 overshoot).
                    if (geo.bones[b].slash && geo.bones[b].quads.length > 0 && pose.shown(b) && pose.signedScale(b) >= 0.25f && f > 0) {
                        impactFx = t;
                        break;
                    }
                }
            }
            if (prev != null) {
                // How fast the visible knife moves; a part that pops in or out, or jumps between two keys, isn't a
                // swing, so each point's move counts up to about 6 px a frame.
                float s = 0f;
                for (int k = 0; k < now.length && k < prev.length; k++) {
                    if (now[k] != null && prev[k] != null) {
                        s += Math.min(6f, dist(now[k], prev[k]));
                    }
                }
                speed[f] = s;
            }
            prev = now;
        }
        float impact = impactFx;
        if (impact < 0f) {
            int last = Math.max(1, (int) Math.floor(settle * 0.6f / STEP));
            int best = 1;
            float top = -1f;
            for (int f = 1; f <= Math.min(last, frames); f++) {
                // A swing lasts several frames; a one-frame spike is a cut between two keys (a key whose pre and
                // post differ), so each frame counts only as fast as the faster of its neighbours.
                float around = Math.max(speed[f - 1], f + 1 <= frames ? speed[f + 1] : 0f);
                float v = Math.min(speed[f], around);
                if (v > top) {
                    top = v;
                    best = f;
                }
            }
            impact = Math.min(c.length, best * STEP);
        }
        return new float[]{impact, Math.max(settle, Math.min(impact, c.length))};
    }

    /**
     * The knife's cube centres (not effects, not arms) posed with {@code c} at {@code t} (or idle for null), in the same
     * order every time; null for a cube whose bone is hidden then.
     */
    private float[][] knifePoints(KnifePose pose, KnifeClip c, float t) {
        pose.pose(staticIdle, 0f, null, 0f, c, t, 1f, null);
        List<float[]> pts = new ArrayList<>();
        for (int b = 0; b < geo.bones.length; b++) {
            KnifeGeo.Bone bone = geo.bones[b];
            if (bone.effect) {
                continue;
            }
            boolean shown = pose.shown(b) && pose.scaleOf(b) > 0.05f;
            for (int k = 0; k < bone.cubes.length; k += 4) {
                float[] p = null;
                if (shown) {
                    p = new float[3];
                    pose.transform(b, bone.cubes[k + 1], bone.cubes[k + 2], bone.cubes[k + 3], p);
                }
                pts.add(p);
            }
        }
        return pts.toArray(new float[0][]);
    }

    private static float dist(float[] a, float[] b) {
        float dx = a[0] - b[0], dy = a[1] - b[1], dz = a[2] - b[2];
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
