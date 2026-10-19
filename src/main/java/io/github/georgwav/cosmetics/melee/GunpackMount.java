package io.github.georgwav.cosmetics.melee;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Knives from gunpacks (the TACZ / LR Tactical layout) as cosmetics. Players drop the packs (zips or folders, or a zip
 * of several) into the knives folder; each pack's {@code display/melee/<id>.json} becomes a cosmetic in the melee slot,
 * mounted as a generated resource pack the game loads like any other:
 * <pre>
 *   assets/&lt;ns&gt;/cosmetics/melee/&lt;id&gt;.json        one per display: slot "melee", its name, picture and knife
 *   assets/&lt;ns&gt;/knives/models/...geo.json           the geometry (once, finishes share it)
 *   assets/&lt;ns&gt;/knives/animations/...json           the animations (once)
 *   assets/&lt;ns&gt;/textures/...png                     its textures and slot picture
 *   assets/&lt;ns&gt;/sounds/....ogg + sounds.json         every sound, so the game's sound engine plays them
 * </pre>
 * Names and per-knife settings come from {@value #OVERRIDES} next to the packs, written with the defaults on the first
 * mount. Nothing of the packs is ever bundled; their Lua scripts are ignored (the behaviour is Sol's own). Pure Java.
 */
public final class GunpackMount {
    /** The overrides file next to the packs. */
    public static final String OVERRIDES = "knives.json";
    /** The mounted pack's folder name (hidden) inside the knives folder. */
    public static final String MOUNTED = ".mounted";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Pattern DISPLAY = Pattern.compile("^(.*?)display/melee/([^/]+)\\.json$");
    private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9_.-]+");

    /** What one mount made: the cosmetics' ids and what went wrong. */
    public record Result(List<String> cosmetics, List<String> problems) {
    }

    private GunpackMount() {
    }

    /** A file in a gunpack: its path in the pack (with '/'), and how to read it. */
    private record Entry(String path, byte[] data) {
    }

    /** One gunpack: its files by path from its namespace root ({@code display/...}), and its namespace. */
    private static final class Pack {
        final String source;
        final String root;
        final Map<String, byte[]> files = new TreeMap<>();
        String namespace;

        Pack(String source, String root) {
            this.source = source;
            this.root = root;
        }

        byte[] file(String path) {
            return files.get(path);
        }
    }

    /**
     * Mounts every gunpack in {@code folder} into {@code folder/.mounted} (only rewritten when the packs change); the
     * mounted pack's folder, or null when there are no knives.
     */
    public static Path mount(Path folder, Result[] out) throws IOException {
        Files.createDirectories(folder);
        Path target = folder.resolve(MOUNTED);
        String stamp = stamp(folder);
        Path stampFile = target.resolve(".stamp");
        if (Files.exists(stampFile) && Files.readString(stampFile).equals(stamp)) {
            if (out != null) {
                out[0] = new Result(List.of(), List.of());
            }
            return Files.exists(target.resolve("pack.mcmeta")) ? target : null;
        }
        List<Pack> packs = read(folder);
        Result r = write(packs, overrides(folder), target);
        Files.writeString(stampFile, stamp);
        if (out != null) {
            out[0] = r;
        }
        return r.cosmetics().isEmpty() ? null : target;
    }

    /** What the packs and overrides are now (names, sizes and times), so an unchanged folder isn't mounted again. */
    private static String stamp(Path folder) throws IOException {
        StringBuilder sb = new StringBuilder("v1\n");
        try (Stream<Path> s = Files.walk(folder, 6)) {
            for (Path p : s.filter(Files::isRegularFile).filter(p -> !folder.relativize(p).toString().replace('\\', '/').startsWith(MOUNTED))
                    .sorted(Comparator.comparing(Path::toString)).toList()) {
                sb.append(folder.relativize(p).toString().replace('\\', '/')).append(' ').append(Files.size(p)).append(' ')
                        .append(Files.getLastModifiedTime(p).toMillis()).append('\n');
            }
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ reading

    /** Every gunpack in the folder: in folders, in zips, and in zips inside zips. */
    static List<Pack> read(Path folder) throws IOException {
        List<Entry> entries = new ArrayList<>();
        try (Stream<Path> s = Files.walk(folder, 12)) {
            for (Path p : s.filter(Files::isRegularFile).sorted(Comparator.comparing(Path::toString)).toList()) {
                String rel = folder.relativize(p).toString().replace('\\', '/');
                if (rel.startsWith(MOUNTED) || rel.equals(OVERRIDES)) {
                    continue;
                }
                if (rel.toLowerCase(Locale.ROOT).endsWith(".zip")) {
                    try (InputStream in = Files.newInputStream(p)) {
                        unzip(rel + "/", in, entries, 0);
                    }
                } else {
                    entries.add(new Entry(rel, null));
                }
            }
        }
        return group(entries, folder);
    }

    private static void unzip(String prefix, InputStream in, List<Entry> out, int depth) throws IOException {
        ZipInputStream zip = new ZipInputStream(in);
        ZipEntry e;
        while ((e = zip.getNextEntry()) != null) {
            if (e.isDirectory()) {
                continue;
            }
            String name = e.getName().replace('\\', '/');
            byte[] data = zip.readAllBytes();
            if (name.toLowerCase(Locale.ROOT).endsWith(".zip") && depth < 2) {
                unzip(prefix + name + "/", new ByteArrayInputStream(data), out, depth + 1);
            } else {
                out.add(new Entry(prefix + name, data));
            }
        }
    }

    /** The files split into gunpacks: each {@code display/melee/} folder's root holds one. */
    private static List<Pack> group(List<Entry> entries, Path folder) throws IOException {
        Map<String, Pack> packs = new LinkedHashMap<>();
        for (Entry e : entries) {
            Matcher m = DISPLAY.matcher(e.path());
            if (m.matches()) {
                packs.computeIfAbsent(m.group(1), r -> new Pack(r.isEmpty() ? "." : r, r));
            }
        }
        // Longest roots first, so nested packs claim their own files.
        List<Pack> byLength = new ArrayList<>(packs.values());
        byLength.sort(Comparator.comparingInt((Pack p) -> p.root.length()).reversed());
        for (Entry e : entries) {
            for (Pack p : byLength) {
                if (e.path().startsWith(p.root)) {
                    byte[] data = e.data() != null ? e.data() : Files.readAllBytes(folder.resolve(e.path()));
                    p.files.put(e.path().substring(p.root.length()), data);
                    break;
                }
            }
        }
        for (Pack p : packs.values()) {
            p.namespace = namespace(p);
        }
        return new ArrayList<>(packs.values());
    }

    /** A pack's namespace: its root's last folder ({@code assets/<ns>/}), else the one its displays name. */
    private static String namespace(Pack p) {
        String root = p.root.endsWith("/") ? p.root.substring(0, p.root.length() - 1) : p.root;
        String last = root.contains("/") ? root.substring(root.lastIndexOf('/') + 1) : root;
        if (root.contains("assets/") && NAMESPACE.matcher(last).matches()) {
            return last;
        }
        for (var f : p.files.entrySet()) {
            if (f.getKey().startsWith("display/melee/")) {
                try {
                    JsonObject d = JsonParser.parseString(new String(f.getValue(), StandardCharsets.UTF_8)).getAsJsonObject();
                    for (String key : new String[]{"model", "texture", "animation"}) {
                        if (d.has(key) && d.get(key).getAsString().contains(":")) {
                            return d.get(key).getAsString().substring(0, d.get(key).getAsString().indexOf(':'));
                        }
                    }
                } catch (RuntimeException ignored) {
                    // Not JSON: the next display may tell.
                }
            }
        }
        String clean = last.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
        return clean.isEmpty() ? "knives" : clean;
    }

    // ------------------------------------------------------------------ overrides

    /**
     * The overrides: per cosmetic id ({@code ns:id}) its name, hand size, camera move and so on, and ids to skip.
     * Written with the defaults when missing.
     */
    static JsonObject overrides(Path folder) throws IOException {
        Path file = folder.resolve(OVERRIDES);
        if (Files.exists(file)) {
            try {
                return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            } catch (RuntimeException e) {
                // A broken file: the defaults, and the file left for the player to fix.
                return defaults();
            }
        }
        JsonObject d = defaults();
        Files.writeString(file, GSON.toJson(d));
        return d;
    }

    /** Georg's names for the three packs' knives, and their fixes. */
    static JsonObject defaults() {
        JsonObject o = new JsonObject();
        JsonArray skip = new JsonArray();
        // Free Dark Star: Georg removed it.
        skip.add("delta_wt:null_annihil");
        o.add("skip", skip);
        JsonObject knives = new JsonObject();
        String[][] names = {
                {"delta_wt:annihil", "Dark Star"}, {"delta_wt:cobra_vanguard", "Cobra Vanguard"}, {"delta_wt:executor", "Executor"},
                {"delta_wt:mercy", "Mercy"}, {"delta_wt:polaris", "Desmoulins' Edge"}, {"delta_wt:quenching", "Dragon's Fang"},
                {"delta_wt:shadowkiller", "Fiery Owl"}, {"delta_wt:silencefd", "TAC Dagger"}, {"delta_wt:thieve_sea", "Sea Gleam"},
                {"delta_wt:treasure_kn", "Creed"},
                {"wtyj:desire_blade", "VCT Lock In"}, {"wtyj:eternal_axe", "Prime Axe"}, {"wtyj:eternal_karambit", "Prime Karambit"},
                {"wtyj:future_butterfly", "RGX 11z Firefly"}, {"wtyj:future_dagger", "RGX 11z Dagger"}, {"wtyj:thunder_hammer", "Smite Hammer"},
                {"cs2_wt:cs2_bayonet", "Bayonet"}, {"cs2_wt:cs2_bayoauto", "Bayonet | Autotronic"},
                {"cs2_wt:cs2_butterfly", "Butterfly Knife"}, {"cs2_wt:cs2_buemerald", "Butterfly Knife | Emerald"},
                {"cs2_wt:cs2_busnowfall", "Butterfly Knife | Snowfall"}, {"cs2_wt:cs2_css", "Classic Knife"},
                {"cs2_wt:cs2_karambit", "Karambit"}, {"cs2_wt:cs2_karobsidian", "Karambit | Obsidian"},
                {"cs2_wt:cs2_m9", "M9 Bayonet"}, {"cs2_wt:cs2_m9amethyst", "M9 Bayonet | Amethyst"},
                {"cs2_wt:cs2_m9auto", "M9 Bayonet | Autotronic"}, {"cs2_wt:cs2_m9emerald", "M9 Bayonet | Emerald"},
                {"cs2_wt:cs2_m9sapphire", "M9 Bayonet | Sapphire"}, {"cs2_wt:cs2_push", "Shadow Daggers"},
                {"cs2_wt:cs2_skeleton", "Skeleton Knife"}, {"cs2_wt:cs2_stiletto", "Stiletto Knife"},
                {"cs2_wt:cs2_stilettosapphire", "Stiletto Knife | Sapphire"}, {"cs2_wt:cs2_stilettott", "Stiletto Knife | Tiger Tooth"},
                {"cs2_wt:cs2_tactical", "Huntsman Knife"}, {"cs2_wt:cs2_talon", "Talon Knife"}};
        for (String[] n : names) {
            JsonObject k = new JsonObject();
            k.addProperty("name", n[1]);
            knives.add(n[0], k);
        }
        // VCT Lock In's hands are drawn smaller round the fist; the Prime Karambit's camera moves so the knife shows.
        knives.getAsJsonObject("wtyj:desire_blade").addProperty("hand_scale", 0.72);
        JsonArray cam = new JsonArray();
        cam.add(1);
        cam.add(-1);
        cam.add(4);
        knives.getAsJsonObject("wtyj:eternal_karambit").add("camera", cam);
        o.add("knives", knives);
        return o;
    }

    // ------------------------------------------------------------------ writing

    /** Writes the mounted pack from the packs (the folder emptied first); what it made. */
    static Result write(List<Pack> packs, JsonObject overrides, Path target) throws IOException {
        clear(target);
        Files.createDirectories(target);
        Files.writeString(target.resolve("pack.mcmeta"), """
                {
                  "pack": {
                    "description": "Sol Client: your knives",
                    "pack_format": 34,
                    "supported_formats": [34, 999],
                    "min_format": 34,
                    "max_format": 999
                  }
                }
                """);
        List<String> made = new ArrayList<>(), problems = new ArrayList<>();
        java.util.Set<String> skip = new java.util.HashSet<>();
        if (overrides.has("skip") && overrides.get("skip").isJsonArray()) {
            overrides.getAsJsonArray("skip").forEach(e -> skip.add(e.getAsString()));
        }
        JsonObject knives = overrides.has("knives") && overrides.get("knives").isJsonObject() ? overrides.getAsJsonObject("knives") : new JsonObject();
        Map<String, JsonObject> sounds = new TreeMap<>();
        for (Pack p : packs) {
            String ns = p.namespace;
            Path assets = target.resolve("assets").resolve(ns);
            java.util.Set<String> copied = new java.util.HashSet<>();
            for (var f : p.files.entrySet()) {
                Matcher m = Pattern.compile("^display/melee/([^/]+)\\.json$").matcher(f.getKey());
                if (!m.matches()) {
                    continue;
                }
                String id = m.group(1);
                String cid = ns + ":" + id;
                if (skip.contains(cid)) {
                    continue;
                }
                try {
                    JsonObject display = JsonParser.parseString(new String(f.getValue(), StandardCharsets.UTF_8)).getAsJsonObject();
                    JsonObject own = knives.has(cid) && knives.get(cid).isJsonObject() ? knives.getAsJsonObject(cid) : new JsonObject();
                    made.add(cosmetic(p, ns, id, display, own, assets, copied, problems));
                } catch (RuntimeException | IOException e) {
                    problems.add(p.source + " " + id + ": " + e.getMessage());
                }
            }
            // Every sound the pack has, so the animations' sound keys play.
            for (var f : p.files.entrySet()) {
                if (f.getKey().startsWith("tacz_sounds/") && f.getKey().toLowerCase(Locale.ROOT).endsWith(".ogg")) {
                    String path = f.getKey().substring("tacz_sounds/".length(), f.getKey().length() - 4).toLowerCase(Locale.ROOT);
                    if (!path.matches("[a-z0-9_./-]+")) {
                        problems.add(p.source + ": sound " + f.getKey() + " has a name the game can't use");
                        continue;
                    }
                    Path to = assets.resolve("sounds").resolve(path + ".ogg");
                    Files.createDirectories(to.getParent());
                    Files.write(to, f.getValue());
                    JsonObject entry = new JsonObject();
                    JsonArray list = new JsonArray();
                    JsonObject s = new JsonObject();
                    s.addProperty("name", ns + ":" + path);
                    // Long clips (some inspects are one) stream rather than load whole.
                    s.addProperty("stream", f.getValue().length > 300_000);
                    list.add(s);
                    entry.add("sounds", list);
                    sounds.computeIfAbsent(ns, k -> new JsonObject()).add(path, entry);
                }
            }
        }
        for (var e : sounds.entrySet()) {
            Path to = target.resolve("assets").resolve(e.getKey()).resolve("sounds.json");
            Files.createDirectories(to.getParent());
            Files.writeString(to, GSON.toJson(e.getValue()));
        }
        return new Result(made, problems);
    }

    /** One display as a cosmetic: its JSON, and its model, animations and pictures copied (once per pack). */
    private static String cosmetic(Pack p, String ns, String id, JsonObject display, JsonObject own, Path assets,
                                   java.util.Set<String> copied, List<String> problems) throws IOException {
        String model = path(display, "model", ns);
        String anim = path(display, "animation", ns);
        String tex = path(display, "texture", ns);
        String slot = display.has("slot_texture") ? path(display, "slot_texture", ns)
                : display.has("slot") ? path(display, "slot", ns) : null;
        String geoFile = find(p, "geo_models/" + model, ".json", ".geo.json", "");
        String animFile = find(p, "animations/" + anim, ".animation.json", ".json", "");
        String texFile = find(p, "textures/" + tex, ".png", "");
        if (geoFile == null || animFile == null || texFile == null) {
            throw new IOException("missing its " + (geoFile == null ? "model" : animFile == null ? "animations" : "texture"));
        }
        // Resource names are lower-case in the game.
        String modelOut = "knives/models/" + strip(geoFile.substring("geo_models/".length()), ".json").toLowerCase(Locale.ROOT);
        String animOut = "knives/animations/" + strip(animFile.substring("animations/".length()), ".json").toLowerCase(Locale.ROOT);
        String texOut = texFile.toLowerCase(Locale.ROOT);
        copy(p, geoFile, assets.resolve(modelOut + ".json"), copied);
        copy(p, animFile, assets.resolve(animOut + ".json"), copied);
        copy(p, texFile, assets.resolve(texOut), copied);
        String slotFile = slot != null ? find(p, "textures/" + slot, ".png", "") : null;
        if (slotFile == null) {
            slotFile = find(p, "textures/melee/slot/" + id, ".png");
        }
        String slotOut = slotFile != null ? slotFile.toLowerCase(Locale.ROOT) : null;
        if (slotFile != null) {
            copy(p, slotFile, assets.resolve(slotOut), copied);
        }
        String name = own.has("name") ? own.get("name").getAsString() : prettify(id);
        JsonObject c = new JsonObject();
        c.addProperty("slot", "melee");
        c.addProperty("name", name);
        c.addProperty("texture", ns + ":" + texOut);
        if (slotOut != null) {
            c.addProperty("icon", ns + ":" + slotOut);
        }
        // Finishes share their knife's model and animations, so they group under it.
        c.addProperty("group", name.contains(" | ") ? name.substring(0, name.indexOf(" | ")) : name);
        JsonObject melee = new JsonObject();
        melee.addProperty("model", ns + ":" + modelOut + ".json");
        melee.addProperty("animations", ns + ":" + animOut + ".json");
        melee.addProperty("sounds", ns);
        melee.addProperty("pack", p.source);
        for (String k : new String[]{"hand_scale", "camera", "angel", "grip"}) {
            if (own.has(k)) {
                melee.add(k, own.get(k));
            }
        }
        JsonElement transforms = display.has("transforms") ? display.get("transforms") : display.get("transform");
        if (transforms != null) {
            melee.add("transforms", transforms);
        }
        c.add("melee", melee);
        Path to = assets.resolve("cosmetics/melee/" + id.toLowerCase(Locale.ROOT) + ".json");
        Files.createDirectories(to.getParent());
        Files.writeString(to, GSON.toJson(c));
        return ns + ":melee/" + id.toLowerCase(Locale.ROOT);
    }

    /** A display's resource ({@code ns:path} or {@code path}) as a path, lower-cased as the game wants. */
    private static String path(JsonObject display, String key, String ns) {
        if (!display.has(key) || !display.get(key).isJsonPrimitive()) {
            throw new IllegalArgumentException("no " + key);
        }
        String v = display.get(key).getAsString();
        return (v.contains(":") ? v.substring(v.indexOf(':') + 1) : v);
    }

    /** The first of {@code base + suffix} the pack has (case as given, then lower-case), or null. */
    private static String find(Pack p, String base, String... suffixes) {
        for (String s : suffixes) {
            if (p.file(base + s) != null) {
                return base + s;
            }
        }
        for (String s : suffixes) {
            String lower = (base + s).toLowerCase(Locale.ROOT);
            for (String k : p.files.keySet()) {
                if (k.toLowerCase(Locale.ROOT).equals(lower)) {
                    return k;
                }
            }
        }
        return null;
    }

    private static void copy(Pack p, String from, Path to, java.util.Set<String> copied) throws IOException {
        if (!copied.add(from)) {
            return;
        }
        Files.createDirectories(to.getParent());
        Files.write(to, p.file(from));
    }

    private static String strip(String s, String suffix) {
        return s.endsWith(suffix) ? s.substring(0, s.length() - suffix.length()) : s;
    }

    /** {@code cobra_vanguard} as "Cobra Vanguard". */
    static String prettify(String id) {
        StringBuilder sb = new StringBuilder();
        for (String w : id.split("[_\\-]+")) {
            if (!w.isEmpty()) {
                sb.append(sb.length() > 0 ? " " : "").append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
            }
        }
        return sb.toString();
    }

    /** Empties a folder (the mounted pack before it's written again). */
    private static void clear(Path folder) throws IOException {
        if (!Files.exists(folder)) {
            return;
        }
        try (Stream<Path> s = Files.walk(folder)) {
            for (Path p : s.sorted(Comparator.reverseOrder()).toList()) {
                if (!p.equals(folder)) {
                    Files.deleteIfExists(p);
                }
            }
        }
    }
}
