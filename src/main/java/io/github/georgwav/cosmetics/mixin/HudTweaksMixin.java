package io.github.georgwav.cosmetics.mixin;

import io.github.georgwav.cosmetics.sol.hud.HudTweaks;
import io.github.georgwav.cosmetics.ui.Canvas;
import net.minecraft.client.DeltaTracker;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.world.scores.Objective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if >=26.1 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?} else {
import net.minecraft.client.gui.GuiGraphics;
//?}

/**
 * Sol UI's scoreboard and title tweaks (HudTweaks): each hidden, or drawn at its size (the scoreboard from the
 * screen's right edge, titles from its middle); the scoreboard's red numbers left out with a blank number format.
 */
//? if >=26.2 {
/*@Mixin(net.minecraft.client.gui.Hud.class)
*///?} else {
@Mixin(net.minecraft.client.gui.Gui.class)
//?}
abstract class HudTweaksMixin {
    //? if >=26.1 {
    /*private static final String TITLE = "extractTitle";
    *///?} else {
    private static final String TITLE = "renderTitle";
    //?}

    @Unique
    private boolean cosmeticsmod$sidebarScaled;
    @Unique
    private boolean cosmeticsmod$titleScaled;

    @Redirect(method = "displayScoreboardSidebar", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/scores/Objective;numberFormatOrDefault(Lnet/minecraft/network/chat/numbers/NumberFormat;)Lnet/minecraft/network/chat/numbers/NumberFormat;"),
            require = 0)
    private NumberFormat cosmeticsmod$numbers(Objective objective, NumberFormat fallback) {
        return HudTweaks.hidesScoreNumbers() ? net.minecraft.network.chat.numbers.BlankFormat.INSTANCE : objective.numberFormatOrDefault(fallback);
    }

    //? if >=26.1 {
    /*@Inject(method = "displayScoreboardSidebar", at = @At("HEAD"), cancellable = true, require = 0)
    private void cosmeticsmod$sidebarIn(GuiGraphicsExtractor graphics, Objective objective, CallbackInfo ci) {
        if (HudTweaks.hidesScoreboard()) {
            ci.cancel();
            return;
        }
        cosmeticsmod$sidebarScaled = HudTweaks.scoreboardIn(new Canvas(graphics), graphics.guiWidth(), graphics.guiHeight());
    }

    @Inject(method = "displayScoreboardSidebar", at = @At("RETURN"), require = 0)
    private void cosmeticsmod$sidebarOut(GuiGraphicsExtractor graphics, Objective objective, CallbackInfo ci) {
        if (cosmeticsmod$sidebarScaled) {
            cosmeticsmod$sidebarScaled = false;
            new Canvas(graphics).pop();
        }
    }

    @Inject(method = TITLE, at = @At("HEAD"), cancellable = true, require = 0)
    private void cosmeticsmod$titleIn(GuiGraphicsExtractor graphics, DeltaTracker delta, CallbackInfo ci) {
        if (HudTweaks.hidesTitles()) {
            ci.cancel();
            return;
        }
        cosmeticsmod$titleScaled = HudTweaks.titleIn(new Canvas(graphics), graphics.guiWidth(), graphics.guiHeight());
    }

    @Inject(method = TITLE, at = @At("RETURN"), require = 0)
    private void cosmeticsmod$titleOut(GuiGraphicsExtractor graphics, DeltaTracker delta, CallbackInfo ci) {
        if (cosmeticsmod$titleScaled) {
            cosmeticsmod$titleScaled = false;
            new Canvas(graphics).pop();
        }
    }
    *///?} else {
    @Inject(method = "displayScoreboardSidebar", at = @At("HEAD"), cancellable = true, require = 0)
    private void cosmeticsmod$sidebarIn(GuiGraphics graphics, Objective objective, CallbackInfo ci) {
        if (HudTweaks.hidesScoreboard()) {
            ci.cancel();
            return;
        }
        cosmeticsmod$sidebarScaled = HudTweaks.scoreboardIn(new Canvas(graphics), graphics.guiWidth(), graphics.guiHeight());
    }

    @Inject(method = "displayScoreboardSidebar", at = @At("RETURN"), require = 0)
    private void cosmeticsmod$sidebarOut(GuiGraphics graphics, Objective objective, CallbackInfo ci) {
        if (cosmeticsmod$sidebarScaled) {
            cosmeticsmod$sidebarScaled = false;
            new Canvas(graphics).pop();
        }
    }

    @Inject(method = TITLE, at = @At("HEAD"), cancellable = true, require = 0)
    private void cosmeticsmod$titleIn(GuiGraphics graphics, DeltaTracker delta, CallbackInfo ci) {
        if (HudTweaks.hidesTitles()) {
            ci.cancel();
            return;
        }
        cosmeticsmod$titleScaled = HudTweaks.titleIn(new Canvas(graphics), graphics.guiWidth(), graphics.guiHeight());
    }

    @Inject(method = TITLE, at = @At("RETURN"), require = 0)
    private void cosmeticsmod$titleOut(GuiGraphics graphics, DeltaTracker delta, CallbackInfo ci) {
        if (cosmeticsmod$titleScaled) {
            cosmeticsmod$titleScaled = false;
            new Canvas(graphics).pop();
        }
    }
    //?}
}
