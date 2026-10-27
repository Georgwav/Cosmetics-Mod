package io.github.georgwav.cosmetics.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.georgwav.cosmetics.CosmeticsMod;
import io.github.georgwav.cosmetics.emote.EmotePlayer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.List;

/**
 * Elbows and knees for emotes ({@link EmotePlayer#bends}). The game's player has straight arms and legs, so while an
 * emote bends one, the game's model skips that limb's own cubes (and its sleeve or trouser layer) and they're drawn
 * here instead, right after the rest of the model, into the same buffer with the same light, overlay and colour: the
 * upper half as the game has it, the lower half turned about the middle, and a 2-pixel joint between them whose middle
 * turns half way. Each side is cut across where it bends, its texture with it, so the skin's own pixels bend.
 *
 * <p>The player model hooks call {@link #hide} right after the model is posed: it first gives back whatever it hid
 * before, so the next player drawn with the same shared model is whole. The model hooks call {@link #draw} at the end
 * of the model's own drawing, and the held item follows the forearm ({@link #toHand}). The first-person hand is drawn
 * straight ({@link #restore} before it).
 *
 * <p>Render thread only.
 */
public final class BentLimbs {
    /** Whether a limb is bent now: the one check the model hooks make for every model drawn. */
    public static boolean active;
    /** Times bent limbs were drawn (read by the in-game check). */
    public static long drawn;
    private static boolean broken;
    private static Object model;
    // Left arm, right arm, left leg, right leg (EmotePlayer.bends' order).
    private static final ModelPart[] LIMB = new ModelPart[4];
    private static final ModelPart[] LAYER = new ModelPart[4];
    /** Each limb's turn in quarters: none, a quarter, half, three quarters, all of it. */
    private static final Quaternionf[][] TURN = new Quaternionf[4][5];
    /** Half the joint's length, in pixels. */
    private static final float JOINT = 1f;
    /** Which quarter turn each cut is at (top, joint start, middle, joint end, bottom), and each piece's normal. */
    private static final int[] TURN_AT = {0, 0, 2, 4, 4};
    private static final int[] PIECE_TURN = {0, 1, 3, 4};
    private static final float[] LEVEL = new float[5];
    private static final int[] PARTNER = new int[4];
    private static final Vector3f P = new Vector3f();
    private static final Vector3f N = new Vector3f();
    private static final Vector3f OUT = new Vector3f();

    static {
        for (Quaternionf[] turn : TURN) {
            for (int k = 0; k < turn.length; k++) {
                turn[k] = new Quaternionf();
            }
        }
    }

    private BentLimbs() {
    }

    /**
     * Right after {@code model} was posed for the player {@code entityId}: gives back what was hidden before, then hides
     * the limbs (and their layers) that bend in its emote now, for {@link #draw} to draw bent.
     */
    public static void hide(int entityId, HumanoidModel<?> model, ModelPart leftSleeve, ModelPart rightSleeve,
                            ModelPart leftPants, ModelPart rightPants) {
        restore();
        float[][] bends = broken ? null : EmotePlayer.bends(entityId);
        if (bends == null) {
            return;
        }
        hide(0, bends[0], model.leftArm, leftSleeve);
        hide(1, bends[1], model.rightArm, rightSleeve);
        hide(2, bends[2], model.leftLeg, leftPants);
        hide(3, bends[3], model.rightLeg, rightPants);
        if (active) {
            BentLimbs.model = model;
        }
    }

    private static void hide(int i, float[] bend, ModelPart limb, ModelPart layer) {
        // A limb hidden already (by the game or another mod) stays as it is.
        if (bend == null || limb == null || !limb.visible || limb.skipDraw || cubes(limb).isEmpty()) {
            return;
        }
        Quaternionf[] turn = TURN[i];
        turn[0].identity();
        turn[4].rotationZYX(bend[2], bend[1], bend[0]);
        for (int k = 1; k < 4; k++) {
            turn[k].identity().slerp(turn[4], k / 4f);
        }
        LIMB[i] = limb;
        limb.skipDraw = true;
        if (layer != null && layer.visible && !layer.skipDraw) {
            LAYER[i] = layer;
            layer.skipDraw = true;
        }
        active = true;
    }

    /** Gives every hidden limb back to the game's drawing. */
    public static void restore() {
        if (!active) {
            return;
        }
        active = false;
        model = null;
        for (int i = 0; i < LIMB.length; i++) {
            if (LIMB[i] != null) {
                LIMB[i].skipDraw = false;
                LIMB[i] = null;
            }
            if (LAYER[i] != null) {
                LAYER[i].skipDraw = false;
                LAYER[i] = null;
            }
        }
    }

