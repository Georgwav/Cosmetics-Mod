package io.github.georgwav.cosmetics.sol.atmosphere;

import io.github.georgwav.cosmetics.sol.Feature;
import io.github.georgwav.cosmetics.sol.Guard;
import io.github.georgwav.cosmetics.sol.render.FrameInfo;
import io.github.georgwav.cosmetics.sol.render.LightPass;
import io.github.georgwav.cosmetics.sol.render.SolTexture;
import io.github.georgwav.cosmetics.sol.render.WorldDraw;
import io.github.georgwav.cosmetics.sol.theme.ThemeSpec;
import io.github.georgwav.cosmetics.sol.theme.ThemeState;
import io.github.georgwav.cosmetics.sol.util.Colors;
import io.github.georgwav.cosmetics.sol.util.PixelCanvas;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Light on the lens, drawn over the finished world: the sun's streak and seven ghosts (the prototype's
 * {@code drawSunFront}), the glare, Sakura's lens petals, Monsoon's lightning flash and the vignette. Screen
 * positions are drawn on a plane just in front of the camera. The flare fades when terrain hides the sun: a ray
 * toward it ten times a second, smoothed.
 */
public final class LensFlare {
    private static final float[][] GHOSTS = {{0.3f, 0.32f, 0xFFFFB347, 0}, {0.52f, 0.6f, 0xFF5EFFC1, 1},
            {0.72f, 0.22f, 0xFFC78BFF, 0}, {0.98f, 0.85f, 0xFF7FC4FF, 1}, {1.22f, 0.38f, 0xFFFF7AB8, 0},
            {1.5f, 1.15f, 0xFFFFD36B, 1}, {1.85f, 0.28f, 0xFF9CFF7A, 0}};
    private static final int[] GHOST_COLORS = {0xFFFFB347, 0xFF5EFFC1, 0xFFC78BFF, 0xFF7FC4FF, 0xFFFF7AB8, 0xFFFFD36B,
            0xFF9CFF7A};

    private static float occlusion = 1f;
    /** Frames the flare or the glare was drawn in (the scenario checks none are in a closed room). */
    public static int drawn;
    private static float occlusionTarget = 1f;
    private static float nextRay;
    private static SolTexture vignette;
    private static final float[] screen = new float[2];
    // Lens petals: screen fractions, size, angle, phase.
    private static final float[] petalX = {0.18f, 0.62f, 0.86f};
    private static final float[] petalY = {0.3f, 0.72f, 0.18f};
    private static final float[] petalA = {0.4f, 2.1f, 4.0f};

    private LensFlare() {
    }

    /** How much of the sun the terrain leaves showing (0..1, smoothed; from the 10 Hz ray toward it). */
    public static float occlusion() {
        return occlusion;
    }

    public static void render() {
        if (!FrameInfo.inWorld || !Atmosphere.active() || !Guard.on(Feature.FLARE)) {
            return;
        }
        Guard.run(Feature.FLARE, LensFlare::draw);
    }

