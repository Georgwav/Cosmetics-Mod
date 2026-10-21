package io.github.georgwav.cosmetics.sol.atmosphere;

import io.github.georgwav.cosmetics.CosmeticsMod;
import io.github.georgwav.cosmetics.sol.render.SolTexture;
import io.github.georgwav.cosmetics.sol.render.WorldDraw;
import io.github.georgwav.cosmetics.sol.sky.NebulaTex;
import io.github.georgwav.cosmetics.sol.sky.SkySphere;
import io.github.georgwav.cosmetics.sol.util.Colors;
import io.github.georgwav.cosmetics.sol.util.Ease;
import io.github.georgwav.cosmetics.sol.util.SolExecutors;

/**
 * The three huge painted nebulae (the Night Sky lab's {@code NEBBIG}): magenta, coral and blue clouds of gas fixed
 * among the stars, nearly a hundred degrees across at the biggest, each turned to celestial north so they wheel with
 * the sky through the night. A theme shows some of them at night, each leaning toward its own colour. The dark dust that crosses them is laid over the stars behind first,
 * then the glowing gas is added on top. Painted once on Sol's CPU thread ({@link NebulaTex}). Render thread only;
 * nothing allocated per frame.
 */
final class Nebula {
    private static final int N = NebulaTex.BIG.length;
    private static final SolTexture[] emission = new SolTexture[N], dust = new SolTexture[N];
    private static final NebulaTex.Pictures[] ready = new NebulaTex.Pictures[N];
    private static boolean asked;

    private static final float[] w = new float[3], nd = new float[2];

    private Nebula() {
    }

    static void render(java.util.List<io.github.georgwav.cosmetics.sol.theme.ThemeSpec.Nebula> list, float rain) {
        SkySphere sp = NightSky.SPHERE;
        float k = Ease.smoothstep(-6f, -16f, sp.sunAlt) * (1f - rain);
        if (k <= 0.004f) {
            return;
        }
        if (!asked) {
            asked = true;
            SolExecutors.CPU.execute(Nebula::bake);
        }
        double raS = Math.toRadians(sp.lamSun);
        for (int j = 0; j < list.size(); j++) {
            var own = list.get(j);
            int i = own.big() >= 0 ? own.big() : j;
            if (i >= N || !uploaded(i)) {
                continue;
            }
            NebulaTex.Spec n = NebulaTex.BIG[i];
            int tint = Colors.mix(0xFFFFFFFF, own.color(), own.tint());
            double a = raS + Math.PI + Math.toRadians(n.dra() * 15), d = Math.toRadians(n.dec());
            sp.toWorld(Math.cos(d) * Math.cos(a), Math.cos(d) * Math.sin(a), Math.sin(d), w);
            // Thinned by the air near the horizon, gone below it.
            float ext = Ease.smoothstep(-0.05f, 0.25f, w[1]);
            if (ext <= 0.004f) {
                continue;
            }
            SkyDraw.billboard(w[0], w[1], w[2]);
            SkyDraw.along(sp.pole[0], sp.pole[1], sp.pole[2], nd);
            float turn = (float) (Math.atan2(nd[0], nd[1]) + Math.toRadians(n.rot()));
            float rx = (float) Math.tan(Math.toRadians(n.w() / 2)) / SkyDraw.T, ry = rx * (float) (n.h() / n.w());
            WorldDraw.begin(WorldDraw.SKY_ALPHA, dust[i]);
            SkyDraw.rotRect(0f, 0f, turn, -rx, -ry, rx, ry, Colors.withAlpha(0xFFFFFFFF, Math.min(1f, k * ext * 0.9f * own.strength())));
            WorldDraw.end();
            WorldDraw.begin(WorldDraw.SKY_ADD, emission[i]);
            SkyDraw.rotRect(0f, 0f, turn, -rx, -ry, rx, ry, Colors.withAlpha(tint, Math.min(1f, k * ext * (float) n.a() * own.strength())));
            WorldDraw.end();
        }
    }

    /** Whether nebula i's pictures are on the GPU, uploading them once they're painted. */
    private static boolean uploaded(int i) {
        if (emission[i] != null && !emission[i].released() && dust[i] != null && !dust[i].released()) {
            return true;
        }
        NebulaTex.Pictures p;
        synchronized (ready) {
            p = ready[i];
        }
        if (p == null) {
            // Released with the GPU's textures (a resource reload): paint again.
            if (emission[i] != null) {
                emission[i] = null;
                asked = false;
            }
            return false;
        }
        emission[i] = SolTexture.of("nebula_" + i, NebulaTex.W, NebulaTex.H, true, p.emission());
        dust[i] = SolTexture.of("nebula_dust_" + i, NebulaTex.W, NebulaTex.H, true, p.dust());
        synchronized (ready) {
            ready[i] = null;
        }
        return true;
    }

    private static void bake() {
        for (int i = 0; i < N; i++) {
            try {
                NebulaTex.Spec n = NebulaTex.BIG[i];
                NebulaTex.Pictures p = NebulaTex.bake(n.seed(), n.pal());
                synchronized (ready) {
                    ready[i] = p;
                }
            } catch (RuntimeException e) {
                CosmeticsMod.LOGGER.warn("[Sol] Couldn't paint nebula {}", i, e);
            }
        }
    }
}
