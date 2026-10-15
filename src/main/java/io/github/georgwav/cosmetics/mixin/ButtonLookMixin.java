package io.github.georgwav.cosmetics.mixin;

import io.github.georgwav.cosmetics.sol.ui.screens.VanillaLook;
import io.github.georgwav.cosmetics.ui.Canvas;
import net.minecraft.client.gui.components.AbstractButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The game's buttons in Sol glass (Sol UI on): the glass in place of the button sprite; the label stays the game's. */
@Mixin(AbstractButton.class)
public abstract class ButtonLookMixin {
    //? if >=26.1 {
    /*@Inject(method = "extractDefaultSprite", at = @At("HEAD"), cancellable = true, require = 0)
    private void cosmeticsmod$glass(net.minecraft.client.gui.GuiGraphicsExtractor graphics, CallbackInfo ci) {
        if (glass(new Canvas(graphics))) {
            ci.cancel();
        }
    }
    *///?} elif >=1.21.11 {
    @Inject(method = "renderDefaultSprite", at = @At("HEAD"), cancellable = true, require = 0)
    private void cosmeticsmod$glass(net.minecraft.client.gui.GuiGraphics graphics, CallbackInfo ci) {
        if (glass(new Canvas(graphics))) {
            ci.cancel();
        }
    }
    //?} else {
    /*@Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true, require = 0)
    private void cosmeticsmod$glass(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (glass(new Canvas(graphics))) {
            AbstractButton self = (AbstractButton) (Object) this;
            self.renderString(graphics, net.minecraft.client.Minecraft.getInstance().font, self.active ? 0xFFFFFFFF : 0xFFA0A0A0);
            ci.cancel();
        }
    }
    *///?}

    private boolean glass(Canvas c) {
        if (!VanillaLook.on()) {
            return false;
        }
        AbstractButton self = (AbstractButton) (Object) this;
        return VanillaLook.button(c, self.getX(), self.getY(), self.getWidth(), self.getHeight(), self.isHoveredOrFocused(), self.active);
    }
}
