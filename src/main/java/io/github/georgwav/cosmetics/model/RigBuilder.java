package io.github.georgwav.cosmetics.model;

import com.google.gson.JsonParseException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * Turns a parsed {@code .md5mesh} (and optionally its {@code .md5anim}) into cosmetic geometry, once, at
 * load time.
 * <ul>
 *   <li>Without an animation the bind pose is baked into an ordinary still mesh: it costs exactly what an
 *       OBJ with the same triangles costs.</li>
 *   <li>With an animation, every triangle whose corners all follow the same single joint becomes part of
 *       that joint's rigid piece, kept in the joint's own space: drawing it is one joint transform on the
 *       pose stack and the normal emit path. Only the remaining triangles (corners weighted to several
 *       joints, or to different joints) are skinned on the CPU, by {@link SkinnedMesh}.</li>
 * </ul>
 *
 * <p>Pure Java with no Minecraft types, so it is unit-tested without a game instance.
 */
final class RigBuilder {
    /** A weight at least this strong carries a vertex alone. */
    static final float SINGLE_WEIGHT = 0.999f;
    /** Weights weaker than this are ignored when deciding whether a vertex follows one joint. */
    static final float NEGLIGIBLE_WEIGHT = 1.0e-4f;

    /**
     * How far (as a share of the texture) a model's texture coordinate may stray outside the texture, rounded there by
     * its exporter, before it is an error (a texture meant to repeat, which isn't supported).
     */
    static final float UV_SLACK = 0.01f;

    private RigBuilder() {
    }

    /** Which way the file's triangles turn when seen from outside. */
    enum Winding {
        /** Closed meshes: worked out from the enclosed volume. Open ones: clockwise, the id Tech convention. */
        AUTO,
        CLOCKWISE,
        COUNTER_CLOCKWISE;

        static Winding byId(String id, String where) {
            return switch (id.toLowerCase(Locale.ROOT)) {
                case "auto" -> AUTO;
                case "cw", "clockwise" -> CLOCKWISE;
                case "ccw", "counter_clockwise", "counterclockwise" -> COUNTER_CLOCKWISE;
                default -> throw new JsonParseException(where + ": winding must be auto, cw or ccw, not '" + id + "'");
            };
        }
    }

    /**
     * @param submesh which {@code mesh} blocks to use: a block's shader name (or the last part of it), its
     *                index, or null for all
     * @param flipV   texture V runs up in the file instead of down
     */
    record Options(String submesh, boolean flipV, Winding winding, MeshBuilder.Placement placement, boolean smooth,
                   float texW, float texH) {
    }

    /** One animation to play on the mesh, by name. */
    record NamedAnim(String name, Md5Parser.AnimFile anim, boolean loop, float speed, String source) {
    }

    /** Blocks prepared for building: bind-pose positions, UVs in pixels, outward-facing triangles. */
    private record Prepared(List<Md5Parser.MeshBlock> blocks, List<float[]> positions, List<float[]> uvs,
                            List<int[]> triangles, List<float[]> normals) {
    }

    // ------------------------------------------------------------------ still

    /** The bind pose as an ordinary still mesh. */
    static CosmeticDefinition.Mesh still(Md5Parser.MeshFile file, Options options, String source,
                                         Consumer<String> warnings) {
        Prepared prepared = prepare(file, options, source, warnings);
        MeshBuilder.Raw raw = new MeshBuilder.Raw();
        Map<String, Integer> positionIds = new HashMap<>();
        for (int b = 0; b < prepared.blocks().size(); b++) {
            float[] positions = prepared.positions().get(b);
            float[] uvs = prepared.uvs().get(b);
            int first = raw.positions.size();
            for (int v = 0; v < positions.length / 3; v++) {
                float[] p = {positions[v * 3], positions[v * 3 + 1], positions[v * 3 + 2]};
                Integer id = positionIds.computeIfAbsent(p[0] + "," + p[1] + "," + p[2], k -> positionIds.size());
                raw.addCorner(p, new float[]{uvs[v * 2], uvs[v * 2 + 1]}, null, id);
            }
            int[] triangles = prepared.triangles().get(b);
            for (int t = 0; t < triangles.length; t += 3) {
                raw.faces.add(new int[]{first + triangles[t], first + triangles[t + 1], first + triangles[t + 2]});
                raw.faceSources.add(source + " mesh " + b + " triangle " + t / 3);
            }
        }
        return MeshBuilder.build(raw, options.placement(), options.smooth(), options.texW(), options.texH(), source, warnings);
    }

