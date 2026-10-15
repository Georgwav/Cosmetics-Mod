package io.github.georgwav.cosmetics.mixin;

import io.github.georgwav.cosmetics.client.Modules;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** While the game saves its options, modules put the player's own values back (see Fullbright). */
@Mixin(Options.class)
abstract class OptionsMixin {
    @Inject(method = "save", at = @At("HEAD"))
    private void cosmeticsmod$beforeSave(CallbackInfo ci) {
        Modules.beforeOptionsSave();
    }

    @Inject(method = "save", at = @At("RETURN"))
    private void cosmeticsmod$afterSave(CallbackInfo ci) {
        Modules.afterOptionsSave();
    }
}
