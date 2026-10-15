package io.github.georgwav.cosmetics.mixin;

import io.github.georgwav.cosmetics.client.modules.HeldItems;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Held Items' swing speed: your own swing's length (getCurrentSwingDuration; getModifiedSwingDuration from 26.3). */
@Mixin(LivingEntity.class)
abstract class SwingMixin {
    //? if >=26.3 {
    /*@Inject(method = "getModifiedSwingDuration(Lnet/minecraft/world/item/component/SwingAnimation;)I", at = @At("RETURN"),
            cancellable = true, require = 0)
    private void cosmeticsmod$swing(net.minecraft.world.item.component.SwingAnimation animation, CallbackInfoReturnable<Integer> cir) {
        cosmeticsmod$own(cir);
    }
    *///?} else {
    @Inject(method = "getCurrentSwingDuration()I", at = @At("RETURN"), cancellable = true, require = 0)
    private void cosmeticsmod$swing(CallbackInfoReturnable<Integer> cir) {
        cosmeticsmod$own(cir);
    }
    //?}

    private void cosmeticsmod$own(CallbackInfoReturnable<Integer> cir) {
        if ((Object) this == Minecraft.getInstance().player) {
            cir.setReturnValue(HeldItems.swing(cir.getReturnValueI()));
        }
    }
}
