package io.github.georgwav.cosmetics.ui.widget;

import io.github.georgwav.cosmetics.ui.Canvas;
import io.github.georgwav.cosmetics.ui.Theme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** The row widgets of the settings screen: one label on the left, one control on the right. */
public final class Rows {
    private Rows() {
    }

    private static int textY(FlatWidget w, Font font) {
        return w.getY() + (w.getHeight() - font.lineHeight) / 2 + 1;
    }

    /** Small grey section title. */
    public static final class Section extends FlatWidget {
        private final String title;

        public Section(int x, int y, int width, String title) {
            super(x, y, width, Theme.SECTION_HEIGHT, Component.literal(title));
            this.title = title;
            this.active = false;
        }

        @Override
        protected void draw(Canvas g, Font font, int mouseX, int mouseY, boolean hovered) {
            int y = getY() + getHeight() - font.lineHeight - 1;
            text(g, font, title, getX() + 4, y, Theme.TEXT_DIM);
            int lineX = getX() + 10 + font.width(title);
            rect(g, lineX, y + font.lineHeight / 2, getX() + getWidth() - lineX, 1, Theme.DIVIDER);
        }
    }

    /** Read-only label/value line. */
    public static final class Info extends FlatWidget {
        private final String label;
        private final Supplier<String> value;
        private final int valueColor;

        public Info(int x, int y, int width, String label, Supplier<String> value, int valueColor) {
            super(x, y, width, Theme.ROW_HEIGHT, Component.literal(label));
            this.label = label;
            this.value = value;
            this.valueColor = valueColor;
            this.active = false;
        }

        @Override
        protected void draw(Canvas g, Font font, int mouseX, int mouseY, boolean hovered) {
            String v = value.get();
            int ty = textY(this, font);
            text(g, font, fit(font, label, getWidth() - font.width(v) - 20), getX() + 6, ty, Theme.TEXT);
            textRight(g, font, v, getX() + getWidth() - 6, ty, valueColor);
        }
    }

    /**
     * On/off switch. The whole row is the click target, except the arrow at the right end when the
     * row has an {@link #onEdit edit action}.
     */
    public static final class Toggle extends FlatWidget {
        private static final int EDIT_WIDTH = 18;
        private final String label;
        private final String detail;
        private final BooleanSupplier getter;
        private final Consumer<Boolean> setter;
        private Runnable edit;

        public Toggle(int x, int y, int width, String label, String detail, BooleanSupplier getter, Consumer<Boolean> setter) {
            super(x, y, width, Theme.ROW_HEIGHT, Component.literal(label));
            this.label = label;
            this.detail = detail;
            this.getter = getter;
            this.setter = setter;
        }

        /** Adds an arrow at the right end of the row that runs {@code action} (e.g. open a detail page). */
        public Toggle onEdit(Runnable action) {
            this.edit = action;
            return this;
        }

        private boolean overEdit(double mouseX) {
            return edit != null && mouseX >= getX() + getWidth() - EDIT_WIDTH;
        }

        @Override
        protected void draw(Canvas g, Font font, int mouseX, int mouseY, boolean hovered) {
            if (hovered) {
                rect(g, getX(), getY(), getWidth(), getHeight(), Theme.ROW_HOVER);
            }
            boolean on = getter.getAsBoolean();
            int ty = textY(this, font);
            int editW = edit != null ? EDIT_WIDTH : 0;
            if (edit != null) {
                boolean overArrow = hovered && overEdit(mouseX);
                if (overArrow) {
                    rect(g, getX() + getWidth() - EDIT_WIDTH, getY(), EDIT_WIDTH, getHeight(), Theme.ROW_HOVER);
                }
                g.centeredText(font, ">", getX() + getWidth() - EDIT_WIDTH / 2, ty,
                        overArrow ? Theme.ACCENT_HOVER : Theme.TEXT_DIM);
            }
            int switchW = 22;
            int available = getWidth() - switchW - 24 - editW;
            String shownLabel = fit(font, label, available);
            text(g, font, shownLabel, getX() + 6, ty, Theme.TEXT);
            if (detail != null && !detail.isEmpty()) {
                int detailX = getX() + 12 + font.width(shownLabel);
                int detailSpace = getX() + getWidth() - switchW - 12 - editW - detailX;
                if (detailSpace > 20) {
                    text(g, font, fit(font, detail, detailSpace), detailX, ty, Theme.TEXT_FAINT);
                }
            }
            int sx = getX() + getWidth() - switchW - 6 - editW;
            int sy = getY() + (getHeight() - 10) / 2;
            rect(g, sx, sy, switchW, 10, on ? (hovered ? Theme.ACCENT_HOVER : Theme.ACCENT) : (hovered ? Theme.TRACK_HOVER : Theme.TRACK));
            rect(g, on ? sx + switchW - 9 : sx + 1, sy + 1, 8, 8, Theme.KNOB);
        }

