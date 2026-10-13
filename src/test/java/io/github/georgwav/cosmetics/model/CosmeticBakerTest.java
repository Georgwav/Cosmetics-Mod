package io.github.georgwav.cosmetics.model;

import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CosmeticBakerTest {
    private static final float EPS = 1.0e-5f;

    private static BakedCosmetic bake(String json) {
        return CosmeticBaker.bake(CosmeticParser.parse("test:thing", new StringReader(json)));
    }

    /** Vertex {@code v} of quad {@code q}: {x, y, z, u, v, nx, ny, nz}. */
    private static float[] vertex(BakedMesh mesh, int q, int v) {
        float[] out = new float[BakedMesh.FLOATS_PER_VERTEX];
        System.arraycopy(mesh.data(), q * BakedMesh.FLOATS_PER_QUAD + v * BakedMesh.FLOATS_PER_VERTEX, out, 0, out.length);
        return out;
    }

    @Test
    void boxUvMatchesVanillaHeadLayout() {
        // The vanilla head cube: addBox(-4, -8, -4, 8, 8, 8) with texOffs(0, 0) on a 64x64 skin.
        BakedCosmetic cosmetic = bake("""
                {"slot": "head", "texture": "test:t.png", "texture_size": [64, 64],
                 "parts": [{"cubes": [{"from": [-4, -8, -4], "to": [4, 0, 4], "uv": [0, 0]}]}]}
                """);
        BakedMesh mesh = cosmetic.layers()[0].mesh();
        assertEquals(6, mesh.quadCount());

        // Quad order is DOWN, UP, NORTH, SOUTH, WEST, EAST. NORTH (the face) uses u 8..16, v 8..16.
        float[] north0 = vertex(mesh, 2, 0);
        assertArrayEquals(new float[]{4 / 16f, -8 / 16f, -4 / 16f, 16 / 64f, 8 / 64f, 0, 0, -1}, north0, EPS);
        float[] north2 = vertex(mesh, 2, 2);
        assertArrayEquals(new float[]{-4 / 16f, 0, -4 / 16f, 8 / 64f, 16 / 64f, 0, 0, -1}, north2, EPS);

        // UP face (top of the box in model space is minY) samples u 16..24, v 0..8, flipped on V like vanilla.
        float[] up0 = vertex(mesh, 1, 0);
        assertEquals(24 / 64f, up0[3], EPS);
        assertEquals(8 / 64f, up0[4], EPS);
    }

    @Test
    void mirrorReversesWindingAndFlipsNormalX() {
        BakedMesh plain = bake("""
                {"slot": "body", "texture": "test:t.png",
                 "parts": [{"cubes": [{"from": [0, 0, 0], "to": [2, 2, 2], "uv": [0, 0]}]}]}
                """).layers()[0].mesh();
        BakedMesh mirrored = bake("""
                {"slot": "body", "texture": "test:t.png",
                 "parts": [{"cubes": [{"from": [0, 0, 0], "to": [2, 2, 2], "uv": [0, 0], "mirror": true}]}]}
                """).layers()[0].mesh();
        // WEST face (index 4): normal -X normally, +X when mirrored.
        assertEquals(-1f, vertex(plain, 4, 0)[5], EPS);
        assertEquals(1f, vertex(mirrored, 4, 0)[5], EPS);
        // UVs of the first plain vertex end up on the last mirrored vertex.
        assertEquals(vertex(plain, 2, 0)[3], vertex(mirrored, 2, 3)[3], EPS);
        assertEquals(vertex(plain, 2, 0)[4], vertex(mirrored, 2, 3)[4], EPS);
    }

    @Test
    void rotationIsAppliedAroundPivot() {
        // A 90 degree turn around Y with the pivot at the cube's own corner: +X becomes -Z.
        BakedMesh mesh = bake("""
                {"slot": "head", "texture": "test:t.png",
                 "parts": [{"cubes": [{"from": [0, 0, 0], "to": [16, 1, 1], "uv": [0, 0],
                                       "pivot": [0, 0, 0], "rotation": [0, 90, 0]}]}]}
                """).layers()[0].mesh();
        float minZ = Float.MAX_VALUE;
        float maxAbsX = 0;
        for (int q = 0; q < mesh.quadCount(); q++) {
            for (int v = 0; v < 4; v++) {
                float[] p = vertex(mesh, q, v);
                minZ = Math.min(minZ, p[2]);
                maxAbsX = Math.max(maxAbsX, Math.abs(p[0]));
            }
        }
        assertEquals(-1f, minZ, EPS);          // 16px along +X is now 1 block along -Z
        assertEquals(1 / 16f, maxAbsX, EPS);   // the 1px depth is now along X
        // EAST face normal (+X) now points to -Z.
        float[] east = vertex(mesh, 5, 0);
        assertEquals(0f, east[5], EPS);
        assertEquals(-1f, east[7], EPS);
    }

    @Test
    void perFaceUvSkipsMissingFaces() {
        BakedCosmetic cosmetic = bake("""
                {"slot": "head", "texture": "test:t.png", "texture_size": [16, 16],
                 "parts": [{"mode": "emissive", "cubes": [{"from": [0, 0, 0], "to": [1, 1, 1],
                            "faces": {"north": [0, 0, 4, 4], "up": [4, 4, 8, 8]}}]}]}
                """);
        BakedCosmetic.Layer layer = cosmetic.layers()[0];
        assertEquals(RenderMode.EMISSIVE, layer.mode());
        assertEquals(2, layer.mesh().quadCount());
    }

    @Test
    void layersAreGroupedByModeAndTextureInDrawOrder() {
        BakedCosmetic cosmetic = bake("""
                {"slot": "cape", "texture": "test:cape.png",
                 "parts": [
                   {"mode": "emissive", "texture": "test:glow.png", "cubes": [{"from": [0,0,0], "to": [1,1,1], "uv": [0,0]}]},
                   {"cubes": [{"from": [0,0,0], "to": [1,1,1], "uv": [0,0]}]},
                   {"cubes": [{"from": [2,0,0], "to": [3,1,1], "uv": [0,0]}]}
                 ]}
                """);
        assertEquals(2, cosmetic.layers().length);
        assertEquals(RenderMode.CUTOUT, cosmetic.layers()[0].mode());
        assertEquals("test:cape.png", cosmetic.layers()[0].texture());
        assertEquals(12, cosmetic.layers()[0].mesh().quadCount());
        assertEquals(RenderMode.EMISSIVE, cosmetic.layers()[1].mode());
        assertEquals("test:glow.png", cosmetic.layers()[1].texture());
    }

    @Test
    void textureWithoutNamespaceUsesTheCosmeticsNamespace() {
        BakedCosmetic cosmetic = bake("""
                {"slot": "back", "texture": "textures/cosmetic/x.png",
                 "parts": [{"cubes": [{"from": [0,0,0], "to": [1,1,1], "uv": [0,0]}]}]}
                """);
        assertEquals("test:textures/cosmetic/x.png", cosmetic.layers()[0].texture());
    }

    @Test
    void invalidFilesFailWithAClearMessage() {
        assertThrows(RuntimeException.class, () -> bake("{\"texture\": \"test:t.png\", \"parts\": []}"));
        assertThrows(RuntimeException.class, () -> bake("""
                {"slot": "nope", "texture": "test:t.png", "parts": [{"cubes": [{"from": [0,0,0], "to": [1,1,1], "uv": [0,0]}]}]}
                """));
        assertThrows(RuntimeException.class, () -> bake("""
                {"slot": "head", "texture": "test:T.png", "parts": [{"cubes": [{"from": [0,0,0], "to": [1,1,1], "uv": [0,0]}]}]}
                """));
        assertThrows(RuntimeException.class, () -> bake("""
                {"slot": "head", "texture": "test:t.png", "parts": [{"cubes": [{"from": [1,0,0], "to": [0,1,1], "uv": [0,0]}]}]}
                """));
        assertThrows(RuntimeException.class, () -> bake("""
                {"slot": "head", "texture": "test:t.png", "parts": [{"cubes": [{"from": [0,0,0], "to": [1,1,1]}]}]}
                """));
    }

    @Test
    void namesComeFromJsonOrTheFileName() {
        assertEquals("Top Hat", CosmeticParser.prettify("cosmeticsmod:top_hat"));
        assertEquals("Wings", CosmeticParser.prettify("pack:seasonal/wings"));
        assertEquals("Fancy Hat", bake("""
                {"name": "Fancy Hat", "slot": "head", "texture": "test:t.png",
                 "parts": [{"cubes": [{"from": [0,0,0], "to": [1,1,1], "uv": [0,0]}]}]}
                """).name());
    }
}
