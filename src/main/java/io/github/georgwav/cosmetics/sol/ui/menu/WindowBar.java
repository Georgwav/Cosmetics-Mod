package io.github.georgwav.cosmetics.sol.ui.menu;

import io.github.georgwav.cosmetics.sol.config.SolProfiles;
import io.github.georgwav.cosmetics.sol.render.SolTexture;
import io.github.georgwav.cosmetics.sol.theme.Tokens;
import io.github.georgwav.cosmetics.sol.ui.kit.Gfx;
import io.github.georgwav.cosmetics.sol.ui.kit.Icons;
import io.github.georgwav.cosmetics.sol.ui.kit.ModuleIcons;
import io.github.georgwav.cosmetics.sol.ui.kit.Pix;
import io.github.georgwav.cosmetics.sol.ui.kit.PixelTokens;
import io.github.georgwav.cosmetics.sol.ui.kit.PlayerFace;
import io.github.georgwav.cosmetics.sol.ui.kit.SolFont;
import io.github.georgwav.cosmetics.sol.ui.kit.SolScreen;
import io.github.georgwav.cosmetics.sol.ui.kit.Ui;
import io.github.georgwav.cosmetics.sol.util.Colors;
import io.github.georgwav.cosmetics.sol.util.PixelCanvas;
import io.github.georgwav.cosmetics.sol.util.Spring;
import io.github.georgwav.cosmetics.sol.util.SunCpu;
import net.minecraft.client.Minecraft;

/**
 * The window's title bar (A.7.2), shared by the full menu and the Wardrobe: Sol's icon in its own cell, back, the
 * tabs (Sol's icon first, for the quick menu; the tab you're on wide with its name, the rest icons with a tooltip),
 * your slab (the profiles) and the red close; and the sun laid flat on the horizon under the tab you're on, gliding
 * to the next. Drawing and hit areas only: the screen says what each one does.
 */
public final class WindowBar {
    /** The tabs after Sol's icon, in order. */
    public static final String[] TABS = {"modules", "hud", "world", "presets", "wardrobe", "styles", "emotes", "music", "mods", "settings"};
    /** The slabs' top, a tab's size and the gap between tabs; the close's width. */
    static final float Y = Pix.WIN_Y + Pix.E + 3f, TAB = 48f, GAP = 6f, CLOSE_W = 60f;

    /** What the bar's slabs do. */
    public interface Host {
        /** The tab that's open. */
        String current();

        void go(String tab);

        /** The quick menu (Sol's icon tab). */
        void quickMenu();

        /** The back slab. */
        void back();

        void close();

        /** Your slab: the profiles. */
        void profiles();

        /** Whether the profiles are open (your slab draws on). */
        boolean profilesOpen();

        /** The back slab's tooltip. */
        String backTip();
    }

    private final Spring sunX = new Spring(Spring.SUN_ARC, 0f);
    private boolean sunPlaced;
    private SolTexture sunTex, iconTex;
    private final PixelCanvas sunCanvas = new PixelCanvas(32, 32);
    private final int[] sunPixels = new int[32 * 32];
    private float sunDrawnAt = -1f;
    private final float[] tabMid = new float[TABS.length];

    public static int indexOf(String id) {
        for (int i = 0; i < TABS.length; i++) {
            if (TABS[i].equals(id)) {
                return i;
            }
        }
        return -1;
    }

