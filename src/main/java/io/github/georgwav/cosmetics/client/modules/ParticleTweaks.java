package io.github.georgwav.cosmetics.client.modules;

import io.github.georgwav.cosmetics.client.Module;
import io.github.georgwav.cosmetics.client.Modules;
import io.github.georgwav.cosmetics.client.Setting;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.InteractionResult;

/**
 * Particles: more hit particles (crits, and the enchanted hit's sparks on every hit if you like), and the busy ones
 * switched off: blocks breaking, explosions, and rain and splashes.
 */
public final class ParticleTweaks extends Module {
    private final Setting.Slider multiplier = add(new Setting.Slider("multiplier", 1, 1, 5, 1, v -> Math.round(v) + "×").group("hits"));
    private final Setting.Toggle sharpness = add(new Setting.Toggle("sharpness", false).group("hits"));
    private final Setting.Toggle blocks = add(new Setting.Toggle("blocks", true).group("busy"));
    private final Setting.Toggle explosions = add(new Setting.Toggle("explosions", true).group("busy"));
    private final Setting.Toggle weather = add(new Setting.Toggle("weather", true).group("busy"));

    public ParticleTweaks() {
        super("particles", Category.VIEW, "dots");
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            Minecraft mc = Minecraft.getInstance();
            if (enabled() && level.isClientSide() && player == mc.player) {
                int extra = (int) Math.round(multiplier.get()) - 1;
                for (int i = 0; i < extra; i++) {
                    mc.particleEngine.createTrackingEmitter(entity, ParticleTypes.CRIT);
                }
                // Hit Effects' own particles take the sparks' place while they're on.
                if (sharpness.get() && !Modules.HIT_EFFECTS.particlesOn()) {
                    for (int i = 0; i <= extra; i++) {
                        mc.particleEngine.createTrackingEmitter(entity, ParticleTypes.ENCHANTED_HIT);
                    }
                }
            }
            return InteractionResult.PASS;
        });
    }

    /** Whether a particle about to be added is one the module hides. */
    public static boolean hides(Particle particle) {
        ParticleTweaks p = Modules.PARTICLES;
        if (!p.enabled()) {
            return false;
        }
        return !p.blocks.get() && particle instanceof net.minecraft.client.particle.TerrainParticle
                || !p.explosions.get() && (particle instanceof net.minecraft.client.particle.HugeExplosionParticle
                || particle instanceof net.minecraft.client.particle.HugeExplosionSeedParticle)
                || !p.weather.get() && (particle instanceof net.minecraft.client.particle.WaterDropParticle
                || particle instanceof net.minecraft.client.particle.SplashParticle);
    }
}
