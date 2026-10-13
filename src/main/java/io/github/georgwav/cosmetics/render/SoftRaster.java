package io.github.georgwav.cosmetics.render;

import io.github.georgwav.cosmetics.model.BakedMesh;

import java.util.Arrays;

/**
 * A small software renderer for the Wardrobe's tile pictures: draws baked meshes, textured, lit and depth
 * tested, into a square ARGB image on the CPU. The image is then shown like any other texture, so the tiles
 * look the same on every Minecraft version, whatever its GUI does with 3D rendering.
 *
 * <p>The view is orthographic: a vertex's transformed x and y are its pixel position (x to the right, y down)
 * and a smaller z is nearer. Faces are drawn from both sides, like the mod's cutout render types.
 */
public final class SoftRaster {
    /** Texture pixels see-through below this alpha are skipped, like vanilla's cutout. */
    private static final int CUTOUT_ALPHA = 26;
    /** Light from the upper left, towards the viewer: shade = AMBIENT + DIFFUSE * max(0, n . light). */
    private static final float LIGHT_X = -0.42f, LIGHT_Y = -0.70f, LIGHT_Z = -0.58f;
    private static final float AMBIENT = 0.55f, DIFFUSE = 0.45f;

    /** How a layer's pixels combine with what is already drawn. */
    public enum Blend { CUTOUT, TRANSLUCENT, EMISSIVE }

    /** A texture's pixels, ARGB, row by row from the top. */
    public record Texture(int width, int height, int[] argb) {
        public static final Texture WHITE = new Texture(1, 1, new int[]{0xFFFFFFFF});
    }

    private final int size;
    private final int[] pixels;
    private final float[] depth;

    public SoftRaster(int size) {
        this.size = size;
        this.pixels = new int[size * size];
        this.depth = new float[size * size];
        clear();
    }

    public int size() {
        return size;
    }

    /** The picture, ARGB, row by row from the top. Valid until the next draw or clear. */
    public int[] pixels() {
        return pixels;
    }

    public void clear() {
        Arrays.fill(pixels, 0);
        Arrays.fill(depth, Float.POSITIVE_INFINITY);
    }

    /**
     * Draws a mesh. {@code m} is the position transform as JOML stores a 4x4 matrix's first three rows by column
     * (m00, m01, m02, m10, m11, m12, m20, m21, m22, m30, m31, m32), {@code n} the normal transform likewise
     * (n00 ... n22). {@code vOffset} moves V (one frame of a strip texture). {@code tint} multiplies the colours
     * (ARGB, white keeps them).
     */
    public void draw(BakedMesh mesh, float[] m, float[] n, float vOffset, Texture texture, int tint, Blend blend) {
        float[] d = mesh.data();
        float[] sx = new float[4], sy = new float[4], sz = new float[4], su = new float[4], sv = new float[4];
        int quads = mesh.quadCount();
        int i = 0;
        for (int q = 0; q < quads; q++) {
            float lnx = d[i + 5], lny = d[i + 6], lnz = d[i + 7];
            float nx = n[0] * lnx + n[3] * lny + n[6] * lnz;
            float ny = n[1] * lnx + n[4] * lny + n[7] * lnz;
            float nz = n[2] * lnx + n[5] * lny + n[8] * lnz;
            float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            float shade = 1f;
            if (blend != Blend.EMISSIVE) {
                float dot = len > 1.0e-6f ? (nx * LIGHT_X + ny * LIGHT_Y + nz * LIGHT_Z) / len : 0f;
                shade = AMBIENT + DIFFUSE * Math.max(0f, dot);
            }
            for (int v = 0; v < 4; v++) {
                float x = d[i], y = d[i + 1], z = d[i + 2];
                sx[v] = m[0] * x + m[3] * y + m[6] * z + m[9];
                sy[v] = m[1] * x + m[4] * y + m[7] * z + m[10];
                sz[v] = m[2] * x + m[5] * y + m[8] * z + m[11];
                su[v] = d[i + 3];
                sv[v] = d[i + 4] + vOffset;
                i += BakedMesh.FLOATS_PER_VERTEX;
            }
            triangle(sx, sy, sz, su, sv, 0, 1, 2, texture, tint, shade, blend);
            triangle(sx, sy, sz, su, sv, 0, 2, 3, texture, tint, shade, blend);
        }
    }

