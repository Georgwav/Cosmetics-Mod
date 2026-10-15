package io.github.georgwav.cosmetics.sol.atmosphere;

import io.github.georgwav.cosmetics.sol.Feature;
import io.github.georgwav.cosmetics.sol.Guard;
import io.github.georgwav.cosmetics.sol.render.FrameInfo;
import io.github.georgwav.cosmetics.sol.render.WorldDraw;
import io.github.georgwav.cosmetics.sol.theme.ThemeSpec;
import io.github.georgwav.cosmetics.sol.theme.ThemeState;
import io.github.georgwav.cosmetics.sol.theme.Tokens;
import io.github.georgwav.cosmetics.sol.util.Colors;
import io.github.georgwav.cosmetics.sol.util.Ease;
import io.github.georgwav.cosmetics.sol.util.Mulberry32;
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
    /**
     * Box half-sizes around the camera, in blocks: close enough that a theme's count fills the view like the
     * prototype's (whose counts are one screen's worth), deep enough to reach into the fog.
     */
    private static final float HX = 15f, HY = 9f, HZ = 15f;
    /** Prototype pixels (at a 560 px view) to blocks. */
    private static final float SIZE = 0.028f;

    private static final int PETAL = 0, BLOSSOM = 1, SNOW = 2, FLAKE = 3, EMBER = 4, DUST = 5, FIREFLY = 6,
            SPARKLE = 7, RAIN = 8, LEAF = 9, ASH = 10, NOTE = 11;

    private static int capacity;
    private static int count;
    private static float[] x, y, z, size, angle, flip, phase;
    /** The top of the ground under each particle (world y), for fading where it meets blocks. */
    private static float[] ground;
    private static int[] type, color;
    private static boolean[] hidden;
    private static String builtFor = "";
    private static int builtBudget = -1;
    private static int builtGeneration = -1;
    private static double lastX, lastY, lastZ;
    private static boolean placed;
    private static float lastTime = -1f;
    private static int heightCursor;

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

    private static final float[] uv = new float[4];

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

        // Screen pixels per block at one block away: what decides a sprite's size on screen.
        pxPerBlock = FrameInfo.height / (2f * (float) Math.tan(Math.toRadians(Math.max(1f, FrameInfo.fovY)) / 2f));
        // The particles are lit by the theme's light (the sun, the rune, the moon...): tinted toward it, and glowing
        // through when it's behind them; the theme's own light keeps them from going dark at night.
        float light = 0.35f + 0.65f * Math.max(1f - tk.night, KeyLight.strength * 0.85f);
        float sunUp = KeyLight.strength * Math.max(0.35f, KeyLight.visible);
        int sunTint = Colors.mix(0xFFFFFFFF, KeyLight.color, 0.45f * sunUp);
        int backColor = Colors.mix(KeyLight.color, 0xFFFFFFFF, 0.3f);
        float[] wind = theme.wind();

        // 1. Light: the glowing types (a wide halo and a bright core, drawn added, brighter than white where they
        // stack so the light pass blooms them), the big out-of-focus ones near the camera as soft discs, and the
        // glow of shaped particles with the sun behind them.
        WorldDraw.begin(WorldDraw.WORLD_ADD, ParticleSprites.glow());
        for (int i = 0; i < count; i++) {
            if (hidden[i]) {
                continue;
            }
            int k = type[i];
            float a = fade(i);
            if (a <= 0.01f) {
                continue;
            }
            float d = dist(i);
            if (glowing(k)) {
                float al = a * flicker(k, i, t, tk);
                if (al <= 0.01f) {
                    continue;
                }
                float r = size[i] * (k == DUST ? 2.2f : k == NOTE ? 1.6f : k == SPARKLE ? 1.8f : 3.2f);
                // Close to the camera a light goes out of focus: bigger, fainter, a disc.
                float blur = Ease.smoothstep(2.8f, 0.9f, d);
                if (blur > 0.02f && k != NOTE) {
                    float rb = r * (1f + 2.4f * blur);
                    float ab = al * (0.85f / (1f + 2.2f * blur));
                    ParticleSprites.glowUv(1, ParticleSprites.glowLevel(2f * rb * pxPerBlock / d), uv);
                    billboard(i, rb, rb, 0f, 1f, uv[0], uv[1], uv[2], uv[3], Colors.withAlpha(color[i], Math.min(1f, ab * blur)));
                    al *= 1f - blur;
                }
                ParticleSprites.glowUv(0, ParticleSprites.glowLevel(2f * r * pxPerBlock / d), uv);
                billboard(i, r, r, 0f, 1f, uv[0], uv[1], uv[2], uv[3], Colors.withAlpha(color[i], Math.min(1f, al)));
                // The core: smaller and whiter.
                float rc = r * 0.42f;
                ParticleSprites.glowUv(0, ParticleSprites.glowLevel(2f * rc * pxPerBlock / d), uv);
                billboard(i, rc, rc, 0f, 1f, uv[0], uv[1], uv[2], uv[3],
                        Colors.withAlpha(Colors.mix(color[i], 0xFFFFFFFF, 0.6f), Math.min(1f, al * 0.9f)));
            } else if (k != RAIN && sunUp > 0f) {
                float back = backlight(i, d) * sunUp;
                if (back > 0.02f) {
                    float r = size[i] * 1.7f;
                    ParticleSprites.glowUv(0, ParticleSprites.glowLevel(2f * r * pxPerBlock / d), uv);
                    billboard(i, r, r, 0f, 1f, uv[0], uv[1], uv[2], uv[3], Colors.withAlpha(backColor, Math.min(1f, 0.6f * back * a)));
                }
            }
        }
        WorldDraw.end();

        // 2. Shapes: petals, blossoms, leaves, ash, snow and flakes, smooth-edged, lit by the theme's sun and glowing
        // through when it's behind them.
        WorldDraw.begin(WorldDraw.WORLD_ALPHA, ParticleSprites.shapes());
        for (int i = 0; i < count; i++) {
            int k = type[i];
            if (hidden[i] || glowing(k) || k == RAIN) {
                continue;
            }
            float a = fade(i);
            if (a <= 0.01f) {
                continue;
            }
            float d = dist(i);
            float back = sunUp > 0f ? backlight(i, d) * sunUp : 0f;
            int c = Colors.multiply(color[i], sunTint);
            c = Colors.scale(c, light);
            if (back > 0f) {
                c = Colors.mix(c, backColor, 0.5f * back);
            }
            // Never a big blot in front of the camera: a shape stays within a few percent of the screen.
            float s = Math.min(size[i], 0.03f * FrameInfo.height * d / pxPerBlock);
            float sq = Math.max(0.18f, Math.abs((float) Math.cos(flip[i])));
            int shape;
            float w, h, rot = angle[i], squash = 1f;
            switch (k) {
                case PETAL -> { shape = ParticleSprites.PETAL; w = s * 0.62f; h = s; squash = sq; }
                case ASH -> { shape = ParticleSprites.ASH; w = s; h = s; squash = sq; }
                case BLOSSOM -> { shape = ParticleSprites.BLOSSOM; w = s; h = s; }
                case LEAF -> { shape = ParticleSprites.LEAF; w = s * 0.7f; h = s; squash = Math.max(0.2f, sq); }
                case SNOW -> { shape = ParticleSprites.SNOW; w = s * 0.6f; h = s * 0.6f; rot = 0f; }
                default -> { shape = ParticleSprites.FLAKE; w = s; h = s; }
            }
            ParticleSprites.shapeUv(shape, ParticleSprites.shapeLevel(2f * h * pxPerBlock / d), uv);
            billboard(i, w, h, rot, squash, uv[0], uv[1], uv[2], uv[3], Colors.withAlpha(c, Math.min(1f, a * Colors.a(c) / 255f)));
        }
        WorldDraw.end();

        // 3. Light with a shape: sparkles' four points and the music notes, added.
        WorldDraw.begin(WorldDraw.WORLD_ADD, ParticleSprites.shapes());
        for (int i = 0; i < count; i++) {
            int k = type[i];
            if (hidden[i] || (k != SPARKLE && k != NOTE)) {
                continue;
            }
            float a = fade(i) * flicker(k, i, t, tk);
            if (a <= 0.01f) {
                continue;
            }
            float d = dist(i);
            // Up close a light is only its soft bokeh (pass 1): the points fade out as it goes out of focus, and
            // never grow past a small sparkle on screen (a big hard-edged star in front of the camera was the
            // "big four-point star").
            a *= 1f - Ease.smoothstep(2.8f, 1.2f, d);
            if (a <= 0.01f) {
                continue;
            }
            float s = Math.min(size[i] * (k == NOTE ? 0.9f : 1.1f), 0.016f * FrameInfo.height * d / pxPerBlock);
            ParticleSprites.shapeUv(k == NOTE ? ParticleSprites.NOTE : ParticleSprites.STAR,
                    ParticleSprites.shapeLevel(2f * s * pxPerBlock / d), uv);
            billboard(i, s, s, 0f, 1f, uv[0], uv[1], uv[2], uv[3], Colors.withAlpha(Colors.mix(color[i], 0xFFFFFFFF, 0.35f), Math.min(1f, a)));
        }
        WorldDraw.end();

        // 4. Rain: thin streaks along the fall.
        boolean anyRain = false;
        for (int i = 0; i < count && !anyRain; i++) {
            anyRain = type[i] == RAIN;
        }
        if (anyRain) {
            WorldDraw.begin(WorldDraw.WORLD_ALPHA, null);
            for (int i = 0; i < count; i++) {
                if (hidden[i] || type[i] != RAIN) {
                    continue;
                }
                float a = fade(i);
                if (a > 0.01f) {
                    rainStreak(i, 0.55f, wind, Colors.withAlpha(Colors.scale(color[i], light), 0.45f * a));
                }
            }
            WorldDraw.end();
        }
    }

    private static float pxPerBlock = 700f;

    private static boolean glowing(int k) {
        return k == EMBER || k == DUST || k == FIREFLY || k == SPARKLE || k == NOTE;
    }

    /** The prototype's per-type twinkle for the glowing types. */
    private static float flicker(int k, int i, float t, Tokens tk) {
        float ph = phase[i];
        return switch (k) {
            case EMBER -> 0.55f + 0.45f * (float) Math.sin(t * 6f + ph);
            case DUST -> 0.35f + 0.4f * (0.5f + 0.5f * (float) Math.sin(t * 1.7f + ph));
            case FIREFLY -> {
                float s = Math.max(0f, (float) Math.sin(t * 0.9f + ph));
                yield s * s * s * (0.3f + 0.7f * tk.night);
            }
            case SPARKLE -> {
                float s = 0.5f + 0.5f * (float) Math.sin(t * 1.3f + ph);
                yield s * s * s * s;
            }
            case NOTE -> 0.35f * (1f + MusicSky.beat * 0.8f);
            default -> 1f;
        };
    }

    private static float dist(int i) {
        return Math.max(0.3f, (float) Math.sqrt(x[i] * x[i] + y[i] * y[i] + z[i] * z[i]));
    }

    /** How much the sun is behind a particle as you look at it (0 none, 1 right behind it). */
    private static float backlight(int i, float d) {
        float dot = (x[i] * KeyLight.x + y[i] * KeyLight.y + z[i] * KeyLight.z) / d;
        return Ease.smoothstep(0.8f, 0.985f, dot);
    }

    /**
     * Fades a particle out near the camera, at the box's edge (so wrapping is never seen) and where it meets the
     * ground, so nothing is ever cut off by a block.
     */
    private static float fade(int i) {
        float d = (float) Math.sqrt(x[i] * x[i] + y[i] * y[i] + z[i] * z[i]);
        // Lights go out of focus up close (drawn as bokeh); a petal or a leaf that close would just be a big blot.
        float near = glowing(type[i]) ? Ease.smoothstep(0.35f, 0.9f, d) : Ease.smoothstep(1.2f, 3f, d);
        float edge = 1f - Ease.smoothstep(HX * 0.65f, HX * 0.98f, Math.max(Math.abs(x[i]), Math.abs(z[i])));
        float vert = 1f - Ease.smoothstep(HY * 0.7f, HY, Math.abs(y[i]));
        float above = (float) (FrameInfo.camY + y[i] - ground[i]);
        float soft = Ease.smoothstep(0f, Math.max(0.25f, size[i] * 3f), above);
        return near * edge * vert * soft;
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
        // Fades along its length, and across its width to nothing at both sides.
        int tail = Colors.withAlpha(c, 0f);
        WorldDraw.vertex(ax, ay, az, 0.5f, 0.5f, c);
        WorldDraw.vertex(bx - nx, by - ny, bz, 0.5f, 0.5f, tail);
        WorldDraw.vertex(bx + nx, by + ny, bz, 0.5f, 0.5f, tail);
        WorldDraw.vertex(ax, ay, az, 0.5f, 0.5f, c);
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
            ground[i] = top;
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
        boolean notes = MusicSky.notes;
        if (key.equals(builtFor) && budget == builtBudget && gen == builtGeneration && Math.abs(scale - lastScale) < 0.05f
                && theme.particles() == lastEmitters && notes == lastNotes) {
            return;
        }
        builtFor = key;
        builtBudget = budget;
        builtGeneration = gen;
        lastScale = scale;
        lastEmitters = theme.particles();
        lastNotes = notes;
        java.util.List<ThemeSpec.Emitter> emitters = theme.particles();
        if (notes) {
            // The note particles visualizer: music notes in the accent and the glow among the theme's own.
            emitters = new java.util.ArrayList<>(emitters);
            emitters.add(new ThemeSpec.Emitter("note", 26, new int[]{tk.accent, tk.skyGlow, 0xFFFFFFFF}, 1.6f, 2.8f, null, 1f, 1f));
        }
        int want = 0;
        for (ThemeSpec.Emitter e : emitters) {
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
            ground = new float[capacity];
        }
        Mulberry32 r = new Mulberry32(theme.id().length() * 977 + theme.id().charAt(0));
        count = 0;
        for (ThemeSpec.Emitter e : emitters) {
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
                // A real spread of sizes: one light in eight is a big one.
                if ((kind == EMBER || kind == DUST || kind == FIREFLY) && r.next() < 0.125) {
                    size[i] *= 1.8f + 0.8f * (float) r.next();
                }
                type[i] = kind;
                hidden[i] = false;
                ground[i] = -1e9f;
            }
        }
        placed = false;
    }

    private static float lastScale = -1f;
    private static java.util.List<ThemeSpec.Emitter> lastEmitters;
    private static boolean lastNotes;

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
}
