package io.github.georgwav.cosmetics.model;

/**
 * The part of a skeletal mesh whose vertices are weighted to more than one joint, so no single joint
 * transform can carry it. Each frame it is skinned on the CPU into a buffer made once at load, which is
 * then drawn like any smooth-shaded mesh. Nothing is allocated per frame.
 *
 * <p>Render thread only: the output buffer is shared, so it must be drawn before the next {@link #update}.
 */
public final class SkinnedMesh {
    private final MeshRig rig;
    private final int[] weightStart;
    private final int[] weightCount;
    private final int[] weightJoint;
    private final float[] weightBias;
    /** Per weight: the vertex position in the joint's space. */
    private final float[] weightPos;
    /** Per weight: the vertex normal in the joint's space. */
    private final float[] weightNormal;
    /** Per output vertex (four per quad): which source vertex it shows. */
    private final int[] slotVertex;
    /** Skinned position and normal per source vertex. */
    private final float[] skinned;
    private final float[] scratch = new float[3];
    private final BakedMesh output;
    private int outputClip = -1;
    private float outputTime = Float.NaN;

    SkinnedMesh(MeshRig rig, int[] weightStart, int[] weightCount, int[] weightJoint, float[] weightBias,
                float[] weightPos, float[] weightNormal, int[] slotVertex, BakedMesh output) {
        this.rig = rig;
        this.weightStart = weightStart;
        this.weightCount = weightCount;
        this.weightJoint = weightJoint;
        this.weightBias = weightBias;
        this.weightPos = weightPos;
        this.weightNormal = weightNormal;
        this.slotVertex = slotVertex;
        this.skinned = new float[weightStart.length * 6];
        this.output = output;
    }

    public MeshRig rig() {
        return rig;
    }

    public int vertexCount() {
        return weightStart.length;
    }

    /**
     * Skins the mesh for a clip at {@code clipTime} and returns the buffer to draw (positions in the mesh
     * file's units, per-vertex normals, UVs as baked). Skinning the same moment twice in a row does the
     * work once.
     */
    public BakedMesh update(int clip, float clipTime) {
        if (clip == outputClip && clipTime == outputTime) {
            return output;
        }
        outputClip = clip;
        outputTime = clipTime;
        float[] joints = rig.evaluate(clip, clipTime);
        float[] s = scratch;
        for (int v = 0; v < weightStart.length; v++) {
            float px = 0, py = 0, pz = 0, nx = 0, ny = 0, nz = 0;
            for (int w = weightStart[v], end = w + weightCount[v]; w < end; w++) {
                int j = weightJoint[w] * MeshRig.STRIDE;
                float bias = weightBias[w];
                Md5Parser.rotate(joints, j + 3, weightPos[w * 3], weightPos[w * 3 + 1], weightPos[w * 3 + 2], s, 0);
                px += (joints[j] + s[0]) * bias;
                py += (joints[j + 1] + s[1]) * bias;
                pz += (joints[j + 2] + s[2]) * bias;
                Md5Parser.rotate(joints, j + 3, weightNormal[w * 3], weightNormal[w * 3 + 1], weightNormal[w * 3 + 2], s, 0);
                nx += s[0] * bias;
                ny += s[1] * bias;
                nz += s[2] * bias;
            }
            float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (length > 1.0e-6f) {
                nx /= length;
                ny /= length;
                nz /= length;
            }
            int o = v * 6;
            skinned[o] = px;
            skinned[o + 1] = py;
            skinned[o + 2] = pz;
            skinned[o + 3] = nx;
            skinned[o + 4] = ny;
            skinned[o + 5] = nz;
        }
        float[] d = output.data();
        for (int slot = 0, o = 0; slot < slotVertex.length; slot++, o += BakedMesh.FLOATS_PER_VERTEX) {
            int k = slotVertex[slot] * 6;
            d[o] = skinned[k];
            d[o + 1] = skinned[k + 1];
            d[o + 2] = skinned[k + 2];
            d[o + 5] = skinned[k + 3];
            d[o + 6] = skinned[k + 4];
            d[o + 7] = skinned[k + 5];
        }
        return output;
    }
}
