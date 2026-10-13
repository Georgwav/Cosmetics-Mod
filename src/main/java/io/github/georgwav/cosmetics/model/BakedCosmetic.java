package io.github.georgwav.cosmetics.model;

import java.util.List;

/**
 * A cosmetic whose geometry has been baked once on load. Rendering only ever reads from here.
 * Geometry is grouped into {@link Layer}s, one per (render mode, texture, animation), so each layer is
 * a single vertex-consumer pass.
 */
public final class BakedCosmetic {
    private final CosmeticDefinition definition;
    private final Layer[] layers;

    private final boolean emissive;
    private final boolean animated;

    BakedCosmetic(CosmeticDefinition definition, List<Layer> layers) {
        this.definition = definition;
        this.layers = layers.toArray(new Layer[0]);
        this.emissive = layers.stream().anyMatch(l -> l.mode() == RenderMode.EMISSIVE);
        this.animated = layers.stream().anyMatch(l -> l.animations().length > 0);
    }

    /** Has glowing parts (so the glow setting applies to it). */
    public boolean hasEmissive() {
        return emissive;
    }

    /** Has moving parts (so the animation settings apply to it). */
    public boolean hasAnimation() {
        return animated;
    }

    public String id() {
        return definition.id();
    }

    public String name() {
        return definition.name();
    }

    public CosmeticSlot slot() {
        return definition.slot();
    }

    public CosmeticDefinition definition() {
        return definition;
    }

    /** Layers in draw order: cutout, then translucent, then emissive. */
    public Layer[] layers() {
        return layers;
    }

    public static final class Layer {
        private final RenderMode mode;
        private final String texture;
        private final BakedMesh mesh;
        private final CosmeticDefinition.Animation[] animations;
        /** The vanilla render type for this layer, created on first use. Render thread only. */
        public Object renderType;

        Layer(RenderMode mode, String texture, BakedMesh mesh, CosmeticDefinition.Animation[] animations) {
            this.mode = mode;
            this.texture = texture;
            this.mesh = mesh;
            this.animations = animations;
        }

        /** Movements applied to this layer every frame, in order; empty for a still layer. */
        public CosmeticDefinition.Animation[] animations() {
            return animations;
        }

        public RenderMode mode() {
            return mode;
        }

        public String texture() {
            return texture;
        }

        public BakedMesh mesh() {
            return mesh;
        }
    }
}
