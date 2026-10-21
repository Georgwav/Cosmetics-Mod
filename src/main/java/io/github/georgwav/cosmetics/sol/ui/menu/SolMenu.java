package io.github.georgwav.cosmetics.sol.ui.menu;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.georgwav.cosmetics.client.Module;
import io.github.georgwav.cosmetics.client.Modules;
import io.github.georgwav.cosmetics.sol.Feature;
import io.github.georgwav.cosmetics.sol.config.SolProfiles;
import io.github.georgwav.cosmetics.sol.theme.ThemeRegistry;
import io.github.georgwav.cosmetics.sol.theme.ThemeState;
import io.github.georgwav.cosmetics.sol.theme.Tokens;
import io.github.georgwav.cosmetics.sol.ui.kit.Gfx;
import io.github.georgwav.cosmetics.sol.ui.kit.Icons;
import io.github.georgwav.cosmetics.sol.ui.kit.Notifications;
import io.github.georgwav.cosmetics.sol.ui.kit.Pix;
import io.github.georgwav.cosmetics.sol.ui.kit.PixelTokens;
import io.github.georgwav.cosmetics.sol.ui.kit.SolFont;
import io.github.georgwav.cosmetics.sol.ui.kit.SolScreen;
import io.github.georgwav.cosmetics.sol.ui.kit.Ui;
import io.github.georgwav.cosmetics.sol.ui.kit.Widgets;
import io.github.georgwav.cosmetics.sol.ui.quick.QuickMenu;
import io.github.georgwav.cosmetics.sol.util.Colors;
import io.github.georgwav.cosmetics.ui.KeyBinds;
import io.github.georgwav.cosmetics.ui.ScreenOpener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * The full Sol menu as the pixel UI's window (A.7.2): 1200 x 642 at (40, 62). Its 60 px title bar holds Sol's icon,
 * back, the tabs (Sol's icon first, back to the quick menu; the tab you're on wide with its name, the rest icons),
 * your slab (the profiles) and the red close; Sol's horizon runs under it with the sun laid flat on it under the tab
 * you're on. The tab's page fills the body; a module's settings or the theme's page open over the window; Ctrl K
 * searches; the status line is the window's footer. The same screen Mod Menu's Configure opens.
 */
public final class SolMenu extends SolScreen {
    /** The tabs after Sol's icon, in order. */
    public static final String[] TABS = WindowBar.TABS;
    /** The pages search lists. */
    public static final String[] STOPS = TABS;
    /** Frames drawn (the in-game check). */
    public static int framesRendered;
    /** The body under the title bar (design px): 22 px in from the window's sides, the footer under it. */
    static final float BODY_X = Pix.WIN_X + Pix.E + 22f, BODY_Y = Pix.WIN_Y + Pix.BAR_H + Pix.E + 16f,
            BODY_W = Pix.WIN_W - 2f * Pix.E - 44f, BODY_BOTTOM = Pix.WIN_Y + Pix.WIN_H - Pix.E - 28f;

    private String section;
    private final boolean fromQuick;
    final Ui ui = new Ui();
    private final Map<String, Section> sections = new HashMap<>();
    private final WindowBar bar = new WindowBar();
    private final WindowBar.Host host = new WindowBar.Host() {
        @Override
        public String current() {
            return section;
        }

        @Override
        public void go(String tab) {
            SolMenu.this.go(tab);
        }

        @Override
        public void quickMenu() {
            ScreenOpener.open(new QuickMenu(parent));
        }

        @Override
        public void back() {
            backOut();
        }

        @Override
        public void close() {
            onClose();
        }

        @Override
        public void profiles() {
            if (popover) {
                popover = false;
            } else {
                openPopover();
            }
        }

        @Override
        public boolean profilesOpen() {
            return popover;
        }

        @Override
        public String backTip() {
            return tr(fromQuick ? "sol.menu.back_quick" : "sol.menu.back");
        }
    };
    private float sectionT;
    private Page drawer;
    private CommandBar palette;
    private boolean popover;
    /** Each saved profile's theme, read when the popover opens. */
    private final Map<String, SolProfiles.Summary> profileThemes = new HashMap<>();

