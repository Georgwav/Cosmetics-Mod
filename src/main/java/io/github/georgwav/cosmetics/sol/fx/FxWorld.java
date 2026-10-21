package io.github.georgwav.cosmetics.sol.fx;

import io.github.georgwav.cosmetics.sol.Feature;
import io.github.georgwav.cosmetics.sol.Guard;
import io.github.georgwav.cosmetics.sol.atmosphere.QualityGovernor;
import io.github.georgwav.cosmetics.sol.render.FrameInfo;
import io.github.georgwav.cosmetics.sol.render.SolTiming;
import io.github.georgwav.cosmetics.sol.ui.kit.SolScreen;

/**
 * The world's effects (C.3): one pool on the frame's clock, drawn after the level through {@link WorldOut} (so walls
 * and terrain hide them and shader packs keep them), capped by the quality the frame time allows. With calm motion an
 * effect keeps its look but plays only its key moment. Render thread only.
 */
public final class FxWorld {
    public static final FxPool POOL = new FxPool(192, 96);
    /** For the checks: the most effects alive at once, and frames drawn with any. */
    public static int peak;
    /** The scenario's frozen clock (seconds on the frame clock), or NaN to run with the frames. */
    public static float hold = Float.NaN;

    private FxWorld() {
    }

    /** Whether calm motion is on: no flashes or shakes, effects shortened to their key moment. */
    public static boolean calm() {
        return SolScreen.calm();
    }

    /** The effects' clock: the frame clock, or the scenario's frozen moment. */
    public static float now() {
        return Float.isNaN(hold) ? FrameInfo.time : hold;
    }

    /** Starts an effect in the world (see {@link FxPool#spawn}). */
    public static Fx spawn(FxKind kind, double x, double y, double z, float life) {
        POOL.setNow(now());
        boolean calm = calm();
        if (calm) {
            life = Math.min(life, 0.6f);
        }
        Fx f = POOL.spawn(kind, x, y, z, life, QualityGovernor.level());
        f.calm = calm;
        return f;
    }

    /** After the level each frame (SolHooks.frameEnd). */
    public static void render() {
        if (!FrameInfo.inWorld) {
            if (POOL.any()) {
                POOL.clear();
            }
            return;
        }
        POOL.setNow(now());
        FxPreview.tickWorld();
        if (!Guard.on(Feature.FX)) {
            return;
        }
        long t0 = SolTiming.start();
        if (POOL.any()) {
            Guard.run(Feature.FX, () -> {
                WorldOut out = WorldOut.INSTANCE;
                POOL.draw(out, () -> out.begin(true), () -> out.begin(false), out::end);
                peak = Math.max(peak, POOL.count((FxKind) null));
            });
        }
        // Flashes over the view, on top of everything.
        Guard.run(Feature.FX, FxScreen::render);
        SolTiming.end(SolTiming.Part.FX, t0);
    }
}
