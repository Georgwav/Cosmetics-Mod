package io.github.georgwav.cosmetics.mixin;

import io.github.georgwav.cosmetics.client.modules.SoundMixer;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Sounds: each sound's volume through the module's sliders, as the game works it out. */
@Mixin(SoundEngine.class)
abstract class SoundMixerMixin {
    @Inject(method = "calculateVolume(Lnet/minecraft/client/resources/sounds/SoundInstance;)F", at = @At("RETURN"),
            cancellable = true, require = 0)
    private void cosmeticsmod$volume(SoundInstance sound, CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(SoundMixer.volume(sound, cir.getReturnValueF()));
    }
}
