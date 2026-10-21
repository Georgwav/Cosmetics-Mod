package io.github.georgwav.cosmetics.sol.util;

import io.github.georgwav.cosmetics.sol.theme.ThemeParser;
import io.github.georgwav.cosmetics.sol.theme.ThemeSpec;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * A theme's showpiece as a small picture: the prototypes' 2D {@code render()} on the CPU, for preset cards, the
 * drawer's sky preview and the World section. The same layers in the same order: sky, nebula, stars, galaxy,
 * aurora, sun (or moon or eclipse), horizon band, rune, ridges, mist, three terrain layers with fog between them,
 * the theme's particles, Monsoon's flash and the vignette. A still frame at time {@code t}, made on a worker.
 */
public final class MiniSky {
    /** The prototypes' terrain colours (top, side) per built-in theme; a pack theme gets grass tinted by its fog. */
    private static final Map<String, int[]> GROUND = Map.ofEntries(
            Map.entry("sakura_dusk", new int[]{0xFF6F8A3C, 0xFF7A5A44}),
            Map.entry("frost_morning", new int[]{0xFFE9EEF3, 0xFFB8C2C9}),
            Map.entry("ember_solstice", new int[]{0xFFFCCF71, 0xFFE8A64E}),
            Map.entry("aurora", new int[]{0xFF2B4A3C, 0xFF1D2B2A}),
            Map.entry("firefly_meadow", new int[]{0xFF4F7A2E, 0xFF5A4630}),
            Map.entry("monsoon", new int[]{0xFF3D5A3A, 0xFF3A3833}),
            Map.entry("maple_afternoon", new int[]{0xFF9C8A35, 0xFF7A5235}),
            Map.entry("eclipse", new int[]{0xFF3A2A28, 0xFF1F1615}),
            Map.entry("deep_field", new int[]{0xFF1B1B1F, 0xFF0A0A0C}),
            Map.entry("silver_night", new int[]{0xFF2F4A3A, 0xFF232A35}),
            Map.entry("grace", new int[]{0xFF16291F, 0xFF08110D}),
            Map.entry("empty", new int[]{0xFF5D9B3A, 0xFF866043}));
    private static final int[] NOTE = {0b000110, 0b000101, 0b000100, 0b000100, 0b011100, 0b111100, 0b011000};

    private record Part(String type, float x, float y, float z, float ph, float a, float f, int c, float s) {
    }

    private record Star(float x, float y, int s, float ph, float sp) {
    }

    private MiniSky() {
    }

    /** The theme's frame at {@code w} x {@code h} pixels (the prototypes' cards are 112 x 46 at 2x). */
    public static PixelCanvas render(ThemeSpec th, String id, int w, int h, float t) {
        return render(th, id, w, h, t, true);
    }

