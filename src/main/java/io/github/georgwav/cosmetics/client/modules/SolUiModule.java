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
 * the hotbar (how the selected slot shows, its numbers, its sweep of light, its glass), the size of the hotbar and
 * the bars over it, the hearts' glow, the tab list (ping in milliseconds, everyone's heads, its size, how many are
 * online), the scoreboard (shown or not, its red numbers, its size), the boss bars and the titles (shown or not,
 * their size); mace and totem slots, the tab list's players sliding in, screens sliding up as they open, cursor
 * particles in menus and UI sounds. See SolHud and HudTweaks.
 */
public final class SolUiModule extends Module {
    private static final java.util.function.DoubleFunction<String> PERCENT = v -> Math.round(v) + "%";

    /** The selected slot: lifted (the brief's), a little bigger in place, or only ringed. */
    public final Setting.Choice hotbarSelect = add(new Setting.Choice("hotbar_select", "lift", List.of("lift", "grow", "flat")).group("hotbar"));
    public final Setting.Toggle hotbarNumbers = add(new Setting.Toggle("hotbar_numbers", true).group("hotbar"));
    public final Setting.Toggle hotbarSweep = add(new Setting.Toggle("hotbar_sweep", true).group("hotbar"));
    /** How dark the hotbar's glass is against its usual; 0 for none. */
    public final Setting.Slider hotbarGlass = add(new Setting.Slider("hotbar_glass", 100, 0, 200, 10, PERCENT).group("hotbar"));
    /** The hotbar with the hearts, hunger, armour, air and experience over it, sized round the bottom middle. */
    public final Setting.Slider bottomScale = add(new Setting.Slider("bottom_scale", 100, 50, 150, 5, PERCENT).group("hotbar"));
    /** A mace's slot in its enchantment's colour (Breach, Density), filling as you fall; a totem's on a gold backdrop. */
    public final Setting.Toggle maceSlots = add(new Setting.Toggle("mace_slots", true).group("hotbar"));
    public final Setting.Toggle totemBackdrop = add(new Setting.Toggle("totem_backdrop", true).group("hotbar"));
    public final Setting.Toggle heartsGlow = add(new Setting.Toggle("hearts_glow", true).group("bars"));
    public final Setting.Toggle xpGlow = add(new Setting.Toggle("xp_glow", true).group("bars"));
    public final Setting.Choice tabPing = add(new Setting.Choice("tab_ping", "ms", List.of("bars", "ms")).group("tab_list"));
    public final Setting.Toggle tabHeads = add(new Setting.Toggle("tab_heads", true).group("tab_list"));
    public final Setting.Toggle tabCount = add(new Setting.Toggle("tab_count", true).group("tab_list"));
    public final Setting.Slider tabScale = add(new Setting.Slider("tab_scale", 100, 50, 150, 5, PERCENT).group("tab_list"));
    /** The players slide in one after another as the tab list opens. */
    public final Setting.Toggle tabSlide = add(new Setting.Toggle("tab_slide", true).group("tab_list"));
    public final Setting.Toggle scoreboard = add(new Setting.Toggle("scoreboard", true).group("scoreboard"));
    public final Setting.Toggle scoreNumbers = add(new Setting.Toggle("score_numbers", true).group("scoreboard"));
    public final Setting.Slider scoreboardScale = add(new Setting.Slider("scoreboard_scale", 100, 50, 150, 5, PERCENT).group("scoreboard"));
    public final Setting.Toggle bossBars = add(new Setting.Toggle("boss_bars", true).group("boss_bars"));
    public final Setting.Slider bossScale = add(new Setting.Slider("boss_scale", 100, 50, 150, 5, PERCENT).group("boss_bars"));
    public final Setting.Toggle titles = add(new Setting.Toggle("titles", true).group("titles"));
    public final Setting.Slider titleScale = add(new Setting.Slider("title_scale", 100, 25, 150, 5, PERCENT).group("titles"));
    /** Inventories, chat and containers slide up 8 pixels in three steps as they open, fading in through a dither. */
    public final Setting.Toggle screenSlide = add(new Setting.Toggle("screen_slide", true).group("menus"));
    /** Stars, hearts or Sol's sparkles trailing the cursor in menus. */
    public final Setting.Choice cursorParticles = add(new Setting.Choice("cursor_particles", "off",
            List.of("off", "stars", "hearts", "sparkles")).group("menus"));
    /** Clicks and chimes for switches, menus and toasts, from one of Sol's sound packs or yours. */
    public final Setting.Choice uiSounds = add(new Setting.Choice("ui_sounds", "off", uiSoundChoices()).group("menus"));
    public final Setting.Slider uiVolume = add(new Setting.Slider("ui_volume", 60, 0, 150, 10, PERCENT).group("menus"));

    /** The module while Sol UI is on (its tweaks apply), else null. */
    public static SolUiModule tweaks() {
        SolUiModule m = Modules.SOL_UI;
        return m.enabled() ? m : null;
    }

    public SolUiModule() {
        super("sol_ui", Category.HUD, "heart");
        uiVolume.shownWhen(() -> !uiSounds.get().equals("off"));
    }

    /** "off" and the sound packs (Sol's, and yours in client/sounds). */
    private static List<String> uiSoundChoices() {
        List<String> out = new java.util.ArrayList<>(List.of("off"));
        for (String c : io.github.georgwav.cosmetics.sol.fx.SolSounds.choices()) {
            if (!c.equals("off")) {
                out.add(c);
            }
        }
        return out;
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

    /** The UI sounds' packs by their names (Sol's), or a folder's own name (yours). */
    @Override
    public String optionName(Setting<?> setting, String option) {
        if (setting == uiSounds) {
            return option.startsWith("yours_") ? option.substring(6)
                    : net.minecraft.network.chat.Component.translatable("cosmeticsmod.fx.sound." + option).getString();
        }
        return super.optionName(setting, option);
    }

    @Override
    public boolean onByDefault() {
        return true;
    }

    @Override
    protected void onEnable(Minecraft mc) {
    }
}
