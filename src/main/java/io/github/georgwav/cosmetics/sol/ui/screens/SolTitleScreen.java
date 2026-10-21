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
import io.github.georgwav.cosmetics.sol.ui.kit.Pix;
import io.github.georgwav.cosmetics.sol.ui.kit.PixelTokens;
import io.github.georgwav.cosmetics.sol.ui.kit.PlayerFace;
import io.github.georgwav.cosmetics.sol.ui.kit.SolFont;
import io.github.georgwav.cosmetics.sol.ui.kit.SolScreen;
import io.github.georgwav.cosmetics.sol.ui.kit.Ui;
import io.github.georgwav.cosmetics.sol.util.MiniSky;
import io.github.georgwav.cosmetics.sol.util.SolExecutors;
import io.github.georgwav.cosmetics.ui.ScreenOpener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Sol's title screen (with Sol UI on, A.7.4): the theme's own landscape alive behind everything (the prototype's sky,
 * sun, ridges and particles, drawn off the render thread about 20 times a second), the live logo, three big slabs
 * (Singleplayer in the accent, Multiplayer, the Sol menu), a small row for Realms, mods, options and quitting, you
 * at the top right and standing at the bottom right (both open the Wardrobe), and the footer in pixel type. Replaces the game's title screen
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
    /** You on a little island beside the buttons, turning slowly, with what you wear. */
    private final io.github.georgwav.cosmetics.ui.Mannequin figure = new io.github.georgwav.cosmetics.ui.Mannequin("title", 256);

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
    protected boolean openSound() {
        return false;
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
        figure.release();
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

    @Override
    protected void game(io.github.georgwav.cosmetics.ui.Canvas c, int mouseX, int mouseY) {
        // You, bottom right: the Mannequin in its 240 x 288 box at (940, 344), with nothing under you.
        double gs = minecraft.getWindow().getGuiScale();
        int x1 = (int) Math.round(d.sx(FIG_X) / gs), x2 = (int) Math.round(d.sx(FIG_X + FIG_W) / gs);
        int y1 = (int) Math.round(d.sy(FIG_Y) / gs), y2 = (int) Math.round(d.sy(FIG_Y + FIG_H) / gs);
        int side = Math.min(x2 - x1, y2 - y1);
        float yaw = calm() ? 25f : 25f + (float) Math.sin(time * 0.35f) * 35f;
        figure.draw(c, (x1 + x2 - side) / 2, y2 - side, side, yaw, 14f, false);
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

    /** Updates are looked for once per start, the first time the title screen shows. */
    private static boolean updatesChecked;
    /** The Mannequin's box (A.7.4). */
    private static final float FIG_X = 940f, FIG_Y = 344f, FIG_W = 240f, FIG_H = 288f;

    @Override
    protected void frame(Gfx g) {
        if (!updatesChecked) {
            updatesChecked = true;
            io.github.georgwav.cosmetics.sol.client.Updates.daily();
            io.github.georgwav.cosmetics.sol.client.Updates.afterUpdate();
        }
        Tokens tk = ThemeState.tokens();
        PixelTokens p = PixelTokens.get();
        ThemeRegistry.Entry e = ThemeState.entry();
        sky(e);
        ui.begin(g, mx, my);
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
            g.imageScreen(sky, 0f, 0f, sw, sh, u0, v0, u1, v1, 0xFFFFFFFF, false);
        }

        // The logo at three times the menu's, on whole screen pixels.
        SolTexture t = logo.texture(ThemeState.baseEntry(), time, dt);
        int k = Math.max(1, Math.round(3f * g.d.u));
        float lw = LogoRenderer.W * k, lh = LogoRenderer.H * k;
        float lx = Math.round(g.d.sx(640f) - lw / 2f), ly = Math.round(g.d.sy(30f));
        g.imageScreen(t, lx, ly, lx + lw, ly + lh, 0f, 0f, 1f, 1f, 0xFFFFFFFF, false);

        // The three buttons, 360 x 56 from y 266: Singleplayer in the accent.
        SolFont big = g.font(SolFont.Family.PIXEL_BOLD, 16f);
        String[] labels = {tr("menu.singleplayer"), tr("menu.multiplayer"), tr("sol.title.menu")};
        String[] icons = {"person", "people", "solsq"};
        Runnable[] actions = {this::singleplayer, this::multiplayer, this::solMenu};
        for (int i = 0; i < labels.length; i++) {
            float bx = 460f, by = 266f + i * (56f + 12f), bw = 360f, bh = 56f;
            boolean over = ui.over(bx, by, bw, bh);
            int st = i == 0 ? Pix.ON : ui.pressed(bx, by, bw, bh) ? Pix.PRESSED : over ? Pix.HOVER : Pix.NORMAL;
            if (i == 0 && over) {
                Pix.slab(g, p, bx, by, bw, bh, Pix.ON);
                Pix.fill(g, bx + Pix.E, by + Pix.E, bw - 2f * Pix.E, bh - 2f * Pix.E, 0x14FFFFFF);
            } else {
                Pix.slab(g, p, bx, by, bw, bh, st);
            }
            float dy = st == Pix.PRESSED ? Pix.E : 0f;
            int c = Pix.text(p, st);
            float tw = 24f + 14f + g.width(big, labels[i]), tx = bx + (bw - tw) / 2f;
            Icons.draw(g, icons[i], tx, by + bh / 2f - 12f + dy, 24f, c, 0f, 0);
            g.textMid(big, labels[i], tx + 24f + 14f, by + bh / 2f + dy, c);
            int fi = ui.click(bx, by, bw, bh, actions[i]);
            if (ui.focused(fi)) {
                Pix.focus(g, bx, by, bw, bh);
            }
        }
        // The small row at y 470: Realms, Mods, Options and the red Quit, each named under it.
        String[] rIcons = {"globe", "box", "gear", "x"};
        String[] names = {"sol.title.realms", "sol.title.mods", "sol.title.options", "sol.title.quit"};
        SolFont nameFont = g.font(SolFont.Family.PIXEL, 11f);
        float dropWas = g.drop;
        g.drop = 2f;
        Runnable[] round = {this::realms, this::mods, this::options, this::quit};
        float rx = 640f - (rIcons.length * 52f + (rIcons.length - 1) * 12f) / 2f;
        for (int i = 0; i < rIcons.length; i++) {
            float x = rx + i * (52f + 12f), y = 470f;
            boolean over = ui.over(x, y, 52f, 52f);
            if (i == rIcons.length - 1) {
                Pix.redClose(g, p, x, y, 52f, 52f, over);
            } else {
                int st = ui.pressed(x, y, 52f, 52f) ? Pix.PRESSED : over ? Pix.HOVER : Pix.NORMAL;
                Pix.slab(g, p, x, y, 52f, 52f, st);
                Icons.draw(g, rIcons[i], x + 14f, y + 14f + (st == Pix.PRESSED ? Pix.E : 0f), 24f, Pix.text(p, st), 0f, 0);
            }
            int fi = ui.click(x, y, 52f, 52f, round[i]);
            if (ui.focused(fi)) {
                Pix.focus(g, x, y, 52f, 52f);
            }
            g.textCentre(nameFont, g.fitLabel(nameFont, tr(names[i]), 64f), x + 26f, y + 52f + 14f, 0xFFFFFFFF);
        }
        g.drop = dropWas;

        // You, top right: your face, name and "Wardrobe"; and the Mannequin (drawn by game()) opens it too.
        var mc = net.minecraft.client.Minecraft.getInstance();
        String user = io.github.georgwav.cosmetics.sol.client.Streamer.shownName(mc.getUser().getName());
        String sub = tr("sol.section.wardrobe");
        SolFont nf = g.font(SolFont.Family.PIXEL_BOLD, 13f), pf = g.font(SolFont.Family.MEDIUM, 11.5f);
        float mw = 6f + 38f + 10f + Math.max(g.width(nf, user), g.width(pf, sub)) + 14f, mxx = 1280f - 24f - mw, myy = 24f;
        boolean overMe = ui.over(mxx, myy, mw, 48f);
        Pix.slab(g, p, mxx, myy, mw, 48f, overMe ? Pix.HOVER : Pix.NORMAL);
        float fx = Math.round(g.d.sx(mxx + 9f)), fy = Math.round(g.d.sy(myy + 8f));
        int fk = Math.max(1, Math.round(4f * g.d.u));
        g.rectPx(fx - g.px(3f), fy - g.px(3f), fx + 8 * fk + g.px(3f), fy + 8 * fk + g.px(3f), p.edge);
        PlayerFace.drawPx(g, fx, fy, fk);
        g.textMid(nf, user, mxx + 54f, myy + 17f, Pix.text(p, overMe ? Pix.HOVER : Pix.NORMAL));
        g.textMid(pf, sub, mxx + 54f, myy + 33f, p.acc);
        int fm = ui.click(mxx, myy, mw, 48f, this::wardrobe);
        if (ui.focused(fm)) {
            Pix.focus(g, mxx, myy, mw, 48f);
        }
        ui.click(FIG_X, FIG_Y, FIG_W, FIG_H, this::wardrobe);

        // The footer in pixel type: versions at the left, Mojang's line at the right (it opens the credits).
        SolFont f = g.font(SolFont.Family.PIXEL, 10f);
        float shadow = g.drop;
        g.drop = 2f;
        String version = "Minecraft " + version("minecraft") + " · Sol Client " + version("cosmeticsmod")
                + " · " + io.github.georgwav.cosmetics.sol.ui.kit.Widgets.themeName(ThemeState.baseEntry());
        g.textMid(f, version, 20f, 703f, 0xFFFFFFFF);
        String copy = tr("title.credits");
        float cw = g.width(f, copy);
        ui.click(1260f - cw, 694f, cw, 18f, () -> ScreenOpener.open(
                new net.minecraft.client.gui.screens.CreditsAndAttributionScreen(this)));
        g.textRight(f, copy, 1260f, 703f, ui.over(1260f - cw, 694f, cw, 18f) ? p.accHi : 0xFFFFFFFF);
        g.drop = shadow;
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

    private void wardrobe() {
        ScreenOpener.open(io.github.georgwav.cosmetics.sol.ui.wardrobe.WardrobeScreen.on(this, "all"));
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
