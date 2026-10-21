package io.github.georgwav.cosmetics.melee;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnifeGeoTest {
    /** One 2×3×4 cube at the origin; every face's rectangle (u, v) to (u + 10, v + 10) from (face × 16, 0), on 64×32. */
    private static JsonObject cube(String extra) {
        String faces = "\"north\":{\"uv\":[0,0],\"uv_size\":[10,10]},\"south\":{\"uv\":[16,0],\"uv_size\":[10,10]},"
                + "\"east\":{\"uv\":[32,0],\"uv_size\":[10,10]},\"west\":{\"uv\":[48,0],\"uv_size\":[10,10]},"
                + "\"up\":{\"uv\":[0,16],\"uv_size\":[10,10]},\"down\":{\"uv\":[16,16],\"uv_size\":[10,10]" + extra + "}";
        return JsonParser.parseString("{\"minecraft:geometry\":[{\"description\":{\"texture_width\":64,\"texture_height\":32},"
                + "\"bones\":[{\"name\":\"b\",\"pivot\":[0,0,0],\"cubes\":[{\"origin\":[0,0,0],\"size\":[2,3,4],\"uv\":{" + faces
                + "}}]}]}]}").getAsJsonObject();
    }

    /** Georg's face table: for each face, the Bedrock corner at left-top, right-top, left-bottom and right-bottom. */
    private static final Object[][] TABLE = {
            {"up", new float[][]{{0, 3, 4}, {2, 3, 4}, {0, 3, 0}, {2, 3, 0}}, 0f, 16f},
            {"down", new float[][]{{0, 0, 0}, {2, 0, 0}, {0, 0, 4}, {2, 0, 4}}, 16f, 16f},
            {"east", new float[][]{{0, 3, 4}, {0, 3, 0}, {0, 0, 4}, {0, 0, 0}}, 32f, 0f},
            {"west", new float[][]{{2, 3, 0}, {2, 3, 4}, {2, 0, 0}, {2, 0, 4}}, 48f, 0f},
            {"north", new float[][]{{0, 3, 0}, {2, 3, 0}, {0, 0, 0}, {2, 0, 0}}, 0f, 0f},
            {"south", new float[][]{{2, 3, 4}, {0, 3, 4}, {2, 0, 4}, {0, 0, 4}}, 16f, 0f}};

    @Test
    void facesThatShowNothingAreLeftOut() {
        KnifeGeo g = KnifeGeo.parse(cube(""), "test");
        int[] cut = new int[64 * 32];
        cut[5 * 64 + 5] = 0xFF112233;      // inside north's picture (0, 0) to (10, 10)
        cut[9 * 64 + 41] = 0xFF112233;     // the last texel of east's (32, 0) to (42, 10)
        cut[5 * 64 + 26] = 0xFF112233;     // just past south's (16, 0) to (26, 10): not south's
        float[][] solid = Knife.solidQuads(g, cut, 64, 32);
        float[] q = g.bones[0].quads, kept = solid[0];
        assertEquals(2 * KnifeGeo.QUAD, kept.length, "north and east show; the see-through faces are left out");
        java.util.List<Float> us = new java.util.ArrayList<>();
        for (int o = 0; o < kept.length; o += KnifeGeo.QUAD) {
            us.add(kept[o + 3] * 64f);
        }
        assertTrue(us.contains(0f) && us.contains(32f), "north (u 0) and east (u 32): " + us);
        // Every face shows on a solid texture: the bone keeps its own array.
        java.util.Arrays.fill(cut, 0xFF000000);
        assertTrue(Knife.solidQuads(g, cut, 64, 32)[0] == q);
        // A cube of no height: its four sides have no area, the top and bottom stay.
        String flat = "{\"minecraft:geometry\":[{\"description\":{\"texture_width\":64,\"texture_height\":32},"
                + "\"bones\":[{\"name\":\"b\",\"pivot\":[0,0,0],\"cubes\":[{\"origin\":[0,0,0],\"size\":[2,0,4],\"uv\":[0,0]}]}]}]}";
        KnifeGeo f = KnifeGeo.parse(JsonParser.parseString(flat).getAsJsonObject(), "test");
        assertEquals(2 * KnifeGeo.QUAD, Knife.solidQuads(f, cut, 64, 32)[0].length);
    }

    @Test
    void facesFollowTheTable() {
        KnifeGeo g = KnifeGeo.parse(cube(""), "test");
        float[] q = g.bones[0].quads;
        assertEquals(6 * KnifeGeo.QUAD, q.length);
        for (Object[] row : TABLE) {
            float[][] corners = (float[][]) row[1];
            float u0 = (float) row[2] / 64f, v0 = (float) row[3] / 32f, u1 = u0 + 10f / 64f, v1 = v0 + 10f / 32f;
            float[][] uvs = {{u0, v0}, {u1, v0}, {u0, v1}, {u1, v1}};
            for (int k = 0; k < 4; k++) {
                // Knife space mirrors X.
                float[] want = {-corners[k][0], corners[k][1], corners[k][2]};
                assertEquals(1, count(q, want, uvs[k]), row[0] + " corner " + k);
            }
        }
    }

    /** How many corners in the quads sit at {@code p} with UV {@code uv}. */
    private static int count(float[] q, float[] p, float[] uv) {
        int n = 0;
        for (int i = 0; i < q.length; i += KnifeGeo.QUAD) {
            for (int k = 0; k < 4; k++) {
                int o = i + k * 5;
                if (near(q[o], p[0]) && near(q[o + 1], p[1]) && near(q[o + 2], p[2]) && near(q[o + 3], uv[0]) && near(q[o + 4], uv[1])) {
                    n++;
                }
            }
        }
        return n;
    }

    private static boolean near(float a, float b) {
        return Math.abs(a - b) < 1e-4f;
    }

    @Test
    void uvsDivideByTheDeclaredSizeAndANegativeSizeFlips() {
        String json = "{\"minecraft:geometry\":[{\"description\":{\"texture_width\":2048,\"texture_height\":2048},"
                + "\"bones\":[{\"name\":\"b\",\"pivot\":[0,0,0],\"cubes\":[{\"origin\":[0,0,0],\"size\":[1,1,1],"
                + "\"uv\":{\"north\":{\"uv\":[1024,512],\"uv_size\":[-512,256],\"uv_rotation\":90}}}]}]}]}";
        KnifeGeo g = KnifeGeo.parse(JsonParser.parseString(json).getAsJsonObject(), "test");
        float[] q = g.bones[0].quads;
        // Left-top takes uv, right-top uv + size (flipped back to 0.25); uv_rotation is ignored.
        assertArrayEquals(new float[]{0.5f, 0.25f}, new float[]{q[3], q[4]}, 1e-6f);
        assertArrayEquals(new float[]{0.25f, 0.25f}, new float[]{q[8], q[9]}, 1e-6f);
    }

    @Test
    void specialBones() {
        String json = "{\"minecraft:geometry\":[{\"description\":{\"texture_width\":16,\"texture_height\":16},\"bones\":["
                + "{\"name\":\"weapon\",\"pivot\":[0,0,0]},"
                + "{\"name\":\"blade_illuminated\",\"parent\":\"weapon\",\"pivot\":[0,0,0]},"
                + "{\"name\":\"tip\",\"parent\":\"blade_illuminated\",\"pivot\":[0,0,0]},"
                + "{\"name\":\"1p_effect_1\",\"parent\":\"weapon\",\"pivot\":[0,0,0]},"
                + "{\"name\":\"glint\",\"parent\":\"weapon\",\"pivot\":[0,0,0]},"
                + "{\"name\":\"righthand_pos\",\"pivot\":[0,0,0],\"cubes\":[{\"origin\":[0,0,0],\"size\":[4,12,4],\"uv\":[0,0]}]},"
                + "{\"name\":\"lefthand_pos\",\"pivot\":[0,0,0]},"
                + "{\"name\":\"idle_view\",\"pivot\":[0,0,0],\"cubes\":[{\"origin\":[0,0,0],\"size\":[1,1,1],\"uv\":[0,0]}]}]}]}";
        KnifeGeo g = KnifeGeo.parse(JsonParser.parseString(json).getAsJsonObject(), "test");
        assertTrue(g.bones[g.bone("tip")].lit, "under an illuminated bone counts as lit");
        assertFalse(g.bones[g.bone("weapon")].lit);
        assertTrue(g.bones[g.bone("1p_effect_1")].slash);
        assertTrue(g.bones[g.bone("glint")].effect);
        assertFalse(g.bones[g.bone("glint")].slash, "a glint isn't a slash");
        assertEquals(1, g.bones[g.bone("righthand_pos")].arm);
        assertEquals(-1, g.bones[g.bone("lefthand_pos")].arm);
        assertEquals(0, g.bones[g.bone("righthand_pos")].quads.length, "an arm's placeholder cubes are never drawn");
        assertEquals(0, g.bones[g.bone("idle_view")].quads.length, "markers draw nothing");
    }

    @Test
    void parentsComeBeforeChildrenWhateverTheFileOrder() {
        String json = "{\"minecraft:geometry\":[{\"description\":{},\"bones\":["
                + "{\"name\":\"child\",\"parent\":\"mid\",\"pivot\":[1,2,3]},{\"name\":\"mid\",\"parent\":\"top\",\"pivot\":[0,0,0]},"
                + "{\"name\":\"top\",\"pivot\":[0,0,0]}]}]}";
        KnifeGeo g = KnifeGeo.parse(JsonParser.parseString(json).getAsJsonObject(), "test");
        assertTrue(g.bone("top") < g.bone("mid") && g.bone("mid") < g.bone("child"));
        assertEquals(-1f, g.bones[g.bone("child")].px, "knife space mirrors X");
    }
}
