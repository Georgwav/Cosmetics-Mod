package io.github.georgwav.cosmetics.render;

import com.google.gson.JsonParseException;
import io.github.georgwav.cosmetics.model.CosmeticDefinition;
import io.github.georgwav.cosmetics.model.CosmeticParser;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WingMotionTest {
    private static final long FRAME = 16_666_667L; // 60 fps

    private static CosmeticDefinition.Wings wings(String json) {
        return CosmeticParser.parse("t:w", new StringReader("{\"slot\":\"back\",\"texture\":\"t:a.png\",\"wings\":" + json
                + ",\"parts\":[{\"wing\":\"left\",\"wing_pivot\":[1,2,3],\"cubes\":[{\"from\":[0,0,0],\"to\":[1,1,1],\"uv\":[0,0]}]}]}"))
                .wings();
    }

    /** Runs a player for {@code seconds} and returns the largest angle change between two frames. */
    private static float run(UUID player, CosmeticDefinition.Wings wings, long[] clock, double seconds, float speed,
                             boolean sneaking, boolean flying) {
        float last = Float.NaN;
        float biggestStep = 0f;
        for (long end = clock[0] + (long) (seconds * 1e9); clock[0] < end; clock[0] += FRAME) {
            float angle = WingMotion.pose(player, speed, sneaking, flying, wings, clock[0]).angle;
            if (!Float.isNaN(last)) {
                biggestStep = Math.max(biggestStep, Math.abs(angle - last));
            }
            last = angle;
        }
        return biggestStep;
    }

    @Test
    void flappingFollowsWhatThePlayerDoesWithoutJumps() {
        CosmeticDefinition.Wings wings = wings("{\"idle\":{\"amplitude\":5,\"speed\":0.5},\"moving\":{\"amplitude\":20,"
                + "\"speed\":1.5},\"flying\":{\"amplitude\":40,\"speed\":3},\"base\":10}");
        UUID player = UUID.randomUUID();
        long[] clock = {1_000_000_000L};
        run(player, wings, clock, 2, 0f, false, false);
        double idlePhase = WingMotion.pose(player, 0f, false, false, wings, clock[0]).phase;
        run(player, wings, clock, 1, 0f, false, false);
        double idleRate = WingMotion.pose(player, 0f, false, false, wings, clock[0]).phase - idlePhase;
        assertEquals(0.5, idleRate, 0.01, "standing still flaps at the idle speed");

        // Start flying: the speed ramps up, and no frame jumps by more than a smooth flap allows.
        float step = run(player, wings, clock, 3, 0f, false, true);
        double before = WingMotion.pose(player, 0f, false, true, wings, clock[0]).phase;
        run(player, wings, clock, 1, 0f, false, true);
        double flyRate = WingMotion.pose(player, 0f, false, true, wings, clock[0]).phase - before;
        assertEquals(3.0, flyRate, 0.05, "flying flaps at the flying speed once settled");
        // A 40 degree flap at 3 per second moves at most 2 * pi * 3 * 40 / 60 = 12.6 degrees per frame.
        assertTrue(step < 13f, "largest step " + step);

        // Walking at full speed settles on the moving flap and its amplitude.
        run(player, wings, clock, 3, 0.3f, false, false);
        float max = -999f;
        float min = 999f;
        for (int i = 0; i < 120; i++, clock[0] += FRAME) {
            float angle = WingMotion.pose(player, 0.3f, false, false, wings, clock[0]).angle;
            max = Math.max(max, angle);
            min = Math.min(min, angle);
        }
        assertEquals(30f, max, 0.5f, "base + moving amplitude");
        assertEquals(-10f, min, 0.5f, "base - moving amplitude");
    }

    @Test
    void sneakingFoldsAndReportsHowMuch() {
        CosmeticDefinition.Wings wings = wings("{\"base\":20}");
        UUID player = UUID.randomUUID();
        long[] clock = {5_000_000_000L};
        run(player, wings, clock, 3, 0f, true, false);
        WingMotion.Pose pose = WingMotion.pose(player, 0f, true, false, wings, clock[0]);
        assertEquals(1f, pose.sneak, 0.01f);
        assertEquals(10f, pose.base, 0.1f, "sneaking rests 10 degrees more folded than the other states");
    }

    @Test
    void drawingTwiceInOneFrameDoesNotAdvance() {
        CosmeticDefinition.Wings wings = wings("{}");
        UUID player = UUID.randomUUID();
        WingMotion.pose(player, 0f, false, false, wings, 1_000L);
        double phase = WingMotion.pose(player, 0f, false, false, wings, 1_000L + FRAME).phase;
        // The shadow pass draws the player again in the same frame.
        assertEquals(phase, WingMotion.pose(player, 0f, false, false, wings, 1_000L + FRAME).phase);
    }

    @Test
    void parsing() {
        CosmeticDefinition.Wings wings = wings("{\"axis\":\"z\",\"offset\":[0,1,2],\"sneak_offset\":[0,0,-1],"
                + "\"armor_offset\":[0,0,1.5],\"spread\":2,\"flying\":{\"speed\":4}}");
        assertEquals(2, wings.axis());
        assertArrayEquals(new float[]{0, 1, 2}, wings.offset());
        assertArrayEquals(new float[]{0, 0, 1.5f}, wings.armorOffset());
        assertEquals(2f, wings.spread());
        assertEquals(4f, wings.flying().speed());
        var cosmetic = io.github.georgwav.cosmetics.model.CosmeticBaker.bake(CosmeticParser.parse("t:w", new StringReader(
                "{\"slot\":\"back\",\"texture\":\"t:a.png\",\"wings\":{},\"parts\":["
                        + "{\"wing\":\"left\",\"wing_pivot\":[1,2,3],\"cubes\":[{\"from\":[0,0,0],\"to\":[1,1,1],\"uv\":[0,0]}]},"
                        + "{\"wing\":\"right\",\"cubes\":[{\"from\":[0,0,0],\"to\":[1,1,1],\"uv\":[0,0]}]},"
                        + "{\"cubes\":[{\"from\":[0,0,0],\"to\":[1,1,1],\"uv\":[0,0]}]}]}")));
        assertEquals(3, cosmetic.layers().length, "each wing is its own layer");
        assertEquals(new CosmeticDefinition.WingPart(1, 1, 2, 3), cosmetic.layers()[0].wing());
        assertEquals(-1, cosmetic.layers()[1].wing().side());
        assertEquals(null, cosmetic.layers()[2].wing());
        assertTrue(cosmetic.hasAnimation(), "the animation settings apply to flapping");
        assertEquals(CosmeticParser.parse("t:w", new StringReader("{\"slot\":\"back\",\"texture\":\"t:a.png\","
                + "\"parts\":[{\"cubes\":[{\"from\":[0,0,0],\"to\":[1,1,1],\"uv\":[0,0]}]}]}")).wings(), null);
        for (String bad : new String[]{"[]", "{\"axis\":\"w\"}", "{\"idle\":3}", "{\"moving\":{\"speed\":-1}}"}) {
            assertThrows(JsonParseException.class, () -> wings(bad), bad);
        }
        assertThrows(JsonParseException.class, () -> CosmeticParser.parse("t:w", new StringReader(
                "{\"slot\":\"back\",\"texture\":\"t:a.png\",\"parts\":[{\"wing\":\"middle\",\"cubes\":[]}]}")));
    }
}
