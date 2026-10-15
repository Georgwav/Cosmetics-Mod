package io.github.georgwav.cosmetics.client.modules;

import io.github.georgwav.cosmetics.client.Module;
import io.github.georgwav.cosmetics.client.Modules;
import io.github.georgwav.cosmetics.client.Setting;
import net.minecraft.client.Minecraft;

/**
 * Performance: the frame rate while the game's window isn't in front (the game keeps drawing at full speed behind
 * other windows), so it stays cool and quiet in the background.
 */
public final class Performance extends Module {
    private final Setting.Slider background = add(new Setting.Slider("background_fps", 30, 5, 120, 5,
            v -> Math.round(v) + " FPS"));

    public Performance() {
        super("performance", Category.TOOLS, "bolt");
    }

    /** The frame rate cap for the game's {@code limit} now. */
    public static int limit(int limit) {
        Performance p = Modules.PERFORMANCE;
        if (!p.enabled() || Minecraft.getInstance().isWindowActive()) {
            return limit;
        }
        return Math.min(limit, (int) Math.round(p.background.get()));
    }
}
