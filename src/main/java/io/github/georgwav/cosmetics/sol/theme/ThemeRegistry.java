package io.github.georgwav.cosmetics.sol.theme;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.georgwav.cosmetics.CosmeticsMod;
import io.github.georgwav.cosmetics.sol.util.LogOnce;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Every theme from every resource pack: {@code assets/<namespace>/atmospheres/<name>.json}. Read on startup and on
 * every resource reload (F3+T). The mod's own themes come first in their built-in order, then pack themes A–Z.
 * A theme file's {@code keyframes} hold its dawn, day, dusk and night versions, each merged over the file itself; the
 * file's own values are the version its {@code show.phase} names.
 */
public final class ThemeRegistry {
    public static final String DIRECTORY = "atmospheres";
    /** The theme used when a profile names one that isn't loaded. */
    public static final String FALLBACK = "deep_field";
    /** The prototypes' short ids, so profiles and commands may use them. */
    private static final Map<String, String> ALIASES = Map.of("sakura", "sakura_dusk", "frost", "frost_morning",
            "ember", "ember_solstice", "firefly", "firefly_meadow", "maple", "maple_afternoon", "mono", "deep_field",
            "silver", "silver_night");

    /** The four times of day a theme can describe. */
    public enum Phase {
        DAWN, DAY, DUSK, NIGHT;

        public static Phase of(String name) {
            return switch (name) {
                case "dawn" -> DAWN;
                case "dusk" -> DUSK;
                case "night" -> NIGHT;
                default -> DAY;
            };
        }
    }

    /** One loaded theme: its own values, its four times of day, and how many particles each time of day has. */
    public record Entry(String id, ThemeSpec base, ThemeSpec[] phases, float[] particleScale, boolean builtIn) {
        public ThemeSpec phase(Phase phase) {
            return phases[phase.ordinal()];
        }

        public String name() {
            return base.name();
        }

        /** The same theme with every time of day changed by {@code change} (customisation). */
        public Entry map(java.util.function.UnaryOperator<ThemeSpec> change) {
            ThemeSpec[] out = new ThemeSpec[phases.length];
            for (int i = 0; i < phases.length; i++) {
                out[i] = phases[i] == base ? null : change.apply(phases[i]);
            }
            ThemeSpec newBase = change.apply(base);
            for (int i = 0; i < out.length; i++) {
                if (out[i] == null) {
                    out[i] = newBase;
                }
            }
            return new Entry(id, newBase, out, particleScale, builtIn);
        }
    }

    /** A theme file to read: its id, its text, and whether it is one of the mod's own. */
    public record Source(String id, String text, boolean builtIn) {
    }

    private static volatile List<Entry> all = List.of();
    private static volatile Map<String, Entry> byId = Map.of();
    private static volatile int generation;

    private ThemeRegistry() {
    }

    /** Reads every theme file from the resource packs (render thread, during a resource reload). */
    public static void reload(ResourceManager manager) {
        List<Source> sources = new ArrayList<>();
        for (Map.Entry<?, Resource> e : manager.listResources(DIRECTORY, location -> location.getPath().endsWith(".json"))
                .entrySet()) {
            String key = e.getKey().toString();
            int colon = key.indexOf(':');
            String namespace = key.substring(0, colon);
            String path = key.substring(colon + 1 + DIRECTORY.length() + 1, key.length() - ".json".length());
            boolean builtIn = CosmeticsMod.MOD_ID.equals(namespace);
            String id = builtIn ? path : namespace + ":" + path;
            try (Reader reader = e.getValue().openAsReader()) {
                StringBuilder text = new StringBuilder();
                char[] buffer = new char[4096];
                int n;
                while ((n = reader.read(buffer)) > 0) {
                    text.append(buffer, 0, n);
                }
                sources.add(new Source(id, text.toString(), builtIn));
            } catch (IOException | RuntimeException ex) {
                LogOnce.warn("theme-read:" + id, "Couldn't read the theme {}: {}", id, ex.toString());
            }
        }
        load(sources, (id, message) -> LogOnce.warn("theme:" + id + ":" + message, "Theme {}: {}", id, message));
    }

    /**
     * Reads every moon look from the resource packs ({@code assets/<namespace>/moon_looks/<name>.json}; a look from
     * another pack is named {@code namespace:name}).
     */
    public static void reloadMoonLooks(ResourceManager manager) {
        String dir = "moon_looks";
        Map<String, String> files = new LinkedHashMap<>();
        for (Map.Entry<?, Resource> e : manager.listResources(dir, location -> location.getPath().endsWith(".json")).entrySet()) {
            String key = e.getKey().toString();
            int colon = key.indexOf(':');
            String namespace = key.substring(0, colon);
            String path = key.substring(colon + 1 + dir.length() + 1, key.length() - ".json".length());
            String id = CosmeticsMod.MOD_ID.equals(namespace) ? path : namespace + ":" + path;
            try (Reader reader = e.getValue().openAsReader()) {
                StringBuilder text = new StringBuilder();
                char[] buffer = new char[4096];
                int n;
                while ((n = reader.read(buffer)) > 0) {
                    text.append(buffer, 0, n);
                }
                files.put(id, text.toString());
            } catch (IOException | RuntimeException ex) {
                LogOnce.warn("moon-look-read:" + id, "Couldn't read the moon look {}: {}", id, ex.toString());
            }
        }
        io.github.georgwav.cosmetics.sol.sky.MoonLooks.load(files,
                (id, message) -> LogOnce.warn("moon-look:" + id + ":" + message, "Moon look {}: {}", id, message));
    }

