package io.github.georgwav.cosmetics.sol.ui.menu;

import io.github.georgwav.cosmetics.client.Modules;
import io.github.georgwav.cosmetics.sol.config.SolProfiles;
import io.github.georgwav.cosmetics.sol.render.SolTexture;
import io.github.georgwav.cosmetics.sol.theme.Customisation;
import io.github.georgwav.cosmetics.sol.theme.ThemeParser;
import io.github.georgwav.cosmetics.sol.theme.ThemeRegistry;
import io.github.georgwav.cosmetics.sol.theme.ThemeSpec;
import io.github.georgwav.cosmetics.sol.theme.ThemeState;
import io.github.georgwav.cosmetics.sol.theme.Tokens;
import io.github.georgwav.cosmetics.sol.ui.kit.ColorPicker;
import io.github.georgwav.cosmetics.sol.ui.kit.Gfx;
import io.github.georgwav.cosmetics.sol.ui.kit.Scroll;
import io.github.georgwav.cosmetics.sol.ui.kit.SolFont;
import io.github.georgwav.cosmetics.sol.ui.kit.Ui;
import io.github.georgwav.cosmetics.sol.ui.kit.Widgets;
import io.github.georgwav.cosmetics.sol.util.Colors;
import io.github.georgwav.cosmetics.sol.util.MiniSky;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Customise a theme, over the whole menu like a module's page: any part of it in a colour of your own (the sky, fog,
 * light, sun, moon, clouds, particles, its aurora, nebulae or corona, and its night on its own), how much of each of
 * its effects, which particles it has, and its night sky. On the left the theme by day and by night as it looks with
 * the edits, redrawn as they change; on the right the edits in groups, each with Default back to the theme's own.
 * Saved with the theme in the profile.
 */
public final class ThemePage implements Page {
    private static final float X = ModulePage.X, Y = ModulePage.Y, W = ModulePage.W, H = ModulePage.H;
    private static final float LEFT_W = 360f, GAP = 28f, PAD = 20f;
    private static final int[] PRESETS = {0xFFFFFF, 0xFF4D4D, 0xFF9A3C, 0xFFD23F, 0x7CF27C, 0x3FD8C7, 0x4DA6FF, 0x8B6BFF,
            0xFF6EC7, 0xB0B0B8, 0x3A3A44, 0x000000};

    private final Scroll scroll = new Scroll();
    /** The part whose colour is being picked (its picker open under its row), or null. */
    private String picking;
    private ColorPicker picker;
    private SolTexture day, night;
    /** What the pictures show (the theme and its edits), and what the CPU thread is drawing now (null when idle). */
    private Customisation drawnWith;
    private String drawnFor;
    private Customisation drawingWith;
    /** The two pictures the CPU thread finished, waiting to be uploaded on the render thread. */
    private final java.util.concurrent.atomic.AtomicReference<Drawn> finished = new java.util.concurrent.atomic.AtomicReference<>();

    private record Drawn(String themeId, Customisation with, int w, int h, int[] day, int[] night) {
    }
    /** Whether the moon looks are open under their row. */
    private boolean choosingLook;
    /** Scroll to the night once the page knows where it is (opened from the Night sky page). */
    private boolean toNight;
    private float nightY = -1f;

    ThemePage(boolean night) {
        this.toNight = night;
    }

    private static Customisation c() {
        return ThemeState.customisation();
    }

    private static void set(Customisation c) {
        WorldSection.customise(c);
    }

    @Override
    public void draw(SolMenu menu, Gfx g, Ui ui, Tokens tk) {
        g.glow(X, Y, W, H, 22f, 40f, Colors.withAlpha(tk.glow, 0.18f));
        g.round(X, Y, W, H, 22f, 0xFF0B0C11);
        g.stroke(X, Y, W, H, 22f, Math.max(1f, g.d.u), tk.rim);
        g.highlight(X, Y, W, H, 22f, 0x40FFFFFF);
        ui.area(X, Y, W, H, () -> { });
        ThemeRegistry.Entry e = ThemeState.baseEntry();
        header(menu, g, ui, tk, e);
        float top = Y + 92f, bottom = Y + H - PAD;
        left(menu, g, ui, tk, e, X + PAD, top, bottom);
        right(menu, g, ui, tk, e, X + PAD + LEFT_W + GAP, top, X + W - PAD, bottom);
    }

