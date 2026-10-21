package io.github.georgwav.cosmetics.sol.ui.screens;

import io.github.georgwav.cosmetics.config.CosmeticsConfig;
import io.github.georgwav.cosmetics.emote.Emote;
import io.github.georgwav.cosmetics.emote.Emotes;
import io.github.georgwav.cosmetics.sol.theme.Tokens;
import io.github.georgwav.cosmetics.sol.ui.kit.Gfx;
import io.github.georgwav.cosmetics.sol.ui.kit.Pix;
import io.github.georgwav.cosmetics.sol.ui.kit.SolFont;
import io.github.georgwav.cosmetics.sol.ui.kit.Ui;
import io.github.georgwav.cosmetics.sol.util.Colors;
import io.github.georgwav.cosmetics.ui.Canvas;
import io.github.georgwav.cosmetics.ui.EmotePictures;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Picking the emote for one slot of a wheel: every emote as a tile (made for this wheel first), scrolled with the
 * mouse wheel, with Back and "Empty this slot". The emote wheel and the quick menu's wheel both use it.
 */
public final class EmotePicker {
    private static final float TILE = 84f, TILE_GAP = 12f;

    private final boolean elytra;
    private final int slot;
    private float scroll;

    public EmotePicker(boolean elytra, int slot) {
        this.elytra = elytra;
        this.slot = slot;
    }

    private static String t(String key, Object... args) {
        return Component.translatable("cosmeticsmod.emotes." + key, args).getString();
    }

    public void scroll(float amount) {
        scroll -= amount * 60f;
    }

    /** Draws the picker round (cx, cy); {@code done} runs when it should close (picked, emptied or Back). */
    public void draw(Gfx g, Ui ui, Tokens tk, float cx, float cy, float my, EmotePictures pictures, Runnable done) {
        float pw = 720f, ph = 520f, px = cx - pw / 2f, py = cy - ph / 2f;
        io.github.georgwav.cosmetics.sol.ui.kit.Pix.card(g, io.github.georgwav.cosmetics.sol.ui.kit.PixelTokens.get(), px, py, pw, ph);
        ui.area(px, py, pw, ph, () -> { });
        SolFont h = g.font(SolFont.Family.PIXEL_BOLD, 15f), small = g.font(SolFont.Family.MEDIUM, 12f);
        g.textMid(h, t("pick_title", slot % CosmeticsConfig.WHEEL_SLOTS + 1), px + 22f, py + 30f, 0xFFFFFFFF);
        List<Emote> all = new ArrayList<>(Emotes.all());
        all.sort(Comparator.comparing((Emote e) -> e.elytra() != elytra));
        float gx = px + 22f, gy = py + 58f, gw = pw - 44f, gh = ph - 58f - 60f;
        int cols = Math.max(1, (int) ((gw + TILE_GAP) / (TILE + TILE_GAP)));
        float left = gx + (gw - (cols * (TILE + TILE_GAP) - TILE_GAP)) / 2f;
        float rowH = TILE + 26f;
        int rows = (all.size() + cols - 1) / cols;
        float maxScroll = Math.max(0f, rows * rowH - gh);
        scroll = Math.max(0f, Math.min(maxScroll, scroll));
        g.clip(gx - 4f, gy - 4f, gx + gw + 4f, gy + gh);
        Emote current = Emotes.at(elytra, slot);
        for (int i = 0; i < all.size(); i++) {
            Emote e = all.get(i);
            float tx = left + (i % cols) * (TILE + TILE_GAP), ty = gy + (i / cols) * rowH - scroll;
            if (ty + rowH < gy || ty > gy + gh) {
                continue;
            }
            boolean hover = ui.over(tx, ty, TILE, TILE) && my >= gy && my <= gy + gh;
            ui.click(tx, ty, TILE, TILE, () -> {
                pick(e);
                done.run();
            });
            Pix.fill(g, tx, ty, TILE, TILE, hover ? Colors.mix(tk.panel, 0xFFFFFFFF, 0.1f) : 0x14FFFFFF);
            Pix.frame(g, tx, ty, TILE, TILE, hover ? tk.accent : e == current ? tk.rim : 0x1FFFFFFF);
            Canvas c = g.game();
            int s = Math.round((TILE - 8f) * g.d.u);
            pictures.draw(c, e, Math.round(g.d.sx(tx + TILE / 2f)) - s / 2, Math.round(g.d.sy(ty + TILE / 2f)) - s / 2, s);
            g.layer();
            g.textCentre(small, g.fit(small, Emotes.name(e), TILE + TILE_GAP - 2f), tx + TILE / 2f, ty + TILE + 12f,
                    hover ? 0xFFFFFFFF : 0x99FFFFFF);
        }
        g.endClip();
        if (all.isEmpty()) {
            g.textCentre(small, t("none"), cx, cy, 0x99FFFFFF);
        }
        float by = py + ph - 30f;
        ui.button(tk, px + pw - 22f, by, t("back"), false, done);
        if (current != null) {
            ui.button(tk, px + 22f + g.width(small, t("empty_slot")) + 28f, by, t("empty_slot"), false, () -> {
                Emotes.assign(elytra, slot, "");
                CosmeticsConfig.save();
                done.run();
            });
        }
    }

    /** Puts {@code emote} in the slot; an emote is on a wheel once, so it leaves its old slot. */
    private void pick(Emote emote) {
        for (int i = 0; i < Emotes.slots(elytra).size(); i++) {
            if (i != slot && Emotes.slots(elytra).get(i).equals(emote.id())) {
                Emotes.assign(elytra, i, "");
            }
        }
        Emotes.assign(elytra, slot, emote.id());
        CosmeticsConfig.save();
    }
}
