package io.github.georgwav.cosmetics.sol.render;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.georgwav.cosmetics.CosmeticsMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
//? if >=1.21.11 {
import net.minecraft.resources.Identifier;
//?} else {
/*import net.minecraft.resources.ResourceLocation;
*///?}
//? if >=26.3 {
/*import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
*///?} elif >=1.21.11 {
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
//?}

/**
 * A texture Sol paints on the CPU (glows, rays, the plasma sun, star faces, sprites) and draws in the world and the
 * GUI. Nearest filtering for pixel art, linear for glows. Render thread only.
 */
public final class SolTexture {
    //? if >=1.21.11 {
    public final Identifier id;
    //?} else {
    /*public final ResourceLocation id;
    *///?}
    public final int width;
    public final int height;
    public final boolean linear;
    private final DynamicTexture texture;
    private boolean released;
    /** Whether it is drawn smooth right now: its own filter, or pixel-sharp under the Pixel light style. */
    private boolean drawnLinear;
    /**
     * Render types made for this texture, by {@link WorldDraw} kind, smooth and crisp (versions that need one per
     * texture), and the GUI's added one last.
     */
    final Object[] renderTypes = new Object[WorldDraw.KINDS * 2 + 1];

    /** Registers {@code cosmeticsmod:sol/<path>} as an empty {@code width} x {@code height} texture. */
    public SolTexture(String path, int width, int height, boolean linear) {
        this(CosmeticsMod.MOD_ID, "sol/" + path, width, height, linear);
    }

    /**
     * Registers an empty texture under exactly {@code namespace:path} (the light pass's textures sit where the
     * game's post-effect chains look for theirs, {@code textures/effect/<name>.png}).
     */
    public SolTexture(String namespace, String path, int width, int height, boolean linear) {
        this.width = width;
        this.height = height;
        this.linear = linear;
        this.drawnLinear = linear;
        //? if >=1.21.11 {
        id = Identifier.fromNamespaceAndPath(namespace, path);
        //?} else {
        /*id = ResourceLocation.fromNamespaceAndPath(namespace, path);
        *///?}
        //? if >=1.21.5 {
        texture = new DynamicTexture("cosmeticsmod sol " + path, width, height, true);
        //?} else {
        /*texture = new DynamicTexture(width, height, true);
        *///?}
        //? if <1.21.11 {
        /*texture.setFilter(linear, false);
        *///?}
        Minecraft.getInstance().getTextureManager().register(id, texture);
    }

    /** Whether the pixels are stored premultiplied (GUI glows drawn added, see GuiDraw). */
    public boolean premultiplied;

    /** A copy of a straight-alpha ARGB picture with its colour premultiplied by alpha. */
    public static int[] premultiply(int[] argb) {
        int[] out = new int[argb.length];
        for (int i = 0; i < argb.length; i++) {
            int c = argb[i];
            int a = c >>> 24;
            int r = ((c >> 16) & 0xFF) * a / 255, g = ((c >> 8) & 0xFF) * a / 255, b = (c & 0xFF) * a / 255;
            out[i] = (a << 24) | (r << 16) | (g << 8) | b;
        }
        return out;
    }

    /** A premultiplied copy of a picture, for drawing it added in the GUI. */
    public static SolTexture premultiplied(String path, int width, int height, boolean linear, int[] argb) {
        SolTexture t = new SolTexture(path, width, height, linear);
        t.premultiplied = true;
        t.upload(premultiply(argb));
        return t;
    }

    /** A texture already filled from an ARGB array (row by row from the top). */
    public static SolTexture of(String path, int width, int height, boolean linear, int[] argb) {
        SolTexture t = new SolTexture(path, width, height, linear);
        t.upload(argb);
        return t;
    }

    /** Copies an ARGB picture in and uploads it. */
    public void upload(int[] argb) {
        if (released) {
            return;
        }
        NativeImage pixels = texture.getPixels();
        if (pixels == null) {
            return;
        }
        int i = 0;
        for (int py = 0; py < height; py++) {
            for (int px = 0; px < width; px++) {
                int c = argb[i++];
                //? if >=1.21.2 {
                pixels.setPixel(px, py, c);
                //?} else {
                /*pixels.setPixelRGBA(px, py, (c & 0xFF00FF00) | ((c & 0xFF) << 16) | ((c >> 16) & 0xFF));
                *///?}
            }
        }
        texture.upload();
        // Up to 1.21.4 an upload sets the texture's filter to nearest (NativeImage._upload): put the filter it's
        // drawn with back.
        //? if <1.21.5 {
        /*texture.setFilter(drawnLinear, false);
        *///?}
    }

    /**
     * Draws it smooth or pixel-sharp from now on. The world's Pixel light style draws every Sol light pixel-sharp
     * (glows in visible steps, the look of a block game); Smooth uses each texture's own filter.
     */
    public void useLinear(boolean on) {
        if (on == drawnLinear || released) {
            return;
        }
        drawnLinear = on;
        //? if <1.21.11 {
        /*texture.setFilter(on, false);
        *///?}
    }

    //? if >=1.21.11 {
    /** The sampler this texture is drawn with. */
    public GpuSampler sampler() {
        return RenderSystem.getSamplerCache().getClampToEdge(drawnLinear ? FilterMode.LINEAR : FilterMode.NEAREST);
    }
    //?}

    //? if >=26.3 {
    /*public com.mojang.renderpearl.api.textures.GpuTextureView view() {
        return texture.getTextureView();
    }
    *///?} elif >=1.21.6 {
    public com.mojang.blaze3d.textures.GpuTextureView view() {
        return texture.getTextureView();
    }
    //?}

    public void release() {
        if (!released) {
            released = true;
            Minecraft.getInstance().getTextureManager().release(id);
        }
    }

    public boolean released() {
        return released;
    }
}
