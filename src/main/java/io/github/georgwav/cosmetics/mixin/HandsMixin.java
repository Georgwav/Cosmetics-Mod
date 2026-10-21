package io.github.georgwav.cosmetics.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.georgwav.cosmetics.render.FirstPersonHands;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if >=1.21.9 {
import net.minecraft.client.renderer.SubmitNodeCollector;
//?} else {
/*import net.minecraft.client.renderer.MultiBufferSource;
*///?}

/**
 * The Punchy module's first-person hands ({@link FirstPersonHands}), on the game's own hand drawing: the hands kept from
 * swaying as you turn, each hand's view model (just after the game pushes its pose), the swing styles (at the game's
 * swing turn), 1.7's swing (the main hand left up after a hit), and, as the game finishes each hand, your arm holding
 * what you carry and the flames. Applied before Punchy (priority 600), after the knives (500). Nothing required: a
 * version or another mod that moves these leaves that part out.
 */
//? if >=26.3 {
/*@Mixin(value = net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer.class, priority = 600)
*///?} else {
@Mixin(value = net.minecraft.client.renderer.ItemInHandRenderer.class, priority = 600)
//?}
abstract class HandsMixin {
    //? if >=26.3 {
    /*@Shadow
    private void renderPlayerArm(PoseStack pose, SubmitNodeCollector buffers, int light, float equip, float swing, HumanoidArm arm,
                                 net.minecraft.client.renderer.state.level.PlayerRenderState player) {
        throw new AssertionError();
    }
    *///?} elif >=1.21.9 {
    @Shadow
    private void renderPlayerArm(PoseStack pose, SubmitNodeCollector buffers, int light, float equip, float swing, HumanoidArm arm) {
        throw new AssertionError();
    }
    //?} else {
    /*@Shadow
    private void renderPlayerArm(PoseStack pose, MultiBufferSource buffers, int light, float equip, float swing, HumanoidArm arm) {
        throw new AssertionError();
    }
    *///?}

    // ------------------------------------------------------------------ keep still: no turn lag

    //? if >=26.3 {
    /*@Inject(method = "submitHandsWithItems", at = @At("HEAD"), require = 0)
    private void cosmeticsmod$still(float partialTick, PoseStack pose, SubmitNodeCollector buffers,
                                    net.minecraft.client.renderer.state.level.PlayerRenderState player,
                                    net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState state, CallbackInfo ci) {
        if (FirstPersonHands.holdStill()) {
            state.xBob = state.viewXRot;
            state.yBob = state.viewYRot;
        }
    }
    *///?} elif >=26.2 {
    /*@Inject(method = "submitHandsWithItems", at = @At("HEAD"), require = 0)
    private void cosmeticsmod$still(float partialTick, PoseStack pose, SubmitNodeCollector buffers,
                                    net.minecraft.client.player.LocalPlayer player, int light, CallbackInfo ci) {
        cosmeticsmod$still(player, partialTick);
    }
    *///?} elif >=1.21.9 {
    @Inject(method = "renderHandsWithItems", at = @At("HEAD"), require = 0)
    private void cosmeticsmod$still(float partialTick, PoseStack pose, SubmitNodeCollector buffers,
                                    net.minecraft.client.player.LocalPlayer player, int light, CallbackInfo ci) {
        cosmeticsmod$still(player, partialTick);
    }
    //?} else {
    /*@Inject(method = "renderHandsWithItems", at = @At("HEAD"), require = 0)
    private void cosmeticsmod$still(float partialTick, PoseStack pose, MultiBufferSource.BufferSource buffers,
                                    net.minecraft.client.player.LocalPlayer player, int light, CallbackInfo ci) {
        cosmeticsmod$still(player, partialTick);
    }
    *///?}

    //? if <26.3 {
    /** No turn lag: the hands' view is where you look (they sway by the difference). */
    private static void cosmeticsmod$still(net.minecraft.client.player.LocalPlayer player, float partialTick) {
        if (FirstPersonHands.holdStill()) {
            player.xBobO = player.xBob = player.getViewXRot(partialTick);
            player.yBobO = player.yBob = player.getViewYRot(partialTick);
        }
    }
    //?}

    // ------------------------------------------------------------------ the main hand's swing and lowering

    //? if >=26.3 {
    /*@ModifyArg(method = "submitHandsWithItems", require = 0, index = 5, at = @At(value = "INVOKE", ordinal = 0, target =
            "Lnet/minecraft/client/renderer/FirstPersonHandsAndItemsRenderer;submitArmWithItem(Lnet/minecraft/client/renderer/state/level/PlayerRenderState;Lnet/minecraft/client/renderer/state/level/FirstPersonHandsAndItemsRenderState;FFLnet/minecraft/world/InteractionHand;FLnet/minecraft/world/item/ItemStack;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V"))
    *///?} elif >=26.2 {
    /*@ModifyArg(method = "submitHandsWithItems", require = 0, index = 4, at = @At(value = "INVOKE", ordinal = 0, target =
            "Lnet/minecraft/client/renderer/ItemInHandRenderer;submitArmWithItem(Lnet/minecraft/client/player/AbstractClientPlayer;FFLnet/minecraft/world/InteractionHand;FLnet/minecraft/world/item/ItemStack;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V"))
    *///?} elif >=1.21.9 {
    @ModifyArg(method = "renderHandsWithItems", require = 0, index = 4, at = @At(value = "INVOKE", ordinal = 0, target =
            "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderArmWithItem(Lnet/minecraft/client/player/AbstractClientPlayer;FFLnet/minecraft/world/InteractionHand;FLnet/minecraft/world/item/ItemStack;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V"))
    //?} else {
    /*@ModifyArg(method = "renderHandsWithItems", require = 0, index = 4, at = @At(value = "INVOKE", ordinal = 0, target =
            "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderArmWithItem(Lnet/minecraft/client/player/AbstractClientPlayer;FFLnet/minecraft/world/InteractionHand;FLnet/minecraft/world/item/ItemStack;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
    *///?}
    private float cosmeticsmod$swing(float swing) {
        return FirstPersonHands.mainSwing(swing);
    }

    //? if >=26.3 {
    /*@ModifyArg(method = "submitHandsWithItems", require = 0, index = 7, at = @At(value = "INVOKE", ordinal = 0, target =
            "Lnet/minecraft/client/renderer/FirstPersonHandsAndItemsRenderer;submitArmWithItem(Lnet/minecraft/client/renderer/state/level/PlayerRenderState;Lnet/minecraft/client/renderer/state/level/FirstPersonHandsAndItemsRenderState;FFLnet/minecraft/world/InteractionHand;FLnet/minecraft/world/item/ItemStack;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V"))
    *///?} elif >=26.2 {
    /*@ModifyArg(method = "submitHandsWithItems", require = 0, index = 6, at = @At(value = "INVOKE", ordinal = 0, target =
            "Lnet/minecraft/client/renderer/ItemInHandRenderer;submitArmWithItem(Lnet/minecraft/client/player/AbstractClientPlayer;FFLnet/minecraft/world/InteractionHand;FLnet/minecraft/world/item/ItemStack;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V"))
    *///?} elif >=1.21.9 {
    @ModifyArg(method = "renderHandsWithItems", require = 0, index = 6, at = @At(value = "INVOKE", ordinal = 0, target =
            "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderArmWithItem(Lnet/minecraft/client/player/AbstractClientPlayer;FFLnet/minecraft/world/InteractionHand;FLnet/minecraft/world/item/ItemStack;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V"))
    //?} else {
    /*@ModifyArg(method = "renderHandsWithItems", require = 0, index = 6, at = @At(value = "INVOKE", ordinal = 0, target =
            "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderArmWithItem(Lnet/minecraft/client/player/AbstractClientPlayer;FFLnet/minecraft/world/InteractionHand;FLnet/minecraft/world/item/ItemStack;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
    *///?}
    private float cosmeticsmod$equip(float equip) {
        return FirstPersonHands.mainEquip(equip);
    }

    // ------------------------------------------------------------------ each hand: view model, then arm and flames

    //? if >=26.3 {
    /*@Inject(method = "submitArmWithItem", require = 0,
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", shift = At.Shift.AFTER, ordinal = 0))
    private void cosmeticsmod$viewModel(net.minecraft.client.renderer.state.level.PlayerRenderState player,
                                        net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState state,
                                        float partialTick, float pitch, InteractionHand hand, float swing, ItemStack stack,
                                        float equip, PoseStack poseStack, SubmitNodeCollector buffers, int light, CallbackInfo ci) {
    *///?} elif >=26.2 {
    /*@Inject(method = "submitArmWithItem", require = 0,
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", shift = At.Shift.AFTER, ordinal = 0))
    private void cosmeticsmod$viewModel(net.minecraft.client.player.AbstractClientPlayer player, float partialTick, float pitch,
                                        InteractionHand hand, float swing, ItemStack stack, float equip, PoseStack poseStack,
                                        SubmitNodeCollector buffers, int light, CallbackInfo ci) {
    *///?} elif >=1.21.9 {
    @Inject(method = "renderArmWithItem", require = 0,
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", shift = At.Shift.AFTER, ordinal = 0))
    private void cosmeticsmod$viewModel(net.minecraft.client.player.AbstractClientPlayer player, float partialTick, float pitch,
                                        InteractionHand hand, float swing, ItemStack stack, float equip, PoseStack poseStack,
                                        SubmitNodeCollector buffers, int light, CallbackInfo ci) {
    //?} else {
    /*@Inject(method = "renderArmWithItem", require = 0,
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", shift = At.Shift.AFTER, ordinal = 0))
    private void cosmeticsmod$viewModel(net.minecraft.client.player.AbstractClientPlayer player, float partialTick, float pitch,
                                        InteractionHand hand, float swing, ItemStack stack, float equip, PoseStack poseStack,
                                        MultiBufferSource buffers, int light, CallbackInfo ci) {
    *///?}
        boolean right = cosmeticsmod$right(hand);
        FirstPersonHands.viewModel(poseStack, right, hand == InteractionHand.MAIN_HAND);
        if (FirstPersonHands.armWith(stack)) {
            FirstPersonHands.holdInFist(poseStack, right, stack);
        }
    }

    //? if >=26.3 {
    /*@Inject(method = "submitArmWithItem", require = 0,
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V"))
    private void cosmeticsmod$held(net.minecraft.client.renderer.state.level.PlayerRenderState player,
                                   net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState state,
                                   float partialTick, float pitch, InteractionHand hand, float swing, ItemStack stack,
                                   float equip, PoseStack poseStack, SubmitNodeCollector buffers, int light, CallbackInfo ci) {
    *///?} elif >=26.2 {
    /*@Inject(method = "submitArmWithItem", require = 0,
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V"))
    private void cosmeticsmod$held(net.minecraft.client.player.AbstractClientPlayer player, float partialTick, float pitch,
                                   InteractionHand hand, float swing, ItemStack stack, float equip, PoseStack poseStack,
                                   SubmitNodeCollector buffers, int light, CallbackInfo ci) {
    *///?} elif >=1.21.9 {
    @Inject(method = "renderArmWithItem", require = 0,
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V"))
    private void cosmeticsmod$held(net.minecraft.client.player.AbstractClientPlayer player, float partialTick, float pitch,
                                   InteractionHand hand, float swing, ItemStack stack, float equip, PoseStack poseStack,
                                   SubmitNodeCollector buffers, int light, CallbackInfo ci) {
    //?} else {
    /*@Inject(method = "renderArmWithItem", require = 0,
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V"))
    private void cosmeticsmod$held(net.minecraft.client.player.AbstractClientPlayer player, float partialTick, float pitch,
                                   InteractionHand hand, float swing, ItemStack stack, float equip, PoseStack poseStack,
                                   MultiBufferSource buffers, int light, CallbackInfo ci) {
    *///?}
        boolean right = cosmeticsmod$right(hand);
        if (FirstPersonHands.armWith(stack)) {
            poseStack.pushPose();
            FirstPersonHands.armToItem(poseStack, right, stack);
            //? if >=26.3 {
            /*renderPlayerArm(poseStack, buffers, light, 0f, 0f, right ? HumanoidArm.RIGHT : HumanoidArm.LEFT, player);
            *///?} else {
            renderPlayerArm(poseStack, buffers, light, 0f, 0f, right ? HumanoidArm.RIGHT : HumanoidArm.LEFT);
            //?}
            poseStack.popPose();
        }
        FirstPersonHands.flames(poseStack, buffers, stack, right, hand == InteractionHand.MAIN_HAND);
    }

    /** The game's swing turn: a swing style's pose instead, when one is picked. */
    @Inject(method = "applyItemArmAttackTransform", at = @At("HEAD"), cancellable = true, require = 0)
    private void cosmeticsmod$swingStyle(PoseStack pose, HumanoidArm arm, float swing, CallbackInfo ci) {
        if (FirstPersonHands.swing(pose, arm, swing)) {
            ci.cancel();
        }
    }

    /** Whether that hand is on the right of the screen. */
    private static boolean cosmeticsmod$right(InteractionHand hand) {
        boolean rightMain = Minecraft.getInstance().options.mainHand().get() == HumanoidArm.RIGHT;
        return (hand == InteractionHand.MAIN_HAND) == rightMain;
    }
}
