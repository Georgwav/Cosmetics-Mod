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
 * the inventory, on the ground, armour), because the pack's own glint textures are recoloured. In inventories and the
 * hotbar an enchanted item can have the glint, an outline round its shape in the glint's colour, or both.
 */
public final class GlintModule extends Module {
    private static final java.util.Map<String, Integer> NAMES = java.util.Map.of("purple", 0xFF8040FF, "red", 0xFFFF4040,
            "orange", 0xFFFF8A30, "gold", 0xFFFFD34A, "green", 0xFF4CFF6A, "cyan", 0xFF40E8FF, "blue", 0xFF4A7DFF,
            "pink", 0xFFFF60D0, "white", 0xFFFFFFFF);
    private final Setting.Choice mode = add(new Setting.Choice("mode", "theme", List.of("solid", "two", "rainbow", "theme")).group("colours"));
    private final Setting.Color color = add(new Setting.Color("color", 0xFF40E8FF).names(NAMES).group("colours"));
    private final Setting.Color color2 = add(new Setting.Color("color2", 0xFFFF60D0).names(NAMES).group("colours"));
    private final Setting.Slider strength = add(new Setting.Slider("strength", 100, 30, 200, 10, v -> Math.round(v) + "%").group("colours"));
    /** Enchanted items in inventories and the hotbar: the glint, an outline round the item in its colour, or both. */
    private final Setting.Choice gui = add(new Setting.Choice("inventory", "glint", List.of("glint", "outline", "both")).group("inventory"));
    /** Whether the game is working out (or drawing) an item's inventory picture right now. */
    private static boolean drawingGui;
    private final Setting.Slider speed = add(new Setting.Slider("speed", 1.0, 0.1, 3.0, 0.1,
            v -> String.format(Locale.ROOT, "%.1f×", v)).group("colours"));
    private String paintedKey = "";

    public GlintModule() {
        super("glint", Category.VIEW, "sparkle");
    }

    private static int rgb(Setting.Color c) {
        return c.argb();
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

    /** Set while an inventory picture's look is worked out (the mixins), so "Outline only" can leave its glint off. */
    public static void drawing(boolean gui) {
        drawingGui = gui;
    }

    /** Whether an item worked out now goes without its glint: an inventory picture, with "Outline only". */
    public static boolean hidesFoil() {
        GlintModule g = Modules.GLINT;
        return drawingGui && g != null && g.enabled() && g.gui.get().equals("outline");
    }

    /** After an item's decorations in the GUI: an enchanted item's outline round its shape, in the glint's colour. */
    public static void decorate(io.github.georgwav.cosmetics.ui.Canvas c, net.minecraft.world.item.ItemStack stack, int x, int y) {
        GlintModule g = Modules.GLINT;
        if (!g.enabled() || stack.isEmpty() || g.gui.get().equals("glint") || !stack.hasFoil()) {
            return;
        }
        int col = 0xFF000000 | g.outlineColour();
        if (io.github.georgwav.cosmetics.sol.glint.GlintOutline.draw(c, stack, x, y, col)) {
            return;
        }
        // An item without a flat picture of its own (a block, a shield): a one-pixel frame round the slot.
        int argb = 0xC0000000 | (col & 0xFFFFFF);
        c.fill(x + 1, y - 1, x + 15, y, argb);
        c.fill(x + 1, y + 16, x + 15, y + 17, argb);
        c.fill(x - 1, y + 1, x, y + 15, argb);
        c.fill(x + 16, y + 1, x + 17, y + 15, argb);
    }

    /** After a resource reload the game has its own glint back: paint again on the next tick. */
    public static void reloaded() {
        GlintColors.reload();
        io.github.georgwav.cosmetics.sol.glint.GlintOutline.clear();
        Modules.GLINT.paintedKey = "";
    }
}
