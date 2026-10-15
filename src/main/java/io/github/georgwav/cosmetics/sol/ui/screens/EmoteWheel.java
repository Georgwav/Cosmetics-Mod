package io.github.georgwav.cosmetics.sol.ui.screens;

import io.github.georgwav.cosmetics.config.CosmeticsConfig;
import io.github.georgwav.cosmetics.emote.Emote;
import io.github.georgwav.cosmetics.emote.EmotePlayer;
import io.github.georgwav.cosmetics.emote.Emotes;
import io.github.georgwav.cosmetics.sol.Feature;
import io.github.georgwav.cosmetics.sol.theme.ThemeState;
import io.github.georgwav.cosmetics.sol.theme.Tokens;
import io.github.georgwav.cosmetics.sol.ui.kit.Gfx;
import io.github.georgwav.cosmetics.sol.ui.kit.Icons;
import io.github.georgwav.cosmetics.sol.ui.kit.SolFont;
import io.github.georgwav.cosmetics.sol.ui.kit.SolScreen;
import io.github.georgwav.cosmetics.sol.ui.kit.Ui;
import io.github.georgwav.cosmetics.sol.util.Colors;
import io.github.georgwav.cosmetics.ui.Canvas;
import io.github.georgwav.cosmetics.ui.EmotePictures;
import io.github.georgwav.cosmetics.ui.ScreenOpener;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
//? if >=1.21.9 {
import net.minecraft.client.input.KeyEvent;
//?}

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The emote wheel (B by default), in the Sol style: eight glass slots in a ring round a glass disc, each with its
 * emote's most telling pose.
 * - Hold the key, point at an emote and let go to play it; or tap the key and click one.
 * - Empty slots show a "+": click it to pick an emote. On a filled slot the gear changes it and the cross empties it.
 * - The scroll wheel turns the page; pages can be added (up to 20), and an empty one removed.
 * - Opened while gliding it is the elytra wheel, with its own slots, whose emotes play until you land.
 * The logic is the Cosmetics screen's emote wheel, unchanged.
 */
public final class EmoteWheel extends SolScreen {
    private static final int PER_PAGE = CosmeticsConfig.WHEEL_SLOTS;
    /** Letting go of the key this soon after it opened the wheel was a tap: the wheel stays open for clicking. */
    private static final long TAP_NANOS = 300_000_000L;
    private static final float CX = 640f, CY = 352f, RING = 205f, SLOT = 92f, HUB = 74f;
    private static final float TILE = 84f, TILE_GAP = 12f;
    /** Remembered between openings. */
    private static int lastPage;
    /** Frames drawn (read by the in-game test). */
    public static int framesRendered;
    /** The key that opens the wheel (set when it is registered). */
    public static KeyMapping key;

    private final long openedNanos = System.nanoTime();
    /** The elytra wheel (opened while gliding). */
    private final boolean elytra;
    private final EmotePictures pictures = new EmotePictures();
    private final Ui ui = new Ui();
    private int page;
    /** The slot pointed at this frame (its index), or -1. */
    private int pointed = -1;
    /** The slot an emote is being picked for, or -1 while the wheel shows. */
    private int picking = -1;
    private float pickerScroll;

    public EmoteWheel(boolean elytra) {
        super(Component.translatable(elytra ? "cosmeticsmod.emotes.elytra_title" : "cosmeticsmod.emotes.title"), null, Feature.MENU);
        this.elytra = elytra;
        this.page = Math.max(0, Math.min(lastPage, Emotes.pages(elytra) - 1));
    }

    /** Opens the wheel (the elytra wheel while gliding), unless another screen is open or emotes are off. */
    public static void openIfAllowed(Minecraft client) {
        if (ScreenOpener.current() == null && client.player != null && CosmeticsConfig.get().emotes) {
            ScreenOpener.open(new EmoteWheel(client.player.isFallFlying()));
        }
    }

    /** Whether this is the elytra wheel (for the in-game test). */
    public boolean isElytraWheel() {
        return elytra;
    }

    /** Emote pictures made since the game started (for the in-game test). */
    public static int picturesMade() {
        return EmotePictures.made;
    }

    private static String t(String key, Object... args) {
        return Component.translatable("cosmeticsmod.emotes." + key, args).getString();
    }

    private int pages() {
        return Emotes.pages(elytra);
    }

    private boolean pageEmpty() {
        for (int i = 0; i < PER_PAGE; i++) {
            if (Emotes.at(elytra, page * PER_PAGE + i) != null) {
                return false;
            }
        }
        return true;
    }

