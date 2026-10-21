package io.github.georgwav.cosmetics.melee;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnifeFinishTest {
    private static final int W = 16, H = 16;

    private static int[] picture() {
        int[] px = new int[W * H];
        for (int i = 0; i < px.length; i++) {
            int x = i % W, y = i / W;
            if (y < 4) {
                px[i] = 0;
            } else if (y < 8) {
                // Bright grey metal, getting brighter across.
                int v = 120 + x * 8;
                px[i] = 0xFF000000 | v << 16 | v << 8 | v;
            } else if (y < 12) {
                // The grip's violet wrap.
                px[i] = 0xFF5A3F6E;
            } else {
                // Dark metal, half see-through.
                px[i] = 0x80303232;
            }
        }
        return px;
    }

    @Test
    void rubyTurnsMetalRedTheWrapNearBlackAndKeepsWhatsSeeThrough() {
        int[] in = picture(), out = KnifeFinish.apply(in, W, H, KnifeFinish.RUBY);
        for (int i = 0; i < in.length; i++) {
            assertEquals(in[i] >>> 24, out[i] >>> 24, "alpha kept at " + i);
            int y = i / W, r = (out[i] >> 16) & 0xFF, g = (out[i] >> 8) & 0xFF, b = out[i] & 0xFF;
            if (y < 4) {
                assertEquals(0, out[i]);
            } else if (y < 8) {
                assertTrue(r >= 120 && r - g >= 40 && r - b >= 30, "metal is ruby red: " + Integer.toHexString(out[i]));
            } else {
                assertTrue(r < 60 && g < 20 && b < 25, "dark: " + Integer.toHexString(out[i]));
            }
        }
        // Brighter metal stays brighter (without the Doppler patches, which are the same at every brightness).
        assertTrue(KnifeFinish.ruby(0xFFF0F0F0, 0, 0, 1e6f) > KnifeFinish.ruby(0xFF808080, 0, 0, 1e6f));
    }

    @Test
    void theSamePictureGivesTheSameFinish() {
        assertArrayEquals(KnifeFinish.apply(picture(), W, H, "ruby"), KnifeFinish.apply(picture(), W, H, "RUBY"));
        assertTrue(KnifeFinish.known("Ruby"));
        assertFalse(KnifeFinish.known("gold"));
        assertThrows(IllegalArgumentException.class, () -> KnifeFinish.apply(picture(), W, H, "gold"));
    }

    @Test
    void aPngComesBackAsAPngOfTheSameSize() throws IOException {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, W, H, picture(), 0, W);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(img, "png", bytes);
        BufferedImage back = ImageIO.read(new ByteArrayInputStream(KnifeFinish.png(bytes.toByteArray(), KnifeFinish.RUBY)));
        assertEquals(W, back.getWidth());
        assertEquals(H, back.getHeight());
        assertArrayEquals(KnifeFinish.apply(picture(), W, H, KnifeFinish.RUBY), back.getRGB(0, 0, W, H, null, 0, W));
        assertThrows(IOException.class, () -> KnifeFinish.png(new byte[]{1, 2, 3}, KnifeFinish.RUBY));
    }
}
