package io.github.georgwav.cosmetics.sol.fx;

import io.github.georgwav.cosmetics.sol.util.Colors;

/**
 * The effects' light and matter, drawn from {@link FxSoft}'s pictures: soft glows and hot cores (added, so they bloom),
 * lens flares, sparks stretched along their motion, glowing lines and beams, rings lying on the ground, decals and
 * pools of light on the terrain, and the pieces of matter (smoke, shards, shapes) that are blended over the world.
 * Every effect is built from these. Positions are blocks from the origin set by {@link #origin}. Glows draw only in
 * the glow pass; everything else draws in whichever pass it's asked in, so an effect decides what adds light and what
 * covers. Render thread only; nothing allocated.
 */
public final class Vfx {
    /** The camera's right and up, its position relative to the origin, and the origin's world position. */
    private static final float[] R = new float[3], U = new float[3], E = new float[3];
    private static double ox, oy, oz;

    private Vfx() {
    }

    /** Sets the origin to the effect's place and reads the camera; once per effect per pass. */
    public static void origin(FxOut out, Fx fx) {
        origin(out, fx.x, fx.y, fx.z);
    }

    public static void origin(FxOut out, double x, double y, double z) {
        ox = x;
        oy = y;
        oz = z;
        current = out;
        out.origin(x, y, z);
        out.right(R);
        out.up(U);
        out.eye(E);
    }

    // ------------------------------------------------------------------ light (glow pass)

    /** A soft glow of {@code radius} blocks (glow pass). */
    public static void glow(FxOut out, float x, float y, float z, float radius, int argb) {
        if (out.glowPass()) {
            billboard(out, FxSoft.GLOW, x, y, z, radius, radius, 0f, argb);
        }
    }

    /** A hot core: a bright point in a soft halo (glow pass). */
    public static void hot(FxOut out, float x, float y, float z, float radius, int argb) {
        if (out.glowPass()) {
            billboard(out, FxSoft.HOT, x, y, z, radius, radius, 0f, argb);
        }
    }

    /** A four-point lens flare turned by {@code angle} radians (glow pass). */
    public static void flare(FxOut out, float x, float y, float z, float radius, float angle, int argb) {
        if (out.glowPass()) {
            billboard(out, FxSoft.FLARE, x, y, z, radius, radius, angle, argb);
        }
    }

    /** A six-point star glint (glow pass). */
    public static void glint(FxOut out, float x, float y, float z, float radius, float angle, int argb) {
        if (out.glowPass()) {
            billboard(out, FxSoft.STAR6, x, y, z, radius, radius, angle, argb);
        }
    }

    /**
     * A pool of light on the ground round (x, y, z): the effect lighting the blocks it's over (glow pass). It follows
     * the terrain within a block up or down.
     */
    public static void pool(FxOut out, float x, float y, float z, float radius, int argb) {
        if (out.glowPass() && Colors.a(argb) > 0) {
            decalGround(out, FxSoft.GLOW, x, y, z, radius, 0f, argb);
        }
    }

    // ------------------------------------------------------------------ pictures facing the camera

    /** A picture facing the camera, half sizes {@code hw} and {@code hh}, turned {@code angle} radians. */
    public static void billboard(FxOut out, FxSoft.Region r, float x, float y, float z, float hw, float hh, float angle, int argb) {
        if (Colors.a(argb) == 0 || hw <= 0f || hh <= 0f) {
            return;
        }
        out.use(FxSoft.atlas());
        float c = 1f, s = 0f;
        if (angle != 0f) {
            c = (float) Math.cos(angle);
            s = (float) Math.sin(angle);
        }
        float rx = (R[0] * c + U[0] * s) * hw, ry = (R[1] * c + U[1] * s) * hw, rz = (R[2] * c + U[2] * s) * hw;
        float ux = (U[0] * c - R[0] * s) * hh, uy = (U[1] * c - R[1] * s) * hh, uz = (U[2] * c - R[2] * s) * hh;
        out.vertex(x - rx - ux, y - ry - uy, z - rz - uz, r.u0, r.v1, argb);
        out.vertex(x - rx + ux, y - ry + uy, z - rz + uz, r.u0, r.v0, argb);
        out.vertex(x + rx + ux, y + ry + uy, z + rz + uz, r.u1, r.v0, argb);
        out.vertex(x + rx - ux, y + ry - uy, z + rz - uz, r.u1, r.v1, argb);
    }

