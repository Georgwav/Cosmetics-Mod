package io.github.georgwav.cosmetics.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.georgwav.cosmetics.client.modules.HeldItems;
import io.github.georgwav.cosmetics.client.modules.PunchyModule;
import io.github.georgwav.cosmetics.sol.fx.FxSprites;
import io.github.georgwav.cosmetics.sol.fx.Ramp;
import io.github.georgwav.cosmetics.sol.fx.WorldOut;
import io.github.georgwav.cosmetics.sol.util.Colors;
import io.github.georgwav.cosmetics.ui.TilePictures;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
//? if >=1.21.11 {
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
//?} else {
/*import net.minecraft.client.renderer.RenderType;
*///?}
//? if >=1.21.9 {
import net.minecraft.client.renderer.SubmitNodeCollector;
//?} else {
/*import net.minecraft.client.renderer.MultiBufferSource;
*///?}

/**
 * The Punchy module's first-person hands (C.9), on the game's own hand drawing (the mixins call in here):
 * <ul>
 * <li>The view model: each hand moved, turned and sized round where it holds things, the left one mirroring what's set
 * for a hand on the right. Knives and Sol's arms go on top.</li>
 * <li>Keep hands still: no sway as you turn and no bob as you walk (your view still bobs).</li>
 * <li>Swing styles: the arm's pose through a swing, played in the sword's swing length (the game's swing progress), in
 * place of the game's turn; 1.7's is the game's swing without the dip after each hit.</li>
 * <li>Your arm drawn holding what you carry, flames licking up from the held item (three pixel frames, the effects'
 * flame), and the shield tinted by its cooldown.</li>
 * </ul>
 * While Punchy's own animations draw the hands (its own way, past the game's hand methods) the per-hand parts aren't
 * reached; keeping the hands still and the shield's tint still work. Render thread only; nothing allocated per frame.
 */
public final class FirstPersonHands {
    /** The swing styles, as the setting lists them. */
    public static final List<String> SWING_STYLES = List.of("vanilla", "v1_7", "exhibition", "sigma", "swong", "spin", "stab", "slide", "push");
    private static final int OLD = 1, EXHIBITION = 2, SIGMA = 3, SWONG = 4, SPIN = 5, STAB = 6, SLIDE = 7, PUSH = 8;

    /** For the test scenario: the main hand's swing held at this point (0..1), or -1. */
    public static float swingHeld = -1f;
    /** For the test scenario: swings drawn in a style, arms drawn with items, flames drawn, shields tinted. */
    public static int styledSwings, armsWithItems, flamesDrawn, shieldsTinted, stillFrames;

    /** Where the game holds an item in the right hand (first person): what a hand turns and sizes round. */
    private static final float HOLD_X = 0.56f, HOLD_Y = -0.52f, HOLD_Z = -0.72f;
    /**
     * The middle of the empty hand's fist as the game draws that arm (right; worked out from its arm transforms): drawn
     * holding an item, the arm is moved so this lands on the item's grip.
     */
    private static final float FIST_X = 0.704f, FIST_Y = -0.523f, FIST_Z = -1.062f;
    /**
     * Where a flat item's grip is from where the game holds it (its first-person model's turn, lift and size applied to
     * the bottom of the handle), and a block's (under it).
     */
    private static final float GRIP_X = 0.071f, GRIP_Y = -0.12f, GRIP_Z = -0.02f, BLOCK_GRIP_Y = -0.22f;


    private static final float[] VIEW = new float[7];
    /** Whether the game is drawing the first-person hands now (set as they start, cleared when they're done). */
    private static boolean inHands;
    /** The hands' pose as the game started them (after the bob): its up and right are the screen's, for the flames. */
    private static final Matrix4f BASE = new Matrix4f();

    /** The main hand's item and when it last changed (client ticks), for 1.7's swing: the dip stays for a new item. */
    private static net.minecraft.world.item.Item mainItem;
    private static int ticks, mainChanged = -100;

    private FirstPersonHands() {
    }

    // ------------------------------------------------------------------ the hands as a whole

