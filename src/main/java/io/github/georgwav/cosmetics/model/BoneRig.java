package io.github.georgwav.cosmetics.model;

import io.github.georgwav.cosmetics.emote.Ease;

import java.util.List;
import java.util.Locale;

/**
 * The bones of a Blockbench (Bedrock) model and the animations that move them: Bedrock / GeckoLib keyframe
 * animations, with Molang in their values, played by controllers that pick an animation from what the player is
 * doing (gliding, swimming, in a screen, ...), blending from one to the next.
 *
 * <p>Geometry is baked where it sits at rest. Drawing asks for each bone's <em>correction</em>: the transform from
 * its rest place to where the animation puts it, so a layer's baked vertices only need that one extra transform.
 * Bones that nothing animates move with their parent, so layers are grouped by the nearest animated bone above
 * them (their {@link #driver}).
 *
 * <p>With a player skeleton, bones named like the player's parts ({@code bipedHead}, {@code armorBody},
 * {@code rightArm}, ...) and everything below them follow that part of the player ({@link Follow}), like GeckoLib
 * armor and NoRisk cosmetics.
 *
 * <p>Units: bone pivots are in model pixels, in the mod's space (+Y down, from the neck); corrections come out in
 * blocks. Render thread only: evaluating writes shared scratch arrays.
 */
public final class BoneRig {
    /** Body parts a bone can follow, with where vanilla puts the part's pivot (pixels, from the neck, +Y down). */
    public enum Follow {
        NONE(0f, 0f), HEAD(0f, 0f), BODY(0f, 0f), RIGHT_ARM(-5f, 2f), LEFT_ARM(5f, 2f), RIGHT_LEG(-1.9f, 12f),
        LEFT_LEG(1.9f, 12f);

        public final float pivotX;
        public final float pivotY;

        Follow(float pivotX, float pivotY) {
            this.pivotX = pivotX;
            this.pivotY = pivotY;
        }

        /** The part a bone of this name stands for (case-insensitive; GeckoLib's and Bedrock's names), else NONE. */
        public static Follow byBoneName(String name) {
            String key = name.toLowerCase(Locale.ROOT).replace("_", "");
            for (String prefix : new String[]{"biped", "armor"}) {
                if (key.startsWith(prefix)) {
                    key = key.substring(prefix.length());
                }
            }
            return switch (key) {
                case "head" -> HEAD;
                case "body", "torso" -> BODY;
                case "rightarm" -> RIGHT_ARM;
                case "leftarm" -> LEFT_ARM;
                case "rightleg" -> RIGHT_LEG;
                case "leftleg" -> LEFT_LEG;
                default -> NONE;
            };
        }
    }

    /** What an animation does once it reaches its end. */
    public enum Loop { ONCE, LOOP, HOLD }

    /**
     * A bone as the model has it.
     *
     * @param parent   index of the parent bone (always lower), or -1
     * @param pivot    pixels, the mod's space
     * @param rotation rest rotation, degrees, applied Z then Y then X like the model's own rotations
     */
    public record Bone(String name, int parent, float[] pivot, float[] rotation) {
    }

    /** Three animated numbers (x, y, z) over time; each keyframe's values are Molang, so they may move. */
    public static final class Timeline {
        final float[] times;
        /** Value arriving at each keyframe, three per keyframe. */
        final Molang.Expr[] pre;
        /** Value leaving each keyframe, three per keyframe. */
        final Molang.Expr[] post;
        /** How the value moves into each keyframe from the one before. */
        final Ease[] eases;

        public Timeline(float[] times, Molang.Expr[] pre, Molang.Expr[] post, Ease[] eases) {
            if (times.length == 0 || pre.length != times.length * 3 || post.length != pre.length
                    || eases.length != times.length) {
                throw new IllegalArgumentException("a timeline needs keyframes with three values each");
            }
            this.times = times;
            this.pre = pre;
            this.post = post;
            this.eases = eases;
        }

        float end() {
            return times[times.length - 1];
        }

