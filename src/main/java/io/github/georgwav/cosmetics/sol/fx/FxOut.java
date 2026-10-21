package io.github.georgwav.cosmetics.sol.fx;

/**
 * Where effects draw: the world ({@link WorldOut}, through Sol's world draw) or a menu's live preview
 * ({@link RasterOut}, on the CPU). Every effect is written once against this. Quads are four vertices around an
 * origin set per effect, in blocks; the camera's right and up let a quad face the viewer.
 *
 * <p>Drawing goes in two passes: the glow pass (added light) and then the body pass (alpha blended). An effect is asked
 * for both and draws what belongs to the pass it's in ({@link #glowPass()}).
 */
public interface FxOut {
    /** Uses this picture for the next quads (only switches the batch when it changes). */
    void use(FxTex tex);

    /** The world position the next vertices are relative to. */
    void origin(double x, double y, double z);

    /** One vertex: position relative to the origin (blocks), atlas coordinates, ARGB colour. Four make a quad. */
    void vertex(float x, float y, float z, float u, float v, int argb);

    /** Whether this is the glow pass (added) rather than the body pass (alpha blended). */
    boolean glowPass();

    /** The camera's right and up directions, unit length, in world space ({@code out} gets x, y, z). */
    void right(float[] out);

    void up(float[] out);

    /** The camera's position, for effects that face it or hide in first person. */
    double camX();

    double camY();

    double camZ();

    /** The ground's height under a point: the top of the first solid block at or below {@code fromY}. */
    float ground(double x, double z, double fromY);
}
