package io.github.georgwav.cosmetics.model;

/**
 * How a cosmetic takes the colour its player picks (a colour or the rainbow); a player who picks none sees the
 * cosmetic as its pack made it, its own default colour included, as {@link #CLASSIC} has it. Left unset, the
 * style is chosen from the texture (see {@link io.github.georgwav.cosmetics.render.Recolor#paintFor}).
 */
public enum ColorStyle {
    /** Every part takes the colour by its brightness: white becomes the colour, grey a darker shade, black stays. */
    WHOLE,
    /** The coloured parts take the colour, keeping their shading; white, grey and black parts stay as they are. */
    COLORED_PARTS,
    /** The colour multiplies the texture, as the game tints: dark parts and opposite colours turn black. */
    CLASSIC
}
