package io.github.georgwav.cosmetics.sol.ui.quick;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.georgwav.cosmetics.client.Module;
import io.github.georgwav.cosmetics.client.Modules;
import io.github.georgwav.cosmetics.emote.Emote;
import io.github.georgwav.cosmetics.emote.EmotePlayer;
import io.github.georgwav.cosmetics.emote.Emotes;
import io.github.georgwav.cosmetics.sol.Feature;
import io.github.georgwav.cosmetics.sol.config.SolConfig;
import io.github.georgwav.cosmetics.sol.config.SolProfiles;
import io.github.georgwav.cosmetics.sol.render.SolTexture;
import io.github.georgwav.cosmetics.sol.theme.ThemeRegistry;
import io.github.georgwav.cosmetics.sol.theme.ThemeState;
import io.github.georgwav.cosmetics.sol.theme.Tokens;
import io.github.georgwav.cosmetics.sol.ui.kit.Gfx;
import io.github.georgwav.cosmetics.sol.ui.kit.Icons;
import io.github.georgwav.cosmetics.sol.ui.kit.ModuleIcons;
import io.github.georgwav.cosmetics.sol.ui.kit.Notifications;
import io.github.georgwav.cosmetics.sol.ui.kit.PlayerFace;
import io.github.georgwav.cosmetics.sol.ui.kit.SolFont;
import io.github.georgwav.cosmetics.sol.ui.kit.SolScreen;
import io.github.georgwav.cosmetics.sol.ui.kit.Widgets;
import io.github.georgwav.cosmetics.sol.ui.menu.SolMenu;
import io.github.georgwav.cosmetics.sol.util.Colors;
import io.github.georgwav.cosmetics.sol.util.Mulberry32;
import io.github.georgwav.cosmetics.sol.util.PixelCanvas;
import io.github.georgwav.cosmetics.sol.util.Spring;
import io.github.georgwav.cosmetics.sol.util.SunCpu;
import io.github.georgwav.cosmetics.ui.KeyBinds;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The quick menu (Right Shift): you in the middle, the sections round you on a sundial, your pinned modules
 * underneath. The sun follows the pointer round the dial and lights the whole screen (noon at the top, dawn and dusk
 * at the sides, night at the bottom). Point at yourself and the sun slides in behind your head for a total eclipse
 * with a pixel corona; click to open presets, emotes and wardrobe inside it, click again to let the sun out. A
 * section expands into the full menu on that screen. A port of the Sol menu prototype's quick menu: its geometry,
 * its light keys, its springs.
 */
public final class QuickMenu extends SolScreen {
    /** Frames drawn (the in-game check). */
    public static int framesRendered;

    static final float CX = 640f, CY = 318f, R1 = 118f, R2 = 182f, RT = 212f, RD = 56f;

    /** One place on a ring: its id, its icon and its angle on the dial (degrees, 0 = right, clockwise). */
    record Place(String id, String icon, float angle) {
    }

    static final Place[] MAIN = {new Place("hud", "hud", -150f), new Place("modules", "grid4", -90f),
            new Place("world", "sun", -30f), new Place("music", "note", 30f), new Place("settings", "gear", 90f),
            new Place("mods", "puzzle", 150f)};
    static final Place[] ME = {new Place("presets", "sun", -90f), new Place("emotes", "run", 30f),
            new Place("wardrobe", "shirt", 150f)};

    enum Tray { PINS, PRESETS, EMOTES }

    private boolean meRing;
    private Tray tray = Tray.PINS;
    private String hot;
    private String mainHot;
    private boolean meHover, meFocus, meFresh;
    private final Spring ang = new Spring(Spring.QUICK_SUN, -90f);
    private final Spring ecl = new Spring(Spring.ECLIPSE, 0f);
    private final Spring ray = new Spring(Spring.RAY, -90f);
    private float rayA, flare;
    private float ringT, eclOnAt = -10f, openT;
    private float expand = -1f;
    private String expandTo;
    private final QuickLight light = new QuickLight();
    private final Corona corona = new Corona();
    private SolTexture coronaTex, sunTex;
    private PixelCanvas sunCanvas;
    private final int[] sunPixels = new int[32 * 32];
    private float sunDrawnAt = -1f;
    private final float[] starX = new float[84], starY = new float[84];
    private final int[] starC = new int[84];
    private final boolean[] starBig = new boolean[84];
    /** Keyboard focus: -1 none, 0 you, 1.. the ring's places, then the tray's items, then the search pill. */
    private int focus = -1;
    private final List<Hit> hits = new ArrayList<>();
    private float presetScroll = Float.NaN;
    private final PresetThumbs thumbs = new PresetThumbs();

    /** A clickable thing drawn this frame (tray items, the search pill), for the pointer and the keyboard. */
    private record Hit(float x, float y, float w, float h, Runnable action) {
        boolean contains(float px, float py) {
            return px >= x && px <= x + w && py >= y && py <= y + h;
        }
    }

    public QuickMenu(Screen parent) {
        this(parent, false, null);
    }

    public QuickMenu(Screen parent, boolean meRing, String hot) {
        super(Component.translatable("sol.quick.title"), parent, Feature.QUICK_MENU);
        Mulberry32 r = new Mulberry32(31);
        int[] cols = {0xFFFFFFFF, 0xFFDFE8FF, 0xFFFFF1D0, 0xFFC9DCFF};
        for (int i = 0; i < 84; i++) {
            starX[i] = Math.round(r.next() * 1276 / 2) * 2;
            starY[i] = Math.round((64 + r.next() * 470) / 2) * 2;
            starBig[i] = r.next() < 0.16;
            starC[i] = cols[(int) (r.next() * 4)];
        }
        this.meRing = meRing;
        this.tray = meRing ? Tray.PRESETS : Tray.PINS;
        this.mainHot = hot != null && find(MAIN, hot) != null ? hot : "modules";
        setRing(hot);
        ang.snap(target(mainHot));
    }

    /**
     * For the dev scenario: one of the states the prototype's reference shots show. A main section's id points at
     * it; "eclipse" points at you; "presets" opens your ring; "emotes" its emotes; "back" points at you from there.
     */
    public QuickMenu stage(String state) {
        switch (state) {
            case "eclipse" -> {
                meHover = true;
                meFresh = false;
            }
            case "presets" -> {
                if (!meRing) {
                    toggleMe();
                }
                meHover = false;
            }
            case "emotes", "back" -> {
                if (!meRing) {
                    toggleMe();
                }
                setHot("emotes");
                tray = Tray.EMOTES;
                meHover = "back".equals(state);
                meFresh = false;
            }
            default -> setHot(state);
        }
        return this;
    }

