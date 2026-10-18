package io.github.georgwav.cosmetics.sol.sky;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NebulaTexTest {
    private static float alpha(int[] px, int x0, int y0, int x1, int y1) {
        float sum = 0f;
        int n = 0;
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                sum += (px[y * NebulaTex.W + x] >>> 24) / 255f;
                n++;
            }
        }
        return sum / n;
    }

    @Test
    void theGasIsInTheMiddleAndFadesToNothingAtTheEdges() {
        for (NebulaTex.Spec s : NebulaTex.BIG) {
            NebulaTex.Pictures p = NebulaTex.bake(s.seed(), s.pal());
            int w = NebulaTex.W, h = NebulaTex.H;
            float middle = alpha(p.emission(), w / 4, h / 4, w * 3 / 4, h * 3 / 4);
            float border = (alpha(p.emission(), 0, 0, w, 3) + alpha(p.emission(), 0, h - 3, w, h)
                    + alpha(p.emission(), 0, 0, 3, h) + alpha(p.emission(), w - 3, 0, w, h)) / 4f;
            assertTrue(middle > 0.08f, "gas in the middle of nebula " + s.seed() + ": " + middle);
            assertTrue(border < 0.01f, "no hard edge on nebula " + s.seed() + ": " + border);
        }
    }

    @Test
    void theDustIsDarkAndNeverCoversCompletely() {
        NebulaTex.Pictures p = NebulaTex.bake(1, NebulaTex.BIG[0].pal());
        int max = 0;
        boolean any = false;
        for (int c : p.dust()) {
            int a = c >>> 24;
            max = Math.max(max, a);
            if (a > 0) {
                any = true;
                assertTrue((c >> 16 & 255) < 16 && (c >> 8 & 255) < 16 && (c & 255) < 16, "dark dust");
            }
        }
        assertTrue(any, "some dust lanes");
        assertTrue(max <= Math.round(0.85f * 255f), "the stars behind still show a little: " + max);
    }

    @Test
    void theSameSeedPaintsTheSamePicture() {
        NebulaTex.Spec s = NebulaTex.BIG[2];
        assertArrayEquals(NebulaTex.bake(s.seed(), s.pal()).emission(), NebulaTex.bake(s.seed(), s.pal()).emission());
    }
}
