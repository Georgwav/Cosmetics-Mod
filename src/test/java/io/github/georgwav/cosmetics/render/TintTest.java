package io.github.georgwav.cosmetics.render;

import io.github.georgwav.cosmetics.config.CosmeticsConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class TintTest {
    @Test
    void plainColourIsOpaque() {
        CosmeticsConfig.CosmeticOptions options = new CosmeticsConfig.CosmeticOptions();
        assertEquals(0xFFFFFFFF, Tint.argb(options, 123));
        options.color = 0x336699;
        assertEquals(0xFF336699, Tint.argb(options, 123));
    }

    @Test
    void rainbowGoesAroundTheWheel() {
        assertEquals(0xFF0000, Tint.hsvToRgb(0f, 1f, 1f));
        assertEquals(0x00FF00, Tint.hsvToRgb(1f / 3f, 1f, 1f));
        assertEquals(0x0000FF, Tint.hsvToRgb(2f / 3f, 1f, 1f));

        CosmeticsConfig.CosmeticOptions options = new CosmeticsConfig.CosmeticOptions();
        options.rainbow = true;
        int start = Tint.argb(options, 0);
        assertEquals(0xFF, start >>> 24);
        assertNotEquals(start, Tint.argb(options, 3.3));
        assertEquals(start, Tint.argb(options, 10)); // one full cycle per 10 s at speed 1
    }
}
