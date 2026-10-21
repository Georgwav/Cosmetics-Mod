package io.github.georgwav.cosmetics.sol.fx;

import io.github.georgwav.cosmetics.client.Modules;
import io.github.georgwav.cosmetics.sol.fx.effects.WorldFx;
import io.github.georgwav.cosmetics.sol.util.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Block Outline's Sol looks, drawn with the effects (depth-tested, a hair toward you so they sit on the block's faces):
 * the stepped glow outline (a one-texel line in the ramp's core tone, a main-tone band behind it, and the glow pass's
 * fainter bands round it), and the End stars fill (each face a window onto a deep sky with twinkling stars in the theme's
 * tones). While either shows, the game's own lines or flat fill stand aside (OutlineHook). Nothing allocated per frame.
 */
public final class BlockHighlight implements FxWorld.Layer, Shapes.DoubleLineConsumer {
    public static final BlockHighlight INSTANCE = new BlockHighlight();
    private static final float T = FxDraw.TEXEL;

    private final int[] ramp = new int[Ramp.TONES];
    private final float[] eye = new float[3];
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
    private WorldOut out;
    private boolean glowPass, faces;
    private float u, v, alpha, now;
    private int seed, face;
    /** For the in-game test: frames the glow outline or the stars fill drew. */
    public static int framesDrawn;

    private BlockHighlight() {
    }

    @Override
    public boolean active() {
        var b = Modules.OUTLINE;
        if (!b.enabled() || !(b.glowLook() || b.starsFill())) {
            return false;
        }
        HitResult hit = Minecraft.getInstance().hitResult;
        return hit instanceof BlockHitResult bh && hit.getType() == HitResult.Type.BLOCK && Minecraft.getInstance().level != null
                && !Minecraft.getInstance().level.getBlockState(bh.getBlockPos()).isAir();
    }

    @Override
    public void draw(WorldOut out, float now) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.hitResult instanceof BlockHitResult bh) || mc.level == null) {
            return;
        }
        pos.set(bh.getBlockPos());
        BlockState state = mc.level.getBlockState(pos);
        VoxelShape shape = mc.getCameraEntity() == null ? state.getShape(mc.level, pos)
                : state.getShape(mc.level, pos, CollisionContext.of(mc.getCameraEntity()));
        if (shape.isEmpty()) {
            return;
        }
        var b = Modules.OUTLINE;
        int argb = b.argb();
        alpha = Colors.a(argb) / 255f;
        Ramp.of(argb | 0xFF000000, ramp);
        this.out = out;
        this.now = now;
        glowPass = out.glowPass();
        out.origin(pos.getX(), pos.getY(), pos.getZ());
        out.eye(eye);
        out.use(FxSprites.atlas());
        float[] uv = FxSprites.SQUARE.uv[FxSprites.ALL];
        u = (uv[0] + uv[2]) / 2f;
        v = (uv[1] + uv[3]) / 2f;
        seed = (int) pos.asLong();
        if (b.starsFill()) {
            faces = true;
            shape.forAllBoxes(this);
        }
        if (b.glowLook()) {
            faces = false;
            out.bias = 0.01f;
            shape.forAllEdges(this);
            out.bias = 0f;
        }
        framesDrawn++;
    }

    @Override
    public void consume(double x1, double y1, double z1, double x2, double y2, double z2) {
        if (faces) {
            box((float) x1, (float) y1, (float) z1, (float) x2, (float) y2, (float) z2);
        } else {
            edge((float) x1, (float) y1, (float) z1, (float) x2, (float) y2, (float) z2);
        }
    }

    /** One edge as camera-facing bands: the core line and the main band behind it, or the glow pass's halo. */
    private void edge(float x1, float y1, float z1, float x2, float y2, float z2) {
        if (glowPass) {
            strip(x1, y1, z1, x2, y2, z2, T * 2.5f, Colors.withAlpha(FxColour.glow(), 0.3f * alpha));
            strip(x1, y1, z1, x2, y2, z2, T * 4f, Colors.withAlpha(FxColour.glow(), 0.12f * alpha));
            return;
        }
        strip(x1, y1, z1, x2, y2, z2, T * 1.5f, Colors.withAlpha(ramp[Ramp.MAIN], 0.55f * alpha));
        strip(x1, y1, z1, x2, y2, z2, T * 0.5f, Colors.withAlpha(ramp[Ramp.CORE], alpha));
    }

    /** A strip along an edge, {@code half} blocks to each side, square to the edge and to the view. */
    private void strip(float x1, float y1, float z1, float x2, float y2, float z2, float half, int argb) {
        float dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
        // To the eye from the edge's middle.
        float ex = eye[0] - (x1 + x2) / 2f, ey = eye[1] - (y1 + y2) / 2f, ez = eye[2] - (z1 + z2) / 2f;
        float sx = dy * ez - dz * ey, sy = dz * ex - dx * ez, sz = dx * ey - dy * ex;
        float l = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (l < 1e-6f) {
            return;
        }
        sx *= half / l;
        sy *= half / l;
        sz *= half / l;
        // Out past the corners by the strip's width, so corners close.
        float el = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        float ox = dx / el * half, oy = dy / el * half, oz = dz / el * half;
        out.vertex(x1 - ox - sx, y1 - oy - sy, z1 - oz - sz, u, v, argb);
        out.vertex(x1 - ox + sx, y1 - oy + sy, z1 - oz + sz, u, v, argb);
        out.vertex(x2 + ox + sx, y2 + oy + sy, z2 + oz + sz, u, v, argb);
        out.vertex(x2 + ox - sx, y2 + oy - sy, z2 + oz - sz, u, v, argb);
    }

    /** One box's faces as windows onto stars (body pass): a deep sky, then twelve twinkling stars on each face. */
    private void box(float x0, float y0, float z0, float x1, float y1, float z1) {
        if (glowPass) {
            return;
        }
        float e = 0.004f;
        x0 -= e;
        y0 -= e;
        z0 -= e;
        x1 += e;
        y1 += e;
        z1 += e;
        int fill = Modules.OUTLINE.fillAlpha();
        int sky = Colors.withAlpha(Colors.scale(ramp[Ramp.DEEP], 0.5f), fill / 255f);
        FxDraw.box(out, x0, y0, z0, x1, y1, z1, sky, sky, sky, sky);
        for (face = 0; face < 6; face++) {
            int s = seed + face * 977;
            for (int i = 0; i < 12; i++) {
                float tw = Ramp.steps(0.55f + 0.45f * (float) Math.sin(now * (1.5f + FxDraw.hash(s, i) * 2f) + i), 3, true);
                int tone = FxDraw.hash(s, i + 50) > 0.8f ? Ramp.CORE : Ramp.MAIN;
                int gx = (int) (FxDraw.hash(s, i + 100) * 16), gy = (int) (FxDraw.hash(s, i + 150) * 16);
                WorldFx.faceCell(out, face, x0, y0, z0, x1, y1, z1, gx, gy, Colors.withAlpha(ramp[tone], tw * Math.min(1f, fill / 255f * 2.5f)), u, v);
            }
        }
    }
}
