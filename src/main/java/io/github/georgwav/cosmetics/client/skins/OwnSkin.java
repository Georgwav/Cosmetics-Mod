package io.github.georgwav.cosmetics.client.skins;

import io.github.georgwav.cosmetics.ui.PictureTexture;
//? if >=1.21.9 {
import net.minecraft.core.ClientAsset;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
//?} else {
/*import net.minecraft.client.resources.PlayerSkin;
*///?}

/**
 * The skin you just applied, shown on yourself right away: Mojang's servers have it, but the game only fetches your
 * skin when you join, so until then your own player wears this texture (with your cape as it is).
 */
public final class OwnSkin {
    private static PictureTexture texture;
    private static boolean slim;
    private static boolean active;

    private OwnSkin() {
    }

    /** Wears these 64 x 64 ARGB pixels from now on. Render thread. */
    public static void set(int[] argb, boolean slimArms) {
        if (texture == null) {
            texture = new PictureTexture("own_skin", 64);
        }
        int[] pixels = argb.clone();
        // As the game does with downloaded skins: the inner layer is solid (only the outer layer may be see-through).
        opaque(pixels, 0, 0, 32, 16);
        opaque(pixels, 0, 16, 64, 32);
        opaque(pixels, 16, 48, 48, 64);
        texture.upload(pixels);
        slim = slimArms;
        active = true;
    }

    private static void opaque(int[] argb, int x0, int y0, int x1, int y1) {
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                argb[y * 64 + x] |= 0xFF000000;
            }
        }
    }

    public static boolean active() {
        return active;
    }

    /** Back to the skin the game has (the in-game test). */
    public static void clear() {
        active = false;
    }

    /** Whether the player's skin is the one applied here: its texture and arm width (the in-game test). */
    public static boolean isWorn(PlayerSkin skin) {
        //? if >=1.21.9 {
        return texture != null && skin.body().texturePath().equals(texture.id)
                && skin.model() == (slim ? PlayerModelType.SLIM : PlayerModelType.WIDE);
        //?} else {
        /*return texture != null && skin.texture().equals(texture.id)
                && skin.model() == (slim ? PlayerSkin.Model.SLIM : PlayerSkin.Model.WIDE);
        *///?}
    }

    /** Your skin as the game has it, with the body texture and arm width swapped for the one you applied. */
    public static PlayerSkin apply(PlayerSkin skin) {
        if (!active || skin == null) {
            return skin;
        }
        //? if >=1.21.9 {
        return new PlayerSkin(new ClientAsset.ResourceTexture(texture.id, texture.id), skin.cape(), skin.elytra(),
                slim ? PlayerModelType.SLIM : PlayerModelType.WIDE, skin.secure());
        //?} else {
        /*return new PlayerSkin(texture.id, null, skin.capeTexture(), skin.elytraTexture(),
                slim ? PlayerSkin.Model.SLIM : PlayerSkin.Model.WIDE, skin.secure());
        *///?}
    }
}
