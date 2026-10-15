package io.github.georgwav.cosmetics.ui;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.georgwav.cosmetics.CosmeticsMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
//? if >=1.21.11 {
import net.minecraft.resources.Identifier;
//?} else {
/*import net.minecraft.resources.ResourceLocation;
*///?}

/**
 * A small square texture the screens paint pictures into on the CPU (the Wardrobe tiles, the emote wheel), shown
 * with {@link Canvas#blit}. Holds the version-specific texture plumbing. Render thread only.
 */
public final class PictureTexture {
    //? if >=1.21.11 {
    public final Identifier id;
    //?} else {
    /*public final ResourceLocation id;
    *///?}
    public final int size;
    private final DynamicTexture texture;

    /** Registers {@code cosmeticsmod:<path>} as a {@code size} x {@code size} texture. */
    public PictureTexture(String path, int size) {
        this.size = size;
        //? if >=1.21.11 {
        id = Identifier.fromNamespaceAndPath(CosmeticsMod.MOD_ID, path);
        //?} else {
        /*id = ResourceLocation.fromNamespaceAndPath(CosmeticsMod.MOD_ID, path);
        *///?}
        //? if >=1.21.5 {
        texture = new DynamicTexture("cosmeticsmod picture", size, size, true);
        //?} else {
        /*texture = new DynamicTexture(size, size, true);
        *///?}
        Minecraft.getInstance().getTextureManager().register(id, texture);
    }

    /** Copies an ARGB picture (row by row from the top, {@code size} x {@code size}) in and uploads it. */
    public void upload(int[] argb) {
        NativeImage pixels = texture.getPixels();
        for (int py = 0; py < size; py++) {
            for (int px = 0; px < size; px++) {
                int c = argb[py * size + px];
                //? if >=1.21.2 {
                pixels.setPixel(px, py, c);
                //?} else {
                /*pixels.setPixelRGBA(px, py, (c & 0xFF00FF00) | ((c & 0xFF) << 16) | ((c >> 16) & 0xFF));
                *///?}
            }
        }
        texture.upload();
    }

    public void draw(Canvas g, int x, int y, int drawSize) {
        g.blit(id, x, y, drawSize, drawSize, size);
    }

    public void release() {
        Minecraft.getInstance().getTextureManager().release(id);
    }

    /** The pose stack's current pose as {@link io.github.georgwav.cosmetics.render.SoftRaster#draw} takes it. */
    static void load(PoseStack poseStack, float[] matrix, float[] normal) {
        PoseStack.Pose last = poseStack.last();
        var m = last.pose();
        var n = last.normal();
        matrix[0] = m.m00();
        matrix[1] = m.m01();
        matrix[2] = m.m02();
        matrix[3] = m.m10();
        matrix[4] = m.m11();
        matrix[5] = m.m12();
        matrix[6] = m.m20();
        matrix[7] = m.m21();
        matrix[8] = m.m22();
        matrix[9] = m.m30();
        matrix[10] = m.m31();
        matrix[11] = m.m32();
        normal[0] = n.m00();
        normal[1] = n.m01();
        normal[2] = n.m02();
        normal[3] = n.m10();
        normal[4] = n.m11();
        normal[5] = n.m12();
        normal[6] = n.m20();
        normal[7] = n.m21();
        normal[8] = n.m22();
    }
}
