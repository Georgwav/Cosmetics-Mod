package io.github.georgwav.cosmetics.sol.atmosphere;

import io.github.georgwav.cosmetics.sol.Feature;
import io.github.georgwav.cosmetics.sol.Guard;
import io.github.georgwav.cosmetics.sol.render.FrameInfo;
import io.github.georgwav.cosmetics.sol.render.WorldDraw;
import io.github.georgwav.cosmetics.sol.util.Colors;
import io.github.georgwav.cosmetics.sol.util.Ease;
import io.github.georgwav.cosmetics.sol.util.Mulberry32;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;

/**
 * The glitter path: the light (a low sun, the moon, the burning ring) reflected on the water toward you as a road
 * of twinkling glints. Every half second the water surface is found in a narrowing cone toward the light; each
 * frame its glints twinkle there, added in the light's colour, brighter the nearer the light is to the horizon.
 */
public final class GlitterPath {
    private static final int MAX = 220;
    private static final double[] gx = new double[MAX], gz = new double[MAX];
    private static final float[] gy = new float[MAX], ph = new float[MAX], sz = new float[MAX];
    private static int count;
    private static float nextScan = -1f;
    /** The light's horizontal direction and its perpendicular at the last scan (the glints lie across it). */
    private static float dirX, dirZ = 1f, acrossX = 1f, acrossZ;
    private static final Mulberry32 random = new Mulberry32(17);
    private static final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

    private GlitterPath() {
    }

    /** How strongly the path shows now: a light low over the horizon, in sight. */
    private static float strength() {
        float alt = (float) Math.toDegrees(Math.asin(Math.max(-1f, Math.min(1f, KeyLight.y))));
        return KeyLight.strength * Ease.smoothstep(0.05f, 0.4f, KeyLight.visible) * Ease.smoothstep(-2f, 3f, alt)
                * (1f - Ease.smoothstep(26f, 55f, alt)) * (1f - FrameInfo.rain * 0.8f);
    }

    public static void render() {
        Atmosphere at = Atmosphere.INSTANCE;
        if (at == null || !Atmosphere.active() || !FrameInfo.inWorld || !Guard.on(Feature.PARTICLES)) {
            return;
        }
        Guard.run(Feature.PARTICLES, GlitterPath::draw);
    }

    private static void draw() {
        float k = strength() * Atmosphere.intensity();
        if (k <= 0.01f) {
            count = 0;
            return;
        }
        float t = FrameInfo.time;
        if (t >= nextScan || t < nextScan - 2f) {
            nextScan = t + 0.5f;
            scan();
        }
        if (count == 0) {
            return;
        }
        var view = FrameInfo.view;
        int base = Colors.mix(KeyLight.color, 0xFFFFFFFF, 0.45f);
        road(view, base, k);
        WorldDraw.begin(WorldDraw.WORLD_ADD, null);
        for (int i = 0; i < count; i++) {
            float x = (float) (gx[i] - FrameInfo.camX), y = (float) (gy[i] - FrameInfo.camY), z = (float) (gz[i] - FrameInfo.camZ);
            float d = (float) Math.sqrt(x * x + y * y + z * z);
            // A sharp twinkle: mostly dark, a glint now and then; nearer ones bigger and dimmer, far ones finer.
            float tw = (float) Math.pow(Math.max(0.0, Math.sin(t * (2.2f + (i % 5) * 0.37f) + ph[i])), 4.0);
            float a = k * tw * (0.6f + 0.4f * Ease.smoothstep(6f, 40f, d));
            if (a <= 0.01f) {
                continue;
            }
            float s = sz[i] * (0.8f + 0.035f * d);
            int c = Colors.withAlpha(base, Math.min(1f, a));
            // Flat on the water: a short streak across the path, so far glints still read from a low eye.
            float ax = acrossX * s * 2.2f, az = acrossZ * s * 2.2f, lx = dirX * s * 1.4f, lz = dirZ * s * 1.4f;
            corner(view, x - ax - lx, y, z - az - lz, c);
            corner(view, x - ax + lx, y, z - az + lz, c);
            corner(view, x + ax + lx, y, z + az + lz, c);
            corner(view, x + ax - lx, y, z + az - lz, c);
        }
        WorldDraw.end();
    }

