package io.github.georgwav.cosmetics.sol.fx;

/**
 * Lightning: a jagged path from one point to another by midpoint displacement, with branches forking off it, each
 * thinner than what it grew from. Built into scratch arrays from a seed (the same seed, the same bolt), then drawn as
 * glowing lines: a wide glow in the effect's colour and a white-hot core. Render thread only; nothing allocated.
 */
public final class Bolts {
    private static final int MAX = 768;
    /** The segments: each from (ax, ay, az) to (bx, by, bz), with its width (1 = the main channel). */
    private static final float[] AX = new float[MAX], AY = new float[MAX], AZ = new float[MAX];
    private static final float[] BX = new float[MAX], BY = new float[MAX], BZ = new float[MAX];
    private static final float[] WIDTH = new float[MAX];
    /** How far down the strike a segment is (0 at the start, 1 at the strike point): it shows once the strike gets there. */
    private static final float[] ORDER = new float[MAX];
    private static int count;
    /** A path's points while it's built. */
    private static final float[] PX = new float[129], PY = new float[129], PZ = new float[129];

    private Bolts() {
    }

    /** How many segments the last bolt built has. */
    public static int count() {
        return count;
    }

    /**
     * Builds a bolt from (x0, y0, z0) to (x1, y1, z1): {@code jag} is how far it wanders (a share of each piece's
     * length), {@code branches} how many forks grow off it (each may fork once more).
     */
    public static void build(int seed, float x0, float y0, float z0, float x1, float y1, float z1, float jag, int branches) {
        count = 0;
        int n = path(seed, x0, y0, z0, x1, y1, z1, jag, 6);
        add(n, 1f, 0f);
        // Forks: from points along the upper two thirds, angling out and onward.
        float mainLen = (float) Math.sqrt(sq(x1 - x0) + sq(y1 - y0) + sq(z1 - z0));
        float dirX = (x1 - x0) / mainLen, dirY = (y1 - y0) / mainLen, dirZ = (z1 - z0) / mainLen;
        for (int b = 0; b < branches; b++) {
            int at = 4 + (int) (FxDraw.hash(seed, 300 + b) * (n * 0.62f));
            at = Math.min(n - 2, at);
            float sx = PXsaved(at, 0), sy = PXsaved(at, 1), sz = PXsaved(at, 2);
            float len = mainLen * (0.18f + 0.22f * FxDraw.hash(seed, 310 + b));
            // Onward along the bolt, pushed out sideways.
            float ox = FxDraw.signed(seed, 320 + b), oy = FxDraw.signed(seed, 330 + b) * 0.4f, oz = FxDraw.signed(seed, 340 + b);
            float ex = sx + (dirX * 0.7f + ox * 0.9f) * len, ey = sy + (dirY * 0.7f + oy) * len, ez = sz + (dirZ * 0.7f + oz * 0.9f) * len;
            int m = path(seed * 31 + b * 7 + 1, sx, sy, sz, ex, ey, ez, jag * 1.1f, 4);
            add(m, 0.5f, at / (n - 1f));
            // A twig off the branch, sometimes.
            if (FxDraw.hash(seed, 350 + b) < 0.6f && m > 6) {
                int at2 = 2 + (int) (FxDraw.hash(seed, 360 + b) * (m - 4));
                float tx = PX[at2], ty = PY[at2], tz = PZ[at2];
                float l2 = len * 0.45f;
                float qx = FxDraw.signed(seed, 370 + b), qz = FxDraw.signed(seed, 380 + b);
                int k = path(seed * 17 + b * 3 + 5, tx, ty, tz, tx + (dirX * 0.6f + qx) * l2, ty + (dirY * 0.7f - 0.2f) * l2,
                        tz + (dirZ * 0.6f + qz) * l2, jag * 1.2f, 3);
                add(k, 0.28f, at / (n - 1f) + 0.1f);
            }
        }
    }

    /** The main path's points are copied out before branches overwrite the scratch path. */
    private static final float[] MAIN_X = new float[129], MAIN_Y = new float[129], MAIN_Z = new float[129];

    private static float PXsaved(int i, int axis) {
        return axis == 0 ? MAIN_X[i] : axis == 1 ? MAIN_Y[i] : MAIN_Z[i];
    }