    /** The same, with or without the theme's particles (left out where they are drawn live over the picture). */
    public static PixelCanvas render(ThemeSpec th, String id, int w, int h, float t, boolean particles) {
        PixelCanvas g = new PixelCanvas(w, h);
        float W = w, H = h, sc = H / 560f, hz = H * 0.64f;
        int[] sky = {th.sky().top(), th.sky().middle(), th.sky().horizon()};
        int glowC = th.sky().glow(), fog = th.fog().color();
        float fd = th.fog().density();
        Mulberry32 r = new Mulberry32(id.length() * 977 + id.charAt(0));
        List<Part> parts = new ArrayList<>();
        for (ThemeSpec.Emitter e : th.particles()) {
            for (int i = 0; i < e.count(); i++) {
                boolean lens = "lens".equals(e.type());
                float x = (float) r.next();
                float y = "firefly".equals(e.type()) ? 0.62f + (float) r.next() * 0.33f : (float) r.next();
                float z = lens ? 0.1f : 0.25f + (float) r.next() * 0.75f;
                float ph = (float) r.next() * 6.283f, a = (float) r.next() * 6.283f, f = (float) r.next() * 6.283f;
                int c = e.colors()[(int) Math.floor(r.next() * e.colors().length)];
                float s = e.minSize() + (e.maxSize() - e.minSize()) * (float) r.next();
                parts.add(new Part(e.type(), x, y, z, ph, a, f, c, s));
            }
        }
        parts.sort(Comparator.comparingDouble(p -> -p.z()));
        List<Star> stars = new ArrayList<>();
        for (int i = 0; i < th.stars().count(); i++) {
            stars.add(new Star((float) r.next(), (float) r.next() * 0.6f, r.next() < 0.15 ? 2 : 1, (float) r.next() * 6.283f,
                    0.5f + (float) r.next() * 2f));
        }

        // Sky and the fog below the horizon.
        g.fillRect(0, 0, W, hz + 1, PixelCanvas.linear(0, 0, 0, hz, PixelCanvas.Stops.of(0f, sky[0], 0.55f, sky[1], 1f, sky[2])));
        g.fillRect(0, hz, W, H - hz, fog);
        // The nebulae, which only show at night.
        g.setLighter(true);
        for (ThemeSpec.Nebula n : "night".equals(th.show().phase()) ? th.nebula() : java.util.List.<ThemeSpec.Nebula>of()) {
            g.setGlobalAlpha((0.38f + 0.06f * (float) Math.sin(t * 0.3f + n.x() * 9f)) * Math.min(1f, n.strength()));
            float rr = n.r() * W;
            g.drawImage(Sprites.glow(n.color()), n.x() * W - rr, n.y() * H - rr * 0.7f, rr * 2f, rr * 1.4f, true);
        }
        g.setLighter(false);
        // Stars.
        for (Star s : stars) {
            g.setGlobalAlpha(0.35f + 0.65f * (0.5f + 0.5f * (float) Math.sin(t * s.sp() + s.ph())));
            float ss = s.s() * Math.max(1f, sc);
            g.fillRect(Math.round(s.x() * W), Math.round(s.y() * hz), ss, ss, 0xFFFFFFFF);
        }
        g.setGlobalAlpha(1f);
        // Galaxy.
        if (th.galaxy() != null) {
            ThemeSpec.Galaxy gx = th.galaxy();
            float rr = gx.r() * W;
            g.save();
            g.translate(gx.x() * W, gx.y() * H);
            g.rotate(-0.35f);
            g.scale(1f, gx.tilt());
            g.rotate(t * 0.02f);
            g.setLighter(true);
            g.setGlobalAlpha(0.92f);
            g.drawImage(Sprites.galaxy(), -rr, -rr, rr * 2f, rr * 2f, true);
            g.restore();
        }
        // Aurora.
        if (th.aurora() != null) {
            g.setLighter(true);
            for (int ri = 0; ri < th.aurora().length; ri++) {
                PixelCanvas sp = Sprites.strip(th.aurora()[ri]);
                float stp = Math.max(3f, 4f * sc);
                for (float x = 0; x < W; x += stp) {
                    float u = x / W;
                    float y0 = H * (0.1f + 0.07f * ri) + (float) Math.sin(u * 6 + t * 0.25f + ri * 2) * H * 0.045f
                            + (float) Math.sin(u * 14 + t * 0.45f) * H * 0.014f;
                    float len = H * (0.14f + 0.06f * (float) Math.sin(u * 9 + t * 0.35f + ri));
                    g.setGlobalAlpha(Math.min(1f, 0.22f * (0.55f + 0.45f * (float) Math.sin(u * 21 + t * 0.8f + ri))));
                    g.drawImage(sp, x, y0, stp + 1, len, true);
                }
            }
            g.setGlobalAlpha(1f);
            g.setLighter(false);
        }
        // Sun, moon or eclipse.
        ThemeSpec.Sun sun = th.sun();
        boolean night = "night".equals(th.show().phase());
        if (sun != null && th.eclipse() == null && !night) {
            float sx = sun.x() * W, sy = sun.y() * H;
            float e = Math.max(0f, Math.min(1f, (hz - sy) / (hz * 0.85f)));
            if (th.vanillaSun()) {
                float s = sun.size() * H;
                g.setLighter(true);
                g.setGlobalAlpha(0.35f);
                float rr = s * 3.2f;
                g.drawImage(Sprites.glow(0xFFFFF2C0), sx - rr, sy - rr, rr * 2f, rr * 2f, true);
                g.setLighter(false);
                g.setGlobalAlpha(1f);
                g.fillRect(sx - s, sy - s, s * 2f, s * 2f, 0xFFFFF3A8);
                g.fillRect(sx - s * 0.5f, sy - s * 0.5f, s, s, 0xFFFFFFFF);
            } else {
                float q = Math.round(e * 20f) / 20f;
                float s = sun.size() * H * (1.75f - 0.75f * q);
                SunCpu.drawBack(g, H, sx, sy, s, q, SunCpu.core(q), SunCpu.halo(sun.halo(), q), SunCpu.HERO, t, null);
            }
        }
        ThemeSpec.Moon moon = th.moon();
        ThemeSpec.Moon unplaced = ThemeParser.DEFAULTS.moon();
        if (moon != null && (moon.x() != unplaced.x() || moon.y() != unplaced.y())) {
            float x = moon.x() * W, y = moon.y() * H, s = moon.size() * H;
            g.setLighter(true);
            g.setGlobalAlpha(moon.haloAlpha());
            float rr = moon.haloRadius() * H;
            g.drawImage(Sprites.glow(moon.halo()), x - rr, y - rr, rr * 2f, rr * 2f, true);
            g.setLighter(false);
            g.setGlobalAlpha(1f);
            g.fillRect(x - s, y - s, s * 2f, s * 2f, moon.color());
            int crater = Colors.argb(89, 120, 130, 160);
            g.fillRect(x - s * 0.5f, y - s * 0.4f, s * 0.45f, s * 0.45f, crater);
            g.fillRect(x + s * 0.2f, y + s * 0.15f, s * 0.35f, s * 0.35f, crater);
            g.fillRect(x - s * 0.1f, y + s * 0.45f, s * 0.25f, s * 0.25f, crater);
        }
        if (th.eclipse() != null) {
            ThemeSpec.Eclipse ec = th.eclipse();
            float x = ec.x() * W, y = ec.y() * H, s = ec.size() * H;
            g.setLighter(true);
            g.setGlobalAlpha(0.9f);
            g.drawImage(Sprites.glow(ec.corona()), x - s * 4.5f, y - s * 4.5f, s * 9f, s * 9f, true);
            g.setGlobalAlpha(0.7f);
            g.drawImage(Sprites.glow(0xFFFFD0A0), x - s * 2f, y - s * 2f, s * 4f, s * 4f, true);
            g.setLighter(false);
            g.setGlobalAlpha(1f);
            g.fillRect(x - s, y - s, s * 2f, s * 2f, 0xFF050303);
            g.strokeRect(x - s, y - s, s * 2f, s * 2f, Math.max(1f, 1.5f * sc), Colors.argb(217, 255, 170, 120));
        }
        // Horizon glow band.
        g.fillRect(0, hz - H * 0.13f, W, H * 0.18f, PixelCanvas.linear(0, hz - H * 0.13f, 0, hz + H * 0.05f,
                PixelCanvas.Stops.of(0f, Colors.withAlpha(glowC, 0f), 0.72f, Colors.withAlpha(glowC, 0.75f), 1f, Colors.withAlpha(glowC, 0f))));
        if (th.sigil() != null) {
            sigil(g, th, W, H, hz, t);
        }
        if (!th.noRidges()) {
            ridges(g, W, H, sky, glowC, hz);
        }
        if (th.mist() != null) {
            mist(g, th, W, H, hz, t);
        }
        int[] ground = GROUND.getOrDefault(id, new int[]{Colors.mix(0xFF5D9B3A, fog, 0.3f), Colors.mix(0xFF866043, fog, 0.3f)});
        terrain(g, W, H, ground, fog, fd, 0);
        g.fillRect(0, hz - H * 0.02f, W, H * 0.16f, PixelCanvas.linear(0, hz - H * 0.02f, 0, hz + H * 0.14f,
                PixelCanvas.Stops.of(0f, Colors.withAlpha(fog, 0.85f), 1f, Colors.withAlpha(fog, 0f))));
        terrain(g, W, H, ground, fog, fd, 1);
        g.fillRect(0, hz + H * 0.03f, W, H * 0.17f, PixelCanvas.linear(0, hz + H * 0.03f, 0, hz + H * 0.2f,
                PixelCanvas.Stops.of(0f, Colors.withAlpha(fog, 0.45f * fd + 0.15f), 1f, Colors.withAlpha(fog, 0f))));
        terrain(g, W, H, ground, fog, fd, 2);
        if (fd > 0.6f) {
            g.fillRect(0, hz, W, H - hz, Colors.withAlpha(fog, (fd - 0.6f) * 0.9f));
        }
        for (Part p : particles ? parts : List.<Part>of()) {
            part(g, p, W, H, sc, fd, t);
        }
        g.setGlobalAlpha(1f);
        g.setLighter(false);
        // Vignette.
        float cx = W / 2f, cy = H * 0.55f, r0 = Math.min(W, H) * 0.35f, r1 = Math.max(W, H) * 0.75f;
        g.fillRect(0, 0, W, H, PixelCanvas.radial(cx, cy, r1, PixelCanvas.Stops.of(0f, 0x00000000, r0 / r1, 0x00000000, 1f,
                Colors.argb(82, 0, 0, 0))));
        return g;
    }

