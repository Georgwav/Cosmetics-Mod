package io.github.georgwav.cosmetics.sol.chat;

import io.github.georgwav.cosmetics.client.modules.SolChat;
import io.github.georgwav.cosmetics.sol.theme.ThemeState;
import io.github.georgwav.cosmetics.sol.theme.Tokens;
import io.github.georgwav.cosmetics.sol.util.Colors;
import io.github.georgwav.cosmetics.ui.Canvas;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Sol Chat's emoji picker: a small button at the end of the chat box opens a grid of the shortcodes' symbols
 * above it; one clicked goes into the message where the cursor is (as the symbol, so everyone sees it). Esc or a
 * click elsewhere closes the grid. The chat screen's mixin (ChatEmojiMixin) calls these.
 */
public final class EmojiPicker {
    /** The button's room at the end of the chat box. */
    public static final int BUTTON = 12;
    private static final int CELL = 14, COLUMNS = 8, PAD = 4;
    private static final List<Map.Entry<String, String>> ENTRIES = new ArrayList<>(Emoji.CODES.entrySet());
    private static boolean open;
    /** Emoji put into the chat box, and frames the grid was drawn (for the self-test). */
    public static int inserted, gridsDrawn;

    private EmojiPicker() {
    }

    /** Whether the picker is offered (Sol Chat and its emoji on). */
    public static boolean shown() {
        return SolChat.emojiOn();
    }

    /** A new chat screen starts with the grid shut. */
    public static void reset() {
        open = false;
    }

    public static boolean isOpen() {
        return open;
    }

    /** Opens the grid on the chat screen just opened (the test scenario's shot). */
    public static void openForShot() {
        open = true;
    }

    private static int rows() {
        return (ENTRIES.size() + COLUMNS - 1) / COLUMNS;
    }

    /** The button: x, y, size (at the chat box's right end). */
    private static int[] button(int width, int height) {
        return new int[]{width - 2 - BUTTON, height - 13, BUTTON - 1};
    }

    /** The grid's top-left corner (above the button, its right edge on the screen's). */
    private static int[] grid(int width, int height) {
        int w = COLUMNS * CELL + PAD * 2, h = rows() * CELL + PAD * 2;
        return new int[]{width - 2 - w, height - 16 - h, w, h};
    }

    public static void draw(Canvas g, int width, int height, int mouseX, int mouseY) {
        if (!shown()) {
            return;
        }
        Tokens tk = ThemeState.tokens();
        Font font = Minecraft.getInstance().font;
        int[] b = button(width, height);
        boolean overButton = in(mouseX, mouseY, b[0], b[1], b[2], b[2]);
        g.fill(b[0], b[1], b[0] + b[2], b[1] + b[2], open || overButton ? Colors.withAlpha(tk.accent, 0.55f) : 0x66000000);
        g.centeredText(font, "\u263a", b[0] + b[2] / 2 + 1, b[1] + 2, open || overButton ? tk.onAccent : 0xFFE0E0E0);
        if (!open) {
            return;
        }
        int[] r = grid(width, height);
        gridsDrawn++;
        g.nextLayer();
        g.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], Colors.withAlpha(tk.panel, 0.92f));
        g.outline(r[0], r[1], r[0] + r[2], r[1] + r[3], Colors.withAlpha(tk.rim, 0.7f));
        String hovered = null;
        for (int i = 0; i < ENTRIES.size(); i++) {
            int cx = r[0] + PAD + (i % COLUMNS) * CELL, cy = r[1] + PAD + (i / COLUMNS) * CELL;
            Map.Entry<String, String> e = ENTRIES.get(i);
            boolean over = in(mouseX, mouseY, cx, cy, CELL, CELL);
            if (over) {
                g.fill(cx, cy, cx + CELL, cy + CELL, Colors.withAlpha(tk.accent, 0.45f));
                hovered = ":" + e.getKey() + ":";
            }
            // The long ones (a shrug, a flipped table) show one symbol of theirs; the whole goes in.
            String shown = switch (e.getKey()) {
                case "shrug" -> "\u30c4";
                case "tableflip" -> "\u253b";
                default -> e.getValue();
            };
            g.centeredText(font, shown, cx + CELL / 2, cy + 3, 0xFFFFFFFF);
        }
        if (hovered != null) {
            g.text(font, hovered, r[0] + PAD, r[1] - 10, 0xFFFFFFFF, true);
        }
    }

    /** A click at (x, y): opens or shuts the grid, or puts an emoji in; true when it was the picker's. */
    public static boolean click(int width, int height, double x, double y, java.util.function.Consumer<String> insert) {
        if (!shown()) {
            return false;
        }
        int[] b = button(width, height);
        if (in(x, y, b[0], b[1], b[2], b[2])) {
            open = !open;
            return true;
        }
        if (!open) {
            return false;
        }
        int[] r = grid(width, height);
        if (!in(x, y, r[0], r[1], r[2], r[3])) {
            open = false;
            return false;
        }
        int col = (int) ((x - r[0] - PAD) / CELL), row = (int) ((y - r[1] - PAD) / CELL);
        int i = row * COLUMNS + col;
        if (col >= 0 && col < COLUMNS && row >= 0 && i < ENTRIES.size()) {
            insert.accept(ENTRIES.get(i).getValue());
            inserted++;
        }
        return true;
    }

    /** Esc shuts the grid first (true when it was open). */
    public static boolean escape() {
        if (open) {
            open = false;
            return true;
        }
        return false;
    }

    private static boolean in(double x, double y, int x0, int y0, int w, int h) {
        return x >= x0 && x < x0 + w && y >= y0 && y < y0 + h;
    }
}