    private SolMenu(Screen parent, String section, boolean fromQuick) {
        super(Component.translatable("sol.menu.title"), parent, Feature.MENU);
        this.section = indexOf(section) >= 0 ? section : "modules";
        this.fromQuick = fromQuick;
        sections.put("modules", new ModulesSection());
        sections.put("hud", new HudSection());
        sections.put("world", new WorldSection());
        sections.put("presets", new PresetsSection());
        sections.put("wardrobe", new WardrobeSection());
        sections.put("emotes", new EmotesSection());
        sections.put("music", new MusicSection());
        sections.put("mods", new ModsSection());
        sections.put("settings", new SettingsSection());
    }

    public static SolMenu create(Screen parent, String section, boolean fromQuick) {
        return new SolMenu(parent, section, fromQuick);
    }

    /** Opens the full menu on a page. */
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
        return WindowBar.indexOf(id);
    }

    /**
     * For the dev scenario: one of the states the reference shots show (a page, the outfits tab, a module's settings,
     * the command bar, the profiles).
     */
    public SolMenu stage(String state) {
        switch (state) {
            case "profiles" -> openPopover();
            case "cmdbar" -> openPalette("");
            case "music_lyrics" -> {
                go("music");
                ((MusicSection) sections.get("music")).stage("lyrics");
            }
            case "world_night" -> {
                go("world");
                ((WorldSection) sections.get("world")).showNight();
            }
            case "theme" -> {
                go("world");
                openTheme(false);
            }
            case "theme_night" -> {
                go("world");
                openTheme(true);
            }
            case "settings_about" -> {
                go("settings");
                ((SettingsSection) sections.get("settings")).showAbout();
            }
            default -> {
                if (state.startsWith("drawer_")) {
                    go("modules");
                    // "drawer_advanced_<id>": with Advanced open, scrolled to it; "drawer_<id>__<words>": its settings
                    // searched for those words.
                    boolean advanced = state.startsWith("drawer_advanced_");
                    String rest = state.substring(advanced ? "drawer_advanced_".length() : "drawer_".length());
                    int words = rest.indexOf("__");
                    Module m = io.github.georgwav.cosmetics.client.Modules.byId(words < 0 ? rest : rest.substring(0, words));
                    if (m != null) {
                        openDrawer(m);
                        if (advanced && drawer() != null) {
                            drawer().showAdvanced();
                        }
                        if (words >= 0 && drawer() != null) {
                            drawer().showSearch(rest.substring(words + 2).replace('_', ' '));
                        }
                    }
                } else {
                    go(state);
                }
            }
        }
        return this;
    }

    // ------------------------------------------------------------------ what pages use

    public String section() {
        return section;
    }

    public void go(String id) {
        if (id.equals(section) || indexOf(id) < 0) {
            return;
        }
        sections.get(section).left();
        section = id;
        sectionT = 0f;
        closeDrawer();
        sections.get(id).opened();
    }

    private void openPopover() {
        popover = true;
        profileThemes.clear();
        for (String n : SolProfiles.names()) {
            profileThemes.put(n, SolProfiles.summary(n));
        }
    }

    /** Opens a module's settings page over the menu. */
    public void openDrawer(Module m) {
        if (drawer != null) {
            drawer.release();
        }
        drawer = new ModulePage(m);
    }

    /** Opens the theme's page over the menu ({@code night}: at its night). */
    public void openTheme(boolean night) {
        if (drawer != null) {
            drawer.release();
        }
        drawer = new ThemePage(night);
    }

    public void closeDrawer() {
        if (drawer != null) {
            drawer.release();
        }
        drawer = null;
    }

    /** The module page open over the menu, or null (none, or the theme's page). */
    public ModulePage drawer() {
        return drawer instanceof ModulePage m ? m : null;
    }

    /** Whether a page (a module's or the theme's) is open over the menu. */
    public boolean pageOpen() {
        return drawer != null;
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

    /**
     * A page's head (the prototype's .whead): a round "i", a line in Silkscreen 15 and, after it, a count in pixel 11.
     * Returns where the line ends.
     */
    static float head(Gfx g, String line, String count, float x, float cy) {
        PixelTokens p = PixelTokens.get();
        // The "i": a 28 px disc of pale ink with a dark pixel "i".
        g.circle(x + 14f, cy, 14f, 0xFFD9D4DC);
        SolFont i = g.font(SolFont.Family.PIXEL_BOLD, 14f);
        g.textCentre(i, "i", x + 14f, cy, 0xFF232326);
        SolFont h2 = g.font(SolFont.Family.PIXEL_BOLD, 15f);
        float tx = x + 28f + 12f;
        g.textMid(h2, line, tx, cy, PixelTokens.INK);
        float end = tx + g.width(h2, line);
        if (count != null && !count.isEmpty()) {
            SolFont c = g.font(SolFont.Family.PIXEL, 11f);
            g.textMid(c, count, end + 12f, cy, PixelTokens.INK3);
            end += 12f + g.width(c, count);
        }
        return end;
    }

    // ------------------------------------------------------------------ frame

    @Override
    protected void frame(Gfx g) {
        framesRendered++;
        Tokens tk = ThemeState.tokens();
        PixelTokens p = PixelTokens.get();
        sectionT += dt;
        ui.begin(g, mx, my);

        Pix.window(g, p);
        bar.draw(g, ui, tk, time, dt, host);
        Section s = sections.get(section);
        s.draw(this, g, ui, tk, BODY_X, BODY_Y, BODY_W, BODY_BOTTOM - BODY_Y);
        footer(g, p);
        if (drawer != null) {
            // A module's page or the theme's over the window, the window dimmed flat behind it.
            g.rect(g.d.left(), g.d.top(), g.d.right() - g.d.left(), g.d.bottom() - g.d.top(), 0x99000000);
            drawer.draw(this, g, ui, tk);
        }
        if (popover) {
            popover(g, p, tk);
        }
        if (palette != null) {
            palette.draw(g, ui, tk);
        }
    }

    /** The status line as the window's footer, in pixel type. */
    private void footer(Gfx g, PixelTokens p) {
        SolFont f = g.font(SolFont.Family.PIXEL, 10f);
        float y = Pix.WIN_Y + Pix.WIN_H - Pix.E - 14f, x = BODY_X;
        int on = 0;
        for (Module m : Modules.all()) {
            on += m.available() && m.enabled() ? 1 : 0;
        }
        String[] parts = {world(), minecraft.getFps() + " FPS", ping(), tr("sol.menu.status.world", Widgets.themeName(ThemeState.baseEntry())),
                tr("sol.menu.status.profile", SolProfiles.active()), tr("sol.menu.status.modules", on)};
        for (String s : parts) {
            if (s == null || s.isEmpty()) {
                continue;
            }
            g.textMid(f, s, x, y, 0x73FFFFFF);
            x += g.width(f, s) + 18f;
        }
        String playing = io.github.georgwav.cosmetics.sol.music.MusicService.line();
        String right = playing != null ? g.fit(f, playing, 360f) : tr("sol.quick.detail.music_none");
        float rx = BODY_X + BODY_W;
        g.textRight(f, right, rx, y, 0x73FFFFFF);
        if (playing != null) {
            Icons.draw(g, "note", rx - g.width(f, right) - 18f, y - 6f, 12f, 0x73FFFFFF, 0f, 0);
        }
    }

    private void popover(Gfx g, PixelTokens p, Tokens tk) {
        float w = 340f, x = Pix.WIN_X + Pix.WIN_W - Pix.E - WindowBar.CLOSE_W - 8f - w, y = WindowBar.Y + WindowBar.TAB + 8f;
        java.util.List<String> names = SolProfiles.names();
        float rowH = 50f, h = 12f + 24f + names.size() * (rowH + 4f) + 8f + 42f + 12f;
        Pix.slab(g, p, x, y, w, h, Pix.NORMAL);
        ui.area(x, y, w, h, () -> { });
        SolFont head = g.font(SolFont.Family.PIXEL, 11f), b = g.font(SolFont.Family.PIXEL_BOLD, 13f), s = g.font(SolFont.Family.MEDIUM, 11.5f);
        g.textMid(head, tr("sol.menu.profiles"), x + 14f, y + 24f, PixelTokens.INK3);
        float ry = y + 12f + 24f;
        String active = SolProfiles.active();
        for (String n : names) {
            SolProfiles.Summary sum = profileThemes.get(n);
            ThemeRegistry.Entry th = ThemeRegistry.get(sum == null ? ThemeRegistry.FALLBACK : sum.theme());
            boolean on = n.equals(active), over = ui.over(x + 8f, ry, w - 16f, rowH);
            Pix.slab(g, p, x + 8f, ry, w - 16f, rowH, on ? Pix.ON : over ? Pix.HOVER : Pix.NORMAL);
            int fi = ui.click(x + 8f, ry, w - 16f, rowH, () -> {
                popover = false;
                SolProfiles.switchTo(n);
                toast(tr("sol.notify.profile", n));
            });
            int acc = sum != null && sum.accent() != null ? Colors.parse(sum.accent(), th.base().ui().accent()) : th.base().ui().accent();
            Pix.swatch(g, p, x + 20f, ry + 13f, 30f, 24f, acc, false);
            g.textMid(b, g.fit(b, n, w - 100f), x + 62f, ry + 18f, Pix.text(p, on ? Pix.ON : over ? Pix.HOVER : Pix.NORMAL));
            String line = tr("sol.menu.profile_line", Widgets.themeName(th), sum == null ? 0 : sum.modulesOn());
            g.textMid(s, g.fit(s, line, w - 100f), x + 62f, ry + 35f, PixelTokens.INK2);
            if (on) {
                Icons.draw(g, "check", x + w - 44f, ry + 13f, 24f, p.acc, 0f, 0);
            }
            if (ui.focused(fi)) {
                Pix.focus(g, x + 8f, ry, w - 16f, rowH);
            }
            ry += rowH + 4f;
        }
        ui.bigButton(x + 8f, ry + 8f, "plus", tr("sol.menu.save_profile"), false, true, () -> {
            popover = false;
            String saved = SolProfiles.saveAs(SolProfiles.newName());
            if (saved != null) {
                toast(tr("sol.notify.profile_saved", saved));
            }
        });
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

    @Override
    protected void game(io.github.georgwav.cosmetics.ui.Canvas canvas, int mouseX, int mouseY) {
        if (palette == null && !popover) {
            if (drawer != null) {
                drawer.game(canvas);
            } else {
                sections.get(section).game(this, canvas, mouseX, mouseY, dt);
            }
        }
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
        if (drawer != null) {
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
        if (drawer != null && drawer.key(key, ctrl)) {
            return true;
        }
        if (drawer == null && sections.get(section).key(key, ctrl)) {
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
        if (drawer != null ? drawer.typed(text) : sections.get(section).typed(text)) {
            return true;
        }
        if (!io.github.georgwav.cosmetics.sol.config.SolConfig.get().searchNeedsCtrl && !text.isBlank()) {
            openPalette(text);
            return true;
        }
        return false;
    }

    /** The back slab: what Esc does with nothing open over the window. */
    private void backOut() {
        if (!back()) {
            onClose();
        }
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
            closeDrawer();
            return true;
        }
        if (fromQuick) {
            boolean me = section.equals("wardrobe") || section.equals("presets") || section.equals("emotes");
            ScreenOpener.open(new QuickMenu(parent, me, me ? section : indexOf(section) >= 0 && isMain(section) ? section : "modules"));
            return true;
        }
        return false;
    }

    /** Whether a page is one of the quick menu's main sections. */
    private static boolean isMain(String id) {
        return switch (id) {
            case "hud", "modules", "world", "music", "settings", "mods" -> true;
            default -> false;
        };
    }

    @Override
    protected void closed() {
        // A page still open (Esc or the menu key closes the menu over it): its pictures go too.
        closeDrawer();
        bar.release();
        sections.values().forEach(Section::closed);
        Modules.save();
        SolProfiles.flush();
    }
}
