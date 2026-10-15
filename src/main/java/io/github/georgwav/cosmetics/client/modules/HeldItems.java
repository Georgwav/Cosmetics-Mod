package io.github.georgwav.cosmetics.client.modules;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.georgwav.cosmetics.client.Module;
import io.github.georgwav.cosmetics.client.Modules;
import io.github.georgwav.cosmetics.client.Setting;
import net.minecraft.world.entity.HumanoidArm;

import java.util.Locale;

/**
 * Held Items: where the items in your hands sit in first person (sideways, up, forward, mirrored for the other
 * hand), how large they are, and how fast you swing.
 */
public final class HeldItems extends Module {
    private final Setting.Slider x = add(new Setting.Slider("x", 0, -1, 1, 0.05, HeldItems::offset));
    private final Setting.Slider y = add(new Setting.Slider("y", 0, -1, 1, 0.05, HeldItems::offset));
    private final Setting.Slider z = add(new Setting.Slider("z", 0, -1, 1, 0.05, HeldItems::offset));
    private final Setting.Slider size = add(new Setting.Slider("size", 100, 40, 150, 5, v -> Math.round(v) + "%"));
    private final Setting.Slider swing = add(new Setting.Slider("swing", 100, 25, 200, 5, v -> Math.round(v) + "%"));
    /** The shield sits lower in first person, so it covers less of the view. */
    private final Setting.Toggle lowShield = add(new Setting.Toggle("low_shield", false));
    /** The totem's pop on screen, smaller. */
    private final Setting.Slider totem = add(new Setting.Slider("totem_size", 100, 25, 100, 5, v -> Math.round(v) + "%"));

    public HeldItems() {
        super("held_items", Category.VIEW, "hand");
    }

    private static String offset(double v) {
        return String.format(Locale.ROOT, "%+.2f", v);
    }

    /** Moves and sizes an item after the game has put it by its arm. */
    public static void apply(PoseStack pose, HumanoidArm arm) {
        HeldItems h = Modules.HELD_ITEMS;
        if (!h.enabled()) {
            return;
        }
        float side = arm == HumanoidArm.RIGHT ? 1f : -1f;
        pose.translate(side * h.x.get().floatValue() * 0.5f, h.y.get().floatValue() * 0.5f, -h.z.get().floatValue() * 0.5f);
        if (h.lowShield.get() && holds(arm, net.minecraft.world.item.Items.SHIELD)) {
            pose.translate(0f, -0.22f, 0f);
        }
        float s = (float) (h.size.get() / 100.0);
        if (s != 1f) {
            pose.scale(s, s, s);
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

    /** Your swing's length in ticks for the game's {@code ticks} (faster = shorter). */
    public static int swing(int ticks) {
        HeldItems h = Modules.HELD_ITEMS;
        if (!h.enabled() || h.swing.get() == 100.0) {
            return ticks;
        }
        return Math.max(1, (int) Math.round(ticks * 100.0 / h.swing.get()));
    }
}