    /** The game starts drawing the first-person hands with this pose (Sol's arms already in it). */
    public static void start(PoseStack pose) {
        inHands = true;
        BASE.set(pose.last().pose());
    }

    /** The hands are drawn. */
    public static void done() {
        inHands = false;
    }

    /** No sway as you turn and no bob as you walk. */
    public static boolean keepStill() {
        return PunchyModule.keepStill();
    }

    /** As the game starts the hands: whether to take their turn sway out now (counted for the test scenario). */
    public static boolean holdStill() {
        boolean still = PunchyModule.keepStill();
        if (still) {
            stillFrames++;
        }
        return still;
    }

    /** Every client tick: notes when the main hand's item changes (1.7's swing keeps the dip for that). */
    public static void tick(Minecraft mc) {
        ticks++;
        if (mc.player == null) {
            return;
        }
        var item = mc.player.getMainHandItem().getItem();
        if (item != mainItem) {
            mainItem = item;
            mainChanged = ticks;
        }
    }

    /**
     * The main hand's lowering ({@code equip}, 0 up to 1 down): with 1.7's swing it stays up after a hit (in 1.7 an
     * attack didn't lower the item while it recharged); a new item still comes up from below.
     */
    public static float mainEquip(float equip) {
        return PunchyModule.swingStyle() == OLD && ticks - mainChanged > 8 ? 0f : equip;
    }

    /** The main hand's swing (the test scenario can hold it at a point). */
    public static float mainSwing(float swing) {
        return swingHeld >= 0f ? swingHeld : swing;
    }

    // ------------------------------------------------------------------ view model

    /** Just after the game pushes a hand's pose: the view model ({@code right}: the hand is on the right). */
    public static void viewModel(PoseStack pose, boolean right, boolean main) {
        if (!PunchyModule.viewModel(main, VIEW)) {
            return;
        }
        float s = right ? 1f : -1f;
        // Moved (forward is away from you), then turned and sized round where the hand holds things.
        pose.translate(s * VIEW[0] + s * HOLD_X, VIEW[1] + HOLD_Y, -VIEW[2] + HOLD_Z);
        if (VIEW[3] != 0f) {
            pose.rotateAround(Axis.XP.rotationDegrees(VIEW[3]), 0f, 0f, 0f);
        }
        if (VIEW[4] != 0f) {
            pose.rotateAround(Axis.YP.rotationDegrees(s * VIEW[4]), 0f, 0f, 0f);
        }
        if (VIEW[5] != 0f) {
            pose.rotateAround(Axis.ZP.rotationDegrees(s * VIEW[5]), 0f, 0f, 0f);
        }
        if (VIEW[6] != 1f) {
            pose.scale(VIEW[6], VIEW[6], VIEW[6]);
        }
        pose.translate(-s * HOLD_X, -HOLD_Y, -HOLD_Z);
    }

    // ------------------------------------------------------------------ swing styles

    /**
     * At the game's swing turn (applyItemArmAttackTransform, right after its swing moved the item): the style's pose
     * instead, with that move taken back; false leaves the swing to the game.
     */
    public static boolean swing(PoseStack pose, HumanoidArm arm, float swing) {
        int style = PunchyModule.swingStyle();
        if (style <= OLD || swing <= 0f) {
            return false;
        }
        float o = arm == HumanoidArm.RIGHT ? 1f : -1f;
        float sq = (float) Math.sqrt(swing);
        // The game's swing move, taken back (before 1.21.11 the item's size from Held Items came after it).
        //? if >=1.21.11 {
        float k = 1f;
        //?} else {
        /*float k = Math.max(0.01f, HeldItems.size(arm));
        *///?}
        pose.translate(o * 0.4f * sin(sq * PI) / k, -0.2f * sin(sq * 2f * PI) / k, 0.2f * sin(swing * PI) / k);
        style(pose, style, o, swing);
        styledSwings++;
        return true;
    }

    private static final float PI = (float) Math.PI;

    private static float sin(float a) {
        return (float) Math.sin(a);
    }

