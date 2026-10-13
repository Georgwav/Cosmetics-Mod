package io.github.georgwav.cosmetics.model;

import com.google.gson.JsonParseException;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads id Software's MD5 skeletal formats (version 10): {@code .md5mesh} (joints in their bind pose and
 * meshes whose vertices hang off weighted joints) and {@code .md5anim} (joint transforms per frame).
 * Only parsing and the maths that turns frames into absolute joint transforms live here; splitting a
 * mesh into rigid and skinned pieces is {@link RigBuilder}'s job.
 *
 * <p>Quaternions are stored as {@code x, y, z}; {@code w} is derived as {@code -sqrt(1 - x² - y² - z²)},
 * the format's convention. Joint transforms are 7 floats: position {@code x, y, z} then quaternion
 * {@code x, y, z, w}.
 *
 * <p>Pure Java with no Minecraft types, so it is unit-tested without a game instance.
 */
final class Md5Parser {
    /** Floats per joint transform: position, then quaternion. */
    static final int JOINT_FLOATS = 7;
    static final int MAX_JOINTS = 256;
    static final int MAX_FRAMES = 20_000;

    private Md5Parser() {
    }

    record Joint(String name, int parent) {
    }

    /** One {@code mesh { }} block. */
    record MeshBlock(String shader, float[] uvs, int[] weightStart, int[] weightCount, int[] triangles,
                     int[] weightJoint, float[] weightBias, float[] weightPos) {
        int vertexCount() {
            return weightStart.length;
        }
    }

    /**
     * @param bind absolute bind-pose transform per joint ({@link #JOINT_FLOATS} each)
     */
    record MeshFile(List<Joint> joints, float[] bind, List<MeshBlock> meshes) {
    }

    /**
     * @param frames absolute joint transforms, frame after frame ({@code joints * JOINT_FLOATS} per frame)
     */
    record AnimFile(List<Joint> joints, int frameCount, float frameRate, float[] frames) {
    }

    // ------------------------------------------------------------------ md5mesh

    static MeshFile parseMesh(String text, String file) {
        Tokens t = new Tokens(text, file);
        header(t);
        int numJoints = -1;
        int numMeshes = -1;
        List<Joint> joints = null;
        float[] bind = null;
        List<MeshBlock> meshes = new ArrayList<>();
        while (!t.atEnd()) {
            String key = t.word();
            switch (key) {
                case "commandline" -> t.string();
                case "numJoints" -> numJoints = count(t, MAX_JOINTS, "numJoints");
                case "numMeshes" -> numMeshes = count(t, 4096, "numMeshes");
                case "joints" -> {
                    if (numJoints < 1) {
                        throw t.error("'joints' before a valid numJoints");
                    }
                    joints = new ArrayList<>();
                    bind = new float[numJoints * JOINT_FLOATS];
                    t.expect("{");
                    for (int j = 0; j < numJoints; j++) {
                        String name = t.string();
                        int parent = t.integer();
                        if (parent < -1 || parent >= j) {
                            throw t.error("joint " + j + " ('" + name + "') has parent " + parent
                                    + "; parents must come before their children");
                        }
                        joints.add(new Joint(name, parent));
                        float[] p = t.vector(3);
                        float[] q = t.vector(3);
                        int o = j * JOINT_FLOATS;
                        bind[o] = p[0];
                        bind[o + 1] = p[1];
                        bind[o + 2] = p[2];
                        setQuaternion(bind, o + 3, q[0], q[1], q[2]);
                    }
                    t.expect("}");
                }
                case "mesh" -> meshes.add(meshBlock(t, numJoints));
                default -> throw t.error("unexpected '" + key + "'");
            }
        }
        if (joints == null) {
            throw new JsonParseException(file + ": no joints");
        }
        if (meshes.isEmpty()) {
            throw new JsonParseException(file + ": no meshes");
        }
        if (numMeshes >= 0 && numMeshes != meshes.size()) {
            throw new JsonParseException(file + ": numMeshes is " + numMeshes + " but " + meshes.size() + " meshes follow");
        }
        return new MeshFile(List.copyOf(joints), bind, List.copyOf(meshes));
    }

