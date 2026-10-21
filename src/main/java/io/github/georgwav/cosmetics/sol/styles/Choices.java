package io.github.georgwav.cosmetics.sol.styles;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;

/**
 * What the menu offers for one part (B.2.11), in its order: the part packs first (your personal styles, your own
 * styles folder, then your resource packs), two packs with the same files shown once with "also in"; then the base,
 * Vanilla and Off; then Sol's built-ins; last the full packs' version of the part, folded. Each tile knows the
 * picture its thumbnail shows. Plain Java, so it is unit-tested.
 */
public final class Choices {
    /** Which group a tile is in. */
    public enum Group {
        PART, BASE, VANILLA, OFF, BUILTIN, FULL
    }

    /**
     * One tile.
     *
     * @param key     what identifies it (the part and its source), for favourites and recent use
     * @param source  the choice's source when it's picked ("pack:...", "vanilla", "game", "off", "builtin:...")
     * @param label   its name: the pack's name, or a built-in's id for the menu to translate
     * @param pack    its pack's key, or null
     * @param alsoIn  the other packs with the same files
     * @param picture what its thumbnail shows (a texture), or null (Off)
     */
    public record Tile(String key, String source, String label, String pack, Group group, List<String> alsoIn, Mixer.Source picture) {
    }

    /** The built-ins every part can take: the theme's colour and the seven recolours. */
    public static final List<String> RECOLOURS = List.of("theme", "aqua", "blueberry", "lavender", "red", "ice", "black", "white");

    private Choices() {
    }

    /**
     * The tiles for a part, in the menu's order (the part packs unsorted: {@link #sort} orders them).
     *
     * @param themeTint the theme's colour now (RGB), for the theme tile's picture
     */
    public static List<Tile> of(PartTable.Part part, StylesIndex.Snapshot idx, StyleSetup setup, int themeTint) {
        List<Tile> out = new ArrayList<>();
        PackInfo base = setup.base() == null ? null : idx.get(setup.base());
        // The part packs: each pack with at least one of the part's files, the same files once.
        Map<String, List<PackInfo>> bySignature = new LinkedHashMap<>();
        List<PackInfo> full = new ArrayList<>();
        for (PackInfo.Origin origin : new PackInfo.Origin[]{PackInfo.Origin.PERSONAL, PackInfo.Origin.USER, PackInfo.Origin.RESOURCE_PACK}) {
            for (PackInfo p : idx.packs()) {
                if (p.origin() != origin || base != null && p.key().equals(base.key()) || !supplies(p, part)) {
                    continue;
                }
                if (p.kind() == PackInfo.Kind.FULL) {
                    full.add(p);
                } else {
                    bySignature.computeIfAbsent(signature(p, part), k -> new ArrayList<>()).add(p);
                }
            }
        }
        for (List<PackInfo> same : bySignature.values()) {
            PackInfo first = same.get(0);
            List<String> also = new ArrayList<>();
            for (int i = 1; i < same.size(); i++) {
                also.add(same.get(i).name());
            }
            out.add(packTile(part, first, Group.PART, List.copyOf(also), idx.vanilla()));
        }
        // The base (when one is on), the game's own and off.
        if (base != null) {
            out.add(new Tile(part.id() + "|vanilla", StyleSetup.VANILLA, base.name(), base.key(), Group.BASE, List.of(),
                    picture(part, base, idx.vanilla())));
            out.add(new Tile(part.id() + "|game", "game", "vanilla", null, Group.VANILLA, List.of(), picture(part, null, idx.vanilla())));
        } else {
            out.add(new Tile(part.id() + "|vanilla", StyleSetup.VANILLA, "vanilla", null, Group.VANILLA, List.of(), picture(part, null, idx.vanilla())));
        }
        Mixer.Source now = picture(part, base, idx.vanilla());
        if (part.off()) {
            Mixer.Source pic = null;
            if (part.id().equals("shield") && now != null) {
                pic = Mixer.made("see_through", now, Map.of("share", Float.toString(Pixels.SEE_THROUGH)), part.kind().name());
            }
            out.add(new Tile(part.id() + "|off", StyleSetup.OFF, part.id().equals("shield") ? "see_through" : "off", null, Group.OFF,
                    List.of(), pic));
        }
        // Sol's built-ins, made from what the part looks like now.
        if (now != null) {
            if (part.id().equals("crystal")) {
                out.add(builtin(part, "see_through", Mixer.made("see_through", now, Map.of("share", Float.toString(Pixels.SEE_THROUGH)), part.kind().name())));
            }
            if (part.id().equals("fire")) {
                out.add(builtin(part, "low_fire", Mixer.made("low_fire", now, Map.of(), part.kind().name())));
            }
            for (String r : RECOLOURS) {
                int rgb = r.equals("theme") ? themeTint & 0xFFFFFF : Pixels.preset(r);
                Map<String, String> params = new TreeMap<>();
                params.put("hue", "0");
                params.put("saturation", "1.0");
                params.put("brightness", "1.0");
                params.put("tint", Integer.toHexString(rgb));
                out.add(builtin(part, r, Mixer.made("recolour", now, params, part.kind().name())));
            }
        }
        // The full packs' version of the part, last (the menu folds them).
        for (PackInfo p : full) {
            out.add(packTile(part, p, Group.FULL, List.of(), idx.vanilla()));
        }
        return out;
    }

