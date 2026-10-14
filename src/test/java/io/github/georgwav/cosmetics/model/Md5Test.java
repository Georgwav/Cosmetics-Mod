package io.github.georgwav.cosmetics.model;

import com.google.gson.JsonParseException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.StringReader;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** MD5 meshes and animations: parsing, the rigid/skinned split, playback and styles. All files are made up. */
class Md5Test {
    /**
     * Three joints in a chain. A quad follows joint a alone, a quad follows joint b alone, two triangles have
     * corners blended between a and b, and one triangle mixes corners that follow a and b separately.
     */
    static final String MESH = """
            MD5Version 10
            commandline "made up for the tests"
            numJoints 3
            numMeshes 1
            joints {
              "root" -1 ( 0 0 0 ) ( 0 0 0 ) // the origin
              "a" 0 ( 0 4 0 ) ( 0 0 0 )
              "b" 1 ( 0 8 0 ) ( 0 0 0.38268343 )
            }
            mesh {
              shader "textures/body.png"
              numverts 10
              vert 0 ( 0 0 ) 0 1
              vert 1 ( 0.5 0 ) 1 1
              vert 2 ( 0.5 0.5 ) 2 1
              vert 3 ( 0 0.5 ) 3 1
              vert 4 ( 0.5 0.5 ) 4 1
              vert 5 ( 1 0.5 ) 5 1
              vert 6 ( 1 1 ) 6 1
              vert 7 ( 0.5 1 ) 7 1
              vert 8 ( 0.25 0.75 ) 8 2
              vert 9 ( 0.75 0.25 ) 10 2
              numtris 7
              tri 0 0 1 2
              tri 1 0 2 3
              tri 2 4 5 6
              tri 3 4 6 7
              tri 4 3 2 8
              tri 5 2 9 8
              tri 6 0 4 1
              numweights 12
              weight 0 1 1 ( -1 0 0 )
              weight 1 1 1 ( 1 0 0 )
              weight 2 1 1 ( 1 2 0 )
              weight 3 1 1 ( -1 2 0 )
              weight 4 2 1 ( -1 0 0.5 )
              weight 5 2 1 ( 1 0 0.5 )
              weight 6 2 1 ( 1 2 0.5 )
              weight 7 2 1 ( -1 2 0.5 )
              weight 8 1 0.5 ( 0 3 1 )
              weight 9 2 0.5 ( 0 -1 1 )
              weight 10 1 0.25 ( 1 3 1 )
              weight 11 2 0.75 ( 1 -1 1 )
            }
            """;

    /** Joint a turns, joint b slides; three frames at 10 fps. */
    static final String ANIM = """
            MD5Version 10
            commandline ""
            numFrames 3
            numJoints 3
            frameRate 10
            numAnimatedComponents 4
            hierarchy {
              "root" -1 0 0
              "a" 0 56 0
              "b" 1 1 3
            }
            bounds {
              ( -2 -2 -2 ) ( 2 12 2 )
              ( -2 -2 -2 ) ( 2 12 2 )
              ( -2 -2 -2 ) ( 2 12 2 )
            }
            baseframe {
              ( 0 0 0 ) ( 0 0 0 )
              ( 0 4 0 ) ( 0 0 0 )
              ( 0 4 0 ) ( 0 0 0.38268343 )
            }
            frame 0 {
              0 0 0 0
            }
            frame 1 {
              0.2 0 0 0.5
            }
            frame 2 {
              0 0.3 0.1 1
            }
            """;

    private static final String SECOND_ANIM = ANIM.replace("frame 1 {\n  0.2 0 0 0.5", "frame 1 {\n  0 0.5 0 -2");

    static CosmeticParser.Context context(Map<String, String> files, List<String> warnings) {
        return new CosmeticParser.Context(CosmeticParser.Context.withModels(id -> null).models(), location -> {
            String text = files.get(location);
            if (text == null) {
                throw new FileNotFoundException(location);
            }
            return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
        }, warnings::add, CosmeticParser.Context.DEFAULT_MAX_MESH_TRIANGLES);
    }