    /**
     * At the end of a model's drawing (into {@code consumer}, from the model's own frame in {@code poseStack}): draws
     * its bent limbs if it is the model {@link #hide} hid them in. {@code root} is the model's root part (from
     * 1.21.2), null where the limbs are drawn from the model's frame directly.
     */
    public static void draw(Object model, ModelPart root, PoseStack poseStack, VertexConsumer consumer, int light,
                            int overlay, int color) {
        if (model != BentLimbs.model) {
            return;
        }
        try {
            for (int i = 0; i < LIMB.length; i++) {
                ModelPart limb = LIMB[i];
                if (limb == null) {
                    continue;
                }
                poseStack.pushPose();
                if (root != null) {
                    root.translateAndRotate(poseStack);
                }
                limb.translateAndRotate(poseStack);
                emit(cubes(limb), TURN[i], poseStack.last(), consumer, light, overlay, color);
                ModelPart layer = LAYER[i];
                if (layer != null) {
                    //? if >=1.21.2 {
                    // The sleeve or trouser layer is the limb's child.
                    layer.translateAndRotate(poseStack);
                    //?} else {
                    /*// The sleeve or trouser layer is a part of its own, posed as the limb.
                    poseStack.popPose();
                    poseStack.pushPose();
                    layer.translateAndRotate(poseStack);
                    *///?}
                    emit(cubes(layer), TURN[i], poseStack.last(), consumer, light, overlay, color);
                }
                poseStack.popPose();
            }
            drawn++;
        } catch (RuntimeException | LinkageError e) {
            // Straight limbs from now on rather than none.
            broken = true;
            restore();
            CosmeticsMod.LOGGER.error("Bent arms and legs for emotes failed and are off for this session", e);
        }
    }

    /**
     * After the game moved {@code poseStack} to the arm of {@code model} that holds an item: on along the forearm, so
     * the item stays in a bent arm's hand.
     */
    public static void toHand(Object model, boolean right, PoseStack poseStack) {
        if (!active || model != BentLimbs.model) {
            return;
        }
        int i = right ? 1 : 0;
        ModelPart limb = LIMB[i];
        if (limb == null) {
            return;
        }
        ModelPart.Cube cube = cubes(limb).get(0);
        float cx = (cube.minX + cube.maxX) / 32f;
        float cy = (cube.minY + cube.maxY) / 32f;
        float cz = (cube.minZ + cube.maxZ) / 32f;
        poseStack.translate(cx, cy, cz);
        PoseStack.Pose last = poseStack.last();
        last.pose().rotate(TURN[i][4]);
        last.normal().rotate(TURN[i][4]);
        poseStack.translate(-cx, -cy, -cz);
    }

    /** One limb's cubes, bent about the middle of each by {@code turn} (quarter steps). */
    private static void emit(List<ModelPart.Cube> cubes, Quaternionf[] turn, PoseStack.Pose pose, VertexConsumer consumer,
                             int light, int overlay, int color) {
        for (ModelPart.Cube cube : cubes) {
            float top = cube.minY;
            float bottom = cube.maxY;
            float mid = (top + bottom) / 2f;
            float joint = Math.min(JOINT, (bottom - top) / 4f);
            float cx = (cube.minX + cube.maxX) / 2f;
            float cz = (cube.minZ + cube.maxZ) / 2f;
            LEVEL[0] = top;
            LEVEL[1] = mid - joint;
            LEVEL[2] = mid;
            LEVEL[3] = mid + joint;
            LEVEL[4] = bottom;
            for (ModelPart.Polygon polygon : polygons(cube)) {
                ModelPart.Vertex[] v = vertices(polygon);
                Vector3fc normal = normal(polygon);
                if (v.length != 4) {
                    continue;
                }
                if (!side(v)) {
                    // The shoulder or hip end as it is; the hand or foot end turned all the way.
                    Quaternionf q = turn[y(v[0]) < mid ? 0 : 4];
                    for (ModelPart.Vertex corner : v) {
                        put(pose, consumer, x(corner), y(corner), z(corner), u(corner), v(corner), q, q, normal, cx,
                                mid, cz, light, overlay, color);
                    }
                    continue;
                }
                // A side: two corners at the top, two at the bottom. Cut across at each level, every corner sliding
                // along its edge (with its texture).
                for (int s = 0; s < 4; s++) {
                    if (LEVEL[s + 1] - LEVEL[s] < 1e-4f) {
                        continue;
                    }
                    Quaternionf nq = turn[PIECE_TURN[s]];
                    for (int k = 0; k < 4; k++) {
                        ModelPart.Vertex corner = v[k];
                        ModelPart.Vertex other = v[PARTNER[k]];
                        int at = y(corner) < mid ? s : s + 1;
                        float level = LEVEL[at];
                        float t = (level - y(corner)) / (y(other) - y(corner));
                        put(pose, consumer, x(corner), level, z(corner), u(corner) + (u(other) - u(corner)) * t,
                                v(corner) + (v(other) - v(corner)) * t, turn[TURN_AT[at]], nq, normal, cx, mid, cz,
                                light, overlay, color);
                    }
                }
            }
        }
    }

