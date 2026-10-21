package io.github.georgwav.cosmetics.mixin;

import io.github.georgwav.cosmetics.melee.KnifeInput;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Knives with Punchy installed: at the very start of the game's tick (applied early, before Punchy's and the tick
 * events), a press of Punchy's inspect key goes to the knife in hand instead of Punchy.
 */
@Mixin(value = Minecraft.class, priority = 500)
abstract class KnifeKeyMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void cosmeticsmod$inspectKey(CallbackInfo ci) {
        KnifeInput.beforeTick((Minecraft) (Object) this);
    }
}
