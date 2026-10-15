package io.github.georgwav.cosmetics.sol.ui.kit;

import io.github.georgwav.cosmetics.devtools.SolScenario;
import io.github.georgwav.cosmetics.sol.Feature;
import io.github.georgwav.cosmetics.sol.Guard;
import io.github.georgwav.cosmetics.ui.Canvas;
import net.minecraft.client.Minecraft;

/**
 * What Sol draws over everything (see SolOverlayMixin): above the HUD and any open screen, even with the HUD hidden.
 */
public final class SolOverlay {
    private SolOverlay() {
    }

    public static void render(Canvas g) {
        if (!Guard.on(Feature.SOL_UI)) {
            return;
        }
        Guard.run(Feature.SOL_UI, () -> draw(g));
    }

    private static void draw(Canvas g) {
        Minecraft mc = Minecraft.getInstance();
        if (SolScenario.crosshairOnly && io.github.georgwav.cosmetics.ui.ScreenOpener.current() == null && mc.level != null) {
            // The reference frames show the world and the crosshair, nothing else.
            int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
            int cx = w / 2, cy = h / 2;
            g.fill(cx - 4, cy, cx + 5, cy + 1, 0xE6FFFFFF);
            g.fill(cx, cy - 4, cx + 1, cy, 0xE6FFFFFF);
            g.fill(cx, cy + 1, cx + 1, cy + 5, 0xE6FFFFFF);
        }
    }
}
