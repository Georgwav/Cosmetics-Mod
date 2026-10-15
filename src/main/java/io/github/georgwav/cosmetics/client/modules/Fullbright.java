package io.github.georgwav.cosmetics.client.modules;

import com.google.gson.JsonObject;
import io.github.georgwav.cosmetics.client.Module;
import io.github.georgwav.cosmetics.client.Setting;
import io.github.georgwav.cosmetics.compat.Compat;
import io.github.georgwav.cosmetics.mixin.OptionInstanceAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import java.util.List;
import java.util.Locale;

/**
 * Fullbright: the game's brightness turned past its slider's 100%, and (because shader packs ignore that
 * brightness) night vision on your own screen, like Gamma Utils. Night vision can be on with shaders only (the
 * default), always, or never. Your own brightness goes back when it's switched off, and is what the game saves.
 */
public final class Fullbright extends Module {
    private final Setting.Slider brightness = add(new Setting.Slider("brightness", 10.0, 1.0, 15.0, 0.5,
            v -> String.format(Locale.ROOT, "%d%%", Math.round(v * 100))));
    private final Setting.Choice nightVision = add(new Setting.Choice("night_vision", "shaders",
            List.of("shaders", "always", "off")));

    /** Your own brightness while the module has the game's; null while it hasn't. */
    private Double ownGamma;
    /** The night vision this module gave you, to take back only that one. */
    private MobEffectInstance given;

    public Fullbright() {
        super("fullbright", Category.VIEW, "bulb");
    }

    @Override
    protected void onEnable(Minecraft mc) {
        if (ownGamma == null) {
            ownGamma = mc.options.gamma().get();
        }
        setGamma(mc, brightness.get());
    }

    @Override
    protected void onSettingsChanged(Minecraft mc) {
        setGamma(mc, brightness.get());
    }

    @Override
    protected void tick(Minecraft mc) {
        // Another screen may have set the brightness (the slider in Video Settings): it stays boosted.
        if (!mc.options.gamma().get().equals(brightness.get())) {
            setGamma(mc, brightness.get());
        }
        boolean wantNightVision = switch (nightVision.get()) {
            case "always" -> true;
            case "shaders" -> Compat.isShaderPackActive();
            default -> false;
        };
        if (mc.player == null) {
            given = null;
            return;
        }
        MobEffectInstance current = mc.player.getEffect(MobEffects.NIGHT_VISION);
        if (wantNightVision && current == null) {
            // Only on your screen: no particles, no icon, until it's switched off (the server may take it away on
            // death or a world change; it's given again the next tick).
            given = new MobEffectInstance(MobEffects.NIGHT_VISION, MobEffectInstance.INFINITE_DURATION, 0, false, false, false);
            mc.player.addEffect(given);
        } else if (!wantNightVision && current != null && current == given) {
            takeNightVision(mc);
        }
    }

    @Override
    protected void onDisable(Minecraft mc) {
        if (ownGamma != null) {
            mc.options.gamma().set(ownGamma);
            ownGamma = null;
        }
        takeNightVision(mc);
    }

    private void takeNightVision(Minecraft mc) {
        if (mc.player != null && given != null && mc.player.getEffect(MobEffects.NIGHT_VISION) == given) {
            mc.player.removeEffect(MobEffects.NIGHT_VISION);
        }
        given = null;
    }

    // The game's options file only takes a brightness up to 100%: while it's saved, your own is put back.
    @Override
    protected void beforeOptionsSave(Minecraft mc) {
        if (ownGamma != null) {
            setGamma(mc, ownGamma);
        }
    }

    @Override
    protected void afterOptionsSave(Minecraft mc) {
        setGamma(mc, brightness.get());
    }

    /** Sets the brightness past the slider's range (the game's own set() would refuse it). */
    private static void setGamma(Minecraft mc, double value) {
        ((OptionInstanceAccessor) (Object) mc.options.gamma()).cosmeticsmod$setValue(value);
    }

    @Override
    protected void saveState(JsonObject state) {
        if (ownGamma != null) {
            state.addProperty("ownGamma", ownGamma);
        }
    }

    @Override
    protected void loadState(JsonObject state) {
        if (state.has("ownGamma")) {
            ownGamma = state.get("ownGamma").getAsDouble();
        }
    }
}
