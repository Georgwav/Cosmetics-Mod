package io.github.georgwav.cosmetics.mixin;

// Before 1.21.4, shields are drawn by BlockEntityWithoutLevelRenderer.renderByItem; a held shield of a player
// with a shield cosmetic draws the cosmetic instead.
//? if <1.21.4 {
/*import com.mojang.blaze3d.vertex.PoseStack;
import io.github.georgwav.cosmetics.render.HeldItems;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockEntityWithoutLevelRenderer.class)
abstract class ShieldItemRendererMixin {
    @Inject(method = "renderByItem", at = @At("HEAD"), cancellable = true)
    private void cosmeticsmod$draw(ItemStack stack, ItemDisplayContext context, PoseStack poseStack,
                                   MultiBufferSource buffers, int light, int overlay, CallbackInfo ci) {
        if (stack.is(Items.SHIELD) && HeldItems.drawShield(poseStack, buffers, light, context)) {
            ci.cancel();
        }
    }
}
*///?}
