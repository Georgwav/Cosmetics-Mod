package io.github.georgwav.cosmetics.sol.fx;

/**
 * The effects' soft pictures, made here at load and packed into one smoothly filtered atlas: the light the effects are
 * made of. A wide glow and a hot core for bloom, a lens flare, a spark streak to stretch along a particle's motion,
 * line profiles (a soft line, a neon tube, a shock front) that beams, bolts, trails and rings are drawn with, smoke
 * puffs, flame tongues, crystal shards, a heart, a star and a diamond with their own shading, a thin ring, a magic
 * circle, a scorch mark and a flowing noise strip for beams. White pictures (shaded in grey where a shape has light
 * and shadow) that the effect's colours tint. Plain arithmetic: the GPU copy is made where it's drawn.
 */
public final class FxSoft {
    /** A region of the atlas: u0, v0, u1, v1. */
    public static final class Region {
        public final String name;
        final int x, y, w, h;
        public float u0, v0, u1, v1;

        Region(String name, int x, int y, int w, int h) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }
    }

    static final int W = 1024, H = 1024;

    // Row 1: the round lights, 128 square.
    public static final Region GLOW = new Region("glow", 0, 0, 128, 128);
    public static final Region HOT = new Region("hot", 130, 0, 128, 128);
    public static final Region FLARE = new Region("flare", 260, 0, 128, 128);
    public static final Region RING = new Region("ring", 390, 0, 128, 128);
    public static final Region DOT = new Region("dot", 520, 0, 64, 64);
    public static final Region DOT_SOFT = new Region("dot_soft", 586, 0, 64, 64);
    public static final Region STAR6 = new Region("star6", 652, 0, 128, 128);
    public static final Region ORB = new Region("orb", 782, 0, 64, 64);
    public static final Region BUBBLE = new Region("bubble", 848, 0, 96, 96);
    public static final Region SQUARE = new Region("square", 946, 0, 16, 16);
    // Row 2: streaks and line profiles, then the shapes.
    public static final Region SPARK = new Region("spark", 0, 130, 128, 32);
    public static final Region LINE = new Region("line", 130, 130, 64, 16);
    public static final Region TUBE = new Region("tube", 196, 130, 64, 16);
    public static final Region FRONT = new Region("front", 262, 130, 64, 16);
    public static final Region FADE = new Region("fade", 328, 130, 64, 16);
    /** A strip of flowing noise, two tiles wide: a window half its width slides along it without a seam. */
    public static final Region NOISE = new Region("noise", 0, 164, 512, 64);
    public static final Region HEART = new Region("heart", 520, 130, 64, 64);
    public static final Region STAR = new Region("star", 586, 130, 64, 64);
    public static final Region DIAMOND = new Region("diamond", 652, 130, 64, 64);
    public static final Region CROSS = new Region("cross", 718, 130, 64, 64);
    public static final Region FEATHER = new Region("feather", 784, 130, 64, 96);
    public static final Region SKULL = new Region("skull", 850, 130, 64, 64);
    public static final Region NOTE = new Region("note", 916, 130, 64, 64);
    // Row 3: smoke and flames.
    public static final Region[] SMOKE = {
            new Region("smoke_0", 0, 230, 96, 96), new Region("smoke_1", 98, 230, 96, 96),
            new Region("smoke_2", 196, 230, 96, 96), new Region("smoke_3", 294, 230, 96, 96)};
    public static final Region[] FLAME = {
            new Region("flame_0", 392, 230, 64, 128), new Region("flame_1", 458, 230, 64, 128),
            new Region("flame_2", 524, 230, 64, 128), new Region("flame_3", 590, 230, 64, 128)};
    public static final Region[] SHARD = {
            new Region("shard_0", 656, 230, 48, 64), new Region("shard_1", 706, 230, 48, 64),
            new Region("shard_2", 756, 230, 48, 64)};
    public static final Region[] CONFETTI = {
            new Region("confetti_0", 806, 230, 16, 32), new Region("confetti_1", 824, 230, 32, 32),
            new Region("confetti_2", 858, 230, 24, 24)};
    public static final Region LEAF = new Region("leaf", 884, 230, 48, 48);
    public static final Region FOOT = new Region("foot", 934, 230, 32, 64);
    // Row 4: the big decals.
    public static final Region MAGIC = new Region("magic", 0, 360, 256, 256);
    public static final Region RUNES = new Region("runes", 258, 360, 256, 256);
    public static final Region SCORCH = new Region("scorch", 516, 360, 192, 192);
    public static final Region SHOCK = new Region("shock", 710, 360, 192, 192);
    public static final Region SPIRAL = new Region("spiral", 0, 618, 256, 256);
    public static final Region HEX = new Region("hex", 258, 618, 256, 256);
    public static final Region CRACKS = new Region("cracks", 516, 618, 192, 192);
    public static final Region BOLT = new Region("bolt", 710, 618, 64, 128);
    /**
     * A gravestone's face, shaped as {@link #GRAVE_W} wide and {@link #GRAVE_H} tall with a round top of
     * {@link #GRAVE_R}: weathered granite, a carved border and emblem, moss at its foot.
     */
    public static final Region GRAVE = new Region("grave", 904, 360, 96, 144);
    /** Plain granite for the stone's sides and plinth. */
    public static final Region GRANITE = new Region("granite", 904, 506, 96, 96);
    public static final float GRAVE_W = 0.6f, GRAVE_H = 0.95f, GRAVE_R = 0.3f;

    private static final Region[] ALL = {GLOW, HOT, FLARE, RING, DOT, DOT_SOFT, STAR6, ORB, BUBBLE, SQUARE, SPARK, LINE, TUBE, FRONT,
            FADE, NOISE, HEART, STAR, DIAMOND, CROSS, FEATHER, SKULL, NOTE, SMOKE[0], SMOKE[1], SMOKE[2], SMOKE[3], FLAME[0], FLAME[1],
            FLAME[2], FLAME[3], SHARD[0], SHARD[1], SHARD[2], CONFETTI[0], CONFETTI[1], CONFETTI[2], LEAF, FOOT, MAGIC, RUNES, SCORCH,
            SHOCK, SPIRAL, HEX, CRACKS, BOLT, GRAVE, GRANITE};

    private static FxTex atlas;

    private FxSoft() {
    }

    /** The atlas (made on first use). */
    public static synchronized FxTex atlas() {
        if (atlas == null) {
            atlas = make();
        }
        return atlas;
    }

    // ------------------------------------------------------------------ making the pictures

    private static FxTex make() {
        int[] px = new int[W * H];
        for (Region r : ALL) {
            // Half a texel in, so the smooth filter never reads the neighbour's edge.
            r.u0 = (r.x + 0.5f) / W;
            r.v0 = (r.y + 0.5f) / H;
            r.u1 = (r.x + r.w - 0.5f) / W;
            r.v1 = (r.y + r.h - 0.5f) / H;
        }
        paint(px, GLOW, (u, v) -> {
            float r = rad(u, v);
            return white(edge(r) * (0.62f * g(r, 0.2f) + 0.38f * g(r, 0.52f)));
        });
        paint(px, HOT, (u, v) -> {
            float r = rad(u, v);
            return white(edge(r) * Math.min(1f, 1.0f * g(r, 0.07f) + 0.42f * g(r, 0.22f) + 0.16f * g(r, 0.55f)));
        });
        paint(px, FLARE, (u, v) -> {
            float x = Math.abs(u * 2f - 1f), y = Math.abs(v * 2f - 1f);
            float r = rad(u, v);
            float rays = ray(x, y) + ray(y, x);
            // Faint diagonals.
            float d1 = Math.abs(x - y) / 1.4142f, d2 = (x + y) / 1.4142f;
            rays += 0.35f * g(d1, 0.018f) * pow(Math.max(0f, 1f - d2 / 1.1f), 3f);
            return white(edge(r * 0.98f) * Math.min(1f, rays + 0.9f * g(r, 0.07f) + 0.3f * g(r, 0.2f)));
        });
        paint(px, STAR6, (u, v) -> {
            float x = u * 2f - 1f, y = v * 2f - 1f;
            float r = (float) Math.sqrt(x * x + y * y);
            double a = Math.atan2(y, x);
            float rays = 0f;
            for (int k = 0; k < 6; k++) {
                double ang = a - k * Math.PI / 3;
                float across = (float) Math.abs(Math.sin(ang)) * r, along = (float) Math.cos(ang) * r;
                if (along > 0f) {
                    rays += g(across, 0.022f + 0.02f * along) * pow(Math.max(0f, 1f - along), 2f);
                }
            }
            return white(edge(r) * Math.min(1f, rays + 0.9f * g(r, 0.08f) + 0.25f * g(r, 0.25f)));
        });
        paint(px, RING, (u, v) -> {
            float r = rad(u, v);
            return white(edge(r) * Math.min(1f, g(r - 0.8f, 0.045f) + 0.28f * g(r - 0.74f, 0.13f)));
        });
        paint(px, DOT, (u, v) -> {
            float r = rad(u, v);
            // A bright disc with a soft rim: a particle that reads as a point of light.
            float body = 1f - smooth(0.55f, 0.95f, r);
            return shaded(body, 0.75f + 0.25f * (1f - r));
        });
        paint(px, DOT_SOFT, (u, v) -> white(edge(rad(u, v)) * g(rad(u, v), 0.38f)));
        paint(px, ORB, (u, v) -> {
            float x = u * 2f - 1f, y = v * 2f - 1f;
            float r = (float) Math.sqrt(x * x + y * y);
            float body = 1f - smooth(0.78f, 0.96f, r);
            // Lit from the top left: a highlight, then the body, a darker rim.
            float light = 0.62f + 0.38f * clamp(1f - ((x + 0.35f) * (x + 0.35f) + (y + 0.4f) * (y + 0.4f)) * 1.6f) - 0.22f * smooth(0.6f, 0.95f, r);
            float spec = g((float) Math.sqrt((x + 0.32f) * (x + 0.32f) + (y + 0.38f) * (y + 0.38f)), 0.12f);
            return shaded(body, Math.min(1f, light + spec));
        });
        paint(px, BUBBLE, (u, v) -> {
            float x = u * 2f - 1f, y = v * 2f - 1f;
            float r = (float) Math.sqrt(x * x + y * y);
            float rim = g(r - 0.9f, 0.05f) + 0.18f * (1f - smooth(0.0f, 0.92f, r)) * smooth(0.3f, 0.9f, r);
            float spec = g((float) Math.sqrt((x + 0.38f) * (x + 0.38f) + (y + 0.42f) * (y + 0.42f)), 0.1f)
                    + 0.6f * g((float) Math.sqrt((x - 0.45f) * (x - 0.45f) + (y - 0.5f) * (y - 0.5f)), 0.06f);
            return white(Math.min(1f, rim + spec) * edge(r));
        });
        paint(px, SQUARE, (u, v) -> 0xFFFFFFFF);
        paint(px, SPARK, (u, v) -> {
            // The head at the right, the tail fading to the left; round across.
            float across = Math.abs(v * 2f - 1f);
            float along = u;
            float body = pow(smooth(0f, 0.92f, along), 1.6f) * (1f - smooth(0.93f, 1f, along));
            float head = g((float) Math.sqrt((along - 0.9f) * (along - 0.9f) * 9f + across * across), 0.35f);
            return white(Math.min(1f, body * g(across, 0.42f) + head * 0.8f) * (1f - smooth(0.9f, 1f, across)));
        });
        // Across a line: soft all the way, reaching nothing exactly at its sides so no edge ever shows.
        paint(px, LINE, (u, v) -> {
            float x = Math.abs(u * 2f - 1f);
            return white(pow(1f - x * x, 2.5f));
        });
        paint(px, TUBE, (u, v) -> {
            float x = Math.abs(u * 2f - 1f);
            return white(Math.min(1f, g(x, 0.16f) * (1f - smooth(0.5f, 1f, x)) + 0.38f * pow(1f - x * x, 3f)));
        });
        paint(px, FRONT, (u, v) -> {
            // A shock front: sharp at the outer edge (u = 1), trailing softly inward.
            float a = u < 0.86f ? pow(u / 0.86f, 2.6f) : 1f - smooth(0.86f, 1f, u);
            return white(a);
        });
        paint(px, FADE, (u, v) -> white(pow(1f - u, 1.5f)));
        paint(px, NOISE, (u, v) -> {
            // Tileable across its half width, so a sliding window never shows a seam.
            float n = fbmTile(u * 2f * 8f, v * 2f, 8, 4);
            float across = Math.abs(v * 2f - 1f);
            return white(clamp(0.25f + 0.95f * n) * (1f - smooth(0.6f, 1f, across) * 0.6f));
        });
        paint(px, HEART, (u, v) -> {
            // Inigo Quilez's heart: the tip at the bottom, two round lobes.
            float x = (u - 0.5f) * 1.4f, y = (1f - v) * 1.3f - 0.1f;
            float d = heartDist(x, y);
            float inside = 1f - smooth(-0.012f, 0.012f, d);
            float light = 0.7f + 0.3f * clamp(1f - ((x + 0.22f) * (x + 0.22f) + (y - 0.78f) * (y - 0.78f)) * 4f);
            return shaded(inside, light);
        });
        paint(px, STAR, (u, v) -> {
            float x = u * 2f - 1f, y = v * 2f - 1f;
            float d = starDist(x, y + 0.06f, 5, 0.95f, 0.42f);
            float inside = 1f - smooth(-0.015f, 0.03f, d);
            float light = 0.72f + 0.28f * clamp(1f - (x + 0.2f) * (x + 0.2f) * 2f - (y + 0.3f) * (y + 0.3f) * 2f);
            return shaded(inside, light);
        });
        paint(px, DIAMOND, (u, v) -> {
            float x = u * 2f - 1f, y = v * 2f - 1f;
            float d = Math.abs(x) / 0.62f + Math.abs(y) - 0.96f;
            float inside = 1f - smooth(-0.02f, 0.03f, d);
            // Facets: the top left brightest.
            float light = x < 0f ? (y < 0f ? 1f : 0.72f) : (y < 0f ? 0.86f : 0.58f);
            return shaded(inside, light);
        });
        paint(px, CROSS, (u, v) -> {
            float x = Math.abs(u * 2f - 1f), y = (v * 2f - 1f);
            boolean bar = x < 0.16f && y > -0.92f && y < 0.95f || Math.abs(y + 0.32f) < 0.15f && x < 0.62f;
            float d = bar ? 1f : 0f;
            return shaded(d, 0.82f + 0.18f * (1f - x));
        });
        paint(px, FEATHER, (u, v) -> {
            float x = u * 2f - 1f, y = v * 2f - 1f;
            // A long leaf shape with a vane line down it.
            float half = 0.55f * (float) Math.sin(Math.PI * clamp((y + 1f) / 2f)) * (0.6f + 0.4f * (1f - (y + 1f) / 2f));
            float inside = 1f - smooth(half - 0.06f, half + 0.02f, Math.abs(x));
            float vane = g(x, 0.04f) * 0.25f;
            float barbs = 0.12f * (float) Math.abs(Math.sin((y * 14f + Math.abs(x) * 6f)));
            return shaded(inside, clamp(0.78f + vane - barbs + 0.15f * (1f - Math.abs(x) / Math.max(0.01f, half))));
        });
        paint(px, SKULL, (u, v) -> {
            float x = u * 2f - 1f, y = v * 2f - 1f;
            float head = 1f - smooth(0.78f, 0.86f, (float) Math.sqrt(x * x + (y + 0.12f) * (y + 0.12f) * 1.15f));
            float jaw = Math.abs(x) < 0.42f && y > 0.35f && y < 0.82f ? 1f : 0f;
            float eyes = (1f - smooth(0.16f, 0.22f, (float) Math.sqrt((Math.abs(x) - 0.33f) * (Math.abs(x) - 0.33f) + (y + 0.05f) * (y + 0.05f))));
            float nose = Math.abs(x) < 0.08f && y > 0.18f && y < 0.34f ? 1f : 0f;
            float teeth = jaw > 0f && y > 0.62f && Math.abs((x * 5f) % 1f) < 0.12f ? 1f : 0f;
            float a = Math.max(head, jaw) * (1f - eyes) * (1f - nose) * (1f - teeth);
            return shaded(a, 0.8f + 0.2f * clamp(1f - (x + 0.3f) * (x + 0.3f) - (y + 0.4f) * (y + 0.4f)));
        });
        paint(px, NOTE, (u, v) -> {
            float x = u * 2f - 1f, y = v * 2f - 1f;
            float head = 1f - smooth(0.24f, 0.3f, (float) Math.sqrt((x + 0.2f) * (x + 0.2f) * 1.3f + (y - 0.55f) * (y - 0.55f) * 2.2f));
            float stem = x > 0.02f && x < 0.14f && y > -0.85f && y < 0.55f ? 1f : 0f;
            float flag = y > -0.85f && y < -0.45f && x > 0.08f && x < 0.08f + 0.5f * (1f - (y + 0.85f) / 0.4f) ? 1f : 0f;
            return shaded(Math.max(head, Math.max(stem, flag)), 0.9f);
        });
        for (int i = 0; i < 4; i++) {
            final int seed = 17 + i * 31;
            paint(px, SMOKE[i], (u, v) -> {
                float x = u * 2f - 1f, y = v * 2f - 1f;
                float r = (float) Math.sqrt(x * x + y * y);
                float n = fbm(u * 3.2f + seed, v * 3.2f - seed, 5);
                float mask = clamp(1.25f * (1f - smooth(0.25f, 0.98f, r)) * (0.45f + 0.9f * n) - 0.12f);
                float light = 0.62f + 0.38f * clamp(0.5f - y * 0.55f - x * 0.25f + (n - 0.5f) * 0.6f);
                return shaded(mask, light);
            });
        }
        for (int i = 0; i < 4; i++) {
            final float phase = i * 0.25f;
            paint(px, FLAME[i], (u, v) -> {
                float x = u * 2f - 1f, y = v; // y: 0 at the top, 1 at the bottom
                float wob = (fbm(u * 3f + phase * 7f, y * 2.5f - phase * 5f, 4) - 0.5f) * 0.6f * (1f - y);
                float xx = x + wob;
                // A teardrop: wide at the bottom, licking up to a point.
                float width = 0.85f * (float) Math.pow(clamp(y), 0.75f) * (1f - smooth(0.78f, 1f, y) * 0.55f);
                float inside = 1f - smooth(width * 0.7f, width + 0.02f, Math.abs(xx));
                float core = 1f - smooth(width * 0.15f, width * 0.6f, Math.abs(xx));
                inside *= smooth(0f, 0.12f, y) * (1f - smooth(0.96f, 1f, y));
                float n = fbm(u * 6f, y * 5f - phase * 9f, 3);
                return shaded(clamp(inside * (0.75f + 0.5f * n)), clamp(0.55f + 0.45f * core * smooth(0.25f, 0.85f, y)));
            });
        }
        float[][][] shards = {
                {{0.5f, 0.02f}, {0.92f, 0.38f}, {0.62f, 0.98f}, {0.12f, 0.62f}},
                {{0.3f, 0.04f}, {0.86f, 0.18f}, {0.7f, 0.96f}, {0.08f, 0.44f}},
                {{0.55f, 0.0f}, {0.98f, 0.7f}, {0.4f, 1f}, {0.02f, 0.3f}}};
        for (int i = 0; i < 3; i++) {
            final float[][] poly = shards[i];
            paint(px, SHARD[i], (u, v) -> {
                float inside = polyInside(poly, u, v);
                // Two facets split along the long diagonal, and a bright edge.
                float side = (u - poly[0][0]) * (poly[2][1] - poly[0][1]) - (v - poly[0][1]) * (poly[2][0] - poly[0][0]);
                float light = side > 0f ? 0.95f : 0.62f;
                return shaded(inside, light);
            });
        }
        paint(px, CONFETTI[0], (u, v) -> shaded(1f, 0.75f + 0.25f * (1f - v)));
        paint(px, CONFETTI[1], (u, v) -> {
            // A curl: a thin bent strip.
            float x = u * 2f - 1f, y = v * 2f - 1f;
            float r = (float) Math.sqrt(x * x + y * y);
            float a = Math.abs(r - 0.6f) < 0.2f && y < 0.3f ? 1f : 0f;
            return shaded(a, 0.7f + 0.3f * (1f - v));
        });
        paint(px, CONFETTI[2], (u, v) -> {
            float x = u * 2f - 1f, y = v * 2f - 1f;
            return shaded(starDist(x, y, 4, 0.95f, 0.35f) < 0f ? 1f : 0f, 0.85f);
        });
        paint(px, LEAF, (u, v) -> {
            float x = u * 2f - 1f, y = v * 2f - 1f;
            float rx = (x + y) * 0.7071f, ry = (y - x) * 0.7071f;
            float half = 0.42f * (float) Math.sqrt(Math.max(0f, 1f - ry * ry / 0.95f));
            float inside = 1f - smooth(half - 0.04f, half + 0.03f, Math.abs(rx));
            float vein = g(rx, 0.03f) * 0.3f;
            return shaded(inside, clamp(0.72f + 0.2f * (1f - Math.abs(rx) * 2f) - vein));
        });
        paint(px, FOOT, (u, v) -> {
            float x = u * 2f - 1f, y = v * 2f - 1f;
            float sole = 1f - smooth(0.9f, 1.02f, (float) Math.sqrt(x * x * 1.1f + (y + 0.15f) * (y + 0.15f) * 2.6f));
            float heel = 1f - smooth(0.6f, 0.72f, (float) Math.sqrt(x * x * 1.3f + (y - 0.72f) * (y - 0.72f) * 6f));
            return shaded(Math.max(sole, heel), 0.85f);
        });
        paintMagic(px);
        paintRunes(px);
        paint(px, SCORCH, (u, v) -> {
            float x = u * 2f - 1f, y = v * 2f - 1f;
            float r = (float) Math.sqrt(x * x + y * y);
            double a = Math.atan2(y, x);
            float spikes = 0.8f + 0.2f * (float) Math.sin(a * 9 + fbm(u * 4f, v * 4f, 2) * 6f);
            float n = fbm(u * 6f + 3f, v * 6f - 1f, 5);
            float mask = clamp((1f - smooth(0.2f, 0.95f * spikes, r)) * (0.55f + 0.8f * n));
            return shaded(mask, 0.25f + 0.5f * n);
        });
        paint(px, SHOCK, (u, v) -> {
            float r = rad(u, v);
            // A shock ring: a bright front, a soft trail inward, and dust specks on it.
            float front = g(r - 0.86f, 0.035f) + 0.45f * g(r - 0.8f, 0.09f) + 0.12f * (1f - smooth(0.2f, 0.85f, r)) * smooth(0.4f, 0.8f, r);
            float n = fbm(u * 10f, v * 10f, 3);
            return white(edge(r) * clamp(front * (0.7f + 0.6f * n)));
        });
        paintSpiral(px);
        paintHex(px);
        paint(px, CRACKS, (u, v) -> {
            float x = u * 2f - 1f, y = v * 2f - 1f;
            float r = (float) Math.sqrt(x * x + y * y);
            float best = 99f;
            for (int k = 0; k < 9; k++) {
                double ang = k * 0.698 + 0.3 * Math.sin(k * 3.1);
                float dx = (float) Math.cos(ang), dy = (float) Math.sin(ang);
                float along = x * dx + y * dy;
                if (along < 0f) {
                    continue;
                }
                float wig = 0.06f * (float) Math.sin(along * 17f + k) + 0.03f * (float) Math.sin(along * 41f + k * 2);
                float across = Math.abs(-x * dy + y * dx - wig);
                best = Math.min(best, across / (0.02f + 0.015f * (1f - along)));
            }
            float line = 1f - smooth(0.6f, 1.2f, best);
            return white(line * (1f - smooth(0.7f, 1f, r)) + 0.5f * g(r, 0.12f));
        });
        paint(px, BOLT, (u, v) -> {
            float x = u * 2f - 1f;
            float zig = 0.35f * (float) Math.sin(v * 9.4f) * (float) Math.sin(v * 3.1f + 1f);
            return white(Math.min(1f, g(x - zig, 0.08f) + 0.4f * g(x - zig, 0.3f)));
        });
        paint(px, GRANITE, (u, v) -> granite(u * 3f, v * 3f, 0f));
        paintGrave(px);
        return linear(new FxTex("fx_soft", W, H, px));
    }

    /** Granite at (x, y): grey with a blue cast, mottled, flecked light and dark; {@code wet} darkens it. */
    private static int granite(float x, float y, float wet) {
        float n = fbm(x * 2.2f + 40f, y * 2.2f + 11f, 5);
        float fleck = hash((int) (x * 48f), (int) (y * 48f));
        float l = 0.56f + 0.24f * n;
        if (fleck > 0.93f) {
            l += 0.16f;
        } else if (fleck < 0.06f) {
            l -= 0.18f;
        }
        l *= 1f - 0.35f * wet;
        return opaque(l * 0.97f, l * 0.98f, l * 1.04f);
    }

    /** The gravestone's face: granite, weathered from the top, a carved border following its shape, an emblem, moss below. */
    private static void paintGrave(int[] px) {
        final float w = GRAVE_W, h = GRAVE_H, r = GRAVE_R;
        paint(px, GRAVE, (u, v) -> {
            float x = (u - 0.5f) * w, y = (1f - v) * h;
            // Distance inside the stone's outline (a rectangle with a half-round top).
            float rectTop = h - r;
            float inside = y <= rectTop ? Math.min(w / 2f - Math.abs(x), y) : r - (float) Math.sqrt(x * x + (y - rectTop) * (y - rectTop));
            inside = Math.min(inside, y);
            // Rain streaks running down from the top, and the dirt and moss splashed up the foot.
            float streak = fbm(x * 30f + 5f, y * 2f, 3);
            float wet = clamp((streak - 0.55f) * 2.2f) * smooth(0.2f, 0.9f, y / h) * 0.6f;
            int c = granite(x * 9f, y * 9f, wet);
            float cr = (c >> 16 & 255) / 255f, cg = (c >> 8 & 255) / 255f, cb = (c & 255) / 255f;
            // The carved border: a groove a little in from the edge, shadowed above, lit below.
            float groove = Math.abs(inside - 0.045f);
            if (groove < 0.012f) {
                float k = 1f - groove / 0.012f;
                cr *= 1f - 0.45f * k;
                cg *= 1f - 0.45f * k;
                cb *= 1f - 0.45f * k;
            } else if (inside > 0.057f && inside < 0.066f) {
                cr = Math.min(1f, cr * 1.18f);
                cg = Math.min(1f, cg * 1.18f);
                cb = Math.min(1f, cb * 1.18f);
            }
            // The emblem up in the arch: a Celtic cross, its ring round where the arms cross.
            float ex = x, ey = y - (rectTop + 0.03f);
            float ring = Math.abs((float) Math.sqrt(ex * ex + ey * ey) - 0.055f);
            boolean bar = Math.abs(ex) < 0.011f && ey > -0.17f && ey < 0.1f || Math.abs(ey) < 0.011f && Math.abs(ex) < 0.085f;
            if (ring < 0.009f || bar) {
                cr *= 0.5f;
                cg *= 0.5f;
                cb *= 0.52f;
            }
            // A crack from the top right corner down a third of the way.
            float crackX = 0.16f - (rectTop + r - y) * 0.35f + 0.015f * (float) Math.sin(y * 70f);
            if (y > h * 0.62f && Math.abs(x - crackX) < 0.006f) {
                cr *= 0.45f;
                cg *= 0.45f;
                cb *= 0.45f;
            }
            // Moss and lichen at the foot.
            float moss = clamp((0.2f - y) / 0.2f) * clamp(fbm(x * 18f, y * 18f, 4) * 2.2f - 0.75f);
            float lichen = clamp(fbm(x * 25f + 9f, y * 25f, 3) * 3f - 2.35f) * 0.7f;
            float m = Math.max(moss, lichen);
            cr = cr * (1f - m) + 0.33f * m;
            cg = cg * (1f - m) + 0.45f * m;
            cb = cb * (1f - m) + 0.22f * m;
            // Darker round the rim, where it's rounded off.
            float rim = clamp(1f - inside / 0.02f) * 0.25f;
            return opaque(cr * (1f - rim), cg * (1f - rim), cb * (1f - rim));
        });
    }

    private static int opaque(float r, float g, float b) {
        return 0xFF000000 | Math.round(clamp(r) * 255f) << 16 | Math.round(clamp(g) * 255f) << 8 | Math.round(clamp(b) * 255f);
    }

    private static FxTex linear(FxTex t) {
        t.linear = true;
        return t;
    }

    /** The magic circle: two rings, a band of ticks and runes between them, a hexagram and three small circles. */
    private static void paintMagic(int[] px) {
        paint(px, MAGIC, (u, v) -> {
            float x = u * 2f - 1f, y = v * 2f - 1f;
            float r = (float) Math.sqrt(x * x + y * y);
            double a = Math.atan2(y, x);
            float lines = 0f;
            lines = Math.max(lines, lineAt(r, 0.95f, 0.012f));
            lines = Math.max(lines, lineAt(r, 0.88f, 0.008f));
            lines = Math.max(lines, lineAt(r, 0.62f, 0.01f));
            lines = Math.max(lines, lineAt(r, 0.56f, 0.006f));
            lines = Math.max(lines, lineAt(r, 0.18f, 0.008f));
            // Ticks between the outer rings.
            if (r > 0.885f && r < 0.945f) {
                float tick = (float) Math.abs(Math.sin(a * 36));
                lines = Math.max(lines, 1f - smooth(0.06f, 0.14f, tick));
            }
            // Runes: little marks in the band between 0.62 and 0.88.
            if (r > 0.65f && r < 0.85f) {
                double seg = (a + Math.PI) / (2 * Math.PI) * 24;
                int k = (int) Math.floor(seg);
                float f = (float) (seg - k);
                float h = (r - 0.65f) / 0.2f;
                int glyph = (k * 7 + 3) % 5;
                float mark = switch (glyph) {
                    case 0 -> Math.abs(f - 0.5f) < 0.07f && h > 0.2f && h < 0.8f ? 1f : 0f;
                    case 1 -> Math.abs(h - 0.5f) < 0.08f && f > 0.25f && f < 0.75f ? 1f : 0f;
                    case 2 -> Math.abs((f - 0.5f) - (h - 0.5f)) < 0.08f && h > 0.2f && h < 0.8f ? 1f : 0f;
                    case 3 -> Math.abs(Math.sqrt((f - 0.5f) * (f - 0.5f) + (h - 0.5f) * (h - 0.5f)) - 0.22f) < 0.06f ? 1f : 0f;
                    default -> Math.abs(f - 0.3f) < 0.06f && h > 0.25f || Math.abs(h - 0.25f) < 0.07f && f > 0.3f && f < 0.7f ? 1f : 0f;
                };
                lines = Math.max(lines, mark);
            }
            // A hexagram inside the inner ring.
            for (int t = 0; t < 2; t++) {
                for (int s = 0; s < 3; s++) {
                    double a0 = t * Math.PI / 3 + s * 2 * Math.PI / 3 - Math.PI / 2;
                    double a1 = a0 + 2 * Math.PI / 3;
                    lines = Math.max(lines, segment(x, y, 0.56f * (float) Math.cos(a0), 0.56f * (float) Math.sin(a0),
                            0.56f * (float) Math.cos(a1), 0.56f * (float) Math.sin(a1), 0.009f));
                }
            }
            for (int s = 0; s < 3; s++) {
                double ang = s * 2 * Math.PI / 3 + Math.PI / 2;
                float cx = 0.56f * (float) Math.cos(ang), cy = 0.56f * (float) Math.sin(ang);
                float d = (float) Math.sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy));
                lines = Math.max(lines, lineAt(d, 0.08f, 0.008f));
            }
            float glow = 0.18f * g(r - 0.92f, 0.05f) + 0.1f * g(r - 0.6f, 0.06f);
            return white(Math.min(1f, lines + glow) * edge(r * 0.99f));
        });
    }

    /** A ring of runes on its own, for rings that turn (Target's, the totem's). */
    private static void paintRunes(int[] px) {
        paint(px, RUNES, (u, v) -> {
            float x = u * 2f - 1f, y = v * 2f - 1f;
            float r = (float) Math.sqrt(x * x + y * y);
            double a = Math.atan2(y, x);
            float lines = Math.max(lineAt(r, 0.94f, 0.01f), lineAt(r, 0.66f, 0.01f));
            if (r > 0.7f && r < 0.9f) {
                double seg = (a + Math.PI) / (2 * Math.PI) * 16;
                int k = (int) Math.floor(seg);
                float f = (float) (seg - k), h = (r - 0.7f) / 0.2f;
                int glyph = (k * 5 + 1) % 4;
                float mark = switch (glyph) {
                    case 0 -> Math.abs(f - 0.5f) < 0.06f || Math.abs(h - 0.7f) < 0.06f && f > 0.3f && f < 0.7f ? 1f : 0f;
                    case 1 -> Math.abs((f - 0.5f) + (h - 0.5f)) < 0.07f || Math.abs((f - 0.5f) - (h - 0.5f)) < 0.07f ? 1f : 0f;
                    case 2 -> Math.abs(Math.sqrt((f - 0.5f) * (f - 0.5f) + (h - 0.5f) * (h - 0.5f)) - 0.25f) < 0.06f ? 1f : 0f;
                    default -> Math.abs(h - 0.3f) < 0.06f || Math.abs(f - 0.3f) < 0.06f && h > 0.3f ? 1f : 0f;
                };
                if (h > 0.12f && h < 0.88f && f > 0.15f && f < 0.85f) {
                    lines = Math.max(lines, mark);
                }
            }
            return white(Math.min(1f, lines + 0.12f * g(r - 0.8f, 0.12f)) * edge(r * 0.99f));
        });
    }

    /** A spiral galaxy of arms, for a portal or a black hole's swirl. */
    private static void paintSpiral(int[] px) {
        paint(px, SPIRAL, (u, v) -> {
            float x = u * 2f - 1f, y = v * 2f - 1f;
            float r = (float) Math.sqrt(x * x + y * y);
            double a = Math.atan2(y, x);
            float arms = 0f;
            for (int k = 0; k < 3; k++) {
                double phase = a + k * 2 * Math.PI / 3 - Math.log(Math.max(0.02, r)) * 2.6;
                arms += (float) Math.pow(Math.max(0.0, Math.cos(phase)), 6.0);
            }
            float n = fbm(u * 7f, v * 7f, 3);
            float a2 = arms * (0.5f + 0.7f * n) * (1f - smooth(0.4f, 1f, r)) * smooth(0.04f, 0.2f, r) + 0.6f * g(r, 0.1f);
            return white(clamp(a2) * edge(r));
        });
    }

    /** A hexagon grid fading out to the edge: a shield's or a jump ring's floor. */
    private static void paintHex(int[] px) {
        paint(px, HEX, (u, v) -> {
            float x = (u * 2f - 1f) * 5.5f, y = (v * 2f - 1f) * 5.5f;
            // Two offset lattices of hexagon centres; the nearer centre's hexagon holds the point.
            final float sy = 1.7320508f;
            float ax = (float) Math.floor(x) + 0.5f, ay = (float) Math.floor(y / sy) + 0.5f;
            float bx = (float) Math.floor(x - 0.5f) + 0.5f, by = (float) Math.floor((y - 1f) / sy) + 0.5f;
            float hx = x - ax, hy = y - ay * sy;
            float kx = x - (bx + 0.5f), ky = y - (by + 0.5f) * sy;
            float lx, ly;
            if (hx * hx + hy * hy < kx * kx + ky * ky) {
                lx = hx;
                ly = hy;
            } else {
                lx = kx;
                ly = ky;
            }
            lx = Math.abs(lx);
            ly = Math.abs(ly);
            float hexd = Math.max(lx * 0.5f + ly * 0.8660254f, lx);
            float line = 1f - smooth(0.02f, 0.06f, Math.abs(hexd - 0.5f));
            float r = rad(u, v);
            return white(line * (1f - smooth(0.55f, 0.98f, r)) + 0.08f * (1f - smooth(0.3f, 0.95f, r)));
        });
    }

    private static float heartDist(float x, float y) {
        x = Math.abs(x);
        if (y + x > 1f) {
            float dx = x - 0.25f, dy = y - 0.75f;
            return (float) Math.sqrt(dx * dx + dy * dy) - 0.35355339f;
        }
        float ax = x, ay = y - 1f;
        float m = 0.5f * Math.max(x + y, 0f);
        float bx = x - m, by = y - m;
        float d = (float) Math.sqrt(Math.min(ax * ax + ay * ay, bx * bx + by * by));
        return d * Math.signum(x - y);
    }

    // ------------------------------------------------------------------ helpers

    private interface Shader {
        /** The ARGB colour at (u, v), both 0..1 over the region. */
        int at(float u, float v);
    }

    /** Fills a region, each pixel the average of four samples (smooth edges). */
    private static void paint(int[] px, Region r, Shader s) {
        for (int y = 0; y < r.h; y++) {
            for (int x = 0; x < r.w; x++) {
                int a = 0, rr = 0, gg = 0, bb = 0;
                for (int k = 0; k < 4; k++) {
                    float u = (x + 0.25f + (k & 1) * 0.5f) / r.w, v = (y + 0.25f + (k >> 1) * 0.5f) / r.h;
                    int c = s.at(u, v);
                    int ca = c >>> 24;
                    a += ca;
                    rr += (c >> 16 & 255) * ca;
                    gg += (c >> 8 & 255) * ca;
                    bb += (c & 255) * ca;
                }
                int out;
                if (a == 0) {
                    // Clear, but white: the smooth filter blends toward white, never toward black.
                    out = 0x00FFFFFF;
                } else {
                    out = (a / 4) << 24 | (rr / a) << 16 | (gg / a) << 8 | (bb / a);
                }
                px[(r.y + y) * W + r.x + x] = out;
            }
        }
    }

    private static int white(float alpha) {
        int a = Math.round(clamp(alpha) * 255f);
        return a << 24 | 0xFFFFFF;
    }

    /** Alpha {@code alpha}, grey {@code light} (1 white): a shape's light and shadow for the tint to colour. */
    private static int shaded(float alpha, float light) {
        int a = Math.round(clamp(alpha) * 255f);
        int l = Math.round(clamp(light) * 255f);
        return a << 24 | l << 16 | l << 8 | l;
    }

    private static float rad(float u, float v) {
        float x = u * 2f - 1f, y = v * 2f - 1f;
        return (float) Math.sqrt(x * x + y * y);
    }

    /** A Gaussian of width {@code s}. */
    static float g(float x, float s) {
        return (float) Math.exp(-(x * x) / (s * s));
    }

    /** 1 inside, fading to 0 at the region's round edge (so nothing is cut off square). */
    private static float edge(float r) {
        return 1f - smooth(0.8f, 1f, r);
    }

    private static float ray(float along, float across) {
        return g(across, 0.02f + 0.03f * along) * pow(Math.max(0f, 1f - along), 2.2f);
    }

    private static float lineAt(float r, float at, float width) {
        return 1f - smooth(width * 0.6f, width * 1.6f, Math.abs(r - at));
    }

    private static float segment(float x, float y, float x0, float y0, float x1, float y1, float width) {
        float dx = x1 - x0, dy = y1 - y0;
        float t = clamp(((x - x0) * dx + (y - y0) * dy) / (dx * dx + dy * dy));
        float ex = x - (x0 + dx * t), ey = y - (y0 + dy * t);
        float d = (float) Math.sqrt(ex * ex + ey * ey);
        return 1f - smooth(width * 0.6f, width * 1.6f, d);
    }

    /** A star's signed distance (negative inside): {@code points} points, outer and inner radii. */
    private static float starDist(float x, float y, int points, float outer, float inner) {
        double a = Math.atan2(x, -y);
        double seg = Math.PI / points;
        double k = Math.abs(((a % (2 * seg)) + 2 * seg) % (2 * seg) - seg) / seg;
        float rr = (float) (inner + (outer - inner) * Math.pow(k, 1.6));
        return (float) Math.sqrt(x * x + y * y) - rr;
    }

    private static float polyInside(float[][] poly, float u, float v) {
        boolean in = false;
        float minEdge = 9f;
        for (int i = 0, j = poly.length - 1; i < poly.length; j = i++) {
            float xi = poly[i][0], yi = poly[i][1], xj = poly[j][0], yj = poly[j][1];
            if (yi > v != yj > v && u < (xj - xi) * (v - yi) / (yj - yi) + xi) {
                in = !in;
            }
            float dx = xj - xi, dy = yj - yi;
            float t = clamp(((u - xi) * dx + (v - yi) * dy) / (dx * dx + dy * dy));
            float ex = u - (xi + dx * t), ey = v - (yi + dy * t);
            minEdge = Math.min(minEdge, (float) Math.sqrt(ex * ex + ey * ey));
        }
        return in ? smooth(0f, 0.025f, minEdge) : 0f;
    }

    static float smooth(float e0, float e1, float x) {
        float t = clamp((x - e0) / (e1 - e0));
        return t * t * (3f - 2f * t);
    }

    static float clamp(float x) {
        return x < 0f ? 0f : x > 1f ? 1f : x;
    }

    private static float pow(float x, float p) {
        return (float) Math.pow(x, p);
    }

    // Value noise and its fractal sum.

    private static float hash(int x, int y) {
        int h = x * 374761393 + y * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0xFFFFFF) / (float) 0xFFFFFF;
    }

    private static float noise(float x, float y, int period) {
        int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y);
        float fx = x - x0, fy = y - y0;
        fx = fx * fx * (3f - 2f * fx);
        fy = fy * fy * (3f - 2f * fy);
        int ax = period > 0 ? Math.floorMod(x0, period) : x0, bx = period > 0 ? Math.floorMod(x0 + 1, period) : x0 + 1;
        float a = hash(ax, y0), b = hash(bx, y0), c = hash(ax, y0 + 1), d = hash(bx, y0 + 1);
        return a + (b - a) * fx + (c - a) * fy + (a - b - c + d) * fx * fy;
    }

    static float fbm(float x, float y, int octaves) {
        float sum = 0f, amp = 0.5f, norm = 0f;
        for (int i = 0; i < octaves; i++) {
            sum += amp * noise(x, y, 0);
            norm += amp;
            x = x * 2.03f + 17.1f;
            y = y * 2.03f - 9.7f;
            amp *= 0.5f;
        }
        return sum / norm;
    }

    /** Fractal noise that repeats every {@code period} along x. */
    private static float fbmTile(float x, float y, int period, int octaves) {
        float sum = 0f, amp = 0.5f, norm = 0f;
        int p = period;
        for (int i = 0; i < octaves; i++) {
            sum += amp * noise(x, y, p);
            norm += amp;
            x *= 2f;
            y = y * 2f + 7.3f;
            p *= 2;
            amp *= 0.5f;
        }
        return sum / norm;
    }
}
