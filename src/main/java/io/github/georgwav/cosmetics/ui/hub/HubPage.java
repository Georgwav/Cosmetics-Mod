package io.github.georgwav.cosmetics.ui.hub;

/** A page the hub opens over its sphere (Mods, Skins): its contents, and what the island and search bar say. */
interface HubPage {
    /** Draws the contents inside the open panel. */
    void draw(Ui ui, String query);

    /** Whether (x, y) in design pixels is over the part that scrolls. */
    boolean overList(float x, float y);

    void scrollBy(double amount);

    /** The island's name, line and icon while the page is open. */
    String title();

    String line();

    String icon();

    /** The search field's hint. */
    String searchHint();

    /**
     * The page's panels when open, in design pixels: {centre x, centre y, width, height, wave seed} each. One wide
     * panel unless the page has its own shape (the Wardrobe's two blobs).
     */
    default float[][] panels() {
        return new float[][]{{720f, 412f, 1280f, 640f, 1.2f}};
    }

    /** A button of the page's own in the island (next to Done), or null; and what it does. */
    default String actionLabel() {
        return null;
    }

    default void action() {
    }
}
