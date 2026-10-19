package io.github.georgwav.cosmetics.melee;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.georgwav.cosmetics.client.modules.PunchyModule;
import io.github.georgwav.cosmetics.model.BakedCosmetic;
import io.github.georgwav.cosmetics.model.CosmeticSlot;
import io.github.georgwav.cosmetics.render.CosmeticRenderer;
import io.github.georgwav.cosmetics.render.RenderGuard;
import io.github.georgwav.cosmetics.render.RenderStats;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;
//? if >=1.21.11 {
import net.minecraft.client.renderer.rendertype.RenderType;
//?} else {
/*import net.minecraft.client.renderer.RenderType;
*///?}
//? if >=1.21.9 {
import net.minecraft.client.renderer.SubmitNodeCollector;
//?} else {
/*import net.minecraft.client.renderer.MultiBufferSource;
*///?}

/**
 * Knives in third person (when the Knives setting is on): for you in F5, and for others you've given a knife here
 * (cosmetics aren't sent to other players), the sword in the main hand is drawn as the knife. It is posed as its
 * {@code static_idle} with no arms, slash effects or markers, at the hand as the game holds items: the arm's pose,
 * the game's turn into the hand, then the display's {@code thirdperson_righthand} transform (for the left hand its
 * {@code thirdperson_lefthand}, or the right one mirrored as the game mirrors item transforms), with the model's
 * {@code thirdperson_hand} bone in the fist. Drawn from the cosmetics layer; the game's own item for that hand is left
 * out ({@link #hides}). Render thread only; nothing allocated per frame.
 */
public final class KnifeThirdPerson {
    private static final Quaternionf TURN = new Quaternionf();
    private static final float DEG = (float) (Math.PI / 180.0);
    /** Third-person knives drawn since the game started (read by the in-game test). */
    public static int drawn;

    private KnifeThirdPerson() {
    }

    /** The knife a player holds in third person, or null. */
    public static Knife knifeOf(Entity entity) {
        if (!(entity instanceof AbstractClientPlayer p) || !PunchyModule.knifeInThirdPerson()) {
            return null;
        }
        ItemStack s = p.getMainHandItem();
        if (s.isEmpty() || !(s.is(ItemTags.SWORDS) || PunchyModule.knifeOnAxes() && s.is(ItemTags.AXES))) {
            return null;
        }
        boolean local = p == Minecraft.getInstance().player;
        if (!CosmeticRenderer.showsFor(p.getUUID(), local)) {
            return null;
        }
        BakedCosmetic c = CosmeticRenderer.heldFor(p.getUUID(), local, CosmeticSlot.MELEE);
        Knife k = c == null ? null : KnifeRegistry.get(c.id());
        return k == null || RenderGuard.isDisabled(k.id) ? null : k;
    }

    /** Whether the game's own held item is left out: a knife-holding player's main-hand item, in third person. */
    public static boolean hides(Object owner, ItemStack stack, ItemDisplayContext context) {
        if (context != ItemDisplayContext.THIRD_PERSON_RIGHT_HAND && context != ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                || !(owner instanceof AbstractClientPlayer p)) {
            return false;
        }
        // The hook is reached (the knife or, as it is by default, the sword drawn after it).
        io.github.georgwav.cosmetics.sol.Hooks.KNIFE_THIRD.hit();
        boolean left = context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
        if (left != (p.getMainArm() == HumanoidArm.LEFT)) {
            return false;
        }
        ItemStack main = p.getMainHandItem();
        if ((stack == main || ItemStack.matches(stack, main)) && knifeOf(p) != null) {
            return true;
        }
        return false;
    }

