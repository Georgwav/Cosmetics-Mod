package io.github.georgwav.cosmetics.sol.styles;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Reads the mix's files: a pack file straight from its zip (kept open, read on demand) or folder, the game's own from
 * its jar, and Sol's made files from the cache, making them first when they aren't there yet (off the render thread).
 * Only made files are cached ({@code client/styles-cache/<hash>.png}); nothing is ever copied out of a pack.
 */
public final class Builder implements AutoCloseable {
    private final Path cache;
    private final Path gameJar;
    /** Open zips by path, the most recently used kept (16 at most). */
    private final Map<String, ZipFile> zips = new LinkedHashMap<>(16, 0.75f, true);

    public Builder(Path cache, Path gameJar) {
        this.cache = cache;
        this.gameJar = gameJar;
    }

    /** A source's bytes, or null when it can't be read. */
    public byte[] read(Mixer.Source s) throws IOException {
        if (s instanceof Mixer.FromPack p) {
            return readPack(p.location(), p.entry());
        }
        if (s instanceof Mixer.FromGame g) {
            return gameJar == null ? null : readPack(gameJar.toString(), "assets/minecraft/" + g.path());
        }
        if (s instanceof Mixer.FromMod m) {
            try (var in = Builder.class.getResourceAsStream("/assets/cosmeticsmod/" + m.path())) {
                return in == null ? null : in.readAllBytes();
            }
        }
        Mixer.Made m = (Mixer.Made) s;
        Path out = cached(m);
        if (!Files.exists(out)) {
            byte[] made = make(m);
            if (made == null) {
                return null;
            }
            Files.createDirectories(out.getParent());
            Path tmp = out.resolveSibling(out.getFileName() + ".tmp");
            Files.write(tmp, made);
            try {
                Files.move(tmp, out, java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                Files.move(tmp, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            return made;
        }
        return Files.readAllBytes(out);
    }

    /** Where a made file lives in the cache. */
    public Path cached(Mixer.Made m) {
        return cache.resolve(m.key() + (m.op().equals("extrude") ? ".json" : ".png"));
    }

    private byte[] readPack(String location, String entry) throws IOException {
        Path loc = StylesIndex.path(location);
        if (Files.isDirectory(loc)) {
            Path f = loc.resolve(entry);
            return Files.isRegularFile(f) ? Files.readAllBytes(f) : null;
        }
        ZipFile z;
        synchronized (zips) {
            z = zips.get(location);
            if (z == null) {
                z = new ZipFile(loc.toFile());
                zips.put(location, z);
                if (zips.size() > 16) {
                    var eldest = zips.entrySet().iterator().next();
                    eldest.getValue().close();
                    zips.remove(eldest.getKey());
                }
            }
        }
        ZipEntry e = z.getEntry(entry);
        if (e == null) {
            return null;
        }
        try (InputStream in = z.getInputStream(e)) {
            return in.readAllBytes();
        }
    }

    /** Makes a file from its recipe. */
    byte[] make(Mixer.Made m) throws IOException {
        byte[] input = read(m.input());
        if (input == null) {
            return null;
        }
        Map<String, String> p = m.params();
        if (m.op().equals("extrude")) {
            Pixels.Image img = decode(input);
            Extruder.Model model = img == null ? null : Extruder.extrude(img, p.getOrDefault("texture", "minecraft:item/missing"),
                    Float.parseFloat(p.getOrDefault("thickness", "1")), Boolean.parseBoolean(p.getOrDefault("handheld", "false")));
            return model == null ? null : model.json().toString().getBytes(StandardCharsets.UTF_8);
        }
        Pixels.Image img = decode(input);
        if (img == null) {
            return null;
        }
        Pixels.Image out = switch (m.op()) {
            case "recolour" -> {
                String t = p.getOrDefault("tint", "-1");
                int hue = Integer.parseInt(p.getOrDefault("hue", "0"));
                float sat = Float.parseFloat(p.getOrDefault("saturation", "1")), bright = Float.parseFloat(p.getOrDefault("brightness", "1"));
                int tint = t.equals("-1") ? -1 : Integer.parseInt(t, 16);
                String rect = p.get("rect");
                if (rect != null) {
                    String[] r = rect.split(",");
                    yield Pixels.recolour(img, hue, sat, bright, tint, new float[]{Float.parseFloat(r[0]), Float.parseFloat(r[1]),
                            Float.parseFloat(r[2]), Float.parseFloat(r[3])});
                }
                yield Pixels.recolour(img, hue, sat, bright, tint);
            }
            case "glow" -> Pixels.glow(img, Integer.parseInt(p.getOrDefault("tint", "ffffff"), 16));
            case "pixel_glow" -> Pixels.pixelGlow(img);
            case "mark" -> Pixels.mark(img, Integer.parseInt(p.getOrDefault("tint", "ffffff"), 16));
            case "see_through" -> Pixels.seeThrough(img, Float.parseFloat(p.getOrDefault("share", "0.35")));
            case "off" -> Pixels.off(img);
            case "low_fire" -> Pixels.lowFire(img);
            case "keep_opaque" -> {
                byte[] v = gameJar == null ? null : readPack(gameJar.toString(), "assets/minecraft/" + p.get("vanilla"));
                Pixels.Image vi = v == null ? null : decode(v);
                yield vi != null && vi.opaque() ? Pixels.keepOpaque(img) : img;
            }
            default -> img;
        };
        return encode(out);
    }

    /** A PNG as pixels, or null when it isn't one. */
    public static Pixels.Image decode(byte[] png) throws IOException {
        BufferedImage b = ImageIO.read(new ByteArrayInputStream(png));
        if (b == null) {
            return null;
        }
        int w = b.getWidth(), h = b.getHeight();
        return new Pixels.Image(w, h, b.getRGB(0, 0, w, h, null, 0, w));
    }

    public static byte[] encode(Pixels.Image img) throws IOException {
        BufferedImage b = new BufferedImage(img.w(), img.h(), BufferedImage.TYPE_INT_ARGB);
        b.setRGB(0, 0, img.w(), img.h(), img.argb(), 0, img.w());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(b, "png", out);
        return out.toByteArray();
    }

    @Override
    public void close() {
        synchronized (zips) {
            for (ZipFile z : zips.values()) {
                try {
                    z.close();
                } catch (IOException ignored) {
                    // Closing anyway.
                }
            }
            zips.clear();
        }
    }
}
