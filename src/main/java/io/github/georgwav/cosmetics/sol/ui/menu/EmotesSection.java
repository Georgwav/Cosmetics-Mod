package io.github.georgwav.cosmetics.sol.ui.menu;

import io.github.georgwav.cosmetics.config.CosmeticsConfig;
import io.github.georgwav.cosmetics.emote.Emote;
import io.github.georgwav.cosmetics.emote.EmotePlayer;
import io.github.georgwav.cosmetics.emote.Emotes;
import io.github.georgwav.cosmetics.sol.theme.Tokens;
import io.github.georgwav.cosmetics.sol.ui.kit.Gfx;
import io.github.georgwav.cosmetics.sol.ui.kit.Icons;
import io.github.georgwav.cosmetics.sol.ui.kit.Pix;
import io.github.georgwav.cosmetics.sol.ui.kit.PixelTokens;
import io.github.georgwav.cosmetics.sol.ui.kit.SolFont;
import io.github.georgwav.cosmetics.sol.ui.kit.Ui;
import io.github.georgwav.cosmetics.sol.ui.screens.EmotePicker;
import io.github.georgwav.cosmetics.ui.Canvas;
import io.github.georgwav.cosmetics.ui.EmotePictures;
import io.github.georgwav.cosmetics.ui.Mannequin;
import net.minecraft.client.Minecraft;

/**
 * Emotes (A.7.2): your wheel's page as eight slots on an oval in a recess (each with its emote's picture and name, or the plus
 * where none is), the page and its controls in the middle, and you on the right doing the one you picked. Click a
 * slot to play its emote, the gear on a slot (or an empty slot) to pick the emote for it. Play and Stop in the head;
 * the elytra wheel is a tab of its own.
 */
final class EmotesSection implements Section {
    private static final float PANE_W = 400f, SLOT_W = 96f, SLOT_H = 112f;
    private boolean elytra;
    private int page;
    /** The emote last picked (Play plays it), or null. */
    private Emote picked;
    private EmotePicker picker;
    private EmotePictures pictures;
    private Mannequin mannequin;
    // You, in the game's GUI pixels (set while drawing, used by game()).
    private int px1, py1, px2, py2;
    private boolean shown;
    private float spin;

