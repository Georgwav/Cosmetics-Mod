package io.github.georgwav.cosmetics.sol.atmosphere;

import io.github.georgwav.cosmetics.sol.Feature;
import io.github.georgwav.cosmetics.sol.Guard;
import io.github.georgwav.cosmetics.sol.render.FrameInfo;
import io.github.georgwav.cosmetics.sol.theme.ThemeState;
import io.github.georgwav.cosmetics.sol.theme.Tokens;
import io.github.georgwav.cosmetics.sol.util.Colors;
import net.minecraft.world.level.material.FogType;

/**
 * The theme's fog: its colour (which is also what the far terrain melts into, and the sky below the horizon) and
 * its distances, from the fog density (thicker fog starts and ends closer). Only open-air fog changes: under water,
 * in lava or powder snow, or blinded, the game's own fog stays. Weather and other fog that is already closer win.
 */
public final class FogControl {
    private FogControl() {
    }

    /** Whether Sol sets the fog now (the Sol sky was drawn in the last frames, so this is an open-air sky). */
    public static boolean active() {
        Atmosphere at = Atmosphere.INSTANCE;
        return at != null && at.fog.get() && Atmosphere.active() && !FrameInfo.skyHidden
                && FrameInfo.frame - SkyPass.lastDrawFrame <= 3 && Guard.on(Feature.FOG)
                && io.github.georgwav.cosmetics.sol.render.Mc.camera().getFluidInCamera() == FogType.NONE;
    }

    /** Whether the Sol sky is the open-air sky now (drawn in the last frames). */
    public static boolean activeSky() {
        return io.github.georgwav.cosmetics.sol.render.FrameInfo.frame - SkyPass.lastDrawFrame <= 3;
    }

    /** The fog colour (ARGB): the theme's, greyed by rain like the dome. */
    public static int color() {
        Tokens tk = ThemeState.tokens();
        int c = tk.fogColor;
        float rain = FrameInfo.rain;
        if (rain > 0f) {
            int grey = Colors.scale(Colors.mix(c, 0xFF8A9099, 0.5f), 0.85f);
            c = Colors.mix(c, grey, rain * 0.6f);
        }
        return c;
    }

    /**
     * Fog start in blocks, never further than the game's own: the theme's fixed distance when it has one (Ember's
     * world is a silhouette about 20 blocks out at any render distance), else a part of the render distance.
     */
    public static float start(float vanilla) {
        Tokens tk = ThemeState.tokens();
        float ours = tk.fogFar > 0f ? tk.fogNear : tk.fogStart * FrameInfo.renderDistance;
        return Math.min(vanilla, ours);
    }

    /** Fog end in blocks, never further than the game's own. */
    public static float end(float vanilla) {
        Tokens tk = ThemeState.tokens();
        float ours = tk.fogFar > 0f ? tk.fogFar : tk.fogEnd * FrameInfo.renderDistance;
        return Math.max(1f, Math.min(vanilla, ours));
    }

    public static float r() {
        return Colors.r(color()) / 255f;
    }

    public static float g() {
        return Colors.g(color()) / 255f;
    }

    public static float b() {
        return Colors.b(color()) / 255f;
    }
}