        @Override
        protected void pressed(double mouseX, double mouseY) {
            if (overEdit(mouseX)) {
                edit.run();
            } else {
                setter.accept(!getter.getAsBoolean());
            }
        }
    }

    /**
     * A key bind: the key's name; click it, then press a key (or a mouse button other than the left) to change it,
     * Escape for none. The arrow at the end puts back the default. A key another bind also uses shows in orange.
     */
    public static final class KeyBind extends FlatWidget {
        private static final int RESET_WIDTH = 18;
        private final String label;
        private final Supplier<String> keyName;
        private final BooleanSupplier conflicts;
        private final BooleanSupplier listening;
        private final Runnable listen;
        private final Runnable reset;

        public KeyBind(int x, int y, int width, String label, Supplier<String> keyName, BooleanSupplier conflicts,
                       BooleanSupplier listening, Runnable listen, Runnable reset) {
            super(x, y, width, Theme.ROW_HEIGHT, Component.literal(label));
            this.label = label;
            this.keyName = keyName;
            this.conflicts = conflicts;
            this.listening = listening;
            this.listen = listen;
            this.reset = reset;
        }

        private boolean overReset(double mouseX) {
            return mouseX >= getX() + getWidth() - RESET_WIDTH;
        }

        @Override
        protected void draw(Canvas g, Font font, int mouseX, int mouseY, boolean hovered) {
            if (hovered) {
                rect(g, getX(), getY(), getWidth(), getHeight(), Theme.ROW_HOVER);
            }
            int ty = textY(this, font);
            boolean overReset = hovered && overReset(mouseX);
            if (overReset) {
                rect(g, getX() + getWidth() - RESET_WIDTH, getY(), RESET_WIDTH, getHeight(), Theme.ROW_HOVER);
            }
            g.centeredText(font, "\u21BA", getX() + getWidth() - RESET_WIDTH / 2, ty,
                    overReset ? Theme.ACCENT_HOVER : Theme.TEXT_DIM);
            int boxW = 86;
            int boxX = getX() + getWidth() - RESET_WIDTH - 4 - boxW;
            text(g, font, fit(font, label, boxX - getX() - 12), getX() + 6, ty, Theme.TEXT);
            boolean waiting = listening.getAsBoolean();
            rect(g, boxX, getY() + 2, boxW, getHeight() - 4, waiting ? Theme.ACCENT : (hovered && !overReset ? Theme.TRACK_HOVER : Theme.TRACK));
            String name = fit(font, keyName.get(), boxW - 16);
            int color = waiting ? Theme.KNOB : conflicts.getAsBoolean() ? Theme.WARN : Theme.TEXT;
            g.centeredText(font, waiting ? "> " + name + " <" : name, boxX + boxW / 2, ty, color);
        }

        @Override
        protected void pressed(double mouseX, double mouseY) {
            if (overReset(mouseX)) {
                reset.run();
            } else {
                listen.run();
            }
        }
    }

