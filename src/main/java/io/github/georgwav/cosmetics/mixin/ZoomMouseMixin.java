package io.github.georgwav.cosmetics.mixin;

import io.github.georgwav.cosmetics.client.modules.Zoom;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Zoom's mouse: slower turning while zoomed, and the scroll wheel zooming instead of changing the slot. */
@Mixin(MouseHandler.class)
abstract class ZoomMouseMixin {
    @Shadow
    private double accumulatedDX;
    @Shadow
    private double accumulatedDY;

    @Inject(method = "turnPlayer(D)V", at = @At("HEAD"), require = 0)
    private void cosmeticsmod$slower(double time, CallbackInfo ci) {
        double k = Zoom.sensitivity();
        if (k != 1.0) {
            accumulatedDX *= k;
            accumulatedDY *= k;
        }
    }

    @Inject(method = "onScroll(JDD)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void cosmeticsmod$scroll(long window, double x, double y, CallbackInfo ci) {
        if (io.github.georgwav.cosmetics.ui.ScreenOpener.current() == null && Zoom.scroll(y)) {
            ci.cancel();
        }
    }
}