    private static void ridges(PixelCanvas g, float W, float H, int[] sky, int glow, float hz) {
        float b = Math.max(1, Math.round(H * 0.006f));
        int cols = (int) Math.ceil(W / b) + 2;
        float[][] layers = {{0.2f, 5f, 0.5f, 0.6f}, {0.13f, 41f, 0.78f, 0.9f}};
        for (float[] l : layers) {
            int fill = Colors.mix(sky[2], sky[0], l[2]);
            int rim = Colors.withAlpha(glow, l[3]);
            float prev = Float.NaN;
            for (int i = 0; i < cols; i++) {
                float seed = l[1];
                float n = ((float) Math.sin(i * 0.021f + seed) * 0.5f + 0.5f) * 0.6f
                        + ((float) Math.sin(i * 0.057f + seed * 1.7f) * 0.5f + 0.5f) * 0.3f
                        + ((float) Math.sin(i * 0.13f + seed * 0.3f) * 0.5f + 0.5f) * 0.1f;
                float y = Math.round((hz + H * 0.01f - n * l[0] * H) / b) * b, x = (float) Math.floor(i * b);
                g.fillRect(x, y, b + 1, hz + H * 0.06f - y, fill);
                g.fillRect(x, y, b + 1, Math.max(1f, b * 0.5f), rim);
                if (!Float.isNaN(prev) && prev != y) {
                    float top = Math.min(prev, y), hgt = Math.abs(prev - y);
                    g.fillRect(prev < y ? x : x - Math.max(1f, b * 0.5f) + 1, top, Math.max(1f, b * 0.5f), hgt, rim);
                }
                prev = y;
            }
        }
    }

