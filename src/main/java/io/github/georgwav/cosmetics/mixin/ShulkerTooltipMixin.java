package io.github.georgwav.cosmetics.mixin;

import io.github.georgwav.cosmetics.sol.client.ShulkerPreview;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/** Inventory's shulker previews: a box's tooltip image is its contents (when the game has none for it). */
@Mixin(ItemStack.class)
abstract class ShulkerTooltipMixin {
    @Inject(method = "getTooltipImage", at = @At("RETURN"), cancellable = true, require = 0)
    private void cosmeticsmod$preview(CallbackInfoReturnable<Optional<TooltipComponent>> cir) {
        if (cir.getReturnValue().isEmpty()) {
            Optional<TooltipComponent> preview = ShulkerPreview.of((ItemStack) (Object) this);
            if (preview.isPresent()) {
                cir.setReturnValue(preview);
            }
        }
    }
}