    // ------------------------------------------------------------------ header

    private void header(SolMenu menu, Gfx g, Ui ui, Tokens tk, ThemeRegistry.Entry e) {
        float iy = Y + 16f;
        g.round(X + PAD, iy, 46f, 46f, 13f, 0x1AFFFFFF);
        g.glowSprite(X + PAD + 23f, iy + 23f, 30f, 30f, Colors.withAlpha(tk.glow, 0.6f));
        g.rect(X + PAD + 15f, iy + 15f, 16f, 16f, tk.accent);
        float right = X + W - PAD;
        ui.tool(tk, right - 26f, Y + 26f, "close", false, menu::closeDrawer);
        float bx = right - 26f - 22f;
        float aw = ui.button(tk, bx, Y + 39f, SolMenu.tr("sol.menu.theme.atmosphere"), false, () -> {
            if (Modules.ATMOSPHERE != null) {
                menu.openDrawer(Modules.ATMOSPHERE);
            }
        });
        float rw = ui.button(tk, bx - aw - 10f, Y + 39f, SolMenu.tr("sol.menu.theme.reset"), false, () -> {
            close();
            set(Customisation.NONE);
            menu.toast(SolMenu.tr("sol.notify.theme_reset", Widgets.themeName(e)));
        });
        float tx = X + PAD + 60f, room = bx - aw - 10f - rw - 16f - tx;
        SolFont name = g.font(SolFont.Family.SEMIBOLD, 22f), desc = g.font(SolFont.Family.MEDIUM, 12.5f);
        g.textMid(name, g.fit(name, SolMenu.tr("sol.menu.theme.title", Widgets.themeName(e)), room), tx, Y + 30f, 0xFFFFFFFF);
        g.textMid(desc, g.fit(desc, SolMenu.tr("sol.menu.theme.line"), room), tx, Y + 52f, 0x94FFFFFF);
        g.rect(X + 1f, Y + 76f, W - 2f, Math.max(1f / g.d.u, 1f), 0x14FFFFFF);
    }

    // ------------------------------------------------------------------ left: the theme by day and by night

    private void left(SolMenu menu, Gfx g, Ui ui, Tokens tk, ThemeRegistry.Entry e, float x, float top, float bottom) {
        float ph = 196f;
        redraw(e, Math.round(LEFT_W * 2), Math.round(ph * 2));
        preview(g, day, x, top, ph, SolMenu.tr("sol.menu.theme.by_day"));
        preview(g, night, x, top + ph + 12f, ph, SolMenu.tr("sol.menu.theme.by_night"));
        SolFont dim = g.font(SolFont.Family.MEDIUM, 11.5f);
        List<String> lines = HudSection.wrap(g, dim, SolMenu.tr("sol.menu.theme.saved", Widgets.themeName(e), SolProfiles.active()), LEFT_W);
        float iy = bottom - 8f - (lines.size() - 1) * 17f;
        for (String line : lines) {
            g.textMid(dim, line, x, iy, 0x8CFFFFFF);
            iy += 17f;
        }
    }

    private static void preview(Gfx g, SolTexture t, float x, float y, float h, String caption) {
        g.round(x, y, LEFT_W, h, 14f, 0x0AFFFFFF);
        if (t != null && !t.released()) {
            g.clip(x, y, x + LEFT_W, y + h);
            g.image(t, x, y, LEFT_W, h, 0f, 0f, 1f, 1f, 0xFFFFFFFF, false);
            g.endClip();
        }
        g.stroke(x, y, LEFT_W, h, 14f, Math.max(1f, g.d.u), 0x14FFFFFF);
        SolFont cap = g.font(SolFont.Family.MONO, 10.5f);
        g.text(cap, caption, x + 12f, y + h - 10f, 0xB3FFFFFF);
    }

