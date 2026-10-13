package io.github.georgwav.cosmetics.model;

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

    // ------------------------------------------------------------------ inline meshes

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
        assertError("zero length", withMesh("{" + tri + ", \"faces\": [[0,1,2]], \"shading\": \"smooth\", \"normals\": [[0,0,0],[0,0,1],[0,0,1]]}"));
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

    private static float[] slice(float[] values, int start, int end) {
        float[] out = new float[end - start];
        System.arraycopy(values, start, out, 0, out.length);
        return out;
    }
}