    // ------------------------------------------------------------------ state

    private Place[] places() {
        return meRing ? ME : MAIN;
    }

    private static Place find(Place[] list, String id) {
        for (Place p : list) {
            if (p.id().equals(id)) {
                return p;
            }
        }
        return null;
    }

    private float target(String id) {
        Place p = find(MAIN, id);
        return p == null ? -90f : p.angle();
    }

    private void setRing(String wanted) {
        ringT = 0f;
        Place[] list = places();
        String h = wanted != null && find(list, wanted) != null ? wanted : meRing ? list[0].id() : mainHot;
        setHot(h);
        if (meRing) {
            ray.snap(ray.target());
        }
    }

    private void setHot(String id) {
        Place p = find(places(), id);
        if (p == null) {
            return;
        }
        hot = id;
        if (meRing) {
            ray.setTarget(p.angle());
        } else {
            mainHot = id;
            ang.setTarget(p.angle());
        }
    }

    /** You count as pointed at while hovered (not straight after a click on you) or focused from the keyboard. */
    private boolean meOn() {
        return (meHover && !meFresh) || meFocus;
    }

    /** 0: sun on the dial; 1: total eclipse behind you; .7 lets it peek out while Back is pointed at. */
    private float eclTarget() {
        return meRing ? (meOn() ? 0.7f : 1f) : (meOn() ? 1f : 0f);
    }

    private void choose(String id) {
        if (!meRing) {
            expand(id);
            return;
        }
        flare = 1f;
        switch (id) {
            case "wardrobe" -> expand("wardrobe");
            case "emotes" -> tray = Tray.EMOTES;
            default -> tray = Tray.PRESETS;
        }
    }

    private void expand(String section) {
        if (expand >= 0f) {
            return;
        }
        if (calm()) {
            SolMenu.open(parent, section, true);
            return;
        }
        expand = 0f;
        expandTo = section;
    }

    private void toggleMe() {
        meFresh = true;
        meFocus = false;
        if (meRing) {
            meRing = false;
            tray = Tray.PINS;
            setRing(mainHot);
        } else {
            meRing = true;
            tray = Tray.PRESETS;
            presetScroll = Float.NaN;
            setRing(null);
        }
    }

    // ------------------------------------------------------------------ frame

    @Override
    protected void frame(Gfx g) {
        framesRendered++;
        Tokens tk = ThemeState.tokens();
        openT += dt;
        ringT += dt;
        boolean eclWas = ecl.target() > 0.5f;
        ecl.setTarget(eclTarget());
        if (!eclWas && ecl.target() > 0.5f) {
            eclOnAt = time;
        }
        float rayT = meRing && !meOn() ? 1f : 0f;
        if (calm() || dt == 0f) {
            ang.snap(ang.target());
            ecl.snap(ecl.target());
            ray.snap(ray.target());
            rayA = rayT;
            flare = 0f;
        } else {
            ang.stepAngle(dt);
            ecl.step(dt);
            ray.stepAngle(dt);
            rayA += (rayT - rayA) * Math.min(1f, dt * 9f);
            flare *= (float) Math.exp(-4f * dt);
        }
        if (expand >= 0f) {
            expand += dt / 0.2f;
            if (expand >= 1f) {
                expand = -1f;
                SolMenu.open(parent, expandTo, true);
                return;
            }
        }
        float ec = Math.max(0f, Math.min(1f, ecl.value()));
        float rr = RT * Math.min(1.1f, 1f - Math.min(1f, ecl.value()));
        double a = Math.toRadians(ang.value());
        float sx = CX + rr * (float) Math.cos(a), sy = CY + rr * (float) Math.sin(a);
        light.update(ang.value(), sx, sy, ec, tk.glow);
        hits.clear();

        float fade = calm() ? 1f : Math.min(1f, openT / 0.2f);
        g.alpha = fade;
        veil(g, tk);
        sky(g, ec);
        float ringScale = expand >= 0f ? 1f + 0.7f * expand : 1f;
        float ringAlpha = expand >= 0f ? 1f - expand : 1f;
        g.alpha = fade * ringAlpha;
        dial(g, tk, ec, ringScale);
        sun(g, sx, sy, ec, (float) Math.sin(a), tk);
        coronaLayer(g, ec);
        places(g, tk, ec, ringScale);
        me(g, tk, ec);
        label(g, tk);
        g.alpha = fade * (expand >= 0f ? Math.max(0f, 1f - expand * 3f) : 1f);
        trayLayer(g, tk);
        search(g, tk);
        if (tray == Tray.PINS) {
            SolFont mono = g.font(SolFont.Family.MONO, 11f);
            g.textCentre(mono, tr("sol.quick.hint", keyName(KeyBinds.quickMenu())), CX, 698f, 0x8CFFFFFF);
        }
        g.alpha = 1f;
    }

    /** The veil over the world: blurred by the game, darkened round the middle so the dial reads. */
    private void veil(Gfx g, Tokens tk) {
        float vb = QuickLight.veil(tk.skyMiddle);
        float far = (float) Math.hypot(Math.max(CX - g.d.left(), g.d.right() - CX), Math.max(316.8f - g.d.top(), g.d.bottom() - 316.8f));
        g.radialB.set(0, 0, 1).stop(0f, rgba(4, 5, 10, vb)).stop(0.4f, rgba(4, 5, 10, vb + 0.06f)).stop(1f, rgba(4, 5, 10, vb + 0.3f));
        g.radial(CX, 316.8f, far, far, g.radialB, 64);
        g.radialB.set(0, 0, 1).stop(0f, rgba(4, 5, 10, vb + 0.14f)).stop(1f, 0);
        g.radial(CX, 330f, 190f, 190f, g.radialB, 48);
    }