    /** {@link #billboard} with one size. */
    public static void sprite(FxOut out, FxSoft.Region r, float x, float y, float z, float half, float angle, int argb) {
        billboard(out, r, x, y, z, half, half, angle, argb);
    }

    /**
     * A spark: a streak from {@code length} blocks behind (x, y, z) along (dx, dy, dz) up to it, {@code width} wide, its
     * bright head at the point. The direction needn't be unit length.
     */
    public static void streak(FxOut out, float x, float y, float z, float dx, float dy, float dz, float length, float width, int argb) {
        float l = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (l < 1e-5f || Colors.a(argb) == 0) {
            billboard(out, FxSoft.DOT_SOFT, x, y, z, width, width, 0f, argb);
            return;
        }
        dx /= l;
        dy /= l;
        dz /= l;
        float head = width * 0.5f;
        quadAlong(out, FxSoft.SPARK, x - dx * length, y - dy * length, z - dz * length, x + dx * head, y + dy * head, z + dz * head,
                width, argb, argb, true);
    }

    /**
     * A glowing line from (x0, y0, z0) to (x1, y1, z1), {@code width} wide, across the {@code profile} (a soft line, a
     * neon tube, a front), its colour going from {@code c0} to {@code c1}. It turns to face the camera round its length.
     */
    public static void line(FxOut out, FxSoft.Region profile, float x0, float y0, float z0, float x1, float y1, float z1, float width,
                            int c0, int c1) {
        if (Colors.a(c0) == 0 && Colors.a(c1) == 0) {
            return;
        }
        quadAlong(out, profile, x0, y0, z0, x1, y1, z1, width, c0, c1, false);
    }

    private static void quadAlong(FxOut out, FxSoft.Region r, float x0, float y0, float z0, float x1, float y1, float z1, float width,
                                  int c0, int c1, boolean alongU) {
        float dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
        // To the camera from the middle.
        float vx = (x0 + x1) * 0.5f - E[0], vy = (y0 + y1) * 0.5f - E[1], vz = (z0 + z1) * 0.5f - E[2];
        float sx = dy * vz - dz * vy, sy = dz * vx - dx * vz, sz = dx * vy - dy * vx;
        float sl = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (sl < 1e-6f) {
            sx = R[0];
            sy = R[1];
            sz = R[2];
            sl = 1f;
        }
        float h = width * 0.5f / sl;
        sx *= h;
        sy *= h;
        sz *= h;
        out.use(FxSoft.atlas());
        if (alongU) {
            // u runs along the line (tail 0, head 1), v across.
            out.vertex(x0 - sx, y0 - sy, z0 - sz, r.u0, r.v0, c0);
            out.vertex(x0 + sx, y0 + sy, z0 + sz, r.u0, r.v1, c0);
            out.vertex(x1 + sx, y1 + sy, z1 + sz, r.u1, r.v1, c1);
            out.vertex(x1 - sx, y1 - sy, z1 - sz, r.u1, r.v0, c1);
        } else {
            // u runs across the line (the profile), v along it.
            out.vertex(x0 - sx, y0 - sy, z0 - sz, r.u0, r.v0, c0);
            out.vertex(x0 + sx, y0 + sy, z0 + sz, r.u1, r.v0, c0);
            out.vertex(x1 + sx, y1 + sy, z1 + sz, r.u1, r.v1, c1);
            out.vertex(x1 - sx, y1 - sy, z1 - sz, r.u0, r.v1, c1);
        }
    }