    private static void terrain(PixelCanvas g, float W, float H, int[] ground, int fog, float fd, int layer) {
        float[][] L = {{0.011f, 0.655f, 3f, 0.62f + 0.33f * fd, 11f}, {0.022f, 0.7f, 4f, 0.3f + 0.45f * fd, 23f},
                {0.05f, 0.81f, 3f, 0.04f + 0.45f * fd, 37f}};
        float[] l = L[layer];
        float b = Math.max(2f, H * l[0]), base = H * l[1], seed = l[4];
        int top = Colors.mix(ground[0], fog, l[3]);
        int side = Colors.mix(ground[1], fog, Math.min(1f, l[3] + 0.04f));
        int dirt = Colors.mix(Colors.mix(ground[1], 0xFF000000, 0.1f), fog, Math.min(1f, l[3] + 0.02f));
        int cols = (int) Math.ceil(W / b) + 2;
        for (int i = 0; i < cols; i++) {
            float x = (float) Math.floor(i * b);
            float hh = (float) Math.floor(((float) Math.sin(i * 0.16f + seed) * 0.5f + 0.5f) * l[2]
                    + ((float) Math.sin(i * 0.047f + seed * 2.1f) * 0.5f + 0.5f) * (l[2] + 1.5f));
            float y = Math.round(base - hh * b);
            g.fillRect(x, y + b, (float) Math.ceil(b) + 1, H - y, side);
            g.fillRect(x, y + b, (float) Math.ceil(b) + 1, (float) Math.ceil(b * 0.35f), dirt);
            g.fillRect(x, y, (float) Math.ceil(b) + 1, (float) Math.ceil(b), top);
        }
    }

