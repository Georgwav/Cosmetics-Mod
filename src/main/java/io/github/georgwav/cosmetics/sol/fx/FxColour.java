package io.github.georgwav.cosmetics.sol.fx;

import io.github.georgwav.cosmetics.sol.theme.ThemeState;
import io.github.georgwav.cosmetics.sol.theme.Tokens;
import io.github.georgwav.cosmetics.sol.util.Colors;

/**
 * An effect's colours from its Colour setting (C.2): the preset's accent (Theme, the default, so it changes with the
 * preset), your own colour, a rainbow, or two colours; and its glow in the preset's glow colour. Fills an effect's
 * ramps as it starts.
 */
public final class FxColour {
    /** Twelve rainbow ramps, made once. */
    private static final int[][] RAINBOW = new int[12][];

    static {
        for (int i = 0; i < 12; i++) {
            RAINBOW[i] = Ramp.of(Colors.fromHsv(i / 12f, 0.75f, 1f));
        }
    }

    private FxColour() {
    }

    /** The ramp of a rainbow hue (0..1, in twelve steps). */
    static int[] rainbowRamp(float hue) {
        int i = Math.floorMod(Math.round(hue * 12f), 12);
        return RAINBOW[i];
    }

    /** The preset's accent, opaque. */
    public static int accent() {
        Tokens tk = ThemeState.tokens();
        return 0xFF000000 | tk.accent;
    }

    /** The preset's glow colour, opaque. */
    public static int glow() {
        Tokens tk = ThemeState.tokens();
        return 0xFF000000 | tk.glow;
    }

    /**
     * Sets an effect's colours: {@code mode} is "theme", "custom", "rainbow" or "two"; {@code custom} and {@code second}
     * the picked colours. {@code fallback} is the colour a style has of its own when it isn't the theme's (0: none).
     */
    public static void apply(Fx fx, String mode, int custom, int second) {
        int main = switch (mode) {
            case "custom", "two" -> custom;
            default -> accent();
        };
        Ramp.of(main, fx.ramp);
        Ramp.of(second, fx.ramp2);
        fx.two = mode.equals("two");
        fx.rainbow = mode.equals("rainbow");
        fx.glowColour = fx.rainbow ? 0xFFFFFFFF : mode.equals("theme") ? glow() : Colors.mixRgb(main, 0xFFFFFFFF, 0.3f);
    }

    /** Sets an effect's colours to one fixed colour (a style's own, like Burst's red). */
    public static void fixed(Fx fx, int colour) {
        Ramp.of(colour, fx.ramp);
        Ramp.of(colour, fx.ramp2);
        fx.two = fx.rainbow = false;
        fx.glowColour = Colors.mixRgb(colour, 0xFFFFFFFF, 0.3f);
    }
}
