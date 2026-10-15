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
        float s = (float) (h.size.get() / 100.0);
        if (s != 1f) {
            pose.scale(s, s, s);
        }
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