    /**
     * A strip of the flowing noise from (x0, y0, z0) to (x1, y1, z1), {@code width} wide, the noise slid {@code scroll}
     * (0..1, wrapping) along it: energy pouring down a beam.
     */
    public static void flow(FxOut out, float x0, float y0, float z0, float x1, float y1, float z1, float width, float scroll, int argb) {
        if (Colors.a(argb) == 0) {
            return;
        }
        float dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
        float vx = (x0 + x1) * 0.5f - E[0], vy = (y0 + y1) * 0.5f - E[1], vz = (z0 + z1) * 0.5f - E[2];
        float sx = dy * vz - dz * vy, sy = dz * vx - dx * vz, sz = dx * vy - dy * vx;
        float sl = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (sl < 1e-6f) {
            sx = R[0];
            sy = R[1];
            sz = R[2];
            sl = 1f;
        }
        float h = width * 0.5f / sl;
        sx *= h;
        sy *= h;
        sz *= h;
        FxSoft.Region r = FxSoft.NOISE;
        float half = (r.u1 - r.u0) * 0.5f;
        float s = scroll - (float) Math.floor(scroll);
        float ua = r.u0 + s * half, ub = ua + half;
        out.use(FxSoft.atlas());
        out.vertex(x0 - sx, y0 - sy, z0 - sz, ua, r.v0, argb);
        out.vertex(x0 + sx, y0 + sy, z0 + sz, ua, r.v1, argb);
        out.vertex(x1 + sx, y1 + sy, z1 + sz, ub, r.v1, argb);
        out.vertex(x1 - sx, y1 - sy, z1 - sz, ub, r.v0, argb);
    }

    // ------------------------------------------------------------------ solid shapes

