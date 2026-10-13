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
     * @param name       for error messages only
     * @param mode       which vanilla render type draws this part
     * @param texture    texture for this part; usually the cosmetic's texture, but e.g. a glowing overlay
     *                   has its own mostly-transparent texture
     * @param cubes      the part's boxes
     * @param animations movement applied to the whole part every frame, in order (empty for none)
     * @param meshes     the part's free-form meshes (empty for none)
     */
    public record Part(String name, RenderMode mode, String texture, List<Cube> cubes, List<Animation> animations,
                       List<Mesh> meshes) {
        public Part(String name, RenderMode mode, String texture, List<Cube> cubes, List<Animation> animations) {
            this(name, mode, texture, cubes, animations, List.of());
        }
    }

    /**
     * A free-form mesh, already validated and placed in cosmetic space at load time, so baking only
     * copies it into the vertex buffers.
     *
     * @param source    where it came from (a file, or the mesh's place in the cosmetic file), for messages
     * @param smooth    per-corner normals (smooth shading); otherwise every corner of a face carries
     *                  the face normal
     * @param corners   8 floats per face corner: x, y, z in pixels, u, v in texture pixels, nx, ny, nz
     * @param faceSizes corners per face, 3 or 4, in order
     * @param triangles triangle count (a quad counts as two), for the per-cosmetic limit
     */
    public record Mesh(String source, boolean smooth, float[] corners, int[] faceSizes, int triangles) {
        public static final int FLOATS_PER_CORNER = 8;
    }

    /**
     * A simple looping movement. All fields are plain values, so equal animations compare equal and
     * parts that move together are baked into one layer.
     *
     * @param type      what kind of movement
     * @param axis      0 = X, 1 = Y, 2 = Z (model axes: +Y is down)
     * @param pivotX    rotation origin in pixels (spin and swing)
     * @param pivotY    rotation origin in pixels
     * @param pivotZ    rotation origin in pixels
     * @param amplitude degrees for swing, pixels for bob; unused for spin
     * @param speed     turns per second for spin, cycles per second for bob and swing
     * @param phase     offset into the cycle, 0..1, so two parts can move out of step
     */
    public record Animation(AnimationType type, int axis, float pivotX, float pivotY, float pivotZ,
                            float amplitude, float speed, float phase) {
        /**
         * Where the movement is at {@code seconds}: degrees for spin and swing, pixels for bob.
         * {@code speedFactor} scales the speed (the per-cosmetic animation speed setting).
         */
        public float valueAt(double seconds, float speedFactor) {
            double cycle = seconds * speed * speedFactor + phase;
            cycle -= Math.floor(cycle);
            return switch (type) {
                case SPIN -> (float) (cycle * 360.0);
                case BOB, SWING -> (float) (amplitude * Math.sin(cycle * 2.0 * Math.PI));
            };
        }
    }

    public enum AnimationType {
        /** Keeps turning around the axis. */
        SPIN,
        /** Moves back and forth along the axis. */
        BOB,
        /** Rocks back and forth around the axis, like a flapping wing. */
        SWING
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
     * @param pivot     rotation origin in pixels
     * @param rotation  rotation in degrees around X, Y, Z (applied Z, then Y, then X like vanilla)
     * @param transform optional full placement {@code [m00..m22 row-major, tx, ty, tz]} in pixels, used
     *                  instead of {@code pivot}/{@code rotation} (imported models with nested rotations)
     */
    public record Cube(
            float[] from,
            float[] to,
            float inflate,
            boolean mirror,
            float[] boxUv,
            float[][] faces,
            float[] pivot,
            float[] rotation,
            float[] transform
    ) {
        public Cube(float[] from, float[] to, float inflate, boolean mirror, float[] boxUv, float[][] faces,
                    float[] pivot, float[] rotation) {
            this(from, to, inflate, mirror, boxUv, faces, pivot, rotation, null);
        }
    }

    /** Face order used by per-face UVs. */
    public enum Face {
        DOWN, UP, NORTH, SOUTH, WEST, EAST
    }
}
