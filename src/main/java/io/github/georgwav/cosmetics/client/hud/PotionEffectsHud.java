package io.github.georgwav.cosmetics.client.hud;

import io.github.georgwav.cosmetics.client.Setting;
import io.github.georgwav.cosmetics.sol.hud.HudKit;
import io.github.georgwav.cosmetics.sol.theme.Tokens;
import io.github.georgwav.cosmetics.sol.ui.kit.Gfx;
import io.github.georgwav.cosmetics.sol.ui.kit.SolFont;
import io.github.georgwav.cosmetics.sol.util.Colors;
import io.github.georgwav.cosmetics.ui.Canvas;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Potion Effects: your effects, each with its icon, its level and the time it has left. Sol UI draws a chip per
 * effect with a ring that drains with the time left (from the longest it has had) and pulses in the last ten seconds;
 * the plain look writes lines with the icons.
 */
public final class PotionEffectsHud extends TextHud {
    private static final String[] LEVELS = {"", " II", " III", " IV", " V", " VI", " VII", " VIII", " IX", " X"};
    private static final int ICON = 9;

    private final Setting.Toggle icons = add(new Setting.Toggle("icons", true).group("effects"));
    private final Setting.Choice sort = add(new Setting.Choice("sort", "time", List.of("time", "name", "level")).group("effects"));
    /** Not the ones from beacons and conduits. */
    private final Setting.Toggle hideAmbient = add(new Setting.Toggle("hide_ambient", false).group("effects"));
    /** Not the ones with no end (from commands, armour trims and the like). */
    private final Setting.Toggle hideInfinite = add(new Setting.Toggle("hide_infinite", false).group("effects"));
    private final Setting.Slider maxShown = add(new Setting.Slider("max_shown", 12, 1, 12, 1, v -> Integer.toString((int) Math.round(v)))
            .group("effects"));
    /** Warns (amber, pulsing) in an effect's last seconds. */
    private final Setting.Slider warnAt = add(new Setting.Slider("warn_at", 10, 0, 30, 1, v -> v == 0 ? "–" : Math.round(v) + " s")
            .group("effects"));
    /** The effect of each line last shown (lines() and sample() fill it as they make the lines). */
    private List<Holder<MobEffect>> shownEffects = List.of();
    /** The longest time left each effect has had (what its ring drains from). */
    private final Map<Holder<MobEffect>, Integer> longest = new HashMap<>();

    public PotionEffectsHud() {
        // Left middle: the right middle is the scoreboard's, the top right the game's own effect icons.
        super("potion_effects", "timer", 0f, 0.5f, 4f, 0f);
    }

    private List<MobEffectInstance> effects(Minecraft mc) {
        if (mc.player == null) {
            return List.of();
        }
        List<MobEffectInstance> effects = new ArrayList<>();
        for (MobEffectInstance e : mc.player.getActiveEffects()) {
            if (hideAmbient.get() && e.isAmbient() || hideInfinite.get() && e.isInfiniteDuration()) {
                continue;
            }
            effects.add(e);
        }
        switch (sort.get()) {
            case "name" -> effects.sort(Comparator.comparing(PotionEffectsHud::name));
            case "level" -> effects.sort(Comparator.comparingInt(MobEffectInstance::getAmplifier).reversed()
                    .thenComparing(Comparator.comparingInt(MobEffectInstance::getDuration).reversed()));
            default -> effects.sort(Comparator.comparingInt(MobEffectInstance::getDuration).reversed());
        }
        int n = (int) Math.round(maxShown.get());
        return effects.size() > n ? effects.subList(0, n) : effects;
    }

    private static String name(MobEffectInstance effect) {
        String name = Component.translatable(effect.getEffect().value().getDescriptionId()).getString();
        int amplifier = effect.getAmplifier();
        return name + (amplifier < LEVELS.length ? LEVELS[amplifier] : " " + (amplifier + 1));
    }

