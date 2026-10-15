package io.github.georgwav.cosmetics.sol.ui.kit;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.georgwav.cosmetics.sol.theme.Tokens;
import net.minecraft.client.Minecraft;

import java.util.function.Consumer;

/**
 * A one-line text field in the Sol style (the prototype's search pill): click to type in it, Backspace (Ctrl for the
 * whole text), Ctrl+V to paste, Esc or Enter to leave it. The text is edited at its end, as the prototype's fields
 * are; {@code onChange} hears every change.
 */
public final class TextField {
    private final int max;
    private final Consumer<String> onChange;
    private String text = "";
    private boolean focused;
    private float blink;

    public TextField(int max, Consumer<String> onChange) {
        this.max = max;
        this.onChange = onChange;
    }

    public String text() {
        return text;
    }

    /** Sets the text without telling {@code onChange} (for a field showing a saved value). */
    public void set(String value) {
        text = value == null ? "" : value.length() > max ? value.substring(0, max) : value;
    }

    public boolean focused() {
        return focused;
    }

    public void focus(boolean on) {
        focused = on;
        blink = 0f;
    }

    /** Draws it at (x, y), w x h design pixels, with an icon (or null) and a hint while empty. */
    public void draw(Gfx g, Ui ui, Tokens tk, float x, float y, float w, float h, String icon, String hint, float dt) {
        blink += dt;
        ui.click(x, y, w, h, () -> focus(true));
        boolean hover = ui.over(x, y, w, h);
        g.round(x, y, w, h, h / 2f, 0xCC08080C);
        g.stroke(x, y, w, h, h / 2f, Math.max(1f, g.d.u), focused ? tk.rim : hover ? 0x40FFFFFF : 0x24FFFFFF);
        float tx = x + 14f;
        if (icon != null) {
            Icons.draw(g, icon, x + 12f, y + h / 2f - 8f, 16f, 0x99FFFFFF, Icons.FILL, 0);
            tx = x + 36f;
        }
        SolFont f = g.font(SolFont.Family.MEDIUM, 13f);
        float room = x + w - 12f - tx;
        if (text.isEmpty()) {
            g.textMid(f, g.fit(f, hint, room), tx, y + h / 2f, 0x66FFFFFF);
        } else {
            // The end of a long text stays in view.
            String shown = text;
            while (shown.length() > 1 && g.width(f, shown) > room) {
                shown = shown.substring(1);
            }
            g.textMid(f, shown, tx, y + h / 2f, 0xFFFFFFFF);
            tx += g.width(f, shown);
        }
        if (focused && ((int) (blink * 2f)) % 2 == 0) {
            g.rect(tx + 1f, y + h / 2f - 8f, 1.5f, 16f, 0xFFFFFFFF);
        }
    }

    /** A key while focused; true if the field took it. */
    public boolean key(int key, boolean ctrl) {
        if (!focused) {
            return false;
        }
        switch (key) {
            case InputConstants.KEY_BACKSPACE -> {
                if (!text.isEmpty()) {
                    change(ctrl ? "" : text.substring(0, text.offsetByCodePoints(text.length(), -1)));
                }
            }
            case InputConstants.KEY_V -> {
                if (!ctrl) {
                    return false;
                }
                String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
                if (clip != null) {
                    change(text + clip.replaceAll("[\\r\\n\\t]", " "));
                }
            }
            case InputConstants.KEY_ESCAPE, InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> focused = false;
            default -> {
                return false;
            }
        }
        blink = 0f;
        return true;
    }

    /** A typed character while focused; true if the field took it. */
    public boolean typed(String s) {
        if (!focused || s.isEmpty() || Character.isISOControl(s.charAt(0))) {
            return false;
        }
        change(text + s);
        blink = 0f;
        return true;
    }

    private void change(String next) {
        if (next.length() > max) {
            next = next.substring(0, max);
        }
        if (!next.equals(text)) {
            text = next;
            onChange.accept(text);
        }
    }
}
