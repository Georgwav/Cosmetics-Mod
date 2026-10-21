package io.github.georgwav.cosmetics.sol.styles;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * What the Sol Styles pack holds for a setup (B.2.7): for every path it answers, where the file comes from. A pack
 * file is read straight from its pack; Sol's own files (recolours, see-through and off parts, low fire, extruded
 * models) are recipes, made once into the cache and read from there. The base pack is never mixed (it is mounted as
 * itself under this one). The same setup and packs always give the same plan. Plain Java, so it is unit-tested.
 */
public final class Mixer {
    /** Where one file of the mix comes from. */
    public sealed interface Source permits FromPack, FromGame, FromMod, Made {
        /** What identifies its content, for the cache keys of the files made from it. */
        String identity();
    }

    /** A file in a pack: the zip or folder, the entry's path in it, and its CRC and size. */
    public record FromPack(String pack, String location, String entry, int crc, long size) implements Source {
        @Override
        public String identity() {
            return "pack:" + location + "!" + entry + "#" + Integer.toHexString(crc) + "/" + size;
        }
    }

    /** The game's own file (when a base pack is on, a part set to vanilla takes the game's file over the base's). */
    public record FromGame(String path, int crc, long size) implements Source {
        @Override
        public String identity() {
            return "game:" + path + "#" + Integer.toHexString(crc) + "/" + size;
        }
    }

    /** One of Sol's own files (its models for the Model shape): under assets/cosmeticsmod/ in the mod. */
    public record FromMod(String path) implements Source {
        @Override
        public String identity() {
            return "mod:" + path;
        }
    }

    /**
     * A file Sol makes: {@code op} on {@code input} with {@code params}, kept in the cache under {@code key}.
     * Ops: recolour (hue, saturation, brightness, tint), see_through (share), off, low_fire, keep_opaque, extrude
     * (thickness, handheld, texture).
     */
    public record Made(String key, String op, Source input, Map<String, String> params, String kind) implements Source {
        @Override
        public String identity() {
            return "made:" + key;
        }
    }

    /** The mix: path (under assets/minecraft/, or "ns:path") to its source, and notes for the log and the menu. */
    public record Plan(Map<String, Source> files, List<String> notes) {
        public boolean isEmpty() {
            return files.isEmpty();
        }
    }

    private Mixer() {
    }

    /**
     * The plan for a setup.
     *
     * @param packs      a pack by its key, or null when it's gone
     * @param packText   a pack file's text (models, item definitions), or null
     * @param themeTint  the theme's colour now (RGB), for parts that follow it
     */
    public static Plan plan(StyleSetup setup, PartTable table, Function<String, PackInfo> packs, VanillaIndex vanilla,
                            java.util.function.BiFunction<PackInfo, String, String> packText, int themeTint) {
        return plan(setup, table, packs, vanilla, packText, themeTint, themeTint);
    }

