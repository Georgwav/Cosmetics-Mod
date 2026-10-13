package io.github.georgwav.cosmetics.aura;

import io.github.georgwav.cosmetics.model.CosmeticDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuraSpawnerTest {
    private static CosmeticDefinition.ParticleEmitter emitter(float[] origin, float[] area, float[] velocity, float[] spread) {
        return new CosmeticDefinition.ParticleEmitter(List.of("minecraft:heart"), 10f, 1f, 2f, origin, area, velocity,
                spread, 32f, 0.5f, 4f, 2f, 1f, 0f, new int[]{0xFF0000, 0x00FF00}, true, 90f,
                CosmeticDefinition.ParticleTrigger.ALWAYS);
    }

    @Test
    void playerDirectionsTurnWithTheBody() {
        // Facing south (+Z): their right is west (-X), behind them is north (-Z).
        assertArrayEquals(new double[]{-1, 0, 0}, AuraSpawner.turn(1, 0, 0, 0f), 1e-9);
        assertArrayEquals(new double[]{0, 0, -1}, AuraSpawner.turn(0, 0, 1, 0f), 1e-9);
        // Facing west (-X): their right is north (-Z), behind them is east (+X).
        assertArrayEquals(new double[]{0, 0, -1}, AuraSpawner.turn(1, 0, 0, 90f), 1e-9);
        assertArrayEquals(new double[]{1, 0, 0}, AuraSpawner.turn(0, 0, 1, 90f), 1e-9);
        assertArrayEquals(new double[]{0, 5, 0}, AuraSpawner.turn(0, 5, 0, 37f), 1e-9);
    }

    @Test
    void countKeepsTheFractionForNextTime() {
        float[] carry = new float[1];
        int total = 0;
        for (int tick = 0; tick < 20; tick++) {
            total += AuraSpawner.count(carry, 3f, 0.05f, 1f); // 3 per second, one second of ticks
        }
        assertEquals(3, total);
        assertEquals(0, AuraSpawner.count(new float[1], 3f, 0.05f, 0f)); // density 0: none
        assertEquals(20, AuraSpawner.count(new float[1], 10f, 1f, 2f));
    }

    @Test
    void spawnsAreInGameUnitsAndWithinTheArea() {
        CosmeticDefinition.ParticleEmitter e = emitter(new float[]{0, 16, 0}, new float[]{8, 4, 8},
                new float[]{0, 20, 0}, new float[]{0, 0, 0});
        Random random = new Random(7);
        for (int i = 0; i < 200; i++) {
            AuraSpawner.Spawn s = AuraSpawner.spawn(e, 123f, random);
            assertTrue(Math.abs(s.x()) <= Math.sqrt(2) * 0.5 + 1e-6 && Math.abs(s.z()) <= Math.sqrt(2) * 0.5 + 1e-6);
            assertTrue(s.y() >= 0.75 - 1e-6 && s.y() <= 1.25 + 1e-6);
            assertEquals(20f / 16f / 20f, s.vy(), 1e-6); // 20 px/s up
            assertTrue(s.lifetime() >= 20 && s.lifetime() <= 40);
            assertEquals(4f / 16f / 2f, s.startSize(), 1e-6);
            assertEquals(2f / 16f / 2f, s.endSize(), 1e-6);
            assertEquals(32f / 16f / 400f, s.gravity(), 1e-9);
            assertEquals(Math.pow(0.5, 1.0 / 20), s.friction(), 1e-6);
            assertTrue(s.color() == 0xFF0000 || s.color() == 0x00FF00);
            assertTrue(Math.abs(s.spin()) <= Math.toRadians(90) / 20 + 1e-6);
        }
    }

    @Test
    void triggersFollowMovement() {
        assertTrue(CosmeticDefinition.ParticleTrigger.ALWAYS.active(true));
        assertTrue(CosmeticDefinition.ParticleTrigger.MOVING.active(true));
        assertTrue(!CosmeticDefinition.ParticleTrigger.MOVING.active(false));
        assertTrue(CosmeticDefinition.ParticleTrigger.STILL.active(false));
    }
}
