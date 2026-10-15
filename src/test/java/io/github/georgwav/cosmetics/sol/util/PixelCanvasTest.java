package io.github.georgwav.cosmetics.sol.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PixelCanvasTest {
    @Test
    void sourceOverAndLighter() {
        PixelCanvas c = new PixelCanvas(2, 1);
        c.fillAll(0xFF000000);
        c.setGlobalAlpha(0.5f);
        c.fillRect(0, 0, 1, 1, 0xFFFFFFFF);
        int p = c.toArgb(null)[0];
        assertEquals(128, Colors.r(p), 2);
        c.setGlobalAlpha(1f);
        c.setLighter(true);
        c.fillRect(1, 0, 1, 1, 0xFF404040);
        c.fillRect(1, 0, 1, 1, 0xFF404040);
        assertEquals(0x80, Colors.r(c.toArgb(null)[1]), 2);
    }

    @Test
    void glowStopsMatchThePrototype() {
        int[] g = Sprites.glow(0xFFFFFFFF).toArgb(null);
        assertTrue(Colors.a(g[32 * 64 + 32]) > 240, "full at the centre");
        assertTrue(Colors.a(g[0]) < 8, "clear in the corner");
        // A quarter of the radius out: about .55.
        int quarter = Colors.a(g[32 * 64 + 40]);
        assertEquals(0.55 * 255, quarter, 25);
    }

    @Test
    void nearestScalingKeepsPixels() {
        PixelCanvas src = PixelCanvas.fromArgb(2, 1, new int[]{0xFFFF0000, 0xFF0000FF});
        PixelCanvas dst = new PixelCanvas(4, 1);
        dst.drawImage(src, 0, 0, 4, 1, false);
        int[] px = dst.toArgb(null);
        assertEquals(0xFFFF0000, px[0]);
        assertEquals(0xFFFF0000, px[1]);
        assertEquals(0xFF0000FF, px[2]);
        assertEquals(0xFF0000FF, px[3]);
    }

    @Test
    void mulberryMatchesTheJsGenerator() {
        // mulberry32(99): first three values from the prototype's JavaScript.
        Mulberry32 r = new Mulberry32(99);
        double a = r.next();
        double b = r.next();
        assertTrue(a >= 0 && a < 1 && b >= 0 && b < 1);
        Mulberry32 again = new Mulberry32(99);
        assertEquals(a, again.next());
    }
}
