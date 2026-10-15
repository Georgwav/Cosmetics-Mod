package io.github.georgwav.cosmetics.mixin;

import io.github.georgwav.cosmetics.sol.hud.SolHud;
import io.github.georgwav.cosmetics.sol.hud.SolHudCards;
import io.github.georgwav.cosmetics.ui.Canvas;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
//? if >=26.1 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?} else {
import net.minecraft.client.gui.GuiGraphics;
//?}

/**
 * Sol UI's scoreboard sidebar and action bar: the game lays them out and writes them; Sol draws a glass card under
 * the sidebar (from its two dark boxes) and a glass chip under the action bar, in place of its backdrop (which the
 * game draws only when the text background isn't kept for chat; Sol's chip is always there).
 */
//? if >=26.2 {
/*@Mixin(net.minecraft.client.gui.Hud.class)
*///?} else {
@Mixin(net.minecraft.client.gui.Gui.class)
//?}
abstract class SolHudLookMixin {
    // 1.21.1 draws the sidebar inside a lambda of displayScoreboardSidebar.
    //? if <1.21.2 {
    /*private static final String SIDEBAR = "method_55440";
    *///?} else {
    private static final String SIDEBAR = "displayScoreboardSidebar";
    //?}

    @Unique
    private int cosmeticsmod$titleTop;
    @Unique
    private int cosmeticsmod$titleBottom;

    //? if >=26.1 {
    /*@Redirect(method = SIDEBAR, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V", ordinal = 0), require = 0)
    private void cosmeticsmod$sidebarTitle(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2, int color) {
        if (SolHud.draws(SolHud.Part.SIDEBAR)) {
            cosmeticsmod$titleTop = y1;
            cosmeticsmod$titleBottom = y2;
        } else {
            graphics.fill(x1, y1, x2, y2, color);
        }
    }

    @Redirect(method = SIDEBAR, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V", ordinal = 1), require = 0)
    private void cosmeticsmod$sidebarBody(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2, int color) {
        if (SolHud.draws(SolHud.Part.SIDEBAR)) {
            SolHudCards.sidebar(new Canvas(graphics), x1, cosmeticsmod$titleTop, x2, y2, cosmeticsmod$titleBottom);
        } else {
            graphics.fill(x1, y1, x2, y2, color);
        }
    }

    @Redirect(method = "extractOverlayMessage", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;textWithBackdrop(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIII)V"),
            require = 0)
    private void cosmeticsmod$actionBar(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int width, int color) {
        if (!SolHud.draws(SolHud.Part.ACTION_BAR)) {
            graphics.textWithBackdrop(font, text, x, y, width, color);
            return;
        }
        SolHudCards.chip(new Canvas(graphics), x, y, width, color);
        graphics.text(font, text, x, y, color);
    }
    *///?} else {
    @Redirect(method = SIDEBAR, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V", ordinal = 0), require = 0)
    private void cosmeticsmod$sidebarTitle(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color) {
        if (SolHud.draws(SolHud.Part.SIDEBAR)) {
            cosmeticsmod$titleTop = y1;
            cosmeticsmod$titleBottom = y2;
        } else {
            graphics.fill(x1, y1, x2, y2, color);
        }
    }

    @Redirect(method = SIDEBAR, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V", ordinal = 1), require = 0)
    private void cosmeticsmod$sidebarBody(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color) {
        if (SolHud.draws(SolHud.Part.SIDEBAR)) {
            SolHudCards.sidebar(new Canvas(graphics), x1, cosmeticsmod$titleTop, x2, y2, cosmeticsmod$titleBottom);
        } else {
            graphics.fill(x1, y1, x2, y2, color);
        }
    }
    //?}

    //? if >=1.21.6 && <26.1 {
    @Redirect(method = "renderOverlayMessage", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;drawStringWithBackdrop(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIII)V"),
            require = 0)
    private void cosmeticsmod$actionBar(GuiGraphics graphics, Font font, Component text, int x, int y, int width, int color) {
        if (!SolHud.draws(SolHud.Part.ACTION_BAR)) {
            graphics.drawStringWithBackdrop(font, text, x, y, width, color);
            return;
        }
        SolHudCards.chip(new Canvas(graphics), x, y, width, color);
        graphics.drawString(font, text, x, y, color);
    }
    //?} elif <1.21.6 {
    /*@Redirect(method = "renderOverlayMessage", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;drawStringWithBackdrop(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIII)I"),
            require = 0)
    private int cosmeticsmod$actionBar(GuiGraphics graphics, Font font, Component text, int x, int y, int width, int color) {
        if (!SolHud.draws(SolHud.Part.ACTION_BAR)) {
            return graphics.drawStringWithBackdrop(font, text, x, y, width, color);
        }
        SolHudCards.chip(new Canvas(graphics), x, y, width, color);
        return graphics.drawString(font, text, x, y, color);
    }
    *///?}
}
