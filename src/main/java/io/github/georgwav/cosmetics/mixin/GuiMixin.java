package io.github.georgwav.cosmetics.mixin;

// Before 1.21.6 only: from 1.21.6 Fabric's HUD elements replace the crosshair and move the game's HUD parts (see
// Hud). Here the crosshair is swapped the same way, and each HUD part's draw method is wrapped in a move by where the
// HUD editor put it (VanillaHud). Each is named with its full signature, so a method this version doesn't have (or
// has with other arguments) is skipped (require = 0) instead of failing.
//? if <1.21.6 {
/*import io.github.georgwav.cosmetics.client.Modules;
import io.github.georgwav.cosmetics.client.hud.VanillaHud;
import io.github.georgwav.cosmetics.ui.Canvas;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
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

    private static void enter(GuiGraphics graphics, VanillaHud.Part part) {
        Canvas g = new Canvas(graphics);
        g.push();
        g.translate(VanillaHud.dx(part), VanillaHud.dy(part));
    }

    private static void exit(GuiGraphics graphics) {
        new Canvas(graphics).pop();
    }

    @Inject(method = "renderItemHotbar(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V", at = @At("HEAD"), require = 0)
    private void cosmeticsmod$hotbarIn(GuiGraphics graphics, DeltaTracker delta, CallbackInfo ci) {
        enter(graphics, VanillaHud.Part.HOTBAR);
    }

    @Inject(method = "renderItemHotbar(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V", at = @At("RETURN"), require = 0)
    private void cosmeticsmod$hotbarOut(GuiGraphics graphics, DeltaTracker delta, CallbackInfo ci) {
        exit(graphics);
    }

    @Inject(method = "renderExperienceBar(Lnet/minecraft/client/gui/GuiGraphics;I)V", at = @At("HEAD"), require = 0)
    private void cosmeticsmod$xpIn(GuiGraphics graphics, int x, CallbackInfo ci) {
        enter(graphics, VanillaHud.Part.HOTBAR);
    }

    @Inject(method = "renderExperienceBar(Lnet/minecraft/client/gui/GuiGraphics;I)V", at = @At("RETURN"), require = 0)
    private void cosmeticsmod$xpOut(GuiGraphics graphics, int x, CallbackInfo ci) {
        exit(graphics);
    }

    @Inject(method = "renderExperienceLevel(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V", at = @At("HEAD"), require = 0)
    private void cosmeticsmod$levelIn(GuiGraphics graphics, DeltaTracker delta, CallbackInfo ci) {
        enter(graphics, VanillaHud.Part.HOTBAR);
    }

    @Inject(method = "renderExperienceLevel(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V", at = @At("RETURN"), require = 0)
    private void cosmeticsmod$levelOut(GuiGraphics graphics, DeltaTracker delta, CallbackInfo ci) {
        exit(graphics);
    }

    @Inject(method = "renderSelectedItemName(Lnet/minecraft/client/gui/GuiGraphics;)V", at = @At("HEAD"), require = 0)
    private void cosmeticsmod$itemNameIn(GuiGraphics graphics, CallbackInfo ci) {
        enter(graphics, VanillaHud.Part.HELD_ITEM);
    }

    @Inject(method = "renderSelectedItemName(Lnet/minecraft/client/gui/GuiGraphics;)V", at = @At("RETURN"), require = 0)
    private void cosmeticsmod$itemNameOut(GuiGraphics graphics, CallbackInfo ci) {
        exit(graphics);
    }

    @Inject(method = "renderOverlayMessage(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V", at = @At("HEAD"), require = 0)
    private void cosmeticsmod$actionBarIn(GuiGraphics graphics, DeltaTracker delta, CallbackInfo ci) {
        enter(graphics, VanillaHud.Part.ACTION_BAR);
    }

    @Inject(method = "renderOverlayMessage(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V", at = @At("RETURN"), require = 0)
    private void cosmeticsmod$actionBarOut(GuiGraphics graphics, DeltaTracker delta, CallbackInfo ci) {
        exit(graphics);
    }

    @Inject(method = "renderEffects(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V", at = @At("HEAD"), require = 0)
    private void cosmeticsmod$effectsIn(GuiGraphics graphics, DeltaTracker delta, CallbackInfo ci) {
        enter(graphics, VanillaHud.Part.EFFECTS);
    }

    @Inject(method = "renderEffects(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V", at = @At("RETURN"), require = 0)
    private void cosmeticsmod$effectsOut(GuiGraphics graphics, DeltaTracker delta, CallbackInfo ci) {
        exit(graphics);
    }

    @Inject(method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V", at = @At("HEAD"), require = 0)
    private void cosmeticsmod$scoreboardIn(GuiGraphics graphics, DeltaTracker delta, CallbackInfo ci) {
        enter(graphics, VanillaHud.Part.SCOREBOARD);
    }

    @Inject(method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V", at = @At("RETURN"), require = 0)
    private void cosmeticsmod$scoreboardOut(GuiGraphics graphics, DeltaTracker delta, CallbackInfo ci) {
        exit(graphics);
    }

    @Inject(method = "renderHearts(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/entity/player/Player;IIIIFIIIZ)V", at = @At("HEAD"), require = 0)
    private void cosmeticsmod$heartsIn(GuiGraphics graphics, Player player, int x, int y, int rowHeight, int regen,
                                       float maxHealth, int health, int displayHealth, int absorption, boolean blink,
                                       CallbackInfo ci) {
        enter(graphics, VanillaHud.Part.HEALTH);
    }

    @Inject(method = "renderHearts(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/entity/player/Player;IIIIFIIIZ)V", at = @At("RETURN"), require = 0)
    private void cosmeticsmod$heartsOut(GuiGraphics graphics, Player player, int x, int y, int rowHeight, int regen,
                                        float maxHealth, int health, int displayHealth, int absorption, boolean blink,
                                        CallbackInfo ci) {
        exit(graphics);
    }

    @Inject(method = "renderArmor(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/entity/player/Player;IIII)V", at = @At("HEAD"), require = 0)
    private static void cosmeticsmod$armorIn(GuiGraphics graphics, Player player, int y, int rows, int rowHeight, int x,
                                             CallbackInfo ci) {
        enter(graphics, VanillaHud.Part.ARMOR);
    }

    @Inject(method = "renderArmor(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/entity/player/Player;IIII)V", at = @At("RETURN"), require = 0)
    private static void cosmeticsmod$armorOut(GuiGraphics graphics, Player player, int y, int rows, int rowHeight, int x,
                                              CallbackInfo ci) {
        exit(graphics);
    }

    @Inject(method = "renderFood(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/entity/player/Player;II)V", at = @At("HEAD"), require = 0)
    private void cosmeticsmod$foodIn(GuiGraphics graphics, Player player, int y, int x, CallbackInfo ci) {
        enter(graphics, VanillaHud.Part.FOOD);
    }

    @Inject(method = "renderFood(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/entity/player/Player;II)V", at = @At("RETURN"), require = 0)
    private void cosmeticsmod$foodOut(GuiGraphics graphics, Player player, int y, int x, CallbackInfo ci) {
        exit(graphics);
    }

    @Inject(method = "renderVehicleHealth(Lnet/minecraft/client/gui/GuiGraphics;)V", at = @At("HEAD"), require = 0)
    private void cosmeticsmod$mountIn(GuiGraphics graphics, CallbackInfo ci) {
        enter(graphics, VanillaHud.Part.FOOD);
    }

    @Inject(method = "renderVehicleHealth(Lnet/minecraft/client/gui/GuiGraphics;)V", at = @At("RETURN"), require = 0)
    private void cosmeticsmod$mountOut(GuiGraphics graphics, CallbackInfo ci) {
        exit(graphics);
    }

    @Inject(method = "renderAirBubbles(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/entity/player/Player;III)V", at = @At("HEAD"), require = 0)
    private void cosmeticsmod$airIn(GuiGraphics graphics, Player player, int vehicleHearts, int y, int x, CallbackInfo ci) {
        enter(graphics, VanillaHud.Part.AIR);
    }

    @Inject(method = "renderAirBubbles(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/entity/player/Player;III)V", at = @At("RETURN"), require = 0)
    private void cosmeticsmod$airOut(GuiGraphics graphics, Player player, int vehicleHearts, int y, int x, CallbackInfo ci) {
        exit(graphics);
    }
}
*///?}
