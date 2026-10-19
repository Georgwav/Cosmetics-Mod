package io.github.georgwav.cosmetics.melee;

/**
 * Poses a knife for one frame, in knife space (see {@link KnifeGeo}), from its tracks:
 * <ol>
 *   <li>the base ({@code static_idle}), looping or held;</li>
 *   <li>the main animation (draw, inspect or an attack), which overrides the channels it has, cross-faded from the
 *       one before it;</li>
 *   <li>blended tracks (the {@code idle} loop, walking or running), which add rotation and position and multiply
 *       scale.</li>
 * </ol>
 * A bone's matrix is its parent's times its own: moved to its pivot (plus the animation's move, X mirrored), turned by
 * its bind rotation and then the animation's (each Z, then Y, then X, as TACZ composes them, rather than adding the
 * angles as Blockbench does), then scaled. Then hands a pack hides are shown ({@link KnifeRig#handFix}). Allocates
 * nothing per frame; one per knife, render thread only.
 */
public final class KnifePose {
    private static final float DEG = (float) (Math.PI / 180.0);

    public final KnifeGeo geo;
    private final int n;
    private final float[] rot, pos, scl;
    /** Whether an animation set the bone's scale this frame (slash effects show only then). */
    private final boolean[] scaled;
    /** Each bone's scale with its parents' (the smallest axis, signed): what it really shows at. */
    private final float[] cumulative;
    /** Each bone's world matrix in knife space: rows of (m0 m1 m2 t), twelve floats a bone. */
    public final float[] world;
    /** The camera bone's rotation this frame (degrees, as the file has it), for the camera shake. */
    public final float[] camera = new float[3];
    private final float[] tmp = new float[3], tmp2 = new float[3], rb = new float[9], ra = new float[9], r = new float[9];
    private final int cameraBone;
    /** Scales to show hands at when an animation hides them, per bone (null for none); from {@link KnifeRig}. */
    float[][] handFix;

    public KnifePose(KnifeGeo geo) {
        this.geo = geo;
        this.n = geo.bones.length;
        this.rot = new float[n * 3];
        this.pos = new float[n * 3];
        this.scl = new float[n * 3];
        this.scaled = new boolean[n];
        this.cumulative = new float[n];
        this.world = new float[n * 12];
        this.cameraBone = geo.bone("camera");
    }

    /** One blended track this frame: its clip, time and weight (0 to 1). */
    public static final class Blend {
        public KnifeClip clip;
        public float time, weight;
    }

