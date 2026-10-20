package io.github.georgwav.cosmetics.client.modules;

import io.github.georgwav.cosmetics.client.Module;
import io.github.georgwav.cosmetics.client.Modules;
import io.github.georgwav.cosmetics.client.Setting;

/**
 * PvP Tweaks: how much the screen tilts when you're hurt (hurt cam, 0 to 100%), how high the fire overlay reaches
 * while you burn, and the flash entities give when they're hit: its colour and strength, or none.
 */
public final class PvpTweaks extends Module {
    private final Setting.Slider hurtCam = add(new Setting.Slider("hurt_cam", 100, 0, 100, 5, v -> Math.round(v) + "%").group("camera"));
    private final Setting.Slider fire = add(new Setting.Slider("fire", 100, 0, 100, 5, v -> Math.round(v) + "%").group("camera"));
    /** Whether entities flash at all when hit. */
    final Setting.Toggle hitFlash = add(new Setting.Toggle("hit_flash", true).group("hit_flash"));
    final Setting.Color hitColor = add(new Setting.Color("hit_color", Setting.Color.THEME).was(0xFFFF0000).themed().names(java.util.Map.of(
            "red", 0xFFFF0000, "accent", Setting.Color.THEME, "white", 0xFFFFFFFF, "blue", 0xFF4A7DFF, "green", 0xFF44E05A,
            "purple", 0xFFA95CFF)).group("hit_flash"));
    /** How strongly the flash tints them (the game's is 70%). */
    private final Setting.Slider hitStrength = add(new Setting.Slider("hit_strength", 70, 10, 100, 5, v -> Math.round(v) + "%")
            .group("hit_flash"));

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

    /** The hit flash's colour now (the game's red when off), ARGB with its strength. */
    private int hitArgb() {
        if (!enabled()) {
            return 0xB2FF0000;
        }
        if (!hitFlash.get()) {
            return 0x00FFFFFF;
        }
        int alpha = Math.round((float) (hitStrength.get() / 100.0) * 255f);
        return alpha << 24 | (hitColor.argb() & 0xFFFFFF);
    }

    @Override
    protected void migrate(com.google.gson.JsonObject settings) {
        // Older versions had "none" among the colours.
        if (settings.get("hit_color") instanceof com.google.gson.JsonPrimitive p && "none".equals(p.getAsString())) {
            settings.addProperty("hit_flash", false);
            settings.remove("hit_color");
        }
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
