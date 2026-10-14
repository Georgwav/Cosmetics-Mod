package io.github.georgwav.cosmetics.client;

import io.github.georgwav.cosmetics.client.hud.VanillaHud;
import io.github.georgwav.cosmetics.client.skins.OwnSkin;
import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import java.util.ArrayList;
import java.util.List;
//? if >=26.3 {
/*import net.minecraft.client.ClientClockManager;
*///?}

/**
 * For the in-game test: switches the world modules on (thunder, midnight, fullbright with night vision), checks the
 * game shows them, then switches them off and checks everything went back.
 */
public final class ModulesSelfTest {
    private static double gammaBefore;

    private ModulesSelfTest() {
    }

    public static void switchOn(Minecraft mc) {
        gammaBefore = mc.options.gamma().get();
        setting(Modules.WEATHER, "weather").set("thunder");
        setting(Modules.TIME, "time").set(18000.0);
        setting(Modules.FULLBRIGHT, "night_vision").set("always");
        Modules.WEATHER.setEnabled(true);
        Modules.TIME.setEnabled(true);
        Modules.FULLBRIGHT.setEnabled(true);
    }

    /** What's wrong while they're on; then switches them off and adds what didn't go back. */
    public static List<String> checkAndSwitchOff(Minecraft mc) {
        List<String> problems = new ArrayList<>();
        if (mc.level == null || mc.player == null) {
            problems.add("no world to check the modules in");
            return problems;
        }
        if (mc.level.getRainLevel(1f) < 0.99f || mc.level.getThunderLevel(1f) < 0.99f) {
            problems.add("the weather changer's thunder doesn't show (rain " + mc.level.getRainLevel(1f) + ")");
        }
        //? if >=26.3 {
        /*long clock = new ClientClockManager.ClientClockInstance().totalTicks();
        if (clock != 18000L) {
            problems.add("the time changer's midnight doesn't show on the world clocks (" + clock + ")");
        }
        *///?} elif >=26.1 {
        //?} else {
        if (mc.level.getDayTime() != 18000L) {
            problems.add("the time changer's midnight doesn't show (day time " + mc.level.getDayTime() + ")");
        }
        //?}
        if (mc.options.gamma().get() < 9.9) {
            problems.add("fullbright didn't raise the brightness (" + mc.options.gamma().get() + ")");
        }
        if (!mc.player.hasEffect(MobEffects.NIGHT_VISION)) {
            problems.add("fullbright's night vision isn't on");
        }
        Modules.FULLBRIGHT.setEnabled(false);
        Modules.TIME.setEnabled(false);
        Modules.WEATHER.setEnabled(false);
        if (Math.abs(mc.options.gamma().get() - gammaBefore) > 1e-6) {
            problems.add("fullbright didn't put the brightness back (" + mc.options.gamma().get() + ", was " + gammaBefore + ")");
        }
        if (mc.player.hasEffect(MobEffects.NIGHT_VISION)) {
            problems.add("fullbright's night vision stayed on after switching it off");
        }
        return problems;
    }

    @SuppressWarnings("unchecked")
    private static <T> Setting<T> setting(Module m, String id) {
        for (Setting<?> s : m.settings) {
            if (s.id.equals(id)) {
                return (Setting<T>) s;
            }
        }
        throw new IllegalArgumentException(m.id + " has no setting " + id);
    }

    /** Every HUD module and the crosshair on (and a potion effect for the effect timers to show), for a screenshot. */
    private static float pitchBefore;

    /**
     * The HUD modules all on, the crosshair, a raised hotbar, and a red block outline: the player looks down at the
     * ground in front so there is a block to outline.
     */
    public static void switchOnHud(Minecraft mc) {
        ModulesSelfTest.<Boolean>setting(Modules.COORDS, "biome").set(true);
        ModulesSelfTest.<String>setting(Modules.FPS, "font").set("geist");
        ModulesSelfTest.<String>setting(Modules.CLOCK, "font").set("geist");
        if (mc.player != null) {
            mc.player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 20 * 95, 0));
            pitchBefore = mc.player.getXRot();
            mc.player.setXRot(30f);
        }
        ModulesSelfTest.<String>setting(Modules.OUTLINE, "color").set("red");
        ModulesSelfTest.<Double>setting(Modules.OUTLINE, "opacity").set(1.0);
        Modules.OUTLINE.setEnabled(true);
        for (Module m : Modules.hud()) {
            m.setEnabled(true);
        }
        Modules.CROSSHAIR.setEnabled(true);
        // The game's hotbar with its hearts, armor, hunger and air raised a little, as the HUD editor would.
        for (VanillaHud.Part part : new VanillaHud.Part[]{VanillaHud.Part.HOTBAR, VanillaHud.Part.HEALTH,
                VanillaHud.Part.ARMOR, VanillaHud.Part.FOOD, VanillaHud.Part.AIR}) {
            VanillaHud.move(part, 0f, -30f);
        }
    }

    public static void switchOffHud(Minecraft mc) {
        for (Module m : Modules.hud()) {
            m.setEnabled(false);
        }
        Modules.CROSSHAIR.setEnabled(false);
        VanillaHud.reset();
        ModulesSelfTest.<Boolean>setting(Modules.COORDS, "biome").reset();
        ModulesSelfTest.<String>setting(Modules.FPS, "font").reset();
        ModulesSelfTest.<String>setting(Modules.CLOCK, "font").reset();
        Modules.OUTLINE.setEnabled(false);
        ModulesSelfTest.<String>setting(Modules.OUTLINE, "color").reset();
        ModulesSelfTest.<Double>setting(Modules.OUTLINE, "opacity").reset();
        if (mc.player != null) {
            mc.player.setXRot(pitchBefore);
            mc.player.removeEffect(MobEffects.NIGHT_VISION);
        }
    }

    /** Wears a plain test skin with slim arms the way Apply does, checks your player has it, and takes it off. */
    public static List<String> checkOwnSkin(Minecraft mc) {
        List<String> problems = new ArrayList<>();
        if (mc.player == null) {
            problems.add("no player to wear a skin");
            return problems;
        }
        int[] argb = new int[64 * 64];
        java.util.Arrays.fill(argb, 0xFF3A7BD5);
        OwnSkin.set(argb, true);
        if (!OwnSkin.isWorn(mc.player.getSkin())) {
            problems.add("an applied skin doesn't show on your own player");
        }
        OwnSkin.clear();
        if (OwnSkin.isWorn(mc.player.getSkin())) {
            problems.add("the applied skin stayed on after taking it off");
        }
        return problems;
    }
}
