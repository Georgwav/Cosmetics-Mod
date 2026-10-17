package io.github.georgwav.cosmetics.sol.ui.menu;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.georgwav.cosmetics.client.Module;
import io.github.georgwav.cosmetics.client.Modules;
import io.github.georgwav.cosmetics.sol.Feature;
import io.github.georgwav.cosmetics.sol.config.SolProfiles;
import io.github.georgwav.cosmetics.sol.render.SolTexture;
import io.github.georgwav.cosmetics.sol.theme.ThemeRegistry;
import io.github.georgwav.cosmetics.sol.theme.ThemeState;
import io.github.georgwav.cosmetics.sol.theme.Tokens;
import io.github.georgwav.cosmetics.sol.ui.kit.Gfx;
import io.github.georgwav.cosmetics.sol.ui.kit.Icons;
import io.github.georgwav.cosmetics.sol.ui.kit.LogoRenderer;
import io.github.georgwav.cosmetics.sol.ui.kit.ModuleIcons;
import io.github.georgwav.cosmetics.sol.ui.kit.Notifications;
import io.github.georgwav.cosmetics.sol.ui.kit.PlayerFace;
import io.github.georgwav.cosmetics.sol.ui.kit.SolFont;
import io.github.georgwav.cosmetics.sol.ui.kit.SolScreen;
import io.github.georgwav.cosmetics.sol.ui.kit.Ui;
import io.github.georgwav.cosmetics.sol.ui.kit.Widgets;
import io.github.georgwav.cosmetics.sol.ui.quick.QuickMenu;
import io.github.georgwav.cosmetics.sol.util.Colors;
import io.github.georgwav.cosmetics.sol.util.PixelCanvas;
import io.github.georgwav.cosmetics.sol.util.Spring;
import io.github.georgwav.cosmetics.sol.util.SunCpu;
import io.github.georgwav.cosmetics.ui.KeyBinds;
import io.github.georgwav.cosmetics.ui.ScreenOpener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * The full Sol menu: the sun's path. Seven sections sit along the arc the sun travels and the pixel sun glides to
 * the one you pick; search sits on the horizon; the section opens under it, with a settings drawer on the right; the
 * profile is in the corner and the status line at the bottom. The same screen Mod Menu's Configure opens. A port of
 * the Sol menu prototype's full menu.
 */
public final class SolMenu extends SolScreen {
    public static final String[] STOPS = {"modules", "hud", "world", "wardrobe", "music", "mods", "settings"};
    /** Frames drawn (the in-game check). */
    public static int framesRendered;
    static final float[] TS = {0.1f, 0.233f, 0.367f, 0.5f, 0.633f, 0.767f, 0.9f};
    /** The content area under the horizon (the prototype's .content). */
    static final float CONTENT_X = 40f, CONTENT_Y = 294f, CONTENT_BOTTOM = 676f;
    static final float DRAWER_W = 404f;

    private String section;
    private final boolean fromQuick;
    final Ui ui = new Ui();
    private final Map<String, Section> sections = new HashMap<>();
    private final Spring sunT;
    private float sunRise;
    private float openT, sectionT;
    private ModuleDrawer drawer;
    private float drawerK;
    private CommandBar palette;
    private boolean popover;
    /** Each saved profile's theme, read when the popover opens. */
    private final Map<String, SolProfiles.Summary> profileThemes = new HashMap<>();
    private final LogoRenderer logo = new LogoRenderer(false);
    private SolTexture sunTex;
    private final PixelCanvas sunCanvas = new PixelCanvas(32, 32);
    private final int[] sunPixels = new int[32 * 32];
    private float sunDrawnAt = -1f;

    private SolMenu(Screen parent, String section, boolean fromQuick) {
        super(Component.translatable("sol.menu.title"), parent, Feature.MENU);
        this.section = indexOf(section) >= 0 ? section : "modules";
        this.fromQuick = fromQuick;
        sunT = new Spring(Spring.SUN_ARC, TS[indexOf(this.section)]);
        sunRise = calm() ? 1f : 0f;
        sections.put("modules", new ModulesSection());
        sections.put("world", new WorldSection());
        sections.put("hud", new HudSection());
        sections.put("wardrobe", new WardrobeSection());
        sections.put("music", new MusicSection());
        sections.put("mods", new ModsSection());
        sections.put("settings", new SettingsSection());
    }

    public static SolMenu create(Screen parent, String section, boolean fromQuick) {
        return new SolMenu(parent, section, fromQuick);
    }

