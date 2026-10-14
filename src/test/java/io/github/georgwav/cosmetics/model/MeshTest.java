package io.github.georgwav.cosmetics.model;

import com.google.gson.JsonParseException;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MeshTest {
    private static final float EPS = 1.0e-5f;

    private final List<String> warnings = new ArrayList<>();

    private CosmeticDefinition parse(String json, Map<String, String> files, int maxTriangles) {
        CosmeticParser.Context context = new CosmeticParser.Context(
                modelId -> {
                    throw new FileNotFoundException(modelId);
                },
                location -> {
                    String text = files.get(location);
                    if (text == null) {
                        throw new FileNotFoundException(location);
                    }
                    return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
                },
                warnings::add, maxTriangles);
        return CosmeticParser.parse("test:thing", new StringReader(json), context);
    }

    private CosmeticDefinition parse(String json, Map<String, String> files) {
        return parse(json, files, CosmeticParser.Context.DEFAULT_MAX_MESH_TRIANGLES);
    }

    private CosmeticDefinition parse(String json) {
        return parse(json, Map.of());
    }

    /** A cosmetic with one part holding the given mesh JSON. */
    private static String withMesh(String mesh) {
        return """
                {"slot": "head", "texture": "test:t.png", "texture_size": [16, 16],
                 "parts": [{"meshes": [%s]}]}
                """.formatted(mesh);
    }

    private static CosmeticDefinition.Mesh onlyMesh(CosmeticDefinition definition) {
        return definition.parts().get(0).meshes().get(0);
    }

    /** Vertex {@code v} of quad {@code q}: {x, y, z, u, v, nx, ny, nz}. */
    private static float[] vertex(BakedMesh mesh, int q, int v) {
        float[] out = new float[BakedMesh.FLOATS_PER_VERTEX];
        System.arraycopy(mesh.data(), q * BakedMesh.FLOATS_PER_QUAD + v * BakedMesh.FLOATS_PER_VERTEX, out, 0, out.length);
        return out;
    }

    // ------------------------------------------------------------------ inline meshes

    @Test
    void inlineMeshKeepsQuadsAndRepeatsTheLastTriangleVertex() {
        CosmeticDefinition definition = parse(withMesh("""
                {"vertices": [[0,0,0], [8,0,0], [8,8,0], [0,8,0], [16,0,0]],
                 "uvs": [[0,0], [8,0], [8,8], [0,8], [16,0]],
                 "faces": [[3,2,1,0], [1,4,2]]}
                """));
        CosmeticDefinition.Mesh mesh = onlyMesh(definition);
        assertArrayEquals(new int[]{4, 3}, mesh.faceSizes());
        assertEquals(3, mesh.triangles());

        BakedMesh baked = CosmeticBaker.bake(definition).layers()[0].mesh();
        assertEquals(2, baked.quadCount());
        // Positions to blocks, UVs to 0..1.
        assertArrayEquals(new float[]{0, 8 / 16f, 0, 0, 8 / 16f}, slice(vertex(baked, 0, 0), 5), EPS);
        // The triangle is a quad whose last vertex repeats its third.
        assertArrayEquals(vertex(baked, 1, 2), vertex(baked, 1, 3), EPS);
        assertArrayEquals(new float[]{1, 0, 0}, slice(vertex(baked, 1, 1), 3), EPS);
    }

    @Test
    void flatFacesFaceOutwardsLikeCubeFaces() {
        // The same square as a cube's north face, given in the cube face's own corner order, bakes to
        // exactly the same quad: same positions, UVs, winding and normal.
        BakedMesh cube = CosmeticBaker.bake(CosmeticParser.parse("test:c", new StringReader("""
                {"slot": "head", "texture": "test:t.png", "texture_size": [16, 16],
                 "parts": [{"cubes": [{"from": [0,0,0], "to": [4,4,4], "faces": {"north": [0,0,4,4]}}]}]}
                """))).layers()[0].mesh();
        BakedMesh mesh = CosmeticBaker.bake(parse(withMesh("""
                {"vertices": [[4,0,0], [0,0,0], [0,4,0], [4,4,0]],
                 "uvs": [[4,0], [0,0], [0,4], [4,4]],
                 "faces": [[0,1,2,3]]}
                """))).layers()[0].mesh();
        assertArrayEquals(cube.data(), mesh.data(), EPS);
        assertArrayEquals(new float[]{0, 0, -1}, slice(vertex(mesh, 0, 0), 5, 8), EPS);
    }

    @Test
    void cubesAndMeshesShareTheirLayerAndSmoothMeshesGetTheSecondBuffer() {
        BakedCosmetic cosmetic = CosmeticBaker.bake(parse("""
                {"slot": "head", "texture": "test:t.png", "texture_size": [16, 16],
                 "parts": [{"cubes": [{"from": [0,0,0], "to": [1,1,1], "uv": [0,0]}],
                            "meshes": [{"vertices": [[0,0,0],[1,0,0],[0,1,0]], "uvs": [[0,0],[1,0],[0,1]], "faces": [[0,1,2]]},
                                       {"shading": "smooth", "vertices": [[0,0,0],[1,0,0],[0,1,0]], "uvs": [[0,0],[1,0],[0,1]], "faces": [[0,1,2]]}]}]}
                """));
        assertEquals(1, cosmetic.layers().length); // no extra draw calls
        BakedCosmetic.Layer layer = cosmetic.layers()[0];
        assertEquals(7, layer.mesh().quadCount()); // 6 cube faces + 1 flat triangle
        assertNotNull(layer.smoothMesh());
        assertEquals(1, layer.smoothMesh().quadCount());
    }

    @Test
    void cubeOnlyLayersHaveNoSmoothMesh() {
        BakedCosmetic cosmetic = CosmeticBaker.bake(CosmeticParser.parse("test:c", new StringReader("""
                {"slot": "head", "texture": "test:t.png", "parts": [{"cubes": [{"from": [0,0,0], "to": [1,1,1], "uv": [0,0]}]}]}
                """)));
        assertNull(cosmetic.layers()[0].smoothMesh());
    }

    @Test
    void placementMirrorsScalesRotatesThenOffsets() {
        CosmeticDefinition.Mesh mesh = onlyMesh(parse(withMesh("""
                {"vertices": [[1,0,0], [1,1,0], [1,0,1]], "uvs": [[0,0],[0,0],[0,0]], "faces": [[0,1,2]],
                 "mirror": true, "scale": [2, 3, 4], "pivot": [0, 0, 0], "rotation": [0, 0, 90], "offset": [10, 20, 30]}
                """)));
        // (1,0,0) -> mirror (-1,0,0) -> scale (-2,0,0) -> Rz(90) (0,-2,0) -> offset (10,18,30).
        // Mirroring reverses the winding, so the first stored corner is the original last one.
        float[] c = mesh.corners();
        int stride = CosmeticDefinition.Mesh.FLOATS_PER_CORNER;
        assertArrayEquals(new float[]{10, 18, 30}, new float[]{c[2 * stride], c[2 * stride + 1], c[2 * stride + 2]}, 1.0e-4f);
    }

    @Test
    void mirroringKeepsFacesPointingOutwards() {
        String quad = """
                "vertices": [[4,0,0], [0,0,0], [0,4,0], [4,4,0]], "uvs": [[0,0],[0,0],[0,0],[0,0]], "faces": [[0,1,2,3]]""";
        float[] plain = slice(vertex(CosmeticBaker.bake(parse(withMesh("{" + quad + "}"))).layers()[0].mesh(), 0, 0), 5, 8);
        float[] mirrored = slice(vertex(CosmeticBaker.bake(parse(withMesh("{" + quad + ", \"mirror\": true}"))).layers()[0].mesh(), 0, 0), 5, 8);
        float[] negative = slice(vertex(CosmeticBaker.bake(parse(withMesh("{" + quad + ", \"scale\": [1, -1, 1]}"))).layers()[0].mesh(), 0, 0), 5, 8);
        // A face in the z = 0 plane keeps facing -Z under an X mirror or a Y flip.
        assertArrayEquals(new float[]{0, 0, -1}, plain, EPS);
        assertArrayEquals(new float[]{0, 0, -1}, mirrored, EPS);
        assertArrayEquals(new float[]{0, 0, -1}, negative, EPS);
    }

    // ------------------------------------------------------------------ smooth normals

    @Test
    void smoothNormalsAreAreaWeightedPerSharedVertex() {
        // A big face facing -Z and a small face facing -X share vertex 0 (by index).
        CosmeticDefinition.Mesh mesh = onlyMesh(parse(withMesh("""
                {"shading": "smooth",
                 "vertices": [[0,0,0], [4,0,0], [4,4,0], [0,0,1], [0,1,0]],
                 "uvs": [[0,0],[0,0],[0,0],[0,0],[0,0]],
                 "faces": [[0,2,1], [0,3,4]]}
                """)));
        float[] n0 = normalOf(mesh, 0, 0);
        // Areas 8 and 0.5: the averaged normal leans 16:1 towards the big face.
        float expectedZ = -16f / (float) Math.sqrt(16f * 16f + 1f);
        float expectedX = -1f / (float) Math.sqrt(16f * 16f + 1f);
        assertArrayEquals(new float[]{expectedX, 0, expectedZ}, n0, 1.0e-4f);
        // A corner only on the big face keeps that face's normal.
        assertArrayEquals(new float[]{0, 0, -1}, normalOf(mesh, 0, 1), EPS);
    }

    @Test
    void smoothNormalsFallBackToTheFaceWhenTheyCancelOut() {
        // Two faces back to back sharing all three vertices: the sums are zero.
        CosmeticDefinition.Mesh mesh = onlyMesh(parse(withMesh("""
                {"shading": "smooth", "vertices": [[0,0,0], [4,0,0], [0,4,0]], "uvs": [[0,0],[0,0],[0,0]],
                 "faces": [[0,1,2], [2,1,0]]}
                """)));
        assertArrayEquals(new float[]{0, 0, 1}, normalOf(mesh, 0, 0), EPS);
        assertArrayEquals(new float[]{0, 0, -1}, normalOf(mesh, 1, 0), EPS);
    }

    @Test
    void smoothShadingUsesGivenNormalsRotatedAndNormalized() {
        CosmeticDefinition.Mesh mesh = onlyMesh(parse(withMesh("""
                {"shading": "smooth", "rotation": [0, 90, 0],
                 "vertices": [[0,0,0], [4,0,0], [0,4,0]], "uvs": [[0,0],[0,0],[0,0]],
                 "normals": [[2,0,0], [2,0,0], [2,0,0]], "faces": [[0,1,2]]}
                """)));
        // +X rotated 90 degrees around Y is -Z.
        assertArrayEquals(new float[]{0, 0, -1}, normalOf(mesh, 0, 0), EPS);
    }

    @Test
    void zeroLengthNormalsAreWorkedOutFromTheFaces() {
        // Exporters write (0, 0, 0) for corners they couldn't give a direction (Badlion's backpacks have some).
        CosmeticDefinition.Mesh mesh = onlyMesh(parse(withMesh("""
                {"shading": "smooth", "vertices": [[0,0,0],[4,0,0],[0,4,0]], "uvs": [[0,0],[0,0],[0,0]],
                 "normals": [[0,0,0],[1,0,0],[0,1,0]], "faces": [[0,1,2]]}
                """)));
        assertArrayEquals(new float[]{0, 0, 1}, normalOf(mesh, 0, 0), EPS);
        assertArrayEquals(new float[]{1, 0, 0}, normalOf(mesh, 0, 1), EPS);
        assertArrayEquals(new float[]{0, 1, 0}, normalOf(mesh, 0, 2), EPS);
        assertEquals(1, warnings.size(), warnings.toString());
        assertTrue(warnings.get(0).contains("1 normal(s) have zero length"), warnings.get(0));
    }

    @Test
    void smoothMeshesUseThePerVertexBuffer() {
        BakedCosmetic.Layer layer = CosmeticBaker.bake(parse(withMesh("""
                {"shading": "smooth", "vertices": [[0,0,0],[4,0,0],[0,4,0]], "uvs": [[0,0],[0,0],[0,0]],
                 "normals": [[0,0,1],[1,0,0],[0,1,0]], "faces": [[0,1,2]]}
                """))).layers()[0];
        assertTrue(layer.mesh().isEmpty());
        BakedMesh smooth = layer.smoothMesh();
        assertArrayEquals(new float[]{0, 0, 1}, slice(vertex(smooth, 0, 0), 5, 8), EPS);
        assertArrayEquals(new float[]{1, 0, 0}, slice(vertex(smooth, 0, 1), 5, 8), EPS);
        assertArrayEquals(new float[]{0, 1, 0}, slice(vertex(smooth, 0, 2), 5, 8), EPS);
        assertArrayEquals(new float[]{0, 1, 0}, slice(vertex(smooth, 0, 3), 5, 8), EPS);
    }

    // ------------------------------------------------------------------ OBJ

    private static final String OBJ_CUBE_CORNER = """
            # a square, a triangle and a pentagon
            o test
            v 0 0 0
            v 4 0 0
            v 4 4 0
            v 0 4 0
            v 8 0 0
            vt 0 1
            vt 1 1
            vt 1 0
            vt 0 0
            vn 0 0 -1
            s off
            usemtl whatever
            f 4/4 3/3 2/2 1/1
            f -4/-3 -1/-1 -3/-2
            f 1/1 2/2 5/2 3/3 4/4
            """;

    @Test
    void objReadsQuadsTrianglesPolygonsAndNegativeIndices() {
        CosmeticDefinition.Mesh mesh = onlyMesh(parse(withMesh("{\"model\": \"test:models/cosmetic/a.obj\"}"),
                Map.of("test:models/cosmetic/a.obj", OBJ_CUBE_CORNER)));
        // Quad, triangle, then the pentagon fanned into 3 triangles, of which the first (1, 2, 5) lies on
        // a line and is dropped.
        assertArrayEquals(new int[]{4, 3, 3, 3}, mesh.faceSizes());
        assertEquals(2 + 1 + 2, mesh.triangles());
        assertEquals(1, warnings.size(), warnings.toString());
        assertTrue(warnings.get(0).contains("a.obj:17"), warnings.get(0));
        // UVs: 0..1 with V up become pixels with V down (texture 16x16): vt "0 0" is (0, 16).
        float[] c = mesh.corners();
        assertArrayEquals(new float[]{0, 4, 0, 0, 16}, slice(c, 0, 5), EPS);
        // Negative indices count back: -4 is vertex 2 (4,0,0) at this point, -3 is uv 2.
        int second = 4 * CosmeticDefinition.Mesh.FLOATS_PER_CORNER;
        assertArrayEquals(new float[]{4, 0, 0, 16, 0}, slice(c, second, second + 5), EPS);
    }

    @Test
    void objNormalsAreUsedForSmoothShadingWhenEveryFaceHasThem() {
        String obj = """
                v 0 0 0
                v 4 0 0
                v 0 4 0
                vt 0 0
                vn 1 0 0
                f 1/1/1 3/1/1 2/1/1
                """;
        CosmeticDefinition.Mesh mesh = onlyMesh(parse(withMesh("{\"model\": \"test:models/cosmetic/n.obj\", \"shading\": \"smooth\"}"),
                Map.of("test:models/cosmetic/n.obj", obj)));
        assertArrayEquals(new float[]{1, 0, 0}, normalOf(mesh, 0, 0), EPS);
        // Flat shading ignores file normals.
        CosmeticDefinition.Mesh flat = onlyMesh(parse(withMesh("{\"model\": \"test:models/cosmetic/n.obj\"}"),
                Map.of("test:models/cosmetic/n.obj", obj)));
        assertArrayEquals(new float[]{0, 0, -1}, normalOf(flat, 0, 0), EPS);
    }

    @Test
    void uvsRoundedJustOutsideTheTextureArePulledOntoIt() {
        // As Badlion's cat pets' exporter wrote them: -0.00245 and 1.004.
        String obj = "v 0 0 0\nv 1 0 0\nv 0 1 0\nvt -0.00245 1.0\nvt 1.004 0\nvt 0 0\nf 1/1 2/2 3/3\n";
        CosmeticDefinition.Mesh mesh = onlyMesh(parse(withMesh("{\"model\": \"test:models/cosmetic/n.obj\"}"),
                Map.of("test:models/cosmetic/n.obj", obj)));
        float[] corners = mesh.corners();
        for (int i = 0; i < corners.length; i += CosmeticDefinition.Mesh.FLOATS_PER_CORNER) {
            assertTrue(corners[i + 3] >= 0f && corners[i + 3] <= 16f, "u " + corners[i + 3]);
            assertTrue(corners[i + 4] >= 0f && corners[i + 4] <= 16f, "v " + corners[i + 4]);
        }
    }

    @Test
    void objErrorsNameTheFileAndLine() {
        assertMessage("n.obj:3", "v 0 0 0\nvt 0 0\nf 1/1 2/1 3/1\n");                 // index out of range
        assertMessage("n.obj:4", "v 0 0 0\nv 1 0 0\nv 0 1 0\nf 1 2 3\n");             // no texture coordinate
        assertMessage("n.obj:1", "v 0 zero 0\n");                                     // not a number
        assertMessage("n.obj:2", "v 0 0 0\nvt 0 2\n");                                // uv outside 0..1
        assertMessage("n.obj:3", "v 0 0 0\nvt 0 0\nf 0/1 1/1 1/1\n");                 // index 0
        assertMessage("n.obj:3", "v 0 0 0\nvt 0 0\nf 1/1 1/1\n");                     // too few vertices
        assertMessage("n.obj", "v 0 0 0\n");                                          // no faces
    }

    private void assertMessage(String expected, String obj) {
        RuntimeException e = assertThrows(RuntimeException.class, () -> parse(withMesh("{\"model\": \"test:models/cosmetic/n.obj\"}"),
                Map.of("test:models/cosmetic/n.obj", obj)));
        assertTrue(e.getMessage().contains(expected), e.getMessage());
    }

    // ------------------------------------------------------------------ Blockbench

    @Test
    void bbmodelMeshesConvertLikeBlockbenchsJavaExport() {
        String bbmodel = """
                {"meta": {"format_version": "4.10", "model_format": "free"},
                 "resolution": {"width": 32, "height": 32},
                 "elements": [
                   {"type": "cube", "name": "ignored", "from": [0,0,0], "to": [1,1,1], "uuid": "c1"},
                   {"type": "mesh", "name": "plate", "uuid": "m1", "origin": [1, 30, 0], "rotation": [0, 0, 0],
                    "vertices": {"a": [0,0,0], "b": [2,0,0], "c": [2,0,2], "d": [0,0,2]},
                    "faces": {
                      "under": {"vertices": ["a", "c", "b", "d"], "uv": {"a": [0,0], "b": [32,0], "c": [32,32], "d": [0,32]}, "texture": 0},
                      "hidden": {"vertices": ["a", "b", "c"], "uv": {"a": [0,0], "b": [0,0], "c": [0,0]}, "texture": null}}}],
                 "outliner": ["m1"]}
                """;
        CosmeticDefinition.Mesh mesh = onlyMesh(parse(withMesh("{\"model\": \"test:models/cosmetic/p.bbmodel\"}"),
                Map.of("test:models/cosmetic/p.bbmodel", bbmodel)));
        assertEquals(1, mesh.faceSizes().length);              // the null-texture face is hidden
        assertTrue(warnings.stream().anyMatch(w -> w.contains("1 non-mesh element")), warnings.toString());
        float[] c = mesh.corners();
        int stride = CosmeticDefinition.Mesh.FLOATS_PER_CORNER;
        // Blockbench's corner order for the quad is a, b, c, d; internal (1,30,0) becomes (-1, -6, 0).
        assertArrayEquals(new float[]{-1, -6, 0}, slice(c, 0, 3), EPS);
        assertArrayEquals(new float[]{-3, -6, 0}, slice(c, stride, stride + 3), EPS);
        // UVs are in project pixels (32x32) and become texture pixels (16x16).
        assertArrayEquals(new float[]{16, 0}, slice(c, stride + 3, stride + 5), EPS);
        // Blockbench draws the side its right-hand normal points to: (b - a) x (c - a) is down (-Y up
        // there), which is +Y for us.
        assertArrayEquals(new float[]{0, 1, 0}, normalOf(mesh, 0, 0), EPS);
    }

    @Test
    void bbmodelGroupsRotateTheirMeshes() {
        String bbmodel = """
                {"resolution": {"width": 16, "height": 16},
                 "groups": [{"uuid": "g1", "name": "spin", "origin": [0, 24, 0], "rotation": [0, 90, 0]}],
                 "elements": [{"type": "mesh", "uuid": "m1", "origin": [0, 24, 0],
                   "vertices": {"a": [1,0,0], "b": [1,1,0], "c": [1,0,1]},
                   "faces": {"f": {"vertices": ["a", "b", "c"], "uv": {"a": [0,0], "b": [0,0], "c": [0,0]}, "texture": 0}}}],
                 "outliner": [{"uuid": "g1", "children": ["m1"]}]}
                """;
        CosmeticDefinition.Mesh mesh = onlyMesh(parse(withMesh("{\"model\": \"test:models/cosmetic/g.bbmodel\"}"),
                Map.of("test:models/cosmetic/g.bbmodel", bbmodel)));
        // Internal (1,24,0) turned 90 degrees around Y is (0,24,-1); ours is (-0, 0, -1).
        assertArrayEquals(new float[]{0, 0, -1}, slice(mesh.corners(), 0, 3), 1.0e-4f);
    }

    @Test
    void bbmodelQuadCornersAreSortedTheBlockbenchWay() {
        Map<String, Integer> ids = Map.of("a", 0, "b", 1, "c", 2, "d", 3);
        List<float[]> square = List.of(new float[]{0, 0, 0}, new float[]{1, 0, 0}, new float[]{0, 1, 0}, new float[]{1, 1, 0});
        // a, b, c, d given as a "Z" order: the loop is a, b, d, c.
        List<String> sorted = BbmodelParser.sortQuad(List.of("a", "b", "c", "d"), ids, square);
        assertEquals(List.of("c", "a", "b", "d"), sorted);
        // An order that already goes around stays as it is.
        assertEquals(List.of("a", "b", "d", "c"), BbmodelParser.sortQuad(List.of("a", "b", "d", "c"), ids, square));
    }

    // ------------------------------------------------------------------ docs/COSMETIC_FORMAT.md

    @Test
    void theDocumentedInlineExampleFacesTheFront() {
        CosmeticDefinition.Mesh mesh = onlyMesh(parse(withMesh("""
                {"vertices": [[-2, -10, 0], [2, -10, 0], [2, -14, 0], [-2, -14, 0], [0, -16, 0]],
                 "uvs":      [[0, 8], [4, 8], [4, 4], [0, 4], [2, 2]],
                 "faces":    [[0, 1, 2, 3], [3, 2, 4]]}
                """)));
        assertArrayEquals(new int[]{4, 3}, mesh.faceSizes());
        assertArrayEquals(new float[]{0, 0, -1}, normalOf(mesh, 0, 0), EPS);
        assertArrayEquals(new float[]{0, 0, -1}, normalOf(mesh, 1, 0), EPS);
        assertTrue(warnings.isEmpty(), warnings.toString());
    }

    @Test
    void theDocumentedBlenderPlacementPutsTheNeckOnTheAnchor() {
        // A Y-up triangle in blocks around the neck (1.5 up), facing the front (-Z) in Blender's export.
        String obj = """
                v 0.25 1.5 0
                v -0.25 1.5 0
                v 0 1.75 0
                vt 0 0
                f 1/1 2/1 3/1
                """;
        CosmeticDefinition.Mesh mesh = onlyMesh(parse(withMesh(
                "{\"model\": \"test:models/cosmetic/b.obj\", \"scale\": [16, -16, 16], \"offset\": [0, 24, 0]}"),
                Map.of("test:models/cosmetic/b.obj", obj)));
        float[] c = mesh.corners();
        int stride = CosmeticDefinition.Mesh.FLOATS_PER_CORNER;
        // The Y flip reverses the winding back, so the corners come out in reverse order.
        assertArrayEquals(new float[]{0, -4, 0}, slice(c, 0, 3), EPS);                  // 0.25 above the neck
        assertArrayEquals(new float[]{-4, 0, 0}, slice(c, stride, stride + 3), EPS);
        assertArrayEquals(new float[]{4, 0, 0}, slice(c, 2 * stride, 2 * stride + 3), EPS);
        assertArrayEquals(new float[]{0, 0, -1}, normalOf(mesh, 0, 0), EPS);           // still facing the front
    }

    // ------------------------------------------------------------------ validation

    @Test
    void everyMistakeIsAClearError() {
        String tri = "\"vertices\": [[0,0,0],[1,0,0],[0,1,0]], \"uvs\": [[0,0],[1,0],[0,1]]";
        assertError("out of range", withMesh("{" + tri + ", \"faces\": [[0,1,3]]}"));
        assertError("non-finite", withMesh("{\"vertices\": [[0,0,0],[1,0,0],[NaN,1,0]], \"uvs\": [[0,0],[1,0],[0,1]], \"faces\": [[0,1,2]]}"));
        assertError("one entry per vertex", withMesh("{\"vertices\": [[0,0,0],[1,0,0],[0,1,0]], \"uvs\": [[0,0]], \"faces\": [[0,1,2]]}"));
        assertError("one entry per vertex", withMesh("{" + tri + ", \"normals\": [[0,0,1]], \"faces\": [[0,1,2]]}"));
        assertError("3 or 4", withMesh("{" + tri + ", \"faces\": [[0,1]]}"));
        assertError("3 or 4", withMesh("{" + tri + ", \"faces\": [[0,1,2,0,1]]}"));
        assertError("whole number", withMesh("{" + tri + ", \"faces\": [[0,1,1.5]]}"));
        assertError("outside the 16x16 texture", withMesh("{\"vertices\": [[0,0,0],[1,0,0],[0,1,0]], \"uvs\": [[0,0],[17,0],[0,1]], \"faces\": [[0,1,2]]}"));
        assertError("shading", withMesh("{" + tri + ", \"faces\": [[0,1,2]], \"shading\": \"glossy\"}"));
        assertError("scale", withMesh("{" + tri + ", \"faces\": [[0,1,2]], \"scale\": 0}"));
        assertError("missing.obj", withMesh("{\"model\": \"test:models/cosmetic/missing.obj\"}"));
        assertError(".obj, .md5mesh or .bbmodel", withMesh("{\"model\": \"test:models/cosmetic/a.fbx\"}"));
        assertError("not both", withMesh("{\"model\": \"test:models/cosmetic/a.obj\", " + tri + ", \"faces\": [[0,1,2]]}"));
        assertError("'cubes', 'meshes' or both", """
                {"slot": "head", "texture": "test:t.png", "parts": [{"name": "empty"}]}
                """);
    }

    private void assertError(String expected, String json) {
        RuntimeException e = assertThrows(RuntimeException.class, () -> parse(json));
        assertTrue(e.getMessage().contains(expected), e.getMessage());
    }

    @Test
    void zeroAreaFacesAreDroppedWithAWarning() {
        CosmeticDefinition.Mesh mesh = onlyMesh(parse(withMesh("""
                {"vertices": [[0,0,0],[1,0,0],[0,1,0],[2,0,0]], "uvs": [[0,0],[1,0],[0,1],[2,0]],
                 "faces": [[0,1,2], [0,1,3]]}
                """)));
        assertEquals(1, mesh.faceSizes().length);
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("faces[1]"), warnings.get(0));
    }

    @Test
    void filesOverTheSizeLimitAreRefused() {
        String huge = "#" + "x".repeat(CosmeticParser.MAX_OBJ_BYTES) + "\n";
        RuntimeException e = assertThrows(RuntimeException.class, () -> parse(withMesh("{\"model\": \"test:models/cosmetic/big.obj\"}"),
                Map.of("test:models/cosmetic/big.obj", huge)));
        assertTrue(e.getMessage().contains("larger than 16 MiB"), e.getMessage());
    }

    @Test
    void tooManyTrianglesIsAnError() {
        // 600 separate unit squares: 1200 triangles.
        StringBuilder vertices = new StringBuilder();
        StringBuilder uvs = new StringBuilder();
        StringBuilder faces = new StringBuilder();
        for (int i = 0; i < 600; i++) {
            String sep = i == 0 ? "" : ",";
            vertices.append(sep).append("[%d,0,0],[%d,0,0],[%d,1,0],[%d,1,0]".formatted(i, i + 1, i + 1, i));
            uvs.append(sep).append("[0,0],[0,0],[0,0],[0,0]");
            int b = i * 4;
            faces.append(sep).append("[%d,%d,%d,%d]".formatted(b + 3, b + 2, b + 1, b));
        }
        String json = withMesh("{\"vertices\": [" + vertices + "], \"uvs\": [" + uvs + "], \"faces\": [" + faces + "]}");
        assertEquals(1200, onlyMesh(parse(json, Map.of(), 1200)).triangles());
        RuntimeException e = assertThrows(RuntimeException.class, () -> parse(json, Map.of(), 1199));
        assertTrue(e.getMessage().contains("1200 mesh triangles"), e.getMessage());
    }

    // ------------------------------------------------------------------ helpers

    private static float[] normalOf(CosmeticDefinition.Mesh mesh, int face, int corner) {
        int first = 0;
        for (int f = 0; f < face; f++) {
            first += mesh.faceSizes()[f];
        }
        int o = (first + corner) * CosmeticDefinition.Mesh.FLOATS_PER_CORNER;
        return slice(mesh.corners(), o + 5, o + 8);
    }

    private static float[] slice(float[] values, int end) {
        return slice(values, 0, end);
    }

    private static float[] slice(float[] values, int start, int end) {
        float[] out = new float[end - start];
        System.arraycopy(values, start, out, 0, out.length);
        return out;
    }

    @Test
    void objGroupsPickFacesAndFlipVKeepsTheFilesV() {
        String obj = """
                v 0 0 0
                v 1 0 0
                v 1 1 0
                vt 0 0.25
                vt 1 0.25
                vt 1 1
                o left
                f 1/1 2/2 3/3
                o right
                f 3/3 2/2 1/1
                f 1/1 3/3 2/2
                """;
        Map<String, String> files = Map.of("test:models/w.obj", obj, "test:models/g.obj",
                obj.replace("o left", "g wing_l").replace("o right", "g wing_r"));
        assertEquals(2, onlyMesh(parse(withMesh("{\"model\": \"test:models/g.obj\", \"group\": \"wing_r\"}"), files))
                .faceSizes().length, "g groups work like o objects");
        assertEquals(1, onlyMesh(parse(withMesh("{\"model\": \"test:models/w.obj\", \"group\": \"LEFT\"}"), files))
                .faceSizes().length);
        assertEquals(2, onlyMesh(parse(withMesh("{\"model\": \"test:models/w.obj\", \"group\": \"right\"}"), files))
                .faceSizes().length);
        assertEquals(3, onlyMesh(parse(withMesh("{\"model\": \"test:models/w.obj\"}"), files)).faceSizes().length);
        JsonParseException e = assertThrows(JsonParseException.class,
                () -> parse(withMesh("{\"model\": \"test:models/w.obj\", \"group\": \"middle\"}"), files));
        assertTrue(e.getMessage().contains("left, right"), e.getMessage());

        // V 0.25 is 12 px down a 16 px texture normally, 4 px down with flip_v.
        assertEquals(12f, onlyMesh(parse(withMesh("{\"model\": \"test:models/w.obj\"}"), files)).corners()[4], EPS);
        assertEquals(4f, onlyMesh(parse(withMesh("{\"model\": \"test:models/w.obj\", \"flip_v\": true}"), files))
                .corners()[4], EPS);
    }
}
