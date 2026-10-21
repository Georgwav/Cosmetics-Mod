package io.github.georgwav.cosmetics.sol.styles;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sol Styles' parts for this Minecraft version: each thing on screen a style can change, with exactly the files this
 * version has for it (assets/cosmeticsmod/styles/parts.json, which the build resolves from the version's own client
 * assets, src/styles/parts.json). Paths are under assets/minecraft/. Plain Java, so it is unit-tested.
 */
public final class PartTable {
    /** What a part is, for the rules: blocks stay opaque and can't be switched off (B.0 rule 4). */
    public enum Kind {
        BLOCK, ITEM, ENTITY, OVERLAY, GUI, PARTICLE, SKY;

        static Kind of(String s) {
            try {
                return valueOf(s.toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return ITEM;
            }
        }
    }

    /**
     * One part: its id, its group in the texture tree, the Bauloo tab it sits under (null for one of Sol's own, under
     * "More"), its kind, whether it can go off, whether its colours change without a reload, whether it can follow
     * the glint, whether only its colours may change (ores), the texture its tiles show, and its files.
     */
    public record Part(String id, String group, String tab, Kind kind, boolean off, boolean live, boolean glint,
                       boolean recolourOnly, String icon, List<String> paths) {
        /** Its texture files (the .png paths). */
        public List<String> textures() {
            List<String> out = new ArrayList<>();
            for (String p : paths) {
                if (p.endsWith(".png")) {
                    out.add(p);
                }
            }
            return out;
        }

        public boolean opaque() {
            return kind == Kind.BLOCK;
        }
    }

    private static PartTable loaded;

    private final String minecraft;
    private final List<String> tabs;
    private final List<String> groups;
    private final List<Part> parts;
    private final Map<String, Part> byId = new HashMap<>();
    private final Map<String, List<Part>> byPath = new HashMap<>();

    PartTable(String minecraft, List<String> tabs, List<String> groups, List<Part> parts) {
        this.minecraft = minecraft;
        this.tabs = List.copyOf(tabs);
        this.groups = List.copyOf(groups);
        this.parts = List.copyOf(parts);
        for (Part p : parts) {
            byId.put(p.id(), p);
            for (String path : p.paths()) {
                byPath.computeIfAbsent(path, k -> new ArrayList<>(2)).add(p);
            }
        }
    }

    /** This version's table (read once from the jar); an empty one if it can't be read. */
    public static synchronized PartTable get() {
        if (loaded == null) {
            try (InputStream in = PartTable.class.getResourceAsStream("/assets/cosmeticsmod/styles/parts.json")) {
                loaded = in == null ? empty() : parse(new InputStreamReader(in, StandardCharsets.UTF_8));
            } catch (Exception e) {
                io.github.georgwav.cosmetics.CosmeticsMod.LOGGER.warn("[Sol] Styles: couldn't read the part table: {}", e.toString());
                loaded = empty();
            }
        }
        return loaded;
    }

    static PartTable empty() {
        return new PartTable("", List.of(), List.of(), List.of());
    }

    /** Reads a resolved table (the build's output). */
    public static PartTable parse(java.io.Reader reader) {
        JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
        List<String> tabs = strings(root.getAsJsonArray("tabs"));
        List<String> groups = strings(root.getAsJsonArray("groups"));
        List<Part> parts = new ArrayList<>();
        for (JsonElement e : root.getAsJsonArray("parts")) {
            JsonObject o = e.getAsJsonObject();
            parts.add(new Part(
                    o.get("id").getAsString(),
                    str(o, "group", "other"),
                    str(o, "tab", null),
                    Kind.of(str(o, "kind", "item")),
                    bool(o, "off"), bool(o, "live"), bool(o, "glint"), bool(o, "recolourOnly"),
                    str(o, "icon", null),
                    strings(o.getAsJsonArray("paths"))));
        }
        return new PartTable(str(root, "mc", ""), tabs, groups, parts);
    }

    private static String str(JsonObject o, String key, String fallback) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : fallback;
    }

    private static boolean bool(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsBoolean();
    }

    private static List<String> strings(JsonArray a) {
        List<String> out = new ArrayList<>();
        if (a != null) {
            for (JsonElement e : a) {
                out.add(e.getAsString());
            }
        }
        return out;
    }

    /** The version the table was made for. */
    public String minecraft() {
        return minecraft;
    }

    /** Bauloo's tabs, in its order. */
    public List<String> tabs() {
        return tabs;
    }

    public List<String> groups() {
        return groups;
    }

    public List<Part> parts() {
        return parts;
    }

    public Part part(String id) {
        return byId.get(id);
    }

    /** The parts that list a path (a helmet's texture is in both Armour and Helmets), or none. */
    public List<Part> partsOf(String path) {
        return byPath.getOrDefault(path, Collections.emptyList());
    }

    /** The parts under a tab, in the table's order. */
    public List<Part> inTab(String tab) {
        List<Part> out = new ArrayList<>();
        for (Part p : parts) {
            if (tab.equals(p.tab())) {
                out.add(p);
            }
        }
        return out;
    }

    /** Sol's own parts, under "More", by group. */
    public Map<String, List<Part>> more() {
        Map<String, List<Part>> out = new LinkedHashMap<>();
        for (String g : groups) {
            out.put(g, new ArrayList<>());
        }
        for (Part p : parts) {
            if (p.tab() == null) {
                out.computeIfAbsent(p.group(), k -> new ArrayList<>()).add(p);
            }
        }
        out.values().removeIf(List::isEmpty);
        return out;
    }
}
