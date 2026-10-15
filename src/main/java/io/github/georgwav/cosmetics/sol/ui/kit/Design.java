package io.github.georgwav.cosmetics.sol.ui.kit;

/**
 * The menus' design space: the prototypes' 1280 x 720 game screen, scaled to fit the window and centred. Sol draws
 * in screen pixels (the GUI transform scaled by 1 / GUI scale), so shapes are smooth and text is sharp at every GUI
 * scale and window size; every size in the kit is in design pixels and turned into screen pixels here.
 */
public final class Design {
    public static final float W = 1280f;
    public static final float H = 720f;

    /** Screen pixels per design pixel. */
    public float u = 1f;
    /** Where design (0, 0) is on the screen, in screen pixels. */
    public float ox;
    public float oy;
    /** The game's GUI scale (screen pixels per GUI unit). */
    public float gs = 1f;
    /** The window in screen pixels. */
    public float screenW = W;
    public float screenH = H;

    /** Fits the design into a {@code guiWidth} x {@code guiHeight} GUI (in GUI units) at {@code guiScale}. */
    public void update(int guiWidth, int guiHeight, double guiScale) {
        gs = (float) guiScale;
        screenW = guiWidth * gs;
        screenH = guiHeight * gs;
        u = Math.min(screenW / W, screenH / H);
        ox = (screenW - W * u) / 2f;
        oy = (screenH - H * u) / 2f;
    }

    /**
     * Design units = the game's GUI units, from the window's top left: the HUD's layout, where Sol's glass sits
     * exactly where the game's own HUD parts are.
     */
    public void gui(int guiWidth, int guiHeight, double guiScale) {
        gs = (float) guiScale;
        screenW = guiWidth * gs;
        screenH = guiHeight * gs;
        u = gs;
        ox = 0f;
        oy = 0f;
    }

    public float sx(float x) {
        return ox + x * u;
    }

    public float sy(float y) {
        return oy + y * u;
    }

    /** A GUI-unit mouse position as design x. */
    public float mouseX(double guiX) {
        return ((float) guiX * gs - ox) / u;
    }

    public float mouseY(double guiY) {
        return ((float) guiY * gs - oy) / u;
    }

    /** The window's left and right edges in design x (beyond 0 and 1280 on wide windows). */
    public float left() {
        return -ox / u;
    }

    public float right() {
        return (screenW - ox) / u;
    }

    public float top() {
        return -oy / u;
    }

    public float bottom() {
        return (screenH - oy) / u;
    }

    /** A design x snapped to the nearest screen pixel, still in design units. */
    public float snapX(float x) {
        return (Math.round(sx(x)) - ox) / u;
    }

    public float snapY(float y) {
        return (Math.round(sy(y)) - oy) / u;
    }
}
