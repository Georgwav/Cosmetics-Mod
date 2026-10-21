package io.github.georgwav.cosmetics.sol.styles;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The texture editor's picture (B.2.12): its frames (an animated texture's, stacked as the game stacks them), the
 * tools' work on them, a selection, and 200 steps of undo kept as diffs (the pixels each stroke changed, before and
 * after), not copies. On a block texture everything stays opaque and the eraser paints the original's pixel back
 * instead of clearing. Plain Java, so it is unit-tested; the screen draws it and sends it live.
 */
public final class EditorImage {
    public static final int UNDO = 200;
    /** The sizes the editor resizes between. */
    public static final int[] SIZES = {16, 32, 64};

    private int w, h;
    private final List<int[]> frames = new ArrayList<>();
    private final List<Integer> times = new ArrayList<>();
    /** The original's frames, for a block's eraser (resized with the picture). */
    private final List<int[]> base = new ArrayList<>();
    private int frame;
    private final boolean opaque;
    /** The selection (inclusive-exclusive), or null. */
    private int[] sel;

    /** One step: the pixels a stroke changed in one frame, or the whole picture when its shape changed. */
    private record Step(int frame, int[] at, int[] before, int[] after, Shape shapeBefore, Shape shapeAfter) {
    }

    /** The whole picture: its size, frames, times and originals. */
    private record Shape(int w, int h, List<int[]> frames, List<Integer> times, List<int[]> base, int frame) {
    }

    private final Deque<Step> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
    /** The stroke being recorded: index to its colour before. */
    private final java.util.LinkedHashMap<Integer, Integer> stroke = new java.util.LinkedHashMap<>();
    private boolean inStroke;

    private EditorImage(int w, int h, boolean opaque) {
        this.w = w;
        this.h = h;
        this.opaque = opaque;
    }

    /**
     * The editor's picture for a texture: its frames when it's a strip of squares (with their times from its
     * .mcmeta, or one tick each), else the one picture.
     */
    public static EditorImage of(Pixels.Image tex, boolean opaque, String mcmeta) {
        int fw = tex.w(), fh = tex.h();
        int n = 1;
        if (mcmeta != null && tex.h() > tex.w() && tex.h() % tex.w() == 0) {
            n = tex.h() / tex.w();
            fh = tex.w();
        }
        EditorImage e = new EditorImage(fw, fh, opaque);
        int time = frameTime(mcmeta);
        for (int i = 0; i < n; i++) {
            int[] f = Arrays.copyOfRange(tex.argb(), i * fw * fh, (i + 1) * fw * fh);
            e.frames.add(f);
            e.base.add(f.clone());
            e.times.add(time);
        }
        return e;
    }

    /** The "frametime" in an animation's .mcmeta (1 when there's none). */
    static int frameTime(String mcmeta) {
        if (mcmeta == null) {
            return 1;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"frametime\"\\s*:\\s*(\\d+)").matcher(mcmeta);
        return m.find() ? Math.max(1, Integer.parseInt(m.group(1))) : 1;
    }

    public int width() {
        return w;
    }

    public int height() {
        return h;
    }

    public int frames() {
        return frames.size();
    }

    public int frame() {
        return frame;
    }

    public boolean opaque() {
        return opaque;
    }

    public void showFrame(int i) {
        frame = Math.max(0, Math.min(frames.size() - 1, i));
    }

    public int frameTime(int i) {
        return times.get(i);
    }

    /** The current frame as a picture. */
    public Pixels.Image image() {
        return new Pixels.Image(w, h, frames.get(frame).clone());
    }

    /** Another frame (the onion skin's), or null. */
    public int[] framePixels(int i) {
        return i < 0 || i >= frames.size() ? null : frames.get(i);
    }

    /** The texture as the game reads it: the frames stacked from the top. */
    public Pixels.Image texture() {
        int[] out = new int[w * h * frames.size()];
        for (int i = 0; i < frames.size(); i++) {
            System.arraycopy(frames.get(i), 0, out, i * w * h, w * h);
        }
        return new Pixels.Image(w, h * frames.size(), out);
    }

