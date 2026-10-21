package io.github.georgwav.cosmetics.sol.styles;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Every pack Styles can take a look from, scanned (B.2.8): the personal build's styles, the resourcepacks folder and
 * its groups, and client/styles. Each pack's file list is read once and kept by its size and modified time in
 * client/styles-index.json, so a second start reads only what changed. Paths, sizes, CRCs and counts only, never an
 * image. Built off the render thread; the result is an immutable snapshot.
 */
public final class StylesIndex {
    /** A pack to scan: its key, name, where it's from, its tab (personal styles) and where it is. */
    public record Candidate(String key, String name, PackInfo.Origin origin, String tab, Path path) {
    }

    /** The scanned packs, by key, and how long the scan took. */
    public record Snapshot(VanillaIndex vanilla, List<PackInfo> packs, Map<String, PackInfo> byKey, long scanMillis, int rescanned) {
        public PackInfo get(String key) {
            return key == null ? null : byKey.get(key);
        }

        /** The full packs (the base row's choices). */
        public List<PackInfo> full() {
            List<PackInfo> out = new ArrayList<>();
            for (PackInfo p : packs) {
                if (p.kind() == PackInfo.Kind.FULL) {
                    out.add(p);
                }
            }
            return out;
        }

        /** A pack by its name and size (a share code's reference), or null. */
        public String find(ShareCode.PackRef ref) {
            for (PackInfo p : packs) {
                if (p.name().equals(ref.name()) && sizeOf(p) == ref.size()) {
                    return p.key();
                }
            }
            for (PackInfo p : packs) {
                if (p.name().equals(ref.name())) {
                    return p.key();
                }
            }
            return null;
        }
    }

    static final int CACHE_VERSION = 1;

    private StylesIndex() {
    }

    /** A pack's size for share codes: the zip's size, or the total of a folder's own files. */
    public static long sizeOf(PackInfo p) {
        try {
            Path path = path(p.location());
            if (!Files.isDirectory(path)) {
                return Files.size(path);
            }
        } catch (IOException | RuntimeException ignored) {
            // Fall back to the files' sizes.
        }
        long s = 0;
        for (VanillaIndex.Entry e : p.files().values()) {
            s += e.size();
        }
        return s;
    }

    /** Where a pack is, as kept in the index: a plain path, or a URI inside the mod's jar. */
    public static String location(Path p) {
        return p.getFileSystem() == FileSystems.getDefault() ? p.toString() : p.toUri().toString();
    }

    public static Path path(String location) {
        return location.startsWith("jar:") || location.startsWith("file:") ? Path.of(URI.create(location)) : Path.of(location);
    }

    /** The packs to scan: personal styles (tab/style folders), resource packs, and the small user styles. */
    public static List<Candidate> candidates(Path personal, Path resourcePacks, Path userStyles) {
        List<Candidate> out = new ArrayList<>();
        if (personal != null && Files.isDirectory(personal)) {
            for (Path tab : list(personal)) {
                if (!Files.isDirectory(tab)) {
                    continue;
                }
                String t = tab.getFileName().toString().replace("/", "");
                for (Path style : list(tab)) {
                    String n = style.getFileName().toString().replace("/", "");
                    out.add(new Candidate("personal:" + t + "/" + n, n, PackInfo.Origin.PERSONAL, t, style));
                }
            }
        }
        if (resourcePacks != null) {
            for (Path p : PackScanner.packsIn(resourcePacks)) {
                String rel = resourcePacks.relativize(p).toString().replace('\\', '/');
                String n = p.getFileName().toString();
                if (n.toLowerCase(java.util.Locale.ROOT).endsWith(".zip")) {
                    n = n.substring(0, n.length() - 4);
                }
                out.add(new Candidate("rp:" + rel, n, PackInfo.Origin.RESOURCE_PACK, null, p));
            }
        }
        if (userStyles != null && Files.isDirectory(userStyles)) {
            for (Path tab : list(userStyles)) {
                if (!Files.isDirectory(tab)) {
                    continue;
                }
                if (Files.isDirectory(tab.resolve("assets"))) {
                    // A pack straight in client/styles.
                    out.add(new Candidate("user:" + tab.getFileName(), tab.getFileName().toString(), PackInfo.Origin.USER, null, tab));
                    continue;
                }
                for (Path style : list(tab)) {
                    if (Files.isDirectory(style)) {
                        out.add(new Candidate("user:" + tab.getFileName() + "/" + style.getFileName(), style.getFileName().toString(),
                                PackInfo.Origin.USER, tab.getFileName().toString(), style));
                    }
                }
            }
        }
        return out;
    }