    /** As above, with the glint's colour now (RGB) for tools that follow it. */
    public static Plan plan(StyleSetup setup, PartTable table, Function<String, PackInfo> packs, VanillaIndex vanilla,
                            java.util.function.BiFunction<PackInfo, String, String> packText, int themeTint, int glintTint) {
        Map<String, Source> files = new TreeMap<>();
        List<String> notes = new ArrayList<>();
        PackInfo base = setup.base() == null ? null : packs.apply(setup.base());
        if (setup.base() != null && base == null) {
            notes.add("base pack missing: " + setup.base());
        }
        for (Map.Entry<String, StyleSetup.Choice> e : setup.parts().entrySet()) {
            PartTable.Part part = table.part(e.getKey());
            if (part == null) {
                continue;
            }
            StyleSetup.Choice c = e.getValue();
            // 1. Where its files come from.
            String src = c.source();
            if (src.startsWith("pack:")) {
                PackInfo pack = packs.apply(c.packKey());
                if (pack == null) {
                    notes.add(part.id() + ": pack missing, back to the base: " + c.packKey());
                } else {
                    for (String path : part.paths()) {
                        if (pack.has(path)) {
                            files.put(path, fromPack(pack, path));
                        }
                    }
                    // Its models' parents and textures, from the same pack.
                    for (String path : part.paths()) {
                        if ((path.startsWith("models/") || path.startsWith("items/")) && pack.has(path)) {
                            ModelDeps.Needs needs = ModelDeps.walk(path, p -> pack.has(p) ? packText.apply(pack, p) : null,
                                    p -> pack.has(p) || vanilla.has(p));
                            for (String f : needs.files()) {
                                files.putIfAbsent(f, fromPack(pack, f));
                            }
                            for (String m : needs.missing()) {
                                notes.add(part.id() + ": " + pack.name() + " names a texture nobody has: " + m);
                            }
                        }
                    }
                }
            } else if (src.equals("game")) {
                for (String path : part.paths()) {
                    VanillaIndex.Entry v = vanilla.get(path);
                    if (v != null && base != null && base.has(path)) {
                        files.put(path, new FromGame(path, v.crc(), v.size()));
                    }
                }
            }
            // 2. Off and Sol's built-ins: made from whatever the texture is now.
            String op = null;
            Map<String, String> params = new TreeMap<>();
            if (src.equals(StyleSetup.OFF) && part.off()) {
                if (part.id().equals("shield")) {
                    op = "see_through";
                    params.put("share", Float.toString(Pixels.SEE_THROUGH));
                } else {
                    op = "off";
                }
            } else if (c.builtin() != null) {
                String b = c.builtin();
                if (b.equals("see_through") && part.off()) {
                    op = "see_through";
                    params.put("share", Float.toString(Pixels.SEE_THROUGH));
                } else if (b.equals("low_fire") && part.id().equals("fire")) {
                    op = "low_fire";
                } else if (Pixels.preset(b) >= 0 || b.equals("theme")) {
                    op = "recolour";
                    int tint = b.equals("theme") ? themeTint : Pixels.preset(b);
                    params.put("hue", "0");
                    params.put("saturation", "1.0");
                    params.put("brightness", "1.0");
                    params.put("tint", Integer.toHexString(tint & 0xFFFFFF));
                }
            }
            if (op != null) {
                for (String tex : part.textures()) {
                    if (part.opaque() && !op.equals("recolour")) {
                        continue;
                    }
                    Source in = current(files, tex, base, vanilla);
                    if (in != null) {
                        files.put(tex, made(op, in, params, part.kind().name()));
                    }
                }
            }
            // 3. Its own colours, on top.
            if (c.recoloured()) {
                Map<String, String> p = new TreeMap<>();
                int tint = c.paint() == StyleSetup.Paint.THEME || c.paint() == StyleSetup.Paint.RAINBOW ? themeTint
                        : c.paint() == StyleSetup.Paint.GLINT ? glintTint : c.tint();
                p.put("hue", Integer.toString(c.hue()));
                p.put("saturation", Float.toString(c.saturation()));
                p.put("brightness", Float.toString(c.brightness()));
                p.put("tint", tint < 0 ? "-1" : Integer.toHexString(tint & 0xFFFFFF));
                for (String tex : part.textures()) {
                    Source in = current(files, tex, base, vanilla);
                    if (in != null) {
                        files.put(tex, made("recolour", in, p, part.kind().name()));
                    }
                }
            }
            // 3b. The part's own colours: the crystal's frame and core, the anchor's charge glow.
            if (part.id().equals("crystal")) {
                String[][] regions = {{"frame", "0,0,0.5,0.5"}, {"core", "0.5,0,1,0.5"}};
                for (String[] r : regions) {
                    int rgb = colour(c.extra(r[0], null), themeTint);
                    if (rgb < 0) {
                        continue;
                    }
                    for (String tex : part.textures()) {
                        if (!tex.endsWith("entity/end_crystal/end_crystal.png")) {
                            continue;
                        }
                        Source in = current(files, tex, base, vanilla);
                        if (in != null) {
                            Map<String, String> p = new TreeMap<>();
                            p.put("hue", "0");
                            p.put("saturation", "1.0");
                            p.put("brightness", "1.0");
                            p.put("tint", Integer.toHexString(rgb));
                            p.put("rect", r[1]);
                            files.put(tex, made("recolour", in, p, part.kind().name()));
                        }
                    }
                }
            } else if (part.id().equals("anchor")) {
                int rgb = colour(c.extra("glow", null), themeTint);
                if (rgb >= 0) {
                    for (String tex : part.textures()) {
                        Source in = current(files, tex, base, vanilla);
                        if (in != null) {
                            files.put(tex, made("glow", in, Map.of("tint", Integer.toHexString(rgb)), part.kind().name()));
                        }
                    }
                }
            }
            // 4. Blocks stay opaque, whatever the pack did (rule B.0.4).
            if (part.opaque()) {
                for (String tex : part.textures()) {
                    Source s = files.get(tex);
                    if (s != null && !(s instanceof FromGame)) {
                        files.put(tex, made("keep_opaque", s, Map.of("vanilla", tex), part.kind().name()));
                    }
                }
            }
            // 5. One of Sol's own models instead of the part's item models, its texture in the part's colours.
            if (c.shape() == StyleSetup.Shape.MODEL && c.model() != null && StyleModels.known(part.id(), c.model())) {
                for (String path : part.paths()) {
                    if (path.startsWith("models/item/") && path.endsWith(".json")) {
                        files.put(path, new FromMod("styles/models/" + c.model() + ".json"));
                    }
                }
                Source tex = new FromMod("textures/item/styles/" + c.model() + ".png");
                if (c.recoloured()) {
                    Map<String, String> p = new TreeMap<>();
                    int tint = c.paint() == StyleSetup.Paint.THEME || c.paint() == StyleSetup.Paint.RAINBOW ? themeTint
                            : c.paint() == StyleSetup.Paint.GLINT ? glintTint : c.tint();
                    p.put("hue", Integer.toString(c.hue()));
                    p.put("saturation", Float.toString(c.saturation()));
                    p.put("brightness", Float.toString(c.brightness()));
                    p.put("tint", tint < 0 ? "-1" : Integer.toHexString(tint & 0xFFFFFF));
                    files.put("cosmeticsmod:textures/item/styles/" + c.model() + ".png", made("recolour", tex, p, part.kind().name()));
                }
            }
            // 5b. Extruded: the item textures as voxel models.
            if (c.shape() == StyleSetup.Shape.EXTRUDED && (part.kind() == PartTable.Kind.ITEM)) {
                for (String tex : part.textures()) {
                    if (!tex.startsWith("textures/item/")) {
                        continue;
                    }
                    String name = tex.substring("textures/item/".length(), tex.length() - 4);
                    String model = "models/item/" + name + ".json";
                    if (!vanilla.has(model)) {
                        continue;
                    }
                    Source in = current(files, tex, base, vanilla);
                    if (in == null) {
                        continue;
                    }
                    Map<String, String> p = new TreeMap<>();
                    p.put("thickness", Float.toString(c.thickness()));
                    p.put("handheld", Boolean.toString(part.glint()));
                    p.put("texture", "minecraft:item/" + name);
                    files.put(model, made("extrude", in, p, "MODEL"));
                }
            }
        }
        // Single textures from other packs (the tree's leaves) last: they win.
        for (Map.Entry<String, String> leaf : setup.leaves().entrySet()) {
            String path = leaf.getKey(), src = leaf.getValue();
            if (src.startsWith("pack:")) {
                PackInfo pack = packs.apply(src.substring(5));
                if (pack != null && pack.has(path)) {
                    files.put(path, fromPack(pack, path));
                }
            } else if (src.equals("game")) {
                VanillaIndex.Entry v = vanilla.get(path);
                if (v != null) {
                    files.put(path, new FromGame(path, v.crc(), v.size()));
                }
            }
        }
        return new Plan(java.util.Collections.unmodifiableMap(files), List.copyOf(notes));
    }

