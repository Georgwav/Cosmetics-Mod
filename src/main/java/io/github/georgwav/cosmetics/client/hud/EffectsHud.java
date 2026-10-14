package io.github.georgwav.cosmetics.client.hud;

import io.github.georgwav.cosmetics.client.Setting;
import io.github.georgwav.cosmetics.ui.Canvas;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Your potion effects, each with its icon, its level and the time it has left. */
public final class EffectsHud extends TextHud {
    private static final String[] LEVELS = {"", " II", " III", " IV", " V", " VI", " VII", " VIII", " IX", " X"};
    private static final int ICON = 9;

    private final Setting.Toggle icons = add(new Setting.Toggle("icons", true));
    /** The effect of each line last shown (lines() and sample() fill it as they make the lines). */
    private List<Holder<MobEffect>> shownEffects = List.of();

    public EffectsHud() {
        super("effects", "timer", 1f, 0.5f, -4f, 0f);
    }

    @Override
    protected List<String> lines(Minecraft mc) {
        if (mc.player == null) {
            shownEffects = List.of();
            return List.of();
        }
        List<MobEffectInstance> effects = new ArrayList<>(mc.player.getActiveEffects());
        effects.sort(Comparator.comparingInt(MobEffectInstance::getDuration).reversed());
        List<String> lines = new ArrayList<>();
        List<Holder<MobEffect>> shown = new ArrayList<>();
        for (MobEffectInstance effect : effects) {
            shown.add(effect.getEffect());
            String name = Component.translatable(effect.getEffect().value().getDescriptionId()).getString();
            int amplifier = effect.getAmplifier();
            String level = amplifier < LEVELS.length ? LEVELS[amplifier] : " " + (amplifier + 1);
            lines.add(name + level + "  " + time(effect));
        }
        shownEffects = shown;
        return lines;
    }

    @Override
    protected List<String> sample(Minecraft mc) {
        shownEffects = List.of(MobEffects.REGENERATION, MobEffects.NIGHT_VISION);
        return List.of(Component.translatable(MobEffects.REGENERATION.value().getDescriptionId()).getString() + " II  1:30",
                Component.translatable(MobEffects.NIGHT_VISION.value().getDescriptionId()).getString() + "  **:**");
    }

    @Override
    protected int iconRoom() {
        return icons.get() ? ICON + 3 : 0;
    }

    @Override
    protected void drawIcon(Canvas g, Minecraft mc, int index, int x, int y) {
        if (index < shownEffects.size()) {
            g.mobEffect(shownEffects.get(index), x, y, ICON);
        }
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