    private static List<Path> list(Path folder) {
        try (Stream<Path> s = Files.list(folder)) {
            return s.filter(p -> !p.getFileName().toString().startsWith(".")).sorted(Comparator.comparing(p -> p.getFileName().toString())).toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    /** Scans the candidates, reusing what the cache has for packs that haven't changed. */
    public static Snapshot build(VanillaIndex vanilla, List<Candidate> candidates, Map<String, PackInfo> cached) {
        long t0 = System.nanoTime();
        int[] rescanned = {0};
        List<PackInfo> packs = Collections.synchronizedList(new ArrayList<>());
        candidates.parallelStream().forEach(c -> {
            try {
                long stamp = PackScanner.stamp(c.path());
                String loc = location(c.path());
                PackInfo old = cached.get(c.key());
                if (old != null && old.stamp() == stamp && old.location().equals(loc)) {
                    // Classified again (vanilla may be another version's), its file list reused.
                    packs.add(PackScanner.classify(c.key(), c.name(), c.origin(), c.tab(), loc, stamp, old.files(), vanilla));
                    return;
                }
                Map<String, VanillaIndex.Entry> files = Files.isDirectory(c.path()) ? PackScanner.listFolder(c.path()) : PackScanner.listZip(c.path());
                packs.add(PackScanner.classify(c.key(), c.name(), c.origin(), c.tab(), loc, stamp, files, vanilla));
                synchronized (rescanned) {
                    rescanned[0]++;
                }
            } catch (IOException | RuntimeException e) {
                io.github.georgwav.cosmetics.CosmeticsMod.LOGGER.warn("[Sol] Styles: couldn't read the pack {}: {}", c.path(), e.toString());
            }
        });
        List<PackInfo> sorted = new ArrayList<>(packs);
        sorted.sort(Comparator.comparing((PackInfo p) -> p.origin().ordinal() == 1 ? 0 : p.origin().ordinal() == 0 ? 1 : 2)
                .thenComparing(p -> p.tab() == null ? "" : p.tab()).thenComparing(p -> p.name().toLowerCase(java.util.Locale.ROOT)));
        Map<String, PackInfo> byKey = new LinkedHashMap<>();
        for (PackInfo p : sorted) {
            byKey.put(p.key(), p);
        }
        return new Snapshot(vanilla, List.copyOf(sorted), Collections.unmodifiableMap(byKey), (System.nanoTime() - t0) / 1_000_000L, rescanned[0]);
    }

    // ------------------------------------------------------------------ the cache file

    public static Map<String, PackInfo> readCache(Path file) {
        Map<String, PackInfo> out = new HashMap<>();
        if (!Files.exists(file)) {
            return out;
        }
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!root.has("v") || root.get("v").getAsInt() != CACHE_VERSION) {
                return out;
            }
            for (JsonElement e : root.getAsJsonArray("packs")) {
                JsonObject o = e.getAsJsonObject();
                Map<String, VanillaIndex.Entry> files = new HashMap<>();
                for (Map.Entry<String, JsonElement> f : o.getAsJsonObject("files").entrySet()) {
                    JsonArray a = f.getValue().getAsJsonArray();
                    files.put(f.getKey(), new VanillaIndex.Entry(a.get(0).getAsInt(), a.get(1).getAsLong()));
                }
                PackInfo p = new PackInfo(o.get("key").getAsString(), o.get("name").getAsString(),
                        PackInfo.Origin.valueOf(o.get("origin").getAsString()), o.get("location").getAsString(), o.get("stamp").getAsLong(),
                        files, 0, 0, 0, PackInfo.Kind.EMPTY, o.has("tab") ? o.get("tab").getAsString() : null);
                out.put(p.key(), p);
            }
        } catch (IOException | RuntimeException e) {
            // A broken cache is rebuilt.
            out.clear();
        }
        return out;
    }

    /** Keeps every file a pack really has (its vanilla copies too, so a second start can reclassify it alone). */
    public static void writeCache(Path file, Snapshot snap, Map<String, Map<String, VanillaIndex.Entry>> allFiles) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("v", CACHE_VERSION);
        JsonArray packs = new JsonArray();
        for (PackInfo p : snap.packs()) {
            JsonObject o = new JsonObject();
            o.addProperty("key", p.key());
            o.addProperty("name", p.name());
            o.addProperty("origin", p.origin().name());
            o.addProperty("location", p.location());
            o.addProperty("stamp", p.stamp());
            if (p.tab() != null) {
                o.addProperty("tab", p.tab());
            }
            JsonObject files = new JsonObject();
            Map<String, VanillaIndex.Entry> all = allFiles != null && allFiles.containsKey(p.key()) ? allFiles.get(p.key()) : p.files();
            for (Map.Entry<String, VanillaIndex.Entry> f : all.entrySet()) {
                JsonArray a = new JsonArray();
                a.add(f.getValue().crc());
                a.add(f.getValue().size());
                files.add(f.getKey(), a);
            }
            o.add("files", files);
            packs.add(o);
        }
        root.add("packs", packs);
        Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, root.toString(), StandardCharsets.UTF_8);
        try {
            Files.move(tmp, file, java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
