package io.github.georgwav.cosmetics.mixin;

// Before 1.21.6 only (from 1.21.6 Fabric's HUD elements move it, see Hud): the boss bars drawn moved by where the HUD
// editor put them (VanillaHud).
//? if <1.21.6 {
/*import io.github.georgwav.cosmetics.client.hud.VanillaHud;
import io.github.georgwav.cosmetics.ui.Canvas;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.BossHealthOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BossHealthOverlay.class)
abstract class BossOverlayMixin {
    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;)V", at = @At("HEAD"), require = 0)
    private void cosmeticsmod$in(GuiGraphics graphics, CallbackInfo ci) {
        // Sol UI may leave the boss bars out (BossBarTweaksMixin cancels), and then this is never undone.
        if (io.github.georgwav.cosmetics.sol.hud.HudTweaks.hidesBossBars()) {
            return;
        }
        Canvas g = new Canvas(graphics);
        g.push();
        g.translate(VanillaHud.dx(VanillaHud.Part.BOSS_BAR), VanillaHud.dy(VanillaHud.Part.BOSS_BAR));
    }

    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;)V", at = @At("RETURN"), require = 0)
    private void cosmeticsmod$out(GuiGraphics graphics, CallbackInfo ci) {
        if (!io.github.georgwav.cosmetics.sol.hud.HudTweaks.hidesBossBars()) {
            new Canvas(graphics).pop();
        }
    }
}
*///?}
