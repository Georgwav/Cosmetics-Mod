package io.github.georgwav.cosmetics.mixin;

import io.github.georgwav.cosmetics.render.FirstPersonHands;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
//? if >=1.21.9 {
import org.spongepowered.asm.mixin.injection.ModifyArg;
//?} else {
/*import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.injection.Redirect;
*///?}

/**
 * Punchy › Shield status: a shield in your first-person hands drawn in a tint from its cooldown
 * ({@link FirstPersonHands#shieldTint}); its banner's patterns keep their colours. From 1.21.9 the shield's draw takes
 * a tint; before, its two parts are drawn with one instead of none. Nothing required.
 */
//? if >=26.3 {
/*@Mixin(net.minecraft.client.renderer.special.ShieldSpecialRenderer.class)
abstract class ShieldStatusMixin {
    @ModifyArg(method = "submit(Lnet/minecraft/core/component/DataComponentMap;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IIZI)V",
            require = 0, index = 5, at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;IIILnet/minecraft/client/resources/model/sprite/SpriteId;Lnet/minecraft/client/resources/model/sprite/SpriteGetter;I)V"))
    private int cosmeticsmod$plain(int tint) {
        return FirstPersonHands.shieldTint(tint);
    }

    @ModifyArg(method = "submit(Lnet/minecraft/core/component/DataComponentMap;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IIZI)V",
            require = 0, index = 6, at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/UvMapping;I)V"))
    private int cosmeticsmod$enchanted(int tint) {
        return FirstPersonHands.shieldTint(tint);
    }
}
*///?} elif >=26.1 {
/*@Mixin(net.minecraft.client.renderer.special.ShieldSpecialRenderer.class)
abstract class ShieldStatusMixin {
    @ModifyArg(method = "submit(Lnet/minecraft/core/component/DataComponentMap;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IIZI)V",
            require = 0, index = 5, at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;IIILnet/minecraft/client/resources/model/sprite/SpriteId;Lnet/minecraft/client/resources/model/sprite/SpriteGetter;ILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V"))
    private int cosmeticsmod$plain(int tint) {
        return FirstPersonHands.shieldTint(tint);
    }
}
*///?} elif >=1.21.11 {
@Mixin(net.minecraft.client.renderer.special.ShieldSpecialRenderer.class)
abstract class ShieldStatusMixin {
    @ModifyArg(method = "submit(Lnet/minecraft/core/component/DataComponentMap;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IIZI)V",
            require = 0, index = 8, at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModelPart(Lnet/minecraft/client/model/geom/ModelPart;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IILnet/minecraft/client/renderer/texture/TextureAtlasSprite;ZZILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;I)V"))
    private int cosmeticsmod$plain(int tint) {
        return FirstPersonHands.shieldTint(tint);
    }
}
//?} elif >=1.21.9 {
/*@Mixin(net.minecraft.client.renderer.special.ShieldSpecialRenderer.class)
abstract class ShieldStatusMixin {
    @ModifyArg(method = "submit(Lnet/minecraft/core/component/DataComponentMap;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IIZI)V",
            require = 0, index = 8, at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModelPart(Lnet/minecraft/client/model/geom/ModelPart;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/RenderType;IILnet/minecraft/client/renderer/texture/TextureAtlasSprite;ZZILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;I)V"))
    private int cosmeticsmod$plain(int tint) {
        return FirstPersonHands.shieldTint(tint);
    }
}
*///?} elif >=1.21.4 {
/*@Mixin(net.minecraft.client.renderer.special.ShieldSpecialRenderer.class)
abstract class ShieldStatusMixin {
    @Redirect(method = "render(Lnet/minecraft/core/component/DataComponentMap;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IIZ)V",
            require = 0, at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/model/geom/ModelPart;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;II)V"))
    private void cosmeticsmod$part(ModelPart part, PoseStack pose, VertexConsumer consumer, int light, int overlay) {
        part.render(pose, consumer, light, overlay, FirstPersonHands.shieldTint(-1));
    }
}
*///?} else {
/*@Mixin(net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer.class)
abstract class ShieldStatusMixin {
    @org.spongepowered.asm.mixin.injection.Inject(method = "renderByItem", at = @At("HEAD"), require = 0)
    private void cosmeticsmod$which(net.minecraft.world.item.ItemStack stack, net.minecraft.world.item.ItemDisplayContext context,
                                    PoseStack pose, net.minecraft.client.renderer.MultiBufferSource buffers, int light, int overlay,
                                    org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        FirstPersonHands.shieldNow = stack.is(net.minecraft.world.item.Items.SHIELD);
    }

    @Redirect(method = "renderByItem", require = 0, at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/model/geom/ModelPart;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;II)V"))
    private void cosmeticsmod$part(ModelPart part, PoseStack pose, VertexConsumer consumer, int light, int overlay) {
        part.render(pose, consumer, light, overlay, FirstPersonHands.shieldNow ? FirstPersonHands.shieldTint(-1) : -1);
    }
}
*///?}
