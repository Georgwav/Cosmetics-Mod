package io.github.georgwav.cosmetics.melee;

import io.github.georgwav.cosmetics.model.CosmeticDefinition;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnifeViewTest {
    private static Knife standIn(float[] camera) {
        KnifeGeo g = KnifeGeo.parse(StandInKnife.geo(), "standin");
        KnifeRig rig = new KnifeRig(g, KnifeClip.parseAll(StandInKnife.animations(), g));
        var spec = new CosmeticDefinition.Melee("t:m.json", "t:a.json", "t", "", 1f, camera, CosmeticDefinition.Melee.RANDOM,
                null, null, null);
        return new Knife("t:melee/standin", spec, "t:x.png", rig);
    }

    private static final KnifeBehaviour.Sounds QUIET = new KnifeBehaviour.Sounds() {
        @Override
        public void play(String key, long owner) {
        }

        @Override
        public void stop(long owner) {
        }
    };

    @Test
    void theArmTemplateIsThePlayersArmWithTheFistAtTheTop() {
        for (boolean slim : new boolean[]{false, true}) {
            float[] q = ArmGeo.quads(true, slim);
            float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
            float minZ = Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
            // The arm's own faces (the first six; the sleeve is a quarter pixel bigger).
            for (int f = 0; f < 6; f++) {
                for (int k = 0; k < 4; k++) {
                    int i = f * KnifeGeo.QUAD + k * 5;
                    minX = Math.min(minX, q[i]);
                    maxX = Math.max(maxX, q[i]);
                    minY = Math.min(minY, q[i + 1]);
                    maxY = Math.max(maxY, q[i + 1]);
                    minZ = Math.min(minZ, q[i + 2]);
                    maxZ = Math.max(maxZ, q[i + 2]);
                }
            }
            float half = slim ? 1.5f : 2f;
            assertEquals(-6f - half, minX, 1e-4f, "slim " + slim);
            assertEquals(-6f + half, maxX, 1e-4f);
            assertEquals(0f, minY, 1e-4f);
            assertEquals(12f, maxY, 1e-4f);
            assertEquals(-2f, minZ, 1e-4f);
            assertEquals(2f, maxZ, 1e-4f);
            assertEquals(12, q.length / KnifeGeo.QUAD, "the arm and its sleeve");
        }
        // The fist end is the skin's hand end: the right arm's "down" picture (48, 16) to (52, 20) at y 12.
        float[] q = ArmGeo.quads(true, false);
        boolean found = false;
        for (int f = 0; f < 6; f++) {
            int o = f * KnifeGeo.QUAD;
            boolean top = true;
            float u = 0, v = 0;
            for (int k = 0; k < 4; k++) {
                top &= Math.abs(q[o + k * 5 + 1] - 12f) < 1e-4f;
                u += q[o + k * 5 + 3] / 4f;
                v += q[o + k * 5 + 4] / 4f;
            }
            if (top) {
                found = true;
                assertEquals(50f / 64f, u, 1e-4f);
                assertEquals(18f / 64f, v, 1e-4f);
            }
        }
        assertTrue(found);
        // The left arm is mirrored.
        float[] l = ArmGeo.quads(false, false);
        float lx = Float.MAX_VALUE;
        for (int f = 0; f < 6; f++) {
            for (int k = 0; k < 4; k++) {
                lx = Math.min(lx, l[f * KnifeGeo.QUAD + k * 5]);
            }
        }
        assertEquals(4f, lx, 1e-4f);
    }

    @Test
    void theTextureIsCutAtHalfAlphaAndItsBrightQuarterAveraged() {
        int[] px = {0x40FFFFFF, 0xFF101010, 0xFF202020, 0xFF303030, 0xC0F0E0D0, 0x7FFFFFFF, 0xFF000000, 0x00000000};
        int[] out = new int[px.length];
        int bright = Knife.cut(px, out);
        assertEquals(0, out[0], "under half alpha: gone");
        assertEquals(0xFFF0E0D0, out[4], "over half: solid");
        assertEquals(0, out[5]);
        assertEquals(0xFF101010, out[1]);
        // Five solid pixels: the brightest quarter is the light one.
        assertEquals(0xFFF0E0D0, bright);
    }

    @Test
    void theEyeTakesTheCameraFixAndTheKnifeRestsAfterItsDraw() {
        KnifeView v = new KnifeView(standIn(new float[]{1f, -1f, 4f}), new Random(1), QUIET);
        assertEquals(1f, v.eye[0], 1e-4f);
        assertEquals(63f, v.eye[1], 1e-4f);
        assertEquals(4f, v.eye[2], 1e-4f);
        v.behaviour.equip(0f, 0.625f, true, 0);
        v.frame(0.1f, true);
        assertTrue(v.behaviour.drawing());
        v.frame(2f, true);
        assertEquals(KnifeBehaviour.Kind.NONE, v.behaviour.kind(), "at rest once the draw is done");
    }
}
