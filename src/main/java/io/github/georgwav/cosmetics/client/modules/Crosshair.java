package io.github.georgwav.cosmetics.client.modules;

import io.github.georgwav.cosmetics.client.Module;
import io.github.georgwav.cosmetics.client.Setting;
import io.github.georgwav.cosmetics.ui.Canvas;
import net.minecraft.client.Minecraft;

import java.util.List;

/**
 * Your own crosshair in place of the game's: a cross, a T, a circle or a dot, with its arm length, gap, thickness,
 * colour and opacity, a centre dot and an outline; another colour on something living, arms that open while you
 * move, and a bar for the attack cooldown (the game's attack indicator isn't drawn while it is on). It is drawn in
 * screen pixels, so it is as sharp and as large at any GUI scale.
 */
public final class Crosshair extends Module {
    private final Setting.Choice style = add(new Setting.Choice("style", "cross", List.of("cross", "t", "circle", "dot")).group("look"));
    private final Setting.Slider length = add(new Setting.Slider("length", 7, 1, 24, 1, Crosshair::px).group("look"));
    private final Setting.Slider gap = add(new Setting.Slider("gap", 3, 0, 16, 1, Crosshair::px).group("look"));
    private final Setting.Slider thickness = add(new Setting.Slider("thickness", 2, 1, 6, 1, Crosshair::px).group("look"));
    private final Setting.Toggle dot = add(new Setting.Toggle("dot", false).group("look"));
    private final Setting.Color color = add(new Setting.Color("color", 0xFFFFFFFF).themed().group("colours"));
    private final Setting.Slider opacity = add(new Setting.Slider("opacity", 1.0, 0.2, 1.0, 0.05,
            v -> Math.round(v * 100) + "%").group("colours"));
    private final Setting.Toggle outline = add(new Setting.Toggle("outline", true).group("colours"));
    private final Setting.Color outlineColor = add(new Setting.Color("outline_color", 0xFF000000).themed().group("colours"));
    /** Another colour while it's on something living. */
    private final Setting.Toggle targetColour = add(new Setting.Toggle("target_colour", false).group("colours"));
    private final Setting.Color target = add(new Setting.Color("target_color", 0xFFFF4D4D).themed().group("colours"));
    private final Setting.Toggle thirdPerson = add(new Setting.Toggle("third_person", false).group("behaviour"));
    /** The arms open up while you walk and more while you sprint, and close again when you stop. */
    private final Setting.Toggle spread = add(new Setting.Toggle("spread", false).group("behaviour"));
    /** A thin bar under it while your attack recharges (the game's own indicator isn't drawn while it is on). */
    private final Setting.Toggle cooldown = add(new Setting.Toggle("cooldown", true).group("behaviour"));
    private float opened;
    private long lastFrame;
    /** Whether it's being drawn on the HUD now (target colour and cooldown), not in the preview. */
    private boolean live;

    public Crosshair() {
        super("crosshair", Category.VIEW, "target");
    }

    private static String px(double v) {
        return Math.round(v) + " px";
    }

    @Override
    public float previewHeight() {
        return 120f;
    }

    /** Enlarged, as large as fits, so each of its pixels shows. */
    @Override
    public void drawPreview(Canvas g, int x0, int y0, int x1, int y1) {
        int extent = (int) Math.round(gap.get() + length.get() + thickness.get()) + 2;
        int pixel = Math.max(1, Math.min(4, ((y1 - y0) / 2 - 4) / extent));
        draw(g, (x0 + x1) / 2, (y0 + y1) / 2, pixel);
    }

    /** Draws it in the middle of the HUD (the game's GUI transform), as the game's crosshair would be. */
    public void drawOnHud(Canvas g, Minecraft mc) {
        if (mc.player == null || !mc.options.getCameraType().isFirstPerson() && !thirdPerson.get()) {
            return;
        }
        long now = System.nanoTime();
        float dt = lastFrame == 0L ? 0f : Math.min(0.1f, (now - lastFrame) / 1e9f);
        lastFrame = now;
        float want = 0f;
        if (spread.get()) {
            var v = mc.player.getDeltaMovement();
            double speed = Math.sqrt(v.x * v.x + v.z * v.z);
            want = mc.player.isSprinting() ? 3f : speed > 0.03 ? 1.5f : 0f;
            if (!mc.player.onGround()) {
                want += 1f;
            }
        }
        opened += (want - opened) * Math.min(1f, dt * 12f);
        float gs = (float) mc.getWindow().getGuiScale();
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        g.push();
        g.scale(1f / gs, 1f / gs);
        live = true;
        try {
            draw(g, Math.round(w * gs) / 2, Math.round(h * gs) / 2, 1);
        } finally {
            live = false;
        }
        g.pop();
    }

