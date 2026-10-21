package io.github.georgwav.cosmetics.sol.fx;

/**
 * The parts most effects are made of, built from {@link Vfx}: an impact (a hot flash, a lens flare, light thrown on the
 * ground and a shock front racing out), a burst of sparks that streak along their flight, slow and fall, embers
 * drifting up and flickering, smoke rolling up and spreading, and a body's glowing spirit. Each is a function of its
 * age alone, so effects stay stateless. Light goes in the glow pass; the same parts are laid over the world faintly in
 * the body pass too, so they read against a bright day sky where added light washes out.
 */
public final class FxBits {
    private FxBits() {
    }

    /**
     * An impact at (x, y, z) {@code t} seconds ago, {@code scale} big, its light in {@code colour} with a white-hot
     * centre: a flash, a flare, a pool of light on the ground at height {@code gy} and a shock front racing out over it.
     */
    public static void impact(FxOut out, Fx fx, float t, float x, float y, float z, float gy, float scale, int colour) {
        if (t < 0f) {
            return;
        }
        float glowK = glowK(fx);
        int hot = Vfx.white(colour, 0.7f);
        if (out.glowPass()) {
            if (t < 0.45f) {
                float k = t / 0.45f;
                float f = (1f - k) * (1f - k);
                Vfx.hot(out, x, y, z, 1.6f * scale * (0.7f + 0.5f * k), Vfx.with(hot, f * glowK));
                Vfx.glow(out, x, y, z, 3.4f * scale, Vfx.with(colour, 0.55f * f * glowK));
                Vfx.flare(out, x, y, z, 3.6f * scale * (0.8f + 0.4f * k), 0.25f + t * 0.6f, Vfx.with(hot, 0.9f * f * glowK));
            }
            if (t < 1.1f) {
                float k = t / 1.1f;
                Vfx.pool(out, x, gy, z, 3.4f * scale, Vfx.with(colour, 0.8f * (1f - k) * (1f - k) * glowK));
            }
        }
        // The shock front over the ground.
        if (t < 0.7f) {
            float k = t / 0.7f;
            float r = 0.3f * scale + 4.2f * scale * Vfx.outExpo(k);
            float w = 1.1f * scale * (1f - 0.6f * k);
            float a = (float) Math.pow(1f - k, 1.4f);
            if (out.glowPass()) {
                Vfx.ring(out, FxSoft.FRONT, x, gy + 0.03f, z, r, w, Vfx.with(colour, a * glowK), true);
            }
        }
    }

    /** The glow setting as strength: off draws no light, soft some, strong more. */
    public static float glowK(Fx fx) {
        return fx.glow == 0 ? 0f : fx.glow == 2 ? 1.35f : 1f;
    }

    /**
     * {@code n} sparks burst from (x, y, z) {@code t} seconds ago at about {@code speed} blocks a second, {@code up}
     * biasing them upward (0 any way, 1 mostly up), slowed by {@code drag}, pulled down by {@code g}, each living up
     * to {@code life} seconds and stopping at the ground {@code gy}. White-hot as they leave, cooling to
     * {@code colour}. {@code salt} keeps different bursts of one effect apart.
     */
    public static void sparks(FxOut out, Fx fx, float t, float x, float y, float z, int n, float speed, float up, float g, float drag,
                              float life, int colour, float scale, float gy, int salt) {
        if (t < 0f) {
            return;
        }
        boolean glow = out.glowPass();
        float glowK = glowK(fx);
        if (glow && glowK == 0f) {
            return;
        }
        int hot = Vfx.white(colour, 0.85f);
        for (int i = 0; i < n; i++) {
            int k = salt * 977 + i;
            float li = life * (0.45f + 0.55f * Vfx.h(fx.seed, k));
            if (t > li) {
                continue;
            }
            double th = Vfx.h(fx.seed, k + 1) * Math.PI * 2;
            float dy = -0.35f + 1.35f * (float) Math.pow(Vfx.h(fx.seed, k + 2), 1f - 0.7f * up);
            dy = Math.max(-1f, Math.min(1f, dy));
            float hz = (float) Math.sqrt(1f - dy * dy);
            float dx = (float) Math.cos(th) * hz, dz = (float) Math.sin(th) * hz;
            float v = speed * (0.35f + 0.9f * Vfx.h(fx.seed, k + 3));
            float px = x + dx * Vfx.fly(v, drag, 0f, t), pz = z + dz * Vfx.fly(v, drag, 0f, t);
            float py = y + Vfx.fly(dy * v, drag, g, t);
            float vh = Vfx.speed(v, drag, 0f, t), vy = Vfx.speed(dy * v, drag, g, t);
            if (py < gy + 0.03f) {
                // On the ground: it lies there, cooling.
                py = gy + 0.03f;
                vy = 0f;
                vh *= 0.1f;
            }
            float age = t / li;
            float a = (float) Math.pow(1f - age, 1.1f);
            float sp = (float) Math.sqrt(vh * vh + vy * vy);
            float len = Math.max(0.05f, Math.min(1.1f, sp * 0.055f)) * scale;
            float w = 0.075f * scale * (1f - 0.5f * age);
            int c = Vfx.mix(hot, colour, Math.min(1f, age * 1.8f));
            if (glow) {
                Vfx.streak(out, px, py, pz, dx * vh, vy, dz * vh, len, w * 1.6f, Vfx.with(c, a * glowK));
                if (i % 3 == 0) {
                    Vfx.glow(out, px, py, pz, 0.22f * scale, Vfx.with(colour, 0.35f * a * glowK));
                }
            } else {
                Vfx.streak(out, px, py, pz, dx * vh, vy, dz * vh, len, w, Vfx.with(colour, 0.6f * a));
            }
        }
    }

