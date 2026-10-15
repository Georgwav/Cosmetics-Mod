package io.github.georgwav.cosmetics.mixin;

import io.github.georgwav.cosmetics.sol.ui.screens.VanillaLook;
import io.github.georgwav.cosmetics.ui.Canvas;
import net.minecraft.client.gui.screens.LoadingOverlay;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The loading screen in Sol's look (Sol UI on): Sol's dark in place of the red (or dark) Mojang wash, the Mojang logo
 * and bar hidden, and the square sun rising out of the horizon as the progress; the game's own fades kept.
 */
@Mixin(LoadingOverlay.class)
public abstract class LoadingLookMixin {
    @Shadow
    private float currentProgress;
    @Shadow
    private long fadeOutStart;
    @Shadow
    private long fadeInStart;
    @Shadow
    @Final
    private boolean fadeIn;

    @Inject(method = "replaceAlpha", at = @At("RETURN"), cancellable = true, require = 0)
    private static void cosmeticsmod$background(int color, int alpha, CallbackInfoReturnable<Integer> cir) {
        if (VanillaLook.on()) {
            cir.setReturnValue(VanillaLook.loadingBackground(cir.getReturnValue()));
        }
    }

    //? if >=26.1 {
    /*@Redirect(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/ARGB;white(F)I"), require = 0)
    private int cosmeticsmod$hideLogo(float alpha) {
        return VanillaLook.on() ? 0x00FFFFFF : net.minecraft.util.ARGB.white(alpha);
    }

    @Inject(method = "extractProgressBar", at = @At("HEAD"), cancellable = true, require = 0)
    private void cosmeticsmod$hideBar(CallbackInfo ci) {
        if (VanillaLook.on()) {
            ci.cancel();
        }
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"), require = 0)
    private void cosmeticsmod$sun(net.minecraft.client.gui.GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick,
                                  CallbackInfo ci) {
        if (VanillaLook.on()) {
            VanillaLook.loading(new Canvas(graphics), graphics.guiWidth(), graphics.guiHeight(), currentProgress, alpha());
        }
    }
    *///?} else {
    //? if >=1.21.2 {
    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/ARGB;white(F)I"), require = 0)
    private int cosmeticsmod$hideLogo(float alpha) {
        return VanillaLook.on() ? 0x00FFFFFF : net.minecraft.util.ARGB.white(alpha);
    }
    //?} else {
    /*@Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;blit(Lnet/minecraft/resources/ResourceLocation;IIIIFFIIII)V"), require = 0)
    private void cosmeticsmod$hideLogo(net.minecraft.client.gui.GuiGraphics g, net.minecraft.resources.ResourceLocation id, int x, int y,
                                       int w, int h, float u, float v, int uw, int vh, int tw, int th) {
        if (!VanillaLook.on()) {
            g.blit(id, x, y, w, h, u, v, uw, vh, tw, th);
        }
    }
    *///?}

    @Inject(method = "drawProgressBar", at = @At("HEAD"), cancellable = true, require = 0)
    private void cosmeticsmod$hideBar(CallbackInfo ci) {
        if (VanillaLook.on()) {
            ci.cancel();
        }
    }

    @Inject(method = "render", at = @At("TAIL"), require = 0)
    private void cosmeticsmod$sun(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (VanillaLook.on()) {
            VanillaLook.loading(new Canvas(graphics), graphics.guiWidth(), graphics.guiHeight(), currentProgress, alpha());
        }
    }
    //?}

    /** The game's own fade: in over half a second on a reload, out over a second when done. */
    private float alpha() {
        long now = System.nanoTime() / 1_000_000L;
        float out = fadeOutStart > -1L ? (now - fadeOutStart) / 1000f : -1f;
        float in = fadeInStart > -1L ? (now - fadeInStart) / 500f : -1f;
        if (out >= 1f) {
            return 1f - Math.max(0f, Math.min(1f, out - 1f));
        }
        return fadeIn ? Math.max(0f, Math.min(1f, in)) : 1f;
    }
}
