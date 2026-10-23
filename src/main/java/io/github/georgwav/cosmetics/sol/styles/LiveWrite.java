package io.github.georgwav.cosmetics.sol.styles;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.georgwav.cosmetics.mixin.SpriteContentsAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/**
 * Styles' live path (B.2.6): a texture's new pixels written straight into the game, with no reload. A sprite is
 * written into its place in its atlas, every mip level (made here again from the new pixels); one of another
 * resolution (a 32x pack's over a 16x one) is brought to the size of that place until a reload stitches the atlas
 * again. A texture of its own is uploaded again, or, at another size, replaced by one of the new size (given back to
 * the game at the next reload). Each era writes textures its own way, so every difference is here. Render thread
 * only.
 */
final class LiveWrite {
    private LiveWrite() {
    }

    /** How a texture went in. */
    enum Result {
        /** As it is. */
        EXACT,
        /** Brought to the size of its place in an atlas: right, but sharper only after a reload. */
        SCALED,
        /** Not at all (an animated sprite, an unknown texture): a reload does it. */
        NO
    }

    /** Textures of their own replaced at another size: the game's again at the next reload. */
    private static final java.util.Set<Object> REPLACED = new java.util.HashSet<>();

    /** Writes the texture at {@code path} (under assets/minecraft/). {@code w} and {@code h}: its size now. */
    static Result write(String path, Pixels.Image img, int w, int h) {
        if (!path.startsWith("textures/") || !path.endsWith(".png")) {
            return Result.NO;
        }
        String name = path.substring("textures/".length(), path.length() - 4);
        TextureAtlasSprite sprite = sprite(name);
        if (sprite != null) {
            if (animated(sprite)) {
                return Result.NO;
            }
            int sw = sprite.contents().width(), sh = sprite.contents().height();
            boolean same = img.w() == sw && img.h() == sh;
            if (!writeSprite(sprite, same ? img : Pixels.resample(img, sw, sh))) {
                return Result.NO;
            }
            return same ? Result.EXACT : Result.SCALED;
        }
        if (inAtlas(name)) {
            // An atlas sprite we couldn't find: leave it to the reload.
            return Result.NO;
        }
        return writeTexture(path, img, img.w() != w || img.h() != h) ? Result.EXACT : Result.NO;
    }

    /** The game's atlases that Styles' textures can sit in (each one's sources read from its atlases/ file). */
    private static final String[] ATLASES = {"blocks", "items", "particles", "gui", "celestials", "shield_patterns", "banner_patterns",
            "chests", "beds", "signs", "shulker_boxes", "decorated_pot", "armor_trims", "paintings", "map_decorations"};

    /** One atlas source: textures under {@code source}/ are sprites named {@code prefix} + their path below it. */
    private record Source(String atlas, String source, String prefix, String single) {
    }

    private static java.util.List<Source> sources;

    /**
     * After a reload: forgets the atlases' sources (a pack may have changed them), and gives the textures replaced at
     * another size back to the game, which loads each again from the packs the next time it's drawn.
     */
    static void reloaded() {
        sources = null;
        for (Object id : REPLACED) {
            io.github.georgwav.cosmetics.render.RecoloredTextures.release(id);
        }
        REPLACED.clear();
    }