    /** The sky the sun lights: the dial's light keys as a wash, the light round the sun, the stars, the horizon glow. */
    private void sky(Gfx g, float ec) {
        float l = g.d.left(), r = g.d.right(), top = g.d.top(), bottom = g.d.bottom();
        g.vgradient(l, top, r - l, 316.8f - top, light.skyTop, light.skyMid);
        g.vgradient(l, 316.8f, r - l, 518.4f - 316.8f, light.skyMid, light.skyBottom);
        g.rect(l, 518.4f, r - l, bottom - 518.4f, light.skyBottom);
        int lg = light.light;
        g.radialB.set(0, 0, 1).stop(0f, lg).stop(0.34f, Colors.fade(lg, 0.3f)).stop(1f, Colors.withAlpha(lg, 0f));
        g.radial(light.lx, light.ly, light.lr, light.lr, g.radialB, 64);
        // Stars: two groups twinkling out of step, in three steps like the prototype's CSS.
        if (light.stars > 0.01f) {
            for (int i = 0; i < 84; i++) {
                float period = i % 2 == 0 ? 2.4f : 3.7f;
                float ph = ((time + (i % 2 == 0 ? 0f : 1.4f)) / period) % 2f;
                float k = ph < 1f ? ph : 2f - ph;
                float tw = 1f - 0.75f * (float) Math.floor(k * 3f) / 3f;
                float s = starBig[i] ? 4f : 2f;
                g.rect(starX[i] - (starBig[i] ? 1f : 0f), starY[i] - (starBig[i] ? 1f : 0f), s, s,
                        Colors.fade(starC[i], light.stars * (calm() ? 1f : tw)));
            }
        }
        int hc = light.horizon;
        g.radialB.set(0, 0, 1).stop(0f, Colors.withAlpha(hc, light.ha)).stop(0.45f, Colors.withAlpha(hc, light.ha * 0.3f))
                .stop(1f, Colors.withAlpha(hc, 0f));
        g.radial(light.hx, CY, light.hw, 56f, g.radialB, 64);
    }

    private float rx(float x, float s) {
        return CX + (x - CX) * s;
    }

    private float ry(float y, float s) {
        return CY + (y - CY) * s;
    }

    /** The dial: the horizon line, the track and ticks lit by the sun, the beads of totality, the sections. */
    private void dial(Gfx g, Tokens tk, float ec, float s) {
        // The horizon line across the screen, brightest under the sun.
        int hc = light.horizon;
        float[] at = {0f, light.hzFrom, light.hzPeak, light.hzTo, 1f};
        int[] cols = {Colors.withAlpha(hc, 0f), Colors.withAlpha(hc, light.hpk * 0.35f), Colors.withAlpha(hc, light.hpk),
                Colors.withAlpha(hc, light.hpk * 0.35f), Colors.withAlpha(hc, 0f)};
        g.hgradient(0f, CY - 1f, 1280f, 2f, at, cols, 5);
        Gfx.Radial lit = litPaint(g);
        // Track and ticks.
        int n = 96;
        float[] xs = new float[n], ys = new float[n];
        for (int i = 0; i < n; i++) {
            double t = i * Math.PI * 2 / n;
            xs[i] = g.d.sx(rx(CX + RT * (float) Math.cos(t), s));
            ys[i] = g.d.sy(ry(CY + RT * (float) Math.sin(t), s));
        }
        float trackAlpha = g.alpha;
        g.alpha = trackAlpha * 0.6f;
        g.strokePath(xs, ys, n, true, Math.max(1f, g.d.u), lit);
        g.alpha = trackAlpha;
        for (int i = 0; i < 24; i++) {
            double t = Math.toRadians(i * 15);
            boolean big = i % 6 == 0;
            float r0 = big ? 213f : 215f, r1 = big ? 224f : 220f;
            g.lineScreen(g.d.sx(rx(CX + r0 * (float) Math.cos(t), s)), g.d.sy(ry(CY + r0 * (float) Math.sin(t), s)),
                    g.d.sx(rx(CX + r1 * (float) Math.cos(t), s)), g.d.sy(ry(CY + r1 * (float) Math.sin(t), s)),
                    (big ? 2.5f : 1.5f) * g.d.u, lit);
        }
        // The beads round the track in totality, swept on clockwise from the top.
        boolean eclState = meRing || meOn();
        int cor = Corona.color(tk.glow);
        for (int i = 0; i < 24; i++) {
            float k = eclState ? Math.max(0f, Math.min(1f, (time - eclOnAt - 0.09f - i * 0.014f) / 0.18f)) : 0f;
            float alpha = eclState ? (meRing && meOn() ? 0.4f : k) : 0f;
            if (alpha <= 0.01f) {
                continue;
            }
            double ab = Math.toRadians(i * 15 - 90);
            float z = i % 6 == 0 ? 6f : 4f;
            float bx = rx(CX + 218f * (float) Math.cos(ab), s), by = ry(CY + 218f * (float) Math.sin(ab), s);
            g.glowSprite(bx, by, z * 1.2f, z * 1.2f, Colors.withAlpha(cor, 0.9f * alpha));
            g.rect(Math.round(bx - z / 2f), Math.round(by - z / 2f), z, z, Colors.withAlpha(cor, alpha));
        }
        // The sections (or your petals).
        Place[] list = places();
        for (int i = 0; i < list.length; i++) {
            Place p = list[i];
            float in = entrance(i * 0.045f, 0.55f);
            if (in <= 0f) {
                continue;
            }
            boolean isHot = p.id().equals(hot);
            float sc = (0.72f + 0.28f * pop(in)) * (isHot && in >= 1f ? 1.045f : 1f) * s;
            float rot = (1f - pop(in)) * -25f;
            float al = Math.min(1f, in * 1.8f) * (ec > 0.5f && !meRing ? 0.34f : 1f);
            float base = g.alpha;
            g.alpha = base * al;
            if (meRing) {
                petal(g, p.angle() + rot, 96f, sc, isHot, tk);
            } else {
                segment(g, p.angle() - 28f + rot, p.angle() + 28f + rot, sc, isHot, lit, tk);
            }
            g.alpha = base;
        }
        // You are the gnomon: your shadow falls across the dial away from the sun.
        if (light.shadow > 0.01f) {
            shadow(g, s);
        }
    }

    /** The prototype's qlit gradient: the dial's lines glow where the sun is. */
    private Gfx.Radial litPaint(Gfx g) {
        int lc = light.lightRgb;
        return g.radialA.set(g.d.sx(light.lx), g.d.sy(light.ly), light.litR * g.d.u)
                .stop(0f, Colors.withAlpha(lc, 0.95f)).stop(0.5f, Colors.withAlpha(lc, 0.38f)).stop(1f, 0x1AFFFFFF);
    }

