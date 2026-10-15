package io.github.georgwav.cosmetics.sol.hud;

import io.github.georgwav.cosmetics.sol.theme.Tokens;
import io.github.georgwav.cosmetics.ui.Canvas;
import io.github.georgwav.cosmetics.ui.PictureTexture;

/** The Sol HUD's pixel icons ({@link HudIconAtlas}) as a texture, baked again when the theme's colours change. */
final class HudIcons {
    private static PictureTexture texture;
    private static int accent, glow;

    private HudIcons() {
    }

    /** The texture for this theme (render thread). */
    static PictureTexture texture(Tokens tk) {
        if (texture == null) {
            texture = new PictureTexture("sol_hud_icons", HudIconAtlas.SIZE);
            accent = ~tk.accent;
        }
        if (tk.accent != accent || tk.glow != glow) {
            accent = tk.accent;
            glow = tk.glow;
            texture.upload(HudIconAtlas.bake(accent, glow));
        }
        return texture;
    }

    /** One icon, 9 x 9 GUI pixels with its top left at (x, y), as the game draws its HUD sprites. */
    static void draw(Canvas c, PictureTexture t, HudIconAtlas.Icon icon, int x, int y) {
        c.blitRegion(t.id, x, y, HudIconAtlas.CELL, HudIconAtlas.CELL, icon.u(), icon.v(), HudIconAtlas.CELL, HudIconAtlas.CELL,
                HudIconAtlas.SIZE, HudIconAtlas.SIZE);
    }
}
