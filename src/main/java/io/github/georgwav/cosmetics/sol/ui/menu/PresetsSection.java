package io.github.georgwav.cosmetics.sol.ui.menu;

import io.github.georgwav.cosmetics.sol.render.SolTexture;
import io.github.georgwav.cosmetics.sol.theme.ThemeRegistry;
import io.github.georgwav.cosmetics.sol.theme.ThemeState;
import io.github.georgwav.cosmetics.sol.theme.Tokens;
import io.github.georgwav.cosmetics.sol.ui.kit.Gfx;
import io.github.georgwav.cosmetics.sol.ui.kit.Icons;
import io.github.georgwav.cosmetics.sol.ui.kit.LogoRenderer;
import io.github.georgwav.cosmetics.sol.ui.kit.Pix;
import io.github.georgwav.cosmetics.sol.ui.kit.PixelTokens;
import io.github.georgwav.cosmetics.sol.ui.kit.Scroll;
import io.github.georgwav.cosmetics.sol.ui.kit.SolFont;
import io.github.georgwav.cosmetics.sol.ui.kit.Ui;
import io.github.georgwav.cosmetics.sol.ui.kit.Widgets;
import io.github.georgwav.cosmetics.sol.ui.quick.PresetThumbs;

import java.util.List;

/**
 * Presets (A.7.2): every theme as a card (its sky in pixels, its name and its line) three to a row, the one in use
 * marked with the on badge; on the right the preview pane shows the one you point at, or the one in use, with Sol's
 * logo over its sky. Apply preset and Random in the head.
 */
final class PresetsSection implements Section {
    /** The preview pane's width, and its sky's size in picture pixels (2 design px each). */
    static final float PANE_W = 400f;
    private static final int SKY_W = 197, SKY_H = 240;
    private final Scroll scroll = new Scroll();
    private final PresetThumbs thumbs = new PresetThumbs(116, 58, false);
    private final PresetThumbs skies = new PresetThumbs(SKY_W, SKY_H, false);
    private final LogoRenderer logo = new LogoRenderer(false);
    /** The card last pointed at or focused (the pane shows it, Apply puts it on), or null for the one in use. */
    private ThemeRegistry.Entry pointed;

