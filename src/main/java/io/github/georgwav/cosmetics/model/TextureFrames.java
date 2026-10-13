package io.github.georgwav.cosmetics.model;

import java.util.List;

/**
 * A layer's texture animation, ready for the render thread: which frame shows at a given time, and how
 * that frame is reached (a V offset into a vertical strip, or its own texture). Picking a frame is a
 * division or a short binary search and never allocates.
 */
public final class TextureFrames {
    private final int count;
    /** End of each frame, in milliseconds since the start of a cycle. */
    private final int[] ends;
    private final int total;
    /** Every frame shows for this long, or 0 when the frames have their own times. */
    private final int uniformTime;
    private final String[] textures;
    /** Render type per frame texture, created on first use. Render thread only. */
    public final Object[] renderTypes;

    TextureFrames(CosmeticDefinition.TextureAnimation animation) {
        this.count = animation.frames();
        this.ends = new int[count];
        int sum = 0;
        boolean uniform = true;
        List<Integer> times = animation.frameTimes();
        for (int i = 0; i < count; i++) {
            sum += times.get(i);
            ends[i] = sum;
            uniform &= times.get(i).equals(times.get(0));
        }
        this.total = sum;
        this.uniformTime = uniform ? times.get(0) : 0;
        this.textures = animation.strip() ? null : animation.textures().toArray(new String[0]);
        this.renderTypes = textures != null ? new Object[count] : null;
    }

    public int count() {
        return count;
    }

    /** All frames are stacked in one texture, so a frame is reached by moving V. */
    public boolean strip() {
        return textures == null;
    }

    /** Texture of a frame when every frame has its own file. */
    public String texture(int frame) {
        return textures[frame];
    }

    /** How far V moves (in 0..1 texture space) to show {@code frame} of a strip; 0 for the first frame. */
    public float vOffset(int frame) {
        return textures == null ? (float) frame / count : 0f;
    }

    /** The frame showing {@code seconds} into the animation, played at {@code speed} times normal speed. */
    public int frameAt(double seconds, float speed) {
        if (count == 1) {
            return 0;
        }
        double millis = seconds * 1000.0 * speed;
        long t = (long) (millis - Math.floor(millis / total) * total);
        if (t < 0 || t >= total) {
            t = 0;
        }
        if (uniformTime > 0) {
            return (int) Math.min(count - 1, t / uniformTime);
        }
        int low = 0;
        int high = count - 1;
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (t < ends[mid]) {
                high = mid;
            } else {
                low = mid + 1;
            }
        }
        return low;
    }
}
