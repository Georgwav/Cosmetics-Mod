package io.github.georgwav.cosmetics.model;

import com.google.gson.JsonParseException;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Turns raw mesh geometry (inline JSON, OBJ or a Blockbench {@code .bbmodel}) into a validated
 * {@link CosmeticDefinition.Mesh} in cosmetic space. Everything mesh-related happens here, once, at
 * load time: transforms, winding, splitting polygons, normals. Baking then only copies floats.
 *
 * <p>Faces wind counter-clockwise seen from outside (right-hand rule), the same as cube faces, so meshes
 * are culled exactly where cubes are.
 *
 * <p>Pure Java with no Minecraft types, so it is unit-tested without a game instance.
 */
final class MeshBuilder {
    /** Faces smaller than this many square pixels are dropped. */
    static final float MIN_AREA = 1.0e-6f;
    /** How far (in texture pixels) a UV may stray outside the texture before it's an error. */
    static final float UV_TOLERANCE = 1.0e-3f;

    private MeshBuilder() {
    }

    /**
     * Raw geometry as parallel per-corner arrays. A corner is one vertex of one face as the source
     * defines it (position, UV and maybe a normal); corners that share a position share a
     * {@code positionId}, which is what smooth shading averages over.
     */
    static final class Raw {
        final List<float[]> positions = new ArrayList<>();
        final List<float[]> uvs = new ArrayList<>();
        /** Per corner, or null when the source has no normals. */
        List<float[]> normals;
        final List<Integer> positionIds = new ArrayList<>();
        final List<int[]> faces = new ArrayList<>();
        /** Where each face comes from, for messages ("model.obj:12", "faces[3]", ...). */
        final List<String> faceSources = new ArrayList<>();

        int addCorner(float[] position, float[] uv, float[] normal, int positionId) {
            positions.add(position);
            uvs.add(uv);
            if (normals != null) {
                normals.add(normal);
            }
            positionIds.add(positionId);
            return positions.size() - 1;
        }
    }

    /**
     * Per-mesh placement from the cosmetic file, applied in this order: mirror X, scale, rotate around
     * the pivot (X, then Y, then Z, like cubes), offset. Mirroring or a negative scale flips the winding
     * back so faces still point outwards.
     */
    record Placement(boolean mirror, float[] scale, float[] pivot, float[] rotation, float[] offset) {
        static final Placement NONE = new Placement(false, new float[]{1, 1, 1}, new float[3], new float[3], new float[3]);
    }

    static CosmeticDefinition.Mesh build(Raw raw, Placement placement, boolean smooth, float texW, float texH,
                                         String source, Consumer<String> warnings) {
        int corners = raw.positions.size();
        for (int i = 0; i < corners; i++) {
            requireFinite(raw.positions.get(i), source, "vertex " + i);
            float[] uv = raw.uvs.get(i);
            requireFinite(uv, source, "uv " + i);
            if (uv[0] < -UV_TOLERANCE || uv[0] > texW + UV_TOLERANCE || uv[1] < -UV_TOLERANCE || uv[1] > texH + UV_TOLERANCE) {
                throw new JsonParseException(source + ": uv " + i + " (" + uv[0] + ", " + uv[1]
                        + ") is outside the " + (int) texW + "x" + (int) texH + " texture");
            }
            if (raw.normals != null) {
                requireFinite(raw.normals.get(i), source, "normal " + i);
            }
        }
        for (float s : placement.scale()) {
            if (s == 0f || !Float.isFinite(s)) {
                throw new JsonParseException(source + ": scale must be finite and not 0");
            }
        }

        // Placement.
        float[] m = CosmeticBaker.rotationMatrix(placement.rotation());
        float mx = placement.mirror() ? -1f : 1f;
        float sx = placement.scale()[0] * mx, sy = placement.scale()[1], sz = placement.scale()[2];
        float[] pivot = placement.pivot(), offset = placement.offset();
        float[][] positions = new float[corners][];
        for (int i = 0; i < corners; i++) {
            float[] p = raw.positions.get(i);
            float[] r = CosmeticBaker.rotate(m, p[0] * sx - pivot[0], p[1] * sy - pivot[1], p[2] * sz - pivot[2]);
            positions[i] = new float[]{r[0] + pivot[0] + offset[0], r[1] + pivot[1] + offset[1], r[2] + pivot[2] + offset[2]};
        }
        float[][] fileNormals = null;
        int zeroNormals = 0;
        if (smooth && raw.normals != null) {
            fileNormals = new float[corners][];
            for (int i = 0; i < corners; i++) {
                float[] n = raw.normals.get(i);
                // Normals take the inverse scale (inverse transpose of a diagonal matrix). One with no direction
                // (exporters write them for degenerate corners) is worked out from the faces, as without normals.
                float[] r = normalize(CosmeticBaker.rotate(m, n[0] / sx, n[1] / sy, n[2] / sz));
                if (r == null) {
                    zeroNormals++;
                }
                fileNormals[i] = r;
            }
            if (zeroNormals > 0) {
                warnings.accept(source + ": " + zeroNormals + " normal(s) have zero length; worked out from the faces");
            }
        }
        boolean flipWinding = sx * sy * sz < 0f;

        // Faces: check indices, restore winding, split into triangles and quads, drop slivers.
        List<int[]> faces = new ArrayList<>();
        List<float[]> faceNormals = new ArrayList<>(); // unnormalized: length is twice the area
        for (int f = 0; f < raw.faces.size(); f++) {
            int[] face = raw.faces.get(f).clone();
            String where = raw.faceSources.get(f);
            if (face.length < 3) {
                throw new JsonParseException(where + ": a face needs at least 3 vertices");
            }
            for (int index : face) {
                if (index < 0 || index >= corners) {
                    throw new JsonParseException(where + ": vertex index " + index + " is out of range");
                }
            }
            if (flipWinding) {
                reverse(face);
            }
            for (int[] piece : split(face, positions)) {
                float[] normal = newell(piece, positions);
                float area = length(normal) / 2f;
                if (!(area >= MIN_AREA)) {
                    warnings.accept(where + ": face has no area and was dropped");
                    continue;
                }
                faces.add(piece);
                faceNormals.add(normal);
            }
        }

        // Smooth normals computed from area-weighted face normals, per shared position.
        float[][] computed = null;
        if (smooth && (fileNormals == null || zeroNormals > 0)) {
            int maxId = 0;
            for (int id : raw.positionIds) {
                maxId = Math.max(maxId, id);
            }
            float[][] sums = new float[maxId + 1][3];
            for (int f = 0; f < faces.size(); f++) {
                float[] n = faceNormals.get(f);
                for (int corner : faces.get(f)) {
                    float[] sum = sums[raw.positionIds.get(corner)];
                    sum[0] += n[0];
                    sum[1] += n[1];
                    sum[2] += n[2];
                }
            }
            computed = sums;
        }

        int cornerCount = 0;
        int triangles = 0;
        for (int[] face : faces) {
            cornerCount += face.length;
            triangles += face.length - 2;
        }
        float[] out = new float[cornerCount * CosmeticDefinition.Mesh.FLOATS_PER_CORNER];
        int[] sizes = new int[faces.size()];
        int o = 0;
        for (int f = 0; f < faces.size(); f++) {
            int[] face = faces.get(f);
            sizes[f] = face.length;
            float[] faceNormal = normalize(faceNormals.get(f));
            for (int corner : face) {
                float[] n = faceNormal;
                float[] given = fileNormals != null ? fileNormals[corner] : null;
                if (given != null) {
                    n = given;
                } else if (computed != null) {
                    float[] averaged = normalize(computed[raw.positionIds.get(corner)]);
                    if (averaged != null) {
                        n = averaged;
                    }
                }
                float[] p = positions[corner];
                float[] uv = raw.uvs.get(corner);
                out[o++] = p[0];
                out[o++] = p[1];
                out[o++] = p[2];
                out[o++] = uv[0];
                out[o++] = uv[1];
                out[o++] = n[0];
                out[o++] = n[1];
                out[o++] = n[2];
            }
        }
        return new CosmeticDefinition.Mesh(source, smooth, out, sizes, triangles);
    }

