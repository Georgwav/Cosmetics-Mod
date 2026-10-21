package io.github.georgwav.cosmetics.sol.styles;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The whole Styles setup: the base pack, each part's choice (where its look comes from, its colours and its shape),
 * single textures taken from other packs (the tree's leaves), and the menu's own memory (favourites, the sort, the
 * folded group). Saved in the profile, so profiles switch it with everything else. Immutable choices; the setup is a
 * value that is copied to change. Plain Java, so it is unit-tested.
 */
public final class StyleSetup {
    /** Where a part's look comes from. */
    public static final String VANILLA = "vanilla", OFF = "off";

    /**
     * The colour changes on a part: none, a fixed recolour, a rainbow, the theme's colour or the glint's (both
     * changing live).
     */
    public enum Paint {
        NONE, RECOLOUR, RAINBOW, THEME, GLINT
    }

    /** Flat (the vanilla model, the chosen texture), extruded (a voxel model of the texture) or one of Sol's models. */
    public enum Shape {
        FLAT, EXTRUDED, MODEL
    }

    /**
     * One part's choice.
     *
     * @param source     "vanilla", "off", "builtin:<id>" or "pack:<key>"
     * @param paint      how its colours change
     * @param hue        degrees, -180..180
     * @param saturation 0..2, 1 as it is
     * @param brightness 0..2, 1 as it is
     * @param tint       RGB painted over it (for {@link Paint#RECOLOUR}), or -1 for none
     * @param shape      flat, extruded or model
     * @param thickness  an extruded model's depth in pixels, 0.5..4
     * @param model      Sol's model for {@link Shape#MODEL} (a cosmetic id), or null
     * @param extra      the part's own settings (the crystal's core and frame colours, the anchor's glow...), as text
     */
    public record Choice(String source, Paint paint, int hue, float saturation, float brightness, int tint, Shape shape,
                         float thickness, String model, Map<String, String> extra) {
        public static final Choice DEFAULT = new Choice(VANILLA, Paint.NONE, 0, 1f, 1f, -1, Shape.FLAT, 1f, null, Map.of());

        public Choice {
            source = source == null ? VANILLA : source;
            paint = paint == null ? Paint.NONE : paint;
            hue = Math.max(-180, Math.min(180, hue));
            saturation = clamp(saturation, 0f, 2f);
            brightness = clamp(brightness, 0f, 2f);
            shape = shape == null ? Shape.FLAT : shape;
            thickness = clamp(thickness, 0.5f, 4f);
            extra = extra == null ? Map.of() : Map.copyOf(new TreeMap<>(extra));
        }

        public boolean isDefault() {
            return equals(DEFAULT);
        }

        /** Whether its colours differ from the source's own. */
        public boolean recoloured() {
            return paint != Paint.NONE || hue != 0 || saturation != 1f || brightness != 1f || tint >= 0;
        }

        public Choice withSource(String s) {
            return new Choice(s, paint, hue, saturation, brightness, tint, shape, thickness, model, extra);
        }

        public Choice withColour(Paint p, int h, float s, float b, int t) {
            return new Choice(source, p, h, s, b, t, shape, thickness, model, extra);
        }

        public Choice withShape(Shape sh, float thick, String m) {
            return new Choice(source, paint, hue, saturation, brightness, tint, sh, thick, m, extra);
        }

        public Choice withExtra(String key, String value) {
            Map<String, String> e = new TreeMap<>(extra);
            if (value == null) {
                e.remove(key);
            } else {
                e.put(key, value);
            }
            return new Choice(source, paint, hue, saturation, brightness, tint, shape, thickness, model, e);
        }

        public String extra(String key, String fallback) {
            return extra.getOrDefault(key, fallback);
        }

        /** The pack's key, when the look comes from a pack. */
        public String packKey() {
            return source.startsWith("pack:") ? source.substring(5) : null;
        }

        /** The built-in's id, when the look is one of Sol's built-ins. */
        public String builtin() {
            return source.startsWith("builtin:") ? source.substring(8) : null;
        }

        private static float clamp(float v, float lo, float hi) {
            return Float.isNaN(v) ? lo : Math.max(lo, Math.min(hi, v));
        }
    }

    private final String base;
    private final Map<String, Choice> parts;
    private final Map<String, String> leaves;
    private final Set<String> favourites;
    private final String sort;
    private final boolean fullOpen;

    public static final StyleSetup EMPTY = new StyleSetup(null, Map.of(), Map.of(), Set.of(), "pack", false);

    public StyleSetup(String base, Map<String, Choice> parts, Map<String, String> leaves, Set<String> favourites, String sort, boolean fullOpen) {
        this.base = base;
        Map<String, Choice> p = new TreeMap<>();
        parts.forEach((k, v) -> {
            if (v != null && !v.isDefault()) {
                p.put(k, v);
            }
        });
        this.parts = java.util.Collections.unmodifiableMap(p);
        this.leaves = java.util.Collections.unmodifiableMap(new TreeMap<>(leaves));
        this.favourites = java.util.Collections.unmodifiableSet(new LinkedHashSet<>(favourites));
        this.sort = sort == null ? "pack" : sort;
        this.fullOpen = fullOpen;
    }

    /** The pack under everything (its key), or null for vanilla. */
    public String base() {
        return base;
    }

    public Choice choice(String part) {
        return parts.getOrDefault(part, Choice.DEFAULT);
    }

    /** The parts that aren't vanilla. */
    public Map<String, Choice> parts() {
        return parts;
    }

    /** Single textures from other packs: path to "pack:<key>" (or "vanilla"). */
    public Map<String, String> leaves() {
        return leaves;
    }

    public Set<String> favourites() {
        return favourites;
    }

    /** How a part's tiles are sorted: "pack", "colour", "recent" or "favourites". */
    public String sort() {
        return sort;
    }

    /** Whether the "From full packs" group is open. */
    public boolean fullOpen() {
        return fullOpen;
    }

    public boolean isEmpty() {
        return base == null && parts.isEmpty() && leaves.isEmpty();
    }

    public StyleSetup withBase(String b) {
        return new StyleSetup(b, parts, leaves, favourites, sort, fullOpen);
    }

    public StyleSetup with(String part, Choice c) {
        Map<String, Choice> p = new TreeMap<>(parts);
        p.put(part, c);
        return new StyleSetup(base, p, leaves, favourites, sort, fullOpen);
    }

    public StyleSetup withLeaf(String path, String source) {
        Map<String, String> l = new TreeMap<>(leaves);
        if (source == null) {
            l.remove(path);
        } else {
            l.put(path, source);
        }
        return new StyleSetup(base, parts, l, favourites, sort, fullOpen);
    }

    public StyleSetup withFavourite(String tile, boolean on) {
        Set<String> f = new LinkedHashSet<>(favourites);
        if (on) {
            f.add(tile);
        } else {
            f.remove(tile);
        }
        return new StyleSetup(base, parts, leaves, f, sort, fullOpen);
    }

    public StyleSetup withSort(String s) {
        return new StyleSetup(base, parts, leaves, favourites, s, fullOpen);
    }

    public StyleSetup withFullOpen(boolean open) {
        return new StyleSetup(base, parts, leaves, favourites, sort, open);
    }

    /** Every part back to vanilla, the base and the leaves too (the menu's memory stays). */
    public StyleSetup cleared() {
        return new StyleSetup(null, Map.of(), Map.of(), favourites, sort, fullOpen);
    }

    // ------------------------------------------------------------------ JSON (the profile's "styles")

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        if (base != null) {
            o.addProperty("base", base);
        }
        JsonObject ps = new JsonObject();
        parts.forEach((k, c) -> ps.add(k, choiceJson(c)));
        o.add("parts", ps);
        JsonObject ls = new JsonObject();
        leaves.forEach(ls::addProperty);
        o.add("leaves", ls);
        JsonArray fav = new JsonArray();
        favourites.forEach(fav::add);
        o.add("favourites", fav);
        o.addProperty("sort", sort);
        o.addProperty("fullOpen", fullOpen);
        return o;
    }

    static JsonObject choiceJson(Choice c) {
        JsonObject j = new JsonObject();
        j.addProperty("source", c.source());
        if (c.paint() != Paint.NONE) {
            j.addProperty("paint", c.paint().name().toLowerCase(java.util.Locale.ROOT));
        }
        if (c.hue() != 0) {
            j.addProperty("hue", c.hue());
        }
        if (c.saturation() != 1f) {
            j.addProperty("saturation", c.saturation());
        }
        if (c.brightness() != 1f) {
            j.addProperty("brightness", c.brightness());
        }
        if (c.tint() >= 0) {
            j.addProperty("tint", String.format("#%06X", c.tint() & 0xFFFFFF));
        }
        if (c.shape() != Shape.FLAT) {
            j.addProperty("shape", c.shape().name().toLowerCase(java.util.Locale.ROOT));
            j.addProperty("thickness", c.thickness());
        }
        if (c.model() != null) {
            j.addProperty("model", c.model());
        }
        if (!c.extra().isEmpty()) {
            JsonObject e = new JsonObject();
            c.extra().forEach(e::addProperty);
            j.add("extra", e);
        }
        return j;
    }

    /** The setup in a profile; bad values fall back to the defaults. */
    public static StyleSetup fromJson(JsonElement el) {
        if (el == null || !el.isJsonObject()) {
            return EMPTY;
        }
        JsonObject o = el.getAsJsonObject();
        String base = str(o, "base", null);
        Map<String, Choice> parts = new LinkedHashMap<>();
        if (o.has("parts") && o.get("parts").isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("parts").entrySet()) {
                if (e.getValue().isJsonObject()) {
                    parts.put(e.getKey(), choiceFrom(e.getValue().getAsJsonObject()));
                }
            }
        }
        Map<String, String> leaves = new LinkedHashMap<>();
        if (o.has("leaves") && o.get("leaves").isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("leaves").entrySet()) {
                if (e.getValue().isJsonPrimitive()) {
                    leaves.put(e.getKey(), e.getValue().getAsString());
                }
            }
        }
        Set<String> fav = new LinkedHashSet<>();
        if (o.has("favourites") && o.get("favourites").isJsonArray()) {
            for (JsonElement e : o.getAsJsonArray("favourites")) {
                if (e.isJsonPrimitive()) {
                    fav.add(e.getAsString());
                }
            }
        }
        boolean open = o.has("fullOpen") && o.get("fullOpen").isJsonPrimitive() && o.get("fullOpen").getAsBoolean();
        return new StyleSetup(base, parts, leaves, fav, str(o, "sort", "pack"), open);
    }

    static Choice choiceFrom(JsonObject j) {
        Paint paint = Paint.NONE;
        try {
            paint = Paint.valueOf(str(j, "paint", "none").toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            // Unknown: none.
        }
        Shape shape = Shape.FLAT;
        try {
            shape = Shape.valueOf(str(j, "shape", "flat").toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            // Unknown: flat.
        }
        int tint = -1;
        String t = str(j, "tint", null);
        if (t != null && t.matches("#?[0-9a-fA-F]{6}")) {
            tint = Integer.parseInt(t.replace("#", ""), 16);
        }
        Map<String, String> extra = new TreeMap<>();
        if (j.has("extra") && j.get("extra").isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : j.getAsJsonObject("extra").entrySet()) {
                if (e.getValue().isJsonPrimitive()) {
                    extra.put(e.getKey(), e.getValue().getAsString());
                }
            }
        }
        return new Choice(str(j, "source", VANILLA), paint, (int) num(j, "hue", 0), (float) num(j, "saturation", 1), (float) num(j, "brightness", 1),
                tint, shape, (float) num(j, "thickness", 1), str(j, "model", null), extra);
    }

    private static String str(JsonObject o, String k, String fallback) {
        JsonElement e = o.get(k);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : fallback;
    }

    private static double num(JsonObject o, String k, double fallback) {
        JsonElement e = o.get(k);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) {
            return fallback;
        }
        return e.getAsDouble();
    }

    /** The packs the setup uses (base, parts, leaves), as keys. */
    public List<String> packsUsed() {
        Set<String> out = new LinkedHashSet<>();
        if (base != null) {
            out.add(base);
        }
        parts.values().forEach(c -> {
            if (c.packKey() != null) {
                out.add(c.packKey());
            }
        });
        leaves.values().forEach(s -> {
            if (s.startsWith("pack:")) {
                out.add(s.substring(5));
            }
        });
        return new ArrayList<>(out);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof StyleSetup s && java.util.Objects.equals(base, s.base) && parts.equals(s.parts) && leaves.equals(s.leaves)
                && favourites.equals(s.favourites) && sort.equals(s.sort) && fullOpen == s.fullOpen;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(base, parts, leaves, favourites, sort, fullOpen);
    }
}
