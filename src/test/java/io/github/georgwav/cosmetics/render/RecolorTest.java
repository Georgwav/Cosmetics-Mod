package io.github.georgwav.cosmetics.render;

import io.github.georgwav.cosmetics.config.CosmeticsConfig;
import io.github.georgwav.cosmetics.model.ColorStyle;
import io.github.georgwav.cosmetics.model.CosmeticOption;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecolorTest {
    private static final int WHITE = 0xFFFFFF;

    @Test
    void untouchedSettingsLeaveTheTextureAlone() {
        CosmeticsConfig.CosmeticOptions options = new CosmeticsConfig.CosmeticOptions();
        assertNull(Recolor.of(options, CosmeticOption.ALL, WHITE));
        options.hue = 0.3f; // rounds to no turn
        assertNull(Recolor.of(options, CosmeticOption.ALL, WHITE));
        options.hue = 120f;
        assertNull(Recolor.of(options, 0, WHITE), "a cosmetic that doesn't allow colour changes keeps its texture");
        assertEquals(new Recolor(120, 1f, 1f), Recolor.of(options, CosmeticOption.ALL, WHITE));
    }

    @Test
    void saturationPalesTheTintOfATintedCosmeticNotItsTextureToo() {
        CosmeticsConfig.CosmeticOptions options = new CosmeticsConfig.CosmeticOptions();
        options.saturation = 0.5f;
        assertEquals(new Recolor(0, 0.5f, 1f), Recolor.of(options, CosmeticOption.ALL, WHITE));
        // A colour picked, the cosmetic's own colour, or the rainbow: the tint takes it (Tint).
        options.color = 0x3366FF;
        assertNull(Recolor.of(options, CosmeticOption.ALL, WHITE));
        options.hue = 90f;
        assertEquals(new Recolor(90, 1f, 1f), Recolor.of(options, CosmeticOption.ALL, WHITE));
        options.color = null;
        assertEquals(new Recolor(90, 1f, 1f), Recolor.of(options, CosmeticOption.ALL, 0xFF0000));
        options.rainbow = true;
        assertEquals(new Recolor(90, 1f, 1f), Recolor.of(options, CosmeticOption.ALL, WHITE));
        // A colour the cosmetic doesn't take leaves the saturation to its texture.
        options.rainbow = false;
        options.color = 0x3366FF;
        int noTint = CosmeticOption.ALL & ~CosmeticOption.TINT.bit() & ~CosmeticOption.RAINBOW.bit();
        assertEquals(new Recolor(90, 0.5f, 1f), Recolor.of(options, noTint, WHITE));
    }

    @Test
    void redTurnsBlueGreyOrDarkerAndKeepsItsAlpha() {
        int red = 0x80FF0000;
        assertEquals(0x800000FF, new Recolor(-120, 1f, 1f).apply(red));
        assertEquals(0x8000FF00, new Recolor(120, 1f, 1f).apply(red));
        // No saturation: black and white, as bright as it was.
        assertEquals(0x80FFFFFF, new Recolor(0, 0f, 1f).apply(red));
        assertEquals(0x80800000, new Recolor(0, 1f, 0.5f).apply(red));
        // Doubled, a dark red becomes full red; past full it washes out towards white.
        int bright = new Recolor(0, 1f, 2f).apply(0xFF800000);
        assertEquals(0xFF, bright >> 16 & 0xFF);
        assertEquals(0, bright & 0xFF, 1);
        assertEquals(0x80FF8080, new Recolor(0, 1f, 2f).apply(red));
    }

    @Test
    void wholeCosmeticPaintsGreyByBrightnessSoTheTintColoursWhiteAndKeepsBlack() {
        Recolor grey = Recolor.painted(null, Recolor.Paint.GREY, 0);
        assertEquals(new Recolor(0, 1f, 1f, Recolor.Paint.GREY, 0), grey);
        assertEquals(0xFFFFFFFF, grey.apply(0xFFFFFFFF), "white: the tint colour itself");
        assertEquals(0x80808080, grey.apply(0x80808080), "grey: a darker shade, alpha kept");
        assertEquals(0xFF000000, grey.apply(0xFF000000), "black stays black");
        // A coloured part keeps its brightness, so even the colour opposite the tint takes it, not black.
        assertEquals(0xFFFFFFFF, grey.apply(0xFF00FFFF));
        assertEquals(0xFF808080, grey.apply(0xFF008080));
    }

    @Test
    void colouredPartsTakeTheColourWithTheirShadingAndWhiteGreyAndBlackStay() {
        Recolor red = Recolor.painted(null, Recolor.Paint.COLORED, 0xFFFF0000);
        assertEquals(0xFF0000, red.tint(), "the colour is RGB");
        assertEquals(0xFFFFFFFF, red.apply(0xFFFFFFFF));
        assertEquals(0xFF808080, red.apply(0xFF808080));
        assertEquals(0xFF000000, red.apply(0xFF000000));
        // Blue wings turn red, shade for shade; cyan (red's opposite) turns red too, not black.
        assertEquals(0xFFFF0000, red.apply(0xFF0000FF));
        assertEquals(0x80800000, red.apply(0x80000080));
        assertEquals(0xFFFF8080, red.apply(0xFF8080FF));
        assertEquals(0xFFFF0000, red.apply(0xFF00FFFF));
        // A dark colour darkens the coloured parts only.
        Recolor darkRed = Recolor.painted(null, Recolor.Paint.COLORED, 0x800000);
        assertEquals(0xFF800000, darkRed.apply(0xFF0000FF));
        assertEquals(0xFFFFFFFF, darkRed.apply(0xFFFFFFFF));
        // Texture changes come first: turned hue, then painted (the hue is the colour's anyway).
        Recolor base = new Recolor(90, 1f, 0.5f);
        assertEquals(0xFF800000, Recolor.painted(base, Recolor.Paint.COLORED, 0xFF0000).apply(0xFF0000FF));
        assertSame(base, Recolor.painted(base, Recolor.Paint.NONE, 0xFF0000));
        assertNull(Recolor.painted(null, Recolor.Paint.NONE, 0xFF0000));
    }

    @Test
    void theStyleIsChosenFromTheTextureUnlessThePlayerPickedOne() {
        int small = 64 * 64;
        // No colour picked: the cosmetic as its pack made it.
        assertEquals(Recolor.Paint.NONE, Recolor.paintFor(ColorStyle.WHOLE, false, false, 0f, small));
        // Automatic: white cosmetics all over, coloured ones in their coloured parts.
        assertEquals(Recolor.Paint.GREY, Recolor.paintFor(null, true, false, 0.05f, small));
        assertEquals(Recolor.Paint.COLORED, Recolor.paintFor(null, true, false, 0.6f, small));
        assertEquals(Recolor.Paint.GREY, Recolor.paintFor(ColorStyle.WHOLE, true, false, 0.6f, small));
        assertEquals(Recolor.Paint.COLORED, Recolor.paintFor(ColorStyle.COLORED_PARTS, true, false, 0f, small));
        assertEquals(Recolor.Paint.NONE, Recolor.paintFor(ColorStyle.CLASSIC, true, false, 0.6f, small));
        // The rainbow repaints a texture as it turns: a big one takes it all over instead.
        assertEquals(Recolor.Paint.COLORED, Recolor.paintFor(ColorStyle.COLORED_PARTS, true, true, 0.6f, small));
        assertEquals(Recolor.Paint.GREY, Recolor.paintFor(ColorStyle.COLORED_PARTS, true, true, 0.6f, 1024 * 1024));
    }

    @Test
    void colouredPixelsAndRainbowSteps() {
        assertTrue(Recolor.coloured(0xFFFF0000));
        assertTrue(!Recolor.coloured(0xFFE0E0E0), "near white");
        assertTrue(!Recolor.coloured(0xFF101020), "near black");
        assertTrue(!Recolor.coloured(0x10FF0000), "see-through");
        // The rainbow's hue in steps of ten degrees.
        assertEquals(Tint.hsvToRgb(10f / 360f, 0.65f, 1f), Recolor.rainbowStep(Tint.hsvToRgb(12f / 360f, 0.65f, 1f)));
        assertEquals(Tint.hsvToRgb(0f, 0.65f, 1f), Recolor.rainbowStep(Tint.hsvToRgb(358f / 360f, 0.65f, 1f)));
    }
}
