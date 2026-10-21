package io.github.georgwav.cosmetics.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.List;
import java.util.function.DoubleFunction;

/**
 * One setting of a client module: a switch, a slider, a choice, a line of text, a colour or a small picture. Its name and hint are
 * translation keys ({@code cosmeticsmod.module.<module>.<setting>} and {@code ...hint}); its value is saved with the
 * module. Settings are shown in groups on the module's page ({@link #group}), the advanced ones folded at the end.
 */
public abstract class Setting<T> {
    public final String id;
    public final T def;
    protected T value;
    /** Called after the value changes. */
    Runnable onChange = () -> {
    };
    /** Shown under "Advanced" on the module's page, folded by default. */
    public boolean advanced;
    /** The group it's shown in on the module's page ({@code cosmeticsmod.group.<group>}). */
    public String group = "general";

    protected Setting(String id, T def) {
        this.id = id;
        this.def = def;
        this.value = def;
    }

    public T get() {
        return value;
    }

    /** Puts it in a group on the module's page ("look", "behaviour", ...). */
    @SuppressWarnings("unchecked")
    public <S extends Setting<T>> S group(String name) {
        group = name;
        return (S) this;
    }

    /**
     * Its default before this one: a value saved by an older version that is still that default reads as the default
     * now (once, see {@link Modules#DEFAULTS}), so a changed default reaches everyone who never picked their own.
     */
    Object oldDefault;

    @SuppressWarnings("unchecked")
    public <S extends Setting<T>> S was(T oldDefault) {
        this.oldDefault = oldDefault;
        return (S) this;
    }

    /** Whether the module's page shows it now (a colour picker only while its colour is "Custom", say). */
    private java.util.function.BooleanSupplier shown;

    /** Shows it on the module's page only while {@code when} says so. */
    @SuppressWarnings("unchecked")
    public <S extends Setting<T>> S shownWhen(java.util.function.BooleanSupplier when) {
        shown = when;
        return (S) this;
    }

    /** Also needs {@code when} to be shown (on top of what it already needs). */
    public void alsoShownWhen(java.util.function.BooleanSupplier when) {
        java.util.function.BooleanSupplier old = shown;
        shown = old == null ? when : () -> when.getAsBoolean() && old.getAsBoolean();
    }

    public boolean shown() {
        return shown == null || shown.getAsBoolean();
    }

    /** Marks this setting as advanced (folded away in the drawer). */
    @SuppressWarnings("unchecked")
    public <S extends Setting<T>> S advanced() {
        advanced = true;
        return (S) this;
    }

    public void set(T v) {
        T next = clean(v);
        if (!next.equals(value)) {
            value = next;
            onChange.run();
            Modules.save();
        }
    }

    public void reset() {
        set(def);
    }

    public boolean changed() {
        return !value.equals(def);
    }

    /** The value as the setting allows it (a slider keeps it in range). */
    protected T clean(T v) {
        return v;
    }

    abstract JsonElement save();

    abstract void load(JsonElement json);

    /**
     * Loads a saved value. Saved under older defaults ({@link Modules#DEFAULTS}) and still the old default, it takes
     * today's default instead, as on a fresh install (nothing is saved or told).
     */
    void loadSaved(JsonElement json, boolean olderDefaults) {
        load(json);
        if (olderDefaults && oldDefault != null && java.util.Objects.equals(value, oldDefault)) {
            value = def;
        }
    }

    /** On or off. */
    public static final class Toggle extends Setting<Boolean> {
        public Toggle(String id, boolean def) {
            super(id, def);
        }

        @Override
        JsonElement save() {
            return new JsonPrimitive(value);
        }

