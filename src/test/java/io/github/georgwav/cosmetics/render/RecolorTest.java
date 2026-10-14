package io.github.georgwav.cosmetics.render;

import io.github.georgwav.cosmetics.config.CosmeticsConfig;
import io.github.georgwav.cosmetics.model.CosmeticOption;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RecolorTest {
    @Test
    void untouchedSettingsLeaveTheTextureAlone() {
        CosmeticsConfig.CosmeticOptions options = new CosmeticsConfig.CosmeticOptions();
        assertNull(Recolor.of(options, CosmeticOption.ALL));
        options.hue = 0.3f; // rounds to no turn
        assertNull(Recolor.of(options, CosmeticOption.ALL));
        options.hue = 120f;
        assertNull(Recolor.of(options, 0), "a cosmetic that doesn't allow colour changes keeps its texture");
        assertEquals(new Recolor(120, 1f, 1f), Recolor.of(options, CosmeticOption.ALL));
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
}
