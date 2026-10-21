package io.github.georgwav.cosmetics.client.modules;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.georgwav.cosmetics.client.Module;
import io.github.georgwav.cosmetics.client.Modules;
import io.github.georgwav.cosmetics.client.Setting;
import net.minecraft.world.entity.HumanoidArm;

/**
 * Held Items: the shield lower in first person (it covers less of the view), and the shield's and the totem's size
 * in your hand. The totem's pop on screen moved to Totems.
 */
public final class HeldItems extends Module {
    /** The shield sits lower in first person, so it covers less of the view. */
    private final Setting.Toggle lowShield = add(new Setting.Toggle("low_shield", false));
    /** The shield's and the totem's size in your hand (first person). */
    private final Setting.Slider shieldSize = add(new Setting.Slider("shield_size", 100, 40, 150, 5, v -> Math.round(v) + "%"));
    private final Setting.Slider totemHand = add(new Setting.Slider("totem_hand", 100, 40, 150, 5, v -> Math.round(v) + "%"));

    public HeldItems() {
        super("held_items", Category.VIEW, "hand");
    }

    /** Lowers and sizes a held shield, and sizes a held totem, after the game has put it by its arm. */
    public static void apply(PoseStack pose, HumanoidArm arm) {
        HeldItems h = Modules.HELD_ITEMS;
        if (!h.enabled()) {
            return;
        }
        if (holds(arm, net.minecraft.world.item.Items.SHIELD)) {
            if (h.lowShield.get()) {
                pose.translate(0f, -0.22f, 0f);
            }
            float k = (float) (h.shieldSize.get() / 100.0);
            if (k != 1f) {
                pose.scale(k, k, k);
            }
        } else if (holds(arm, net.minecraft.world.item.Items.TOTEM_OF_UNDYING)) {
            float k = (float) (h.totemHand.get() / 100.0);
            if (k != 1f) {
                pose.scale(k, k, k);
            }
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
}