    /**
     * A colour picker: a saturation/brightness square and a hue bar to drag on, the colour with its hex code,
     * a row of quick picks, and "Default" to go back to the cosmetic's own colour.
     */
    public static final class ColorPicker extends FlatWidget {
        public static final int HEIGHT = 96;
        private static final int BAR = 10;
        private static final int SWATCH = 12;
        private final IntSupplier getter;
        private final IntConsumer setter;
        private final Runnable reset;
        private final List<Integer> presets;
        private final String defaultLabel;
        /** The colour being edited, as hue, saturation, value, so dragging to black keeps the hue. */
        private float hue;
        private float sat;
        private float val;
        private int shown = -1;
        /** What the press started on: 1 the square, 2 the hue bar, 0 nothing to drag. */
        private int dragging;

        public ColorPicker(int x, int y, int width, IntSupplier getter, IntConsumer setter, Runnable reset,
                           List<Integer> presets, String defaultLabel) {
            super(x, y, width, HEIGHT, Component.empty());
            this.getter = getter;
            this.setter = setter;
            this.reset = reset;
            this.presets = presets;
            this.defaultLabel = defaultLabel;
        }

        private int squareWidth() {
            return Math.max(60, Math.min(160, getWidth() / 2 - 24));
        }

        private int squareX() {
            return getX();
        }

        private int squareY() {
            return getY() + 4;
        }

        private int squareHeight() {
            return getHeight() - 8;
        }

        private int barX() {
            return squareX() + squareWidth() + 6;
        }

        private int panelX() {
            return barX() + BAR + 10;
        }

        /** Takes over the colour when it was changed elsewhere (the quick picks, Default, another screen). */
        private void sync() {
            int rgb = getter.getAsInt() & 0xFFFFFF;
            if (rgb != shown) {
                float[] hsv = io.github.georgwav.cosmetics.render.Tint.rgbToHsv(rgb);
                // A grey or black has no hue of its own: keep the one being dragged.
                if (hsv[1] > 0.001f && hsv[2] > 0.001f) {
                    hue = hsv[0];
                }
                if (hsv[2] > 0.001f) {
                    sat = hsv[1];
                }
                val = hsv[2];
                shown = rgb;
            }
        }

        private void apply() {
            int rgb = io.github.georgwav.cosmetics.render.Tint.hsvToRgb(hue, sat, val);
            shown = rgb;
            setter.accept(rgb);
        }

        @Override
        protected void draw(Canvas g, Font font, int mouseX, int mouseY, boolean hovered) {
            sync();
            int sx = squareX(), sy = squareY(), sw = squareWidth(), sh = squareHeight();
            // The square: saturation left to right, brightness top to bottom, two pixels a column.
            for (int i = 0; i < sw; i += 2) {
                int top = 0xFF000000 | io.github.georgwav.cosmetics.render.Tint.hsvToRgb(hue, (i + 1f) / sw, 1f);
                g.gradient(sx + i, sy, Math.min(sx + i + 2, sx + sw), sy + sh, top, 0xFF000000);
            }
            int cx = sx + Math.round(sat * (sw - 1));
            int cy = sy + Math.round((1f - val) * (sh - 1));
            rect(g, cx - 2, cy - 2, 5, 1, 0xFFFFFFFF);
            rect(g, cx - 2, cy + 2, 5, 1, 0xFFFFFFFF);
            rect(g, cx - 2, cy - 1, 1, 3, 0xFFFFFFFF);
            rect(g, cx + 2, cy - 1, 1, 3, 0xFFFFFFFF);
            // The hue bar, red at the top round to red at the bottom.
            int bx = barX();
            for (int k = 0; k < 6; k++) {
                int y1 = sy + sh * k / 6;
                int y2 = sy + sh * (k + 1) / 6;
                g.gradient(bx, y1, bx + BAR, y2, 0xFF000000 | io.github.georgwav.cosmetics.render.Tint.hsvToRgb(k / 6f, 1f, 1f),
                        0xFF000000 | io.github.georgwav.cosmetics.render.Tint.hsvToRgb(((k + 1) % 6) / 6f, 1f, 1f));
            }
            int hy = sy + Math.round(hue * (sh - 1));
            rect(g, bx - 2, hy - 1, BAR + 4, 3, 0xFFFFFFFF);
            rect(g, bx - 1, hy, BAR + 2, 1, 0xFF000000);
            // The colour, its hex code, the quick picks and Default.
            int px = panelX();
            rect(g, px - 1, sy - 1, 34, 22, Theme.PANEL_BORDER);
            rect(g, px, sy, 32, 20, 0xFF000000 | shown);
            text(g, font, String.format(java.util.Locale.ROOT, "#%06X", shown), px + 40, sy + 6, Theme.TEXT);
            int perRow = Math.max(1, Math.min(presets.size(), (getX() + getWidth() - px) / (SWATCH + 3)));
            for (int i = 0; i < presets.size(); i++) {
                int x = px + (i % perRow) * (SWATCH + 3);
                int y = sy + 28 + (i / perRow) * (SWATCH + 3);
                boolean over = mouseX >= x && mouseX < x + SWATCH && mouseY >= y && mouseY < y + SWATCH;
                rect(g, x - 1, y - 1, SWATCH + 2, SWATCH + 2, over ? Theme.ACCENT_HOVER : Theme.PANEL_BORDER);
                rect(g, x, y, SWATCH, SWATCH, 0xFF000000 | presets.get(i));
            }
            int dy = sy + sh - font.lineHeight;
            boolean overDefault = mouseX >= px && mouseX < px + font.width(defaultLabel) && mouseY >= dy - 2
                    && mouseY < dy + font.lineHeight;
            text(g, font, defaultLabel, px, dy, overDefault ? Theme.ACCENT_HOVER : Theme.TEXT_DIM);
        }

