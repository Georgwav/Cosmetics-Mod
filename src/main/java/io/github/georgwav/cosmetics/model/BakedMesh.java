package io.github.georgwav.cosmetics.model;

/**
 * Pre-baked quads in model space, ready to be pushed through a vanilla vertex consumer.
 * Positions are already divided by 16 and every per-cube rotation is already applied, so emitting
 * a mesh is one matrix multiply per vertex and nothing else.
 *
 * <p>Layout per vertex: {@code x, y, z, u, v, nx, ny, nz}. Four vertices per quad. All four
 * vertices of a quad share one normal.
 */
public final class BakedMesh {
    public static final int FLOATS_PER_VERTEX = 8;
    public static final int VERTICES_PER_QUAD = 4;
    public static final int FLOATS_PER_QUAD = FLOATS_PER_VERTEX * VERTICES_PER_QUAD;

    private final float[] data;
    private final int quadCount;

    BakedMesh(float[] data) {
        if (data.length % FLOATS_PER_QUAD != 0) {
            throw new IllegalArgumentException("Mesh data is not a whole number of quads: " + data.length);
        }
        for (float f : data) {
            if (!Float.isFinite(f)) {
                throw new IllegalArgumentException("Mesh contains a non-finite value");
            }
        }
        this.data = data;
        this.quadCount = data.length / FLOATS_PER_QUAD;
    }

    /** Raw vertex data. Must not be modified. */
    public float[] data() {
        return data;
    }

    public int quadCount() {
        return quadCount;
    }

    public boolean isEmpty() {
        return quadCount == 0;
    }
}
