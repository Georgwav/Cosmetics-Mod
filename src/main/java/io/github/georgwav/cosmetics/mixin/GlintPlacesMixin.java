package io.github.georgwav.cosmetics.mixin;

import io.github.georgwav.cosmetics.client.modules.GlintModule;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
//? if >=1.21.4 {
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//?} else {
/*import org.spongepowered.asm.mixin.injection.Redirect;
*///?}

/**
 * Glint by place: an item shows its shimmer only where Glint lets it (in hand, in inventories, dropped, in item frames,
 * worn on the head). From 1.21.4 the item's layers are filled for where it's shown (the model resolver), so their
 * foil is cleared there; before, the item renderer asks the stack, and the answer is changed for that place.
 */
//? if >=1.21.4 {
@Mixin(net.minecraft.client.renderer.item.ItemModelResolver.class)
//?} else {
/*@Mixin(net.minecraft.client.renderer.entity.ItemRenderer.class)
*///?}
abstract class GlintPlacesMixin {
    //? if >=1.21.9 {
    @Inject(method = "appendItemLayers", at = @At("TAIL"), require = 0)
    private void cosmeticsmod$places(net.minecraft.client.renderer.item.ItemStackRenderState state, ItemStack stack,
                                     ItemDisplayContext context, net.minecraft.world.level.Level level,
                                     net.minecraft.world.entity.ItemOwner owner, int seed, CallbackInfo ci) {
        cosmeticsmod$clear(state, context);
    }
    //?} elif >=1.21.4 {
    /*@Inject(method = "appendItemLayers", at = @At("TAIL"), require = 0)
    private void cosmeticsmod$places(net.minecraft.client.renderer.item.ItemStackRenderState state, ItemStack stack,
                                     ItemDisplayContext context, net.minecraft.world.level.Level level,
                                     net.minecraft.world.entity.LivingEntity entity, int seed, CallbackInfo ci) {
        cosmeticsmod$clear(state, context);
    }
    *///?} elif >=1.21.2 {
    /*@Redirect(method = "renderItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;hasFoil()Z"), require = 0)
    private boolean cosmeticsmod$places(ItemStack stack, ItemStack item, ItemDisplayContext context,
                                        com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.client.renderer.MultiBufferSource buffers,
                                        int light, int overlay, net.minecraft.client.resources.model.BakedModel model, boolean flag) {
        return stack.hasFoil() && GlintModule.shows(context);
    }
    *///?} else {
    /*@Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;hasFoil()Z"), require = 0)
    private boolean cosmeticsmod$places(ItemStack stack, ItemStack item, ItemDisplayContext context, boolean leftHand,
                                        com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.client.renderer.MultiBufferSource buffers,
                                        int light, int overlay, net.minecraft.client.resources.model.BakedModel model) {
        return stack.hasFoil() && GlintModule.shows(context);
    }
    *///?}

    //? if >=1.21.4 {
    private static void cosmeticsmod$clear(net.minecraft.client.renderer.item.ItemStackRenderState state, ItemDisplayContext context) {
        if (GlintModule.shows(context)) {
            return;
        }
        var layers = ((ItemLayersAccess) state).cosmeticsmod$layers();
        int count = ((ItemLayersAccess) state).cosmeticsmod$count();
        for (int i = 0; i < count && i < layers.length; i++) {
            layers[i].setFoilType(net.minecraft.client.renderer.item.ItemStackRenderState.FoilType.NONE);
        }
    }
    //?}
}
