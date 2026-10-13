package io.github.georgwav.cosmetics.render;

import io.github.georgwav.cosmetics.model.BakedMesh;
import io.github.georgwav.cosmetics.model.CosmeticBaker;
import io.github.georgwav.cosmetics.model.CosmeticParser;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Wardrobe tiles' software renderer. */
class SoftRasterTest {
    private static final float[] NORMAL = {1, 0, 0, 0, 1, 0, 0, 0, 1};

    /** A cube's mesh, positions in blocks (pixels / 16). */
    private static BakedMesh cube(String from, String to) {
        return CosmeticBaker.bake(CosmeticParser.parse("t:c", new StringReader(
                "{\"slot\":\"head\",\"texture\":\"t:a.png\",\"texture_size\":[16,16],\"parts\":[{\"cubes\":["
                        + "{\"from\":" + from + ",\"to\":" + to + ",\"uv\":[0,0]}]}]}"))).layers()[0].mesh();
    }

    /** Scale by s (blocks to pixels) and move by (tx, ty, tz). */
    private static float[] place(float s, float tx, float ty, float tz) {
        return new float[]{s, 0, 0, 0, s, 0, 0, 0, s, tx, ty, tz};
    }

    private static SoftRaster.Texture solid(int argb) {
        int[] pixels = new int[16 * 16];
        java.util.Arrays.fill(pixels, argb);
        return new SoftRaster.Texture(16, 16, pixels);
    }

    @Test
    void aCubeCoversItsSquareAndNothingElse() {
        SoftRaster raster = new SoftRaster(32);
        // A 1-block cube drawn 16 pixels to the block: x and y from 8 to 24.
        raster.draw(cube("[0,0,0]", "[16,16,16]"), place(16, 8, 8, 0), NORMAL, 0f, solid(0xFFFF0000),
                0xFFFFFFFF, SoftRaster.Blend.CUTOUT);
        int[] p = raster.pixels();
        assertEquals(0xFF, p[16 * 32 + 16] >>> 24, "the middle is drawn");
        assertTrue(((p[16 * 32 + 16] >> 16) & 0xFF) > 100, "in the texture's red, lit");
        assertEquals(0, p[2 * 32 + 2], "outside stays clear");
        assertEquals(0, p[16 * 32 + 28], "outside stays clear");
    }

    @Test
    void theNearerFaceWins() {
        SoftRaster raster = new SoftRaster(32);
        // Red cube behind (larger z), then a green one in front (smaller z): green shows wherever both are.
        raster.draw(cube("[0,0,0]", "[16,16,16]"), place(16, 8, 8, 10), NORMAL, 0f, solid(0xFFFF0000),
                0xFFFFFFFF, SoftRaster.Blend.CUTOUT);
        raster.draw(cube("[0,0,0]", "[16,16,16]"), place(16, 8, 8, -10), NORMAL, 0f, solid(0xFF00FF00),
                0xFFFFFFFF, SoftRaster.Blend.CUTOUT);
        int middle = raster.pixels()[16 * 32 + 16];
        assertTrue(((middle >> 8) & 0xFF) > 100 && ((middle >> 16) & 0xFF) == 0, "green in front");
        // Drawn the other way round, the green one still wins.
        raster.clear();
        raster.draw(cube("[0,0,0]", "[16,16,16]"), place(16, 8, 8, -10), NORMAL, 0f, solid(0xFF00FF00),
                0xFFFFFFFF, SoftRaster.Blend.CUTOUT);
        raster.draw(cube("[0,0,0]", "[16,16,16]"), place(16, 8, 8, 10), NORMAL, 0f, solid(0xFFFF0000),
                0xFFFFFFFF, SoftRaster.Blend.CUTOUT);
        middle = raster.pixels()[16 * 32 + 16];
        assertTrue(((middle >> 8) & 0xFF) > 100 && ((middle >> 16) & 0xFF) == 0, "still green");
    }

    @Test
    void seeThroughPixelsAreSkippedAndTintMultiplies() {
        SoftRaster raster = new SoftRaster(32);
        raster.draw(cube("[0,0,0]", "[16,16,16]"), place(16, 8, 8, 0), NORMAL, 0f, solid(0x00FFFFFF),
                0xFFFFFFFF, SoftRaster.Blend.CUTOUT);
        assertEquals(0, raster.pixels()[16 * 32 + 16], "clear texture, nothing drawn");
        raster.draw(cube("[0,0,0]", "[16,16,16]"), place(16, 8, 8, 0), NORMAL, 0f, solid(0xFFFFFFFF),
                0xFF0000FF, SoftRaster.Blend.CUTOUT);
        int middle = raster.pixels()[16 * 32 + 16];
        assertEquals(0, (middle >> 16) & 0xFF, "a blue tint leaves no red");
        assertTrue((middle & 0xFF) > 100, "but blue");
    }

    @Test
    void translucentPixelsBlendOverWhatIsBehind() {
        int half = SoftRaster.over(255, 255, 255, 128, 0xFF000000);
        assertEquals(0xFF, half >>> 24);
        assertTrue(Math.abs(((half >> 16) & 0xFF) - 128) <= 1, "half white over black is grey");
        assertEquals(0xFF0000FF & 0xFF, SoftRaster.over(0, 0, 255, 255, 0xFFFF0000) & 0xFF);
        assertEquals(0, SoftRaster.over(1, 2, 3, 0, 0));
    }

    @Test
    void boundsFollowTheTransform() {
        float[] bounds = SoftRaster.bounds(cube("[-4,0,-2]", "[4,8,2]"), place(1, 0, 0, 0), SoftRaster.emptyBounds());
        // Positions are in blocks: -4..4 px is -0.25..0.25.
        assertEquals(-0.25f, bounds[0], 1e-5);
        assertEquals(0.5f, bounds[4], 1e-5);
        assertEquals(0.125f, bounds[5], 1e-5);
    }
}
