package io.github.georgwav.cosmetics.mixin;

// Zoom's field of view: the game's world FOV (not the hand's), narrowed by Zoom. Up to 1.21.11 GameRenderer.getFov
// (a double in 1.21.1); from 26.1 the camera works it out (ZoomCameraMixin).
//? if <26.1 {
import io.github.georgwav.cosmetics.client.modules.Zoom;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
abstract class ZoomFovMixin {
    //? if >=1.21.2 {
    @Inject(method = "getFov(Lnet/minecraft/client/Camera;FZ)F", at = @At("RETURN"), cancellable = true, require = 0)
    private void cosmeticsmod$zoom(Camera camera, float partialTick, boolean useFovSetting, CallbackInfoReturnable<Float> cir) {
        io.github.georgwav.cosmetics.sol.Hooks.ZOOM_FOV.hit();
        if (useFovSetting) {
            cir.setReturnValue(Zoom.fov(cir.getReturnValueF()));
        }
    }
    //?} else {
    /*@Inject(method = "getFov(Lnet/minecraft/client/Camera;FZ)D", at = @At("RETURN"), cancellable = true, require = 0)
    private void cosmeticsmod$zoom(Camera camera, float partialTick, boolean useFovSetting, CallbackInfoReturnable<Double> cir) {
        io.github.georgwav.cosmetics.sol.Hooks.ZOOM_FOV.hit();
        if (useFovSetting) {
            cir.setReturnValue(Zoom.fov(cir.getReturnValueD()));
        }
    }
    *///?}
}
//?}
