package io.github.georgwav.cosmetics.model;

/**
 * The skeleton of one skeletal mesh, ready for the render thread: absolute joint transforms for every
 * animation frame, worked out once at load, and the mesh's placement in the cosmetic. Drawing asks for
 * the joints at a time ({@link #evaluate}), which blends two neighbouring frames into a reusable palette
 * without allocating.
 *
 * <p>Geometry hangs off this rig in two ways (see {@link RigBuilder}): rigid pieces that follow one joint
 * each (drawn with that joint's transform on the pose stack, like any other layer), and at most one
 * {@link SkinnedMesh} for vertices weighted to several joints.
 *
 * <p>A rig can have several named clips (for example a cape's styles, or a pet's states); the cosmetic's
 * style setting picks which one plays.
 *
 * <p>Units are the mesh file's own; the placement turns them into cosmetic pixels.
 */
public final class MeshRig {
    static final int STRIDE = Md5Parser.JOINT_FLOATS;

    /**
     * One named animation: absolute joint transforms for each frame, worked out at load.
     *
     * @param frames joint transforms, frame after frame ({@code joints * 7} floats per frame)
     */
    public record Clip(String name, float[] frames, int frameCount, float frameRate, boolean loop, float speed) {
    }

    private final String source;
    private final int joints;
    private final Clip[] clips;
    /** Clip to play for each of the cosmetic's styles; set once when the cosmetic is baked. */
    private int[] styleClips = new int[0];

    // Placement, applied around the whole rig in this order: scale (with mirror), rotate around the
    // pivot, offset. Same keys and meaning as for static meshes.
    private final float[] scale;
    private final float[] rotation;
    private final float[] pivot;
    private final float[] offset;

    // Render thread only.
    private final float[] palette;
    private int paletteClip = -1;
    private float paletteTime = Float.NaN;

    /**
     * @param clips    at least one; the first is played when a style has no clip of its own
     * @param rotation placement rotation as a unit quaternion {@code x, y, z, w}
     */
    MeshRig(String source, int joints, Clip[] clips, float[] scale, float[] rotation, float[] pivot, float[] offset) {
        if (clips.length == 0) {
            throw new IllegalArgumentException("a rig needs at least one clip");
        }
        this.source = source;
        this.joints = joints;
        this.clips = clips;
        this.scale = scale;
        this.rotation = rotation;
        this.pivot = pivot;
        this.offset = offset;
        this.palette = new float[joints * STRIDE];
        System.arraycopy(clips[0].frames(), 0, palette, 0, palette.length);
    }

    public String source() {
        return source;
    }

    public int joints() {
        return joints;
    }

    public int clipCount() {
        return clips.length;
    }

    public Clip clip(int index) {
        return clips[index];
    }

    /** Some clip has more than one frame, so the rig moves. */
    public boolean animated() {
        for (Clip clip : clips) {
            if (clip.frameCount() > 1) {
                return true;
            }
        }
        return false;
    }

    /** Matches the cosmetic's styles (by name) to this rig's clips; styles it has no clip for play the first. */
    void bindStyles(String[] styles) {
        int[] map = new int[styles.length];
        for (int s = 0; s < styles.length; s++) {
            for (int c = 0; c < clips.length; c++) {
                if (clips[c].name().equals(styles[s])) {
                    map[s] = c;
                }
            }
        }
        styleClips = map;
    }

    /** The clip to play for one of the cosmetic's styles. */
    public int clipForStyle(int style) {
        return style >= 0 && style < styleClips.length ? styleClips[style] : 0;
    }

    public float[] scale() {
        return scale;
    }

    public float[] rotation() {
        return rotation;
    }

    public float[] pivot() {
        return pivot;
    }

    public float[] offset() {
        return offset;
    }

    /**
     * Where a clip is at {@code seconds}, in seconds into it: looping, or held on the last frame once it has
     * played. {@code speedFactor} 0 stays on the first frame.
     */
    public float clipTime(int clip, double seconds, float speedFactor) {
        Clip c = clips[clip];
        if (c.frameCount() <= 1 || speedFactor <= 0f) {
            return 0f;
        }
        double t = seconds * c.speed() * speedFactor;
        if (c.loop()) {
            double length = c.frameCount() / (double) c.frameRate();
            return (float) (t - Math.floor(t / length) * length);
        }
        return (float) Math.min(t, (c.frameCount() - 1) / (double) c.frameRate());
    }

    /** Where a clip is after {@code cycles} loops of it (only the fraction counts), in seconds into it. */
    public float clipTimeAtCycle(int clip, double cycles) {
        Clip c = clips[clip];
        if (c.frameCount() <= 1) {
            return 0f;
        }
        double length = (c.loop() ? c.frameCount() : c.frameCount() - 1) / (double) c.frameRate();
        return (float) ((cycles - Math.floor(cycles)) * length);
    }

    /**
     * The joints of a clip at {@code clipTime}: {@code joints * 7} floats, position then rotation
     * quaternion per joint. The array is reused; read it before the next call.
     */
    public float[] evaluate(int clip, float clipTime) {
        if (clip == paletteClip && clipTime == paletteTime) {
            return palette;
        }
        paletteClip = clip;
        paletteTime = clipTime;
        Clip c = clips[clip];
        float[] frames = c.frames();
        int frameCount = c.frameCount();
        if (frameCount <= 1) {
            System.arraycopy(frames, 0, palette, 0, palette.length);
            return palette;
        }
        float f = clipTime * c.frameRate();
        int first = (int) Math.floor(f);
        float blend = f - first;
        int a;
        int b;
        if (c.loop()) {
            a = Math.floorMod(first, frameCount);
            b = (a + 1) % frameCount;
        } else {
            a = Math.max(0, Math.min(frameCount - 1, first));
            b = Math.min(frameCount - 1, a + 1);
            if (first >= frameCount - 1) {
                blend = 0f;
            }
        }
        int oa = a * joints * STRIDE;
        int ob = b * joints * STRIDE;
        float keep = 1f - blend;
        for (int j = 0; j < joints; j++) {
            int o = j * STRIDE;
            int pa = oa + o;
            int pb = ob + o;
            palette[o] = frames[pa] * keep + frames[pb] * blend;
            palette[o + 1] = frames[pa + 1] * keep + frames[pb + 1] * blend;
            palette[o + 2] = frames[pa + 2] * keep + frames[pb + 2] * blend;
            // Normalised lerp along the shorter way round.
            float sign = frames[pa + 3] * frames[pb + 3] + frames[pa + 4] * frames[pb + 4]
                    + frames[pa + 5] * frames[pb + 5] + frames[pa + 6] * frames[pb + 6] < 0f ? -blend : blend;
            float x = frames[pa + 3] * keep + frames[pb + 3] * sign;
            float y = frames[pa + 4] * keep + frames[pb + 4] * sign;
            float z = frames[pa + 5] * keep + frames[pb + 5] * sign;
            float w = frames[pa + 6] * keep + frames[pb + 6] * sign;
            float inverse = 1f / (float) Math.sqrt(x * x + y * y + z * z + w * w);
            palette[o + 3] = x * inverse;
            palette[o + 4] = y * inverse;
            palette[o + 5] = z * inverse;
            palette[o + 6] = w * inverse;
        }
        return palette;
    }
}