    private static void part(PixelCanvas g, Part p, float W, float H, float sc, float fd, float t) {
        float px = p.x() * W, py = p.y() * H;
        float a = 1f - (p.z() - 0.25f) / 0.75f * fd * 0.75f;
        if (a <= 0f) {
            return;
        }
        float s = p.s() * sc * (0.5f / p.z());
        g.setGlobalAlpha(Math.min(1f, a));
        g.setLighter(false);
        switch (p.type()) {
            case "petal", "ash" -> {
                g.save();
                g.translate(px, py);
                g.rotate(p.a());
                g.scale(Math.max(0.18f, Math.abs((float) Math.cos(p.f()))), 1f);
                g.fillEllipse(0, 0, s, s * ("ash".equals(p.type()) ? 0.7f : 0.58f), p.c());
                g.restore();
            }
            case "blossom" -> {
                g.save();
                g.translate(px, py);
                g.rotate(p.a());
                for (int i = 0; i < 5; i++) {
                    float an = i * 1.2566f;
                    g.fillEllipse((float) Math.cos(an) * s * 0.55f, (float) Math.sin(an) * s * 0.55f, s * 0.42f, s * 0.42f, p.c());
                }
                g.fillEllipse(0, 0, s * 0.28f, s * 0.28f, 0xFFFFF1F6);
                g.restore();
            }
            case "leaf" -> {
                g.save();
                g.translate(px, py);
                g.rotate(p.a());
                g.scale(Math.max(0.2f, Math.abs((float) Math.cos(p.f()))), 1f);
                g.fillPolygon(new float[]{0, s * 0.62f, 0, -s * 0.62f}, new float[]{-s, 0, s, 0}, p.c());
                g.line(0, -s, 0, s, Math.max(0.6f, s * 0.08f), PixelCanvas.solid(Colors.argb(89, 60, 20, 10)));
                g.restore();
            }
            case "snow" -> g.fillEllipse(px, py, s * 0.6f, s * 0.6f, p.c());
            case "flake" -> {
                g.save();
                g.translate(px, py);
                g.rotate(p.a());
                for (int i = 0; i < 3; i++) {
                    float an = i * 1.0472f;
                    g.line((float) Math.cos(an) * -s, (float) Math.sin(an) * -s, (float) Math.cos(an) * s, (float) Math.sin(an) * s,
                            Math.max(0.7f, s * 0.16f), PixelCanvas.solid(p.c()));
                }
                g.restore();
            }
            case "rain" -> {
                float k = 0.35f / p.z();
                g.line(px, py, px - 6 * k * sc, py + 26 * k * sc, Math.max(0.6f, 0.9f * k), PixelCanvas.solid(Colors.withAlpha(p.c(), 0.5f)));
            }
            case "ember", "dust", "firefly", "sparkle" -> {
                float al = a;
                switch (p.type()) {
                    case "ember" -> al *= 0.55f + 0.45f * (float) Math.sin(t * 6 + p.ph());
                    case "dust" -> al *= 0.35f + 0.4f * (0.5f + 0.5f * (float) Math.sin(t * 1.7f + p.ph()));
                    case "firefly" -> al *= (float) Math.pow(Math.max(0, Math.sin(t * 0.9f + p.ph())), 3);
                    default -> al *= (float) Math.pow(0.5f + 0.5f * Math.sin(t * 1.3f + p.ph()), 4);
                }
                if (al <= 0.01f) {
                    return;
                }
                g.setLighter(true);
                g.setGlobalAlpha(Math.min(1f, al));
                if ("sparkle".equals(p.type())) {
                    g.fillRect(px - s, py - 0.5f, s * 2f, 1f, p.c());
                    g.fillRect(px - 0.5f, py - s, 1f, s * 2f, p.c());
                }
                float rr = s * ("dust".equals(p.type()) ? 2.2f : 3.2f);
                g.drawImage(Sprites.glow(p.c()), px - rr, py - rr, rr * 2f, rr * 2f, true);
                g.setLighter(false);
            }
            case "note" -> {
                float q = Math.max(1f, s * 0.22f);
                g.setLighter(true);
                g.setGlobalAlpha(Math.min(1f, a * 0.35f));
                float rr = q * 7f;
                g.drawImage(Sprites.glow(p.c()), px - rr, py - rr, rr * 2f, rr * 2f, true);
                g.setLighter(false);
                g.setGlobalAlpha(Math.min(1f, a * 0.92f));
                for (int row = 0; row < NOTE.length; row++) {
                    for (int c = 0; c < 6; c++) {
                        if ((NOTE[row] >> (5 - c) & 1) != 0) {
                            g.fillRect(Math.round(px + (c - 3) * q), Math.round(py + (row - 3.5f) * q), (float) Math.ceil(q),
                                    (float) Math.ceil(q), p.c());
                        }
                    }
                }
            }
            case "lens" -> {
                g.setGlobalAlpha(0.26f);
                g.save();
                g.translate(px, py);
                g.rotate(p.a());
                g.scale(1f, 0.6f);
                g.drawImage(Sprites.glow(p.c()), -s, -s, s * 2f, s * 2f, true);
                g.restore();
            }
            default -> {
            }
        }
    }

