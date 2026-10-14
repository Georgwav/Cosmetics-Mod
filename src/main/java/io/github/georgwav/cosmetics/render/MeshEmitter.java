package io.github.georgwav.cosmetics.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.georgwav.cosmetics.compat.Compat;
import io.github.georgwav.cosmetics.model.BakedCosmetic;
import io.github.georgwav.cosmetics.model.BakedMesh;
import io.github.georgwav.cosmetics.model.Cloth;
import io.github.georgwav.cosmetics.model.SkinnedMesh;

/**
 * Pushes a {@link BakedMesh} into a vanilla {@link VertexConsumer}. This is the only place vertices
 * are written: no custom GL buffers, no GL state, no wrapping of the consumer (Sodium requires the
 * consumer it handed out to be used directly).
 *
 * <p>Without a shader pack each vertex goes in with the one-call {@code addVertex} that vanilla's own model parts
 * use, which the buffer writes straight to memory for the entity vertex format; see {@link #vertex}.
 */
public final class MeshEmitter {
    /** {@code LightTexture.FULL_BRIGHT}; a literal because the light class was renamed in 26.x. */
    public static final int FULL_BRIGHT = 0xF000F0;
    /** {@code OverlayTexture.NO_OVERLAY}. */
    public static final int NO_OVERLAY = 10 << 16;

    private MeshEmitter() {
    }

    /**
     * Emits all of a layer's geometry: the main mesh, then the smooth-shaded mesh if it has one, or for a
     * skinned layer its geometry skinned for {@code clip} at {@code clipTime}. {@code vOffset} is non-zero
     * only while a texture animated as a vertical strip shows a later frame. With {@code cloth} (a cloth cloak),
     * the layer's geometry is drawn bent along that shape instead.
     */
    public static void emitLayer(BakedCosmetic.Layer layer, float vOffset, int clip, float clipTime, PoseStack.Pose pose,
                                 VertexConsumer consumer, int light, int color, Cloth.Shape cloth) {
        boolean oneCall = !Compat.isShaderPackActive();
        SkinnedMesh skin = layer.skin();
        if (cloth != null && skin == null) {
            emitCloth(layer.clothMesh().mesh(), cloth, pose, consumer, light, color, vOffset, oneCall);
            Cloth.Mesh smooth = layer.clothSmoothMesh();
            if (smooth != null) {
                emitCloth(smooth.mesh(), cloth, pose, consumer, light, color, vOffset, oneCall);
            }
            return;
        }
        if (skin != null) {
            BakedMesh skinned = skin.update(clip, clipTime);
            if (vOffset == 0f) {
                emitSmooth(skinned, pose, consumer, light, color, oneCall);
            } else {
                emitFrame(skinned, pose, consumer, light, color, vOffset, oneCall);
            }
            return;
        }
        BakedMesh smooth = layer.smoothMesh();
        if (vOffset == 0f) {
            emit(layer.mesh(), pose, consumer, light, color, oneCall);
            if (smooth != null) {
                emitSmooth(smooth, pose, consumer, light, color, oneCall);
            }
        } else {
            emitFrame(layer.mesh(), pose, consumer, light, color, vOffset, oneCall);
            if (smooth != null) {
                emitFrame(smooth, pose, consumer, light, color, vOffset, oneCall);
            }
        }
    }

    /**
     * One vertex. Without a shader pack it goes in with the one-call {@code addVertex}, which the vanilla buffer
     * writes straight to memory. With one, Iris's wider vertex format takes that call slower than the
     * element-by-element writes (the benchmark measured about 10% more time), so those stay.
     */
    private static void vertex(VertexConsumer consumer, boolean oneCall, float x, float y, float z, int color, float u,
                               float v, int light, float nx, float ny, float nz) {
        if (oneCall) {
            consumer.addVertex(x, y, z, color, u, v, NO_OVERLAY, light, nx, ny, nz);
        } else {
            consumer.addVertex(x, y, z)
                    .setColor(color)
                    .setUv(u, v)
                    .setOverlay(NO_OVERLAY)
                    .setLight(light)
                    .setNormal(nx, ny, nz);
        }
    }

