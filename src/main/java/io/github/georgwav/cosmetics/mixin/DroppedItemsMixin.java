package io.github.georgwav.cosmetics.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.georgwav.cosmetics.client.modules.DroppedItems;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Dropped Items' size: the item entity's drawing scaled about where it lies (render up to 1.21.8, submit after; the
 * exact descriptor, not the bridge method).
 */
@Mixin(ItemEntityRenderer.class)
abstract class DroppedItemsMixin {
    //? if >=26.1 {
    /*private static final String DRAW = "submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V";
    *///?} elif >=1.21.9 {
    private static final String DRAW = "submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V";
    //?} elif >=1.21.2 {
    /*private static final String DRAW = "render(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V";
    *///?} else {
    /*private static final String DRAW = "render(Lnet/minecraft/world/entity/item/ItemEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V";
    *///?}

    @ModifyVariable(method = DRAW, at = @At("HEAD"), argsOnly = true, require = 0)
    private PoseStack cosmeticsmod$grow(PoseStack pose) {
        float s = DroppedItems.scale();
        pose.pushPose();
        if (s != 1f) {
            pose.scale(s, s, s);
        }
        return pose;
    }

    @ModifyVariable(method = DRAW, at = @At("RETURN"), argsOnly = true, require = 0)
    private PoseStack cosmeticsmod$back(PoseStack pose) {
        pose.popPose();
        return pose;
    }
}