    private static MeshBlock meshBlock(Tokens t, int numJoints) {
        if (numJoints < 1) {
            throw t.error("'mesh' before a valid numJoints");
        }
        t.expect("{");
        String shader = "";
        float[] uvs = null;
        int[] weightStart = null;
        int[] weightCount = null;
        int[] triangles = null;
        int[] weightJoint = null;
        float[] weightBias = null;
        float[] weightPos = null;
        while (!t.peek("}")) {
            String key = t.word();
            switch (key) {
                case "shader" -> shader = t.string();
                case "numverts" -> {
                    int n = count(t, 1_000_000, "numverts");
                    uvs = new float[n * 2];
                    weightStart = new int[n];
                    weightCount = new int[n];
                }
                case "vert" -> {
                    if (uvs == null) {
                        throw t.error("'vert' before numverts");
                    }
                    int i = index(t, weightStart.length, "vert");
                    float[] uv = t.vector(2);
                    uvs[i * 2] = uv[0];
                    uvs[i * 2 + 1] = uv[1];
                    weightStart[i] = t.integer();
                    weightCount[i] = t.integer();
                }
                case "numtris" -> triangles = new int[count(t, 1_000_000, "numtris") * 3];
                case "tri" -> {
                    if (triangles == null) {
                        throw t.error("'tri' before numtris");
                    }
                    int i = index(t, triangles.length / 3, "tri");
                    for (int c = 0; c < 3; c++) {
                        triangles[i * 3 + c] = t.integer();
                    }
                }
                case "numweights" -> {
                    int n = count(t, 4_000_000, "numweights");
                    weightJoint = new int[n];
                    weightBias = new float[n];
                    weightPos = new float[n * 3];
                }
                case "weight" -> {
                    if (weightJoint == null) {
                        throw t.error("'weight' before numweights");
                    }
                    int i = index(t, weightJoint.length, "weight");
                    weightJoint[i] = t.integer();
                    weightBias[i] = t.number();
                    float[] p = t.vector(3);
                    System.arraycopy(p, 0, weightPos, i * 3, 3);
                }
                default -> throw t.error("unexpected '" + key + "' in a mesh");
            }
        }
        t.expect("}");
        if (uvs == null || triangles == null || weightJoint == null) {
            throw t.error("a mesh needs numverts, numtris and numweights");
        }
        for (int v = 0; v < weightStart.length; v++) {
            if (weightCount[v] < 1 || weightStart[v] < 0 || weightStart[v] + weightCount[v] > weightJoint.length) {
                throw t.error("vert " + v + " points at weights outside 0.." + (weightJoint.length - 1));
            }
        }
        for (int i = 0; i < weightJoint.length; i++) {
            if (weightJoint[i] < 0 || weightJoint[i] >= numJoints) {
                throw t.error("weight " + i + " uses joint " + weightJoint[i] + " of " + numJoints);
            }
            if (!(weightBias[i] >= 0f && weightBias[i] <= 1.0001f)) {
                throw t.error("weight " + i + " has bias " + weightBias[i] + " (must be 0 to 1)");
            }
        }
        for (int index : triangles) {
            if (index < 0 || index >= weightStart.length) {
                throw t.error("a triangle uses vert " + index + " of " + weightStart.length);
            }
        }
        return new MeshBlock(shader, uvs, weightStart, weightCount, triangles, weightJoint, weightBias, weightPos);
    }

    // ------------------------------------------------------------------ md5anim

