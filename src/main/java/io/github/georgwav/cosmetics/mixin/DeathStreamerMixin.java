package io.github.georgwav.cosmetics.mixin;

import io.github.georgwav.cosmetics.sol.client.Streamer;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Streamer mode (Social): the death screen's cause ("Georg was slain by a Zombie") reads "You" for your name. */
@Mixin(DeathScreen.class)
abstract class DeathStreamerMixin {
    @Shadow
    @Final
    @Mutable
    private Component causeOfDeath;

    @Inject(method = "init", at = @At("TAIL"), require = 0)
    private void cosmeticsmod$streamer(CallbackInfo ci) {
        if (causeOfDeath != null) {
            causeOfDeath = Streamer.scrub(causeOfDeath);
        }
    }
}
