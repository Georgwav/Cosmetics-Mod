package io.github.georgwav.cosmetics.sol.ui.quick;

import io.github.georgwav.cosmetics.sol.util.Colors;

/**
 * The quick menu's light: where the sun stands on the dial lights the whole screen. Noon is at the top, dawn and
 * dusk at the sides and night at the bottom; the total eclipse behind you turns it all to a black sky with stars.
 * A port of the Sol menu prototype's {@code QSKY} keys and {@code qLight()}: the same keys, the same blend, the same
 * derived values its CSS variables carried. Pure maths, so it is tested without the game.
 */
public final class QuickLight {
    /** Values per key: sky top, horizon, bottom, light (rgb each), light alpha, horizon glow rgb, its alpha, stars, lit. */
    static final int N = 19;
    private static final float[] ANGLES = {-180, -150, -120, -90, -60, -30, 0, 30, 60, 90, 120, 150, 180};
    private static final float[][] KEYS = {
            key("#272a5e", "#a84c76", "#0c0710", "#ff9cb4", .30f, "#ff8fb0", .8f, .3f, .55f),   // dawn, left horizon
            key("#3a5394", "#b97670", "#100b10", "#ffcfb4", .34f, "#ffad90", .62f, 0f, .8f),    // morning: HUD
            key("#6a82b4", "#a08060", "#120e0c", "#ffe6c4", .42f, "#ffd0a0", .32f, 0f, .92f),
            key("#d6a654", "#7e5c38", "#140e09", "#fff2cc", .5f, "#ffe2b0", .3f, 0f, 1f),       // noon: Modules
            key("#b98a4a", "#86592f", "#130d09", "#ffd89c", .46f, "#ffbf70", .4f, 0f, .96f),
            key("#4b3c7c", "#bd6434", "#130909", "#ffa24a", .42f, "#ff8a3a", .85f, 0f, .9f),     // golden hour: World
            key("#28265c", "#a94634", "#0d0609", "#ff7a3a", .34f, "#ff6a30", .95f, .12f, .6f),   // sunset, right horizon
            key("#141842", "#5c2756", "#07060c", "#ff6c86", .2f, "#ff5a76", .5f, .5f, .35f),     // afterglow: Music
            key("#0b1030", "#20204c", "#05050a", "#a6b8ff", .14f, "#6a5aa8", .2f, .85f, .3f),
            key("#070b22", "#0f1b42", "#04050b", "#a8c0ff", .16f, "#3c5cae", .14f, 1f, .32f),    // midnight: Settings
            key("#0b1232", "#1d2350", "#05050b", "#b4aaff", .15f, "#6a5ab0", .2f, .85f, .3f),
            key("#131946", "#4c336a", "#07060c", "#d6a6ff", .18f, "#c87ab8", .42f, .55f, .4f),   // before dawn: Mods
            key("#272a5e", "#a84c76", "#0c0710", "#ff9cb4", .30f, "#ff8fb0", .8f, .3f, .55f)};
    /** Totality: a black sky, stars out, the corona the only light, the horizon glowing all the way round. */
    private static final float[] ECLIPSE = key("#04050c", "#0a0c1c", "#030409", "#dfe6ff", .3f, "#ff8a4a", .48f, .95f, 0f);

    private final float[] ls = new float[N];

    /** Sky: top, horizon (at 44%) and bottom (from 72%) of the screen, each with its alpha. */
    public int skyTop, skyMid, skyBottom;
    /** The light round the sun: colour with its alpha, the bare colour, and the dimmed fill for the dial. */
    public int light, lightRgb, fill;
    /** Where the light is (design pixels), its radius, and the lit gradient's radius for the dial's lines. */
    public float lx, ly, lr, litR;
    /** The horizon glow: colour, alpha, peak, centre x and width; and the horizon line's stops (0..1 across). */
    public int horizon;
    public float ha, hpk, hx, hw, hzFrom, hzPeak, hzTo;
    /** Stars, how lit you are, the eclipse. */
    public float stars, lit, ecl;
    /** Where the light comes from on your disc (unit vector, 0 in totality). */
    public float ux, uy;
    /** Your shadow on the dial: its angle (degrees), length and strength. */
    public float shadowAngle, shadowLength, shadow;
    /** The rim round you. */
    public int rim;