    private static void draw() {
        Atmosphere at = Atmosphere.INSTANCE;
        ThemeSpec theme = ThemeState.entry().base();
        float W = FrameInfo.width, H = FrameInfo.height;
        updateOcclusion();

        boolean sunOn = SkyPass.drewThisFrame && theme.sun() != null && theme.eclipse() == null && SkyPass.sunVisible > 0.01f;
        boolean flareOn = sunOn && theme.sun().flare() && at.flare.get();
        boolean glareOn = sunOn && theme.sun().glare() && at.glare.get();
        if ((flareOn || glareOn) && FrameInfo.project(SkyPass.sunX * 100f, SkyPass.sunY * 100f, SkyPass.sunZ * 100f, screen)) {
            float x = screen[0], y = screen[1];
            // The sun's half-size in pixels.
            float s = SkyPass.sunS * SkyDraw.T / (2f * (float) Math.tan(Math.toRadians(FrameInfo.fovY) / 2f)) * H;
            float vis = SkyPass.sunVisible * occlusion * SkyOpen.get();
            // Off screen it fades over a sun's width.
            float off = Math.max(Math.max(-x, x - W), Math.max(-y, y - H));
            vis *= 1f - Math.max(0f, Math.min(1f, off / Math.max(1f, s * 3f)));
            if (vis > 0.005f) {
                drawn++;
                float dx = W / 2f - x, dy = H / 2f - y;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                float maxD = (float) Math.sqrt(W * W + H * H) * 0.5f;
                float px = s * 2f / 16f;
                if (flareOn) {
                    float str = vis * (0.65f + 0.35f * Math.max(0f, 1f - dist / maxD));
                    WorldDraw.begin(WorldDraw.SKY_ADD, SkyDraw.glow());
                    screenRect(x - W * 0.42f, y - s * 0.16f, x + W * 0.42f, y + s * 0.16f, Colors.withAlpha(SkyPass.sunCore, 0.4f * str));
                    for (int g = 0; g < GHOSTS.length; g++) {
                        float k = GHOSTS[g][0], f = GHOSTS[g][1];
                        float gx = x + dx * k, gy = y + dy * k;
                        float h = Math.max(px, Math.round(s * f / Math.max(px, 1e-3f)) * px);
                        float R = h * 2.2f;
                        screenRect(gx - R, gy - R, gx + R, gy + R, Colors.withAlpha(GHOST_COLORS[g], 0.1f * str));
                    }
                    WorldDraw.end();
                    WorldDraw.begin(WorldDraw.SKY_ADD, null);
                    for (int g = 0; g < GHOSTS.length; g++) {
                        float k = GHOSTS[g][0], f = GHOSTS[g][1];
                        boolean hollow = GHOSTS[g][3] > 0f;
                        float gx = x + dx * k, gy = y + dy * k;
                        float h = Math.max(px, Math.round(s * f / Math.max(px, 1e-3f)) * px);
                        int c = GHOST_COLORS[g];
                        if (hollow) {
                            float lw = Math.max(1f, px * 0.45f);
                            screenHollow(gx, gy, h, lw, Colors.withAlpha(c, 0.13f * str));
                            screenRect(gx - h, gy - h, gx + h, gy + h, Colors.withAlpha(c, 0.04f * str));
                        } else {
                            screenRect(gx - h, gy - h, gx + h, gy + h, Colors.withAlpha(c, 0.14f * str));
                        }
                    }
                    WorldDraw.end();
                }
                if (glareOn) {
                    float g = Math.max(0f, 1f - dist / maxD);
                    g = g * g * 0.32f * vis;
                    if (g > 0.005f) {
                        WorldDraw.begin(WorldDraw.SKY_ADD, null);
                        screenRect(0f, 0f, W, H, Colors.withAlpha(SkyPass.sunHalo, g));
                        WorldDraw.end();
                    }
                }
            }
        }
        lensPetals(theme);
        if (Extras.flash > 0f && SkyOpen.get() > 0.01f) {
            WorldDraw.begin(WorldDraw.SKY_ALPHA, null);
            // With the light pass lighting the world and the clouds, only a thin veil is left to add.
            float veil = LightPass.preset() == LightPass.HIGH ? 0.3f : 1f;
            screenRect(0f, 0f, W, H, Colors.withAlpha(0xFFDCE8FF, Extras.flash * veil * SkyOpen.get()));
            WorldDraw.end();
        }
        if (at.vignette.get()) {
            float R = Math.max(W, H) * 0.75f;
            float cx = W / 2f, cy = H * 0.55f;
            WorldDraw.begin(WorldDraw.SKY_ALPHA, vignette());
            screenRect(cx - R, cy - R, cx + R, cy + R, 0xFFFFFFFF);
            WorldDraw.end();
        }
    }

