package io.github.georgwav.cosmetics.client.hud;

import io.github.georgwav.cosmetics.client.Module;
import io.github.georgwav.cosmetics.client.Modules;
import io.github.georgwav.cosmetics.client.Setting;
import io.github.georgwav.cosmetics.sol.fx.FxWorld;
import io.github.georgwav.cosmetics.sol.fx.Ramp;
import io.github.georgwav.cosmetics.sol.hud.HudKit;
import io.github.georgwav.cosmetics.sol.theme.ThemeState;
import io.github.georgwav.cosmetics.sol.theme.Tokens;
import io.github.georgwav.cosmetics.sol.ui.kit.Gfx;
import io.github.georgwav.cosmetics.sol.ui.kit.SolFont;
import io.github.georgwav.cosmetics.sol.util.Colors;
import io.github.georgwav.cosmetics.ui.Canvas;
import net.minecraft.client.Minecraft;

import java.util.List;

/**
 * Active modules: the modules that are on, listed down the right edge in pixel type, the widest at the top, each row
 * in the next of the theme's four tones (bright at the top, deep at the bottom). A module switched on slides in from
 * the edge, one switched off slides out, in whole steps. The HUD modules themselves are left out unless asked for.
 */
public final class ActiveModulesHud extends HudModule {
    private final Setting.Toggle hudModules = add(new Setting.Toggle("list_hud", false).group("behaviour"));
    private final Setting.Choice tones = add(new Setting.Choice("list_tones", "ramp", List.of("ramp", "accent", "white")).group("look"));

    /** A row: its module, how far it's in (0..1), and its name and width as last drawn. */
    private static final class Row {
        final Module module;
        float shown;
        String name = "";
        float width;

        Row(Module module) {
            this.module = module;
        }
    }

    private Row[] rows;
    /** The rows showing (or sliding), widest first; kept between frames so nothing is allocated. */
    private Row[] order;
    private int count;
    private long last;
    private final int[] ramp = new int[Ramp.TONES];
    /** For the in-game test: frames the list drew rows in. */
    public static int framesDrawn;

    public ActiveModulesHud() {
        super("active_modules", "lines", 1f, 0f, -2f, 2f);
    }

    private boolean listed(Module m) {
        return m != this && (hudModules.get() || m.category != Category.HUD);
    }

    /** Steps every row toward on or off and orders the ones showing, widest first ({@code font} measures them). */
    private void update(java.util.function.ToDoubleFunction<String> measure) {
        if (rows == null) {
            List<Module> all = Modules.all();
            rows = new Row[all.size()];
            order = new Row[all.size()];
            for (int i = 0; i < rows.length; i++) {
                rows[i] = new Row(all.get(i));
            }
        }
        long now = System.nanoTime();
        float dt = last == 0L ? 0f : Math.min(0.1f, (now - last) / 1e9f);
        last = now;
        boolean example = preview();
        count = 0;
        for (Row r : rows) {
            boolean on = listed(r.module) && (r.module.enabled() || example && r.module.category == Category.VIEW);
            float target = on ? 1f : 0f;
            if (FxWorld.calm()) {
                r.shown = target;
            } else {
                r.shown += Math.signum(target - r.shown) * Math.min(Math.abs(target - r.shown), dt / 0.2f);
            }
            if (r.shown <= 0f) {
                continue;
            }
            r.name = r.module.name();
            r.width = (float) measure.applyAsDouble(r.name);
            // Widest first (insertion: the list is short and mostly in order already).
            int i = count++;
            while (i > 0 && order[i - 1].width < r.width) {
                order[i] = order[i - 1];
                i--;
            }
            order[i] = r;
        }
    }

    /** How far in a row is, in steps of a quarter. */
    private static float step(float shown) {
        return Ramp.steps(shown, 4, true);
    }