    /**
     * Poses the knife.
     *
     * @param base     the base clip ({@code static_idle}) or null, at {@code baseT}
     * @param prev     the main clip faded out of (or null), at {@code prevT}
     * @param main     the main clip (or null), at {@code mainT}
     * @param fade     how far the cross-fade from {@code prev} to {@code main} is (1 when done)
     * @param blends   blended tracks (null entries and clips skipped)
     */
    public void pose(KnifeClip base, float baseT, KnifeClip prev, float prevT, KnifeClip main, float mainT, float fade,
                     Blend[] blends) {
        for (int b = 0; b < n; b++) {
            int o = b * 3;
            scaled[b] = channel(base, b, 2) != null || (fade < 1f && channel(prev, b, 2) != null) || channel(main, b, 2) != null;
            // The main layer: the previous main faded into the current one, each falling back to the base.
            layer(base, baseT, prev, prevT, b, o);
            float pr0 = tmp[0], pr1 = tmp[1], pr2 = tmp[2];
            float pp0 = tmp2[0], pp1 = tmp2[1], pp2 = tmp2[2];
            float ps0 = scl[o], ps1 = scl[o + 1], ps2 = scl[o + 2];
            layer(base, baseT, main, mainT, b, o);
            float f = Math.max(0f, Math.min(1f, fade));
            rot[o] = pr0 + (tmp[0] - pr0) * f;
            rot[o + 1] = pr1 + (tmp[1] - pr1) * f;
            rot[o + 2] = pr2 + (tmp[2] - pr2) * f;
            pos[o] = pp0 + (tmp2[0] - pp0) * f;
            pos[o + 1] = pp1 + (tmp2[1] - pp1) * f;
            pos[o + 2] = pp2 + (tmp2[2] - pp2) * f;
            scl[o] = ps0 + (scl[o] - ps0) * f;
            scl[o + 1] = ps1 + (scl[o + 1] - ps1) * f;
            scl[o + 2] = ps2 + (scl[o + 2] - ps2) * f;
            if (blends != null) {
                for (Blend bl : blends) {
                    if (bl == null || bl.clip == null || bl.weight <= 0f) {
                        continue;
                    }
                    KnifeClip c = bl.clip;
                    float w = Math.min(1f, bl.weight);
                    if (c.rotation[b] != null) {
                        c.rotation[b].sample(bl.time, tmp);
                        rot[o] += tmp[0] * w;
                        rot[o + 1] += tmp[1] * w;
                        rot[o + 2] += tmp[2] * w;
                    }
                    if (c.position[b] != null) {
                        c.position[b].sample(bl.time, tmp);
                        pos[o] += tmp[0] * w;
                        pos[o + 1] += tmp[1] * w;
                        pos[o + 2] += tmp[2] * w;
                    }
                    if (c.scale[b] != null) {
                        scaled[b] = true;
                        c.scale[b].sample(bl.time, tmp);
                        scl[o] *= 1f + (tmp[0] - 1f) * w;
                        scl[o + 1] *= 1f + (tmp[1] - 1f) * w;
                        scl[o + 2] *= 1f + (tmp[2] - 1f) * w;
                    }
                }
            }
            if (handFix != null && handFix[b] != null
                    && Math.min(Math.abs(scl[o]), Math.min(Math.abs(scl[o + 1]), Math.abs(scl[o + 2]))) < 0.01f) {
                scl[o] = handFix[b][0];
                scl[o + 1] = handFix[b][1];
                scl[o + 2] = handFix[b][2];
            }
        }
        for (int b = 0; b < n; b++) {
            int o = b * 3;
            float own = Math.min(scl[o], Math.min(scl[o + 1], scl[o + 2]));
            int parent = geo.bones[b].parent;
            cumulative[b] = parent >= 0 ? cumulative[parent] * own : own;
        }
        if (cameraBone >= 0) {
            camera[0] = rot[cameraBone * 3];
            camera[1] = rot[cameraBone * 3 + 1];
            camera[2] = rot[cameraBone * 3 + 2];
        } else {
            camera[0] = camera[1] = camera[2] = 0f;
        }
        matrices();
    }

    /**
     * One main layer's channels for a bone: the clip's where it has them, else the base's, else the bind pose. Rotation
     * into {@code tmp}, position into {@code tmp2}, scale into {@link #scl}.
     */
    private void layer(KnifeClip base, float baseT, KnifeClip clip, float t, int b, int o) {
        tmp[0] = tmp[1] = tmp[2] = 0f;
        tmp2[0] = tmp2[1] = tmp2[2] = 0f;
        scl[o] = scl[o + 1] = scl[o + 2] = 1f;
        KnifeClip.Channel c;
        if ((c = pick(clip, base, b, 0)) != null) {
            c.sample(c == channel(clip, b, 0) ? t : baseT, tmp);
        }
        if ((c = pick(clip, base, b, 1)) != null) {
            c.sample(c == channel(clip, b, 1) ? t : baseT, tmp2);
        }
        if ((c = pick(clip, base, b, 2)) != null) {
            float[] s = scratchScale;
            c.sample(c == channel(clip, b, 2) ? t : baseT, s);
            scl[o] = s[0];
            scl[o + 1] = s[1];
            scl[o + 2] = s[2];
        }
    }

    private final float[] scratchScale = new float[3];

    private static KnifeClip.Channel channel(KnifeClip clip, int b, int kind) {
        if (clip == null) {
            return null;
        }
        return kind == 0 ? clip.rotation[b] : kind == 1 ? clip.position[b] : clip.scale[b];
    }

    private static KnifeClip.Channel pick(KnifeClip clip, KnifeClip base, int b, int kind) {
        KnifeClip.Channel c = channel(clip, b, kind);
        return c != null ? c : channel(base, b, kind);
    }

