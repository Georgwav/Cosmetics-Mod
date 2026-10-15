package io.github.georgwav.cosmetics.mixin;

import io.github.georgwav.cosmetics.client.modules.ScreenTweaks;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Screen: the underwater overlay (the blue wobble on the view) can be switched off (26.3 calls it submitWater). */
@Mixin(ScreenEffectRenderer.class)
abstract class WaterOverlayMixin {
    @Inject(method = {"renderWater", "submitWater"}, at = @At("HEAD"), cancellable = true, require = 0)
    private static void cosmeticsmod$noWater(CallbackInfo ci) {
        if (ScreenTweaks.hidesWater()) {
            ci.cancel();
        }
    }
}
