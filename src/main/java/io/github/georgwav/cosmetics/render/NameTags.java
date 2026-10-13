package io.github.georgwav.cosmetics.render;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.georgwav.cosmetics.config.CosmeticsConfig;
import io.github.georgwav.cosmetics.model.BakedCosmetic;
import io.github.georgwav.cosmetics.model.CosmeticSlot;
import io.github.georgwav.cosmetics.model.PetPosition;
import io.github.georgwav.cosmetics.source.EquippedCosmetics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;

/**
 * Raises a player's name tag above their hats, halos and other head cosmetics, so the name sits just over them
 * instead of inside them. {@code PlayerRendererMixin} calls {@link #begin} before the player renderer draws the
 * name and {@link #end} after. Render thread only.
 */
public final class NameTags {
    /** The player model is drawn at 15/16 of its size. */
    private static final float MODEL_SCALE = 0.9375f;
    /** The head's top is 8 px above the neck, the head cosmetics' anchor. */
    private static final float HEAD_HEIGHT = 0.5f;
    /** Room vanilla leaves between the top of the head and the bottom of the name, in blocks. */
    private static final float VANILLA_GAP = 0.2f;
    /** Room left between a cosmetic and the name (also covers a little bobbing), in blocks. */
    private static final float MARGIN = 0.08f;

    private static boolean raised;

    private NameTags() {
    }

    /** Before the name of {@code entity} is drawn. */
    public static void begin(Entity entity, PoseStack poseStack) {
        raised = false;
        float lift = entity instanceof AbstractClientPlayer player ? lift(player) : 0f;
        if (lift > 0f) {
            poseStack.pushPose();
            poseStack.translate(0f, lift, 0f);
            raised = true;
        }
    }

    /** Like {@link #begin(Entity, PoseStack)} for a render state's entity id. */
    public static void begin(int entityId, PoseStack poseStack) {
        begin(CosmeticsLayer.entity(entityId), poseStack);
    }

    /** After it. */
    public static void end(PoseStack poseStack) {
        if (raised) {
            poseStack.popPose();
            raised = false;
        }
    }

    /** How far to raise the player's name, in blocks: 0 unless a head cosmetic reaches into it. */
    public static float lift(AbstractClientPlayer player) {
        CosmeticsConfig config = CosmeticsConfig.get();
        boolean local = player == Minecraft.getInstance().player;
        if (!config.enabled || player.isInvisible()
                || (local ? !config.showOwnCosmetics : !config.showOtherPlayersCosmetics)) {
            return 0f;
        }
        boolean helmet = !player.getItemBySlot(EquipmentSlot.HEAD).isEmpty();
        if (helmet && config.hideHeadwearWithHelmet) {
            return 0f;
        }
        float above = 0f; // blocks above the top of the head
        for (BakedCosmetic cosmetic : EquippedCosmetics.forPlayer(player.getUUID(), local)) {
            CosmeticSlot slot = cosmetic.slot();
            CosmeticsConfig.CosmeticOptions options = config.options(cosmetic.id());
            // Pets on the head stand on its top, 8 px above the anchor of the other head cosmetics.
            boolean headPet = slot == CosmeticSlot.PET
                    && CosmeticRenderer.petPosition(cosmetic, options) == PetPosition.HEAD;
            if ((slot.anchor() != CosmeticSlot.Anchor.HEAD && !headPet) || !config.slotVisible(slot)
                    || RenderGuard.isDisabled(cosmetic.id())) {
                continue;
            }
            float reach = cosmetic.reachUp() * options.scale - options.offsetY / 16f + (headPet ? HEAD_HEIGHT : 0f);
            above = Math.max(above, reach - HEAD_HEIGHT);
        }
        return Math.max(0f, above * MODEL_SCALE * player.getScale() - VANILLA_GAP + MARGIN);
    }
}
