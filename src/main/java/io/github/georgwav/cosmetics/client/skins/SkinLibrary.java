package io.github.georgwav.cosmetics.client.skins;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;

/**
 * The skins saved in the launcher (Threadrinth, like the Modrinth App): its database's
 * {@code custom_minecraft_skins} (whose skin, which texture, wide or slim arms, in which order) and
 * {@code custom_minecraft_skin_textures} (the PNG of each texture). Only read, never written.
 */
public final class SkinLibrary {
    /** One saved skin: its texture's key, its PNG, and whether its arms are slim. */
    public record Skin(String key, byte[] png, boolean slim) {
    }

    private SkinLibrary() {
    }

    /**
     * The skins saved for this player, in the launcher's order; when none are saved for them (another account), the
     * skins of every account.
     */
    public static List<Skin> load(Path database, UUID player) throws IOException {
        try (SqliteReader db = SqliteReader.open(database)) {
            if (!db.hasTable("custom_minecraft_skins") || !db.hasTable("custom_minecraft_skin_textures")) {
                return List.of();
            }
            Map<String, byte[]> textures = new HashMap<>();
            for (Map<String, Object> row : db.table("custom_minecraft_skin_textures")) {
                if (row.get("texture_key") instanceof String key && row.get("texture") instanceof byte[] png) {
                    textures.put(key, png);
                }
            }
            List<Map<String, Object>> rows = new ArrayList<>(db.table("custom_minecraft_skins"));
            rows.sort(Comparator.comparingLong(row -> row.get("display_order") instanceof Long order ? order : 0L));
            List<Skin> mine = skins(rows, textures, player);
            return mine.isEmpty() ? skins(rows, textures, null) : mine;
        }
    }

    private static List<Skin> skins(List<Map<String, Object>> rows, Map<String, byte[]> textures, UUID player) {
        // One entry per texture: the same skin saved with different capes shows once.
        Map<String, Skin> byKey = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            if (player != null && !player.equals(uuid(row.get("minecraft_user_uuid")))) {
                continue;
            }
            if (row.get("texture_key") instanceof String key && textures.containsKey(key) && !byKey.containsKey(key)) {
                boolean slim = "SLIM".equalsIgnoreCase(String.valueOf(row.get("variant")));
                byKey.put(key, new Skin(key, textures.get(key), slim));
            }
        }
        return new ArrayList<>(byKey.values());
    }

    /** A player's id as the database keeps it: 16 bytes, or text with or without dashes. */
    static UUID uuid(Object value) {
        try {
            if (value instanceof byte[] bytes && bytes.length == 16) {
                long hi = 0;
                long lo = 0;
                for (int i = 0; i < 8; i++) {
                    hi = hi << 8 | (bytes[i] & 0xFF);
                    lo = lo << 8 | (bytes[i + 8] & 0xFF);
                }
                return new UUID(hi, lo);
            }
            if (value instanceof String text) {
                String hex = text.replace("-", "").trim().toLowerCase(Locale.ROOT);
                if (hex.length() == 32) {
                    byte[] bytes = HexFormat.of().parseHex(hex);
                    return uuid(bytes);
                }
            }
        } catch (IllegalArgumentException ignored) {
            // Not an id.
        }
        return null;
    }

    /**
     * A skin's (or a cape's) PNG as 64 x 64 ARGB pixels: a 64 x 64 skin as it is, an old 64 x 32 skin with its right
     * arm and leg copied to the left ones, a 64 x 32 cape in the top half ({@code height} 32 asks for a cape).
     */
    public static int[] pixels(byte[] png, int height) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
            if (image == null || image.getWidth() % 64 != 0) {
                return null;
            }
            int scale = image.getWidth() / 64;
            int h = image.getHeight() / scale;
            if (h != 64 && h != 32) {
                return null;
            }
            int[] argb = new int[64 * 64];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < 64; x++) {
                    argb[y * 64 + x] = image.getRGB(x * scale, y * scale);
                }
            }
            if (height == 64 && h == 32) {
                copy(argb, 0, 16, 16, 32, 16, 48);
                copy(argb, 40, 16, 16, 32, 32, 48);
            }
            return argb;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static void copy(int[] argb, int fromX, int fromY, int w, int h, int toX, int toY) {
        for (int y = 0; y < h; y++) {
            System.arraycopy(argb, (fromY + y) * 64 + fromX, argb, (toY + y) * 64 + toX, w);
        }
    }
}