    /** Whether {@code v} is a side running from top to bottom; fills {@link #PARTNER} with each corner's other end. */
    private static boolean side(ModelPart.Vertex[] v) {
        for (int k = 0; k < 4; k++) {
            PARTNER[k] = -1;
            for (int j = 0; j < 4; j++) {
                if (j != k && Math.abs(y(v[j]) - y(v[k])) > 1e-4f && Math.abs(x(v[j]) - x(v[k])) < 1e-4f
                        && Math.abs(z(v[j]) - z(v[k])) < 1e-4f) {
                    PARTNER[k] = j;
                }
            }
            if (PARTNER[k] < 0) {
                return false;
            }
        }
        return true;
    }

    private static void put(PoseStack.Pose pose, VertexConsumer consumer, float x, float y, float z, float u, float v,
                            Quaternionf q, Quaternionf nq, Vector3fc normal, float cx, float cy, float cz, int light,
                            int overlay, int color) {
        q.transform(P.set(x - cx, y - cy, z - cz)).add(cx, cy, cz);
        pose.pose().transformPosition(P.x / 16f, P.y / 16f, P.z / 16f, OUT);
        pose.transformNormal(nq.transform(N.set(normal)).x, N.y, N.z, N);
        consumer.addVertex(OUT.x, OUT.y, OUT.z, color, u, v, overlay, light, N.x, N.y, N.z);
    }

    private static List<ModelPart.Cube> cubes(ModelPart part) {
        return ((io.github.georgwav.cosmetics.mixin.ModelPartAccess) (Object) part).cosmeticsmod$cubes();
    }

    // The model's faces: records with x() from 1.21.9, pos() from 1.21.2, fields before (and the cube's faces private).
    //? if >=1.21.9 {
    private static float x(ModelPart.Vertex v) {
        return v.x();
    }

    private static float y(ModelPart.Vertex v) {
        return v.y();
    }

    private static float z(ModelPart.Vertex v) {
        return v.z();
    }
    //?} elif >=1.21.2 {
    /*private static float x(ModelPart.Vertex v) {
        return v.pos().x();
    }

    private static float y(ModelPart.Vertex v) {
        return v.pos().y();
    }

    private static float z(ModelPart.Vertex v) {
        return v.pos().z();
    }
    *///?} else {
    /*private static float x(ModelPart.Vertex v) {
        return v.pos.x();
    }

    private static float y(ModelPart.Vertex v) {
        return v.pos.y();
    }

    private static float z(ModelPart.Vertex v) {
        return v.pos.z();
    }
    *///?}

    //? if >=1.21.2 {
    private static float u(ModelPart.Vertex v) {
        return v.u();
    }

    private static float v(ModelPart.Vertex v) {
        return v.v();
    }

    private static ModelPart.Polygon[] polygons(ModelPart.Cube cube) {
        return cube.polygons;
    }

    private static ModelPart.Vertex[] vertices(ModelPart.Polygon polygon) {
        return polygon.vertices();
    }

    private static Vector3fc normal(ModelPart.Polygon polygon) {
        return polygon.normal();
    }
    //?} else {
    /*private static float u(ModelPart.Vertex v) {
        return v.u;
    }

    private static float v(ModelPart.Vertex v) {
        return v.v;
    }

    private static ModelPart.Polygon[] polygons(ModelPart.Cube cube) {
        return ((io.github.georgwav.cosmetics.mixin.CubeAccess) cube).cosmeticsmod$polygons();
    }

    private static ModelPart.Vertex[] vertices(ModelPart.Polygon polygon) {
        return polygon.vertices;
    }

    private static Vector3fc normal(ModelPart.Polygon polygon) {
        return polygon.normal;
    }
    *///?}
}
