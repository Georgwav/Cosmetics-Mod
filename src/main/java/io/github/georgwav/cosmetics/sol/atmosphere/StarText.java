package io.github.georgwav.cosmetics.sol.atmosphere;

import io.github.georgwav.cosmetics.sol.render.WorldDraw;
import io.github.georgwav.cosmetics.sol.theme.Tokens;
import io.github.georgwav.cosmetics.sol.util.Colors;

import java.util.Locale;

/**
 * The line being sung, written in stars on the album sky (the brief's Album theme, with the album sky on at night):
 * each letter a 3 x 5 grid of twinkling star points, the line centred opposite the sun 34° up, fading in and out as
 * lines change. Fed by the Music module ({@link #line}).
 */
public final class StarText {
    /** The line to write (upper case, or null for none); set by the Music module each frame. */
    public static volatile String line;
    private static String shown = "";
    private static float alpha;
    private static long lastNanos;

    /** 3 x 5 letters, rows top to bottom, bit 2 the left column. */
    private static final int[][] FONT = new int[128][];

    static {
        // Each glyph: the character, then its five rows top to bottom as 3-bit numbers (4 = left, 1 = right).
        String[] glyphs = {"A75755", "B65656", "C74447", "D65556", "E74747", "F74744", "G74557", "H55755", "I72227", "J11157",
                "K55655", "L44447", "M57755", "N65555", "O75557", "P75744", "Q75571", "R75655", "S74717", "T72222", "U55557",
                "V55552", "W55775", "X55255", "Y55222", "Z71247", "075557", "126227", "271747", "371717", "455711", "574717",
                "674757", "771111", "875757", "975717", "'22000", ",00024", ".00002", "!22202", "?71202", "-00700", "(24442",
                ")21112", "&25257", ":02020"};
        for (String g : glyphs) {
            int[] rows = new int[5];
            for (int r = 0; r < 5; r++) {
                rows[r] = g.charAt(1 + r) - '0';
            }
            FONT[g.charAt(0)] = rows;
        }
    }

    private StarText() {
    }

    /** Words into at most two rows of {@code width} letters (the rest cut with a dot). */
    static java.util.List<String> wrap(String text, int width) {
        java.util.List<String> rows = new java.util.ArrayList<>();
        StringBuilder row = new StringBuilder();
        for (String word : text.trim().split("\\s+")) {
            if (row.length() > 0 && row.length() + 1 + word.length() > width) {
                rows.add(row.toString());
                row.setLength(0);
                if (rows.size() == 2) {
                    break;
                }
            }
            if (row.length() > 0) {
                row.append(' ');
            }
            row.append(word.length() > width ? word.substring(0, width) : word);
        }
        if (rows.size() < 2 && row.length() > 0) {
            rows.add(row.toString());
        } else if (rows.size() == 2 && row.length() > 0) {
            String last = rows.get(1);
            rows.set(1, last.length() >= width ? last.substring(0, width - 1) + "." : last + ".");
        }
        return rows;
    }

    /** Draws it (sky pass, additive batch begun by the caller is not assumed: begins its own). */
    static void render(Tokens tk, float sunAz, float t, float rain) {
        long now = System.nanoTime();
        float dt = lastNanos == 0L ? 0f : Math.min(0.1f, (now - lastNanos) / 1e9f);
        lastNanos = now;
        String want = line == null ? "" : line.toUpperCase(Locale.ROOT);
        float night = tk.night * (1f - rain);
        if (!want.equals(shown)) {
            alpha -= dt * 3f;
            if (alpha <= 0f) {
                alpha = 0f;
                shown = want;
            }
        } else if (!shown.isEmpty()) {
            alpha = Math.min(1f, alpha + dt * 1.5f);
        }
        if (shown.isEmpty() || alpha <= 0.01f || night <= 0.05f) {
            return;
        }
        // Up to two rows of 20 letters, centred opposite the sun.
        java.util.List<String> rows = wrap(shown, 20);
        float cell = 0.95f, gap = cell * 4f, rowGap = cell * 7f;
        WorldDraw.begin(WorldDraw.SKY_ADD, SkyDraw.glow());
        int color = Colors.mix(tk.accent, 0xFFFFFFFF, 0.65f);
        for (int row = 0; row < rows.size(); row++) {
            String text = rows.get(row);
            float width = text.length() * gap - cell;
            // Azimuth grows to the left as you face it: the line starts at the larger angle so it reads left to right.
            float az0 = sunAz + 180f + width / 2f, alt0 = 38f - row * rowGap + (rows.size() - 1) * rowGap / 2f;
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                int[] glyph = c < 128 ? FONT[c] : null;
                if (glyph == null) {
                    continue;
                }
                for (int r = 0; r < 5; r++) {
                    for (int col = 0; col < 3; col++) {
                        if ((glyph[r] >> (2 - col) & 1) == 0) {
                            continue;
                        }
                        float az = az0 - i * gap - col * cell, alt = alt0 + (4 - r) * cell;
                        float tw = 0.7f + 0.3f * (float) Math.sin(t * 2.3f + i * 1.7f + r * 2.1f + col * 0.9f + row);
                        int c2 = Colors.withAlpha(color, Math.min(1f, tw * alpha * night));
                        float s = 0.42f;
                        SkyDraw.altAz(alt - s, az - s, 0f, 1f, c2);
                        SkyDraw.altAz(alt + s, az - s, 0f, 0f, c2);
                        SkyDraw.altAz(alt + s, az + s, 1f, 0f, c2);
                        SkyDraw.altAz(alt - s, az + s, 1f, 1f, c2);
                    }
                }
            }
        }
        WorldDraw.end();
    }
}