    /** Whether a pack has a file for the part (for the sky, a skybox of its own counts too). */
    public static boolean supplies(PackInfo p, PartTable.Part part) {
        for (String path : part.paths()) {
            if (p.has(path)) {
                return true;
            }
        }
        if (part.kind() == PartTable.Kind.SKY) {
            for (String f : p.files().keySet()) {
                if (f.startsWith("optifine/sky/") || f.startsWith("fabricskyboxes:sky/")) {
                    return true;
                }
            }
        }
        return false;
    }

    /** How many tiles are in the folded group. */
    public static int folded(List<Tile> tiles) {
        int n = 0;
        for (Tile t : tiles) {
            if (t.group() == Group.FULL) {
                n++;
            }
        }
        return n;
    }

    /**
     * Orders the part packs (only they move; the rest keep their places): by pack name, by colour (average hue, grey
     * last), recently used first, or favourites first, each then by name.
     *
     * @param hue    a tile's average hue (0..1, 2 for grey or not known yet)
     * @param recent a tile's place in the recent list (0 the latest, MAX_VALUE when never used)
     */
    public static List<Tile> sort(List<Tile> tiles, String mode, ToDoubleFunction<Tile> hue, ToIntFunction<Tile> recent, Set<String> favourites) {
        List<Tile> parts = new ArrayList<>();
        for (Tile t : tiles) {
            if (t.group() == Group.PART) {
                parts.add(t);
            }
        }
        Comparator<Tile> byName = Comparator.comparing(t -> t.label().toLowerCase(Locale.ROOT));
        Comparator<Tile> order = switch (mode == null ? "pack" : mode) {
            case "colour" -> Comparator.<Tile>comparingDouble(hue).thenComparing(byName);
            case "recent" -> Comparator.<Tile>comparingInt(recent).thenComparing(byName);
            case "favourites" -> Comparator.<Tile>comparingInt(t -> favourites.contains(t.key()) ? 0 : 1).thenComparing(byName);
            default -> byName;
        };
        parts.sort(order);
        List<Tile> out = new ArrayList<>(tiles.size());
        int i = 0;
        for (Tile t : tiles) {
            out.add(t.group() == Group.PART ? parts.get(i++) : t);
        }
        return out;
    }

    /** Whether a part's choice is the tile's. */
    public static boolean chosen(Tile t, StyleSetup.Choice c) {
        return t.source().equals(c.source());
    }

