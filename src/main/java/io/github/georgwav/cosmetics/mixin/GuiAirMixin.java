package io.github.georgwav.cosmetics.mixin;

// 1.21.1 only: the game draws the air bubbles inside renderPlayerHealth itself (later versions have renderAirBubbles,
// see GuiMixin). Where Sol UI draws the air, the game's bubbles (the only sprites that method draws itself) are
// skipped and Sol's are drawn after it, moved by where the HUD editor put them.
//? if <1.21.2 {
/*import io.github.georgwav.cosmetics.client.hud.VanillaHud;
import io.github.georgwav.cosmetics.sol.hud.SolHud;
import io.github.georgwav.cosmetics.ui.Canvas;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
abstract class GuiAirMixin {
    @Redirect(method = "renderPlayerHealth(Lnet/minecraft/client/gui/GuiGraphics;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lnet/minecraft/resources/ResourceLocation;IIII)V"),
            require = 0)
    private void cosmeticsmod$bubble(GuiGraphics graphics, ResourceLocation sprite, int x, int y, int w, int h) {
        if (!SolHud.draws(SolHud.Part.AIR)) {
            graphics.blitSprite(sprite, x, y, w, h);
        }
    }

    @Inject(method = "renderPlayerHealth(Lnet/minecraft/client/gui/GuiGraphics;)V", at = @At("TAIL"), require = 0)
    private void cosmeticsmod$solAir(GuiGraphics graphics, CallbackInfo ci) {
        if (SolHud.draws(SolHud.Part.AIR)) {
            Canvas g = new Canvas(graphics);
            g.push();
            g.translate(VanillaHud.dx(VanillaHud.Part.AIR), VanillaHud.dy(VanillaHud.Part.AIR));
            SolHud.draw(SolHud.Part.AIR, g, 1f);
            g.pop();
        }
    }
}
*///?}
