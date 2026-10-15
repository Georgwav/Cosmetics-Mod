package io.github.georgwav.cosmetics.mixin;

import io.github.georgwav.cosmetics.sol.ui.screens.VanillaLook;
import io.github.georgwav.cosmetics.ui.Canvas;
import net.minecraft.client.gui.components.AbstractSelectionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The game's lists (servers, worlds, packs, languages) with Sol's selection: the chosen row in a soft glass of the
 * accent instead of the white box (Sol UI on). The game draws the box as two fills, the border then the inside.
 */
@Mixin(AbstractSelectionList.class)
public abstract class ListLookMixin {
    //? if >=26.1 {
    /*@Redirect(method = "extractSelection", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V", ordinal = 0), require = 0)
    private void cosmeticsmod$border(net.minecraft.client.gui.GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int color) {
        if (!VanillaLook.on() || !VanillaLook.selection(new Canvas(g), x0, y0, x1 - x0, y1 - y0)) {
            g.fill(x0, y0, x1, y1, color);
        }
    }

    @Redirect(method = "extractSelection", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V", ordinal = 1), require = 0)
    private void cosmeticsmod$inside(net.minecraft.client.gui.GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int color) {
        if (!VanillaLook.on()) {
            g.fill(x0, y0, x1, y1, color);
        }
    }
    *///?} else {
    @Redirect(method = "renderSelection", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V", ordinal = 0), require = 0)
    private void cosmeticsmod$border(net.minecraft.client.gui.GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        if (!VanillaLook.on() || !VanillaLook.selection(new Canvas(g), x0, y0, x1 - x0, y1 - y0)) {
            g.fill(x0, y0, x1, y1, color);
        }
    }

    @Redirect(method = "renderSelection", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V", ordinal = 1), require = 0)
    private void cosmeticsmod$inside(net.minecraft.client.gui.GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        if (!VanillaLook.on()) {
            g.fill(x0, y0, x1, y1, color);
        }
    }
    //?}
}
