package io.github.georgwav.cosmetics.sol.atmosphere;

import io.github.georgwav.cosmetics.client.Module;
import io.github.georgwav.cosmetics.client.Setting;
import io.github.georgwav.cosmetics.sol.theme.ThemeState;
import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.Locale;

/**
 * The world's look from the theme: the Sol sky and sun, the stellar sky, fog, light, clouds and the theme's
 * particles. On by default for a new profile. Each sun layer can be switched off; the time and weather locks
 * (the old Time and Weather modules) live in its advanced settings.
 */
public final class Atmosphere extends Module {
    public static Atmosphere INSTANCE;

    // Sun layers (the Sun lab's switches).
    public final Setting.Toggle plasma = add(new Setting.Toggle("plasma", true).advanced());
    public final Setting.Toggle corona = add(new Setting.Toggle("corona", true).advanced());
    public final Setting.Toggle rays = add(new Setting.Toggle("rays", true).advanced());
    public final Setting.Toggle dogs = add(new Setting.Toggle("dogs", true).advanced());
    public final Setting.Toggle pillar = add(new Setting.Toggle("pillar", true).advanced());
    public final Setting.Toggle shimmer = add(new Setting.Toggle("shimmer", true).advanced());
    public final Setting.Toggle flare = add(new Setting.Toggle("flare", true).advanced());
    public final Setting.Toggle glare = add(new Setting.Toggle("glare", false).advanced());
    public final Setting.Toggle vignette = add(new Setting.Toggle("vignette", false).advanced());
    /**
     * How Sol's light looks: Pixel (the default) draws every glow pixel-sharp with crisp edges and the bloom in
     * visible steps, the look of a block game; Smooth draws them soft.
     */
    public final Setting.Choice lightStyle = add(new Setting.Choice("light_style", "pixel", List.of("pixel", "smooth")));
    // World.
    public final Setting.Toggle particles = add(new Setting.Toggle("particles", true).advanced());
    public final Setting.Choice quality = add(new Setting.Choice("quality", "auto", List.of("auto", "low", "medium", "high")));
    public final Setting.Toggle fog = add(new Setting.Toggle("fog", true).advanced());
    public final Setting.Toggle light = add(new Setting.Toggle("light", true).advanced());
    public final Setting.Toggle clouds = add(new Setting.Toggle("clouds", true).advanced());
    public final Setting.Toggle stars = add(new Setting.Toggle("stars", true).advanced());
    public final Setting.Toggle planets = add(new Setting.Toggle("planets", true).advanced());
    public final Setting.Toggle ambience = add(new Setting.Toggle("ambience", false).advanced());
    // Advanced: time and weather, as the Time and Weather modules had them.
    public final Setting.Toggle lockTime = add(new Setting.Toggle("lock_time", false).advanced());
    public final Setting.Slider time = add(new Setting.Slider("time", 6000, 0, 23750, 250, Atmosphere::clock).advanced());
    public final Setting.Toggle lockWeather = add(new Setting.Toggle("lock_weather", false).advanced());
    public final Setting.Choice weather = add(new Setting.Choice("weather", "clear", List.of("clear", "rain", "thunder")).advanced());

    public Atmosphere() {
        super("atmosphere", Category.WORLD, "sun");
        INSTANCE = this;
    }

    /** Whether the Sol sky replaces the game's sky now: on, in a world, and the theme isn't the plain one. */
    public static boolean active() {
        Atmosphere a = INSTANCE;
        return a != null && a.enabled() && !ThemeState.entry().base().vanillaSun();
    }

    /** The time of day to show (0 is 06:00), or -1 to leave the world's. */
    public static long lockedTime() {
        Atmosphere a = INSTANCE;
        return a != null && a.enabled() && a.lockTime.get() ? Math.round(a.time.get()) : -1L;
    }

    /** Rain level to show (0..1), or -1 to leave the world's. */
    public static float lockedRain() {
        Atmosphere a = INSTANCE;
        if (a == null || !a.enabled() || !a.lockWeather.get()) {
            return -1f;
        }
        return "clear".equals(a.weather.get()) ? 0f : 1f;
    }

    /** Thunder level to show (0..1), or -1 to leave the world's. */
    public static float lockedThunder() {
        Atmosphere a = INSTANCE;
        if (a == null || !a.enabled() || !a.lockWeather.get()) {
            return -1f;
        }
        return "thunder".equals(a.weather.get()) ? 1f : 0f;
    }

    /** The particle pool for the quality setting (the governor picks for "auto"). */
    public int particleBudget() {
        return switch (quality.get()) {
            case "low" -> 300;
            case "medium" -> 600;
            case "high" -> 1200;
            default -> QualityGovernor.budget();
        };
    }

    @Override
    protected void onEnable(Minecraft mc) {
        SkyModGuard.atmosphereOn();
    }

    @Override
    protected void onDisable(Minecraft mc) {
        SkyModGuard.atmosphereOff();
        ParticleField.clear();
    }

    static String clock(double ticks) {
        int minutes = (int) Math.round((ticks / 1000.0 + 6.0) * 60.0) % (24 * 60);
        return String.format(Locale.ROOT, "%02d:%02d", minutes / 60, minutes % 60);
    }
}
