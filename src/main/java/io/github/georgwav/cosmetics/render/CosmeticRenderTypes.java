package io.github.georgwav.cosmetics.render;

import io.github.georgwav.cosmetics.model.BakedCosmetic;
import io.github.georgwav.cosmetics.model.RenderMode;
import io.github.georgwav.cosmetics.model.TextureFrames;
//? if >=1.21.11 {
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
//?} else {
/*import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
*///?}

/**
 * Maps a cosmetic's render mode to a stock vanilla entity render type. Only vanilla types are used,
 * never custom shaders or pipelines, which is what lets Iris substitute its own entity programs (and
 * draw the cosmetic into the shadow map) and lets Sodium and ImmediatelyFast batch it.
 */
public final class CosmeticRenderTypes {
    private CosmeticRenderTypes() {
    }

    public static RenderType get(BakedCosmetic.Layer layer) {
        Object cached = layer.renderType;
        if (cached == null) {
            cached = create(layer.texture(), layer.mode());
            layer.renderType = cached;
        }
        return (RenderType) cached;
    }

    /**
     * The render type for one frame of the layer; frames with their own texture each have their own. While a
     * recoloured cosmetic is drawn (see {@link RecoloredTextures}), its recoloured copy of the texture.
     */
    public static RenderType get(BakedCosmetic.Layer layer, int frame) {
        TextureFrames frames = layer.textureFrames();
        RenderType recolored = RecoloredTextures.renderType(frames == null || frames.strip() ? layer.texture()
                : frames.texture(frame), layer.mode());
        if (recolored != null) {
            return recolored;
        }
        if (frames == null || frames.strip()) {
            return get(layer);
        }
        Object cached = frames.renderTypes[frame];
        if (cached == null) {
            cached = create(frames.texture(frame), layer.mode());
            frames.renderTypes[frame] = cached;
        }
        return (RenderType) cached;
    }

    static RenderType create(String texture, RenderMode mode) {
        //? if >=26.1 {
        /*Identifier id = Identifier.parse(texture);
        // 26.1 renamed entityCutoutNoCull to entityCutout (the culling one is now entityCutoutCull).
        return switch (mode) {
            case CUTOUT -> RenderTypes.entityCutout(id);
            case TRANSLUCENT -> RenderTypes.entityTranslucent(id);
            case EMISSIVE -> RenderTypes.eyes(id);
        };
        *///?} elif >=1.21.11 {
        Identifier id = Identifier.parse(texture);
        return switch (mode) {
            case CUTOUT -> RenderTypes.entityCutoutNoCull(id);
            case TRANSLUCENT -> RenderTypes.entityTranslucent(id);
            case EMISSIVE -> RenderTypes.eyes(id);
        };
        //?} else {
        /*ResourceLocation id = ResourceLocation.parse(texture);
        return switch (mode) {
            case CUTOUT -> RenderType.entityCutoutNoCull(id);
            case TRANSLUCENT -> RenderType.entityTranslucent(id);
            case EMISSIVE -> RenderType.eyes(id);
        };
        *///?}
    }
}