    /**
     * Keeps the two pictures up to date with the theme and its edits: they're drawn on Sol's CPU thread (one at a
     * time, the latest edits each time it's free) and uploaded here when ready, so dragging a slider never waits.
     */
    private void redraw(ThemeRegistry.Entry base, int pw, int ph) {
        Drawn done = finished.getAndSet(null);
        if (done != null) {
            drawingWith = null;
            if (done.day() != null) {
                release();
                day = SolTexture.of("theme_day", done.w(), done.h(), true, done.day());
                night = SolTexture.of("theme_night", done.w(), done.h(), true, done.night());
            }
            // A picture that couldn't be drawn counts as drawn for these edits: the next edit tries again.
            drawnFor = done.themeId();
            drawnWith = done.with();
        }
        Customisation c = c();
        boolean stale = !base.id().equals(drawnFor) || !c.equals(drawnWith);
        if (!stale || drawingWith != null) {
            return;
        }
        drawingWith = c;
        ThemeRegistry.Entry e = ThemeState.entry();
        String id = base.id();
        io.github.georgwav.cosmetics.sol.util.SolExecutors.CPU.execute(() -> {
            try {
                ThemeSpec showpiece = e.base();
                // A night theme shows its night as its showpiece: its day is the time of day the game shows at noon.
                ThemeSpec byDay = "night".equals(showpiece.show().phase()) ? e.phase(ThemeRegistry.Phase.NOON) : showpiece;
                int[] d = MiniSky.render(byDay, e.id(), pw, ph, 2f).toArgb(null);
                int[] n = MiniSky.render(nightView(e.phase(ThemeRegistry.Phase.NIGHT)), e.id(), pw, ph, 2f).toArgb(null);
                finished.set(new Drawn(id, c, pw, ph, d, n));
            } catch (RuntimeException | LinkageError ex) {
                io.github.georgwav.cosmetics.CosmeticsMod.LOGGER.warn("[Sol] Couldn't draw the theme page's pictures", ex);
                finished.set(new Drawn(id, c, pw, ph, null, null));
            }
        });
    }

    /** The night time of day as a picture shows a night: the moon up (where the theme doesn't place it) and stars out. */
    private static ThemeSpec nightView(ThemeSpec s) {
        ThemeSpec.Moon m = s.moon(), unplaced = ThemeParser.DEFAULTS.moon();
        if (m == null) {
            m = unplaced;
        }
        if (m.x() == unplaced.x() && m.y() == unplaced.y()) {
            m = new ThemeSpec.Moon(m.size(), m.color(), m.halo(), m.haloRadius(), m.haloAlpha(), m.ring(), m.dogs(), 0.72f, 0.2f);
        }
        ThemeSpec.Stars st = s.stars();
        if (st.count() < 120) {
            st = new ThemeSpec.Stars(120, st.density(), st.palette(), st.tint(), st.milkyWay(), st.deepSky(), st.shooting(),
                    st.meteorNights(), st.comets(), st.through());
        }
        return new ThemeSpec(s.id(), s.name(), s.line(), s.order(), s.ui(), s.sky(), s.fog(), s.light(), s.sun(), m, st, s.planets(),
                s.galaxy(), s.aurora(), s.nebula(), s.eclipse(), s.lightning(), s.sigil(), s.mist(), s.clouds(), s.wind(), s.particles(),
                new ThemeSpec.Show("night", s.show().azimuth()), s.noRidges(), s.vanillaSun(), s.grade(), s.night());
    }

    // ------------------------------------------------------------------ right: the edits

