package io.github.georgwav.cosmetics.sol;

import io.github.georgwav.cosmetics.sol.atmosphere.LensFlare;
import io.github.georgwav.cosmetics.sol.atmosphere.ParticleField;
import io.github.georgwav.cosmetics.sol.atmosphere.QualityGovernor;
import io.github.georgwav.cosmetics.sol.render.FrameInfo;
import io.github.georgwav.cosmetics.sol.theme.ThemeState;
import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffects;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;

/**
 * Where Sol's mixins call in. Each entry point is short and guarded: the game's own drawing never sees an exception
 * from Sol.
 */
public final class SolHooks {
    private SolHooks() {
    }

    /** Start of the level render: camera, projection, time; brings the theme's tokens up to date. */
    public static void frameStart(double x, double y, double z, Quaternionf rotation, Matrix4fc projection) {
        try {
            FrameInfo.tick();
            FrameInfo.camX = x;
            FrameInfo.camY = y;
            FrameInfo.camZ = z;
            FrameInfo.setRotation(rotation);
            FrameInfo.projection.set(projection);
            float m11 = FrameInfo.projection.m11();
            if (m11 > 0.01f) {
                FrameInfo.fovY = (float) Math.toDegrees(2.0 * Math.atan(1.0 / m11));
            }
            Minecraft mc = Minecraft.getInstance();
            FrameInfo.width = mc.getWindow().getWidth();
            FrameInfo.height = mc.getWindow().getHeight();
            var level = mc.level;
            FrameInfo.inWorld = level != null;
            if (level != null) {
                FrameInfo.rain = level.getRainLevel(1f);
                FrameInfo.thunder = level.getThunderLevel(1f);
                //? if <1.21.9 {
                /*FrameInfo.sunAngle = level.getSunAngle(1f);
                FrameInfo.moonPhase = level.getMoonPhase();
                FrameInfo.rainBrightness = 1f - FrameInfo.rain;
                *///?}
                FrameInfo.renderDistance = mc.options.getEffectiveRenderDistance() * 16f;
                FrameInfo.day = Math.floorDiv(level.getGameTime(), 24000L);
                var player = mc.player;
                FrameInfo.skyHidden = player != null && (player.hasEffect(MobEffects.BLINDNESS)
                        || player.hasEffect(MobEffects.DARKNESS));
            }
            FrameInfo.timeOfDay = (float) (FrameInfo.sunAngle / (Math.PI * 2.0));
            FrameInfo.timeOfDay -= (float) Math.floor(FrameInfo.timeOfDay);
            ThemeState.update(FrameInfo.inWorld ? FrameInfo.timeOfDay : Float.NaN);
            QualityGovernor.frame(FrameInfo.frameSeconds);
        } catch (RuntimeException | LinkageError e) {
            Guard.fail(Feature.SKY, e);
        }
    }

    /** End of the level render: particles and screen-space light over the finished world. */
    public static void frameEnd() {
        ParticleField.render();
        LensFlare.render();
    }
}
