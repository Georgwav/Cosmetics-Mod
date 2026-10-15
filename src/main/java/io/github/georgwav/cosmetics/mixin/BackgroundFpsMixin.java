package io.github.georgwav.cosmetics.mixin;

import io.github.georgwav.cosmetics.client.modules.Performance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Performance's background frame rate: the game's frame cap (Minecraft's in 1.21.1, its limit tracker after). */
//? if >=1.21.2 {
@Mixin(com.mojang.blaze3d.platform.FramerateLimitTracker.class)
//?} else {
/*@Mixin(net.minecraft.client.Minecraft.class)
*///?}
abstract class BackgroundFpsMixin {
    @Inject(method = "getFramerateLimit()I", at = @At("RETURN"), cancellable = true, require = 0)
    private void cosmeticsmod$background(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(Performance.limit(cir.getReturnValueI()));
    }
}
