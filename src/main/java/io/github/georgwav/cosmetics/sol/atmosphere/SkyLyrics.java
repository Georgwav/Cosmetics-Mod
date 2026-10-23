package io.github.georgwav.cosmetics.sol.atmosphere;

import io.github.georgwav.cosmetics.sol.render.SolTexture;
import io.github.georgwav.cosmetics.sol.render.WorldDraw;
import io.github.georgwav.cosmetics.sol.sky.SkyFont;
import io.github.georgwav.cosmetics.sol.theme.Tokens;
import io.github.georgwav.cosmetics.sol.util.Colors;
import io.github.georgwav.cosmetics.sol.util.Ease;
import io.github.georgwav.cosmetics.sol.util.LogOnce;

import java.io.InputStream;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

/**
 * The line being sung, written on the night sky (the brief's Album theme, with the album sky on): Sol's type (Geist
 * SemiBold, {@code sky/lyrics_text}) laid out on the screen like the sky's names, so it reads upright and left to
 * right whichever way you face. A soft veil behind it quiets the stars there and the theme's accent glows round it.
 * When the lyrics time their words, the sung part lights up letter by letter with a spark at its edge; otherwise the
 * whole line shows lit. A new line rises in as the last one fades up and away. The line hangs 24° up and drifts after
 * your view, so it stays ahead of you; hills and trees pass in front of it. Fed by the Music module ({@link #feed}).
 * Render thread only; a line is laid out once when it changes (or the screen does), nothing is allocated per frame.
 */
public final class SkyLyrics {
    /** How high the line hangs, in degrees above the horizon. */
    private static final float ALTITUDE = 24f;
    /** The most letters a line lays out. */
    private static final int MAX = 160;

    private static volatile String want;
    private static volatile float wantSung = -1f;

    private static final Line now = new Line(), old = new Line();
    /** Where the line hangs (degrees, as {@link SkyDraw#altAz}); NaN until it first shows. */
    private static float az = Float.NaN;
    private static long lastNanos;
    /** Frames it was written in, and letters written the last of them (for the scenario's check). */
    public static int drawn;
    public static int lettersDrawn;

    private static SkyFont font;
    private static SolTexture sheet;
    private static boolean failed;

    // One frame's layout of a line: each letter's left and right edge and baseline on the screen, its row's middle.
    private static final float[] gx0 = new float[MAX], gx1 = new float[MAX], gBase = new float[MAX];
    private static final int[] gRow = new int[MAX];
    private static final char[] gChar = new char[MAX];
    private static final float[] rowMid = new float[2], rowW = new float[2], rowY = new float[2];

    private SkyLyrics() {
    }

    /** One line as laid out: its text in the lettering's own characters, in at most two rows. */
    private static final class Line {
        String source = "";
        String text = "";
        final List<String> rows = new ArrayList<>(2);
        int faceSize = -1;
        float maxW = -1f;
        float alpha;
        /** Letters lit (eased toward what's sung), or -1: the whole line, its words aren't timed. */
        float sung = -1f;

        void copy(Line o) {
            source = o.source;
            text = o.text;
            rows.clear();
            rows.addAll(o.rows);
            faceSize = o.faceSize;
            maxW = o.maxW;
            alpha = o.alpha;
            sung = o.sung;
        }
    }

    /**
     * The line to write (null: none) and how much of it is sung, 0..1 by its letters, or -1 when its words aren't
     * timed. Set by the Music module each frame.
     */
    public static void feed(String line, float sung) {
        want = line;
        wantSung = sung;
    }