    private void right(SolMenu menu, Gfx g, Ui ui, Tokens tk, ThemeRegistry.Entry e, float x, float top, float x1, float bottom) {
        float w = x1 - x;
        g.clip(x - 6f, top - 4f, x1 + 6f, bottom);
        float y = top - scroll.value();
        Customisation c = c();
        ThemeSpec b = e.base();

        y = header(g, x, w, y, "colours");
        y = slider(g, ui, tk, x, w, y, "hue", c.hue() != 0f, (c.hue() + 180f) / 360f, Math.round(c.hue()) + "°",
                k -> set(c().withHue(Math.round(k * 72f) * 5f - 180f)), () -> set(c().withHue(0f)));
        y = slider(g, ui, tk, x, w, y, "saturation", c.sat() != 1f, c.sat() / 2f, Math.round(c.sat() * 100f) + "%",
                k -> set(c().withSat(Math.round(k * 40f) / 20f)), () -> set(c().withSat(1f)));
        y = colour(menu, g, ui, tk, e, x, w, y, "accent");
        y = colour(menu, g, ui, tk, e, x, w, y, "panel");
        y += 10f;

        y = header(g, x, w, y, "sky");
        for (String p : List.of("sky_top", "sky_middle", "sky_horizon", "sky_glow", "fog", "clouds")) {
            y = colour(menu, g, ui, tk, e, x, w, y, p);
        }
        float fog = c.fog() == null ? b.fog().density() : c.fog();
        y = slider(g, ui, tk, x, w, y, "fog_amount", c.fog() != null, fog, Math.round(fog * 100f) + "%",
                k -> set(c().withFog(Math.round(k * 20f) / 20f)), () -> set(c().withFog(null)));
        y += 10f;

        y = header(g, x, w, y, "light");
        y = colour(menu, g, ui, tk, e, x, w, y, "light");
        y = colour(menu, g, ui, tk, e, x, w, y, "sunlight");
        for (String a : List.of("light", "bloom", "contrast", "colourful", "vignette")) {
            y = amount(g, ui, tk, x, w, y, a);
        }
        y += 10f;

        if (b.sun() != null) {
            y = header(g, x, w, y, "sun");
            y = slider(g, ui, tk, x, w, y, "sun_size", c.sunSize() != 1f, (c.sunSize() - 0.5f) / 2f, Math.round(c.sunSize() * 100f) + "%",
                    k -> set(c().withSunSize(0.5f + Math.round(k * 40f) / 20f)), () -> set(c().withSunSize(1f)));
            int rays = c.rays() != null ? c.rays() : b.sun().rays();
            y = slider(g, ui, tk, x, w, y, "rays", c.rays() != null, rays / 32f, Integer.toString(rays),
                    k -> set(c().withRays(Math.round(k * 32f))), () -> set(c().withRays(null)));
            y = amount(g, ui, tk, x, w, y, "ray_length");
            y = amount(g, ui, tk, x, w, y, "sun_glow");
            y = colour(menu, g, ui, tk, e, x, w, y, "sun_core");
            y = colour(menu, g, ui, tk, e, x, w, y, "sun_halo");
            y += 10f;
        }

        y = header(g, x, w, y, "particles");
        y = slider(g, ui, tk, x, w, y, "density", c.density() != 1f, c.density() / 3f, Math.round(c.density() * 100f) + "%",
                k -> set(c().withDensity(Math.round(k * 60f) / 20f)), () -> set(c().withDensity(1f)));
        y = amount(g, ui, tk, x, w, y, "particle_size");
        y = amount(g, ui, tk, x, w, y, "particle_speed");
        y = amount(g, ui, tk, x, w, y, "wind");
        if (!b.particles().isEmpty()) {
            y = colour(menu, g, ui, tk, e, x, w, y, "particles");
            Set<String> types = new LinkedHashSet<>();
            for (ThemeSpec.Emitter em : b.particles()) {
                types.add(em.type());
            }
            for (String type : types) {
                boolean on = c.particleOn(type);
                y = toggle(g, ui, tk, x, w, y, SolMenu.tr("sol.menu.theme.particle", particleName(type)), on,
                        () -> set(c().withParticle(type, !on)));
            }
        }
        y += 10f;

        if (b.aurora() != null || !b.nebula().isEmpty() || b.eclipse() != null) {
            y = header(g, x, w, y, "extras");
            if (b.aurora() != null) {
                y = colour(menu, g, ui, tk, e, x, w, y, "aurora_1");
                if (b.aurora().length > 1) {
                    y = colour(menu, g, ui, tk, e, x, w, y, "aurora_2");
                }
            }
            if (!b.nebula().isEmpty()) {
                y = colour(menu, g, ui, tk, e, x, w, y, "nebula");
            }
            if (b.eclipse() != null) {
                y = colour(menu, g, ui, tk, e, x, w, y, "corona");
            }
            y += 10f;
        }

        nightY = y + scroll.value() - top;
        y = header(g, x, w, y, "night");
        for (String p : List.of("night_top", "night_middle", "night_horizon")) {
            y = colour(menu, g, ui, tk, e, x, w, y, p);
        }
        y = moonLook(g, ui, tk, b, x, w, y);
        for (String p : List.of("moon", "moonlight", "airglow")) {
            y = colour(menu, g, ui, tk, e, x, w, y, p);
        }
        y = amount(g, ui, tk, x, w, y, "moon_size");
        y = amount(g, ui, tk, x, w, y, "moon_glow");
        y = slider(g, ui, tk, x, w, y, "stars", c.stars() != 1f, c.stars() / 3f, Math.round(c.stars() * 100f) + "%",
                k -> set(c().withStars(Math.round(k * 60f) / 20f)), () -> set(c().withStars(1f)));
        y = colour(menu, g, ui, tk, e, x, w, y, "stars");
        float mw = c.milkyWay() != null ? c.milkyWay() : b.stars().milkyWay();
        y = slider(g, ui, tk, x, w, y, "milky_way", c.milkyWay() != null, mw, Math.round(mw * 100f) + "%",
                k -> set(c().withMilkyWay(Math.round(k * 20f) / 20f)), () -> set(c().withMilkyWay(null)));
        boolean planets = c.planets() != null ? c.planets() : b.planets().enabled();
        y = toggle(g, ui, tk, x, w, y, SolMenu.tr("sol.menu.theme.row.planets"), planets, () -> set(c().withPlanets(!planets)));
        y = colour(menu, g, ui, tk, e, x, w, y, "planets");
        for (String layer : Customisation.LAYERS) {
            boolean on = c.layerOn(layer);
            y = toggle(g, ui, tk, x, w, y, SolMenu.tr("sol.menu.night.layer." + layer), on, () -> set(c().withLayer(layer, !on)));
        }

        g.endClip();
        scroll.setMax(y + scroll.value() - bottom + 12f);
        if (toNight && nightY >= 0f) {
            toNight = false;
            scroll.to(nightY);
        }
        scroll.step(menu.dt());
        scroll.bar(g, x1 + 8f, top, bottom - top);
    }

