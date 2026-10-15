package io.github.georgwav.cosmetics.mixin;

import io.github.georgwav.cosmetics.sol.atmosphere.WorldTint;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if >=1.21.2 {
import net.minecraft.client.renderer.CloudRenderer;
//?} else {
/*import net.minecraft.client.renderer.LevelRenderer;
*///?}

/**
 * Leaves the game's clouds out while a theme with a clear sky is on (the prototypes' skies have none; Monsoon
 * keeps its overcast). Up to 1.21.1 the level renderer draws them; from 1.21.2 the cloud renderer.
 */
//? if >=1.21.2 {
@Mixin(CloudRenderer.class)
abstract class SolCloudsMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void cosmeticsmod$clouds(CallbackInfo ci) {
        if (!WorldTint.cloudsShown()) {
            ci.cancel();
        }
    }
    //? if >=26.3 {
    /*@Inject(method = "renderOit", at = @At("HEAD"), cancellable = true, require = 0)
    private void cosmeticsmod$cloudsOit(CallbackInfo ci) {
        if (!WorldTint.cloudsShown()) {
            ci.cancel();
        }
    }
    *///?}
}
//?} else {
/*@Mixin(LevelRenderer.class)
abstract class SolCloudsMixin {
    @Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true)
    private void cosmeticsmod$clouds(CallbackInfo ci) {
        if (!WorldTint.cloudsShown()) {
            ci.cancel();
        }
    }
}
*///?}