    @Override
    public void draw(SolMenu menu, Gfx g, Ui ui, Tokens tk, float x, float y, float w, float h) {
        PixelTokens p = PixelTokens.get();
        if (pictures == null) {
            pictures = new EmotePictures();
        }
        int pages = Math.max(1, Emotes.pages(elytra));
        page = Math.min(page, pages - 1);
        // The head: Play, Stop and which wheel.
        String play = SolMenu.tr("sol.menu.emotes.play"), stop = SolMenu.tr("sol.menu.emotes.stop");
        String wheel = SolMenu.tr(elytra ? "sol.menu.emotes.ground" : "sol.menu.emotes.elytra");
        float right = x + w;
        right -= ui.bigButtonWidth("stop", stop);
        ui.bigButton(right, y, "stop", stop, false, true, EmotePlayer::stop);
        right -= 8f + ui.bigButtonWidth("play", play);
        ui.bigButton(right, y, "play", play, true, false, () -> {
            Emote e = picked != null ? picked : firstOnPage();
            if (e != null) {
                EmotePlayer.play(e, elytra);
            }
        });
        right -= 8f + ui.bigButtonWidth("wing", wheel);
        ui.bigButton(right, y, "wing", wheel, elytra, true, () -> {
            elytra = !elytra;
            page = 0;
        });
        SolMenu.head(g, SolMenu.tr("sol.menu.emotes.head"), SolMenu.tr("sol.menu.emotes.count", Emotes.all().size()), x, y + 21f);

        // The slots round the recess.
        float gy = y + 42f + 16f, gh = h - 42f - 16f, gw = w - PANE_W - 18f;
        Pix.recess(g, p, x, gy, gw, gh);
        // The slots on an oval: wider than tall, so the page controls in the middle keep clear of the side slots.
        float cx = x + gw / 2f, cy = gy + gh / 2f, ry = Math.min(gw, gh) / 2f - 66f;
        float rx = Math.min(gw / 2f - SLOT_W / 2f - 14f, ry * 1.35f);
        int per = CosmeticsConfig.WHEEL_SLOTS;
        Emote playing = EmotePlayer.playing();
        SolFont nf = g.font(SolFont.Family.PIXEL_BOLD, 10f);
        for (int i = 0; i < per; i++) {
            int slot = page * per + i;
            Emote e = Emotes.at(elytra, slot);
            double a = Math.toRadians(-90 + i * 360.0 / per);
            float sx = Math.round(cx + (float) Math.cos(a) * rx - SLOT_W / 2f), sy = Math.round(cy + (float) Math.sin(a) * ry - SLOT_H / 2f);
            boolean on = e != null && (e == picked || e == playing), over = ui.over(sx, sy, SLOT_W, SLOT_H);
            Pix.slab(g, p, sx, sy, SLOT_W, SLOT_H, on ? Pix.ON : over ? Pix.HOVER : Pix.NORMAL);
            int c = Pix.text(p, on ? Pix.ON : over ? Pix.HOVER : Pix.NORMAL);
            if (e != null) {
                int size = Math.round(64f * g.d.u);
                pictures.draw(g.game(), e, Math.round(g.d.sx(sx + SLOT_W / 2f) - size / 2f), Math.round(g.d.sy(sy + 10f)), size);
                g.layer();
                // Two lines when the name needs them ("Head / Shake"), the first moved up to make room.
                String name = Emotes.name(e);
                float line = nf.cap() / g.d.u + 5f;
                boolean two = g.width(nf, name) > SLOT_W - 12f;
                g.labelCentre(nf, name, sx + SLOT_W / 2f, sy + SLOT_H - 16f - (two ? line : 0f), SLOT_W - 12f, line, c);
            } else {
                Icons.draw(g, "plus", sx + SLOT_W / 2f - 18f, sy + 30f, 36f, c, 0f, 0);
                g.textCentre(nf, SolMenu.tr("sol.quick.wheel.empty_slot"), sx + SLOT_W / 2f, sy + SLOT_H - 16f, c);
            }
            int fi = ui.click(sx, sy, SLOT_W, SLOT_H, () -> {
                if (e == null) {
                    picker = new EmotePicker(elytra, slot);
                } else {
                    picked = e;
                    EmotePlayer.play(e, elytra);
                }
            });
            if (e != null && (over || on)) {
                // The gear: pick another emote for this slot.
                float bx = sx + SLOT_W - 30f, by = sy + 4f;
                ui.tool(tk, bx, by, "gear", false, () -> picker = new EmotePicker(elytra, slot));
            }
            if (ui.focused(fi)) {
                Pix.focus(g, sx, sy, SLOT_W, SLOT_H);
            }
        }
        // The middle: the page, a line on what to do, and the page controls.
        SolFont bf = g.font(SolFont.Family.PIXEL_BOLD, 14f), sf = g.font(SolFont.Family.MEDIUM, 12f);
        g.textCentre(bf, SolMenu.tr("sol.menu.emotes.page", page + 1, pages), cx, cy - 34f, PixelTokens.INK);
        g.textCentre(sf, SolMenu.tr("sol.menu.emotes.hint"), cx, cy - 12f, PixelTokens.INK3);
        float rowY = cy + 8f;
        float bw = ui.bigButtonWidth("prev", "");
        boolean canPrev = page > 0, canNext = page < pages - 1;
        float addW = ui.bigButtonWidth("plus", SolMenu.tr("sol.menu.emotes.add_page"));
        float total = bw + 8f + addW + 8f + bw;
        float bx = cx - total / 2f;
        ui.bigButton(bx, rowY, "prev", "", false, !canPrev, () -> page = Math.max(0, page - 1));
        ui.bigButton(bx + bw + 8f, rowY, "plus", SolMenu.tr("sol.menu.emotes.add_page"), false, true, () -> {
            if (Emotes.addPage(elytra)) {
                page = Emotes.pages(elytra) - 1;
            }
        });
        ui.bigButton(bx + bw + 8f + addW + 8f, rowY, "next", "", false, !canNext, () -> page = Math.min(Emotes.pages(elytra) - 1, page + 1));

        // You, doing it.
        float vx = x + w - PANE_W;
        Pix.pane(g, p, vx, gy, PANE_W, gh);
        double gs = Minecraft.getInstance().getWindow().getGuiScale();
        px1 = (int) Math.round(g.d.sx(vx + 8f) / gs);
        px2 = (int) Math.round(g.d.sx(vx + PANE_W - 8f) / gs);
        py1 = (int) Math.round(g.d.sy(gy + 16f) / gs);
        py2 = (int) Math.round(g.d.sy(gy + gh - 90f) / gs);
        shown = picker == null && py2 - py1 > 20;
        Pix.paneEdge(g, p, vx, gy, PANE_W, gh);
        Emote now = playing != null ? playing : picked;
        Pix.caption(g, p, vx, gy, PANE_W, gh, now != null ? Emotes.name(now) : SolMenu.tr("sol.menu.emotes.you"),
                SolMenu.tr(now != null ? "sol.menu.emotes.playing_line" : "sol.menu.emotes.you_line"));

        if (picker != null) {
            g.rect(g.d.left(), g.d.top(), g.d.right() - g.d.left(), g.d.bottom() - g.d.top(), 0x99000000);
            picker.draw(g, ui, tk, 640f, 380f, menu.mouseY(), pictures, () -> picker = null);
        }
    }

    private Emote firstOnPage() {
        for (int i = 0; i < CosmeticsConfig.WHEEL_SLOTS; i++) {
            Emote e = Emotes.at(elytra, page * CosmeticsConfig.WHEEL_SLOTS + i);
            if (e != null) {
                return e;
            }
        }
        return null;
    }

    @Override
    public void game(SolMenu menu, Canvas c, int mouseX, int mouseY, float dt) {
        if (!shown) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        spin += SolMenu.calm() ? 0f : dt * 14f;
        if (mc.player == null) {
            if (mannequin == null) {
                mannequin = new Mannequin("menu_emotes", 256);
            }
            int side = Math.min(px2 - px1, py2 - py1);
            mannequin.draw(c, (px1 + px2 - side) / 2, (py1 + py2 - side) / 2, side, -spin, 8f, false);
            return;
        }
        int scale = Math.max(16, (int) ((py2 - py1) / 2.9f));
        c.entityTurned(px1, py1, px2, py2, scale, 0.0625f, mouseX, mouseY, mc.player, 20f, 0f);
    }

    @Override
    public boolean scroll(float amount) {
        if (picker != null) {
            picker.scroll(amount);
            return true;
        }
        return false;
    }

    @Override
    public void left() {
        picker = null;
        shown = false;
    }

    @Override
    public void closed() {
        shown = false;
        picker = null;
        if (pictures != null) {
            pictures.close();
            pictures = null;
        }
        if (mannequin != null) {
            mannequin.release();
            mannequin = null;
        }
    }
}