    static BakedCosmetic bake(String meshJson, String extra) {
        return CosmeticBaker.bake(definition(meshJson, extra));
    }

    static CosmeticDefinition definition(String meshJson, String extra) {
        return CosmeticParser.parse("t:x", new StringReader("{\"slot\":\"back\",\"texture\":\"t:a.png\",\"texture_size\":[16,16]"
                + extra + ",\"parts\":[{\"meshes\":[" + meshJson + "]}]}"), context(Map.of(
                "t:models/m.md5mesh", MESH, "t:models/idle.md5anim", ANIM, "t:models/wave.md5anim", SECOND_ANIM),
                new ArrayList<>()));
    }

    // ------------------------------------------------------------------ reference maths

    /** Quaternion rotation written out as q v q*, independently of the code under test. */
    static float[] rotate(float[] q, int o, float[] v) {
        double x = q[o], y = q[o + 1], z = q[o + 2], w = q[o + 3];
        // t = q * (v, 0)
        double tw = -x * v[0] - y * v[1] - z * v[2];
        double tx = w * v[0] + y * v[2] - z * v[1];
        double ty = w * v[1] + z * v[0] - x * v[2];
        double tz = w * v[2] + x * v[1] - y * v[0];
        // t * conj(q)
        return new float[]{
                (float) (tx * w - tw * x - ty * z + tz * y),
                (float) (ty * w - tw * y - tz * x + tx * z),
                (float) (tz * w - tw * z - tx * y + ty * x)};
    }

    /** Where an MD5 vertex is with the given absolute joints. */
    static float[] skin(Md5Parser.MeshBlock block, int v, float[] joints, int jointOffset) {
        float[] p = new float[3];
        for (int w = block.weightStart()[v]; w < block.weightStart()[v] + block.weightCount()[v]; w++) {
            int j = jointOffset + block.weightJoint()[w] * 7;
            float[] r = rotate(joints, j + 3, new float[]{block.weightPos()[w * 3], block.weightPos()[w * 3 + 1],
                    block.weightPos()[w * 3 + 2]});
            for (int k = 0; k < 3; k++) {
                p[k] += (joints[j + k] + r[k]) * block.weightBias()[w];
            }
        }
        return p;
    }

    static void assertNear(float[] expected, float[] actual, String message) {
        for (int k = 0; k < 3; k++) {
            assertEquals(expected[k], actual[k], 1e-4f, message + " [" + k + "]");
        }
    }

    // ------------------------------------------------------------------ tests

    @Test
    void parsesMeshAndAnimation() {
        Md5Parser.MeshFile mesh = Md5Parser.parseMesh(MESH, "m.md5mesh");
        assertEquals(3, mesh.joints().size());
        assertEquals(1, mesh.joints().get(2).parent());
        assertEquals(1, mesh.meshes().size());
        assertEquals("textures/body.png", mesh.meshes().get(0).shader());
        // w is derived negative, and the quaternion is a unit one.
        float[] bind = mesh.bind();
        assertEquals(-0.9238795f, bind[2 * 7 + 6], 1e-6f);

        Md5Parser.AnimFile anim = Md5Parser.parseAnim(ANIM, "idle.md5anim");
        assertEquals(3, anim.frameCount());
        assertEquals(10f, anim.frameRate());
        // Frame 1: joint b slid 0.5 along its parent's X; frames are stored absolute.
        float[] frame1 = java.util.Arrays.copyOfRange(anim.frames(), 3 * 7, 6 * 7);
        float[] bInA = rotate(frame1, 7 + 3, new float[]{0.5f, 4, 0});
        assertNear(new float[]{bInA[0], 4 + bInA[1], bInA[2]}, new float[]{frame1[14], frame1[15], frame1[16]},
                "child joints are made absolute");
    }

