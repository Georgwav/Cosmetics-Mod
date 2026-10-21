package io.github.georgwav.cosmetics.sol.fx;

import io.github.georgwav.cosmetics.sol.render.FrameInfo;
import io.github.georgwav.cosmetics.sol.render.SolTexture;
import io.github.georgwav.cosmetics.sol.util.Colors;
import io.github.georgwav.cosmetics.ui.SkinPixels;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

/**
 * The dummy effects play on (C.2, C.3): Try it, which plays a module's effects on a copy of you three blocks in front
 * of you in the world, and the settings page's live scene, the same effects on the same dummy under the preset's sky,
 * replayed every two seconds as they're set. The scene is drawn on the CPU ({@link RasterOut}).
 */
public final class FxPreview {
    /** Where the effects play: the dummy's feet, the way it faces, its body, and whether it's in the world. */
    public static final class Dummy {
        public double x, y, z;
        public float yaw;
        public PoseSnapshot body;
        public boolean world;

        /** Its chest, where hits land. */
        public double chestY() {
            return y + body.height * 0.62;
        }
    }

    /** The dummy standing in the world while its effects play (hidden once a kill takes it). */
    static final FxKind DUMMY = new FxKind("dummy", 2) {
        @Override
        public void draw(Fx fx, float age, float now, FxOut out) {
            if (fx.copy == null || out.glowPass() || (fx.a > 0f && age >= fx.a)) {
                return;
            }
            FxDraw.origin(out, fx);
            float in = Ramp.steps(Math.min(1f, age / 0.2f), 3, true);
            float left = Ramp.steps(Math.min(1f, (fx.end - now) / 0.25f), 3, true);
            fx.copy.draw(out, 0f, 0f, 0f, 1f, 0f, null, Colors.withAlpha(0xFFFFFFFF, Math.min(in, left)), PoseSnapshot.WHOLE, 0f);
        }
    };

    private static FxModule pendingModule;
    private static Dummy pendingDummy;
    private static float pendingAt;

    private FxPreview() {
    }

    /** A copy of you standing, facing {@code yaw}. */
    static PoseSnapshot you(float yaw) {
        PoseSnapshot.Pose p = new PoseSnapshot.Pose();
        p.bodyYaw = yaw;
        p.headYaw = yaw;
        PoseSnapshot s = new PoseSnapshot();
        s.capture(SkinPixels.current(), p);
        return s;
    }

    /**
     * A dummy standing at (x, y, z) facing {@code yaw}, shown for {@code seconds} unless the module's effect takes it
     * away (the scenario's staging, and Try it).
     */
    public static Dummy stage(FxModule m, double x, double y, double z, float yaw, float seconds) {
        Dummy d = new Dummy();
        d.x = x;
        d.y = y;
        d.z = z;
        d.yaw = yaw;
        d.body = you(yaw);
        d.world = true;
        if (!m.hidesDummy()) {
            Fx dummy = FxWorld.spawn(DUMMY, x, y, z, seconds);
            dummy.copy = d.body;
            dummy.snap = false;
        }
        return d;
    }

    /** Try it in the world: a copy of you three blocks ahead, facing you, and the module's effects on it. */
    public static boolean tryInWorld(FxModule m) {
        Minecraft mc = Minecraft.getInstance();
        Player you = mc.player;
        if (you == null || mc.level == null) {
            return false;
        }
        float yaw = you.getYRot();
        double a = Math.toRadians(yaw);
        double x = you.getX() - Math.sin(a) * 3.0, z = you.getZ() + Math.cos(a) * 3.0;
        Dummy d = new Dummy();
        d.x = x;
        d.z = z;
        d.y = WorldOut.INSTANCE.ground(x, z, you.getY() + 1.0);
        d.yaw = yaw + 180f;
        d.body = you(d.yaw);
        d.world = true;
        Fx dummy = FxWorld.spawn(DUMMY, d.x, d.y, d.z, 3.2f);
        dummy.copy = d.body;
        dummy.snap = false;
        // The effects start once you've seen the dummy for a moment.
        pendingModule = m;
        pendingDummy = d;
        pendingAt = FrameInfo.time + 0.45f;
        if (m.hidesDummy()) {
            dummy.a = 0.45f;
        }
        return true;
    }

