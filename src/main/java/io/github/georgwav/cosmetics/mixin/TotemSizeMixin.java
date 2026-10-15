package io.github.georgwav.cosmetics.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.georgwav.cosmetics.client.modules.HeldItems;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Held Items' small totem: the totem pop on screen scaled down (its animation moved to ScreenEffectRenderer in 1.21.6). */
//? if >=1.21.6 {
@Mixin(net.minecraft.client.renderer.ScreenEffectRenderer.class)
//?} else {
/*@Mixin(net.minecraft.client.renderer.GameRenderer.class)
*///?}
abstract class TotemSizeMixin {
    @Redirect(method = "renderItemActivationAnimation", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;scale(FFF)V",
            ordinal = 0), require = 0)
    private void cosmeticsmod$smallTotem(PoseStack pose, float x, float y, float z) {
        float k = HeldItems.totemScale();
        pose.scale(x * k, y * k, z * k);
    }
}
