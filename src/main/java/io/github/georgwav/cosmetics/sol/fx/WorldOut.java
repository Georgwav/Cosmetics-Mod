package io.github.georgwav.cosmetics.sol.fx;

import io.github.georgwav.cosmetics.sol.render.FrameInfo;
import io.github.georgwav.cosmetics.sol.render.SolTexture;
import io.github.georgwav.cosmetics.sol.render.WorldDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;

/**
 * Effects drawn into the world after the level, through {@link WorldDraw}: tested against the world's depth (so walls
 * hide them), nothing written to it; the glow pass added, the body pass alpha blended. Pictures get their GPU copy on
 * first use. Render thread only; nothing allocated per frame.
 */
public final class WorldOut implements FxOut {
    public static final WorldOut INSTANCE = new WorldOut();

    private boolean glow;
    private FxTex current;
    private float ox, oy, oz;
    /**
     * Pulls vertices this far toward the camera (blocks): the same place on screen, a hair nearer, so a copy drawn over
     * a body wins the depth test where the body shows and nowhere else (Target's chams). 0 normally.
     */
    public float bias;
    /** Ground heights asked this frame, by block column and start height. */
    private final long[] groundKeys = new long[256];
    private final float[] groundValues = new float[256];
    private long groundFrame = -1;
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

    private WorldOut() {
    }

    /** Starts a pass. */
    void begin(boolean glowPass) {
        glow = glowPass;
        current = null;
    }

    /** Ends a pass: draws what's batched. */
    void end() {
        if (current != null) {
            WorldDraw.end();
            current = null;
        }
    }

    @Override
    public void use(FxTex tex) {
        if (tex == current) {
            return;
        }
        if (current != null) {
            WorldDraw.end();
        }
        current = tex;
        WorldDraw.begin(glow ? WorldDraw.WORLD_ADD : WorldDraw.WORLD_ALPHA, texture(tex));
    }

    public static SolTexture texture(FxTex tex) {
        SolTexture t = tex.gpu instanceof SolTexture s && !s.released() ? s : null;
        if (t == null || t.width != tex.width || t.height != tex.height) {
            if (t != null) {
                t.release();
            }
            t = SolTexture.of("fx/" + tex.name, tex.width, tex.height, false, tex.argb);
            tex.gpu = t;
            tex.gpuVersion = tex.version;
        } else if (tex.gpuVersion != tex.version) {
            t.upload(tex.argb);
            tex.gpuVersion = tex.version;
        }
        return t;
    }

    static void release(FxTex tex) {
        if (tex.gpu instanceof SolTexture s) {
            s.release();
        }
        tex.gpu = null;
        tex.gpuVersion = -1;
    }

    @Override
    public void origin(double x, double y, double z) {
        ox = (float) (x - FrameInfo.camX);
        oy = (float) (y - FrameInfo.camY);
        oz = (float) (z - FrameInfo.camZ);
    }

    @Override
    public void vertex(float x, float y, float z, float u, float v, int argb) {
        Matrix4f m = FrameInfo.view;
        float wx = ox + x, wy = oy + y, wz = oz + z;
        if (bias > 0f) {
            float l = (float) Math.sqrt(wx * wx + wy * wy + wz * wz);
            if (l > bias * 2f) {
                float k = (l - bias) / l;
                wx *= k;
                wy *= k;
                wz *= k;
            }
        }
        WorldDraw.vertex(m.m00() * wx + m.m10() * wy + m.m20() * wz, m.m01() * wx + m.m11() * wy + m.m21() * wz,
                m.m02() * wx + m.m12() * wy + m.m22() * wz, u, v, argb);
    }

    @Override
    public boolean glowPass() {
        return glow;
    }

    @Override
    public void eye(float[] out) {
        // The origin is kept relative to the camera.
        out[0] = -ox;
        out[1] = -oy;
        out[2] = -oz;
    }

    @Override
    public void right(float[] out) {
        // The view's first row is the camera's right in world space.
        Matrix4f m = FrameInfo.view;
        out[0] = m.m00();
        out[1] = m.m10();
        out[2] = m.m20();
    }

    @Override
    public void up(float[] out) {
        Matrix4f m = FrameInfo.view;
        out[0] = m.m01();
        out[1] = m.m11();
        out[2] = m.m21();
    }

    @Override
    public double camX() {
        return FrameInfo.camX;
    }

    @Override
    public double camY() {
        return FrameInfo.camY;
    }

    @Override
    public double camZ() {
        return FrameInfo.camZ;
    }

    @Override
    public float ground(double x, double z, double fromY) {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            return (float) fromY;
        }
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z), by = (int) Math.floor(fromY + 0.5);
        if (groundFrame != FrameInfo.frame) {
            groundFrame = FrameInfo.frame;
            java.util.Arrays.fill(groundKeys, Long.MIN_VALUE);
        }
        long key = ((long) bx & 0x3FFFFFF) << 38 | ((long) bz & 0x3FFFFFF) << 12 | (by & 0xFFF);
        int slot = (int) ((key * 0x9E3779B97F4A7C15L) >>> 56);
        if (groundKeys[slot] == key) {
            return groundValues[slot];
        }
        float found = (float) fromY;
        // Down from just above the start, at most eight blocks: the top of the first block that stops you.
        for (int y = by; y >= by - 8; y--) {
            cursor.set(bx, y, bz);
            BlockState state = level.getBlockState(cursor);
            if (state.isAir()) {
                continue;
            }
            VoxelShape shape = state.getCollisionShape(level, cursor);
            if (!shape.isEmpty()) {
                found = y + (float) shape.max(net.minecraft.core.Direction.Axis.Y);
                break;
            }
        }
        groundKeys[slot] = key;
        groundValues[slot] = found;
        return found;
    }
}