    // ------------------------------------------------------------------ rigged

    /** A skeletal mesh with one or more named animations; the first plays unless a style picks another. */
    static CosmeticDefinition.Mesh rigged(Md5Parser.MeshFile file, List<NamedAnim> anims, Options options,
                                          String source, Consumer<String> warnings) {
        MeshRig.Clip[] clips = new MeshRig.Clip[anims.size()];
        for (int i = 0; i < anims.size(); i++) {
            NamedAnim named = anims.get(i);
            Md5Parser.AnimFile anim = named.anim();
            if (anim.joints().size() != file.joints().size()) {
                throw new JsonParseException(named.source() + " has " + anim.joints().size() + " joints but " + source
                        + " has " + file.joints().size());
            }
            for (int j = 0; j < anim.joints().size(); j++) {
                Md5Parser.Joint a = anim.joints().get(j);
                Md5Parser.Joint m = file.joints().get(j);
                if (!a.name().equals(m.name()) || a.parent() != m.parent()) {
                    throw new JsonParseException(named.source() + ": joint " + j + " is '" + a.name() + "' (parent "
                            + a.parent() + ") but " + source + " has '" + m.name() + "' (parent " + m.parent() + ")");
                }
            }
            clips[i] = new MeshRig.Clip(named.name(), anim.frames(), anim.frameCount(), anim.frameRate(), named.loop(),
                    named.speed());
        }
        Prepared prepared = prepare(file, options, source, warnings);
        MeshBuilder.Placement placement = options.placement();
        float mirror = placement.mirror() ? -1f : 1f;
        float[] scale = {placement.scale()[0] * mirror, placement.scale()[1], placement.scale()[2]};
        for (float s : scale) {
            if (s == 0f || !Float.isFinite(s)) {
                throw new JsonParseException(source + ": scale must be finite and not 0");
            }
        }
        // The placement is applied on the pose stack when drawing, so a mirroring placement would turn the
        // faces inside out there; turn them back now.
        boolean mirrored = scale[0] * scale[1] * scale[2] < 0f;

        float[] bind = file.bind();
        float[] conjugate = new float[4];
        float[] rotated = new float[3];
        TreeMap<Integer, FloatList> pieces = new TreeMap<>();
        // Skinned vertices: key (block, vertex) when smooth, or a fresh vertex per corner when flat.
        Map<Long, Integer> skinnedIndex = new HashMap<>();
        IntList skinStart = new IntList();
        IntList skinCount = new IntList();
        IntList skinJoint = new IntList();
        FloatList skinBias = new FloatList();
        FloatList skinPos = new FloatList();
        FloatList skinNormal = new FloatList();
        FloatList skinUv = new FloatList();
        IntList skinTriangles = new IntList();
        int triangleCount = 0;

        for (int b = 0; b < prepared.blocks().size(); b++) {
            Md5Parser.MeshBlock block = prepared.blocks().get(b);
            float[] positions = prepared.positions().get(b);
            float[] uvs = prepared.uvs().get(b);
            float[] normals = prepared.normals().get(b);
            int[] triangles = prepared.triangles().get(b);
            for (int t = 0; t < triangles.length; t += 3) {
                // Normals face out in the file's own space (the pose stack carries them through the
                // placement); only the order the corners are drawn in is turned around for a mirror.
                float[] faceNormal = faceNormal(positions, new int[]{triangles[t], triangles[t + 1], triangles[t + 2]});
                int[] corners = mirrored
                        ? new int[]{triangles[t], triangles[t + 2], triangles[t + 1]}
                        : new int[]{triangles[t], triangles[t + 1], triangles[t + 2]};
                triangleCount++;
                int joint = singleJoint(block, corners[0]);
                for (int c = 1; c < 3 && joint >= 0; c++) {
                    if (singleJoint(block, corners[c]) != joint) {
                        joint = -1;
                    }
                }
                if (joint >= 0) {
                    FloatList piece = pieces.computeIfAbsent(joint, k -> new FloatList());
                    conjugate(bind, joint * MeshRig.STRIDE + 3, conjugate);
                    for (int v : corners) {
                        int w = strongestWeight(block, v);
                        float[] n = options.smooth() ? vector(normals, v) : faceNormal;
                        Md5Parser.rotate(conjugate, 0, n[0], n[1], n[2], rotated, 0);
                        piece.add(block.weightPos()[w * 3]);
                        piece.add(block.weightPos()[w * 3 + 1]);
                        piece.add(block.weightPos()[w * 3 + 2]);
                        piece.add(uvs[v * 2]);
                        piece.add(uvs[v * 2 + 1]);
                        piece.add(rotated[0]);
                        piece.add(rotated[1]);
                        piece.add(rotated[2]);
                    }
                    continue;
                }
                for (int c = 0; c < 3; c++) {
                    int v = corners[c];
                    long key = options.smooth() ? ((long) b << 32) | v : -1L - (triangleCount * 3L + c);
                    Integer index = skinnedIndex.get(key);
                    if (index == null) {
                        index = skinStart.size();
                        skinnedIndex.put(key, index);
                        float[] n = options.smooth() ? vector(normals, v) : faceNormal;
                        skinStart.add(skinJoint.size());
                        skinCount.add(block.weightCount()[v]);
                        for (int w = block.weightStart()[v], end = w + block.weightCount()[v]; w < end; w++) {
                            int j = block.weightJoint()[w];
                            conjugate(bind, j * MeshRig.STRIDE + 3, conjugate);
                            Md5Parser.rotate(conjugate, 0, n[0], n[1], n[2], rotated, 0);
                            skinJoint.add(j);
                            skinBias.add(block.weightBias()[w]);
                            skinPos.add(block.weightPos()[w * 3]);
                            skinPos.add(block.weightPos()[w * 3 + 1]);
                            skinPos.add(block.weightPos()[w * 3 + 2]);
                            skinNormal.add(rotated[0]);
                            skinNormal.add(rotated[1]);
                            skinNormal.add(rotated[2]);
                        }
                        skinUv.add(uvs[v * 2]);
                        skinUv.add(uvs[v * 2 + 1]);
                    }
                    skinTriangles.add(index);
                }
            }
        }

        List<CosmeticDefinition.RigidPiece> rigid = new ArrayList<>();
        pieces.forEach((joint, corners) -> rigid.add(new CosmeticDefinition.RigidPiece(joint, corners.toArray())));
        CosmeticDefinition.SkinnedPiece skinned = skinTriangles.size() == 0 ? null : new CosmeticDefinition.SkinnedPiece(
                skinStart.toArray(), skinCount.toArray(), skinJoint.toArray(), skinBias.toArray(), skinPos.toArray(),
                skinNormal.toArray(), skinUv.toArray(), skinTriangles.toArray());
        MeshRig rig = new MeshRig(source, file.joints().size(), clips, scale, quaternion(placement.rotation()),
                placement.pivot().clone(), placement.offset().clone());
        return new CosmeticDefinition.Mesh(source, options.smooth(), new float[0], new int[0], triangleCount,
                new CosmeticDefinition.Rigged(rig, List.copyOf(rigid), skinned));
    }

