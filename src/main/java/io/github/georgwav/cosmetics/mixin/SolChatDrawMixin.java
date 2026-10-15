package io.github.georgwav.cosmetics.mixin;

// From 1.21.11 (before it, see SolChatLookMixin): the chat's dark line backgrounds, drawn by its graphics helpers,
// in the theme's panel colour while Sol UI draws the chat.
//? if >=1.21.11 {
import io.github.georgwav.cosmetics.sol.hud.SolHudCards;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(targets = {"net.minecraft.client.gui.components.ChatComponent$DrawingBackgroundGraphicsAccess",
        "net.minecraft.client.gui.components.ChatComponent$DrawingFocusedGraphicsAccess"})
abstract class SolChatDrawMixin {
    //? if >=26.1 {
    /*@ModifyArg(method = "fill(IIIII)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V"), index = 4, require = 0)
    *///?} else {
    @ModifyArg(method = "fill(IIIII)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V"), index = 4, require = 0)
    //?}
    private int cosmeticsmod$tint(int color) {
        return SolHudCards.chatTint(color);
    }
}
//?}
