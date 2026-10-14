package io.github.georgwav.cosmetics.client.hud;

import net.minecraft.client.Minecraft;

import java.util.List;

/** The frame rate. */
public final class FpsHud extends TextHud {
    public FpsHud() {
        super("fps", "debug", 0f, 0f, 4f, 4f);
    }

    @Override
    protected List<String> lines(Minecraft mc) {
        return List.of(mc.getFps() + " FPS");
    }
}
