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
    public final Setting.Toggle plasma = add(new Setting.Toggle("plasma", true).group("sun").advanced());
    public final Setting.Toggle corona = add(new Setting.Toggle("corona", true).group("sun").advanced());
    public final Setting.Toggle rays = add(new Setting.Toggle("rays", true).group("sun").advanced());
    public final Setting.Toggle dogs = add(new Setting.Toggle("dogs", true).group("sun").advanced());
    public final Setting.Toggle pillar = add(new Setting.Toggle("pillar", true).group("sun").advanced());
    public final Setting.Toggle shimmer = add(new Setting.Toggle("shimmer", true).group("sun").advanced());
    public final Setting.Toggle flare = add(new Setting.Toggle("flare", true).group("lens").advanced());
    public final Setting.Toggle glare = add(new Setting.Toggle("glare", false).group("lens").advanced());
    public final Setting.Toggle vignette = add(new Setting.Toggle("vignette", false).group("lens").advanced());
    /**
     * How strongly the theme's look is laid on: 100% is the full cinematic look (the theme's light, rims, shafts,
     * bloom, grade and fog); lower tones it down toward the game's own.
     */
    public final Setting.Slider intensity = add(new Setting.Slider("intensity", 100, 0, 100, 5, v -> Math.round(v) + "%"));
    // World.
    public final Setting.Toggle particles = add(new Setting.Toggle("particles", true).group("world").advanced());
    public final Setting.Choice quality = add(new Setting.Choice("quality", "auto", List.of("auto", "low", "medium", "high")));
    public final Setting.Toggle fog = add(new Setting.Toggle("fog", true).group("world").advanced());
    /** How far you see under water against the game (100%). */
    public final Setting.Slider waterView = add(new Setting.Slider("water_view", 100, 100, 400, 25, v -> Math.round(v) + "%").group("world").advanced());
    public final Setting.Toggle light = add(new Setting.Toggle("light", true).group("world").advanced());
    public final Setting.Toggle clouds = add(new Setting.Toggle("clouds", true).group("world").advanced());
    /** The game's square moon instead of the round pixel moon. */
    public final Setting.Toggle squareMoon = add(new Setting.Toggle("square_moon", false).group("night").advanced());
    // The night sky, for every theme (each theme's own layers are on the Night sky page, in its edits).
    /** The game's own sky instead of Sol's, to compare. */
    public final Setting.Toggle vanillaSky = add(new Setting.Toggle("vanilla_sky", false).group("world").advanced());
    /** How far the moon's light spreads into the sky round it. */
    public final Setting.Slider moonGlow = add(new Setting.Slider("moon_glow", 45, 0, 100, 5, v -> Math.round(v) + "%").group("night").advanced());
    public final Setting.Choice constellations = add(new Setting.Choice("constellations", "always", List.of("off", "look", "always")).group("night").advanced());
    /** How high the celestial pole stands over the north (0° is the game's straight east-to-west path). */
    public final Setting.Slider latitude = add(new Setting.Slider("latitude", 34, 0, 60, 1, v -> Math.round(v) + "°").group("night").advanced());
    /** Days in the sky's year: the stars at a given hour turn once round through it. */
    public final Setting.Slider year = add(new Setting.Slider("year", 96, 8, 384, 8, v -> Math.round(v) + "d").group("night").advanced());
    public final Setting.Toggle ambience = add(new Setting.Toggle("ambience", false).group("world").advanced());
    // Advanced: time and weather, as the Time and Weather modules had them.
    public final Setting.Toggle lockTime = add(new Setting.Toggle("lock_time", false).group("time_weather").advanced());
    public final Setting.Slider time = add(new Setting.Slider("time", 6000, 0, 23750, 250, Atmosphere::clock).group("time_weather").advanced());
    public final Setting.Toggle lockWeather = add(new Setting.Toggle("lock_weather", false).group("time_weather").advanced());
    public final Setting.Choice weather = add(new Setting.Choice("weather", "clear", List.of("clear", "rain", "thunder")).group("time_weather").advanced());

    public Atmosphere() {
        super("atmosphere", Category.WORLD, "sun");
        INSTANCE = this;
    }

    /** The intensity setting, 0..1 (1 when Atmosphere isn't loaded). */
    public static float intensity() {
        Atmosphere a = INSTANCE;
        return a == null ? 1f : (float) (a.intensity.get() / 100.0);
    }

    /** Whether the Sol sky replaces the game's sky now: on, in a world, and the theme isn't the plain one. */
    public static boolean active() {
        Atmosphere a = INSTANCE;
        return a != null && a.enabled() && !ThemeState.entry().base().vanillaSun();
    }

    /**
     * The time of day to show (0 is 06:00), or -1 to leave the world's. The lock works with Atmosphere's sky off too
     * (it took over the Time module).
     */
    public static long lockedTime() {
        Atmosphere a = INSTANCE;
        return a != null && a.lockTime.get() ? Math.round(a.time.get()) : -1L;
    }

    /** Rain level to show (0..1), or -1 to leave the world's (with the sky off too, as the time lock). */
    public static float lockedRain() {
        Atmosphere a = INSTANCE;
        if (a == null || !a.lockWeather.get()) {
            return -1f;
        }
        return "clear".equals(a.weather.get()) ? 0f : 1f;
    }

    /** Thunder level to show (0..1), or -1 to leave the world's. */
    public static float lockedThunder() {
        Atmosphere a = INSTANCE;
        if (a == null || !a.lockWeather.get()) {
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
    protected void tick(Minecraft mc) {
        Ambience.tick(mc, this);
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
