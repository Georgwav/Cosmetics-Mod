package io.github.georgwav.cosmetics.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Your potion effects, each with its level and the time it has left. */
public final class EffectsHud extends TextHud {
    private static final String[] LEVELS = {"", " II", " III", " IV", " V", " VI", " VII", " VIII", " IX", " X"};

    public EffectsHud() {
        super("effects", "timer", 1f, 0.5f, -4f, 0f);
    }

    @Override
    protected List<String> lines(Minecraft mc) {
        if (mc.player == null) {
            return List.of();
        }
        List<MobEffectInstance> effects = new ArrayList<>(mc.player.getActiveEffects());
        effects.sort(Comparator.comparingInt(MobEffectInstance::getDuration).reversed());
        List<String> lines = new ArrayList<>();
        for (MobEffectInstance effect : effects) {
            String name = Component.translatable(effect.getEffect().value().getDescriptionId()).getString();
            int amplifier = effect.getAmplifier();
            String level = amplifier < LEVELS.length ? LEVELS[amplifier] : " " + (amplifier + 1);
            lines.add(name + level + "  " + time(effect));
        }
        return lines;
    }

    @Override
    protected List<String> sample(Minecraft mc) {
        return List.of(Component.translatable("effect.minecraft.speed").getString() + " II  1:30",
                Component.translatable("effect.minecraft.night_vision").getString() + "  **:**");
    }

    private static String time(MobEffectInstance effect) {
        if (effect.isInfiniteDuration()) {
            return "**:**";
        }
        int seconds = effect.getDuration() / 20;
        return seconds >= 3600
                ? String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
                : String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }
}
