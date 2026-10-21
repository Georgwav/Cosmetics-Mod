package io.github.georgwav.cosmetics.sol.styles;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Reads what a pack changes, without decompressing anything in a zip: the central directory already has each file's
 * CRC-32 and size, and a file with vanilla's CRC and size is a copy of vanilla, which doesn't count. Folders are walked
 * (their files read once for the CRC). Paths under assets/minecraft/ are kept, and the sky mods' own namespaces
 * (FabricSkyboxes) as "ns:path". Plain Java, so it is unit-tested.
 */
public final class PackScanner {
    /** Namespaces besides minecraft whose files Styles uses (skyboxes). */
    static final List<String> OTHER_NAMESPACES = List.of("fabricskyboxes");
    /** A full pack changes more than this share of vanilla's textures. */
    static final double FULL_SHARE = 0.4;

    private PackScanner() {
    }

    /** The stamp a pack has now: a zip's size and modified time, or a folder's (every file's, without reading). */
    public static long stamp(Path pack) throws IOException {
        if (!Files.isDirectory(pack)) {
            return Files.size(pack) * 31L ^ Files.getLastModifiedTime(pack).toMillis();
        }
        long h = 17L;
        Path assets = pack.resolve("assets");
        if (!Files.isDirectory(assets)) {
            return h;
        }
        try (Stream<Path> walk = Files.walk(assets)) {
            for (Path p : (Iterable<Path>) walk::iterator) {
                BasicFileAttributes a = Files.readAttributes(p, BasicFileAttributes.class);
                if (a.isRegularFile()) {
                    h = h * 1_000_003L ^ pack.relativize(p).toString().hashCode();
                    h = h * 31L ^ a.size();
                    h = h * 31L ^ a.lastModifiedTime().toMillis();
                }
            }
        }
        return h;
    }

    /** Scans a pack (zip or folder) against vanilla. */
    public static PackInfo scan(String key, String name, PackInfo.Origin origin, String tab, Path pack, VanillaIndex vanilla) throws IOException {
        Map<String, VanillaIndex.Entry> all = Files.isDirectory(pack) ? listFolder(pack) : listZip(pack);
        return classify(key, name, origin, tab, StylesIndex.location(pack), stamp(pack), all, vanilla);
    }

    /** Sorts a pack's files into what it changes, adds and only copies, and so what kind of pack it is. */
    static PackInfo classify(String key, String name, PackInfo.Origin origin, String tab, String location, long stamp,
                             Map<String, VanillaIndex.Entry> all, VanillaIndex vanilla) {
        Map<String, VanillaIndex.Entry> files = new HashMap<>();
        int changed = 0, added = 0, copies = 0, changedAny = 0;
        for (Map.Entry<String, VanillaIndex.Entry> e : all.entrySet()) {
            String path = e.getKey();
            VanillaIndex.Entry f = e.getValue();
            if (vanilla.isVanilla(path, f.crc(), f.size())) {
                copies++;
                continue;
            }
            files.put(path, f);
            if (vanilla.has(path)) {
                changedAny++;
                if (VanillaIndex.isTexture(path)) {
                    changed++;
                }
            } else {
                added++;
            }
        }
        PackInfo.Kind kind = vanilla.textures() > 0 && changed > FULL_SHARE * vanilla.textures() ? PackInfo.Kind.FULL
                : changedAny > 0 ? PackInfo.Kind.PART : added > 0 ? PackInfo.Kind.OVERLAY : PackInfo.Kind.EMPTY;
        return new PackInfo(key, name, origin, location, stamp, Map.copyOf(files), changed, added, copies, kind, tab);
    }

    /** A zip's files from its central directory. */
    static Map<String, VanillaIndex.Entry> listZip(Path zip) throws IOException {
        Map<String, VanillaIndex.Entry> out = new HashMap<>();
        try (ZipFile z = new ZipFile(zip.toFile())) {
            Enumeration<? extends ZipEntry> entries = z.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                String path = e.isDirectory() ? null : relative(e.getName());
                if (path != null) {
                    out.put(path, new VanillaIndex.Entry((int) e.getCrc(), e.getSize()));
                }
            }
        }
        return out;
    }

    /** A folder's files, each read once for its CRC. */
    static Map<String, VanillaIndex.Entry> listFolder(Path pack) throws IOException {
        Map<String, VanillaIndex.Entry> out = new HashMap<>();
        Path assets = pack.resolve("assets");
        if (!Files.isDirectory(assets)) {
            return out;
        }
        byte[] buffer = new byte[64 * 1024];
        try (Stream<Path> walk = Files.walk(assets)) {
            for (Path p : (Iterable<Path>) walk::iterator) {
                if (!Files.isRegularFile(p)) {
                    continue;
                }
                String path = relative(pack.relativize(p).toString().replace('\\', '/'));
                if (path == null) {
                    continue;
                }
                CRC32 crc = new CRC32();
                long size = 0;
                try (InputStream in = Files.newInputStream(p)) {
                    for (int n; (n = in.read(buffer)) > 0; ) {
                        crc.update(buffer, 0, n);
                        size += n;
                    }
                }
                out.put(path, new VanillaIndex.Entry((int) crc.getValue(), size));
            }
        }
        return out;
    }

    /** A pack entry's path as Styles keys it, or null when Styles doesn't use it. */
    static String relative(String entry) {
        if (entry.startsWith("assets/minecraft/")) {
            String rest = entry.substring("assets/minecraft/".length());
            return rest.isEmpty() ? null : rest;
        }
        for (String ns : OTHER_NAMESPACES) {
            String prefix = "assets/" + ns + "/";
            if (entry.startsWith(prefix) && entry.length() > prefix.length()) {
                return ns + ":" + entry.substring(prefix.length());
            }
        }
        return null;
    }

    /** The packs in a folder (zips, and folders with pack.mcmeta), and in its groups up to two folders deep. */
    public static List<Path> packsIn(Path folder) {
        List<Path> out = new ArrayList<>();
        collect(folder, 0, out);
        return out;
    }

    private static void collect(Path folder, int depth, List<Path> out) {
        if (depth > 2 || !Files.isDirectory(folder)) {
            return;
        }
        try (Stream<Path> list = Files.list(folder)) {
            for (Path p : (Iterable<Path>) list.sorted()::iterator) {
                String n = p.getFileName().toString();
                if (n.startsWith(".")) {
                    continue;
                }
                if (Files.isDirectory(p)) {
                    if (Files.exists(p.resolve("pack.mcmeta"))) {
                        out.add(p);
                    } else {
                        collect(p, depth + 1, out);
                    }
                } else if (n.toLowerCase(java.util.Locale.ROOT).endsWith(".zip")) {
                    out.add(p);
                }
            }
        } catch (IOException ignored) {
            // An unreadable folder has no packs.
        }
    }
}
