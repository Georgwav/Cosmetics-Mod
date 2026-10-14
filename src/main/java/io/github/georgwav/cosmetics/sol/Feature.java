package io.github.georgwav.cosmetics.sol;

/** Sol's features that run inside the game's own code, each behind {@link Guard}. */
public enum Feature {
    SKY("sky"),
    SUN("sun"),
    FLARE("flare"),
    PARTICLES("particles"),
    FOG("fog"),
    LIGHT("light"),
    CLOUDS("clouds"),
    SOL_UI("sol_ui"),
    HUD("hud"),
    CHAT("chat"),
    GLINT("glint"),
    OUTLINE("outline"),
    MUSIC("music"),
    QUICK_MENU("quick_menu"),
    MENU("menu"),
    TITLE("title"),
    SCREENS("screens"),
    NOTIFICATIONS("notifications"),
    MODULES("modules");

    public final String key;

    Feature(String key) {
        this.key = key;
    }
}
