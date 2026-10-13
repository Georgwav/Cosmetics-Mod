package io.github.georgwav.cosmetics.ui.widget;

import io.github.georgwav.cosmetics.ui.Theme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.Function;
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
        protected void draw(GuiGraphics g, Font font, int mouseX, int mouseY, boolean hovered) {
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
        protected void draw(GuiGraphics g, Font font, int mouseX, int mouseY, boolean hovered) {
            String v = value.get();
            int ty = textY(this, font);
            text(g, font, fit(font, label, getWidth() - font.width(v) - 20), getX() + 6, ty, Theme.TEXT);
            textRight(g, font, v, getX() + getWidth() - 6, ty, valueColor);
        }
    }

    /** On/off switch. The whole row is the click target. */
    public static final class Toggle extends FlatWidget {
        private final String label;
        private final String detail;
        private final BooleanSupplier getter;
        private final Consumer<Boolean> setter;

        public Toggle(int x, int y, int width, String label, String detail, BooleanSupplier getter, Consumer<Boolean> setter) {
            super(x, y, width, Theme.ROW_HEIGHT, Component.literal(label));
            this.label = label;
            this.detail = detail;
            this.getter = getter;
            this.setter = setter;
        }

        @Override
        protected void draw(GuiGraphics g, Font font, int mouseX, int mouseY, boolean hovered) {
            if (hovered) {
                rect(g, getX(), getY(), getWidth(), getHeight(), Theme.ROW_HOVER);
            }
            boolean on = getter.getAsBoolean();
            int ty = textY(this, font);
            int switchW = 22;
            int available = getWidth() - switchW - 24;
            String shownLabel = fit(font, label, available);
            text(g, font, shownLabel, getX() + 6, ty, Theme.TEXT);
            if (detail != null && !detail.isEmpty()) {
                int detailX = getX() + 12 + font.width(shownLabel);
                int detailSpace = getX() + getWidth() - switchW - 12 - detailX;
                if (detailSpace > 20) {
                    text(g, font, fit(font, detail, detailSpace), detailX, ty, Theme.TEXT_FAINT);
                }
            }
            int sx = getX() + getWidth() - switchW - 6;
            int sy = getY() + (getHeight() - 10) / 2;
            rect(g, sx, sy, switchW, 10, on ? (hovered ? Theme.ACCENT_HOVER : Theme.ACCENT) : (hovered ? Theme.TRACK_HOVER : Theme.TRACK));
            rect(g, on ? sx + switchW - 9 : sx + 1, sy + 1, 8, 8, Theme.KNOB);
        }

        @Override
        protected void pressed(double mouseX, double mouseY) {
            setter.accept(!getter.getAsBoolean());
        }
    }

    /** Picks one of a few values; click the left half to go back, the right half to go forward. */
    public static final class Cycle<T> extends FlatWidget {
        private final String label;
        private final List<T> values;
        private final Supplier<T> getter;
        private final Consumer<T> setter;
        private final Function<T, String> names;

        public Cycle(int x, int y, int width, String label, List<T> values, Supplier<T> getter, Consumer<T> setter,
                     Function<T, String> names) {
            super(x, y, width, Theme.ROW_HEIGHT, Component.literal(label));
            this.label = label;
            this.values = values;
            this.getter = getter;
            this.setter = setter;
            this.names = names;
        }

        private int valueLeft(Font font) {
            int widest = 0;
            for (T value : values) {
                widest = Math.max(widest, font.width(names.apply(value)));
            }
            return getX() + getWidth() - 6 - widest - 24;
        }

        @Override
        protected void draw(GuiGraphics g, Font font, int mouseX, int mouseY, boolean hovered) {
            if (hovered) {
                rect(g, getX(), getY(), getWidth(), getHeight(), Theme.ROW_HOVER);
            }
            int ty = textY(this, font);
            int left = valueLeft(font);
            text(g, font, fit(font, label, left - getX() - 12), getX() + 6, ty, Theme.TEXT);
            int right = getX() + getWidth() - 6;
            String value = names.apply(getter.get());
            int center = (left + right) / 2;
            text(g, font, "<", left, ty, hovered && mouseX < center ? Theme.ACCENT_HOVER : Theme.TEXT_FAINT);
            textRight(g, font, ">", right, ty, hovered && mouseX >= center ? Theme.ACCENT_HOVER : Theme.TEXT_FAINT);
            g.drawCenteredString(font, value, center, ty, Theme.ACCENT);
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
        protected void draw(GuiGraphics g, Font font, int mouseX, int mouseY, boolean hovered) {
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
        protected void draw(GuiGraphics g, Font font, int mouseX, int mouseY, boolean hovered) {
            if (primary) {
                rect(g, getX(), getY(), getWidth(), getHeight(), hovered ? Theme.ACCENT_HOVER : Theme.ACCENT);
            } else {
                rect(g, getX(), getY(), getWidth(), getHeight(), hovered ? Theme.TRACK_HOVER : Theme.TRACK);
            }
            int ty = getY() + (getHeight() - font.lineHeight) / 2 + 1;
            g.drawCenteredString(font, fit(font, label, getWidth() - 8), getX() + getWidth() / 2, ty,
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
        protected void draw(GuiGraphics g, Font font, int mouseX, int mouseY, boolean hovered) {
            boolean on = selected.getAsBoolean();
            int ty = getY() + (getHeight() - font.lineHeight) / 2;
            g.drawCenteredString(font, label, getX() + getWidth() / 2, ty,
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
}
