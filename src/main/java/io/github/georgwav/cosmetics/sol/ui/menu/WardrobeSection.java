package io.github.georgwav.cosmetics.sol.ui.menu;

import io.github.georgwav.cosmetics.config.CosmeticsConfig;
import io.github.georgwav.cosmetics.model.BakedCosmetic;
import io.github.georgwav.cosmetics.model.CosmeticRegistry;
import io.github.georgwav.cosmetics.sol.config.SolProfiles;
import io.github.georgwav.cosmetics.sol.theme.Tokens;
import io.github.georgwav.cosmetics.sol.ui.kit.Gfx;
import io.github.georgwav.cosmetics.sol.ui.kit.Scroll;
import io.github.georgwav.cosmetics.sol.ui.kit.SolFont;
import io.github.georgwav.cosmetics.sol.ui.kit.Ui;
import io.github.georgwav.cosmetics.sol.util.Colors;
import io.github.georgwav.cosmetics.source.EquippedCosmetics;
import io.github.georgwav.cosmetics.ui.ScreenOpener;
import io.github.georgwav.cosmetics.ui.TilePictures;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Wardrobe in the menu: what you can wear at a glance (cosmetics with their pictures and a switch each, worn first),
 * your outfits, and the way into the full Wardrobe with its 3D preview and every cosmetic's settings.
 */
final class WardrobeSection implements Section {
    private String tab = "cosmetics";

    void tab(String t) {
        tab = t;
    }
    private final Scroll scroll = new Scroll();
    private TilePictures pictures;

    @Override
    public void draw(SolMenu menu, Gfx g, Ui ui, Tokens tk, float x, float y, float w, float h) {
        float headY = y + 15f;
        SolFont h2 = g.font(SolFont.Family.SEMIBOLD, 22f), mono = g.font(SolFont.Family.MONO, 12f);
        String title = SolMenu.tr("sol.section.wardrobe");
        g.textMid(h2, title, x, headY, 0xFFFFFFFF);
        float tx = x + g.width(h2, title) + 12f;
        // Tabs.
        String[] tabs = {"cosmetics", "outfits"};
        String[] names = new String[tabs.length];
        for (int i = 0; i < tabs.length; i++) {
            names[i] = SolMenu.tr("sol.menu.wardrobe." + tabs[i]);
        }
        int sel = tab.equals("outfits") ? 1 : 0;
        SolFont tf = g.font(SolFont.Family.MEDIUM, 12f);
        float segW = 6f;
        for (String n : names) {
            segW += g.width(tf, n) + 20f;
        }
        ui.segmented(tk, tx + segW, headY, names, sel, i -> {
            tab = tabs[i];
            scroll.reset();
        });
        String count = tab.equals("outfits") ? SolMenu.tr("sol.menu.wardrobe.outfits_count")
                : SolMenu.tr("sol.menu.wardrobe.count", SolProfiles.active());
        g.textMid(mono, count, tx + segW + 14f, headY, 0x8CFFFFFF);
        ui.button(tk, x + w, headY, SolMenu.tr("sol.menu.wardrobe.open"), true,
                () -> ScreenOpener.open(io.github.georgwav.cosmetics.sol.ui.wardrobe.WardrobeScreen.on(menu, tab.equals("outfits") ? "outfits" : null)));

        float gy = y + 42f, gh = h - 42f;
        g.clip(x - 4f, gy - 4f, x + w + 4f, gy + gh);
        float contentH = tab.equals("outfits") ? outfits(menu, g, ui, tk, x, gy - scroll.value(), w)
                : cosmetics(menu, g, ui, tk, x, gy - scroll.value(), w, gy, gh);
        g.endClip();
        scroll.setMax(contentH - gh);
        scroll.step(menu.dt());
        scroll.bar(g, x + w + 4f, gy, gh);
    }