    /** The animation's .mcmeta for more than one frame (each frame's time), else null. */
    public String mcmeta() {
        if (frames.size() < 2) {
            return null;
        }
        boolean same = true;
        for (int t : times) {
            same &= t == times.get(0);
        }
        StringBuilder sb = new StringBuilder("{\n  \"animation\": {\n    \"frametime\": ").append(times.get(0));
        if (!same) {
            sb.append(",\n    \"frames\": [");
            for (int i = 0; i < times.size(); i++) {
                sb.append(i == 0 ? "" : ", ").append("{\"index\": ").append(i).append(", \"time\": ").append(times.get(i)).append('}');
            }
            sb.append(']');
        }
        return sb.append("\n  }\n}\n").toString();
    }

    public int get(int x, int y) {
        return x < 0 || y < 0 || x >= w || y >= h ? 0 : frames.get(frame)[y * w + x];
    }

    // ------------------------------------------------------------------ strokes and undo

    /** Starts a stroke: everything until {@link #end()} is one undo step. */
    public void begin() {
        stroke.clear();
        inStroke = true;
    }

    /** Ends the stroke; returns whether it changed anything. */
    public boolean end() {
        inStroke = false;
        if (stroke.isEmpty()) {
            return false;
        }
        int n = stroke.size();
        int[] at = new int[n], before = new int[n], after = new int[n];
        int i = 0;
        int[] f = frames.get(frame);
        for (var e : stroke.entrySet()) {
            at[i] = e.getKey();
            before[i] = e.getValue();
            after[i] = f[e.getKey()];
            i++;
        }
        stroke.clear();
        push(new Step(frame, at, before, after, null, null));
        return true;
    }

    private void push(Step s) {
        undo.push(s);
        while (undo.size() > UNDO) {
            undo.removeLast();
        }
        redo.clear();
    }

    public boolean canUndo() {
        return !undo.isEmpty();
    }

    public boolean canRedo() {
        return !redo.isEmpty();
    }

    public int undoSteps() {
        return undo.size();
    }

    public boolean undo() {
        Step s = undo.poll();
        if (s == null) {
            return false;
        }
        apply(s, true);
        redo.push(s);
        return true;
    }

    public boolean redo() {
        Step s = redo.poll();
        if (s == null) {
            return false;
        }
        apply(s, false);
        undo.push(s);
        return true;
    }

    private void apply(Step s, boolean back) {
        if (s.shapeBefore() != null) {
            restore(back ? s.shapeBefore() : s.shapeAfter());
            return;
        }
        int[] f = frames.get(s.frame());
        int[] v = back ? s.before() : s.after();
        for (int i = 0; i < s.at().length; i++) {
            f[s.at()[i]] = v[i];
        }
        frame = s.frame();
    }

    private Shape shape() {
        List<int[]> fs = new ArrayList<>(), bs = new ArrayList<>();
        frames.forEach(f -> fs.add(f.clone()));
        base.forEach(b -> bs.add(b.clone()));
        return new Shape(w, h, fs, new ArrayList<>(times), bs, frame);
    }

    private void restore(Shape s) {
        w = s.w();
        h = s.h();
        frames.clear();
        s.frames().forEach(f -> frames.add(f.clone()));
        base.clear();
        s.base().forEach(b -> base.add(b.clone()));
        times.clear();
        times.addAll(s.times());
        frame = Math.min(s.frame(), frames.size() - 1);
        sel = null;
    }

    /** A change of the picture's shape (size, frames) as one step. */
    private void reshape(Runnable change) {
        Shape before = shape();
        change.run();
        push(new Step(frame, null, null, null, before, shape()));
    }

    /** Sets a pixel (inside the selection, when there is one), opaque on a block; recorded in the stroke. */
    private void set(int x, int y, int c) {
        if (x < 0 || y < 0 || x >= w || y >= h || sel != null && !inSel(x, y)) {
            return;
        }
        if (opaque) {
            c |= 0xFF000000;
        }
        int i = y * w + x;
        int[] f = frames.get(frame);
        if (f[i] == c) {
            return;
        }
        if (inStroke) {
            stroke.putIfAbsent(i, f[i]);
            f[i] = c;
        } else {
            // A change outside a stroke is a step of its own.
            begin();
            stroke.put(i, f[i]);
            f[i] = c;
            end();
        }
    }

    private boolean inSel(int x, int y) {
        return x >= sel[0] && y >= sel[1] && x < sel[2] && y < sel[3];
    }

