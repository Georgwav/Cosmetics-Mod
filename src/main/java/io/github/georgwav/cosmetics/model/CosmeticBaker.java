package io.github.georgwav.cosmetics.model;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a {@link CosmeticDefinition} into {@link BakedMesh}es. Box UVs and face winding match
 * vanilla {@code ModelPart.Cube} exactly, so textures made for vanilla-style entity models (e.g.
 * Blockbench "Modded Entity" projects with box UV) map the same way.
 *
 * <p>Pure Java with no Minecraft types, so it is unit-tested without a game instance.
 */
public final class CosmeticBaker {
    private static final float[][] FACE_NORMALS = {
            {0, -1, 0}, // DOWN
            {0, 1, 0},  // UP
            {0, 0, -1}, // NORTH
            {0, 0, 1},  // SOUTH
            {-1, 0, 0}, // WEST
            {1, 0, 0},  // EAST
    };

    private static final float[] ZERO = new float[3];

    private CosmeticBaker() {
    }

    /** Parts that share a mode, texture, movement, texture animation, tinting and wing are drawn as one layer. */
    private record LayerKey(String texture, List<CosmeticDefinition.Animation> animations,
                            CosmeticDefinition.TextureAnimation textureAnimation, boolean tinted,
                            CosmeticDefinition.WingPart wing, int bone) {
    }

    /**
     * A layer's vertex data: cubes and flat-shaded meshes share one buffer (one normal per quad), and
     * smooth-shaded meshes go in a second buffer of the same layer (one normal per vertex). Both are
     * drawn with the layer's one render type, so meshes add no draw calls.
     */
    private static final class LayerBuffers {
        final FloatList flat = new FloatList();
        FloatList smooth;
    }

    public static BakedCosmetic bake(CosmeticDefinition definition) {
        // One layer per (mode, texture, animation), in draw order: cutout, translucent, emissive.
        Map<RenderMode, Map<LayerKey, LayerBuffers>> buffers = new EnumMap<>(RenderMode.class);
        // Skeletal meshes: a layer per joint piece (it moves with its joint) and one for the skinned rest.
        Map<RenderMode, List<BakedCosmetic.Layer>> rigLayers = new EnumMap<>(RenderMode.class);
        for (CosmeticDefinition.Part part : definition.parts()) {
            LayerBuffers out = buffers.computeIfAbsent(part.mode(), m -> new LinkedHashMap<>())
                    .computeIfAbsent(new LayerKey(part.texture(), part.animations(), part.textureAnimation(), part.tinted(), part.wing(),
                            part.bone()),
                            k -> new LayerBuffers());
            // UVs address one frame; in a vertical strip that frame is 1/frames of the image's height.
            float texH = definition.textureHeight();
            if (part.textureAnimation() != null && part.textureAnimation().strip()) {
                texH *= part.textureAnimation().frames();
            }
            for (CosmeticDefinition.Cube cube : part.cubes()) {
                bakeCube(cube, definition.textureWidth(), texH, out.flat);
            }
            for (CosmeticDefinition.Mesh mesh : part.meshes()) {
                if (mesh.rigged() != null) {
                    rigLayers.computeIfAbsent(part.mode(), m -> new ArrayList<>())
                            .addAll(bakeRig(part, mesh, definition.textureWidth(), texH));
                    continue;
                }
                FloatList target = out.flat;
                if (mesh.smooth()) {
                    if (out.smooth == null) {
                        out.smooth = new FloatList();
                    }
                    target = out.smooth;
                }
                bakeMesh(mesh, definition.textureWidth(), texH, target);
            }
        }
        List<BakedCosmetic.Layer> layers = new ArrayList<>();
        for (RenderMode mode : RenderMode.values()) {
            buffers.getOrDefault(mode, Map.of()).forEach((key, lists) -> {
                BakedMesh mesh = new BakedMesh(lists.flat.toArray());
                BakedMesh smooth = lists.smooth != null ? new BakedMesh(lists.smooth.toArray()) : null;
                if (smooth != null && smooth.isEmpty()) {
                    smooth = null;
                }
                if (!mesh.isEmpty() || smooth != null) {
                    TextureFrames frames = key.textureAnimation() != null ? new TextureFrames(key.textureAnimation()) : null;
                    layers.add(new BakedCosmetic.Layer(mode, key.texture(), mesh,
                            key.animations().toArray(new CosmeticDefinition.Animation[0]), smooth, frames, key.tinted(),
                            null, -1, null, key.wing(), key.bone()));
                }
            });
            layers.addAll(rigLayers.getOrDefault(mode, List.of()));
        }
        String[] styles = CosmeticParser.styles(definition.parts()).toArray(new String[0]);
        for (BakedCosmetic.Layer layer : layers) {
            if (layer.rig() != null) {
                layer.rig().bindStyles(styles);
            }
        }
        return new BakedCosmetic(definition, layers, styles);
    }

