package io.github.georgwav.cosmetics.mixin;

import io.github.georgwav.cosmetics.client.modules.Nametags;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Nametags: whether a living entity's tag shows (with the distance to it from 1.21.2). */
@Mixin(LivingEntityRenderer.class)
abstract class NametagMixin {
    //? if >=1.21.2 {
    @Inject(method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;D)Z", at = @At("RETURN"), cancellable = true, require = 0)
    private void cosmeticsmod$name(LivingEntity entity, double distance, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(Nametags.shows(entity, cir.getReturnValueZ()));
    }
    //?} else {
    /*@Inject(method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;)Z", at = @At("RETURN"), cancellable = true, require = 0)
    private void cosmeticsmod$name(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(Nametags.shows(entity, cir.getReturnValueZ()));
    }
    *///?}
}