    /** What the eraser leaves: clear, or on a block the original's pixel. */
    private int erased(int x, int y) {
        if (!opaque) {
            return 0;
        }
        int[] b = base.get(Math.min(frame, base.size() - 1));
        return b[y * w + x] | 0xFF000000;
    }

    // ------------------------------------------------------------------ the tools

    /** The pencil: a square brush of {@code size} pixels, mirrored across the middle when asked. */
    public void pencil(int x, int y, int size, int c, boolean mirrorX, boolean mirrorY) {
        brush(x, y, size, mirrorX, mirrorY, (px, py) -> set(px, py, c));
    }

    public void erase(int x, int y, int size, boolean mirrorX, boolean mirrorY) {
        brush(x, y, size, mirrorX, mirrorY, (px, py) -> {
            if (px >= 0 && py >= 0 && px < w && py < h) {
                set(px, py, erased(px, py));
            }
        });
    }

    private interface Plot {
        void at(int x, int y);
    }

    private void brush(int x, int y, int size, boolean mirrorX, boolean mirrorY, Plot plot) {
        int n = Math.max(1, Math.min(8, size)), o = (n - 1) / 2;
        for (int dy = 0; dy < n; dy++) {
            for (int dx = 0; dx < n; dx++) {
                int px = x - o + dx, py = y - o + dy;
                plot.at(px, py);
                if (mirrorX) {
                    plot.at(w - 1 - px, py);
                }
                if (mirrorY) {
                    plot.at(px, h - 1 - py);
                }
                if (mirrorX && mirrorY) {
                    plot.at(w - 1 - px, h - 1 - py);
                }
            }
        }
    }

    /** A line of the brush from one pixel to another (the pencil or, with {@code c} null, the eraser). */
    public void line(int x0, int y0, int x1, int y1, int size, Integer c, boolean mirrorX, boolean mirrorY) {
        int dx = Math.abs(x1 - x0), dy = -Math.abs(y1 - y0), sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1, err = dx + dy;
        int x = x0, y = y0;
        while (true) {
            if (c == null) {
                erase(x, y, size, mirrorX, mirrorY);
            } else {
                pencil(x, y, size, c, mirrorX, mirrorY);
            }
            if (x == x1 && y == y1) {
                break;
            }
            int e2 = 2 * err;
            if (e2 >= dy) {
                err += dy;
                x += sx;
            }
            if (e2 <= dx) {
                err += dx;
                y += sy;
            }
        }
    }

    /** A rectangle's outline (or filled), corner to corner. */
    public void rect(int x0, int y0, int x1, int y1, int size, int c, boolean filled) {
        int ax = Math.min(x0, x1), bx = Math.max(x0, x1), ay = Math.min(y0, y1), by = Math.max(y0, y1);
        if (filled) {
            for (int y = ay; y <= by; y++) {
                for (int x = ax; x <= bx; x++) {
                    set(x, y, c);
                }
            }
            return;
        }
        line(ax, ay, bx, ay, size, c, false, false);
        line(bx, ay, bx, by, size, c, false, false);
        line(bx, by, ax, by, size, c, false, false);
        line(ax, by, ax, ay, size, c, false, false);
    }

    /** Flood fill from a pixel: every touching pixel within {@code tolerance} (0..255 per channel) of it. */
    public void fill(int x, int y, int c, int tolerance) {
        if (x < 0 || y < 0 || x >= w || y >= h) {
            return;
        }
        int[] f = frames.get(frame);
        int from = f[y * w + x];
        boolean[] seen = new boolean[w * h];
        ArrayDeque<Integer> q = new ArrayDeque<>();
        q.add(y * w + x);
        seen[y * w + x] = true;
        List<Integer> hit = new ArrayList<>();
        while (!q.isEmpty()) {
            int i = q.poll();
            hit.add(i);
            int px = i % w, py = i / w;
            int[][] nb = {{px - 1, py}, {px + 1, py}, {px, py - 1}, {px, py + 1}};
            for (int[] n : nb) {
                if (n[0] < 0 || n[1] < 0 || n[0] >= w || n[1] >= h) {
                    continue;
                }
                int j = n[1] * w + n[0];
                if (!seen[j] && close(f[j], from, tolerance) && (sel == null || inSel(n[0], n[1]))) {
                    seen[j] = true;
                    q.add(j);
                }
            }
        }
        for (int i : hit) {
            set(i % w, i / w, c);
        }
    }