    @Test
    void stillMeshBakesTheBindPose() {
        BakedCosmetic cosmetic = bake("{\"model\":\"t:models/m.md5mesh\"}", "");
        assertEquals(1, cosmetic.layers().length);
        BakedCosmetic.Layer layer = cosmetic.layers()[0];
        assertNull(layer.rig(), "no animation: plain geometry, no rig at draw time");
        assertEquals(7, layer.mesh().quadCount());
        assertTrue(cosmetic.styles().length == 0 && !cosmetic.hasAnimation());
        // The first quad's corners are joint a's bind position plus the weight offsets, in blocks.
        Md5Parser.MeshFile file = Md5Parser.parseMesh(MESH, "m");
        float[] d = layer.mesh().data();
        float[] corner = skin(file.meshes().get(0), 0, file.bind(), 0);
        boolean found = false;
        for (int i = 0; i < d.length; i += BakedMesh.FLOATS_PER_VERTEX) {
            found |= Math.abs(d[i] - corner[0] / 16f) < 1e-6 && Math.abs(d[i + 1] - corner[1] / 16f) < 1e-6
                    && Math.abs(d[i + 2] - corner[2] / 16f) < 1e-6;
        }
        assertTrue(found);
    }

    @Test
    void trianglesFollowingOneJointAreRigidTheRestIsSkinned() {
        CosmeticDefinition definition = definition(
                "{\"model\":\"t:models/m.md5mesh\",\"bone_animation\":{\"file\":\"t:models/idle.md5anim\"}}", "");
        CosmeticDefinition.Rigged rigged = definition.parts().get(0).meshes().get(0).rigged();
        assertNotNull(rigged);
        assertEquals(2, rigged.pieces().size());
        assertEquals(1, rigged.pieces().get(0).joint());
        assertEquals(2, rigged.pieces().get(0).triangles());
        assertEquals(2, rigged.pieces().get(1).joint());
        assertEquals(2, rigged.pieces().get(1).triangles());
        assertEquals(9, rigged.skinned().triangles().length, "blended corners, and a triangle across two joints");
        assertEquals(7, definition.parts().get(0).meshes().get(0).triangles());

        BakedCosmetic cosmetic = CosmeticBaker.bake(definition);
        assertEquals(3, cosmetic.layers().length);
        assertTrue(cosmetic.hasAnimation());
        assertEquals(1, cosmetic.layers()[0].joint());
        assertNull(cosmetic.layers()[0].skin());
        assertNotNull(cosmetic.layers()[2].skin());
        assertEquals(-1, cosmetic.layers()[2].joint());
    }

    @Test
    void rigidAndSkinnedGeometryLandWhereFullSkinningPutsIt() {
        BakedCosmetic cosmetic = bake(
                "{\"model\":\"t:models/m.md5mesh\",\"winding\":\"ccw\",\"bone_animation\":{\"file\":\"t:models/idle.md5anim\"}}", "");
        Md5Parser.MeshBlock block = Md5Parser.parseMesh(MESH, "m").meshes().get(0);
        Md5Parser.AnimFile anim = Md5Parser.parseAnim(ANIM, "a");
        MeshRig rig = cosmetic.layers()[0].rig();
        int[] tris = block.triangles();
        for (int frame = 0; frame < 3; frame++) {
            float time = frame / 10f;
            int offset = frame * 3 * 7;
            // Rigid pieces: joint transform applied to the joint-space vertex.
            for (int l = 0; l < 2; l++) {
                BakedCosmetic.Layer layer = cosmetic.layers()[l];
                float[] joints = rig.evaluate(0, time);
                float[] d = layer.mesh().data();
                for (int q = 0; q < layer.mesh().quadCount(); q++) {
                    for (int c = 0; c < 3; c++) {
                        int o = (q * 4 + c) * BakedMesh.FLOATS_PER_VERTEX;
                        float[] r = rotate(joints, layer.joint() * 7 + 3, new float[]{d[o], d[o + 1], d[o + 2]});
                        float[] world = {joints[layer.joint() * 7] + r[0], joints[layer.joint() * 7 + 1] + r[1],
                                joints[layer.joint() * 7 + 2] + r[2]};
                        int source = tris[(l * 2 + q) * 3 + c];
                        assertNear(skin(block, source, anim.frames(), offset), world,
                                "frame " + frame + " rigid layer " + l + " triangle " + q + " corner " + c);
                    }
                }
            }
            // Skinned layer: CPU skinning matches.
            BakedMesh skinned = cosmetic.layers()[2].skin().update(0, time);
            float[] d = skinned.data();
            for (int q = 0; q < 3; q++) {
                for (int c = 0; c < 4; c++) {
                    int o = (q * 4 + c) * BakedMesh.FLOATS_PER_VERTEX;
                    int source = tris[(4 + q) * 3 + Math.min(c, 2)];
                    assertNear(skin(block, source, anim.frames(), offset), new float[]{d[o], d[o + 1], d[o + 2]},
                            "frame " + frame + " skinned triangle " + q + " corner " + c);
                    float length = (float) Math.sqrt(d[o + 5] * d[o + 5] + d[o + 6] * d[o + 6] + d[o + 7] * d[o + 7]);
                    assertEquals(1f, length, 1e-4f, "normals stay unit length");
                }
            }
        }
    }

