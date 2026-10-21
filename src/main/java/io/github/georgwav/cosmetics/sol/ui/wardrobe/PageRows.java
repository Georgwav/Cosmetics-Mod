package io.github.georgwav.cosmetics.sol.ui.wardrobe;

import io.github.georgwav.cosmetics.sol.theme.Tokens;
import io.github.georgwav.cosmetics.sol.ui.kit.Gfx;
import io.github.georgwav.cosmetics.sol.ui.kit.SolFont;
import io.github.georgwav.cosmetics.sol.ui.kit.Ui;
import io.github.georgwav.cosmetics.sol.util.Colors;

import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * The rows of a Wardrobe page, one after another down a scrolling column: section headers, then a label (with a
 * dim note) on the left and its control on the right, as the Sol menu's drawer rows. With a search, only rows whose
 * label (or section) matches show, with their section's header, as the Cosmetics screen's settings search did.
 */
final class PageRows {
    static final float ROW = 38f;

    private final Gfx g;
    private final Ui ui;
    private final Tokens tk;
    private final float x, w;
    float y;
    private final String needle;
    private String pending;
    private boolean sectionMatches = true;
    private boolean any;

    PageRows(Gfx g, Ui ui, Tokens tk, float x, float y, float w, String search) {
        this.g = g;
        this.ui = ui;
        this.tk = tk;
        this.x = x;
        this.y = y;
        this.w = w;
        String s = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        this.needle = s.isEmpty() ? null : s;
    }

    /** Whether anything showed (for "No setting matches"). */
    boolean any() {
        return any;
    }

    float right() {
        return x + w;
    }

    float left() {
        return x;
    }

    float width() {
        return w;
    }

    void section(String title) {
        if (needle != null) {
            pending = title;
            sectionMatches = title.toLowerCase(Locale.ROOT).contains(needle);
            return;
        }
        header(title);
    }

    private void header(String title) {
        y += any ? 14f : 2f;
        SolFont f = g.font(SolFont.Family.PIXEL, 11f);
        g.textSpaced(f, title.toUpperCase(Locale.ROOT), x, y + 10f, 1.1f, 0x80FFFFFF);
        y += 22f;
    }

    /** Whether a row with this label shows (a search may leave it out); shows its section's header first. */
    private boolean shows(String label) {
        if (needle != null && !sectionMatches && !label.toLowerCase(Locale.ROOT).contains(needle)) {
            return false;
        }
        if (pending != null) {
            String h = pending;
            pending = null;
            header(h);
        }
        any = true;
        return true;
    }

    /** A row's label and note; returns the row's middle, or NaN when the search leaves it out. */
    float row(String label, String note) {
        if (!shows(label)) {
            return Float.NaN;
        }
        float cy = y + ROW / 2f;
        SolFont f = g.font(SolFont.Family.MEDIUM, 13f);
        float lw = g.width(f, label);
        g.textMid(f, g.fit(f, label, w * 0.55f), x, cy, 0xFFFFFFFF);
        if (note != null && !note.isEmpty()) {
            SolFont n = g.font(SolFont.Family.MEDIUM, 11.5f);
            g.textMid(n, g.fit(n, note, w * 0.5f - Math.min(lw, w * 0.55f) - 10f), x + Math.min(lw, w * 0.55f) + 10f, cy,
                    Colors.withAlpha(Tokens.AMBER, 0.9f));
        }
        g.rect(x, y + ROW - 1f, w, Math.max(1f / g.d.u, 1f), 0x0DFFFFFF);
        y += ROW;
        return cy;
    }

    void toggle(String label, String note, boolean on, Runnable flip) {
        float cy = row(label, note);
        if (!Float.isNaN(cy)) {
            ui.smallSwitch(tk, right() - 30f, cy - 9f, on, flip);
        }
    }

    void slider(String label, String note, float value01, String text, Consumer<Float> set) {
        float cy = row(label, note);
        if (!Float.isNaN(cy)) {
            ui.slider(tk, right(), cy, value01, text, set);
        }
    }

    /** One of a few: side by side when they fit, else one button stepping through them. */
    void choice(String label, String note, String[] names, int selected, IntConsumer pick) {
        float cy = row(label, note);
        if (Float.isNaN(cy)) {
            return;
        }
        float total = 6f + ui.segmentedWidth(names);
        if (total <= w * 0.6f) {
            ui.segmented(tk, right(), cy, names, selected, pick);
        } else {
            int sel = Math.max(0, selected);
            ui.button(tk, right(), cy, names[sel] + "  ▸", false, () -> pick.accept((sel + 1) % names.length));
        }
    }

    void button(String label, String note, String text, boolean accent, Runnable action) {
        float cy = row(label, note);
        if (!Float.isNaN(cy)) {
            ui.button(tk, right(), cy, text, accent, action);
        }
    }

    void info(String label, String value, int color) {
        float cy = row(label, null);
        if (!Float.isNaN(cy)) {
            SolFont f = g.font(SolFont.Family.PIXEL, 11f);
            g.textRight(f, g.fit(f, value, w * 0.42f), right(), cy, color);
        }
    }

    /** A line of text (wrapped), not a setting: hints and notices. */
    void text(String line, int color) {
        if (needle != null) {
            return;
        }
        SolFont f = g.font(SolFont.Family.MEDIUM, 12f);
        for (String l : wrap(g, f, line, w)) {
            g.text(f, l, x, y + 14f, color);
            y += 17f;
        }
        y += 6f;
        any = true;
    }

    /** Room for a control of its own (a colour picker, a grid); returns its top. */
    float block(String label, float height) {
        if (label != null && !shows(label)) {
            return Float.NaN;
        }
        if (label == null) {
            any = true;
        }
        float top = y;
        y += height;
        return top;
    }

    static java.util.List<String> wrap(Gfx g, SolFont f, String text, float width) {
        java.util.List<String> out = new java.util.ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String next = line.length() == 0 ? word : line + " " + word;
            if (g.width(f, next) > width && line.length() > 0) {
                out.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(next);
            }
        }
        if (line.length() > 0) {
            out.add(line.toString());
        }
        return out;
    }
}