    static AnimFile parseAnim(String text, String file) {
        Tokens t = new Tokens(text, file);
        header(t);
        int numFrames = -1;
        int numJoints = -1;
        float frameRate = 24f;
        int numComponents = -1;
        List<Joint> joints = null;
        int[] flags = null;
        int[] starts = null;
        float[] base = null;
        float[][] frameData = null;
        while (!t.atEnd()) {
            String key = t.word();
            switch (key) {
                case "commandline" -> t.string();
                case "numFrames" -> numFrames = count(t, MAX_FRAMES, "numFrames");
                case "numJoints" -> numJoints = count(t, MAX_JOINTS, "numJoints");
                case "frameRate" -> {
                    frameRate = t.number();
                    if (!(frameRate > 0f && frameRate <= 1000f)) {
                        throw t.error("frameRate must be above 0 and at most 1000");
                    }
                }
                case "numAnimatedComponents" -> numComponents = count(t, MAX_JOINTS * 6, "numAnimatedComponents");
                case "hierarchy" -> {
                    if (numJoints < 1) {
                        throw t.error("'hierarchy' before a valid numJoints");
                    }
                    joints = new ArrayList<>();
                    flags = new int[numJoints];
                    starts = new int[numJoints];
                    t.expect("{");
                    for (int j = 0; j < numJoints; j++) {
                        String name = t.string();
                        int parent = t.integer();
                        if (parent < -1 || parent >= j) {
                            throw t.error("joint " + j + " ('" + name + "') has parent " + parent
                                    + "; parents must come before their children");
                        }
                        joints.add(new Joint(name, parent));
                        flags[j] = t.integer();
                        starts[j] = t.integer();
                    }
                    t.expect("}");
                }
                case "bounds" -> {
                    t.expect("{");
                    while (!t.peek("}")) {
                        t.vector(3);
                    }
                    t.expect("}");
                }
                case "baseframe" -> {
                    if (numJoints < 1) {
                        throw t.error("'baseframe' before a valid numJoints");
                    }
                    base = new float[numJoints * 6];
                    t.expect("{");
                    for (int j = 0; j < numJoints; j++) {
                        System.arraycopy(t.vector(3), 0, base, j * 6, 3);
                        System.arraycopy(t.vector(3), 0, base, j * 6 + 3, 3);
                    }
                    t.expect("}");
                }
                case "frame" -> {
                    if (numFrames < 1 || numComponents < 0) {
                        throw t.error("'frame' before valid numFrames and numAnimatedComponents");
                    }
                    if (frameData == null) {
                        frameData = new float[numFrames][];
                    }
                    int index = index(t, numFrames, "frame");
                    float[] values = new float[numComponents];
                    t.expect("{");
                    for (int i = 0; i < numComponents; i++) {
                        values[i] = t.number();
                    }
                    t.expect("}");
                    frameData[index] = values;
                }
                default -> throw t.error("unexpected '" + key + "'");
            }
        }
        if (joints == null || base == null || frameData == null) {
            throw new JsonParseException(file + ": needs a hierarchy, a baseframe and frames");
        }
        for (int f = 0; f < frameData.length; f++) {
            if (frameData[f] == null) {
                throw new JsonParseException(file + ": frame " + f + " is missing");
            }
        }
        for (int j = 0; j < joints.size(); j++) {
            int used = Integer.bitCount(flags[j] & 63);
            if ((flags[j] & ~63) != 0 || starts[j] < 0 || starts[j] + used > numComponents) {
                throw new JsonParseException(file + ": joint " + j + " ('" + joints.get(j).name()
                        + "') reads frame values outside 0.." + (numComponents - 1));
            }
        }

        int n = joints.size();
        float[] frames = new float[numFrames * n * JOINT_FLOATS];
        float[] local = new float[6];
        for (int f = 0; f < numFrames; f++) {
            float[] values = frameData[f];
            int frameOffset = f * n * JOINT_FLOATS;
            for (int j = 0; j < n; j++) {
                System.arraycopy(base, j * 6, local, 0, 6);
                int next = starts[j];
                for (int c = 0; c < 6; c++) {
                    if ((flags[j] & (1 << c)) != 0) {
                        local[c] = values[next++];
                    }
                }
                int o = frameOffset + j * JOINT_FLOATS;
                float[] joint = new float[JOINT_FLOATS];
                joint[0] = local[0];
                joint[1] = local[1];
                joint[2] = local[2];
                setQuaternion(joint, 3, local[3], local[4], local[5]);
                int parent = joints.get(j).parent();
                if (parent >= 0) {
                    // Frames are relative to the parent joint; store them absolute.
                    combine(frames, frameOffset + parent * JOINT_FLOATS, joint, 0, frames, o);
                } else {
                    System.arraycopy(joint, 0, frames, o, JOINT_FLOATS);
                }
            }
        }
        return new AnimFile(List.copyOf(joints), numFrames, frameRate, frames);
    }

    // ------------------------------------------------------------------ maths

    /** Writes the unit quaternion with the given x, y, z and the format's negative w. */
    static void setQuaternion(float[] out, int o, float x, float y, float z) {
        float t = 1f - x * x - y * y - z * z;
        float w = t < 0f ? 0f : (float) -Math.sqrt(t);
        float length = (float) Math.sqrt(x * x + y * y + z * z + w * w);
        if (!(length > 1.0e-6f)) {
            throw new JsonParseException("joint orientation (" + x + ", " + y + ", " + z + ") is not a rotation");
        }
        out[o] = x / length;
        out[o + 1] = y / length;
        out[o + 2] = z / length;
        out[o + 3] = w / length;
    }

    /** {@code parent * child}: the child's transform, given relative to its parent, made absolute. */
    static void combine(float[] parent, int p, float[] child, int c, float[] out, int o) {
        float px = parent[p], py = parent[p + 1], pz = parent[p + 2];
        float qx = parent[p + 3], qy = parent[p + 4], qz = parent[p + 5], qw = parent[p + 6];
        float cx = child[c], cy = child[c + 1], cz = child[c + 2];
        float rx = child[c + 3], ry = child[c + 4], rz = child[c + 5], rw = child[c + 6];
        // Position: parent position + parent rotation applied to the child's position.
        float tx = 2f * (qy * cz - qz * cy);
        float ty = 2f * (qz * cx - qx * cz);
        float tz = 2f * (qx * cy - qy * cx);
        out[o] = px + cx + qw * tx + (qy * tz - qz * ty);
        out[o + 1] = py + cy + qw * ty + (qz * tx - qx * tz);
        out[o + 2] = pz + cz + qw * tz + (qx * ty - qy * tx);
        // Orientation: parent * child, normalised.
        float x = qw * rx + qx * rw + qy * rz - qz * ry;
        float y = qw * ry - qx * rz + qy * rw + qz * rx;
        float z = qw * rz + qx * ry - qy * rx + qz * rw;
        float w = qw * rw - qx * rx - qy * ry - qz * rz;
        float length = (float) Math.sqrt(x * x + y * y + z * z + w * w);
        out[o + 3] = x / length;
        out[o + 4] = y / length;
        out[o + 5] = z / length;
        out[o + 6] = w / length;
    }