    public static void emit(BakedMesh mesh, PoseStack.Pose pose, VertexConsumer consumer, int light, int color,
                            boolean oneCall) {
        // `var` because these are Matrix4fc/Matrix3fc on newer versions and Matrix4f/Matrix3f on older ones.
        var m = pose.pose();
        var n = pose.normal();
        float m00 = m.m00(), m01 = m.m01(), m02 = m.m02();
        float m10 = m.m10(), m11 = m.m11(), m12 = m.m12();
        float m20 = m.m20(), m21 = m.m21(), m22 = m.m22();
        float m30 = m.m30(), m31 = m.m31(), m32 = m.m32();
        float n00 = n.m00(), n01 = n.m01(), n02 = n.m02();
        float n10 = n.m10(), n11 = n.m11(), n12 = n.m12();
        float n20 = n.m20(), n21 = n.m21(), n22 = n.m22();

        float[] d = mesh.data();
        int quads = mesh.quadCount();
        int i = 0;
        for (int q = 0; q < quads; q++) {
            // All four vertices of a quad share the normal; transform it once.
            float lnx = d[i + 5], lny = d[i + 6], lnz = d[i + 7];
            float nx = n00 * lnx + n10 * lny + n20 * lnz;
            float ny = n01 * lnx + n11 * lny + n21 * lnz;
            float nz = n02 * lnx + n12 * lny + n22 * lnz;
            float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (len > 1.0e-6f) {
                nx /= len;
                ny /= len;
                nz /= len;
            }
            for (int v = 0; v < BakedMesh.VERTICES_PER_QUAD; v++) {
                float x = d[i], y = d[i + 1], z = d[i + 2];
                vertex(consumer, oneCall,
                        m00 * x + m10 * y + m20 * z + m30,
                        m01 * x + m11 * y + m21 * z + m31,
                        m02 * x + m12 * y + m22 * z + m32,
                        color, d[i + 3], d[i + 4], light, nx, ny, nz);
                i += BakedMesh.FLOATS_PER_VERTEX;
            }
        }
        RenderStats.countVertices(quads * BakedMesh.VERTICES_PER_QUAD);
    }

    /**
     * Like {@link #emit}, for smooth-shaded mesh geometry: every vertex has its own normal, so each one
     * is transformed (instead of one per quad). Only layers with smooth meshes call this; cubes never do.
     */
    public static void emitSmooth(BakedMesh mesh, PoseStack.Pose pose, VertexConsumer consumer, int light, int color,
                                  boolean oneCall) {
        var m = pose.pose();
        var n = pose.normal();
        float m00 = m.m00(), m01 = m.m01(), m02 = m.m02();
        float m10 = m.m10(), m11 = m.m11(), m12 = m.m12();
        float m20 = m.m20(), m21 = m.m21(), m22 = m.m22();
        float m30 = m.m30(), m31 = m.m31(), m32 = m.m32();
        float n00 = n.m00(), n01 = n.m01(), n02 = n.m02();
        float n10 = n.m10(), n11 = n.m11(), n12 = n.m12();
        float n20 = n.m20(), n21 = n.m21(), n22 = n.m22();

        float[] d = mesh.data();
        int vertices = mesh.quadCount() * BakedMesh.VERTICES_PER_QUAD;
        int i = 0;
        for (int v = 0; v < vertices; v++) {
            float x = d[i], y = d[i + 1], z = d[i + 2];
            float lnx = d[i + 5], lny = d[i + 6], lnz = d[i + 7];
            float nx = n00 * lnx + n10 * lny + n20 * lnz;
            float ny = n01 * lnx + n11 * lny + n21 * lnz;
            float nz = n02 * lnx + n12 * lny + n22 * lnz;
            float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (len > 1.0e-6f) {
                nx /= len;
                ny /= len;
                nz /= len;
            }
            vertex(consumer, oneCall,
                    m00 * x + m10 * y + m20 * z + m30,
                    m01 * x + m11 * y + m21 * z + m31,
                    m02 * x + m12 * y + m22 * z + m32,
                    color, d[i + 3], d[i + 4], light, nx, ny, nz);
            i += BakedMesh.FLOATS_PER_VERTEX;
        }
        RenderStats.countVertices(vertices);
    }

