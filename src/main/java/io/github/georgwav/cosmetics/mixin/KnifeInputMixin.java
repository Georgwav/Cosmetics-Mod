package io.github.georgwav.cosmetics.mixin;

import io.github.georgwav.cosmetics.melee.KnifeInput;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Knives: the attack click (a critical hit is decided before the click resets the attack charge), its swing, the
 * swings of attack held on a block, and the use key, handed to {@link KnifeInput}.
 */
@Mixin(Minecraft.class)
abstract class KnifeInputMixin {
    @Inject(method = "startAttack", at = @At("HEAD"))
    private void cosmeticsmod$click(CallbackInfoReturnable<Boolean> cir) {
        KnifeInput.clicked((Minecraft) (Object) this);
    }

    //? if >=26.3 {
    /*@Inject(method = "startAttack", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/player/LocalPlayer;swing(Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/item/component/SwingAnimation;Z)Z"))
    *///?} else {
    @Inject(method = "startAttack", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/player/LocalPlayer;swing(Lnet/minecraft/world/InteractionHand;)V"))
    //?}
    private void cosmeticsmod$attack(CallbackInfoReturnable<Boolean> cir) {
        KnifeInput.attacked((Minecraft) (Object) this);
    }

    //? if >=26.3 {
    /*@Inject(method = "continueAttack", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/player/LocalPlayer;swing(Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/item/component/SwingAnimation;Z)Z"))
    *///?} else {
    @Inject(method = "continueAttack", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/player/LocalPlayer;swing(Lnet/minecraft/world/InteractionHand;)V"))
    //?}
    private void cosmeticsmod$held(boolean leftClick, CallbackInfo ci) {
        KnifeInput.swungOnBlock((Minecraft) (Object) this);
    }

    @Inject(method = "startUseItem", at = @At("HEAD"))
    private void cosmeticsmod$use(CallbackInfo ci) {
        KnifeInput.used();
    }
}