        /** The value of one axis at {@code time} seconds. */
        float sample(float time, int axis, Molang.Context context) {
            int n = times.length;
            if (n == 1 || time <= times[0]) {
                return (float) (time < times[0] ? pre[axis] : post[axis]).eval(context);
            }
            if (time >= times[n - 1]) {
                return (float) post[(n - 1) * 3 + axis].eval(context);
            }
            int lo = 0;
            int hi = n - 1;
            while (lo < hi) {
                int mid = (lo + hi + 1) >>> 1;
                if (times[mid] <= time) {
                    lo = mid;
                } else {
                    hi = mid - 1;
                }
            }
            int i = lo;
            float span = times[i + 1] - times[i];
            if (span <= 0f) {
                return (float) pre[(i + 1) * 3 + axis].eval(context);
            }
            float f = (time - times[i]) / span;
            Ease ease = eases[i + 1];
            if (ease == Ease.STEP) {
                return (float) post[i * 3 + axis].eval(context);
            }
            float a = (float) post[i * 3 + axis].eval(context);
            float b = (float) pre[(i + 1) * 3 + axis].eval(context);
            if (ease == Ease.CATMULLROM) {
                float p0 = i > 0 ? (float) post[(i - 1) * 3 + axis].eval(context) : a;
                float p3 = i + 2 < n ? (float) pre[(i + 2) * 3 + axis].eval(context) : b;
                float t2 = f * f;
                float t3 = t2 * f;
                return 0.5f * (2 * a + (-p0 + b) * f + (2 * p0 - 5 * a + 4 * b - p3) * t2 + (-p0 + 3 * a - 3 * b + p3) * t3);
            }
            return a + (b - a) * ease.apply(f);
        }
    }

    /**
     * One bone's movement in an animation: rotation in degrees, position in pixels (Bedrock's axes, +Y up) and
     * scale; any may be null.
     */
    public record Track(Timeline rotation, Timeline position, Timeline scale) {
    }

    /**
     * One named animation.
     *
     * @param length seconds; 0 for one that only follows Molang (its time just keeps running)
     * @param tracks per bone index, null for bones it doesn't move
     */
    public record Clip(String name, Loop loop, float length, Track[] tracks) {
        /** Seconds into the clip after it has played for {@code played} seconds. */
        float time(double played) {
            if (length <= 0f) {
                return (float) played;
            }
            if (loop == Loop.LOOP) {
                return (float) (played - Math.floor(played / length) * length);
            }
            return (float) Math.min(played, length);
        }
    }

    /**
     * One state of a controller: plays {@code clip} (-1: none, the bones at rest) while {@code when} holds (null:
     * always), blending in over {@code blend} seconds.
     */
    public record State(int clip, Molang.Expr when, float blend) {
    }

    /** One player's place in the controllers: which state each is in and since when. */
    public static final class Playback {
        final int[] state;
        final int[] previous;
        final double[] since;
        final double[] previousSince;
        /** Seconds when it was last drawn, so long-unused ones can be dropped. */
        public double lastUsed;

        public int controllers() {
            return state.length;
        }

        Playback(int controllers) {
            state = new int[controllers];
            previous = new int[controllers];
            since = new double[controllers];
            previousSince = new double[controllers];
            java.util.Arrays.fill(state, -1);
            java.util.Arrays.fill(previous, -1);
        }
    }

    private static final float BLOCK = 1f / 16f;

    private final Bone[] bones;
    /** Placement of the whole model (pixels), or null. */
    private final float[] root;
    private final Clip[] clips;
    private final State[][] controllers;
    private final int[] driver;
    private final Follow[] follow;
    /** Inverse of each bone's place at rest, 12 floats per bone ([m00..m22] row-major, then translation). */
    private final float[] restInverse;
    private final boolean animated;

    // Scratch, render thread only.
    private final float[] rot;
    private final float[] pos;
    private final float[] scale;
    private final float[] absolute;
    private final float[] correction;
    private final boolean[] identity;
    private final boolean[] hidden;
    private final Molang.Context stillContext = new Molang.Context();

    /**
     * @param bones       parents before children
     * @param controllers each a list of states, tried in order; empty to play the first clip on its own
     * @param skeleton    bones named like the player's parts follow those parts
     */
    public BoneRig(List<Bone> bones, List<Clip> clips, List<List<State>> controllers, boolean skeleton) {
        this(bones, clips, controllers, skeleton, null);
    }

