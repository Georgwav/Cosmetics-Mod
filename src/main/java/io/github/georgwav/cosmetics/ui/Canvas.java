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
//? if >=1.21.11 {
import net.minecraft.resources.Identifier;
//?} else {
/*import net.minecraft.resources.ResourceLocation;
*///?}
//? if >=1.21.6 {
//?} elif >=1.21.2 {
/*import net.minecraft.client.renderer.RenderType;
*///?} else {
/*import com.mojang.blaze3d.systems.RenderSystem;
*///?}

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

    /** Rectangle fading from {@code top} to {@code bottom} (ARGB). */
    public void gradient(int x1, int y1, int x2, int y2, int top, int bottom) {
        raw.fillGradient(x1, y1, x2, y2, top, bottom);
    }

    /** A whole square texture of {@code textureSize} pixels drawn into a width x height rectangle, see-through where it is. */
    //? if >=1.21.11 {
    public void blit(Identifier texture, int x, int y, int width, int height, int textureSize) {
        raw.blit(texture, x, y, x + width, y + height, 0f, 1f, 0f, 1f);
    }
    //?} elif >=1.21.6 {
    /*public void blit(ResourceLocation texture, int x, int y, int width, int height, int textureSize) {
        raw.blit(texture, x, y, x + width, y + height, 0f, 1f, 0f, 1f);
    }
    *///?} elif >=1.21.2 {
    /*public void blit(ResourceLocation texture, int x, int y, int width, int height, int textureSize) {
        raw.blit(RenderType::guiTextured, texture, x, y, 0f, 0f, width, height, textureSize, textureSize,
                textureSize, textureSize);
    }
    *///?} else {
    /*public void blit(ResourceLocation texture, int x, int y, int width, int height, int textureSize) {
        RenderSystem.enableBlend();
        raw.blit(texture, x, y, width, height, 0f, 0f, textureSize, textureSize, textureSize, textureSize);
        RenderSystem.disableBlend();
    }
    *///?}

    /**
     * A {@code regionWidth} x {@code regionHeight} part of a texture, from ({@code u}, {@code v}) in a
     * {@code textureWidth} x {@code textureHeight} texture, drawn into a width x height rectangle (a skin's face).
     */
    //? if >=1.21.11 {
    public void blitRegion(Identifier texture, int x, int y, int width, int height, float u, float v,
                           int regionWidth, int regionHeight, int textureWidth, int textureHeight) {
        raw.blit(texture, x, y, x + width, y + height, u / textureWidth, (u + regionWidth) / textureWidth,
                v / textureHeight, (v + regionHeight) / textureHeight);
    }
    //?} elif >=1.21.6 {
    /*public void blitRegion(ResourceLocation texture, int x, int y, int width, int height, float u, float v,
                           int regionWidth, int regionHeight, int textureWidth, int textureHeight) {
        raw.blit(texture, x, y, x + width, y + height, u / textureWidth, (u + regionWidth) / textureWidth,
                v / textureHeight, (v + regionHeight) / textureHeight);
    }
    *///?} elif >=1.21.2 {
    /*public void blitRegion(ResourceLocation texture, int x, int y, int width, int height, float u, float v,
                           int regionWidth, int regionHeight, int textureWidth, int textureHeight) {
        raw.blit(RenderType::guiTextured, texture, x, y, u, v, width, height, regionWidth, regionHeight,
                textureWidth, textureHeight);
    }
    *///?} else {
    /*public void blitRegion(ResourceLocation texture, int x, int y, int width, int height, float u, float v,
                           int regionWidth, int regionHeight, int textureWidth, int textureHeight) {
        RenderSystem.enableBlend();
        raw.blit(texture, x, y, width, height, u, v, regionWidth, regionHeight, textureWidth, textureHeight);
        RenderSystem.disableBlend();
    }
    *///?}

    // The hub draws in screen pixels and scales its text: the transform is saved, scaled and moved here.
    // 1.21.6 made the GUI transform two-dimensional.

    /** Saves the current transform until {@link #pop()}. */
    public void push() {
        //? if >=1.21.6 {
        raw.pose().pushMatrix();
        //?} else {
        /*raw.pose().pushPose();
        *///?}
    }

    public void pop() {
        //? if >=1.21.6 {
        raw.pose().popMatrix();
        //?} else {
        /*raw.pose().popPose();
        *///?}
    }

    public void translate(float x, float y) {
        //? if >=1.21.6 {
        raw.pose().translate(x, y);
        //?} else {
        /*raw.pose().translate(x, y, 0f);
        *///?}
    }

    public void scale(float x, float y) {
        //? if >=1.21.6 {
        raw.pose().scale(x, y);
        //?} else {
        /*raw.pose().scale(x, y, 1f);
        *///?}
    }

    /**
     * What is drawn after this goes over what was drawn before, text included. From 1.21.6 the game draws text
     * after the shapes it was drawn among, so without this a later shape can't cover earlier text.
     */
    public void nextLayer() {
        //? if >=1.21.6 {
        raw.nextStratum();
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
     * Clips to a rectangle in screen pixels while the transform is scaled to them (the hub). From 1.21.2 the clip
     * goes through the transform; before, it is in GUI units.
     */
    public void clipScreen(float x0, float y0, float x1, float y1, float guiScale) {
        //? if >=1.21.2 {
        raw.enableScissor((int) Math.floor(x0), (int) Math.floor(y0), (int) Math.ceil(x1), (int) Math.ceil(y1));
        //?} else {
        /*raw.enableScissor((int) Math.floor(x0 / guiScale), (int) Math.floor(y0 / guiScale),
                (int) Math.ceil(x1 / guiScale), (int) Math.ceil(y1 / guiScale));
        *///?}
    }

    /**
     * The inventory-style entity preview, turned {@code spin} degrees round its vertical axis and tipped
     * {@code pitch} degrees towards or away from you (both by dragging). The body stays as turned; only the head
     * looks at the mouse, while the face is towards you. Built like vanilla's follow-mouse preview on each version
     * (InventoryScreen.renderEntityInInventoryFollowsMouse).
     */
    public void entityTurned(int x1, int y1, int x2, int y2, int scale, float yOffset, float mouseX, float mouseY,
                             LivingEntity entity, float spin, float pitch) {
        float centerX = (x1 + x2) / 2f;
        float centerY = (y1 + y2) / 2f;
        float turn = (float) Math.atan((centerX - mouseX) / 40f);
        float tilt = (float) Math.atan((centerY - mouseY) / 40f);
        // Looking at the mouse fades out as the face turns away from you.
        float facing = Math.max(0f, (float) Math.cos(Math.toRadians(spin)));
        float headYaw = turn * 40f * facing;
        float headPitch = -tilt * 20f * facing;
        Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI);
        Quaternionf camera = new Quaternionf().rotateX((float) Math.toRadians(pitch));
        pose.mul(camera);
        //? if >=26.1 {
        /*EntityRenderState state = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity)
                .createRenderState(entity, 1f);
        state.shadowPieces.clear();
        state.outlineColor = 0;
        if (state instanceof LivingEntityRenderState living) {
            living.bodyRot = 180f + spin;
            living.yRot = headYaw;
            living.xRot = living.pose != Pose.FALL_FLYING ? headPitch : 0f;
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
            living.bodyRot = 180f + spin;
            living.yRot = headYaw;
            living.xRot = living.pose != Pose.FALL_FLYING ? headPitch : 0f;
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
        entity.yBodyRot = 180f + spin;
        entity.setYRot(180f + spin + headYaw);
        entity.setXRot(headPitch);
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
        entity.yBodyRot = 180f + spin;
        entity.setYRot(180f + spin + headYaw);
        entity.setXRot(headPitch);
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
