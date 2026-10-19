package io.github.georgwav.cosmetics.melee;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.georgwav.cosmetics.CosmeticsMod;
import io.github.georgwav.cosmetics.client.modules.PunchyModule;
import io.github.georgwav.cosmetics.compat.Compat;
import io.github.georgwav.cosmetics.model.BakedCosmetic;
import io.github.georgwav.cosmetics.model.CosmeticDefinition;
import io.github.georgwav.cosmetics.model.CosmeticSlot;
import io.github.georgwav.cosmetics.model.RenderMode;
import io.github.georgwav.cosmetics.render.CosmeticRenderTypes;
import io.github.georgwav.cosmetics.render.CosmeticRenderer;
import io.github.georgwav.cosmetics.render.MeshEmitter;
import io.github.georgwav.cosmetics.render.RenderGuard;
import io.github.georgwav.cosmetics.render.RenderStats;
import io.github.georgwav.cosmetics.sol.render.SolTexture;
import io.github.georgwav.cosmetics.sol.util.SolExecutors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
//? if >=1.21.11 {
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
//?} else {
/*import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
*///?}
//? if >=1.21.9 {
import net.minecraft.client.renderer.SubmitNodeCollector;
//?} else {
/*import net.minecraft.client.renderer.MultiBufferSource;
*///?}

import java.io.InputStream;
import java.util.Locale;

/**
 * Knives in first person: hold a sword (or an axe, if the Knives settings say) while wearing a knife and the game's
 * main hand is drawn as the knife, posed by its own animations, with your arms (your skin and sleeves) where the pack's
 * animators put them. It is drawn in the hand's place, with the pose the game hands the hand (its bobbing and turn,
 * and the Punchy module's arm size), with depth, and its arms with your skin's own render type: so Sol arms, PvP mode,
 * dark arms, the glow and the motion trail all take it as your hands. The offhand stays as it is.
 *
 * <p>Knife space (pixels, Blockbench's axes, the camera looking toward -Z) becomes the hand's space by 1/16 and the
 * eye ({@link KnifeView#eye}) moved to the origin. Switching away lowers the knife as the game lowers an item; the
 * knife coming out plays its draw. Its texture is drawn cut at half alpha (TACZ's cutout), made off the game's thread
 * the first time the knife is shown (the pack's own texture meanwhile). Render thread only; nothing allocated per frame.
 */
public final class KnifeRenderer {
    private static final int NORMAL = 0, LIT = 1, ARMS = 2;
    private static final long EPOCH = System.nanoTime();
    private static final java.util.Random RANDOM = new java.util.Random();

    private static KnifeView view;
    /** Whether the last hand drawn was a knife, and the hotbar slot it came out of. */
    private static boolean shownLastFrame;
    private static int equipSlot = -1;
    /** Draws since the game started (read by the in-game test). */
    public static int drawn;

    /** The arms' render type, and the texture it was made with. */
    private static RenderType armType;
    private static Object armTypeTexture;

    // What the passes draw (set when the knife is handed over; read when its vertices are written).
    private static KnifeView drawing;
    private static boolean slim;
    private static int light;
    private static float handScale, gripX, gripZ;

    //? if >=1.21.9 {
    private static final SubmitNodeCollector.CustomGeometryRenderer[] PASSES = {
            (pose, consumer) -> emit(pose, consumer, NORMAL),
            (pose, consumer) -> emit(pose, consumer, LIT),
            (pose, consumer) -> emit(pose, consumer, ARMS)};
    //?}

    private KnifeRenderer() {
    }

    /** The knife you hold now, or null: a sword (or axe) in your main hand and a knife worn. */
    public static Knife knifeFor(AbstractClientPlayer player, ItemStack stack) {
        if (stack.isEmpty() || !(stack.is(ItemTags.SWORDS) || PunchyModule.knifeOnAxes() && stack.is(ItemTags.AXES))
                || !CosmeticRenderer.showsFor(player.getUUID(), true)) {
            return null;
        }
        BakedCosmetic c = CosmeticRenderer.heldFor(player.getUUID(), true, CosmeticSlot.MELEE);
        Knife k = c == null ? null : KnifeRegistry.get(c.id());
        return k == null || RenderGuard.isDisabled(k.id) ? null : k;
    }