    /**
     * A style's pose at swing point {@code p} (0..1), round where the hand holds the item; each is back where it started
     * by the end. {@code a} rises fast and settles slowly (a strike), {@code b} evenly, {@code c} late.
     */
    static void style(PoseStack pose, int style, float o, float p) {
        float a = sin((float) Math.sqrt(p) * PI), b = sin(p * PI), c = sin(p * p * PI);
        switch (style) {
            case EXHIBITION -> {
                // A short chop at the wrist, the blade tipping down and in, kept low.
                pose.translate(o * -0.06f * b, 0.04f * b, -0.04f * a);
                turn(pose, Axis.YP, o * -25f * b);
                turn(pose, Axis.XP, -55f * a);
                turn(pose, Axis.ZP, o * -10f * c);
            }
            case SIGMA -> {
                // A twisting jab: the item rolls over toward the middle as it pushes in.
                pose.translate(o * -0.14f * a, 0.06f * a, -0.18f * a);
                turn(pose, Axis.ZP, o * -35f * a);
                turn(pose, Axis.XP, -25f * c);
            }
            case SWONG -> {
                // A wide sweep across the screen and back.
                pose.translate(o * -0.28f * a, 0.08f * b, -0.06f * b);
                turn(pose, Axis.YP, o * -65f * a);
                turn(pose, Axis.XP, -15f * a);
            }
            case SPIN -> {
                // One full turn over, round the middle of the item, with a small push.
                float e = 1f - (1f - p) * (1f - p);
                pose.translate(0f, 0.06f * b + 0.18f, -0.1f * b);
                turn(pose, Axis.XP, -360f * e);
                pose.translate(0f, -0.18f, 0f);
            }
            case STAB -> {
                // A straight thrust, the tip pointing ahead.
                pose.translate(o * -0.05f * a, 0.03f * a, -0.42f * a);
                turn(pose, Axis.XP, -60f * a);
            }
            case SLIDE -> {
                // Slides in toward the middle and back, leaning.
                pose.translate(o * -0.42f * b, -0.04f * b, -0.04f * b);
                turn(pose, Axis.ZP, o * 18f * b);
            }
            case PUSH -> {
                // Shoved forward and up.
                pose.translate(0f, 0.1f * a, -0.3f * a);
                turn(pose, Axis.XP, -20f * a);
                turn(pose, Axis.YP, o * -10f * a);
            }
            default -> {
            }
        }
    }

    private static void turn(PoseStack pose, Axis axis, float degrees) {
        if (degrees != 0f) {
            pose.rotateAround(axis.rotationDegrees(degrees), 0f, 0f, 0f);
        }
    }

    // ------------------------------------------------------------------ shield status

    private static final int AMBER = 0xFFFFB040, RED = 0xFFFF4040;
    /** A shield, for its cooldown (made once, when first asked). */
    private static ItemStack shield;

    /** Before 1.21.4: whether the item the game is drawing now is a shield. */
    public static boolean shieldNow;

    /** The tint a shield's draw takes instead of {@code tint} (its own, kept when there's no status to show). */
    public static int shieldTint(int tint) {
        int s = shieldTint();
        return s == -1 ? tint : s;
    }

    /**
     * The colour a shield in your hands is drawn with: as it is (white) when it's ready, amber while it cools down, red
     * right after an axe disabled it (the first two thirds of its cooldown).
     */
    public static int shieldTint() {
        if (!inHands || !PunchyModule.shieldStatus()) {
            return -1;
        }
        var player = Minecraft.getInstance().player;
        if (player == null) {
            return -1;
        }
        //? if >=1.21.2 {
        if (shield == null) {
            shield = new ItemStack(Items.SHIELD);
        }
        float left = player.getCooldowns().getCooldownPercent(shield, 0f);
        //?} else {
        /*float left = player.getCooldowns().getCooldownPercent(Items.SHIELD, 0f);
        *///?}
        if (left <= 0f) {
            return -1;
        }
        shieldsTinted++;
        return left > 1f / 3f ? RED : AMBER;
    }

    // ------------------------------------------------------------------ the arm and the flames

