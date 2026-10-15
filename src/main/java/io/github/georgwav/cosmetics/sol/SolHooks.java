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
import io.github.georgwav.cosmetics.sol.render.SolTiming;

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
            // The vertical field of view from the projection; a matrix that isn't a perspective one (a version
            // passing something else) keeps the last good value instead of making the sky huge or tiny.
            float m11 = FrameInfo.projection.m11();
            float fov = m11 > 0.01f ? (float) Math.toDegrees(2.0 * Math.atan(1.0 / m11)) : -1f;
            if (fov >= 2f && fov <= 170f && Math.abs(FrameInfo.projection.m23() + 1f) < 0.01f) {
                FrameInfo.fovY = fov;
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
            float worldTime = (float) (FrameInfo.sunAngle / (Math.PI * 2.0));
            worldTime -= (float) Math.floor(worldTime);
            // The time the sky shows: the world's, or the theme's own while it holds its showpiece.
            io.github.georgwav.cosmetics.sol.atmosphere.SkyTime.update(worldTime,
                    io.github.georgwav.cosmetics.sol.atmosphere.Atmosphere.intensity());
            FrameInfo.timeOfDay = io.github.georgwav.cosmetics.sol.atmosphere.SkyTime.shown;
            ThemeState.update(FrameInfo.inWorld ? FrameInfo.timeOfDay : Float.NaN);
            QualityGovernor.frame(FrameInfo.frameSeconds);
            SolTiming.frame(FrameInfo.frameSeconds);
            io.github.georgwav.cosmetics.devtools.SolScenario.frame(FrameInfo.frameSeconds,
                    io.github.georgwav.cosmetics.sol.render.LightPass.nanos);
        } catch (RuntimeException | LinkageError e) {
            Guard.fail(Feature.SKY, e);
        }
    }

    /**
     * End of the level render: the particles, then the light pass's world half (sunlight, scattering, shafts, while
     * the depth is still the world's), then the lens flare over it all.
     */
    public static void frameEnd() {
        long t0 = SolTiming.start();
        ParticleField.render();
        io.github.georgwav.cosmetics.sol.atmosphere.GlitterPath.render();
        SolTiming.end(SolTiming.Part.PARTICLES, t0);
        t0 = SolTiming.start();
        io.github.georgwav.cosmetics.sol.render.LightPass.world();
        SolTiming.end(SolTiming.Part.LIGHT, t0);
        io.github.georgwav.cosmetics.client.modules.WorldOverlays.render();
        t0 = SolTiming.start();
        LensFlare.render();
        SolTiming.end(SolTiming.Part.FLARE, t0);
    }

    /** After the hand: the light pass's bloom, grade and vignette over the finished frame. */
    public static void afterHand() {
        long t0 = SolTiming.start();
        io.github.georgwav.cosmetics.sol.render.LightPass.grade();
        SolTiming.end(SolTiming.Part.LIGHT, t0);
    }
}