    /** The knife in your hand this frame (its behaviour takes your attacks), or null. */
    public static KnifeView current() {
        return shownLastFrame ? view : null;
    }

    /** Dev runs (the test scenario's shots): the knives' clock held at this time, or NaN while it runs. */
    private static float pinned = Float.NaN;

    /** Seconds on the knives' clock. */
    public static float now() {
        return Float.isNaN(pinned) ? (System.nanoTime() - EPOCH) * 1.0e-9f : pinned;
    }

    /**
     * Dev runs: the knife in hand plays {@code action} ("draw", "light", "heavy", "inspect", or "idle" for nothing) from
     * now, and its clock stops {@code at} seconds into it, so a screenshot shows the same frame on every machine.
     */
    public static void hold(String action, float at) {
        pinned = Float.NaN;
        KnifeView v = view;
        if (v == null) {
            return;
        }
        float t = now();
        float cooldown = KnifeTiming.cooldown(1.6), swing = KnifeTiming.swing(0, 0);
        boolean fit = PunchyModule.knifeFit();
        switch (action) {
            case "draw" -> v.behaviour.equip(t, cooldown, fit, angelChoice(v.knife));
            case "light" -> v.behaviour.attack(t, false, true, cooldown, swing, fit);
            case "heavy" -> v.behaviour.attack(t, true, true, cooldown, swing, fit);
            case "inspect" -> v.behaviour.inspect(t);
            default -> {
            }
        }
        pinned = t + at;
    }

    /** Dev runs: the knives' clock runs again. */
    public static void release() {
        pinned = Float.NaN;
    }

