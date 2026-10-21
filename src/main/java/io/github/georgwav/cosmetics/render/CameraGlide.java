package io.github.georgwav.cosmetics.render;

/**
 * Camera › F5 glide's path, on its own (CameraTweaks keeps the state): where the camera is a fraction {@code k} (0 to 1)
 * of the way through a glide, as an offset from your eyes. Out to third person it starts just behind your head
 * ({@link #NEAR}) and slows into the game's place; back to first person it speeds in from where it was to just behind
 * your head (the last bit is the switch itself); home from the freecam it eases from the freecam's place to the game's.
 */
public final class CameraGlide {
    /** How long a glide takes. */
    public static final float SECONDS = 0.25f;
    /** How far behind your eyes a glide starts or ends: just outside your head. */
    public static final double NEAR = 0.6;

    private CameraGlide() {
    }

    /**
     * Out: the camera at offset (x, y, z) from your eyes (the game's third-person place), {@code k} of the way out;
     * false when it's too close to glide (a wall right behind you).
     */
    public static boolean out(double k, double x, double y, double z, double[] o) {
        double len = Math.sqrt(x * x + y * y + z * z);
        if (len < NEAR) {
            return false;
        }
        double s = 1.0 - (1.0 - k) * (1.0 - k);
        double d = (NEAR + (len - NEAR) * s) / len;
        o[0] = x * d;
        o[1] = y * d;
        o[2] = z * d;
        return true;
    }

    /** In: from offset (x, y, z) where the camera was, {@code k} of the way in; false when it was already that close. */
    public static boolean in(double k, double x, double y, double z, double[] o) {
        double len = Math.sqrt(x * x + y * y + z * z);
        if (len < NEAR) {
            return false;
        }
        double d = (len - (len - NEAR) * k * k) / len;
        o[0] = x * d;
        o[1] = y * d;
        o[2] = z * d;
        return true;
    }

    /**
     * Home: from offset (fx, fy, fz) (the freecam's place) to offset (tx, ty, tz) (the game's), {@code k} of the way;
     * into first person (tx, ty, tz all 0) it ends just behind your head, on the freecam's side.
     */
    public static void home(double k, double fx, double fy, double fz, double tx, double ty, double tz, boolean firstPerson, double[] o) {
        if (firstPerson) {
            double len = Math.sqrt(fx * fx + fy * fy + fz * fz);
            if (len > NEAR) {
                tx = fx / len * NEAR;
                ty = fy / len * NEAR;
                tz = fz / len * NEAR;
            }
        }
        double s = k * k * (3.0 - 2.0 * k);
        o[0] = fx + (tx - fx) * s;
        o[1] = fy + (ty - fy) * s;
        o[2] = fz + (tz - fz) * s;
    }
}
