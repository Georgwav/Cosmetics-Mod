package io.github.georgwav.cosmetics.client.modules;

import io.github.georgwav.cosmetics.client.Module;
import io.github.georgwav.cosmetics.client.Modules;
import io.github.georgwav.cosmetics.client.Setting;
import io.github.georgwav.cosmetics.sol.glint.GlintColors;
import io.github.georgwav.cosmetics.sol.glint.GlintRecolor;
import io.github.georgwav.cosmetics.sol.theme.ThemeState;
import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.Locale;

/**
 * Glint: the enchantment shimmer in a colour of yours: one colour, two drifting into each other, a rainbow sweeping
 * across, or following the theme's accent; brighter or softer. Everywhere the game draws glint (items in hand, in
 * the inventory, on the ground, armour), because the pack's own glint textures are recoloured.
 */
public final class GlintModule extends Module {
    static final List<String> COLORS = List.of("purple", "red", "orange", "gold", "green", "cyan", "blue", "pink", "white");
    private static final int[] RGB = {0xFF8040FF, 0xFFFF4040, 0xFFFF8A30, 0xFFFFD34A, 0xFF4CFF6A, 0xFF40E8FF, 0xFF4A7DFF,
            0xFFFF60D0, 0xFFFFFFFF};
    private final Setting.Choice mode = add(new Setting.Choice("mode", "theme", List.of("solid", "two", "rainbow", "theme")));
    private final Setting.Choice color = add(new Setting.Choice("color", "cyan", COLORS));
    private final Setting.Choice color2 = add(new Setting.Choice("color2", "pink", COLORS));
    private final Setting.Slider strength = add(new Setting.Slider("strength", 100, 30, 200, 10, v -> Math.round(v) + "%"));
    /** Enchanted items in inventories and the hotbar: an outline in the glint's colour, or a soft glow round the slot. */
    private final Setting.Choice gui = add(new Setting.Choice("gui", "outline", List.of("none", "outline", "glow")));
    private final Setting.Slider speed = add(new Setting.Slider("speed", 1.0, 0.1, 3.0, 0.1,
            v -> String.format(Locale.ROOT, "%.1f×", v)));
    private String paintedKey = "";

    public GlintModule() {
        super("glint", Category.VIEW, "sparkle");
    }

    private static int rgb(Setting.Choice c) {
        return RGB[Math.max(0, COLORS.indexOf(c.get()))];
    }

    @Override
    protected void onEnable(Minecraft mc) {
        paintedKey = "";
    }

    @Override
    protected void onDisable(Minecraft mc) {
        GlintColors.restore();
        paintedKey = "";
    }

    @Override
    protected void tick(Minecraft mc) {
        String m = mode.get();
        GlintRecolor.Mode rm = switch (m) {
            case "two" -> GlintRecolor.Mode.TWO;
            case "rainbow" -> GlintRecolor.Mode.RAINBOW;
            default -> GlintRecolor.Mode.SOLID;
        };
        int a = m.equals("theme") ? ThemeState.tokens().accent : rgb(color);
        int b = rgb(color2);
        boolean animated = rm != GlintRecolor.Mode.SOLID;
        float phase = animated ? (float) ((System.nanoTime() / 1e9 * speed.get() * 0.25) % 1.0) : 0f;
        String key = rm + ":" + Integer.toHexString(a) + ":" + Integer.toHexString(b) + ":" + strength.get();
        // Repainted when something changed, and at 20 Hz while it moves.
        if (!animated && key.equals(paintedKey)) {
            return;
        }
        paintedKey = key;
        GlintColors.paint(rm, a, b, (float) (strength.get() / 100.0), phase);
    }

    /** The glint's colour now (the theme's accent, the first colour, or the rainbow's) for the GUI outline. */
    private int outlineColour() {
        return switch (mode.get()) {
            case "theme" -> ThemeState.tokens().accent;
            case "rainbow" -> java.awt.Color.HSBtoRGB((float) ((System.nanoTime() / 1e9 * speed.get() * 0.25) % 1.0), 0.65f, 1f);
            case "two" -> ((System.nanoTime() / 1_000_000_000L) % 2L == 0L) ? 0xFF000000 | rgb(color) : 0xFF000000 | rgb(color2);
            default -> 0xFF000000 | rgb(color);
        };
    }

    /** After an item's decorations in the GUI: an enchanted item's outline (or glow), in the glint's colour. */
    public static void decorate(io.github.georgwav.cosmetics.ui.Canvas c, net.minecraft.world.item.ItemStack stack, int x, int y) {
        GlintModule g = Modules.GLINT;
        if (!g.enabled() || stack.isEmpty() || !stack.hasFoil() || g.gui.get().equals("none")) {
            return;
        }
        int col = g.outlineColour();
        if (g.gui.get().equals("glow")) {
            for (int i = 1; i <= 3; i++) {
                int a = (int) (0x30 / (float) i);
                int argb = (a << 24) | (col & 0xFFFFFF);
                c.fill(x - i, y - i, x + 16 + i, y - i + 1, argb);
                c.fill(x - i, y + 15 + i, x + 16 + i, y + 16 + i, argb);
                c.fill(x - i, y - i + 1, x - i + 1, y + 15 + i, argb);
                c.fill(x + 15 + i, y - i + 1, x + 16 + i, y + 15 + i, argb);
            }
            return;
        }
        // A one-pixel frame with soft corners just inside the slot.
        int argb = 0xC0000000 | (col & 0xFFFFFF);
        c.fill(x + 1, y - 1, x + 15, y, argb);
        c.fill(x + 1, y + 16, x + 15, y + 17, argb);
        c.fill(x - 1, y + 1, x, y + 15, argb);
        c.fill(x + 16, y + 1, x + 17, y + 15, argb);
    }

    /** After a resource reload the game has its own glint back: paint again on the next tick. */
    public static void reloaded() {
        GlintColors.reload();
        Modules.GLINT.paintedKey = "";
    }
}
