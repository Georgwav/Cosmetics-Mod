package io.github.georgwav.cosmetics.sol.fx.effects;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Elytra contrails (C.4): they come from the wing tips, spread wide in level flight and close in as the wings fold. */
class TrailsTest {
    private static double spread(double[] tips) {
        double dx = tips[3] - tips[0], dy = tips[4] - tips[1], dz = tips[5] - tips[2];
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    @Test
    void contrailsSpreadInLevelFlightAndCloseInADive() {
        double[] level = new double[6], dive = new double[6];
        // Facing south (yaw 0), gliding level.
        Vec3 ahead = new Vec3(0, 0, 1);
        Trails.wingTips(0, 64, 0, ahead, new Vec3(0, 0, 1.5), 0f, level);
        // The wings spread out to the sides (east and west), about a block and a half each, level with each other.
        assertEquals(25.0 / 8.0, spread(level), 1e-6);
        assertEquals(level[1], level[4], 1e-9);
        assertTrue(level[0] > 1.4 && level[3] < -1.4, "left tip to the east, right to the west");
        // Over the shoulders, a block and a half ahead of the feet, just above the back.
        assertEquals(1.5, level[2], 1e-6);
        assertTrue(level[1] > 64 && level[1] < 64.2);

        // Diving steeply: the wings fold back along the body.
        Vec3 down = new Vec3(0, -0.8, 0.6);
        Trails.wingTips(0, 64, 0, down, new Vec3(0, -1.6, 1.2), 0f, dive);
        assertTrue(spread(dive) < spread(level) - 0.8, "folded wings are closer: " + spread(dive));
        // Still either side of the body, mirror images.
        assertEquals(-dive[0], dive[3], 1e-9);
        assertEquals(dive[1], dive[4], 1e-9);
        assertEquals(dive[2], dive[5], 1e-9);
        // Nearer the feet than in level flight: they hang back along the body.
        double levelAlong = level[2], diveAlong = dive[2] * 0.6 - (dive[1] - 64) * 0.8;
        assertTrue(diveAlong < levelAlong, "the tips move back toward the feet as the wings fold");
    }
}
