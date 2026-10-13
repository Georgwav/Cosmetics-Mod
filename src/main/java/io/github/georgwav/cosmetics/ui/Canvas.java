package io.github.georgwav.cosmetics.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.LivingEntity;
//? if >=26.1 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?} else {
import net.minecraft.client.gui.GuiGraphics;
//?}

/**
 * The few drawing calls the settings screen uses. 26.1 renamed {@code GuiGraphics} and its methods, so
 * every version difference in drawing is kept in this one class and the screen and widgets stay the same.
 */
public final class Canvas {
    //? if >=26.1 {
    /*public final GuiGraphicsExtractor raw;

    public Canvas(GuiGraphicsExtractor raw) {
        this.raw = raw;
    }
    *///?} else {
    public final GuiGraphics raw;

    public Canvas(GuiGraphics raw) {
        this.raw = raw;
    }
    //?}

    /** Solid rectangle from (x1, y1) inclusive to (x2, y2) exclusive; colour is ARGB. */
    public void fill(int x1, int y1, int x2, int y2, int color) {
        //? if >=26.1 {
        /*raw.fill(x1, y1, x2, y2, color);
        *///?} else {
        raw.fill(x1, y1, x2, y2, color);
        //?}
    }

    /** Text without shadow; colour is ARGB. */
    public void text(Font font, String text, int x, int y, int color) {
        //? if >=26.1 {
        /*raw.text(font, text, x, y, color, false);
        *///?} else {
        raw.drawString(font, text, x, y, color, false);
        //?}
    }

    public void centeredText(Font font, String text, int centerX, int y, int color) {
        text(font, text, centerX - font.width(text) / 2, y, color);
    }

    /** Clips drawing to a rectangle until {@link #endClip()}. */
    public void clip(int x1, int y1, int x2, int y2) {
        raw.enableScissor(x1, y1, x2, y2);
    }

    public void endClip() {
        raw.disableScissor();
    }

    /** The inventory-style entity preview that turns to follow the mouse. */
    public void entityFollowingMouse(int x1, int y1, int x2, int y2, int scale, float yOffset,
                                     float mouseX, float mouseY, LivingEntity entity) {
        //? if >=26.1 {
        /*InventoryScreen.extractEntityInInventoryFollowsMouse(raw, x1, y1, x2, y2, scale, yOffset, mouseX, mouseY, entity);
        *///?} else {
        InventoryScreen.renderEntityInInventoryFollowsMouse(raw, x1, y1, x2, y2, scale, yOffset, mouseX, mouseY, entity);
        //?}
    }
}
