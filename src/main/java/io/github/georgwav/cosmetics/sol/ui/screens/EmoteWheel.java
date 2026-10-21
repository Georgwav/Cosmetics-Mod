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
    /** Picking the emote for a slot, or null while the wheel shows. */
    private EmotePicker picker;

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
            startPicking(page * PER_PAGE);
        }
        return this;
    }

    /**
     * The hub's text inside its disc: on one line when it fits, else on two (split at the space nearest the middle),
     * else in Silkscreen's smallest size, and only then cut.
     */
    private void hubText(Gfx g, SolFont f, String text, int colour) {
        float room = HUB * 1.8f;
        if (g.width(f, text) <= room) {
            g.textCentre(f, text, CX, CY, colour);
            return;
        }
        int best = -1;
        for (int i = text.indexOf(' '); i >= 0; i = text.indexOf(' ', i + 1)) {
            if (best < 0 || Math.abs(i - text.length() / 2) < Math.abs(best - text.length() / 2)) {
                best = i;
            }
        }
        if (best > 0) {
            String a = text.substring(0, best), b = text.substring(best + 1);
            float line = f.cap() / g.d.u + 7f;
            if (g.width(f, a) <= room * 0.95f && g.width(f, b) <= room * 0.95f) {
                g.textCentre(f, a, CX, CY - line / 2f, colour);
                g.textCentre(f, b, CX, CY + line / 2f, colour);
                return;
            }
        }
        SolFont small = g.font(SolFont.Family.PIXEL_BOLD, 10f);
        g.textCentre(small, g.fitLabel(small, text, room), CX, CY, colour);
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void frame(Gfx g) {
        framesRendered++;
        Tokens tk = ThemeState.tokens();
        ui.begin(g, mx, my);
        // A light dim, darker round the wheel, so you still see yourself and the world.
        g.rectScreen(g.d.left(), g.d.top(), g.d.right(), g.d.bottom(), 0x5004050A, 0x8004050A);
        if (picker != null) {
            picker.draw(g, ui, tk, CX, 360f, my, pictures, () -> picker = null);
        } else {
            wheel(g, tk);
        }
    }

    private void wheel(Gfx g, Tokens tk) {
        pointed = -1;
        // The ring the slots sit on, faintly.
        g.ring(CX, CY, RING, Math.max(1f, g.d.u) * 1.5f, Colors.withAlpha(tk.rim, 0.25f));
        SolFont small = g.font(SolFont.Family.MEDIUM, 12f), mono = g.font(SolFont.Family.PIXEL, 11f);
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
            int fi = ui.click(sx, sy, SLOT, SLOT, () -> {
                if (emote == null) {
                    startPicking(index);
                } else {
                    play(emote);
                }
            });
            // The gear and the cross after the slot, so their hits are on top: a click on them isn't a click on it.
            int gearFocus = -1, crossFocus = -1;
            if (emote != null && hover) {
                gearFocus = ui.click(sx + SLOT - 30f, sy + 4f, 26f, 26f, () -> startPicking(index));
                crossFocus = ui.click(sx + SLOT - 30f, sy + SLOT - 30f, 26f, 26f, () -> {
                    Emotes.assign(elytra, index, "");
                    CosmeticsConfig.save();
                });
            }
            io.github.georgwav.cosmetics.sol.ui.kit.PixelTokens p = io.github.georgwav.cosmetics.sol.ui.kit.PixelTokens.get();
            io.github.georgwav.cosmetics.sol.ui.kit.Pix.slab(g, p, sx, sy, SLOT, SLOT, playing || hover && emote != null
                    ? io.github.georgwav.cosmetics.sol.ui.kit.Pix.ON : hover ? io.github.georgwav.cosmetics.sol.ui.kit.Pix.HOVER
                    : io.github.georgwav.cosmetics.sol.ui.kit.Pix.NORMAL);
            if (emote == null) {
                int c = hover ? p.acc : 0xFF8D8892;
                Icons.draw(g, "plus", sx + SLOT / 2f - 18f, sy + SLOT / 2f - 6f - 18f, 36f, c, 0f, 0);
                g.textCentre(small, t("add"), sx + SLOT / 2f, sy + SLOT - 16f, hover ? 0xFFFFFFFF : 0x80FFFFFF);
            } else {
                Canvas c = g.game();
                int s = Math.round((SLOT - 12f) * g.d.u);
                pictures.draw(c, emote, Math.round(g.d.sx(sx + SLOT / 2f)) - s / 2, Math.round(g.d.sy(sy + SLOT / 2f)) - s / 2, s);
                g.layer();
                g.text(mono, Integer.toString(i + 1), sx + 9f, sy + 16f, 0x80FFFFFF);
                if (hover) {
                    boolean onGear = ui.over(sx + SLOT - 30f, sy + 4f, 26f, 26f);
                    boolean onCross = ui.over(sx + SLOT - 30f, sy + SLOT - 30f, 26f, 26f);
                    io.github.georgwav.cosmetics.sol.ui.kit.Pix.slab(g, p, sx + SLOT - 30f, sy + 4f, 26f, 26f,
                            onGear ? io.github.georgwav.cosmetics.sol.ui.kit.Pix.HOVER : io.github.georgwav.cosmetics.sol.ui.kit.Pix.NORMAL);
                    Icons.draw(g, "gear", sx + SLOT - 29f, sy + 5f, 24f, onGear ? p.acc : 0xFFFFFFFF, 0f, 0);
                    io.github.georgwav.cosmetics.sol.ui.kit.Pix.redClose(g, p, sx + SLOT - 30f, sy + SLOT - 30f, 26f, 26f, onCross);
                    if (ui.focused(gearFocus) || ui.focused(crossFocus)) {
                        float fy = ui.focused(gearFocus) ? sy + 4f : sy + SLOT - 30f;
                        g.stroke(sx + SLOT - 32f, fy - 2f, 30f, 30f, 0f, g.px(2f), tk.accent);
                    }
                }
                // The name under the slot.
                g.textCentre(small, g.fit(small, Emotes.name(emote), SLOT + 28f), sx + SLOT / 2f, sy + SLOT + 13f,
                        hover ? 0xFFFFFFFF : 0x99FFFFFF);
            }
            if (ui.focused(fi)) {
                g.stroke(sx - 3f, sy - 3f, SLOT + 6f, SLOT + 6f, 0f, g.px(2f), tk.accent);
            }
        }
        // The disc in the middle: the name pointed at, the wheel's name, or Stop while an emote plays.
        // A pixel disc: the slab colour inside an edge ring.
        io.github.georgwav.cosmetics.sol.ui.kit.PixelTokens hp = io.github.georgwav.cosmetics.sol.ui.kit.PixelTokens.get();
        g.circle(CX, CY, HUB, hp.c1);
        g.ring(CX, CY, HUB, 3f * g.d.u, hp.edge);
        SolFont mid = g.font(SolFont.Family.PIXEL_BOLD, 15f);
        if (EmotePlayer.playing() != null) {
            float bw = g.width(small, t("stop")) + 28f;
            ui.button(tk, CX + bw / 2f, CY, t("stop"), true, () -> {
                EmotePlayer.stop();
                onClose();
            });
        } else {
            String middle = hoveredName != null ? hoveredName : title.getString();
            hubText(g, mid, middle, hoveredName != null ? 0xFFFFFFFF : 0xB3FFFFFF);
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
        picker = new EmotePicker(elytra, slot);
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
        if (picker != null) {
            picker.scroll(amount);
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
        if (picker != null) {
            picker = null;
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
        if (picker != null) {
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