    // ------------------------------------------------------------------ shared

    private static Prepared prepare(Md5Parser.MeshFile file, Options options, String source, Consumer<String> warnings) {
        List<Md5Parser.MeshBlock> blocks = select(file.meshes(), options.submesh(), source);
        List<float[]> positions = new ArrayList<>();
        List<float[]> uvs = new ArrayList<>();
        List<int[]> triangles = new ArrayList<>();
        float[] bind = file.bind();
        float[] rotated = new float[3];
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        for (Md5Parser.MeshBlock block : blocks) {
            int n = block.vertexCount();
            float[] p = new float[n * 3];
            float[] uv = new float[n * 2];
            for (int v = 0; v < n; v++) {
                for (int w = block.weightStart()[v], end = w + block.weightCount()[v]; w < end; w++) {
                    int j = block.weightJoint()[w] * MeshRig.STRIDE;
                    Md5Parser.rotate(bind, j + 3, block.weightPos()[w * 3], block.weightPos()[w * 3 + 1],
                            block.weightPos()[w * 3 + 2], rotated, 0);
                    float bias = block.weightBias()[w];
                    p[v * 3] += (bind[j] + rotated[0]) * bias;
                    p[v * 3 + 1] += (bind[j + 1] + rotated[1]) * bias;
                    p[v * 3 + 2] += (bind[j + 2] + rotated[2]) * bias;
                }
                minX = Math.min(minX, p[v * 3]);
                minY = Math.min(minY, p[v * 3 + 1]);
                minZ = Math.min(minZ, p[v * 3 + 2]);
                maxX = Math.max(maxX, p[v * 3]);
                maxY = Math.max(maxY, p[v * 3 + 1]);
                maxZ = Math.max(maxZ, p[v * 3 + 2]);
                float u = block.uvs()[v * 2];
                float t = block.uvs()[v * 2 + 1];
                // Exporters round to just outside the texture (-0.0025, 1.0001): up to 1% out is pulled onto it.
                if (u < -UV_SLACK || u > 1f + UV_SLACK || t < -UV_SLACK || t > 1f + UV_SLACK) {
                    throw new JsonParseException(source + ": vert " + v + " has texture coordinate (" + u + ", " + t
                            + ") outside 0..1 (wrapping textures aren't supported)");
                }
                u = Math.max(0f, Math.min(1f, u));
                t = Math.max(0f, Math.min(1f, t));
                uv[v * 2] = u * options.texW();
                uv[v * 2 + 1] = (options.flipV() ? 1f - t : t) * options.texH();
            }
            positions.add(p);
            uvs.add(uv);
        }

        // Signed volume (positive when faces turn counter-clockwise seen from outside) against the bounding
        // box: a clearly signed volume means a closed mesh whose orientation it tells.
        double volume = 0;
        for (int b = 0; b < blocks.size(); b++) {
            int[] tris = blocks.get(b).triangles();
            float[] p = positions.get(b);
            for (int t = 0; t < tris.length; t += 3) {
                volume += signedVolume(p, tris[t], tris[t + 1], tris[t + 2]);
            }
        }
        double box = (double) (maxX - minX) * (maxY - minY) * (maxZ - minZ);
        boolean reverse = switch (options.winding()) {
            case CLOCKWISE -> true;
            case COUNTER_CLOCKWISE -> false;
            case AUTO -> box > 0 && Math.abs(volume) > 0.01 * box ? volume < 0 : true;
        };

        double diagonal2 = (double) (maxX - minX) * (maxX - minX) + (double) (maxY - minY) * (maxY - minY)
                + (double) (maxZ - minZ) * (maxZ - minZ);
        List<float[]> normals = new ArrayList<>();
        for (int b = 0; b < blocks.size(); b++) {
            int[] tris = blocks.get(b).triangles();
            float[] p = positions.get(b);
            IntList kept = new IntList();
            for (int t = 0; t < tris.length; t += 3) {
                int[] corners = reverse ? new int[]{tris[t], tris[t + 2], tris[t + 1]}
                        : new int[]{tris[t], tris[t + 1], tris[t + 2]};
                float[] normal = faceNormal(p, corners);
                double area = Math.sqrt((double) normal[3]) / 2;
                if (!(area > 1.0e-12 * diagonal2)) {
                    warnings.accept(source + " mesh " + b + " triangle " + t / 3 + ": no area, dropped");
                    continue;
                }
                kept.add(corners[0]);
                kept.add(corners[1]);
                kept.add(corners[2]);
            }
            triangles.add(kept.toArray());
            normals.add(options.smooth() ? smoothNormals(p, kept.toArray()) : null);
        }
        return new Prepared(blocks, positions, uvs, triangles, normals);
    }

