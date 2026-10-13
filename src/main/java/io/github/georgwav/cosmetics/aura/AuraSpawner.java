package io.github.georgwav.cosmetics.aura;

import io.github.georgwav.cosmetics.model.CosmeticDefinition;

import java.util.List;
import java.util.Random;

/**
 * The version-independent half of particle cosmetics: how many particles an emitter gives off and where each
 * one starts, how it moves and how it looks, in the game's units (blocks, ticks). The version-specific half
 * (AuraParticles) turns each {@link Spawn} into a particle in the game's particle engine.
 */
public final class AuraSpawner {
    private static final float PIXEL = 1f / 16f;
    private static final float TICKS_PER_SECOND = 20f;

    private AuraSpawner() {
    }

    /**
     * One particle to create.
     *
     * @param x          blocks from the player's feet, world axes (likewise y, z)
     * @param vx         blocks per tick (likewise vy, vz)
     * @param lifetime   ticks
     * @param startSize  half its width at birth, blocks (the game's "quad size")
     * @param endSize    the same at the end of its life
     * @param color      RGB tint
     * @param spin       radians per tick
     * @param gravity    blocks per tick squared, downwards
     * @param friction   share of its speed it keeps each tick
     */
    public record Spawn(double x, double y, double z, double vx, double vy, double vz, int lifetime,
                        float startSize, float endSize, float startAlpha, float endAlpha, int color, float spin,
                        float gravity, float friction, boolean glow, List<String> sprites) {
    }

    /**
     * How many particles to give off now, for {@code seconds} of time at {@code rate} per second times
     * {@code density}. The fraction left over is kept in {@code carry[0]} for next time, so low rates still give
     * particles off at the right pace.
     */
    public static int count(float[] carry, float rate, float seconds, float density) {
        float total = carry[0] + rate * Math.max(0f, density) * seconds;
        // A hair of slack so sums like 20 x 0.15 (2.9999998 in floats) still count as whole particles.
        int whole = (int) (total + 1e-4f);
        carry[0] = Math.max(0f, total - whole);
        return whole;
    }

    /**
     * {@code spawn} as the player set it for this cosmetic: {@code size} times as big, and in {@code color} (RGB)
     * instead of the emitter's own colours unless that is null.
     */
    public static Spawn adjusted(Spawn spawn, float size, Integer color) {
        if (size == 1f && color == null) {
            return spawn;
        }
        return new Spawn(spawn.x(), spawn.y(), spawn.z(), spawn.vx(), spawn.vy(), spawn.vz(), spawn.lifetime(),
                spawn.startSize() * size, spawn.endSize() * size, spawn.startAlpha(), spawn.endAlpha(),
                color != null ? color & 0xFFFFFF : spawn.color(), spawn.spin(), spawn.gravity(), spawn.friction(),
                spawn.glow(), spawn.sprites());
    }

    /** A new particle from {@code emitter}, for a player whose body faces {@code bodyYaw} (degrees, the game's). */
    public static Spawn spawn(CosmeticDefinition.ParticleEmitter emitter, float bodyYaw, Random random) {
        float[] o = emitter.origin();
        float[] a = emitter.area();
        float lx = o[0] + symmetric(random, a[0]);
        float ly = o[1] + symmetric(random, a[1]);
        float lz = o[2] + symmetric(random, a[2]);
        float[] v = emitter.velocity();
        float[] s = emitter.spread();
        float vx = v[0] + symmetric(random, s[0]);
        float vy = v[1] + symmetric(random, s[1]);
        float vz = v[2] + symmetric(random, s[2]);
        double[] position = turn(lx * PIXEL, ly * PIXEL, lz * PIXEL, bodyYaw);
        double[] velocity = turn(vx * PIXEL / TICKS_PER_SECOND, vy * PIXEL / TICKS_PER_SECOND,
                vz * PIXEL / TICKS_PER_SECOND, bodyYaw);
        float life = emitter.minLife() + random.nextFloat() * (emitter.maxLife() - emitter.minLife());
        int[] colors = emitter.colors();
        float spin = emitter.spin() == 0f ? 0f : (float) Math.toRadians(symmetric(random, emitter.spin())) / TICKS_PER_SECOND;
        return new Spawn(position[0], position[1], position[2], velocity[0], velocity[1], velocity[2],
                Math.max(1, Math.round(life * TICKS_PER_SECOND)),
                emitter.startSize() * PIXEL / 2f, emitter.endSize() * PIXEL / 2f,
                emitter.startAlpha(), emitter.endAlpha(), colors[random.nextInt(colors.length)], spin,
                emitter.gravity() * PIXEL / (TICKS_PER_SECOND * TICKS_PER_SECOND),
                (float) Math.pow(1.0 - emitter.drag(), 1.0 / TICKS_PER_SECOND), emitter.glow(), emitter.sprites());
    }

    /**
     * The player's directions (x to their right, y up, z behind them) as world directions, for a body facing
     * {@code bodyYaw} degrees (0 = south, +Z; 90 = west, -X).
     */
    static double[] turn(double x, double y, double z, float bodyYaw) {
        double yaw = Math.toRadians(bodyYaw);
        double sin = Math.sin(yaw);
        double cos = Math.cos(yaw);
        // right = (-cos, 0, -sin), behind = (sin, 0, -cos)
        return new double[]{-cos * x + sin * z, y, -sin * x - cos * z};
    }

    private static float symmetric(Random random, float half) {
        return half == 0f ? 0f : (random.nextFloat() * 2f - 1f) * half;
    }
}