    private void segment(Gfx g, float a0, float a1, float s, boolean isHot, Gfx.Radial lit, Tokens tk) {
        int n = 24;
        float[] ix = new float[n + 1], iy = new float[n + 1], ox = new float[n + 1], oy = new float[n + 1];
        for (int i = 0; i <= n; i++) {
            double t = Math.toRadians(a0 + (a1 - a0) * i / n);
            float c = (float) Math.cos(t), sn = (float) Math.sin(t);
            ix[i] = g.d.sx(CX + R1 * s * c);
            iy[i] = g.d.sy(CY + R1 * s * sn);
            ox[i] = g.d.sx(CX + R2 * s * c);
            oy[i] = g.d.sy(CY + R2 * s * sn);
        }
        Gfx.Paint fillPaint;
        if (isHot) {
            int f = Colors.mix(Colors.argb(204, 16, 18, 26), Colors.withAlpha(tk.accent, 0.8f), 0.16f);
            fillPaint = g.flat(f);
            float mid = (a0 + a1) / 2f;
            double t = Math.toRadians(mid);
            g.glowSprite(CX + (R1 + R2) / 2f * s * (float) Math.cos(t), CY + (R1 + R2) / 2f * s * (float) Math.sin(t), 70f, 70f,
                    Colors.withAlpha(light.lightRgb, 0.28f));
        } else {
            int lf = light.fill;
            fillPaint = g.radialB.set(g.d.sx(light.lx), g.d.sy(light.ly), light.litR * g.d.u)
                    .stop(0f, Colors.withAlpha(lf, 0.66f)).stop(0.55f, Colors.withAlpha(lf, 0.56f)).stop(1f, 0x8F0C0E14);
        }
        g.fillBetween(ix, iy, ox, oy, n + 1, fillPaint);
        float[] px = new float[(n + 1) * 2], py = new float[(n + 1) * 2];
        for (int i = 0; i <= n; i++) {
            px[i] = ox[i];
            py[i] = oy[i];
            px[n + 1 + i] = ix[n - i];
            py[n + 1 + i] = iy[n - i];
        }
        g.strokePath(px, py, (n + 1) * 2, true, 1.25f * g.d.u, isHot ? g.flat(light.lightRgb) : lit);
    }

    /** One of your three corona petals, with a pointed tip (the prototype's petalPath). */
    private void petal(Gfx g, float a, float span, float s, boolean isHot, Tokens tk) {
        float h = span / 2f;
        List<float[]> pts = new ArrayList<>();
        pts.add(polar(R1, a - h + 4f));
        pts.add(polar(168f, a - h));
        quad(pts, polar(168f, a - h), polar(196f, a - h * 0.5f), polar(208f, a));
        quad(pts, polar(208f, a), polar(196f, a + h * 0.5f), polar(168f, a + h));
        pts.add(polar(R1, a + h - 4f));
        for (int i = 1; i < 12; i++) {
            pts.add(polar(R1, a + h - 4f - (span - 8f) * i / 12f));
        }
        int n = pts.size();
        float[] cx = new float[n], cy = new float[n], ox = new float[n], oy = new float[n];
        float[] c = polar(160f, a);
        for (int i = 0; i < n; i++) {
            float[] p = pts.get(i);
            ox[i] = g.d.sx(CX + (p[0] - CX) * s);
            oy[i] = g.d.sy(CY + (p[1] - CY) * s);
            cx[i] = g.d.sx(CX + (c[0] - CX) * s);
            cy[i] = g.d.sy(CY + (c[1] - CY) * s);
        }
        int cor = Corona.color(tk.glow);
        Gfx.Paint fill = isHot
                ? g.radialB.set(g.d.sx(CX), g.d.sy(CY), 200f * g.d.u * s)
                .stop(0.56f, Colors.withAlpha(Colors.mix(tk.accent, 0xFFFFFFFF, 0.55f), 0.5f))
                .stop(0.8f, Colors.withAlpha(Colors.mix(tk.accent, 0xFF10121A, 0.78f), 0.82f))
                .stop(1f, Colors.withAlpha(Colors.mix(tk.accent, 0xFF10121A, 0.86f), 0.86f))
                : g.radialB.set(g.d.sx(CX), g.d.sy(CY), 200f * g.d.u * s)
                .stop(0.56f, Colors.withAlpha(cor, 0.26f)).stop(0.78f, 0x990C0E18).stop(1f, 0xA80C0E18);
        // Fanned from inside the petal, so the concave edge along your disc fills right.
        g.fillBetween(cx, cy, ox, oy, n, fill);
        g.fillBetween(new float[]{ox[n - 1], ox[0]}, new float[]{oy[n - 1], oy[0]}, new float[]{cx[n - 1], cx[0]},
                new float[]{cy[n - 1], cy[0]}, 2, fill);
        if (isHot) {
            float[] tip = polar(186f, a);
            g.glowSprite(CX + (tip[0] - CX) * s, CY + (tip[1] - CY) * s, 80f, 80f, Colors.withAlpha(cor, 0.3f));
        }
        g.strokePath(ox, oy, n, true, 1.25f * g.d.u, g.flat(Colors.withAlpha(cor, isHot ? 0.95f : 0.5f)));
    }

    private static float[] polar(float r, float deg) {
        double t = Math.toRadians(deg);
        return new float[]{CX + r * (float) Math.cos(t), CY + r * (float) Math.sin(t)};
    }

    private static void quad(List<float[]> pts, float[] p0, float[] c, float[] p1) {
        for (int i = 1; i <= 10; i++) {
            float t = i / 10f, u = 1f - t;
            pts.add(new float[]{u * u * p0[0] + 2 * u * t * c[0] + t * t * p1[0], u * u * p0[1] + 2 * u * t * c[1] + t * t * p1[1]});
        }
    }