    /**
     * Layers of one skeletal mesh. Positions stay in the mesh file's units (the rig's placement and joint
     * transforms are applied on the pose stack when drawing); UVs go to 0..1.
     */
    private static List<BakedCosmetic.Layer> bakeRig(CosmeticDefinition.Part part, CosmeticDefinition.Mesh mesh,
                                                     float texW, float texH) {
        CosmeticDefinition.Rigged rigged = mesh.rigged();
        CosmeticDefinition.Animation[] animations = part.animations().toArray(new CosmeticDefinition.Animation[0]);
        BakedMesh empty = new BakedMesh(new float[0]);
        int stride = CosmeticDefinition.Mesh.FLOATS_PER_CORNER;
        List<BakedCosmetic.Layer> layers = new ArrayList<>();
        for (CosmeticDefinition.RigidPiece piece : rigged.pieces()) {
            float[] c = piece.corners();
            FloatList out = new FloatList();
            for (int t = 0; t < piece.triangles(); t++) {
                for (int i = 0; i < BakedMesh.VERTICES_PER_QUAD; i++) {
                    int o = (t * 3 + Math.min(i, 2)) * stride;
                    out.add(c[o]);
                    out.add(c[o + 1]);
                    out.add(c[o + 2]);
                    out.add(c[o + 3] / texW);
                    out.add(c[o + 4] / texH);
                    out.add(c[o + 5]);
                    out.add(c[o + 6]);
                    out.add(c[o + 7]);
                }
            }
            BakedMesh baked = new BakedMesh(out.toArray());
            layers.add(new BakedCosmetic.Layer(part.mode(), part.texture(), mesh.smooth() ? empty : baked, animations,
                    mesh.smooth() ? baked : null, frames(part), part.tinted(), rigged.rig(), piece.joint(), null,
                    part.wing(), -1));
        }
        CosmeticDefinition.SkinnedPiece skinned = rigged.skinned();
        if (skinned != null) {
            int triangles = skinned.triangles().length / 3;
            int[] slotVertex = new int[triangles * BakedMesh.VERTICES_PER_QUAD];
            float[] data = new float[slotVertex.length * BakedMesh.FLOATS_PER_VERTEX];
            for (int t = 0; t < triangles; t++) {
                for (int i = 0; i < BakedMesh.VERTICES_PER_QUAD; i++) {
                    int slot = t * BakedMesh.VERTICES_PER_QUAD + i;
                    int v = skinned.triangles()[t * 3 + Math.min(i, 2)];
                    slotVertex[slot] = v;
                    int o = slot * BakedMesh.FLOATS_PER_VERTEX;
                    data[o + 3] = skinned.uvs()[v * 2] / texW;
                    data[o + 4] = skinned.uvs()[v * 2 + 1] / texH;
                    data[o + 6] = 1f;
                }
            }
            SkinnedMesh skin = new SkinnedMesh(rigged.rig(), skinned.weightStart(), skinned.weightCount(),
                    skinned.weightJoint(), skinned.weightBias(), skinned.weightPos(), skinned.weightNormal(), slotVertex,
                    new BakedMesh(data));
            layers.add(new BakedCosmetic.Layer(part.mode(), part.texture(), empty, animations, null, frames(part),
                    part.tinted(), rigged.rig(), -1, skin, part.wing(), -1));
        }
        return layers;
    }

    private static TextureFrames frames(CosmeticDefinition.Part part) {
        return part.textureAnimation() != null ? new TextureFrames(part.textureAnimation()) : null;
    }