    private static float[] key(String top, String mid, String bot, String lc, float la, String hc, float ha, float st,
                               float lit) {
        float[] k = new float[N];
        put(k, 0, top);
        put(k, 3, mid);
        put(k, 6, bot);
        put(k, 9, lc);
        k[12] = la;
        put(k, 13, hc);
        k[16] = ha;
        k[17] = st;
        k[18] = lit;
        return k;
    }

    private static void put(float[] k, int at, String hex) {
        int c = Colors.parse(hex, 0xFF000000);
        k[at] = Colors.r(c);
        k[at + 1] = Colors.g(c);
        k[at + 2] = Colors.b(c);
    }

    /** -180..180. */
    static float norm(float a) {
        return ((a % 360f) + 540f) % 360f - 180f;
    }

    /** The veil under the menu: heavier on bright worlds (Empty, Frost Morning) so white text keeps its contrast. */
    public static float veil(int skyMiddle) {
        float lum = (Colors.r(skyMiddle) * 0.2126f + Colors.g(skyMiddle) * 0.7152f + Colors.b(skyMiddle) * 0.0722f) / 255f;
        return 0.3f + 0.3f * Math.max(0f, Math.min(1f, (lum - 0.2f) / 0.45f));
    }

    /**
     * The light for the sun at {@code angle} degrees on the dial (its centre at {@code sx}, {@code sy}), the eclipse
     * {@code ec} (0..1) and the theme's glow colour.
     */
    public void update(float angle, float sx, float sy, float ec, int glow) {
        float an = norm(angle);
        int i = 0;
        while (i < ANGLES.length - 2 && an > ANGLES[i + 1]) {
            i++;
        }
        float[] a = KEYS[i], b = KEYS[i + 1];
        float t = (an - ANGLES[i]) / (ANGLES[i + 1] - ANGLES[i]);
        for (int c = 0; c < N; c++) {
            float v = a[c] + (b[c] - a[c]) * t;
            ls[c] = v + (ECLIPSE[c] - v) * ec;
        }
        // A touch of the theme's glow in the light.
        ls[9] += (Colors.r(glow) - ls[9]) * 0.15f * (1f - ec);
        ls[10] += (Colors.g(glow) - ls[10]) * 0.15f * (1f - ec);
        ls[11] += (Colors.b(glow) - ls[11]) * 0.15f * (1f - ec);
        float rad = (float) Math.toRadians(angle);
        float night = Math.max(0f, (float) Math.sin(rad));
        float ca = (float) Math.cos(rad), sa = (float) Math.sin(rad);
        lit = ls[18];
        ecl = ec;
        skyTop = rgba(0, 0.62f);
        skyMid = rgba(3, 0.5f);
        skyBottom = rgba(6, 0.8f);
        lightRgb = rgba(9, 1f);
        light = rgba(9, ls[12]);
        fill = Colors.argb(255, (int) (ls[9] * 0.2f + 10f), (int) (ls[10] * 0.2f + 11f), (int) (ls[11] * 0.2f + 16f));
        lx = sx;
        ly = sy;
        lr = (540f - 150f * night) * (1f - ec) + 330f * ec;
        litR = 430f * (1f - ec) + 230f * ec;
        horizon = rgba(13, 1f);
        ha = ls[16];
        hpk = 0.3f + 0.7f * ha;
        hx = sx + (640f - sx) * ec;
        hw = 330f + 640f * ec;
        float spread = 0.2f + 0.45f * ec;
        float ho = Math.max(0.08f, Math.min(0.92f, hx / 1280f));
        hzFrom = Math.max(0f, ho - spread);
        hzPeak = ho;
        hzTo = Math.min(1f, ho + spread);
        stars = ls[17];
        ux = ca * (1f - ec);
        uy = sa * (1f - ec);
        // You are the gnomon: your shadow falls across the dial away from the sun, long at dawn and dusk, short at noon.
        shadowAngle = angle + 180f;
        shadowLength = 0.62f + 0.5f * (1f - Math.abs(sa));
        shadow = (1f - ec) * (0.2f + 0.6f * lit);
        rim = ec > 0.5f ? Colors.withAlpha(0xFFFFFFFF, 0.3f + 0.7f * ec) : rgba(9, 0.3f + 0.5f * lit);
    }

    private int rgba(int at, float alpha) {
        return Colors.argb(Math.round(Math.max(0f, Math.min(1f, alpha)) * 255f), (int) ls[at], (int) ls[at + 1], (int) ls[at + 2]);
    }
}