        @Override
        protected void pressed(double mouseX, double mouseY) {
            sync();
            dragging = 0;
            int sy = squareY(), sh = squareHeight();
            if (mouseX >= squareX() && mouseX < squareX() + squareWidth() && mouseY >= sy && mouseY < sy + sh) {
                dragging = 1;
            } else if (mouseX >= barX() - 2 && mouseX < barX() + BAR + 2 && mouseY >= sy && mouseY < sy + sh) {
                dragging = 2;
            }
            if (dragging != 0) {
                dragged(mouseX, mouseY);
                return;
            }
            int px = panelX();
            int perRow = Math.max(1, Math.min(presets.size(), (getX() + getWidth() - px) / (SWATCH + 3)));
            for (int i = 0; i < presets.size(); i++) {
                int x = px + (i % perRow) * (SWATCH + 3);
                int y = sy + 28 + (i / perRow) * (SWATCH + 3);
                if (mouseX >= x && mouseX < x + SWATCH && mouseY >= y && mouseY < y + SWATCH) {
                    setter.accept(presets.get(i));
                    return;
                }
            }
            if (mouseX >= px && mouseY >= sy + sh - 12) {
                reset.run();
            }
        }

        @Override
        protected void dragged(double mouseX, double mouseY) {
            int sy = squareY(), sh = squareHeight();
            if (dragging == 1) {
                sat = clamp01((float) (mouseX - squareX()) / (squareWidth() - 1));
                val = 1f - clamp01((float) (mouseY - sy) / (sh - 1));
                apply();
            } else if (dragging == 2) {
                hue = Math.min(0.9999f, clamp01((float) (mouseY - sy) / (sh - 1)));
                apply();
            }
        }

        private static float clamp01(float v) {
            return Math.max(0f, Math.min(1f, v));
        }
    }

    /** Picks one of a few values; click the left half to go back, the right half to go forward. */
    public static final class Cycle<T> extends FlatWidget {
        private final String label;
        private final List<T> values;
        private final Supplier<T> getter;
        private final Consumer<T> setter;
        private final Function<T, String> names;
        private Function<T, Integer> swatch;

        public Cycle(int x, int y, int width, String label, List<T> values, Supplier<T> getter, Consumer<T> setter,
                     Function<T, String> names) {
            super(x, y, width, Theme.ROW_HEIGHT, Component.literal(label));
            this.label = label;
            this.values = values;
            this.getter = getter;
            this.setter = setter;
            this.names = names;
        }

        /** Shows a small square in each value's colour (ARGB) next to its name. */
        public Cycle<T> withSwatch(Function<T, Integer> colors) {
            this.swatch = colors;
            return this;
        }

