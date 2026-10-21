package io.github.georgwav.cosmetics.mixin;

import io.github.georgwav.cosmetics.client.modules.GlintModule;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Glint's "Outline only": an item drawn in an inventory or the hotbar has no shimmer (its outline shows instead). */
@Mixin(ItemStack.class)
abstract class GlintFoilMixin {
    @Inject(method = "hasFoil", at = @At("RETURN"), cancellable = true)
    private void cosmeticsmod$noGuiFoil(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && GlintModule.hidesFoil()) {
            cir.setReturnValue(false);
        }
    }
}
