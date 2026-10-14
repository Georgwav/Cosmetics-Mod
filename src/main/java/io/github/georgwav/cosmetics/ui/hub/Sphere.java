package io.github.georgwav.cosmetics.ui.hub;

import java.util.List;
import java.util.Map;

/**
 * A sphere of orbs: points spread evenly over it, the usable orbs on the points nearest their group's direction and
 * filler on the rest. Also the quaternion maths that turns it; quaternions are {w, x, y, z}, and screen axes are
 * x right, y down, z towards you.
 */
final class Sphere {
    final List<HubItem> items;
    /** The point each item sits on. */
    final int[] itemPoint;
    /** Each point's direction from the middle. */
    final float[][] dirs;
    /** The item on each point, or -1 for filler. */
    final int[] owner;
    /** Each filler orb's size (design pixels). */
    final float[] size;
    final float radius;

    Sphere(List<HubItem> items, Map<String, float[]> groups, int count, float twist, float radius) {
        this.items = items;
        this.radius = radius;
        dirs = new float[count][];
        owner = new int[count];
        size = new float[count];
        for (int k = 0; k < count; k++) {
            float y = 1f - (2f * (k + 0.5f)) / count;
            float r = (float) Math.sqrt(1f - y * y);
            double phi = k * 2.39996323 + twist;
            dirs[k] = new float[]{(float) Math.cos(phi) * r, y, (float) Math.sin(phi) * r};
            owner[k] = -1;
            size[k] = 28 + (k * 37) % 13;
        }
        itemPoint = new int[items.size()];
        for (int i = 0; i < items.size(); i++) {
            float[] c = unit(groups.get(items.get(i).group));
            int best = -1;
            float bestDot = -2f;
            for (int j = 0; j < count; j++) {
                float d = dirs[j][0] * c[0] + dirs[j][1] * c[1] + dirs[j][2] * c[2];
                if (owner[j] < 0 && d > bestDot) {
                    bestDot = d;
                    best = j;
                }
            }
            owner[best] = i;
            itemPoint[i] = best;
        }
    }

    static float[] unit(float[] v) {
        float l = (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        return new float[]{v[0] / l, v[1] / l, v[2] / l};
    }

    static float[] qmul(float[] a, float[] b) {
        return new float[]{
                a[0] * b[0] - a[1] * b[1] - a[2] * b[2] - a[3] * b[3],
                a[0] * b[1] + a[1] * b[0] + a[2] * b[3] - a[3] * b[2],
                a[0] * b[2] - a[1] * b[3] + a[2] * b[0] + a[3] * b[1],
                a[0] * b[3] + a[1] * b[2] - a[2] * b[1] + a[3] * b[0]
        };
    }

    static float[] qaxis(float x, float y, float z, float angle) {
        float s = (float) Math.sin(angle / 2);
        return new float[]{(float) Math.cos(angle / 2), x * s, y * s, z * s};
    }

    static float[] qnorm(float[] q) {
        float l = (float) Math.sqrt(q[0] * q[0] + q[1] * q[1] + q[2] * q[2] + q[3] * q[3]);
        if (l == 0f) {
            return new float[]{1f, 0f, 0f, 0f};
        }
        return new float[]{q[0] / l, q[1] / l, q[2] / l, q[3] / l};
    }

    /** v turned by q. */
    static float[] qrot(float[] q, float[] v) {
        float w = q[0], x = q[1], y = q[2], z = q[3];
        float tx = 2 * (y * v[2] - z * v[1]);
        float ty = 2 * (z * v[0] - x * v[2]);
        float tz = 2 * (x * v[1] - y * v[0]);
        return new float[]{v[0] + w * tx + (y * tz - z * ty), v[1] + w * ty + (z * tx - x * tz), v[2] + w * tz + (x * ty - y * tx)};
    }

    /** From a to b; also past t = 1, so a spring's overshoot turns a little beyond and back. */
    static float[] slerp(float[] a, float[] b, float t) {
        float d = a[0] * b[0] + a[1] * b[1] + a[2] * b[2] + a[3] * b[3];
        float[] c = b;
        if (d < 0) {
            d = -d;
            c = new float[]{-b[0], -b[1], -b[2], -b[3]};
        }
        if (d > 0.9995f) {
            return qnorm(new float[]{a[0] + (c[0] - a[0]) * t, a[1] + (c[1] - a[1]) * t, a[2] + (c[2] - a[2]) * t,
                    a[3] + (c[3] - a[3]) * t});
        }
        float th = (float) Math.acos(d);
        float s = (float) Math.sin(th);
        float ka = (float) Math.sin((1 - t) * th) / s;
        float kb = (float) Math.sin(t * th) / s;
        return qnorm(new float[]{a[0] * ka + c[0] * kb, a[1] * ka + c[1] * kb, a[2] * ka + c[2] * kb, a[3] * ka + c[3] * kb});
    }

    /** The orientation that brings item i from where q has it to the middle (facing you) by the shortest turn. */
    float[] turnedTo(float[] q, int i) {
        float[] w = qrot(q, dirs[itemPoint[i]]);
        float ax = w[1];
        float ay = -w[0]; // w x (0, 0, 1)
        float len = (float) Math.sqrt(ax * ax + ay * ay);
        float dot = Math.max(-1f, Math.min(1f, w[2]));
        if (len < 1e-6f) {
            if (dot > 0) {
                return q;
            }
            ax = 1f;
            ay = 0f;
        } else {
            ax /= len;
            ay /= len;
        }
        return qnorm(qmul(qaxis(ax, ay, 0f, (float) Math.acos(dot)), q));
    }

    /** The usable orb nearest the middle; filler is skipped. */
    int nearest(float[] q) {
        int best = 0;
        float bestZ = -2f;
        for (int i = 0; i < items.size(); i++) {
            float z = qrot(q, dirs[itemPoint[i]])[2];
            if (z > bestZ) {
                bestZ = z;
                best = i;
            }
        }
        return best;
    }
}