    /** A part's extra colour ("theme" or RGB hex) as RGB, or -1 when it isn't set. */
    static int colour(String value, int themeTint) {
        if (value == null || value.isEmpty()) {
            return -1;
        }
        if (value.equals("theme")) {
            return themeTint & 0xFFFFFF;
        }
        try {
            return Integer.parseInt(value.startsWith("#") ? value.substring(1) : value, 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** What a path is now: what the mix already gives it, else the base's, else the game's. */
    private static Source current(Map<String, Source> files, String path, PackInfo base, VanillaIndex vanilla) {
        Source s = files.get(path);
        if (s != null) {
            return s;
        }
        if (base != null && base.has(path)) {
            return fromPack(base, path);
        }
        VanillaIndex.Entry v = vanilla.get(path);
        return v == null ? null : new FromGame(path, v.crc(), v.size());
    }

    static FromPack fromPack(PackInfo pack, String path) {
        VanillaIndex.Entry f = pack.files().get(path);
        return new FromPack(pack.key(), pack.location(), entryName(path), f == null ? 0 : f.crc(), f == null ? 0 : f.size());
    }

    /** A Styles path's entry name inside a pack. */
    public static String entryName(String path) {
        int colon = path.indexOf(':');
        return colon > 0 ? "assets/" + path.substring(0, colon) + "/" + path.substring(colon + 1) : "assets/minecraft/" + path;
    }

    static Made made(String op, Source in, Map<String, String> params, String kind) {
        StringBuilder id = new StringBuilder(op).append('|').append(in.identity());
        new TreeMap<>(params).forEach((k, v) -> id.append('|').append(k).append('=').append(v));
        return new Made(sha1(id.toString()), op, in, Map.copyOf(params), kind);
    }

    static String sha1(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-1").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(40);
            for (byte b : d) {
                sb.append(Character.forDigit(b >> 4 & 15, 16)).append(Character.forDigit(b & 15, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
