package io.github.georgwav.cosmetics.sol.glint;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.georgwav.cosmetics.CosmeticsMod;
import io.github.georgwav.cosmetics.render.RecoloredTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;

import java.util.HashMap;
import java.util.Map;

/**
 * Glint's colours: the pack's own glint textures (item, entity, armour, as this version has them) recoloured in
 * place, so their scrolling, blur and wrapping stay the game's. The originals are read once per resource reload and
 * put back when Glint is off. Render thread only.
 */
public final class GlintColors {
    private static final String[] TEXTURES = {"minecraft:textures/misc/enchanted_glint_item.png",
            "minecraft:textures/misc/enchanted_glint_entity.png", "minecraft:textures/misc/enchanted_glint_armor.png"};
    private static final Map<String, int[][]> ORIGINALS = new HashMap<>();
    private static boolean painted;
    /** Textures painted so far (the test scenario checks Glint reached the game's glint). */
    public static int uploads;

    private GlintColors() {
    }

    /** After a resource reload: the originals may have changed (and the game reloaded its own pixels). */
    public static void reload() {
        ORIGINALS.clear();
        painted = false;
    }

    /** Paints every glint texture with the recolour (null puts the originals back). */
    public static void paint(GlintRecolor.Mode mode, int a, int b, float strength, float phase) {
        for (String id : TEXTURES) {
            try {
                int[][] original = ORIGINALS.computeIfAbsent(id, GlintColors::readOrEmpty);
                if (original.length == 0) {
                    continue;
                }
                int w = original[0][0], h = original[0][1];
                int[] out = new int[w * h];
                if (mode == null) {
                    System.arraycopy(original[1], 0, out, 0, out.length);
                } else {
                    GlintRecolor.apply(original[1], out, w, mode, a, b, strength, phase);
                }
                upload(id, out, w, h);
                if (uploads++ == 0) {
                    CosmeticsMod.LOGGER.info("[Sol] Glint recolours the game's {}", id);
                }
            } catch (Exception | LinkageError e) {
                CosmeticsMod.LOGGER.warn("[Sol] Couldn't recolour {}", id, e);
                ORIGINALS.put(id, new int[0][]);
            }
        }
        painted = mode != null;
    }

    /** Puts the originals back if they were painted. */
    public static void restore() {
        if (painted) {
            paint(null, 0, 0, 1f, 0f);
        }
    }

    private static int[][] readOrEmpty(String id) {
        try {
            int[][] read = RecoloredTextures.read(id);
            return read == null ? new int[0][] : read;
        } catch (Exception e) {
            return new int[0][];
        }
    }

    /** Writes ARGB pixels into the game's own texture for {@code id}. */
    private static void upload(String id, int[] argb, int w, int h) {
        //? if >=1.21.11 {
        AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(net.minecraft.resources.Identifier.parse(id));
        //?} else {
        /*AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(net.minecraft.resources.ResourceLocation.parse(id));
        *///?}
        try (NativeImage image = new NativeImage(w, h, false)) {
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int c = argb[y * w + x];
                    //? if >=1.21.2 {
                    image.setPixel(x, y, c);
                    //?} else {
                    /*image.setPixelRGBA(x, y, (c & 0xFF00FF00) | ((c & 0xFF) << 16) | ((c >> 16) & 0xFF));
                    *///?}
                }
            }
            //? if >=1.21.5 {
            com.mojang.blaze3d.systems.RenderSystem.getDevice().createCommandEncoder().writeToTexture(texture.getTexture(), image);
            //?} elif >=1.21.4 {
            /*texture.bind();
            image.upload(0, 0, 0, 0, 0, w, h, false);
            *///?} else {
            /*texture.bind();
            image.upload(0, 0, 0, 0, 0, w, h, true, false);
            *///?}
        }
    }
}
