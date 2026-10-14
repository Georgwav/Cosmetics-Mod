package io.github.georgwav.cosmetics.mixin;

// Before 1.21.6 only: from 1.21.6 Fabric's HUD elements replace the crosshair (see Hud).
//? if <1.21.6 {
/*import io.github.georgwav.cosmetics.client.Modules;
import io.github.georgwav.cosmetics.ui.Canvas;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// The client's crosshair in place of the game's while it's on.
@Mixin(Gui.class)
abstract class GuiMixin {
    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void cosmeticsmod$crosshair(GuiGraphics graphics, DeltaTracker delta, CallbackInfo ci) {
        if (Modules.CROSSHAIR.enabled()) {
            Modules.CROSSHAIR.drawOnHud(new Canvas(graphics), Minecraft.getInstance());
            ci.cancel();
        }
    }
}
*///?}
