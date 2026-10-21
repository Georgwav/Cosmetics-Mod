package io.github.georgwav.cosmetics.sol.ui.kit;

import io.github.georgwav.cosmetics.ui.Canvas;
import net.minecraft.client.Minecraft;
//? if >=1.21.11 {
import net.minecraft.resources.Identifier;
//?} else {
/*import net.minecraft.resources.ResourceLocation;
*///?}

/**
 * Your face from your skin (face and hat layer), drawn pixel-sharp in a design square: the menus' "you". Without a
 * player (the title screen) a plain head in the theme's glass.
 */
public final class PlayerFace {
    private PlayerFace() {
    }

    /** Draws the face in the {@code size} square at ({@code x}, {@code y}); {@code dim} 0..1 darkens it (the eclipse). */
    public static void draw(Gfx g, float x, float y, float size, float dim) {
        var skin = skin();
        if (skin == null) {
            g.round(x, y, size, size, size * 0.14f, 0xFF8A6A4A);
            g.rect(x + size * 0.125f, y + size * 0.5f, size * 0.25f, size * 0.125f, 0xFF2A3A6B);
            g.rect(x + size * 0.625f, y + size * 0.5f, size * 0.25f, size * 0.125f, 0xFF2A3A6B);
        } else {
            Canvas c = g.game();
            int s = Math.round(size * g.d.u);
            int sx = Math.round(g.d.sx(x + size / 2f)) - s / 2, sy = Math.round(g.d.sy(y + size / 2f)) - s / 2;
            c.blitRegion(skin, sx, sy, s, s, 8f, 8f, 8, 8, 64, 64);
            int hat = Math.round(s * 9f / 8f);
            c.blitRegion(skin, sx - (hat - s) / 2, sy - (hat - s) / 2, hat, hat, 40f, 8f, 8, 8, 64, 64);
            g.layer();
        }
        if (dim > 0.01f) {
            g.rect(x - size * 0.07f, y - size * 0.07f, size * 1.14f, size * 1.14f, io.github.georgwav.cosmetics.sol.util.Colors.argb(
                    Math.round(Math.min(1f, dim) * 255f), 4, 5, 10));
        }
    }

    /**
     * The face at exactly {@code k} screen pixels per skin pixel (8k square), its top left at screen (sx, sy): pixel
     * art never resampled. Without a player, a plain head.
     */
    public static void drawPx(Gfx g, float sx, float sy, int k) {
        var skin = skin();
        int s = 8 * k;
        int x = Math.round(sx), y = Math.round(sy);
        if (skin == null) {
            g.rectPx(x, y, x + s, y + s, 0xFF8A6A4A);
            g.rectPx(x, y, x + s, y + 2 * k, 0xFF3B2716);
            g.rectPx(x + k, y + 4 * k, x + 3 * k, y + 5 * k, 0xFF2A3A6B);
            g.rectPx(x + 5 * k, y + 4 * k, x + 7 * k, y + 5 * k, 0xFF2A3A6B);
            return;
        }
        Canvas c = g.game();
        c.blitRegion(skin, x, y, s, s, 8f, 8f, 8, 8, 64, 64);
        c.blitRegion(skin, x, y, s, s, 40f, 8f, 8, 8, 64, 64);
        g.layer();
    }

    // 1.21.9 moved the skin's texture into its body asset; 1.21.11 renamed ResourceLocation to Identifier.
    //? if >=1.21.11 {
    private static Identifier skin() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? null : mc.player.getSkin().body().texturePath();
    }
    //?} elif >=1.21.9 {
    /*private static ResourceLocation skin() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? null : mc.player.getSkin().body().texturePath();
    }
    *///?} else {
    /*private static ResourceLocation skin() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? null : mc.player.getSkin().texture();
    }
    *///?}
}