    /**
     * The text in the characters the lettering has: curly quotes, dashes and the ellipsis as plain ones, accented
     * letters it hasn't got without their accents, anything else left out; spaces collapsed.
     */
    static String fold(String s, SkyFont.Face face) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); ) {
            int c = s.codePointAt(i);
            i += Character.charCount(c);
            if (Character.isWhitespace(c)) {
                if (b.length() > 0 && b.charAt(b.length() - 1) != ' ') {
                    b.append(' ');
                }
                continue;
            }
            if (face.has(c)) {
                b.appendCodePoint(c);
                continue;
            }
            String plain = switch (c) {
                case '‘', '’', '‚', '′' -> "'";
                case '“', '”', '„', '″' -> "\"";
                case '–', '—', '−' -> "-";
                case '…' -> "...";
                default -> Normalizer.normalize(new String(Character.toChars(c)), Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
            };
            for (int k = 0; k < plain.length(); k++) {
                if (face.has(plain.charAt(k))) {
                    b.append(plain.charAt(k));
                }
            }
        }
        return b.toString().strip();
    }

    /** Words into at most two rows no wider than {@code maxW} pixels; what doesn't fit ends the second with "…". */
    static List<String> wrap(String text, SkyFont.Face face, float maxW) {
        List<String> rows = new ArrayList<>(2);
        StringBuilder row = new StringBuilder();
        boolean cut = false;
        for (String word : text.split(" ")) {
            if (word.isEmpty()) {
                continue;
            }
            String next = row.length() == 0 ? word : row + " " + word;
            if (row.length() > 0 && face.width(next, 0) > maxW) {
                rows.add(row.toString());
                row.setLength(0);
                if (rows.size() == 2) {
                    cut = true;
                    break;
                }
                next = word;
            }
            row.setLength(0);
            row.append(next);
        }
        if (!cut && row.length() > 0) {
            rows.add(row.toString());
        }
        // A word wider than a row is cut to fit; so is the second row when there was more.
        String dots = face.has('…') ? "…" : "...";
        for (int r = 0; r < rows.size(); r++) {
            String s = rows.get(r);
            boolean more = cut && r == rows.size() - 1;
            if (!more && face.width(s, 0) <= maxW) {
                continue;
            }
            while (s.length() > 1 && face.width(s + dots, 0) > maxW) {
                s = s.substring(0, s.length() - 1);
            }
            rows.set(r, s.stripTrailing() + dots);
        }
        return rows;
    }

    /** Writes it (sky pass, after the sky's names). */
    static void render(Tokens tk, float t, float rain) {
        lettersDrawn = 0;
        long nanos = System.nanoTime();
        float dt = lastNanos == 0L ? 0f : Math.min(0.1f, (nanos - lastNanos) / 1e9f);
        lastNanos = nanos;
        String w = want == null ? "" : want.strip();
        if (!w.equals(now.source)) {
            if (now.alpha > 0.02f && !now.rows.isEmpty()) {
                old.copy(now);
            }
            now.source = w;
            now.text = "";
            now.rows.clear();
            now.faceSize = -1;
            now.alpha = 0f;
            now.sung = wantSung < 0f ? -1f : 0f;
        }
        now.alpha = w.isEmpty() ? Math.max(0f, now.alpha - dt * 2.6f) : Math.min(1f, now.alpha + dt * 2.2f);
        old.alpha = Math.max(0f, old.alpha - dt * 2.6f);
        float night = Ease.smoothstep(0.05f, 0.4f, tk.night * (1f - rain));
        if (night <= 0f || now.alpha <= 0.01f && old.alpha <= 0.01f) {
            if (now.alpha <= 0.01f) {
                az = Float.NaN;
            }
            return;
        }
        if (!load()) {
            return;
        }
        follow(dt);
        float alt = (float) Math.toRadians(ALTITUDE), a = (float) Math.toRadians(az);
        float ch = (float) Math.cos(alt);
        if (!NightSky.projectWorld((float) -Math.sin(a) * ch, (float) Math.sin(alt), (float) Math.cos(a) * ch)) {
            return;
        }
        float cx = NightSky.screen[0], cy = NightSky.screen[1];
        // 22 px of a 720-pixel-high view: the same share of any screen. The size there is, or the largest scaled up.
        float px = 22f * NightSky.sh / 720f;
        SkyFont.Face face = font.face(SkyFont.LYRICS, px);
        float k = px > face.size * 1.15f ? px / face.size : 1f;
        float maxW = Math.min(NightSky.sw * 0.7f, face.size * k * 26f);

        int accent = tk.accent;
        int lit = Colors.mix(accent, 0xFFFFFFFF, 0.72f), dim = 0xFFC9D2F2;
        drawn++;
        // The leaving line drifts up as it fades; the new one rises into place.
        for (int pass = 0; pass < 2; pass++) {
            Line line = pass == 0 ? old : now;
            if (line.alpha <= 0.01f || line.source.isEmpty()) {
                continue;
            }
            if (line.faceSize != face.size || line.maxW != maxW) {
                line.text = fold(line.source, face);
                line.rows.clear();
                line.rows.addAll(wrap(line.text, face, maxW / k));
                line.faceSize = face.size;
                line.maxW = maxW;
            }
            if (line.rows.isEmpty()) {
                continue;
            }
            float rowH = face.size * k * 1.38f;
            float shift = pass == 0 ? -(1f - line.alpha) * rowH * 0.45f : (1f - Ease.outCubic(line.alpha)) * rowH * 0.5f;
            int n = layout(line, face, k, cx, cy + shift, rowH);
            if (pass == 1) {
                // The sung part eases toward the song, so the sweep glides between word times.
                float target = wantSung < 0f ? -1f : wantSung * n;
                line.sung = target < 0f ? -1f : line.sung < 0f ? target : line.sung + (target - line.sung) * Math.min(1f, dt * 14f);
            }
            float a0 = line.alpha * night;
            drawLine(line, face, k, n, a0, t, accent, lit, dim);
            lettersDrawn += pass == 1 ? n : 0;
        }
    }

    /** Lays the line's letters out on the screen (into the frame's arrays); how many there are. */
    private static int layout(Line line, SkyFont.Face face, float k, float cx, float cy, float rowH) {
        int rows = line.rows.size(), n = 0;
        for (int r = 0; r < rows; r++) {
            String s = line.rows.get(r);
            float w = face.width(s, 0) * k;
            // Whole pixels where the size is the face's own, so the letters land one to one on the screen.
            float x = cx - w / 2f, base = cy + (r - (rows - 1) / 2f) * rowH + face.ascent * k * 0.36f;
            if (k == 1f) {
                x = Math.round(x);
                base = Math.round(base);
            }
            rowMid[r] = x + w / 2f;
            rowW[r] = w;
            rowY[r] = base - face.ascent * k * 0.36f;
            float pen = x;
            for (int i = 0; i < s.length() && n < MAX; i++) {
                SkyFont.Glyph g = face.glyph(s.charAt(i));
                gChar[n] = s.charAt(i);
                gx0[n] = pen;
                gx1[n] = pen + g.advance() * k;
                gBase[n] = base;
                gRow[n] = r;
                n++;
                pen += g.advance() * k;
            }
        }
        return n;
    }

    private static void drawLine(Line line, SkyFont.Face face, float k, int n, float a0, float t, int accent, int lit, int dim) {
        int rows = line.rows.size();
        float size = face.size * k;
        // The veil: soft dark blots along each row, so the stars and lines behind don't run through the letters.
        WorldDraw.begin(WorldDraw.SKY_ALPHA, SkyDraw.softGlow());
        for (int r = 0; r < rows; r++) {
            blots(rowMid[r], rowY[r], rowW[r], size, 1.15f, Colors.withAlpha(0xFF02030A, 0.5f * a0));
        }
        WorldDraw.end();
        // The glow of the theme's accent round it, added like starlight.
        WorldDraw.begin(WorldDraw.SKY_ADD, SkyDraw.softGlow());
        for (int r = 0; r < rows; r++) {
            blots(rowMid[r], rowY[r], rowW[r], size, 1.5f, Colors.withAlpha(accent, 0.075f * a0));
        }
        WorldDraw.end();
        // The letters: lit as they're sung (all lit without word timing), a slow shimmer through them.
        float sung = line.sung < 0f ? Float.MAX_VALUE : line.sung;
        float iw = 1f / sheet.width, ih = 1f / sheet.height;
        WorldDraw.begin(WorldDraw.SKY_ADD, sheet);
        for (int i = 0; i < n; i++) {
            SkyFont.Glyph g = face.glyph(gChar[i]);
            if (g.w() <= 0 || g.h() <= 0) {
                continue;
            }
            float on = Ease.clamp01(sung - i);
            float shimmer = 0.9f + 0.1f * (float) Math.sin(t * 1.7f + i * 0.73f + gRow[i] * 1.9f);
            int c = Colors.mix(dim, lit, on);
            float alpha = Ease.lerp(0.55f, 1f, on) * shimmer * a0;
            float x0 = gx0[i] + g.dx() * k, y0 = gBase[i] + g.dy() * k;
            NightSky.box(x0, y0, x0 + g.w() * k, y0 + g.h() * k, g.x() * iw, g.y() * ih, (g.x() + g.w()) * iw,
                    (g.y() + g.h()) * ih, Colors.withAlpha(c, alpha));
        }
        WorldDraw.end();
        // The spark where the singing is.
        if (line.sung >= 0.2f && line.sung < n - 0.2f) {
            int i = Math.min(n - 1, (int) line.sung);
            float f = line.sung - i, x = gx0[i] + (gx1[i] - gx0[i]) * f, y = gBase[i] - face.ascent * k * 0.5f;
            float s = size * (0.95f + 0.12f * (float) Math.sin(t * 9f));
            WorldDraw.begin(WorldDraw.SKY_ADD, SkyDraw.glow());
            NightSky.box(x - s, y - s, x + s, y + s, Colors.withAlpha(Colors.mix(accent, 0xFFFFFFFF, 0.5f), 0.55f * a0));
            NightSky.box(x - s * 0.3f, y - s * 0.3f, x + s * 0.3f, y + s * 0.3f, Colors.withAlpha(0xFFFFFFFF, 0.8f * a0));
            WorldDraw.end();
        }
    }

    /** Soft round blots along a row (centre x, middle y, width w), overlapping so the middle is even, the ends soft. */
    private static void blots(float mid, float y, float w, float size, float scale, int argb) {
        float hw = size * 1.6f * scale, hh = size * 1.25f * scale;
        int count = Math.max(1, Math.round(w / (size * 1.4f)));
        float step = count > 1 ? w / (count - 1) : 0f, x = mid - (count > 1 ? w / 2f : 0f);
        for (int i = 0; i < count; i++, x += step) {
            NightSky.box(x - hw, y - hh, x + hw, y + hh, argb);
        }
    }

    /** The line hangs ahead of you: its bearing glides after the view's (straight up or down, it stays put). */
    private static void follow(float dt) {
        float fx = -NightSky.vw[6], fz = -NightSky.vw[8];
        if (fx * fx + fz * fz < 0.02f) {
            if (Float.isNaN(az)) {
                az = 0f;
            }
            return;
        }
        float view = (float) Math.toDegrees(Math.atan2(-fx, fz));
        if (Float.isNaN(az) || dt == 0f) {
            az = view;
            return;
        }
        float d = ((view - az) % 360f + 540f) % 360f - 180f;
        az += d * Math.min(1f, dt * 1.6f);
    }

    /** The line it's been given to write, or "" (for the scenario's check). */
    public static String wanted() {
        String w = want;
        return w == null ? "" : w.strip();
    }

    /** The lettering and its picture, read once. */
    private static boolean load() {
        if (font != null && sheet != null && !sheet.released()) {
            return true;
        }
        if (failed) {
            return false;
        }
        try (InputStream d = SkyLyrics.class.getResourceAsStream("/assets/cosmeticsmod/sky/lyrics_text.dat");
             InputStream p = SkyLyrics.class.getResourceAsStream("/assets/cosmeticsmod/sky/lyrics_text.png")) {
            if (d == null || p == null) {
                throw new java.io.IOException("missing from the jar");
            }
            font = SkyFont.read(new java.io.BufferedInputStream(d));
            java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(p);
            int w = img.getWidth(), h = img.getHeight();
            int[] px = img.getRGB(0, 0, w, h, null, 0, w);
            sheet = SolTexture.of("lyrics_text", w, h, true, px);
            return true;
        } catch (java.io.IOException | RuntimeException e) {
            failed = true;
            LogOnce.warn("sky-lyrics", "Couldn't read the sky's lyrics lettering: " + e.getMessage());
            return false;
        }
    }
}
