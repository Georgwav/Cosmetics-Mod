package io.github.georgwav.cosmetics.sol.styles;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Test packs made in the tests: zips and folders of plain-colour textures, never a real pack. */
final class StylesTestPacks {
    private StylesTestPacks() {
    }

    static final String MCMETA = "{\"pack\":{\"pack_format\":34,\"description\":\"test\"}}";

    /** A 16x16 (or w x h) texture all in one colour. */
    static byte[] png(int w, int h, int argb) throws IOException {
        int[] px = new int[w * h];
        java.util.Arrays.fill(px, argb);
        return Builder.encode(new Pixels.Image(w, h, px));
    }

    /** A zip pack with these files (paths under assets/minecraft/). */
    static Path zip(Path dir, String name, Map<String, byte[]> files) throws IOException {
        Path zip = dir.resolve(name + ".zip");
        try (OutputStream o = Files.newOutputStream(zip); ZipOutputStream z = new ZipOutputStream(o)) {
            z.putNextEntry(new ZipEntry("pack.mcmeta"));
            z.write(MCMETA.getBytes(StandardCharsets.UTF_8));
            for (Map.Entry<String, byte[]> e : new java.util.TreeMap<>(files).entrySet()) {
                z.putNextEntry(new ZipEntry(Mixer.entryName(e.getKey())));
                z.write(e.getValue());
            }
        }
        return zip;
    }

    /** A folder pack with these files. */
    static Path folder(Path dir, String name, Map<String, byte[]> files) throws IOException {
        Path root = dir.resolve(name);
        Files.createDirectories(root);
        Files.writeString(root.resolve("pack.mcmeta"), MCMETA);
        for (Map.Entry<String, byte[]> e : files.entrySet()) {
            Path f = root.resolve(Mixer.entryName(e.getKey()));
            Files.createDirectories(f.getParent());
            Files.write(f, e.getValue());
        }
        return root;
    }

    /** A vanilla index of these files (as if the game had them). */
    static VanillaIndex vanilla(Map<String, byte[]> files) {
        Map<String, VanillaIndex.Entry> m = new java.util.HashMap<>();
        files.forEach((k, v) -> {
            java.util.zip.CRC32 c = new java.util.zip.CRC32();
            c.update(v);
            m.put(k, new VanillaIndex.Entry((int) c.getValue(), v.length));
        });
        return new VanillaIndex(m);
    }
}