    /**
     * The road of light under the glints: a soft glow lying on the water toward the light, widening with distance.
     * It lies just under the shore's sand top, so the depth test keeps it on the water.
     */
    private static void road(org.joml.Matrix4f view, int base, float k) {
        float y = (float) (gy[0] - FrameInfo.camY) + 0.02f;
        float near = 4f, far = 90f;
        float wn = 1.2f + near * 0.09f, wf = 1.2f + far * 0.09f;
        int c = Colors.withAlpha(base, Math.min(1f, 0.28f * k));
        float nx = dirX * near, nz = dirZ * near, fx = dirX * far, fz = dirZ * far;
        WorldDraw.begin(WorldDraw.WORLD_ADD, SkyDraw.glow());
        cornerUv(view, nx - acrossX * wn, y, nz - acrossZ * wn, 0f, 1f, c);
        cornerUv(view, nx + acrossX * wn, y, nz + acrossZ * wn, 1f, 1f, c);
        cornerUv(view, fx + acrossX * wf, y, fz + acrossZ * wf, 1f, 0f, c);
        cornerUv(view, fx - acrossX * wf, y, fz - acrossZ * wf, 0f, 0f, c);
        WorldDraw.end();
    }

    private static void cornerUv(org.joml.Matrix4f view, float x, float y, float z, float u, float v, int c) {
        WorldDraw.vertex(view.m00() * x + view.m10() * y + view.m20() * z, view.m01() * x + view.m11() * y + view.m21() * z,
                view.m02() * x + view.m12() * y + view.m22() * z, u, v, c);
    }

    private static void corner(org.joml.Matrix4f view, float x, float y, float z, int c) {
        WorldDraw.vertex(view.m00() * x + view.m10() * y + view.m20() * z, view.m01() * x + view.m11() * y + view.m21() * z,
                view.m02() * x + view.m12() * y + view.m22() * z, 0.5f, 0.5f, c);
    }

    /** Finds water surfaces in a cone toward the light: wide near you, narrowing to a line far off. */
    private static void scan() {
        var level = Minecraft.getInstance().level;
        count = 0;
        if (level == null) {
            return;
        }
        float hx = KeyLight.x, hz = KeyLight.z;
        float hl = (float) Math.sqrt(hx * hx + hz * hz);
        if (hl < 0.05f) {
            return;
        }
        hx /= hl;
        hz /= hl;
        float px = -hz, pz = hx;
        dirX = hx;
        dirZ = hz;
        acrossX = px;
        acrossZ = pz;
        int cy = (int) Math.floor(FrameInfo.camY);
        for (float d = 3f; d < 80f && count < MAX; d += 0.9f) {
            float spread = 0.5f + d * 0.06f;
            for (int j = 0; j < 3 && count < MAX; j++) {
                float lat = (float) (random.next() * 2.0 - 1.0) * spread;
                double wx = FrameInfo.camX + hx * d + px * lat;
                double wz = FrameInfo.camZ + hz * d + pz * lat;
                int bx = (int) Math.floor(wx), bz = (int) Math.floor(wz);
                for (int y = cy + 2; y >= cy - 14; y--) {
                    pos.set(bx, y, bz);
                    if (level.getFluidState(pos).is(FluidTags.WATER)) {
                        pos.set(bx, y + 1, bz);
                        if (level.getBlockState(pos).isAir()) {
                            gx[count] = wx;
                            gy[count] = y + 0.9f;
                            gz[count] = wz;
                            ph[count] = (float) (random.next() * 6.283);
                            sz[count] = 0.05f + (float) random.next() * 0.06f;
                            count++;
                        }
                        break;
                    }
                    if (!level.getBlockState(pos).isAir()) {
                        break;
                    }
                }
            }
        }
    }
}
