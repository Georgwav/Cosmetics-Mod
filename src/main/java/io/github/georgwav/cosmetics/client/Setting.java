package io.github.georgwav.cosmetics.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.List;
import java.util.function.DoubleFunction;

/**
 * One setting of a client module: a switch, a slider or a choice. Its name and hint are translation keys
 * ({@code cosmeticsmod.module.<module>.<setting>} and {@code ...hint}); its value is saved with the module.
 */
public abstract class Setting<T> {
    public final String id;
    public final T def;
    protected T value;
    /** Called after the value changes. */
    Runnable onChange = () -> {
    };
    /** Shown under "Advanced settings" in the Sol drawer, folded by default. */
    public boolean advanced;

    protected Setting(String id, T def) {
        this.id = id;
        this.def = def;
        this.value = def;
    }

    public T get() {
        return value;
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

    /** A line of text (up to {@code max} characters): what an auto-text key sends, a word to be mentioned by. */
    public static class Text extends Setting<String> {
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
     * A secret line (an API key): kept in the owner-only secrets file, never in a profile (profiles get shared), and
     * shown masked in the drawer.
     */
    public static final class Secret extends Text {
        private final String key;

        public Secret(String id, String secretKey, int max) {
            super(id, "", max);
            this.key = secretKey;
        }

        @Override
        public String get() {
            String v = io.github.georgwav.cosmetics.sol.config.Secrets.get(key);
            return v == null ? "" : v;
        }

        @Override
        public void set(String v) {
            String next = clean(v).strip();
            if (!next.equals(get())) {
                io.github.georgwav.cosmetics.sol.config.Secrets.put(key, next);
                onChange.run();
            }
        }

        @Override
        public boolean changed() {
            return false;
        }

        @Override
        JsonElement save() {
            return null;
        }

        @Override
        void load(JsonElement json) {
        }
    }
}
