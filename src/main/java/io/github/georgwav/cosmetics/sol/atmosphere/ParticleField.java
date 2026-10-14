package io.github.georgwav.cosmetics.sol.atmosphere;

import io.github.georgwav.cosmetics.sol.Feature;
import io.github.georgwav.cosmetics.sol.Guard;
import io.github.georgwav.cosmetics.sol.render.FrameInfo;
import io.github.georgwav.cosmetics.sol.render.SolTexture;
import io.github.georgwav.cosmetics.sol.render.WorldDraw;
import io.github.georgwav.cosmetics.sol.theme.ThemeSpec;
import io.github.georgwav.cosmetics.sol.theme.ThemeState;
import io.github.georgwav.cosmetics.sol.theme.Tokens;
import io.github.georgwav.cosmetics.sol.util.Colors;
import io.github.georgwav.cosmetics.sol.util.Ease;
import io.github.georgwav.cosmetics.sol.util.Mulberry32;
import io.github.georgwav.cosmetics.sol.util.PixelCanvas;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The theme's particles in the world: a fixed pool of quads in a box around the camera that wraps as you move, so
 * the count never changes and nothing is allocated. Wind, sway and spin per type (the prototype's {@code step()}),
 * faded near the camera and toward the box's edge; glowing types are added, the rest blended. Falling types don't
 * show under a roof: each column is checked against the height map a few times a second.
 */
public final class ParticleField {
    /** Box half-sizes around the camera, in blocks. */
    private static final float HX = 22f, HY = 13f, HZ = 22f;
    /** Prototype pixels (at a 560 px view) to blocks. */
    private static final float SIZE = 0.028f;

    private static final int PETAL = 0, BLOSSOM = 1, SNOW = 2, FLAKE = 3, EMBER = 4, DUST = 5, FIREFLY = 6,
            SPARKLE = 7, RAIN = 8, LEAF = 9, ASH = 10, NOTE = 11;

    private static int capacity;
    private static int count;
    private static float[] x, y, z, size, angle, flip, phase;
    private static int[] type, color;
    private static boolean[] hidden;
    private static String builtFor = "";
    private static int builtBudget = -1;
    private static int builtGeneration = -1;
    private static double lastX, lastY, lastZ;
    private static boolean placed;
    private static float lastTime = -1f;
    private static int heightCursor;

    private static SolTexture atlas;

    private ParticleField() {
    }

    public static void clear() {
        count = 0;
        builtFor = "";
        placed = false;
    }

    /** Draws the particles after the level (render thread). */
    public static void render() {
        Atmosphere at = Atmosphere.INSTANCE;
        if (at == null || !Atmosphere.active() || !at.particles.get() || !FrameInfo.inWorld || !Guard.on(Feature.PARTICLES)) {
            return;
        }
        Guard.run(Feature.PARTICLES, () -> draw(at));
    }

    private static void draw(Atmosphere at) {
        ThemeSpec theme = ThemeState.entry().base();
        Tokens tk = ThemeState.tokens();
        int budget = at.particleBudget();
        ensure(theme, budget, tk);
        if (count == 0) {
            return;
        }
        float t = FrameInfo.time;
        float dt = lastTime < 0f ? 0f : Math.min(0.1f, Math.max(0f, t - lastTime));
        lastTime = t;
        follow();
        step(theme, dt, t);
        checkRoofs();

        float light = 0.35f + 0.65f * (1f - tk.night);
        float[] wind = theme.wind();
        // Glowing types first (added), then the rest (blended).
        WorldDraw.begin(WorldDraw.WORLD_ADD, SkyDraw.glow());
        for (int i = 0; i < count; i++) {
            int k = type[i];
            if (hidden[i] || !(k == EMBER || k == DUST || k == FIREFLY || k == SPARKLE || k == NOTE)) {
                continue;
            }
            float a = fade(i);
            if (a <= 0.01f) {
                continue;
            }
            float al = a;
            float ph = phase[i];
            switch (k) {
                case EMBER -> al *= 0.55f + 0.45f * (float) Math.sin(t * 6f + ph);
                case DUST -> al *= 0.35f + 0.4f * (0.5f + 0.5f * (float) Math.sin(t * 1.7f + ph));
                case FIREFLY -> {
                    float s = Math.max(0f, (float) Math.sin(t * 0.9f + ph));
                    al *= s * s * s * (0.3f + 0.7f * tk.night);
                }
                case SPARKLE -> {
                    float s = 0.5f + 0.5f * (float) Math.sin(t * 1.3f + ph);
                    al *= s * s * s * s;
                }
                case NOTE -> al *= 0.35f * (1f + MusicSky.beat * 0.8f);
                default -> {
                }
            }
            if (al <= 0.01f) {
                continue;
            }
            float r = size[i] * (k == DUST ? 2.2f : k == NOTE ? 1.6f : 3.2f);
            billboard(i, r, r, 0f, 1f, 0f, 0f, 1f, 1f, Colors.withAlpha(color[i], Math.min(1f, al)));
        }
        WorldDraw.end();
        WorldDraw.begin(WorldDraw.WORLD_ALPHA, atlas());
        for (int i = 0; i < count; i++) {
            int k = type[i];
            if (hidden[i] || k == EMBER || k == DUST || k == FIREFLY) {
                continue;
            }
            float a = fade(i);
            if (a <= 0.01f) {
                continue;
            }
            int c = Colors.withAlpha(Colors.scale(color[i], light), Math.min(1f, a));
            float s = size[i];
            switch (k) {
                case PETAL -> billboard(i, s, s * 0.58f, angle[i], Math.max(0.18f, Math.abs((float) Math.cos(flip[i]))), 0f, 0f, 0.125f, 1f, c);
                case ASH -> billboard(i, s, s * 0.7f, angle[i], Math.max(0.18f, Math.abs((float) Math.cos(flip[i]))), 0.125f, 0f, 0.25f, 1f, c);
                case BLOSSOM -> billboard(i, s, s, angle[i], 1f, 0.25f, 0f, 0.375f, 1f, c);
                case LEAF -> billboard(i, s * 0.62f, s, angle[i], Math.max(0.2f, Math.abs((float) Math.cos(flip[i]))), 0.375f, 0f, 0.5f, 1f, c);
                case SNOW -> billboard(i, s * 0.6f, s * 0.6f, 0f, 1f, 0.5f, 0f, 0.625f, 1f, c);
                case FLAKE -> billboard(i, s, s, angle[i], 1f, 0.625f, 0f, 0.75f, 1f, c);
                case RAIN -> {
                    // A streak along the fall, slanted by the wind.
                    float len = 0.55f;
                    rainStreak(i, len, wind, Colors.withAlpha(color[i], 0.5f * a));
                }
                case NOTE -> billboard(i, s * 0.9f, s * 0.9f, 0f, 1f, 0.875f, 0f, 1f, 1f, Colors.withAlpha(color[i], 0.92f * a));
                case SPARKLE -> billboard(i, s, s, 0f, 1f, 0.75f, 0f, 0.875f, 1f,
                        Colors.withAlpha(color[i], a * (float) Math.pow(0.5f + 0.5f * (float) Math.sin(t * 1.3f + phase[i]), 4)));
                default -> {
                }
            }
        }
        WorldDraw.end();
    }

    /** Fades a particle out near the camera and at the box's edge (so wrapping is never seen). */
    private static float fade(int i) {
        float d2 = x[i] * x[i] + y[i] * y[i] + z[i] * z[i];
        float d = (float) Math.sqrt(d2);
        float near = Ease.smoothstep(0.7f, 1.8f, d);
        float edge = 1f - Ease.smoothstep(HX * 0.65f, HX * 0.98f, Math.max(Math.abs(x[i]), Math.abs(z[i])));
        float vert = 1f - Ease.smoothstep(HY * 0.7f, HY, Math.abs(y[i]));
        return near * edge * vert;
    }

    /** A camera-facing quad: half-width w, half-height h, turned by {@code rot}, squashed sideways by {@code squash}. */
    private static void billboard(int i, float w, float h, float rot, float squash, float u0, float v0, float u1, float v1,
                                  int c) {
        float vx = FrameInfo.view.m00() * x[i] + FrameInfo.view.m10() * y[i] + FrameInfo.view.m20() * z[i];
        float vy = FrameInfo.view.m01() * x[i] + FrameInfo.view.m11() * y[i] + FrameInfo.view.m21() * z[i];
        float vz = FrameInfo.view.m02() * x[i] + FrameInfo.view.m12() * y[i] + FrameInfo.view.m22() * z[i];
        float cs = (float) Math.cos(rot), sn = (float) Math.sin(rot);
        w *= squash;
        corner(vx, vy, vz, -w, -h, cs, sn, u0, v1, c);
        corner(vx, vy, vz, -w, h, cs, sn, u0, v0, c);
        corner(vx, vy, vz, w, h, cs, sn, u1, v0, c);
        corner(vx, vy, vz, w, -h, cs, sn, u1, v1, c);
    }

    private static void corner(float vx, float vy, float vz, float dx, float dy, float cs, float sn, float u, float v, int c) {
        WorldDraw.vertex(vx + dx * cs - dy * sn, vy + dx * sn + dy * cs, vz, u, v, c);
    }

    private static void rainStreak(int i, float len, float[] wind, int c) {
        float tx = wind[0] * 0.06f, tz = wind[2] * 0.06f;
        float x0 = x[i], y0 = y[i], z0 = z[i];
        float x1 = x0 + tx * len, y1 = y0 + len, z1 = z0 + tz * len;
        float w = 0.012f;
        // Width across the view: perpendicular to the streak in view space.
        var view = FrameInfo.view;
        float ax = view.m00() * x0 + view.m10() * y0 + view.m20() * z0;
        float ay = view.m01() * x0 + view.m11() * y0 + view.m21() * z0;
        float az = view.m02() * x0 + view.m12() * y0 + view.m22() * z0;
        float bx = view.m00() * x1 + view.m10() * y1 + view.m20() * z1;
        float by = view.m01() * x1 + view.m11() * y1 + view.m21() * z1;
        float bz = view.m02() * x1 + view.m12() * y1 + view.m22() * z1;
        float dx = bx - ax, dy = by - ay;
        float l = (float) Math.sqrt(dx * dx + dy * dy);
        if (l < 1e-5f) {
            return;
        }
        float nx = -dy / l * w, ny = dx / l * w;
        WorldDraw.vertex(ax - nx, ay - ny, az, 0.75f, 1f, c);
        WorldDraw.vertex(bx - nx, by - ny, bz, 0.75f, 0f, c);
        WorldDraw.vertex(bx + nx, by + ny, bz, 0.875f, 0f, Colors.withAlpha(c, 0f));
        WorldDraw.vertex(ax + nx, ay + ny, az, 0.875f, 1f, c);
    }

    // ------------------------------------------------------------------ motion

    /** Moves everything by the camera's movement and wraps it back into the box. */
    private static void follow() {
        double cx = FrameInfo.camX, cy = FrameInfo.camY, cz = FrameInfo.camZ;
        if (!placed) {
            lastX = cx;
            lastY = cy;
            lastZ = cz;
            placed = true;
            return;
        }
        float dx = (float) (cx - lastX), dy = (float) (cy - lastY), dz = (float) (cz - lastZ);
        lastX = cx;
        lastY = cy;
        lastZ = cz;
        if (dx == 0f && dy == 0f && dz == 0f) {
            return;
        }
        boolean teleport = Math.abs(dx) > HX || Math.abs(dz) > HZ || Math.abs(dy) > HY;
        for (int i = 0; i < count; i++) {
            x[i] = wrap(x[i] - dx, HX);
            y[i] = wrap(y[i] - dy, HY);
            z[i] = wrap(z[i] - dz, HZ);
            if (teleport) {
                hidden[i] = false;
            }
        }
    }

    private static float wrap(float v, float h) {
        float span = h * 2f;
        if (v < -h) {
            v += span * (float) Math.ceil((-h - v) / span);
        } else if (v >= h) {
            v -= span * (float) Math.ceil((v - h + 1e-4f) / span);
        }
        return v;
    }

    /** The prototype's step(), in blocks per second. */
    private static void step(ThemeSpec theme, float dt, float t) {
        if (dt <= 0f) {
            return;
        }
        float[] wind = theme.wind();
        float wx = wind[0], wy = wind[1], wz = wind[2];
        for (int i = 0; i < count; i++) {
            float w = (float) Math.sin(t * 0.7f + phase[i]);
            float sway = (float) Math.sin(t * 1.1f + phase[i] * 1.7f);
            switch (type[i]) {
                case PETAL -> {
                    y[i] -= 0.6f * dt;
                    x[i] += (wx * 0.9f + 0.25f * w) * dt;
                    z[i] += (wz * 0.9f + 0.18f * sway) * dt;
                    angle[i] += 1.2f * dt;
                    flip[i] += 2f * dt;
                }
                case BLOSSOM -> {
                    y[i] -= 0.48f * dt;
                    x[i] += (wx * 0.7f + 0.18f * w) * dt;
                    z[i] += (wz * 0.7f + 0.12f * sway) * dt;
                    angle[i] += 0.6f * dt;
                }
                case SNOW, FLAKE -> {
                    y[i] -= 0.5f * dt;
                    x[i] += (wx * 0.3f + 0.12f * w) * dt;
                    z[i] += (wz * 0.3f + 0.1f * sway) * dt;
                    angle[i] += 0.4f * dt;
                }
                case RAIN -> {
                    y[i] -= 9f * dt;
                    x[i] += wx * 0.6f * dt;
                    z[i] += wz * 0.6f * dt;
                }
                case LEAF -> {
                    y[i] -= 0.62f * dt;
                    x[i] += (wx * 1.1f + 0.4f * w) * dt;
                    z[i] += (wz * 1.1f + 0.3f * sway) * dt;
                    angle[i] += 1.6f * dt;
                    flip[i] += 2.4f * dt;
                }
                case ASH -> {
                    y[i] -= 0.28f * dt;
                    x[i] += (wx * 0.3f + 0.12f * w) * dt;
                    z[i] += (wz * 0.3f + 0.1f * sway) * dt;
                    angle[i] += 0.5f * dt;
                    flip[i] += 1.2f * dt;
                }
                case EMBER -> {
                    y[i] += (0.45f + wy) * dt;
                    x[i] += (wx * 0.4f + 0.16f * w) * dt;
                    z[i] += (wz * 0.4f + 0.12f * sway) * dt;
                }
                case DUST -> {
                    y[i] += 0.06f * dt;
                    x[i] += (wx * 0.15f + 0.08f * w) * dt;
                    z[i] += (wz * 0.15f + 0.06f * sway) * dt;
                }
                case FIREFLY -> {
                    x[i] += (float) Math.sin(t * 0.3f + phase[i]) * 0.35f * dt;
                    z[i] += (float) Math.cos(t * 0.27f + phase[i]) * 0.35f * dt;
                    y[i] += (float) Math.sin(t * 0.5f + phase[i] * 2f) * 0.12f * dt;
                }
                case NOTE -> {
                    y[i] += 0.3f * dt;
                    x[i] += 0.16f * w * dt;
                }
                default -> {
                }
            }
            x[i] = wrap(x[i], HX);
            y[i] = wrap(y[i], HY);
            z[i] = wrap(z[i], HZ);
        }
    }

    /**
     * Hides falling particles under a roof and keeps fireflies low over the ground, a slice of the pool per frame.
     */
    private static void checkRoofs() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || count == 0) {
            return;
        }
        int per = Math.max(32, count / 6);
        for (int n = 0; n < per; n++) {
            int i = heightCursor = (heightCursor + 1) % count;
            int bx = (int) Math.floor(FrameInfo.camX + x[i]);
            int bz = (int) Math.floor(FrameInfo.camZ + z[i]);
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
            double wy = FrameInfo.camY + y[i];
            if (type[i] == FIREFLY) {
                double want = top + 0.3 + (phase[i] / 6.283) * 2.2;
                y[i] = (float) (want - FrameInfo.camY);
                hidden[i] = Math.abs(y[i]) > HY;
            } else {
                hidden[i] = wy < top;
            }
        }
    }

    // ------------------------------------------------------------------ pool

    private static void ensure(ThemeSpec theme, int budget, Tokens tk) {
        int gen = io.github.georgwav.cosmetics.sol.theme.ThemeRegistry.generation();
        float scale = tk.particleScale;
        String key = theme.id();
        if (key.equals(builtFor) && budget == builtBudget && gen == builtGeneration && Math.abs(scale - lastScale) < 0.05f
                && theme.particles() == lastEmitters) {
            return;
        }
        builtFor = key;
        builtBudget = budget;
        builtGeneration = gen;
        lastScale = scale;
        lastEmitters = theme.particles();
        int want = 0;
        for (ThemeSpec.Emitter e : theme.particles()) {
            want += e.count();
        }
        // The prototype's counts fill one view; the box around the camera takes about five views' worth.
        float k = want == 0 ? 0f : Math.min(5f * scale, budget / (float) want);
        capacity = Math.max(capacity, budget);
        if (x == null || x.length < capacity) {
            x = new float[capacity];
            y = new float[capacity];
            z = new float[capacity];
            size = new float[capacity];
            angle = new float[capacity];
            flip = new float[capacity];
            phase = new float[capacity];
            type = new int[capacity];
            color = new int[capacity];
            hidden = new boolean[capacity];
        }
        Mulberry32 r = new Mulberry32(theme.id().length() * 977 + theme.id().charAt(0));
        count = 0;
        for (ThemeSpec.Emitter e : theme.particles()) {
            int n = Math.min(capacity - count, Math.round(e.count() * k));
            int kind = kindOf(e.type());
            if (kind < 0) {
                continue;
            }
            for (int j = 0; j < n; j++) {
                int i = count++;
                x[i] = (float) (r.next() * 2 - 1) * HX;
                y[i] = (float) (r.next() * 2 - 1) * HY;
                z[i] = (float) (r.next() * 2 - 1) * HZ;
                phase[i] = (float) (r.next() * 6.283);
                angle[i] = (float) (r.next() * 6.283);
                flip[i] = (float) (r.next() * 6.283);
                color[i] = e.colors()[(int) (r.next() * e.colors().length) % e.colors().length];
                size[i] = (e.minSize() + (e.maxSize() - e.minSize()) * (float) r.next()) * SIZE;
                type[i] = kind;
                hidden[i] = false;
            }
        }
        placed = false;
    }

    private static float lastScale = -1f;
    private static java.util.List<ThemeSpec.Emitter> lastEmitters;

    private static int kindOf(String name) {
        return switch (name) {
            case "petal" -> PETAL;
            case "blossom" -> BLOSSOM;
            case "snow" -> SNOW;
            case "flake" -> FLAKE;
            case "ember" -> EMBER;
            case "dust" -> DUST;
            case "firefly" -> FIREFLY;
            case "sparkle" -> SPARKLE;
            case "rain" -> RAIN;
            case "leaf" -> LEAF;
            case "ash" -> ASH;
            case "note" -> NOTE;
            // Lens petals are drawn on the screen with the flare.
            default -> -1;
        };
    }

    /** The particle shapes, white, 8 cells of 32x32: petal, ash, blossom, leaf, snow, flake, streak/sparkle, note. */
    private static SolTexture atlas() {
        if (atlas != null && !atlas.released()) {
            return atlas;
        }
        PixelCanvas c = new PixelCanvas(256, 32);
        int white = 0xFFFFFFFF;
        c.fillEllipse(16, 16, 15, 9, PixelCanvas.solid(white));
        c.fillEllipse(48, 16, 14, 10, PixelCanvas.solid(white));
        for (int i = 0; i < 5; i++) {
            double an = i * 1.2566;
            c.fillEllipse(80 + (float) Math.cos(an) * 8.5f, 16 + (float) Math.sin(an) * 8.5f, 6.5f, 6.5f, PixelCanvas.solid(white));
        }
        c.fillEllipse(80, 16, 4.5f, 4.5f, PixelCanvas.solid(0xFFFFF1F6));
        c.fillPolygon(new float[]{112, 122, 112, 102}, new float[]{1, 16, 31, 16}, PixelCanvas.solid(white));
        c.line(112, 2, 112, 30, 1.2f, PixelCanvas.solid(0xFFB08070));
        c.fillEllipse(144, 16, 14, 14, PixelCanvas.solid(white));
        for (int i = 0; i < 3; i++) {
            double an = i * 1.0472;
            float dx = (float) Math.cos(an) * 14f, dy = (float) Math.sin(an) * 14f;
            c.line(176 - dx, 16 - dy, 176 + dx, 16 + dy, 2.6f, PixelCanvas.solid(white));
        }
        // Cell 6: a sparkle cross (the rain streak uses its middle column).
        c.fillRect(193, 15, 30, 2, PixelCanvas.solid(white));
        c.fillRect(207, 1, 2, 30, PixelCanvas.solid(white));
        String[] note = {"...##.", "...#.#", "...#..", "...#..", ".###..", "####..", ".##..."};
        for (int r = 0; r < note.length; r++) {
            for (int q = 0; q < note[r].length(); q++) {
                if (note[r].charAt(q) == '#') {
                    c.fillRect(224 + 4 + q * 4, 2 + r * 4, 4, 4, PixelCanvas.solid(white));
                }
            }
        }
        atlas = SolTexture.of("particles", 256, 32, true, c.toArgb(null));
        return atlas;
    }
}