    /**
     * Triangles and convex quads stay as they are. A concave quad is split along the diagonal that keeps
     * both halves facing the same way, and larger polygons are fanned out from their first corner.
     */
    static List<int[]> split(int[] face, float[][] positions) {
        List<int[]> pieces = new ArrayList<>();
        if (face.length == 3) {
            pieces.add(face);
        } else if (face.length == 4) {
            // Vanilla draws a quad as (0, 1, 2) and (2, 3, 0); if those halves disagree, the quad folds
            // over that diagonal, so split along the other one instead.
            float[] a = newell(new int[]{face[0], face[1], face[2]}, positions);
            float[] b = newell(new int[]{face[2], face[3], face[0]}, positions);
            if (a[0] * b[0] + a[1] * b[1] + a[2] * b[2] < 0f) {
                pieces.add(new int[]{face[0], face[1], face[3]});
                pieces.add(new int[]{face[1], face[2], face[3]});
            } else {
                pieces.add(face);
            }
        } else {
            for (int i = 1; i + 1 < face.length; i++) {
                pieces.add(new int[]{face[0], face[i], face[i + 1]});
            }
        }
        return pieces;
    }

    /** Newell's polygon normal: points outwards for counter-clockwise faces, length is twice the area. */
    static float[] newell(int[] face, float[][] positions) {
        float nx = 0, ny = 0, nz = 0;
        for (int i = 0; i < face.length; i++) {
            float[] a = positions[face[i]];
            float[] b = positions[face[(i + 1) % face.length]];
            nx += (a[1] - b[1]) * (a[2] + b[2]);
            ny += (a[2] - b[2]) * (a[0] + b[0]);
            nz += (a[0] - b[0]) * (a[1] + b[1]);
        }
        return new float[]{nx, ny, nz};
    }

    static float length(float[] v) {
        return (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
    }

    /** The unit vector, or null for a (near) zero vector. */
    static float[] normalize(float[] v) {
        float length = length(v);
        if (!(length > 1.0e-12f)) {
            return null;
        }
        return new float[]{v[0] / length, v[1] / length, v[2] / length};
    }

    private static void reverse(int[] face) {
        for (int i = 0, j = face.length - 1; i < j; i++, j--) {
            int t = face[i];
            face[i] = face[j];
            face[j] = t;
        }
    }

    private static void requireFinite(float[] values, String source, String what) {
        for (float v : values) {
            if (!Float.isFinite(v)) {
                throw new JsonParseException(source + ": " + what + " contains a non-finite number");
            }
        }
    }
}