    private static List<Md5Parser.MeshBlock> select(List<Md5Parser.MeshBlock> meshes, String submesh, String source) {
        if (submesh == null) {
            return meshes;
        }
        List<Md5Parser.MeshBlock> selected = new ArrayList<>();
        for (int i = 0; i < meshes.size(); i++) {
            String shader = meshes.get(i).shader();
            String last = shader.substring(Math.max(shader.lastIndexOf('/'), shader.lastIndexOf('\\')) + 1);
            int dot = last.lastIndexOf('.');
            String stem = dot > 0 ? last.substring(0, dot) : last;
            if (submesh.equals(String.valueOf(i)) || submesh.equalsIgnoreCase(shader) || submesh.equalsIgnoreCase(last)
                    || submesh.equalsIgnoreCase(stem)) {
                selected.add(meshes.get(i));
            }
        }
        if (selected.isEmpty()) {
            List<String> names = new ArrayList<>();
            for (int i = 0; i < meshes.size(); i++) {
                names.add(i + " ('" + meshes.get(i).shader() + "')");
            }
            throw new JsonParseException(source + ": no mesh matches submesh '" + submesh + "'; it has " + names);
        }
        return selected;
    }

    /** The joint a vertex follows alone, or -1 when it is shared between joints. */
    static int singleJoint(Md5Parser.MeshBlock block, int vertex) {
        int joint = -1;
        for (int w = block.weightStart()[vertex], end = w + block.weightCount()[vertex]; w < end; w++) {
            float bias = block.weightBias()[w];
            if (bias < NEGLIGIBLE_WEIGHT) {
                continue;
            }
            if (joint >= 0 || bias < SINGLE_WEIGHT) {
                return -1;
            }
            joint = block.weightJoint()[w];
        }
        return joint;
    }