    @Test
    void playbackInterpolatesLoopsAndHolds() {
        BakedCosmetic looping = bake("{\"model\":\"t:models/m.md5mesh\",\"bone_animation\":{\"file\":\"t:models/idle.md5anim\","
                + "\"speed\":2}}", "");
        MeshRig rig = looping.layers()[0].rig();
        assertEquals(0.1f, rig.clipTime(0, 0.05, 1f), 1e-6f, "speed 2");
        assertEquals(0.05f, rig.clipTime(0, 0.175, 1f), 1e-5f, "3 frames at 10 fps loop every 0.3 s");
        assertEquals(0f, rig.clipTime(0, 0.175, 0f), "animation off holds the first frame");
        Md5Parser.AnimFile anim = Md5Parser.parseAnim(ANIM, "a");
        // Halfway between frames 0 and 1, joint b's position is halfway too.
        float[] mid = rig.evaluate(0, 0.05f).clone();
        for (int k = 0; k < 3; k++) {
            assertEquals((anim.frames()[14 + k] + anim.frames()[21 + 14 + k]) / 2, mid[14 + k], 1e-4f);
        }
        // Past the last frame a looping clip blends back towards the first.
        float[] wrap = rig.evaluate(0, 0.25f);
        assertEquals((anim.frames()[42 + 14] + anim.frames()[14]) / 2, wrap[14], 1e-4f);

        BakedCosmetic once = bake("{\"model\":\"t:models/m.md5mesh\",\"bone_animation\":{\"file\":\"t:models/idle.md5anim\","
                + "\"loop\":false}}", "");
        MeshRig held = once.layers()[0].rig();
        assertEquals(0.2f, held.clipTime(0, 99, 1f), 1e-6f, "a clip that doesn't loop stays on its last frame");
        assertArrayEquals(java.util.Arrays.copyOfRange(anim.frames(), 42, 63), held.evaluate(0, 0.2f));
    }

    @Test
    void namedAnimationsBecomeStyles() {
        String mesh = "{\"model\":\"t:models/m.md5mesh\",\"bone_animations\":{\"idle\":\"t:models/idle.md5anim\","
                + "\"wave\":{\"file\":\"t:models/wave.md5anim\",\"loop\":false}}}";
        BakedCosmetic cosmetic = bake(mesh, "");
        assertArrayEquals(new String[]{"idle", "wave"}, cosmetic.styles());
        assertEquals(0, cosmetic.defaultStyle());
        MeshRig rig = cosmetic.layers()[0].rig();
        assertEquals(1, rig.clipForStyle(1));
        assertEquals(1, cosmetic.styleIndex("wave"));
        assertEquals(0, cosmetic.styleIndex("nope"), "unknown styles fall back to the default");
        assertEquals(0, cosmetic.styleIndex(null));
        assertTrue(rig.evaluate(1, 0.1f)[15] != rig.evaluate(0, 0.1f)[15], "each style plays its own clip");

        BakedCosmetic waving = bake(mesh, ",\"default_style\":\"wave\"");
        assertEquals(1, waving.defaultStyle());
        assertEquals(1, waving.styleIndex(null));
        assertThrows(JsonParseException.class, () -> bake(mesh, ",\"default_style\":\"dance\""));
    }

