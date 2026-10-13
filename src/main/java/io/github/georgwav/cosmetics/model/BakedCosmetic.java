package io.github.georgwav.cosmetics.model;

import java.util.List;

/**
 * A cosmetic whose geometry has been baked once on load. Rendering only ever reads from here.
 * Geometry is grouped into {@link Layer}s, one per (render mode, texture) pair, so each layer is a
 * single vertex-consumer pass.
 */
public final class BakedCosmetic {
    private final CosmeticDefinition definition;
    private final Layer[] layers;

    BakedCosmetic(CosmeticDefinition definition, List<Layer> layers) {
        this.definition = definition;
        this.layers = layers.toArray(new Layer[0]);
    }

    public String id() {
        return definition.id();
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
        /** The vanilla render type for this layer, created on first use. Render thread only. */
        public Object renderType;

        Layer(RenderMode mode, String texture, BakedMesh mesh) {
            this.mode = mode;
            this.texture = texture;
            this.mesh = mesh;
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