    /**
     * A cut gem at (x, y, z): a bipyramid of {@code sides} facets round (four is the Sims' plumbob), {@code radius}
     * round its middle, its points {@code up} above and {@code down} below, turned {@code spin} radians. Over the world
     * (body pass) its facets are lit from above and catch a highlight toward the camera; as light (glow pass) it glows
     * from within and its edges shine.
     */
    public static void gem(FxOut out, float x, float y, float z, float radius, float up, float down, int sides, float spin, int argb) {
        float a = Colors.a(argb) / 255f;
        if (a <= 0f || radius <= 0f) {
            return;
        }
        boolean glow = out.glowPass();
        out.use(FxSoft.atlas());
        FxSoft.Region w = FxSoft.SQUARE;
        float u = (w.u0 + w.u1) * 0.5f, v = (w.v0 + w.v1) * 0.5f;
        int dark = mix(argb, 0xFF000000, 0.5f), light = white(argb, 0.3f);
        for (int i = 0; i < sides; i++) {
            double a0 = spin + i * 2 * Math.PI / sides, a1 = spin + (i + 1) * 2 * Math.PI / sides;
            float x0 = (float) Math.cos(a0) * radius, z0 = (float) Math.sin(a0) * radius;
            float x1 = (float) Math.cos(a1) * radius, z1 = (float) Math.sin(a1) * radius;
            for (int half = 0; half < 2; half++) {
                float ty = half == 0 ? up : -down;
                // The facet's normal, outward.
                float ex = x0, ey = -ty, ez = z0, fx = x1, fy = -ty, fz = z1;
                float nx = ey * fz - ez * fy, ny = ez * fx - ex * fz, nz = ex * fy - ey * fx;
                float cx = (x0 + x1) / 3f, cy = ty / 3f, cz = (z0 + z1) / 3f;
                if (nx * cx + ny * cy + nz * cz < 0f) {
                    nx = -nx;
                    ny = -ny;
                    nz = -nz;
                }
                float nl = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                nx /= nl;
                ny /= nl;
                nz /= nl;
                float vx = E[0] - (x + cx), vy = E[1] - (y + cy), vz = E[2] - (z + cz);
                float vl = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
                float facing = (nx * vx + ny * vy + nz * vz) / Math.max(1e-5f, vl);
                if (facing <= 0f) {
                    continue;
                }
                int col;
                if (glow) {
                    // Brightest where it's seen edge-on: light caught in the cut.
                    col = with(argb, a * (0.25f + 0.35f * (1f - facing)));
                } else {
                    float diffuse = Math.max(0f, nx * 0.38f + ny * 0.82f + nz * 0.43f);
                    float hx = 0.38f + vx / vl, hy = 0.82f + vy / vl, hz = 0.43f + vz / vl;
                    float hl = (float) Math.sqrt(hx * hx + hy * hy + hz * hz);
                    float spec = (float) Math.pow(Math.max(0f, (nx * hx + ny * hy + nz * hz) / hl), 18f);
                    int c = mix(dark, light, 0.25f + 0.75f * diffuse);
                    c = mix(c, 0xFFFFFFFF, Math.min(1f, spec * 0.85f + 0.25f * (1f - facing)));
                    col = with(c, a * 0.94f);
                }
                out.vertex(x, y + ty, z, u, v, col);
                out.vertex(x + x0, y, z + z0, u, v, col);
                out.vertex(x + x1, y, z + z1, u, v, col);
                out.vertex(x + x1, y, z + z1, u, v, col);
            }
        }
        if (glow) {
            int edge = with(white(argb, 0.55f), a * 0.7f);
            float ew = radius * 0.12f;
            for (int i = 0; i < sides; i++) {
                double a0 = spin + i * 2 * Math.PI / sides, a1 = spin + (i + 1) * 2 * Math.PI / sides;
                float x0 = x + (float) Math.cos(a0) * radius, z0 = z + (float) Math.sin(a0) * radius;
                float x1 = x + (float) Math.cos(a1) * radius, z1 = z + (float) Math.sin(a1) * radius;
                line(out, FxSoft.TUBE, x, y + up, z, x0, y, z0, ew, edge, edge);
                line(out, FxSoft.TUBE, x, y - down, z, x0, y, z0, ew, edge, edge);
                line(out, FxSoft.TUBE, x0, y, z0, x1, y, z1, ew, edge, edge);
            }
        }
    }

    // ------------------------------------------------------------------ lying on the ground

    /**
     * An arc of a ring lying flat round (cx, cy, cz), {@code radius} out and {@code width} wide, from angle {@code a0} to
     * {@code a1} (radians), across the {@code profile} (inner edge at u0). With {@code terrain} each point sits on the
     * ground under it (within a block of {@code cy}).
     */
    public static void arc(FxOut out, FxSoft.Region profile, float cx, float cy, float cz, float radius, float width, float a0, float a1,
                           int argb, boolean terrain) {
        if (radius <= 0f || width <= 0f || Colors.a(argb) == 0) {
            return;
        }
        out.use(FxSoft.atlas());
        float span = a1 - a0;
        int n = Math.max(6, Math.min(160, (int) Math.ceil(Math.abs(span) * Math.max(1f, radius) * 10f)));
        float rin = Math.max(0f, radius - width * 0.5f), rout = radius + width * 0.5f;
        float lift = terrain ? Math.max(0.02f, cy - groundAt(cx, cy, cz)) : 0f;
        float pc = 1f, ps = 0f, py0 = cy, py1 = cy;
        for (int i = 0; i <= n; i++) {
            float a = a0 + span * i / n;
            float c = (float) Math.cos(a), s = (float) Math.sin(a);
            float y0 = cy, y1 = cy;
            if (terrain) {
                // A little over the ground under each point, as far over it as the ring was asked to be.
                y0 = groundAt(cx + c * rin, cy, cz + s * rin) + lift;
                y1 = groundAt(cx + c * rout, cy, cz + s * rout) + lift;
            }
            if (i > 0) {
                out.vertex(cx + pc * rin, py0, cz + ps * rin, profile.u0, profile.v0, argb);
                out.vertex(cx + pc * rout, py1, cz + ps * rout, profile.u1, profile.v0, argb);
                out.vertex(cx + c * rout, y1, cz + s * rout, profile.u1, profile.v1, argb);
                out.vertex(cx + c * rin, y0, cz + s * rin, profile.u0, profile.v1, argb);
            }
            pc = c;
            ps = s;
            py0 = y0;
            py1 = y1;
        }
    }

