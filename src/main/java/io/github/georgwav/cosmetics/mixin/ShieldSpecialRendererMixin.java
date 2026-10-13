package io.github.georgwav.cosmetics.mixin;

// Tags a held shield with its player's shield cosmetic when its look is worked out (extractArgument), and draws
// the cosmetic instead of the vanilla shield when it is drawn. 1.21.4 and later; before, see
// ShieldItemRendererMixin.
//? if >=26.1 {
/*import com.mojang.blaze3d.vertex.PoseStack;
import io.github.georgwav.cosmetics.render.HeldItems;
import net.minecraft.client.renderer.special.ShieldSpecialRenderer;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.client.renderer.SubmitNodeCollector;

@Mixin(ShieldSpecialRenderer.class)
abstract class ShieldSpecialRendererMixin {
    @Inject(method = "extractArgument(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/core/component/DataComponentMap;",
            at = @At("RETURN"), cancellable = true)
    private void cosmeticsmod$tag(ItemStack stack, CallbackInfoReturnable<DataComponentMap> cir) {
        DataComponentMap components = cir.getReturnValue();
        DataComponentMap tagged = HeldItems.tag(components);
        if (tagged != components) {
            cir.setReturnValue(tagged);
        }
    }

    @Inject(method = "submit(Lnet/minecraft/core/component/DataComponentMap;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IIZI)V",
            at = @At("HEAD"), cancellable = true)
    private void cosmeticsmod$draw(DataComponentMap components, PoseStack poseStack, SubmitNodeCollector collector,
                                   int light, int overlay, boolean foil, int outline, CallbackInfo ci) {
        if (HeldItems.drawTagged(components, poseStack, collector, light)) {
            ci.cancel();
        }
    }
}
*///?} elif >=1.21.9 {
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.georgwav.cosmetics.render.HeldItems;
import net.minecraft.client.renderer.special.ShieldSpecialRenderer;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.item.ItemDisplayContext;

@Mixin(ShieldSpecialRenderer.class)
abstract class ShieldSpecialRendererMixin {
    @Inject(method = "extractArgument(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/core/component/DataComponentMap;",
            at = @At("RETURN"), cancellable = true)
    private void cosmeticsmod$tag(ItemStack stack, CallbackInfoReturnable<DataComponentMap> cir) {
        DataComponentMap components = cir.getReturnValue();
        DataComponentMap tagged = HeldItems.tag(components);
        if (tagged != components) {
            cir.setReturnValue(tagged);
        }
    }

    @Inject(method = "submit(Lnet/minecraft/core/component/DataComponentMap;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IIZI)V",
            at = @At("HEAD"), cancellable = true)
    private void cosmeticsmod$draw(DataComponentMap components, ItemDisplayContext context, PoseStack poseStack,
                                   SubmitNodeCollector collector, int light, int overlay, boolean foil, int outline,
                                   CallbackInfo ci) {
        if (HeldItems.drawTagged(components, poseStack, collector, light)) {
            ci.cancel();
        }
    }
}
//?} elif >=1.21.4 {
/*import com.mojang.blaze3d.vertex.PoseStack;
import io.github.georgwav.cosmetics.render.HeldItems;
import net.minecraft.client.renderer.special.ShieldSpecialRenderer;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;

@Mixin(ShieldSpecialRenderer.class)
abstract class ShieldSpecialRendererMixin {
    @Inject(method = "extractArgument(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/core/component/DataComponentMap;",
            at = @At("RETURN"), cancellable = true)
    private void cosmeticsmod$tag(ItemStack stack, CallbackInfoReturnable<DataComponentMap> cir) {
        DataComponentMap components = cir.getReturnValue();
        DataComponentMap tagged = HeldItems.tag(components);
        if (tagged != components) {
            cir.setReturnValue(tagged);
        }
    }

    @Inject(method = "render(Lnet/minecraft/core/component/DataComponentMap;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IIZ)V",
            at = @At("HEAD"), cancellable = true)
    private void cosmeticsmod$draw(DataComponentMap components, ItemDisplayContext context, PoseStack poseStack,
                                   MultiBufferSource buffers, int light, int overlay, boolean foil, CallbackInfo ci) {
        if (HeldItems.drawTagged(components, poseStack, buffers, light)) {
            ci.cancel();
        }
    }
}
*///?}
