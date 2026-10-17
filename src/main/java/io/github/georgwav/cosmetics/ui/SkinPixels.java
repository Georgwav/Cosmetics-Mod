package io.github.georgwav.cosmetics.ui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.georgwav.cosmetics.client.MainFolder;
import io.github.georgwav.cosmetics.client.skins.OwnSkin;
import io.github.georgwav.cosmetics.render.SoftRaster;
import io.github.georgwav.cosmetics.sol.util.SolExecutors;
import net.minecraft.client.Minecraft;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

/**
 * The skin the mannequin wears: the one you applied in the Wardrobe, else your account's (downloaded once from
 * Mojang's skin server, kept in the client folder for the next start), else the game's default skin for your
 * account. Render thread, apart from the download.
 */
public final class SkinPixels {
    /** A skin's 64 x 64 pixels and whether its arms are slim. */
    public record Skin(SoftRaster.Texture texture, boolean slim) {
    }

    private static volatile Skin account;
    private static boolean asked;
    private static Skin own;
    private static int[] ownPixels;
    private static Skin fallback;

    private SkinPixels() {
    }

    public static Skin current() {
        if (io.github.georgwav.cosmetics.sol.client.Streamer.on()) {
            return fallback();
        }
        int[] pixels = OwnSkin.pixels();
        if (pixels != null) {
            if (pixels != ownPixels) {
                ownPixels = pixels;
                own = new Skin(new SoftRaster.Texture(64, 64, pixels), OwnSkin.slim());
            }
            return own;
        }
        if (!asked) {
            asked = true;
            SolExecutors.IO.execute(SkinPixels::fetch);
        }
        Skin a = account;
        return a != null ? a : fallback();
    }

    /** The game's default skin for your account. */
    private static Skin fallback() {
        if (fallback == null) {
            var skin = net.minecraft.client.resources.DefaultPlayerSkin.get(Minecraft.getInstance().getUser().getProfileId());
            //? if >=1.21.9 {
            String id = skin.body().texturePath().toString();
            boolean slim = skin.model() == net.minecraft.world.entity.player.PlayerModelType.SLIM;
            //?} else {
            /*String id = skin.texture().toString();
            boolean slim = skin.model() == net.minecraft.client.resources.PlayerSkin.Model.SLIM;
            *///?}
            fallback = new Skin(TilePictures.read(id), slim);
        }
        return fallback;
    }

    private static Path cached() {
        return MainFolder.clientFolder().resolve("cache").resolve("own_skin.png");
    }

    /** Your account's skin: the copy from last time at once, then the current one if it changed. */
    private static void fetch() {
        Path file = cached();
        Path meta = file.resolveSibling("own_skin.txt");
        String knownUrl = null;
        try {
            if (Files.isRegularFile(file) && Files.isRegularFile(meta)) {
                String[] m = Files.readString(meta).split("\n");
                knownUrl = m[0];
                Skin s = decode(Files.readAllBytes(file), m.length > 1 && m[1].equals("slim"));
                if (s != null) {
                    account = s;
                }
            }
            String[] wanted = profileSkin();
            if (wanted == null || wanted[0].equals(knownUrl)) {
                return;
            }
            byte[] png = io.github.georgwav.cosmetics.sol.music.Http.bytes(wanted[0]);
            Skin s = png == null ? null : decode(png, wanted[1] != null);
            if (s != null) {
                account = s;
                Files.createDirectories(file.getParent());
                Files.write(file, png);
                Files.writeString(meta, wanted[0] + "\n" + (wanted[1] != null ? "slim" : "wide"));
            }
        } catch (Exception e) {
            // No skin of your own then: the default one.
        }
    }

    /** Your profile's skin address and model ("slim" or null), or null when the profile has none. */
    private static String[] profileSkin() {
        var profile = Minecraft.getInstance().getGameProfile();
        //? if >=1.21.9 {
        var textures = profile.properties().get("textures");
        //?} else {
        /*var textures = profile.getProperties().get("textures");
        *///?}
        if (textures == null || textures.isEmpty()) {
            return null;
        }
        String json = new String(Base64.getDecoder().decode(textures.iterator().next().value()), StandardCharsets.UTF_8);
        JsonObject skin = JsonParser.parseString(json).getAsJsonObject().getAsJsonObject("textures").getAsJsonObject("SKIN");
        if (skin == null || !skin.has("url")) {
            return null;
        }
        String url = skin.get("url").getAsString();
        if (!url.startsWith("http://textures.minecraft.net/") && !url.startsWith("https://textures.minecraft.net/")) {
            return null;
        }
        boolean slim = skin.has("metadata") && "slim".equals(skin.getAsJsonObject("metadata").get("model").getAsString());
        return new String[]{url.replace("http://", "https://"), slim ? "slim" : null};
    }

    /**
     * A skin from PNG bytes, prepared as the game prepares downloaded skins: an old 64 x 32 one gets its left leg
     * and arm as mirrored copies of the right ones (face by face) and a see-through hat only where it isn't all
     * solid; the base layer (head, body, arms and legs under the outer layer) is made solid.
     */
    static Skin decode(byte[] png, boolean slim) throws java.io.IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        if (image == null || image.getWidth() != 64 || (image.getHeight() != 64 && image.getHeight() != 32)) {
            return null;
        }
        int[] argb = new int[64 * 64];
        image.getRGB(0, 0, 64, image.getHeight(), argb, 0, 64);
        boolean legacy = image.getHeight() == 32;
        if (legacy) {
            int[][] rects = {{4, 16, 16, 32, 4, 4}, {8, 16, 16, 32, 4, 4}, {0, 20, 24, 32, 4, 12}, {4, 20, 16, 32, 4, 12},
                    {8, 20, 8, 32, 4, 12}, {12, 20, 16, 32, 4, 12}, {44, 16, -8, 32, 4, 4}, {48, 16, -8, 32, 4, 4},
                    {40, 20, 0, 32, 4, 12}, {44, 20, -8, 32, 4, 12}, {48, 20, -16, 32, 4, 12}, {52, 20, -8, 32, 4, 12}};
            for (int[] r : rects) {
                copyMirrored(argb, r[0], r[1], r[2], r[3], r[4], r[5]);
            }
            hatHack(argb);
        }
        solid(argb, 0, 0, 32, 16);
        solid(argb, 0, 16, 64, 32);
        solid(argb, 16, 48, 48, 64);
        return new Skin(new SoftRaster.Texture(64, 64, argb), slim);
    }

    /** The game's legacy copy: the rect at (x, y) to (x + dx, y + dy), mirrored left to right. */
    private static void copyMirrored(int[] argb, int x, int y, int dx, int dy, int w, int h) {
        for (int row = 0; row < h; row++) {
            for (int col = 0; col < w; col++) {
                argb[(y + dy + row) * 64 + x + dx + col] = argb[(y + row) * 64 + x + w - 1 - col];
            }
        }
    }

    /** An old skin's hat that's solid everywhere was never meant as a hat: it goes see-through. */
    private static void hatHack(int[] argb) {
        for (int y = 0; y < 16; y++) {
            for (int x = 32; x < 64; x++) {
                if ((argb[y * 64 + x] >>> 24) < 128) {
                    return;
                }
            }
        }
        for (int y = 0; y < 16; y++) {
            for (int x = 32; x < 64; x++) {
                argb[y * 64 + x] &= 0x00FFFFFF;
            }
        }
    }

    private static void solid(int[] argb, int x0, int y0, int x1, int y1) {
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                argb[y * 64 + x] |= 0xFF000000;
            }
        }
    }
}