    /**
     * The game is about to draw the main hand holding {@code stack}: draws the knife instead and returns true, or
     * returns false to let the game draw it. {@code equip}: how far down the hand is (0 up, 1 out of sight).
     */
    //? if >=1.21.9 {
    public static boolean mainHand(ItemStack stack, float equip, float partialTick, PoseStack poseStack, SubmitNodeCollector buffers,
                                   int packedLight) {
    //?} else {
    /*public static boolean mainHand(ItemStack stack, float equip, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                                   int packedLight) {
    *///?}
        var player = Minecraft.getInstance().player;
        Knife knife = player == null ? null : knifeFor(player, stack);
        if (knife == null) {
            shownLastFrame = false;
            shakeX = shakeY = shakeZ = 0f;
            return false;
        }
        long start = RenderStats.TIMING ? System.nanoTime() : 0L;
        try {
            float now = now();
            if (view == null || view.knife != knife) {
                view = new KnifeView(knife, RANDOM, new KnifeSounds(knife.spec.sounds()));
                shownLastFrame = false;
            }
            ItemStack held = player.getMainHandItem();
            boolean coming = stack == held || ItemStack.matches(stack, held);
            //? if >=1.21.5 {
            int slot = player.getInventory().getSelectedSlot();
            //?} else {
            /*int slot = player.getInventory().selected;
            *///?}
            if (!coming && punchyActive()) {
                // Switching away with Punchy on: Punchy's own animation takes the old item down.
                shownLastFrame = false;
                return false;
            }
            float lower = 0f;
            if (coming) {
                if (!shownLastFrame || slot != equipSlot) {
                    view.behaviour.equip(now, player.getCurrentItemAttackStrengthDelay() / 20f, PunchyModule.knifeFit(),
                            angelChoice(knife));
                    equipSlot = slot;
                }
            } else {
                // Put away: down as the game lowers an item (no pack has a put-away animation).
                lower = equip;
            }
            shownLastFrame = true;
            move(view, player, partialTick, now);
            view.frame(now, PunchyModule.knifeShowsHands());
            // The camera bone's keys shake the world's view (never your aim): ViewBobMixin takes it next frame.
            float shake = PunchyModule.knifeShake();
            shakeX = view.pose.camera[0] * shake;
            shakeY = view.pose.camera[1] * shake;
            shakeZ = view.pose.camera[2] * shake;

            RenderType knifeType = knifeType(knife);
            //? if >=1.21.9 {
            var skinInfo = player.getSkin();
            var skin = skinInfo.body().texturePath();
            slim = skinInfo.model() == net.minecraft.world.entity.player.PlayerModelType.SLIM;
            //?} else {
            /*var skinInfo = player.getSkin();
            var skin = skinInfo.texture();
            slim = skinInfo.model() == net.minecraft.client.resources.PlayerSkin.Model.SLIM;
            *///?}
            // Your skin, or the dark arms' texture while the Punchy module draws the arms dark; the render type kept
            // until that changes (the game's own arm render type, so the hand glow and the rest take it as a hand).
            var armTexture = PunchyModule.skinOrDark(skin);
            if (armTexture != armTypeTexture) {
                armTypeTexture = armTexture;
                //? if >=1.21.11 {
                armType = RenderTypes.entityTranslucent(armTexture);
                //?} else {
                /*armType = RenderType.entityTranslucent(armTexture);
                *///?}
            }

            CosmeticDefinition.Melee spec = knife.spec;
            float[] grip = spec.grip() != null ? spec.grip() : knife.rig.grip;
            drawing = view;
            light = packedLight;
            handScale = spec.handScale();
            gripX = grip[0];
            gripZ = grip[1];
            poseStack.pushPose();
            sway(poseStack, player, partialTick);
            if (lower > 0f) {
                poseStack.translate(0f, -0.6f * lower, 0f);
            }
            if (player.getMainArm() == HumanoidArm.LEFT) {
                // The packs are right-handed: mirrored for a left main hand.
                poseStack.scale(-1f, 1f, 1f);
            }
            poseStack.scale(1f / 16f, 1f / 16f, 1f / 16f);
            poseStack.translate(-view.eye[0], -view.eye[1], -view.eye[2]);
            //? if >=1.21.9 {
            buffers.submitCustomGeometry(poseStack, knifeType, PASSES[NORMAL]);
            buffers.submitCustomGeometry(poseStack, knifeType, PASSES[LIT]);
            buffers.submitCustomGeometry(poseStack, armType, PASSES[ARMS]);
            //?} else {
            /*emit(poseStack.last(), buffers.getBuffer(knifeType), NORMAL);
            emit(poseStack.last(), buffers.getBuffer(knifeType), LIT);
            emit(poseStack.last(), buffers.getBuffer(armType), ARMS);
            *///?}
            poseStack.popPose();
            drawn++;
            return true;
        } catch (RuntimeException | LinkageError e) {
            RenderGuard.cosmeticFailed(knife.id, e);
            shownLastFrame = false;
            return false;
        } finally {
            if (RenderStats.TIMING) {
                RenderStats.nanos += System.nanoTime() - start;
            }
        }
    }

    // ------------------------------------------------------------------ Punchy

    /** Whether Punchy (the mod) draws its own first-person model now: installed, and on with the Punchy module. */
    private static boolean punchyActive() {
        return Compat.PUNCHY && io.github.georgwav.cosmetics.client.Modules.PUNCHY.enabled();
    }

    /** While Punchy draws its own hands this frame and the main one is a knife: its main arm and item are skipped. */
    private static boolean punchyPass;

    /**
     * The game starts drawing the hands, {@code shown} being the main-hand item it draws: when Punchy draws its own
     * model first and that item is a knife coming out or in hand, Punchy's main arm and item are left out of its model
     * (the knife draws them), so there's never a second arm or sword. Punchy keeps the offhand and every other item.
     */
    public static void handsStart(ItemStack shown) {
        var p = Minecraft.getInstance().player;
        punchyPass = p != null && punchyActive() && shown == p.getMainHandItem() && knifeFor(p, shown) != null;
    }

    /** Punchy's model is done (the game draws its own hands next). */
    public static void handsEnd() {
        punchyPass = false;
    }

    /** Whether Punchy's model leaves out this arm now. */
    public static boolean skipPunchyArm(HumanoidArm arm) {
        var p = Minecraft.getInstance().player;
        return punchyPass && p != null && arm == p.getMainArm();
    }

