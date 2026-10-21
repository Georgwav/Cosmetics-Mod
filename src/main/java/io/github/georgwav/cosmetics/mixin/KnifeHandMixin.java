package io.github.georgwav.cosmetics.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.georgwav.cosmetics.melee.KnifeRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Knives: the main hand holding a sword is drawn as your knife ({@link KnifeRenderer}) instead of the game's arm and
 * sword, and the offhand is left out while it shows (the knife's pack draws both hands). Every other item is left to
 * the game (or Punchy). Applied early (priority 500), so it runs
 * before Punchy's hook on the same method and Punchy never draws the knife's hand.
 */
//? if >=26.3 {
/*@Mixin(value = net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer.class, priority = 500)
*///?} else {
@Mixin(value = net.minecraft.client.renderer.ItemInHandRenderer.class, priority = 500)
//?}
abstract class KnifeHandMixin {
    //? if >=26.3 {
    /*@Inject(method = "submitArmWithItem", at = @At("HEAD"), cancellable = true)
    private void cosmeticsmod$knife(net.minecraft.client.renderer.state.level.PlayerRenderState player,
                                    net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState state,
                                    float partialTick, float pitch, InteractionHand hand, float swing, ItemStack stack,
                                    float equip, PoseStack poseStack, net.minecraft.client.renderer.SubmitNodeCollector buffers,
                                    int light, CallbackInfo ci) {
    *///?} elif >=26.2 {
    /*@Inject(method = "submitArmWithItem", at = @At("HEAD"), cancellable = true)
    private void cosmeticsmod$knife(net.minecraft.client.player.AbstractClientPlayer player, float partialTick, float pitch,
                                    InteractionHand hand, float swing, ItemStack stack, float equip, PoseStack poseStack,
                                    net.minecraft.client.renderer.SubmitNodeCollector buffers, int light, CallbackInfo ci) {
    *///?} elif >=1.21.9 {
    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
    private void cosmeticsmod$knife(net.minecraft.client.player.AbstractClientPlayer player, float partialTick, float pitch,
                                    InteractionHand hand, float swing, ItemStack stack, float equip, PoseStack poseStack,
                                    net.minecraft.client.renderer.SubmitNodeCollector buffers, int light, CallbackInfo ci) {
    //?} else {
    /*@Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
    private void cosmeticsmod$knife(net.minecraft.client.player.AbstractClientPlayer player, float partialTick, float pitch,
                                    InteractionHand hand, float swing, ItemStack stack, float equip, PoseStack poseStack,
                                    net.minecraft.client.renderer.MultiBufferSource buffers, int light, CallbackInfo ci) {
    *///?}
        if (hand != InteractionHand.MAIN_HAND) {
            // The knife's pack draws your other hand itself: no second one, and no shield or totem over it.
            if (KnifeRenderer.hidesOffhand()) {
                ci.cancel();
            }
            return;
        }
        io.github.georgwav.cosmetics.sol.Hooks.KNIFE_HAND.hit();
        // The view model's main hand (Punchy › Main hand), then the knife's own pose on top.
        poseStack.pushPose();
        io.github.georgwav.cosmetics.render.FirstPersonHands.viewModel(poseStack,
                net.minecraft.client.Minecraft.getInstance().options.mainHand().get() == net.minecraft.world.entity.HumanoidArm.RIGHT, true);
        boolean drawn = KnifeRenderer.mainHand(stack, equip, partialTick, poseStack, buffers, light);
        poseStack.popPose();
        if (drawn) {
            ci.cancel();
        }
    }
}