    /**
     * Whether your arm is drawn holding {@code stack} in that hand: not over a map (it has its own arms) or behind a
     * shield (which keeps its place, so it doesn't cover the view).
     */
    public static boolean armWith(ItemStack stack) {
        if (!PunchyModule.armWithItems() || stack.isEmpty() || stack.is(Items.FILLED_MAP) || stack.is(Items.SHIELD)) {
            return false;
        }
        var player = Minecraft.getInstance().player;
        return player != null && !player.isInvisible();
    }

    /**
     * Just after the game pushes a hand's pose, with your arm shown holding the item: the item moved so its grip is where
     * the game shows an empty hand's fist (further off and a little smaller than the game holds it, as your arm is), so
     * the arm holding it stays on screen.
     */
    public static void holdInFist(PoseStack pose, boolean right, ItemStack stack) {
        float s = right ? 1f : -1f;
        boolean block = stack.getItem() instanceof BlockItem;
        pose.translate(s * (FIST_X - HOLD_X - (block ? 0f : GRIP_X)), FIST_Y - HOLD_Y - (block ? BLOCK_GRIP_Y : GRIP_Y),
                FIST_Z - HOLD_Z - (block ? 0f : GRIP_Z));
    }

    /**
     * Before the game's empty-hand arm is drawn at the item's pose: moves it so its fist is on the item's grip, so it
     * holds the item and follows its swing.
     */
    public static void armToItem(PoseStack pose, boolean right, ItemStack stack) {
        float s = right ? 1f : -1f;
        if (stack.getItem() instanceof BlockItem) {
            pose.translate(-s * FIST_X, BLOCK_GRIP_Y - FIST_Y, -FIST_Z);
        } else {
            pose.translate(s * (GRIP_X - FIST_X), GRIP_Y - FIST_Y, GRIP_Z - FIST_Z);
        }
        armsWithItems++;
    }

    /** Flame emitters per item: x, y in the item's picture (0..1, y up), or for a block the corners of its top. */
    private static final Map<net.minecraft.world.item.Item, float[]> EMITTERS = new HashMap<>();
    private static final int MAX_FLAMES = 12;
    /** The two hands' flames (x, y, z, u, v, colour per vertex, in the hands' space), handed to the game. */
    private static final Flames[] FLAMES = {new Flames(), new Flames()};
    private static final int[] RAMP = new int[Ramp.TONES];
    private static final Vector4f V = new Vector4f();
    private static final Matrix4f M = new Matrix4f();
    private static RenderType flameType;
    private static Object flameTypeFor;

    /** One hand's flame quads: positions already in the hands' space, drawn with no further pose. */
    private static final class Flames {
        final float[] v = new float[MAX_FLAMES * 3 * 4 * 5];
        final int[] c = new int[MAX_FLAMES * 3 * 4];
        int count;
        float nx, ny, nz;

        void emit(VertexConsumer consumer) {
            for (int i = 0; i < count; i++) {
                MeshEmitter.vertex(consumer, false, v[i * 5], v[i * 5 + 1], v[i * 5 + 2], c[i], v[i * 5 + 3], v[i * 5 + 4],
                        0xF000F0, nx, ny, nz);
            }
        }
    }

    //? if >=1.21.9 {
    private static final SubmitNodeCollector.CustomGeometryRenderer[] DRAW = {
            (pose, consumer) -> FLAMES[0].emit(consumer), (pose, consumer) -> FLAMES[1].emit(consumer)};
    //?}

