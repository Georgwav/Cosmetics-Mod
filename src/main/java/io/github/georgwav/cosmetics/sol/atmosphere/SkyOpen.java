package io.github.georgwav.cosmetics.sol.atmosphere;

import io.github.georgwav.cosmetics.sol.render.FrameInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;

/**
 * How open to the sky the camera is: 1 outdoors, fading indoors and to 0 underground, from the game's sky light
 * where the camera is (it spreads in from the open sky and falls off a level a block), smoothed over half a second.
 * The sun's light on the screen (the light pass's sunlight on blocks, rims, scattering and shafts; the lens flare
 * and glare) is scaled by it, so none of it reaches into a cave the sun can't.
 */
public final class SkyOpen {
    private static float value = 1f;

    private SkyOpen() {
    }

    /** 0 (no sky reaches here) to 1 (in the open). */
    public static float get() {
        return value;
    }

    /** Once a frame, before the sky and the light pass. */
    public static void frame(float seconds) {
        var level = Minecraft.getInstance().level;
        if (level == null || !FrameInfo.inWorld) {
            value = 1f;
            return;
        }
        int sky = level.getBrightness(LightLayer.SKY, BlockPos.containing(FrameInfo.camX, FrameInfo.camY, FrameInfo.camZ));
        value += (target(sky) - value) * Math.min(1f, seconds * 4f);
    }

    /** Sky light 12 and up is open; 2 and below shut; smooth in between. */
    static float target(int skyLight) {
        float t = Math.max(0f, Math.min(1f, (skyLight - 2f) / 10f));
        return t * t * (3f - 2f * t);
    }
}