    /** Whether Punchy's model leaves out this item now: the main-hand item, held in the main arm. */
    public static boolean skipPunchyItem(net.minecraft.world.entity.LivingEntity entity, ItemStack stack,
                                         net.minecraft.world.item.ItemDisplayContext context) {
        var p = Minecraft.getInstance().player;
        if (!punchyPass || p == null || entity != p) {
            return false;
        }
        boolean left = context == net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                || context == net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
        return left == (p.getMainArm() == HumanoidArm.LEFT) && (stack == p.getMainHandItem() || ItemStack.matches(stack, p.getMainHandItem()));
    }

    // ------------------------------------------------------------------ sway, shake and movement

    /** Degrees the knife's camera bone turns the world's view this frame (the knives' shake setting applied). */
    private static float shakeX, shakeY, shakeZ;
    private static final org.joml.Quaternionf TURN = new org.joml.Quaternionf();
    /** The turn lag, eased toward this many degrees at most. */
    private static final float LAG_CAP = 5f;
    private static float lastMove = Float.NaN;

    /**
     * Turns the world's view by the knife's camera shake, in the game's hurt-tilt step of drawing the level (so the
     * world moves, and the knife, your aim and the crosshair don't).
     */
    public static void shake(PoseStack pose) {
        if (shakeX == 0f && shakeY == 0f && shakeZ == 0f || !shownLastFrame
                || !Minecraft.getInstance().options.getCameraType().isFirstPerson()) {
            return;
        }
        // The packs' camera keys as Blockbench turns them: Z, then Y, then X, the X and Y negated.
        CosmeticRenderer.rotate(pose, TURN.rotationZYX(shakeZ * DEG, -shakeY * DEG, -shakeX * DEG));
    }

    /**
     * The knife trails your view as you turn: the game's own lag (a tenth of how far the view is ahead of the arm,
     * straight on) is taken off and put back eased (tanh, at most {@link #LAG_CAP} degrees), times the sway setting.
     * The game's walking bob, already in the pose, carries over.
     */
    private static void sway(PoseStack pose, net.minecraft.client.player.LocalPlayer p, float pt) {
        float xBob = p.xBobO + (p.xBob - p.xBobO) * pt, yBob = p.yBobO + (p.yBob - p.yBobO) * pt;
        float lagX = (p.getViewXRot(pt) - xBob) * 0.1f, lagY = (p.getViewYRot(pt) - yBob) * 0.1f;
        float s = PunchyModule.knifeSway();
        float x = s * LAG_CAP * (float) Math.tanh(lagX / LAG_CAP), y = s * LAG_CAP * (float) Math.tanh(lagY / LAG_CAP);
        CosmeticRenderer.rotate(pose, TURN.rotationY((y - lagY) * DEG));
        CosmeticRenderer.rotate(pose, TURN.rotationX((x - lagX) * DEG));
    }

    /**
     * Movement blended on top: Delta Force's run while you sprint with nothing else playing (held in the air), or
     * Valorant's walk while you walk; its progress follows your steps, and it fades in and out over a fifth of a second.
     */
    private static void move(KnifeView v, net.minecraft.client.player.LocalPlayer p, float pt, float now) {
        KnifeRig rig = v.knife.rig;
        float dt = Float.isNaN(lastMove) ? 0f : Math.max(0f, Math.min(0.1f, now - lastMove));
        lastMove = now;
        KnifeClip clip;
        float target;
        if (rig.run != null) {
            clip = rig.run;
            target = p.onGround() ? (p.isSprinting() && v.behaviour.kind() == KnifeBehaviour.Kind.NONE ? 1f : 0f)
                    : v.move.weight > 0.5f ? 1f : 0f;
        } else if (rig.walkForward != null) {
            clip = rig.walkForward;
            target = p.onGround() && p.getDeltaMovement().horizontalDistanceSqr() > 1.0e-3 ? 1f : 0f;
        } else {
            v.move.clip = null;
            return;
        }
        float w = v.move.weight;
        v.move.weight = target > w ? Math.min(target, w + dt * 5f) : Math.max(target, w - dt * 5f);
        v.move.clip = rig.angel(clip, v.behaviour.angel());
        float steps = p.walkAnimation.position(pt);
        v.move.time = v.move.clip.length > 0f ? (steps % 2f) / 2f * v.move.clip.length : 0f;
    }

    private static final float DEG = (float) (Math.PI / 180.0);

