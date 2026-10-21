package io.github.georgwav.cosmetics.render;

import io.github.georgwav.cosmetics.config.CosmeticsConfig;
import io.github.georgwav.cosmetics.model.CosmeticOption;
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

    @Test
    void everyCosmeticFollowsTheThemeUnlessItHasItsOwnColour() {
        java.util.function.IntUnaryOperator themeWas = Tint.theme;
        java.util.function.Supplier<String> everyWas = Tint.every;
        try {
            Tint.theme = which -> which == 1 ? 0x112233 : 0xFF5FA8;
            Tint.every = () -> "accent";
            CosmeticsConfig.CosmeticOptions options = new CosmeticsConfig.CosmeticOptions();
            assertEquals(0xFFFF5FA8, Tint.argb(options, CosmeticOption.ALL, 0x2266AA, 0), "no colour of its own: the accent");
            options.themeColor = "glow";
            assertEquals(0xFF112233, Tint.argb(options, CosmeticOption.ALL, 0x2266AA, 0), "its own follow setting comes first");
            options.themeColor = "own";
            assertEquals(0xFF2266AA, Tint.argb(options, CosmeticOption.ALL, 0x2266AA, 0), "Off keeps its own colour");
            options.themeColor = null;
            options.color = 0x336699;
            assertEquals(0xFF336699, Tint.argb(options, CosmeticOption.ALL, 0x2266AA, 0), "a picked colour stays");
            options.color = null;
            Tint.every = () -> null;
            assertEquals(0xFF2266AA, Tint.argb(options, CosmeticOption.ALL, 0x2266AA, 0), "switched off: its own colour");
        } finally {
            Tint.theme = themeWas;
            Tint.every = everyWas;
        }
    }

    @Test
    void defaultColourUntilThePlayerPicksOne() {
        CosmeticsConfig.CosmeticOptions options = new CosmeticsConfig.CosmeticOptions();
        assertEquals(0xFF2266AA, Tint.argb(options, CosmeticOption.ALL, 0x2266AA, 0));
        options.color = 0xFFFFFF;
        assertEquals(0xFFFFFFFF, Tint.argb(options, CosmeticOption.ALL, 0x2266AA, 0), "an explicit white wins");
    }

    @Test
    void optionsTheCosmeticDoesNotAllowAreIgnored() {
        CosmeticsConfig.CosmeticOptions options = new CosmeticsConfig.CosmeticOptions();
        options.color = 0x123456;
        options.rainbow = true;
        options.saturation = 0f;
        int noColour = CosmeticOption.ALL & ~CosmeticOption.TINT.bit() & ~CosmeticOption.RAINBOW.bit()
                & ~CosmeticOption.SATURATION.bit();
        assertEquals(0xFF2266AA, Tint.argb(options, noColour, 0x2266AA, 3));
        int tintOnly = noColour | CosmeticOption.TINT.bit();
        assertEquals(0xFF123456, Tint.argb(options, tintOnly, 0x2266AA, 3), "rainbow and saturation are off");
    }

    @Test
    void saturationMakesTheTintPaler() {
        CosmeticsConfig.CosmeticOptions options = new CosmeticsConfig.CosmeticOptions();
        options.color = 0xFF0000;
        options.saturation = 0.5f;
        assertEquals(0xFFFF8080, Tint.argb(options, 0));
        options.saturation = 0f;
        assertEquals(0xFFFFFFFF, Tint.argb(options, 0), "no saturation is no tint");
        options.color = 0x804020;
        options.saturation = 1f;
        assertEquals(0xFF804020, Tint.argb(options, 0), "full saturation keeps the colour exactly");
        assertEquals(0x406080, Tint.desaturate(0x406080, 1f));
        assertEquals(0x808080, Tint.desaturate(0x808080, 0.3f), "greys stay grey");
        options.rainbow = true;
        options.saturation = 0f;
        assertEquals(0xFFFFFFFF, Tint.argb(options, 4.2), "a rainbow without saturation is white");
    }

    @Test
    void hsvRoundTripsEveryPickerColour() {
        for (int rgb : new int[]{0xFF0000, 0x00FF00, 0x0000FF, 0x123456, 0xFEDCBA, 0x808080, 0x000000, 0xFFFFFF}) {
            float[] hsv = Tint.rgbToHsv(rgb);
            int back = Tint.hsvToRgb(hsv[0], hsv[1], hsv[2]);
            for (int shift = 0; shift <= 16; shift += 8) {
                int a = rgb >> shift & 0xFF;
                int b = back >> shift & 0xFF;
                org.junit.jupiter.api.Assertions.assertTrue(Math.abs(a - b) <= 1,
                        String.format("#%06X came back as #%06X", rgb, back));
            }
        }
    }
}