    /** Replaces the loaded themes with these files. Bad values are reported through {@code warn} (id, message). */
    public static void load(List<Source> sources, BiConsumer<String, String> warn) {
        List<Entry> entries = new ArrayList<>();
        for (Source source : sources) {
            entries.add(parse(source, message -> warn.accept(source.id(), message)));
        }
        entries.sort(Comparator.comparing((Entry e) -> !e.builtIn())
                .thenComparingInt(e -> e.builtIn() ? e.base().order() : 0)
                .thenComparing(e -> e.base().name().toLowerCase(java.util.Locale.ROOT))
                .thenComparing(Entry::id));
        Map<String, Entry> map = new LinkedHashMap<>();
        for (Entry e : entries) {
            map.put(e.id(), e);
        }
        all = List.copyOf(entries);
        byId = map;
        generation++;
    }

    /** Reads one theme file and its keyframes. Never throws. */
    public static Entry parse(Source source, Consumer<String> warn) {
        JsonObject json;
        try {
            JsonElement root = JsonParser.parseString(source.text());
            json = root.isJsonObject() ? root.getAsJsonObject() : null;
        } catch (RuntimeException e) {
            json = null;
        }
        if (json == null) {
            warn.accept("not a JSON object");
            json = new JsonObject();
        }
        JsonObject keyframes = json.has("keyframes") && json.get("keyframes").isJsonObject()
                ? json.getAsJsonObject("keyframes") : new JsonObject();
        JsonObject own = json.deepCopy();
        own.remove("keyframes");
        ThemeSpec base = ThemeParser.parse(source.id(), own, ThemeParser.DEFAULTS, warn);
        ThemeSpec[] phases = new ThemeSpec[4];
        float[] scale = {1f, 1f, 1f, 1f};
        Phase show = Phase.of(base.show().phase());
        for (Phase phase : Phase.values()) {
            String key = phase.name().toLowerCase(java.util.Locale.ROOT);
            JsonElement frame = keyframes.get(key);
            if (phase == show || frame == null || !frame.isJsonObject()) {
                if (frame != null && phase != show && !frame.isJsonObject()) {
                    warn.accept("keyframes." + key + " must be an object");
                }
                phases[phase.ordinal()] = base;
                continue;
            }
            JsonObject over = frame.getAsJsonObject().deepCopy();
            if (over.has("particleScale")) {
                try {
                    scale[phase.ordinal()] = Math.max(0f, Math.min(3f, over.get("particleScale").getAsFloat()));
                } catch (RuntimeException e) {
                    warn.accept("keyframes." + key + ".particleScale must be a number");
                }
                over.remove("particleScale");
            }
            phases[phase.ordinal()] = ThemeParser.parse(source.id(), ThemeParser.merge(own, over), ThemeParser.DEFAULTS,
                    message -> warn.accept("keyframes." + key + ": " + message));
        }
        return new Entry(source.id(), base, phases, scale, source.builtIn());
    }

    public static List<Entry> all() {
        return all;
    }

    /** The theme with this id (or a prototype alias), or null. */
    public static Entry find(String id) {
        if (id == null) {
            return null;
        }
        Entry e = byId.get(id);
        return e != null ? e : byId.get(ALIASES.getOrDefault(id, id));
    }

    /** The theme with this id, or the fallback theme, or (before any theme loaded) a plain default. */
    public static Entry get(String id) {
        Entry e = find(id);
        if (e == null) {
            e = byId.get(FALLBACK);
        }
        if (e == null) {
            List<Entry> list = all;
            e = list.isEmpty() ? DEFAULT_ENTRY : list.get(0);
        }
        return e;
    }

    /** The canonical id for an id or alias. */
    public static String canonical(String id) {
        return id == null ? null : ALIASES.getOrDefault(id, id);
    }

    /** Bumped on every load, so caches built from themes know to rebuild. */
    public static int generation() {
        return generation;
    }

    private static final Entry DEFAULT_ENTRY = new Entry("empty", ThemeParser.DEFAULTS,
            new ThemeSpec[]{ThemeParser.DEFAULTS, ThemeParser.DEFAULTS, ThemeParser.DEFAULTS, ThemeParser.DEFAULTS},
            new float[]{1f, 1f, 1f, 1f}, true);
}
