package io.github.georgwav.cosmetics.model;

import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClothTest {
    private static BakedMesh capeMesh() {
        BakedCosmetic cape = CosmeticBaker.bake(CosmeticParser.parse("t:cape", new StringReader(
                "{\"slot\":\"cape\",\"texture\":\"t:c.png\",\"texture_size\":[64,32],\"parts\":[{\"cubes\":"
                        + "[{\"from\":[-5,0,-1],\"to\":[5,16,0],\"uv\":[0,0]}]}]}")));
        return cape.layers()[0].mesh();
    }

    @Test
    void facesDownTheCapeAreSplitIntoPixelStrips() {
        Cloth.Mesh cloth = Cloth.prepare(capeMesh());
        assertEquals(1f, cloth.length(), 1e-6); // 16 px
        // Front, back and both sides become 16 strips each; top and bottom stay whole.
        assertEquals(4 * 16 + 2, cloth.mesh().quadCount());
        float[] d = cloth.mesh().data();
        for (int i = 0; i < d.length; i += BakedMesh.FLOATS_PER_VERTEX) {
            assertTrue(d[i + 1] >= -1e-6 && d[i + 1] <= 1 + 1e-6);
        }
        // Every strip is at most a pixel tall, and V runs with Y (1 px of texture per px of cape).
        for (int q = 0; q < cloth.mesh().quadCount(); q++) {
            float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
            for (int v = 0; v < 4; v++) {
                float y = d[(q * 4 + v) * BakedMesh.FLOATS_PER_VERTEX + 1];
                min = Math.min(min, y);
                max = Math.max(max, y);
            }
            assertTrue(max - min <= 1f / 16f + 1e-5, "strip " + q);
        }
    }

    @Test
    void aStillCapeWithNoStrengthHangsStraight() {
        Cloth.Shape shape = Cloth.shape(1f, 0f, 3.0, 0f, 0f);
        float[] out = new float[6];
        Cloth.bend(shape, 0.2f, 0.5f, -0.0625f, 0f, 0f, -1f, out);
        assertEquals(0.2f, out[0], 1e-6);
        assertEquals(0.5f, out[1], 1e-5);
        assertEquals(-0.0625f, out[2], 1e-5);
        assertEquals(-1f, out[5], 1e-6);
    }

    @Test
    void movingBlowsTheBottomBackAndKeepsTheLength() {
        Cloth.Shape shape = Cloth.shape(1f, 1f, 0.0, 0f, 1f);
        float[] top = new float[6];
        float[] bottom = new float[6];
        Cloth.bend(shape, 0f, 0f, 0f, 0f, 0f, -1f, top);
        Cloth.bend(shape, 0f, 1f, 0f, 0f, 0f, -1f, bottom);
        assertEquals(0f, top[1], 1e-6);
        assertEquals(0f, top[2], 1e-6);
        assertTrue(bottom[2] < -0.2f, "bottom blown back: " + bottom[2]); // back is -Z
        assertTrue(bottom[1] < 0.95f, "and lifted: " + bottom[1]);
        // The centre line keeps its length.
        float length = 0f;
        for (int i = 1; i < shape.y().length; i++) {
            length += (float) Math.hypot(shape.y()[i] - shape.y()[i - 1], shape.z()[i] - shape.z()[i - 1]);
        }
        assertEquals(1f, length, 1e-3);
    }

    @Test
    void theRippleMovesWithTime() {
        Cloth.Shape a = Cloth.shape(1f, 0f, 0.0, 0f, 1f);
        Cloth.Shape b = Cloth.shape(1f, 0f, 0.4, 0f, 1f);
        // The wave shows as the cloth's angle along its length changing over time.
        assertTrue(Math.abs(a.angle()[Cloth.SAMPLES - 1] - b.angle()[Cloth.SAMPLES - 1]) > 0.02);
    }
}
