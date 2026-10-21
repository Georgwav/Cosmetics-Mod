package io.github.georgwav.cosmetics.sol.ui.kit;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The main labels the kit had to cut to fit their slab ({@link Gfx#fitLabel}): a tab, a button, a card's or a tile's
 * name. None should be cut; the in-game check reads them per shot and fails on any. Render thread only.
 */
public final class TextCuts {
    private static final int MAX = 64;
    private static final Set<String> CUT = new LinkedHashSet<>();

    private TextCuts() {
    }

    static void record(String label) {
        if (CUT.size() < MAX) {
            CUT.add(label);
        }
    }

    /** The labels cut since the last call, and forgets them. */
    public static List<String> drain() {
        List<String> out = new ArrayList<>(CUT);
        CUT.clear();
        return out;
    }
}
