import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Sol Styles' test packs (B.4), made from the game's own jar at test time and never committed:
 * <ul>
 * <li>Sol Test Red: the end crystal, every sword and the armour (worn and as items), turned red;</li>
 * <li>Sol Test Blue: fire (its animation kept) and stone, turned blue;</li>
 * <li>Sol Test Sky: the sun turned purple, and an OptiFine sky;</li>
 * <li>Sol Test Full and Full Two: every texture of the game, its hue turned half and a third of the way round (full
 * packs: the first is the scenario's base, the second folds under each part);</li>
 * <li>Sol Test Half: forty of the game's own textures as they are and one changed (the copies must be ignored).</li>
 * </ul>
 * Run with the JDK's source launcher: {@code java tools/styles-packs/MakePacks.java <client jar> <resourcepacks folder>}.
 */
public final class MakePacks {
    private static final String MCMETA = """
            {
              "pack": {
                "description": "%s (Sol Styles test pack)",
                "pack_format": 34,
                "supported_formats": [34, 999],
                "min_format": 34,
                "max_format": 999
              }
            }
            """;

    private MakePacks() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            System.err.println("usage: java MakePacks.java <client jar> <resourcepacks folder>");
            System.exit(2);
        }
        Path out = Path.of(args[1]);
        Files.createDirectories(out);
        long t0 = System.nanoTime();
        try (ZipFile game = new ZipFile(args[0])) {
            Map<String, byte[]> textures = new LinkedHashMap<>();
            Enumeration<? extends ZipEntry> en = game.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                String n = e.getName();
                if (n.startsWith("assets/minecraft/textures/") && (n.endsWith(".png") || n.endsWith(".png.mcmeta"))) {
                    textures.put(n, game.getInputStream(e).readAllBytes());
                }
            }
            Pattern red = Pattern.compile("assets/minecraft/textures/(entity/end_crystal/end_crystal\\.png|item/end_crystal\\.png"
                    + "|item/[a-z_]+_sword\\.png|entity/equipment/humanoid(_leggings)?/[a-z_]+\\.png|models/armor/[a-z_]+_layer_[12]\\.png"
                    + "|item/[a-z_]+_(helmet|chestplate|leggings|boots)\\.png)");
            write(out.resolve("Sol Test Red.zip"), "Sol Test Red", pick(textures, red, 0f, 0.85f));
            Pattern blue = Pattern.compile("assets/minecraft/textures/block/(fire_[01]\\.png(\\.mcmeta)?|stone\\.png)");
            write(out.resolve("Sol Test Blue.zip"), "Sol Test Blue", pick(textures, blue, 0.62f, 0.8f));
            Pattern sun = Pattern.compile("assets/minecraft/textures/environment/(celestial/)?sun\\.png");
            Map<String, byte[]> sky = pick(textures, sun, 0.8f, 0.7f);
            // Always on (no fade times), so the scenario's day frame shows it.
            sky.put("assets/minecraft/optifine/sky/world0/sky1.properties", ("source=./sky1.png\nblend=add\nrotate=true\nspeed=1.0\n"
                    + "axis=0.0 0.0 1.0\n").getBytes(StandardCharsets.UTF_8));
            sky.put("assets/minecraft/optifine/sky/world0/sky1.png", png(skyBox()));
            write(out.resolve("Sol Test Sky.zip"), "Sol Test Sky", sky);
            write(out.resolve("Sol Test Full.zip"), "Sol Test Full", turned(textures, 0.5f));
            // A second full pack, so the menu has full packs to fold while the first is the base.
            write(out.resolve("Sol Test Full Two.zip"), "Sol Test Full Two", turned(textures, 0.33f));
            write(out.resolve("Sol Test Half.zip"), "Sol Test Half", half(textures));
        }
        System.out.printf("Sol Styles test packs in %s (%d ms)%n", out, (System.nanoTime() - t0) / 1_000_000L);
    }

    /** The matching textures in one hue (their .mcmeta as they are). */
    private static Map<String, byte[]> pick(Map<String, byte[]> textures, Pattern which, float hue, float sat) throws IOException {
        Map<String, byte[]> out = new LinkedHashMap<>();
        for (Map.Entry<String, byte[]> e : textures.entrySet()) {
            if (!which.matcher(e.getKey()).matches()) {
                continue;
            }
            out.put(e.getKey(), e.getKey().endsWith(".mcmeta") ? e.getValue() : recolour(e.getValue(), hue, sat, false));
        }
        return out;
    }

    /** Every texture with its hue turned by {@code turn} of the way round. */
    private static Map<String, byte[]> turned(Map<String, byte[]> textures, float turn) throws IOException {
        Map<String, byte[]> out = new LinkedHashMap<>();
        for (Map.Entry<String, byte[]> e : textures.entrySet()) {
            byte[] made = e.getKey().endsWith(".mcmeta") ? e.getValue() : recolour(e.getValue(), turn, -1f, true);
            if (made != null) {
                out.put(e.getKey(), made);
            }
        }
        return out;
    }

    /** Forty of the game's textures as they are, and the diamond pickaxe changed. */
    private static Map<String, byte[]> half(Map<String, byte[]> textures) throws IOException {
        Map<String, byte[]> out = new LinkedHashMap<>();
        List<String> blocks = new ArrayList<>();
        for (String k : textures.keySet()) {
            if (k.startsWith("assets/minecraft/textures/block/") && k.endsWith(".png")) {
                blocks.add(k);
            }
        }
        for (int i = 0; i < Math.min(40, blocks.size()); i++) {
            out.put(blocks.get(i), textures.get(blocks.get(i)));
        }
        String pick = "assets/minecraft/textures/item/diamond_pickaxe.png";
        if (textures.containsKey(pick)) {
            out.put(pick, recolour(textures.get(pick), 0f, 0.85f, false));
        }
        return out;
    }

    /**
     * A picture with every coloured or grey pixel put in {@code hue} at least {@code sat} saturation (or, with
     * {@code turn}, its hue turned by {@code hue}); alpha kept. Null when it can't be read.
     */
    private static byte[] recolour(byte[] data, float hue, float sat, boolean turn) throws IOException {
        BufferedImage in = ImageIO.read(new ByteArrayInputStream(data));
        if (in == null) {
            return null;
        }
        int w = in.getWidth(), h = in.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        float[] hsb = new float[3];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = in.getRGB(x, y);
                int a = p >>> 24;
                Color.RGBtoHSB(p >> 16 & 255, p >> 8 & 255, p & 255, hsb);
                float nh = turn ? (hsb[0] + hue) % 1f : hue;
                float ns = turn ? hsb[1] : Math.max(sat, hsb[1]);
                float nb = turn ? hsb[2] : Math.max(0.25f, hsb[2]);
                out.setRGB(x, y, a << 24 | Color.HSBtoRGB(nh, ns, nb) & 0xFFFFFF);
            }
        }
        return png(out);
    }

    /**
     * An OptiFine sky box (3 x 2 faces of 64: bottom, top, east / south, west, north): the top purple, the sides
     * purple fading toward the horizon with a few stars, each side with a band of its own colour along its top so the
     * faces can be told apart in a shot, the bottom clear.
     */
    private static BufferedImage skyBox() {
        BufferedImage img = new BufferedImage(192, 128, BufferedImage.TYPE_INT_ARGB);
        int[] bands = {0, 0x6A2FD0, 0xFF4040, 0x40FF40, 0x4080FF, 0xFFD040};
        long seed = 7L;
        for (int y = 0; y < 128; y++) {
            for (int x = 0; x < 192; x++) {
                int face = (x / 64) + 3 * (y / 64), fy = y % 64;
                seed = seed * 6364136223846793005L + 1442695040888963407L;
                boolean star = (seed >>> 58) == 0;
                int argb;
                if (face == 0) {
                    argb = 0; // the bottom: nothing
                } else if (face == 1) {
                    argb = star ? 0xFFFFFFFF : 0xC86A2FD0;
                } else if (fy < 6) {
                    argb = 0xFF000000 | bands[face];
                } else {
                    int a = (int) (200 * (1f - fy / 64f));
                    argb = star ? 0xFFFFFFFF : a << 24 | 0x6A2FD0;
                }
                img.setRGB(x, y, argb);
            }
        }
        return img;
    }

    private static byte[] png(BufferedImage img) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        ImageIO.write(img, "png", bo);
        return bo.toByteArray();
    }

    private static void write(Path zip, String name, Map<String, byte[]> files) throws IOException {
        try (OutputStream os = Files.newOutputStream(zip); ZipOutputStream z = new ZipOutputStream(os)) {
            z.putNextEntry(new ZipEntry("pack.mcmeta"));
            z.write(String.format(MCMETA, name).getBytes(StandardCharsets.UTF_8));
            z.closeEntry();
            for (Map.Entry<String, byte[]> e : files.entrySet()) {
                z.putNextEntry(new ZipEntry(e.getKey()));
                z.write(e.getValue());
                z.closeEntry();
            }
        }
        System.out.printf("  %s: %d file(s)%n", zip.getFileName(), files.size());
    }
}