    /**
     * Draws the knife in {@code entity}'s main hand, from the player's cosmetics layer ({@code limbs}: left arm,
     * right arm, left leg, right leg, as the layer has them).
     */
    //? if >=1.21.9 {
    public static void draw(PoseStack pose, SubmitNodeCollector buffers, int light, Entity entity, ModelPart[] limbs) {
    //?} else {
    /*public static void draw(PoseStack pose, MultiBufferSource buffers, int light, Entity entity, ModelPart[] limbs) {
    *///?}
        Knife k = limbs == null ? null : knifeOf(entity);
        if (k == null) {
            return;
        }
        boolean left = ((AbstractClientPlayer) entity).getMainArm() == HumanoidArm.LEFT;
        ModelPart arm = limbs[left ? 0 : 1];
        if (arm == null) {
            return;
        }
        long start = RenderStats.TIMING ? System.nanoTime() : 0L;
        try {
            KnifePose kp = k.thirdPose();
            pose.pushPose();
            // As the game's held item layer does: into the hand, turned to hold, a pixel in and down.
            arm.translateAndRotate(pose);
            CosmeticRenderer.rotate(pose, TURN.rotationX(-90f * DEG));
            CosmeticRenderer.rotate(pose, TURN.rotationY(180f * DEG));
            pose.translate((left ? -1f : 1f) / 16f, 0.125f, -0.625f);
            float[] t = left && k.spec.thirdLeft() != null ? k.spec.thirdLeft() : k.spec.thirdRight();
            boolean mirror = left && k.spec.thirdLeft() == null;
            if (t != null) {
                float side = mirror ? -1f : 1f;
                pose.translate(side * t[3] / 16f, t[4] / 16f, t[5] / 16f);
                CosmeticRenderer.rotate(pose, TURN.rotationXYZ(t[0] * DEG, side * t[1] * DEG, side * t[2] * DEG));
                pose.scale(t[6], t[7], t[8]);
            }
            pose.scale(1f / 16f, 1f / 16f, 1f / 16f);
            int hand = kp.geo.bone("thirdperson_hand");
            if (hand >= 0) {
                int w = hand * 12;
                pose.translate(-kp.world[w + 3], -kp.world[w + 7], -kp.world[w + 11]);
            }
            RenderType type = KnifeRenderer.knifeType(k);
            //? if >=1.21.9 {
            buffers.submitCustomGeometry(pose, type, pass(k, light, KnifeRenderer.NORMAL));
            buffers.submitCustomGeometry(pose, type, pass(k, light, KnifeRenderer.LIT));
            //?} else {
            /*KnifeRenderer.emitPass(pose.last(), buffers.getBuffer(type), KnifeRenderer.NORMAL, kp, k.solid, light, true, null);
            KnifeRenderer.emitPass(pose.last(), buffers.getBuffer(type), KnifeRenderer.LIT, kp, k.solid, light, true, null);
            *///?}
            pose.popPose();
            drawn++;
        } catch (RuntimeException | LinkageError e) {
            RenderGuard.cosmeticFailed(k.id, e);
        }
        if (RenderStats.TIMING) {
            RenderStats.nanos += System.nanoTime() - start;
        }
    }

    //? if >=1.21.9 {
    /**
     * The knives' vertices are written after this frame's players are handed over, so each hand-over keeps its own
     * knife and light: a ring of reusable passes (more knife-holding players in one frame than it holds is unlikely).
     */
    private static final Pass[] PASSES = new Pass[128];
    private static int next;

    static {
        for (int i = 0; i < PASSES.length; i++) {
            PASSES[i] = new Pass();
        }
    }

    private static Pass pass(Knife k, int light, int which) {
        Pass p = PASSES[next];
        next = (next + 1) % PASSES.length;
        p.knife = k;
        p.light = light;
        p.which = which;
        return p;
    }

    private static final class Pass implements SubmitNodeCollector.CustomGeometryRenderer {
        Knife knife;
        int light, which;

        @Override
        public void render(PoseStack.Pose pose, com.mojang.blaze3d.vertex.VertexConsumer consumer) {
            Knife k = knife;
            if (k == null || RenderGuard.isDisabled(k.id)) {
                return;
            }
            try {
                KnifeRenderer.emitPass(pose, consumer, which, k.thirdPose(), k.solid, light, true, null);
            } catch (RuntimeException | LinkageError e) {
                RenderGuard.cosmeticFailed(k.id, e);
            }
        }
    }
    //?}
}