    /**
     * @param root the model's placement around the anchor ({@code [m00..m22, tx, ty, tz]}, pixels), as its geometry
     *             was baked with; null for none
     */
    public BoneRig(List<Bone> bones, List<Clip> clips, List<List<State>> controllers, boolean skeleton, float[] root) {
        this.root = root;
        this.bones = bones.toArray(new Bone[0]);
        this.clips = clips.toArray(new Clip[0]);
        if (controllers.isEmpty() && !clips.isEmpty()) {
            controllers = List.of(List.of(new State(0, null, 0f)));
        }
        this.controllers = new State[controllers.size()][];
        for (int c = 0; c < this.controllers.length; c++) {
            this.controllers[c] = controllers.get(c).toArray(new State[0]);
        }
        int n = this.bones.length;
        boolean[] moved = new boolean[n];
        for (Clip clip : this.clips) {
            for (int b = 0; b < n; b++) {
                if (clip.tracks()[b] != null) {
                    moved[b] = true;
                }
            }
        }
        driver = new int[n];
        follow = new Follow[n];
        restInverse = new float[n * 12];
        float[] restAbsolute = new float[n * 12];
        boolean anyMoved = false;
        for (int b = 0; b < n; b++) {
            Bone bone = this.bones[b];
            if (bone.parent() >= b) {
                throw new IllegalArgumentException("bone '" + bone.name() + "' comes before its parent");
            }
            Follow own = skeleton ? Follow.byBoneName(bone.name()) : Follow.NONE;
            Follow inherited = bone.parent() >= 0 ? follow[bone.parent()] : Follow.NONE;
            // The topmost bone named like a part leads; armorHead under bipedHead just follows along.
            boolean leads = own != Follow.NONE && inherited == Follow.NONE;
            follow[b] = leads ? own : inherited;
            driver[b] = moved[b] || leads ? b : bone.parent() >= 0 ? driver[bone.parent()] : -1;
            anyMoved |= moved[b];
            local(bone, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 1f, 1f, restAbsolute, b * 12);
            if (bone.parent() >= 0) {
                compose(restAbsolute, bone.parent() * 12, restAbsolute, b * 12, restAbsolute, b * 12);
            } else if (root != null) {
                compose(root, 0, restAbsolute, b * 12, restAbsolute, b * 12);
            }
            invert(restAbsolute, b * 12, restInverse, b * 12);
        }
        animated = anyMoved;
        rot = new float[n * 3];
        pos = new float[n * 3];
        scale = new float[n * 3];
        absolute = new float[n * 12];
        correction = new float[n * 12];
        identity = new boolean[n];
        hidden = new boolean[n];
        java.util.Arrays.fill(identity, true);
        for (int b = 0; b < n; b++) {
            setIdentity(correction, b * 12);
        }
    }

    public int boneCount() {
        return bones.length;
    }

    public Bone bone(int index) {
        return bones[index];
    }

    /** Index of the bone called {@code name} (case-insensitive), or -1. */
    public int boneNamed(String name) {
        for (int b = 0; b < bones.length; b++) {
            if (bones[b].name().equalsIgnoreCase(name)) {
                return b;
            }
        }
        return -1;
    }

    /** The bone whose correction moves this bone's geometry, or -1 when it never moves (and follows nothing). */
    public int driver(int bone) {
        return driver[bone];
    }

    /** The body part a driver bone's geometry follows; NONE for -1. */
    public Follow follow(int driverBone) {
        return driverBone < 0 ? Follow.NONE : follow[driverBone];
    }

    /** Some bone follows a body part other than the cosmetic's own anchor would give. */
    public boolean followsParts() {
        for (Follow f : follow) {
            if (f != Follow.NONE) {
                return true;
            }
        }
        return false;
    }

    /** Some animation moves a bone. */
    public boolean animated() {
        return animated;
    }

    public int clipCount() {
        return clips.length;
    }

    public Clip clip(int index) {
        return clips[index];
    }

    public int controllerCount() {
        return controllers.length;
    }

    public Playback newPlayback() {
        return new Playback(controllers.length);
    }

    /**
     * Works out every bone for one player at {@code seconds}: each controller takes the first state whose
     * condition holds (the context's queries), blending from the state it was in. {@code speed} scales time; 0
     * holds each animation at its start. Afterwards {@link #correction}, {@link #isIdentity} and
     * {@link #isHidden} describe the pose, until the next call.
     */
    public void pose(Playback playback, double seconds, float speed, Molang.Context context) {
        java.util.Arrays.fill(rot, 0f);
        java.util.Arrays.fill(pos, 0f);
        java.util.Arrays.fill(scale, 0f);
        for (int c = 0; c < controllers.length; c++) {
            State[] states = controllers[c];
            int current = playback.state[c];
            int currentClip = current >= 0 ? states[current].clip() : -1;
            context.set(Molang.Query.ANIM_TIME, currentClip >= 0
                    ? clips[currentClip].time((seconds - playback.since[c]) * speed) : 0.0);
            int chosen = -1;
            for (int s = 0; s < states.length; s++) {
                if (states[s].when() == null || states[s].when().eval(context) != 0) {
                    chosen = s;
                    break;
                }
            }
            if (chosen != current) {
                playback.previous[c] = current;
                playback.previousSince[c] = playback.since[c];
                playback.state[c] = chosen;
                playback.since[c] = seconds;
            }
            int previous = playback.previous[c];
            float blend = chosen >= 0 ? states[chosen].blend() : 0f;
            float weight = previous >= 0 && blend > 0f
                    ? (float) Math.min(1.0, (seconds - playback.since[c]) / blend) : 1f;
            if (chosen >= 0 && states[chosen].clip() >= 0) {
                add(clips[states[chosen].clip()], (seconds - playback.since[c]) * speed, weight, context);
            }
            if (weight < 1f && states[previous].clip() >= 0) {
                add(clips[states[previous].clip()], (seconds - playback.previousSince[c]) * speed, 1f - weight, context);
            }
        }
        solve();
    }

