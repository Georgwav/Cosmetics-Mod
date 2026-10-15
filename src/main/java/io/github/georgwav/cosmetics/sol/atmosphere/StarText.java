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
        String text = shown.length() > 34 ? shown.substring(0, 33) + "." : shown;
        float cell = 0.55f, gap = cell * 4f;
        float width = text.length() * gap - cell;
        float az0 = sunAz + 180f - width / 2f, alt0 = 34f;
        WorldDraw.begin(WorldDraw.SKY_ADD, SkyDraw.glow());
        int color = Colors.mix(tk.accent, 0xFFFFFFFF, 0.55f);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            int[] rows = c < 128 ? FONT[c] : null;
            if (rows == null) {
                continue;
            }
            for (int r = 0; r < 5; r++) {
                for (int col = 0; col < 3; col++) {
                    if ((rows[r] >> (2 - col) & 1) == 0) {
                        continue;
                    }
                    float az = az0 + i * gap + col * cell, alt = alt0 + (4 - r) * cell;
                    float tw = 0.65f + 0.35f * (float) Math.sin(t * 2.3f + i * 1.7f + r * 2.1f + col * 0.9f);
                    int c2 = Colors.withAlpha(color, Math.min(1f, 0.85f * tw * alpha * night));
                    float s = 0.16f;
                    SkyDraw.altAz(alt - s, az - s, 0f, 1f, c2);
                    SkyDraw.altAz(alt + s, az - s, 0f, 0f, c2);
                    SkyDraw.altAz(alt + s, az + s, 1f, 0f, c2);
                    SkyDraw.altAz(alt - s, az + s, 1f, 1f, c2);
                }
            }
        }
        WorldDraw.end();
    }
}