    private static String particleName(String type) {
        String key = "sol.particle." + type;
        String name = SolMenu.tr(key);
        return name.equals(key) ? type.substring(0, 1).toUpperCase(Locale.ROOT) + type.substring(1).replace('_', ' ') : name;
    }

    private static float header(Gfx g, float x, float w, float y, String group) {
        SolFont f = g.font(SolFont.Family.SEMIBOLD, 12f);
        g.textMid(f, SolMenu.tr("sol.menu.theme.group." + group).toUpperCase(Locale.ROOT), x, y + 14f, 0x99FFFFFF);
        g.rect(x, y + 27f, w, Math.max(1f / g.d.u, 1f), 0x14FFFFFF);
        return y + 34f;
    }

    /** A row's name and hint on the left, and its way back to the theme's own when it's changed; returns its height. */
    private static float label(Gfx g, Ui ui, float x, float w, float y, String name, String hint, boolean changed, Runnable reset) {
        float rh = hint.isEmpty() ? 42f : 54f, cy = y + rh / 2f, labelW = w * 0.5f;
        SolFont f = g.font(SolFont.Family.MEDIUM, 13.5f), hf = g.font(SolFont.Family.MEDIUM, 11.5f);
        if (hint.isEmpty()) {
            g.textMid(f, g.fit(f, name, labelW), x, cy, 0xFFFFFFFF);
        } else {
            g.textMid(f, g.fit(f, name, labelW), x, y + 18f, 0xFFFFFFFF);
            g.textMid(hf, g.fit(hf, hint, labelW), x, y + 37f, 0x80FFFFFF);
        }
        if (changed && reset != null) {
            SolFont df = g.font(SolFont.Family.MEDIUM, 11f);
            String d = SolMenu.tr("sol.menu.page.default");
            float dw = g.width(df, d) + 16f, dx = x + labelW + 10f;
            ui.click(dx, cy - 10f, dw, 20f, reset);
            g.round(dx, cy - 10f, dw, 20f, 10f, ui.over(dx, cy - 10f, dw, 20f) ? 0x29FFFFFF : 0x12FFFFFF);
            g.textMid(df, d, dx + 8f, cy, 0xB3FFFFFF);
        }
        return rh;
    }

    private static String hint(String key) {
        String h = SolMenu.tr(key + ".hint");
        return h.equals(key + ".hint") ? "" : h;
    }

    private static float line(Gfx g, float x, float w, float y) {
        g.rect(x, y - 1f, w, Math.max(1f / g.d.u, 1f), 0x0AFFFFFF);
        return y;
    }

