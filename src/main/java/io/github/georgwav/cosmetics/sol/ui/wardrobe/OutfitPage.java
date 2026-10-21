package io.github.georgwav.cosmetics.sol.ui.wardrobe;

import io.github.georgwav.cosmetics.config.CosmeticsConfig;
import io.github.georgwav.cosmetics.model.BakedCosmetic;
import io.github.georgwav.cosmetics.model.CosmeticRegistry;
import io.github.georgwav.cosmetics.sol.theme.Tokens;
import io.github.georgwav.cosmetics.sol.ui.kit.Gfx;
import io.github.georgwav.cosmetics.sol.ui.kit.Scroll;
import io.github.georgwav.cosmetics.sol.ui.kit.SolFont;
import io.github.georgwav.cosmetics.sol.ui.kit.TextField;
import io.github.georgwav.cosmetics.sol.ui.kit.Ui;
import io.github.georgwav.cosmetics.source.EquippedCosmetics;

/**
 * An outfit's page, as on the Cosmetics screen: its name (a field; a name another outfit has, or none, is refused
 * and said so), wear, update to what you wear now, or delete it, and what it holds, each cosmetic with whether it
 * is still loaded and whether it keeps its own settings in the outfit.
 */
final class OutfitPage {
    private final WardrobeScreen screen;
    private final CosmeticsConfig.Outfit outfit;
    private final Scroll scroll = new Scroll();
    private final TextField name;
    private boolean nameProblem;

    OutfitPage(WardrobeScreen screen, CosmeticsConfig.Outfit outfit) {
        this.screen = screen;
        this.outfit = outfit;
        this.name = new TextField(48, text -> nameProblem = !CosmeticsConfig.get().renameOutfit(outfit, text));
        this.name.set(outfit.name);
    }

    void draw(Gfx g, Tokens tk, float x, float top, float w, float h) {
        Ui ui = screen.ui;
        CosmeticsConfig config = CosmeticsConfig.get();
        SolFont small = g.font(SolFont.Family.MEDIUM, 12f);
        String back = WardrobeScreen.t("detail.back");
        ui.bigButton(x, top, "left", back, false, true, () -> screen.openOutfit(null));
        name.draw(g, ui, tk, x, top + 52f, Math.min(360f, w), 38f, "ring", WardrobeScreen.t("outfits.name_hint"), screen.dt());
        if (nameProblem) {
            g.textMid(small, g.fit(small, WardrobeScreen.t("outfits.name_taken"), w - 380f), x + Math.min(360f, w) + 14f,
                    top + 71f, Tokens.AMBER);
        }
        float bodyTop = top + 104f;
        g.rect(x, bodyTop - 6f, w, Math.max(1f / g.d.u, 1f), 0x14FFFFFF);
        g.clip(x - 4f, bodyTop, x + w + 6f, top + h);
        PageRows rows = new PageRows(g, ui, tk, x, bodyTop - scroll.value(), w, null);
        boolean worn = config.wearsOutfit(outfit);
        float cy = rows.row(WardrobeScreen.t("outfits.this"), worn ? WardrobeScreen.t("outfits.wearing", outfit.cosmetics.size())
                : WardrobeScreen.t("outfits.count", outfit.cosmetics.size()));
        float r = rows.right();
        r -= ui.button(tk, r, cy, WardrobeScreen.t("outfits.delete"), false, () -> {
            config.savedOutfits.remove(outfit);
            screen.openOutfit(null);
        }) + 6f;
        r -= ui.button(tk, r, cy, WardrobeScreen.t("outfits.update"), false, () -> config.updateOutfit(outfit)) + 6f;
        ui.button(tk, r, cy, WardrobeScreen.t("outfits.wear"), true, () -> {
            config.wearOutfit(outfit);
            EquippedCosmetics.invalidate();
        });
        rows.text(WardrobeScreen.t("outfits.update_hint"), 0x8CFFFFFF);
        rows.section(WardrobeScreen.t("outfits.cosmetics", outfit.cosmetics.size()));
        if (outfit.cosmetics.isEmpty()) {
            rows.text(WardrobeScreen.t("outfits.nothing"), 0x99FFFFFF);
        }
        for (String id : outfit.cosmetics) {
            BakedCosmetic c = CosmeticRegistry.get(id);
            String note = c == null ? WardrobeScreen.t("outfits.missing")
                    : outfit.options.containsKey(id) ? WardrobeScreen.t("outfits.own_settings") : WardrobeScreen.t("outfits.as_made");
            rows.info(c != null ? c.name() : id, note, c == null ? Tokens.AMBER : 0x8CFFFFFF);
        }
        float end = rows.y + 12f;
        g.endClip();
        float viewH = top + h - bodyTop;
        scroll.setMax(end + scroll.value() - bodyTop - viewH);
        scroll.step(screen.dt());
        scroll.bar(g, x + w + 4f, bodyTop, viewH);
    }

    void unfocus() {
        name.focus(false);
    }

    boolean scroll(float amount) {
        return scroll.wheel(amount);
    }

    boolean atTop() {
        return scroll.value() == 0f;
    }

    boolean key(int key, boolean ctrl) {
        return name.key(key, ctrl);
    }

    boolean typed(String s) {
        return name.typed(s);
    }
}
