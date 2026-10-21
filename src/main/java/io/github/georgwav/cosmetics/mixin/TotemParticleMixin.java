package io.github.georgwav.cosmetics.mixin;

import net.minecraft.client.particle.SimpleAnimatedParticle;
import net.minecraft.client.particle.TotemParticle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Totems' pop particles: in two colours of yours, fading to a third, in place of the game's green and gold. */
@Mixin(TotemParticle.class)
abstract class TotemParticleMixin {
    @Inject(method = "<init>", at = @At("TAIL"), require = 0)
    private void cosmeticsmod$colours(CallbackInfo ci) {
        int[] c = io.github.georgwav.cosmetics.sol.fx.effects.Totems.particleColours();
        if (c != null) {
            SimpleAnimatedParticle p = (SimpleAnimatedParticle) (Object) this;
            p.setColor(java.util.concurrent.ThreadLocalRandom.current().nextBoolean() ? c[0] : c[1]);
            p.setFadeColor(c[2]);
        }
    }
}