    /** Your shadow on the dial: a soft half-ellipse from your centre, away from the sun. */
    private void shadow(Gfx g, float s) {
        float rad = (float) Math.toRadians(light.shadowAngle);
        float rxx = 270f * light.shadowLength * s, ryy = 58f * s;
        float c = (float) Math.cos(rad), sn = (float) Math.sin(rad);
        float[] rings = {0f, 0.5f, 0.78f, 1f};
        float[] alpha = {0.6f, 0.34f, 0.12f, 0f};
        int n = 24;
        for (int k = 0; k + 1 < rings.length; k++) {
            float[] ix = new float[n + 1], iy = new float[n + 1], ox = new float[n + 1], oy = new float[n + 1];
            for (int i = 0; i <= n; i++) {
                double t = -Math.PI / 2 + Math.PI * i / n;
                float lx = (float) Math.cos(t), ly = (float) Math.sin(t);
                float x0 = lx * rxx * rings[k], y0 = ly * ryy * rings[k], x1 = lx * rxx * rings[k + 1], y1 = ly * ryy * rings[k + 1];
                ix[i] = g.d.sx(CX + x0 * c - y0 * sn);
                iy[i] = g.d.sy(CY + x0 * sn + y0 * c);
                ox[i] = g.d.sx(CX + x1 * c - y1 * sn);
                oy[i] = g.d.sy(CY + x1 * sn + y1 * c);
            }
            final float a0 = alpha[k] * light.shadow, a1 = alpha[k + 1] * light.shadow;
            final float cxs = g.d.sx(CX), cys = g.d.sy(CY), r0 = rings[k], r1 = rings[k + 1];
            final float sxx = rxx * g.d.u, syy = ryy * g.d.u;
            g.fillBetween(ix, iy, ox, oy, n + 1, (x, y) -> {
                float dx = x - cxs, dy = y - cys;
                float lx = dx * c + dy * sn, ly = -dx * sn + dy * c;
                float t = (float) Math.sqrt((lx / sxx) * (lx / sxx) + (ly / syy) * (ly / syy));
                float k2 = Math.max(0f, Math.min(1f, (t - r0) / Math.max(1e-4f, r1 - r0)));
                return Colors.argb(Math.round((a0 + (a1 - a0) * k2) * 255f), 0, 0, 0);
            });
        }
    }

    /** The pixel sun on the dial (or the moon below the horizon), redrawn 20 times a second. */
    private void sun(Gfx g, float sx, float sy, float ec, float sinA, Tokens tk) {
        if (sunTex == null || sunTex.released()) {
            sunTex = new SolTexture("quick_sun", 32, 32, false);
            sunCanvas = new PixelCanvas(32, 32);
            sunDrawnAt = -1f;
        }
        if (sunDrawnAt < 0f || time - sunDrawnAt >= 0.05f || time < sunDrawnAt) {
            sunDrawnAt = time;
            sunCanvas.clear();
            if (sinA < 0.02f || eclTarget() > 0f || ec > 0.02f) {
                SunCpu.drawBack(sunCanvas, 32f, 16f, 16f, 5f, 0.35f, Colors.mix(0xFFFFF1CF, tk.glow, 0.2f), tk.glow,
                        SunCpu.MENU, time, null);
            } else {
                SunCpu.moon(sunCanvas);
            }
            sunTex.upload(sunCanvas.toArgb(sunPixels));
        }
        float size = 64f * (1f + 0.45f * ec);
        g.image(sunTex, sx - size / 2f, sy - size / 2f, size, size, 0f, 0f, 1f, 1f, 0xFFFFFFFF, false);
    }

    /** The eclipse's pixel corona round your disc. */
    private void coronaLayer(Gfx g, float ec) {
        float e = Math.max(0f, Math.min(1.3f, (ecl.value() - 0.5f) / 0.5f));
        if (e <= 0.01f) {
            return;
        }
        corona.draw(e, time, Corona.color(ThemeState.tokens().glow), ray.value(), rayA, flare);
        if (coronaTex == null || coronaTex.released()) {
            coronaTex = new SolTexture("quick_corona", Corona.N, Corona.N, false);
        }
        coronaTex.upload(corona.argb);
        float size = Corona.N * Corona.CELL;
        float base = g.alpha;
        g.alpha = base * Math.min(1f, e * 1.5f);
        g.image(coronaTex, CX - size / 2f, CY - size / 2f, size, size, 0f, 0f, 1f, 1f, 0xFFFFFFFF, false);
        g.alpha = base;
    }

    /** The section labels and icons on the ring. */
    private void places(Gfx g, Tokens tk, float ec, float s) {
        Place[] list = places();
        float ri = meRing ? 154f : 150f;
        SolFont f = g.font(SolFont.Family.MEDIUM, 12.5f);
        for (int i = 0; i < list.length; i++) {
            Place p = list[i];
            float in = entrance(i * 0.045f + 0.06f, 0.55f);
            if (in <= 0f) {
                continue;
            }
            boolean isHot = p.id().equals(hot);
            double t = Math.toRadians(p.angle());
            float x = rx(CX + ri * (float) Math.cos(t), s), y = ry(CY + ri * (float) Math.sin(t), s);
            float base = g.alpha;
            g.alpha = base * Math.min(1f, in * 1.8f) * (ec > 0.5f && !meRing ? 0.45f : 1f);
            float k = 0.7f + 0.3f * pop(in);
            float icon = 22f * k;
            int textColor = isHot ? 0xFFFFFFFF : 0xCCFFFFFF;
            Icons.draw(g, p.icon(), x - icon / 2f, y - 14f * k - icon / 2f + 2f, icon, isHot ? tk.accent : textColor,
                    isHot ? Icons.FILL_ON : Icons.FILL, isHot ? Colors.withAlpha(tk.glow, 0.6f) : 0);
            String name = tr("sol.section." + p.id());
            g.textCentre(f, name, x + 0.5f, y + 14f * k + 1f, 0xB3000000);
            g.textCentre(f, name, x, y + 14f * k, textColor);
            if (focus == i + 1) {
                g.stroke(x - 52f, y - 32f, 104f, 64f, 16f, 2f * g.d.u, tk.accent);
            }
            g.alpha = base;
        }
    }