    /**
     * A cloth cloak: every vertex (of a mesh split into strips, see {@link Cloth#prepare}) moved onto the bent
     * shape and its normal turned with it, then drawn like {@link #emitFrame}.
     */
    static void emitCloth(BakedMesh mesh, Cloth.Shape shape, PoseStack.Pose pose, VertexConsumer consumer, int light,
                          int color, float vOffset, boolean oneCall) {
        var m = pose.pose();
        var n = pose.normal();
        float m00 = m.m00(), m01 = m.m01(), m02 = m.m02();
        float m10 = m.m10(), m11 = m.m11(), m12 = m.m12();
        float m20 = m.m20(), m21 = m.m21(), m22 = m.m22();
        float m30 = m.m30(), m31 = m.m31(), m32 = m.m32();
        float n00 = n.m00(), n01 = n.m01(), n02 = n.m02();
        float n10 = n.m10(), n11 = n.m11(), n12 = n.m12();
        float n20 = n.m20(), n21 = n.m21(), n22 = n.m22();

        float[] d = mesh.data();
        float[] bent = new float[6];
        int vertices = mesh.quadCount() * BakedMesh.VERTICES_PER_QUAD;
        int i = 0;
        for (int v = 0; v < vertices; v++) {
            Cloth.bend(shape, d[i], d[i + 1], d[i + 2], d[i + 5], d[i + 6], d[i + 7], bent);
            float x = bent[0], y = bent[1], z = bent[2];
            float lnx = bent[3], lny = bent[4], lnz = bent[5];
            float nx = n00 * lnx + n10 * lny + n20 * lnz;
            float ny = n01 * lnx + n11 * lny + n21 * lnz;
            float nz = n02 * lnx + n12 * lny + n22 * lnz;
            float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (len > 1.0e-6f) {
                nx /= len;
                ny /= len;
                nz /= len;
            }
            vertex(consumer, oneCall,
                    m00 * x + m10 * y + m20 * z + m30,
                    m01 * x + m11 * y + m21 * z + m31,
                    m02 * x + m12 * y + m22 * z + m32,
                    color, d[i + 3], d[i + 4] + vOffset, light, nx, ny, nz);
            i += BakedMesh.FLOATS_PER_VERTEX;
        }
        RenderStats.countVertices(vertices);
    }

    /**
     * Like {@link #emitSmooth} with every V moved by {@code vOffset}, to show one frame of a vertical-strip
     * texture animation. Transforming each vertex's own normal is right for flat meshes too (their four
     * normals are equal). Only animated layers ever get here.
     */
    static void emitFrame(BakedMesh mesh, PoseStack.Pose pose, VertexConsumer consumer, int light, int color,
                          float vOffset, boolean oneCall) {
        var m = pose.pose();
        var n = pose.normal();
        float m00 = m.m00(), m01 = m.m01(), m02 = m.m02();
        float m10 = m.m10(), m11 = m.m11(), m12 = m.m12();
        float m20 = m.m20(), m21 = m.m21(), m22 = m.m22();
        float m30 = m.m30(), m31 = m.m31(), m32 = m.m32();
        float n00 = n.m00(), n01 = n.m01(), n02 = n.m02();
        float n10 = n.m10(), n11 = n.m11(), n12 = n.m12();
        float n20 = n.m20(), n21 = n.m21(), n22 = n.m22();

        float[] d = mesh.data();
        int vertices = mesh.quadCount() * BakedMesh.VERTICES_PER_QUAD;
        int i = 0;
        for (int v = 0; v < vertices; v++) {
            float x = d[i], y = d[i + 1], z = d[i + 2];
            float lnx = d[i + 5], lny = d[i + 6], lnz = d[i + 7];
            float nx = n00 * lnx + n10 * lny + n20 * lnz;
            float ny = n01 * lnx + n11 * lny + n21 * lnz;
            float nz = n02 * lnx + n12 * lny + n22 * lnz;
            float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (len > 1.0e-6f) {
                nx /= len;
                ny /= len;
                nz /= len;
            }
            vertex(consumer, oneCall,
                    m00 * x + m10 * y + m20 * z + m30,
                    m01 * x + m11 * y + m21 * z + m31,
                    m02 * x + m12 * y + m22 * z + m32,
                    color, d[i + 3], d[i + 4] + vOffset, light, nx, ny, nz);
            i += BakedMesh.FLOATS_PER_VERTEX;
        }
        RenderStats.countVertices(vertices);
    }
}
