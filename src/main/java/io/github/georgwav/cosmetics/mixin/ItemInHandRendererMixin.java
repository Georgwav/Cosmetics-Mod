package io.github.georgwav.cosmetics.mixin;

// Before 1.21.4, items in hands are drawn through ItemInHandRenderer.renderItem (first person, and third person
// up to 1.21.1), with whose hand it is; HeldItems remembers it while the item is drawn.
//? if <1.21.4 {
/*import com.mojang.blaze3d.vertex.PoseStack;
import io.github.georgwav.cosmetics.render.HeldItems;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
abstract class ItemInHandRendererMixin {
    @Inject(method = "renderItem", at = @At("HEAD"))
    private void cosmeticsmod$enter(LivingEntity entity, ItemStack stack, ItemDisplayContext context, boolean leftHand,
                                    PoseStack poseStack, MultiBufferSource buffers, int light, CallbackInfo ci) {
        HeldItems.enter(entity, context);
    }

    @Inject(method = "renderItem", at = @At("RETURN"))
    private void cosmeticsmod$exit(CallbackInfo ci) {
        HeldItems.exit();
    }
}
*///?}
