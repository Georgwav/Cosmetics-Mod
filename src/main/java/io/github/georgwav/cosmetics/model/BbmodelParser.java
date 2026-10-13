package io.github.georgwav.cosmetics.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Reads the mesh elements of a Blockbench project file ({@code .bbmodel}). Cube elements are skipped
 * (export those as a Bedrock model instead, see {@link BedrockGeometry}).
 *
 * <p>Blockbench keeps every format in one internal space: pixels, +Y up, feet at y = 0. Its Java entity
 * export turns that into Java model space by mirroring X and Y around the neck and negating X and Y
 * rotations; this does the same, so a mesh built on Blockbench's player template sits on the player
 * where it was modelled:
 * <ul>
 *   <li>a point {@code (x, y, z)} becomes {@code (-x, 24 - y, z)};</li>
 *   <li>rotations {@code [x, y, z]} (degrees, applied X, then Y, then Z) become {@code [-x, -y, z]}.</li>
 * </ul>
 * That is a rotation, not a mirror, so faces keep their winding.
 */
final class BbmodelParser {
    private BbmodelParser() {
    }

    static MeshBuilder.Raw parse(String text, String file, float texW, float texH, Consumer<String> warnings) {
        JsonElement rootElement;
        try {
            rootElement = JsonParser.parseString(text);
        } catch (JsonParseException e) {
            throw new JsonParseException(file + ": not valid JSON: " + e.getMessage());
        }
        if (!rootElement.isJsonObject()) {
            throw new JsonParseException(file + ": not a Blockbench project");
        }
        JsonObject root = rootElement.getAsJsonObject();
        float resW = 16, resH = 16;
        if (root.has("resolution")) {
            JsonObject resolution = root.getAsJsonObject("resolution");
            resW = resolution.has("width") ? resolution.get("width").getAsFloat() : 16;
            resH = resolution.has("height") ? resolution.get("height").getAsFloat() : 16;
            if (!(resW > 0) || !(resH > 0)) {
                throw new JsonParseException(file + ": resolution must be positive");
            }
        }

        // Group placement (origin and rotation), from "groups" (current format) and from groups written
        // inline in "outliner" (older format), then each element's chain of parent groups.
        Map<String, float[][]> groups = new HashMap<>();
        if (root.has("groups") && root.get("groups").isJsonArray()) {
            for (JsonElement group : root.getAsJsonArray("groups")) {
                readGroup(group.getAsJsonObject(), groups, file);
            }
        }
        Map<String, List<float[][]>> parents = new HashMap<>();
        if (root.has("outliner") && root.get("outliner").isJsonArray()) {
            walkOutliner(root.getAsJsonArray("outliner"), new ArrayList<>(), groups, parents, file, 0);
        }

        if (!root.has("elements") || !root.get("elements").isJsonArray()) {
            throw new JsonParseException(file + ": no elements");
        }
        MeshBuilder.Raw raw = new MeshBuilder.Raw();
        int skipped = 0;
        int meshes = 0;
        int positionBase = 0;
        for (JsonElement elementJson : root.getAsJsonArray("elements")) {
            JsonObject element = elementJson.getAsJsonObject();
            String type = element.has("type") ? element.get("type").getAsString() : "cube";
            if (!type.equals("mesh")) {
                skipped++;
                continue;
            }
            if (element.has("export") && !element.get("export").getAsBoolean()) {
                continue;
            }
            meshes++;
            String name = element.has("name") ? element.get("name").getAsString() : "mesh";
            String where = file + " mesh '" + name + "'";
            float[] origin = element.has("origin") ? floats(element.get("origin"), 3, where + " origin") : new float[3];
            float[] rotation = element.has("rotation") ? floats(element.get("rotation"), 3, where + " rotation") : new float[3];
            float[] m = CosmeticBaker.rotationMatrix(rotation);
            List<float[][]> chain = element.has("uuid") ? parents.getOrDefault(element.get("uuid").getAsString(), List.of()) : List.of();

            // Vertices: local to the mesh origin, then the mesh's own rotation, then the parent groups.
            JsonObject verticesJson = element.getAsJsonObject("vertices");
            JsonObject facesJson = element.getAsJsonObject("faces");
            if (verticesJson == null || facesJson == null) {
                throw new JsonParseException(where + ": needs vertices and faces");
            }
            Map<String, Integer> vertexIds = new HashMap<>();
            List<float[]> internal = new ArrayList<>();
            for (String key : verticesJson.keySet()) {
                float[] local = floats(verticesJson.get(key), 3, where + " vertex '" + key + "'");
                float[] p = CosmeticBaker.rotate(m, local[0], local[1], local[2]);
                p[0] += origin[0];
                p[1] += origin[1];
                p[2] += origin[2];
                for (float[][] group : chain) {
                    float[] o = group[0];
                    float[] r = CosmeticBaker.rotate(group[1], p[0] - o[0], p[1] - o[1], p[2] - o[2]);
                    p = new float[]{r[0] + o[0], r[1] + o[1], r[2] + o[2]};
                }
                vertexIds.put(key, internal.size());
                internal.add(p);
            }

            for (String faceKey : facesJson.keySet()) {
                JsonObject face = facesJson.getAsJsonObject(faceKey);
                String faceWhere = where + " face '" + faceKey + "'";
                if (face.has("texture") && face.get("texture").isJsonNull()) {
                    continue; // Blockbench hides faces without a texture
                }
                JsonArray keys = face.getAsJsonArray("vertices");
                JsonObject faceUv = face.getAsJsonObject("uv");
                if (keys == null || faceUv == null) {
                    throw new JsonParseException(faceWhere + ": needs vertices and uv");
                }
                List<String> order = new ArrayList<>();
                for (JsonElement key : keys) {
                    order.add(key.getAsString());
                }
                if (order.size() < 3) {
                    warnings.accept(faceWhere + ": fewer than 3 vertices, skipped");
                    continue;
                }
                for (String key : order) {
                    if (!vertexIds.containsKey(key)) {
                        throw new JsonParseException(faceWhere + ": unknown vertex '" + key + "'");
                    }
                }
                if (order.size() == 4) {
                    order = sortQuad(order, vertexIds, internal);
                }
                int[] corners = new int[order.size()];
                for (int i = 0; i < order.size(); i++) {
                    String key = order.get(i);
                    if (!faceUv.has(key)) {
                        throw new JsonParseException(faceWhere + ": no uv for vertex '" + key + "'");
                    }
                    float[] uv = floats(faceUv.get(key), 2, faceWhere + " uv");
                    int vertex = vertexIds.get(key);
                    float[] p = internal.get(vertex);
                    corners[i] = raw.addCorner(new float[]{-p[0], BedrockGeometry.PIVOT_HEIGHT - p[1], p[2]},
                            new float[]{uv[0] / resW * texW, uv[1] / resH * texH}, null, positionBase + vertex);
                }
                raw.faces.add(corners);
                raw.faceSources.add(faceWhere);
            }
            positionBase += internal.size();
        }
        if (skipped > 0) {
            warnings.accept(file + ": " + skipped + " non-mesh element(s) skipped; export cubes as a Bedrock model instead");
        }
        if (meshes == 0 || raw.faces.isEmpty()) {
            throw new JsonParseException(file + ": no mesh faces to load");
        }
        return raw;
    }

