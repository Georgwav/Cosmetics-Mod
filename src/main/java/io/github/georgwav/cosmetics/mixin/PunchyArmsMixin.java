package io.github.georgwav.cosmetics.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.georgwav.cosmetics.client.modules.PunchyModule;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * The Punchy module's arms: the pose the game hands to the first-person hands is moved back first, so the hands (the
 * game's or Punchy's, which draws with the same pose) look smaller and closer to the middle of the screen.
 */
@Mixin(GameRenderer.class)
abstract class PunchyArmsMixin {
    //? if >=26.3 {
    /*@ModifyArg(method = "renderItemInHand", index = 1, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/FirstPersonHandsAndItemsRenderer;submitHandsWithItems(FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/PlayerRenderState;Lnet/minecraft/client/renderer/state/level/FirstPersonHandsAndItemsRenderState;)V"))
    *///?} elif >=26.2 {
    /*@ModifyArg(method = "renderItemInHand", index = 1, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;submitHandsWithItems(FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/player/LocalPlayer;I)V"))
    *///?} elif >=1.21.9 {
    @ModifyArg(method = "renderItemInHand", index = 1, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderHandsWithItems(FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/player/LocalPlayer;I)V"))
    //?} else {
    /*@ModifyArg(method = "renderItemInHand", index = 1, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderHandsWithItems(FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;Lnet/minecraft/client/player/LocalPlayer;I)V"))
    *///?}
    private PoseStack cosmeticsmod$arms(PoseStack pose) {
        return PunchyModule.arms(pose);
    }
}
