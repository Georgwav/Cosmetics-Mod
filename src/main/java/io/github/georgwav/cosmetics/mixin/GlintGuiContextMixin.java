package io.github.georgwav.cosmetics.mixin;

// Before 1.21.4 an item is drawn the moment its look is worked out: Glint learns here whether it's an inventory
// picture (from 1.21.4, ItemModelResolverMixin tells it).
//? if <1.21.4 {
/*import com.mojang.blaze3d.vertex.PoseStack;
import io.github.georgwav.cosmetics.client.modules.GlintModule;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemRenderer.class)
abstract class GlintGuiContextMixin {
    @Inject(method = "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V",
            at = @At("HEAD"))
    private void cosmeticsmod$enter(ItemStack stack, ItemDisplayContext context, boolean leftHand, PoseStack pose,
                                    MultiBufferSource buffers, int light, int overlay, BakedModel model, CallbackInfo ci) {
        GlintModule.drawing(context == ItemDisplayContext.GUI);
    }

    @Inject(method = "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V",
            at = @At("RETURN"))
    private void cosmeticsmod$exit(CallbackInfo ci) {
        GlintModule.drawing(false);
    }
}
*///?}
