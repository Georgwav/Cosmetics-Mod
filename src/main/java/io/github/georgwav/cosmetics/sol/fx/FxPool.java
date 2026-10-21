package io.github.georgwav.cosmetics.sol.fx;

/**
 * Short-lived effects in a fixed pool (C.3): a cap per kind and one for all of them, both scaled by the quality the
 * frame time allows; when a cap is hit the oldest of that kind fades out early to make room. The world has one pool
 * ({@link FxWorld}); each menu preview has its own. Nothing is allocated once the pool exists.
 */
public final class FxPool {
    private final Fx[] items;
    private final int globalCap;
    private float now;
    /** For the checks: effects started and drawn. */
    public int started, drawnFrames;

    public FxPool(int size, int globalCap) {
        items = new Fx[size];
        for (int i = 0; i < size; i++) {
            items[i] = new Fx();
        }
        this.globalCap = globalCap;
    }

    /** The pool's clock, seconds. */
    public float now() {
        return now;
    }

    public void setNow(float seconds) {
        now = seconds;
    }

    /** 0.5, 0.75 or 1 of each cap, from the quality level (0 low .. 2 high). */
    static float scale(int qualityLevel) {
        return qualityLevel <= 0 ? 0.5f : qualityLevel == 1 ? 0.75f : 1f;
    }

    /**
     * Starts an effect of {@code kind} lasting {@code life} seconds at (x, y, z); fill its fields, then it plays. When the
     * kind (or the pool) is at its cap, the oldest one is told to end early. Never null: when every slot is busy the
     * oldest one is taken.
     */
    public Fx spawn(FxKind kind, double x, double y, double z, float life, int qualityLevel) {
        float scale = scale(qualityLevel);
        int kindCap = Math.max(1, Math.round(kind.cap * scale));
        int allCap = Math.max(4, Math.round(globalCap * scale));
        int sameKind = 0, all = 0;
        Fx oldestSame = null, oldest = null, free = null;
        for (Fx f : items) {
            if (!f.live) {
                if (free == null) {
                    free = f;
                }
                continue;
            }
            all++;
            if (oldest == null || f.start < oldest.start) {
                oldest = f;
            }
            if (f.kind == kind) {
                sameKind++;
                // The oldest one still playing in full: the one to end early next.
                if (f.end >= f.start + f.life && (oldestSame == null || f.start < oldestSame.start)) {
                    oldestSame = f;
                }
            }
        }
        if (sameKind >= kindCap && oldestSame != null) {
            endEarly(oldestSame);
        } else if (all >= allCap && oldest != null) {
            endEarly(oldest);
        }
        Fx f = free != null ? free : oldest;
        if (f == null) {
            f = items[0];
        }
        f.reset();
        f.live = true;
        f.kind = kind;
        f.start = now;
        f.life = life;
        f.end = now + life;
        f.x = x;
        f.y = y;
        f.z = z;
        f.seed = (int) (Double.doubleToLongBits(x * 31.0 + z * 17.0 + now) * 0x9E3779B97F4A7C15L >>> 33) ^ started;
        started++;
        return f;
    }

    /** Starts an effect {@code seconds} later than spawned (a Try it's second and third hits); nothing shows till then. */
    public static void delay(Fx f, float seconds) {
        f.start += seconds;
        f.end += seconds;
    }

    private void endEarly(Fx f) {
        f.end = Math.min(f.end, now + 0.25f);
    }

    /** Ends every effect now. */
    public void clear() {
        for (Fx f : items) {
            f.live = false;
            f.copy = null;
        }
    }

    /** How many of a kind are playing (every kind when null). */
    public int count(FxKind kind) {
        int n = 0;
        for (Fx f : items) {
            if (f.live && (kind == null || f.kind == kind)) {
                n++;
            }
        }
        return n;
    }

    /** How many effects whose kind's id starts with {@code prefix} are playing. */
    public int count(String prefix) {
        int n = 0;
        for (Fx f : items) {
            if (f.live && f.kind.id.startsWith(prefix)) {
                n++;
            }
        }
        return n;
    }

    public boolean any() {
        for (Fx f : items) {
            if (f.live) {
                return true;
            }
        }
        return false;
    }

    /** Draws both passes of every live effect and retires the finished ones. */
    public void draw(FxOut out, Runnable beginGlow, Runnable beginBody, Runnable endPass) {
        boolean anyLive = false;
        for (Fx f : items) {
            if (f.live && now >= f.end) {
                f.live = false;
                f.copy = null;
            }
            anyLive |= f.live;
        }
        if (!anyLive) {
            return;
        }
        drawnFrames++;
        FxDraw.camera(out);
        beginGlow.run();
        for (Fx f : items) {
            if (f.live && now >= f.start) {
                f.kind.draw(f, now - f.start, now, out);
            }
        }
        endPass.run();
        beginBody.run();
        for (Fx f : items) {
            if (f.live && now >= f.start) {
                f.kind.draw(f, now - f.start, now, out);
            }
        }
        endPass.run();
    }
}