    /** You in the middle: your face on a disc lit from the sun's side. */
    private void me(Gfx g, Tokens tk, float ec) {
        float in = calm() ? 1f : pop(Math.min(1f, openT / 0.5f));
        float r = RD * (0.5f + 0.5f * in);
        float base = g.alpha;
        g.alpha = base * Math.min(1f, openT / 0.3f + (calm() ? 1f : 0f));
        int lc = light.lightRgb;
        float lit = light.lit;
        // Outer glow on the lit side and the faint ring.
        g.glowSprite(CX + light.ux * 14f, CY + light.uy * 14f, r + 40f, r + 40f, Colors.withAlpha(lc, lit * 0.6f * 0.6f));
        g.ring(g.d.sx(CX), g.d.sy(CY), (r + 3f) * g.d.u, 6f * g.d.u, 0x0AFFFFFF);
        float hx = CX + light.ux * 0.44f * 2f * r, hy = CY + light.uy * 0.44f * 2f * r;
        float far = (float) Math.hypot(r + Math.abs(hx - CX), r + Math.abs(hy - CY));
        g.radialB.set(g.d.sx(hx), g.d.sy(hy), far * g.d.u).stop(0f, Colors.withAlpha(lc, lit * 0.36f)).stop(0.72f, 0xE608080C)
                .stop(1f, 0xE608080C);
        g.disc(CX, CY, r, g.radialB);
        // The lit rim on the sun's side: the prototype's inset shadow (offset 7 away from the light, blur 9,
        // spread -5), a thin soft crescent just inside the edge facing the sun.
        if (lit > 0.02f) {
            float ox = CX - light.ux * 7f, oy = CY - light.uy * 7f, hole = r + 5f, big = r + 16f;
            int rim = Colors.withAlpha(lc, lit * 0.95f);
            g.radialB.set(g.d.sx(ox), g.d.sy(oy), big * g.d.u).stop(0f, Colors.withAlpha(lc, 0f))
                    .stop((hole - 4.5f) / big, Colors.withAlpha(lc, 0f)).stop((hole + 4.5f) / big, rim).stop(1f, rim);
            g.disc(CX, CY, r, g.radialB);
        }
        g.ring(g.d.sx(CX), g.d.sy(CY), r * g.d.u, Math.max(1f, g.d.u), light.rim);
        float face = 56f * (0.5f + 0.5f * in);
        PlayerFace.draw(g, CX - face / 2f, CY - face / 2f, face, ec * 0.6f);
        if (focus == 0 && meFocus) {
            g.ring(g.d.sx(CX), g.d.sy(CY), (r + 7f) * g.d.u, 1.5f * g.d.u, Colors.withAlpha(tk.accent, 0.7f));
        }
        g.alpha = base;
    }

    /** What you're pointing at, under you. */
    private void label(Gfx g, Tokens tk) {
        String title, sub;
        if (meOn()) {
            title = meRing ? tr("sol.quick.back") : tr("sol.quick.me", SolProfiles.active());
            sub = meRing ? tr("sol.quick.back_sub") : tr("sol.quick.me_sub");
        } else {
            title = tr("sol.section." + hot);
            sub = detail(hot);
        }
        SolFont b = g.font(SolFont.Family.SEMIBOLD, 14f), s = g.font(SolFont.Family.MEDIUM, 11.5f);
        g.textCentre(b, title, CX + 0.5f, 395.5f, 0xCC000000);
        g.textCentre(b, title, CX, 395f, 0xFFFFFFFF);
        int subC = Colors.mix(tk.accent, 0xFFFFFFFF, 0.38f);
        g.textCentre(s, sub, CX + 0.5f, 412.5f, 0xCC000000);
        g.textCentre(s, sub, CX, 412f, subC);
    }

    private String detail(String id) {
        return switch (id) {
            case "modules" -> {
                int on = 0, all = 0;
                for (Module m : Modules.all()) {
                    if (m.available()) {
                        all++;
                        on += m.enabled() ? 1 : 0;
                    }
                }
                yield tr("sol.quick.detail.modules", on, all);
            }
            case "world" -> tr("sol.quick.detail.world", Widgets.themeName(ThemeState.baseEntry()));
            case "music" -> tr("sol.quick.detail.music_none");
            case "mods" -> tr("sol.quick.detail.mods", Widgets.installedMods());
            case "presets" -> tr("sol.quick.detail.presets", Widgets.themeName(ThemeState.baseEntry()), ThemeRegistry.all().size());
            case "emotes" -> tr("sol.quick.detail.emotes", Emotes.all().size(), keyName(KeyBinds.emotes));
            default -> tr("sol.quick.detail." + id);
        };
    }

    // ------------------------------------------------------------------ the tray

    private void trayLayer(Gfx g, Tokens tk) {
        SolFont head = g.font(SolFont.Family.MONO, 10.5f);
        float y = 560f;
        String heading = switch (tray) {
            case PRESETS -> tr("sol.quick.tray.presets");
            case EMOTES -> tr("sol.quick.tray.emotes", keyName(KeyBinds.emotes));
            default -> tr("sol.quick.tray.pins");
        };
        g.textSpaced(head, heading.toUpperCase(java.util.Locale.ROOT), CX - g.width(head, heading.toUpperCase(java.util.Locale.ROOT), 1.26f) / 2f,
                y + 6f, 1.26f, 0x94FFFFFF);
        y += 20f;
        switch (tray) {
            case PRESETS -> presets(g, tk, y);
            case EMOTES -> emotes(g, tk, y);
            default -> pins(g, tk, y);
        }
    }

    private void pins(Gfx g, Tokens tk, float y) {
        List<Widgets.Chip> chips = new ArrayList<>();
        for (String id : SolProfiles.pinned()) {
            Module m = Modules.byId(id);
            if (m == null || !m.available()) {
                continue;
            }
            chips.add(Widgets.Chip.pin(m.name(), ModuleIcons.module(m.id), m.enabled(), () -> {
                m.setEnabled(!m.enabled());
                Modules.save();
            }));
        }
        chips.add(Widgets.Chip.button(tr("sol.quick.all_modules"), () -> expand("modules")));
        rowOfChips(g, tk, chips, y);
    }

    private void emotes(Gfx g, Tokens tk, float y) {
        List<Widgets.Chip> chips = new ArrayList<>();
        Emote playing = EmotePlayer.playing();
        for (Emote e : Emotes.all()) {
            if (e.elytra()) {
                continue;
            }
            boolean cur = playing != null && playing.id().equals(e.id());
            chips.add(Widgets.Chip.toggle(Emotes.name(e), cur, () -> {
                EmotePlayer.play(e);
                Notifications.post(tr("sol.notify.emote", Emotes.name(e)));
            }));
        }
        chips.add(Widgets.Chip.button(tr("sol.quick.edit_wheel"), () -> io.github.georgwav.cosmetics.ui.ScreenOpener.open(new io.github.georgwav.cosmetics.sol.ui.screens.EmoteWheel(false))));
        rowOfChips(g, tk, chips, y);
    }

