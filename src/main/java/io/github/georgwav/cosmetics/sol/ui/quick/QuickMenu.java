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
import io.github.georgwav.cosmetics.sol.ui.kit.LogoRenderer;
import io.github.georgwav.cosmetics.sol.ui.kit.Notifications;
import io.github.georgwav.cosmetics.sol.ui.kit.Pix;
import io.github.georgwav.cosmetics.sol.ui.kit.PixelIcons;
import io.github.georgwav.cosmetics.sol.ui.kit.PixelTokens;
import io.github.georgwav.cosmetics.sol.ui.kit.PlayerFace;
import io.github.georgwav.cosmetics.sol.ui.kit.SolFont;
import io.github.georgwav.cosmetics.sol.ui.kit.SolScreen;
import io.github.georgwav.cosmetics.sol.ui.kit.Ui;
import io.github.georgwav.cosmetics.sol.ui.kit.Widgets;
import io.github.georgwav.cosmetics.sol.ui.menu.SolMenu;
import io.github.georgwav.cosmetics.sol.ui.screens.EmotePicker;
import io.github.georgwav.cosmetics.sol.util.Colors;
import io.github.georgwav.cosmetics.sol.util.Mulberry32;
import io.github.georgwav.cosmetics.sol.util.Spring;
import io.github.georgwav.cosmetics.ui.EmotePictures;
import io.github.georgwav.cosmetics.ui.KeyBinds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The quick menu (Right Shift), in Sol's pixel style: you in the middle, the six sections round you on a sundial and
 * your pinned modules underneath. The sun follows what you point at round the dial and lights the world behind (noon
 * at the top, dawn and dusk at the sides, night at the bottom, the moon below the horizon). Click yourself and the sun
 * slides behind your head for the eclipse, with your own ring: Presets, Emotes and Wardrobe; Presets and Emotes open
 * their wheels round you in place (the presets on rings, the emote wheel with its pages). A port of the pixel UI
 * prototype's quick menu: the wheel is drawn pixel by pixel ({@link PixelWheel}) at 2 design pixels per art pixel,
 * its labels fitted inside their segments ({@link WheelLabels}), a top bar with the logo, tabs, search, you and the
 * red close, and the tray along the bottom.
 */
public final class QuickMenu extends SolScreen {
    /** Frames drawn (the in-game check). */
    public static int framesRendered;
    /**
     * The menu's own drawing time and allocation for the last {@link #SAMPLES} frames (the speed checks read their
     * medians, so the first frame's texture work doesn't count as a steady frame's cost).
     */
    public static final int SAMPLES = 128;
    public static final long[] frameNanos = new long[SAMPLES], frameBytes = new long[SAMPLES];
    /** Whether the moon showed on the dial in the last frame (the moon check). */
    public static boolean moonShown;
    private static final com.sun.management.ThreadMXBean THREADS =
            java.lang.management.ManagementFactory.getThreadMXBean() instanceof com.sun.management.ThreadMXBean t ? t : null;
    /** Labels that didn't fit their segment in the last fit (the in-game check: there must be none). */
    public static int labelsOutside;

    /** The wheel's middle (design pixels). */
    static final float CX = 640f, CY = 330f;

    /** One place on a ring: its id, its icon and its angle on the dial (degrees, 0 = right, clockwise). */
    record Place(String id, String icon, float angle) {
    }

    static final Place[] MAIN = {new Place("hud", "hud", -150f), new Place("modules", "grid", -90f),
            new Place("world", "mount", -30f), new Place("music", "note", 30f), new Place("settings", "gear", 90f),
            new Place("mods", "box", 150f)};
    static final Place[] ME = {new Place("presets", "sun", -90f), new Place("emotes", "run", 30f),
            new Place("wardrobe", "shirt", 150f)};

    /** A wheel open round you inside your ring. */
    enum Wheel { NONE, PRESETS, EMOTES }

    static final float MAIN_A = PixelWheel.MAIN_A, MAIN_B = PixelWheel.MAIN_B, ME_B = PixelWheel.ME_B,
            P1A = PixelWheel.P1A, P1B = PixelWheel.P1B, P2A = PixelWheel.P2A, P2B = PixelWheel.P2B, P3A = PixelWheel.P3A,
            P3B = PixelWheel.P3B, EM_A = PixelWheel.EM_A, EM_B = PixelWheel.EM_B;
    /** Your own presets per ring before the next one starts. */
    static final int OWN_PER_RING = 10;
    /** Where the tray starts. */
    static final float TRAY_Y = 614f;

    private boolean meRing;
    private String hot;
    private String mainHot;
    private boolean meHover, meFocus, meFresh;
    private final Spring ang = new Spring(Spring.QUICK_SUN, -90f);
    private final Spring ecl = new Spring(Spring.ECLIPSE, 0f);
    private float openT;
    private float clock;
    private final float[] starX = new float[84], starY = new float[84], starPh = new float[84];
    private final int[] starC = new int[84];
    private final boolean[] starBig = new boolean[84];
    /** Keyboard focus: -1 none, 0 you, 1.. the ring's places, then the tray's items, then the bar's. */
    private int focus = -1;
    private final List<Hit> hits = new ArrayList<>();
    /** The tray's width (design pixels, centred). */
    private static final float TRAY_W = 1216f;
    /** The first preset card the tray shows (whole cards, paged), or -1 to start round the one in use. */
    private int presetFirst = -1;
    private final PresetThumbs thumbs = new PresetThumbs(53, 22, false);
    /** For the dev scenario: the theme whose sector stays pointed at, or null. */
    private String stagedHot;
    private Wheel wheel = Wheel.NONE;
    /** The wheel's items (built when what it shows changes), and the one pointed at (-1 none). */
    private final List<Item> items = new ArrayList<>();
    private final List<PixelWheel.Ring> rings = new ArrayList<>();
    private int wheelHot = -1;
    private boolean elytraWheel;
    private int emotePage;
    private EmotePicker picker;
    /** Under the presets wheel: the picker for any accent colour, open from the colour row's last swatch (null while shut). */
    private ColorPicker accentPicker;
    private final EmotePictures pictures = new EmotePictures();
    private final Ui ui = new Ui();
    private final LogoRenderer logo = new LogoRenderer(false);
    private final PixelWheel pixels = new PixelWheel();
    private SolTexture wheelTex;
    private long drawnKey = Long.MIN_VALUE;
    private int layoutKey = Integer.MIN_VALUE;
    private float labelsU = -1f;
    private final List<WheelLabels.Label> labels = new ArrayList<>();
    /** What the rings show while they change (they shrink into you, swap, grow out over 0.3 s in 6 steps). */
    private boolean viewMe;
    private Wheel viewWheel = Wheel.NONE;
    private float swT = -1f;
    // The wheel on screen: screen pixels per art pixel, its middle in design pixels, design pixels per art pixel.
    private int wk = 2;
    private float wcx = CX, wcy = CY, ud = 2f;