    @Test
    void skinningAllocatesNothingPerFrame() {
        SkinnedMesh skin = bake("{\"model\":\"t:models/m.md5mesh\",\"bone_animation\":{\"file\":\"t:models/idle.md5anim\"}}", "")
                .layers()[2].skin();
        BakedMesh first = skin.update(0, 0.01f);
        for (int i = 0; i < 20_000; i++) {
            skin.update(0, (i % 300) / 1000f); // warm up the JIT
        }
        var threads = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        long thread = Thread.currentThread().threadId();
        long before = threads.getThreadAllocatedBytes(thread);
        for (int i = 0; i < 20_000; i++) {
            assertSame(first, skin.update(0, (i % 300) / 1000f));
        }
        long allocated = threads.getThreadAllocatedBytes(thread) - before;
        assertTrue(allocated < 16_384, "skinning 20 000 times allocated " + allocated + " bytes");
    }

    @Test
    void windingAndMirroring() {
        // A closed tetrahedron written clockwise (the id Tech convention) is turned to face outwards.
        String tetra = """
                MD5Version 10
                numJoints 1
                numMeshes 1
                joints {
                  "root" -1 ( 0 0 0 ) ( 0 0 0 )
                }
                mesh {
                  numverts 4
                  vert 0 ( 0 0 ) 0 1
                  vert 1 ( 1 0 ) 1 1
                  vert 2 ( 0 1 ) 2 1
                  vert 3 ( 1 1 ) 3 1
                  numtris 4
                  tri 0 0 1 2
                  tri 1 0 3 1
                  tri 2 0 2 3
                  tri 3 1 3 2
                  numweights 4
                  weight 0 0 1 ( 0 0 0 )
                  weight 1 0 1 ( 4 0 0 )
                  weight 2 0 1 ( 0 4 0 )
                  weight 3 0 1 ( 0 0 4 )
                }
                """;
        for (String winding : new String[]{"auto", "ccw"}) {
            List<String> warnings = new ArrayList<>();
            CosmeticDefinition def = CosmeticParser.parse("t:x", new StringReader("{\"slot\":\"head\",\"texture\":\"t:a.png\","
                    + "\"parts\":[{\"meshes\":[{\"model\":\"t:t.md5mesh\",\"winding\":\"" + winding + "\"}]}]}"),
                    context(Map.of("t:t.md5mesh", tetra), warnings));
            CosmeticDefinition.Mesh mesh = def.parts().get(0).meshes().get(0);
            float[] c = mesh.corners();
            int outward = 0;
            for (int f = 0; f < 4; f++) {
                int o = f * 3 * 8;
                float cx = (c[o] + c[o + 8] + c[o + 16]) / 3, cy = (c[o + 1] + c[o + 9] + c[o + 17]) / 3,
                        cz = (c[o + 2] + c[o + 10] + c[o + 18]) / 3;
                // Outward = away from the centre (1, 1, 1).
                if ((cx - 1) * c[o + 5] + (cy - 1) * c[o + 6] + (cz - 1) * c[o + 7] > 0) {
                    outward++;
                }
            }
            assertEquals(winding.equals("auto") ? 4 : 0, outward, winding);
        }

        // A mirroring placement draws the corners the other way round but keeps the file's normals.
        CosmeticDefinition.Rigged plain = definition("{\"model\":\"t:models/m.md5mesh\",\"bone_animation\":"
                + "{\"file\":\"t:models/idle.md5anim\"}}", "").parts().get(0).meshes().get(0).rigged();
        CosmeticDefinition.Rigged mirrored = definition("{\"model\":\"t:models/m.md5mesh\",\"mirror\":true,\"bone_animation\":"
                + "{\"file\":\"t:models/idle.md5anim\"}}", "").parts().get(0).meshes().get(0).rigged();
        float[] a = plain.pieces().get(0).corners();
        float[] b = mirrored.pieces().get(0).corners();
        assertArrayEquals(java.util.Arrays.copyOfRange(a, 8, 16), java.util.Arrays.copyOfRange(b, 16, 24));
        assertArrayEquals(java.util.Arrays.copyOfRange(a, 5, 8), java.util.Arrays.copyOfRange(b, 5, 8));
        assertEquals(-1f, mirrored.rig().scale()[0]);
    }

