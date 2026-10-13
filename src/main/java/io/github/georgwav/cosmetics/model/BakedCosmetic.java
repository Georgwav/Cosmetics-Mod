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
    private final String[] styles;
    private final int defaultStyle;
    // Last style looked up by name (render thread), so the per-frame lookup is one reference compare.
    private String lastStyleName;
    private int lastStyle;

    BakedCosmetic(CosmeticDefinition definition, List<Layer> layers, String[] styles) {
        this.definition = definition;
        this.layers = layers.toArray(new Layer[0]);
        this.emissive = layers.stream().anyMatch(l -> l.mode() == RenderMode.EMISSIVE);
        this.animated = layers.stream().anyMatch(l -> l.animations().length > 0 || l.textureFrames() != null
                || (l.rig() != null && l.rig().animated())) || definition.wings() != null;
        this.styles = styles;
        int index = definition.defaultStyle() != null ? java.util.Arrays.asList(styles).indexOf(definition.defaultStyle()) : 0;
        this.defaultStyle = Math.max(0, index);
        this.lastStyle = defaultStyle;
    }

    /** Names of the cosmetic's styles (its skeletal animations); empty when it has none. */
    public String[] styles() {
        return styles;
    }

    public int defaultStyle() {
        return defaultStyle;
    }

    /** Index of a style by name; the default style for null or a name the cosmetic doesn't have. */
    public int styleIndex(String name) {
        if (name == lastStyleName) {
            return lastStyle;
        }
        int index = name == null ? -1 : java.util.Arrays.asList(styles).indexOf(name);
        lastStyleName = name;
        lastStyle = index >= 0 ? index : defaultStyle;
        return lastStyle;
    }

    /** Whether players may change this option (otherwise it is hidden and has no effect). */
    public boolean allows(CosmeticOption option) {
        return (definition.options() & option.bit()) != 0;
    }

    /** RGB tint until a player picks another; white keeps the texture's own colours. */
    public int defaultColor() {
        return definition.defaultColor();
    }

    /** Wing behaviour, or null. */
    public CosmeticDefinition.Wings wings() {
        return definition.wings();
    }

    /** Has glowing parts (so the glow setting applies to it). */
    public boolean hasEmissive() {
        return emissive;
    }

    /** Has moving parts or animated textures (so the animation settings apply to it). */
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
        private final BakedMesh smoothMesh;
        private final TextureFrames textureFrames;
        private final boolean tinted;
        private final MeshRig rig;
        private final int joint;
        private final SkinnedMesh skin;
        private final CosmeticDefinition.WingPart wing;
        private Cloth.Mesh clothMesh;
        private Cloth.Mesh clothSmoothMesh;
        /** The vanilla render type for this layer, created on first use. Render thread only. */
        public Object renderType;

        Layer(RenderMode mode, String texture, BakedMesh mesh, CosmeticDefinition.Animation[] animations,
              BakedMesh smoothMesh, TextureFrames textureFrames, boolean tinted) {
            this(mode, texture, mesh, animations, smoothMesh, textureFrames, tinted, null, -1, null, null);
        }

        Layer(RenderMode mode, String texture, BakedMesh mesh, CosmeticDefinition.Animation[] animations,
              BakedMesh smoothMesh, TextureFrames textureFrames, boolean tinted, MeshRig rig, int joint,
              SkinnedMesh skin, CosmeticDefinition.WingPart wing) {
            this.mode = mode;
            this.texture = texture;
            this.mesh = mesh;
            this.animations = animations;
            this.smoothMesh = smoothMesh;
            this.textureFrames = textureFrames;
            this.tinted = tinted;
            this.rig = rig;
            this.joint = joint;
            this.skin = skin;
            this.wing = wing;
        }

        /** Which wing this layer is (turned by the cosmetic's wing behaviour), or null. */
        public CosmeticDefinition.WingPart wing() {
            return wing;
        }

        /** The skeleton this layer's geometry belongs to, or null for still geometry. */
        public MeshRig rig() {
            return rig;
        }

        /** The joint this layer's geometry follows rigidly, or -1 (still geometry, or the skinned layer). */
        public int joint() {
            return joint;
        }

        /** Geometry skinned from several joints, drawn instead of {@link #mesh()}; null for every other layer. */
        public SkinnedMesh skin() {
            return skin;
        }

        /** Takes the cosmetic's tint colour; otherwise it is always drawn in the texture's own colours. */
        public boolean tinted() {
            return tinted;
        }

        /** The layer's texture animation, or null when its texture stands still. */
        public TextureFrames textureFrames() {
            return textureFrames;
        }

        /**
         * Smooth-shaded mesh geometry (one normal per vertex), drawn with the same render type right after
         * {@link #mesh()}; null when the layer has none, which is always the case for cubes.
         */
        public BakedMesh smoothMesh() {
            return smoothMesh;
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

        /** This layer split into strips for cloth cloaks; made on first use (render thread only). */
        public Cloth.Mesh clothMesh() {
            if (clothMesh == null) {
                clothMesh = Cloth.prepare(mesh);
            }
            return clothMesh;
        }

        /** The smooth-shaded mesh split for cloth cloaks, or null when the layer has none. */
        public Cloth.Mesh clothSmoothMesh() {
            if (clothSmoothMesh == null && smoothMesh != null) {
                clothSmoothMesh = Cloth.prepare(smoothMesh);
            }
            return clothSmoothMesh;
        }

        public BakedMesh mesh() {
            return mesh;
        }
    }
}