    /** Group origin and rotation matrix, from Blockbench internal space. */
    private static void readGroup(JsonObject group, Map<String, float[][]> groups, String file) {
        if (!group.has("uuid")) {
            return;
        }
        String where = file + " group '" + (group.has("name") ? group.get("name").getAsString() : "?") + "'";
        float[] origin = group.has("origin") ? floats(group.get("origin"), 3, where + " origin") : new float[3];
        float[] rotation = group.has("rotation") ? floats(group.get("rotation"), 3, where + " rotation") : new float[3];
        groups.put(group.get("uuid").getAsString(), new float[][]{origin, CosmeticBaker.rotationMatrix(rotation)});
    }

    /**
     * Records each element's parent groups, innermost first (the order they apply in). Entries are an
     * element uuid or a group object with {@code children}.
     */
    private static void walkOutliner(JsonArray items, List<float[][]> chain, Map<String, float[][]> groups,
                                     Map<String, List<float[][]>> parents, String file, int depth) {
        if (depth > 64) {
            throw new JsonParseException(file + ": outliner is nested too deeply");
        }
        for (JsonElement item : items) {
            if (item.isJsonPrimitive()) {
                parents.put(item.getAsString(), chain);
            } else if (item.isJsonObject()) {
                JsonObject group = item.getAsJsonObject();
                if (group.has("name") && !groups.containsKey(uuid(group))) {
                    readGroup(group, groups, file); // older files keep group data inline
                }
                float[][] placement = groups.get(uuid(group));
                List<float[][]> inner = new ArrayList<>();
                if (placement != null) {
                    inner.add(placement);
                }
                inner.addAll(chain);
                if (group.has("children") && group.get("children").isJsonArray()) {
                    walkOutliner(group.getAsJsonArray("children"), inner, groups, parents, file, depth + 1);
                }
            }
        }
    }

