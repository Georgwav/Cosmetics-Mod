package io.github.georgwav.cosmetics.sol.ui.kit;

/** A scrolling list's offset: the wheel moves its target, the offset eases there; clamped to the content. */
public final class Scroll {
    private float value;
    private float target;
    private float max;

    public float value() {
        return value;
    }

    /** The content's height beyond the view (0 when it fits). */
    public void setMax(float max) {
        this.max = Math.max(0f, max);
        target = Math.max(0f, Math.min(this.max, target));
        value = Math.max(0f, Math.min(this.max, value));
    }

    /** One wheel step ({@code amount} > 0 is up). */
    public boolean wheel(float amount) {
        if (max <= 0f) {
            return false;
        }
        target = Math.max(0f, Math.min(max, target - amount * 48f));
        return true;
    }

    public void step(float dt) {
        value = dt <= 0f || SolScreen.calm() ? target : value + (target - value) * Math.min(1f, dt * 14f);
    }

    public void reset() {
        value = target = 0f;
    }

    /** A thin scrollbar at the right of a view, when the content is taller. */
    public void bar(Gfx g, float right, float y, float h) {
        if (max <= 0f) {
            return;
        }
        float th = Math.max(24f, h * h / (h + max));
        float ty = y + (h - th) * (value / max);
        g.round(right - 4f, ty, 3f, th, 1.5f, 0x40FFFFFF);
    }
}
