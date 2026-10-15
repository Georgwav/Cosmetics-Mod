package io.github.georgwav.cosmetics.mixin;

import io.github.georgwav.cosmetics.client.modules.InventoryTweaks;
import io.github.georgwav.cosmetics.ui.Canvas;
import net.minecraft.client.gui.Font;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if >=26.1 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?} else {
import net.minecraft.client.gui.GuiGraphics;
//?}

/** Inventory's durability numbers, after the game's item decorations (renderItemDecorations; itemDecorations from 26.1). */
//? if >=26.1 {
/*@Mixin(GuiGraphicsExtractor.class)
*///?} else {
@Mixin(GuiGraphics.class)
//?}
abstract class ItemDecorationsMixin {
    //? if >=26.1 {
    /*@Inject(method = "itemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V",
            at = @At("TAIL"), require = 0)
    private void cosmeticsmod$uses(Font font, ItemStack stack, int x, int y, String count, CallbackInfo ci) {
        InventoryTweaks.decorate(new Canvas((GuiGraphicsExtractor) (Object) this), stack, x, y);
    }
    *///?} else {
    @Inject(method = "renderItemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V",
            at = @At("TAIL"), require = 0)
    private void cosmeticsmod$uses(Font font, ItemStack stack, int x, int y, String count, CallbackInfo ci) {
        InventoryTweaks.decorate(new Canvas((GuiGraphics) (Object) this), stack, x, y);
    }
    //?}
}
