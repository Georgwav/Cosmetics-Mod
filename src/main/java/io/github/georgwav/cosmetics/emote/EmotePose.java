package io.github.georgwav.cosmetics.emote;

/**
 * Poses the player model for an emote. Version-independent: the game's six parts come in and go out as
 * {@code {x, y, z, xRot, yRot, zRot}} (pixels, radians, exactly the model part fields).
 *
 * <p>The game's parts are siblings, but emotes are made for the Bedrock player, where the head and arms hang
 * off the body, the body off the waist, and everything off the root. So when an emote moves a parent, its
 * children are carried along: each part ends up as (parents' movement) x (its own pose), turned back into
 * the six numbers the game's part has.
 */
public final class EmotePose {
    public static final int HEAD = 0;
    public static final int BODY = 1;
    public static final int RIGHT_ARM = 2;
    public static final int LEFT_ARM = 3;
    public static final int RIGHT_LEG = 4;
    public static final int LEFT_LEG = 5;
    public static final int PARTS = 6;

    private static final Emote.Bone[] BONES = {Emote.Bone.HEAD, Emote.Bone.BODY, Emote.Bone.RIGHT_ARM,
            Emote.Bone.LEFT_ARM, Emote.Bone.RIGHT_LEG, Emote.Bone.LEFT_LEG};

    private EmotePose() {
    }

    /**
     * Changes {@code parts} (the game's pose this frame, one {@code {x, y, z, xRot, yRot, zRot}} per part) to
     * the emote's pose at {@code time} seconds, blended with the game's pose by {@code weight} (0..1).
     *
     * @param rest each part's rest offset {@code {x, y, z}}, which the emote's offsets are added to
     */
    public static void apply(Emote emote, float time, float weight, float[][] parts, float[][] rest) {
        if (weight <= 0f) {
            return;
        }
        float w = Math.min(1f, weight);
        float[][] own = new float[PARTS][];
        float[][] posed = new float[PARTS][];
        for (int p = 0; p < PARTS; p++) {
            own[p] = parts[p].clone();
            posed[p] = pose(emote.channels(BONES[p]), time, w, parts[p], rest[p]);
        }

        // How the parents moved. Root and waist have no pose of their own in the game, so theirs start at zero.
        float[] root = parentMove(emote.channels(Emote.Bone.ROOT), time, w, emote.rootPivotY());
        float[] waist = parentMove(emote.channels(Emote.Bone.WAIST), time, w, Emote.WAIST_PIVOT_Y);
        float[] body = emote.channels(Emote.Bone.BODY) == null ? null
                : multiply(matrix(posed[BODY]), invertRigid(matrix(own[BODY])));
        float[] upper = waist == null ? root : root == null ? waist : multiply(root, waist);
        float[] arms = body == null ? upper : upper == null ? body : multiply(upper, body);

        for (int p = 0; p < PARTS; p++) {
            float[] parent = switch (p) {
                case BODY -> upper;
                case HEAD, RIGHT_ARM, LEFT_ARM -> arms;
                default -> root;
            };
            if (parent == null) {
                System.arraycopy(posed[p], 0, parts[p], 0, Emote.CHANNELS);
            } else {
                decompose(multiply(parent, matrix(posed[p])), parts[p]);
            }
        }
    }

    /** A part's own pose: each channel the emote has, blended with the game's value; the game's value elsewhere. */
    private static float[] pose(Channel[] channels, float time, float w, float[] own, float[] rest) {
        float[] out = own.clone();
        if (channels == null) {
            return out;
        }
        for (int c = 0; c < Emote.CHANNELS; c++) {
            Channel channel = channels[c];
            if (channel == null) {
                continue;
            }
            boolean offset = c < 3;
            float base = offset ? own[c] - rest[c] : own[c];
            float value = channel.sample(time, base);
            float target = offset ? rest[c] + value : value;
            out[c] = own[c] + (target - own[c]) * w;
        }
        return out;
    }

    /** The movement of a bone the game doesn't have (root, waist), turning around (0, pivotY, 0); null if none. */
    private static float[] parentMove(Channel[] channels, float time, float w, float pivotY) {
        if (channels == null) {
            return null;
        }
        float[] v = new float[Emote.CHANNELS];
        for (int c = 0; c < Emote.CHANNELS; c++) {
            v[c] = channels[c] == null ? 0f : channels[c].sample(time, 0f) * w;
        }
        // T(pivot + offset) * R * T(-pivot)
        float[] m = matrix(new float[]{v[0], pivotY + v[1], v[2], v[3], v[4], v[5]});
        float[] back = {1, 0, 0, 0, 1, 0, 0, 0, 1, 0, -pivotY, 0};
        return multiply(m, back);
    }

    // ---------------------------------------------------------------- 3x4 rigid transforms
    // {m00, m01, m02, m10, m11, m12, m20, m21, m22, tx, ty, tz}: p' = M p + t

    /** A part's transform as the game applies it: translate(x, y, z), then rotate Z * Y * X. */
    static float[] matrix(float[] pose) {
        double cx = Math.cos(pose[3]), sx = Math.sin(pose[3]);
        double cy = Math.cos(pose[4]), sy = Math.sin(pose[4]);
        double cz = Math.cos(pose[5]), sz = Math.sin(pose[5]);
        return new float[]{
                (float) (cz * cy), (float) (cz * sy * sx - sz * cx), (float) (cz * sy * cx + sz * sx),
                (float) (sz * cy), (float) (sz * sy * sx + cz * cx), (float) (sz * sy * cx - cz * sx),
                (float) (-sy), (float) (cy * sx), (float) (cy * cx),
                pose[0], pose[1], pose[2],
        };
    }

    /** {@code outer} after {@code inner}. */
    static float[] multiply(float[] outer, float[] inner) {
        float[] r = new float[12];
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                r[row * 3 + col] = outer[row * 3] * inner[col] + outer[row * 3 + 1] * inner[3 + col]
                        + outer[row * 3 + 2] * inner[6 + col];
            }
            r[9 + row] = outer[row * 3] * inner[9] + outer[row * 3 + 1] * inner[10] + outer[row * 3 + 2] * inner[11]
                    + outer[9 + row];
        }
        return r;
    }

    static float[] invertRigid(float[] m) {
        float[] r = new float[12];
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                r[row * 3 + col] = m[col * 3 + row];
            }
        }
        for (int row = 0; row < 3; row++) {
            r[9 + row] = -(r[row * 3] * m[9] + r[row * 3 + 1] * m[10] + r[row * 3 + 2] * m[11]);
        }
        return r;
    }

    /** Back to the six numbers: the offset, and Euler angles in the game's Z * Y * X order. */
    static void decompose(float[] m, float[] out) {
        out[0] = m[9];
        out[1] = m[10];
        out[2] = m[11];
        float r20 = Math.max(-1f, Math.min(1f, m[6]));
        if (Math.abs(r20) < 0.99999f) {
            out[4] = (float) Math.asin(-r20);
            out[3] = (float) Math.atan2(m[7], m[8]);
            out[5] = (float) Math.atan2(m[3], m[0]);
        } else {
            // Straight up or down: X and Z turn about the same axis; put it all on X.
            float sy = -r20;
            out[4] = (float) (Math.PI / 2) * Math.signum(sy);
            out[3] = (float) Math.atan2(m[1] * sy, m[4]);
            out[5] = 0f;
        }
    }
}
