package io.github.georgwav.cosmetics.client.modules;

import io.github.georgwav.cosmetics.client.Module;
import io.github.georgwav.cosmetics.client.OutlineHook;
import io.github.georgwav.cosmetics.client.Setting;

import java.util.List;

/**
 * The outline around the block you look at, in your colour and opacity instead of the game's faint black. Drawn
 * through Fabric's block outline event where the game's Fabric API has one, and on 26.x by giving the game's own
 * outline this colour (see OutlineHook).
 */
public final class BlockOutline extends Module {
    private static final List<String> COLORS = List.of("black", "white", "red", "green", "cyan", "yellow", "pink");
    private static final int[] RGB = {0x000000, 0xFFFFFF, 0xFF4040, 0x4CFF4C, 0x40E8FF, 0xFFE040, 0xFF60D0};

    private final Setting.Choice color = add(new Setting.Choice("color", "white", COLORS));
    private final Setting.Slider opacity = add(new Setting.Slider("opacity", 0.8, 0.1, 1.0, 0.05,
            v -> Math.round(v * 100) + "%"));

    public BlockOutline() {
        super("outline", Category.VIEW, "box");
    }

    /** Whether this Minecraft version can draw it yet. */
    public static boolean supported() {
        try {
            return OutlineHook.available;
        } catch (LinkageError e) {
            return false;
        }
    }

    @Override
    public boolean available() {
        return supported();
    }

    /** The outline's colour, ARGB. */
    public int argb() {
        int alpha = Math.round(opacity.get().floatValue() * 255);
        return alpha << 24 | RGB[Math.max(0, COLORS.indexOf(color.get()))];
    }
}
