package io.github.georgwav.cosmetics.mixin;

// Punchy draws its own first-person model at the start of the game's hands, its own way (not through the game's arm
// and item methods). While a knife is in your main hand, the knife is drawn there first and the rest of the hands
// (Punchy's model and the game's) is skipped, so the knife's own arms are the only ones. Up to 26.2 (26.3 draws the
// hands elsewhere, and there is no Punchy for it to meet).
//? if <26.3 {
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.georgwav.cosmetics.melee.KnifeRenderer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.util.Mth;
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

/** Knives with Punchy installed: the knife alone, before Punchy's model (applied before Punchy, which can't run). */
@Mixin(value = ItemInHandRenderer.class, priority = 500)
abstract class KnifePunchyMixin {
    @Shadow
    private ItemStack mainHandItem;

    //? if >=26.2 {
    /*@Inject(method = "submitHandsWithItems", at = @At("HEAD"), cancellable = true)
    private void cosmeticsmod$knifeAlone(float partialTick, PoseStack pose, SubmitNodeCollector buffers, LocalPlayer player,
                                         int light, CallbackInfo ci) {
    *///?} elif >=1.21.9 {
    @Inject(method = "renderHandsWithItems", at = @At("HEAD"), cancellable = true)
    private void cosmeticsmod$knifeAlone(float partialTick, PoseStack pose, SubmitNodeCollector buffers, LocalPlayer player,
                                         int light, CallbackInfo ci) {
    //?} else {
    /*@Inject(method = "renderHandsWithItems", at = @At("HEAD"), cancellable = true)
    private void cosmeticsmod$knifeAlone(float partialTick, PoseStack pose, MultiBufferSource.BufferSource buffers,
                                         LocalPlayer player, int light, CallbackInfo ci) {
    *///?}
        if (!KnifeRenderer.drawsAlone(mainHandItem)) {
            return;
        }
        pose.pushPose();
        // The turn lag the game puts on the hands (the knife eases it its own way).
        float xBob = Mth.lerp(partialTick, player.xBobO, player.xBob), yBob = Mth.lerp(partialTick, player.yBobO, player.yBob);
        pose.mulPose(Axis.XP.rotationDegrees((player.getViewXRot(partialTick) - xBob) * 0.1F));
        pose.mulPose(Axis.YP.rotationDegrees((player.getViewYRot(partialTick) - yBob) * 0.1F));
        io.github.georgwav.cosmetics.sol.Hooks.KNIFE_HAND.hit();
        // The view model's main hand (Punchy › Main hand), then the knife's own pose on top.
        io.github.georgwav.cosmetics.render.FirstPersonHands.viewModel(pose, player.getMainArm() != net.minecraft.world.entity.HumanoidArm.LEFT, true);
        boolean drawn = KnifeRenderer.mainHand(mainHandItem, 0f, partialTick, pose, buffers, light);
        pose.popPose();
        if (!drawn) {
            return;
        }
        // Drawn now, as the game ends its hands.
        //? if >=26.2 {
        //?} elif >=1.21.9 {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        mc.gameRenderer.getFeatureRenderDispatcher().renderAllFeatures();
        mc.renderBuffers().bufferSource().endBatch();
        //?} else {
        /*buffers.endBatch();
        *///?}
        ci.cancel();
    }
}
//?} else {
/*final class KnifePunchyMixin {
    private KnifePunchyMixin() {
    }
}
*///?}
