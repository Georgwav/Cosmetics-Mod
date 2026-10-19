package io.github.georgwav.cosmetics.melee;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnifeRigTest {
    /** A rig with a right hand holding only its arm, and a blade cube in the fist missing it by {@code dx} px across. */
    private static KnifeRig rig(float dx, String anims) {
        // The arm's fist in the righthand_pos frame is at (-6, 12, 0) (knife space); put the blade's long axis (along Z)
        // through (-6 + dx, 12, 0): Bedrock x is the negative.
        float bx = 6f - dx;
        String geo = "{\"minecraft:geometry\":[{\"description\":{\"texture_width\":16,\"texture_height\":16},\"bones\":["
                + "{\"name\":\"root\",\"pivot\":[0,0,0]},"
                + "{\"name\":\"righthand\",\"parent\":\"root\",\"pivot\":[0,0,0]},"
                + "{\"name\":\"righthand_pos\",\"parent\":\"righthand\",\"pivot\":[0,0,0]},"
                + "{\"name\":\"weapon\",\"parent\":\"root\",\"pivot\":[0,0,0],\"cubes\":["
                + "{\"origin\":[" + (bx - 0.5f) + ",11.5,-10],\"size\":[1,1,4],\"uv\":[0,0]},"
                + "{\"origin\":[" + (bx - 0.5f) + ",11.5,-4],\"size\":[1,1,4],\"uv\":[0,0]},"
                + "{\"origin\":[" + (bx - 0.5f) + ",11.5,2],\"size\":[1,1,4],\"uv\":[0,0]},"
                + "{\"origin\":[" + (bx - 0.5f) + ",11.5,8],\"size\":[1,1,4],\"uv\":[0,0]}]}]}]}";
        KnifeGeo g = KnifeGeo.parse(JsonParser.parseString(geo).getAsJsonObject(), "test");
        Map<String, KnifeClip> clips = KnifeClip.parseAll(JsonParser.parseString("{\"animations\":{" + anims + "}}").getAsJsonObject(), g);
        return new KnifeRig(g, clips);
    }

    @Test
    void theGripNudgeMovesTheArmOntoTheHandleAtMostTwoPixels() {
        assertEquals(1f, rig(1f, "\"static_idle\":{\"bones\":{}}").grip[0], 0.05f);
        assertEquals(0f, rig(0.2f, "\"static_idle\":{\"bones\":{}}").grip[0], 1e-6f, "under 0.4 px is left alone");
        assertEquals(2f, rig(3f, "\"static_idle\":{\"bones\":{}}").grip[0], 1e-6f, "capped at 2 px");
    }

    @Test
    void handsAPackHidesAreShownAtTheScaleItsOtherAnimationsUse() {
        KnifeRig r = rig(0f, "\"static_idle\":{\"bones\":{\"righthand\":{\"scale\":0}}},"
                + "\"draw\":{\"animation_length\":1,\"bones\":{\"righthand\":{\"scale\":[1,1.5,1]}}}");
        int hand = r.geo.bone("righthand");
        assertNotNull(r.handFix[hand]);
        assertArrayEquals(new float[]{1f, 1.5f, 1f}, r.handFix[hand], 1e-6f);
        assertNull(r.handFix[r.geo.bone("weapon")], "a bone with cubes isn't a hand");
        KnifePose pose = new KnifePose(r.geo);
        pose.handFix = r.handFix;
        pose.pose(r.staticIdle, 0f, null, 0f, null, 0f, 1f, null);
        assertTrue(pose.scaleOf(hand) > 0.9f, "the idle that hides the hand shows it");
    }

    @Test
    void theStandInKnifeHasEveryPart() {
        JsonObject anims = StandInKnife.animations();
        KnifeGeo g = KnifeGeo.parse(StandInKnife.geo(), "standin");
        KnifeRig r = new KnifeRig(g, KnifeClip.parseAll(anims, g));
        assertEquals(2, r.draws.size());
        assertEquals(2, r.inspects.size());
        assertNotNull(r.light1);
        assertNotNull(r.light2);
        assertNotNull(r.heavy);
        assertNotNull(r.idle);
        float[] t = r.timing.get("melee_1");
        assertTrue(t[0] > 0.1f && t[0] < 0.2f, "the slash effect marks the impact: " + t[0]);
        assertTrue(t[1] > t[0] && t[1] <= 0.9f, "settles before its end: " + t[1]);
        assertEquals(64f, r.eye[1], 1e-4f);
    }
}
