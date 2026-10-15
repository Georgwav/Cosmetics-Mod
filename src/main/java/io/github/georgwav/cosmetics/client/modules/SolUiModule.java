package io.github.georgwav.cosmetics.client.modules;

import io.github.georgwav.cosmetics.client.Module;
import io.github.georgwav.cosmetics.sol.config.SolConfig;
import net.minecraft.client.Minecraft;

/**
 * Sol UI as a module card: the same switch as Settings › Sol UI (kept client-wide in sol.json, not per profile).
 * Which parts Sol draws is on the menu's HUD page.
 */
public final class SolUiModule extends Module {
    public SolUiModule() {
        super("sol_ui", Category.HUD, "heart");
    }

    @Override
    public boolean enabled() {
        return SolConfig.get().solUi;
    }

    @Override
    public void setEnabled(boolean on) {
        SolConfig cfg = SolConfig.get();
        if (cfg.solUi != on) {
            cfg.solUi = on;
            SolConfig.save();
        }
    }

    @Override
    public boolean onByDefault() {
        return true;
    }

    @Override
    protected void onEnable(Minecraft mc) {
    }
}