    /** The pose for a still picture: each controller's state for {@code context}, at the start, no blending. */
    public void poseStill(Molang.Context context) {
        pose(newPlayback(), 0.0, 0f, context);
    }

    /** A still pose for a picture in a screen: in the GUI, standing. */
    public void poseStill() {
        poseStill(stillContext.clear().set(Molang.Query.IS_IN_GUI, true));
    }

    /** One clip's movement, weighted, onto the bones. */
    private void add(Clip clip, double played, float weight, Molang.Context context) {
        float t = clip.time(played);
        context.set(Molang.Query.ANIM_TIME, t);
        Track[] tracks = clip.tracks();
        for (int b = 0; b < tracks.length; b++) {
            Track track = tracks[b];
            if (track == null) {
                continue;
            }
            int o = b * 3;
            if (track.rotation() != null) {
                for (int axis = 0; axis < 3; axis++) {
                    rot[o + axis] += weight * track.rotation().sample(t, axis, context);
                }
            }
            if (track.position() != null) {
                for (int axis = 0; axis < 3; axis++) {
                    float v = track.position().sample(t, axis, context);
                    // Bedrock's +Y is up, ours is down.
                    pos[o + axis] += weight * (axis == 1 ? -v : v);
                }
            }
            if (track.scale() != null) {
                for (int axis = 0; axis < 3; axis++) {
                    scale[o + axis] += weight * (track.scale().sample(t, axis, context) - 1f);
                }
            }
        }
    }

    /** Bone transforms from the summed movement, then each one's correction from rest. */
    private void solve() {
        for (int b = 0; b < bones.length; b++) {
            int o = b * 3;
            int m = b * 12;
            local(bones[b], rot[o], rot[o + 1], rot[o + 2], pos[o], pos[o + 1], pos[o + 2],
                    1f + scale[o], 1f + scale[o + 1], 1f + scale[o + 2], absolute, m);
            int parent = bones[b].parent();
            if (parent >= 0) {
                compose(absolute, parent * 12, absolute, m, absolute, m);
            } else if (root != null) {
                compose(root, 0, absolute, m, absolute, m);
            }
            compose(absolute, m, restInverse, m, correction, m);
            float[] c = correction;
            float det = c[m] * (c[m + 4] * c[m + 8] - c[m + 5] * c[m + 7])
                    - c[m + 1] * (c[m + 3] * c[m + 8] - c[m + 5] * c[m + 6])
                    + c[m + 2] * (c[m + 3] * c[m + 7] - c[m + 4] * c[m + 6]);
            hidden[b] = Math.abs(det) < 1.0e-9f;
            boolean same = true;
            for (int i = 0; i < 12 && same; i++) {
                float expected = i == 0 || i == 4 || i == 8 ? 1f : 0f;
                same = Math.abs(c[m + i] - expected) < 1.0e-6f;
            }
            identity[b] = same;
            // Out in blocks.
            c[m + 9] *= BLOCK;
            c[m + 10] *= BLOCK;
            c[m + 11] *= BLOCK;
        }
    }

    /**
     * A bone's correction from the last {@link #pose}: 12 floats from {@code correction()[bone * 12]}, the 3x3
     * row-major then the translation in blocks, applied to its geometry as baked.
     */
    public float[] correction() {
        return correction;
    }

    /** The bone sits where it was baked: no correction needed. */
    public boolean isIdentity(int bone) {
        return identity[bone];
    }

    /** The bone is scaled to nothing: its geometry needn't be drawn. */
    public boolean isHidden(int bone) {
        return hidden[bone];
    }

    // ------------------------------------------------------------------------------------------------ maths

