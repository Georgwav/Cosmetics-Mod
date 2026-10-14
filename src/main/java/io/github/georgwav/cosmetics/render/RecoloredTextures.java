package io.github.georgwav.cosmetics.render;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.georgwav.cosmetics.CosmeticsMod;
import io.github.georgwav.cosmetics.config.CosmeticsConfig;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Recoloured copies of cosmetic textures (see {@link Recolor}), made on the CPU from the resource pack's image and
 * uploaded as textures of their own, drawn with the same stock render types as the originals. While a cosmetic is
 * drawn, {@link CosmeticRenderer} sets its recolour ({@link #begin}/{@link #end}) and {@link CosmeticRenderTypes}
 * asks here for the texture.
 *
 * <p>One copy per cosmetic, original texture and {@link Recolor.Paint} (cosmetics sharing a texture can be recoloured
 * differently, and a cosmetic's tinted and untinted parts can share one), remade (at most every
 * {@link #REMAKE_NANOS}) when its recolour changes, so dragging a slider or a turning rainbow doesn't pile up
 * textures. Each copy keeps the original's pixels, so it is remade without reading the file again. Which
 * textures are white ones ({@link #analysis}) is worked out once per texture. All dropped on resource reload.
 * Render thread only.
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
        int[] source;
        int[] painted;
        Recolor recolor;
        long made;
        final Map<RenderMode, Object> renderTypes = new EnumMap<>(RenderMode.class);
        boolean failed;
    }

    /**
     * What a texture is like: its size in pixels and the share of them that are coloured (not white, grey or
     * black; see {@link Recolor#paintFor}).
     */
    public record Analysis(int pixels, float coloured) {
    }

    private static final Map<String, Copy> COPIES = new HashMap<>();
    private static final Map<String, Optional<Analysis>> ANALYSES = new HashMap<>();
    private static final Map<List<String>, int[]> PALETTES = new HashMap<>();
    /** Most colours a cosmetic's palette shows. */
    private static final int PALETTE_COLOURS = CosmeticsConfig.MAX_SWAPS;
    private static Recolor current;
    /** Textures painted, by {@link Recolor.Paint} (read by the in-game test). */
    private static final int[] PAINTED = new int[Recolor.Paint.values().length];
    private static String currentCosmetic;
    private static int made;
    /** Textures painted with some of their own colours swapped (read by the in-game test). */
    private static int swapped;

    private RecoloredTextures() {
    }

    /** The cosmetic about to be drawn and its recolour (null for none), until {@link #end}. */
    public static void begin(String cosmetic, Recolor recolor) {
        currentCosmetic = cosmetic;
        current = recolor;
    }

    public static void end() {
        currentCosmetic = null;
        current = null;
    }

    /** Recoloured textures made since the game started (read by the in-game test). */
    public static int made() {
        return made;
    }

    /** How many times a texture was painted {@code paint}'s way since the game started (read by the in-game test). */
    public static int painted(Recolor.Paint paint) {
        return PAINTED[paint.ordinal()];
    }

    /** How many times a texture was painted with some of its own colours swapped (read by the in-game test). */
    public static int swapped() {
        return swapped;
    }

    /** The recolour of the part being drawn, or null (the Wardrobe's pictures paint with it too). */
    public static Recolor current() {
        return current;
    }

    /** What {@code texture} is like, or null when it can't be read. Read once, then remembered. */
    public static Analysis analysis(String texture) {
        Optional<Analysis> known = ANALYSES.get(texture);
        if (known == null) {
            Analysis analysis = null;
            try {
                int[][] read = read(texture);
                if (read != null) {
                    int[] argb = read[1];
                    int seen = 0;
                    int coloured = 0;
                    for (int c : argb) {
                        if ((c >>> 24) >= 0x20) {
                            seen++;
                            if (Recolor.coloured(c)) {
                                coloured++;
                            }
                        }
                    }
                    analysis = new Analysis(argb.length, seen == 0 ? 0f : coloured / (float) seen);
                }
            } catch (Exception | LinkageError e) {
                CosmeticsMod.LOGGER.warn("Couldn't read texture {}", texture, e);
            }
            known = Optional.ofNullable(analysis);
            ANALYSES.put(texture, known);
        }
        return known.orElse(null);
    }

    /**
     * The colours of these textures together (a cosmetic's), most used first (see {@link Recolor#palette}); empty
     * when none can be read or nothing in them is coloured. Worked out once, then remembered.
     */
    public static int[] palette(List<String> textures) {
        return PALETTES.computeIfAbsent(List.copyOf(textures), key -> {
            int[] all = new int[0];
            for (String texture : key) {
                try {
                    int[][] read = read(texture);
                    if (read != null) {
                        int[] joined = java.util.Arrays.copyOf(all, all.length + read[1].length);
                        System.arraycopy(read[1], 0, joined, all.length, read[1].length);
                        all = joined;
                    }
                } catch (Exception | LinkageError e) {
                    CosmeticsMod.LOGGER.warn("Couldn't read texture {}", texture, e);
                }
            }
            return Recolor.palette(all, PALETTE_COLOURS);
        });
    }

    /** The render type for {@code texture} in {@code mode} recoloured as the cosmetic being drawn asks, or null. */
    static RenderType renderType(String texture, RenderMode mode) {
        Recolor recolor = current;
        if (recolor == null) {
            return null;
        }
        Copy copy = COPIES.computeIfAbsent(currentCosmetic + "\n" + texture + "\n" + recolor.paint(), k -> new Copy());
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
        ANALYSES.clear();
        PALETTES.clear();
    }

    /** {@code {{width, height}, argb pixels}} of a texture in the resource packs, or null when it isn't there. */
    private static int[][] read(String texture) throws Exception {
        //? if >=1.21.11 {
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(Identifier.parse(texture));
        //?} else {
        /*Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(ResourceLocation.parse(texture));
        *///?}
        if (resource.isEmpty()) {
            return null;
        }
        try (InputStream in = resource.get().open(); NativeImage source = NativeImage.read(in)) {
            if ((long) source.getWidth() * source.getHeight() > MAX_PIXELS) {
                return null;
            }
            // ARGB, as the Wardrobe's pictures read textures on every version.
            return new int[][]{{source.getWidth(), source.getHeight()}, source.makePixelArray()};
        }
    }

    /** Paints the original recoloured into the copy's texture (reading the original the first time). */
    private static boolean paint(String texture, Copy copy, Recolor recolor) throws Exception {
        if (copy.source == null) {
            int[][] read = read(texture);
            if (read == null) {
                return false;
            }
            copy.width = read[0][0];
            copy.height = read[0][1];
            copy.source = read[1];
            copy.painted = new int[read[1].length];
            //? if >=1.21.11 {
            copy.id = Identifier.fromNamespaceAndPath(CosmeticsMod.MOD_ID, "recolored/" + made++);
            //?} else {
            /*copy.id = ResourceLocation.fromNamespaceAndPath(CosmeticsMod.MOD_ID, "recolored/" + made++);
            *///?}
            //? if >=1.21.5 {
            copy.texture = new DynamicTexture("cosmeticsmod recolored", copy.width, copy.height, false);
            //?} else {
            /*copy.texture = new DynamicTexture(copy.width, copy.height, false);
            *///?}
            register(copy.id, copy.texture);
        }
        int w = copy.width;
        int h = copy.height;
        recolor.applyAll(copy.source, copy.painted);
        PAINTED[recolor.paint().ordinal()]++;
        if (!recolor.swaps().isEmpty()) {
            swapped++;
        }
        NativeImage pixels = copy.texture.getPixels();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int c = copy.painted[y * w + x];
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
