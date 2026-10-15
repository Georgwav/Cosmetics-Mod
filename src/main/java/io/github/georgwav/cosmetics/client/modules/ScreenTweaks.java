package io.github.georgwav.cosmetics.client.modules;

import io.github.georgwav.cosmetics.client.Module;
import io.github.georgwav.cosmetics.client.Modules;
import io.github.georgwav.cosmetics.client.Setting;

/**
 * Screen: the overlays that cover the view, each one you can switch off: the pumpkin's mask, the powder snow frost,
 * the dark vignette at the screen's edges, the nether portal's swirl and nausea's wobble.
 */
public final class ScreenTweaks extends Module {
    private final Setting.Toggle pumpkin = add(new Setting.Toggle("pumpkin", false));
    private final Setting.Toggle powderSnow = add(new Setting.Toggle("powder_snow", false));
    private final Setting.Toggle vignette = add(new Setting.Toggle("vignette", false));
    private final Setting.Toggle portal = add(new Setting.Toggle("portal", true));
    private final Setting.Toggle nausea = add(new Setting.Toggle("nausea", true));

    public ScreenTweaks() {
        super("screen", Category.VIEW, "screen");
    }

    private static boolean hides(Setting.Toggle shown) {
        return Modules.SCREEN.enabled() && !shown.get();
    }

    /** Whether a texture overlay (the pumpkin's, powder snow's) is hidden, by its texture's path. */
    public static boolean hidesTexture(String path) {
        ScreenTweaks s = Modules.SCREEN;
        return path.contains("pumpkinblur") ? hides(s.pumpkin) : path.contains("powder_snow") && hides(s.powderSnow);
    }

    public static boolean hidesVignette() {
        return hides(Modules.SCREEN.vignette);
    }

    public static boolean hidesPortal() {
        return hides(Modules.SCREEN.portal);
    }

    public static boolean hidesNausea() {
        return hides(Modules.SCREEN.nausea);
    }
}
