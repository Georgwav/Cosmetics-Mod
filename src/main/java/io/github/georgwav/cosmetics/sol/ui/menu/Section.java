package io.github.georgwav.cosmetics.sol.ui.menu;

import io.github.georgwav.cosmetics.sol.theme.Tokens;
import io.github.georgwav.cosmetics.sol.ui.kit.Gfx;
import io.github.georgwav.cosmetics.sol.ui.kit.Ui;

/** One of the full menu's seven sections, drawn into the content area under the horizon. */
public interface Section {
    /** Draws the section in the content rectangle (design pixels). */
    void draw(SolMenu menu, Gfx g, Ui ui, Tokens tk, float x, float y, float w, float h);

    /** Scrolling over the content; true if taken. */
    default boolean scroll(float amount) {
        return false;
    }

    /** Text typed while the section has a text field focused; true if taken. */
    default boolean typed(String text) {
        return false;
    }

    /** A key while the section has a text field focused; true if taken. */
    default boolean key(int key, boolean ctrl) {
        return false;
    }

    /** The section was opened (again). */
    default void opened() {
    }

    /** The menu closed: free textures. */
    default void closed() {
    }
}
