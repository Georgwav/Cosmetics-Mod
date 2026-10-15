package io.github.georgwav.cosmetics.sol.ui.kit;

import io.github.georgwav.cosmetics.sol.render.SolTexture;
import io.github.georgwav.cosmetics.sol.theme.ThemeRegistry;
import io.github.georgwav.cosmetics.sol.theme.ThemeSpec;
import io.github.georgwav.cosmetics.sol.util.Colors;
import io.github.georgwav.cosmetics.sol.util.Mulberry32;
import io.github.georgwav.cosmetics.sol.util.PixelCanvas;
import io.github.georgwav.cosmetics.sol.util.Sprites;
import io.github.georgwav.cosmetics.sol.util.SunCpu;

/**
 * The Sol Client logo: "S·L" in big pixel letters with the live Sol sun as the O, "CLIENT" small underneath, the
 * theme's weather drifting through it, Aurora's ribbons behind it and a sparkle on the sun. A port of the Sol menu
 * prototype's {@code drawLogo} on a 112 x 72 pixel canvas, redrawn about 20 times a second into one texture.
 */
public final class LogoRenderer {
    public static final int W = 112, H = 72;
    private static final String[][] BIG = {
            {".####", "#....", "#....", ".###.", "....#", "....#", "####."},
            {"#....", "#....", "#....", "#....", "#....", "#....", "#####"}};
    private static final int[] BIG_X = {36, 66};
    private static final String[][] SMALL = {
            {"###", "#..", "#..", "#..", "###"}, {"#..", "#..", "#..", "#..", "###"}, {"###", ".#.", ".#.", ".#.", "###"},
            {"###", "#..", "##.", "#..", "###"}, {"#..#", "##.#", "#.##", "#..#", "#..#"}, {"###", ".#.", ".#.", ".#.", ".#."}};

    private final boolean background;
    private final PixelCanvas canvas = new PixelCanvas(W, H);
    private final int[] pixels = new int[W * H];
    private SolTexture texture;
    private float drawnAt = -1f;
    private String partsFor;
    private final float[] px = new float[28], py = new float[28], ph = new float[28], sp = new float[28];
    private final int[] pc = new int[28];
    private String partType = "dust";

    /** {@code background}: with the theme's sky behind it (the title screen); without: on glass. */
    public LogoRenderer(boolean background) {
        this.background = background;
    }

    /** The logo for theme {@code e} at time {@code t}, {@code dt} since the last call. */
    public SolTexture texture(ThemeRegistry.Entry e, float t, float dt) {
        if (texture == null || texture.released()) {
            texture = new SolTexture("logo_" + (background ? "bg" : "glass"), W, H, false);
            drawnAt = -1f;
        }
        step(e, dt, t);
        if (drawnAt < 0f || t - drawnAt >= 0.05f || t < drawnAt) {
            drawnAt = t;
            draw(e.id(), e.base(), t);
            texture.upload(canvas.toArgb(pixels));
        }
        return texture;
    }

    public void release() {
        if (texture != null) {
            texture.release();
        }
    }

    private void step(ThemeRegistry.Entry e, float dt, float t) {
        if (!e.id().equals(partsFor)) {
            partsFor = e.id();
            ThemeSpec th = e.base();
            int[] cols = th.particles().isEmpty() ? new int[]{0xFFFFFFFF} : th.particles().get(0).colors();
            partType = th.particles().isEmpty() ? "dust" : th.particles().get(0).type();
            Mulberry32 r = new Mulberry32(e.id().length() * 31 + 7);
            for (int i = 0; i < 28; i++) {
                px[i] = (float) r.next() * W;
                py[i] = (float) r.next() * H;
                pc[i] = cols[(int) Math.floor(r.next() * cols.length)];
                ph[i] = (float) r.next() * 6.283f;
                sp[i] = 0.6f + (float) r.next() * 0.8f;
            }
        }
        if (dt <= 0f) {
            return;
        }
        for (int i = 0; i < 28; i++) {
            float w = (float) Math.sin(t * 0.8f + ph[i]);
            switch (partType) {
                case "ember", "note" -> {
                    py[i] -= 6 * sp[i] * dt;
                    px[i] += w * 2 * dt;
                }
                case "dust", "sparkle" -> {
                    py[i] -= 0.8f * dt;
                    px[i] += w * 0.6f * dt;
                }
                case "firefly" -> {
                    px[i] += (float) Math.sin(t * 0.4f + ph[i]) * 3 * dt;
                    py[i] += (float) Math.cos(t * 0.3f + ph[i]) * 2 * dt;
                }
                case "rain" -> {
                    py[i] += 60 * sp[i] * dt;
                    px[i] -= 12 * dt;
                }
                default -> {
                    py[i] += 5 * sp[i] * dt;
                    px[i] += (2 + w * 3) * dt;
                }
            }
            if (py[i] > 74) {
                py[i] = -2;
                px[i] = (float) Math.random() * W;
            }
            if (py[i] < -2) {
                py[i] = 74;
                px[i] = (float) Math.random() * W;
            }
            if (px[i] > 114) {
                px[i] = -2;
            }
            if (px[i] < -2) {
                px[i] = 114;
            }
        }
    }

