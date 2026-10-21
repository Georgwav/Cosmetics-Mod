package io.github.georgwav.cosmetics.sol.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.georgwav.cosmetics.render.CosmeticRenderer;
import io.github.georgwav.cosmetics.render.MeshEmitter;
import io.github.georgwav.cosmetics.render.SoftRaster;
import io.github.georgwav.cosmetics.ui.SkinPixels;
import io.github.georgwav.cosmetics.ui.TilePictures;
import io.github.georgwav.cosmetics.sol.util.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.joml.Quaternionf;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * A copy of a player's or mob's pose and skin at one instant (C.3): the game's own model geometry, posed as it was,
 * turned the way the body faced, in blocks from its feet, with a copy of its skin's pixels. Effects draw it tinted and
 * see-through, shrink and turn it, and dissolve it in texel rows (from the top, from the feet, or by noise): each face
 * is kept whole for plain drawing and cut into one-texel rows for dissolving. Players use the game's player model in
 * their skin; mobs their own renderer's model and texture; anything else a box its size.
 *
 * <p>{@link #HISTORY} keeps your own last three seconds of poses at 20 a second, for replays (ghost trail, hologram).
 * Render thread only.
 */
public final class PoseSnapshot {
    /** Floats per vertex (x, y, z, u, v) and per quad. */
    static final int VF = 5, QF = VF * 4;
    public static final int WHOLE = 0, FROM_TOP = 1, FROM_FEET = 2, NOISE = 3;

    /**
     * Whole faces, then the faces cut into texel rows, each with its middle height and a noise value; and every face's
     * and row's outward normal and middle (x, y, z), so only the faces turned to the camera are drawn, far ones first.
     */
    float[] quads = new float[QF * 96];
    float[] quadNormal = new float[3 * 96], quadMid = new float[3 * 96];
    int quadCount;
    float[] rows = new float[QF * 512];
    float[] rowHeight = new float[512];
    float[] rowNoise = new float[512];
    float[] rowNormal = new float[3 * 512], rowMid = new float[3 * 512];
    int rowCount;
    /** The faces to draw this frame, far to near, and how far each is (squared). */
    private int[] order = new int[512];
    private float[] far = new float[512];
    private static final float[] EYE = new float[3];
    /** The skin, and its height (blocks): the dissolves run over it. */
    public FxTex skin;
    public float height = 1.8f, width = 0.6f;
    /** Tinted copies of the skin, made when an effect asks (one kept per tint). */
    private FxTex tinted, grey;
    private int tintedFor;
    private float tintedAmount = -1f;

    public PoseSnapshot() {
    }

    // ------------------------------------------------------------------ capturing

    private static final Collector COLLECT = new Collector();
    private static final PoseStack POSE = new PoseStack();
    private static ModelPart wide, slim;
    private static final Map<Object, FxTex> TEXTURES = new HashMap<>();
    private static final FxTex WHITE = new FxTex("white", 1, 1, new int[]{0xFFFFFFFF});

    /** A copy of {@code e} as it is now (between ticks at {@code partialTick}). */
    public static PoseSnapshot of(Entity e, float partialTick) {
        PoseSnapshot s = new PoseSnapshot();
        s.capture(e, partialTick);
        return s;
    }

    /** Copies {@code e} into this snapshot (reused: nothing new is made for a body seen before). */
    public void capture(Entity e, float partialTick) {
        capture(e, partialTick, true);
    }

    /**
     * {@link #capture(Entity, float)}, without the texel rows when {@code rows} is false: a copy drawn whole every frame
     * (Target's chams) needs none of the dissolves.
     */
    public void capture(Entity e, float partialTick, boolean rows) {
        quadCount = 0;
        rowCount = 0;
        height = Math.max(0.2f, e.getBbHeight());
        width = Math.max(0.2f, e.getBbWidth());
        try {
            if (e instanceof Player p) {
                Pose pose = SCRATCH_POSE;
                pose.from(p, partialTick);
                player(SkinPixels.of(p), pose);
            } else if (e instanceof LivingEntity living) {
                mob(living, partialTick);
            }
        } catch (RuntimeException | LinkageError ex) {
            io.github.georgwav.cosmetics.sol.util.LogOnce.warn("fx_copy_" + e.getType(), "[Sol] Couldn't copy a {}: {}", e.getType(),
                    ex.toString());
            quadCount = 0;
        }
        if (quadCount == 0) {
            box(width, height);
        }
        if (rows) {
            cutRows();
        } else {
            mids();
        }
        tintedAmount = -1f;
    }

    private static final Pose SCRATCH_POSE = new Pose();

    /** Every face's middle, for the facing test (cutRows does this as it cuts). */
    private void mids() {
        for (int q = 0; q < quadCount; q++) {
            int o = q * QF;
            for (int k = 0; k < 3; k++) {
                quadMid[q * 3 + k] = (quads[o + k] + quads[o + VF + k] + quads[o + 2 * VF + k] + quads[o + 3 * VF + k]) / 4f;
            }
        }
    }

    /** Copies a player in a pose from {@link #HISTORY} (or any pose) wearing {@code skin}. */
    public void capture(SkinPixels.Skin skinPixels, Pose pose) {
        quadCount = 0;
        rowCount = 0;
        height = pose.crouch ? 1.5f : 1.8f;
        width = 0.6f;
        try {
            player(skinPixels, pose);
        } catch (RuntimeException | LinkageError ex) {
            io.github.georgwav.cosmetics.sol.util.LogOnce.warn("fx_copy_pose", "[Sol] Couldn't copy a pose: {}", ex.toString());
            quadCount = 0;
        }
        if (quadCount == 0) {
            box(width, height);
        }
        cutRows();
        tintedAmount = -1f;
    }

    /** A pose of the player model: where the limbs are, the head's turn, crouching. Angles in degrees. */
    public static final class Pose {
        public double x, y, z;
        public float bodyYaw, headYaw, pitch, walk, walkSpeed;
        public boolean crouch;

        public void from(Player p, float pt) {
            x = net.minecraft.util.Mth.lerp(pt, p.xo, p.getX());
            y = net.minecraft.util.Mth.lerp(pt, p.yo, p.getY());
            z = net.minecraft.util.Mth.lerp(pt, p.zo, p.getZ());
            bodyYaw = net.minecraft.util.Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot);
            headYaw = net.minecraft.util.Mth.rotLerp(pt, p.yHeadRotO, p.yHeadRot);
            pitch = p.getXRot();
            walk = p.walkAnimation.position(pt);
            walkSpeed = Math.min(1f, p.walkAnimation.speed(pt));
            crouch = p.isCrouching();
        }

        public void set(Pose o) {
            x = o.x;
            y = o.y;
            z = o.z;
            bodyYaw = o.bodyYaw;
            headYaw = o.headYaw;
            pitch = o.pitch;
            walk = o.walk;
            walkSpeed = o.walkSpeed;
            crouch = o.crouch;
        }
    }

    private static ModelPart playerModel(boolean slimArms) {
        ModelPart m = slimArms ? slim : wide;
        if (m == null) {
            //? if >=1.21.11 {
            var mesh = net.minecraft.client.model.player.PlayerModel.createMesh(CubeDeformation.NONE, slimArms);
            //?} else {
            /*var mesh = net.minecraft.client.model.PlayerModel.createMesh(CubeDeformation.NONE, slimArms);
            *///?}
            m = LayerDefinition.create(mesh, 64, 64).bakeRoot();
            for (String extra : new String[]{"ear", "cloak"}) {
                if (m.hasChild(extra)) {
                    m.getChild(extra).visible = false;
                }
            }
            if (slimArms) {
                slim = m;
            } else {
                wide = m;
            }
        }
        return m;
    }

    /** The player model posed like vanilla's walk (arms and legs swinging, the head turned), in the skin. */
    private void player(SkinPixels.Skin s, Pose p) {
        ModelPart root = playerModel(s.slim());
        root.getAllParts().forEach(ModelPart::resetPose);
        ModelPart head = root.getChild("head"), body = root.getChild("body");
        ModelPart rightArm = root.getChild("right_arm"), leftArm = root.getChild("left_arm");
        ModelPart rightLeg = root.getChild("right_leg"), leftLeg = root.getChild("left_leg");
        float yaw = net.minecraft.util.Mth.wrapDegrees(p.headYaw - p.bodyYaw);
        head.yRot = (float) Math.toRadians(yaw);
        head.xRot = (float) Math.toRadians(p.pitch);
        float swing = p.walk * 0.6662f;
        rightArm.xRot = (float) Math.cos(swing + Math.PI) * p.walkSpeed;
        leftArm.xRot = (float) Math.cos(swing) * p.walkSpeed;
        rightArm.zRot = 0.06f;
        leftArm.zRot = -0.06f;
        rightLeg.xRot = (float) Math.cos(swing) * 1.4f * p.walkSpeed;
        leftLeg.xRot = (float) Math.cos(swing + Math.PI) * 1.4f * p.walkSpeed;
        if (p.crouch) {
            body.xRot = 0.5f;
            rightArm.xRot += 0.4f;
            leftArm.xRot += 0.4f;
            rightLeg.z += 4f;
            leftLeg.z += 4f;
            rightLeg.y += 0.2f;
            leftLeg.y += 0.2f;
            head.y += 4.2f;
            body.y += 3.2f;
            rightArm.y += 3.2f;
            leftArm.y += 3.2f;
        }
        // 1.21.1 keeps the outer layer beside the parts, not inside them: it follows them by copying their pose.
        String[][] layers = {{"hat", "head"}, {"jacket", "body"}, {"left_sleeve", "left_arm"}, {"right_sleeve", "right_arm"},
                {"left_pants", "left_leg"}, {"right_pants", "right_leg"}};
        for (String[] l : layers) {
            if (root.hasChild(l[0])) {
                ModelPart to = root.getChild(l[0]), from = root.getChild(l[1]);
                to.x = from.x;
                to.y = from.y;
                to.z = from.z;
                to.xRot = from.xRot;
                to.yRot = from.yRot;
                to.zRot = from.zRot;
            }
        }
        skin = texture(s.texture());
        POSE.pushPose();
        turn(p.bodyYaw);
        COLLECT.reset(this);
        root.render(POSE, COLLECT, MeshEmitter.FULL_BRIGHT, 0);
        POSE.popPose();
    }

    /** The model's frame: turned like the body, then the models' upside-down space, the feet at zero. */
    private static void turn(float bodyYaw) {
        CosmeticRenderer.rotate(POSE, new Quaternionf().rotationY((float) Math.toRadians(180f - bodyYaw)));
        POSE.scale(-1f, -1f, 1f);
        POSE.translate(0f, -1.501f, 0f);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void mob(LivingEntity e, float pt) {
        var renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(e);
        if (!(renderer instanceof net.minecraft.client.renderer.entity.LivingEntityRenderer lr)) {
            return;
        }
        float bodyYaw = net.minecraft.util.Mth.rotLerp(pt, e.yBodyRotO, e.yBodyRot);
        POSE.pushPose();
        turn(bodyYaw);
        if (e.isBaby()) {
            POSE.translate(0f, 1.5f * 0.5f, 0f);
            POSE.scale(0.5f, 0.5f, 0.5f);
        }
        COLLECT.reset(this);
        //? if >=1.21.2 {
        net.minecraft.client.renderer.entity.state.EntityRenderState state =
                ((net.minecraft.client.renderer.entity.EntityRenderer) lr).createRenderState(e, pt);
        var model = lr.getModel();
        model.setupAnim(state);
        Object id = lr.getTextureLocation((net.minecraft.client.renderer.entity.state.LivingEntityRenderState) state);
        model.root().render(POSE, COLLECT, MeshEmitter.FULL_BRIGHT, 0);
        //?} else {
        /*var model = lr.getModel();
        float walk = e.walkAnimation.position(pt), speed = Math.min(1f, e.walkAnimation.speed(pt));
        float headYaw = net.minecraft.util.Mth.wrapDegrees(net.minecraft.util.Mth.rotLerp(pt, e.yHeadRotO, e.yHeadRot) - bodyYaw);
        model.prepareMobModel(e, walk, speed, pt);
        model.setupAnim(e, walk, speed, e.tickCount + pt, headYaw, e.getXRot());
        Object id = lr.getTextureLocation(e);
        model.renderToBuffer(POSE, COLLECT, MeshEmitter.FULL_BRIGHT, 0);
        *///?}
        POSE.popPose();
        String key = String.valueOf(id);
        FxTex t = TEXTURES.get(key);
        if (t == null) {
            SoftRaster.Texture px = TilePictures.read(key);
            t = new FxTex("mob_" + TEXTURES.size(), px.width(), px.height(), px.argb());
            if (TEXTURES.size() > 64) {
                for (FxTex old : TEXTURES.values()) {
                    old.release();
                }
                TEXTURES.clear();
            }
            TEXTURES.put(key, t);
        }
        skin = t;
    }

    private static FxTex texture(SoftRaster.Texture px) {
        FxTex t = TEXTURES.get(px);
        if (t == null) {
            t = new FxTex("skin_" + TEXTURES.size(), px.width(), px.height(), px.argb());
            if (TEXTURES.size() > 64) {
                for (FxTex old : TEXTURES.values()) {
                    old.release();
                }
                TEXTURES.clear();
            }
            TEXTURES.put(px, t);
        }
        return t;
    }

    /** A plain box the body's size, for what has no model to copy. */
    private void box(float w, float h) {
        skin = WHITE;
        float x = w / 2f;
        float[][] f = {
                {-x, h, -x, -x, h, x, x, h, x, x, h, -x},
                {-x, 0, -x, x, 0, -x, x, 0, x, -x, 0, x},
                {-x, 0, -x, -x, h, -x, x, h, -x, x, 0, -x},
                {-x, 0, x, x, 0, x, x, h, x, -x, h, x},
                {-x, 0, -x, -x, 0, x, -x, h, x, -x, h, -x},
                {x, 0, -x, x, h, -x, x, h, x, x, 0, x}};
        for (float[] q : f) {
            ensureQuads(quadCount + 1);
            int o = quadCount * QF;
            float mx = 0f, my = 0f, mz = 0f;
            for (int v = 0; v < 4; v++) {
                quads[o + v * VF] = q[v * 3];
                quads[o + v * VF + 1] = q[v * 3 + 1];
                quads[o + v * VF + 2] = q[v * 3 + 2];
                quads[o + v * VF + 3] = v == 0 || v == 3 ? 0f : 1f;
                quads[o + v * VF + 4] = v < 2 ? 1f : 0f;
                mx += q[v * 3] / 4f;
                my += q[v * 3 + 1] / 4f;
                mz += q[v * 3 + 2] / 4f;
            }
            // Outward from the box's middle, along the face's own axis.
            float dx = mx / x, dy = (my - h / 2f) / (h / 2f), dz = mz / x;
            int n = quadCount * 3;
            quadNormal[n] = Math.abs(dx) > 0.99f ? Math.signum(dx) : 0f;
            quadNormal[n + 1] = Math.abs(dy) > 0.99f ? Math.signum(dy) : 0f;
            quadNormal[n + 2] = Math.abs(dz) > 0.99f ? Math.signum(dz) : 0f;
            quadCount++;
        }
    }

    private void ensureQuads(int n) {
        if (n * QF > quads.length) {
            quads = Arrays.copyOf(quads, Math.max(n * QF, quads.length * 2));
            quadNormal = Arrays.copyOf(quadNormal, quads.length / QF * 3);
            quadMid = Arrays.copyOf(quadMid, quads.length / QF * 3);
        }
        ensureOrder(n);
    }

    private void ensureRows(int n) {
        if (n * QF > rows.length) {
            rows = Arrays.copyOf(rows, Math.max(n * QF, rows.length * 2));
            rowHeight = Arrays.copyOf(rowHeight, rows.length / QF);
            rowNoise = Arrays.copyOf(rowNoise, rows.length / QF);
            rowNormal = Arrays.copyOf(rowNormal, rows.length / QF * 3);
            rowMid = Arrays.copyOf(rowMid, rows.length / QF * 3);
        }
        ensureOrder(n);
    }

    private void ensureOrder(int n) {
        if (n > order.length) {
            order = Arrays.copyOf(order, Math.max(n, order.length * 2));
            far = Arrays.copyOf(far, order.length);
        }
    }

    /** Cuts each face into one-texel rows along the way its texture's rows run. */
    private void cutRows() {
        rowCount = 0;
        int th = skin.height;
        for (int q = 0; q < quadCount; q++) {
            int o = q * QF;
            for (int k = 0; k < 3; k++) {
                quadMid[q * 3 + k] = (quads[o + k] + quads[o + VF + k] + quads[o + 2 * VF + k] + quads[o + 3 * VF + k]) / 4f;
            }
            float v0 = quads[o + 4], v1 = quads[o + VF + 4], v3 = quads[o + 3 * VF + 4];
            // Which pair of edges runs along the texture's rows: 0->1 or 0->3.
            boolean along01 = Math.abs(v1 - v0) >= Math.abs(v3 - v0);
            float span = Math.abs(along01 ? v1 - v0 : v3 - v0) * th;
            int n = Math.max(1, Math.min(32, Math.round(span)));
            if (skin == WHITE) {
                n = Math.max(1, Math.round((maxY(o) - minY(o)) * 16f));
            }
            ensureRows(rowCount + n);
            for (int i = 0; i < n; i++) {
                float a = i / (float) n, b = (i + 1) / (float) n;
                int r = rowCount * QF;
                // Corners of the strip at a..b along the cut direction.
                for (int v = 0; v < 4; v++) {
                    float s = along01 ? (v == 0 || v == 3 ? a : b) : (v == 0 || v == 1 ? 0f : 1f);
                    float t = along01 ? (v == 0 || v == 1 ? 0f : 1f) : (v == 0 || v == 3 ? a : b);
                    lerpQuad(o, s, t, r + v * VF);
                }
                float y = (rows[r + 1] + rows[r + VF + 1] + rows[r + 2 * VF + 1] + rows[r + 3 * VF + 1]) / 4f;
                rowHeight[rowCount] = y;
                for (int k = 0; k < 3; k++) {
                    rowNormal[rowCount * 3 + k] = quadNormal[q * 3 + k];
                    rowMid[rowCount * 3 + k] = (rows[r + k] + rows[r + VF + k] + rows[r + 2 * VF + k] + rows[r + 3 * VF + k]) / 4f;
                }
                rowNoise[rowCount] = FxDraw.hash(q * 131 + 7, i);
                rowCount++;
            }
        }
    }

    private float minY(int o) {
        float m = Float.MAX_VALUE;
        for (int v = 0; v < 4; v++) {
            m = Math.min(m, quads[o + v * VF + 1]);
        }
        return m;
    }

    private float maxY(int o) {
        float m = -Float.MAX_VALUE;
        for (int v = 0; v < 4; v++) {
            m = Math.max(m, quads[o + v * VF + 1]);
        }
        return m;
    }

    /**
     * The point at (s, t) of quad {@code o} (s along 0->1, t along 1->2 for the 0->1 cut; s along 1->2 and t along
     * 0->3 otherwise), bilinear over its corners, into rows at {@code out}.
     */
    private void lerpQuad(int o, float s, float t, int out) {
        // Corners 0, 1, 2, 3 at (0,0), (1,0), (1,1), (0,1) of (along, across) for the 0->1 cut; swapped otherwise.
        for (int k = 0; k < VF; k++) {
            float c0 = quads[o + k], c1 = quads[o + VF + k], c2 = quads[o + 2 * VF + k], c3 = quads[o + 3 * VF + k];
            float top = c0 + (c1 - c0) * s, bottom = c3 + (c2 - c3) * s;
            rows[out + k] = top + (bottom - top) * t;
        }
    }

    // ------------------------------------------------------------------ drawing

    /**
     * Draws the copy with its feet at ({@code x}, {@code y}, {@code z}) from the origin, {@code scale} times its size,
     * turned {@code spin} radians more round its middle, in {@code argb} (white and the skin's own colours unless
     * tinted). {@code mode} picks the dissolve and {@code cut} how far it is (0 whole .. 1 gone); body pass only.
     */
    public void draw(FxOut out, float x, float y, float z, float scale, float spin, FxTex tex, int argb, int mode, float cut) {
        if (out.glowPass() || Colors.a(argb) == 0 || cut >= 1f) {
            return;
        }
        draw(out, x, y, z, scale, spin, tex, argb, mode, cut, false);
    }

    /**
     * Draws the copy whole in either pass: its faces turned to the camera, or with {@code back} only the ones turned
     * away (an outline: those of a copy a little bigger, round the body's edges, and hidden by the body elsewhere).
     */
    public void drawFaces(FxOut out, float x, float y, float z, float scale, FxTex tex, int argb, boolean back) {
        if (Colors.a(argb) == 0) {
            return;
        }
        draw(out, x, y, z, scale, 0f, tex, argb, WHOLE, 0f, back);
    }

    private void draw(FxOut out, float x, float y, float z, float scale, float spin, FxTex tex, int argb, int mode, float cut,
                      boolean back) {
        out.use(tex != null ? tex : skin);
        float cs = (float) Math.cos(spin), sn = (float) Math.sin(spin);
        // The camera in the copy's own space (its feet at zero, unturned, unscaled): nothing is written to the depth,
        // so the faces turned away are left out and the rest go far to near.
        out.eye(EYE);
        float ex = EYE[0] - x, ey = EYE[1] - y, ez = EYE[2] - z;
        float s = Math.max(1e-4f, scale);
        float lx = (ex * cs + ez * sn) / s, ly = ey / s, lz = (-ex * sn + ez * cs) / s;
        if (mode == WHOLE || cut <= 0f) {
            int n = 0;
            for (int q = 0; q < quadCount; q++) {
                n = facing(quadNormal, quadMid, q, lx, ly, lz, n, back);
            }
            sort(n);
            for (int i = 0; i < n; i++) {
                emit(out, quads, order[i] * QF, x, y, z, scale, cs, sn, argb);
            }
            return;
        }
        // Rows go in whole texel rows, in the steps the cut takes.
        float level = mode == FROM_TOP ? height * (1f - cut) : height * cut;
        int n = 0;
        for (int r = 0; r < rowCount; r++) {
            boolean gone = switch (mode) {
                case FROM_TOP -> rowHeight[r] > level;
                case FROM_FEET -> rowHeight[r] < level;
                default -> rowNoise[r] < cut;
            };
            if (!gone) {
                n = facing(rowNormal, rowMid, r, lx, ly, lz, n, false);
            }
        }
        sort(n);
        for (int i = 0; i < n; i++) {
            emit(out, rows, order[i] * QF, x, y, z, scale, cs, sn, argb);
        }
    }

    /** Adds face {@code i} to this frame's list when it's turned to the camera at (lx, ly, lz); returns the count. */
    private int facing(float[] normal, float[] mid, int i, float lx, float ly, float lz, int n, boolean back) {
        float dx = lx - mid[i * 3], dy = ly - mid[i * 3 + 1], dz = lz - mid[i * 3 + 2];
        float nx = normal[i * 3], ny = normal[i * 3 + 1], nz = normal[i * 3 + 2];
        // A face without a normal (none given) is always drawn.
        if ((nx * dx + ny * dy + nz * dz < 0f) != back && nx * nx + ny * ny + nz * nz > 0.01f) {
            return n;
        }
        order[n] = i;
        far[n] = dx * dx + dy * dy + dz * dz;
        return n + 1;
    }

    /** This frame's faces far to near (insertion sort: the order hardly changes from frame to frame). */
    private void sort(int n) {
        for (int i = 1; i < n; i++) {
            int o = order[i];
            float d = far[i];
            int j = i - 1;
            while (j >= 0 && far[j] < d) {
                order[j + 1] = order[j];
                far[j + 1] = far[j];
                j--;
            }
            order[j + 1] = o;
            far[j + 1] = d;
        }
    }

    private static void emit(FxOut out, float[] d, int o, float x, float y, float z, float scale, float cs, float sn, int argb) {
        for (int v = 0; v < 4; v++) {
            int i = o + v * VF;
            float px = d[i] * scale, py = d[i + 1] * scale, pz = d[i + 2] * scale;
            out.vertex(x + px * cs - pz * sn, y + py, z + px * sn + pz * cs, d[i + 3], d[i + 4], argb);
        }
    }

    /** How many texel rows the copy has (for the shatter's pieces). */
    public int rowCount() {
        return rowCount;
    }

    /** A row's middle (x, y, z from the feet) and its skin colour at its middle, into {@code out}; returns the colour. */
    public int row(int r, float[] out) {
        int o = r * QF;
        float x = 0f, y = 0f, z = 0f, u = 0f, v = 0f;
        for (int k = 0; k < 4; k++) {
            x += rows[o + k * VF];
            y += rows[o + k * VF + 1];
            z += rows[o + k * VF + 2];
            u += rows[o + k * VF + 3];
            v += rows[o + k * VF + 4];
        }
        out[0] = x / 4f;
        out[1] = y / 4f;
        out[2] = z / 4f;
        return skin.at((int) (u / 4f * skin.width), (int) (v / 4f * skin.height));
    }

    /**
     * The skin mixed {@code amount} of the way toward {@code tint} (1: a grey copy the vertex colour tints fully, its
     * shading kept in the four ramp bands), made once per tint.
     */
    public FxTex tinted(int tint, float amount) {
        if (amount <= 0f) {
            return skin;
        }
        if (amount >= 1f) {
            if (grey == null || grey.width != skin.width || grey.height != skin.height) {
                grey = new FxTex(skin.name + "_grey", skin.width, skin.height);
            }
            if (tintedFor != System.identityHashCode(skin) || tintedAmount != 2f) {
                for (int i = 0; i < skin.argb.length; i++) {
                    int c = skin.argb[i];
                    float l = Colors.luma(c);
                    // Four bands, as the ramp: bright, main, shade, deep.
                    int g = l > 0.62f ? 255 : l > 0.4f ? 214 : l > 0.22f ? 166 : 120;
                    grey.argb[i] = (c & 0xFF000000) | g << 16 | g << 8 | g;
                }
                grey.changed();
                tintedFor = System.identityHashCode(skin);
                tintedAmount = 2f;
            }
            return grey;
        }
        if (tinted == null || tinted.width != skin.width || tinted.height != skin.height) {
            tinted = new FxTex(skin.name + "_tint", skin.width, skin.height);
            tintedAmount = -1f;
        }
        if (tintedAmount != amount || tintedFor != (tint ^ System.identityHashCode(skin))) {
            for (int i = 0; i < skin.argb.length; i++) {
                int c = skin.argb[i];
                tinted.argb[i] = (c & 0xFF000000) | (Colors.mixRgb(c, tint, amount) & 0xFFFFFF);
            }
            tinted.changed();
            tintedAmount = amount;
            tintedFor = tint ^ System.identityHashCode(skin);
        }
        return tinted;
    }

    // ------------------------------------------------------------------ your last three seconds

    /** Your own poses over the last three seconds, 20 a second; recorded only while an effect wants them. */
    public static final class History {
        public static final int SIZE = 60;
        private final Pose[] poses = new Pose[SIZE];
        private int next, count;
        /** Set by the effects that replay (each tick); recording stops a second after nobody asks. */
        private int wantedFor;

        History() {
            for (int i = 0; i < SIZE; i++) {
                poses[i] = new Pose();
            }
        }

        public void want() {
            wantedFor = 20;
        }

        /** Once a client tick. */
        public void tick(Player you) {
            if (wantedFor <= 0 || you == null) {
                count = 0;
                return;
            }
            wantedFor--;
            poses[next].from(you, 1f);
            next = (next + 1) % SIZE;
            count = Math.min(SIZE, count + 1);
        }

        /** How many ticks are kept. */
        public int count() {
            return count;
        }

        /** The pose {@code ticksAgo} ticks back (0 the newest), or null when not kept. */
        public Pose ago(int ticksAgo) {
            if (ticksAgo < 0 || ticksAgo >= count) {
                return null;
            }
            return poses[Math.floorMod(next - 1 - ticksAgo, SIZE)];
        }
    }

    public static final History HISTORY = new History();

    // ------------------------------------------------------------------ the model's vertices

    /** Collects what the model draws (already posed) into a snapshot's whole faces. */
    private static final class Collector implements VertexConsumer {
        private PoseSnapshot into;
        private float x, y, z, u, v;
        private int vertex;

        void reset(PoseSnapshot s) {
            into = s;
            vertex = 0;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            return this;
        }

        public VertexConsumer setColor(int argb) {
            return this;
        }

        public VertexConsumer setUv3(float u, float v) {
            return this;
        }

        public VertexConsumer setLineWidth(float width) {
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            this.u = u;
            this.v = v;
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float nx, float ny, float nz) {
            PoseSnapshot s = into;
            s.ensureQuads(s.quadCount + 1);
            int o = s.quadCount * QF + vertex * VF;
            s.quadNormal[s.quadCount * 3] = nx;
            s.quadNormal[s.quadCount * 3 + 1] = ny;
            s.quadNormal[s.quadCount * 3 + 2] = nz;
            s.quads[o] = x;
            s.quads[o + 1] = y;
            s.quads[o + 2] = z;
            s.quads[o + 3] = u;
            s.quads[o + 4] = v;
            if (++vertex == 4) {
                vertex = 0;
                s.quadCount++;
            }
            return this;
        }
    }
}
