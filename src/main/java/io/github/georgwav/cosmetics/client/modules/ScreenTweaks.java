package io.github.georgwav.cosmetics.client.modules;

import io.github.georgwav.cosmetics.client.Module;
import io.github.georgwav.cosmetics.client.Modules;
import io.github.georgwav.cosmetics.client.Setting;

/**
 * Screen: the overlays that cover the view, each one you can switch off: the pumpkin's mask, the powder snow frost,
 * the dark vignette at the screen's edges, the nether portal's swirl, nausea's wobble, the underwater overlay and the
 * darkening (and blur) behind inventories; and motion blur (not on 1.21.2 to 1.21.5, whose post effects can't keep the last frame).
 */
public final class ScreenTweaks extends Module {
    private final Setting.Toggle pumpkin = add(new Setting.Toggle("pumpkin", false));
    private final Setting.Toggle powderSnow = add(new Setting.Toggle("powder_snow", false));
    private final Setting.Toggle vignette = add(new Setting.Toggle("vignette", false));
    private final Setting.Toggle portal = add(new Setting.Toggle("portal", true));
    private final Setting.Toggle nausea = add(new Setting.Toggle("nausea", true));
    private final Setting.Toggle water = add(new Setting.Toggle("water", true));
    private final Setting.Toggle inventoryDim = add(new Setting.Toggle("inventory_dim", false));
    //? if <1.21.2 || >=1.21.6 {
    /** Motion blur: how much of a frame is left after 1/60 s (0 = none). */
    private final Setting.Slider motionBlur = add(new Setting.Slider("motion_blur", 0, 0, 90, 5,
            v -> v <= 0 ? net.minecraft.network.chat.Component.translatable("options.off").getString() : Math.round(v) + "%"));
    //?}

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

    /** Motion blur's amount (0 to 0.9), 0 when it's off or this version can't blur. */
    public static float motionBlur() {
        //? if <1.21.2 || >=1.21.6 {
        ScreenTweaks s = Modules.SCREEN;
        return s.enabled() ? (float) (s.motionBlur.get() / 100.0) : 0f;
        //?} else {
        /*return 0f;
        *///?}
    }

    public static boolean hidesVignette() {
        return hides(Modules.SCREEN.vignette);
    }

    public static boolean hidesPortal() {
        return hides(Modules.SCREEN.portal);
    }

    public static boolean hidesWater() {
        return hides(io.github.georgwav.cosmetics.client.Modules.SCREEN.water);
    }

    /** Darkenings left out behind inventories (for the self-test). */
    public static int undimmed;

    /** Whether inventories (and other container screens) leave the world behind them undarkened and sharp. */
    public static boolean hidesInventoryDim() {
        boolean hides = hides(Modules.SCREEN.inventoryDim);
        if (hides) {
            undimmed++;
        }
        return hides;
    }

    public static boolean hidesNausea() {
        return hides(Modules.SCREEN.nausea);
    }
}