    /** The prototypes' {@code sigilUnit}: the rune's unit on screen, capped by width so it stays in the frame. */
    private static float sigilUnit(float W, float H, ThemeSpec.Sigil s) {
        return s.r() * Math.min(H, W * 1.15f);
    }

    private static void sigil(PixelCanvas g, ThemeSpec th, float W, float H, float hz, float t) {
        ThemeSpec.Sigil gs = th.sigil();
        Sprites.Sigil sp = Sprites.sigil(gs.color(), gs.halo());
        float u = sigilUnit(W, H, gs), k = u / sp.unit(), x = gs.x() * W, y = gs.y() * H;
        float x0 = x - sp.cx() * k, y0 = y - sp.cy() * k, w = sp.width() * k, h = sp.height() * k;
        float yb = y + (float) Math.sqrt(3) * u;
        float pulse = 0.86f + 0.09f * (float) Math.sin(t * 1.2f) + 0.05f * (float) Math.sin(t * 3.1f);
        g.setLighter(true);
        g.setGlobalAlpha(0.6f * pulse);
        float rr = u * 4.8f;
        g.drawImage(Sprites.glow(gs.halo()), x - rr, yb - rr * 0.55f, rr * 2f, rr * 1.05f, true);
        g.setGlobalAlpha(0.3f * pulse);
        rr = u * 2.6f;
        g.drawImage(Sprites.glow(gs.halo()), x - rr, y + u * 0.9f - rr * 0.85f, rr * 2f, rr * 1.7f, true);
        ThemeSpec.Mist m = th.mist();
        if (m != null && m.warm() != null) {
            Sprites.Mist tex = Sprites.mist(m.color(), m.warm());
            float ww = u * 10f, wh = u * 4.2f, sx = (float) Math.sin(t * 0.09f + 1) * u * 0.1f;
            g.setLighter(false);
            g.setGlobalAlpha(0.85f);
            g.drawImage(tex.warm(), x - ww * 0.5f - sx, yb - u * 0.75f - wh * 0.5f, ww, wh, true);
            g.setLighter(true);
        }
        g.setGlobalAlpha(0.55f * pulse);
        rr = u * 0.16f;
        g.drawImage(Sprites.glow(gs.color()), x - rr, yb - u * 0.2f, rr * 2f, Math.max(u, hz - yb + H * 0.06f), true);
        g.setGlobalAlpha(0.75f * pulse);
        g.drawImage(sp.glow(), x0, y0, w, h, true);
        g.setGlobalAlpha(1f);
        g.setLighter(false);
        g.drawImage(sp.img(), Math.round(x0), Math.round(y0), Math.round(w), Math.round(h), false);
        g.setLighter(true);
        float ph = t * 2.3f, f = ph - (float) Math.floor(ph);
        int j = (int) Math.floor(ph) % 3;
        g.setGlobalAlpha(0.9f * (1f - f));
        g.drawImage(sp.crackle()[j], Math.round(x0), Math.round(y0), Math.round(w), Math.round(h), false);
        g.setGlobalAlpha(0.9f * f);
        g.drawImage(sp.crackle()[(j + 1) % 3], Math.round(x0), Math.round(y0), Math.round(w), Math.round(h), false);
        g.setGlobalAlpha(1f);
        g.setLighter(false);
    }

    private static void mist(PixelCanvas g, ThemeSpec th, float W, float H, float hz, float t) {
        ThemeSpec.Mist m = th.mist();
        Sprites.Mist tex = Sprites.mist(m.color(), m.warm());
        int n = Math.max(1, m.bands());
        for (int b = 0; b < n; b++) {
            float f = b / (float) Math.max(1, n - 1);
            PixelCanvas img = tex.teal()[b % 3];
            float bw = W * (1.3f + 0.25f * (b % 2)), bh = H * (0.18f + 0.08f * ((b * 5) % 3) / 2f);
            float y = H * 0.03f + f * (hz + H * 0.04f - H * 0.03f) - bh * 0.5f;
            float sp = (b % 2 == 1 ? -1 : 1) * (0.006f + b * 0.0016f);
            float off = (((b * 0.37f + t * sp) * bw) % bw + bw) % bw;
            g.setGlobalAlpha(Math.min(1f, m.alpha() * (0.42f + 0.4f * f) * (0.85f + 0.15f * (float) Math.sin(t * 0.25f + b))));
            g.drawImage(img, -off, y, bw, bh, true);
            g.drawImage(img, bw - off, y, bw, bh, true);
        }
        g.setGlobalAlpha(1f);
    }
}
