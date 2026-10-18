package io.github.georgwav.cosmetics.client.modules;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.georgwav.cosmetics.client.Module;
import io.github.georgwav.cosmetics.client.Modules;
import io.github.georgwav.cosmetics.client.Setting;
import net.minecraft.world.entity.HumanoidArm;

/**
 * Held Items: the shield lower in first person (it covers less of the view) and the totem's pop on screen smaller.
 */
public final class HeldItems extends Module {
    /** The shield sits lower in first person, so it covers less of the view. */
    private final Setting.Toggle lowShield = add(new Setting.Toggle("low_shield", false));
    /** The totem's pop on screen, smaller. */
    private final Setting.Slider totem = add(new Setting.Slider("totem_size", 100, 25, 100, 5, v -> Math.round(v) + "%"));

    public HeldItems() {
        super("held_items", Category.VIEW, "hand");
    }

    /** Lowers a held shield after the game has put it by its arm. */
    public static void apply(PoseStack pose, HumanoidArm arm) {
        HeldItems h = Modules.HELD_ITEMS;
        if (h.enabled() && h.lowShield.get() && holds(arm, net.minecraft.world.item.Items.SHIELD)) {
            pose.translate(0f, -0.22f, 0f);
        }
    }

    /** Whether the item in that arm is {@code item}. */
    private static boolean holds(HumanoidArm arm, net.minecraft.world.item.Item item) {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (player == null) {
            return false;
        }
        var hand = arm == player.getMainArm() ? net.minecraft.world.InteractionHand.MAIN_HAND : net.minecraft.world.InteractionHand.OFF_HAND;
        return player.getItemInHand(hand).is(item);
    }

    /** The size of the totem's pop on screen (1 = the game's). */
    public static float totemScale() {
        HeldItems h = Modules.HELD_ITEMS;
        return h.enabled() ? (float) (h.totem.get() / 100.0) : 1f;
    }
}
