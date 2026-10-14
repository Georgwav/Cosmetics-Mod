package io.github.georgwav.cosmetics.sol.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ColorsTest {
    @Test
    void parsesEveryForm() {
        assertEquals(0xFFFFB347, Colors.parse("#ffb347", 0));
        assertEquals(0xFFFFFFFF, Colors.parse("#fff", 0));
        assertEquals(0x853C1608, Colors.parse("rgba(60,22,8,.52)", 0));
        assertEquals(0x12345678, Colors.parse("garbage", 0x12345678));
        assertNull(Colors.tryParse("#12"));
        assertEquals("#ffb347", Colors.hex(0xFFFFB347).toLowerCase());
    }

    @Test
    void hslRoundTrip() {
        int[] samples = {0xFFFF6A2A, 0xFF4A3866, 0xFF8FDCD0, 0xFF000000, 0xFFFFFFFF, 0xFF7B2CFF};
        for (int c : samples) {
            float[] hsl = Colors.toHsl(c);
            int back = Colors.fromHsl(hsl[0], hsl[1], hsl[2]);
            assertEquals(Colors.r(c), Colors.r(back), 1);
            assertEquals(Colors.g(c), Colors.g(back), 1);
            assertEquals(Colors.b(c), Colors.b(back), 1);
        }
    }

    @Test
    void shiftHueZeroIsIdentity() {
        int c = 0xFFFE8A2C;
        int s = Colors.shiftHue(c, 0f, 1f);
        assertEquals(Colors.r(c), Colors.r(s), 1);
        assertEquals(Colors.g(c), Colors.g(s), 1);
        assertEquals(Colors.b(c), Colors.b(s), 1);
    }

    @Test
    void onColourUsesThePrototypeRule() {
        assertEquals(0xFF0C0C10, Colors.onColour(0xFFFFFFFF));
        assertEquals(0xFFFFFFFF, Colors.onColour(0xFF5E0F06));
        assertEquals(0xFF0C0C10, Colors.onColour(0xFFFFB347), "Ember's amber accent takes dark text");
    }

    @Test
    void contrastAndNudge() {
        assertEquals(21.0, Colors.contrast(0xFFFFFFFF, 0xFF000000), 0.01);
        assertEquals(1.0, Colors.contrast(0xFF808080, 0xFF808080), 1e-9);
        int nudged = Colors.nudgeForContrast(0xFF444444, 0xFF222222, 4.5);
        assertTrue(Colors.contrast(nudged, 0xFF222222) >= 4.5);
    }

    @Test
    void mixEndpoints() {
        assertEquals(0xFF000000, Colors.mix(0xFF000000, 0xFFFFFFFF, 0f));
        assertEquals(0xFFFFFFFF, Colors.mix(0xFF000000, 0xFFFFFFFF, 1f));
        int mid = Colors.mix(0xFF000000, 0xFFFFFFFF, 0.5f);
        assertEquals(128, Colors.r(mid), 1);
    }
}
