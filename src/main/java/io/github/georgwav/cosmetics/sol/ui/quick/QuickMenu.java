package io.github.georgwav.cosmetics.sol.ui.quick;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.georgwav.cosmetics.client.Module;
import io.github.georgwav.cosmetics.client.Modules;
import io.github.georgwav.cosmetics.config.CosmeticsConfig;
import io.github.georgwav.cosmetics.emote.Emote;
import io.github.georgwav.cosmetics.emote.EmotePlayer;
import io.github.georgwav.cosmetics.emote.Emotes;
import io.github.georgwav.cosmetics.sol.Feature;
import io.github.georgwav.cosmetics.sol.config.SolConfig;
import io.github.georgwav.cosmetics.sol.config.SolProfiles;
import io.github.georgwav.cosmetics.sol.render.SolTexture;
import io.github.georgwav.cosmetics.sol.theme.ThemeRegistry;
import io.github.georgwav.cosmetics.sol.theme.ThemeSpec;
import io.github.georgwav.cosmetics.sol.theme.ThemeState;
import io.github.georgwav.cosmetics.sol.theme.Tokens;
import io.github.georgwav.cosmetics.sol.ui.kit.ColorPicker;
import io.github.georgwav.cosmetics.sol.ui.kit.Gfx;
import io.github.georgwav.cosmetics.sol.ui.kit.Icons;
import io.github.georgwav.cosmetics.sol.ui.kit.ModuleIcons;
import io.github.georgwav.cosmetics.sol.ui.kit.Notifications;
import io.github.georgwav.cosmetics.sol.ui.kit.PlayerFace;
import io.github.georgwav.cosmetics.sol.ui.kit.SolFont;
import io.github.georgwav.cosmetics.sol.ui.kit.SolScreen;
import io.github.georgwav.cosmetics.sol.ui.kit.Ui;
import io.github.georgwav.cosmetics.sol.ui.kit.Widgets;
import io.github.georgwav.cosmetics.sol.ui.menu.SolMenu;
import io.github.georgwav.cosmetics.sol.ui.screens.EmotePicker;
import io.github.georgwav.cosmetics.sol.util.Colors;
import io.github.georgwav.cosmetics.sol.util.Mulberry32;
import io.github.georgwav.cosmetics.sol.util.PixelCanvas;
import io.github.georgwav.cosmetics.sol.util.Spring;
import io.github.georgwav.cosmetics.sol.util.SunCpu;
import io.github.georgwav.cosmetics.ui.EmotePictures;
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
 * Presets and Emotes open a wheel round you in place of the petals: the presets on a ring (your own profiles and
 * the packs' themes on a second and third ring outside it), or the emote wheel with its pages, to play from or edit.
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

    enum Tray { PINS, PRESETS }

    /** A wheel open round you inside your ring. */
    enum Wheel { NONE, PRESETS, EMOTES }

    /** Ring radii: the built-in presets, your own (two rings), and the emote slots (their hits reach their buttons). */
    static final float W1A = 118f, W1B = 178f, W2A = 186f, W2B = 236f, W3A = 244f, W3B = 292f;
    static final float EA = 118f, EB = 202f, EHIT = 226f;
    /** Your own presets per ring before the next one starts. */
    static final int OWN_PER_RING = 10;

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
    private final WheelSkies skies = new WheelSkies();
    /** For the dev scenario: the theme whose sector stays pointed at, or null. */
    private String stagedHot;
    /** Per wheel item, how far it is pointed at (0..1, eased), for its lift and light. */
    private final float[] hotK = new float[128];
    private Wheel wheel = Wheel.NONE;
    private float wheelT;
    /** The wheel's items this frame, and the one pointed at (-1 none). */
    private final List<Item> items = new ArrayList<>();
    private int wheelHot = -1;
    private boolean elytraWheel;
    private int emotePage;
    private EmotePicker picker;
    /** Under the presets wheel: the picker for any accent colour, open from the colour row's last swatch (null while shut). */
    private ColorPicker accentPicker;
    private final EmotePictures pictures = new EmotePictures();
    private final Ui ui = new Ui();
    private final Band band = new Band();

    /** One item on a wheel: a ring's sector between two angles (degrees, clockwise from the right). */
    private static final class Item {
        float r0, r1, hitR, a0, a1;
        String title, sub, icon;
        int inner, outer;
        boolean current;
        ThemeRegistry.Entry theme;
        /** The theme whose sky fills the sector (a theme's own, or the one a profile wears), or null. */
        ThemeRegistry.Entry sky;
        Emote emote;
        int slot = -1;
        Runnable action, alt;

        float mid() {
            return (a0 + a1) / 2f;
        }

        boolean contains(float x, float y) {
            float dx = x - CX, dy = y - CY, d = (float) Math.hypot(dx, dy);
            if (d < r0 || d > hitR) {
                return false;
            }
            float a = (float) Math.toDegrees(Math.atan2(dy, dx));
            return Math.abs(QuickLight.norm(a - mid())) <= (a1 - a0) / 2f;
        }
    }

    /** A colour from a ring's inner edge to its outer one (screen pixels round the dial's middle). */
    private static final class Band implements Gfx.Paint {
        float cx, cy, r0, r1;
        int c0, c1;

        @Override
        public int at(float x, float y) {
            float d = (float) Math.hypot(x - cx, y - cy);
            return Colors.mix(c0, c1, Math.max(0f, Math.min(1f, (d - r0) / Math.max(1f, r1 - r0))));
        }
    }

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
     * it; "eclipse" points at you; "presets" and "emotes" open their wheels in your ring ("emote_picker" with its
     * picker, "presets_colour" with the colour picker); "back" points at you from your ring.
     */
    public QuickMenu stage(String state) {
        switch (state) {
            case "eclipse" -> {
                meHover = true;
                meFresh = false;
            }
            case "presets", "presets_hot", "presets_colour", "emotes", "emote_picker" -> {
                if (!meRing) {
                    toggleMe();
                }
                meHover = false;
                setHot(state.startsWith("emote") ? "emotes" : "presets");
                openWheel(state.startsWith("emote") ? Wheel.EMOTES : Wheel.PRESETS);
                if (state.equals("emote_picker")) {
                    picker = new EmotePicker(elytraWheel, 0);
                }
                if (state.equals("presets_hot")) {
                    stagedHot = "ember_solstice";
                }
                if (state.equals("presets_colour")) {
                    accentPicker = newAccentPicker();
                }
            }
            case "back" -> {
                if (!meRing) {
                    toggleMe();
                }
                setHot("emotes");
                meHover = true;
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
            case "emotes" -> openWheel(Wheel.EMOTES);
            default -> openWheel(Wheel.PRESETS);
        }
    }

    private void openWheel(Wheel w) {
        wheel = w;
        wheelT = 0f;
        wheelHot = -1;
        focus = -1;
        emotePage = Math.max(0, Math.min(emotePage, Emotes.pages(elytraWheel) - 1));
    }

    /** Back from a wheel to your three petals. */
    private void closeWheel() {
        wheel = Wheel.NONE;
        picker = null;
        accentPicker = null;
        wheelHot = -1;
        ringT = 0f;
        meFresh = true;
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
            wheel = Wheel.NONE;
            picker = null;
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
        wheelT += dt;
        ui.begin(g, mx, my);
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
        if (wheel != Wheel.NONE) {
            wheelLayer(g, tk);
        } else {
            places(g, tk, ec, ringScale);
        }
        me(g, tk, ec);
        label(g, tk);
        g.alpha = fade * (expand >= 0f ? Math.max(0f, 1f - expand * 3f) : 1f);
        if (wheel != Wheel.NONE) {
            wheelExtras(g, tk);
            if (picker != null) {
                g.rectScreen(g.d.left(), g.d.top(), g.d.right(), g.d.bottom(), 0x8C04050A, 0x8C04050A);
                picker.draw(g, ui, tk, CX, 360f, my, pictures, () -> picker = null);
            }
            g.alpha = 1f;
            return;
        }
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
        if (wheel != Wheel.NONE) {
            return;
        }
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
        g.ring(CX, CY, r + 3f, 6f * g.d.u, 0x0AFFFFFF);
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
        g.ring(CX, CY, r, Math.max(1f, g.d.u), light.rim);
        float face = 56f * (0.5f + 0.5f * in);
        PlayerFace.draw(g, CX - face / 2f, CY - face / 2f, face, ec * 0.6f);
        if (focus == 0 && meFocus) {
            g.ring(CX, CY, r + 7f, 1.5f * g.d.u, Colors.withAlpha(tk.accent, 0.7f));
        }
        g.alpha = base;
    }

    /** What you're pointing at, under you. */
    private void label(Gfx g, Tokens tk) {
        String title, sub;
        if (wheel != Wheel.NONE && !meOn()) {
            Item it = wheelHot >= 0 && wheelHot < items.size() ? items.get(wheelHot) : null;
            if (it != null) {
                title = it.title;
                sub = it.sub;
            } else if (wheel == Wheel.PRESETS) {
                title = tr("sol.section.presets");
                sub = tr("sol.quick.wheel.presets_sub");
            } else {
                title = tr(elytraWheel ? "sol.quick.wheel.elytra" : "sol.section.emotes");
                sub = tr("sol.quick.wheel.emotes_sub", emotePage + 1, Emotes.pages(elytraWheel));
            }
        } else if (meOn()) {
            title = meRing ? tr("sol.quick.back") : tr("sol.quick.me", SolProfiles.active());
            sub = wheel != Wheel.NONE ? tr("sol.quick.wheel.back_sub") : meRing ? tr("sol.quick.back_sub") : tr("sol.quick.me_sub");
        } else {
            title = tr("sol.section." + hot);
            sub = detail(hot);
        }
        SolFont b = g.font(SolFont.Family.SEMIBOLD, 14f), s = g.font(SolFont.Family.MEDIUM, 11.5f);
        // Under you, or under a wheel's rings (they fill the space round you).
        float ty = wheel != Wheel.NONE ? labelY() : 395f;
        g.textCentre(b, title, CX + 0.5f, ty + 0.5f, 0xCC000000);
        g.textCentre(b, title, CX, ty, 0xFFFFFFFF);
        int subC = Colors.mix(tk.accent, 0xFFFFFFFF, 0.38f);
        g.textCentre(s, sub, CX + 0.5f, ty + 17.5f, 0xCC000000);
        g.textCentre(s, sub, CX, ty + 17f, subC);
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
            case "music" -> {
                String playing = io.github.georgwav.cosmetics.sol.music.MusicService.line();
                yield playing != null ? playing : tr("sol.quick.detail.music_none");
            }
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
            default -> tr("sol.quick.tray.pins");
        };
        g.textSpaced(head, heading.toUpperCase(java.util.Locale.ROOT), CX - g.width(head, heading.toUpperCase(java.util.Locale.ROOT), 1.26f) / 2f,
                y + 6f, 1.26f, 0x94FFFFFF);
        y += 20f;
        switch (tray) {
            case PRESETS -> presets(g, tk, y);
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
            String saved = SolProfiles.saveAs(SolProfiles.newName());
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
    }

    // ------------------------------------------------------------------ the wheels

    /** This frame's items: the presets' rings, or the emote wheel's page. */
    private void buildWheel() {
        items.clear();
        if (wheel == Wheel.PRESETS) {
            List<ThemeRegistry.Entry> builtIn = new ArrayList<>();
            List<Item> own = new ArrayList<>();
            String current = ThemeState.themeId();
            for (ThemeRegistry.Entry e : ThemeRegistry.all()) {
                if (e.builtIn()) {
                    builtIn.add(e);
                } else {
                    Item it = themeItem(e, current);
                    it.sub = tr("sol.quick.wheel.pack_sub");
                    own.add(it);
                }
            }
            for (int i = 0; i < builtIn.size(); i++) {
                place(themeItem(builtIn.get(i), current), i, builtIn.size(), W1A, W1B);
            }
            // Your own: your profiles (the theme each one wears), then saving what you have as a new one.
            String active = SolProfiles.active();
            for (String name : SolProfiles.names()) {
                Item it = new Item();
                ThemeRegistry.Entry t = ThemeRegistry.get(SolProfiles.themeOf(name));
                it.title = name;
                it.sub = tr("sol.quick.wheel.profile_sub", Widgets.themeName(t));
                ThemeSpec.Sky k = t != null ? t.base().sky() : null;
                it.inner = k != null ? Colors.mix(k.horizon(), 0xFF0C0E14, 0.45f) : 0xFF1A1C24;
                it.outer = k != null ? Colors.mix(k.top(), 0xFF0C0E14, 0.45f) : 0xFF0C0E14;
                it.current = name.equals(active);
                it.sky = t;
                it.action = () -> SolProfiles.switchTo(name);
                own.add(it);
            }
            Item save = new Item();
            save.title = tr("sol.quick.wheel.save");
            save.sub = tr("sol.quick.wheel.save_sub");
            save.icon = "plus";
            save.inner = 0xFF14161E;
            save.outer = 0xFF0C0E14;
            save.action = () -> {
                String saved = SolProfiles.saveAs(SolProfiles.newName());
                if (saved != null) {
                    Notifications.post(tr("sol.notify.profile_saved", saved));
                }
            };
            own.add(save);
            int second = Math.min(own.size(), own.size() > OWN_PER_RING + 2 ? OWN_PER_RING : own.size());
            for (int i = 0; i < own.size(); i++) {
                if (i < second) {
                    place(own.get(i), i, second, W2A, W2B);
                } else {
                    place(own.get(i), i - second, own.size() - second, W3A, W3B);
                }
            }
        } else if (wheel == Wheel.EMOTES) {
            int per = CosmeticsConfig.WHEEL_SLOTS;
            Emote playing = EmotePlayer.playing();
            for (int i = 0; i < per; i++) {
                int slot = emotePage * per + i;
                Emote e = Emotes.at(elytraWheel, slot);
                Item it = new Item();
                it.slot = slot;
                it.emote = e;
                it.title = e != null ? Emotes.name(e) : tr("sol.quick.wheel.empty_slot");
                it.sub = tr(e != null ? "sol.quick.wheel.emote_sub" : "sol.quick.wheel.empty_sub");
                it.inner = 0xFF181A22;
                it.outer = 0xFF0C0E14;
                it.current = e != null && e == playing;
                it.alt = () -> picker = new EmotePicker(elytraWheel, slot);
                it.action = e == null ? it.alt : () -> {
                    EmotePlayer.play(e, elytraWheel);
                    onClose();
                };
                place(it, i, per, EA, EB);
                it.hitR = EHIT;
            }
        }
    }

    private Item themeItem(ThemeRegistry.Entry e, String current) {
        Item it = new Item();
        it.theme = e;
        it.title = Widgets.themeName(e);
        String line = Widgets.themeLine(e);
        it.sub = line == null || line.isBlank() ? tr("sol.quick.wheel.preset_sub") : line;
        ThemeSpec.Sky k = e.base().sky();
        it.inner = Colors.mix(k.horizon(), k.glow(), 0.25f);
        it.outer = Colors.mix(k.top(), 0xFF000000, 0.2f);
        it.current = e.id().equals(current);
        it.sky = e;
        String id = e.id();
        it.action = () -> applyPreset(id);
        return it;
    }

    /** Puts {@code it} at {@code i} of {@code n} round a ring from r0 to r1, the first at the top. */
    private void place(Item it, int i, int n, float r0, float r1) {
        float span = 360f / Math.max(1, n), gap = Math.min(1.6f, span * 0.08f);
        float mid = -90f + i * span;
        it.r0 = r0;
        it.r1 = r1;
        it.hitR = r1;
        it.a0 = mid - span / 2f + gap;
        it.a1 = mid + span / 2f - gap;
        items.add(it);
    }

    private void wheelLayer(Gfx g, Tokens tk) {
        buildWheel();
        for (int i = 0; stagedHot != null && i < items.size(); i++) {
            if (items.get(i).theme != null && items.get(i).theme.id().equals(stagedHot)) {
                wheelHot = i;
            }
        }
        if (wheelHot >= items.size()) {
            wheelHot = -1;
        }
        SolFont name = g.font(SolFont.Family.SEMIBOLD, 10.5f), small = g.font(SolFont.Family.MEDIUM, 10f),
                mono = g.font(SolFont.Family.MONO, 10f);
        float base = g.alpha;
        for (int i = 0; i < items.size(); i++) {
            Item it = items.get(i);
            float in = calm() ? 1f : Math.max(0f, Math.min(1f, (wheelT - i * 0.012f) / 0.32f));
            if (in <= 0f) {
                continue;
            }
            boolean hot = i == wheelHot;
            float s = 0.86f + 0.14f * pop(in);
            g.alpha = base * Math.min(1f, in * 1.6f);
            if (i < hotK.length) {
                float k = hot ? 1f : 0f;
                hotK[i] = calm() ? k : hotK[i] + (k - hotK[i]) * Math.min(1f, dt * 12f);
            }
            if (wheel == Wheel.PRESETS && it.sky != null) {
                skySector(g, tk, it, i, s, i < hotK.length ? hotK[i] : 0f, name);
                continue;
            }
            band.cx = g.d.sx(CX);
            band.cy = g.d.sy(CY);
            band.r0 = it.r0 * s * g.d.u;
            band.r1 = it.r1 * s * g.d.u;
            band.c0 = Colors.withAlpha(hot ? Colors.mix(it.inner, 0xFFFFFFFF, 0.14f) : it.inner, 0.9f);
            band.c1 = Colors.withAlpha(hot ? Colors.mix(it.outer, 0xFFFFFFFF, 0.1f) : it.outer, 0.92f);
            int edge = it.current ? tk.accent : hot ? Colors.withAlpha(light.lightRgb, 0.9f) : 0x38FFFFFF;
            sector(g, it.r0 * s, it.r1 * s, it.a0, it.a1, band, edge, it.current || hot ? 1.5f : 1f);
            float rm = (it.r0 + it.r1) / 2f * s;
            double t = Math.toRadians(it.mid());
            float x = CX + rm * (float) Math.cos(t), y = CY + rm * (float) Math.sin(t);
            if (hot || it.current) {
                g.glowSprite(x, y, 60f, 60f, Colors.withAlpha(it.current ? tk.glow : light.lightRgb, hot ? 0.28f : 0.16f));
            }
            float room = Math.min(110f, (float) Math.toRadians(it.a1 - it.a0) * rm - 8f);
            int text = hot || it.current ? 0xFFFFFFFF : 0xD9FFFFFF;
            if (wheel == Wheel.EMOTES) {
                if (it.emote != null) {
                    int px = Math.round(58f * s * g.d.u);
                    pictures.draw(g.game(), it.emote, Math.round(g.d.sx(x)) - px / 2, Math.round(g.d.sy(y - 7f)) - px / 2, px);
                    g.layer();
                    g.textCentre(small, g.fit(small, it.title, room), x, y + 29f, text);
                } else {
                    plus(g, x, y - 6f, 16f, hot ? tk.accent : 0x80FFFFFF);
                    g.textCentre(small, it.title, x, y + 14f, hot ? 0xFFFFFFFF : 0x80FFFFFF);
                }
                // The slot's number in its first corner, clear of the picture and the name.
                double tn = Math.toRadians(it.a0 + 5f);
                float rn = it.r1 * s - 12f;
                g.textCentre(mono, Integer.toString(it.slot % CosmeticsConfig.WHEEL_SLOTS + 1),
                        CX + rn * (float) Math.cos(tn), CY + rn * (float) Math.sin(tn), 0x66FFFFFF);
            } else if (it.icon != null) {
                plus(g, x, y - 9f, 12f, hot ? tk.accent : 0xB3FFFFFF);
                lines(g, name, it.title, x, y + 8f, room, text);
            } else {
                lines(g, name, it.title, x, y, room, text);
            }
        }
        g.alpha = base;
        // The slot pointed at: change it, or empty it, just outside the ring.
        Item hot = wheelHot >= 0 ? items.get(wheelHot) : null;
        if (wheel == Wheel.EMOTES && hot != null && hot.emote != null && picker == null) {
            slotButton(g, tk, hot, -9f, "gear", hot.alt, false);
            final int slot = hot.slot;
            slotButton(g, tk, hot, 9f, "close", () -> {
                Emotes.assign(elytraWheel, slot, "");
                CosmeticsConfig.save();
            }, true);
        }
    }

    /** One of the round buttons by the slot pointed at. */
    private void slotButton(Gfx g, Tokens tk, Item it, float da, String icon, Runnable action, boolean red) {
        double t = Math.toRadians(it.mid() + da);
        float r = 214f, x = CX + r * (float) Math.cos(t), y = CY + r * (float) Math.sin(t), h = 11f;
        boolean over = Math.hypot(mx - x, my - y) <= h + 1f;
        int fill = red ? Colors.withAlpha(Tokens.RED, over ? 0.95f : 0.75f) : over ? 0xE62A2A33 : 0xD90A0A0E;
        g.disc(x, y, h, g.flat(fill));
        g.ring(x, y, h, Math.max(1f, g.d.u), over ? tk.accent : 0x40FFFFFF);
        Icons.draw(g, icon, x - 6f, y - 6f, 12f, 0xFFFFFFFF, Icons.FILL, 0);
        hits.add(new Hit(x - h, y - h, h * 2f, h * 2f, action));
    }

    /** A name in up to two centred lines (split at a space) that fit {@code room}. */
    private void lines(Gfx g, SolFont f, String text, float x, float y, float room, int argb) {
        if (g.width(f, text) <= room || text.indexOf(' ') < 0) {
            g.textCentre(f, g.fit(f, text, room), x, y, argb);
            return;
        }
        int cut = text.lastIndexOf(' ', Math.max(1, text.length() / 2 + 2));
        if (cut <= 0) {
            cut = text.indexOf(' ');
        }
        g.textCentre(f, g.fit(f, text.substring(0, cut), room), x, y - 6.5f, argb);
        g.textCentre(f, g.fit(f, text.substring(cut + 1), room), x, y + 6.5f, argb);
    }

    /** A ring's sector from r0 to r1 between two angles, filled with {@code fill}, its edge in {@code edge}. */
    private void sector(Gfx g, float r0, float r1, float a0, float a1, Gfx.Paint fill, int edge, float width) {
        int n = Math.max(6, Math.round((a1 - a0) / 2.5f));
        float[] ix = new float[n + 1], iy = new float[n + 1], ox = new float[n + 1], oy = new float[n + 1];
        for (int i = 0; i <= n; i++) {
            double t = Math.toRadians(a0 + (a1 - a0) * i / n);
            float c = (float) Math.cos(t), sn = (float) Math.sin(t);
            ix[i] = g.d.sx(CX + r0 * c);
            iy[i] = g.d.sy(CY + r0 * sn);
            ox[i] = g.d.sx(CX + r1 * c);
            oy[i] = g.d.sy(CY + r1 * sn);
        }
        g.fillBetween(ix, iy, ox, oy, n + 1, fill);
        float[] px = new float[(n + 1) * 2], py = new float[(n + 1) * 2];
        for (int i = 0; i <= n; i++) {
            px[i] = ox[i];
            py[i] = oy[i];
            px[n + 1 + i] = ix[n - i];
            py[n + 1 + i] = iy[n - i];
        }
        g.strokePath(px, py, (n + 1) * 2, true, width * g.d.u, g.flat(edge));
    }

    // Scratch for the sky sectors (reused, so the wheel allocates nothing a frame).
    private final float[] sIx = new float[64], sIy = new float[64], sOx = new float[64], sOy = new float[64];
    private final float[] sIu = new float[64], sIv = new float[64], sOu = new float[64], sOv = new float[64];
    private final float[] sPx = new float[128], sPy = new float[128];

    /**
     * A sector with its theme's sky in it: the picture upright, as through a window, drifting slowly, with the theme's
     * particles alive over it. Pointed at ({@code k} 1), it lifts out of the ring, lights up and its sky moves; the
     * others dim a little meanwhile.
     */
    private void skySector(Gfx g, Tokens tk, Item it, int index, float s, float k, SolFont font) {
        double mid = Math.toRadians(it.mid());
        float lift = 8f * k, cx = CX + lift * (float) Math.cos(mid), cy = CY + lift * (float) Math.sin(mid);
        float r0 = it.r0 * s, r1 = it.r1 * s;
        int n = Math.min(63, Math.max(6, Math.round((it.a1 - it.a0) / 2.5f)));
        float bx0 = Float.MAX_VALUE, by0 = Float.MAX_VALUE, bx1 = -Float.MAX_VALUE, by1 = -Float.MAX_VALUE;
        for (int j = 0; j <= n; j++) {
            double t = Math.toRadians(it.a0 + (it.a1 - it.a0) * j / n);
            float c = (float) Math.cos(t), sn = (float) Math.sin(t);
            sIx[j] = cx + r0 * c;
            sIy[j] = cy + r0 * sn;
            sOx[j] = cx + r1 * c;
            sOy[j] = cy + r1 * sn;
            bx0 = Math.min(bx0, Math.min(sIx[j], sOx[j]));
            bx1 = Math.max(bx1, Math.max(sIx[j], sOx[j]));
            by0 = Math.min(by0, Math.min(sIy[j], sOy[j]));
            by1 = Math.max(by1, Math.max(sIy[j], sOy[j]));
        }
        // The picture covers the sector's box with a little to spare, and drifts within that.
        float bw = bx1 - bx0, bh = by1 - by0, aspect = (float) WheelSkies.W / WheelSkies.H;
        float pw = Math.max(bw, bh * aspect) * 1.14f, ph = pw / aspect;
        float drift = calm() ? 0f : 0.4f;
        float px0 = (bx0 + bx1 - pw) / 2f + (pw - bw) / 2f * drift * (float) Math.sin(wheelT * 0.11f + index * 1.3f);
        float py0 = (by0 + by1 - ph) / 2f + (ph - bh) / 2f * drift * (float) Math.cos(wheelT * 0.09f + index * 2.1f);
        for (int j = 0; j <= n; j++) {
            sIu[j] = (sIx[j] - px0) / pw;
            sIv[j] = (sIy[j] - py0) / ph;
            sOu[j] = (sOx[j] - px0) / pw;
            sOv[j] = (sOy[j] - py0) / ph;
            sIx[j] = g.d.sx(sIx[j]);
            sIy[j] = g.d.sy(sIy[j]);
            sOx[j] = g.d.sx(sOx[j]);
            sOy[j] = g.d.sy(sOy[j]);
        }
        float dim = wheelHot >= 0 ? 0.6f : 0.84f;
        float bright = it.current ? Math.max(dim, 0.92f) : dim;
        bright += (1f - bright) * k;
        SolTexture sky = skies.get(it.sky, wheelT, k > 0.5f || it.current);
        if (sky != null) {
            int v = Math.round(bright * 255f);
            g.imageBetween(sky, sIx, sIy, sOx, sOy, sIu, sIv, sOu, sOv, n + 1, 0xFF000000 | v << 16 | v << 8 | v);
        } else {
            band.cx = g.d.sx(cx);
            band.cy = g.d.sy(cy);
            band.r0 = r0 * g.d.u;
            band.r1 = r1 * g.d.u;
            band.c0 = Colors.withAlpha(it.inner, 0.9f);
            band.c1 = Colors.withAlpha(it.outer, 0.92f);
            g.fillBetween(sIx, sIy, sOx, sOy, n + 1, band);
        }
        for (int pass = 0; pass < 2; pass++) {
            liveParticles(g, it, cx, cy, r0, r1, bx0, by0, bw, bh, bright, pass == 0);
        }
        // Its rim, a halo spilling out while pointed at, and the theme in use breathing in the accent.
        for (int j = 0; j <= n; j++) {
            sPx[j] = sOx[j];
            sPy[j] = sOy[j];
            sPx[n + 1 + j] = sIx[n - j];
            sPy[n + 1 + j] = sIy[n - j];
        }
        int edge = it.current ? tk.accent : Colors.mix(0x40FFFFFF, Colors.withAlpha(light.lightRgb, 0.95f), k);
        g.strokePath(sPx, sPy, (n + 1) * 2, true, (it.current || k > 0.5f ? 1.5f : 1f) * g.d.u, g.flat(edge));
        float ex = CX + (r1 + lift) * (float) Math.cos(mid), ey = CY + (r1 + lift) * (float) Math.sin(mid);
        if (k > 0.01f) {
            g.glowSprite(ex, ey, 52f, 52f, Colors.withAlpha(light.lightRgb, 0.24f * k));
        }
        if (it.current) {
            float breathe = calm() ? 0.5f : 0.5f + 0.5f * (float) Math.sin(wheelT * 1.8f);
            g.glowSprite(ex, ey, 46f, 46f, Colors.withAlpha(tk.glow, 0.1f + 0.08f * breathe));
        }
        // The name over a soft shade, so it reads on the brightest sky.
        float rm = (r0 + r1) / 2f;
        float x = cx + rm * (float) Math.cos(mid), y = cy + rm * (float) Math.sin(mid);
        float room = Math.min(110f, (float) Math.toRadians(it.a1 - it.a0) * rm - 8f);
        g.radial(x, y, Math.min(46f, room / 2f + 10f), 18f, g.radialA.set(0f, 0f, 1f).stop(0f, 0x73000000).stop(0.55f, 0x40000000)
                .stop(1f, 0x00000000), 20);
        boolean shadow = g.shadow;
        g.shadow = true;
        lines(g, font, it.title, x, y, room, it.current || k > 0.5f ? 0xFFFFFFFF : 0xE6FFFFFF);
        g.shadow = shadow;
    }

    /** A number in [0, 1) made up from two others: a particle's fixed traits, from its theme and its number. */
    private static float hash01(int a, int b) {
        int h = a * 0x9E3779B1 + b * 0x85EBCA77;
        h ^= h >>> 15;
        h *= 0x2C1B3C6D;
        h ^= h >>> 12;
        h *= 0x297A2D39;
        h ^= h >>> 15;
        return (h >>> 8) / 16777216f;
    }

    private static float frac(float x) {
        return x - (float) Math.floor(x);
    }

    /**
     * The theme's particles over its sector, moving as they do in its sky: embers rising and flickering, fireflies
     * wandering low and blinking, petals and leaves fluttering down, snow and ash drifting, rain streaking, dust
     * hanging, sparkles twinkling; night themes add a few twinkling stars. Only what falls inside the sector shows,
     * fading at its edges. {@code glowing}: this pass draws the glowing ones (added light), else the solid ones.
     */
    private void liveParticles(Gfx g, Item it, float cx, float cy, float r0, float r1, float bx, float by, float bw,
                               float bh, float bright, boolean glowing) {
        ThemeSpec th = it.sky.base();
        List<ThemeSpec.Emitter> emitters = th.particles();
        int total = 0;
        for (ThemeSpec.Emitter e : emitters) {
            total += e.count();
        }
        int seed = it.sky.id().hashCode();
        float time = calm() ? 0f : wheelT, sizeK = bh / 140f, half = (it.a1 - it.a0) / 2f;
        float tau = (float) (Math.PI * 2);
        int k = 0;
        for (int ei = 0; ei <= emitters.size(); ei++) {
            boolean stars = ei == emitters.size();
            if (stars && !"night".equals(th.show().phase())) {
                break;
            }
            ThemeSpec.Emitter e = stars ? null : emitters.get(ei);
            String type = stars ? "star" : e.type();
            int count = stars ? 5 : total == 0 ? 0 : Math.max(2, Math.round(Math.min(14f, Math.max(5f, total * 0.12f)) * e.count() / total));
            for (int j = 0; j < count; j++, k++) {
                float q1 = hash01(seed, k * 5), q2 = hash01(seed, k * 5 + 1), q3 = hash01(seed, k * 5 + 2);
                float q4 = hash01(seed, k * 5 + 3), q5 = hash01(seed, k * 5 + 4);
                float u, v, a = 1f;
                boolean glow = true, streak = false;
                switch (type) {
                    case "ember" -> {
                        v = 1f - frac(q2 + time * 0.07f * (0.6f + q3));
                        u = q1 + 0.04f * (float) Math.sin(time * 1.3f + q4 * tau);
                        a = 0.55f + 0.45f * (float) Math.sin(time * 6f + q5 * tau);
                    }
                    case "firefly" -> {
                        u = q1 + 0.06f * (float) Math.sin(time * 0.5f + q4 * tau);
                        v = 0.58f + 0.36f * q2 + 0.04f * (float) Math.cos(time * 0.7f + q5 * tau);
                        float b = Math.max(0f, (float) Math.sin(time * 1.6f + q3 * tau));
                        a = b * b * b;
                    }
                    case "rain" -> {
                        v = frac(q2 + time * 0.9f * (0.8f + q3));
                        u = q1 - v * 0.05f;
                        glow = false;
                        streak = true;
                        a = 0.55f;
                    }
                    case "petal", "blossom", "leaf" -> {
                        v = frac(q2 + time * 0.06f * (0.6f + q3));
                        u = q1 + 0.05f * (float) Math.sin(time * 1.1f + q4 * tau) + time * 0.012f;
                        a = 0.65f + 0.35f * Math.abs((float) Math.cos(time * 2f + q5 * tau));
                        glow = false;
                    }
                    case "snow", "flake", "ash" -> {
                        v = frac(q2 + time * 0.045f * (0.6f + q3));
                        u = q1 + 0.03f * (float) Math.sin(time * 0.9f + q4 * tau);
                        glow = "flake".equals(type);
                        a = 0.85f;
                    }
                    case "dust" -> {
                        u = q1 + 0.03f * (float) Math.sin(time * 0.3f + q4 * tau);
                        v = q2 + 0.03f * (float) Math.cos(time * 0.25f + q5 * tau);
                        a = 0.5f + 0.3f * (float) Math.sin(time * 1.2f + q3 * tau);
                        glow = false;
                    }
                    case "star" -> {
                        u = q1;
                        v = q2 * 0.5f;
                        float tw = 0.5f + 0.5f * (float) Math.sin(time * (1.5f + q3 * 2f) + q5 * tau);
                        a = tw * tw;
                    }
                    default -> {
                        u = q1;
                        v = q2 * 0.9f;
                        float tw = 0.5f + 0.5f * (float) Math.sin(time * 2.2f + q5 * tau);
                        a = tw * tw;
                    }
                }
                if (glow != glowing || a <= 0.02f) {
                    continue;
                }
                float x = bx + frac(u) * bw, y = by + Math.max(0f, Math.min(1f, v)) * bh;
                float dx = x - cx, dy = y - cy, d = (float) Math.hypot(dx, dy);
                float off = Math.abs(QuickLight.norm((float) Math.toDegrees(Math.atan2(dy, dx)) - it.mid()));
                float fade = Math.min(Math.min(d - r0 - 2f, r1 - 2f - d) / 6f, (half - 1f - off) / 3f);
                if (fade <= 0f) {
                    continue;
                }
                a *= Math.min(1f, fade) * (0.45f + 0.55f * bright);
                int c = stars ? 0xFFFFFFFF : e.colors()[Math.min(e.colors().length - 1, (int) (q3 * e.colors().length))];
                float size = stars ? 1.1f
                        : Math.max(0.8f, Math.min(3.2f, (e.minSize() + (e.maxSize() - e.minSize()) * q4) * sizeK));
                if (glow) {
                    g.glowSprite(x, y, size * 2.8f, size * 2.8f, Colors.withAlpha(c, Math.min(1f, a)));
                } else if (streak) {
                    g.line(x, y, x - 1.2f, y + 6f * sizeK + 3f, Math.max(1f, g.d.u * 0.8f), Colors.withAlpha(c, Math.min(1f, a)));
                } else {
                    g.disc(x, y, size * 0.6f, g.flat(Colors.withAlpha(c, Math.min(1f, a))));
                }
            }
        }
    }

    /** Under a wheel: the preset pointed at as a picture, or the emote wheel's pages. */
    private void wheelExtras(Gfx g, Tokens tk) {
        if (wheel == Wheel.PRESETS) {
            Item hot = wheelHot >= 0 ? items.get(wheelHot) : null;
            ThemeRegistry.Entry e = hot != null && hot.theme != null ? hot.theme : null;
            float w = 146f, h = 60f, x = CX - w / 2f, y = labelY() + 32f;
            if (e != null && y + h <= COLOURS_Y - Widgets.Chip.H / 2f - 8f) {
                SolTexture thumb = thumbs.get(e);
                g.round(x, y, w, h, 10f, tk.panel);
                if (thumb != null) {
                    g.clip(x, y, x + w, y + h);
                    g.image(thumb, x, y, w, h, 0f, 0f, 1f, 1f, 0xFFFFFFFF, false);
                    g.endClip();
                }
                g.stroke(x, y, w, h, 10f, Math.max(1f, g.d.u), 0x33FFFFFF);
            }
            colours(g, tk);
            return;
        }
        int pages = Emotes.pages(elytraWheel);
        boolean empty = true;
        for (int i = 0; i < CosmeticsConfig.WHEEL_SLOTS; i++) {
            empty &= Emotes.at(elytraWheel, emotePage * CosmeticsConfig.WHEEL_SLOTS + i) == null;
        }
        List<Widgets.Chip> chips = new ArrayList<>();
        if (pages > 1) {
            chips.add(Widgets.Chip.button("‹", () -> turnPage(-1)));
            chips.add(Widgets.Chip.button(tr("sol.quick.wheel.page", emotePage + 1, pages), () -> turnPage(1)));
            chips.add(Widgets.Chip.button("›", () -> turnPage(1)));
        }
        chips.add(Widgets.Chip.button(tr("sol.quick.wheel.add_page"), () -> {
            if (Emotes.addPage(elytraWheel)) {
                emotePage = Emotes.pages(elytraWheel) - 1;
                CosmeticsConfig.save();
            }
        }));
        if (pages > 1 && empty) {
            chips.add(Widgets.Chip.button(tr("sol.quick.wheel.remove_page"), () -> {
                Emotes.removePage(elytraWheel, emotePage);
                emotePage = Math.max(0, emotePage - 1);
                CosmeticsConfig.save();
            }));
        }
        chips.add(Widgets.Chip.toggle(tr("sol.quick.wheel.elytra"), elytraWheel, () -> {
            elytraWheel = !elytraWheel;
            emotePage = 0;
        }));
        if (EmotePlayer.playing() != null) {
            chips.add(Widgets.Chip.button(tr("sol.quick.wheel.stop"), EmotePlayer::stop));
        }
        rowOfChips(g, tk, chips, labelY() + 40f);
    }

    /** The accent colours the presets wheel offers along the bottom, after the theme's own and the song's. */
    private static final int[] COLOURS = {0xFFFF4D4D, 0xFFFF8A3C, 0xFFFFD21F, 0xFF9BE15D, 0xFF2FD47A, 0xFF2FD4C8, 0xFF4DA8FF,
            0xFF5B6CFF, 0xFFA06BFF, 0xFFFF5FC8, 0xFFF2F2F2};
    /** Where that row's middle sits (design pixels). */
    private static final float COLOURS_Y = 684f;

    /** The accent picker's panel, right of the presets wheel and just above the colour row (design pixels). */
    private static final float PICK_X = 880f, PICK_W = 388f, PICK_H = ColorPicker.HEIGHT + 42f,
            PICK_Y = COLOURS_Y - Widgets.Chip.H / 2f - 10f - PICK_H;

    /**
     * Along the bottom of the presets wheel: the colours. Default is the theme's own, Music the playing song's (the
     * album colours, as on the Music page), then a row of colours, any of which becomes your accent, and last the
     * picker for any other colour. Every colour in the client that follows the theme changes with it, live.
     */
    private void colours(Gfx g, Tokens tk) {
        var music = io.github.georgwav.cosmetics.sol.config.SolConfig.get().music;
        Integer accent = ThemeState.accentOverride();
        Widgets.Chip own = Widgets.Chip.toggle(tr("sol.quick.wheel.colour_default"), accent == null && !music.albumUi, () -> {
            music.albumUi = false;
            SolProfiles.setAccent(null);
            io.github.georgwav.cosmetics.sol.config.SolConfig.save();
        });
        Widgets.Chip song = Widgets.Chip.toggle(tr("sol.quick.wheel.colour_music"), accent == null && music.albumUi, () -> {
            music.albumUi = true;
            SolProfiles.setAccent(null);
            io.github.georgwav.cosmetics.sol.config.SolConfig.save();
        });
        float r = 11f, gap = 8f, step = r * 2f + 7f;
        float total = own.width(g) + gap + song.width(g) + gap * 2f + (COLOURS.length + 1) * step - 7f;
        float x = CX - total / 2f, top = COLOURS_Y - Widgets.Chip.H / 2f;
        for (Widgets.Chip c : new Widgets.Chip[]{own, song}) {
            c.draw(g, tk, x, top, false, mx, my);
            hits.add(new Hit(x, top, c.width(g), Widgets.Chip.H, c.action()));
            x += c.width(g) + gap;
        }
        x += gap;
        boolean listed = false;
        for (int colour : COLOURS) {
            float cx = x + r;
            boolean picked = accent != null && (accent & 0xFFFFFF) == (colour & 0xFFFFFF);
            listed |= picked;
            boolean over = Math.hypot(mx - cx, my - COLOURS_Y) <= r + 2f;
            g.disc(cx, COLOURS_Y, r, g.flat(colour));
            if (picked || over) {
                g.ring(cx, COLOURS_Y, r + 3f, Math.max(1f, 1.5f * g.d.u), picked ? 0xFFFFFFFF : 0x80FFFFFF);
            }
            hits.add(new Hit(cx - r - 2f, COLOURS_Y - r - 2f, r * 2f + 4f, r * 2f + 4f, () -> {
                music.albumUi = false;
                SolProfiles.setAccent(colour);
                io.github.georgwav.cosmetics.sol.config.SolConfig.save();
            }));
            x += step;
        }
        // Any colour: a hue wheel, with your own colour in the middle once you've picked one that isn't listed.
        float cx = x + r, scx = g.d.sx(cx), scy = g.d.sy(COLOURS_Y), sr = r * g.d.u;
        for (int k = 0; k < 12; k++) {
            // In screen pixels, as fillConvex takes them.
            double a0 = Math.toRadians(k * 30.0 - 90.0), a1 = Math.toRadians((k + 1) * 30.0 - 90.0);
            wedgeX[0] = scx;
            wedgeY[0] = scy;
            wedgeX[1] = scx + sr * (float) Math.cos(a0);
            wedgeY[1] = scy + sr * (float) Math.sin(a0);
            wedgeX[2] = scx + sr * (float) Math.cos(a1);
            wedgeY[2] = scy + sr * (float) Math.sin(a1);
            g.fillConvex(wedgeX, wedgeY, 3, g.flat(0xFF000000 | io.github.georgwav.cosmetics.render.Tint.hsvToRgb((k + 0.5f) / 12f, 0.85f, 1f)));
        }
        boolean custom = accent != null && !listed;
        g.disc(cx, COLOURS_Y, r * 0.55f, g.flat(custom ? 0xFF000000 | accent : tk.panel));
        if (!custom) {
            g.line(cx - 3.5f, COLOURS_Y, cx + 3.5f, COLOURS_Y, Math.max(1f, 1.5f * g.d.u), 0xFFFFFFFF);
            g.line(cx, COLOURS_Y - 3.5f, cx, COLOURS_Y + 3.5f, Math.max(1f, 1.5f * g.d.u), 0xFFFFFFFF);
        }
        boolean overCustom = Math.hypot(mx - cx, my - COLOURS_Y) <= r + 2f;
        if (custom || overCustom || accentPicker != null) {
            g.ring(cx, COLOURS_Y, r + 3f, Math.max(1f, 1.5f * g.d.u),
                    custom || accentPicker != null ? 0xFFFFFFFF : 0x80FFFFFF);
        }
        hits.add(new Hit(cx - r - 2f, COLOURS_Y - r - 2f, r * 2f + 4f, r * 2f + 4f, () -> {
            accentPicker = accentPicker == null ? newAccentPicker() : null;
        }));
        if (accentPicker != null) {
            accentPanel(g, tk);
        }
    }

    private final float[] wedgeX = new float[3], wedgeY = new float[3];

    /** The picker: it shows the accent in use and sets your own, live, as it's dragged. */
    private ColorPicker newAccentPicker() {
        return new ColorPicker(() -> {
            Integer a = ThemeState.accentOverride();
            return a != null ? a : ThemeState.tokens().accent;
        }, rgb -> {
            var music = io.github.georgwav.cosmetics.sol.config.SolConfig.get().music;
            if (music.albumUi) {
                music.albumUi = false;
                io.github.georgwav.cosmetics.sol.config.SolConfig.save();
            }
            SolProfiles.setAccent(0xFF000000 | rgb);
        });
    }

    private void accentPanel(Gfx g, Tokens tk) {
        float px = PICK_X, py = PICK_Y, pw = PICK_W, ph = PICK_H;
        g.glow(px, py, pw, ph, 16f, 28f, Colors.withAlpha(tk.glow, 0.14f));
        g.round(px, py, pw, ph, 16f, Colors.withAlpha(tk.panel, 0.94f));
        g.stroke(px, py, pw, ph, 16f, Math.max(1f, g.d.u), tk.rim);
        g.highlight(px, py, pw, ph, 16f, 0x2EFFFFFF);
        ui.area(px, py, pw, ph, () -> { });
        SolFont h = g.font(SolFont.Family.SEMIBOLD, 13f), small = g.font(SolFont.Family.MEDIUM, 11f);
        g.textMid(h, tr("sol.quick.wheel.colour_pick"), px + 14f, py + 18f, 0xFFFFFFFF);
        g.textRight(small, tr("sol.quick.wheel.colour_pick_hint"), px + pw - 14f, py + 18f, Ui.text(true));
        List<Integer> presets = new ArrayList<>(COLOURS.length);
        for (int c : COLOURS) {
            presets.add(c & 0xFFFFFF);
        }
        accentPicker.draw(g, ui, tk, px + 14f, py + 32f, pw - 28f, presets, tr("sol.quick.wheel.colour_default"), () -> {
            io.github.georgwav.cosmetics.sol.config.SolConfig.get().music.albumUi = false;
            SolProfiles.setAccent(null);
            io.github.georgwav.cosmetics.sol.config.SolConfig.save();
        }, dt);
    }

    private boolean overAccentPanel(float x, float y) {
        return accentPicker != null && x >= PICK_X && x <= PICK_X + PICK_W && y >= PICK_Y && y <= PICK_Y + PICK_H;
    }

    private void turnPage(int by) {
        emotePage = Math.floorMod(emotePage + by, Emotes.pages(elytraWheel));
    }

    /** Where a wheel's label goes: just under its outermost ring. */
    private float labelY() {
        float outer = wheel == Wheel.EMOTES ? EB : W1B;
        for (Item it : items) {
            outer = Math.max(outer, it.r1);
        }
        return CY + outer + 24f;
    }

    /** A plus sign centred on (x, y). */
    private static void plus(Gfx g, float x, float y, float size, int argb) {
        float bar = Math.max(2f, size / 6f);
        g.round(x - size / 2f, y - bar / 2f, size, bar, bar / 2f, argb);
        g.round(x - bar / 2f, y - size / 2f, bar, size, bar / 2f, argb);
    }

    /** The wheel item under a design point, or -1. */
    private int itemAt(float x, float y) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).contains(x, y)) {
                return i;
            }
        }
        return -1;
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
        if (picker != null) {
            return;
        }
        if (wheel != Wheel.NONE) {
            int i = over ? -1 : itemAt(x, y);
            // The slot's own buttons sit just outside it: pointing at them keeps it pointed at.
            if (i >= 0 || !over && !overHit(x, y)) {
                wheelHot = i;
            }
            return;
        }
        if (!over) {
            Place p = pick(x, y);
            if (p != null && !p.id().equals(hot)) {
                setHot(p.id());
            }
        }
    }

    private boolean overHit(float x, float y) {
        for (Hit h : hits) {
            if (h.contains(x, y)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected boolean press(float x, float y, int button) {
        if (expand >= 0f) {
            return false;
        }
        if (picker != null) {
            return ui.press(x, y);
        }
        if (accentPicker != null) {
            accentPicker.hexField().focus(false);
            if (overAccentPanel(x, y)) {
                return ui.press(x, y);
            }
            boolean onRow = false;
            for (Hit h : hits) {
                onRow |= h.contains(x, y) && h.y() >= COLOURS_Y - Widgets.Chip.H;
            }
            if (!onRow) {
                // A click anywhere else shuts the picker first.
                accentPicker = null;
                return true;
            }
        }
        if (wheel != Wheel.NONE && button == 1) {
            int i = itemAt(x, y);
            if (i >= 0 && items.get(i).alt != null) {
                items.get(i).alt.run();
                return true;
            }
            return false;
        }
        if (button != 0) {
            return false;
        }
        if (overMe(x, y)) {
            if (wheel != Wheel.NONE) {
                closeWheel();
            } else {
                toggleMe();
            }
            return true;
        }
        for (Hit h : hits) {
            if (h.contains(x, y)) {
                h.action().run();
                return true;
            }
        }
        if (wheel != Wheel.NONE) {
            int i = itemAt(x, y);
            if (i >= 0) {
                flare = 1f;
                items.get(i).action.run();
                return true;
            }
            return false;
        }
        Place p = pick(x, y);
        if (p != null) {
            choose(p.id());
            return true;
        }
        return false;
    }

    @Override
    protected boolean release(float x, float y, int button) {
        return (picker != null || accentPicker != null) && ui.release();
    }

    @Override
    protected boolean drag(float x, float y, int button) {
        return (picker != null || accentPicker != null) && ui.dragTo(x, y);
    }

    @Override
    protected boolean scroll(float x, float y, float amount) {
        if (picker != null) {
            picker.scroll(amount);
            return true;
        }
        if (wheel == Wheel.EMOTES) {
            if (amount != 0f && Emotes.pages(elytraWheel) > 1) {
                turnPage(amount > 0 ? -1 : 1);
            }
            return true;
        }
        if (wheel != Wheel.NONE) {
            return false;
        }
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
        if (picker != null) {
            return false;
        }
        if (accentPicker != null && accentPicker.key(key, ctrl)) {
            return true;
        }
        if (wheel != Wheel.NONE && !items.isEmpty()) {
            switch (key) {
                case InputConstants.KEY_LEFT, InputConstants.KEY_RIGHT, InputConstants.KEY_TAB -> {
                    boolean back = key == InputConstants.KEY_LEFT || key == InputConstants.KEY_TAB && shift;
                    wheelHot = wheelHot < 0 ? 0 : Math.floorMod(wheelHot + (back ? -1 : 1), items.size());
                    return true;
                }
                case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER, InputConstants.KEY_SPACE -> {
                    if (wheelHot >= 0) {
                        items.get(wheelHot).action.run();
                    } else {
                        closeWheel();
                    }
                    return true;
                }
                default -> {
                    return false;
                }
            }
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
        if (picker != null) {
            picker = null;
            return true;
        }
        if (accentPicker != null) {
            accentPicker = null;
            return true;
        }
        if (wheel != Wheel.NONE) {
            closeWheel();
            return true;
        }
        if (meRing) {
            toggleMe();
            meFresh = false;
            return true;
        }
        return false;
    }

    @Override
    protected boolean typed(String text) {
        if (accentPicker != null) {
            return accentPicker.typed(text);
        }
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
        skies.release();
        pictures.close();
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