    /** A jagged path into PX/PY/PZ: 2^depth pieces; returns its point count. */
    private static int path(int seed, float x0, float y0, float z0, float x1, float y1, float z1, float jag, int depth) {
        int n = (1 << depth) + 1;
        PX[0] = x0;
        PY[0] = y0;
        PZ[0] = z0;
        PX[n - 1] = x1;
        PY[n - 1] = y1;
        PZ[n - 1] = z1;
        float len = (float) Math.sqrt(sq(x1 - x0) + sq(y1 - y0) + sq(z1 - z0));
        int k = 0;
        for (int step = n - 1; step > 1; step /= 2) {
            float amount = jag * len * step / (n - 1);
            for (int i = 0; i + step < n; i += step) {
                int mid = i + step / 2;
                PX[mid] = (PX[i] + PX[i + step]) * 0.5f + FxDraw.signed(seed, 1000 + k * 3) * amount;
                PY[mid] = (PY[i] + PY[i + step]) * 0.5f + FxDraw.signed(seed, 1001 + k * 3) * amount * 0.5f;
                PZ[mid] = (PZ[i] + PZ[i + step]) * 0.5f + FxDraw.signed(seed, 1002 + k * 3) * amount;
                k++;
            }
        }
        return n;
    }

    private static void add(int n, float width, float from) {
        boolean main = count == 0;
        for (int i = 0; i + 1 < n && count < MAX; i++) {
            AX[count] = PX[i];
            AY[count] = PY[i];
            AZ[count] = PZ[i];
            BX[count] = PX[i + 1];
            BY[count] = PY[i + 1];
            BZ[count] = PZ[i + 1];
            // Thinner toward a fork's end.
            WIDTH[count] = width * (main ? 1f : 1f - 0.6f * i / (n - 1f));
            ORDER[count] = main ? i / (n - 1f) : Math.min(1f, from + 0.25f * i / (n - 1f));
            count++;
        }
        if (main) {
            System.arraycopy(PX, 0, MAIN_X, 0, n);
            System.arraycopy(PY, 0, MAIN_Y, 0, n);
            System.arraycopy(PZ, 0, MAIN_Z, 0, n);
        }
    }

    /**
     * Draws the last bolt built: in the glow pass a wide glow ({@code glowWidth}) in {@code glow} and a hot core
     * ({@code coreWidth}) in {@code core}; in the body pass a thin white line so it reads against a bright sky too.
     * {@code upTo} (0..1) draws only that much of it, from the start: the strike racing down.
     */
    public static void draw(FxOut out, float coreWidth, float glowWidth, int core, int glow, float upTo) {
        boolean glowPass = out.glowPass();
        if (glowPass) {
            // The glow over runs of four pieces: one wide stroke where the bolt only wanders a little.
            for (int i = 0; i < count; i += 4) {
                if (ORDER[i] > upTo) {
                    continue;
                }
                int j = i;
                while (j + 1 < count && j < i + 3 && WIDTH[j + 1] == WIDTH[j] && AX[j + 1] == BX[j] && AY[j + 1] == BY[j]) {
                    j++;
                }
                float w = WIDTH[i];
                Vfx.line(out, FxSoft.LINE, AX[i], AY[i], AZ[i], BX[j], BY[j], BZ[j], glowWidth * (0.4f + 0.6f * w), glow, glow);
            }
        }
        for (int i = 0; i < count; i++) {
            if (ORDER[i] > upTo) {
                continue;
            }
            float w = WIDTH[i];
            if (glowPass) {
                Vfx.line(out, FxSoft.TUBE, AX[i], AY[i], AZ[i], BX[i], BY[i], BZ[i], coreWidth * (0.5f + 0.5f * w) * 2.2f, core, core);
            } else {
                Vfx.line(out, FxSoft.TUBE, AX[i], AY[i], AZ[i], BX[i], BY[i], BZ[i], coreWidth * (0.4f + 0.6f * w),
                        Vfx.a(0xFFFFFFFF, 0.85f), Vfx.a(0xFFFFFFFF, 0.85f));
            }
        }
    }

    /** The point where the last bolt's main channel ends (its strike point), into {@code out}. */
    public static void end(float[] out) {
        out[0] = MAIN_X[64];
        out[1] = MAIN_Y[64];
        out[2] = MAIN_Z[64];
    }

    private static float sq(float v) {
        return v * v;
    }
}
