package io.github.georgwav.cosmetics.client.modules;

import io.github.georgwav.cosmetics.client.Module;
import io.github.georgwav.cosmetics.client.OutlineHook;
import io.github.georgwav.cosmetics.client.Setting;

import java.util.List;

/**
 * The outline around the block you look at, in your colour, opacity and width instead of the game's faint black,
 * and its faces filled see-through if you like. Drawn through Fabric's block outline event where the game's Fabric
 * API has one, and on 26.x by giving the game's own outline this colour and width (see OutlineHook).
 */
public final class BlockOutline extends Module {
    private final Setting.Color color = add(new Setting.Color("color", Setting.Color.THEME).was(0xFFFFFFFF).themed().names(java.util.Map.of(
            "black", 0xFF000000, "white", 0xFFFFFFFF, "red", 0xFFFF4040, "green", 0xFF4CFF4C, "cyan", 0xFF40E8FF,
            "yellow", 0xFFFFE040, "pink", 0xFFFF60D0)).group("colours"));
    private final Setting.Slider opacity = add(new Setting.Slider("opacity", 0.8, 0.1, 1.0, 0.05,
            v -> Math.round(v * 100) + "%").group("colours"));
    /** The lines' width against the game's. */
    private final Setting.Slider width = add(new Setting.Slider("width", 1.0, 0.5, 4.0, 0.25,
            v -> Math.round(v * 100) + "%").group("look"));
    /** Thin lines (the game's, in your colour), or Sol's stepped glow outline in the colour's ramp. */
    private final Setting.Choice look = add(new Setting.Choice("outline_look", "lines", List.of("lines", "glow")).group("look"));
    /** The block's faces filled in its colour, see-through. */
    private final Setting.Toggle fill = add(new Setting.Toggle("fill", false).group("look"));
    /** The fill flat, or as a window onto End stars in the colour's tones. */
    private final Setting.Choice fillLook = add(new Setting.Choice("fill_look", "flat", List.of("flat", "stars")).group("look"));
    private final Setting.Slider fillOpacity = add(new Setting.Slider("fill_opacity", 0.2, 0.05, 0.6, 0.05,
            v -> Math.round(v * 100) + "%").group("look"));
    /** The colour running round the rainbow instead. */
    private final Setting.Toggle chroma = add(new Setting.Toggle("chroma", false).group("chroma"));
    private final Setting.Slider chromaSpeed = add(new Setting.Slider("chroma_speed", 1.0, 0.1, 4.0, 0.1,
            v -> String.format(java.util.Locale.ROOT, "%.1f×", v)).group("chroma"));
    /** Breathes between its opacity and a quarter of it. */
    private final Setting.Toggle pulse = add(new Setting.Toggle("pulse", false).group("chroma"));

    public BlockOutline() {
        super("outline", Category.VIEW, "box");
        fillLook.shownWhen(fill::get);
        io.github.georgwav.cosmetics.sol.fx.FxWorld.LAYERS.add(io.github.georgwav.cosmetics.sol.fx.BlockHighlight.INSTANCE);
    }

    /** Sol's stepped glow outline instead of the lines. */
    public boolean glowLook() {
        return look.get().equals("glow");
    }

    /** The faces filled with stars (the flat fill stands aside). */
    public boolean starsFill() {
        return fill.get() && fillLook.get().equals("stars");
    }

    /** The fill's opacity, 0..255. */
    public int fillAlpha() {
        return Math.round(fillOpacity.get().floatValue() * 255);
    }

    /** On by default: it shows the theme's colour (see Modules.DEFAULTS). */
    @Override
    public boolean onByDefault() {
        return true;
    }

    @Override
    public int onByDefaultSince() {
        return 2;
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

    /** The lines' width against the game's (1 when the module is off). */
    public static float widthScale() {
        BlockOutline b = io.github.georgwav.cosmetics.client.Modules.OUTLINE;
        return b.enabled() ? b.width.get().floatValue() : 1f;
    }

    /** The fill's colour, ARGB: the outline's at the fill's opacity, or 0 for none. */
    public int fillArgb() {
        if (!enabled() || !fill.get() || starsFill()) {
            return 0;
        }
        return Math.round(fillOpacity.get().floatValue() * 255) << 24 | (argb() & 0xFFFFFF);
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
