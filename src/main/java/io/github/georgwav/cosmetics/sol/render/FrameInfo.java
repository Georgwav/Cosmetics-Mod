package io.github.georgwav.cosmetics.sol.render;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * What Sol needs to know about the frame being drawn: the camera, its view rotation and projection, the window,
 * the time. Filled at the start of the level render ({@code LevelRenderMixin}) and read by the sky, the particles,
 * the lens flare and the HUD. Render thread only; nothing here is allocated per frame.
 */
public final class FrameInfo {
    /** Camera position in the world. */
    public static double camX, camY, camZ;
    /** The camera's rotation (world to view) as a matrix, and its forward, up and left vectors in the world. */
    public static final Matrix4f view = new Matrix4f();
    public static final Quaternionf rotation = new Quaternionf();
    public static final Vector3f forward = new Vector3f(0f, 0f, -1f);
    public static final Vector3f up = new Vector3f(0f, 1f, 0f);
    /** The projection the level is drawn with (with view bobbing), when known; else a plain one from the FOV. */
    public static final Matrix4f projection = new Matrix4f();
    /** Vertical field of view in degrees. */
    public static float fovY = 70f;
    /** Framebuffer size in pixels. */
    public static int width = 854, height = 480;
    /** How far into the game tick this frame is (0..1), set as the level starts drawing. */
    public static float partialTick;
    /** Vanilla's time of day fraction (0 = noon, 0.25 = sunset) and the day number (the moon's phase). */
    public static float timeOfDay;
    public static long day;
    /** Days the world has run (its game time, never turned back by /time or sleep), with the fraction. */
    public static double days;
    /** The game's sun angle in radians (0 at noon), the moon phase (0 full .. 7) and 1 − rain, as the sky uses them. */
    public static float sunAngle;
    public static int moonPhase;
    public static float rainBrightness = 1f;
    /** Render distance in blocks. */
    public static float renderDistance = 128f;
    /** Rain and thunder levels, 0..1. */
    public static float rain, thunder;
    /** Whether a world is being drawn this frame, and whether the sky is the overworld's kind. */
    public static boolean inWorld, normalSky;
    /** The camera is under water, in lava or powder snow, or blinded: the sky isn't drawn. */
    public static boolean skyHidden;
    /** Seconds since the game started, for animation (monotonic). */
    public static float time;
    public static float frameSeconds = 1f / 60f;
    public static long frame;
    private static long lastNanos;

    private static final Vector4f scratch = new Vector4f();
    private static final Vector3f dir = new Vector3f();
    private static final Quaternionf inverse = new Quaternionf();

    private FrameInfo() {
    }

    /** Advances the clock; called once per frame. */
    public static void tick() {
        long now = System.nanoTime();
        if (lastNanos != 0L) {
            frameSeconds = Math.min(0.1f, Math.max(0f, (now - lastNanos) / 1e9f));
        }
        lastNanos = now;
        time += frameSeconds;
        frame++;
    }

    /** Sets the view from the camera's rotation (the camera's own quaternion, world from view). */
    public static void setRotation(Quaternionf cameraRotation) {
        rotation.set(cameraRotation);
        // World to view is the inverse of the camera's rotation.
        view.rotation(inverse.set(cameraRotation).conjugate());
        cameraRotation.transform(forward.set(0f, 0f, -1f));
        cameraRotation.transform(up.set(0f, 1f, 0f));
    }

    /** Transforms a camera-relative world direction or position into view space, into {@code out}. */
    public static Vector3f toView(float x, float y, float z, Vector3f out) {
        return view.transformPosition(out.set(x, y, z));
    }

    /**
     * Projects a world direction (from the camera) to the screen: x and y in pixels from the top left, and whether
     * it is in front of the camera. Returns false when behind.
     */
    public static boolean project(float dx, float dy, float dz, float[] outXY) {
        view.transformPosition(dir.set(dx, dy, dz));
        scratch.set(dir.x, dir.y, dir.z, 1f);
        projection.transform(scratch);
        if (scratch.w <= 1e-4f) {
            return false;
        }
        float nx = scratch.x / scratch.w;
        float ny = scratch.y / scratch.w;
        outXY[0] = (nx * 0.5f + 0.5f) * width;
        outXY[1] = (1f - (ny * 0.5f + 0.5f)) * height;
        return true;
    }

    /** Pixels per degree at the screen's centre, vertically. */
    public static float pixelsPerDegree() {
        return height / Math.max(1f, fovY);
    }
}
