package io.github.georgwav.cosmetics.mixin;

import io.github.georgwav.cosmetics.client.modules.GlintModule;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Glint by place: worn armour's shimmer, where the game asks the piece for it (one place per era). */
//? if >=1.21.2 {
@Mixin(net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer.class)
//?} else {
/*@Mixin(net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer.class)
*///?}
abstract class GlintArmourMixin {
    //? if >=1.21.2 {
    @Redirect(method = "renderLayers", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;hasFoil()Z"), require = 0)
    //?} else {
    /*@Redirect(method = "renderArmorPiece", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;hasFoil()Z"), require = 0)
    *///?}
    private boolean cosmeticsmod$armour(ItemStack stack) {
        return stack.hasFoil() && GlintModule.armourShows();
    }
}