    /** A whole ring lying flat (see {@link #arc}). */
    public static void ring(FxOut out, FxSoft.Region profile, float cx, float cy, float cz, float radius, float width, int argb,
                            boolean terrain) {
        arc(out, profile, cx, cy, cz, radius, width, 0f, (float) (Math.PI * 2), argb, terrain);
    }

    /**
     * A shape of {@code sides} straight sides lying round (cx, cy, cz) on the ground (see {@link #arc}): its corners
     * {@code radius} out, turned {@code turn} radians, {@code width} wide across the {@code profile}.
     */
    public static void poly(FxOut out, FxSoft.Region profile, float cx, float cy, float cz, int sides, float radius, float width, float turn,
                            int argb, boolean terrain) {
        if (radius <= 0f || width <= 0f || Colors.a(argb) == 0 || sides < 3) {
            return;
        }
        out.use(FxSoft.atlas());
        float rin = Math.max(0f, radius - width * 0.5f), rout = radius + width * 0.5f;
        float lift = terrain ? Math.max(0.02f, cy - groundAt(cx, cy, cz)) : 0f;
        float side = (float) (2 * radius * Math.sin(Math.PI / sides));
        int m = Math.max(2, Math.min(24, (int) Math.ceil(side * 3f)));
        for (int s = 0; s < sides; s++) {
            double a0 = turn + s * 2 * Math.PI / sides, a1 = turn + (s + 1) * 2 * Math.PI / sides;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            float pix = 0f, piz = 0f, pox = 0f, poz = 0f, piy = cy, poy = cy;
            for (int j = 0; j <= m; j++) {
                float k = j / (float) m;
                float dx = c0 + (c1 - c0) * k, dz = s0 + (s1 - s0) * k;
                float ix = cx + dx * rin, iz = cz + dz * rin, ox = cx + dx * rout, oz = cz + dz * rout;
                float iy = terrain ? groundAt(ix, cy, iz) + lift : cy, oy = terrain ? groundAt(ox, cy, oz) + lift : cy;
                if (j > 0) {
                    out.vertex(pix, piy, piz, profile.u0, profile.v0, argb);
                    out.vertex(pox, poy, poz, profile.u1, profile.v0, argb);
                    out.vertex(ox, oy, oz, profile.u1, profile.v1, argb);
                    out.vertex(ix, iy, iz, profile.u0, profile.v1, argb);
                }
                pix = ix;
                piz = iz;
                pox = ox;
                poz = oz;
                piy = iy;
                poy = oy;
            }
        }
    }

    /**
     * A streak of light lying on the ground from (x0, z0) to (x1, z1) at about height {@code y}, {@code width} wide, its
     * bright head at the far end (a ray shooting out over the ground).
     */
    public static void groundStreak(FxOut out, float x0, float y, float z0, float x1, float z1, float width, int argb) {
        float dx = x1 - x0, dz = z1 - z0;
        float l = (float) Math.sqrt(dx * dx + dz * dz);
        if (l < 1e-4f || Colors.a(argb) == 0) {
            return;
        }
        out.use(FxSoft.atlas());
        float sx = -dz / l * width * 0.5f, sz = dx / l * width * 0.5f;
        float y0 = groundAt(x0, y, z0) + 0.03f, y1 = groundAt(x1, y, z1) + 0.03f;
        FxSoft.Region r = FxSoft.SPARK;
        out.vertex(x0 - sx, y0, z0 - sz, r.u0, r.v0, argb);
        out.vertex(x0 + sx, y0, z0 + sz, r.u0, r.v1, argb);
        out.vertex(x1 + sx, y1, z1 + sz, r.u1, r.v1, argb);
        out.vertex(x1 - sx, y1, z1 - sz, r.u1, r.v0, argb);
    }

