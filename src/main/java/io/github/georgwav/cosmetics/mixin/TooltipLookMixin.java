package io.github.georgwav.cosmetics.mixin;

import io.github.georgwav.cosmetics.sol.ui.screens.VanillaLook;
import io.github.georgwav.cosmetics.ui.Canvas;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Tooltips in Sol glass (Sol UI on), unless an item asks for its own tooltip style. */
@Mixin(TooltipRenderUtil.class)
public abstract class TooltipLookMixin {
    //? if >=26.1 {
    /*@Inject(method = "extractTooltipBackground", at = @At("HEAD"), cancellable = true, require = 0)
    private static void cosmeticsmod$glass(net.minecraft.client.gui.GuiGraphicsExtractor graphics, int x, int y, int w, int h,
                                           net.minecraft.resources.Identifier style, CallbackInfo ci) {
        if (style == null && VanillaLook.on() && VanillaLook.tooltip(new Canvas(graphics), x, y, w, h, 0)) {
            ci.cancel();
        }
    }
    *///?} elif >=1.21.11 {
    @Inject(method = "renderTooltipBackground", at = @At("HEAD"), cancellable = true, require = 0)
    private static void cosmeticsmod$glass(net.minecraft.client.gui.GuiGraphics graphics, int x, int y, int w, int h,
                                           net.minecraft.resources.Identifier style, CallbackInfo ci) {
        if (style == null && VanillaLook.on() && VanillaLook.tooltip(new Canvas(graphics), x, y, w, h, 0)) {
            ci.cancel();
        }
    }
    //?} elif >=1.21.6 {
    /*@Inject(method = "renderTooltipBackground", at = @At("HEAD"), cancellable = true, require = 0)
    private static void cosmeticsmod$glass(net.minecraft.client.gui.GuiGraphics graphics, int x, int y, int w, int h,
                                           net.minecraft.resources.ResourceLocation style, CallbackInfo ci) {
        if (style == null && VanillaLook.on() && VanillaLook.tooltip(new Canvas(graphics), x, y, w, h, 0)) {
            ci.cancel();
        }
    }
    *///?} elif >=1.21.2 {
    /*@Inject(method = "renderTooltipBackground", at = @At("HEAD"), cancellable = true, require = 0)
    private static void cosmeticsmod$glass(net.minecraft.client.gui.GuiGraphics graphics, int x, int y, int w, int h, int z,
                                           net.minecraft.resources.ResourceLocation style, CallbackInfo ci) {
        if (style == null && VanillaLook.on() && VanillaLook.tooltip(new Canvas(graphics), x, y, w, h, z)) {
            ci.cancel();
        }
    }
    *///?} else {
    /*@Inject(method = "renderTooltipBackground", at = @At("HEAD"), cancellable = true, require = 0)
    private static void cosmeticsmod$glass(net.minecraft.client.gui.GuiGraphics graphics, int x, int y, int w, int h, int z,
                                           CallbackInfo ci) {
        if (VanillaLook.on() && VanillaLook.tooltip(new Canvas(graphics), x, y, w, h, z)) {
            ci.cancel();
        }
    }
    *///?}
}