    private float slider(Gfx g, Ui ui, Tokens tk, float x, float w, float y, String row, boolean changed, float value, String text,
                         Consumer<Float> set, Runnable reset) {
        String key = "sol.menu.theme.row." + row;
        float rh = label(g, ui, x, w, y, SolMenu.tr(key), hint(key), changed, reset);
        ui.slider(tk, x + w, y + rh / 2f, Math.max(0f, Math.min(1f, value)), text, set);
        return line(g, x, w, y + rh);
    }

    /** One of {@link Customisation#AMOUNTS}: 100% is the theme's own. */
    private float amount(Gfx g, Ui ui, Tokens tk, float x, float w, float y, String part) {
        float[] range = Customisation.AMOUNTS.get(part);
        float v = c().amount(part);
        return slider(g, ui, tk, x, w, y, "amount." + part, v != 1f, (v - range[0]) / (range[1] - range[0]), Math.round(v * 100f) + "%",
                k -> set(c().withAmount(part, Math.round((range[0] + k * (range[1] - range[0])) * 20f) / 20f)),
                () -> set(c().withAmount(part, 1f)));
    }

    /** The night's moon look: the theme's, or another; its options open under the row. */
    private float moonLook(Gfx g, Ui ui, Tokens tk, ThemeSpec b, float x, float w, float y) {
        String own = b.night() != null ? b.night().moon() : ThemeSpec.Night.DEFAULT.moon();
        String picked = c().moonLook();
        String key = "sol.menu.theme.row.moon_look";
        float rh = label(g, ui, x, w, y, SolMenu.tr(key), hint(key), picked != null, () -> set(c().withMoonLook(null)));
        var looks = io.github.georgwav.cosmetics.sol.sky.MoonLooks.all();
        String now = picked != null ? picked : own;
        var cur = looks.get(now);
        String name = cur != null ? cur.name() : now;
        ui.button(tk, x + w, y + rh / 2f, name + "  …", choosingLook, () -> choosingLook = !choosingLook);
        float extra = 0f;
        if (choosingLook) {
            SolFont of = g.font(SolFont.Family.MEDIUM, 12f);
            float ox = x, oy = y + rh + 4f;
            for (var entry : looks.entrySet()) {
                String id = entry.getKey();
                String label = entry.getValue().name() + (id.equals(own) ? " · " + SolMenu.tr("sol.menu.theme.own") : "");
                float cw = g.width(of, label) + 22f;
                if (ox + cw > x + w) {
                    ox = x;
                    oy += 30f;
                }
                ui.chip(tk, ox, oy + 12f, label, id.equals(now), () -> {
                    set(c().withMoonLook(id.equals(own) ? null : id));
                    choosingLook = false;
                });
                ox += cw + 6f;
            }
            extra = oy + 30f - (y + rh);
        }
        return line(g, x, w, y + rh + extra);
    }

    private float toggle(Gfx g, Ui ui, Tokens tk, float x, float w, float y, String name, boolean on, Runnable flip) {
        float rh = label(g, ui, x, w, y, name, "", false, null);
        ui.smallSwitch(tk, x + w - 30f, y + rh / 2f - 9f, on, flip);
        return line(g, x, w, y + rh);
    }