    private static Tile packTile(PartTable.Part part, PackInfo p, Group group, List<String> also, VanillaIndex vanilla) {
        return new Tile(part.id() + "|pack:" + p.key(), "pack:" + p.key(), p.name(), p.key(), group, also, picture(part, p, vanilla));
    }

    private static Tile builtin(PartTable.Part part, String id, Mixer.Source picture) {
        return new Tile(part.id() + "|builtin:" + id, "builtin:" + id, id, null, Group.BUILTIN, List.of(), picture);
    }

    /**
     * Where one of the part's files comes from when the tile is picked (the preview's other textures: the armour on
     * the mannequin, the fire's frames): the tile's pack, else the base, else the game; Sol's built-ins made from it.
     * Null for Off, and for a file nobody has.
     */
    public static Mixer.Source file(Tile t, String path, PartTable.Part part, StylesIndex.Snapshot idx, StyleSetup setup, int themeTint) {
        PackInfo base = setup.base() == null ? null : idx.get(setup.base());
        VanillaIndex.Entry v = idx.vanilla().get(path);
        Mixer.Source game = v == null ? null : new Mixer.FromGame(path, v.crc(), v.size());
        Mixer.Source under = base != null && base.has(path) ? Mixer.fromPack(base, path) : game;
        return switch (t.group()) {
            case PART, FULL -> {
                PackInfo p = idx.get(t.pack());
                yield p != null && p.has(path) ? Mixer.fromPack(p, path) : under;
            }
            case BASE -> under;
            case VANILLA -> game;
            case OFF -> part.id().equals("shield") && under != null
                    ? Mixer.made("see_through", under, Map.of("share", Float.toString(Pixels.SEE_THROUGH)), part.kind().name()) : null;
            case BUILTIN -> {
                String b = t.source().substring("builtin:".length());
                if (under == null) {
                    yield null;
                }
                if (b.equals("see_through")) {
                    yield Mixer.made("see_through", under, Map.of("share", Float.toString(Pixels.SEE_THROUGH)), part.kind().name());
                }
                if (b.equals("low_fire")) {
                    yield Mixer.made("low_fire", under, Map.of(), part.kind().name());
                }
                int rgb = b.equals("theme") ? themeTint & 0xFFFFFF : Pixels.preset(b);
                Map<String, String> params = new TreeMap<>();
                params.put("hue", "0");
                params.put("saturation", "1.0");
                params.put("brightness", "1.0");
                params.put("tint", Integer.toHexString(Math.max(0, rgb)));
                yield Mixer.made("recolour", under, params, part.kind().name());
            }
        };
    }

    /** The texture a tile shows: the pack's own copy of the part's icon (or its first texture of the part), else the game's. */
    static Mixer.Source picture(PartTable.Part part, PackInfo pack, VanillaIndex vanilla) {
        String icon = part.icon();
        if (pack != null) {
            if (icon != null && pack.has(icon)) {
                return Mixer.fromPack(pack, icon);
            }
            for (String t : part.textures()) {
                if (pack.has(t)) {
                    return Mixer.fromPack(pack, t);
                }
            }
        }
        String path = icon != null && vanilla.has(icon) ? icon : null;
        if (path == null) {
            for (String t : part.textures()) {
                if (vanilla.has(t)) {
                    path = t;
                    break;
                }
            }
        }
        if (path == null) {
            return null;
        }
        VanillaIndex.Entry e = vanilla.get(path);
        return new Mixer.FromGame(path, e.crc(), e.size());
    }

    /** The pack's files for the part, as one text: two packs with the same text supply the same look. */
    private static String signature(PackInfo p, PartTable.Part part) {
        StringBuilder sb = new StringBuilder();
        for (String path : part.paths()) {
            VanillaIndex.Entry e = p.files().get(path);
            if (e != null) {
                sb.append(path).append('=').append(Integer.toHexString(e.crc())).append('/').append(e.size()).append(';');
            }
        }
        return sb.toString();
    }
}
