package io.github.georgwav.cosmetics.sol.render;

import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Where each first-person hand moved on screen since the last frame, so the hand glow's trail fills the whole path
 * between its positions: one smooth fade at any frame rate, not a copy of the hands per frame. The game's pose for the
 * hand (hand space to view space) is taken as it draws it, and seven points spread round the hand and what it holds
 * are projected the way the hands are (70 degrees up and down). The light pass gets, per hand, the best fitting move
 * from where things are now to where they were: a turn, a stretch and a shift, so a swing's path can be followed as an
 * arc. Screen positions here are in screen heights, across and up (so a turn is a true turn on any window shape).
 */
public final class HandMotion {
    /** The points of a hand's pose that are followed (blocks, in hand space): its grip and round it. */
    private static final float[][] POINTS = {{0f, 0f, 0f}, {0.4f, 0f, 0f}, {-0.4f, 0f, 0f}, {0f, 0.4f, 0f}, {0f, -0.4f, 0f},
            {0f, 0f, 0.4f}, {0f, 0f, -0.4f}};
    /** How many points of a hand are followed (see {@link #drawnAt}). */
    public static final int FOLLOWED = POINTS.length;
    /** The farthest a hand may move in a frame (screen heights) before it counts as a jump, not a move. */
    private static final float JUMP = 0.6f;
    private static final float[][] now = new float[2][FOLLOWED * 2];
    private static final float[][] before = new float[2][FOLLOWED * 2];
    private static final boolean[][] okNow = new boolean[2][FOLLOWED];
    private static final boolean[][] okBefore = new boolean[2][FOLLOWED];
    private static final boolean[] seenNow = new boolean[2];
    private static final boolean[] seenBefore = new boolean[2];
    /** The frames {@link #now} and {@link #before} are from. */
    private static long frameNow = -1, frameBefore = -2;
    private static final Vector4f p = new Vector4f();
    private static final float[] posed = new float[FOLLOWED * 3];

    private HandMotion() {
    }

    /** The game drew a hand ({@code right}: the right one) with this pose; on the render thread. */
    public static void drawn(boolean right, Matrix4f pose) {
        for (int i = 0; i < FOLLOWED; i++) {
            pose.transform(p.set(POINTS[i][0], POINTS[i][1], POINTS[i][2], 1f));
            posed[i * 3] = p.x;
            posed[i * 3 + 1] = p.y;
            posed[i * 3 + 2] = p.z;
        }
        drawnAt(right, posed);
    }

    /**
     * A hand drawn some other way (a knife draws its own): seven points that move with it, in the hands' pose space
     * (blocks), as {@link #drawn} follows round a hand's grip; on the render thread.
     */
    public static void drawnAt(boolean right, float[] points) {
        if (FrameInfo.frame != frameNow) {
            // The first hand of a frame: the last frame's hands become the ones before.
            for (int s = 0; s < 2; s++) {
                System.arraycopy(now[s], 0, before[s], 0, FOLLOWED * 2);
                System.arraycopy(okNow[s], 0, okBefore[s], 0, FOLLOWED);
                seenBefore[s] = seenNow[s];
                seenNow[s] = false;
            }
            frameBefore = frameNow;
            frameNow = FrameInfo.frame;
        }
        int side = right ? 0 : 1;
        float aspect = FrameInfo.width / Math.max(1f, FrameInfo.height);
        float f = 1f / (float) Math.tan(Math.toRadians(35.0));
        // The game draws the hands with the view's turn taken out of their pose and put back in the model-view matrix
        // (every version): that matrix takes the points to view space.
        Matrix4f modelView = RenderSystem.getModelViewStack();
        for (int i = 0; i < FOLLOWED; i++) {
            modelView.transform(p.set(points[i * 3], points[i * 3 + 1], points[i * 3 + 2], 1f));
            float x = p.x, y = p.y, z = p.z;
            // A point at the eye, or far off the screen, would throw the fit (an arm reaching past the camera).
            okNow[side][i] = z < -0.05f;
            if (okNow[side][i]) {
                float sx = (0.5f + 0.5f * f / aspect * x / -z) * aspect, sy = 0.5f + 0.5f * f * y / -z;
                okNow[side][i] = sx > -aspect && sx < 2f * aspect && sy > -1f && sy < 2f;
                now[side][i * 2] = sx;
                now[side][i * 2 + 1] = sy;
            }
        }
        seenNow[side] = true;
    }