    /**
     * A ring standing up round (cx, cy, cz) in the plane facing direction (nx, ny, nz): a halo, a portal. Drawn as an
     * arc of glowing lines.
     */
    public static void hoop(FxOut out, FxSoft.Region profile, float cx, float cy, float cz, float nx, float ny, float nz, float radius,
                            float width, int argb) {
        if (radius <= 0f || Colors.a(argb) == 0) {
            return;
        }
        // Two axes in the ring's plane.
        float ax = 0f, ay = 1f, az = 0f;
        if (Math.abs(ny) > 0.9f) {
            ax = 1f;
            ay = 0f;
        }
        float bx = ny * az - nz * ay, by = nz * ax - nx * az, bz = nx * ay - ny * ax;
        float bl = (float) Math.sqrt(bx * bx + by * by + bz * bz);
        bx /= bl;
        by /= bl;
        bz /= bl;
        float qx = ny * bz - nz * by, qy = nz * bx - nx * bz, qz = nx * by - ny * bx;
        int n = Math.max(8, Math.min(96, (int) (radius * 40f)));
        float px = cx + bx * radius, py = cy + by * radius, pz = cz + bz * radius;
        for (int i = 1; i <= n; i++) {
            double a = i * 2 * Math.PI / n;
            float c = (float) Math.cos(a), s = (float) Math.sin(a);
            float x = cx + (bx * c + qx * s) * radius, y = cy + (by * c + qy * s) * radius, z = cz + (bz * c + qz * s) * radius;
            line(out, profile, px, py, pz, x, y, z, width, argb, argb);
            px = x;
            py = y;
            pz = z;
        }
    }

    /**
     * A picture in the plane facing direction (nx, ny, nz), centred on (cx, cy, cz), {@code radius} out and turned
     * {@code angle} radians in its plane: a disc seen at a slant (a black hole's swirl).
     */
    public static void plane(FxOut out, FxSoft.Region r, float cx, float cy, float cz, float nx, float ny, float nz, float radius,
                             float angle, int argb) {
        if (radius <= 0f || Colors.a(argb) == 0) {
            return;
        }
        float nl = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        nx /= nl;
        ny /= nl;
        nz /= nl;
        float ax = 0f, ay = 1f, az = 0f;
        if (Math.abs(ny) > 0.9f) {
            ax = 1f;
            ay = 0f;
        }
        float bx = ny * az - nz * ay, by = nz * ax - nx * az, bz = nx * ay - ny * ax;
        float bl = (float) Math.sqrt(bx * bx + by * by + bz * bz);
        bx /= bl;
        by /= bl;
        bz /= bl;
        float qx = ny * bz - nz * by, qy = nz * bx - nx * bz, qz = nx * by - ny * bx;
        float c = (float) Math.cos(angle) * radius, s = (float) Math.sin(angle) * radius;
        // The picture's x and y axes in the world, turned.
        float ux = bx * c + qx * s, uy = by * c + qy * s, uz = bz * c + qz * s;
        float vx = -bx * s + qx * c, vy = -by * s + qy * c, vz = -bz * s + qz * c;
        out.use(FxSoft.atlas());
        out.vertex(cx - ux - vx, cy - uy - vy, cz - uz - vz, r.u0, r.v0, argb);
        out.vertex(cx - ux + vx, cy - uy + vy, cz - uz + vz, r.u0, r.v1, argb);
        out.vertex(cx + ux + vx, cy + uy + vy, cz + uz + vz, r.u1, r.v1, argb);
        out.vertex(cx + ux - vx, cy + uy - vy, cz + uz - vz, r.u1, r.v0, argb);
    }