    /**
     * As the game finishes a hand (its pose still the item's): flames licking up from what it holds, rising straight up
     * the screen whichever way the item is turned.
     */
    //? if >=1.21.9 {
    public static void flames(PoseStack pose, SubmitNodeCollector buffers, ItemStack stack, boolean right, boolean main) {
    //?} else {
    /*public static void flames(PoseStack pose, MultiBufferSource buffers, ItemStack stack, boolean right, boolean main) {
    *///?}
        if (!PunchyModule.flamingItem() || stack.isEmpty() || !inHands) {
            return;
        }
        // The atlas first: the sprites learn where they are in it when it's packed.
        var tex = WorldOut.texture(FxSprites.atlas());
        Flames f = FLAMES[main ? 0 : 1];
        if (!build(f, pose.last().pose(), stack, right)) {
            return;
        }
        if (flameType == null || flameTypeFor != tex.id) {
            flameTypeFor = tex.id;
            //? if >=1.21.11 {
            flameType = RenderTypes.entityTranslucentEmissive(tex.id);
            //?} else {
            /*flameType = RenderType.entityTranslucentEmissive(tex.id);
            *///?}
        }
        pose.pushPose();
        pose.last().pose().identity();
        pose.last().normal().identity();
        //? if >=1.21.9 {
        buffers.submitCustomGeometry(pose, flameType, DRAW[main ? 0 : 1]);
        //?} else {
        /*f.emit(buffers.getBuffer(flameType));
        *///?}
        pose.popPose();
        flamesDrawn++;
    }

    /** The flame quads for one hand into {@code f}; false when there are none. */
    private static boolean build(Flames f, Matrix4f arm, ItemStack stack, boolean right) {
        float[] at = EMITTERS.computeIfAbsent(stack.getItem(), FirstPersonHands::emitters);
        boolean block = stack.getItem() instanceof BlockItem;
        float s = right ? 1f : -1f;
        // The item's place in the hand as the game's item models have it in first person: a flat item turned edge-on
        // and tipped back, a block turned 45 degrees and small (the left hand mirrored).
        M.set(arm);
        if (block) {
            M.rotateY(s * 45f * PI / 180f).scale(0.4f).translate(-0.5f, -0.5f, -0.5f);
        } else {
            M.translate(s * 1.13f / 16f, 3.2f / 16f, 1.13f / 16f)
                    .rotateXYZ(0f, s * -90f * PI / 180f, s * 25f * PI / 180f).scale(0.68f).translate(-0.5f, -0.5f, -0.5f);
        }
        // The screen's up and right in the hands' space (the hands' pose as they started, bob and all).
        float ux = BASE.m10(), uy = BASE.m11(), uz = BASE.m12();
        float rx = BASE.m00(), ry = BASE.m01(), rz = BASE.m02();
        float ul = (float) Math.sqrt(ux * ux + uy * uy + uz * uz), rl = (float) Math.sqrt(rx * rx + ry * ry + rz * rz);
        if (ul < 1e-5f || rl < 1e-5f) {
            return false;
        }
        ux /= ul;
        uy /= ul;
        uz /= ul;
        rx /= rl;
        ry /= rl;
        rz /= rl;
        float nx = BASE.m20(), ny = BASE.m21(), nz = BASE.m22();
        float nl = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (nl < 1e-5f) {
            return false;
        }
        nx /= nl;
        ny /= nl;
        nz /= nl;
        f.nx = nx;
        f.ny = ny;
        f.nz = nz;
        Ramp.of(PunchyModule.flameColour(), RAMP);
        float size = PunchyModule.flameSize();
        float t = (System.nanoTime() % 1_000_000_000_000L) / 1e9f;
        int n = 0;
        for (int i = 0; i < at.length / 2; i++) {
            float px = at[i * 2], py = at[i * 2 + 1];
            if (block) {
                V.set(px, 1f, py, 1f);
            } else {
                V.set(px, py, 0.5f, 1f);
            }
            M.transform(V);
            // Each flame rises and shrinks over 0.45 s from its own start, flickering through the three frames, and
            // fades in three steps.
            float phase = hash(i);
            float age = (t / 0.45f + phase) % 1f;
            float k = Ramp.steps(1f - age, 3, true);
            float texel = 0.02f * size * (1f - 0.4f * age);
            FxSprites.Sprite sprite = FxSprites.FLAME[(int) (t * 9f + phase * 3f) % 3];
            float w = sprite.w * texel, h = sprite.h * texel, rise = 0.09f * size * age;
            // A little toward you, so the item they lick up from doesn't hide them.
            float bx = V.x + ux * rise - rx * w / 2f + nx * 0.08f, by = V.y + uy * rise - ry * w / 2f + ny * 0.08f,
                    bz = V.z + uz * rise - rz * w / 2f + nz * 0.08f;
            for (int tone = 0; tone < 3; tone++) {
                float[] uv = sprite.uv[tone];
                if (uv == null) {
                    continue;
                }
                int argb = Colors.withAlpha(RAMP[tone], k);
                n = quad(f, n, bx, by, bz, rx * w, ry * w, rz * w, ux * h, uy * h, uz * h, uv, argb);
            }
        }
        f.count = n;
        return n > 0;
    }

