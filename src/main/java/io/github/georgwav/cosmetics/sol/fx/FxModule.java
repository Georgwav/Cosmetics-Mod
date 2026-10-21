package io.github.georgwav.cosmetics.sol.fx;

import io.github.georgwav.cosmetics.client.Module;
import io.github.georgwav.cosmetics.client.Setting;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A module of effects (Kill Effects, Hit Effects, Totems, Target, Jump FX, Trails, You, World FX): its effects'
 * standard settings ({@link FxSettings}) named once for all of them ({@code cosmeticsmod.fx.*}), Try it, and the live
 * preview on its settings page ({@link FxPreview}).
 */
public abstract class FxModule extends Module {
    /** Each standard setting and which one it is. */
    final Map<Setting<?>, String> std = new HashMap<>();

    protected FxModule(String id, Category category, String icon) {
        super(id, category, icon);
    }

    /** Makes one effect's standard settings, in group {@code group}. */
    protected FxSettings fx(String group, List<String> styles, FxSettings.Spec spec) {
        return FxSettings.make(group, styles, spec, this::add, std);
    }

    /**
     * Plays the module's effects on a dummy (Try it in the world, and the settings page's preview, every two
     * seconds): the effects that are on, as they're set. Returns how long they take, seconds.
     */
    public abstract float tryIt(FxPool pool, FxPreview.Dummy dummy);

    /**
     * Starts an effect in {@code pool} (the world's, or a preview's) lasting {@code base} seconds at 100%, made longer
     * or shorter by the effect's duration and speed.
     */
    protected static Fx start(FxPool pool, FxSettings fs, FxKind kind, double x, double y, double z, float base) {
        float speed = fs.speed == null ? 1f : (float) Math.max(0.5, fs.speed.get() / 100.0);
        float life = fs.life(base) / speed;
        Fx f = pool == FxWorld.POOL ? FxWorld.spawn(kind, x, y, z, life) : pool.spawn(kind, x, y, z, life, 2);
        f.base = base;
        return f;
    }

    /** The ground under a point: the world's, or a preview's flat ground. */
    protected static float ground(FxPool pool, double x, double z, double fromY) {
        return pool == FxWorld.POOL ? WorldOut.INSTANCE.ground(x, z, fromY) : 0f;
    }

    /** Whether effects started in {@code pool} make sounds (the world's do; a preview's stay quiet). */
    protected static boolean audible(FxPool pool) {
        return pool == FxWorld.POOL;
    }

    /** Whether the dummy is hidden while its effect plays (a kill takes it away). */
    public boolean hidesDummy() {
        return false;
    }

    private static String tr(String key) {
        return Component.translatable(key).getString();
    }

    private static boolean has(String key) {
        return !tr(key).equals(key);
    }

    /** A style's name: the module's own wording, or the effects' shared one. */
    public String styleName(String option) {
        String own = "cosmeticsmod.module." + id + ".style." + option;
        return has(own) ? tr(own) : tr("cosmeticsmod.fx.style." + option);
    }

    @Override
    public String settingName(Setting<?> setting) {
        String k = std.get(setting);
        if (k == null) {
            return super.settingName(setting);
        }
        if (k.startsWith("tick:")) {
            return styleName(k.substring(5));
        }
        String own = "cosmeticsmod.module." + id + "." + setting.id;
        return has(own) ? tr(own) : tr("cosmeticsmod.fx." + k);
    }

    @Override
    public String settingHint(Setting<?> setting) {
        String k = std.get(setting);
        if (k == null) {
            return super.settingHint(setting);
        }
        String own = "cosmeticsmod.module." + id + "." + setting.id + ".hint";
        if (has(own)) {
            return tr(own);
        }
        String key = "cosmeticsmod.fx." + (k.startsWith("tick:") ? "tick" : k) + ".hint";
        return has(key) ? tr(key) : "";
    }

    @Override
    public String optionName(Setting<?> setting, String option) {
        String k = std.get(setting);
        if (k == null) {
            return super.optionName(setting, option);
        }
        if (k.equals("style")) {
            return styleName(option);
        }
        if (k.equals("sound") && option.startsWith("yours_")) {
            return option.substring(6);
        }
        return tr("cosmeticsmod.fx." + k + "." + option);
    }
}
