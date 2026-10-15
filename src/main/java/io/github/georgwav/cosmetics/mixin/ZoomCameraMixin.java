package io.github.georgwav.cosmetics.mixin;

// From 26.1 (before it, see ZoomFovMixin): the camera works out the world's field of view; Zoom narrows it.
//? if >=26.1 {
/*import io.github.georgwav.cosmetics.client.modules.Zoom;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
abstract class ZoomCameraMixin {
    @Inject(method = "calculateFov(F)F", at = @At("RETURN"), cancellable = true, require = 0)
    private void cosmeticsmod$zoom(float partialTick, CallbackInfoReturnable<Float> cir) {
        io.github.georgwav.cosmetics.sol.Hooks.ZOOM_FOV.hit();
        cir.setReturnValue(Zoom.fov(cir.getReturnValueF()));
    }
}
*///?}