    /**
     * Draws it centred on (cx, cy) in the current transform's pixels, each of its pixels {@code pixel} of them (the
     * Mods page shows it enlarged).
     */
    public void draw(Canvas g, int cx, int cy, int pixel) {
        Minecraft mc = Minecraft.getInstance();
        int rgb = color.argb();
        if (live && targetColour.get() && mc.crosshairPickEntity instanceof net.minecraft.world.entity.LivingEntity e && e.isAlive()) {
            rgb = target.argb();
        }
        int alpha = Math.round(opacity.get().floatValue() * 255);
        int argb = alpha << 24 | (rgb & 0xFFFFFF);
        int shadow = Math.round(opacity.get().floatValue() * 200) << 24 | (outlineColor.argb() & 0xFFFFFF);
        int open = live ? Math.round(opened) : 0;
        if (outline.get()) {
            shapes(g, cx, cy, pixel, 1, open, shadow);
        }
        shapes(g, cx, cy, pixel, 0, open, argb);
        if (live && cooldown.get() && mc.player != null) {
            float k = mc.player.getAttackStrengthScale(0f);
            if (k < 1f) {
                // Under the crosshair: a track and the part recharged so far.
                int t = (int) Math.round(thickness.get());
                int below = (int) Math.round(gap.get() + length.get()) + t / 2 + open + 4;
                int half = 8;
                int fill = Math.round(half * 2 * k);
                if (outline.get()) {
                    rect(g, cx, cy, pixel, 1, -half, below, half, below + 2, shadow);
                }
                rect(g, cx, cy, pixel, 0, -half, below, half, below + 2, (alpha / 3) << 24 | (rgb & 0xFFFFFF));
                rect(g, cx, cy, pixel, 0, -half, below, -half + fill, below + 2, argb);
            }
        }
    }

    /** The crosshair's pieces, each grown by {@code grow} pixels on every side (the outline). */
    private void shapes(Canvas g, int cx, int cy, int p, int grow, int open, int argb) {
        int t = (int) Math.round(thickness.get());
        int len = (int) Math.round(length.get());
        int gp = (int) Math.round(gap.get()) + open;
        // The middle bar's span: t pixels, centred (odd thicknesses sit exactly on the middle pixel).
        int a0 = -t / 2;
        int a1 = a0 + t;
        String s = style.get();
        if (s.equals("cross") || s.equals("t")) {
            rect(g, cx, cy, p, grow, a0 - gp - len, a0, a0 - gp, a1, argb);
            rect(g, cx, cy, p, grow, a1 + gp, a0, a1 + gp + len, a1, argb);
            if (s.equals("cross")) {
                rect(g, cx, cy, p, grow, a0, a0 - gp - len, a1, a0 - gp, argb);
            }
            rect(g, cx, cy, p, grow, a0, a1 + gp, a1, a1 + gp + len, argb);
        } else if (s.equals("circle")) {
            ring(g, cx, cy, p, grow, gp + len / 2f + t, t, argb);
        }
        if (dot.get() || s.equals("dot")) {
            rect(g, cx, cy, p, grow, a0, a0, a1, a1, argb);
        }
    }

    /** A rectangle from (x0, y0) to (x1, y1) in crosshair pixels from the middle. */
    private static void rect(Canvas g, int cx, int cy, int p, int grow, int x0, int y0, int x1, int y1, int argb) {
        g.fill(cx + (x0 - grow) * p, cy + (y0 - grow) * p, cx + (x1 + grow) * p, cy + (y1 + grow) * p, argb);
    }

    /** A ring of outer radius {@code r} and {@code t} thick, in whole pixels row by row (crisp, like the rest). */
    private static void ring(Canvas g, int cx, int cy, int p, int grow, float r, int t, int argb) {
        float outer = r + grow;
        float inner = Math.max(0f, r - t - grow);
        int rows = (int) Math.ceil(outer);
        for (int dy = -rows; dy < rows; dy++) {
            float y = dy + 0.5f;
            if (Math.abs(y) >= outer) {
                continue;
            }
            int xo = Math.round((float) Math.sqrt(outer * outer - y * y));
            int xi = Math.abs(y) < inner ? Math.round((float) Math.sqrt(inner * inner - y * y)) : 0;
            int top = cy + dy * p;
            if (xi <= 0) {
                g.fill(cx - xo * p, top, cx + xo * p, top + p, argb);
            } else if (xo > xi) {
                g.fill(cx - xo * p, top, cx - xi * p, top + p, argb);
                g.fill(cx + xi * p, top, cx + xo * p, top + p, argb);
            }
        }
    }
}