        @Override
        void load(JsonElement json) {
            if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isBoolean()) {
                value = json.getAsBoolean();
            }
        }
    }

    /** A number from min to max in steps; {@code label} shows it ("8x", "06:00"). */
    public static final class Slider extends Setting<Double> {
        public final double min;
        public final double max;
        public final double step;
        public final DoubleFunction<String> label;

        public Slider(String id, double def, double min, double max, double step, DoubleFunction<String> label) {
            super(id, def);
            this.min = min;
            this.max = max;
            this.step = step;
            this.label = label;
        }

        @Override
        protected Double clean(Double v) {
            double stepped = step > 0 ? Math.round((v - min) / step) * step + min : v;
            return Math.max(min, Math.min(max, stepped));
        }

        public String text() {
            return label.apply(value);
        }

        @Override
        JsonElement save() {
            return new JsonPrimitive(value);
        }

        @Override
        void load(JsonElement json) {
            if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber()) {
                value = clean(json.getAsDouble());
            }
        }
    }

    /** One of a few options, by id; each option's name is {@code cosmeticsmod.module.<module>.<setting>.<option>}. */
    public static final class Choice extends Setting<String> {
        public final List<String> options;

        public Choice(String id, String def, List<String> options) {
            super(id, def);
            this.options = options;
        }

        @Override
        protected String clean(String v) {
            return options.contains(v) ? v : def;
        }

        @Override
        JsonElement save() {
            return new JsonPrimitive(value);
        }

        @Override
        void load(JsonElement json) {
            if (json.isJsonPrimitive()) {
                value = clean(json.getAsString());
            }
        }
    }

    /** A line of text (up to {@code max} characters), such as the Discord application id. */
    public static final class Text extends Setting<String> {
        public final int max;

        public Text(String id, String def, int max) {
            super(id, def);
            this.max = max;
        }

        @Override
        protected String clean(String v) {
            if (v == null) {
                return def;
            }
            String flat = v.replaceAll("[\\r\\n\\t]", " ");
            return flat.length() > max ? flat.substring(0, max) : flat;
        }

        @Override
        JsonElement save() {
            return new JsonPrimitive(value);
        }

        @Override
        void load(JsonElement json) {
            if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()) {
                value = clean(json.getAsString());
            }
        }
    }

    /**
     * A small square picture of pixels that are on or off, {@code size} by {@code size} (a painted crosshair): saved
     * as its rows one after another, a '1' for each pixel that's on.
     */
    public static final class Pixels extends Setting<String> {
        public final int size;

        public Pixels(String id, int size, String def) {
            super(id, normal(def, size));
            this.size = size;
        }

        private static String normal(String v, int size) {
            StringBuilder b = new StringBuilder(size * size);
            for (int i = 0; i < size * size; i++) {
                b.append(v != null && i < v.length() && v.charAt(i) == '1' ? '1' : '0');
            }
            return b.toString();
        }

        @Override
        protected String clean(String v) {
            return normal(v, size);
        }

        /** Whether the pixel at (x, y) is on (outside the picture: off). */
        public boolean at(int x, int y) {
            return x >= 0 && y >= 0 && x < size && y < size && value.charAt(y * size + x) == '1';
        }

        /** Turns the pixel at (x, y) on or off. */
        public void put(int x, int y, boolean on) {
            set(with(value, size, x, y, on));
        }

        /** Turns on the pixel at (x, y) and every off pixel joined to it (side by side), as a paint bucket. */
        public void fill(int x, int y) {
            set(filled(value, size, x, y));
        }

        /** {@code v} with the pixel at (x, y) on or off (outside it: unchanged). */
        static String with(String v, int size, int x, int y, boolean on) {
            if (x < 0 || y < 0 || x >= size || y >= size) {
                return v;
            }
            char[] c = v.toCharArray();
            c[y * size + x] = on ? '1' : '0';
            return new String(c);
        }

        /** {@code v} with the off pixels joined to (x, y) turned on (an on pixel there, or outside: unchanged). */
        static String filled(String v, int size, int x, int y) {
            if (x < 0 || y < 0 || x >= size || y >= size || v.charAt(y * size + x) == '1') {
                return v;
            }
            char[] c = v.toCharArray();
            int[] stack = new int[size * size];
            int n = 0;
            stack[n++] = y * size + x;
            c[y * size + x] = '1';
            while (n > 0) {
                int i = stack[--n];
                int px = i % size, py = i / size;
                if (px > 0 && c[i - 1] == '0') {
                    c[i - 1] = '1';
                    stack[n++] = i - 1;
                }
                if (px < size - 1 && c[i + 1] == '0') {
                    c[i + 1] = '1';
                    stack[n++] = i + 1;
                }
                if (py > 0 && c[i - size] == '0') {
                    c[i - size] = '1';
                    stack[n++] = i - size;
                }
                if (py < size - 1 && c[i + size] == '0') {
                    c[i + size] = '1';
                    stack[n++] = i + size;
                }
            }
            return new String(c);
        }

        /** All off. */
        public void clear() {
            set("");
        }

        @Override
        JsonElement save() {
            return new JsonPrimitive(value);
        }

        @Override
        void load(JsonElement json) {
            if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()) {
                value = clean(json.getAsString());
            }
        }
    }

    /**
     * A colour (ARGB), saved as {@code #RRGGBB}, or {@code #AARRGGBB} when it keeps its own opacity. A colour that
     * can follow the theme ({@link #themed}) saves "theme" and is the theme's accent while it does.
     */
    public static final class Color extends Setting<Integer> {
        /** The value of a themed colour that follows the theme's accent (no real colour is fully see-through black). */
        public static final int THEME = 0;
        public final boolean alpha;
        /** Whether it can follow the theme's accent. */
        public boolean themed;
        /** Colour names saved by older versions (when it was a list of names), and what each is now. */
        private java.util.Map<String, Integer> names = java.util.Map.of();

        public Color(String id, int def, boolean alpha) {
            super(id, def == THEME ? THEME : alpha ? def : def | 0xFF000000);
            this.alpha = alpha;
            this.themed = def == THEME;
        }

        public Color(String id, int def) {
            this(id, def, false);
        }

        /** Its default before this one (see {@link Setting#was}). */
        public Color was(int oldDefault) {
            this.oldDefault = alpha ? oldDefault : oldDefault | 0xFF000000;
            return this;
        }

        /** Lets it follow the theme's accent (the "Theme" choice by its picker). */
        public Color themed() {
            themed = true;
            return this;
        }

        /** The colour names an older version saved, each read as this colour ({@link #THEME} for "accent"). */
        public Color names(java.util.Map<String, Integer> legacy) {
            names = legacy;
            return this;
        }

        /** Whether it follows the theme now. */
        public boolean followsTheme() {
            return themed && value == THEME;
        }

        /** The colour to draw with, ARGB: the theme's accent while it follows the theme. */
        public int argb() {
            if (followsTheme()) {
                return 0xFF000000 | io.github.georgwav.cosmetics.sol.theme.ThemeState.tokens().accent;
            }
            return value;
        }

        @Override
        protected Integer clean(Integer v) {
            if (v == null) {
                return def;
            }
            if (themed && v == THEME) {
                return THEME;
            }
            return alpha ? v : v | 0xFF000000;
        }

        @Override
        JsonElement save() {
            if (followsTheme()) {
                return new JsonPrimitive("theme");
            }
            return new JsonPrimitive(alpha ? String.format(java.util.Locale.ROOT, "#%08X", value)
                    : String.format(java.util.Locale.ROOT, "#%06X", value & 0xFFFFFF));
        }

        @Override
        void load(JsonElement json) {
            if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()) {
                String s = json.getAsString();
                if (s.equals("theme") && themed) {
                    value = THEME;
                    return;
                }
                Integer named = names.get(s);
                if (named != null) {
                    value = clean(named);
                    return;
                }
                String h = s.startsWith("#") ? s.substring(1) : s;
                try {
                    if (h.length() == 6) {
                        value = clean((int) Long.parseLong(h, 16) | 0xFF000000);
                    } else if (h.length() == 8) {
                        value = clean((int) Long.parseLong(h, 16));
                    }
                } catch (NumberFormatException e) {
                    // Not a colour: keep the default.
                }
            }
        }
    }
}