    /** One segment on the wheel: its ring (art px), its middle and half span (degrees) and what it does. */
    private static final class Item {
        int ring, index;
        float r0, r1, mid, half;
        String title, sub, icon, id;
        boolean current;
        ThemeRegistry.Entry theme;
        Emote emote;
        int slot = -1;
        Runnable action, alt;
        PixelWheel.Seg seg = new PixelWheel.Seg();
    }

    /** A clickable thing drawn this frame (tray items, the bar), for the pointer and the keyboard. */
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
            starX[i] = Math.round(r.next() * 638) * 2f;
            starY[i] = Math.round(32 + r.next() * 235) * 2f;
            starBig[i] = r.next() < 0.16;
            starC[i] = cols[(int) (r.next() * 4)];
            starPh[i] = (float) (r.next() * 6.0);
        }
        this.meRing = meRing;
        this.viewMe = meRing;
        this.mainHot = hot != null && find(MAIN, hot) != null ? hot : "modules";
        setRing(hot);
        ang.snap(target(mainHot));
    }

    /**
     * For the dev scenario: one of the states the prototype's reference shots show. A main section's id points at
     * it; "eclipse" points at you; "presets" and "emotes" open their wheels in your ring ("emote_picker" with its
     * picker, "presets_colour" with the colour picker); "back" points at you from your ring; "night" points at the
     * bottom of the dial (the moon), "dusk" at its side.
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
            case "night" -> setHot("settings");
            case "dusk" -> setHot("world");
            default -> setHot(state);
        }
        viewMe = meRing;
        viewWheel = wheel;
        swT = -1f;
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
        Place[] list = places();
        String h = wanted != null && find(list, wanted) != null ? wanted : meRing ? list[0].id() : mainHot;
        setHot(h);
    }

    private void setHot(String id) {
        Place p = find(places(), id);
        if (p == null) {
            return;
        }
        hot = id;
        if (!meRing) {
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
            SolMenu.open(parent, id, true);
            return;
        }
        switch (id) {
            case "wardrobe" -> SolMenu.open(parent, "wardrobe", true);
            case "emotes" -> openWheel(Wheel.EMOTES);
            default -> openWheel(Wheel.PRESETS);
        }
    }

    private void openWheel(Wheel w) {
        wheel = w;
        wheelHot = -1;
        focus = -1;
        emotePage = Math.max(0, Math.min(emotePage, Emotes.pages(elytraWheel) - 1));
    }

    /** Back from a wheel to your three places. */
    private void closeWheel() {
        wheel = Wheel.NONE;
        picker = null;
        accentPicker = null;
        wheelHot = -1;
        meFresh = true;
    }

    private void toggleMe() {
        meFresh = true;
        meFocus = false;
        if (meRing) {
            meRing = false;
            wheel = Wheel.NONE;
            picker = null;
            accentPicker = null;
            setRing(mainHot);
        } else {
            meRing = true;
            presetFirst = -1;
            setRing(null);
        }
    }

    // ------------------------------------------------------------------ the wheel's layout

    /** A number that changes whenever what the wheel shows changes. */
    private int layoutKey() {
        int h = (viewMe ? 1 : 0) + viewWheel.ordinal() * 3;
        h = h * 31 + emotePage;
        h = h * 31 + (elytraWheel ? 1 : 0);
        h = h * 31 + ThemeRegistry.generation();
        h = h * 31 + ThemeState.themeId().hashCode();
        h = h * 31 + SolProfiles.active().hashCode();
        h = h * 31 + SolProfiles.names().size();
        if (viewWheel == Wheel.EMOTES) {
            for (int i = 0; i < CosmeticsConfig.WHEEL_SLOTS; i++) {
                Emote e = Emotes.at(elytraWheel, emotePage * CosmeticsConfig.WHEEL_SLOTS + i);
                h = h * 31 + (e == null ? 0 : e.id().hashCode());
            }
            Emote playing = EmotePlayer.playing();
            h = h * 31 + (playing == null ? 0 : playing.id().hashCode());
        }
        return h;
    }

    /** The rings and their segments for what the wheel shows now. */
    private void buildLayout() {
        items.clear();
        rings.clear();
        if (viewWheel == Wheel.PRESETS) {
            List<ThemeRegistry.Entry> builtIn = new ArrayList<>();
            List<Item> own = new ArrayList<>();
            String current = ThemeState.themeId();
            for (ThemeRegistry.Entry e : ThemeRegistry.all()) {
                if (e.builtIn()) {
                    builtIn.add(e);
                } else {
                    Item it = themeItem(e, current, true);
                    it.sub = tr("sol.quick.wheel.pack_sub");
                    own.add(it);
                }
            }
            PixelWheel.Ring r1 = ring(P1A, P1B);
            for (ThemeRegistry.Entry e : builtIn) {
                add(r1, themeItem(e, current, false));
            }
            String active = SolProfiles.active();
            for (String name : SolProfiles.names()) {
                Item it = new Item();
                ThemeRegistry.Entry t = ThemeRegistry.get(SolProfiles.themeOf(name));
                it.title = name;
                it.sub = tr("sol.quick.wheel.profile_sub", Widgets.themeName(t));
                ThemeSpec.Sky k = t != null ? t.base().sky() : null;
                if (k != null) {
                    PixelWheel.skyTones(it.seg, k.top(), k.horizon(), k.glow(), true);
                } else {
                    it.seg.kind = PixelWheel.SAVE;
                }
                it.current = name.equals(active);
                it.seg.current = it.current;
                it.theme = t;
                it.action = () -> SolProfiles.switchTo(name);
                own.add(it);
            }
            Item save = new Item();
            save.title = tr("sol.quick.wheel.save");
            save.sub = tr("sol.quick.wheel.save_sub");
            save.icon = "plus";
            save.seg.kind = PixelWheel.SAVE;
            save.action = () -> {
                String saved = SolProfiles.saveAs(SolProfiles.newName());
                if (saved != null) {
                    Notifications.post(tr("sol.notify.profile_saved", saved));
                }
            };
            own.add(save);
            int second = own.size() > OWN_PER_RING + 2 ? OWN_PER_RING : own.size();
            PixelWheel.Ring r2 = ring(P2A, P2B);
            PixelWheel.Ring r3 = second < own.size() ? ring(P3A, P3B) : null;
            for (int i = 0; i < own.size(); i++) {
                add(i < second ? r2 : r3, own.get(i));
            }
        } else if (viewWheel == Wheel.EMOTES) {
            PixelWheel.Ring r = ring(EM_A, EM_B);
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
                it.icon = e == null ? "plus" : null;
                it.current = e != null && e == playing;
                it.seg.current = it.current;
                it.alt = () -> picker = new EmotePicker(elytraWheel, slot);
                it.action = e == null ? it.alt : () -> {
                    EmotePlayer.play(e, elytraWheel);
                    onClose();
                };
                add(r, it);
            }
        } else {
            Place[] list = viewMe ? ME : MAIN;
            PixelWheel.Ring r = new PixelWheel.Ring(MAIN_A, viewMe ? ME_B : MAIN_B, list[0].angle());
            rings.add(r);
            for (Place p : list) {
                Item it = new Item();
                it.id = p.id();
                it.icon = p.icon();
                it.title = tr("sol.section." + p.id());
                add(r, it);
            }
        }
        // Each item's place once its ring is full.
        for (Item it : items) {
            PixelWheel.Ring r = rings.get(it.ring);
            it.r0 = r.r0;
            it.r1 = r.r1;
            it.mid = r.mid(it.index);
            it.half = r.span() / 2f;
        }
        labelsU = -1f;
    }

    private PixelWheel.Ring ring(float r0, float r1) {
        PixelWheel.Ring r = new PixelWheel.Ring(r0, r1, -90f);
        rings.add(r);
        return r;
    }

    private void add(PixelWheel.Ring r, Item it) {
        it.ring = rings.indexOf(r);
        it.index = r.segs.size();
        r.segs.add(it.seg);
        items.add(it);
    }

    private Item themeItem(ThemeRegistry.Entry e, String current, boolean own) {
        Item it = new Item();
        it.theme = e;
        it.title = Widgets.themeName(e);
        String line = Widgets.themeLine(e);
        it.sub = line == null || line.isBlank() ? tr("sol.quick.wheel.preset_sub") : line;
        ThemeSpec.Sky k = e.base().sky();
        PixelWheel.skyTones(it.seg, k.top(), k.horizon(), k.glow(), own);
        it.current = e.id().equals(current);
        it.seg.current = it.current;
        String id = e.id();
        it.action = () -> applyPreset(id);
        return it;
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

    /** The item pointed at on the main or your ring: the place that is hot. */
    private int placeIndex() {
        Place[] list = viewMe ? ME : MAIN;
        for (int i = 0; i < list.length; i++) {
            if (list[i].id().equals(hot)) {
                return i;
            }
        }
        return 0;
    }

    /** Fits the labels (on a layout or screen-size change): sections' icons and names, emote pictures, the plus. */
    private void fitLabels(Gfx g) {
        labels.clear();
        for (Item it : items) {
            if (it.seg.kind == PixelWheel.SKY) {
                labels.add(null);
                continue;
            }
            WheelLabels.Label l = new WheelLabels.Label(it.r0 * ud, it.r1 * ud, it.mid, it.half,
                    it.id != null ? it.title : null);
            if (it.emote != null) {
                l.picW = 44f;
                l.picH = 44f;
            } else if (it.id == null && it.icon != null) {
                l.text = viewWheel == Wheel.EMOTES ? it.title : null;
                l.bits = PixelIcons.all().get(PixelIcons.resolve(it.icon));
            }
            labels.add(l);
        }
        List<WheelLabels.Label> real = new ArrayList<>();
        for (WheelLabels.Label l : labels) {
            if (l != null) {
                real.add(l);
            }
        }
        WheelLabels.Measure m = new WheelLabels.Measure() {
            @Override
            public float width(String text, float size) {
                return g.width(g.font(SolFont.Family.PIXEL_BOLD, size), text);
            }

            @Override
            public float height(float size) {
                return g.font(SolFont.Family.PIXEL_BOLD, size).cap() / g.d.u + 3f;
            }

            @Override
            public float icon(float size) {
                return PixelIcons.CELL * Math.max(1, Math.round(size / PixelIcons.CELL * g.d.u)) / g.d.u;
            }
        };
        WheelLabels.fit(real, m, wcx, wcy, ud / 2f);
        int outside = 0;
        for (WheelLabels.Label l : real) {
            outside += WheelLabels.fits(l.room) ? 0 : 1;
        }
        labelsOutside = outside;
        labelsU = g.d.u;
    }

    // ------------------------------------------------------------------ frame

    @Override
    protected void frame(Gfx g) {
        long t0 = System.nanoTime(), b0 = THREADS != null ? THREADS.getCurrentThreadAllocatedBytes() : 0L;
        draw(g);
        int i = (framesRendered - 1) % SAMPLES;
        frameNanos[i] = System.nanoTime() - t0;
        frameBytes[i] = THREADS != null ? THREADS.getCurrentThreadAllocatedBytes() - b0 : 0L;
    }

    /** The median of the samples taken so far. */
    public static long median(long[] samples) {
        int n = Math.min(framesRendered, SAMPLES);
        if (n == 0) {
            return 0L;
        }
        long[] a = java.util.Arrays.copyOf(samples, n);
        java.util.Arrays.sort(a);
        return a[n / 2];
    }

    private void draw(Gfx g) {
        framesRendered++;
        Tokens tk = ThemeState.tokens();
        PixelTokens p = PixelTokens.get();
        openT += dt;
        clock += calm() ? 0f : dt;
        ui.begin(g, mx, my);
        hits.clear();
        // The rings change: shrink into you, swap, grow back out (6 steps over 0.3 s).
        if (swT < 0f && (viewMe != meRing || viewWheel != wheel)) {
            if (calm()) {
                viewMe = meRing;
                viewWheel = wheel;
            } else {
                swT = 0f;
            }
        }
        if (swT >= 0f) {
            swT += dt / 0.3f;
            if (swT >= 0.5f) {
                viewMe = meRing;
                viewWheel = wheel;
            }
            if (swT >= 1f) {
                swT = -1f;
            }
        }
        float k = swT < 0f ? 1f : Math.round((swT < 0.5f ? 1f - swT * 2f : swT * 2f - 1f) * 6f) / 6f;
        int key = layoutKey();
        if (key != layoutKey) {
            layoutKey = key;
            buildLayout();
        }
        if (stagedHot != null) {
            // The preset itself (on the inner ring), not a profile of your own that uses it.
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i).theme != null && items.get(i).theme.id().equals(stagedHot)) {
                    wheelHot = i;
                    break;
                }
            }
        }
        if (wheelHot >= items.size()) {
            wheelHot = -1;
        }
        // Springs: the sun round the dial, the eclipse.
        ecl.setTarget(eclTarget());
        if (calm() || dt == 0f) {
            ang.snap(ang.target());
            ecl.snap(ecl.target());
        } else {
            ang.stepAngle(dt);
            ecl.step(dt);
        }
        float eclipse = Math.max(0f, Math.min(1f, ecl.value()));
        float sunA = ang.value();
        float sinA = (float) Math.sin(Math.toRadians(sunA));
        boolean moonUp = sinA >= 0.02f && eclipse < 0.02f && eclTarget() == 0f;
        moonShown = moonUp;
        // Where the wheel is on screen: whole screen pixels per art pixel, centred on (640, 330).
        wk = Math.max(1, Math.round(2f * g.d.u));
        float left = Math.round(g.d.sx(CX) - PixelWheel.WC * wk), top = Math.round(g.d.sy(CY) - PixelWheel.WC * wk);
        ud = wk / g.d.u;
        // The world under the menu in the same pixels as the wheel (the next frame's light pass draws it so).
        io.github.georgwav.cosmetics.sol.render.LightPass.pixelate(wk, Math.round(left), Math.round(top));
        wcx = (left + PixelWheel.WC * wk - g.d.ox) / g.d.u;
        wcy = (top + PixelWheel.WC * wk - g.d.oy) / g.d.u;
        if (labelsU != g.d.u) {
            fitLabels(g);
        }

        world(g, p, sunA, sinA, eclipse);
        // The wheel.
        int hotRing = -1, hotSeg = -1;
        if (swT < 0f) {
            if (viewWheel != Wheel.NONE) {
                if (wheelHot >= 0) {
                    hotRing = items.get(wheelHot).ring;
                    hotSeg = items.get(wheelHot).index;
                }
            } else {
                hotRing = 0;
                hotSeg = placeIndex();
            }
        }
        boolean hovMe = meOn();
        boolean dial = viewWheel == Wheel.NONE;
        long dk = layoutKey;
        dk = dk * 31 + Math.round(k * 6f);
        dk = dk * 31 + hotRing * 131 + hotSeg;
        dk = dk * 31 + (hovMe ? 1 : 0) + (moonUp ? 2 : 0) + (dial ? 4 : 0);
        dk = dk * 31 + p.acc + p.glow * 7L + p.c1 * 13L;
        dk = dk * 31 + Math.round(sunA * 2f);
        dk = dk * 31 + Math.round(eclipse * 50f);
        dk = dk * 31 + (eclipse > 0.05f ? Math.round(clock * 12f) : 0);
        if (wheelTex == null || wheelTex.released() || dk != drawnKey) {
            drawnKey = dk;
            pixels.colours(p);
            pixels.draw(rings, k, hotRing, hotSeg, hovMe, dial, sunA, eclipse, moonUp, clock);
            if (wheelTex == null || wheelTex.released()) {
                wheelTex = SolTexture.of("quick_wheel", PixelWheel.WN, PixelWheel.WN, false, pixels.buf);
            } else {
                wheelTex.upload(pixels.buf);
            }
        }
        float size = PixelWheel.WN * wk;
        g.imageScreen(wheelTex, left, top, left + size, top + size, 0f, 0f, 1f, 1f, 0xFFFFFFFF, false);
        // Your face on the disc: 8 x 8 at 5 art pixels each.
        int fk = 5 * wk;
        PlayerFace.drawPx(g, left + PixelWheel.WC * wk - 4 * fk, top + PixelWheel.WC * wk - 4 * fk, fk);
        if (focus == 0 && meFocus) {
            Pix.focus(g, wcx - PixelWheel.HR * ud, wcy - PixelWheel.HR * ud, PixelWheel.HR * 2f * ud, PixelWheel.HR * 2f * ud);
        }
        if (swT < 0f) {
            labels(g, p);
        }
        centre(g, p);
        bar(g, p, tk);
        tray(g, p, tk);
        if (viewWheel == Wheel.EMOTES && picker == null) {
            slotButtons(g, p);
        }
        if (picker != null) {
            g.rectScreen(0f, 0f, g.d.screenW, g.d.screenH, 0x8C04050A, 0x8C04050A);
            picker.draw(g, ui, tk, CX, 360f, my, pictures, () -> picker = null);
        }
    }

    /**
     * The light on the world behind: night darkens it (more as the sun goes down the dial, and in the eclipse), dusk
     * warms it at the dial's sides, and the stars come out at night and in the eclipse, as 2 px pixels twinkling in
     * steps.
     */
    private void world(Gfx g, PixelTokens p, float sunA, float sinA, float eclipse) {
        float elv = -sinA;
        float night = Math.max(0f, Math.min(0.6f, -elv * 0.42f + eclipse * 0.4f));
        float dusk = (1f - Math.abs(elv)) * 0.32f * (1f - eclipse);
        float l = g.d.left(), r = g.d.right(), t = g.d.top(), b = g.d.bottom();
        if (night > 0.01f) {
            g.rect(l, t, r - l, b - t, Colors.withAlpha(0xFF03030A, night));
        }
        if (dusk > 0.01f) {
            g.rect(l, t, r - l, b - t, Colors.withAlpha(p.glow, dusk * 0.5f));
        }
        float stars = Math.max(night / 0.6f, eclipse * 0.7f);
        if (stars > 0.02f) {
            for (int i = 0; i < 84; i++) {
                float tw = calm() ? 1f : 0.55f + 0.45f * (float) Math.sin(clock * 1.7f + starPh[i]);
                tw = Math.round(tw * 3f) / 3f;
                float s = starBig[i] ? 4f : 2f;
                g.rect(starX[i], starY[i], s, s, Colors.fade(starC[i], stars * tw));
            }
        }
    }

    /** The labels on the wheel: icon over name (Silkscreen, hard black drop), emote pictures, the plus. */
    private void labels(Gfx g, PixelTokens p) {
        float shadow = g.drop;
        g.drop = 3f;
        for (int i = 0; i < items.size() && i < labels.size(); i++) {
            WheelLabels.Label l = labels.get(i);
            if (l == null) {
                continue;
            }
            Item it = items.get(i);
            boolean isHot = viewWheel == Wheel.NONE ? it.index == placeIndex() && !meOn() : i == wheelHot && !meOn();
            int c = isHot ? p.acc : it.current ? p.acc : 0xFFE9E4EC;
            if (it.emote != null) {
                int px = Math.round(l.picW * g.d.u);
                pictures.draw(g.game(), it.emote, Math.round(g.d.sx(l.x) - px / 2f), Math.round(g.d.sy(l.y) - px / 2f), px);
                g.layer();
            } else {
                // The icon at its real size: whole screen pixels per icon pixel, as it was fitted.
                float iconS = PixelIcons.CELL * Math.max(1, Math.round((l.small ? 24f : 36f) / PixelIcons.CELL * g.d.u)) / g.d.u;
                SolFont f = g.font(SolFont.Family.PIXEL_BOLD, l.size);
                float tw = l.text == null ? 0f : g.width(f, l.text);
                if (l.row && l.text != null) {
                    float x0 = l.x - l.w / 2f;
                    if (it.icon != null) {
                        Icons.draw(g, it.icon, x0, l.y - iconS / 2f, iconS, c, 0f, 0);
                    }
                    g.textMid(f, l.text, x0 + iconS + WheelLabels.ROW_GAP, l.y - 1.5f, c);
                } else {
                    float y0 = l.y - l.h / 2f;
                    if (it.icon != null) {
                        g.drop = 0f;
                        Icons.draw(g, it.icon, l.x - iconS / 2f, y0 + 3f, iconS, 0xFF000000, 0f, 0);
                        g.drop = 3f;
                        Icons.draw(g, it.icon, l.x - iconS / 2f, y0, iconS, c, 0f, 0);
                    }
                    if (l.text != null) {
                        float ty = y0 + (it.icon != null ? iconS + WheelLabels.STACK_GAP : 0f);
                        g.text(f, l.text, l.x - tw / 2f, ty + f.cap() / g.d.u, c);
                    }
                }
            }
            if (viewWheel == Wheel.NONE && focus == it.index + 1) {
                Pix.focus(g, l.x - l.w / 2f - 6f, l.y - l.h / 2f - 6f, l.w + 12f, l.h + 12f);
            }
        }
        g.drop = shadow;
    }

    /**
     * What you're pointing at, under your head: its name (Silkscreen 15, down to 12) and its detail in the accent
     * (Geist semibold 12, down to 11), each on a black backing, sized and cut to stay inside the inner disc's chord.
     */
    private void centre(Gfx g, PixelTokens p) {
        String title, sub;
        if (meOn()) {
            title = meRing ? tr("sol.quick.back") : tr("sol.quick.me", SolProfiles.active());
            sub = viewWheel != Wheel.NONE ? tr("sol.quick.wheel.back_sub") : meRing ? tr("sol.quick.back_sub") : tr("sol.quick.me_sub");
        } else if (viewWheel != Wheel.NONE) {
            Item it = wheelHot >= 0 && wheelHot < items.size() ? items.get(wheelHot) : null;
            if (it != null) {
                title = it.title;
                sub = it.sub;
            } else if (viewWheel == Wheel.PRESETS) {
                title = tr("sol.section.presets");
                sub = tr("sol.quick.wheel.presets_sub");
            } else {
                title = tr(elytraWheel ? "sol.quick.wheel.elytra" : "sol.section.emotes");
                sub = tr("sol.quick.wheel.emotes_sub", emotePage + 1, Emotes.pages(elytraWheel));
            }
        } else {
            title = tr("sol.section." + hot);
            sub = detail(hot);
        }
        float inner = PixelWheel.HR * ud + (MAIN_A - PixelWheel.HR) * ud - 6f;
        float y = wcy + PixelWheel.HR * ud + 3f;
        y = line(g, SolFont.Family.PIXEL_BOLD, TITLE_SIZES, title, y, inner, 0xFFFFFFFF, true);
        line(g, SolFont.Family.SEMIBOLD, SUB_SIZES, sub, y + 2f, inner, p.acc);
    }

    /** The centre's title sizes (down to Silkscreen's smallest, one font pixel a screen pixel) and its line's. */
    private static final float[] TITLE_SIZES = {15f, 13f, 12f, 10f}, SUB_SIZES = {12f, 11f, 10f};

    /** One centred line on a black backing, the largest size that fits the chord (else cut); returns its bottom. */
    private float line(Gfx g, SolFont.Family fam, float[] sizes, String text, float y, float inner, int colour) {
        return line(g, fam, sizes, text, y, inner, colour, false);
    }

    private float line(Gfx g, SolFont.Family fam, float[] sizes, String text, float y, float inner, int colour, boolean label) {
        SolFont f = g.font(fam, sizes[sizes.length - 1]);
        float h = 0f, room = 0f;
        for (float s : sizes) {
            f = g.font(fam, s);
            h = f.cap() / g.d.u + 8f;
            room = WheelLabels.chordHalf(inner, y + h - wcy) * 2f;
            if (g.width(f, text) <= room - 10f) {
                break;
            }
        }
        if (g.width(f, text) > room - 10f && text.indexOf(' ') > 0) {
            // Too long even at the smallest size: on two lines, split at the space nearest its middle, when both fit.
            int best = -1, mid = text.length() / 2;
            for (int i = text.indexOf(' '); i >= 0; i = text.indexOf(' ', i + 1)) {
                if (best < 0 || Math.abs(i - mid) < Math.abs(best - mid)) {
                    best = i;
                }
            }
            String a = text.substring(0, best), b = text.substring(best + 1);
            float room2 = WheelLabels.chordHalf(inner, y + 2f * h - wcy) * 2f;
            if (g.width(f, a) <= room - 10f && g.width(f, b) <= room2 - 10f) {
                float wa = g.width(f, a) + 10f, wb = g.width(f, b) + 10f;
                g.rect(wcx - wa / 2f, y, wa, h, 0xB8080A0A);
                g.textMid(f, a, wcx - wa / 2f + 5f, y + h / 2f, colour);
                g.rect(wcx - wb / 2f, y + h, wb, h, 0xB8080A0A);
                g.textMid(f, b, wcx - wb / 2f + 5f, y + h + h / 2f, colour);
                return y + 2f * h;
            }
        }
        String shown = label ? g.fitLabel(f, text, room - 10f) : g.fit(f, text, room - 10f);
        float w = g.width(f, shown) + 10f;
        g.rect(wcx - w / 2f, y, w, h, 0xB8080A0A);
        g.textMid(f, shown, wcx - w / 2f + 5f, y + h / 2f, colour);
        return y + h;
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

    // ------------------------------------------------------------------ the bar along the top

    /**
     * The top bar (y 16, 48 high, x 236 to 1256): the logo above it on the left, "Quick menu" as the current tab, the
     * tabs that open Modules, Presets, Wardrobe and Emotes in the window, the search field, you (opens the Wardrobe)
     * and the red close.
     */
    private void bar(Gfx g, PixelTokens p, Tokens tk) {
        SolTexture lt = logo.texture(ThemeState.entry(), time, dt);
        int lk = Math.max(1, Math.round(2f * g.d.u));
        float lx = Math.round(g.d.sx(4f)), ly = Math.round(g.d.sy(-30f));
        g.imageScreen(lt, lx, ly, lx + LogoRenderer.W * lk, ly + LogoRenderer.H * lk, 0f, 0f, 1f, 1f, 0xFFFFFFFF, false);
        float y = 16f, h = 48f, x = 236f;
        SolFont tf = g.font(SolFont.Family.PIXEL_BOLD, 14f);
        String qt = tr("sol.quick.tab");
        float w = 10f + 24f + 10f + g.width(tf, qt) + 16f;
        Pix.slab(g, p, x, y, w, h, Pix.ON);
        Icons.draw(g, "solsq", x + 10f, y + 12f, 24f, p.acc, 0f, 0);
        g.textMid(tf, qt, x + 44f, y + h / 2f, p.acc);
        x += w + 6f;
        String[][] tabs = {{"modules", "grid"}, {"presets", "sun"}, {"wardrobe", "shirt"}, {"emotes", "run"}};
        for (String[] t : tabs) {
            boolean over = overBox(x, y, h, h);
            Pix.slab(g, p, x, y, h, h, over ? Pix.HOVER : Pix.NORMAL);
            Icons.draw(g, t[1], x + 12f, y + 12f, 24f, Pix.text(p, over ? Pix.HOVER : Pix.NORMAL), 0f, 0);
            final String id = t[0];
            hits.add(new Hit(x, y, h, h, () -> SolMenu.open(parent, id, true)));
            if (over) {
                Pix.tooltip(g, p, x, y + h + 6f, tr("sol.section." + id));
            }
            x += h + 6f;
        }
        // Search, then you and the close flush right.
        String name = io.github.georgwav.cosmetics.sol.client.Streamer.shownName(Minecraft.getInstance().getUser().getName());
        String preset = Widgets.themeName(ThemeState.baseEntry());
        SolFont nf = g.font(SolFont.Family.PIXEL_BOLD, 13f), pf = g.font(SolFont.Family.MEDIUM, 11.5f);
        float meW = 6f + 38f + 10f + Math.max(g.width(nf, name), g.width(pf, preset)) + 14f;
        float closeX = 1256f - h, meX = closeX - 6f - meW;
        float sx = x + 10f, sw = Math.min(330f, meX - 16f - sx);
        if (sw > 120f) {
            boolean over = overBox(sx, y, sw, h);
            Pix.search(g, p, sx, y, sw, h, "", tr("sol.quick.search"), tr("sol.quick.ctrl_k").toUpperCase(java.util.Locale.ROOT), over, -1f);
            hits.add(new Hit(sx, y, sw, h, () -> SolMenu.openSearch(parent, "")));
        }
        boolean overMeSlab = overBox(meX, y, meW, h);
        Pix.slab(g, p, meX, y, meW, h, overMeSlab ? Pix.HOVER : Pix.NORMAL);
        float fx = Math.round(g.d.sx(meX + 6f + 3f)), fy = Math.round(g.d.sy(y + 8f));
        int fkk = Math.max(1, Math.round(4f * g.d.u));
        g.rectPx(fx - g.px(3f), fy - g.px(3f), fx + 8 * fkk + g.px(3f), fy + 8 * fkk + g.px(3f), p.edge);
        PlayerFace.drawPx(g, fx, fy, fkk);
        float tx = meX + 6f + 38f + 10f;
        g.textMid(nf, name, tx, y + 17f, Pix.text(p, overMeSlab ? Pix.HOVER : Pix.NORMAL));
        g.textMid(pf, g.fit(pf, preset, meW - (tx - meX) - 10f), tx, y + 33f, p.acc);
        hits.add(new Hit(meX, y, meW, h, () -> SolMenu.open(parent, "wardrobe", true)));
        Pix.redClose(g, p, closeX, y, h, h, overBox(closeX, y, h, h));
        hits.add(new Hit(closeX, y, h, h, this::onClose));
    }

    private boolean overBox(float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    // ------------------------------------------------------------------ the tray

    /** The tray under the wheel: a one-line head, then your pins, the presets strip, the colours or the emote pages. */
    private void tray(Gfx g, PixelTokens p, Tokens tk) {
        SolFont head = g.font(SolFont.Family.PIXEL_BOLD, 11f);
        String heading = viewWheel == Wheel.PRESETS ? tr("sol.quick.tray.colour")
                : viewWheel == Wheel.EMOTES ? tr("sol.quick.tray.emotes", emotePage + 1, Emotes.pages(elytraWheel))
                : viewMe ? tr("sol.quick.tray.presets") : tr("sol.quick.tray.pins");
        float shadow = g.drop;
        g.drop = 2f;
        g.textCentre(head, heading, CX, TRAY_Y + 6f, 0xFFFFFFFF);
        g.drop = shadow;
        float y = TRAY_Y + 19f;
        if (viewWheel == Wheel.PRESETS) {
            colours(g, p, tk, y);
        } else if (viewWheel == Wheel.EMOTES) {
            emoteChips(g, tk, y);
        } else if (viewMe) {
            presets(g, p, y);
        } else {
            pins(g, tk, y);
            SolFont hint = g.font(SolFont.Family.PIXEL, 10f);
            g.drop = 2f;
            g.textCentre(hint, tr("sol.quick.hint", keyName(KeyBinds.quickMenu())), CX, 704f, 0xB3FFFFFF);
            g.drop = shadow;
        }
    }

    private void pins(Gfx g, Tokens tk, float y) {
        List<Widgets.Chip> chips = new ArrayList<>();
        for (String id : SolProfiles.pinned()) {
            Module m = Modules.byId(id);
            if (m == null || !m.available()) {
                continue;
            }
            chips.add(Widgets.Chip.pin(m.name(), io.github.georgwav.cosmetics.sol.ui.kit.ModuleIcons.module(m.id), m.enabled(), () -> {
                m.setEnabled(!m.enabled());
                Modules.save();
            }));
        }
        chips.add(Widgets.Chip.button(tr("sol.quick.all_modules"), () -> SolMenu.open(parent, "modules", true)));
        rowOfChips(g, tk, chips, y);
    }

    /** Chips in centred rows up to 1040 wide. */
    private void rowOfChips(Gfx g, Tokens tk, List<Widgets.Chip> chips, float y) {
        float maxW = 1040f, gap = 8f;
        List<Widgets.Chip> row = new ArrayList<>();
        float w = 0f;
        int start = 1 + places().length + hits.size();
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

    /**
     * Inside the eclipse: the presets strip. Cards 118 wide, each a 106 x 44 sky picture at 2 px pixels and its name in
     * Silkscreen 10; the current one is on, and the strip keeps it in the middle.
     */
    private void presets(Gfx g, PixelTokens p, float y) {
        List<ThemeRegistry.Entry> themes = ThemeRegistry.all();
        float cardW = 136f, cardH = 74f, gap = 6f, arrow = 30f;
        int n = themes.size();
        // Whole cards only: as many as fit, and page arrows either side when they don't all fit.
        boolean paged = n * (cardW + gap) - gap > TRAY_W;
        int visible = paged ? Math.max(1, (int) ((TRAY_W - 2f * (arrow + 8f) + gap) / (cardW + gap))) : n;
        String current = ThemeState.themeId();
        if (presetFirst < 0) {
            int ci = 0;
            for (int i = 0; i < n; i++) {
                if (themes.get(i).id().equals(current)) {
                    ci = i;
                }
            }
            presetFirst = ci - visible / 2;
        }
        presetFirst = Math.max(0, Math.min(Math.max(0, n - visible), presetFirst));
        float width = visible * (cardW + gap) - gap, x0 = CX - width / 2f;
        int start = 1 + places().length + hits.size();
        int tk = Math.max(1, Math.round(2f * g.d.u));
        for (int v = 0; v < visible && presetFirst + v < n; v++) {
            int i = presetFirst + v;
            ThemeRegistry.Entry e = themes.get(i);
            float x = x0 + v * (cardW + gap);
            boolean cur = e.id().equals(current);
            boolean over = overBox(x, y, cardW, cardH);
            int st = cur ? Pix.ON : over ? Pix.HOVER : Pix.NORMAL;
            Pix.slab(g, p, x, y, cardW, cardH, st);
            float px0 = Math.round(g.d.sx(x + 6f)), py0 = Math.round(g.d.sy(y + 6f));
            SolTexture thumb = thumbs.get(e);
            int e3 = g.px(Pix.E);
            g.rectPx(px0 - e3, py0 - e3, px0 + 53 * tk + e3, py0 + 22 * tk + e3, p.edge);
            if (thumb != null) {
                g.imageScreen(thumb, px0, py0, px0 + 53 * tk, py0 + 22 * tk, 0f, 0f, 1f, 1f, 0xFFFFFFFF, false);
            } else {
                g.rectPx(px0, py0, px0 + 53 * tk, py0 + 22 * tk, e.base().sky().top());
            }
            String name = Widgets.themeName(e);
            SolFont nameF = g.fitting(name, cardW - 14f, 10f, 8f);
            g.textMid(nameF, g.fitLabel(nameF, name, cardW - 14f), x + 7f, y + cardH - 12f, Pix.text(p, st));
            if (focus == start + v) {
                Pix.focus(g, x, y, cardW, cardH);
            }
            final String id = e.id();
            hits.add(new Hit(x, y, cardW, cardH, () -> applyPreset(id)));
        }
        if (paged) {
            float ay = y + cardH / 2f - arrow / 2f;
            pageArrow(g, p, x0 - 8f - arrow, ay, arrow, "prev", presetFirst > 0, () -> presetFirst = Math.max(0, presetFirst - visible));
            pageArrow(g, p, x0 + width + 8f, ay, arrow, "next", presetFirst + visible < n,
                    () -> presetFirst = Math.min(Math.max(0, n - visible), presetFirst + visible));
        }
    }

    /** A square page arrow in the tray; dim (and dead) at either end. */
    private void pageArrow(Gfx g, PixelTokens p, float x, float y, float s, String icon, boolean live, Runnable turn) {
        boolean over = live && overBox(x, y, s, s);
        Pix.slab(g, p, x, y, s, s, over ? Pix.HOVER : Pix.NORMAL);
        Icons.draw(g, icon, x + s / 2f - 9f, y + s / 2f - 9f, 18f, live ? Pix.text(p, over ? Pix.HOVER : Pix.NORMAL) : PixelTokens.INK3, 0f, 0);
        if (live) {
            hits.add(new Hit(x, y, s, s, turn));
        }
    }

    private void emoteChips(Gfx g, Tokens tk, float y) {
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
        rowOfChips(g, tk, chips, y);
    }

    /** By the emote slot pointed at, just outside the ring: change it (gear), or empty it (red X). */
    private void slotButtons(Gfx g, PixelTokens p) {
        Item it = wheelHot >= 0 && wheelHot < items.size() ? items.get(wheelHot) : null;
        if (it == null || it.emote == null) {
            return;
        }
        for (int side = 0; side < 2; side++) {
            double t = Math.toRadians(it.mid + (side == 0 ? -9f : 9f));
            float r = (it.r1 + 14f) * ud, x = wcx + r * (float) Math.cos(t) - 15f, y = wcy + r * (float) Math.sin(t) - 15f;
            boolean over = overBox(x, y, 30f, 30f);
            if (side == 0) {
                Pix.slab(g, p, x, y, 30f, 30f, over ? Pix.HOVER : Pix.NORMAL);
                Icons.draw(g, "gear", x + 3f, y + 3f, 24f, Pix.text(p, over ? Pix.HOVER : Pix.NORMAL), 0f, 0);
                hits.add(new Hit(x, y, 30f, 30f, it.alt));
            } else {
                Pix.redClose(g, p, x, y, 30f, 30f, over);
                final int slot = it.slot;
                hits.add(new Hit(x, y, 30f, 30f, () -> {
                    Emotes.assign(elytraWheel, slot, "");
                    CosmeticsConfig.save();
                }));
            }
        }
    }

    /** The accent colours the presets wheel offers along the bottom, after the theme's own and the song's. */
    private static final int[] COLOURS = {0xFFFF4D4D, 0xFFFF8A3C, 0xFFFFD21F, 0xFF9BE15D, 0xFF2FD47A, 0xFF2FD4C8, 0xFF4DA8FF,
            0xFF5B6CFF, 0xFFA06BFF, 0xFFFF5FC8, 0xFFF2F2F2};
    /** The swatches' size. */
    private static final float SW = 40f, SH = 32f;
    /** The accent picker's panel, right of the presets wheel and just above the colour row (design pixels). */
    private static final float PICK_X = 880f, PICK_W = 388f, PICK_H = ColorPicker.HEIGHT + 42f,
            PICK_Y = TRAY_Y - 10f - PICK_H;

    /**
     * Under the presets wheel: the colours. Default is the theme's own, Music the playing song's (the album colours),
     * then a row of swatches, any of which becomes your accent, and last the picker for any other colour. Every colour
     * in the client that follows the theme changes with it, live.
     */
    private void colours(Gfx g, PixelTokens p, Tokens tk, float y) {
        var music = SolConfig.get().music;
        Integer accent = ThemeState.accentOverride();
        Widgets.Chip own = Widgets.Chip.toggle(tr("sol.quick.wheel.colour_default"), accent == null && !music.albumUi, () -> {
            music.albumUi = false;
            SolProfiles.setAccent(null);
            SolConfig.save();
        });
        Widgets.Chip song = Widgets.Chip.toggle(tr("sol.quick.wheel.colour_music"), accent == null && music.albumUi, () -> {
            music.albumUi = true;
            SolProfiles.setAccent(null);
            SolConfig.save();
        });
        float gap = 8f;
        float total = own.width(g) + gap + song.width(g) + gap * 2f + (COLOURS.length + 1) * (SW + gap) - gap;
        float x = CX - total / 2f, cy = y + Widgets.Chip.H / 2f;
        for (Widgets.Chip c : new Widgets.Chip[]{own, song}) {
            c.draw(g, tk, x, y, false, mx, my);
            hits.add(new Hit(x, y, c.width(g), Widgets.Chip.H, c.action()));
            x += c.width(g) + gap;
        }
        x += gap;
        boolean listed = false;
        for (int colour : COLOURS) {
            boolean picked = accent != null && (accent & 0xFFFFFF) == (colour & 0xFFFFFF);
            listed |= picked;
            Pix.swatch(g, p, x, cy - SH / 2f, SW, SH, colour, picked);
            hits.add(new Hit(x, cy - SH / 2f, SW, SH, () -> {
                music.albumUi = false;
                SolProfiles.setAccent(colour);
                SolConfig.save();
            }));
            x += SW + gap;
        }
        // Any colour: hue bands, with your own colour in the middle once you've picked one that isn't listed.
        boolean custom = accent != null && !listed;
        float sy = cy - SH / 2f;
        for (int i = 0; i < 6; i++) {
            g.rect(x + i * SW / 6f, sy, SW / 6f + 0.5f, SH, 0xFF000000 | io.github.georgwav.cosmetics.render.Tint.hsvToRgb(i / 6f, 0.85f, 1f));
        }
        g.rect(x + 12f, sy + 9f, SW - 24f, SH - 18f, custom ? 0xFF000000 | accent : p.c1);
        if (!custom) {
            Icons.draw(g, "plus", x + 8f, sy + 4f, 24f, 0xFFFFFFFF, 0f, 0);
        }
        g.stroke(x, sy, SW, SH, 0f, g.px(Pix.E), p.edge);
        if (custom || accentPicker != null) {
            Pix.focus(g, x, sy, SW, SH);
        }
        hits.add(new Hit(x, sy, SW, SH, () -> accentPicker = accentPicker == null ? newAccentPicker() : null));
        if (accentPicker != null) {
            accentPanel(g, p, tk);
        }
    }

    /** The picker: it shows the accent in use and sets your own, live, as it's dragged. */
    private ColorPicker newAccentPicker() {
        return new ColorPicker(() -> {
            Integer a = ThemeState.accentOverride();
            return a != null ? a : ThemeState.tokens().accent;
        }, rgb -> {
            var music = SolConfig.get().music;
            if (music.albumUi) {
                music.albumUi = false;
                SolConfig.save();
            }
            SolProfiles.setAccent(0xFF000000 | rgb);
        });
    }

    private void accentPanel(Gfx g, PixelTokens p, Tokens tk) {
        float px = PICK_X, py = PICK_Y, pw = PICK_W, ph = PICK_H;
        Pix.slab(g, p, px, py, pw, ph, Pix.NORMAL);
        ui.area(px, py, pw, ph, () -> { });
        SolFont h = g.font(SolFont.Family.PIXEL_BOLD, 12f), small = g.font(SolFont.Family.MEDIUM, 11f);
        String head = tr("sol.quick.wheel.colour_pick");
        g.textMid(h, head, px + 14f, py + 18f, PixelTokens.INK);
        // The hint beside the head, only where it has room to say something.
        float hintRoom = pw - 28f - g.width(h, head) - 16f;
        if (hintRoom > 80f) {
            g.textRight(small, g.fit(small, tr("sol.quick.wheel.colour_pick_hint"), hintRoom), px + pw - 14f, py + 18f, PixelTokens.INK3);
        }
        List<Integer> presets = new ArrayList<>(COLOURS.length);
        for (int c : COLOURS) {
            presets.add(c & 0xFFFFFF);
        }
        accentPicker.draw(g, ui, tk, px + 14f, py + 32f, pw - 28f, presets, tr("sol.quick.wheel.colour_default"), () -> {
            SolConfig.get().music.albumUi = false;
            SolProfiles.setAccent(null);
            SolConfig.save();
        }, dt);
    }

    private boolean overAccentPanel(float x, float y) {
        return accentPicker != null && x >= PICK_X && x <= PICK_X + PICK_W && y >= PICK_Y && y <= PICK_Y + PICK_H;
    }

    private void turnPage(int by) {
        emotePage = Math.floorMod(emotePage + by, Emotes.pages(elytraWheel));
    }

    // ------------------------------------------------------------------ input

    /** The wheel's segment under a design point (the prototype's ringAt and idxAt), or -1. */
    private int itemAt(float x, float y, boolean strict) {
        if (rings.isEmpty() || swT >= 0f) {
            return -1;
        }
        float dx = (x - wcx) / ud, dy = (y - wcy) / ud, r = (float) Math.hypot(dx, dy);
        if (r < PixelWheel.HR + 2f || r > (strict ? 146f : 140f)) {
            return -1;
        }
        int ri = 0;
        for (int i = 1; i < rings.size(); i++) {
            if (r >= (rings.get(i - 1).r1 + rings.get(i).r0) / 2f) {
                ri = i;
            }
        }
        PixelWheel.Ring R = rings.get(ri);
        if (strict && (r < R.r0 - 2f || r >= R.r1 + 4f)) {
            return -1;
        }
        int seg = R.at((float) Math.toDegrees(Math.atan2(dy, dx)));
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).ring == ri && items.get(i).index == seg) {
                return i;
            }
        }
        return -1;
    }

    private boolean overMe(float x, float y) {
        return Math.hypot(x - wcx, y - wcy) <= (PixelWheel.HR + 2f) * ud;
    }

    private boolean inWheelBand(float y) {
        return y >= 64f && y <= TRAY_Y - 16f;
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
        if (picker != null || over || !inWheelBand(y)) {
            return;
        }
        int i = itemAt(x, y, false);
        if (i < 0) {
            if (viewWheel != Wheel.NONE && !overHit(x, y)) {
                wheelHot = -1;
            }
            return;
        }
        if (viewWheel != Wheel.NONE) {
            wheelHot = i;
            stagedHot = null;
        } else {
            Item it = items.get(i);
            if (it.id != null && !it.id.equals(hot)) {
                setHot(it.id);
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
                onRow |= h.contains(x, y) && h.y() >= TRAY_Y;
            }
            if (!onRow) {
                // A click anywhere else shuts the picker first.
                accentPicker = null;
                return true;
            }
        }
        if (viewWheel != Wheel.NONE && button == InputConstants.MOUSE_BUTTON_RIGHT) {
            int i = itemAt(x, y, true);
            if (i >= 0 && items.get(i).alt != null) {
                items.get(i).alt.run();
                return true;
            }
            return false;
        }
        if (button != InputConstants.MOUSE_BUTTON_LEFT) {
            return false;
        }
        for (Hit h : hits) {
            if (h.contains(x, y)) {
                h.action().run();
                return true;
            }
        }
        if (swT >= 0f) {
            return true;
        }
        if (overMe(x, y)) {
            if (wheel != Wheel.NONE) {
                closeWheel();
            } else {
                toggleMe();
            }
            return true;
        }
        int i = itemAt(x, y, true);
        if (i >= 0) {
            Item it = items.get(i);
            if (it.id != null) {
                choose(it.id);
            } else {
                it.action.run();
            }
            return true;
        }
        return false;
    }

    @Override
    protected boolean release(float x, float y, int button) {
        boolean was = ui.release();
        return (picker != null || accentPicker != null) && was;
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
        if (meRing && y >= TRAY_Y) {
            // A card at a time (presets() keeps it in range).
            presetFirst = Math.max(0, presetFirst - (int) Math.signum(amount));
            return true;
        }
        return false;
    }

    @Override
    protected boolean key(int key, boolean ctrl, boolean shift) {
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
                    stagedHot = null;
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
                int count = 1 + list.length + hits.size();
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
                    // The tray's items, then the bar's, in the order they were drawn.
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
        if (wheelTex != null) {
            wheelTex.release();
        }
        logo.release();
        thumbs.release();
        pictures.close();
    }

    protected static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }
}