    /** The Dark Star set the knife shows: 0 random each draw, 1 Devil, 2 Angel. */
    private static int angelChoice(Knife knife) {
        return switch (knife.spec.angel()) {
            case CosmeticDefinition.Melee.DEVIL -> 1;
            case CosmeticDefinition.Melee.ANGEL -> 2;
            default -> 0;
        };
    }

    // ------------------------------------------------------------------ vertices

    /** One pass of the knife: its own faces (lit or not), or your arms at its {@code *_pos} bones. */
    private static void emit(PoseStack.Pose pose, VertexConsumer consumer, int pass) {
        KnifeView v = drawing;
        if (v == null || RenderGuard.isDisabled(v.knife.id)) {
            return;
        }
        long start = RenderStats.TIMING ? System.nanoTime() : 0L;
        try {
            emitPass(pose, consumer, pass, v);
        } catch (RuntimeException | LinkageError e) {
            RenderGuard.cosmeticFailed(v.knife.id, e);
        }
        if (RenderStats.TIMING) {
            RenderStats.nanos += System.nanoTime() - start;
        }
    }

    private static void emitPass(PoseStack.Pose pose, VertexConsumer consumer, int pass, KnifeView v) {
        // `var`: Matrix4fc/Matrix3fc on newer versions, Matrix4f/Matrix3f on older ones.
        var m = pose.pose();
        var nm = pose.normal();
        float m00 = m.m00(), m01 = m.m10(), m02 = m.m20(), m03 = m.m30();
        float m10 = m.m01(), m11 = m.m11(), m12 = m.m21(), m13 = m.m31();
        float m20 = m.m02(), m21 = m.m12(), m22 = m.m22(), m23 = m.m32();
        float n00 = nm.m00(), n01 = nm.m10(), n02 = nm.m20();
        float n10 = nm.m01(), n11 = nm.m11(), n12 = nm.m21();
        float n20 = nm.m02(), n21 = nm.m12(), n22 = nm.m22();
        boolean oneCall = !Compat.isShaderPackActive();
        int packed = pass == LIT ? MeshEmitter.FULL_BRIGHT : light;
        KnifePose p = v.pose;
        KnifeGeo.Bone[] bones = p.geo.bones;
        float[] w = p.world;
        int vertices = 0;
        for (int b = 0; b < bones.length; b++) {
            KnifeGeo.Bone bone = bones[b];
            if (bone.marker || (pass == ARMS) != (bone.arm != 0) || pass != ARMS && bone.lit != (pass == LIT)
                    || !p.shown(b)) {
                continue;
            }
            float[] quads = pass == ARMS ? ArmGeo.quads(bone.arm > 0, slim) : bone.quads;
            if (quads.length == 0) {
                continue;
            }
            // This bone's matrix (knife space) taken into the hand's space: C = M * W.
            int o = b * 12;
            float w00 = w[o], w01 = w[o + 1], w02 = w[o + 2], w03 = w[o + 3];
            float w10 = w[o + 4], w11 = w[o + 5], w12 = w[o + 6], w13 = w[o + 7];
            float w20 = w[o + 8], w21 = w[o + 9], w22 = w[o + 10], w23 = w[o + 11];
            float c00 = m00 * w00 + m01 * w10 + m02 * w20, c01 = m00 * w01 + m01 * w11 + m02 * w21, c02 = m00 * w02 + m01 * w12 + m02 * w22;
            float c10 = m10 * w00 + m11 * w10 + m12 * w20, c11 = m10 * w01 + m11 * w11 + m12 * w21, c12 = m10 * w02 + m11 * w12 + m12 * w22;
            float c20 = m20 * w00 + m21 * w10 + m22 * w20, c21 = m20 * w01 + m21 * w11 + m22 * w21, c22 = m20 * w02 + m21 * w12 + m22 * w22;
            float c03 = m00 * w03 + m01 * w13 + m02 * w23 + m03;
            float c13 = m10 * w03 + m11 * w13 + m12 * w23 + m13;
            float c23 = m20 * w03 + m21 * w13 + m22 * w23 + m23;
            // An arm: sized round its fist, and the right one moved onto the handle.
            float s = 1f, fx = 0f, fy = 0f, dx = 0f, dz = 0f;
            if (pass == ARMS) {
                s = handScale;
                fx = bone.arm > 0 ? ArmGeo.FIST_X : -ArmGeo.FIST_X;
                fy = ArmGeo.FIST_Y;
                if (bone.arm > 0) {
                    dx = gripX;
                    dz = gripZ;
                }
            }
            for (int q = 0; q < quads.length; q += KnifeGeo.QUAD) {
                float lx = quads[q + 20], ly = quads[q + 21], lz = quads[q + 22];
                float bx = w00 * lx + w01 * ly + w02 * lz, by = w10 * lx + w11 * ly + w12 * lz, bz = w20 * lx + w21 * ly + w22 * lz;
                float nx = n00 * bx + n01 * by + n02 * bz, ny = n10 * bx + n11 * by + n12 * bz, nz = n20 * bx + n21 * by + n22 * bz;
                float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                if (len > 1.0e-6f) {
                    nx /= len;
                    ny /= len;
                    nz /= len;
                }
                for (int k = 0; k < 4; k++) {
                    int i = q + k * 5;
                    float x = quads[i], y = quads[i + 1], z = quads[i + 2];
                    if (pass == ARMS) {
                        x = fx + (x - fx) * s + dx;
                        y = fy + (y - fy) * s;
                        z = z * s + dz;
                    }
                    MeshEmitter.vertex(consumer, oneCall,
                            c00 * x + c01 * y + c02 * z + c03,
                            c10 * x + c11 * y + c12 * z + c13,
                            c20 * x + c21 * y + c22 * z + c23,
                            0xFFFFFFFF, quads[i + 3], quads[i + 4], packed, nx, ny, nz);
                }
                vertices += 4;
            }
        }
        RenderStats.countVertices(vertices);
    }

