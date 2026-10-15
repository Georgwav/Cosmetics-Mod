package io.github.georgwav.cosmetics.client.modules;

import io.github.georgwav.cosmetics.client.Module;
import io.github.georgwav.cosmetics.client.Modules;
import io.github.georgwav.cosmetics.client.Setting;

/** Dropped Items: the size items lying on the ground are drawn at, so loot reads at a glance (or stays small). */
public final class DroppedItems extends Module {
    private final Setting.Slider size = add(new Setting.Slider("size", 150, 50, 300, 10, v -> Math.round(v) + "%"));

    public DroppedItems() {
        super("dropped_items", Category.VIEW, "item");
    }

    /** The scale for dropped items (1 = the game's). */
    public static float scale() {
        DroppedItems d = Modules.DROPPED_ITEMS;
        return d.enabled() ? (float) (d.size.get() / 100.0) : 1f;
    }
}