    private void turn(int by) {
        page = Math.floorMod(page + by, pages());
        lastPage = page;
    }

    private void play(Emote emote) {
        EmotePlayer.play(emote, elytra);
        onClose();
    }

    /** For the scenario: "picker" shows the picker for the first slot. */
    public EmoteWheel stage(String state) {
        if (state.equals("picker")) {
            picking = page * PER_PAGE;
        }
        return this;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void frame(Gfx g) {
        framesRendered++;
        Tokens tk = ThemeState.tokens();
        ui.begin(g, mx, my);
        // A light dim, darker round the wheel, so you still see yourself and the world.
        g.rectScreen(g.d.left(), g.d.top(), g.d.right(), g.d.bottom(), 0x5004050A, 0x8004050A);
        if (picking >= 0) {
            picker(g, tk);
        } else {
            wheel(g, tk);
        }
    }

    private void wheel(Gfx g, Tokens tk) {
        pointed = -1;
        // The ring the slots sit on, faintly.
        g.ring(CX, CY, RING, Math.max(1f, g.d.u) * 1.5f, Colors.withAlpha(tk.rim, 0.25f));
        SolFont small = g.font(SolFont.Family.MEDIUM, 12f), mono = g.font(SolFont.Family.MONO, 11f);
        String hoveredName = null;
        for (int i = 0; i < PER_PAGE; i++) {
            // Clockwise from the top.
            double a = -Math.PI / 2 + i * 2 * Math.PI / PER_PAGE;
            float sx = CX + (float) Math.cos(a) * RING - SLOT / 2f, sy = CY + (float) Math.sin(a) * RING - SLOT / 2f;
            int index = page * PER_PAGE + i;
            Emote emote = Emotes.at(elytra, index);
            boolean hover = ui.over(sx, sy, SLOT, SLOT);
            if (hover) {
                pointed = index;
                hoveredName = emote != null ? Emotes.name(emote) : t("add");
            }
            boolean playing = emote != null && EmotePlayer.playing() == emote;
            // The gear and the cross first, so a click on them isn't a click on the slot.
            if (emote != null && hover) {
                ui.tool(tk, sx + SLOT - 30f, sy + 4f, "gear", false, () -> startPicking(index));
                ui.tool(tk, sx + SLOT - 30f, sy + SLOT - 30f, "close", false, () -> {
                    Emotes.assign(elytra, index, "");
                    CosmeticsConfig.save();
                });
            }
            int fi = ui.click(sx, sy, SLOT, SLOT, () -> {
                if (emote == null) {
                    startPicking(index);
                } else {
                    play(emote);
                }
            });
            if (hover || playing) {
                g.glow(sx, sy, SLOT, SLOT, 20f, 26f, Colors.withAlpha(tk.glow, hover ? 0.35f : 0.22f));
            }
            g.round(sx, sy, SLOT, SLOT, 20f, hover ? Colors.mix(tk.panel, 0xFFFFFFFF, 0.08f) : tk.panel);
            g.stroke(sx, sy, SLOT, SLOT, 20f, Math.max(1f, g.d.u), hover || playing ? tk.accent : 0x29FFFFFF);
            g.highlight(sx, sy, SLOT, SLOT, 20f, 0x2EFFFFFF);
            if (emote == null) {
                float arm = 12f, bar = 3f;
                int c = hover ? tk.accent : 0x66FFFFFF;
                g.round(sx + SLOT / 2f - arm, sy + SLOT / 2f - 6f - bar / 2f, arm * 2f, bar, bar / 2f, c);
                g.round(sx + SLOT / 2f - bar / 2f, sy + SLOT / 2f - 6f - arm, bar, arm * 2f, bar / 2f, c);
                g.textCentre(small, t("add"), sx + SLOT / 2f, sy + SLOT - 16f, hover ? 0xFFFFFFFF : 0x80FFFFFF);
            } else {
                Canvas c = g.game();
                int s = Math.round((SLOT - 12f) * g.d.u);
                pictures.draw(c, emote, Math.round(g.d.sx(sx + SLOT / 2f)) - s / 2, Math.round(g.d.sy(sy + SLOT / 2f)) - s / 2, s);
                g.layer();
                g.text(mono, Integer.toString(i + 1), sx + 9f, sy + 16f, 0x80FFFFFF);
                if (hover) {
                    g.round(sx + SLOT - 30f, sy + 4f, 26f, 26f, 7f, 0xB30A0A0E);
                    Icons.draw(g, "gear", sx + SLOT - 23f, sy + 11f, 12f, 0xFFFFFFFF, Icons.FILL, 0);
                    g.round(sx + SLOT - 30f, sy + SLOT - 30f, 26f, 26f, 7f, Colors.withAlpha(Tokens.RED, 0.75f));
                    Icons.draw(g, "close", sx + SLOT - 23f, sy + SLOT - 23f, 12f, 0xFFFFFFFF, Icons.FILL, 0);
                }
                // The name under the slot.
                g.textCentre(small, g.fit(small, Emotes.name(emote), SLOT + 28f), sx + SLOT / 2f, sy + SLOT + 13f,
                        hover ? 0xFFFFFFFF : 0x99FFFFFF);
            }
            if (ui.focused(fi)) {
                g.stroke(sx - 3f, sy - 3f, SLOT + 6f, SLOT + 6f, 22f, 2f * g.d.u, tk.accent);
            }
        }
        // The disc in the middle: the name pointed at, the wheel's name, or Stop while an emote plays.
        g.glow(CX - HUB, CY - HUB, HUB * 2f, HUB * 2f, HUB, 30f, Colors.withAlpha(tk.glow, 0.18f));
        g.circle(CX, CY, HUB, tk.panel);
        g.ring(CX, CY, HUB, Math.max(1f, g.d.u), tk.rim);
        SolFont mid = g.font(SolFont.Family.SEMIBOLD, 15f);
        if (EmotePlayer.playing() != null) {
            float bw = g.width(small, t("stop")) + 28f;
            ui.button(tk, CX + bw / 2f, CY, t("stop"), true, () -> {
                EmotePlayer.stop();
                onClose();
            });
        } else {
            String middle = hoveredName != null ? hoveredName : title.getString();
            g.textCentre(mid, g.fit(mid, middle, HUB * 1.8f), CX, CY, hoveredName != null ? 0xFFFFFFFF : 0xB3FFFFFF);
        }
        // Pages: turn, add one, remove an empty one; one row under the wheel.
        float by = CY + RING + SLOT / 2f + 42f;
        String label = (page + 1) + " / " + pages();
        float half = g.width(mono, label) / 2f + 10f;
        if (pages() > 1) {
            g.textCentre(mono, label, CX, by, 0x99FFFFFF);
            ui.button(tk, CX - half, by, "<", false, () -> turn(-1));
            ui.button(tk, CX + half + g.width(small, ">") + 28f, by, ">", false, () -> turn(1));
        }
        float r = CX + half + g.width(small, ">") + 40f + g.width(small, t("add_page")) + 28f;
        ui.button(tk, r, by, t("add_page"), false, () -> {
            if (Emotes.addPage(elytra)) {
                page = pages() - 1;
                lastPage = page;
                CosmeticsConfig.save();
            }
        });
        if (pages() > 1 && pageEmpty()) {
            ui.button(tk, CX - half - g.width(small, "<") - 40f, by, t("remove_page"), false, () -> {
                Emotes.removePage(elytra, page);
                page = Math.max(0, page - 1);
                lastPage = page;
                CosmeticsConfig.save();
            });
        }
        g.textCentre(small, t("hint"), CX, 700f, 0x73FFFFFF);
    }

    private void startPicking(int slot) {
        picking = slot;
        pickerScroll = 0f;
    }

    /** Every emote as a tile to pick from, made for this wheel first, scrolled with the mouse wheel. */
    private void picker(Gfx g, Tokens tk) {
        float pw = 720f, ph = 520f, px = CX - pw / 2f, py = 360f - ph / 2f;
        g.glow(px, py, pw, ph, 20f, 34f, Colors.withAlpha(tk.glow, 0.16f));
        g.round(px, py, pw, ph, 20f, Colors.withAlpha(tk.panel, 0.94f));
        g.stroke(px, py, pw, ph, 20f, Math.max(1f, g.d.u), tk.rim);
        g.highlight(px, py, pw, ph, 20f, 0x2EFFFFFF);
        ui.area(px, py, pw, ph, () -> { });
        SolFont h = g.font(SolFont.Family.SEMIBOLD, 17f), small = g.font(SolFont.Family.MEDIUM, 12f);
        g.textMid(h, t("pick_title", picking % PER_PAGE + 1), px + 22f, py + 30f, 0xFFFFFFFF);
        List<Emote> all = new ArrayList<>(Emotes.all());
        all.sort(Comparator.comparing((Emote e) -> e.elytra() != elytra));
        float gx = px + 22f, gy = py + 58f, gw = pw - 44f, gh = ph - 58f - 60f;
        int cols = Math.max(1, (int) ((gw + TILE_GAP) / (TILE + TILE_GAP)));
        float left = gx + (gw - (cols * (TILE + TILE_GAP) - TILE_GAP)) / 2f;
        float rowH = TILE + 26f;
        int rows = (all.size() + cols - 1) / cols;
        float maxScroll = Math.max(0f, rows * rowH - gh);
        pickerScroll = Math.max(0f, Math.min(maxScroll, pickerScroll));
        g.clip(gx - 4f, gy - 4f, gx + gw + 4f, gy + gh);
        Emote current = Emotes.at(elytra, picking);
        for (int i = 0; i < all.size(); i++) {
            Emote e = all.get(i);
            float tx = left + (i % cols) * (TILE + TILE_GAP), ty = gy + (i / cols) * rowH - pickerScroll;
            if (ty + rowH < gy || ty > gy + gh) {
                continue;
            }
            boolean hover = ui.over(tx, ty, TILE, TILE) && my >= gy && my <= gy + gh;
            ui.click(tx, ty, TILE, TILE, () -> pick(e));
            g.round(tx, ty, TILE, TILE, 14f, hover ? Colors.mix(tk.panel, 0xFFFFFFFF, 0.1f) : 0x14FFFFFF);
            g.stroke(tx, ty, TILE, TILE, 14f, Math.max(1f, g.d.u), hover ? tk.accent : e == current ? tk.rim : 0x1FFFFFFF);
            Canvas c = g.game();
            int s = Math.round((TILE - 8f) * g.d.u);
            pictures.draw(c, e, Math.round(g.d.sx(tx + TILE / 2f)) - s / 2, Math.round(g.d.sy(ty + TILE / 2f)) - s / 2, s);
            g.layer();
            g.textCentre(small, g.fit(small, Emotes.name(e), TILE + TILE_GAP - 2f), tx + TILE / 2f, ty + TILE + 12f,
                    hover ? 0xFFFFFFFF : 0x99FFFFFF);
        }
        g.endClip();
        if (all.isEmpty()) {
            g.textCentre(small, t("none"), CX, 360f, 0x99FFFFFF);
        }
        float by = py + ph - 30f;
        ui.button(tk, px + pw - 22f, by, t("back"), false, () -> picking = -1);
        if (current != null) {
            ui.button(tk, px + 22f + g.width(small, t("empty_slot")) + 28f, by, t("empty_slot"), false, () -> {
                Emotes.assign(elytra, picking, "");
                CosmeticsConfig.save();
                picking = -1;
            });
        }
    }

    /** Puts {@code emote} in the slot being filled; an emote is on a wheel once, so it leaves its old slot. */
    private void pick(Emote emote) {
        for (int i = 0; i < Emotes.slots(elytra).size(); i++) {
            if (i != picking && Emotes.slots(elytra).get(i).equals(emote.id())) {
                Emotes.assign(elytra, i, "");
            }
        }
        Emotes.assign(elytra, picking, emote.id());
        CosmeticsConfig.save();
        picking = -1;
    }

    // ------------------------------------------------------------------ input

    @Override
    protected boolean press(float x, float y, int button) {
        return ui.press(x, y);
    }

    @Override
    protected boolean release(float x, float y, int button) {
        return ui.release();
    }

    @Override
    protected boolean scroll(float x, float y, float amount) {
        if (amount == 0f) {
            return false;
        }
        if (picking >= 0) {
            pickerScroll -= amount * 60f;
            return true;
        }
        if (pages() > 1) {
            turn(amount > 0 ? -1 : 1);
            return true;
        }
        return false;
    }

    /** Escape in the picker goes back to the wheel; otherwise it closes the wheel. */
    @Override
    protected boolean back() {
        if (picking >= 0) {
            picking = -1;
            return true;
        }
        return false;
    }

    @Override
    protected void closed() {
        pictures.close();
    }

    // Letting go of the wheel key: plays the emote pointed at; held a while and pointing at nothing closes the wheel.
    // While picking, the key does nothing.
    private boolean wheelKeyReleased() {
        if (picking >= 0) {
            return true;
        }
        Emote emote = pointed >= 0 ? Emotes.at(elytra, pointed) : null;
        if (emote != null) {
            play(emote);
            return true;
        }
        if (System.nanoTime() - openedNanos > TAP_NANOS) {
            onClose();
            return true;
        }
        return false;
    }

    //? if >=1.21.9 {
    @Override
    public boolean keyReleased(KeyEvent event) {
        if (key != null && key.matches(event) && wheelKeyReleased()) {
            return true;
        }
        return super.keyReleased(event);
    }
    //?} else {
    /*@Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (key != null && key.matches(keyCode, scanCode) && wheelKeyReleased()) {
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }
    *///?}
}