    /** Sakura's three lens petals: big soft blobs drifting across the screen. */
    private static void lensPetals(ThemeSpec theme) {
        ThemeSpec.Emitter lens = null;
        for (ThemeSpec.Emitter e : theme.particles()) {
            if ("lens".equals(e.type()) && e.count() > 0) {
                lens = e;
                break;
            }
        }
        float open = SkyOpen.get();
        if (lens == null || open < 0.01f) {
            return;
        }
        float W = FrameInfo.width, H = FrameInfo.height, t = FrameInfo.time;
        float sc = H / 560f;
        WorldDraw.begin(WorldDraw.SKY_ADD, SkyDraw.glow());
        for (int i = 0; i < Math.min(3, lens.count()); i++) {
            float x = ((petalX[i] + t * 0.012f) % 1.1f) * W;
            float y = ((petalY[i] + t * 0.0025f) % 1.05f) * H;
            float R = (lens.minSize() + (lens.maxSize() - lens.minSize()) * (i / 2f)) * sc;
            float a = petalA[i] + t * 0.3f;
            int c = Colors.withAlpha(lens.colors()[i % lens.colors().length], 0.26f * open);
            float cs = (float) Math.cos(a), sn = (float) Math.sin(a);
            screenPoint(x + (-R * cs - -R * 0.6f * sn), y + (-R * sn + -R * 0.6f * cs), 0f, 0f, c);
            screenPoint(x + (-R * cs - R * 0.6f * sn), y + (-R * sn + R * 0.6f * cs), 0f, 1f, c);
            screenPoint(x + (R * cs - R * 0.6f * sn), y + (R * sn + R * 0.6f * cs), 1f, 1f, c);
            screenPoint(x + (R * cs - -R * 0.6f * sn), y + (R * sn + -R * 0.6f * cs), 1f, 0f, c);
        }
        WorldDraw.end();
    }

    /** Ten times a second: is the sun behind terrain? Smoothed so the flare fades rather than blinks. */
    private static void updateOcclusion() {
        float t = FrameInfo.time;
        if (t >= nextRay) {
            nextRay = t + 0.1f;
            var mc = Minecraft.getInstance();
            var level = mc.level;
            var entity = mc.getCameraEntity();
            if (level != null && entity != null && SkyPass.sunY > -0.05f) {
                Vec3 from = new Vec3(FrameInfo.camX, FrameInfo.camY, FrameInfo.camZ);
                Vec3 to = from.add(SkyPass.sunX * 256.0, SkyPass.sunY * 256.0, SkyPass.sunZ * 256.0);
                HitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, entity));
                occlusionTarget = hit.getType() == HitResult.Type.MISS ? 1f : 0f;
            } else {
                occlusionTarget = 0f;
            }
        }
        occlusion += (occlusionTarget - occlusion) * Math.min(1f, FrameInfo.frameSeconds * 8f);
    }

    // A plane at depth 1 in front of the camera: screen pixels to view space.
    private static void screenPoint(float sx, float sy, float u, float v, int c) {
        float tanY = (float) Math.tan(Math.toRadians(FrameInfo.fovY) / 2.0);
        float tanX = tanY * FrameInfo.width / Math.max(1f, FrameInfo.height);
        float nx = sx / FrameInfo.width * 2f - 1f;
        float ny = 1f - sy / FrameInfo.height * 2f;
        WorldDraw.vertex(nx * tanX, ny * tanY, -1f, u, v, c);
    }

    private static void screenRect(float x0, float y0, float x1, float y1, int c) {
        screenPoint(x0, y0, 0f, 0f, c);
        screenPoint(x0, y1, 0f, 1f, c);
        screenPoint(x1, y1, 1f, 1f, c);
        screenPoint(x1, y0, 1f, 0f, c);
    }

    private static void screenHollow(float x, float y, float h, float w, int c) {
        float hw = w / 2f;
        screenRect(x - h - hw, y - h - hw, x + h + hw, y - h + hw, c);
        screenRect(x - h - hw, y + h - hw, x + h + hw, y + h + hw, c);
        screenRect(x - h - hw, y - h + hw, x - h + hw, y + h - hw, c);
        screenRect(x + h - hw, y - h + hw, x + h + hw, y + h - hw, c);
    }

    /** The prototype's vignette: clear inside a quarter of the radius, black at .32 at the edge. */
    private static SolTexture vignette() {
        if (vignette == null || vignette.released()) {
            PixelCanvas c = new PixelCanvas(256, 256);
            c.fillRect(0, 0, 256, 256, PixelCanvas.radial(128, 128, 128,
                    PixelCanvas.Stops.of(0f, 0x00000000, 0.26f, 0x00000000, 1f, 0x52000000)));
            int[] px = c.toArgb(null);
            // Corners beyond the circle stay at the edge's darkness.
            for (int yy = 0; yy < 256; yy++) {
                for (int xx = 0; xx < 256; xx++) {
                    float d = (float) Math.hypot(xx - 127.5, yy - 127.5);
                    if (d > 127f) {
                        px[yy * 256 + xx] = 0x52000000;
                    }
                }
            }
            vignette = SolTexture.of("vignette", 256, 256, true, px);
        }
        return vignette;
    }
}
