package io.github.georgwav.cosmetics.mixin;

// 1.21.2 and 1.21.3 draw items in third person straight from the player's render state, without
// ItemInHandRenderer; the state's entity id says whose hand it is. Up to 1.21.3 a knife held in third person leaves the
// hand's own item out here (from 1.21.4 in ItemModelResolverMixin).
//? if <1.21.2 {
/*import io.github.georgwav.cosmetics.melee.KnifeThirdPerson;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandLayer.class)
abstract class ItemInHandLayerMixin {
    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
    private void cosmeticsmod$knife(LivingEntity entity, ItemStack stack, ItemDisplayContext context,
                                   net.minecraft.world.entity.HumanoidArm arm, com.mojang.blaze3d.vertex.PoseStack poseStack,
                                   net.minecraft.client.renderer.MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (KnifeThirdPerson.hides(entity, stack, context)) {
            ci.cancel();
        }
    }
}
*///?} elif <1.21.4 {
/*import com.mojang.blaze3d.vertex.PoseStack;
import io.github.georgwav.cosmetics.render.HeldItems;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandLayer.class)
abstract class ItemInHandLayerMixin {
    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
    private void cosmeticsmod$enter(LivingEntityRenderState state, BakedModel model, ItemStack stack,
                                    ItemDisplayContext context, HumanoidArm arm, PoseStack poseStack,
                                    MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (state instanceof PlayerRenderState player) {
            if (io.github.georgwav.cosmetics.melee.KnifeThirdPerson.hides(
                    io.github.georgwav.cosmetics.render.HeldItems.entityOf(player.id), stack, context)) {
                ci.cancel();
                return;
            }
            HeldItems.enterEntity(player.id, context);
        }
    }

    @Inject(method = "renderArmWithItem", at = @At("RETURN"))
    private void cosmeticsmod$exit(CallbackInfo ci) {
        HeldItems.exit();
    }
}
*///?}