    /** Draws the bar (after {@link Pix#window}), then the sun on the horizon. */
    public void draw(Gfx g, Ui ui, Tokens tk, float time, float dt, Host host) {
        PixelTokens p = PixelTokens.get();
        float top = Pix.WIN_Y + Pix.E, barH = Pix.BAR_H - Pix.E;
        // Sol's icon in its own cell, an edge on its right.
        float cellR = Pix.WIN_X + Pix.E + 57f;
        Pix.fill(g, Pix.WIN_X + Pix.E, top, 57f, barH, 0xFF232326);
        Pix.fill(g, cellR, top, Pix.E, barH, p.edge);
        SolTexture icon = icon();
        if (icon != null) {
            int k = Math.max(1, Math.round(g.d.u));
            float ix = Math.round(g.d.sx(Pix.WIN_X + Pix.E + 28.5f) - 16f * k), iy = Math.round(g.d.sy(top + barH / 2f) - 16f * k);
            g.imageScreen(icon, ix, iy, ix + 32 * k, iy + 32 * k, 0f, 0f, 1f, 1f, 0xFFFFFFFF, false);
        }
        float x = cellR + Pix.E + 8f, y = Y;
        String tip = null;
        float tipX = 0f;
        // Back.
        if (square(g, ui, p, x, y, "left", false, host::back)) {
            tip = host.backTip();
            tipX = x;
        }
        x += TAB + 8f;
        // Sol's icon tab: the quick menu.
        if (square(g, ui, p, x, y, "solsq", false, host::quickMenu)) {
            tip = tr("sol.quick.tab");
            tipX = x;
        }
        x += TAB + GAP;
        SolFont tf = g.font(SolFont.Family.PIXEL_BOLD, 14f);
        String current = host.current();
        for (int i = 0; i < TABS.length; i++) {
            String id = TABS[i];
            String name = tr("sol.section." + id);
            if (id.equals(current)) {
                float w = 10f + 24f + 10f + g.width(tf, name) + 16f;
                boolean over = ui.over(x, y, w, TAB);
                Pix.slab(g, p, x, y, w, TAB, Pix.ON);
                Icons.draw(g, ModuleIcons.SECTIONS.get(id), x + 12f, y + 12f, 24f, p.acc, 0f, 0);
                g.textMid(tf, name, x + 10f + 24f + 10f, y + TAB / 2f, p.acc);
                int fi = ui.click(x, y, w, TAB, () -> host.go(id));
                if (ui.focused(fi)) {
                    Pix.focus(g, x, y, w, TAB);
                }
                tabMid[i] = x + w / 2f;
                x += w + GAP;
                continue;
            }
            if (square(g, ui, p, x, y, ModuleIcons.SECTIONS.get(id), false, () -> host.go(id))) {
                tip = name;
                tipX = x;
            }
            tabMid[i] = x + TAB / 2f;
            x += TAB + GAP;
        }
        // The close, flush right in the bar, its edge on its left; you beside it.
        float closeX = Pix.WIN_X + Pix.WIN_W - Pix.E - CLOSE_W;
        boolean overClose = ui.over(closeX, top, CLOSE_W, barH);
        Pix.fill(g, closeX, top, CLOSE_W, barH, overClose ? PixelTokens.RED : PixelTokens.CLOSE_FILL);
        Pix.fill(g, closeX, top, Pix.E, barH, overClose ? PixelTokens.RED : p.edge);
        Icons.draw(g, "x", closeX + Pix.E + (CLOSE_W - Pix.E) / 2f - 12f, top + barH / 2f - 12f, 24f, overClose ? 0xFF1A0505 : PixelTokens.RED, 0f, 0);
        int fc = ui.click(closeX, top, CLOSE_W, barH, host::close);
        if (ui.focused(fc)) {
            Pix.focus(g, closeX, top, CLOSE_W, barH);
        }
        me(g, ui, p, closeX - 8f, y, host);
        if (tip != null) {
            Pix.tooltip(g, p, tipX, y + TAB + 6f, tip);
        }
        sun(g, tk, time, dt, current);
    }

    /** A square slab with an icon; returns whether it's pointed at. */
    private static boolean square(Gfx g, Ui ui, PixelTokens p, float x, float y, String icon, boolean on, Runnable action) {
        boolean over = ui.over(x, y, TAB, TAB);
        int st = on ? Pix.ON : ui.pressed(x, y, TAB, TAB) ? Pix.PRESSED : over ? Pix.HOVER : Pix.NORMAL;
        Pix.slab(g, p, x, y, TAB, TAB, st);
        Icons.draw(g, icon, x + 12f, y + 12f + (st == Pix.PRESSED ? Pix.E : 0f), 24f, Pix.text(p, st), 0f, 0);
        int fi = ui.click(x, y, TAB, TAB, action);
        if (ui.focused(fi)) {
            Pix.focus(g, x, y, TAB, TAB);
        }
        return over;
    }

