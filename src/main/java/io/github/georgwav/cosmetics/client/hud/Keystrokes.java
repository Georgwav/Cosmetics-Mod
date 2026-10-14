package io.github.georgwav.cosmetics.client.hud;

import io.github.georgwav.cosmetics.client.Setting;
import io.github.georgwav.cosmetics.ui.Canvas;
import io.github.georgwav.cosmetics.ui.KeyBinds;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/** The movement keys, the mouse buttons with clicks per second, and the space bar, lit while held. */
public final class Keystrokes extends HudModule {
    private static final int KEY = 22;
    private static final int GAP = 2;
    private static final int WIDTH = KEY * 3 + GAP * 2;
    private static final int SPACE = 10;
    private final Setting.Toggle mouse = add(new Setting.Toggle("mouse", true));
    private final Setting.Toggle cps = add(new Setting.Toggle("cps", true));
    private final Setting.Toggle space = add(new Setting.Toggle("space", true));

    public Keystrokes() {
        super("keystrokes", "keys", 1f, 0f, -4f, 22f);
    }

    @Override
    protected boolean wholeBacking() {
        return false;
    }

    @Override
    public int width(Minecraft mc) {
        return WIDTH;
    }

    @Override
    public int height(Minecraft mc) {
        return KEY * 2 + GAP + (mouse.get() ? GAP + KEY : 0) + (space.get() ? GAP + SPACE : 0);
    }

    @Override
    protected void draw(Canvas g, Minecraft mc) {
        key(g, mc, mc.options.keyUp, null, KEY + GAP, 0, KEY, KEY, null);
        key(g, mc, mc.options.keyLeft, null, 0, KEY + GAP, KEY, KEY, null);
        key(g, mc, mc.options.keyDown, null, KEY + GAP, KEY + GAP, KEY, KEY, null);
        key(g, mc, mc.options.keyRight, null, (KEY + GAP) * 2, KEY + GAP, KEY, KEY, null);
        int y = (KEY + GAP) * 2;
        if (mouse.get()) {
            int w = (WIDTH - GAP) / 2;
            key(g, mc, mc.options.keyAttack, "LMB", 0, y, w, KEY, cps.get() ? Hud.leftCps() + " CPS" : null);
            key(g, mc, mc.options.keyUse, "RMB", w + GAP, y, WIDTH - w - GAP, KEY, cps.get() ? Hud.rightCps() + " CPS" : null);
            y += KEY + GAP;
        }
        if (space.get()) {
            boolean down = mc.options.keyJump.isDown();
            box(g, 0, y, WIDTH, SPACE, down);
            int line = down ? 0xFF000000 : 0xFFFFFFFF;
            g.fill(WIDTH / 2 - 12, y + SPACE / 2, WIDTH / 2 + 12, y + SPACE / 2 + 1, line);
        }
    }

    /**
     * One key: lit while held, its bound key's name in the middle (or {@code fixed}, the mouse buttons), and the
     * clicks per second under it.
     */
    private void key(Canvas g, Minecraft mc, KeyMapping mapping, String fixed, int x, int y, int w, int h, String below) {
        boolean down = mapping.isDown();
        box(g, x, y, w, h, down);
        String label = fixed != null ? fixed : KeyBinds.name(mapping);
        while (label.length() > 1 && mc.font.width(label) > w - 4) {
            label = label.substring(0, label.length() - 1);
        }
        int color = down ? 0xFF000000 : 0xFFFFFFFF;
        if (below == null) {
            g.text(mc.font, label, x + (w - mc.font.width(label)) / 2, y + (h - 8) / 2, color, false);
            return;
        }
        g.text(mc.font, label, x + (w - mc.font.width(label)) / 2, y + 3, color, false);
        // The clicks per second at half size.
        g.push();
        g.translate(x + w / 2f, y + 14f);
        g.scale(0.5f, 0.5f);
        g.text(mc.font, below, -mc.font.width(below) / 2, 0, color, false);
        g.pop();
    }

    private void box(Canvas g, int x, int y, int w, int h, boolean down) {
        if (down) {
            g.fill(x, y, x + w, y + h, 0xD0FFFFFF);
        } else if (backing.get()) {
            g.fill(x, y, x + w, y + h, 0x70000000);
        }
    }
}