    private void draw(String id, ThemeSpec th, float t) {
        PixelCanvas g = canvas;
        int sky0 = th.sky().top(), sky1 = th.sky().middle(), sky2 = th.sky().horizon(), glow = th.sky().glow();
        g.clear();
        g.setGlobalAlpha(1f);
        g.setLighter(false);
        float cx = 56f, cy = 34f;
        if (background) {
            g.fillRect(0, 0, W, H, PixelCanvas.linear(0, 0, 0, H, PixelCanvas.Stops.of(0f, sky0, 0.62f, sky1, 1f, sky2)));
            if (th.stars().count() > 0) {
                for (int i = 0; i < 24; i++) {
                    g.setGlobalAlpha(0.3f + 0.7f * (0.5f + 0.5f * (float) Math.sin(t * (1 + i % 3) + i)));
                    g.fillRect((i * 37 + 11) % W, (i * 23 + 5) % 44, 1, 1, 0xFFFFFFFF);
                }
                g.setGlobalAlpha(1f);
            }
            g.fillRect(0, H - 20, W, 20, PixelCanvas.linear(0, H - 20, 0, H, PixelCanvas.Stops.of(0f, Colors.withAlpha(glow, 0f), 1f,
                    Colors.withAlpha(glow, 0.55f))));
            if (th.galaxy() != null) {
                g.save();
                g.translate(78, 20);
                g.rotate(-0.35f);
                g.scale(1f, 0.42f);
                g.rotate(t * 0.02f);
                g.setLighter(true);
                g.setGlobalAlpha(0.9f);
                g.drawImage(Sprites.galaxy(), -40, -40, 80, 80, true);
                g.restore();
            }
        }
        if (th.aurora() != null && th.aurora().length > 0) {
            aurora(g, th.aurora(), t, cx, cy);
        }
        SunCpu.drawBack(g, H, cx, cy, 8f, 0.25f, Colors.mix(0xFFFFF1CF, glow, 0.22f), glow, SunCpu.LOGO, t, null);
        word(g, sky0, glow, t);
        small(g, sky0, glow);
        int s = Math.round(1 + 2 * (float) Math.pow(0.5 + 0.5 * Math.sin(t * 2.2), 3));
        g.setLighter(true);
        g.fillRect(65 - s, 25, s * 2 + 1, 1, 0xFFFFFFFF);
        g.fillRect(65, 25 - s, 1, s * 2 + 1, 0xFFFFFFFF);
        g.setLighter(false);
        parts(g, t);
    }

    private void aurora(PixelCanvas g, int[] cols, float t, float cx, float cy) {
        g.setLighter(true);
        for (int ri = 0; ri < 3; ri++) {
            PixelCanvas lo = Sprites.strip(cols[ri % cols.length]), hi = Sprites.strip(cols[(ri + 1) % cols.length]);
            for (int x = 0; x < W; x++) {
                float f = switch (ri) {
                    case 0 -> cy - 21 + (float) Math.pow((x - cx) / (W / 2f), 2) * 16 + (float) Math.sin(x * 0.17 + t * 0.9) * 2;
                    case 1 -> cy - 5 + (float) Math.sin(x * 0.08 + t * 0.55 + 1) * 5 - (x / (float) W) * 8;
                    default -> cy + 23 + (float) Math.sin(x * 0.12 - t * 0.7) * 2.5f - (float) Math.pow((x - cx) / (W / 2f), 2) * 5;
                };
                int y0 = Math.round(f);
                int len = Math.round(7 + 5 * (float) Math.sin(x * 0.31 + t * 1.3 + ri * 2) + (ri == 0 ? 4 : 0));
                float a = Math.max(0f, (0.38f + 0.3f * (float) Math.sin(x * 0.45 + t * 2.1 + ri)) * (ri == 2 ? 0.7f : 1f));
                g.setGlobalAlpha(a);
                g.drawImage(lo, x, y0 - len, 1, len + 2, true);
                g.setGlobalAlpha(a * 0.6f);
                g.drawImage(hi, x, Math.round(y0 - len * 1.8f), 1, len, true);
            }
        }
        g.setGlobalAlpha(1f);
        g.setLighter(false);
    }