    private static int strongestWeight(Md5Parser.MeshBlock block, int vertex) {
        int best = block.weightStart()[vertex];
        for (int w = best, end = w + block.weightCount()[vertex]; w < end; w++) {
            if (block.weightBias()[w] > block.weightBias()[best]) {
                best = w;
            }
        }
        return best;
    }

    /** Unit face normal plus its squared length before normalising (twice the area, squared) at index 3. */
    private static float[] faceNormal(float[] p, int[] c) {
        float ax = p[c[1] * 3] - p[c[0] * 3], ay = p[c[1] * 3 + 1] - p[c[0] * 3 + 1], az = p[c[1] * 3 + 2] - p[c[0] * 3 + 2];
        float bx = p[c[2] * 3] - p[c[0] * 3], by = p[c[2] * 3 + 1] - p[c[0] * 3 + 1], bz = p[c[2] * 3 + 2] - p[c[0] * 3 + 2];
        float nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
        float length2 = nx * nx + ny * ny + nz * nz;
        float length = (float) Math.sqrt(length2);
        if (!(length > 0f)) {
            return new float[]{0, 1, 0, 0};
        }
        return new float[]{nx / length, ny / length, nz / length, length2};
    }

    private static double signedVolume(float[] p, int a, int b, int c) {
        double ax = p[a * 3], ay = p[a * 3 + 1], az = p[a * 3 + 2];
        double bx = p[b * 3], by = p[b * 3 + 1], bz = p[b * 3 + 2];
        double cx = p[c * 3], cy = p[c * 3 + 1], cz = p[c * 3 + 2];
        return (ax * (by * cz - bz * cy) - ay * (bx * cz - bz * cx) + az * (bx * cy - by * cx)) / 6.0;
    }

