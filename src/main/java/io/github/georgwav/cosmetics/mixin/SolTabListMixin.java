package io.github.georgwav.cosmetics.mixin;

import io.github.georgwav.cosmetics.sol.hud.SolHud;
import io.github.georgwav.cosmetics.sol.hud.SolHudCards;
import io.github.georgwav.cosmetics.ui.Canvas;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
//? if >=26.1 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?} else {
import net.minecraft.client.gui.GuiGraphics;
//?}

/**
 * Sol UI's tab list: the game lays it out and writes it; its dark boxes become Sol glass (the header, the players
 * and the footer: the game's Integer.MIN_VALUE fills) and its rows soft chips (every other fill).
 */
@Mixin(PlayerTabOverlay.class)
abstract class SolTabListMixin {
    //? if >=26.1 {
    /*@Redirect(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;ILnet/minecraft/world/scores/Scoreboard;Lnet/minecraft/world/scores/Objective;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V"), require = 0)
    private void cosmeticsmod$fill(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2, int color) {
        if (!SolHud.draws(SolHud.Part.TAB)) {
            graphics.fill(x1, y1, x2, y2, color);
            return;
        }
        fill(new Canvas(graphics), x1, y1, x2, y2, color);
    }
    *///?} else {
    @Redirect(method = "render(Lnet/minecraft/client/gui/GuiGraphics;ILnet/minecraft/world/scores/Scoreboard;Lnet/minecraft/world/scores/Objective;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V"), require = 0)
    private void cosmeticsmod$fill(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color) {
        if (!SolHud.draws(SolHud.Part.TAB)) {
            graphics.fill(x1, y1, x2, y2, color);
            return;
        }
        fill(new Canvas(graphics), x1, y1, x2, y2, color);
    }
    //?}

    private static void fill(Canvas c, int x1, int y1, int x2, int y2, int color) {
        if (color == Integer.MIN_VALUE) {
            SolHudCards.tabPanel(c, x1, y1, x2, y2);
        } else {
            SolHudCards.tabRow(c, x1, y1, x2, y2);
        }
    }
}
