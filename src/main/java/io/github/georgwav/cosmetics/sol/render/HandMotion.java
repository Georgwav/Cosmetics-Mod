package io.github.georgwav.cosmetics.sol.render;

import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Where each first-person hand moved on screen since the last frame, so the hand glow's trail fills the whole path
 * between its positions: one smooth fade at any frame rate, not a copy of the hands per frame. The game's pose for the
 * hand (hand space to view space) is taken as it draws it, and three points of it are projected the way the hands are
 * (70 degrees up and down). The light pass gets, per hand, the map from where things are now to where they were.
 */
public final class HandMotion {
    /** The three points of a hand's pose that are followed (blocks, in hand space). */
    private static final float[][] POINTS = {{0f, 0f, 0f}, {0.2f, 0f, 0f}, {0f, 0.2f, 0f}};
    /** The farthest a hand's anchor may move in a frame (screen heights) before it counts as a jump, not a move. */
    private static final float JUMP = 0.5f;
    private static final float[][] now = new float[2][6];
    private static final float[][] before = new float[2][6];
    private static final boolean[] seenNow = new boolean[2];
    private static final boolean[] seenBefore = new boolean[2];
    /** The frames {@link #now} and {@link #before} are from. */
    private static long frameNow = -1, frameBefore = -2;
    private static final Vector4f p = new Vector4f();

    private HandMotion() {
    }

    /** The game drew a hand ({@code right}: the right one) with this pose; on the render thread. */
    public static void drawn(boolean right, Matrix4f pose) {
        if (FrameInfo.frame != frameNow) {
            // The first hand of a frame: the last frame's hands become the ones before.
            for (int s = 0; s < 2; s++) {
                System.arraycopy(now[s], 0, before[s], 0, 6);
                seenBefore[s] = seenNow[s];
                seenNow[s] = false;
            }
            frameBefore = frameNow;
            frameNow = FrameInfo.frame;
        }
        int side = right ? 0 : 1;
        float aspect = FrameInfo.width / Math.max(1f, FrameInfo.height);
        float f = 1f / (float) Math.tan(Math.toRadians(35.0));
        for (int i = 0; i < 3; i++) {
            pose.transform(p.set(POINTS[i][0], POINTS[i][1], POINTS[i][2], 1f));
            if (p.z > -0.01f) {
                return;
            }
            now[side][i * 2] = 0.5f + 0.5f * f / aspect * p.x / -p.z;
            now[side][i * 2 + 1] = 0.5f + 0.5f * f * p.y / -p.z;
        }
        seenNow[side] = true;
    }

    /**
     * After the hands are drawn: from {@code at}, for the right hand then the left, whether it moved (1 or 0), the map
     * from now to where it was the frame before (a 2x2 matrix by columns, then the offset, in screen units), its anchor
     * now, and how many steps the trail takes along its path (one every few pixels).
     */
    public static void params(float[] v, int at) {
        boolean fresh = frameNow == FrameInfo.frame && frameBefore == frameNow - 1;
        for (int side = 0; side < 2; side++) {
            int o = at + side * 10;
            java.util.Arrays.fill(v, o, o + 10, 0f);
            v[o + 1] = 1f;
            v[o + 4] = 1f;
            float[] c = now[side], b = before[side];
            if (fresh && seenNow[side] && seenBefore[side] && Math.hypot(c[0] - b[0], c[1] - b[1]) < JUMP) {
                // The map A with A(c_i) = b_i: [b1 - b0, b2 - b0] times the inverse of [c1 - c0, c2 - c0].
                float cx1 = c[2] - c[0], cy1 = c[3] - c[1], cx2 = c[4] - c[0], cy2 = c[5] - c[1];
                float det = cx1 * cy2 - cx2 * cy1;
                if (Math.abs(det) > 1e-7f) {
                    float bx1 = b[2] - b[0], by1 = b[3] - b[1], bx2 = b[4] - b[0], by2 = b[5] - b[1];
                    float i00 = cy2 / det, i01 = -cx2 / det, i10 = -cy1 / det, i11 = cx1 / det;
                    float a00 = bx1 * i00 + bx2 * i10, a01 = bx1 * i01 + bx2 * i11;
                    float a10 = by1 * i00 + by2 * i10, a11 = by1 * i01 + by2 * i11;
                    v[o] = 1f;
                    v[o + 1] = a00;
                    v[o + 2] = a10;
                    v[o + 3] = a01;
                    v[o + 4] = a11;
                    v[o + 5] = b[0] - (a00 * c[0] + a01 * c[1]);
                    v[o + 6] = b[1] - (a10 * c[0] + a11 * c[1]);
                    // How far things a sword's length from the hand moved (in 1080p pixels; a turn moves the tip more
                    // than the grip): a step every 5 of them, 2 to 24 steps.
                    float aspect = FrameInfo.width / Math.max(1f, FrameInfo.height);
                    float most = 0f;
                    for (int i = 0; i < 4; i++) {
                        float x = c[0] + (i == 0 ? 0.35f : i == 1 ? -0.35f : 0f) / aspect;
                        float y = c[1] + (i == 2 ? 0.35f : i == 3 ? -0.35f : 0f);
                        float dx = a00 * x + a01 * y + v[o + 5] - x, dy = a10 * x + a11 * y + v[o + 6] - y;
                        most = Math.max(most, (float) Math.hypot(dx * aspect, dy));
                    }
                    v[o + 9] = Math.max(2f, Math.min(24f, most * 1080f / 5f));
                }
            }
            v[o + 7] = fresh && seenNow[side] ? c[0] : -10f;
            v[o + 8] = fresh && seenNow[side] ? c[1] : -10f;
        }
    }
}