    /**
     * Per-vertex normals averaged from the faces around each position (larger faces count more), so
     * vertices split at a texture seam still shade as one.
     */
    private static float[] smoothNormals(float[] p, int[] triangles) {
        int n = p.length / 3;
        Map<String, Integer> groups = new HashMap<>();
        int[] group = new int[n];
        for (int v = 0; v < n; v++) {
            group[v] = groups.computeIfAbsent(p[v * 3] + "," + p[v * 3 + 1] + "," + p[v * 3 + 2], k -> groups.size());
        }
        double[] sums = new double[groups.size() * 3];
        for (int t = 0; t < triangles.length; t += 3) {
            float[] f = faceNormal(p, new int[]{triangles[t], triangles[t + 1], triangles[t + 2]});
            double weight = Math.sqrt(f[3]);
            for (int c = 0; c < 3; c++) {
                int g = group[triangles[t + c]] * 3;
                sums[g] += f[0] * weight;
                sums[g + 1] += f[1] * weight;
                sums[g + 2] += f[2] * weight;
            }
        }
        float[] normals = new float[n * 3];
        for (int v = 0; v < n; v++) {
            int g = group[v] * 3;
            double length = Math.sqrt(sums[g] * sums[g] + sums[g + 1] * sums[g + 1] + sums[g + 2] * sums[g + 2]);
            if (length > 1.0e-12) {
                normals[v * 3] = (float) (sums[g] / length);
                normals[v * 3 + 1] = (float) (sums[g + 1] / length);
                normals[v * 3 + 2] = (float) (sums[g + 2] / length);
            } else {
                normals[v * 3 + 1] = 1f;
            }
        }
        return normals;
    }

    private static float[] vector(float[] values, int index) {
        return new float[]{values[index * 3], values[index * 3 + 1], values[index * 3 + 2]};
    }

    private static void conjugate(float[] q, int o, float[] out) {
        out[0] = -q[o];
        out[1] = -q[o + 1];
        out[2] = -q[o + 2];
        out[3] = q[o + 3];
    }

    /** Unit quaternion {@code x, y, z, w} for degrees around X, then Y, then Z (like cube rotations). */
    static float[] quaternion(float[] degrees) {
        double hx = Math.toRadians(degrees[0]) / 2, hy = Math.toRadians(degrees[1]) / 2, hz = Math.toRadians(degrees[2]) / 2;
        double sx = Math.sin(hx), cx = Math.cos(hx);
        double sy = Math.sin(hy), cy = Math.cos(hy);
        double sz = Math.sin(hz), cz = Math.cos(hz);
        // qz * qy * qx
        double x = cz * cy * sx - sz * sy * cx;
        double y = cz * sy * cx + sz * cy * sx;
        double z = sz * cy * cx - cz * sy * sx;
        double w = cz * cy * cx + sz * sy * sx;
        return new float[]{(float) x, (float) y, (float) z, (float) w};
    }

    /** Growable float array (load time only). */
    static final class FloatList {
        private float[] data = new float[64];
        private int size;

        void add(float f) {
            if (size == data.length) {
                data = java.util.Arrays.copyOf(data, size * 2);
            }
            data[size++] = f;
        }

        float[] toArray() {
            return java.util.Arrays.copyOf(data, size);
        }
    }

    /** Growable int array (load time only). */
    static final class IntList {
        private int[] data = new int[64];
        private int size;

        void add(int i) {
            if (size == data.length) {
                data = java.util.Arrays.copyOf(data, size * 2);
            }
            data[size++] = i;
        }

        int size() {
            return size;
        }

        int[] toArray() {
            return java.util.Arrays.copyOf(data, size);
        }
    }
}
