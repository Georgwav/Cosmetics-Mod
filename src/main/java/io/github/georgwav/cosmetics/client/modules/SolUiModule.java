package io.github.georgwav.cosmetics.client.modules;

import io.github.georgwav.cosmetics.client.Module;
import io.github.georgwav.cosmetics.client.Modules;
import io.github.georgwav.cosmetics.client.Setting;
import io.github.georgwav.cosmetics.sol.config.SolConfig;
import net.minecraft.client.Minecraft;

import java.util.List;

/**
 * Sol UI as a module card: the same switch as Settings › Sol UI (kept client-wide in sol.json, not per profile).
 * Which parts Sol draws is on the menu's HUD page. Its settings tweak the game's own HUD parts while Sol UI is on:
 * the tab list (ping in milliseconds, everyone's heads, its size, how many are online), the scoreboard (shown or
 * not, its red numbers, its size), the boss bars and the titles (shown or not, their size). See HudTweaks.
 */
public final class SolUiModule extends Module {
    private static final java.util.function.DoubleFunction<String> PERCENT = v -> Math.round(v) + "%";

    public final Setting.Choice tabPing = add(new Setting.Choice("tab_ping", "ms", List.of("bars", "ms")).group("tab_list"));
    public final Setting.Toggle tabHeads = add(new Setting.Toggle("tab_heads", true).group("tab_list"));
    public final Setting.Toggle tabCount = add(new Setting.Toggle("tab_count", true).group("tab_list"));
    public final Setting.Slider tabScale = add(new Setting.Slider("tab_scale", 100, 50, 150, 5, PERCENT).group("tab_list"));
    public final Setting.Toggle scoreboard = add(new Setting.Toggle("scoreboard", true).group("scoreboard"));
    public final Setting.Toggle scoreNumbers = add(new Setting.Toggle("score_numbers", true).group("scoreboard"));
    public final Setting.Slider scoreboardScale = add(new Setting.Slider("scoreboard_scale", 100, 50, 150, 5, PERCENT).group("scoreboard"));
    public final Setting.Toggle bossBars = add(new Setting.Toggle("boss_bars", true).group("boss_bars"));
    public final Setting.Slider bossScale = add(new Setting.Slider("boss_scale", 100, 50, 150, 5, PERCENT).group("boss_bars"));
    public final Setting.Toggle titles = add(new Setting.Toggle("titles", true).group("titles"));
    public final Setting.Slider titleScale = add(new Setting.Slider("title_scale", 100, 25, 150, 5, PERCENT).group("titles"));

    /** The module while Sol UI is on (its tweaks apply), else null. */
    public static SolUiModule tweaks() {
        SolUiModule m = Modules.SOL_UI;
        return m.enabled() ? m : null;
    }

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