    /** Your slab, its right edge at {@code right}: your face, name and profile. */
    private static void me(Gfx g, Ui ui, PixelTokens p, float right, float y, Host host) {
        Minecraft mc = Minecraft.getInstance();
        String name = io.github.georgwav.cosmetics.sol.client.Streamer.shownName(mc.getUser().getName());
        String prof = SolProfiles.active();
        SolFont nf = g.font(SolFont.Family.PIXEL_BOLD, 13f), pf = g.font(SolFont.Family.MEDIUM, 11.5f);
        float w = 6f + 38f + 10f + Math.max(g.width(nf, name), g.width(pf, prof)) + 14f, x = right - w;
        boolean over = ui.over(x, y, w, TAB), on = host.profilesOpen();
        int st = on ? Pix.ON : over ? Pix.HOVER : Pix.NORMAL;
        Pix.slab(g, p, x, y, w, TAB, st);
        float fx = Math.round(g.d.sx(x + 6f + 3f)), fy = Math.round(g.d.sy(y + 8f));
        int k = Math.max(1, Math.round(4f * g.d.u));
        g.rectPx(fx - g.px(3f), fy - g.px(3f), fx + 8 * k + g.px(3f), fy + 8 * k + g.px(3f), p.edge);
        PlayerFace.drawPx(g, fx, fy, k);
        float tx = x + 6f + 38f + 10f;
        g.textMid(nf, name, tx, y + 17f, Pix.text(p, st));
        g.textMid(pf, g.fit(pf, prof, w - (tx - x) - 10f), tx, y + 33f, p.acc);
        int fi = ui.click(x, y, w, TAB, host::profiles);
        if (ui.focused(fi)) {
            Pix.focus(g, x, y, w, TAB);
        }
    }

    /** Sol's icon (the mod's own picture) at 32 x 32, read once. */
    private SolTexture icon() {
        if (iconTex != null && !iconTex.released()) {
            return iconTex;
        }
        try (var in = WindowBar.class.getResourceAsStream("/assets/cosmeticsmod/icon.png")) {
            if (in == null) {
                return null;
            }
            java.awt.image.BufferedImage im = javax.imageio.ImageIO.read(in);
            int[] px = new int[32 * 32];
            int step = Math.max(1, im.getWidth() / 32);
            for (int y = 0; y < 32; y++) {
                for (int x = 0; x < 32; x++) {
                    px[y * 32 + x] = im.getRGB(Math.min(im.getWidth() - 1, x * step), Math.min(im.getHeight() - 1, y * step));
                }
            }
            iconTex = SolTexture.of("window_icon", 32, 32, false, px);
        } catch (java.io.IOException | RuntimeException e) {
            return null;
        }
        return iconTex;
    }

    /** The sun laid flat: a 32 x 32 pixel sun on the horizon under the tab you're on. */
    private void sun(Gfx g, Tokens tk, float time, float dt, String current) {
        int at = indexOf(current);
        if (at < 0) {
            return;
        }
        float target = tabMid[at];
        if (!sunPlaced || SolScreen.calm() || dt == 0f) {
            sunX.snap(target);
            sunPlaced = true;
        } else {
            sunX.setTarget(target);
            sunX.step(dt);
        }
        if (sunTex == null || sunTex.released()) {
            sunTex = new SolTexture("window_sun", 32, 32, false);
            sunDrawnAt = -1f;
        }
        if (sunDrawnAt < 0f || time - sunDrawnAt >= 0.1f || time < sunDrawnAt) {
            sunDrawnAt = time;
            sunCanvas.clear();
            SunCpu.drawBack(sunCanvas, 32f, 16f, 16f, 5f, 0.35f, Colors.mix(0xFFFFF1CF, tk.glow, 0.2f), tk.glow, SunCpu.MENU,
                    SolScreen.calm() ? 0f : time, null);
            sunTex.upload(sunCanvas.toArgb(sunPixels));
        }
        // Whole screen pixels per sun pixel, its middle on the horizon line.
        int k = Math.max(1, Math.round(g.d.u));
        float horizon = Math.round(g.d.sy(Pix.WIN_Y + Pix.BAR_H)) + g.px(Pix.E) / 2f;
        float sx = Math.round(g.d.sx(sunX.value()) - 16f * k), sy = Math.round(horizon - 16f * k);
        g.imageScreen(sunTex, sx, sy, sx + 32 * k, sy + 32 * k, 0f, 0f, 1f, 1f, 0xFFFFFFFF, false);
    }

    public void release() {
        if (sunTex != null) {
            sunTex.release();
        }
        if (iconTex != null) {
            iconTex.release();
        }
    }

    private static String tr(String key) {
        return net.minecraft.network.chat.Component.translatable(key).getString();
    }
}