    @Test
    void placementRotationMatchesCubeRotation() {
        float[] degrees = {30, -45, 70};
        float[] m = CosmeticBaker.rotationMatrix(degrees);
        float[] q = RigBuilder.quaternion(degrees);
        float[] v = {1, 2, 3};
        float[] expected = CosmeticBaker.rotate(m, v[0], v[1], v[2]);
        assertNear(expected, rotate(q, 0, v), "the rig rotates like cubes and still meshes do");
    }

    @Test
    void submeshesUvFlipAndMistakes() {
        BakedCosmetic flipped = bake("{\"model\":\"t:models/m.md5mesh\",\"flip_v\":true,\"submesh\":\"body\"}", "");
        BakedCosmetic plain = bake("{\"model\":\"t:models/m.md5mesh\",\"submesh\":\"0\"}", "");
        assertEquals(1f - plain.layers()[0].mesh().data()[4], flipped.layers()[0].mesh().data()[4], 1e-6f);

        String[][] bad = {
                {"{\"model\":\"t:models/m.md5mesh\",\"submesh\":\"wings\"}", "no mesh matches"},
                {"{\"model\":\"t:models/m.md5mesh\",\"bone_animation\":{\"file\":\"t:models/m.md5mesh\"}}", ".md5anim"},
                {"{\"model\":\"t:models/m.md5mesh\",\"bone_animation\":{}}", "file"},
                {"{\"model\":\"t:models/m.md5mesh\",\"bone_animation\":\"t:models/idle.md5anim\","
                        + "\"bone_animations\":{\"a\":\"t:models/idle.md5anim\"}}", "not both"},
                {"{\"model\":\"t:models/m.md5mesh\",\"bone_animations\":{\"Bad Name\":\"t:models/idle.md5anim\"}}", "name"},
                {"{\"model\":\"t:models/m.md5mesh\",\"winding\":\"left\"}", "winding"},
                {"{\"model\":\"t:models/a.obj\",\"submesh\":\"x\"}", "only apply to .md5mesh"},
                {"{\"model\":\"t:models/m.md5mesh\",\"group\":\"x\"}", "only applies to .obj"},
        };
        for (String[] entry : bad) {
            JsonParseException e = assertThrows(JsonParseException.class, () -> bake(entry[0], ""), entry[0]);
            assertTrue(e.getMessage().contains(entry[1]), e.getMessage());
        }
    }

    @Test
    void textureCoordinatesRoundedJustOutsideAreKept() {
        // Badlion's cat pets: "vert 27 has texture coordinate (-0.0024510198, 1.0)".
        String mesh = MESH.replace("vert 0 ( 0 0 )", "vert 0 ( -0.0024510198 1.0 )");
        CosmeticDefinition definition = CosmeticParser.parse("t:x", new StringReader(
                "{\"slot\":\"head\",\"texture\":\"t:a.png\",\"parts\":[{\"meshes\":[{\"model\":\"t:m.md5mesh\"}]}]}"),
                context(Map.of("t:m.md5mesh", mesh), new ArrayList<>()));
        assertEquals(1, definition.parts().get(0).meshes().size());
    }

