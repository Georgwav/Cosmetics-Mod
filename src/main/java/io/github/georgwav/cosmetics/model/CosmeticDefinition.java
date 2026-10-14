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
 * @param options       which {@link CosmeticOption}s players may change, as a bit set
 * @param defaultColor  RGB tint the cosmetic has until a player picks another (white = the texture's own colours)
 * @param defaultStyle  style (named skeletal animation) shown until a player picks another, or null for the first
 * @param wings         wing behaviour (flapping with movement), or null
 * @param particles     particles the cosmetic gives off around its player (an aura, a trail); empty for none
 * @param sides         for arm and feet cosmetics, which limbs: {@link #LEFT}, {@link #RIGHT} or both
 * @param prop          only shown while an emote that brings it plays (a cloud to ride); not in the Wardrobe
 * @param petPosition   for a pet, where it sits unless the player moves it
 * @param kind          what it counts as when a player wears one of a kind at a time ("hat", "ears", ...), or
 *                      null for its slot (or "wings" for wings)
 * @param bones         the bones of its Blockbench model and their animations, when it has any; else null
 */
public record CosmeticDefinition(
        String id,
        String name,
        CosmeticSlot slot,
        String texture,
        int textureWidth,
        int textureHeight,
        List<Part> parts,
        int options,
        int defaultColor,
        String defaultStyle,
        Wings wings,
        List<ParticleEmitter> particles,
        int sides,
        boolean prop,
        PetPosition petPosition,
        String kind,
        BoneRig bones,
        boolean firstPersonTurned
) {
    public static final int WHITE = 0xFFFFFF;
    public static final int LEFT = 1;
    public static final int RIGHT = 2;

    public CosmeticDefinition(String id, String name, CosmeticSlot slot, String texture, int textureWidth,
                              int textureHeight, List<Part> parts) {
        this(id, name, slot, texture, textureWidth, textureHeight, parts, CosmeticOption.ALL, WHITE, null, null, List.of(),
                slot.defaultSides(), false, PetPosition.LEFT_SHOULDER, null, null, false);
    }

    /**
     * Particles given off around the player: an aura, a cape's trail, sparkles from wings. They live in the world
     * like vanilla particles (they stay behind when the player moves). Distances are in pixels (1/16 block) and
     * directions are the player's, turned with the body: +X to the player's right, +Y up, +Z behind them.
     *
     * @param sprites    particle textures, one per stage of the particle's life (a single one for a still image).
     *                   A sprite {@code ns:name} is the file {@code assets/ns/textures/particle/name.png}, so vanilla
     *                   ones ({@code minecraft:heart}, {@code minecraft:flame}, ...) work too
     * @param rate       particles per second
     * @param minLife    shortest life, seconds
     * @param maxLife    longest life, seconds
     * @param origin     centre of the area particles start in, from the feet
     * @param area       half size of that area (a box)
     * @param velocity   starting speed, pixels per second
     * @param spread     random extra starting speed, up to this either way, pixels per second
     * @param gravity    pull downwards, pixels per second squared (negative lifts)
     * @param drag       share of its speed a particle loses per second (0 keeps it, 1 stops it)
     * @param startSize  size at birth, pixels
     * @param endSize    size at the end of its life, pixels
     * @param startAlpha opacity at birth (0 to 1)
     * @param endAlpha   opacity at the end of its life
     * @param colors     RGB tints, one picked per particle (white keeps the texture's colours)
     * @param glow       drawn full bright, ignoring the light around it
     * @param spin       greatest turn, degrees per second either way
     * @param when       when the player gives particles off
     */
    public record ParticleEmitter(List<String> sprites, float rate, float minLife, float maxLife, float[] origin,
                                  float[] area, float[] velocity, float[] spread, float gravity, float drag,
                                  float startSize, float endSize, float startAlpha, float endAlpha, int[] colors,
                                  boolean glow, float spin, ParticleTrigger when) {
    }

    /** When an emitter gives particles off. */
    public enum ParticleTrigger {
        ALWAYS, MOVING, STILL;

        public boolean active(boolean moving) {
            return this == ALWAYS || (this == MOVING) == moving;
        }
    }

    /**
     * Wings that flap with what the player does: a resting angle and a flap for standing, moving, flying and
     * sneaking, blended smoothly. Parts marked {@code "wing": "left"/"right"} turn by the flap, in opposite
     * directions; skeletal meshes play one loop of their clip per flap.
     *
     * @param axis         0 = X, 1 = Y, 2 = Z (model axes) the wings turn around
     * @param offset       pixels the whole cosmetic moves, always
     * @param sneakOffset  pixels added while sneaking
     * @param armorOffset  pixels added while something is worn in the chest slot (not an elytra)
     * @param spread       pixels each wing moves outwards, sideways
     */
    public record Wings(int axis, WingState idle, WingState moving, WingState flying, WingState sneaking,
                        float[] offset, float[] sneakOffset, float[] armorOffset, float spread) {
    }

    /**
     * @param base      resting angle in degrees (positive opens the wings outwards)
     * @param amplitude flap in degrees either side of the resting angle
     * @param speed     flaps per second
     */
    public record WingState(float base, float amplitude, float speed) {
    }

    /**
     * A part that is one of the wings.
     *
     * @param side +1 for the left wing (+X), -1 for the right
     */
    public record WingPart(int side, float pivotX, float pivotY, float pivotZ) {
    }

    /**
     * @param name       for error messages only
     * @param mode       which vanilla render type draws this part
     * @param texture    texture for this part; usually the cosmetic's texture, but e.g. a glowing overlay
     *                   has its own mostly-transparent texture
     * @param cubes      the part's boxes
     * @param animations movement applied to the whole part every frame, in order (empty for none)
     * @param meshes     the part's free-form meshes (empty for none)
     * @param textureAnimation flip-book animation of the part's texture, or null for a still texture
     * @param tinted     takes the cosmetic's tint colour; false keeps the texture's own colours whatever the
     *                   player picks (for example everything except the one coloured layer)
     * @param wing       which wing this part is, for the cosmetic's {@link Wings}; null for other parts
     * @param bone       for a part of a Blockbench model with bones, the bone ({@link BoneRig#driver}) that moves
     *                   it; -1 for one that never moves
     */
    public record Part(String name, RenderMode mode, String texture, List<Cube> cubes, List<Animation> animations,
                       List<Mesh> meshes, TextureAnimation textureAnimation, boolean tinted, WingPart wing, int bone) {
        public Part(String name, RenderMode mode, String texture, List<Cube> cubes, List<Animation> animations) {
            this(name, mode, texture, cubes, animations, List.of(), null, true, null, -1);
        }

        public Part(String name, RenderMode mode, String texture, List<Cube> cubes, List<Animation> animations,
                    List<Mesh> meshes, TextureAnimation textureAnimation, boolean tinted, WingPart wing) {
            this(name, mode, texture, cubes, animations, meshes, textureAnimation, tinted, wing, -1);
        }

        public Part withTextureAnimation(TextureAnimation animation) {
            return new Part(name, mode, texture, cubes, animations, meshes, animation, tinted, wing, bone);
        }

        public Part withBone(int bone) {
            return new Part(name, mode, texture, cubes, animations, meshes, textureAnimation, tinted, wing, bone);
        }
    }

    /**
     * A texture that flips through frames, like a flip-book. Either one image holding every frame stacked
     * top to bottom ({@code textures} is null; UVs address one frame, as {@code texture_size} describes
     * it), or one file per frame. Lists, not arrays, so equal animations compare equal and parts that
     * animate together share a layer.
     *
     * @param frames     number of frames, at least 1
     * @param frameTimes how long each frame shows, in milliseconds, one entry per frame
     * @param textures   one texture per frame, or null for a vertical strip in the part's texture
     */
    public record TextureAnimation(int frames, List<Integer> frameTimes, List<String> textures) {
        public boolean strip() {
            return textures == null;
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
     * @param rigged    for a skeletal mesh, its rig and geometry (then {@code corners} and {@code faceSizes}
     *                  are empty); null for a still mesh
     */
    public record Mesh(String source, boolean smooth, float[] corners, int[] faceSizes, int triangles, Rigged rigged) {
        public static final int FLOATS_PER_CORNER = 8;

        public Mesh(String source, boolean smooth, float[] corners, int[] faceSizes, int triangles) {
            this(source, smooth, corners, faceSizes, triangles, null);
        }
    }

    /**
     * A skeletal mesh split at load time: pieces that follow a single joint, and the rest, which is
     * skinned on the CPU. Positions are in the mesh file's units; the rig's placement maps them into the
     * cosmetic.
     *
     * @param pieces  rigid geometry per joint
     * @param skinned vertices weighted to several joints, or null when every triangle is rigid
     */
    public record Rigged(MeshRig rig, List<RigidPiece> pieces, SkinnedPiece skinned) {
    }

    /**
     * Triangles whose corners all follow one joint, in that joint's space.
     *
     * @param corners {@link Mesh#FLOATS_PER_CORNER} floats per corner, three corners per triangle
     */
    public record RigidPiece(int joint, float[] corners) {
        public int triangles() {
            return corners.length / (3 * Mesh.FLOATS_PER_CORNER);
        }
    }

    /**
     * Vertices skinned from several joints.
     *
     * @param weightStart  first weight of each vertex
     * @param weightCount  number of weights of each vertex
     * @param weightJoint  joint of each weight
     * @param weightBias   share of each weight, the shares of a vertex add up to 1
     * @param weightPos    vertex position in each weight's joint space (3 floats per weight)
     * @param weightNormal vertex normal in each weight's joint space (3 floats per weight)
     * @param uvs          texture pixels per vertex (2 floats)
     * @param triangles    vertex indices, three per triangle
     */
    public record SkinnedPiece(int[] weightStart, int[] weightCount, int[] weightJoint, float[] weightBias,
                               float[] weightPos, float[] weightNormal, float[] uvs, int[] triangles) {
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
