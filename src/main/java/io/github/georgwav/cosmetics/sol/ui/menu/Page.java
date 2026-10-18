package io.github.georgwav.cosmetics.sol.ui.menu;

import io.github.georgwav.cosmetics.sol.theme.Tokens;
import io.github.georgwav.cosmetics.sol.ui.kit.Gfx;
import io.github.georgwav.cosmetics.sol.ui.kit.Ui;

/** A page over the whole menu, the menu dimmed behind it: a module's settings ({@link ModulePage}) or a theme's ({@link ThemePage}). */
interface Page {
    void draw(SolMenu menu, Gfx g, Ui ui, Tokens tk);

    /** The game's own drawing over the page (a module's live preview). */
    default void game(io.github.georgwav.cosmetics.ui.Canvas c) {
    }

    boolean scroll(float amount);

    boolean key(int key, boolean ctrl);

    boolean typed(String text);

    void release();
}
