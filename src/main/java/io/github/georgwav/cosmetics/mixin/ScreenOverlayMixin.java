package io.github.georgwav.cosmetics.mixin;

import io.github.georgwav.cosmetics.client.modules.ScreenTweaks;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if >=26.1 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?} else {
import net.minecraft.client.gui.GuiGraphics;
//?}
//? if >=1.21.11 {
import net.minecraft.resources.Identifier;
//?} else {
/*import net.minecraft.resources.ResourceLocation;
*///?}

/** Screen: the overlays it hides (pumpkin, powder snow, vignette, portal, nausea), skipped at the game's draw. */
//? if >=26.2 {
/*@Mixin(net.minecraft.client.gui.Hud.class)
*///?} else {
@Mixin(net.minecraft.client.gui.Gui.class)
//?}
abstract class ScreenOverlayMixin {
    //? if >=26.1 {
    /*@Inject(method = "extractTextureOverlay", at = @At("HEAD"), cancellable = true, require = 0)
    private void cosmeticsmod$texture(GuiGraphicsExtractor graphics, Identifier texture, float alpha, CallbackInfo ci) {
        if (ScreenTweaks.hidesTexture(texture.getPath())) {
            ci.cancel();
        }
    }

    @Inject(method = "extractVignette", at = @At("HEAD"), cancellable = true, require = 0)
    private void cosmeticsmod$vignette(GuiGraphicsExtractor graphics, Entity camera, CallbackInfo ci) {
        if (ScreenTweaks.hidesVignette()) {
            ci.cancel();
        }
    }

    @Inject(method = "extractPortalOverlay", at = @At("HEAD"), cancellable = true, require = 0)
    private void cosmeticsmod$portal(GuiGraphicsExtractor graphics, float alpha, CallbackInfo ci) {
        if (ScreenTweaks.hidesPortal()) {
            ci.cancel();
        }
    }

    @Inject(method = "extractConfusionOverlay", at = @At("HEAD"), cancellable = true, require = 0)
    private void cosmeticsmod$nausea(GuiGraphicsExtractor graphics, float strength, CallbackInfo ci) {
        if (ScreenTweaks.hidesNausea()) {
            ci.cancel();
        }
    }
    *///?} else {
    //? if >=1.21.11 {
    @Inject(method = "renderTextureOverlay", at = @At("HEAD"), cancellable = true, require = 0)
    private void cosmeticsmod$texture(GuiGraphics graphics, Identifier texture, float alpha, CallbackInfo ci) {
        if (ScreenTweaks.hidesTexture(texture.getPath())) {
            ci.cancel();
        }
    }
    //?} else {
    /*@Inject(method = "renderTextureOverlay", at = @At("HEAD"), cancellable = true, require = 0)
    private void cosmeticsmod$texture(GuiGraphics graphics, ResourceLocation texture, float alpha, CallbackInfo ci) {
        if (ScreenTweaks.hidesTexture(texture.getPath())) {
            ci.cancel();
        }
    }
    *///?}

    @Inject(method = "renderVignette", at = @At("HEAD"), cancellable = true, require = 0)
    private void cosmeticsmod$vignette(GuiGraphics graphics, Entity camera, CallbackInfo ci) {
        if (ScreenTweaks.hidesVignette()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderPortalOverlay", at = @At("HEAD"), cancellable = true, require = 0)
    private void cosmeticsmod$portal(GuiGraphics graphics, float alpha, CallbackInfo ci) {
        if (ScreenTweaks.hidesPortal()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderConfusionOverlay", at = @At("HEAD"), cancellable = true, require = 0)
    private void cosmeticsmod$nausea(GuiGraphics graphics, float strength, CallbackInfo ci) {
        if (ScreenTweaks.hidesNausea()) {
            ci.cancel();
        }
    }
    //?}
}
