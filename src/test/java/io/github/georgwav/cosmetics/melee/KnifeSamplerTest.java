package io.github.georgwav.cosmetics.melee;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnifeSamplerTest {
    private static final KnifeGeo GEO = KnifeGeo.parse(JsonParser.parseString(
            "{\"minecraft:geometry\":[{\"description\":{},\"bones\":[{\"name\":\"a\",\"pivot\":[0,0,0]},"
                    + "{\"name\":\"b\",\"parent\":\"a\",\"pivot\":[0,10,0],\"rotation\":[90,0,0]},"
                    + "{\"name\":\"1p_effect\",\"parent\":\"a\",\"pivot\":[0,0,0],\"cubes\":[{\"origin\":[0,0,0],\"size\":[1,1,1],\"uv\":[0,0]}]}]}]}")
            .getAsJsonObject(), "test");

    private static KnifeClip clip(String bones) {
        JsonObject a = JsonParser.parseString("{\"animation_length\":2,\"bones\":{" + bones + "}}").getAsJsonObject();
        return KnifeClip.parse("t", a, GEO);
    }

    private static float x(KnifeClip.Channel c, float t) {
        float[] out = new float[3];
        c.sample(t, out);
        return out[0];
    }

    @Test
    void linearRunsFromPostToPre() {
        KnifeClip c = clip("\"a\":{\"position\":{\"0\":{\"pre\":[0,0,0],\"post\":[10,0,0]},\"1\":{\"pre\":[20,0,0],\"post\":[99,0,0]}}}");
        KnifeClip.Channel p = c.position[GEO.bone("a")];
        assertEquals(15f, x(p, 0.5f), 1e-4f);
        assertEquals(0f, x(p, -1f), 1e-4f, "before the first key: its pre");
        assertEquals(99f, x(p, 1.5f), 1e-4f, "after the last key: its post");
    }

    @Test
    void catmullromStartsFromTheStartKeysPre() {
        // TACZ's quirk: the segment begins at the start key's pre (5), not its post (50).
        KnifeClip c = clip("\"a\":{\"position\":{\"0\":{\"pre\":[5,0,0],\"post\":[50,0,0],\"lerp_mode\":\"catmullrom\"},"
                + "\"1\":{\"pre\":[20,0,0],\"post\":[20,0,0],\"lerp_mode\":\"catmullrom\"}}}");
        KnifeClip.Channel p = c.position[GEO.bone("a")];
        assertEquals(5f, x(p, 1e-6f), 1e-3f);
        assertEquals(20f, x(p, 1f - 1e-6f), 1e-2f);
    }

    @Test
    void stepHoldsUntilTheNextKey() {
        KnifeClip c = clip("\"a\":{\"position\":{\"0\":{\"post\":[3,0,0],\"lerp_mode\":\"step\"},\"1\":[9,0,0]}}");
        KnifeClip.Channel p = c.position[GEO.bone("a")];
        assertEquals(3f, x(p, 0.99f), 1e-4f);
        assertEquals(9f, x(p, 1.01f), 1e-4f);
    }

    @Test
    void scaleZeroHidesAndBlendsMultiplyScale() {
        KnifePose pose = new KnifePose(GEO);
        int a = GEO.bone("a");
        KnifeClip hide = clip("\"a\":{\"scale\":0}");
        pose.pose(null, 0f, null, 0f, hide, 0f, 1f, null);
        assertTrue(pose.scaleOf(a) < 0.01f);
        KnifeClip half = clip("\"a\":{\"scale\":0.5}");
        KnifePose.Blend bl = new KnifePose.Blend();
        bl.clip = clip("\"a\":{\"scale\":0.5}");
        bl.weight = 1f;
        pose.pose(null, 0f, null, 0f, half, 0f, 1f, new KnifePose.Blend[]{bl});
        assertEquals(0.25f, pose.scaleOf(a), 1e-5f, "the blended track multiplies the main's scale");
    }

    @Test
    void effectsShowOnlyWhileAnAnimationScalesThem() {
        KnifePose pose = new KnifePose(GEO);
        int fx = GEO.bone("1p_effect");
        pose.pose(null, 0f, null, 0f, null, 0f, 1f, null);
        assertFalse(pose.shown(fx), "an effect nobody scales stays hidden");
        pose.pose(null, 0f, null, 0f, clip("\"1p_effect\":{\"scale\":{\"0\":[0,0,0],\"1\":[1,1,1]}}"), 1f, 1f, null);
        assertTrue(pose.shown(fx));
    }

    @Test
    void animationRotationComesAfterTheBindRotation() {
        // Bone b has a bind rotation of 90° about X; the animation turns it 90° about Z on top, in b's own frame.
        KnifePose pose = new KnifePose(GEO);
        int b = GEO.bone("b");
        pose.pose(null, 0f, null, 0f, clip("\"b\":{\"rotation\":[0,0,90]}"), 0f, 1f, null);
        float[] p = new float[3];
        float[] o = new float[3];
        pose.transform(b, 1f, 0f, 0f, p);
        pose.origin(b, o);
        // TACZ: R = Rx(-90) (the bind; knife space negates X) · Rz(90) (the animation). Local X → Rz(90) → +Y →
        // Rx(-90) → -Z. Adding the angles instead (Blockbench) would give Rz(90)·Rx(-90): local X → +Y.
        assertEquals(0f, p[0] - o[0], 1e-4f);
        assertEquals(0f, p[1] - o[1], 1e-4f);
        assertEquals(-1f, p[2] - o[2], 1e-4f);
    }

    @Test
    void soundsAreReadInOrder() {
        JsonObject a = JsonParser.parseString("{\"animation_length\":1,\"bones\":{},\"sound_effects\":{\"0.5\":{\"effect\":\"x:b\"},"
                + "\"0.0\":[{\"effect\":\"x:a\"},{\"effect\":\"x:a2\"}]}}").getAsJsonObject();
        KnifeClip c = KnifeClip.parse("t", a, GEO);
        assertEquals(3, c.sounds.length);
        assertEquals("x:a", c.sounds[0]);
        assertEquals(0.5f, c.soundTimes[2], 1e-6f);
    }

    @Test
    void parsesEveryAnimationInAFile() {
        Map<String, KnifeClip> all = KnifeClip.parseAll(JsonParser.parseString(
                "{\"animations\":{\"static_idle\":{\"bones\":{}},\"draw\":{\"animation_length\":0.5,\"bones\":{}}}}").getAsJsonObject(), GEO);
        assertEquals(2, all.size());
        assertEquals(0.5f, all.get("draw").length, 1e-6f);
    }
}
