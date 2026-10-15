package io.github.georgwav.cosmetics.mixin;

// Before 1.21.11 only (from 1.21.11 the chat draws through its graphics helpers, see SolChatDrawMixin): the chat's
// dark line backgrounds in the theme's panel colour while Sol UI draws the chat.
//? if <1.21.11 {
/*import io.github.georgwav.cosmetics.sol.hud.SolHudCards;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(ChatComponent.class)
abstract class SolChatLookMixin {
    //? if >=1.21.6 {
    @ModifyArg(method = {"render(Lnet/minecraft/client/gui/GuiGraphics;IIIZ)V", "method_71992"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V"), index = 4, require = 0)
    //?} else {
    /^@ModifyArg(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIIZ)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V"), index = 4, require = 0)
    ^///?}
    private int cosmeticsmod$tint(int color) {
        return SolHudCards.chatTint(color);
    }
}
*///?}