        private int valueLeft(Font font) {
            int widest = 0;
            for (T value : values) {
                widest = Math.max(widest, font.width(names.apply(value)));
            }
            return getX() + getWidth() - 6 - widest - 24 - (swatch != null ? 12 : 0);
        }

        @Override
        protected void draw(Canvas g, Font font, int mouseX, int mouseY, boolean hovered) {
            if (hovered) {
                rect(g, getX(), getY(), getWidth(), getHeight(), Theme.ROW_HOVER);
            }
            int ty = textY(this, font);
            int left = valueLeft(font);
            text(g, font, fit(font, label, left - getX() - 12), getX() + 6, ty, Theme.TEXT);
            int right = getX() + getWidth() - 6;
            T current = getter.get();
            String value = names.apply(current);
            int center = (left + right) / 2;
            text(g, font, "<", left, ty, hovered && mouseX < center ? Theme.ACCENT_HOVER : Theme.TEXT_FAINT);
            textRight(g, font, ">", right, ty, hovered && mouseX >= center ? Theme.ACCENT_HOVER : Theme.TEXT_FAINT);
            if (swatch != null) {
                int textLeft = center - font.width(value) / 2;
                int sy = getY() + (getHeight() - 8) / 2;
                rect(g, textLeft - 13, sy - 1, 10, 10, Theme.PANEL_BORDER);
                rect(g, textLeft - 12, sy, 8, 8, swatch.apply(current));
            }
            g.centeredText(font, value, center, ty, Theme.ACCENT);
        }

        @Override
        protected void pressed(double mouseX, double mouseY) {
            int index = values.indexOf(getter.get());
            int left = valueLeft(net.minecraft.client.Minecraft.getInstance().font);
            int center = (left + getX() + getWidth() - 6) / 2;
            int step = mouseX < center && mouseX >= left ? -1 : 1;
            setter.accept(values.get(Math.floorMod(index + step, values.size())));
        }
    }

    /** Horizontal slider over [min, max] with a fixed step. */
    public static final class Slider extends FlatWidget {
        private static final int TRACK_WIDTH = 90;
        private final String label;
        private final double min;
        private final double max;
        private final double step;
        private final DoubleSupplier getter;
        private final DoubleConsumer setter;
        private final Function<Double, String> format;

        public Slider(int x, int y, int width, String label, double min, double max, double step,
                      DoubleSupplier getter, DoubleConsumer setter, Function<Double, String> format) {
            super(x, y, width, Theme.ROW_HEIGHT, Component.literal(label));
            this.label = label;
            this.min = min;
            this.max = max;
            this.step = step;
            this.getter = getter;
            this.setter = setter;
            this.format = format;
        }

        private int trackLeft() {
            return getX() + getWidth() - 6 - TRACK_WIDTH;
        }

        @Override
        protected void draw(Canvas g, Font font, int mouseX, int mouseY, boolean hovered) {
            if (hovered) {
                rect(g, getX(), getY(), getWidth(), getHeight(), Theme.ROW_HOVER);
            }
            int ty = textY(this, font);
            String value = format.apply(getter.getAsDouble());
            int valueRight = trackLeft() - 8;
            text(g, font, fit(font, label, valueRight - font.width(value) - getX() - 16), getX() + 6, ty, Theme.TEXT);
            textRight(g, font, value, valueRight, ty, Theme.TEXT_DIM);
            int tx = trackLeft();
            int ty2 = getY() + getHeight() / 2 - 1;
            double t = (getter.getAsDouble() - min) / (max - min);
            int filled = (int) Math.round(Math.max(0, Math.min(1, t)) * TRACK_WIDTH);
            rect(g, tx, ty2, TRACK_WIDTH, 3, hovered ? Theme.TRACK_HOVER : Theme.TRACK);
            rect(g, tx, ty2, filled, 3, hovered ? Theme.ACCENT_HOVER : Theme.ACCENT);
            rect(g, tx + filled - 2, ty2 - 3, 4, 9, Theme.KNOB);
        }

