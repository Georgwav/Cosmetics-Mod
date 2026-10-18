package io.github.georgwav.cosmetics.client.modules;

import io.github.georgwav.cosmetics.client.Module;
import io.github.georgwav.cosmetics.config.CosmeticsConfig;
import io.github.georgwav.cosmetics.source.EquippedCosmetics;

/**
 * Cosmetics as a module card: the same switch as the Wardrobe's Settings › Display › Cosmetics enabled (kept in the
 * cosmetics config). Everything else about cosmetics is in the Wardrobe.
 */
public final class CosmeticsModule extends Module {
    public CosmeticsModule() {
        super("cosmetics", Category.VIEW, "cape");
    }

    @Override
    public boolean enabled() {
        return CosmeticsConfig.get().enabled;
    }

    @Override
    public void setEnabled(boolean on) {
        CosmeticsConfig cfg = CosmeticsConfig.get();
        if (cfg.enabled != on) {
            cfg.enabled = on;
            CosmeticsConfig.save();
            EquippedCosmetics.invalidate();
        }
    }

    @Override
    public boolean onByDefault() {
        return true;
    }
}
