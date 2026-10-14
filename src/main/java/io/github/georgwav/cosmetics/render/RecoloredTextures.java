package io.github.georgwav.cosmetics.render;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.georgwav.cosmetics.CosmeticsMod;
import io.github.georgwav.cosmetics.model.RenderMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.server.packs.resources.Resource;
//? if >=1.21.11 {
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
//?} else {
/*import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
*///?}

import java.io.InputStream;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Recoloured copies of cosmetic textures (see {@link Recolor}), made on the CPU from the resource pack's image and
 * uploaded as textures of their own, drawn with the same stock render types as the originals. While a cosmetic is
 * drawn, {@link CosmeticRenderer} sets its recolour ({@link #begin}/{@link #end}) and {@link CosmeticRenderTypes}
 * asks here for the texture.
 *
 * <p>One copy per original texture, remade (at most every {@link #REMAKE_NANOS}) when its recolour changes, so
 * dragging a slider doesn't pile up textures. Dropped on resource reload. Render thread only.
 */
public final class RecoloredTextures {
    /** Fewest nanoseconds between two remakes of one texture (a slider being dragged). */
    private static final long REMAKE_NANOS = 120_000_000L;
    /** Textures bigger than this many pixels aren't recoloured (their tint still applies). */
    private static final int MAX_PIXELS = 16 * 1024 * 1024;

    private static final class Copy {
        Object id;
        DynamicTexture texture;
        int width;
        int height;
        Recolor recolor;
        long made;
        final Map<RenderMode, Object> renderTypes = new EnumMap<>(RenderMode.class);
        boolean failed;
    }

    private static final Map<String, Copy> COPIES = new HashMap<>();
    private static Recolor current;
    private static int made;

    private RecoloredTextures() {
    }

    /** The recolour of the cosmetic about to be drawn (null for none), until {@link #end}. */
    public static void begin(Recolor recolor) {
        current = recolor;
    }

    public static void end() {
        current = null;
    }

    /** Recoloured textures made since the game started (read by the in-game test). */
    public static int made() {
        return made;
    }

    /** The render type for {@code texture} in {@code mode} recoloured as the cosmetic being drawn asks, or null. */
    static RenderType renderType(String texture, RenderMode mode) {
        Recolor recolor = current;
        if (recolor == null) {
            return null;
        }
        Copy copy = COPIES.computeIfAbsent(texture, k -> new Copy());
        if (copy.failed) {
            return null;
        }
        long now = System.nanoTime();
        if (copy.recolor == null || (!copy.recolor.equals(recolor) && now - copy.made > REMAKE_NANOS)) {
            try {
                if (!paint(texture, copy, recolor)) {
                    copy.failed = true;
                    return null;
                }
            } catch (Exception | LinkageError e) {
                CosmeticsMod.LOGGER.warn("Couldn't recolour texture {}", texture, e);
                copy.failed = true;
                return null;
            }
            copy.recolor = recolor;
            copy.made = now;
        }
        Object type = copy.renderTypes.get(mode);
        if (type == null) {
            type = CosmeticRenderTypes.create(copy.id.toString(), mode);
            copy.renderTypes.put(mode, type);
        }
        return (RenderType) type;
    }

    /** Forgets every copy (resource reload: the originals may have changed). */
    public static void clear() {
        for (Copy copy : COPIES.values()) {
            if (copy.id != null) {
                release(copy.id);
            }
        }
        COPIES.clear();
    }

    /** Reads the original from the resource packs and paints it recoloured into the copy's texture. */
    private static boolean paint(String texture, Copy copy, Recolor recolor) throws Exception {
        //? if >=1.21.11 {
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(Identifier.parse(texture));
        //?} else {
        /*Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(ResourceLocation.parse(texture));
        *///?}
        if (resource.isEmpty()) {
            return false;
        }
        try (InputStream in = resource.get().open(); NativeImage source = NativeImage.read(in)) {
            int w = source.getWidth();
            int h = source.getHeight();
            if ((long) w * h > MAX_PIXELS) {
                return false;
            }
            if (copy.texture == null || copy.width != w || copy.height != h) {
                if (copy.id != null) {
                    release(copy.id);
                }
                copy.width = w;
                copy.height = h;
                copy.renderTypes.clear();
                //? if >=1.21.11 {
                copy.id = Identifier.fromNamespaceAndPath(CosmeticsMod.MOD_ID, "recolored/" + made++);
                //?} else {
                /*copy.id = ResourceLocation.fromNamespaceAndPath(CosmeticsMod.MOD_ID, "recolored/" + made++);
                *///?}
                //? if >=1.21.5 {
                copy.texture = new DynamicTexture("cosmeticsmod recolored", w, h, false);
                //?} else {
                /*copy.texture = new DynamicTexture(w, h, false);
                *///?}
                register(copy.id, copy.texture);
            }
            // ARGB, as the Wardrobe's pictures read textures on every version.
            int[] argb = source.makePixelArray();
            NativeImage pixels = copy.texture.getPixels();
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int c = recolor.apply(argb[y * w + x]);
                    //? if >=1.21.2 {
                    pixels.setPixel(x, y, c);
                    //?} else {
                    /*pixels.setPixelRGBA(x, y, abgr(c));
                    *///?}
                }
            }
            copy.texture.upload();
            return true;
        }
    }

    /** ARGB to ABGR and back (red and blue swapped). */
    static int abgr(int c) {
        return (c & 0xFF00FF00) | ((c & 0xFF) << 16) | ((c >> 16) & 0xFF);
    }

    private static void register(Object id, DynamicTexture texture) {
        //? if >=1.21.11 {
        Minecraft.getInstance().getTextureManager().register((Identifier) id, texture);
        //?} else {
        /*Minecraft.getInstance().getTextureManager().register((ResourceLocation) id, texture);
        *///?}
    }

    private static void release(Object id) {
        //? if >=1.21.11 {
        Minecraft.getInstance().getTextureManager().release((Identifier) id);
        //?} else {
        /*Minecraft.getInstance().getTextureManager().release((ResourceLocation) id);
        *///?}
    }
}