    /** A quad from its bottom-left corner along {@code r} (right) and {@code u} (up), its picture the right way up. */
    private static int quad(Flames f, int n, float x, float y, float z, float rx, float ry, float rz, float ux, float uy,
                            float uz, float[] uv, int argb) {
        n = put(f, n, x, y, z, uv[0], uv[3], argb);
        n = put(f, n, x + rx, y + ry, z + rz, uv[2], uv[3], argb);
        n = put(f, n, x + rx + ux, y + ry + uy, z + rz + uz, uv[2], uv[1], argb);
        return put(f, n, x + ux, y + uy, z + uz, uv[0], uv[1], argb);
    }

    private static int put(Flames f, int n, float x, float y, float z, float u, float v, int argb) {
        int o = n * 5;
        f.v[o] = x;
        f.v[o + 1] = y;
        f.v[o + 2] = z;
        f.v[o + 3] = u;
        f.v[o + 4] = v;
        f.c[n] = argb;
        return n + 1;
    }

    private static float hash(int i) {
        float x = (float) Math.sin(i * 12.9898f + 4.1f) * 43758.547f;
        return x - (float) Math.floor(x);
    }

    /**
     * Where an item's flames start: for a block the corners and edges of its top; for an item, its picture's outline
     * (opaque pixels next to clear ones), up to twelve spread along it; or a few round the middle when it has no picture.
     */
    static float[] emitters(net.minecraft.world.item.Item item) {
        if (item instanceof BlockItem) {
            return new float[]{0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f, 0.5f, 0f, 1f, 0.5f, 0.5f, 1f, 0f, 0.5f};
        }
        String id = String.valueOf(BuiltInRegistries.ITEM.getKey(item));
        int colon = id.indexOf(':');
        String ns = colon < 0 ? "minecraft" : id.substring(0, colon), path = colon < 0 ? id : id.substring(colon + 1);
        SoftRaster.Texture px = null;
        try {
            px = TilePictures.read(ns + ":textures/item/" + path + ".png");
        } catch (RuntimeException e) {
            // No picture: the fallback below.
        }
        if (px == null || px.width() <= 0 || px.height() <= 0) {
            return new float[]{0.4f, 0.55f, 0.5f, 0.7f, 0.6f, 0.55f, 0.5f, 0.4f};
        }
        // One frame of an animated picture (frames are stacked downwards).
        return outline(px.argb(), px.width(), Math.min(px.width(), px.height()));
    }

    /** Up to {@link #MAX_FLAMES} outline pixels of a picture, evenly spread, as x, y (0..1, y up). */
    static float[] outline(int[] argb, int w, int h) {
        int[] edge = new int[w * h];
        int count = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!solid(argb, w, h, x, y)) {
                    continue;
                }
                if (!solid(argb, w, h, x - 1, y) || !solid(argb, w, h, x + 1, y) || !solid(argb, w, h, x, y - 1)
                        || !solid(argb, w, h, x, y + 1)) {
                    edge[count++] = y * w + x;
                }
            }
        }
        if (count == 0) {
            return new float[]{0.5f, 0.5f};
        }
        int n = Math.min(MAX_FLAMES, count);
        float[] out = new float[n * 2];
        for (int i = 0; i < n; i++) {
            int e = edge[(int) ((i + 0.5f) * count / n)];
            out[i * 2] = (e % w + 0.5f) / w;
            out[i * 2 + 1] = 1f - (e / w + 0.5f) / h;
        }
        return out;
    }

    private static boolean solid(int[] argb, int w, int h, int x, int y) {
        return x >= 0 && y >= 0 && x < w && y < h && (argb[y * w + x] >>> 24) >= 128;
    }
}