    /**
     * {@code n} embers drifting up from round (x, y, z) within {@code radius}, born over the first {@code spread}
     * seconds, each rising about {@code rise} blocks a second for up to {@code life} seconds, wavering and
     * flickering.
     */
    public static void embers(FxOut out, Fx fx, float t, float x, float y, float z, int n, float radius, float rise, float spread,
                              float life, int colour, float scale, int salt) {
        boolean glow = out.glowPass();
        float glowK = glowK(fx);
        if (glow && glowK == 0f) {
            return;
        }
        for (int i = 0; i < n; i++) {
            int k = salt * 613 + i * 7;
            float born = spread * Vfx.h(fx.seed, k);
            float li = life * (0.5f + 0.5f * Vfx.h(fx.seed, k + 1));
            float age = t - born;
            if (age < 0f || age > li) {
                continue;
            }
            double th = Vfx.h(fx.seed, k + 2) * Math.PI * 2;
            float rr = radius * (float) Math.sqrt(Vfx.h(fx.seed, k + 3));
            float wob = 0.25f * scale * (0.5f + Vfx.h(fx.seed, k + 4));
            float f1 = 1.5f + 2.5f * Vfx.h(fx.seed, k + 5);
            float px = x + (float) Math.cos(th) * rr + (float) Math.sin(age * f1 + i) * wob;
            float pz = z + (float) Math.sin(th) * rr + (float) Math.cos(age * f1 * 0.8f + i * 1.3f) * wob;
            float py = y + rise * age * (0.6f + 0.8f * Vfx.h(fx.seed, k + 6)) * scale;
            float k2 = age / li;
            float a = Vfx.life(age, li, 0.12f, li * 0.5f) * (0.65f + 0.35f * (float) Math.sin(age * 17f + i * 2.1f));
            float size = (0.035f + 0.05f * Vfx.h(fx.seed, k + 8)) * scale * (1f - 0.4f * k2);
            if (glow) {
                Vfx.hot(out, px, py, pz, size * 5f, Vfx.with(Vfx.white(colour, 0.35f), a * glowK));
            } else {
                Vfx.sprite(out, FxSoft.DOT, px, py, pz, size, 0f, Vfx.with(Vfx.white(colour, 0.25f), 0.8f * a));
            }
        }
    }

    /**
     * {@code n} puffs of smoke rolling up from round (x, y, z): each born in the first {@code spread} seconds, rising
     * {@code rise} blocks a second, growing from {@code size} to three times it and fading over {@code life}; drawn
     * over the world (body pass) in {@code colour} at most {@code alpha}.
     */
    public static void smoke(FxOut out, Fx fx, float t, float x, float y, float z, int n, float radius, float rise, float spread, float life,
                             float size, int colour, float alpha, int salt) {
        if (out.glowPass()) {
            return;
        }
        for (int i = 0; i < n; i++) {
            int k = salt * 389 + i * 5;
            float born = spread * Vfx.h(fx.seed, k);
            float li = life * (0.6f + 0.4f * Vfx.h(fx.seed, k + 1));
            float age = t - born;
            if (age < 0f || age > li) {
                continue;
            }
            float k2 = age / li;
            double th = Vfx.h(fx.seed, k + 2) * Math.PI * 2;
            float rr = radius * (0.3f + 0.7f * Vfx.h(fx.seed, k + 3)) * (1f + 0.6f * k2);
            float px = x + (float) Math.cos(th) * rr, pz = z + (float) Math.sin(th) * rr;
            float py = y + Vfx.fly(rise * (0.7f + 0.6f * Vfx.h(fx.seed, k + 4)), 0.8f, 0f, age);
            float s = size * (1f + 2.2f * Vfx.outCubic(k2));
            float ang = Vfx.s(fx.seed, k + 5) * 3f + age * 0.5f * Vfx.s(fx.seed, k + 6);
            float a = alpha * Math.min(1f, k2 / 0.12f) * (float) Math.pow(1f - k2, 1.6f);
            Vfx.sprite(out, FxSoft.SMOKE[i & 3], px, py, pz, s, ang, Vfx.with(colour, a));
        }
    }

    /**
     * A body's spirit: the copy drawn as light (glow pass) {@code scale} times its size with its feet at (x, y, z), a
     * wider aura round it, and over the world a faint tinted copy so it shows by day.
     */
    public static void spirit(FxOut out, Fx fx, float x, float y, float z, float scale, int colour, float alpha) {
        PoseSnapshot copy = fx.copy;
        if (copy == null || alpha <= 0f) {
            return;
        }
        FxTex grey = copy.tinted(0xFFFFFFFF, 1f);
        if (out.glowPass()) {
            float glowK = glowK(fx);
            // The body as faint light, and a wide aura round it.
            copy.drawFaces(out, x, y, z, scale, grey, Vfx.with(colour, 0.32f * alpha * Math.max(0.6f, glowK)), false);
            copy.drawFaces(out, x, y - 0.06f * scale, z, scale * 1.12f, grey, Vfx.with(colour, 0.22f * alpha * glowK), true);
            Vfx.glow(out, x, y + copy.height * scale * 0.55f, z, copy.height * scale * 0.9f, Vfx.with(colour, 0.3f * alpha * glowK));
        } else {
            // Over the world: the skin itself, washed in the colour and see-through.
            copy.drawFaces(out, x, y, z, scale, copy.tinted(colour, 0.55f), Vfx.with(0xFFFFFFFF, 0.5f * alpha), false);
        }
    }
}