    private float cosmetics(SolMenu menu, Gfx g, Ui ui, Tokens tk, float x, float y, float w, float viewY, float viewH) {
        CosmeticsConfig cfg = CosmeticsConfig.get();
        List<String> worn = cfg.localEquipped();
        List<BakedCosmetic> list = new ArrayList<>();
        for (BakedCosmetic c : CosmeticRegistry.all()) {
            if (!c.definition().prop()) {
                list.add(c);
            }
        }
        list.sort(Comparator.comparing((BakedCosmetic c) -> !worn.contains(c.id())).thenComparing(BakedCosmetic::name));
        if (list.isEmpty()) {
            g.text(g.font(SolFont.Family.MEDIUM, 13f), SolMenu.tr("sol.menu.wardrobe.empty"), x, y + 18f, 0x99FFFFFF);
            return 40f;
        }
        int cols = menu.drawer() != null ? 4 : 6;
        float gap = 10f, cw = (w - gap * (cols - 1) - 8f) / cols, ch = 10f + 86f + 8f + 20f + 10f;
        if (pictures == null) {
            pictures = new TilePictures();
        }
        pictures.beginFrame();
        SolFont nf = g.font(SolFont.Family.SEMIBOLD, 12.5f);
        for (int i = 0; i < list.size(); i++) {
            BakedCosmetic c = list.get(i);
            float cx = x + (i % cols) * (cw + gap), cy = y + 2f + (i / cols) * (ch + gap);
            if (cy + ch < viewY - 8f || cy > viewY + viewH + 8f) {
                continue;
            }
            boolean on = worn.contains(c.id());
            boolean hover = ui.over(cx, cy, cw, ch);
            ui.click(cx, cy, cw, ch, () -> wear(c, !worn.contains(c.id())));
            if (on) {
                g.glow(cx, cy, cw, ch, 12f, 20f, Colors.withAlpha(tk.glow, 0.25f));
            }
            g.round(cx, cy, cw, ch, 12f, tk.panel);
            g.stroke(cx, cy, cw, ch, 12f, Math.max(1f, g.d.u), on ? tk.rim : hover ? 0x38FFFFFF : 0x1AFFFFFF);
            // The art: the cosmetic's own picture, turning while pointed at.
            float ax = cx + 10f, ay = cy + 10f, aw = cw - 20f, ah = 86f;
            g.radialB.set(g.d.sx(ax + aw / 2f), g.d.sy(ay + ah * 0.6f), aw * 0.7f * g.d.u).stop(0f, 0x1AFFFFFF).stop(1f, 0);
            g.round(ax, ay, aw, ah, 9f, g.radialB);
            float size = Math.min(aw, ah);
            int s = Math.round(size * g.d.u);
            int px = Math.round(g.d.sx(ax + aw / 2f)) - s / 2, py = Math.round(g.d.sy(ay + ah / 2f)) - s / 2;
            if (!pictures.draw(g.game(), c, px, py, s, hover)) {
                g.glowSprite(ax + aw / 2f, ay + ah / 2f, 22f, 22f, Colors.withAlpha(tk.accent, 0.5f));
            }
            g.layer();
            g.text(nf, g.fit(nf, c.name(), cw - 20f - 38f), cx + 10f, cy + 10f + ah + 8f + 14f, 0xFFFFFFFF);
            ui.smallSwitch(tk, cx + cw - 10f - 30f, cy + 10f + ah + 8f + 1f, on, () -> wear(c, !worn.contains(c.id())));
        }
        int rows = (list.size() + cols - 1) / cols;
        return rows * (ch + gap) + 4f;
    }

    private static void wear(BakedCosmetic c, boolean on) {
        CosmeticsConfig cfg = CosmeticsConfig.get();
        CosmeticsConfig.wear(cfg.localEquipped(), c.id(), on, other -> {
            BakedCosmetic o = CosmeticRegistry.get(other);
            return o != null ? o.kind() : null;
        }, cfg.severalOfAKind);
        EquippedCosmetics.invalidate();
        CosmeticsConfig.save();
    }

    private float outfits(SolMenu menu, Gfx g, Ui ui, Tokens tk, float x, float y, float w) {
        CosmeticsConfig cfg = CosmeticsConfig.get();
        List<CosmeticsConfig.Outfit> list = cfg.savedOutfits;
        SolFont b = g.font(SolFont.Family.SEMIBOLD, 13.5f), s = g.font(SolFont.Family.MEDIUM, 11.5f);
        float colW = (w - 24f) / 2f;
        int i = 0;
        for (CosmeticsConfig.Outfit o : list) {
            float cx = x + (i % 2) * (colW + 24f), cy = y + (i / 2) * 56f;
            boolean wearing = cfg.wearsOutfit(o);
            g.round(cx, cy, colW, 48f, 12f, 0x0AFFFFFF);
            g.stroke(cx, cy, colW, 48f, 12f, Math.max(1f, g.d.u), wearing ? tk.rim : 0x12FFFFFF);
            g.text(b, g.fit(b, o.name, colW - 120f), cx + 14f, cy + 21f, 0xFFFFFFFF);
            g.text(s, SolMenu.tr("sol.menu.wardrobe.outfit_items", o.cosmetics.size()), cx + 14f, cy + 37f, 0x8FFFFFFF);
            ui.button(tk, cx + colW - 12f, cy + 24f, SolMenu.tr(wearing ? "sol.menu.wardrobe.wearing" : "sol.menu.wardrobe.wear"),
                    wearing, () -> {
                        cfg.wearOutfit(o);
                        EquippedCosmetics.invalidate();
                        CosmeticsConfig.save();
                        menu.toast(SolMenu.tr("sol.notify.outfit", o.name));
                    });
            i++;
        }
        float ny = y + ((i + 1) / 2) * 56f + 6f;
        ui.button(tk, x + w, ny + 13f, SolMenu.tr("sol.menu.wardrobe.save_outfit"), false, () -> {
            CosmeticsConfig.Outfit o = cfg.saveOutfit(SolMenu.tr("sol.menu.wardrobe.outfit_prefix"));
            CosmeticsConfig.save();
            menu.toast(SolMenu.tr("sol.notify.outfit_saved", o.name));
        });
        if (list.isEmpty()) {
            g.text(s, SolMenu.tr("sol.menu.wardrobe.no_outfits"), x, ny + 17f, 0x99FFFFFF);
        }
        return ny - y + 40f;
    }

    @Override
    public boolean scroll(float amount) {
        return scroll.wheel(amount);
    }

    @Override
    public void closed() {
        if (pictures != null) {
            pictures.close();
            pictures = null;
        }
    }
}