    /**
     * After the hands are drawn: from {@code at}, 11 values for the right hand then 11 for the left. Whether it moved
     * (1 or 0); the move from now to where it was the frame before, as a turn (radians) and a stretch (a 2x2 matrix by
     * columns) about its middle now, which was at the given place before; its middle now; and how many steps the trail
     * takes along its path (one every few pixels).
     */
    public static void params(float[] v, int at) {
        boolean fresh = frameNow == FrameInfo.frame && frameBefore == frameNow - 1;
        for (int side = 0; side < 2; side++) {
            int o = at + side * 11;
            java.util.Arrays.fill(v, o, o + 11, 0f);
            v[o + 2] = 1f;
            v[o + 5] = 1f;
            if (fresh && seenNow[side] && seenBefore[side]) {
                fit(side, v, o);
            }
        }
    }

    /** The least-squares move from the points now to the same points before, split into a turn and a stretch. */
    private static void fit(int side, float[] v, int o) {
        float[] c = now[side], b = before[side];
        float cx = 0f, cy = 0f, bx = 0f, by = 0f;
        int n = 0;
        for (int i = 0; i < FOLLOWED; i++) {
            if (okNow[side][i] && okBefore[side][i]) {
                cx += c[i * 2];
                cy += c[i * 2 + 1];
                bx += b[i * 2];
                by += b[i * 2 + 1];
                n++;
            }
        }
        if (n < 3) {
            return;
        }
        cx /= n;
        cy /= n;
        bx /= n;
        by /= n;
        if (Math.hypot(bx - cx, by - cy) > JUMP) {
            return;
        }
        // L = (sum of db dc^T) times the inverse of (sum of dc dc^T).
        float sxx = 0f, sxy = 0f, syy = 0f, m00 = 0f, m01 = 0f, m10 = 0f, m11 = 0f;
        for (int i = 0; i < FOLLOWED; i++) {
            if (okNow[side][i] && okBefore[side][i]) {
                float dx = c[i * 2] - cx, dy = c[i * 2 + 1] - cy, ex = b[i * 2] - bx, ey = b[i * 2 + 1] - by;
                sxx += dx * dx;
                sxy += dx * dy;
                syy += dy * dy;
                m00 += ex * dx;
                m01 += ex * dy;
                m10 += ey * dx;
                m11 += ey * dy;
            }
        }
        float det = sxx * syy - sxy * sxy;
        if (Math.abs(det) < 1e-9f) {
            return;
        }
        float i00 = syy / det, i01 = -sxy / det, i11 = sxx / det;
        float l00 = m00 * i00 + m01 * i01, l01 = m00 * i01 + m01 * i11;
        float l10 = m10 * i00 + m11 * i01, l11 = m10 * i01 + m11 * i11;
        // The turn closest to L, and what's left of L once it's taken out (S = R(-a) L).
        float a = (float) Math.atan2(l10 - l01, l00 + l11);
        float cos = (float) Math.cos(-a), sin = (float) Math.sin(-a);
        v[o] = 1f;
        v[o + 1] = a;
        v[o + 2] = cos * l00 - sin * l10;
        v[o + 3] = sin * l00 + cos * l10;
        v[o + 4] = cos * l01 - sin * l11;
        v[o + 5] = sin * l01 + cos * l11;
        v[o + 6] = bx;
        v[o + 7] = by;
        v[o + 8] = cx;
        v[o + 9] = cy;
        // How far the farthest followed point moved, in 1080p pixels (half again, for the tip of a long item): a step
        // every 5 of them, 2 to 32 steps.
        float most = 0f;
        for (int i = 0; i < FOLLOWED; i++) {
            if (okNow[side][i] && okBefore[side][i]) {
                most = Math.max(most, (float) Math.hypot(c[i * 2] - b[i * 2], c[i * 2 + 1] - b[i * 2 + 1]));
            }
        }
        v[o + 10] = Math.max(2f, Math.min(32f, most * 1.5f * 1080f / 5f));
    }
}