    /** Rotates {@code (x, y, z)} by the unit quaternion at {@code q[o..o+3]} into {@code out[r..r+2]}. */
    static void rotate(float[] q, int o, float x, float y, float z, float[] out, int r) {
        float qx = q[o], qy = q[o + 1], qz = q[o + 2], qw = q[o + 3];
        float tx = 2f * (qy * z - qz * y);
        float ty = 2f * (qz * x - qx * z);
        float tz = 2f * (qx * y - qy * x);
        out[r] = x + qw * tx + (qy * tz - qz * ty);
        out[r + 1] = y + qw * ty + (qz * tx - qx * tz);
        out[r + 2] = z + qw * tz + (qx * ty - qy * tx);
    }

    // ------------------------------------------------------------------ tokens

    private static void header(Tokens t) {
        if (!t.word().equals("MD5Version")) {
            throw t.error("not an MD5 file (it must start with MD5Version)");
        }
        int version = t.integer();
        if (version != 10) {
            throw t.error("MD5Version " + version + " is not supported (only 10)");
        }
    }

    private static int count(Tokens t, int max, String what) {
        int value = t.integer();
        if (value < 0 || value > max) {
            throw t.error(what + " must be 0 to " + max + ", not " + value);
        }
        return value;
    }

    private static int index(Tokens t, int size, String what) {
        int value = t.integer();
        if (value < 0 || value >= size) {
            throw t.error(what + " index " + value + " is out of range (0.." + (size - 1) + ")");
        }
        return value;
    }

    /** Whitespace-separated words, quoted strings and parentheses; {@code //} starts a comment. */
    private static final class Tokens {
        private final String text;
        private final String file;
        private int pos;
        private int line = 1;

        Tokens(String text, String file) {
            this.text = text;
            this.file = file;
        }

        JsonParseException error(String message) {
            return new JsonParseException(file + ":" + line + ": " + message);
        }

        private void skip() {
            while (pos < text.length()) {
                char c = text.charAt(pos);
                if (c == '\n') {
                    line++;
                    pos++;
                } else if (Character.isWhitespace(c)) {
                    pos++;
                } else if (c == '/' && pos + 1 < text.length() && text.charAt(pos + 1) == '/') {
                    while (pos < text.length() && text.charAt(pos) != '\n') {
                        pos++;
                    }
                } else {
                    return;
                }
            }
        }

        boolean atEnd() {
            skip();
            return pos >= text.length();
        }

        boolean peek(String token) {
            skip();
            if (pos >= text.length()) {
                throw error("unexpected end of file, expected '" + token + "'");
            }
            return text.startsWith(token, pos);
        }

        String word() {
            skip();
            if (pos >= text.length()) {
                throw error("unexpected end of file");
            }
            char first = text.charAt(pos);
            if (first == '{' || first == '}' || first == '(' || first == ')') {
                pos++;
                return String.valueOf(first);
            }
            int start = pos;
            while (pos < text.length()) {
                char c = text.charAt(pos);
                if (Character.isWhitespace(c) || c == '{' || c == '}' || c == '(' || c == ')' || c == '"') {
                    break;
                }
                pos++;
            }
            return text.substring(start, pos);
        }

        void expect(String token) {
            String got = word();
            if (!got.equals(token)) {
                throw error("expected '" + token + "' but found '" + got + "'");
            }
        }

        String string() {
            skip();
            if (pos >= text.length() || text.charAt(pos) != '"') {
                throw error("expected a quoted string");
            }
            int end = text.indexOf('"', pos + 1);
            if (end < 0 || text.substring(pos, end).indexOf('\n') >= 0) {
                throw error("unterminated string");
            }
            String value = text.substring(pos + 1, end);
            pos = end + 1;
            return value;
        }

        int integer() {
            String word = word();
            try {
                return Integer.parseInt(word);
            } catch (NumberFormatException e) {
                throw error("'" + word + "' is not a whole number");
            }
        }

        float number() {
            String word = word();
            float value;
            try {
                value = Float.parseFloat(word);
            } catch (NumberFormatException e) {
                throw error("'" + word + "' is not a number");
            }
            if (!Float.isFinite(value)) {
                throw error("'" + word + "' is not a finite number");
            }
            return value;
        }

        float[] vector(int size) {
            expect("(");
            float[] values = new float[size];
            for (int i = 0; i < size; i++) {
                values[i] = number();
            }
            expect(")");
            return values;
        }
    }
}
