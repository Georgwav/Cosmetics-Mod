package io.github.georgwav.cosmetics.model;

import java.util.List;

/**
 * A cosmetic as described by its JSON file under {@code assets/<namespace>/cosmetics/}.
 * Coordinates are in model pixels (1/16 block) relative to the anchor model part's pivot, using the
 * same axes as vanilla entity models (+Y is down, -Z is the front of the player).
 *
 * @param id            {@code namespace:path} of the JSON file, e.g. {@code cosmeticsmod:top_hat}
 * @param name          display name (the JSON "name", or derived from the file name)
 * @param slot          where the cosmetic attaches
 * @param texture       default texture location ({@code namespace:path/to/texture.png}) for parts
 * @param textureWidth  texture width in pixels used for UV maths
 * @param textureHeight texture height in pixels used for UV maths
 * @param parts         geometry, grouped by render mode
 */
public record CosmeticDefinition(
        String id,
        String name,
        CosmeticSlot slot,
        String texture,
        int textureWidth,
        int textureHeight,
        List<Part> parts
) {

    /**
     * @param name    for error messages only
     * @param mode    which vanilla render type draws this part
     * @param texture texture for this part; usually the cosmetic's texture, but e.g. a glowing overlay
     *                has its own mostly-transparent texture
     * @param cubes   the part's boxes
     */
    public record Part(String name, RenderMode mode, String texture, List<Cube> cubes) {
    }

    /**
     * An axis-aligned box, optionally rotated around {@code pivot}.
     *
     * @param from     minimum corner in pixels
     * @param to       maximum corner in pixels
     * @param inflate  grows the box on every side without changing its UVs (like vanilla CubeDeformation)
     * @param mirror   mirrors the box UVs on the X axis (vanilla "mirror")
     * @param boxUv    vanilla box-UV origin {@code [u, v]}, or {@code null} when {@code faces} is used
     * @param faces    per-face UV rectangles {@code [u1, v1, u2, v2]} indexed by {@link Face#ordinal()};
     *                 a {@code null} entry hides that face. {@code null} when box UV is used.
     * @param pivot    rotation origin in pixels
     * @param rotation rotation in degrees around X, Y, Z (applied Z, then Y, then X like vanilla)
     */
    public record Cube(
            float[] from,
            float[] to,
            float inflate,
            boolean mirror,
            float[] boxUv,
            float[][] faces,
            float[] pivot,
            float[] rotation
    ) {
    }

    /** Face order used by per-face UVs. */
    public enum Face {
        DOWN, UP, NORTH, SOUTH, WEST, EAST
    }
}
