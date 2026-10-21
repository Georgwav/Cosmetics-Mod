package io.github.georgwav.cosmetics.sol.fx;

/**
 * A picture the effects draw from: its ARGB pixels, kept on the CPU (the menus' live preview draws them there), and
 * the GPU copy the world draw makes of them on first use ({@link WorldOut}). Changing the pixels bumps the version,
 * so the copy is uploaded again. Nearest filtering: every texel stays a sharp square.
 */
public final class FxTex {
    public final String name;
    public final int width, height;
    public final int[] argb;
    /** Bumped when the pixels change. */
    public int version;
    /** The GPU copy and the version it holds (owned by {@link WorldOut}). */
    Object gpu;
    int gpuVersion = -1;

    public FxTex(String name, int width, int height, int[] argb) {
        this.name = name;
        this.width = width;
        this.height = height;
        this.argb = argb;
    }

    public FxTex(String name, int width, int height) {
        this(name, width, height, new int[width * height]);
    }

    /** The pixels changed: upload them again before the next draw. */
    public void changed() {
        version++;
    }

    /** The pixel at (x, y), clamped to the picture. */
    public int at(int x, int y) {
        x = Math.max(0, Math.min(width - 1, x));
        y = Math.max(0, Math.min(height - 1, y));
        return argb[y * width + x];
    }

    /** Frees the GPU copy (the pixels stay). */
    public void release() {
        WorldOut.release(this);
    }
}
