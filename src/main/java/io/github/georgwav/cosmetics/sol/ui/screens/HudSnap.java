package io.github.georgwav.cosmetics.sol.ui.screens;

import java.util.ArrayList;
import java.util.List;

/**
 * Where a box dragged in the HUD editor lands. Its left/top, middle or right/bottom snaps to the nearest guide within
 * {@link #SNAP} pixels:
 * - the screen's edges (with a {@link #MARGIN} gap) and middle;
 * - the other boxes' edges and middles;
 * - just outside the other boxes (to sit next to them with the gap).
 * The smallest shift wins. With Shift held it moves freely. It always stays on the screen. Pure Java, so it is
 * unit-tested; GUI pixels throughout.
 */
public final class HudSnap {
    /** How close an edge comes to a guide before it snaps to it. */
    public static final float SNAP = 4f;
    /** The gap kept from the screen's edges, and between boxes put next to each other. */
    public static final float MARGIN = 2f;

    /** A box on the HUD. */
    public record Rect(float x, float y, float w, float h) {
    }

    /** Where the box lands, and the guides it snapped to (NaN for none). */
    public record Result(float x, float y, float guideX, float guideY) {
    }

    private HudSnap() {
    }

    /**
     * Places a {@code w} x {@code h} box that the pointer would put at ({@code x}, {@code y}) on a {@code width} x
     * {@code height} screen with the {@code others}; {@code free}: Shift is held, no snapping.
     */
    public static Result place(float x, float y, float w, float h, float width, float height, List<Rect> others, boolean free) {
        float[] sx = {x, Float.NaN}, sy = {y, Float.NaN};
        if (!free) {
            List<Float> xs = new ArrayList<>(List.of(MARGIN, width / 2f, width - MARGIN));
            List<Float> ys = new ArrayList<>(List.of(MARGIN, height / 2f, height - MARGIN));
            for (Rect o : others) {
                xs.addAll(List.of(o.x(), o.x() + o.w() / 2f, o.x() + o.w(), o.x() - MARGIN, o.x() + o.w() + MARGIN));
                ys.addAll(List.of(o.y(), o.y() + o.h() / 2f, o.y() + o.h(), o.y() - MARGIN, o.y() + o.h() + MARGIN));
            }
            sx = snap(x, w, xs);
            sy = snap(y, h, ys);
        }
        return new Result(clamp(sx[0], w, width), clamp(sy[0], h, height), sx[1], sy[1]);
    }

    /** The box moved by the arrow keys: one pixel, ten with Shift; kept on the screen. */
    public static Rect nudge(Rect box, int dx, int dy, boolean shift, float width, float height) {
        float step = shift ? 10f : 1f;
        return new Rect(clamp(box.x() + dx * step, box.w(), width), clamp(box.y() + dy * step, box.h(), height), box.w(), box.h());
    }

    /**
     * Moves {@code start} (an edge of a box {@code size} long) so that its start, middle or end lines up with the
     * nearest target within {@link #SNAP}; returns the new start and the target (NaN when nothing is near).
     */
    static float[] snap(float start, float size, List<Float> targets) {
        float best = Float.NaN;
        float shift = 0f;
        float[] points = {start, start + size / 2f, start + size};
        for (float target : targets) {
            for (float point : points) {
                float d = target - point;
                if (Math.abs(d) <= SNAP && (Float.isNaN(best) || Math.abs(d) < Math.abs(shift))) {
                    best = target;
                    shift = d;
                }
            }
        }
        return new float[]{start + shift, best};
    }

    private static float clamp(float start, float size, float room) {
        return Math.max(0f, Math.min(room - size, start));
    }
}
