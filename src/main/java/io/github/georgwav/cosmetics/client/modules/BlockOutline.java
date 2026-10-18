package io.github.georgwav.cosmetics.client.modules;

import io.github.georgwav.cosmetics.client.Module;
import io.github.georgwav.cosmetics.client.OutlineHook;
import io.github.georgwav.cosmetics.client.Setting;

/**
 * The outline around the block you look at, in your colour and opacity instead of the game's faint black. Drawn
 * through Fabric's block outline event where the game's Fabric API has one, and on 26.x by giving the game's own
 * outline this colour (see OutlineHook).
 */
public final class BlockOutline extends Module {
    private final Setting.Color color = add(new Setting.Color("color", 0xFFFFFFFF).themed().names(java.util.Map.of(
            "black", 0xFF000000, "white", 0xFFFFFFFF, "red", 0xFFFF4040, "green", 0xFF4CFF4C, "cyan", 0xFF40E8FF,
            "yellow", 0xFFFFE040, "pink", 0xFFFF60D0)).group("colours"));
    private final Setting.Slider opacity = add(new Setting.Slider("opacity", 0.8, 0.1, 1.0, 0.05,
            v -> Math.round(v * 100) + "%").group("colours"));
    /** The colour running round the rainbow instead. */
    private final Setting.Toggle chroma = add(new Setting.Toggle("chroma", false).group("chroma"));
    private final Setting.Slider chromaSpeed = add(new Setting.Slider("chroma_speed", 1.0, 0.1, 4.0, 0.1,
            v -> String.format(java.util.Locale.ROOT, "%.1f×", v)).group("chroma"));
    /** Breathes between its opacity and a quarter of it. */
    private final Setting.Toggle pulse = add(new Setting.Toggle("pulse", false).group("chroma"));

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
        float a = opacity.get().floatValue();
        double t = System.nanoTime() / 1e9;
        if (pulse.get()) {
            a *= 0.625f + 0.375f * (float) Math.sin(t * 3.0);
        }
        int rgb = chroma.get() ? java.awt.Color.HSBtoRGB((float) ((t * chromaSpeed.get() * 0.2) % 1.0), 0.75f, 1f) : color.argb();
        return Math.round(a * 255) << 24 | (rgb & 0xFFFFFF);
    }
}
