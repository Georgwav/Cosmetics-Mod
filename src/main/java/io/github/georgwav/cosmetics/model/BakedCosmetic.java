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
    // Highest point of the geometry (smallest y, blocks), worked out on first use.
    private float top = Float.NaN;
    // Middle of the geometry's bounds (blocks), worked out on first use.
    private float[] middle;
    private final boolean tintedLayer;

    BakedCosmetic(CosmeticDefinition definition, List<Layer> layers, String[] styles) {
        this.definition = definition;
        this.layers = layers.toArray(new Layer[0]);
        this.emissive = layers.stream().anyMatch(l -> l.mode() == RenderMode.EMISSIVE);
        this.tintedLayer = layers.stream().anyMatch(Layer::tinted);
        this.animated = layers.stream().anyMatch(l -> l.animations().length > 0 || l.textureFrames() != null
                || (l.rig() != null && l.rig().animated())) || definition.wings() != null
                || (definition.bones() != null && definition.bones().animated());
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

    /** Index of the style called {@code name}, or -1 when the cosmetic has none by that name. */
    public int styleNamed(String name) {
        return java.util.Arrays.asList(styles).indexOf(name);
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

    /** The bones of its Blockbench model and their animations, or null when it has none. */
    public BoneRig bones() {
        return definition.bones();
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

    /**
     * What it counts as when a player wears one of a kind at a time: the cosmetic's own kind, else "wings" for
     * wings, else its slot. New wings replace old wings, but not a backpack.
     */
    public String kind() {
        String kind = definition.kind();
        if (kind != null) {
            return kind;
        }
        return definition.wings() != null ? "wings" : definition.slot().id();
    }

    public CosmeticDefinition definition() {
        return definition;
    }

    /**
     * How far the cosmetic reaches above its anchor's pivot, in blocks (the geometry's smallest y, negated; +Y is
     * down), at rest. 0 for a cosmetic with no geometry. Render thread only.
     */
    public float reachUp() {
        if (Float.isNaN(top)) {
            float min = 0f;
            for (Layer layer : layers) {
                BakedMesh mesh = layer.skin() != null ? layer.skin().update(0, 0f) : layer.mesh();
                float[] data = mesh.data();
                for (int i = 1; i < data.length; i += BakedMesh.FLOATS_PER_VERTEX) {
                    min = Math.min(min, data[i]);
                }
            }
            top = min;
        }
        return -top;
    }

    /** Some layer takes the player's colour. */
    public boolean hasTintedLayer() {
        return tintedLayer;
    }

    /**
     * The middle of the geometry's bounds at rest, in blocks around the anchor's pivot ({0, 0, 0} for a cosmetic
     * with no geometry). Render thread only.
     */
    public float[] middle() {
        if (middle == null) {
            float[] lo = {Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY};
            float[] hi = {Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY};
            for (Layer layer : layers) {
                BakedMesh[] meshes = layer.skin() != null ? new BakedMesh[]{layer.skin().update(0, 0f)}
                        : new BakedMesh[]{layer.mesh(), layer.smoothMesh()};
                for (BakedMesh mesh : meshes) {
                    if (mesh == null) {
                        continue;
                    }
                    float[] data = mesh.data();
                    for (int i = 0; i < data.length; i += BakedMesh.FLOATS_PER_VERTEX) {
                        for (int a = 0; a < 3; a++) {
                            lo[a] = Math.min(lo[a], data[i + a]);
                            hi[a] = Math.max(hi[a], data[i + a]);
                        }
                    }
                }
            }
            middle = lo[0] > hi[0] ? new float[3]
                    : new float[]{(lo[0] + hi[0]) / 2f, (lo[1] + hi[1]) / 2f, (lo[2] + hi[2]) / 2f};
        }
        return middle;
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
        private final int bone;
        private Cloth.Mesh clothMesh;
        private Cloth.Mesh clothSmoothMesh;
        /** The vanilla render type for this layer, created on first use. Render thread only. */
        public Object renderType;

        Layer(RenderMode mode, String texture, BakedMesh mesh, CosmeticDefinition.Animation[] animations,
              BakedMesh smoothMesh, TextureFrames textureFrames, boolean tinted) {
            this(mode, texture, mesh, animations, smoothMesh, textureFrames, tinted, null, -1, null, null, -1);
        }

        Layer(RenderMode mode, String texture, BakedMesh mesh, CosmeticDefinition.Animation[] animations,
              BakedMesh smoothMesh, TextureFrames textureFrames, boolean tinted, MeshRig rig, int joint,
              SkinnedMesh skin, CosmeticDefinition.WingPart wing, int bone) {
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
            this.bone = bone;
        }

        /**
         * The bone of the cosmetic's {@link BoneRig} that moves this layer (its correction applies to the baked
         * geometry, and its {@link BoneRig#follow} says which body part it follows); -1 for still geometry.
         */
        public int bone() {
            return bone;
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
