package io.github.georgwav.cosmetics.client.modules;

import io.github.georgwav.cosmetics.client.Module;
import io.github.georgwav.cosmetics.client.Modules;
import io.github.georgwav.cosmetics.client.Setting;

/**
 * PvP Tweaks: how much the screen tilts when you're hurt (hurt cam, 0 to 100%), how high the fire overlay reaches
 * while you burn, and the colour entities flash when they're hit.
 */
public final class PvpTweaks extends Module {
    private final Setting.Slider hurtCam = add(new Setting.Slider("hurt_cam", 100, 0, 100, 5, v -> Math.round(v) + "%"));
    private final Setting.Slider fire = add(new Setting.Slider("fire", 100, 0, 100, 5, v -> Math.round(v) + "%"));
    final Setting.Choice hitColor = add(new Setting.Choice("hit_color", "red",
            java.util.List.of("red", "accent", "white", "blue", "green", "purple", "none")));

    public PvpTweaks() {
        super("pvp_tweaks", Category.VIEW, "sword");
    }

    @Override
    protected void onEnable(net.minecraft.client.Minecraft mc) {
        applyHitColor(mc);
    }

    @Override
    protected void onSettingsChanged(net.minecraft.client.Minecraft mc) {
        applyHitColor(mc);
    }

    @Override
    protected void onDisable(net.minecraft.client.Minecraft mc) {
        applyHitColor(mc);
    }

    private int painted = 0xB2FF0000;

    /** The hit flash's colour now (the game's red when off), ARGB with the game's 70% strength. */
    private int hitArgb() {
        if (!enabled()) {
            return 0xB2FF0000;
        }
        return switch (hitColor.get()) {
            case "accent" -> 0xB2000000 | (io.github.georgwav.cosmetics.sol.theme.ThemeState.tokens().accent & 0xFFFFFF);
            case "white" -> 0xB2FFFFFF;
            case "blue" -> 0xB24A7DFF;
            case "green" -> 0xB244E05A;
            case "purple" -> 0xB2A95CFF;
            case "none" -> 0x00FFFFFF;
            default -> 0xB2FF0000;
        };
    }

    /** Rewrites the hit half of the game's overlay texture in the chosen colour (only when it changes). */
    public void applyHitColor(net.minecraft.client.Minecraft mc) {
        if (mc.gameRenderer == null) {
            return;
        }
        int argb = hitArgb();
        if (argb == painted) {
            return;
        }
        painted = argb;
        var texture = ((io.github.georgwav.cosmetics.mixin.OverlayTextureAccessor) mc.gameRenderer.overlayTexture()).cosmeticsmod$texture();
        var pixels = texture.getPixels();
        if (pixels == null) {
            return;
        }
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 16; x++) {
                //? if >=1.21.2 {
                pixels.setPixel(x, y, argb);
                //?} else {
                /*pixels.setPixelRGBA(x, y, (argb & 0xFF00FF00) | ((argb & 0xFF) << 16) | ((argb >> 16) & 0xFF));
                *///?}
            }
        }
        //? if >=1.21.2 {
        texture.upload();
        //?} else {
        /*com.mojang.blaze3d.systems.RenderSystem.activeTexture(33985);
        texture.bind();
        pixels.upload(0, 0, 0, 0, 0, pixels.getWidth(), pixels.getHeight(), false, true, false, false);
        com.mojang.blaze3d.systems.RenderSystem.activeTexture(33984);
        *///?}
    }

    @Override
    protected void tick(net.minecraft.client.Minecraft mc) {
        // Follows the theme's accent as it changes.
        applyHitColor(mc);
    }

    /** How much of the hurt tilt to keep (1 = the game's). */
    public static float hurtCam() {
        PvpTweaks p = Modules.PVP_TWEAKS;
        return p.enabled() ? (float) (p.hurtCam.get() / 100.0) : 1f;
    }

    /** How far down the fire overlay moves (0 = the game's height), in its own units. */
    public static float fireDrop() {
        PvpTweaks p = Modules.PVP_TWEAKS;
        return p.enabled() ? (float) (1.0 - p.fire.get() / 100.0) * 0.45f : 0f;
    }
}