    /** Every atlas's directory and single-file sources, read once from the game's resources. */
    private static java.util.List<Source> sources() {
        if (sources != null) {
            return sources;
        }
        java.util.List<Source> out = new java.util.ArrayList<>();
        var rm = Minecraft.getInstance().getResourceManager();
        for (String atlas : ATLASES) {
            var res = rm.getResource(id("atlases/" + atlas + ".json"));
            if (res.isEmpty()) {
                continue;
            }
            try (var reader = res.get().openAsReader()) {
                var root = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
                for (var el : root.getAsJsonArray("sources")) {
                    var o = el.getAsJsonObject();
                    String type = o.has("type") ? o.get("type").getAsString() : "";
                    if (type.endsWith("directory") && o.has("source")) {
                        out.add(new Source(atlas, o.get("source").getAsString(), o.has("prefix") ? o.get("prefix").getAsString() : "", null));
                    } else if (type.endsWith("single") && o.has("resource")) {
                        String r = o.get("resource").getAsString();
                        r = r.contains(":") ? r.substring(r.indexOf(':') + 1) : r;
                        String sprite = o.has("sprite") ? o.get("sprite").getAsString() : r;
                        out.add(new Source(atlas, null, sprite.contains(":") ? sprite.substring(sprite.indexOf(':') + 1) : sprite, r));
                    }
                }
            } catch (java.io.IOException | RuntimeException e) {
                // An atlas we can't read: its textures go by reload.
            }
        }
        sources = out;
        return out;
    }

    /** The sprite for a texture name ("block/stone", "environment/celestial/sun"...), or null when it's in no atlas. */
    private static TextureAtlasSprite sprite(String name) {
        for (Source src : sources()) {
            String sprite;
            if (src.single() != null) {
                if (!src.single().equals(name)) {
                    continue;
                }
                sprite = src.prefix();
            } else if (name.startsWith(src.source() + "/")) {
                sprite = src.prefix() + name.substring(src.source().length() + 1);
            } else {
                continue;
            }
            AbstractTexture tex = Minecraft.getInstance().getTextureManager().getTexture(id("textures/atlas/" + src.atlas() + ".png"));
            if (tex instanceof TextureAtlas atlas) {
                TextureAtlasSprite s = atlas.getSprite(id(sprite));
                if (s != null && s.contents().name().equals(id(sprite))) {
                    return s;
                }
            }
        }
        return null;
    }

    /** Whether a texture sits in one of the atlases (then only its sprite may be written, never a texture of its own). */
    private static boolean inAtlas(String name) {
        for (Source src : sources()) {
            if (src.single() != null ? src.single().equals(name) : name.startsWith(src.source() + "/")) {
                return true;
            }
        }
        return false;
    }

    private static boolean writeSprite(TextureAtlasSprite sprite, Pixels.Image img) {
        if (sprite.contents().width() != img.w() || sprite.contents().height() != img.h()) {
            return false;
        }
        NativeImage[] mips = ((SpriteContentsAccess) sprite.contents()).cosmeticsmod$byMipLevel();
        if (mips == null || mips.length == 0) {
            return false;
        }
        // Level 0 is the picture; each smaller level averages the one above it.
        int[] level = img.argb();
        int lw = img.w(), lh = img.h();
        for (int m = 0; m < mips.length; m++) {
            if (m > 0) {
                int nw = Math.max(1, lw / 2), nh = Math.max(1, lh / 2);
                level = half(level, lw, lh, nw, nh);
                lw = nw;
                lh = nh;
            }
            NativeImage mi = mips[m];
            int iw = Math.min(lw, mi.getWidth()), ih = Math.min(lh, mi.getHeight());
            for (int y = 0; y < ih; y++) {
                for (int x = 0; x < iw; x++) {
                    set(mi, x, y, level[y * lw + x]);
                }
            }
        }
        TextureAtlas atlas = (TextureAtlas) Minecraft.getInstance().getTextureManager().getTexture(sprite.atlasLocation());
        //? if >=26.2 {
        /*var encoder = com.mojang.blaze3d.systems.RenderSystem.getDevice().createCommandEncoder();
        for (int m = 0; m < mips.length; m++) {
            encoder.writeToTexture(atlas.getTexture(), mips[m], m, 0, sprite.getX() >> m, sprite.getY() >> m);
        }
        *///?} elif >=1.21.9 {
        var encoder = com.mojang.blaze3d.systems.RenderSystem.getDevice().createCommandEncoder();
        for (int m = 0; m < mips.length; m++) {
            encoder.writeToTexture(atlas.getTexture(), mips[m], m, 0, sprite.getX() >> m, sprite.getY() >> m,
                    mips[m].getWidth(), mips[m].getHeight(), 0, 0);
        }
        //?} elif >=1.21.5 {
        /*sprite.uploadFirstFrame(atlas.getTexture());
        *///?} else {
        /*atlas.bind();
        sprite.uploadFirstFrame();
        *///?}
        return true;
    }

