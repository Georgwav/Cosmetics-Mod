package io.github.georgwav.cosmetics.model;

import com.google.gson.JsonParseException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads Wavefront OBJ geometry: {@code v}, {@code vt}, {@code vn} and {@code f} (triangles, quads and
 * larger polygons, which {@link MeshBuilder} fans out), with 1-based and negative (relative) indices
 * and every corner form ({@code v}, {@code v/vt}, {@code v//vn}, {@code v/vt/vn}). Groups, objects,
 * smoothing groups and materials are ignored: shading and texture come from the cosmetic file.
 *
 * <p>Coordinates are taken as cosmetic-space pixels (+Y down, -Z front). OBJ texture coordinates run
 * 0..1 with V pointing up; they become texture pixels with V pointing down.
 */
final class ObjParser {
    private ObjParser() {
    }

    static MeshBuilder.Raw parse(String text, String file, float texW, float texH) {
        List<float[]> positions = new ArrayList<>();
        List<float[]> uvs = new ArrayList<>();
        List<float[]> normals = new ArrayList<>();
        MeshBuilder.Raw raw = new MeshBuilder.Raw();
        // One corner per distinct (position, uv, normal) triple.
        Map<String, Integer> cornerIds = new HashMap<>();
        boolean sawNormals = false;
        boolean sawFaceWithoutNormals = false;
        List<Integer> faceLines = new ArrayList<>();
        List<int[][]> faceRefs = new ArrayList<>();

        String[] lines = text.split("\r?\n|\r", -1);
        for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
            String where = file + ":" + (lineIndex + 1);
            String line = lines[lineIndex];
            int comment = line.indexOf('#');
            if (comment >= 0) {
                line = line.substring(0, comment);
            }
            line = line.strip();
            if (line.isEmpty()) {
                continue;
            }
            String[] tokens = line.split("\\s+");
            switch (tokens[0]) {
                case "v" -> positions.add(numbers(tokens, 3, where, "v"));
                case "vt" -> {
                    float[] t = numbers(tokens, 2, where, "vt");
                    if (t[0] < -1.0e-4f || t[0] > 1.0001f || t[1] < -1.0e-4f || t[1] > 1.0001f) {
                        throw new JsonParseException(where + ": texture coordinate (" + t[0] + ", " + t[1]
                                + ") is outside 0..1 (wrapping textures aren't supported)");
                    }
                    // Exporters round to 1.0001 and the like; pull those back onto the texture.
                    float u = Math.max(0f, Math.min(1f, t[0]));
                    float v = Math.max(0f, Math.min(1f, t[1]));
                    uvs.add(new float[]{u * texW, (1f - v) * texH});
                }
                case "vn" -> {
                    normals.add(numbers(tokens, 3, where, "vn"));
                    sawNormals = true;
                }
                case "f" -> {
                    if (tokens.length < 4) {
                        throw new JsonParseException(where + ": a face needs at least 3 vertices");
                    }
                    int[][] refs = new int[tokens.length - 1][];
                    for (int i = 1; i < tokens.length; i++) {
                        refs[i - 1] = corner(tokens[i], positions.size(), uvs.size(), normals.size(), where);
                        if (refs[i - 1][2] < 0) {
                            sawFaceWithoutNormals = true;
                        }
                    }
                    faceRefs.add(refs);
                    faceLines.add(lineIndex + 1);
                }
                default -> {
                    // o, g, s, usemtl, mtllib, l, p and anything else: not geometry we use.
                }
            }
        }
        if (faceRefs.isEmpty()) {
            throw new JsonParseException(file + ": no faces");
        }
        boolean useNormals = sawNormals && !sawFaceWithoutNormals;
        if (useNormals) {
            raw.normals = new ArrayList<>();
        }
        for (int f = 0; f < faceRefs.size(); f++) {
            int[][] refs = faceRefs.get(f);
            int[] face = new int[refs.length];
            for (int i = 0; i < refs.length; i++) {
                int[] ref = refs[i];
                String key = ref[0] + "/" + ref[1] + "/" + (useNormals ? ref[2] : -1);
                Integer id = cornerIds.get(key);
                if (id == null) {
                    id = raw.addCorner(positions.get(ref[0]).clone(), uvs.get(ref[1]).clone(),
                            useNormals ? normals.get(ref[2]).clone() : null, ref[0]);
                    cornerIds.put(key, id);
                }
                face[i] = id;
            }
            raw.faces.add(face);
            raw.faceSources.add(file + ":" + faceLines.get(f));
        }
        return raw;
    }

    /** {@code v}, {@code v/vt}, {@code v//vn} or {@code v/vt/vn} as 0-based {@code [v, vt, vn]} (vn -1 if absent). */
    private static int[] corner(String token, int positions, int uvs, int normals, String where) {
        String[] parts = token.split("/", -1);
        if (parts.length > 3 || parts[0].isEmpty()) {
            throw new JsonParseException(where + ": bad face vertex '" + token + "'");
        }
        int v = index(parts[0], positions, where, "vertex");
        if (parts.length < 2 || parts[1].isEmpty()) {
            throw new JsonParseException(where + ": face vertex '" + token + "' has no texture coordinate");
        }
        int vt = index(parts[1], uvs, where, "texture coordinate");
        int vn = parts.length == 3 && !parts[2].isEmpty() ? index(parts[2], normals, where, "normal") : -1;
        return new int[]{v, vt, vn};
    }

    /** OBJ index (1-based, or negative counting back from the last one so far) to 0-based. */
    private static int index(String text, int count, String where, String what) {
        int value;
        try {
            value = Integer.parseInt(text);
        } catch (NumberFormatException e) {
            throw new JsonParseException(where + ": bad " + what + " index '" + text + "'");
        }
        int index = value > 0 ? value - 1 : count + value;
        if (value == 0 || index < 0 || index >= count) {
            throw new JsonParseException(where + ": " + what + " index " + value + " is out of range (" + count + " defined so far)");
        }
        return index;
    }

    private static float[] numbers(String[] tokens, int count, String where, String what) {
        if (tokens.length < count + 1) {
            throw new JsonParseException(where + ": '" + what + "' needs " + count + " numbers");
        }
        float[] values = new float[count];
        for (int i = 0; i < count; i++) {
            try {
                values[i] = Float.parseFloat(tokens[i + 1]);
            } catch (NumberFormatException e) {
                throw new JsonParseException(where + ": '" + tokens[i + 1] + "' is not a number");
            }
            if (!Float.isFinite(values[i])) {
                throw new JsonParseException(where + ": '" + tokens[i + 1] + "' is not a finite number");
            }
        }
        return values;
    }
}