    /** Opens the full menu on a section. */
    public static void open(Screen parent, String section, boolean fromQuick) {
        ScreenOpener.open(create(parent, section, fromQuick));
    }

    /** Opens the full menu with search (Ctrl+K, or typing in the quick menu) already open. */
    public static void openSearch(Screen parent, String seed) {
        SolMenu m = create(parent, "modules", true);
        m.palette = new CommandBar(m, seed);
        ScreenOpener.open(m);
    }

    private static int indexOf(String id) {
        for (int i = 0; i < STOPS.length; i++) {
            if (STOPS[i].equals(id)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * For the dev scenario: one of the states the prototype's reference shots show (a section, the outfits tab, a
     * module's drawer, the command bar, the profiles).
     */
    public SolMenu stage(String state) {
        switch (state) {
            case "profiles" -> openPopover();
            case "cmdbar" -> openPalette("");
            case "music_lyrics" -> {
                go("music");
                ((MusicSection) sections.get("music")).stage("lyrics");
            }
            case "wardrobe_outfits" -> {
                go("wardrobe");
                ((WardrobeSection) sections.get("wardrobe")).tab("outfits");
            }
            default -> {
                if (state.startsWith("drawer_")) {
                    go("modules");
                    Module m = io.github.georgwav.cosmetics.client.Modules.byId(state.substring("drawer_".length()));
                    if (m != null) {
                        openDrawer(m);
                    }
                } else {
                    go(state);
                }
            }
        }
        return this;
    }

    // ------------------------------------------------------------------ what sections use

    public String section() {
        return section;
    }

    public void go(String id) {
        if (id.equals(section) || indexOf(id) < 0) {
            return;
        }
        section = id;
        sectionT = 0f;
        closeDrawer();
        sections.get(id).opened();
        sunT.setTarget(TS[indexOf(id)]);
    }

    private void openPopover() {
        popover = true;
        profileThemes.clear();
        for (String n : SolProfiles.names()) {
            profileThemes.put(n, SolProfiles.summary(n));
        }
    }

    public void openDrawer(Module m) {
        drawer = new ModuleDrawer(m);
    }

    public void closeDrawer() {
        drawer = null;
    }

    public ModuleDrawer drawer() {
        return drawer;
    }

    public float time() {
        return time;
    }

    public float dt() {
        return dt;
    }

    public float mouseX() {
        return mx;
    }

    public float mouseY() {
        return my;
    }

    public void toast(String text) {
        Notifications.post(text);
    }

    public void openPalette(String seed) {
        palette = new CommandBar(this, seed);
    }

    public void closePalette() {
        palette = null;
    }

    public Minecraft mc() {
        return minecraft;
    }

    static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    /** x of the arc at t (the prototype's bez()). */
    static float arcX(float t) {
        float u = 1f - t;
        return u * u * 170f + 2f * u * t * 640f + t * t * 1110f;
    }

    static float arcY(float t) {
        float u = 1f - t;
        return u * u * 250f + 2f * u * t * -90f + t * t * 250f;
    }

    // ------------------------------------------------------------------ frame

    @Override
    protected void frame(Gfx g) {
        framesRendered++;
        Tokens tk = ThemeState.tokens();
        openT += dt;
        sectionT += dt;
        if (calm() || dt == 0f) {
            sunT.snap(sunT.target());
            sunRise = 1f;
        } else {
            sunT.step(dt);
            sunRise += (1f - sunRise) * (1f - (float) Math.exp(-dt * 5f));
        }
        float dk = drawer != null ? 1f : 0f;
        drawerK = calm() ? dk : drawerK + (dk - drawerK) * Math.min(1f, dt * 10f);
        ui.begin(g, mx, my);
        float open = calm() ? 1f : Math.min(1f, openT / 0.22f);
        g.alpha = open;

        veil(g, tk);
        band(g, tk);
        logo(g);
        arc(g);
        stops(g, tk);
        sun(g, tk);
        horizon(g, tk);
        command(g, tk);
        profile(g, tk);

        // The section, rising in under the horizon.
        float rightEdge = 1280f - 40f - (DRAWER_W + 16f) * drawerK;
        float rise = calm() ? 1f : Math.max(0f, Math.min(1f, (Math.min(openT, sectionT) - 0.05f) / 0.45f));
        float riseEase = 1f - (1f - rise) * (1f - rise) * (1f - rise);
        g.alpha = open * Math.min(1f, rise * 1.6f);
        float cy = CONTENT_Y + 16f * (1f - riseEase);
        Section s = sections.get(section);
        s.draw(this, g, ui, tk, CONTENT_X, cy, rightEdge - CONTENT_X, CONTENT_BOTTOM - CONTENT_Y);
        g.alpha = open;
        if (drawer != null || drawerK > 0.01f) {
            if (drawer != null) {
                float slide = 40f * (1f - drawerK);
                g.alpha = open * drawerK;
                drawer.draw(this, g, ui, tk, 1280f - 40f - DRAWER_W + slide, CONTENT_Y, DRAWER_W, CONTENT_BOTTOM - CONTENT_Y);
                g.alpha = open;
            }
        }
        status(g, tk);
        if (popover) {
            popover(g, tk);
        }
        if (palette != null) {
            palette.draw(g, ui, tk);
        }
        g.alpha = 1f;
    }

    private void veil(Gfx g, Tokens tk) {
        float l = g.d.left(), r = g.d.right(), top = g.d.top(), bottom = g.d.bottom();
        g.vgradient(l, top, r - l, 216f - top, 0x6104050A, 0x4204050A);
        g.vgradient(l, 216f, r - l, 50.4f, 0x4204050A, 0xB804050A);
        g.vgradient(l, 266.4f, r - l, 720f - 266.4f, 0xB804050A, 0xD604050A);
        if (bottom > 720f) {
            g.rect(l, 720f, r - l, bottom - 720f, 0xD604050A);
        }
    }

    private void band(Gfx g, Tokens tk) {
        float k = calm() ? 1f : Math.max(0f, Math.min(1f, (openT - 0.15f) / 0.6f));
        if (k <= 0f) {
            return;
        }
        float base = g.alpha;
        g.alpha = base * k;
        g.vgradient(g.d.left(), 166f, g.d.right() - g.d.left(), 84f, Colors.withAlpha(tk.band, 0f), tk.band);
        g.alpha = base;
    }

    private void logo(Gfx g) {
        SolTexture t = logo.texture(ThemeState.baseEntry(), time, dt);
        g.image(t, 24f, 14f, 168f, 108f, 0f, 0f, 1f, 1f, 0xFFFFFFFF, false);
    }

    private void arc(Gfx g) {
        float k = calm() ? 1f : Math.max(0f, Math.min(1f, (openT - 0.08f) / 0.8f));
        k = 1f - (1f - k) * (1f - k) * (1f - k);
        int n = 64;
        int m = Math.max(2, Math.round(n * k));
        float[] xs = new float[m], ys = new float[m];
        for (int i = 0; i < m; i++) {
            float t = i / (float) (n - 1);
            xs[i] = g.d.sx(arcX(t));
            ys[i] = g.d.sy(arcY(t));
        }
        if (k > 0.01f) {
            g.strokePath(xs, ys, m, false, 1.5f * g.d.u, g.flat(0x4DFFFFFF));
        }
    }

    private void stops(Gfx g, Tokens tk) {
        SolFont f = g.font(SolFont.Family.MEDIUM, 12.5f);
        for (int i = 0; i < STOPS.length; i++) {
            String id = STOPS[i];
            float x = arcX(TS[i]), y = arcY(TS[i]) - 22f;
            float k = calm() ? 1f : Math.max(0f, Math.min(1f, (openT - 0.15f - i * 0.04f) / 0.5f));
            if (k <= 0f) {
                continue;
            }
            float sc = 0.6f + 0.4f * pop(k);
            boolean on = id.equals(section);
            float r = 22f * sc;
            boolean hover = ui.over(x - 26f, y - 4f, 52f, 70f);
            float base = g.alpha;
            g.alpha = base * Math.min(1f, k * 1.6f);
            int fi = ui.click(x - 30f, y - 4f, 60f, 70f, () -> go(id));
            float cy = y + r;
            if (on) {
                g.glowSprite(x, cy, 34f, 34f, Colors.withAlpha(tk.glow, 0.45f));
            }
            g.circle(x, cy, r, tk.panel);
            g.ring(g.d.sx(x), g.d.sy(cy), r * g.d.u, Math.max(1f, g.d.u), on || hover ? tk.rim : 0x29FFFFFF);
            Icons.draw(g, ModuleIcons.SECTIONS.get(id), x - 9f * sc, cy - 9f * sc, 18f * sc, on ? tk.accent : 0xB8FFFFFF,
                    on ? Icons.FILL_ON : Icons.FILL, on ? Colors.withAlpha(tk.glow, 0.5f) : 0);
            g.textCentre(f, tr("sol.section." + id), x, cy + r + 13f, on ? 0xFFFFFFFF : 0xB8FFFFFF);
            if (ui.focused(fi)) {
                g.ring(g.d.sx(x), g.d.sy(cy), (r + 5f) * g.d.u, 2f * g.d.u, tk.accent);
            }
            g.alpha = base;
        }
    }

    private void sun(Gfx g, Tokens tk) {
        if (sunTex == null || sunTex.released()) {
            sunTex = new SolTexture("menu_sun", 32, 32, false);
            sunDrawnAt = -1f;
        }
        if (sunDrawnAt < 0f || time - sunDrawnAt >= 0.05f || time < sunDrawnAt) {
            sunDrawnAt = time;
            sunCanvas.clear();
            SunCpu.drawBack(sunCanvas, 32f, 16f, 16f, 5f, 0.35f, Colors.mix(0xFFFFF1CF, tk.glow, 0.2f), tk.glow, SunCpu.MENU, time, null);
            sunTex.upload(sunCanvas.toArgb(sunPixels));
        }
        float t = sunT.value();
        float x = arcX(t), y = arcY(t);
        float yy = 250f + (y - 44f - 250f) * sunRise;
        g.image(sunTex, x - 32f, yy - 32f, 64f, 64f, 0f, 0f, 1f, 1f, 0xFFFFFFFF, false);
    }

    private void horizon(Gfx g, Tokens tk) {
        float k = calm() ? 1f : Math.min(1f, openT / 0.55f);
        k = 1f - (1f - k) * (1f - k) * (1f - k);
        float half = 640f * k;
        g.glowSprite(640f, 251f, half, 16f, Colors.withAlpha(tk.glow, 0.35f));
        float[] at = {0f, 0.18f, 0.5f, 0.82f, 1f};
        int[] cols = {Colors.withAlpha(tk.glow, 0f), tk.glow, 0xFFFFFFFF, tk.glow, Colors.withAlpha(tk.glow, 0f)};
        g.hgradient(640f - half, 250f, half * 2f, 2f, at, cols, 5);
    }

    private void command(Gfx g, Tokens tk) {
        float k = calm() ? 1f : Math.max(0f, Math.min(1f, (openT - 0.25f) / 0.3f));
        if (k <= 0f) {
            return;
        }
        float w = 440f, h = 44f, x = 640f - w / 2f, y = 250f - h / 2f + (1f - pop(k)) * 3.5f;
        float base = g.alpha;
        g.alpha = base * k;
        boolean hover = ui.over(x, y, w, h);
        int fi = ui.click(x, y, w, h, () -> openPalette(""));
        g.glow(x, y, w, h, h / 2f, 26f, Colors.withAlpha(tk.glow, 0.22f));
        g.round(x, y, w, h, h / 2f, 0xD108080C);
        g.stroke(x, y, w, h, h / 2f, Math.max(1f, g.d.u), hover || ui.focused(fi) ? tk.rim : 0x29FFFFFF);
        g.highlight(x, y, w, h, h / 2f, 0x33FFFFFF);
        Icons.draw(g, "search", x + 16f, y + 13f, 18f, 0xA8FFFFFF, Icons.FILL, 0);
        g.textMid(g.font(SolFont.Family.MEDIUM, 14f), tr("sol.quick.search"), x + 44f, y + h / 2f, 0xA8FFFFFF);
        Widgets.kbd(g, tr("sol.quick.ctrl_k"), x + w - 10f, y + h / 2f);
        g.alpha = base;
    }

    private void profile(Gfx g, Tokens tk) {
        SolFont b = g.font(SolFont.Family.SEMIBOLD, 13f), s = g.font(SolFont.Family.MEDIUM, 11f);
        String name = io.github.georgwav.cosmetics.sol.client.Streamer.shownName(minecraft.getUser().getName());
        String prof = SolProfiles.active();
        float tw = Math.max(g.width(b, name), g.width(s, prof));
        float w = 5f + 32f + 10f + tw + 10f + 10f + 14f, h = 42f;
        float x = 1280f - 30f - w, y = 24f;
        boolean hover = ui.over(x, y, w, h);
        int fi = ui.click(x, y, w, h, () -> {
            if (popover) {
                popover = false;
            } else {
                openPopover();
            }
        });
        g.glow(x, y, w, h, h / 2f, 20f, Colors.withAlpha(tk.glow, 0.12f));
        g.round(x, y, w, h, h / 2f, tk.panel);
        g.stroke(x, y, w, h, h / 2f, Math.max(1f, g.d.u), hover || popover || ui.focused(fi) ? tk.rim : 0x24FFFFFF);
        g.highlight(x, y, w, h, h / 2f, 0x38FFFFFF);
        PlayerFace.draw(g, x + 5f, y + 5f, 32f, 0f);
        g.text(b, name, x + 47f, y + 19f, 0xFFFFFFFF);
        g.text(s, prof, x + 47f, y + 33f, tk.accent);
        g.text(s, "▾", x + w - 22f, y + 25f, 0x99FFFFFF);
    }

    private void popover(Gfx g, Tokens tk) {
        float w = 320f, x = 1280f - 30f - w, y = 74f;
        java.util.List<String> names = SolProfiles.names();
        float h = 8f + 26f + names.size() * 46f + 40f;
        g.glow(x, y, w, h, 16f, 30f, 0x80000000);
        g.round(x, y, w, h, 16f, 0xF00A0A0E);
        g.stroke(x, y, w, h, 16f, Math.max(1f, g.d.u), 0x24FFFFFF);
        ui.area(x, y, w, h, () -> { });
        SolFont head = g.font(SolFont.Family.MONO, 11f), b = g.font(SolFont.Family.SEMIBOLD, 13.5f), s = g.font(SolFont.Family.MEDIUM, 11.5f);
        g.textSpaced(head, tr("sol.menu.profiles").toUpperCase(java.util.Locale.ROOT), x + 16f, y + 20f, 1.1f, 0x80FFFFFF);
        float ry = y + 34f;
        String active = SolProfiles.active();
        for (String n : names) {
            SolProfiles.Summary sum = profileThemes.get(n);
            ThemeRegistry.Entry th = ThemeRegistry.get(sum == null ? ThemeRegistry.FALLBACK : sum.theme());
            boolean hover = ui.over(x + 8f, ry, w - 16f, 44f);
            ui.click(x + 8f, ry, w - 16f, 44f, () -> {
                popover = false;
                SolProfiles.switchTo(n);
                toast(tr("sol.notify.profile", n));
            });
            if (hover) {
                g.round(x + 8f, ry, w - 16f, 44f, 10f, 0x12FFFFFF);
            }
            int acc = sum != null && sum.accent() != null ? Colors.parse(sum.accent(), th.base().ui().accent()) : th.base().ui().accent();
            g.glowSprite(x + 24f, ry + 22f, 9f, 9f, Colors.withAlpha(sum != null && sum.accent() != null ? acc : th.base().sky().glow(), 0.7f));
            g.round(x + 19f, ry + 17f, 10f, 10f, 3f, acc);
            g.text(b, n, x + 40f, ry + 19f, 0xFFFFFFFF);
            String line = tr("sol.menu.profile_line", Widgets.themeName(th), sum == null ? 0 : sum.modulesOn());
            g.text(s, g.fit(s, line, w - 80f), x + 40f, ry + 35f, 0x8CFFFFFF);
            if (n.equals(active)) {
                g.textRight(s, "✓", x + w - 20f, ry + 22f, tk.accent);
            }
            ry += 46f;
        }
        ui.button(tk, x + w - 8f, ry + 18f, tr("sol.menu.save_profile"), false, () -> {
            popover = false;
            String saved = SolProfiles.saveAs(SolProfiles.newName());
            if (saved != null) {
                toast(tr("sol.notify.profile_saved", saved));
            }
        });
    }

    private void status(Gfx g, Tokens tk) {
        float k = calm() ? 1f : Math.max(0f, Math.min(1f, (openT - 0.35f) / 0.3f));
        if (k <= 0f) {
            return;
        }
        float base = g.alpha;
        g.alpha = base * k;
        SolFont f = g.font(SolFont.Family.MONO, 11.5f);
        float y = 720f - 14f - 6f;
        float x = 40f;
        g.glowSprite(x + 3f, y, 5f, 5f, Colors.withAlpha(tk.accent, 0.8f));
        g.circle(x + 3f, y, 3f, tk.accent);
        x += 13f;
        int on = 0;
        for (Module m : Modules.all()) {
            on += m.available() && m.enabled() ? 1 : 0;
        }
        String[] parts = {world(), minecraft.getFps() + " FPS", ping(), tr("sol.menu.status.world", Widgets.themeName(ThemeState.baseEntry())),
                tr("sol.menu.status.profile", SolProfiles.active()), tr("sol.menu.status.modules", on)};
        for (String p : parts) {
            if (p == null || p.isEmpty()) {
                continue;
            }
            g.textMid(f, p, x, y, 0x99FFFFFF);
            x += g.width(f, p) + 18f;
        }
        String playing = io.github.georgwav.cosmetics.sol.music.MusicService.line();
        String right = playing != null ? g.fit(f, playing, 360f) : tr("sol.quick.detail.music_none");
        g.textRight(f, right, 1240f, y, 0x99FFFFFF);
        if (playing != null) {
            io.github.georgwav.cosmetics.sol.ui.kit.Icons.draw(g, "note", 1240f - g.width(f, right) - 16f, y - 6f, 12f, 0x99FFFFFF,
                    io.github.georgwav.cosmetics.sol.ui.kit.Icons.FILL, 0);
        }
        g.alpha = base;
    }

    private String world() {
        if (minecraft.level == null) {
            return tr("sol.menu.status.menu");
        }
        if (minecraft.hasSingleplayerServer()) {
            return tr("sol.menu.status.singleplayer");
        }
        var data = minecraft.getCurrentServer();
        return data == null || io.github.georgwav.cosmetics.sol.client.Streamer.on() ? tr("sol.menu.status.multiplayer") : data.ip;
    }

    private String ping() {
        if (minecraft.getConnection() == null || minecraft.player == null) {
            return "";
        }
        var info = minecraft.getConnection().getPlayerInfo(minecraft.player.getUUID());
        return info == null ? "" : info.getLatency() + " ms";
    }

    // ------------------------------------------------------------------ input

    @Override
    protected boolean press(float x, float y, int button) {
        if (button != 0) {
            return false;
        }
        if (palette != null) {
            return palette.press(x, y) || true;
        }
        boolean hit = ui.press(x, y);
        if (popover && !hit) {
            popover = false;
            return true;
        }
        return hit;
    }

    @Override
    protected boolean drag(float x, float y, int button) {
        return ui.dragTo(x, y);
    }

    @Override
    protected boolean release(float x, float y, int button) {
        return ui.release();
    }

    @Override
    protected boolean scroll(float x, float y, float amount) {
        if (palette != null) {
            return palette.scroll(amount);
        }
        if (drawer != null && x >= 1280f - 40f - DRAWER_W) {
            return drawer.scroll(amount);
        }
        return sections.get(section).scroll(amount);
    }

    @Override
    protected boolean key(int key, boolean ctrl, boolean shift) {
        if (palette != null) {
            return palette.key(key, ctrl);
        }
        if (KeyBinds.matches(KeyBinds.quickMenu(), key) && !ctrl) {
            onClose();
            return true;
        }
        if (ctrl && key == InputConstants.KEY_K) {
            openPalette("");
            return true;
        }
        if (sections.get(section).key(key, ctrl)) {
            return true;
        }
        if (drawer != null && drawer.key(key, ctrl)) {
            return true;
        }
        switch (key) {
            case InputConstants.KEY_TAB -> {
                ui.tab(shift);
                return true;
            }
            case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER, InputConstants.KEY_SPACE -> {
                return ui.activate();
            }
            default -> {
            }
        }
        return false;
    }

    @Override
    protected boolean typed(String text) {
        if (palette != null) {
            return palette.typed(text);
        }
        if (sections.get(section).typed(text) || drawer != null && drawer.typed(text)) {
            return true;
        }
        if (!io.github.georgwav.cosmetics.sol.config.SolConfig.get().searchNeedsCtrl && !text.isBlank()) {
            openPalette(text);
            return true;
        }
        return false;
    }

    @Override
    protected boolean back() {
        if (palette != null) {
            palette = null;
            return true;
        }
        if (popover) {
            popover = false;
            return true;
        }
        if (drawer != null) {
            drawer = null;
            return true;
        }
        if (fromQuick) {
            boolean me = section.equals("wardrobe");
            ScreenOpener.open(new QuickMenu(parent, me, me ? "wardrobe" : section));
            return true;
        }
        return false;
    }

    @Override
    protected void closed() {
        logo.release();
        if (sunTex != null) {
            sunTex.release();
        }
        sections.values().forEach(Section::closed);
        Modules.save();
        SolProfiles.flush();
    }

    static float pop(float k) {
        float c1 = 1.70158f, c3 = c1 + 1f, x = k - 1f;
        return 1f + c3 * x * x * x + c1 * x * x;
    }
}