    private static boolean animated(TextureAtlasSprite sprite) {
        //? if >=1.21.9 {
        return sprite.contents().isAnimated();
        //?} else {
        /*return sprite.contents().getUniqueFrames().count() > 1;
        *///?}
    }

    /** A texture of its own, uploaded again; at another size ({@code resized}), a texture of the new size in its place. */
    private static boolean writeTexture(String path, Pixels.Image img, boolean resized) {
        var id = id(path);
        AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(id);
        if (texture == null || texture instanceof TextureAtlas) {
            return false;
        }
        //? if >=1.21.5 {
        resized |= texture.getTexture().getWidth(0) != img.w() || texture.getTexture().getHeight(0) != img.h();
        //?}
        if (resized) {
            var made = io.github.georgwav.cosmetics.render.RecoloredTextures.newTexture(img.w(), img.h());
            io.github.georgwav.cosmetics.render.RecoloredTextures.upload(made, img.argb(), img.w(), img.h());
            io.github.georgwav.cosmetics.render.RecoloredTextures.register(id, made);
            REPLACED.add(id);
            return true;
        }
        try (NativeImage image = new NativeImage(img.w(), img.h(), false)) {
            for (int y = 0; y < img.h(); y++) {
                for (int x = 0; x < img.w(); x++) {
                    set(image, x, y, img.argb()[y * img.w() + x]);
                }
            }
            //? if >=1.21.5 {
            com.mojang.blaze3d.systems.RenderSystem.getDevice().createCommandEncoder().writeToTexture(texture.getTexture(), image);
            //?} elif >=1.21.4 {
            /*texture.bind();
            image.upload(0, 0, 0, 0, 0, img.w(), img.h(), false);
            *///?} else {
            /*texture.bind();
            image.upload(0, 0, 0, 0, 0, img.w(), img.h(), true, false);
            *///?}
        }
        return true;
    }

    private static void set(NativeImage image, int x, int y, int argb) {
        //? if >=1.21.2 {
        image.setPixel(x, y, argb);
        //?} else {
        /*image.setPixelRGBA(x, y, (argb & 0xFF00FF00) | ((argb & 0xFF) << 16) | ((argb >> 16) & 0xFF));
        *///?}
    }

    /** A level half the size: each pixel the alpha-weighted average of the four above it. */
    static int[] half(int[] src, int w, int h, int nw, int nh) {
        int[] out = new int[nw * nh];
        for (int y = 0; y < nh; y++) {
            for (int x = 0; x < nw; x++) {
                long a = 0, r = 0, g = 0, b = 0;
                for (int dy = 0; dy < 2; dy++) {
                    for (int dx = 0; dx < 2; dx++) {
                        int p = src[Math.min(h - 1, y * 2 + dy) * w + Math.min(w - 1, x * 2 + dx)];
                        int pa = p >>> 24;
                        a += pa;
                        r += (long) (p >> 16 & 255) * pa;
                        g += (long) (p >> 8 & 255) * pa;
                        b += (long) (p & 255) * pa;
                    }
                }
                out[y * nw + x] = a == 0 ? 0 : (int) (a / 4) << 24 | (int) (r / a) << 16 | (int) (g / a) << 8 | (int) (b / a);
            }
        }
        return out;
    }

    //? if >=1.21.11 {
    private static net.minecraft.resources.Identifier id(String path) {
        return net.minecraft.resources.Identifier.withDefaultNamespace(path);
    }
    //?} else {
    /*private static net.minecraft.resources.ResourceLocation id(String path) {
        return net.minecraft.resources.ResourceLocation.withDefaultNamespace(path);
    }
    *///?}
}
