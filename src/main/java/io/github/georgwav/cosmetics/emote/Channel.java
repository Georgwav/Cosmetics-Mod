package io.github.georgwav.cosmetics.emote;

/**
 * One animated number of a body part over an emote, such as the right arm's X rotation: its keyframes and how
 * it moves between them.
 *
 * @param times  keyframe times in seconds, ascending. Two keyframes may share a time: then the value jumps there
 *               (the first is the value just before, the second the value from then on).
 * @param values the value at each keyframe (pixels for offsets, radians for rotations)
 * @param eases  how the value moves INTO each keyframe from the one before; for the first keyframe, how it
 *               moves from the player's own pose when {@code leadIn} is set
 * @param leadIn before the first keyframe, move from the player's own pose to it (Emotecraft); otherwise hold
 *               the first keyframe's value (Blockbench)
 */
public record Channel(float[] times, float[] values, Ease[] eases, boolean leadIn) {

    public Channel {
        if (times.length == 0 || times.length != values.length || times.length != eases.length) {
            throw new IllegalArgumentException("a channel needs as many times, values and eases, at least one");
        }
    }

    /** A value that never changes. */
    public static Channel constant(float value) {
        return new Channel(new float[]{0f}, new float[]{value}, new Ease[]{Ease.LINEAR}, false);
    }

    /** The value at {@code time} seconds; {@code own} is the player's own pose, for the lead-in. */
    public float sample(float time, float own) {
        int n = times.length;
        if (time < times[0]) {
            if (leadIn && times[0] > 0f) {
                float f = Math.max(0f, time) / times[0];
                return own + (values[0] - own) * eases[0].apply(f);
            }
            return values[0];
        }
        if (time >= times[n - 1]) {
            return values[n - 1];
        }
        // The last keyframe at or before `time` (the later one when two share a time).
        int lo = 0;
        int hi = n - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (times[mid] <= time) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        int i = lo;
        float span = times[i + 1] - times[i];
        if (span <= 0f) {
            return values[i + 1];
        }
        float f = (time - times[i]) / span;
        Ease ease = eases[i + 1];
        if (ease == Ease.CATMULLROM) {
            float p0 = values[Math.max(0, i - 1)];
            float p3 = values[Math.min(n - 1, i + 2)];
            return catmullRom(p0, values[i], values[i + 1], p3, f);
        }
        return values[i] + (values[i + 1] - values[i]) * ease.apply(f);
    }

    /** Time of the last keyframe. */
    public float end() {
        return times[times.length - 1];
    }

    private static float catmullRom(float p0, float p1, float p2, float p3, float t) {
        float t2 = t * t;
        float t3 = t2 * t;
        return 0.5f * (2 * p1 + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3);
    }
}