    /** Each bone's world matrix from its pivot, bind and animated rotation and scale. */
    private void matrices() {
        KnifeGeo.Bone[] bones = geo.bones;
        for (int b = 0; b < n; b++) {
            KnifeGeo.Bone bone = bones[b];
            int o = b * 3;
            KnifeGeo.rotation(bone.rx, bone.ry, bone.rz, rb);
            KnifeGeo.rotation(-rot[o] * DEG, -rot[o + 1] * DEG, rot[o + 2] * DEG, ra);
            mul3(rb, ra, r);
            float sx = scl[o], sy = scl[o + 1], sz = scl[o + 2];
            float tx, ty, tz;
            if (bone.parent >= 0) {
                KnifeGeo.Bone p = bones[bone.parent];
                tx = bone.px - p.px;
                ty = bone.py - p.py;
                tz = bone.pz - p.pz;
            } else {
                tx = bone.px;
                ty = bone.py;
                tz = bone.pz;
            }
            tx += -pos[o];
            ty += pos[o + 1];
            tz += pos[o + 2];
            int w = b * 12;
            // Local: rows of R·S, then the translation.
            float l00 = r[0] * sx, l01 = r[1] * sy, l02 = r[2] * sz;
            float l10 = r[3] * sx, l11 = r[4] * sy, l12 = r[5] * sz;
            float l20 = r[6] * sx, l21 = r[7] * sy, l22 = r[8] * sz;
            if (bone.parent < 0) {
                world[w] = l00;
                world[w + 1] = l01;
                world[w + 2] = l02;
                world[w + 3] = tx;
                world[w + 4] = l10;
                world[w + 5] = l11;
                world[w + 6] = l12;
                world[w + 7] = ty;
                world[w + 8] = l20;
                world[w + 9] = l21;
                world[w + 10] = l22;
                world[w + 11] = tz;
            } else {
                int p = bone.parent * 12;
                for (int row = 0; row < 3; row++) {
                    float a0 = world[p + row * 4], a1 = world[p + row * 4 + 1], a2 = world[p + row * 4 + 2], a3 = world[p + row * 4 + 3];
                    world[w + row * 4] = a0 * l00 + a1 * l10 + a2 * l20;
                    world[w + row * 4 + 1] = a0 * l01 + a1 * l11 + a2 * l21;
                    world[w + row * 4 + 2] = a0 * l02 + a1 * l12 + a2 * l22;
                    world[w + row * 4 + 3] = a0 * tx + a1 * ty + a2 * tz + a3;
                }
            }
        }
    }

    private static void mul3(float[] a, float[] b, float[] out) {
        for (int i = 0; i < 3; i++) {
            float a0 = a[i * 3], a1 = a[i * 3 + 1], a2 = a[i * 3 + 2];
            out[i * 3] = a0 * b[0] + a1 * b[3] + a2 * b[6];
            out[i * 3 + 1] = a0 * b[1] + a1 * b[4] + a2 * b[7];
            out[i * 3 + 2] = a0 * b[2] + a1 * b[5] + a2 * b[8];
        }
    }

    /**
     * Whether a bone's faces show this frame: not scaled away, and a slash effect or glint only while an animation
     * scales it (itself or the effect bone it's under) above 0.
     */
    public boolean shown(int bone) {
        if (scaleOf(bone) < 0.01f) {
            return false;
        }
        KnifeGeo.Bone[] bones = geo.bones;
        if (!bones[bone].effect) {
            return true;
        }
        // An effect's catmullrom keys round 0 overshoot a little either side: below 0 it's off, not mirrored.
        if (signedScale(bone) <= 0.01f) {
            return false;
        }
        for (int b = bone; b >= 0 && bones[b].effect; b = bones[b].parent) {
            if (scaled[b]) {
                return true;
            }
        }
        return false;
    }

    /** The scale a bone shows at this frame (its own and its parents', smallest axis), with its sign. */
    public float signedScale(int bone) {
        return cumulative[bone];
    }

    /** The scale a bone shows at this frame (its own and its parents', absolute): below about 0.01 it's hidden. */
    public float scaleOf(int bone) {
        return Math.abs(cumulative[bone]);
    }

    /** A point relative to a bone's pivot, in knife space, into {@code out}. */
    public void transform(int bone, float x, float y, float z, float[] out) {
        int w = bone * 12;
        out[0] = world[w] * x + world[w + 1] * y + world[w + 2] * z + world[w + 3];
        out[1] = world[w + 4] * x + world[w + 5] * y + world[w + 6] * z + world[w + 7];
        out[2] = world[w + 8] * x + world[w + 9] * y + world[w + 10] * z + world[w + 11];
    }

    /** A bone's pivot in knife space this frame, into {@code out}. */
    public void origin(int bone, float[] out) {
        int w = bone * 12;
        out[0] = world[w + 3];
        out[1] = world[w + 7];
        out[2] = world[w + 11];
    }
}
