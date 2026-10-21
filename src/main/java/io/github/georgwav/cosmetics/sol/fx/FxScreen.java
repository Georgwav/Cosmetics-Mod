package io.github.georgwav.cosmetics.sol.fx;

import io.github.georgwav.cosmetics.sol.render.FrameInfo;
import io.github.georgwav.cosmetics.sol.render.WorldDraw;
import io.github.georgwav.cosmetics.sol.util.Colors;

/**
 * Flashes over the whole view (a kill's sky flash, a totem's edge flash): drawn just in front of the camera after the
 * level, in hard steps, never smoothly; none with calm motion. Render thread only.
 */
public final class FxScreen {
    /** The flash's steps: colour, alpha and how long each lasts. */
    private static int colour;
    private static float[] steps = new float[0];
    private static float stepTime, started = -1f;
    private static boolean edges;
    /** For the checks: flashes started. */
    public static int flashes;

    private FxScreen() {
    }

    /** A flash over the view in {@code argb}: {@code alphas} one after another, {@code each} seconds apiece. */
    public static void flash(int argb, float each, float... alphas) {
        if (FxWorld.calm()) {
            return;
        }
        colour = argb;
        steps = alphas;
        stepTime = each;
        started = FrameInfo.time;
        edges = false;
        flashes++;
    }

    /** A flash round the edges of the view only (the totem's), in steps. */
    public static void edgeFlash(int argb, float each, float... alphas) {
        flash(argb, each, alphas);
        edges = true;
    }

    static void render() {
        if (started < 0f) {
            return;
        }
        int step = (int) ((FrameInfo.time - started) / Math.max(1e-3f, stepTime));
        if (step >= steps.length || step < 0) {
            started = -1f;
            return;
        }
        float a = steps[step];
        if (a <= 0f) {
            return;
        }
        float w = FrameInfo.width, h = FrameInfo.height;
        WorldDraw.begin(WorldDraw.SKY_ALPHA, null);
        int c = Colors.withAlpha(colour, a);
        if (edges) {
            // Bands round the view, a twelfth of its height deep, in two tones.
            float d = Math.round(h / 12f), d2 = Math.round(h / 24f);
            rect(0f, 0f, w, d, Colors.withAlpha(colour, a * 0.5f));
            rect(0f, h - d, w, h, Colors.withAlpha(colour, a * 0.5f));
            rect(0f, d, d, h - d, Colors.withAlpha(colour, a * 0.5f));
            rect(w - d, d, w, h - d, Colors.withAlpha(colour, a * 0.5f));
            rect(0f, 0f, w, d2, c);
            rect(0f, h - d2, w, h, c);
            rect(0f, d2, d2, h - d2, c);
            rect(w - d2, d2, w, h - d2, c);
        } else {
            rect(0f, 0f, w, h, c);
        }
        WorldDraw.end();
    }

    private static void rect(float x0, float y0, float x1, float y1, int c) {
        point(x0, y0, c);
        point(x0, y1, c);
        point(x1, y1, c);
        point(x1, y0, c);
    }

    private static void point(float sx, float sy, int c) {
        float tanY = (float) Math.tan(Math.toRadians(FrameInfo.fovY) / 2.0);
        float tanX = tanY * FrameInfo.width / Math.max(1f, FrameInfo.height);
        float nx = sx / FrameInfo.width * 2f - 1f;
        float ny = 1f - sy / FrameInfo.height * 2f;
        WorldDraw.vertex(nx * tanX * 0.1f, ny * tanY * 0.1f, -0.1f, 0.5f, 0.5f, c);
    }
}