    /** A part's colour: its swatch (its own, or the theme's), the picker under it while open, Default back to the theme's. */
    private float colour(SolMenu menu, Gfx g, Ui ui, Tokens tk, ThemeRegistry.Entry e, float x, float w, float y, String part) {
        Customisation c = c();
        Integer own = c.colour(part);
        int shown = own != null ? own : Colors.shiftHue(themeColour(part, e), c.hue(), c.sat());
        String key = "sol.menu.theme.colour." + part;
        float rh = label(g, ui, x, w, y, SolMenu.tr(key), hint(key), own != null, () -> {
            if (part.equals(picking)) {
                close();
            }
            set(c().withColour(part, null));
        });
        float right = x + w, cy = y + rh / 2f, sw = 46f;
        boolean open = part.equals(picking);
        ui.click(right - sw, cy - 12f, sw, 24f, () -> {
            if (open) {
                close();
            } else {
                picking = part;
                picker = new ColorPicker(() -> current(part) & 0xFFFFFF, rgb -> set(c().withColour(part, 0xFF000000 | rgb)));
            }
        });
        g.round(right - sw, cy - 12f, sw, 24f, 7f, shown | 0xFF000000);
        g.stroke(right - sw, cy - 12f, sw, 24f, 7f, Math.max(1f, g.d.u), open ? tk.accent : 0x40FFFFFF);
        SolFont m = g.font(SolFont.Family.MONO, 11.5f);
        g.textRight(m, own != null ? String.format(Locale.ROOT, "#%06X", own & 0xFFFFFF) : SolMenu.tr("sol.menu.theme.own"),
                right - sw - 10f, cy, own != null ? 0xB3FFFFFF : 0x73FFFFFF);
        float extra = 0f;
        if (open && picker != null) {
            List<Integer> presets = new ArrayList<>();
            presets.add(themeColour(part, e) & 0xFFFFFF);
            for (int p : PRESETS) {
                presets.add(p);
            }
            picker.draw(g, ui, tk, x, y + rh, w, presets, SolMenu.tr("sol.menu.theme.own"), () -> {
                close();
                set(c().withColour(part, null));
            }, menu.dt());
            extra = ColorPicker.HEIGHT + 10f;
        }
        return line(g, x, w, y + rh + extra);
    }

    private int current(String part) {
        Integer own = c().colour(part);
        return own != null ? own : themeColour(part, ThemeState.baseEntry());
    }

    private void close() {
        picking = null;
        picker = null;
    }

    /** The theme's own colour for a part (its showpiece's, the night's for the night's parts). */
    static int themeColour(String part, ThemeRegistry.Entry e) {
        ThemeSpec b = e.base(), n = e.phase(ThemeRegistry.Phase.NIGHT);
        ThemeSpec.Night night = b.night() != null ? b.night() : ThemeSpec.Night.DEFAULT;
        ThemeSpec.Moon moon = n.moon() != null ? n.moon() : ThemeParser.DEFAULTS.moon();
        return 0xFF000000 | switch (part) {
            case "accent" -> b.ui().accent();
            case "panel" -> b.ui().panel();
            case "sky_top" -> b.sky().top();
            case "sky_middle" -> b.sky().middle();
            case "sky_horizon" -> b.sky().horizon();
            case "sky_glow" -> b.sky().glow();
            case "fog" -> b.fog().color();
            case "clouds" -> b.clouds().tint() != null ? b.clouds().tint() : 0xFFFFFF;
            case "light" -> b.light().tint();
            case "sunlight" -> b.light().sun();
            case "sun_core" -> b.sun() != null ? b.sun().core() : 0xFFF3C4;
            case "sun_halo" -> b.sun() != null ? b.sun().halo() : 0xFFB347;
            case "particles" -> !b.particles().isEmpty() && b.particles().get(0).colors().length > 0 ? b.particles().get(0).colors()[0]
                    : 0xFFFFFF;
            case "aurora_1" -> b.aurora() != null && b.aurora().length > 0 ? b.aurora()[0] : 0x4DFFB0;
            case "aurora_2" -> b.aurora() != null && b.aurora().length > 1 ? b.aurora()[1] : 0x7A6BFF;
            case "nebula" -> b.nebula().isEmpty() ? 0x9650D2 : b.nebula().get(0).color();
            case "corona" -> b.eclipse() != null ? b.eclipse().corona() : 0xFFD0A0;
            case "night_top" -> n.sky().top();
            case "night_middle" -> n.sky().middle();
            case "night_horizon" -> n.sky().horizon();
            case "moon" -> moon.color();
            case "moon_halo" -> moon.halo();
            case "moonlight" -> night.light();
            case "airglow" -> night.airglow();
            case "stars" -> b.stars().tint() != null ? b.stars().tint() : 0xFFFFFF;
            case "planets" -> b.planets().tint() != null ? b.planets().tint() : 0xE8D8B0;
            default -> 0xFFFFFF;
        };
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean scroll(float amount) {
        return scroll.wheel(amount);
    }

    @Override
    public boolean typed(String text) {
        return picker != null && picker.typed(text);
    }

    @Override
    public boolean key(int key, boolean ctrl) {
        return picker != null && picker.key(key, ctrl);
    }

    @Override
    public void release() {
        if (day != null) {
            day.release();
        }
        if (night != null) {
            night.release();
        }
    }
}