    @Override
    public void draw(SolMenu menu, Gfx g, Ui ui, Tokens tk, float x, float y, float w, float h) {
        PixelTokens p = PixelTokens.get();
        ThemeRegistry.Entry cur = ThemeState.baseEntry();
        ThemeRegistry.Entry shown = pointed != null ? pointed : cur;
        List<ThemeRegistry.Entry> themes = ThemeRegistry.all();
        // The head: what this is, and Apply / Random.
        String random = SolMenu.tr("sol.menu.presets.random"), apply = SolMenu.tr("sol.menu.presets.apply");
        float rw = ui.bigButtonWidth("dice", random), aw = ui.bigButtonWidth("check", apply);
        ui.bigButton(x + w - rw, y, "dice", random, false, true, () -> random(menu, cur));
        final ThemeRegistry.Entry target = shown;
        ui.bigButton(x + w - rw - 8f - aw, y, "check", apply, true, false, () -> {
            WorldSection.apply(menu, target);
            pointed = null;
        });
        SolMenu.head(g, SolMenu.tr("sol.menu.presets.head"), SolMenu.tr("sol.menu.presets.count", themes.size()), x, y + 21f);

        // The cards in a recess, the pane beside it.
        float gy = y + 42f + 16f, gh = h - 42f - 16f, gw = w - PANE_W - 18f;
        Pix.recess(g, p, x, gy, gw, gh);
        int cols = 3;
        float pad = 14f, gap = 12f;
        float inX = x + Pix.E + pad, inY = gy + Pix.E + pad, inW = gw - 2f * (Pix.E + pad) - 10f, inH = gh - 2f * Pix.E - pad;
        float cw = (inW - gap * (cols - 1)) / cols, thumbH = Math.round(cw / 2f), ch = thumbH + Pix.E * 2f + 56f;
        int rows = (themes.size() + cols - 1) / cols;
        float content = rows * (ch + gap) - gap + pad;
        scroll.setMax(content - inH);
        scroll.step(menu.dt());
        g.clip(x + Pix.E, gy + Pix.E, x + gw - Pix.E, gy + gh - Pix.E);
        SolFont nf = g.font(SolFont.Family.PIXEL_BOLD, 13f), lf = g.font(SolFont.Family.MEDIUM, 12.5f);
        for (int i = 0; i < themes.size(); i++) {
            ThemeRegistry.Entry e = themes.get(i);
            float cx = inX + (i % cols) * (cw + gap), cy = inY + (i / cols) * (ch + gap) - scroll.value();
            if (cy + ch < gy || cy > gy + gh) {
                continue;
            }
            boolean on = e.id().equals(cur.id()), over = ui.over(cx, cy, cw, ch) && ui.over(x, gy, gw, gh);
            if (over) {
                pointed = e;
            }
            Pix.slab(g, p, cx, cy, cw, ch, on ? Pix.ON : over ? Pix.HOVER : Pix.NORMAL);
            // Its sky, edge to edge under the outline, an edge under it.
            float tx = cx + Pix.E, ty = cy + Pix.E, tw = cw - 2f * Pix.E;
            Pix.fill(g, tx, ty, tw, thumbH, 0xFF000000);
            SolTexture t = thumbs.get(e);
            if (t != null) {
                Pix.pixels(g, t, thumbs.w, thumbs.h, tx, ty, tw, thumbH);
            }
            Pix.fill(g, tx, ty + thumbH, tw, Pix.E, on ? p.acc : p.edge);
            float textY = ty + thumbH + Pix.E;
            g.textMid(nf, g.fit(nf, Widgets.themeName(e), cw - 24f - (on ? 36f : 0f)), cx + 11f, textY + 17f,
                    on ? p.acc : over ? PixelTokens.HOVER_TEXT : PixelTokens.INK);
            g.textMid(lf, g.fit(lf, Widgets.themeLine(e), cw - 22f), cx + 11f, textY + 38f, PixelTokens.INK2);
            if (on) {
                // The on badge: a 30 px glowing check in the card's corner.
                float bx = cx + cw - 8f - 30f, by = cy + 8f;
                Pix.onGlow(g, p, bx, by, 30f, 30f, false);
                Icons.draw(g, "check", bx + 3f, by + 3f, 24f, p.enText, 0f, 0);
            }
            int fi = ui.click(cx, cy, cw, ch, () -> {
                WorldSection.apply(menu, e);
                pointed = null;
            });
            if (ui.focused(fi)) {
                Pix.focus(g, cx, cy, cw, ch);
                pointed = e;
            }
        }
        g.endClip();
        if (content > inH) {
            float from = scroll.value() / content, to = (scroll.value() + inH) / content;
            Pix.scrollbar(g, p, x + gw - Pix.E - 10f, gy + Pix.E + 4f, gh - 2f * Pix.E - 8f, from, Math.min(1f, to));
        }
        pane(menu, g, p, shown, x + w - PANE_W, gy, PANE_W, gh);
    }

    /** The preview pane: the theme's sky at half size, Sol's logo over it, its name and line in the caption. */
    private void pane(SolMenu menu, Gfx g, PixelTokens p, ThemeRegistry.Entry e, float x, float y, float w, float h) {
        Pix.pane(g, p, x, y, w, h);
        SolTexture sky = skies.get(e);
        if (sky != null) {
            Pix.pixels(g, sky, SKY_W, SKY_H, x + Pix.E, y + Pix.E, w - 2f * Pix.E, h - 2f * Pix.E);
        }
        SolTexture t = logo.texture(e, menu.time(), menu.dt());
        int k = Math.max(1, Math.round(3f * g.d.u));
        float lw = LogoRenderer.W * k, lh = LogoRenderer.H * k;
        float lx = Math.round(g.d.sx(x + w / 2f) - lw / 2f), ly = Math.round(g.d.sy(y + 50f));
        g.imageScreen(t, lx, ly, lx + lw, ly + lh, 0f, 0f, 1f, 1f, 0xFFFFFFFF, false);
        Pix.paneEdge(g, p, x, y, w, h);
        Pix.caption(g, p, x, y, w, h, Widgets.themeName(e), Widgets.themeLine(e));
    }

    private static void random(SolMenu menu, ThemeRegistry.Entry cur) {
        List<ThemeRegistry.Entry> all = ThemeRegistry.all();
        if (all.size() < 2) {
            return;
        }
        ThemeRegistry.Entry pick;
        do {
            pick = all.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(all.size()));
        } while (pick.id().equals(cur.id()));
        WorldSection.apply(menu, pick);
    }

    @Override
    public boolean scroll(float amount) {
        return scroll.wheel(amount);
    }

    @Override
    public void opened() {
        scroll.reset();
        pointed = null;
    }

    @Override
    public void closed() {
        thumbs.release();
        skies.release();
        logo.release();
    }
}