    // ------------------------------------------------------------------ the texture

    /** The knife's render type: its texture cut at half alpha once that's made, the pack's own until then. */
    private static RenderType knifeType(Knife k) {
        if (k.cutTexture == null) {
            int[] px = k.cutPixels;
            if (px != null) {
                k.cutTexture = SolTexture.of("knife/" + k.id.replace(':', '/').toLowerCase(Locale.ROOT), k.cutWidth,
                        k.cutHeight, false, px);
                k.cutPixels = null;
                k.renderType = null;
            } else if (!k.cutting && !k.cutFailed) {
                k.cutting = true;
                SolExecutors.CPU.execute(() -> cut(k));
            }
        }
        if (k.renderType == null) {
            String texture = k.cutTexture instanceof SolTexture t ? t.id.toString() : k.texture;
            k.renderType = CosmeticRenderTypes.create(texture, RenderMode.CUTOUT);
        }
        return (RenderType) k.renderType;
    }

    /** Reads the knife's texture and cuts it (off the game's thread). */
    private static void cut(Knife k) {
        try {
            //? if >=1.21.11 {
            var id = Identifier.parse(k.texture);
            //?} else {
            /*var id = ResourceLocation.parse(k.texture);
            *///?}
            var resource = Minecraft.getInstance().getResourceManager().getResource(id).orElseThrow();
            try (InputStream in = resource.open(); NativeImage image = NativeImage.read(in)) {
                int[] argb = image.makePixelArray();
                int[] out = new int[argb.length];
                k.bright = Knife.cut(argb, out);
                k.cutWidth = image.getWidth();
                k.cutHeight = image.getHeight();
                k.cutPixels = out;
            }
        } catch (Exception e) {
            k.cutFailed = true;
            CosmeticsMod.LOGGER.warn("Knife {}: couldn't read its texture {}: {}", k.id, k.texture, e.toString());
        } finally {
            k.cutting = false;
        }
    }

    /** Cosmetics were reloaded: the old knives' textures go, the new knives come in. Game thread. */
    public static void reload(java.util.Map<String, Knife> knives) {
        for (Knife k : KnifeRegistry.all()) {
            if (k.cutTexture instanceof SolTexture t) {
                t.release();
            }
        }
        view = null;
        drawing = null;
        shownLastFrame = false;
        KnifeRegistry.apply(knives);
    }
}
