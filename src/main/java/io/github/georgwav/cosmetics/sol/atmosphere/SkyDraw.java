package io.github.georgwav.cosmetics.sol.atmosphere;

import io.github.georgwav.cosmetics.sol.render.FrameInfo;
import io.github.georgwav.cosmetics.sol.render.SolTexture;
import io.github.georgwav.cosmetics.sol.render.WorldDraw;
import io.github.georgwav.cosmetics.sol.util.Sprites;
import org.joml.Vector3f;

/**
 * Draws the prototypes' 2D sky layers in the 3D sky. A layer is drawn on a "billboard": the plane facing the camera
 * through a direction in the sky, kept square to the horizon. Lengths on it are in the prototypes' units, fractions
 * of a 70° tall view (H), so a glow that covers a third of the prototype's frame covers the same part of the sky
 * here. Also the shared sky textures. Render thread only; no allocation per frame.
 */
public final class SkyDraw {
    /** Distance of sky geometry from the camera. */
    public static final float D = 100f;
    /** One H in tangent units: 2·tan(35°), the prototype frame's height at 70° FOV. */
    public static final float T = 1.4004199f;
    private static final float UNIT = D * T;

    private static SolTexture glow;
    private static SolTexture strip;
    private static SolTexture ray;

    // The billboard: centre, right and up in view space (one H long each).
    private static float cx, cy, cz, rx, ry, rz, ux, uy, uz;
    private static final Vector3f a = new Vector3f();
    private static final Vector3f b = new Vector3f();

    private SkyDraw() {
    }

    public static SolTexture glow() {
        if (glow == null || glow.released()) {
            glow = SolTexture.of("glow", 64, 64, true, Sprites.glow(0xFFFFFFFF).toArgb(null));
        }
        return glow;
    }

    public static SolTexture strip() {
        if (strip == null || strip.released()) {
            strip = SolTexture.of("strip", 1, 64, true, Sprites.strip(0xFFFFFFFF).toArgb(null));
        }
        return strip;
    }

    public static SolTexture ray() {
        if (ray == null || ray.released()) {
            ray = SolTexture.of("ray", 256, 64, true, Sprites.ray(0xFFFFFFFF).toArgb(null));
        }
        return ray;
    }

    /** One screen pixel, in H units. */
    public static float pixel() {
        float fov = (float) Math.toRadians(FrameInfo.fovY);
        return (2f * (float) Math.tan(fov / 2f) / Math.max(1, FrameInfo.height)) / T;
    }

    /**
     * Sets the billboard to the plane through world direction (x, y, z) (from the camera; need not be unit length).
     */
    public static void billboard(float x, float y, float z) {
        float len = (float) Math.sqrt(x * x + y * y + z * z);
        if (len < 1e-6f) {
            x = 0f;
            y = 1f;
            z = 0f;
            len = 1f;
        }
        x /= len;
        y /= len;
        z /= len;
        // right = dir × up (world up), up' = right × dir; near the zenith use east as right.
        float rX = -z;
        float rY = 0f;
        float rZ = x;
        float rl = (float) Math.sqrt(rX * rX + rZ * rZ);
        if (rl < 1e-4f) {
            rX = 1f;
            rZ = 0f;
            rl = 1f;
        }
        rX /= rl;
        rZ /= rl;
        float uX = rY * z - rZ * y;
        float uY = rZ * x - rX * z;
        float uZ = rX * y - rY * x;
        FrameInfo.view.transformDirection(a.set(x * D, y * D, z * D));
        cx = a.x;
        cy = a.y;
        cz = a.z;
        FrameInfo.view.transformDirection(a.set(rX * UNIT, rY * UNIT, rZ * UNIT));
        rx = a.x;
        ry = a.y;
        rz = a.z;
        FrameInfo.view.transformDirection(b.set(uX * UNIT, uY * UNIT, uZ * UNIT));
        ux = b.x;
        uy = b.y;
        uz = b.z;
    }