    /**
     * A wall of light standing on a ring round (cx, cy, cz), {@code radius} out and {@code height} tall: bright at its
     * foot and fading up (a shock dome's edge, a jump ring's glow). On the terrain when asked.
     */
    public static void wall(FxOut out, float cx, float cy, float cz, float radius, float height, int argb, boolean terrain) {
        if (radius <= 0f || height <= 0f || Colors.a(argb) == 0) {
            return;
        }
        out.use(FxSoft.atlas());
        FxSoft.Region r = FxSoft.FADE;
        int n = Math.max(12, Math.min(128, (int) Math.ceil(radius * 14f)));
        float pc = 1f, ps = 0f, py = terrain ? groundAt(cx + radius, cy, cz) : cy;
        for (int i = 1; i <= n; i++) {
            double a = i * 2 * Math.PI / n;
            float c = (float) Math.cos(a), s = (float) Math.sin(a);
            float y = terrain ? groundAt(cx + c * radius, cy, cz + s * radius) : cy;
            float x0 = cx + pc * radius, z0 = cz + ps * radius, x1 = cx + c * radius, z1 = cz + s * radius;
            out.vertex(x0, py, z0, r.u0, r.v0, argb);
            out.vertex(x0, py + height, z0, r.u1, r.v0, argb);
            out.vertex(x1, y + height, z1, r.u1, r.v1, argb);
            out.vertex(x1, y, z1, r.u0, r.v1, argb);
            pc = c;
            ps = s;
            py = y;
        }
    }

    /** A picture lying flat at height {@code y}, {@code radius} out from (x, z), turned {@code angle} radians. */
    public static void decal(FxOut out, FxSoft.Region r, float x, float y, float z, float radius, float angle, int argb) {
        if (radius <= 0f || Colors.a(argb) == 0) {
            return;
        }
        out.use(FxSoft.atlas());
        float c = (float) Math.cos(angle) * radius, s = (float) Math.sin(angle) * radius;
        y += 0.012f;
        out.vertex(x - c + s, y, z - s - c, r.u0, r.v0, argb);
        out.vertex(x - c - s, y, z - s + c, r.u0, r.v1, argb);
        out.vertex(x + c - s, y, z + s + c, r.u1, r.v1, argb);
        out.vertex(x + c + s, y, z + s - c, r.u1, r.v0, argb);
    }

