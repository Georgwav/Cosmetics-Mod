package io.github.georgwav.cosmetics.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.georgwav.cosmetics.model.BakedCosmetic;
import io.github.georgwav.cosmetics.model.BakedMesh;
import io.github.georgwav.cosmetics.model.SkinnedMesh;

/**
 * Pushes a {@link BakedMesh} into a vanilla {@link VertexConsumer}. This is the only place vertices
 * are written: no custom GL buffers, no GL state, no wrapping of the consumer (Sodium requires the
 * consumer it handed out to be used directly).
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
     * only while a texture animated as a vertical strip shows a later frame.
     */
    public static void emitLayer(BakedCosmetic.Layer layer, float vOffset, int clip, float clipTime, PoseStack.Pose pose,
                                 VertexConsumer consumer, int light, int color) {
        SkinnedMesh skin = layer.skin();
        if (skin != null) {
            BakedMesh skinned = skin.update(clip, clipTime);
            if (vOffset == 0f) {
                emitSmooth(skinned, pose, consumer, light, color);
            } else {
                emitFrame(skinned, pose, consumer, light, color, vOffset);
            }
            return;
        }
        BakedMesh smooth = layer.smoothMesh();
        if (vOffset == 0f) {
            emit(layer.mesh(), pose, consumer, light, color);
            if (smooth != null) {
                emitSmooth(smooth, pose, consumer, light, color);
            }
        } else {
            emitFrame(layer.mesh(), pose, consumer, light, color, vOffset);
            if (smooth != null) {
                emitFrame(smooth, pose, consumer, light, color, vOffset);
            }
        }
    }

    public static void emit(BakedMesh mesh, PoseStack.Pose pose, VertexConsumer consumer, int light, int color) {
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
                consumer.addVertex(
                                m00 * x + m10 * y + m20 * z + m30,
                                m01 * x + m11 * y + m21 * z + m31,
                                m02 * x + m12 * y + m22 * z + m32)
                        .setColor(color)
                        .setUv(d[i + 3], d[i + 4])
                        .setOverlay(NO_OVERLAY)
                        .setLight(light)
                        .setNormal(nx, ny, nz);
                i += BakedMesh.FLOATS_PER_VERTEX;
            }
        }
    }

    /**
     * Like {@link #emit}, for smooth-shaded mesh geometry: every vertex has its own normal, so each one
     * is transformed (instead of one per quad). Only layers with smooth meshes call this; cubes never do.
     */
    public static void emitSmooth(BakedMesh mesh, PoseStack.Pose pose, VertexConsumer consumer, int light, int color) {
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
            consumer.addVertex(
                            m00 * x + m10 * y + m20 * z + m30,
                            m01 * x + m11 * y + m21 * z + m31,
                            m02 * x + m12 * y + m22 * z + m32)
                    .setColor(color)
                    .setUv(d[i + 3], d[i + 4])
                    .setOverlay(NO_OVERLAY)
                    .setLight(light)
                    .setNormal(nx, ny, nz);
            i += BakedMesh.FLOATS_PER_VERTEX;
        }
    }

    /**
     * Like {@link #emitSmooth} with every V moved by {@code vOffset}, to show one frame of a vertical-strip
     * texture animation. Transforming each vertex's own normal is right for flat meshes too (their four
     * normals are equal). Only animated layers ever get here.
     */
    static void emitFrame(BakedMesh mesh, PoseStack.Pose pose, VertexConsumer consumer, int light, int color,
                          float vOffset) {
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
            consumer.addVertex(
                            m00 * x + m10 * y + m20 * z + m30,
                            m01 * x + m11 * y + m21 * z + m31,
                            m02 * x + m12 * y + m22 * z + m32)
                    .setColor(color)
                    .setUv(d[i + 3], d[i + 4] + vOffset)
                    .setOverlay(NO_OVERLAY)
                    .setLight(light)
                    .setNormal(nx, ny, nz);
            i += BakedMesh.FLOATS_PER_VERTEX;
        }
    }
}