    /**
     * Copies a mesh into quads: a quad as it is, a triangle as a quad whose last vertex repeats (the
     * vanilla entity render types draw quads). Positions to blocks, UVs to 0..1.
     */
    static void bakeMesh(CosmeticDefinition.Mesh mesh, float texW, float texH, FloatList out) {
        float[] c = mesh.corners();
        int first = 0;
        for (int size : mesh.faceSizes()) {
            for (int i = 0; i < BakedMesh.VERTICES_PER_QUAD; i++) {
                int o = (first + Math.min(i, size - 1)) * CosmeticDefinition.Mesh.FLOATS_PER_CORNER;
                out.add(c[o] / 16f);
                out.add(c[o + 1] / 16f);
                out.add(c[o + 2] / 16f);
                out.add(c[o + 3] / texW);
                out.add(c[o + 4] / texH);
                out.add(c[o + 5]);
                out.add(c[o + 6]);
                out.add(c[o + 7]);
            }
            first += size;
        }
    }

    static void bakeCube(CosmeticDefinition.Cube cube, float texW, float texH, FloatList out) {
        float minX = cube.from()[0] - cube.inflate();
        float minY = cube.from()[1] - cube.inflate();
        float minZ = cube.from()[2] - cube.inflate();
        float maxX = cube.to()[0] + cube.inflate();
        float maxY = cube.to()[1] + cube.inflate();
        float maxZ = cube.to()[2] + cube.inflate();
        if (cube.mirror()) {
            float tmp = maxX;
            maxX = minX;
            minX = tmp;
        }

        // Corner numbering matches vanilla ModelPart.Cube.
        float[][] c = {
                {minX, minY, minZ}, {maxX, minY, minZ}, {maxX, maxY, minZ}, {minX, maxY, minZ},
                {minX, minY, maxZ}, {maxX, minY, maxZ}, {maxX, maxY, maxZ}, {minX, maxY, maxZ},
        };
        // Vertex order per face, indexed like CosmeticDefinition.Face.
        int[][] faceCorners = {
                {5, 4, 0, 1}, // DOWN
                {2, 3, 7, 6}, // UP
                {1, 0, 3, 2}, // NORTH
                {4, 5, 6, 7}, // SOUTH
                {0, 4, 7, 3}, // WEST
                {5, 1, 2, 6}, // EAST
        };

        float[][] uvs = cube.boxUv() != null ? boxUv(cube) : cube.faces();
        // p' = m (p - before) + after. Hand-written cubes keep exactly the original arithmetic
        // (rotation around the pivot); imported cubes use their full transform.
        float[] m;
        float[] before;
        float[] after;
        if (cube.transform() == null) {
            m = rotationMatrix(cube.rotation());
            before = cube.pivot();
            after = cube.pivot();
        } else {
            m = cube.transform();
            before = ZERO;
            after = new float[]{m[9], m[10], m[11]};
        }

        for (int face = 0; face < 6; face++) {
            float[] uv = uvs[face];
            if (uv == null) {
                continue;
            }
            float u1 = uv[0] / texW;
            float v1 = uv[1] / texH;
            float u2 = uv[2] / texW;
            float v2 = uv[3] / texH;
            // Same UV assignment as vanilla ModelPart.Polygon.
            float[][] quadUv = {{u2, v1}, {u1, v1}, {u1, v2}, {u2, v2}};
            int[] order = {0, 1, 2, 3};
            float[] normal = FACE_NORMALS[face].clone();
            if (cube.mirror()) {
                order = new int[]{3, 2, 1, 0};
                normal[0] = -normal[0];
            }
            float[] n = rotate(m, normal[0], normal[1], normal[2]);
            for (int i : order) {
                float[] p = c[faceCorners[face][i]];
                float[] r = rotate(m, p[0] - before[0], p[1] - before[1], p[2] - before[2]);
                out.add((r[0] + after[0]) / 16f);
                out.add((r[1] + after[1]) / 16f);
                out.add((r[2] + after[2]) / 16f);
                out.add(quadUv[i][0]);
                out.add(quadUv[i][1]);
                out.add(n[0]);
                out.add(n[1]);
                out.add(n[2]);
            }
        }
    }

