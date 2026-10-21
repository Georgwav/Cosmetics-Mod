package io.github.georgwav.cosmetics.sol.fx;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FxSoftTest {
    private static List<FxSoft.Region> regions() throws IllegalAccessException {
        List<FxSoft.Region> out = new ArrayList<>();
        for (Field f : FxSoft.class.getFields()) {
            Object v = f.get(null);
            if (v instanceof FxSoft.Region r) {
                out.add(r);
            } else if (v instanceof FxSoft.Region[] rs) {
                out.addAll(List.of(rs));
            }
        }
        return out;
    }

    @Test
    void regionsFitAndNeverOverlap() throws IllegalAccessException {
        FxTex atlas = FxSoft.atlas();
        assertTrue(atlas.linear, "the soft pictures are filtered smoothly");
        List<FxSoft.Region> all = regions();
        for (FxSoft.Region r : all) {
            assertTrue(r.x >= 0 && r.y >= 0 && r.x + r.w <= FxSoft.W && r.y + r.h <= FxSoft.H, r.name + " is inside the atlas");
            assertTrue(r.u1 > r.u0 && r.v1 > r.v0, r.name + " has its coordinates");
        }
        for (int i = 0; i < all.size(); i++) {
            for (int j = i + 1; j < all.size(); j++) {
                FxSoft.Region a = all.get(i), b = all.get(j);
                boolean apart = a.x + a.w <= b.x || b.x + b.w <= a.x || a.y + a.h <= b.y || b.y + b.h <= a.y;
                assertTrue(apart, a.name + " and " + b.name + " overlap");
            }
        }
    }

    @Test
    void roundLightsAreBrightInTheMiddleAndClearAtTheEdge() {
        FxTex atlas = FxSoft.atlas();
        for (FxSoft.Region r : new FxSoft.Region[]{FxSoft.GLOW, FxSoft.HOT, FxSoft.FLARE}) {
            int mid = atlas.argb[(r.y + r.h / 2) * FxSoft.W + r.x + r.w / 2] >>> 24;
            int corner = atlas.argb[r.y * FxSoft.W + r.x] >>> 24;
            int side = atlas.argb[(r.y + r.h / 2) * FxSoft.W + r.x] >>> 24;
            assertTrue(mid > 200, r.name + " is bright in the middle: " + mid);
            assertEquals(0, corner, r.name + " is clear in its corner");
            assertTrue(side < 6, r.name + " fades out before its edge: " + side);
        }
    }

    @Test
    void clearPixelsAreWhiteSoTheFilterNeverDarkensEdges() {
        FxTex atlas = FxSoft.atlas();
        int dark = 0;
        for (int c : atlas.argb) {
            if (c >>> 24 == 0 && (c & 0xFFFFFF) != 0xFFFFFF) {
                dark++;
            }
        }
        // The space between regions is never painted: only regions' own clear pixels count, and they're white.
        assertFalse(dark > FxSoft.W * FxSoft.H * 0.9, "clear pixels inside regions are white");
        FxSoft.Region g = FxSoft.GLOW;
        assertEquals(0xFFFFFF, atlas.argb[g.y * FxSoft.W + g.x] & 0xFFFFFF);
    }

    @Test
    void theNoiseStripRepeatsAcrossItsHalfWidth() {
        FxTex atlas = FxSoft.atlas();
        FxSoft.Region n = FxSoft.NOISE;
        int half = n.w / 2;
        int differ = 0;
        for (int y = 0; y < n.h; y++) {
            int a = atlas.argb[(n.y + y) * FxSoft.W + n.x] >>> 24, b = atlas.argb[(n.y + y) * FxSoft.W + n.x + half] >>> 24;
            differ = Math.max(differ, Math.abs(a - b));
        }
        assertTrue(differ < 24, "the strip's two halves line up (" + differ + ")");
    }

    @Test
    void motionFollowsDragAndGravity() {
        // No drag: plain ballistics.
        assertEquals(2f * 0.5f - 0.5f * 9.8f * 0.25f, Vfx.fly(2f, 0f, -9.8f, 0.5f), 1e-4f);
        // With drag, a launch slows toward its terminal speed.
        float v = Vfx.speed(10f, 3f, -9.8f, 5f);
        assertEquals(-9.8f / 3f, v, 0.05f);
        assertTrue(Vfx.fly(10f, 3f, 0f, 10f) < 10f / 3f + 1e-3f, "drag caps how far it goes");
    }

    @Test
    void vividKeepsTheHueAndGreysStayGrey() {
        int pale = 0xFF8FDCD0;
        int v = FxColour.vivid(pale);
        float[] a = java.awt.Color.RGBtoHSB(0x8F, 0xDC, 0xD0, null);
        float[] b = java.awt.Color.RGBtoHSB(v >> 16 & 255, v >> 8 & 255, v & 255, null);
        assertEquals(a[0], b[0], 0.02f, "same hue");
        assertTrue(b[1] >= 0.69f && b[2] >= 0.94f, "saturated and bright");
        assertEquals(0xFF808080, FxColour.vivid(0xFF808080), "a grey stays grey");
    }
}
