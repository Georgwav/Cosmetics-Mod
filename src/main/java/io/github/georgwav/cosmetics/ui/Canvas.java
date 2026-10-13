package io.github.georgwav.cosmetics.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import org.joml.Quaternionf;
import org.joml.Vector3f;
//? if >=1.21.9 {
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
//?}
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

    /**
     * The inventory-style entity preview that turns to follow the mouse, turned a further {@code spin} degrees
     * around its vertical axis so its back can be shown too. Does exactly what vanilla's follow-mouse preview
     * does on each version (InventoryScreen.renderEntityInInventoryFollowsMouse), with the turn added to the body.
     */
    public void entityTurned(int x1, int y1, int x2, int y2, int scale, float yOffset, float mouseX, float mouseY,
                             LivingEntity entity, float spin) {
        float centerX = (x1 + x2) / 2f;
        float centerY = (y1 + y2) / 2f;
        float turn = (float) Math.atan((centerX - mouseX) / 40f);
        float tilt = (float) Math.atan((centerY - mouseY) / 40f);
        Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI);
        Quaternionf camera = new Quaternionf().rotateX(tilt * 20f * (float) (Math.PI / 180));
        pose.mul(camera);
        //? if >=26.1 {
        /*EntityRenderState state = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity)
                .createRenderState(entity, 1f);
        state.shadowPieces.clear();
        state.outlineColor = 0;
        if (state instanceof LivingEntityRenderState living) {
            living.bodyRot = 180f + turn * 20f + spin;
            living.yRot = turn * 20f;
            living.xRot = living.pose != Pose.FALL_FLYING ? -tilt * 20f : 0f;
            living.boundingBoxWidth /= living.scale;
            living.boundingBoxHeight /= living.scale;
            living.scale = 1f;
        }
        Vector3f translation = new Vector3f(0f, state.boundingBoxHeight / 2f + yOffset, 0f);
        raw.entity(state, scale, translation, pose, camera, x1, y1, x2, y2);
        *///?} elif >=1.21.9 {
        EntityRenderState state = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity)
                .createRenderState(entity, 1f);
        state.lightCoords = 15728880; // full bright, as vanilla's preview
        state.shadowPieces.clear();
        state.outlineColor = 0;
        if (state instanceof LivingEntityRenderState living) {
            living.bodyRot = 180f + turn * 20f + spin;
            living.yRot = turn * 20f;
            living.xRot = living.pose != Pose.FALL_FLYING ? -tilt * 20f : 0f;
            living.boundingBoxWidth /= living.scale;
            living.boundingBoxHeight /= living.scale;
            living.scale = 1f;
        }
        Vector3f translation = new Vector3f(0f, state.boundingBoxHeight / 2f + yOffset, 0f);
        raw.submitEntityRenderState(state, scale, translation, pose, camera, x1, y1, x2, y2);
        //?} elif >=1.21.6 {
        /*float bodyRot = entity.yBodyRot;
        float yRot = entity.getYRot();
        float xRot = entity.getXRot();
        float headRotO = entity.yHeadRotO;
        float headRot = entity.yHeadRot;
        entity.yBodyRot = 180f + turn * 20f + spin;
        entity.setYRot(180f + turn * 40f + spin);
        entity.setXRot(-tilt * 20f);
        entity.yHeadRot = entity.getYRot();
        entity.yHeadRotO = entity.getYRot();
        float entityScale = entity.getScale();
        Vector3f translation = new Vector3f(0f, entity.getBbHeight() / 2f + yOffset * entityScale, 0f);
        raw.enableScissor(x1, y1, x2, y2);
        InventoryScreen.renderEntityInInventory(raw, x1, y1, x2, y2, scale / entityScale, translation, pose, camera, entity);
        raw.disableScissor();
        entity.yBodyRot = bodyRot;
        entity.setYRot(yRot);
        entity.setXRot(xRot);
        entity.yHeadRotO = headRotO;
        entity.yHeadRot = headRot;
        *///?} else {
        /*float bodyRot = entity.yBodyRot;
        float yRot = entity.getYRot();
        float xRot = entity.getXRot();
        float headRotO = entity.yHeadRotO;
        float headRot = entity.yHeadRot;
        entity.yBodyRot = 180f + turn * 20f + spin;
        entity.setYRot(180f + turn * 40f + spin);
        entity.setXRot(-tilt * 20f);
        entity.yHeadRot = entity.getYRot();
        entity.yHeadRotO = entity.getYRot();
        float entityScale = entity.getScale();
        Vector3f translation = new Vector3f(0f, entity.getBbHeight() / 2f + yOffset * entityScale, 0f);
        raw.enableScissor(x1, y1, x2, y2);
        InventoryScreen.renderEntityInInventory(raw, centerX, centerY, scale / entityScale, translation, pose, camera, entity);
        raw.disableScissor();
        entity.yBodyRot = bodyRot;
        entity.setYRot(yRot);
        entity.setXRot(xRot);
        entity.yHeadRotO = headRotO;
        entity.yHeadRot = headRot;
        *///?}
    }
}