    private static String uuid(JsonObject group) {
        return group.has("uuid") ? group.get("uuid").getAsString() : "";
    }

    /**
     * Blockbench's own corner order for a quad ({@code MeshFace.getSortedVertices}): files store the
     * sorted order, but this keeps hand-edited files drawing the way Blockbench shows them.
     */
    static List<String> sortQuad(List<String> keys, Map<String, Integer> ids, List<float[]> points) {
        float[] p0 = points.get(ids.get(keys.get(0)));
        float[] p1 = points.get(ids.get(keys.get(1)));
        float[] p2 = points.get(ids.get(keys.get(2)));
        float[] p3 = points.get(ids.get(keys.get(3)));
        if (otherSide(p1, p2, p0, p3)) {
            return List.of(keys.get(2), keys.get(0), keys.get(1), keys.get(3));
        }
        if (otherSide(p0, p1, p2, p3)) {
            return List.of(keys.get(0), keys.get(2), keys.get(1), keys.get(3));
        }
        return keys;
    }

    /**
     * Whether {@code check} lies on the far side of the line through {@code base1} and {@code base2},
     * seen from {@code top} (a plane through the line, facing away from {@code top}).
     */
    private static boolean otherSide(float[] base1, float[] base2, float[] top, float[] check) {
        double dx = base2[0] - base1[0], dy = base2[1] - base1[1], dz = base2[2] - base1[2];
        double lengthSq = dx * dx + dy * dy + dz * dz;
        double t = lengthSq == 0 ? 0 : ((top[0] - base1[0]) * dx + (top[1] - base1[1]) * dy + (top[2] - base1[2]) * dz) / lengthSq;
        double nx = base1[0] + dx * t - top[0];
        double ny = base1[1] + dy * t - top[1];
        double nz = base1[2] + dz * t - top[2];
        double distance = nx * (check[0] - base2[0]) + ny * (check[1] - base2[1]) + nz * (check[2] - base2[2]);
        return distance > 0;
    }

    private static float[] floats(JsonElement element, int count, String where) {
        if (element == null || !element.isJsonArray() || element.getAsJsonArray().size() < count) {
            throw new JsonParseException(where + " must be an array of " + count + " numbers");
        }
        float[] result = new float[count];
        for (int i = 0; i < count; i++) {
            result[i] = element.getAsJsonArray().get(i).getAsFloat();
            if (!Float.isFinite(result[i])) {
                throw new JsonParseException(where + " contains a non-finite number");
            }
        }
        return result;
    }
}
