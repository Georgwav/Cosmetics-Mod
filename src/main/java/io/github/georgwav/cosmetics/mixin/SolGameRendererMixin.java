package io.github.georgwav.cosmetics.mixin;

import io.github.georgwav.cosmetics.sol.SolHooks;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The end of drawing the level, the hand included: the Sol light pass's bloom, grade and vignette go over the
 * finished frame here, before the HUD. And the start of each frame, for Sol's GUI batches.
 */
@Mixin(GameRenderer.class)
abstract class SolGameRendererMixin {
    @Inject(method = "render", at = @At("HEAD"), require = 0)
    private void cosmeticsmod$frame(CallbackInfo ci) {
        io.github.georgwav.cosmetics.sol.render.GuiDraw.gameFrame();
    }

    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void cosmeticsmod$afterHand(CallbackInfo ci) {
        SolHooks.afterHand();
    }
}
