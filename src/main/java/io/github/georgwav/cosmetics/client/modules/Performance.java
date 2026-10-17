package io.github.georgwav.cosmetics.client.modules;

import io.github.georgwav.cosmetics.client.Module;
import io.github.georgwav.cosmetics.client.Setting;
import net.minecraft.client.Minecraft;

/**
 * Performance: one choice for the game's heaviest video settings (render, simulation and entity distance, biome
 * blend): low-end, balanced or high. A preset is applied when it's picked; what a profile loads leaves the video
 * settings as they are.
 */
public final class Performance extends Module {
    /** One choice for the game's heaviest video settings (render, simulation and entity distance, biome blend). */
    private final Setting.Choice preset = add(new Setting.Choice("video_preset", "custom",
            java.util.List.of("custom", "low", "balanced", "high")));
    /** The preset the video settings were last set from (or loaded with): only a new pick changes them. */
    private String applied;

    public Performance() {
        super("performance", Category.TOOLS, "bolt");
    }

    @Override
    protected void loadState(com.google.gson.JsonObject state) {
        // What's saved stays as the video settings are now: a preset is applied only when it's picked.
        applied = preset.get();
    }

    @Override
    protected void onEnable(Minecraft mc) {
        // A preset picked while the module was off.
        onSettingsChanged(mc);
    }

    @Override
    protected void onSettingsChanged(Minecraft mc) {
        String p = preset.get();
        if (p.equals(applied)) {
            return;
        }
        applied = p;
        if (p.equals("custom")) {
            return;
        }
        var o = mc.options;
        int distance = switch (p) {
            case "low" -> 6;
            case "balanced" -> 12;
            default -> 20;
        };
        o.renderDistance().set(distance);
        o.simulationDistance().set(Math.min(distance, p.equals("low") ? 6 : 10));
        o.entityDistanceScaling().set(p.equals("low") ? 0.75 : p.equals("balanced") ? 1.0 : 1.5);
        o.biomeBlendRadius().set(p.equals("low") ? 0 : p.equals("balanced") ? 2 : 5);
        o.save();
    }
}
