package io.github.georgwav.cosmetics.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.georgwav.cosmetics.sol.render.HandMotion;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The hand glow's trail: each first-person hand's pose as the game finishes drawing it (just before the arm's own
 * pose is popped), for {@link HandMotion} to follow it from frame to frame. Not required: without it the trail keeps
 * a copy per frame.
 */
//? if >=26.3 {
/*@Mixin(net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer.class)
*///?} else {
@Mixin(net.minecraft.client.renderer.ItemInHandRenderer.class)
//?}
abstract class HandMotionMixin {
    //? if >=26.3 {
    /*@Inject(method = "submitArmWithItem", require = 0,
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V"))
    private void cosmeticsmod$handDrawn(net.minecraft.client.renderer.state.level.PlayerRenderState player,
                                        net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState state,
                                        float partialTick, float pitch, InteractionHand hand, float swing, ItemStack stack,
                                        float equip, PoseStack poseStack, net.minecraft.client.renderer.SubmitNodeCollector buffers,
                                        int light, CallbackInfo ci) {
    *///?} elif >=26.2 {
    /*@Inject(method = "submitArmWithItem", require = 0,
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V"))
    private void cosmeticsmod$handDrawn(net.minecraft.client.player.AbstractClientPlayer player, float partialTick, float pitch,
                                        InteractionHand hand, float swing, ItemStack stack, float equip, PoseStack poseStack,
                                        net.minecraft.client.renderer.SubmitNodeCollector buffers, int light, CallbackInfo ci) {
    *///?} elif >=1.21.9 {
    @Inject(method = "renderArmWithItem", require = 0,
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V"))
    private void cosmeticsmod$handDrawn(net.minecraft.client.player.AbstractClientPlayer player, float partialTick, float pitch,
                                        InteractionHand hand, float swing, ItemStack stack, float equip, PoseStack poseStack,
                                        net.minecraft.client.renderer.SubmitNodeCollector buffers, int light, CallbackInfo ci) {
    //?} else {
    /*@Inject(method = "renderArmWithItem", require = 0,
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V"))
    private void cosmeticsmod$handDrawn(net.minecraft.client.player.AbstractClientPlayer player, float partialTick, float pitch,
                                        InteractionHand hand, float swing, ItemStack stack, float equip, PoseStack poseStack,
                                        net.minecraft.client.renderer.MultiBufferSource buffers, int light, CallbackInfo ci) {
    *///?}
        boolean rightMain = Minecraft.getInstance().options.mainHand().get() == HumanoidArm.RIGHT;
        HandMotion.drawn((hand == InteractionHand.MAIN_HAND) == rightMain, poseStack.last().pose());
    }
}
