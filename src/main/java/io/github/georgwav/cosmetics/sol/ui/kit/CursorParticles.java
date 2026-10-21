package io.github.georgwav.cosmetics.sol.ui.kit;

import io.github.georgwav.cosmetics.client.Modules;
import io.github.georgwav.cosmetics.sol.fx.Ramp;
import io.github.georgwav.cosmetics.sol.hud.HudKit;
import io.github.georgwav.cosmetics.sol.theme.ThemeState;
import io.github.georgwav.cosmetics.sol.util.Colors;
import io.github.georgwav.cosmetics.sol.util.Mulberry32;
import net.minecraft.client.Minecraft;

/**
 * Sol UI's cursor particles: while a screen is open, stars, hearts or Sol's sparkles drop off the cursor as it moves,
 * drift down, twinkle and go out in steps, in whole GUI pixels. The stars and sparkles in the theme's tones, the hearts
 * pink. A fixed pool: nothing is allocated as they come and go. Not with calm motion.
 */
public final class CursorParticles {
    private static final int MAX = 48;
    private static final float LIFE = 0.8f;
    private static final float[] X = new float[MAX], Y = new float[MAX], VX = new float[MAX], VY = new float[MAX], AGE = new float[MAX];
    private static final int[] SEED = new int[MAX];
    private static int next;
    private static float lastX = Float.NaN, lastY, moved;
    private static long lastNanos;
    private static final Mulberry32 RANDOM = new Mulberry32(41);
    private static final int[] RAMP = new int[Ramp.TONES];
    private static final int[] PINK = new int[Ramp.TONES];
    /** For the in-game test: particles let go, and a pointer position the scenario moves (NaN: the real mouse). */
    public static int spawned;
    public static float fakeX = Float.NaN, fakeY = Float.NaN;

    /** A heart, five by four. */
    private static final String[] HEART = {".#.#.", "#####", ".###.", "..#.."};

    static {
        for (int i = 0; i < MAX; i++) {
            AGE[i] = LIFE;
        }
    }

    private CursorParticles() {
    }

    /** Steps them on and draws them, with the kit in GUI units (Sol's top layer, a screen open). */
    public static void draw(Gfx g) {
        String kind = Modules.SOL_UI.cursorParticles.get();
        long now = System.nanoTime();
        float dt = lastNanos == 0L ? 0f : Math.min(0.05f, (now - lastNanos) / 1e9f);
        lastNanos = now;
        if (kind.equals("off") || SolScreen.calm() || io.github.georgwav.cosmetics.ui.ScreenOpener.current() == null) {
            lastX = Float.NaN;
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        double gs = mc.getWindow().getGuiScale();
        float mx = Float.isNaN(fakeX) ? (float) (mc.mouseHandler.xpos() / gs) : fakeX;
        float my = Float.isNaN(fakeY) ? (float) (mc.mouseHandler.ypos() / gs) : fakeY;
        if (!Float.isNaN(lastX)) {
            moved += (float) Math.hypot(mx - lastX, my - lastY);
        }
        lastX = mx;
        lastY = my;
        // One every four pixels moved, at most three a frame.
        for (int n = 0; moved >= 4f && n < 3; n++) {
            moved -= 4f;
            int i = next;
            next = (next + 1) % MAX;
            X[i] = mx + (RANDOM.nextFloat() - 0.5f) * 4f;
            Y[i] = my + (RANDOM.nextFloat() - 0.5f) * 4f;
            VX[i] = (RANDOM.nextFloat() - 0.5f) * 10f;
            VY[i] = 6f + RANDOM.nextFloat() * 10f;
            AGE[i] = 0f;
            SEED[i] = RANDOM.nextInt(1 << 20);
            spawned++;
        }
        moved = Math.min(moved, 12f);
        Ramp.of(0xFF000000 | ThemeState.tokens().accent, RAMP);
        Ramp.of(0xFFFF6E9A, PINK);
        for (int i = 0; i < MAX; i++) {
            if (AGE[i] >= LIFE) {
                continue;
            }
            AGE[i] += dt;
            X[i] += VX[i] * dt;
            Y[i] += VY[i] * dt;
            float k = Math.min(1f, AGE[i] / LIFE);
            // Out in three steps, twinkling.
            float a = Ramp.steps(1f - k, 3, true);
            if (((int) (AGE[i] * 10f) + SEED[i]) % 5 == 0) {
                a *= 0.5f;
            }
            if (a <= 0f) {
                continue;
            }
            float px = Math.round(X[i]), py = Math.round(Y[i]);
            switch (kind) {
                case "hearts" -> heart(g, px, py, a);
                case "stars" -> star(g, px, py, k < 0.5f ? 1 : 0, a);
                default -> sparkle(g, px, py, k < 0.34f ? 2 : k < 0.67f ? 1 : 0, a, SEED[i]);
            }
        }
    }

    /** A plus: the bright middle and an arm each way (the arms go first). */
    private static void star(Gfx g, float x, float y, int arm, float a) {
        HudKit.box(g, x, y, 1f, 1f, Colors.withAlpha(RAMP[Ramp.CORE], a));
        if (arm > 0) {
            int c = Colors.withAlpha(RAMP[Ramp.MAIN], a);
            HudKit.box(g, x - 1f, y, 1f, 1f, c);
            HudKit.box(g, x + 1f, y, 1f, 1f, c);
            HudKit.box(g, x, y - 1f, 1f, 1f, c);
            HudKit.box(g, x, y + 1f, 1f, 1f, c);
        }
    }

    /** Sol's sparkle: a four-pointed star whose arms shrink from two pixels to none, its tips a tone deeper. */
    private static void sparkle(Gfx g, float x, float y, int arm, float a, int seed) {
        int core = Colors.withAlpha(RAMP[(seed & 1) == 0 ? Ramp.CORE : Ramp.MAIN], a);
        HudKit.box(g, x, y, 1f, 1f, core);
        for (int d = 1; d <= arm; d++) {
            int c = Colors.withAlpha(RAMP[d == 1 ? Ramp.MAIN : Ramp.SHADE], a);
            HudKit.box(g, x - d, y, 1f, 1f, c);
            HudKit.box(g, x + d, y, 1f, 1f, c);
            HudKit.box(g, x, y - d, 1f, 1f, c);
            HudKit.box(g, x, y + d, 1f, 1f, c);
        }
    }

    private static void heart(Gfx g, float x, float y, float a) {
        for (int r = 0; r < HEART.length; r++) {
            String row = HEART[r];
            for (int c = 0; c < row.length(); c++) {
                if (row.charAt(c) == '#') {
                    int tone = r == 0 || c == 0 ? Ramp.CORE : r == HEART.length - 1 ? Ramp.SHADE : Ramp.MAIN;
                    HudKit.box(g, x - 2f + c, y - 2f + r, 1f, 1f, Colors.withAlpha(PINK[tone], a));
                }
            }
        }
    }
}
