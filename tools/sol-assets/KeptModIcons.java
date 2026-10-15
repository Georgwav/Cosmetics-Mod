import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.georgwav.cosmetics.sol.ui.kit.IconRaster;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Makes the "Sol for kept mods" pack's pictures (run from the repository root; see make_kept_mods.sh):
 * Simple Voice Chat's microphone and speaker icons drawn from Sol's own line icons (sol/icons.json), and Jade's
 * tooltip background as Sol glass. Nothing is copied from the mods; only their file names and sizes are matched.
 */
public final class KeptModIcons {
    static final Path OUT = Path.of("src/main/resources/resourcepacks/sol_kept_mods/assets");
    static JsonObject icons;

    public static void main(String[] args) throws Exception {
        icons = JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/cosmeticsmod/sol/icons.json"))).getAsJsonObject();
        Path vc = OUT.resolve("voicechat/textures/icons");
        Files.createDirectories(vc);
        int white = 0xFFFFFFFF, violet = 0xFFC4A7FF, red = 0xFFFF6A5A;
        icon("mic", white, false, vc.resolve("microphone.png"));
        icon("mic", white, true, vc.resolve("microphone_off.png"));
        icon("mic", violet, false, vc.resolve("microphone_whisper.png"));
        icon("speaker", white, false, vc.resolve("speaker.png"));
        icon("speaker", white, true, vc.resolve("speaker_off.png"));
        icon("speaker", white, true, vc.resolve("speaker_small_off.png"));
        icon("speaker", violet, false, vc.resolve("speaker_whisper.png"));
        icon("signal", red, true, vc.resolve("disconnected.png"));
        Path jade = OUT.resolve("jade/textures/gui/sprites/tooltip");
        Files.createDirectories(jade);
        ImageIO.write(glass(100, 6f), "png", jade.resolve("dark_background.png").toFile());
        ImageIO.write(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), "png", jade.resolve("dark_frame.png").toFile());
        System.out.println("kept-mod pictures written to " + OUT);
    }

    /** A 16 x 16 icon: the line icon in {@code color} with its soft fill, and a red slash when {@code off}. */
    static void icon(String name, int color, boolean off, Path out) throws Exception {
        int px = 16;
        float[] fill = new float[px * px], stroke = new float[px * px];
        IconRaster.raster(IconRaster.parse(icons.getAsJsonArray(name)), px, fill, stroke);
        BufferedImage img = new BufferedImage(px, px, BufferedImage.TYPE_INT_ARGB);
        for (int i = 0; i < px * px; i++) {
            float a = Math.min(1f, stroke[i] + fill[i] * 0.22f);
            img.setRGB(i % px, i / px, ((int) (a * 255f + 0.5f) << 24) | (color & 0xFFFFFF));
        }
        if (off) {
            // The slash: a dark gap, then the red line over it, corner to corner.
            for (int i = 1; i < px - 1; i++) {
                for (int d = -1; d <= 1; d++) {
                    int x = i + d, y = px - 1 - i;
                    if (x >= 0 && x < px) {
                        img.setRGB(x, y, d == 0 ? 0xFF000000 | (0xFF6A5A) : 0x00000000);
                    }
                }
            }
        }
        ImageIO.write(img, "png", out.toFile());
    }

    /** Sol glass for a nine-slice: dark panel, rounded corners, a faint rim and a highlight along the top. */
    static BufferedImage glass(int size, float r) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float cx = Math.max(0f, Math.max(r - (x + 0.5f), (x + 0.5f) - (size - r)));
                float cy = Math.max(0f, Math.max(r - (y + 0.5f), (y + 0.5f) - (size - r)));
                float d = (float) Math.sqrt(cx * cx + cy * cy) - r;
                float inside = Math.max(0f, Math.min(1f, 0.5f - d));
                if (inside <= 0f) {
                    continue;
                }
                float edge = Math.max(0f, Math.min(1f, 1.5f + d));
                int panel = 0x0C0C12;
                float a = 0.9f * inside;
                int rgb = panel;
                if (edge > 0f) {
                    // The rim: lighter at the top edge.
                    float k = edge * (y < size / 2 ? 0.26f : 0.14f);
                    rgb = mix(panel, 0xFFFFFF, k);
                    a = Math.max(a, (0.9f + 0.1f * edge) * inside);
                } else if (y == 2 || y == 3) {
                    rgb = mix(panel, 0xFFFFFF, y == 2 ? 0.08f : 0.04f);
                }
                img.setRGB(x, y, ((int) (a * 255f + 0.5f) << 24) | rgb);
            }
        }
        return img;
    }

    static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
        int g = (int) (((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
        int bl = (int) ((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
        return r << 16 | g << 8 | bl;
    }
}
