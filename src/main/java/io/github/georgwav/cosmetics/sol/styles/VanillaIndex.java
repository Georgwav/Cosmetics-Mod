package io.github.georgwav.cosmetics.sol.styles;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * The game's own files for this version, by path under assets/minecraft/, with each one's CRC-32 and size: read from
 * the client jar's central directory (no file is decompressed), once per version, then from a small cache. A pack file
 * with the same CRC and size is a copy of vanilla, which Styles ignores. Plain Java, so it is unit-tested.
 */
public final class VanillaIndex {
    /** One file's CRC-32 and size. */
    public record Entry(int crc, long size) {
    }

    private static final String PREFIX = "assets/minecraft/";
    private static final int CACHE_MAGIC = 0x534F4C56;

    private final Map<String, Entry> files;
    private final int textures;

    VanillaIndex(Map<String, Entry> files) {
        this.files = Collections.unmodifiableMap(files);
        int t = 0;
        for (String p : files.keySet()) {
            if (isTexture(p)) {
                t++;
            }
        }
        this.textures = t;
    }

    public static boolean isTexture(String path) {
        return path.startsWith("textures/") && path.endsWith(".png");
    }

    public Entry get(String path) {
        return files.get(path);
    }

    public boolean has(String path) {
        return files.containsKey(path);
    }

    public Map<String, Entry> all() {
        return files;
    }

    /** How many textures vanilla has (a full pack changes more than 40% of them). */
    public int textures() {
        return textures;
    }

    /** Whether a pack's file at {@code path} is just vanilla's own. */
    public boolean isVanilla(String path, int crc, long size) {
        Entry e = files.get(path);
        return e != null && e.crc == crc && e.size == size;
    }

    /** From a jar's central directory: every assets/minecraft/ file's CRC and size. */
    public static VanillaIndex ofZip(Path jar) throws IOException {
        Map<String, Entry> out = new HashMap<>();
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                if (!e.isDirectory() && e.getName().startsWith(PREFIX)) {
                    out.put(e.getName().substring(PREFIX.length()), new Entry((int) e.getCrc(), e.getSize()));
                }
            }
        }
        return new VanillaIndex(out);
    }

    /** From an unpacked folder (the game's assets/minecraft/ folder itself), reading each file for its CRC. */
    public static VanillaIndex ofFolder(Path assetsMinecraft) throws IOException {
        Map<String, Entry> out = new HashMap<>();
        try (Stream<Path> walk = Files.walk(assetsMinecraft)) {
            for (Path p : (Iterable<Path>) walk::iterator) {
                if (Files.isRegularFile(p)) {
                    byte[] data = Files.readAllBytes(p);
                    CRC32 crc = new CRC32();
                    crc.update(data);
                    out.put(assetsMinecraft.relativize(p).toString().replace('\\', '/'), new Entry((int) crc.getValue(), data.length));
                }
            }
        }
        return new VanillaIndex(out);
    }

    /**
     * Where the game's assets are: the jar (or folder) the class loader finds assets/minecraft/ in. Null when it can't
     * be told (then Styles treats every pack file as changed).
     */
    public static Path locate() {
        try {
            URL url = VanillaIndex.class.getResource("/assets/minecraft/textures/block/stone.png");
            if (url == null) {
                return null;
            }
            String s = url.toString();
            if (s.startsWith("jar:")) {
                String file = s.substring(4, s.indexOf("!/"));
                return Path.of(URI.create(file));
            }
            if (s.startsWith("file:")) {
                // .../assets/minecraft/textures/block/stone.png -> .../assets/minecraft
                return Path.of(URI.create(s)).getParent().getParent().getParent();
            }
        } catch (Exception ignored) {
            // Unknown layout: no index.
        }
        return null;
    }

    /** This version's index: from the cache when it was made from the same file, else built and cached. */
    public static VanillaIndex load(Path cache) {
        Path source = locate();
        if (source == null) {
            return new VanillaIndex(new HashMap<>());
        }
        try {
            long stamp = Files.isDirectory(source) ? 0L : Files.size(source) ^ Files.getLastModifiedTime(source).toMillis();
            String key = source.toString();
            if (Files.exists(cache)) {
                try (DataInputStream in = new DataInputStream(new java.io.BufferedInputStream(Files.newInputStream(cache)))) {
                    VanillaIndex v = read(in, key, stamp);
                    if (v != null) {
                        return v;
                    }
                } catch (IOException ignored) {
                    // Rebuilt below.
                }
            }
            VanillaIndex v = Files.isDirectory(source) ? ofFolder(source) : ofZip(source);
            Files.createDirectories(cache.getParent());
            try (DataOutputStream out = new DataOutputStream(new java.io.BufferedOutputStream(Files.newOutputStream(cache)))) {
                v.write(out, key, stamp);
            }
            return v;
        } catch (IOException e) {
            io.github.georgwav.cosmetics.CosmeticsMod.LOGGER.warn("[Sol] Styles: couldn't index the game's files: {}", e.toString());
            return new VanillaIndex(new HashMap<>());
        }
    }

    void write(OutputStream stream, String key, long stamp) throws IOException {
        DataOutputStream out = stream instanceof DataOutputStream d ? d : new DataOutputStream(stream);
        out.writeInt(CACHE_MAGIC);
        out.writeUTF(key);
        out.writeLong(stamp);
        out.writeInt(files.size());
        for (Map.Entry<String, Entry> e : files.entrySet()) {
            out.writeUTF(e.getKey());
            out.writeInt(e.getValue().crc());
            out.writeLong(e.getValue().size());
        }
        out.flush();
    }

    /** The cached index, or null when it was made from another file. */
    static VanillaIndex read(InputStream stream, String key, long stamp) throws IOException {
        DataInputStream in = stream instanceof DataInputStream d ? d : new DataInputStream(stream);
        if (in.readInt() != CACHE_MAGIC || !in.readUTF().equals(key) || in.readLong() != stamp) {
            return null;
        }
        int n = in.readInt();
        Map<String, Entry> files = new HashMap<>(n * 2);
        for (int i = 0; i < n; i++) {
            files.put(in.readUTF(), new Entry(in.readInt(), in.readLong()));
        }
        return new VanillaIndex(files);
    }
}
