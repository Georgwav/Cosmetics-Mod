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
    protected int plainWidth(Minecraft mc) {
        return WIDTH;
    }

    @Override
    protected int plainHeight(Minecraft mc) {
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

    // ---------------------------------------------------------------- Sol UI: glass keys

    private static final float S_KEY = 17f, S_GAP = 2.5f, S_SPACE = 8f, S_WIDTH = S_KEY * 3 + S_GAP * 2;
    /** How lit each key is (eased toward held, so a tap glows briefly). */
    private final float[] lit = new float[7];
    private long lastFrame;

    @Override
    protected int solWidth(Minecraft mc) {
        return (int) Math.ceil(S_WIDTH);
    }

    @Override
    protected int solHeight(Minecraft mc) {
        return (int) Math.ceil(S_KEY * 2 + S_GAP + (mouse.get() ? S_GAP + S_KEY : 0f) + (space.get() ? S_GAP + S_SPACE : 0f));
    }

    @Override
    protected void drawSol(io.github.georgwav.cosmetics.sol.ui.kit.Gfx g, io.github.georgwav.cosmetics.sol.theme.Tokens tk,
                           Minecraft mc) {
        long now = System.nanoTime() / 1_000_000L;
        float dt = lastFrame == 0L ? 0f : Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        var f = g.font(io.github.georgwav.cosmetics.sol.ui.kit.SolFont.Family.MONO, 5.5f);
        var small = g.font(io.github.georgwav.cosmetics.sol.ui.kit.SolFont.Family.MEDIUM, 4.5f);
        solKey(g, tk, f, small, 0, mc.options.keyUp, null, S_KEY + S_GAP, 0f, S_KEY, S_KEY, null, dt);
        solKey(g, tk, f, small, 1, mc.options.keyLeft, null, 0f, S_KEY + S_GAP, S_KEY, S_KEY, null, dt);
        solKey(g, tk, f, small, 2, mc.options.keyDown, null, S_KEY + S_GAP, S_KEY + S_GAP, S_KEY, S_KEY, null, dt);
        solKey(g, tk, f, small, 3, mc.options.keyRight, null, (S_KEY + S_GAP) * 2, S_KEY + S_GAP, S_KEY, S_KEY, null, dt);
        float y = (S_KEY + S_GAP) * 2;
        if (mouse.get()) {
            float w = (S_WIDTH - S_GAP) / 2f;
            solKey(g, tk, f, small, 4, mc.options.keyAttack, "LMB", 0f, y, w, S_KEY, cps.get() ? Hud.leftCps() + " CPS" : null, dt);
            solKey(g, tk, f, small, 5, mc.options.keyUse, "RMB", w + S_GAP, y, S_WIDTH - w - S_GAP, S_KEY,
                    cps.get() ? Hud.rightCps() + " CPS" : null, dt);
            y += S_KEY + S_GAP;
        }
        if (space.get()) {
            float k = light(6, mc.options.keyJump.isDown(), dt);
            solBox(g, tk, 0f, y, S_WIDTH, S_SPACE, k);
            int line = io.github.georgwav.cosmetics.sol.util.Colors.mix(0xCCFFFFFF, tk.onAccent, k);
            g.round(S_WIDTH / 2f - 10f, y + S_SPACE / 2f - 0.5f, 20f, 1f, 0.5f, line);
        }
    }

    private float light(int i, boolean down, float dt) {
        float target = down ? 1f : 0f;
        lit[i] += (target - lit[i]) * Math.min(1f, dt * (down ? 30f : 9f));
        if (io.github.georgwav.cosmetics.sol.ui.kit.SolScreen.calm() || dt == 0f) {
            lit[i] = target;
        }
        return lit[i];
    }

    private void solKey(io.github.georgwav.cosmetics.sol.ui.kit.Gfx g, io.github.georgwav.cosmetics.sol.theme.Tokens tk,
                        io.github.georgwav.cosmetics.sol.ui.kit.SolFont f, io.github.georgwav.cosmetics.sol.ui.kit.SolFont small,
                        int i, KeyMapping mapping, String fixed, float x, float y, float w, float h, String below, float dt) {
        float k = light(i, mapping.isDown(), dt);
        solBox(g, tk, x, y, w, h, k);
        String label = fixed != null ? fixed : KeyBinds.name(mapping);
        label = g.fit(f, label, w - 3f);
        int color = io.github.georgwav.cosmetics.sol.util.Colors.mix(0xFFFFFFFF, tk.onAccent, k);
        if (below == null) {
            g.textCentre(f, label, x + w / 2f, y + h / 2f, color);
        } else {
            g.textCentre(f, label, x + w / 2f, y + h / 2f - 2.5f, color);
            g.textCentre(small, below, x + w / 2f, y + h / 2f + 3.5f, io.github.georgwav.cosmetics.sol.util.Colors.fade(color, 0.75f));
        }
    }

    /** A glass key, filling with the accent and glowing as it's held. */
    private static void solBox(io.github.georgwav.cosmetics.sol.ui.kit.Gfx g, io.github.georgwav.cosmetics.sol.theme.Tokens tk,
                               float x, float y, float w, float h, float k) {
        float r = Math.min(4.5f, h / 2f);
        if (k < 1f) {
            io.github.georgwav.cosmetics.sol.hud.HudKit.card(g, tk, x, y, w, h, r);
        }
        if (k > 0.01f) {
            g.glow(x, y, w, h, r, 6f, io.github.georgwav.cosmetics.sol.util.Colors.withAlpha(tk.accent, 0.5f * k));
            g.round(x, y, w, h, r, io.github.georgwav.cosmetics.sol.util.Colors.withAlpha(tk.accent, k));
        }
    }

    private void box(Canvas g, int x, int y, int w, int h, boolean down) {
        if (down) {
            g.fill(x, y, x + w, y + h, 0xD0FFFFFF);
        } else if (backing.get()) {
            g.fill(x, y, x + w, y + h, 0x70000000);
        }
    }
}
