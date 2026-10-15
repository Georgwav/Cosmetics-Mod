package io.github.georgwav.cosmetics.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.georgwav.cosmetics.client.modules.HeldItems;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Held Items: the first-person item moved and sized after the game puts it by its arm. */
//? if >=26.3 {
/*@Mixin(net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer.class)
*///?} else {
@Mixin(net.minecraft.client.renderer.ItemInHandRenderer.class)
//?}
abstract class HeldItemsMixin {
    @Inject(method = "applyItemArmTransform(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/entity/HumanoidArm;F)V",
            at = @At("TAIL"), require = 0)
    private void cosmeticsmod$place(PoseStack pose, HumanoidArm arm, float equip, CallbackInfo ci) {
        io.github.georgwav.cosmetics.sol.Hooks.HELD_ITEM.hit();
        HeldItems.apply(pose, arm);
    }
}