    /** One vertex at billboard point (x, y) in H units, y down as in the prototypes. */
    public static void point(float x, float y, float u, float v, int argb) {
        WorldDraw.vertex(cx + rx * x - ux * y, cy + ry * x - uy * y, cz + rz * x - uz * y, u, v, argb);
    }

    /** An axis-aligned rectangle on the billboard, with the whole texture. */
    public static void rect(float x0, float y0, float x1, float y1, int argb) {
        rect(x0, y0, x1, y1, 0f, 0f, 1f, 1f, argb);
    }

    public static void rect(float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1, int argb) {
        point(x0, y0, u0, v0, argb);
        point(x0, y1, u0, v1, argb);
        point(x1, y1, u1, v1, argb);
        point(x1, y0, u1, v0, argb);
    }

    /** A glow centred at (x, y) with radii rx, ry. */
    public static void glowAt(float x, float y, float radiusX, float radiusY, int argb) {
        rect(x - radiusX, y - radiusY, x + radiusX, y + radiusY, argb);
    }

    /**
     * A rectangle from (x0, y0) to (x1, y1) in a frame turned by {@code angle} radians around (ox, oy) (the canvas's
     * translate + rotate + drawImage).
     */
    public static void rotRect(float ox, float oy, float angle, float x0, float y0, float x1, float y1, int argb) {
        float c = (float) Math.cos(angle);
        float s = (float) Math.sin(angle);
        rotPoint(ox, oy, c, s, x0, y0, 0f, 0f, argb);
        rotPoint(ox, oy, c, s, x0, y1, 0f, 1f, argb);
        rotPoint(ox, oy, c, s, x1, y1, 1f, 1f, argb);
        rotPoint(ox, oy, c, s, x1, y0, 1f, 0f, argb);
    }

    private static void rotPoint(float ox, float oy, float c, float s, float x, float y, float u, float v, int argb) {
        point(ox + x * c - y * s, oy + x * s + y * c, u, v, argb);
    }

    /** A hollow square (stroked rect) of half-size h and line width w, centred at (x, y). */
    public static void hollowSquare(float x, float y, float h, float w, int argb) {
        float hw = w / 2f;
        rect(x - h - hw, y - h - hw, x + h + hw, y - h + hw, argb);
        rect(x - h - hw, y + h - hw, x + h + hw, y + h + hw, argb);
        rect(x - h - hw, y - h + hw, x - h + hw, y + h - hw, argb);
        rect(x + h - hw, y - h + hw, x + h + hw, y + h - hw, argb);
    }

    /** A ring of radius r and width w around (x, y), as {@code segments} thin quads. */
    public static void ring(float x, float y, float r, float w, int segments, int argb) {
        float r0 = r - w / 2f;
        float r1 = r + w / 2f;
        for (int i = 0; i < segments; i++) {
            float a0 = (float) (i * Math.PI * 2 / segments);
            float a1 = (float) ((i + 1) * Math.PI * 2 / segments);
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0);
            float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            point(x + c0 * r0, y + s0 * r0, 0f, 0f, argb);
            point(x + c0 * r1, y + s0 * r1, 0f, 1f, argb);
            point(x + c1 * r1, y + s1 * r1, 1f, 1f, argb);
            point(x + c1 * r0, y + s1 * r0, 1f, 0f, argb);
        }
    }

    /** A vertex at a world direction (from the camera) at the sky's distance. */
    public static void sphere(float x, float y, float z, float u, float v, int argb) {
        FrameInfo.view.transformDirection(a.set(x * D, y * D, z * D));
        WorldDraw.vertex(a.x, a.y, a.z, u, v, argb);
    }

    /** A vertex at altitude and azimuth (degrees; azimuth 0 = south, 90 = west, like the game's yaw). */
    public static void altAz(float altitude, float azimuth, float u, float v, int argb) {
        double al = Math.toRadians(altitude);
        double az = Math.toRadians(azimuth);
        float h = (float) Math.cos(al);
        sphere((float) (-Math.sin(az) * h), (float) Math.sin(al), (float) (Math.cos(az) * h), u, v, argb);
    }
}