    /** Starts a waiting Try it (FxWorld.render). */
    static void tickWorld() {
        if (pendingModule != null && FrameInfo.time >= pendingAt) {
            FxModule m = pendingModule;
            Dummy d = pendingDummy;
            pendingModule = null;
            pendingDummy = null;
            m.tryIt(FxWorld.POOL, d);
        }
    }

    // ------------------------------------------------------------------ the settings page's scene

    /** A module's live scene: the dummy on a patch of grass, its effects replaying every two seconds. */
    public static final class Scene {
        private static final float CYCLE = 2.4f, START = 0.4f;
        private final FxModule module;
        private final FxPool pool = new FxPool(64, 48);
        private final RasterOut raster;
        private final Dummy dummy = new Dummy();
        private SolTexture texture;
        private float clock, lastCycle = -1f, hideFrom = -1f;
        private long lastPaint;
        private boolean played;
        private float playsFor;
        /** For the checks: frames painted with effects in them. */
        public int frames;

        public Scene(FxModule module, int width, int height) {
            this.module = module;
            raster = new RasterOut(width, height);
            dummy.body = you(0f);
        }

        /** Starts the cycle again now (Try it on the page). */
        public void restart() {
            clock = 0f;
            lastCycle = -1f;
        }

        /** Advances and paints at most 30 times a second; returns the picture. */
        public SolTexture paint(float dt) {
            long now = System.nanoTime();
            clock += Math.min(0.1f, dt);
            if (texture == null || now - lastPaint > 33_000_000L) {
                lastPaint = now;
                draw();
                if (texture == null || texture.released()) {
                    texture = SolTexture.of("fx_scene", raster.width, raster.height, false, raster.pixels);
                } else {
                    texture.upload(raster.pixels);
                }
            }
            return texture;
        }

        public void release() {
            if (texture != null) {
                texture.release();
                texture = null;
            }
            pool.clear();
        }

        private void draw() {
            float t = clock % CYCLE;
            float cycle = (float) Math.floor(clock / CYCLE);
            if (cycle != lastCycle) {
                lastCycle = cycle;
                pool.clear();
                played = false;
                hideFrom = -1f;
            }
            pool.setNow(clock);
            if (!played && t >= START) {
                played = true;
                playsFor = module.tryIt(pool, dummy);
                hideFrom = module.hidesDummy() ? clock : -1f;
            }
            raster.clear();
            raster.camera(0f, 1.6f, 0f, 22f, 14f, raster.height / 4.2f);
            // The ground: a patch of grass in two greens, a block a tile.
            raster.begin(false, true);
            FxTex atlas = FxSprites.atlas();
            raster.use(atlas);
            raster.origin(0, 0, 0);
            float[] uv = FxSprites.SQUARE.uv[FxSprites.ALL];
            for (int gx = -4; gx < 4; gx++) {
                for (int gz = -4; gz < 4; gz++) {
                    int c = ((gx + gz) & 1) == 0 ? 0xFF5E9B4A : 0xFF588F45;
                    FxDraw.flat(raster, gx + 0.5f, 0f, gz + 0.5f, 0.5f, 0.5f, uv, c);
                }
            }
            if (hideFrom < 0f) {
                raster.origin(dummy.x, dummy.y, dummy.z);
                dummy.body.draw(raster, 0f, 0f, 0f, 1f, 0f, null, 0xFFFFFFFF, PoseSnapshot.WHOLE, 0f);
            }
            pool.draw(raster, () -> raster.begin(true, false), () -> raster.begin(false, false), () -> { });
            if (pool.any()) {
                frames++;
            }
        }
    }
}