    /** {@link #decal} following the ground under it, in a 4 x 4 grid, each point within a block of {@code y}. */
    public static void decalGround(FxOut out, FxSoft.Region r, float x, float y, float z, float radius, float angle, int argb) {
        if (radius <= 0f || Colors.a(argb) == 0) {
            return;
        }
        out.use(FxSoft.atlas());
        final int n = 4;
        float c = (float) Math.cos(angle), s = (float) Math.sin(angle);
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                corner(out, r, x, y, z, radius, c, s, i, j, n, argb);
                corner(out, r, x, y, z, radius, c, s, i, j + 1, n, argb);
                corner(out, r, x, y, z, radius, c, s, i + 1, j + 1, n, argb);
                corner(out, r, x, y, z, radius, c, s, i + 1, j, n, argb);
            }
        }
    }

    private static void corner(FxOut out, FxSoft.Region r, float x, float y, float z, float radius, float c, float s, int i, int j, int n,
                               int argb) {
        float fu = i / (float) n, fv = j / (float) n;
        float lx = (fu * 2f - 1f) * radius, lz = (fv * 2f - 1f) * radius;
        float px = x + lx * c - lz * s, pz = z + lx * s + lz * c;
        float py = groundAt(px, y, pz) + 0.015f;
        out.vertex(px, py, pz, r.u0 + (r.u1 - r.u0) * fu, r.v0 + (r.v1 - r.v0) * fv, argb);
    }

    /** The ground's height (relative to the origin) under (x, z), searched from a block above {@code y}. */
    public static float groundAt(float x, float y, float z) {
        // Through the output that's drawing (the world's, or the preview's flat floor).
        FxOut out = current;
        if (out == null) {
            return y;
        }
        float g = out.ground(ox + x, oz + z, oy + y + 1.0) - (float) oy;
        return Math.abs(g - y) > 1.6f ? y : g;
    }

    /** The output the effects draw into now (set by the pool and layers as they draw). */
    static FxOut current;

    // ------------------------------------------------------------------ motion

    /**
     * Where something launched at speed {@code v} along one axis is after {@code t} seconds, slowed by {@code drag}
     * (per second) and pulled by {@code g} (blocks per second squared; negative is down).
     */
    public static float fly(float v, float drag, float g, float t) {
        if (drag <= 1e-4f) {
            return v * t + 0.5f * g * t * t;
        }
        float term = g / drag;
        return (v - term) * (1f - (float) Math.exp(-drag * t)) / drag + term * t;
    }

    /** Its speed then. */
    public static float speed(float v, float drag, float g, float t) {
        if (drag <= 1e-4f) {
            return v + g * t;
        }
        float term = g / drag;
        return (v - term) * (float) Math.exp(-drag * t) + term;
    }

    public static float clamp01(float k) {
        return k < 0f ? 0f : k > 1f ? 1f : k;
    }

    public static float outCubic(float k) {
        k = 1f - clamp01(k);
        return 1f - k * k * k;
    }

    public static float outExpo(float k) {
        k = clamp01(k);
        return k >= 1f ? 1f : 1f - (float) Math.pow(2, -10 * k);
    }

    public static float inOut(float k) {
        k = clamp01(k);
        return k * k * (3f - 2f * k);
    }

    public static float outBack(float k) {
        k = clamp01(k) - 1f;
        float s = 1.9f;
        return 1f + k * k * ((s + 1f) * k + s);
    }

    /** 0 to 1 and back to 0 over {@code k} 0..1: in quickly ({@code in} of the time), out over the rest. */
    public static float spike(float k, float in) {
        if (k <= 0f || k >= 1f) {
            return 0f;
        }
        return k < in ? k / in : 1f - (k - in) / (1f - in);
    }

    /** A fade in over {@code in} and out over {@code out} (seconds) of a life of {@code life}, at {@code t}. */
    public static float life(float t, float life, float in, float out) {
        if (t < 0f || t > life) {
            return 0f;
        }
        float a = in > 0f ? Math.min(1f, t / in) : 1f;
        float b = out > 0f ? Math.min(1f, (life - t) / out) : 1f;
        return Math.min(a, b);
    }

    // ------------------------------------------------------------------ colour

    /** {@code argb} at alpha {@code a} (0..1) times its own. */
    public static int a(int argb, float a) {
        int al = Math.round(Colors.a(argb) * clamp01(a));
        return al << 24 | argb & 0xFFFFFF;
    }

    /** {@code argb}'s colour at alpha {@code a} (0..1). */
    public static int with(int argb, float a) {
        return Math.round(clamp01(a) * 255f) << 24 | argb & 0xFFFFFF;
    }

    /** The colour mixed {@code k} toward white: a hot centre. */
    public static int white(int argb, float k) {
        return Colors.mixRgb(argb, 0xFFFFFFFF, k) & 0xFFFFFF | argb & 0xFF000000;
    }

    /** Between two colours. */
    public static int mix(int a, int b, float k) {
        return Colors.mix(a, b, clamp01(k));
    }

    /** A hue turned {@code degrees} round, kept as bright. */
    public static int hue(int argb, float degrees) {
        return Colors.shiftHue(argb, degrees, 1f);
    }

    /** A small hash in [0, 1) from a seed and a part number. */
    public static float h(int seed, int i) {
        return FxDraw.hash(seed, i);
    }

    /** A hash in [-1, 1). */
    public static float s(int seed, int i) {
        return FxDraw.signed(seed, i);
    }
}