        private void setFromMouse(double mouseX) {
            double t = (mouseX - trackLeft()) / TRACK_WIDTH;
            t = Math.max(0, Math.min(1, t));
            double raw = min + t * (max - min);
            double snapped = Math.round(raw / step) * step;
            setter.accept(Math.max(min, Math.min(max, snapped)));
        }

        @Override
        protected void pressed(double mouseX, double mouseY) {
            if (mouseX >= trackLeft() - 4) {
                setFromMouse(mouseX);
            }
        }

        @Override
        protected void dragged(double mouseX, double mouseY) {
            setFromMouse(mouseX);
        }
    }

    /** Flat button; {@code primary} fills it with the accent colour. */
    public static final class Button extends FlatWidget {
        private final String label;
        private final boolean primary;
        private final Runnable action;

        public Button(int x, int y, int width, int height, String label, boolean primary, Runnable action) {
            super(x, y, width, height, Component.literal(label));
            this.label = label;
            this.primary = primary;
            this.action = action;
        }

        @Override
        protected void draw(Canvas g, Font font, int mouseX, int mouseY, boolean hovered) {
            if (primary) {
                rect(g, getX(), getY(), getWidth(), getHeight(), hovered ? Theme.ACCENT_HOVER : Theme.ACCENT);
            } else {
                rect(g, getX(), getY(), getWidth(), getHeight(), hovered ? Theme.TRACK_HOVER : Theme.TRACK);
            }
            int ty = getY() + (getHeight() - font.lineHeight) / 2 + 1;
            g.centeredText(font, fit(font, label, getWidth() - 8), getX() + getWidth() / 2, ty,
                    primary ? 0xFF0B0D12 : Theme.TEXT);
        }

        @Override
        protected void pressed(double mouseX, double mouseY) {
            action.run();
        }
    }

    /** Text tab with an accent underline when selected. */
    public static final class Tab extends FlatWidget {
        private final String label;
        private final BooleanSupplier selected;
        private final Runnable action;

        public Tab(int x, int y, int width, int height, String label, BooleanSupplier selected, Runnable action) {
            super(x, y, width, height, Component.literal(label));
            this.label = label;
            this.selected = selected;
            this.action = action;
        }

        @Override
        protected void draw(Canvas g, Font font, int mouseX, int mouseY, boolean hovered) {
            boolean on = selected.getAsBoolean();
            int ty = getY() + (getHeight() - font.lineHeight) / 2;
            g.centeredText(font, label, getX() + getWidth() / 2, ty,
                    on ? Theme.TEXT : (hovered ? Theme.TEXT : Theme.TEXT_DIM));
            if (on) {
                int w = font.width(label);
                rect(g, getX() + (getWidth() - w) / 2, getY() + getHeight() - 2, w, 2, Theme.ACCENT);
            }
        }

        @Override
        protected void pressed(double mouseX, double mouseY) {
            action.run();
        }
    }

    /** A category in a side bar: its name, how many it holds, and an accent bar when it's the open one. */
    public static final class SideTab extends FlatWidget {
        private final String label;
        private final IntSupplier count;
        private final BooleanSupplier selected;
        private final Runnable action;

        public SideTab(int x, int y, int width, int height, String label, IntSupplier count, BooleanSupplier selected,
                       Runnable action) {
            super(x, y, width, height, Component.literal(label));
            this.label = label;
            this.count = count;
            this.selected = selected;
            this.action = action;
        }

        @Override
        protected void draw(Canvas g, Font font, int mouseX, int mouseY, boolean hovered) {
            boolean on = selected.getAsBoolean();
            if (on || hovered) {
                rect(g, getX(), getY(), getWidth(), getHeight(), Theme.ROW_HOVER);
            }
            if (on) {
                rect(g, getX(), getY(), 2, getHeight(), Theme.ACCENT);
            }
            int ty = textY(this, font);
            int n = count.getAsInt();
            String number = n >= 0 ? String.valueOf(n) : "";
            textRight(g, font, number, getX() + getWidth() - 5, ty, Theme.TEXT_FAINT);
            text(g, font, fit(font, label, getWidth() - 16 - font.width(number)), getX() + 8, ty,
                    on || hovered ? Theme.TEXT : Theme.TEXT_DIM);
        }

