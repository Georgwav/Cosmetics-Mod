package io.github.georgwav.cosmetics.mixin;

// Punchy draws its own first-person model at the start of the game's hands (its arms through renderPlayerArm, its
// items through renderItem). While a knife is in your main hand, its main arm and item are left out, so the knife's
// own arm is the only one. Up to 26.2 (26.3 draws the hands elsewhere, and there is no Punchy for it to meet).
//? if <26.3 {
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.georgwav.cosmetics.melee.KnifeRenderer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if >=1.21.9 {
import net.minecraft.client.renderer.SubmitNodeCollector;
//?} else {
/*import net.minecraft.client.renderer.MultiBufferSource;
*///?}

/** Knives with Punchy installed: Punchy's model leaves out the main hand while a knife shows (applied before Punchy). */
@Mixin(value = ItemInHandRenderer.class, priority = 500)
abstract class KnifePunchyMixin {
    @Shadow
    private ItemStack mainHandItem;

    //? if >=26.2 {
    /*@Inject(method = "submitHandsWithItems", at = @At("HEAD"))
    private void cosmeticsmod$handsStart(float partialTick, PoseStack pose, SubmitNodeCollector buffers, LocalPlayer player,
                                         int light, CallbackInfo ci) {
    *///?} elif >=1.21.9 {
    @Inject(method = "renderHandsWithItems", at = @At("HEAD"))
    private void cosmeticsmod$handsStart(float partialTick, PoseStack pose, SubmitNodeCollector buffers, LocalPlayer player,
                                         int light, CallbackInfo ci) {
    //?} else {
    /*@Inject(method = "renderHandsWithItems", at = @At("HEAD"))
    private void cosmeticsmod$handsStart(float partialTick, PoseStack pose, MultiBufferSource.BufferSource buffers,
                                         LocalPlayer player, int light, CallbackInfo ci) {
    *///?}
        KnifeRenderer.handsStart(mainHandItem);
    }

    //? if >=26.2 {
    /*@Inject(method = {"submitArmWithItem", "submitHandsWithItems"}, at = {@At("HEAD"), @At("RETURN")})
    *///?} else {
    @Inject(method = "renderArmWithItem", at = @At("HEAD"))
    //?}
    private void cosmeticsmod$handsEnd(CallbackInfo ci) {
        KnifeRenderer.handsEnd();
    }

    //? if >=1.21.9 {
    @Inject(method = "renderPlayerArm", at = @At("HEAD"), cancellable = true)
    private void cosmeticsmod$arm(PoseStack pose, SubmitNodeCollector buffers, int light, float equip, float swing,
                                  HumanoidArm arm, CallbackInfo ci) {
    //?} else {
    /*@Inject(method = "renderPlayerArm", at = @At("HEAD"), cancellable = true)
    private void cosmeticsmod$arm(PoseStack pose, MultiBufferSource buffers, int light, float equip, float swing,
                                  HumanoidArm arm, CallbackInfo ci) {
    *///?}
        if (KnifeRenderer.skipPunchyArm(arm)) {
            ci.cancel();
        }
    }

    //? if >=1.21.9 {
    @Inject(method = "renderItem", at = @At("HEAD"), cancellable = true)
    private void cosmeticsmod$item(LivingEntity entity, ItemStack stack, ItemDisplayContext context, PoseStack pose,
                                   SubmitNodeCollector buffers, int light, CallbackInfo ci) {
    //?} elif >=1.21.5 {
    /*@Inject(method = "renderItem", at = @At("HEAD"), cancellable = true)
    private void cosmeticsmod$item(LivingEntity entity, ItemStack stack, ItemDisplayContext context, PoseStack pose,
                                   MultiBufferSource buffers, int light, CallbackInfo ci) {
    *///?} else {
    /*@Inject(method = "renderItem", at = @At("HEAD"), cancellable = true)
    private void cosmeticsmod$item(LivingEntity entity, ItemStack stack, ItemDisplayContext context, boolean leftHand,
                                   PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
    *///?}
        if (KnifeRenderer.skipPunchyItem(entity, stack, context)) {
            ci.cancel();
        }
    }
}
//?} else {
/*final class KnifePunchyMixin {
    private KnifePunchyMixin() {
    }
}
*///?}
