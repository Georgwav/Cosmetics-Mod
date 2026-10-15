package io.github.georgwav.cosmetics.mixin;

import io.github.georgwav.cosmetics.client.modules.ParticleTweaks;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Particles: the ones the module hides never join the game's particles. */
@Mixin(ParticleEngine.class)
abstract class ParticleTweaksMixin {
    @Inject(method = "add(Lnet/minecraft/client/particle/Particle;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void cosmeticsmod$hide(Particle particle, CallbackInfo ci) {
        if (ParticleTweaks.hides(particle)) {
            ci.cancel();
        }
    }
}