    private interface Cell {
        void at(int x, int y, int row);
    }

    private static void cells(Cell fn) {
        int u = 2, y0 = 27;
        for (int l = 0; l < 2; l++) {
            String[] rows = BIG[l];
            for (int r = 0; r < 7; r++) {
                for (int c = 0; c < 5; c++) {
                    if (rows[r].charAt(c) == '#') {
                        fn.at(BIG_X[l] + c * u, y0 + r * u, r);
                    }
                }
            }
        }
    }

    private static void word(PixelCanvas g, int sky0, int glow, float t) {
        int u = 2, y0 = 27;
        int outline = Colors.mix(sky0, 0xFF000000, 0.6f);
        cells((x, y, r) -> {
            g.fillRect(x - 1, y, u + 2, u, outline);
            g.fillRect(x, y - 1, u, u + 2, outline);
        });
        int deep = Colors.mix(sky0, 0xFF000000, 0.3f), side = Colors.mix(glow, sky0, 0.55f);
        for (int k = 3; k >= 1; k--) {
            final int kk = k;
            final int col = k == 1 ? side : deep;
            cells((x, y, r) -> g.fillRect(x + kk, y + kk, u, u, col));
        }
        float gx = ((t * 40f) % 230f) + 10f;
        cells((x, y, r) -> {
            float d = x + (y - y0) * 0.7f - gx;
            int c = (d >= 0 && d < 3) || r == 0 ? 0xFFFFFFFF : Colors.mix(0xFFFFF6E0, glow, Math.min(1f, r / 6f * 0.9f));
            g.fillRect(x, y, u, u, c);
        });
    }

    private static void small(PixelCanvas g, int sky0, int glow) {
        int w = -1;
        for (String[] ch : SMALL) {
            w += ch[0].length() + 1;
        }
        int x = Math.round(56 - w / 2f);
        int y = 50;
        int sh = Colors.mix(sky0, 0xFF000000, 0.45f), face = Colors.mix(0xFFFFFFFF, glow, 0.45f);
        for (String[] rows : SMALL) {
            for (int pass = 0; pass < 2; pass++) {
                for (int r = 0; r < rows.length; r++) {
                    for (int c = 0; c < rows[r].length(); c++) {
                        if (rows[r].charAt(c) == '#') {
                            if (pass == 0) {
                                g.fillRect(x + c + 1, y + r + 1, 1, 1, sh);
                            } else {
                                g.fillRect(x + c, y + r, 1, 1, face);
                            }
                        }
                    }
                }
            }
            x += rows[0].length() + 1;
        }
    }

    private void parts(PixelCanvas g, float t) {
        boolean glowy = partType.equals("ember") || partType.equals("dust") || partType.equals("sparkle") || partType.equals("firefly");
        for (int i = 0; i < 28; i++) {
            float a = 1f;
            switch (partType) {
                case "ember" -> a = 0.5f + 0.5f * (float) Math.sin(t * 6 + ph[i]);
                case "dust", "sparkle" -> a = 0.3f + 0.7f * (float) Math.pow(0.5 + 0.5 * Math.sin(t * 1.5 + ph[i]), 2);
                case "firefly" -> a = (float) Math.pow(Math.max(0, Math.sin(t * 0.9 + ph[i])), 2);
                default -> {
                }
            }
            g.setLighter(glowy);
            g.setGlobalAlpha(a);
            int x = Math.round(px[i]), y = Math.round(py[i]);
            switch (partType) {
                case "rain" -> g.fillRect(x, y, 1, 3, pc[i]);
                case "petal", "leaf", "blossom" -> g.fillRect(x, y, Math.sin(t * 2 + ph[i]) > 0 ? 2 : 1, 1, pc[i]);
                case "sparkle" -> {
                    g.fillRect(x - 1, y, 3, 1, pc[i]);
                    g.fillRect(x, y - 1, 1, 3, pc[i]);
                }
                default -> g.fillRect(x, y, 1, 1, pc[i]);
            }
        }
        g.setGlobalAlpha(1f);
        g.setLighter(false);
    }
}