        @Override
        protected void pressed(double mouseX, double mouseY) {
            action.run();
        }
    }

    /** A label, a faint detail, and a few small buttons at the right end, each with its own action. */
    public static final class Actions extends FlatWidget {
        private static final int GAP = 4;
        private final String label;
        private final Supplier<String> detail;
        private final List<String> names;
        private final List<Runnable> actions;

        public Actions(int x, int y, int width, String label, Supplier<String> detail, List<String> names,
                       List<Runnable> actions) {
            super(x, y, width, Theme.ROW_HEIGHT, Component.literal(label));
            this.label = label;
            this.detail = detail;
            this.names = names;
            this.actions = actions;
        }

        /** Left edge of each button, then the right edge of the last. */
        private int[] edges(Font font) {
            int[] edges = new int[names.size() + 1];
            int right = getX() + getWidth() - 4;
            edges[names.size()] = right;
            for (int i = names.size() - 1; i >= 0; i--) {
                right -= font.width(names.get(i)) + 10;
                edges[i] = right;
                right -= GAP;
            }
            return edges;
        }

        @Override
        protected void draw(Canvas g, Font font, int mouseX, int mouseY, boolean hovered) {
            if (hovered) {
                rect(g, getX(), getY(), getWidth(), getHeight(), Theme.ROW_HOVER);
            }
            int ty = textY(this, font);
            int[] edges = edges(font);
            String shown = fit(font, label, edges[0] - getX() - 16);
            text(g, font, shown, getX() + 6, ty, Theme.TEXT);
            String extra = detail.get();
            int detailX = getX() + 12 + font.width(shown);
            if (extra != null && !extra.isEmpty() && edges[0] - 8 - detailX > 20) {
                text(g, font, fit(font, extra, edges[0] - 8 - detailX), detailX, ty, Theme.TEXT_FAINT);
            }
            for (int i = 0; i < names.size(); i++) {
                int left = edges[i];
                int right = i + 1 < names.size() ? edges[i + 1] - GAP : edges[names.size()];
                boolean over = hovered && mouseX >= left && mouseX < right;
                rect(g, left, getY() + 3, right - left, getHeight() - 6, over ? Theme.TRACK_HOVER : Theme.TRACK);
                g.centeredText(font, names.get(i), (left + right) / 2, ty, over ? Theme.ACCENT_HOVER : Theme.TEXT);
            }
        }

        @Override
        protected void pressed(double mouseX, double mouseY) {
            int[] edges = edges(net.minecraft.client.Minecraft.getInstance().font);
            for (int i = 0; i < names.size(); i++) {
                int right = i + 1 < names.size() ? edges[i + 1] - GAP : edges[names.size()];
                if (mouseX >= edges[i] && mouseX < right) {
                    actions.get(i).run();
                    return;
                }
            }
        }
    }

    /** An invisible area that reports how far the mouse moved sideways while held, e.g. to turn the preview. */
    public static final class DragArea extends FlatWidget {
        /** How far the mouse moved while dragging, in GUI pixels. */
        @FunctionalInterface
        public interface Drag {
            void moved(double dx, double dy);
        }

        private final Drag onDrag;
        private double lastX;
        private double lastY;

        public DragArea(int x, int y, int width, int height, Drag onDrag) {
            super(x, y, width, height, Component.empty());
            this.onDrag = onDrag;
        }

        @Override
        protected void draw(Canvas g, Font font, int mouseX, int mouseY, boolean hovered) {
        }

        @Override
        public void playDownSound(SoundManager sounds) {
            // Grabbing the preview isn't a button press.
        }

        @Override
        protected void pressed(double mouseX, double mouseY) {
            lastX = mouseX;
            lastY = mouseY;
        }

        @Override
        protected void dragged(double mouseX, double mouseY) {
            onDrag.moved(mouseX - lastX, mouseY - lastY);
            lastX = mouseX;
            lastY = mouseY;
        }
    }
}