    @Test
    void brokenFilesAreClearErrors() {
        String[][] bad = {
                {"MD5Version 11\n", "only 10"},
                {"hello", "not an MD5 file"},
                {MESH.replace("weight 11 2 0.75", "weight 11 7 0.75"), "joint 7"},
                {MESH.replace("tri 6 0 4 1", "tri 6 0 4 12"), "vert 12"},
                {MESH.replace("vert 9 ( 0.75 0.25 ) 10 2", "vert 9 ( 0.75 0.25 ) 11 2"), "weights outside"},
                {MESH.replace("\"b\" 1 (", "\"b\" 2 ("), "parent"},
                {MESH.replace("vert 0 ( 0 0 )", "vert 0 ( 1.5 0 )"), "outside 0..1"},
                {MESH.replace("numMeshes 1", "numMeshes 2"), "numMeshes"},
        };
        for (String[] entry : bad) {
            JsonParseException e = assertThrows(JsonParseException.class, () -> CosmeticParser.parse("t:x",
                    new StringReader("{\"slot\":\"head\",\"texture\":\"t:a.png\",\"parts\":[{\"meshes\":[{\"model\":\"t:m.md5mesh\"}]}]}"),
                    context(Map.of("t:m.md5mesh", entry[0]), new ArrayList<>())), entry[1]);
            assertTrue(e.getMessage().contains(entry[1]), e.getMessage());
        }
        String mismatch = ANIM.replace("\"b\" 1 1 3", "\"c\" 1 1 3");
        JsonParseException e = assertThrows(JsonParseException.class, () -> CosmeticParser.parse("t:x", new StringReader(
                "{\"slot\":\"head\",\"texture\":\"t:a.png\",\"parts\":[{\"meshes\":[{\"model\":\"t:m.md5mesh\","
                        + "\"bone_animation\":\"t:a.md5anim\"}]}]}"),
                context(Map.of("t:m.md5mesh", MESH, "t:a.md5anim", mismatch), new ArrayList<>())));
        assertTrue(e.getMessage().contains("'c'"), e.getMessage());
    }

    @Test
    void bundledPennantSplitsItsFlagAndFacesOutwards() throws Exception {
        String base = "assets/cosmeticsmod/";
        java.util.function.Function<String, String> read = path -> {
            try (var in = getClass().getClassLoader().getResourceAsStream(base + path)) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (Exception e) {
                throw new AssertionError(path, e);
            }
        };
        String dir = "cosmeticsmod:models/cosmetic/pennant/";
        CosmeticDefinition definition = CosmeticParser.parse("cosmeticsmod:wind_pennant",
                new StringReader(read.apply("cosmetics/wind_pennant.json")), context(Map.of(
                        dir + "pennant.md5mesh", read.apply("models/cosmetic/pennant/pennant.md5mesh"),
                        dir + "wave.md5anim", read.apply("models/cosmetic/pennant/wave.md5anim"),
                        dir + "calm.md5anim", read.apply("models/cosmetic/pennant/calm.md5anim")), new ArrayList<>()));
        CosmeticDefinition.Mesh pole = definition.parts().get(0).meshes().get(0);
        assertNull(pole.rigged(), "the pole doesn't move");
        float[] c = pole.corners();
        for (int f = 0; f < pole.faceSizes().length; f++) {
            int o = f * 3 * 8;
            float cx = (c[o] + c[o + 8] + c[o + 16]) / 3 - 3, cy = (c[o + 1] + c[o + 9] + c[o + 17]) / 3 - 1,
                    cz = (c[o + 2] + c[o + 10] + c[o + 18]) / 3 - 3;
            assertTrue(cx * c[o + 5] + cy * c[o + 6] + cz * c[o + 7] > 0, "pole face " + f + " faces outwards");
        }
        CosmeticDefinition.Rigged flag = definition.parts().get(1).meshes().get(0).rigged();
        assertEquals(2, flag.pieces().size(), "the columns between single-joint vertices are rigid");
        assertEquals(8 * 3, flag.skinned().triangles().length, "the columns next to blended vertices are skinned");
        BakedCosmetic cosmetic = CosmeticBaker.bake(definition);
        assertArrayEquals(new String[]{"wave", "calm"}, cosmetic.styles());
        assertEquals(0xE04848, cosmetic.defaultColor());
        assertTrue(!cosmetic.layers()[0].tinted() && cosmetic.layers()[1].tinted());
    }
}
