package io.github.georgwav.cosmetics.model;

import java.util.Locale;

/**
 * How a part of a cosmetic is drawn. Each mode maps to one vanilla entity render type, so Sodium,
 * Iris and shaderpacks treat cosmetic geometry exactly like any other entity geometry.
 */
public enum RenderMode {
    /** Opaque/alpha-tested geometry, vanilla {@code entityCutoutNoCull}. */
    CUTOUT,
    /** Alpha-blended geometry, vanilla {@code entityTranslucent}. */
    TRANSLUCENT,
    /**
     * Glowing geometry, vanilla {@code eyes} (the spider/enderman eye render type). Shaderpacks
     * recognise this render type as emissive, so glow parts light up correctly with shaders on.
     */
    EMISSIVE;

    public static RenderMode byId(String id) {
        return valueOf(id.toUpperCase(Locale.ROOT));
    }
}