    @Override
    protected List<String> lines(Minecraft mc) {
        List<MobEffectInstance> effects = effects(mc);
        List<String> lines = new ArrayList<>();
        List<Holder<MobEffect>> shown = new ArrayList<>();
        for (MobEffectInstance effect : effects) {
            shown.add(effect.getEffect());
            lines.add(name(effect) + "  " + time(effect));
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

    // ---------------------------------------------------------------- Sol UI: a chip per effect

    private static final float H = 18f, GAP = 3f, RING = 12f, PAD_L = 4f, PAD_R = 6f;

    /** What the chips show: an effect, or for the editor's preview a made-up one. */
    private record Chip(Holder<MobEffect> effect, String name, String time, float left, boolean ending) {
    }

    private List<Chip> chips(Minecraft mc) {
        List<Chip> out = new ArrayList<>();
        for (MobEffectInstance e : effects(mc)) {
            int d = e.getDuration();
            int most = Math.max(d, longest.getOrDefault(e.getEffect(), 0));
            longest.put(e.getEffect(), most);
            float left = e.isInfiniteDuration() ? 1f : most <= 0 ? 0f : (float) d / most;
            out.add(new Chip(e.getEffect(), name(e), time(e), left, !e.isInfiniteDuration() && d < warnAt.get() * 20.0));
        }
        if (mc.player != null && longest.size() > out.size() + 8) {
            longest.keySet().removeIf(k -> !mc.player.hasEffect(k));
        }
        if (out.isEmpty() && preview()) {
            out.add(new Chip(MobEffects.REGENERATION, Component.translatable(MobEffects.REGENERATION.value().getDescriptionId())
                    .getString() + " II", "1:30", 0.62f, false));
            out.add(new Chip(MobEffects.NIGHT_VISION, Component.translatable(MobEffects.NIGHT_VISION.value().getDescriptionId())
                    .getString(), "0:08", 0.05f, true));
        }
        return out;
    }

    private float chipWidth(Chip c) {
        float text = Math.max(HudKit.measure(SolFont.Family.MEDIUM, HudKit.TEXT, c.name(), scale()),
                HudKit.measure(SolFont.Family.PIXEL_BOLD, HudKit.SMALL, c.time(), scale()));
        return PAD_L + RING + 4.5f + text + PAD_R;
    }

    @Override
    protected int solWidth(Minecraft mc) {
        float w = 0f;
        for (Chip c : chips(mc)) {
            w = Math.max(w, chipWidth(c));
        }
        return (int) Math.ceil(w);
    }

    @Override
    protected int solHeight(Minecraft mc) {
        int n = chips(mc).size();
        return n == 0 ? 0 : (int) Math.ceil(n * H + (n - 1) * GAP);
    }

    @Override
    protected void drawSol(Gfx g, Tokens tk, Minecraft mc) {
        List<Chip> chips = chips(mc);
        float full = solWidth(mc), y = 0f;
        SolFont name = HudKit.text(g), time = g.font(SolFont.Family.PIXEL_BOLD, HudKit.SMALL);
        float pulse = HudKit.pulse();
        for (Chip c : chips) {
            float w = chipWidth(c), x = anchorX >= 0.5f ? full - w : 0f;
            HudKit.card(g, tk, x, y, w, H, HudKit.CARD_R);
            float cx = x + PAD_L + RING / 2f, cy = y + H / 2f;
            int col = c.ending() ? Colors.fade(Tokens.AMBER, pulse) : tk.accent;
            float rx = x + PAD_L, ry = (float) Math.floor(cy - RING / 2f);
            square(g, rx, ry, RING, 1f, 0x24FFFFFF);
            square(g, rx, ry, RING, c.left(), col);
            float tx = x + PAD_L + RING + 4.5f;
            g.textMid(name, c.name(), tx, cy - 3f, 0xFFFFFFFF);
            g.textMid(time, c.time(), tx, cy + 3.5f, c.ending() ? Colors.fade(Tokens.AMBER, 0.5f + 0.5f * pulse) : 0xA8FFFFFF);
            y += H + GAP;
        }
        // The effects' icons inside the rings, by the game.
        g.layer();
        Canvas gc = g.game();
        gc.push();
        gc.scale(g.d.u, g.d.u);
        y = 0f;
        for (Chip c : chips) {
            float x = anchorX >= 0.5f ? full - chipWidth(c) : 0f;
            gc.push();
            gc.translate(x + PAD_L + RING / 2f - 3.5f, y + H / 2f - 3.5f);
            gc.scale(7f / 9f, 7f / 9f);
            gc.mobEffect(c.effect(), 0, 0, 9);
            gc.pop();
            y += H + GAP;
        }
        gc.pop();
    }

    /**
     * The pixel ring: a square outline two GUI pixels thick round the icon, the part still left of it lit clockwise
     * from the top's middle, a whole GUI pixel at a time.
     */
    private static void square(Gfx g, float x, float y, float side, float left, int color) {
        if (left <= 0f) {
            return;
        }
        float t = 2f, run = side - t, half = Math.round(side / 2f);
        // The perimeter as five runs: top right, right, bottom, left, top left.
        float length = Math.round(4f * run * Math.min(1f, left));
        float a = Math.min(length, side - half);
        HudKit.box(g, x + half, y, a, t, color);
        length -= a;
        a = Math.min(length, run);
        HudKit.box(g, x + side - t, y + t, t, a, color);
        length -= a;
        a = Math.min(length, run);
        HudKit.box(g, x + side - t - a, y + side - t, a, t, color);
        length -= a;
        a = Math.min(length, run);
        HudKit.box(g, x, y + side - t - a, t, a, color);
        length -= a;
        a = Math.min(length, half - t);
        HudKit.box(g, x + t, y, a, t, color);
    }
}
