package io.github.georgwav.cosmetics.render;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.georgwav.cosmetics.model.BakedCosmetic;
import io.github.georgwav.cosmetics.model.CosmeticRegistry;
import io.github.georgwav.cosmetics.model.CosmeticSlot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.item.ItemDisplayContext;
import java.util.UUID;
//? if >=1.21.4 {
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import java.util.Set;
//?}
//? if >=1.21.9 {
import net.minecraft.client.renderer.SubmitNodeCollector;
//?} else {
/*import net.minecraft.client.renderer.MultiBufferSource;
*///?}

/**
 * Held cosmetics: a shield cosmetic is drawn instead of the vanilla shield its player holds, in first and
 * third person. The mixins in {@code io.github.georgwav.cosmetics.mixin} call in here:
 *
 * <ol>
 *   <li>While the game works out how an item in a player's hand looks, {@link #enter} remembers whose hand it
 *       is ({@link #exit} forgets it again).</li>
 *   <li>From 1.21.4 the shield's renderer reads the item's components then; {@link #tag} wraps them with the
 *       shield cosmetic that replaces the shield, so the choice travels with the item's render state to
 *       wherever it is drawn. Before 1.21.4 the shield is drawn right away, so {@link #shield} is asked
 *       directly.</li>
 *   <li>When the shield is drawn, {@code draw...} draws the cosmetic instead and the vanilla shield is
 *       skipped.</li>
 * </ol>
 *
 * Shields in the inventory, in item frames and on the ground stay vanilla. Render thread only.
 */
public final class HeldItems {
    private static UUID holder;
    private static boolean holderLocal;

    private HeldItems() {
    }

    /** The game starts working out how {@code owner}'s item looks; only hand items of players count. */
    public static void enter(Object owner, ItemDisplayContext context) {
        holder = null;
        if (owner instanceof AbstractClientPlayer player && isHand(context)) {
            holder = player.getUUID();
            holderLocal = player == Minecraft.getInstance().player;
        }
    }

    /** Like {@link #enter} for a render state's entity id (1.21.2 and 1.21.3 third person). */
    public static void enterEntity(int entityId, ItemDisplayContext context) {
        enter(CosmeticsLayer.entity(entityId), context);
    }

    public static void exit() {
        holder = null;
    }

    private static boolean isHand(ItemDisplayContext context) {
        return context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND || context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND || context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
    }

    /** The shield cosmetic of the player whose hand item is being worked out, or null. */
    public static BakedCosmetic shield() {
        return holder == null ? null : CosmeticRenderer.heldFor(holder, holderLocal, CosmeticSlot.SHIELD);
    }

    //? if >=1.21.4 {
    // A shield's components, with the shield cosmetic that replaces it. Reads exactly like the components.
    public record ShieldArgument(DataComponentMap components, String cosmetic, boolean local) implements DataComponentMap {
        @Override
        public <T> T get(DataComponentType<? extends T> type) {
            return components.get(type);
        }

        @Override
        public Set<DataComponentType<?>> keySet() {
            return components.keySet();
        }
    }

    // The shield's components, tagged with the holder's shield cosmetic if they have one.
    public static DataComponentMap tag(DataComponentMap components) {
        BakedCosmetic shield = shield();
        return shield == null || components instanceof ShieldArgument ? components
                : new ShieldArgument(components, shield.id(), holderLocal);
    }
    //?}

    //? if >=1.21.9 {
    // Draws the cosmetic a shield was tagged with; false (draw the vanilla shield) when there is none.
    public static boolean drawTagged(DataComponentMap components, PoseStack poseStack, SubmitNodeCollector collector,
                                     int light) {
        if (!(components instanceof ShieldArgument tag)) {
            return false;
        }
        BakedCosmetic cosmetic = CosmeticRegistry.get(tag.cosmetic());
        return cosmetic != null && CosmeticRenderer.renderHeld(poseStack, light, cosmetic, tag.local(),
                (pose, c, layer, l, color, frame, clip, clipTime) ->
                        CosmeticsLayer.submit(collector, pose, c, layer, l, color, frame, clip, clipTime));
    }
    //?} elif >=1.21.4 {
    /*// Draws the cosmetic a shield was tagged with; false (draw the vanilla shield) when there is none.
    public static boolean drawTagged(DataComponentMap components, PoseStack poseStack, MultiBufferSource buffers,
                                     int light) {
        if (!(components instanceof ShieldArgument tag)) {
            return false;
        }
        BakedCosmetic cosmetic = CosmeticRegistry.get(tag.cosmetic());
        return cosmetic != null && CosmeticRenderer.renderHeld(poseStack, light, cosmetic, tag.local(),
                (pose, c, layer, l, color, frame, clip, clipTime) ->
                        CosmeticsLayer.drawToBuffers(buffers, pose, layer, l, color, frame, clip, clipTime));
    }
    *///?} else {
    /*// Draws the shield cosmetic of the player holding the shield being drawn; false (draw the vanilla shield)
    // when they have none.
    public static boolean drawShield(PoseStack poseStack, MultiBufferSource buffers, int light) {
        BakedCosmetic cosmetic = shield();
        return cosmetic != null && CosmeticRenderer.renderHeld(poseStack, light, cosmetic, holderLocal,
                (pose, c, layer, l, color, frame, clip, clipTime) ->
                        CosmeticsLayer.drawToBuffers(buffers, pose, layer, l, color, frame, clip, clipTime));
    }
    *///?}
}