    private void triangle(float[] x, float[] y, float[] z, float[] u, float[] v, int a, int b, int c,
                          Texture texture, int tint, float shade, Blend blend) {
        float area = (x[b] - x[a]) * (y[c] - y[a]) - (x[c] - x[a]) * (y[b] - y[a]);
        if (Math.abs(area) < 1.0e-9f) {
            return; // a quad that is really a triangle repeats its last vertex
        }
        int minX = Math.max(0, (int) Math.floor(Math.min(x[a], Math.min(x[b], x[c]))));
        int maxX = Math.min(size - 1, (int) Math.ceil(Math.max(x[a], Math.max(x[b], x[c]))));
        int minY = Math.max(0, (int) Math.floor(Math.min(y[a], Math.min(y[b], y[c]))));
        int maxY = Math.min(size - 1, (int) Math.ceil(Math.max(y[a], Math.max(y[b], y[c]))));
        if (minX > maxX || minY > maxY) {
            return;
        }
        float inv = 1f / area;
        int tw = texture.width(), th = texture.height();
        int[] tex = texture.argb();
        float tr = ((tint >> 16) & 0xFF) / 255f * shade;
        float tg = ((tint >> 8) & 0xFF) / 255f * shade;
        float tb = (tint & 0xFF) / 255f * shade;
        for (int py = minY; py <= maxY; py++) {
            float cy = py + 0.5f;
            for (int px = minX; px <= maxX; px++) {
                float cx = px + 0.5f;
                // Barycentric weights; the same sign as the area means inside.
                float w0 = ((x[b] - cx) * (y[c] - cy) - (x[c] - cx) * (y[b] - cy)) * inv;
                float w1 = ((x[c] - cx) * (y[a] - cy) - (x[a] - cx) * (y[c] - cy)) * inv;
                float w2 = 1f - w0 - w1;
                if (w0 < 0f || w1 < 0f || w2 < 0f) {
                    continue;
                }
                int index = py * size + px;
                float pz = w0 * z[a] + w1 * z[b] + w2 * z[c];
                if (pz >= depth[index]) {
                    continue;
                }
                float tu = w0 * u[a] + w1 * u[b] + w2 * u[c];
                float tv = w0 * v[a] + w1 * v[b] + w2 * v[c];
                int tx = Math.min(tw - 1, Math.max(0, (int) Math.floor(tu * tw)));
                int ty = Math.min(th - 1, Math.max(0, (int) Math.floor(tv * th)));
                int texel = tex[ty * tw + tx];
                int alpha = texel >>> 24;
                if (alpha < CUTOUT_ALPHA) {
                    continue;
                }
                int r = Math.min(255, (int) (((texel >> 16) & 0xFF) * tr));
                int g = Math.min(255, (int) (((texel >> 8) & 0xFF) * tg));
                int bl = Math.min(255, (int) ((texel & 0xFF) * tb));
                int under = pixels[index];
                if (blend == Blend.EMISSIVE) {
                    // Added on top, like vanilla's glowing ("eyes") render type.
                    float k = alpha / 255f;
                    pixels[index] = (Math.max(alpha, under >>> 24) << 24)
                            | (Math.min(255, ((under >> 16) & 0xFF) + (int) (r * k)) << 16)
                            | (Math.min(255, ((under >> 8) & 0xFF) + (int) (g * k)) << 8)
                            | Math.min(255, (under & 0xFF) + (int) (bl * k));
                } else if (blend == Blend.TRANSLUCENT && alpha < 250) {
                    pixels[index] = over(r, g, bl, alpha, under);
                } else {
                    pixels[index] = 0xFF000000 | (r << 16) | (g << 8) | bl;
                    depth[index] = pz;
                }
            }
        }
    }

    /** A colour with alpha drawn over another (ARGB). */
    static int over(int r, int g, int b, int alpha, int under) {
        int ua = under >>> 24;
        float a = alpha / 255f;
        float keep = ua / 255f * (1f - a);
        float outA = a + keep;
        if (outA <= 0f) {
            return 0;
        }
        int outR = (int) ((r * a + ((under >> 16) & 0xFF) * keep) / outA);
        int outG = (int) ((g * a + ((under >> 8) & 0xFF) * keep) / outA);
        int outB = (int) ((b * a + (under & 0xFF) * keep) / outA);
        return (Math.round(outA * 255f) << 24) | (outR << 16) | (outG << 8) | outB;
    }

    /**
     * Grows {@code bounds} (min x, y, z, max x, y, z) by the mesh's transformed vertices; returns it. For framing:
     * {@code m} as in {@link #draw}.
     */
    public static float[] bounds(BakedMesh mesh, float[] m, float[] bounds) {
        float[] d = mesh.data();
        for (int i = 0; i < d.length; i += BakedMesh.FLOATS_PER_VERTEX) {
            float x = d[i], y = d[i + 1], z = d[i + 2];
            float tx = m[0] * x + m[3] * y + m[6] * z + m[9];
            float ty = m[1] * x + m[4] * y + m[7] * z + m[10];
            float tz = m[2] * x + m[5] * y + m[8] * z + m[11];
            bounds[0] = Math.min(bounds[0], tx);
            bounds[1] = Math.min(bounds[1], ty);
            bounds[2] = Math.min(bounds[2], tz);
            bounds[3] = Math.max(bounds[3], tx);
            bounds[4] = Math.max(bounds[4], ty);
            bounds[5] = Math.max(bounds[5], tz);
        }
        return bounds;
    }

    /** Bounds that grow from nothing. */
    public static float[] emptyBounds() {
        return new float[]{Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY,
                Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY};
    }
}
