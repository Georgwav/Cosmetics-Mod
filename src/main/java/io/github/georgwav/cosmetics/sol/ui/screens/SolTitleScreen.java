package io.github.georgwav.cosmetics.sol.ui.screens;

import io.github.georgwav.cosmetics.sol.Feature;
import io.github.georgwav.cosmetics.sol.config.SolConfig;
import io.github.georgwav.cosmetics.sol.render.SolTexture;
import io.github.georgwav.cosmetics.sol.theme.ThemeRegistry;
import io.github.georgwav.cosmetics.sol.theme.ThemeState;
import io.github.georgwav.cosmetics.sol.theme.Tokens;
import io.github.georgwav.cosmetics.sol.ui.kit.Gfx;
import io.github.georgwav.cosmetics.sol.ui.kit.Icons;
import io.github.georgwav.cosmetics.sol.ui.kit.LogoRenderer;
import io.github.georgwav.cosmetics.sol.ui.kit.PlayerFace;
import io.github.georgwav.cosmetics.sol.ui.kit.SolFont;
import io.github.georgwav.cosmetics.sol.ui.kit.SolScreen;
import io.github.georgwav.cosmetics.sol.ui.kit.Ui;
import io.github.georgwav.cosmetics.sol.util.Colors;
import io.github.georgwav.cosmetics.sol.util.MiniSky;
import io.github.georgwav.cosmetics.sol.util.SolExecutors;
import io.github.georgwav.cosmetics.ui.ScreenOpener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Sol's title screen (with Sol UI on): the theme's own landscape alive behind everything (the prototype's sky, sun,
 * ridges and particles, drawn off the render thread about 20 times a second), the live logo, glass pills for
 * playing, and small round buttons for the Sol menu, mods, options and quitting. Replaces the game's title screen
 * whenever the game opens it ({@link #swap}); the game's own comes back with Sol UI off or after an error.
 */
public final class SolTitleScreen extends SolScreen {
    private static final int SKY_W = 480, SKY_H = 270;
    private final LogoRenderer logo = new LogoRenderer(false);
    private final Ui ui = new Ui();
    private final AtomicReference<int[]> ready = new AtomicReference<>();
    private volatile boolean rendering;
    private long lastStart;
    private SolTexture sky;
    private boolean skyShown;

    public SolTitleScreen() {
        super(Component.translatable("narrator.screen.title"), null, Feature.TITLE);
    }

    /** The screen the game opens instead of its own title screen (the game's own one only, not a mod's subclass). */
    public static Screen swap(Screen screen) {
        if (screen != null && screen.getClass() == TitleScreen.class && SolConfig.get().solUi
                && io.github.georgwav.cosmetics.sol.Guard.on(Feature.TITLE)) {
            return new SolTitleScreen();
        }
        return screen;
    }

    @Override
    protected boolean blurs() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    protected boolean back() {
        return true;
    }

    @Override
    public void onClose() {
        // Only after an error: the game's own title screen (the swap lets it through now).
        ScreenOpener.open(new TitleScreen());
    }

    @Override
    protected void closed() {
        logo.release();
        if (sky != null) {
            sky.release();
            sky = null;
        }
    }

    @Override
    protected boolean press(float x, float y, int button) {
        return ui.press(x, y);
    }

    @Override
    protected boolean release(float x, float y, int button) {
        return ui.release();
    }

    @Override
    protected boolean key(int key, boolean ctrl, boolean shift) {
        if (key == com.mojang.blaze3d.platform.InputConstants.KEY_TAB) {
            ui.tab(shift);
            return true;
        }
        if (key == com.mojang.blaze3d.platform.InputConstants.KEY_RETURN || key == com.mojang.blaze3d.platform.InputConstants.KEY_SPACE) {
            return ui.activate();
        }
        return false;
    }

    /** The landscape: the next frame starts on the CPU thread once the last one is in, at most 20 a second. */
    private void sky(ThemeRegistry.Entry e) {
        long now = System.nanoTime();
        if (!rendering && now - lastStart > 50_000_000L) {
            rendering = true;
            lastStart = now;
            float t = time;
            var spec = e.base();
            String id = e.id();
            SolExecutors.CPU.execute(() -> {
                try {
                    ready.set(MiniSky.render(spec, id, SKY_W, SKY_H, t).toArgb(null));
                } finally {
                    rendering = false;
                }
            });
        }
        int[] px = ready.getAndSet(null);
        if (px != null) {
            if (sky == null || sky.released()) {
                sky = new SolTexture("title_sky", SKY_W, SKY_H, true);
            }
            sky.upload(px);
            skyShown = true;
        }
    }

    @Override
    protected void frame(Gfx g) {
        Tokens tk = ThemeState.tokens();
        ThemeRegistry.Entry e = ThemeState.entry();
        sky(e);
        ui.begin(g, mx, my);
        float fade = calm() ? 1f : Math.min(1f, time / 0.8f);
        // The landscape, filling the window (cropped to keep its shape).
        float sw = g.d.screenW, sh = g.d.screenH;
        g.rect(g.d.left(), g.d.top(), g.d.right() - g.d.left(), g.d.bottom() - g.d.top(), 0xFF000000);
        if (skyShown) {
            float aspect = (float) SKY_W / SKY_H, screen = sw / sh;
            float u0 = 0f, u1 = 1f, v0 = 0f, v1 = 1f;
            if (screen > aspect) {
                float k = aspect / screen;
                v0 = (1f - k) / 2f;
                v1 = 1f - v0;
            } else {
                float k = screen / aspect;
                u0 = (1f - k) / 2f;
                u1 = 1f - u0;
            }
            g.imageScreen(sky, 0f, 0f, sw, sh, u0, v0, u1, v1, Colors.withAlpha(0xFFFFFFFF, fade), false);
        }
        g.alpha = fade;
        // A soft veil at the bottom, for the buttons and the footer.
        g.vgradient(g.d.left(), 360f, g.d.right() - g.d.left(), g.d.bottom() - 360f, 0x00000000, 0xA6050508);

        // The logo, three times the menu's, rising a little as it fades in.
        float rise = (1f - fade) * 14f;
        SolTexture t = logo.texture(ThemeState.baseEntry(), time, dt);
        float lw = LogoRenderer.W * 3.2f, lh = LogoRenderer.H * 3.2f;
        g.image(t, 640f - lw / 2f, 70f + rise, lw, lh, 0f, 0f, 1f, 1f, 0xFFFFFFFF, false);

        // The main pills.
        SolFont big = g.font(SolFont.Family.SEMIBOLD, 15f);
        String[] labels = {tr("menu.singleplayer"), tr("menu.multiplayer"), tr("menu.online")};
        Runnable[] actions = {this::singleplayer, this::multiplayer, this::realms};
        for (int i = 0; i < labels.length; i++) {
            pill(g, tk, big, 640f, 404f + i * 52f, 300f, 42f, labels[i], i == 0, actions[i]);
        }
        // The round buttons: the Sol menu, mods, options, quit.
        String[] icons = {"sun", "puzzle", "gear", "close"};
        String[] tips = {"sol.title.menu", "sol.title.mods", "menu.options", "menu.quit"};
        Runnable[] round = {this::solMenu, this::mods, this::options, this::quit};
        float rx = 640f - (icons.length * 46f - 12f) / 2f;
        SolFont small = g.font(SolFont.Family.MEDIUM, 11f);
        for (int i = 0; i < icons.length; i++) {
            float x = rx + i * 46f, y = 572f;
            int fi = ui.click(x, y, 34f, 34f, round[i]);
            boolean hover = ui.over(x, y, 34f, 34f);
            g.glow(x, y, 34f, 34f, 17f, 10f, Colors.withAlpha(tk.glow, hover ? 0.3f : 0.12f));
            g.round(x, y, 34f, 34f, 17f, Colors.withAlpha(tk.panel, Math.max(0.6f, tk.glass)));
            g.stroke(x, y, 34f, 34f, 17f, Math.max(1f, g.d.u), hover ? tk.rim : 0x33FFFFFF);
            Icons.draw(g, icons[i], x + 8f, y + 8f, 18f, hover ? tk.accent : 0xE6FFFFFF, hover ? Icons.FILL_ON : Icons.FILL, 0);
            if (ui.focused(fi)) {
                g.stroke(x - 3f, y - 3f, 40f, 40f, 20f, 2f, tk.accent);
            }
            if (hover) {
                String tip = tr(tips[i]);
                g.textCentre(small, tip, x + 17f, y + 48f, 0xCCFFFFFF);
            }
        }

        // The player at the top right.
        var mc = net.minecraft.client.Minecraft.getInstance();
        SolFont name = g.font(SolFont.Family.SEMIBOLD, 13f);
        String user = mc.getUser().getName();
        float nw = g.width(name, user) + 56f, px = g.d.right() - 24f - nw;
        g.round(px, 22f, nw, 40f, 20f, Colors.withAlpha(tk.panel, Math.max(0.6f, tk.glass)));
        g.stroke(px, 22f, nw, 40f, 20f, Math.max(1f, g.d.u), 0x33FFFFFF);
        PlayerFace.draw(g, px + 6f, 28f, 28f, 0f);
        g.textMid(name, user, px + 42f, 42f, 0xFFFFFFFF);

        // The footer: versions at the left, Mojang's line at the right (it opens the credits).
        SolFont mono = g.font(SolFont.Family.MONO, 11f);
        String version = "Minecraft " + version("minecraft") + "  ·  Sol Client " + version("cosmeticsmod")
                + "  ·  " + io.github.georgwav.cosmetics.sol.ui.kit.Widgets.themeName(ThemeState.baseEntry());
        g.textMid(mono, version, g.d.left() + 24f, 696f, 0x99FFFFFF);
        String copy = tr("title.credits");
        float cw = g.width(mono, copy);
        ui.click(g.d.right() - 24f - cw, 686f, cw, 20f, () -> ScreenOpener.open(
                new net.minecraft.client.gui.screens.CreditsAndAttributionScreen(this)));
        g.textRight(mono, copy, g.d.right() - 24f, 696f, ui.over(g.d.right() - 24f - cw, 686f, cw, 20f) ? 0xFFFFFFFF : 0x99FFFFFF);
        g.alpha = 1f;
    }

    private void pill(Gfx g, Tokens tk, SolFont f, float cx, float y, float w, float h, String text, boolean accent, Runnable action) {
        float x = cx - w / 2f;
        int fi = ui.click(x, y, w, h, action);
        boolean hover = ui.over(x, y, w, h);
        g.glow(x, y, w, h, h / 2f, hover ? 18f : 12f, Colors.withAlpha(accent ? tk.accent : tk.glow, hover ? 0.32f : 0.14f));
        g.round(x, y, w, h, h / 2f, accent ? Colors.withAlpha(tk.accent, hover ? 1f : 0.92f)
                : Colors.withAlpha(tk.panel, Math.max(0.62f, tk.glass)));
        g.stroke(x, y, w, h, h / 2f, Math.max(1f, g.d.u), accent ? Colors.mix(tk.accent, 0xFFFFFFFF, 0.3f) : hover ? tk.rim : 0x33FFFFFF);
        g.highlight(x, y, w, h, h / 2f, accent ? 0x59FFFFFF : 0x38FFFFFF);
        g.textCentre(f, text, cx, y + h / 2f, accent ? tk.onAccent : 0xFFFFFFFF);
        if (ui.focused(fi)) {
            g.stroke(x - 3f, y - 3f, w + 6f, h + 6f, h / 2f + 3f, 2f, tk.accent);
        }
    }

    private static String tr(String key) {
        return Component.translatable(key).getString();
    }

    private static String version(String mod) {
        return net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer(mod)
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?");
    }

    private void singleplayer() {
        ScreenOpener.open(new net.minecraft.client.gui.screens.worldselection.SelectWorldScreen(this));
    }

    private void multiplayer() {
        ScreenOpener.open(minecraft.options.skipMultiplayerWarning
                ? new net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen(this)
                : new net.minecraft.client.gui.screens.multiplayer.SafetyScreen(this));
    }

    private void realms() {
        ScreenOpener.open(new com.mojang.realmsclient.RealmsMainScreen(this));
    }

    private void solMenu() {
        ScreenOpener.open(io.github.georgwav.cosmetics.sol.ui.menu.SolMenu.create(this, "modules", false));
    }

    private void mods() {
        ScreenOpener.open(io.github.georgwav.cosmetics.sol.ui.menu.SolMenu.create(this, "mods", false));
    }

    private void options() {
        //? if >=26.1 && <26.3 {
        /*ScreenOpener.open(new net.minecraft.client.gui.screens.options.OptionsScreen(this, minecraft.options, false));
        *///?} else {
        ScreenOpener.open(new net.minecraft.client.gui.screens.options.OptionsScreen(this, minecraft.options));
        //?}
    }

    private void quit() {
        minecraft.stop();
    }
}
