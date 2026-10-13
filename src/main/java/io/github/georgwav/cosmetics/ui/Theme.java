package io.github.georgwav.cosmetics.ui;

/**
 * Colours (ARGB, always with alpha: 1.21.6+ treats a missing alpha as fully transparent) and metrics
 * for the settings screen. Flat, dark, one accent colour.
 */
public final class Theme {
    public static final int BACKDROP = 0xC80A0B0E;
    public static final int PANEL = 0xFF14161B;
    public static final int PANEL_BORDER = 0xFF252932;
    public static final int DIVIDER = 0xFF20232B;
    public static final int ROW_HOVER = 0x16FFFFFF;
    public static final int PREVIEW_BG = 0xFF101216;
    /** A wardrobe tile, and the one the mouse is on. */
    public static final int TILE = 0xFF1A1D24;
    public static final int TILE_HOVER = 0xFF232731;

    public static final int TEXT = 0xFFE7E9EF;
    public static final int TEXT_DIM = 0xFF8A90A0;
    public static final int TEXT_FAINT = 0xFF5D6270;

    public static final int ACCENT = 0xFF6E8BFF;
    public static final int ACCENT_HOVER = 0xFF8BA2FF;
    public static final int TRACK = 0xFF2C303A;
    public static final int TRACK_HOVER = 0xFF363B47;
    public static final int KNOB = 0xFFF4F5F8;
    public static final int GOOD = 0xFF5BD69B;
    public static final int WARN = 0xFFE8B05A;

    public static final int PAD = 12;
    public static final int ROW_HEIGHT = 20;
    public static final int ROW_GAP = 2;
    public static final int SECTION_HEIGHT = 18;
    public static final int HEADER_HEIGHT = 30;
    public static final int FOOTER_HEIGHT = 30;
    public static final int PREVIEW_WIDTH = 112;

    private Theme() {
    }
}