    /** A cube's UVs in texture pixels, indexed like {@link CosmeticDefinition.Face} (null for a face that isn't drawn). */
    public static float[][] faceUvs(CosmeticDefinition.Cube cube) {
        return cube.boxUv() != null ? boxUv(cube) : cube.faces() != null ? cube.faces() : new float[6][];
    }

    /** Vanilla box UV layout, in pixels, indexed like {@link CosmeticDefinition.Face}. */
    private static float[][] boxUv(CosmeticDefinition.Cube cube) {
        float dx = cube.to()[0] - cube.from()[0];
        float dy = cube.to()[1] - cube.from()[1];
        float dz = cube.to()[2] - cube.from()[2];
        float u = cube.boxUv()[0];
        float v = cube.boxUv()[1];
        float u0 = u;
        float u1 = u + dz;
        float u2 = u + dz + dx;
        float u3 = u + dz + dx + dx;
        float u4 = u + dz + dx + dz;
        float u5 = u + dz + dx + dz + dx;
        float v0 = v;
        float v1 = v + dz;
        float v2 = v + dz + dy;
        return new float[][]{
                {u1, v0, u2, v1}, // DOWN
                {u2, v1, u3, v0}, // UP
                {u1, v1, u2, v2}, // NORTH
                {u4, v1, u5, v2}, // SOUTH
                {u0, v1, u1, v2}, // WEST
                {u2, v1, u4, v2}, // EAST
        };
    }

    /**
     * Row-major 3x3 rotation for Euler angles in degrees, applied in vanilla's order
     * ({@code rotationZYX}: X first, then Y, then Z).
     */
    static float[] rotationMatrix(float[] degrees) {
        double x = Math.toRadians(degrees[0]);
        double y = Math.toRadians(degrees[1]);
        double z = Math.toRadians(degrees[2]);
        double cx = Math.cos(x), sx = Math.sin(x);
        double cy = Math.cos(y), sy = Math.sin(y);
        double cz = Math.cos(z), sz = Math.sin(z);
        // Rz * Ry * Rx
        return new float[]{
                (float) (cz * cy), (float) (cz * sy * sx - sz * cx), (float) (cz * sy * cx + sz * sx),
                (float) (sz * cy), (float) (sz * sy * sx + cz * cx), (float) (sz * sy * cx - cz * sx),
                (float) (-sy), (float) (cy * sx), (float) (cy * cx),
        };
    }

    /** Rotation {@code m} around {@code pivot} as {@code [m00..m22, tx, ty, tz]}: p' = m p + t. */
    static float[] affine(float[] m, float[] pivot) {
        float[] r = rotate(m, pivot[0], pivot[1], pivot[2]);
        float[] a = new float[12];
        System.arraycopy(m, 0, a, 0, 9);
        a[9] = pivot[0] - r[0];
        a[10] = pivot[1] - r[1];
        a[11] = pivot[2] - r[2];
        return a;
    }

    /** {@code outer} applied after {@code inner}, both {@code [m00..m22, tx, ty, tz]}. */
    static float[] compose(float[] outer, float[] inner) {
        float[] a = new float[12];
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                a[row * 3 + col] = outer[row * 3] * inner[col] + outer[row * 3 + 1] * inner[3 + col]
                        + outer[row * 3 + 2] * inner[6 + col];
            }
        }
        float[] t = rotate(outer, inner[9], inner[10], inner[11]);
        a[9] = t[0] + outer[9];
        a[10] = t[1] + outer[10];
        a[11] = t[2] + outer[11];
        return a;
    }

    /** Multiplies the 3x3 part of {@code m} (the first 9 entries, row-major) with a vector. */
    static float[] rotate(float[] m, float x, float y, float z) {
        return new float[]{
                m[0] * x + m[1] * y + m[2] * z,
                m[3] * x + m[4] * y + m[5] * z,
                m[6] * x + m[7] * y + m[8] * z,
        };
    }

    /** Minimal growable float array, to avoid boxing while baking. */
    static final class FloatList {
        private float[] data = new float[256];
        private int size;

        void add(float f) {
            if (size == data.length) {
                float[] grown = new float[data.length * 2];
                System.arraycopy(data, 0, grown, 0, size);
                data = grown;
            }
            data[size++] = f;
        }

        float[] toArray() {
            float[] result = new float[size];
            System.arraycopy(data, 0, result, 0, size);
            return result;
        }
    }
}
