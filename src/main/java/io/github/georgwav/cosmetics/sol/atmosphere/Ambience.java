package io.github.georgwav.cosmetics.sol.atmosphere;

import io.github.georgwav.cosmetics.sol.Feature;
import io.github.georgwav.cosmetics.sol.Guard;
import io.github.georgwav.cosmetics.sol.render.FrameInfo;
import io.github.georgwav.cosmetics.sol.util.Mulberry32;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * More ambience (Atmosphere): the world around you a little more alive. Four times a second a handful of blocks
 * near you are looked at: under leaves with air below, bits of leaf drift down; on open ground in the rain, drops
 * splash; in grass and flowers at night, fireflies blink; by lava and fire, sparks rise. They are the game's own
 * particles, so the Particles module's amount applies to them too.
 */
public final class Ambience {
    /** Blocks looked at per look (four looks a second), by quality. */
    private static final int LOW = 10, MEDIUM = 20, HIGH = 36;
    private static final int RADIUS = 14;
    private static final Mulberry32 random = new Mulberry32(41);
    private static final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
    private static final BlockPos.MutableBlockPos below = new BlockPos.MutableBlockPos();
    private static int ticks;
    /** Particles made (for the self-test). */
    public static int made;

    private Ambience() {
    }

    /** Every client tick while Atmosphere is on. */
    public static void tick(Minecraft mc, Atmosphere at) {
        if (!at.ambience.get() || mc.level == null || mc.player == null || mc.isPaused() || !Guard.on(Feature.PARTICLES)
                || ++ticks % 5 != 0) {
            return;
        }
        Guard.run(Feature.PARTICLES, () -> look(mc.level, mc.player.blockPosition(), budget(at)));
    }

    private static int budget(Atmosphere at) {
        return switch (at.quality.get()) {
            case "low" -> LOW;
            case "high" -> HIGH;
            case "medium" -> MEDIUM;
            default -> at.particleBudget() <= 300 ? LOW : at.particleBudget() >= 1200 ? HIGH : MEDIUM;
        };
    }

    private static void look(ClientLevel level, BlockPos centre, int count) {
        // 0 at noon, 0.5 at midnight: the sun is down past a quarter turn either side.
        boolean night = Math.cos(FrameInfo.timeOfDay * Math.PI * 2.0) < -0.1;
        boolean raining = level.isRaining();
        for (int i = 0; i < count; i++) {
            pos.set(centre.getX() + random.nextInt(RADIUS * 2 + 1) - RADIUS,
                    centre.getY() + random.nextInt(13) - 4,
                    centre.getZ() + random.nextInt(RADIUS * 2 + 1) - RADIUS);
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }
            double x = pos.getX() + random.nextFloat(), y = pos.getY(), z = pos.getZ() + random.nextFloat();
            if (state.is(BlockTags.LEAVES)) {
                below.set(pos.getX(), pos.getY() - 1, pos.getZ());
                if (level.getBlockState(below).isAir() && random.nextFloat() < 0.5f) {
                    add(level, new BlockParticleOption(ParticleTypes.FALLING_DUST, state), x, y - 0.05, z, 0, 0, 0);
                }
            } else if (state.getFluidState().is(FluidTags.LAVA)) {
                below.set(pos.getX(), pos.getY() + 1, pos.getZ());
                if (level.getBlockState(below).isAir()) {
                    add(level, random.nextFloat() < 0.3f ? ParticleTypes.LAVA : ParticleTypes.SMALL_FLAME, x, y + 1.0, z,
                            0, 0.03 + random.nextFloat() * 0.04, 0);
                }
            } else if (state.is(BlockTags.FIRE) || state.is(BlockTags.CAMPFIRES)) {
                add(level, ParticleTypes.SMALL_FLAME, x, y + 0.6 + random.nextFloat() * 0.6, z,
                        (random.nextFloat() - 0.5) * 0.02, 0.05 + random.nextFloat() * 0.04, (random.nextFloat() - 0.5) * 0.02);
            } else if (night && (state.is(Blocks.SHORT_GRASS) || state.is(Blocks.TALL_GRASS) || state.is(BlockTags.FLOWERS))) {
                if (random.nextFloat() < 0.35f && level.canSeeSky(pos)) {
                    add(level, firefly(), x, y + 0.4 + random.nextFloat() * 1.6, z, 0, 0, 0);
                }
            } else if (raining) {
                below.set(pos.getX(), pos.getY() + 1, pos.getZ());
                if (level.getBlockState(below).isAir() && level.isRainingAt(below)) {
                    for (int d = 0; d < 3; d++) {
                        add(level, ParticleTypes.SPLASH, pos.getX() + random.nextFloat(), y + 1.02, pos.getZ() + random.nextFloat(), 0, 0, 0);
                    }
                }
            }
        }
    }

    private static ParticleOptions firefly() {
        //? if >=1.21.5 {
        return ParticleTypes.FIREFLY;
        //?} else {
        /*return ParticleTypes.GLOW;
        *///?}
    }

    private static void add(ClientLevel level, ParticleOptions particle, double x, double y, double z, double dx, double dy, double dz) {
        level.addParticle(particle, x, y, z, dx, dy, dz);
        made++;
    }
}
