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
import io.github.georgwav.cosmetics.sol.render.HandMotion;
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
    static final int NORMAL = 0, LIT = 1, ARMS = 2;
    private static final long EPOCH = System.nanoTime();
    private static final java.util.Random RANDOM = new java.util.Random();

    private static KnifeView view;
    /** Whether the last hand drawn was a knife, and the hotbar slot it came out of. */
    private static boolean shownLastFrame;
    private static int equipSlot = -1;
    /** Draws since the game started (read by the in-game test). */
    public static int drawn;

    // The knife's own cost (test runs): render-thread time and bytes allocated drawing it, per frame, after it warmed up.
    private static final boolean BENCH = System.getProperty("cosmeticsmod.solScenario") != null
            || Boolean.getBoolean("cosmeticsmod.knifeBench");
    private static final java.lang.management.ThreadMXBean THREADS = java.lang.management.ManagementFactory.getThreadMXBean();
    private static final int WARM_UP = 30;
    private static int shownFrames;
    public static long benchNanos, benchBytes;
    public static int benchFrames;

    static long allocated() {
        return THREADS instanceof com.sun.management.ThreadMXBean t ? t.getCurrentThreadAllocatedBytes() : 0L;
    }

    /** Your skin's texture and whether it's slim, and the tick they were looked up. */
    //? if >=1.21.11 {
    private static net.minecraft.resources.Identifier skin;
    //?} else {
    /*private static net.minecraft.resources.ResourceLocation skin;
    *///?}
    private static int skinTick = Integer.MIN_VALUE;
    /** Knife space into the hand's this frame (rows of a 3x4 matrix), see {@link #local}. */
    private static final float[] LOCAL = new float[12];
    /** The points of the knife and the pack's other hand the glow's trail follows (pose space), and a box's scratch. */
    private static final float[] TRAIL = new float[HandMotion.FOLLOWED * 3], ARM_BOX = new float[6];
    /** The arms' render type, and the texture it was made with. */
    private static RenderType armType;
    private static Object armTypeTexture;

    // What the passes draw (set when the knife is handed over; read when its vertices are written).
    private static KnifeView drawing;
    private static boolean slim;
    private static int light;
    private static float handScale, gripX, gripZ;

    //? if >=1.21.9 {
    /** The knife's own faces (both passes, one hand-over), and the arms. */
    private static final SubmitNodeCollector.CustomGeometryRenderer[] PASSES = {
            (pose, consumer) -> {
                emit(pose, consumer, NORMAL);
                emit(pose, consumer, LIT);
            },
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

    /** The bright colour of the knife in your hand (ARGB), for the glow's "item" colours; 0 when none shows yet. */
    public static int brightColour() {
        KnifeView v = current();
        return v == null ? 0 : v.knife.bright;
    }

    /** Dev runs: how fast the knives' clock runs (slow motion for the trail shot), and where it was rebased. */
    private static float timeScale = 1f;
    private static double clockAt;
    private static long clockNanos = EPOCH;

    /** Seconds on the knives' clock. */
    public static float now() {
        if (!Float.isNaN(pinned)) {
            return pinned;
        }
        return (float) (clockAt + (System.nanoTime() - clockNanos) * 1.0e-9 * timeScale);
    }

    /** Dev runs: the knives' clock runs at {@code scale} times real time from now on. */
    public static void timeScale(float scale) {
        long n = System.nanoTime();
        clockAt += (n - clockNanos) * 1.0e-9 * timeScale;
        clockNanos = n;
        timeScale = scale;
    }

    /**
     * Dev runs: the knife in hand plays {@code action} ("draw", "light", "heavy", "inspect", or "idle" for nothing) from
     * now, and its clock stops {@code at} seconds into it, so a screenshot shows the same frame on every machine.
     */
    public static void hold(String action, float at) {
        pinned = Float.NaN;
        float t = play(action);
        if (!Float.isNaN(t)) {
            pinned = t + at;
        }
    }

    /** Dev runs: the knife in hand plays {@code action} now; the time it started, or NaN with no knife in hand. */
    public static float play(String action) {
        KnifeView v = view;
        if (v == null) {
            return Float.NaN;
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
        return t;
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
        boolean bench = BENCH && ++shownFrames > WARM_UP;
        long benchStart = bench ? System.nanoTime() : 0L, benchAlloc = bench ? allocated() : 0L;
        boolean preset = false;
        try {
            float now = now();
            if (view == null || view.knife != knife) {
                shownFrames = 0;
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

            colours(knife, now);
            RenderType knifeType = colouredType(knife);
            if (player.tickCount != skinTick) {
                // Your skin, looked up once a tick (the lookup allocates).
                skinTick = player.tickCount;
                //? if >=1.21.9 {
                var skinInfo = player.getSkin();
                skin = skinInfo.body().texturePath();
                slim = skinInfo.model() == net.minecraft.world.entity.player.PlayerModelType.SLIM;
                //?} else {
                /*var skinInfo = player.getSkin();
                skin = skinInfo.texture();
                slim = skinInfo.model() == net.minecraft.client.resources.PlayerSkin.Model.SLIM;
                *///?}
            }
            // Your skin, or the dark arms' texture while the Punchy module draws the arms dark; the render type kept
            // until that changes (the game's own arm render type, so the hand glow and the rest take it as a hand).
            var armTexture = PunchyModule.skinNow(skin);
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
            Float hands = io.github.georgwav.cosmetics.config.CosmeticsConfig.get().options(knife.id).knifeHands;
            handScale = spec.handScale() * (hands != null ? hands : 1f);
            gripX = grip[0];
            gripZ = grip[1];
            // Knife space into the hand's: the sway, lowered while put away, mirrored for a left main hand (the packs
            // are right-handed), pixels to blocks, the eye at the origin. Kept as a matrix the vertices take, so the
            // hand's pose is handed over as it is (no pose pushed).
            local(player, partialTick, lower, player.getMainArm() == HumanoidArm.LEFT, view.eye);
            preset = PunchyModule.knifePreset(poseStack);
            trail(poseStack.last(), view, player.getMainArm() != HumanoidArm.LEFT);
            //? if >=1.21.9 {
            buffers.submitCustomGeometry(poseStack, knifeType, PASSES[0]);
            buffers.submitCustomGeometry(poseStack, armType, PASSES[1]);
            //?} else {
            /*emit(poseStack.last(), buffers.getBuffer(knifeType), NORMAL);
            emit(poseStack.last(), buffers.getBuffer(knifeType), LIT);
            emit(poseStack.last(), buffers.getBuffer(armType), ARMS);
            *///?}
            drawn++;
            return true;
        } catch (RuntimeException | LinkageError e) {
            RenderGuard.cosmeticFailed(knife.id, e);
            shownLastFrame = false;
            return false;
        } finally {
            if (preset) {
                poseStack.popPose();
            }
            if (RenderStats.TIMING) {
                RenderStats.nanos += System.nanoTime() - start;
            }
            if (bench) {
                benchNanos += System.nanoTime() - benchStart;
                benchBytes += allocated() - benchAlloc;
                benchFrames++;
            }
        }
    }

    // ------------------------------------------------------------------ Punchy

    /** Whether Punchy (the mod) draws its own first-person model now: installed, and on with the Punchy module. */
    private static boolean punchyActive() {
        return Compat.PUNCHY && io.github.georgwav.cosmetics.client.Modules.PUNCHY.enabled();
    }

    /**
     * Whether the hands are the knife alone this frame, {@code shown} being the main-hand item the game is about to
     * draw: Punchy draws its own model at the start of the hands (its own way, not through the game's arm and item
     * methods), so while a knife is coming out or in hand the knife is drawn there instead and the rest is skipped:
     * the knife draws both hands as its pack animates them, so there's never a second arm, sword or offhand item.
     */
    public static boolean drawsAlone(ItemStack shown) {
        var p = Minecraft.getInstance().player;
        return p != null && punchyActive() && !p.isScoping() && shown == p.getMainHandItem() && knifeFor(p, shown) != null;
    }

    /**
     * Whether the game's offhand (its arm or what it holds, a shield or a totem) is left out this frame: the knife
     * in your main hand is showing, and its pack draws your other hand itself.
     */
    public static boolean hidesOffhand() {
        var p = Minecraft.getInstance().player;
        return shownLastFrame && view != null && p != null && knifeFor(p, p.getMainHandItem()) != null;
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
    private static void local(net.minecraft.client.player.LocalPlayer p, float pt, float lower, boolean mirror, float[] eye) {
        float xBob = p.xBobO + (p.xBob - p.xBobO) * pt, yBob = p.yBobO + (p.yBob - p.yBobO) * pt;
        float lagX = (p.getViewXRot(pt) - xBob) * 0.1f, lagY = (p.getViewYRot(pt) - yBob) * 0.1f;
        float s = PunchyModule.knifeSway();
        float x = s * LAG_CAP * (float) Math.tanh(lagX / LAG_CAP), y = s * LAG_CAP * (float) Math.tanh(lagY / LAG_CAP);
        // The turn: about Y, then X (as the game's own lag is applied).
        float ay = (y - lagY) * DEG, ax = (x - lagX) * DEG;
        float cy = (float) Math.cos(ay), sy = (float) Math.sin(ay), cx = (float) Math.cos(ax), sx = (float) Math.sin(ax);
        // R = Ry * Rx.
        float r00 = cy, r01 = sy * sx, r02 = sy * cx;
        float r10 = 0f, r11 = cx, r12 = -sx;
        float r20 = -sy, r21 = cy * sx, r22 = cy * cx;
        // Then lowered: t = R * (0, -0.6 lower, 0).
        float dy = -0.6f * lower;
        float t0 = r01 * dy, t1 = r11 * dy, t2 = r21 * dy;
        // Then mirrored and to blocks: R's columns scaled.
        float kx = (mirror ? -1f : 1f) / 16f, k = 1f / 16f;
        r00 *= kx; r10 *= kx; r20 *= kx;
        r01 *= k; r11 *= k; r21 *= k;
        r02 *= k; r12 *= k; r22 *= k;
        // Then the eye to the origin: t += R * -eye.
        t0 -= r00 * eye[0] + r01 * eye[1] + r02 * eye[2];
        t1 -= r10 * eye[0] + r11 * eye[1] + r12 * eye[2];
        t2 -= r20 * eye[0] + r21 * eye[1] + r22 * eye[2];
        float[] l = LOCAL;
        l[0] = r00; l[1] = r01; l[2] = r02; l[3] = t0;
        l[4] = r10; l[5] = r11; l[6] = r12; l[7] = t1;
        l[8] = r20; l[9] = r21; l[10] = r22; l[11] = t2;
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
        // One loop of the clip a stride: the game swings the legs on cos(position * 0.6662), so a stride is 2 pi / 0.6662
        // of its walk position (a loop every 2 of it raced the clip some 8 times a second: the knife wiggled).
        float steps = p.walkAnimation.position(pt);
        v.move.time = v.move.clip.length > 0f ? (steps % STRIDE) / STRIDE * v.move.clip.length : 0f;
    }

    private static final float DEG = (float) (Math.PI / 180.0);
    /** The game's walk position over one stride (both legs). */
    static final float STRIDE = (float) (2.0 * Math.PI / 0.6662);

    /** The Dark Star set the knife shows: 0 random each draw, 1 Devil, 2 Angel. */
    private static int angelChoice(Knife knife) {
        String own = io.github.georgwav.cosmetics.config.CosmeticsConfig.get().options(knife.id).knifeSet;
        return switch (own != null ? own : knife.spec.angel()) {
            case CosmeticDefinition.Melee.DEVIL -> 1;
            case CosmeticDefinition.Melee.ANGEL -> 2;
            default -> 0;
        };
    }

    // ------------------------------------------------------------------ the glow's trail

    /**
     * The hand glow's trail follows the knife as it moves, not the game's hand (never drawn while the knife is): the
     * knife's blade as the main hand, and the pack's other hand (when it shows) as the other.
     */
    private static void trail(PoseStack.Pose pose, KnifeView v, boolean rightMain) {
        KnifeRig rig = v.knife.rig;
        if (rig.blade >= 0) {
            trailPoints(pose, v.pose.world, rig.blade, rig.bladeBox);
            HandMotion.drawnAt(rightMain, TRAIL);
        }
        if (rig.otherHand >= 0 && v.pose.shown(rig.otherHand)) {
            // The left arm's fist end in its bone's frame (x 4 to 8, y 6 to 12, z -2 to 2: the arm reaches back past
            // the eye), sized round the fist as it's drawn.
            float s = handScale;
            ARM_BOX[0] = -ArmGeo.FIST_X;
            ARM_BOX[1] = ArmGeo.FIST_Y - 3f * s;
            ARM_BOX[2] = 0f;
            ARM_BOX[3] = 2f * s;
            ARM_BOX[4] = 3f * s;
            ARM_BOX[5] = 2f * s;
            trailPoints(pose, v.pose.world, rig.otherHand, ARM_BOX);
            HandMotion.drawnAt(!rightMain, TRAIL);
        }
    }

    /** A box's middle and either side of it on each axis, in bone {@code b}'s frame, into the hands' pose space ({@link #TRAIL}). */
    private static void trailPoints(PoseStack.Pose pose, float[] w, int b, float[] box) {
        var m = pose.pose();
        float[] l = LOCAL;
        int o = b * 12;
        for (int i = 0; i < HandMotion.FOLLOWED; i++) {
            float x = box[0], y = box[1], z = box[2];
            float side = (i & 1) == 1 ? 1f : -1f;
            switch ((i - 1) / 2) {
                case 0 -> x += i > 0 ? side * box[3] : 0f;
                case 1 -> y += side * box[4];
                default -> z += side * box[5];
            }
            // The bone's frame to knife space, to the hand's, to the pose the hands are drawn with.
            float kx = w[o] * x + w[o + 1] * y + w[o + 2] * z + w[o + 3];
            float ky = w[o + 4] * x + w[o + 5] * y + w[o + 6] * z + w[o + 7];
            float kz = w[o + 8] * x + w[o + 9] * y + w[o + 10] * z + w[o + 11];
            float hx = l[0] * kx + l[1] * ky + l[2] * kz + l[3];
            float hy = l[4] * kx + l[5] * ky + l[6] * kz + l[7];
            float hz = l[8] * kx + l[9] * ky + l[10] * kz + l[11];
            TRAIL[i * 3] = m.m00() * hx + m.m10() * hy + m.m20() * hz + m.m30();
            TRAIL[i * 3 + 1] = m.m01() * hx + m.m11() * hy + m.m21() * hz + m.m31();
            TRAIL[i * 3 + 2] = m.m02() * hx + m.m12() * hy + m.m22() * hz + m.m32();
        }
    }

    // ------------------------------------------------------------------ vertices

    /** One pass of the knife: its own faces (lit or not), or your arms at its {@code *_pos} bones. */
    private static void emit(PoseStack.Pose pose, VertexConsumer consumer, int pass) {
        KnifeView v = drawing;
        if (v == null || RenderGuard.isDisabled(v.knife.id)) {
            return;
        }
        long start = RenderStats.TIMING ? System.nanoTime() : 0L;
        boolean bench = BENCH && shownFrames > WARM_UP;
        long benchStart = bench ? System.nanoTime() : 0L, benchAlloc = bench ? allocated() : 0L;
        try {
            emitPass(pose, consumer, pass, v.pose, v.knife.solid, light, false, LOCAL);
        } catch (RuntimeException | LinkageError e) {
            RenderGuard.cosmeticFailed(v.knife.id, e);
        }
        if (RenderStats.TIMING) {
            RenderStats.nanos += System.nanoTime() - start;
        }
        if (bench) {
            // Counted with the frame's hand-over (the vertices are written after it from 1.21.9).
            benchNanos += System.nanoTime() - benchStart;
            benchBytes += allocated() - benchAlloc;
        }
    }

    /**
     * One pass of a posed knife into a consumer: {@code pass} {@link #NORMAL} or {@link #LIT} faces, or {@link #ARMS};
     * {@code solid}: the knife's faces that show something ({@link Knife#solid}), or null for all of them;
     * {@code third}: as held in third person (no slash effects or glints).
     */
    static void emitPass(PoseStack.Pose pose, VertexConsumer consumer, int pass, KnifePose p, float[][] solid, int light,
                         boolean third, float[] local) {
        // `var`: Matrix4fc/Matrix3fc on newer versions, Matrix4f/Matrix3f on older ones.
        var m = pose.pose();
        var nm = pose.normal();
        float m00 = m.m00(), m01 = m.m10(), m02 = m.m20(), m03 = m.m30();
        float m10 = m.m01(), m11 = m.m11(), m12 = m.m21(), m13 = m.m31();
        float m20 = m.m02(), m21 = m.m12(), m22 = m.m22(), m23 = m.m32();
        float n00 = nm.m00(), n01 = nm.m10(), n02 = nm.m20();
        float n10 = nm.m01(), n11 = nm.m11(), n12 = nm.m21();
        float n20 = nm.m02(), n21 = nm.m12(), n22 = nm.m22();
        if (local != null) {
            // M = M * L, and the normals' N = N * L's turn.
            float[] l = local;
            float a00 = m00 * l[0] + m01 * l[4] + m02 * l[8], a01 = m00 * l[1] + m01 * l[5] + m02 * l[9];
            float a02 = m00 * l[2] + m01 * l[6] + m02 * l[10], a03 = m00 * l[3] + m01 * l[7] + m02 * l[11] + m03;
            float a10 = m10 * l[0] + m11 * l[4] + m12 * l[8], a11 = m10 * l[1] + m11 * l[5] + m12 * l[9];
            float a12 = m10 * l[2] + m11 * l[6] + m12 * l[10], a13 = m10 * l[3] + m11 * l[7] + m12 * l[11] + m13;
            float a20 = m20 * l[0] + m21 * l[4] + m22 * l[8], a21 = m20 * l[1] + m21 * l[5] + m22 * l[9];
            float a22 = m20 * l[2] + m21 * l[6] + m22 * l[10], a23 = m20 * l[3] + m21 * l[7] + m22 * l[11] + m23;
            m00 = a00; m01 = a01; m02 = a02; m03 = a03;
            m10 = a10; m11 = a11; m12 = a12; m13 = a13;
            m20 = a20; m21 = a21; m22 = a22; m23 = a23;
            float b00 = n00 * l[0] + n01 * l[4] + n02 * l[8], b01 = n00 * l[1] + n01 * l[5] + n02 * l[9], b02 = n00 * l[2] + n01 * l[6] + n02 * l[10];
            float b10 = n10 * l[0] + n11 * l[4] + n12 * l[8], b11 = n10 * l[1] + n11 * l[5] + n12 * l[9], b12 = n10 * l[2] + n11 * l[6] + n12 * l[10];
            float b20 = n20 * l[0] + n21 * l[4] + n22 * l[8], b21 = n20 * l[1] + n21 * l[5] + n22 * l[9], b22 = n20 * l[2] + n21 * l[6] + n22 * l[10];
            n00 = b00; n01 = b01; n02 = b02;
            n10 = b10; n11 = b11; n12 = b12;
            n20 = b20; n21 = b21; n22 = b22;
        }
        boolean oneCall = !Compat.isShaderPackActive();
        // The player's tint on the knife in hand (third person and the arms keep their own colours).
        int colour = pass == ARMS || third ? 0xFFFFFFFF : tint;
        int packed = pass == LIT ? MeshEmitter.FULL_BRIGHT : light;
        KnifeGeo.Bone[] bones = p.geo.bones;
        float[] w = p.world;
        int vertices = 0;
        for (int b = 0; b < bones.length; b++) {
            KnifeGeo.Bone bone = bones[b];
            if (bone.marker || (pass == ARMS) != (bone.arm != 0) || pass != ARMS && bone.lit != (pass == LIT)
                    || third && bone.effect || !p.shown(b)) {
                continue;
            }
            float[] quads = pass == ARMS ? ArmGeo.quads(bone.arm > 0, slim) : solid != null ? solid[b] : bone.quads;
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
                            colour, quads[i + 3], quads[i + 4], packed, nx, ny, nz);
                }
                vertices += 4;
            }
        }
        RenderStats.countVertices(vertices);
    }

    // ------------------------------------------------------------------ the texture

    /** The knife's render type: its texture cut at half alpha once that's made, the pack's own until then. */
    static RenderType knifeType(Knife k) {
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

    /** The knife in hand's render type in the player's colours ({@link #colours} worked them out this frame). */
    private static RenderType colouredType(Knife k) {
        RenderType plain = knifeType(k);
        io.github.georgwav.cosmetics.render.Recolor r = recolor;
        int[] source = k.cutSource;
        if (r == null || source == null || !(k.cutTexture instanceof SolTexture)) {
            return plain;
        }
        // The player's colours: the cut texture recoloured (again at most every eighth of a second while a slider moves).
        long now = System.nanoTime();
        if (k.recolorTexture == null || !r.equals(k.recolorWith) && now - k.recolorMade > 120_000_000L) {
            if (k.recolorPixels == null) {
                k.recolorPixels = new int[source.length];
            }
            r.applyAll(source, k.recolorPixels);
            if (k.recolorTexture instanceof SolTexture t) {
                t.upload(k.recolorPixels);
            } else {
                SolTexture t = SolTexture.of("knife/" + k.id.replace(':', '/').toLowerCase(Locale.ROOT) + "_colour",
                        k.cutWidth, k.cutHeight, false, k.recolorPixels);
                k.recolorTexture = t;
                k.recolorType = CosmeticRenderTypes.create(t.id.toString(), RenderMode.CUTOUT);
            }
            k.recolorWith = r;
            k.recolorMade = now;
        }
        return (RenderType) k.recolorType;
    }

    /** The recolour the knife in hand is drawn with this frame (null: as its pack made it), and its tint (ARGB). */
    private static io.github.georgwav.cosmetics.render.Recolor recolor;
    private static int tint = 0xFFFFFFFF;

    /** The knife's colours from its Wardrobe settings, as other cosmetics take theirs (see CosmeticRenderer). */
    private static void colours(Knife k, float now) {
        var config = io.github.georgwav.cosmetics.config.CosmeticsConfig.get();
        var baked = io.github.georgwav.cosmetics.model.CosmeticRegistry.get(k.id);
        if (baked == null) {
            recolor = null;
            tint = 0xFFFFFFFF;
            return;
        }
        var options = config.options(k.id);
        int allowed = config.allowedOptions(baked);
        var base = io.github.georgwav.cosmetics.render.Recolor.of(options, allowed, 0xFFFFFF);
        boolean picked = io.github.georgwav.cosmetics.render.Tint.picked(options, allowed);
        boolean rainbow = io.github.georgwav.cosmetics.render.Tint.rainbow(options, allowed);
        int colour = io.github.georgwav.cosmetics.render.Tint.argb(options, baked, now);
        int[] source = k.cutSource;
        var paint = picked && source != null ? io.github.georgwav.cosmetics.render.Recolor.paintFor(options.colorStyle, true,
                rainbow, k.coloured, source.length) : io.github.georgwav.cosmetics.render.Recolor.Paint.NONE;
        recolor = io.github.georgwav.cosmetics.render.Recolor.painted(base, paint,
                rainbow ? io.github.georgwav.cosmetics.render.Recolor.rainbowStep(colour) : colour);
        tint = picked && paint != io.github.georgwav.cosmetics.render.Recolor.Paint.COLORED ? colour | 0xFF000000 : 0xFFFFFFFF;
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
                k.solid = Knife.solidQuads(k.rig.geo, out, image.getWidth(), image.getHeight());
                k.coloured = io.github.georgwav.cosmetics.render.Recolor.coloured(out);
                k.cutSource = out.clone();
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
