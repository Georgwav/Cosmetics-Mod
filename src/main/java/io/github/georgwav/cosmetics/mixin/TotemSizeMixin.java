package io.github.georgwav.cosmetics.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.georgwav.cosmetics.sol.fx.effects.Totems;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Totems' pop on screen: the game's floating totem at Totems' size, or not drawn at all while one of Sol's pop styles
 * (or none) takes its place (its animation moved to ScreenEffectRenderer in 1.21.6).
 */
//? if >=1.21.6 {
@Mixin(net.minecraft.client.renderer.ScreenEffectRenderer.class)
//?} else {
/*@Mixin(net.minecraft.client.renderer.GameRenderer.class)
*///?}
abstract class TotemSizeMixin {
    @Inject(method = "renderItemActivationAnimation", at = @At("HEAD"), cancellable = true, require = 0)
    private void cosmeticsmod$popStyle(CallbackInfo ci) {
        if (Totems.replacesPop()) {
            ci.cancel();
        }
    }

    @Redirect(method = "renderItemActivationAnimation", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;scale(FFF)V",
            ordinal = 0), require = 0)
    private void cosmeticsmod$popSize(PoseStack pose, float x, float y, float z) {
        float k = Totems.vanillaScale();
        pose.scale(x * k, y * k, z * k);
    }
}