    /** Chips in centred rows up to 1040 wide, as the prototype's flex-wrap. */
    private void rowOfChips(Gfx g, Tokens tk, List<Widgets.Chip> chips, float y) {
        float maxW = 1040f, gap = 8f;
        List<Widgets.Chip> row = new ArrayList<>();
        float w = 0f;
        int start = 1 + places().length;
        int idx = 0;
        for (int i = 0; i <= chips.size(); i++) {
            Widgets.Chip c = i < chips.size() ? chips.get(i) : null;
            float cw = c == null ? 0f : c.width(g);
            if (c == null || (!row.isEmpty() && w + gap + cw > maxW)) {
                float x = CX - w / 2f;
                for (Widgets.Chip r : row) {
                    float rw = r.width(g);
                    boolean focused = focus == start + idx;
                    r.draw(g, tk, x, y, focused, mx, my);
                    hits.add(new Hit(x, y, rw, Widgets.Chip.H, r.action()));
                    x += rw + gap;
                    idx++;
                }
                row.clear();
                w = 0f;
                y += Widgets.Chip.H + gap;
            }
            if (c != null) {
                w += (row.isEmpty() ? 0f : gap) + cw;
                row.add(c);
            }
        }
    }

    private void presets(Gfx g, Tokens tk, float y) {
        List<ThemeRegistry.Entry> themes = ThemeRegistry.all();
        float cardW = 112f, cardH = 46f + 22f, gap = 8f;
        float total = themes.size() * (cardW + gap) - gap;
        float viewX = 120f, viewW = 1040f;
        String current = ThemeState.themeId();
        if (Float.isNaN(presetScroll)) {
            int ci = 0;
            for (int i = 0; i < themes.size(); i++) {
                if (themes.get(i).id().equals(current)) {
                    ci = i;
                }
            }
            presetScroll = Math.max(0f, Math.min(Math.max(0f, total - viewW + 40f), ci * (cardW + gap) - viewW / 2f + cardW / 2f));
        }
        float x0 = total + 40f <= viewW ? CX - total / 2f : viewX + 20f - presetScroll;
        g.clip(viewX, y - 4f, viewX + viewW, y + cardH + 6f);
        SolFont nameF = g.font(SolFont.Family.SEMIBOLD, 11f);
        int start = 1 + places().length;
        for (int i = 0; i < themes.size(); i++) {
            ThemeRegistry.Entry e = themes.get(i);
            float x = x0 + i * (cardW + gap);
            if (x + cardW < viewX - 4f || x > viewX + viewW + 4f) {
                continue;
            }
            boolean cur = e.id().equals(current);
            boolean over = mx >= x && mx <= x + cardW && my >= y && my <= y + cardH && g.inClip(mx, my);
            boolean focused = focus == start + i;
            g.round(x, y, cardW, cardH, 11f, tk.panel);
            SolTexture thumb = thumbs.get(e);
            if (thumb != null) {
                g.clip(x, y, x + cardW, y + 46f);
                g.image(thumb, x, y, cardW, 46f, 0f, 0f, 1f, 1f, 0xFFFFFFFF, false);
                g.endClip();
            }
            g.rect(x + 8f, y + 46f + 7f, 8f, 8f, e.base().ui().accent());
            g.text(nameF, g.fit(nameF, Widgets.themeName(e), cardW - 30f), x + 22f, y + 46f + 15f, 0xFFFFFFFF);
            boolean empty = e.id().equals("empty");
            int rim = cur ? tk.rim : over ? 0x40FFFFFF : 0x1FFFFFFF;
            if (empty) {
                Widgets.dashed(g, x, y, cardW, cardH, 11f, rim);
            } else {
                g.stroke(x, y, cardW, cardH, 11f, Math.max(1f, g.d.u), rim);
            }
            if (cur) {
                g.glow(x, y, cardW, cardH, 11f, 18f, Colors.withAlpha(tk.glow, 0.35f));
            }
            if (focused) {
                g.stroke(x - 3f, y - 3f, cardW + 6f, cardH + 6f, 13f, 2f * g.d.u, tk.accent);
            }
            final String id = e.id();
            hits.add(new Hit(Math.max(x, viewX), y, Math.min(x + cardW, viewX + viewW) - Math.max(x, viewX), cardH, () -> applyPreset(id)));
        }
        g.endClip();
        // Profiles and "save as profile".
        List<Widgets.Chip> chips = new ArrayList<>();
        String active = SolProfiles.active();
        for (String name : SolProfiles.names()) {
            chips.add(Widgets.Chip.toggle(tr("sol.quick.profile", name), name.equals(active), () -> {
                SolProfiles.switchTo(name);
                Notifications.post(tr("sol.notify.profile", name));
            }));
        }
        chips.add(Widgets.Chip.button(tr("sol.quick.save_profile"), () -> {
            String saved = SolProfiles.saveAs(null);
            if (saved != null) {
                Notifications.post(tr("sol.notify.profile_saved", saved));
            }
        }));
        // Their keyboard focus follows the cards.
        int saveFocus = focus;
        focus = saveFocus - themes.size();
        rowOfChips(g, tk, chips, y + cardH + 14f);
        focus = saveFocus;
    }

    private void applyPreset(String id) {
        ThemeRegistry.Entry e = ThemeRegistry.find(id);
        if (e == null) {
            return;
        }
        boolean empty = id.equals("empty");
        if (Modules.ATMOSPHERE != null) {
            Modules.ATMOSPHERE.setEnabled(!empty);
        }
        ThemeState.setAccent(null);
        SolProfiles.setAccent(null);
        ThemeState.setTheme(id, true);
        SolProfiles.setTheme(id);
        Notifications.post(empty ? tr("sol.notify.preset_empty")
                : tr("sol.notify.preset", Widgets.themeName(e), SolProfiles.active()));
    }