    static boolean close(int a, int b, int tolerance) {
        for (int s = 0; s < 32; s += 8) {
            if (Math.abs((a >>> s & 255) - (b >>> s & 255)) > tolerance) {
                return false;
            }
        }
        return true;
    }

    /** Every pixel within {@code tolerance} of one colour turned into another (in the selection, when there is one). */
    public void replace(int from, int to, int tolerance) {
        int[] f = frames.get(frame);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (close(f[y * w + x], from, tolerance)) {
                    set(x, y, to);
                }
            }
        }
    }

    /** Hue, saturation and brightness on the selection or everything, through {@link Pixels#recolour}. */
    public void hsb(int hue, float saturation, float brightness) {
        Pixels.Image done = Pixels.recolour(image(), hue, saturation, brightness, -1);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                set(x, y, done.argb()[y * w + x]);
            }
        }
    }

    /**
     * A gradient from one point to another, from {@code c0} to {@code c1}, over the selection (or everything): on its
     * visible pixels, keeping their alpha (on a clear area, all of it).
     */
    public void gradient(int x0, int y0, int x1, int y1, int c0, int c1) {
        int[] f = frames.get(frame);
        boolean anyVisible = false;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                anyVisible |= (sel == null || inSel(x, y)) && f[y * w + x] >>> 24 > 0;
            }
        }
        float dx = x1 - x0, dy = y1 - y0, len2 = Math.max(1f, dx * dx + dy * dy);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = f[y * w + x];
                if (anyVisible && p >>> 24 == 0) {
                    continue;
                }
                float t = Math.max(0f, Math.min(1f, ((x - x0) * dx + (y - y0) * dy) / len2));
                int c = mix(c0, c1, t);
                set(x, y, anyVisible ? (p & 0xFF000000) | (c & 0xFFFFFF) : c);
            }
        }
    }

    static int mix(int a, int b, float t) {
        int out = 0;
        for (int s = 0; s < 32; s += 8) {
            int ca = a >>> s & 255, cb = b >>> s & 255;
            out |= Math.round(ca + (cb - ca) * t) << s;
        }
        return out;
    }

    /** An outline around the visible pixels (of the selection): every clear pixel touching one, in {@code c}. */
    public void outline(int c) {
        int[] f = frames.get(frame).clone();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (f[y * w + x] >>> 24 != 0 || sel != null && !inSel(x, y)) {
                    continue;
                }
                boolean touches = false;
                for (int[] n : new int[][]{{x - 1, y}, {x + 1, y}, {x, y - 1}, {x, y + 1}}) {
                    touches |= n[0] >= 0 && n[1] >= 0 && n[0] < w && n[1] < h && f[n[1] * w + n[0]] >>> 24 != 0;
                }
                if (touches) {
                    set(x, y, c);
                }
            }
        }
    }

    // ------------------------------------------------------------------ the selection

    public void select(int x0, int y0, int x1, int y1) {
        int ax = Math.max(0, Math.min(x0, x1)), ay = Math.max(0, Math.min(y0, y1));
        int bx = Math.min(w, Math.max(x0, x1) + 1), by = Math.min(h, Math.max(y0, y1) + 1);
        sel = bx > ax && by > ay ? new int[]{ax, ay, bx, by} : null;
    }

    public void selectNone() {
        sel = null;
    }

    /** The selection (x0, y0, x1, y1, end exclusive), or null. */
    public int[] selection() {
        return sel == null ? null : sel.clone();
    }

    /** Moves the selected pixels by (dx, dy): cut where they were (cleared, or the original on a block) and put down. */
    public void move(int dx, int dy) {
        if (sel == null || dx == 0 && dy == 0) {
            return;
        }
        int[] f = frames.get(frame);
        int sw = sel[2] - sel[0], sh = sel[3] - sel[1];
        int[] cut = new int[sw * sh];
        for (int y = 0; y < sh; y++) {
            System.arraycopy(f, (sel[1] + y) * w + sel[0], cut, y * sw, sw);
        }
        int[] old = sel;
        sel = null;
        boolean was = inStroke;
        if (!was) {
            begin();
        }
        for (int y = 0; y < sh; y++) {
            for (int x = 0; x < sw; x++) {
                set(old[0] + x, old[1] + y, erased(old[0] + x, old[1] + y));
            }
        }
        for (int y = 0; y < sh; y++) {
            for (int x = 0; x < sw; x++) {
                int c = cut[y * sw + x];
                if (c >>> 24 != 0 || opaque) {
                    set(old[0] + x + dx, old[1] + y + dy, c);
                }
            }
        }
        if (!was) {
            end();
        }
        sel = new int[]{old[0] + dx, old[1] + dy, old[2] + dx, old[3] + dy};
    }

    // ------------------------------------------------------------------ size and frames

    /** Resized to {@code size} pixels wide (nearest neighbour), the height in proportion: 16 to 32 to 64 and back. */
    public void resize(int size) {
        if (size == w || size < 1) {
            return;
        }
        reshape(() -> {
            int nw = size, nh = Math.max(1, h * size / w);
            for (int i = 0; i < frames.size(); i++) {
                frames.set(i, scaled(frames.get(i), w, h, nw, nh));
                base.set(i, scaled(base.get(i), w, h, nw, nh));
            }
            w = nw;
            h = nh;
            sel = null;
        });
    }

    static int[] scaled(int[] src, int w, int h, int nw, int nh) {
        int[] out = new int[nw * nh];
        for (int y = 0; y < nh; y++) {
            for (int x = 0; x < nw; x++) {
                out[y * nw + x] = src[(y * h / nh) * w + x * w / nw];
            }
        }
        return out;
    }

    /** A copy of this frame after it (an animation's next frame). */
    public void addFrame() {
        reshape(() -> {
            frames.add(frame + 1, frames.get(frame).clone());
            base.add(frame + 1, base.get(Math.min(frame, base.size() - 1)).clone());
            times.add(frame + 1, times.get(frame));
            frame++;
        });
    }

    public void removeFrame() {
        if (frames.size() < 2) {
            return;
        }
        reshape(() -> {
            frames.remove(frame);
            base.remove(Math.min(frame, base.size() - 1));
            times.remove(frame);
            frame = Math.min(frame, frames.size() - 1);
        });
    }

    public void setFrameTime(int i, int ticks) {
        int t = Math.max(1, Math.min(200, ticks));
        if (times.get(i) != t) {
            reshape(() -> times.set(i, t));
        }
    }

    /** A picture dropped on the window (PNG) as the whole texture, as one step. */
    public void replaceAll(Pixels.Image img) {
        reshape(() -> {
            EditorImage in = of(img, opaque, img.h() > img.w() && img.h() % img.w() == 0 ? "{}" : null);
            w = in.w;
            h = in.h;
            frames.clear();
            frames.addAll(in.frames);
            times.clear();
            times.addAll(in.times);
            while (base.size() < frames.size()) {
                base.add(base.get(base.size() - 1));
            }
            for (int i = 0; i < base.size(); i++) {
                if (base.get(i).length != w * h) {
                    base.set(i, frames.get(Math.min(i, frames.size() - 1)).clone());
                }
            }
            if (opaque) {
                for (int[] f : frames) {
                    for (int i = 0; i < f.length; i++) {
                        f[i] |= 0xFF000000;
                    }
                }
            }
            frame = 0;
            sel = null;
        });
    }

    // ------------------------------------------------------------------ colours

    /** The picture's own colours (every frame), sorted by hue and lightness, greys first; at most {@code max}. */
    public List<Integer> palette(int max) {
        Set<Integer> seen = new LinkedHashSet<>();
        for (int[] f : frames) {
            for (int p : f) {
                if (p >>> 24 != 0) {
                    seen.add(p | 0xFF000000);
                }
            }
        }
        List<Integer> out = new ArrayList<>(seen);
        out.sort((a, b) -> {
            float[] ha = java.awt.Color.RGBtoHSB(a >> 16 & 255, a >> 8 & 255, a & 255, null);
            float[] hb = java.awt.Color.RGBtoHSB(b >> 16 & 255, b >> 8 & 255, b & 255, null);
            boolean ga = ha[1] < 0.12f, gb = hb[1] < 0.12f;
            if (ga != gb) {
                return ga ? -1 : 1;
            }
            int byHue = ga ? 0 : Float.compare(Math.round(ha[0] * 24f), Math.round(hb[0] * 24f));
            return byHue != 0 ? byHue : Float.compare(ha[2], hb[2]);
        });
        return out.size() > max ? out.subList(0, max) : out;
    }
}