    /**
     * A bone's own transform with movement: translate by the position, rotate (rest + moved) and scale around the
     * pivot, as GeckoLib does. Writes 12 floats at {@code out[at]}.
     */
    private static void local(Bone bone, float rx, float ry, float rz, float px, float py, float pz,
                              float sx, float sy, float sz, float[] out, int at) {
        float[] r = bone.rotation();
        float[] m = CosmeticBaker.rotationMatrix(new float[]{r[0] + rx, r[1] + ry, r[2] + rz});
        for (int row = 0; row < 3; row++) {
            out[at + row * 3] = m[row * 3] * sx;
            out[at + row * 3 + 1] = m[row * 3 + 1] * sy;
            out[at + row * 3 + 2] = m[row * 3 + 2] * sz;
        }
        float[] p = bone.pivot();
        for (int row = 0; row < 3; row++) {
            float moved = out[at + row * 3] * p[0] + out[at + row * 3 + 1] * p[1] + out[at + row * 3 + 2] * p[2];
            out[at + 9 + row] = p[row] - moved + (row == 0 ? px : row == 1 ? py : pz);
        }
    }

    /** {@code out = outer * inner}; any of the arrays may be the same (each 12 floats at its offset). */
    static void compose(float[] outer, int o, float[] inner, int i, float[] out, int at) {
        float a00 = outer[o], a01 = outer[o + 1], a02 = outer[o + 2];
        float a10 = outer[o + 3], a11 = outer[o + 4], a12 = outer[o + 5];
        float a20 = outer[o + 6], a21 = outer[o + 7], a22 = outer[o + 8];
        float at0 = outer[o + 9], at1 = outer[o + 10], at2 = outer[o + 11];
        float b00 = inner[i], b01 = inner[i + 1], b02 = inner[i + 2];
        float b10 = inner[i + 3], b11 = inner[i + 4], b12 = inner[i + 5];
        float b20 = inner[i + 6], b21 = inner[i + 7], b22 = inner[i + 8];
        float bt0 = inner[i + 9], bt1 = inner[i + 10], bt2 = inner[i + 11];
        out[at] = a00 * b00 + a01 * b10 + a02 * b20;
        out[at + 1] = a00 * b01 + a01 * b11 + a02 * b21;
        out[at + 2] = a00 * b02 + a01 * b12 + a02 * b22;
        out[at + 3] = a10 * b00 + a11 * b10 + a12 * b20;
        out[at + 4] = a10 * b01 + a11 * b11 + a12 * b21;
        out[at + 5] = a10 * b02 + a11 * b12 + a12 * b22;
        out[at + 6] = a20 * b00 + a21 * b10 + a22 * b20;
        out[at + 7] = a20 * b01 + a21 * b11 + a22 * b21;
        out[at + 8] = a20 * b02 + a21 * b12 + a22 * b22;
        out[at + 9] = a00 * bt0 + a01 * bt1 + a02 * bt2 + at0;
        out[at + 10] = a10 * bt0 + a11 * bt1 + a12 * bt2 + at1;
        out[at + 11] = a20 * bt0 + a21 * bt1 + a22 * bt2 + at2;
    }

    /** Inverse of an affine transform (12 floats at {@code o}); a degenerate one gives the identity. */
    static void invert(float[] m, int o, float[] out, int at) {
        float a = m[o], b = m[o + 1], c = m[o + 2];
        float d = m[o + 3], e = m[o + 4], f = m[o + 5];
        float g = m[o + 6], h = m[o + 7], i = m[o + 8];
        float det = a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g);
        if (Math.abs(det) < 1.0e-12f) {
            setIdentity(out, at);
            return;
        }
        float k = 1f / det;
        float[] r = {
                (e * i - f * h) * k, (c * h - b * i) * k, (b * f - c * e) * k,
                (f * g - d * i) * k, (a * i - c * g) * k, (c * d - a * f) * k,
                (d * h - e * g) * k, (b * g - a * h) * k, (a * e - b * d) * k};
        float tx = m[o + 9], ty = m[o + 10], tz = m[o + 11];
        System.arraycopy(r, 0, out, at, 9);
        for (int row = 0; row < 3; row++) {
            out[at + 9 + row] = -(r[row * 3] * tx + r[row * 3 + 1] * ty + r[row * 3 + 2] * tz);
        }
    }

    private static void setIdentity(float[] m, int at) {
        java.util.Arrays.fill(m, at, at + 12, 0f);
        m[at] = 1f;
        m[at + 4] = 1f;
        m[at + 8] = 1f;
    }
}