    /**
     * Under the kill feed when both are in the top right corner and it has lines (it shows them only for a while), so
     * neither covers the other.
     */
    @Override
    public float y(int screenH, Minecraft mc) {
        float y = super.y(screenH, mc);
        KillFeedHud feed = Modules.KILL_FEED;
        if (preview() || !feed.enabled() || feed.anchorX != 1f || feed.anchorY != 0f || anchorX != 1f || anchorY != 0f
                || feed.isEmpty(mc)) {
            return y;
        }
        return Math.max(y, feed.y(screenH, mc) + feed.height(mc) * feed.scale() + 2f);
    }

    /** A row's text: a light tone, whichever band it's in, so it reads on the slab. */
    private int text(int i, int n) {
        int band = tone(i, n);
        return tones.get().equals("ramp") ? Colors.mix(ramp[Ramp.CORE], band, 0.35f) : band;
    }

    /** The colour of the row at {@code i} of {@code n}: the theme's tones down the list, the accent, or white. */
    private int tone(int i, int n) {
        return switch (tones.get()) {
            case "accent" -> 0xFF000000 | ThemeState.tokens().accent;
            case "white" -> 0xFFFFFFFF;
            default -> {
                Ramp.of(0xFF000000 | ThemeState.tokens().accent, ramp);
                int band = n <= 1 ? 0 : Math.min(Ramp.TONES - 1, i * Ramp.TONES / n);
                yield ramp[band == 0 ? Ramp.CORE : band == 1 ? Ramp.MAIN : band == 2 ? Ramp.SHADE : Ramp.DEEP];
            }
        };
    }

    // ------------------------------------------------------------------ the plain look

    private static final int P_ROW = 10;

    @Override
    protected boolean wholeBacking() {
        return false;
    }

    @Override
    protected int plainWidth(Minecraft mc) {
        update(s -> mc.font.width(s));
        return count == 0 ? 0 : (int) order[0].width + 6;
    }

    @Override
    protected int plainHeight(Minecraft mc) {
        return count * P_ROW;
    }

    @Override
    protected void draw(Canvas g, Minecraft mc) {
        int w = (int) (count == 0 ? 0 : order[0].width + 6);
        if (count > 0) {
            framesDrawn++;
        }
        for (int i = 0; i < count; i++) {
            Row r = order[i];
            int rw = (int) r.width + 6;
            int x = w - Math.round(rw * step(r.shown));
            int y = i * P_ROW;
            if (backing.get()) {
                g.fill(x, y, w, y + P_ROW, backingArgb());
            }
            g.fill(w - 1, y, w, y + P_ROW, tone(i, count));
            g.text(mc.font, r.name, x + 2, y + 1, text(i, count), true);
        }
    }

    // ------------------------------------------------------------------ Sol UI's look

    private static final float ROW = 9f, PAD = 3f, BAR = 1f;

    @Override
    protected int solWidth(Minecraft mc) {
        float s = scale();
        update(t -> HudKit.measure(SolFont.Family.PIXEL_BOLD, HudKit.TEXT, t, s));
        return count == 0 ? 0 : (int) Math.ceil(order[0].width + PAD * 2f + BAR);
    }

    @Override
    protected int solHeight(Minecraft mc) {
        return (int) Math.ceil(count * ROW);
    }

    @Override
    protected void drawSol(Gfx g, Tokens tk, Minecraft mc) {
        if (count == 0) {
            return;
        }
        framesDrawn++;
        SolFont f = HudKit.mono(g);
        float w = order[0].width + PAD * 2f + BAR;
        float base = HudKit.glass;
        for (int i = 0; i < count; i++) {
            Row r = order[i];
            float rw = g.width(f, r.name) + PAD * 2f + BAR;
            // In from the edge in whole steps.
            float x = Math.round(w - rw * step(r.shown));
            float y = i * ROW;
            int c = tone(i, count);
            HudKit.slab(g, tk, x, y, w - x, ROW, base);
            // The band: its tone faintly over the slab and down the edge.
            HudKit.box(g, x, y, w - x, ROW, Colors.withAlpha(c, 0.14f));
            HudKit.box(g, w - BAR, y, BAR, ROW, c);
            g.textMid(f, r.name, x + PAD, y + ROW / 2f, text(i, count));
        }
    }
}
