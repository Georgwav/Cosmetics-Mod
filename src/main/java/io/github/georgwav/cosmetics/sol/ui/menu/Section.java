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

    /** Another section was opened instead of this one. */
    default void left() {
    }

    /**
     * After the menu's frame, in the game's GUI pixels and drawing tools: for what only the game draws (the 3D player).
     * Not called while the command bar or the profiles cover the section.
     */
    default void game(SolMenu menu, io.github.georgwav.cosmetics.ui.Canvas canvas, int mouseX, int mouseY, float dt) {
    }

    /** The menu closed: free textures. */
    default void closed() {
    }
}
