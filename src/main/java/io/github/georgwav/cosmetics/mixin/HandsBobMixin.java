package io.github.georgwav.cosmetics.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.georgwav.cosmetics.render.FirstPersonHands;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Punchy › Keep hands still: the game's walking bob is left off the first-person hands (the view itself still bobs, as
 * set in the game's options).
 */
@Mixin(GameRenderer.class)
abstract class HandsBobMixin {
    //? if >=26.1 {
    /*@WrapWithCondition(method = "renderItemInHand", require = 0, at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/GameRenderer;bobView(Lnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V"))
    private boolean cosmeticsmod$bob(GameRenderer self, net.minecraft.client.renderer.state.level.CameraRenderState camera, PoseStack pose) {
        return !FirstPersonHands.keepStill();
    }
    *///?} else {
    @WrapWithCondition(method = "renderItemInHand", require = 0, at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/GameRenderer;bobView(Lcom/mojang/blaze3d/vertex/PoseStack;F)V"))
    private boolean cosmeticsmod$bob(GameRenderer self, PoseStack pose, float partialTick) {
        return !FirstPersonHands.keepStill();
    }
    //?}
}