    /** The search pill at the top. */
    private void search(Gfx g, Tokens tk) {
        float x = 460f, y = 24f, w = 360f, h = 36f;
        boolean over = mx >= x && mx <= x + w && my >= y && my <= y + h;
        g.round(x, y, w, h, 18f, 0xCC08080C);
        g.stroke(x, y, w, h, 18f, Math.max(1f, g.d.u), over || focus == searchFocus() ? tk.rim : 0x24FFFFFF);
        Icons.draw(g, "search", x + 14f, y + 10f, 16f, 0x9EFFFFFF, Icons.FILL, 0);
        SolFont f = g.font(SolFont.Family.MEDIUM, 13f);
        g.textMid(f, tr("sol.quick.search"), x + 40f, y + h / 2f, 0x9EFFFFFF);
        Widgets.kbd(g, tr("sol.quick.ctrl_k"), x + w - 8f, y + h / 2f);
        hits.add(new Hit(x, y, w, h, () -> SolMenu.openSearch(parent, "")));
    }

    private int searchFocus() {
        return 1 + places().length + trayCount();
    }

    private int trayCount() {
        return switch (tray) {
            case PRESETS -> ThemeRegistry.all().size() + SolProfiles.names().size() + 1;
            case EMOTES -> (int) Emotes.all().stream().filter(e -> !e.elytra()).count() + 1;
            default -> (int) SolProfiles.pinned().stream().map(Modules::byId).filter(m -> m != null && m.available()).count() + 1;
        };
    }

    // ------------------------------------------------------------------ input

    /** Which place on the ring a design point is over (the prototype's qPick), or null. */
    private Place pick(float x, float y) {
        float dx = x - CX, dy = y - CY, d = (float) Math.hypot(dx, dy);
        if (d < R1 - 4f || d > RT + 22f) {
            return null;
        }
        float a = (float) Math.toDegrees(Math.atan2(dy, dx));
        float half = (meRing ? 112f : 56f) / 2f + 2f;
        for (Place p : places()) {
            if (Math.abs(QuickLight.norm(a - p.angle())) <= half) {
                return p;
            }
        }
        return null;
    }

    private boolean overMe(float x, float y) {
        return Math.hypot(x - CX, y - CY) <= RD;
    }

    @Override
    protected void moved(float x, float y) {
        boolean over = overMe(x, y);
        if (over != meHover) {
            meHover = over;
            if (!over) {
                meFresh = false;
            }
        }
        if (!over) {
            Place p = pick(x, y);
            if (p != null && !p.id().equals(hot)) {
                setHot(p.id());
            }
        }
    }

    @Override
    protected boolean press(float x, float y, int button) {
        if (button != 0 || expand >= 0f) {
            return false;
        }
        if (overMe(x, y)) {
            toggleMe();
            return true;
        }
        for (Hit h : hits) {
            if (h.contains(x, y)) {
                h.action().run();
                return true;
            }
        }
        Place p = pick(x, y);
        if (p != null) {
            choose(p.id());
            return true;
        }
        return false;
    }

    @Override
    protected boolean scroll(float x, float y, float amount) {
        if (tray == Tray.PRESETS && y >= 576f && y <= 650f) {
            float total = ThemeRegistry.all().size() * 120f - 8f;
            presetScroll = Math.max(0f, Math.min(Math.max(0f, total - 1000f), (Float.isNaN(presetScroll) ? 0f : presetScroll) - amount * 60f));
            return true;
        }
        return false;
    }

    @Override
    protected boolean key(int key, boolean ctrl, boolean shift) {
        if (expand >= 0f) {
            return true;
        }
        if (KeyBinds.matches(KeyBinds.quickMenu(), key) && !ctrl) {
            onClose();
            return true;
        }
        if (ctrl && key == InputConstants.KEY_K) {
            SolMenu.openSearch(parent, "");
            return true;
        }
        Place[] list = places();
        switch (key) {
            case InputConstants.KEY_LEFT, InputConstants.KEY_RIGHT -> {
                int i = 0;
                for (int k = 0; k < list.length; k++) {
                    if (list[k].id().equals(hot)) {
                        i = k;
                    }
                }
                i = (i + (key == InputConstants.KEY_RIGHT ? 1 : list.length - 1)) % list.length;
                setHot(list[i].id());
                focus = i + 1;
                meFocus = false;
                return true;
            }
            case InputConstants.KEY_TAB -> {
                int count = searchFocus() + 1;
                focus = ((focus < 0 ? -1 : focus) + (shift ? count - 1 : 1) + count) % count;
                meFocus = focus == 0;
                if (focus >= 1 && focus <= list.length) {
                    setHot(list[focus - 1].id());
                }
                return true;
            }
            case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER, InputConstants.KEY_SPACE -> {
                if (focus == 0 || focus < 0 && meFocus) {
                    toggleMe();
                } else if (focus >= 1 && focus <= list.length) {
                    choose(list[focus - 1].id());
                } else if (focus > list.length) {
                    int idx = focus - list.length - 1;
                    // Tray items then the search pill, in the order they were drawn.
                    if (idx >= 0 && idx < hits.size()) {
                        hits.get(idx).action().run();
                    }
                } else {
                    choose(hot);
                }
                return true;
            }
            default -> {
            }
        }
        return false;
    }

    @Override
    protected boolean back() {
        if (meRing) {
            toggleMe();
            meFresh = false;
            return true;
        }
        return false;
    }

    @Override
    protected boolean typed(String text) {
        if (!SolConfig.get().searchNeedsCtrl && !text.isBlank()) {
            SolMenu.openSearch(parent, text);
            return true;
        }
        return false;
    }

    @Override
    protected void closed() {
        if (coronaTex != null) {
            coronaTex.release();
        }
        if (sunTex != null) {
            sunTex.release();
        }
        thumbs.release();
    }

    // ------------------------------------------------------------------ motion helpers

    /** How far an entrance is (0..1) that starts {@code delay} seconds after the ring appeared and lasts {@code len}. */
    private float entrance(float delay, float len) {
        if (calm()) {
            return 1f;
        }
        return Math.max(0f, Math.min(1f, (ringT - delay) / len));
    }

    /** The prototypes' springy CSS ease, cubic-bezier(.34, 1.56, .64, 1), as an ease-out-back. */
    static float pop(float k) {
        float c1 = 1.70158f, c3 = c1 + 1f, x = k - 1f;
        return 1f + c3 * x * x * x + c1 * x * x;
    }

    private static int rgba(int r, int g, int b, float a) {
        return Colors.argb(Math.round(Math.max(0f, Math.min(1f, a)) * 255f), r, g, b);
    }

    protected static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }
}
