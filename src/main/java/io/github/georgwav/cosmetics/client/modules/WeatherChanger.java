package io.github.georgwav.cosmetics.client.modules;

import io.github.georgwav.cosmetics.client.Module;
import io.github.georgwav.cosmetics.client.Modules;
import io.github.georgwav.cosmetics.client.Setting;

import java.util.List;

/** The weather you see, whatever the server's is: only on your screen (rain falls as snow where it's cold). */
public final class WeatherChanger extends Module {
    private final Setting.Choice weather = add(new Setting.Choice("weather", "clear", List.of("clear", "rain", "thunder")));

    public WeatherChanger() {
        super("weather", Category.WORLD, "cloud");
    }

    /** The rain level to show (0 to 1), or a negative number for the server's. */
    public static float rain() {
        if (io.github.georgwav.cosmetics.devtools.SolScenario.lockedTime >= 0L) {
            return 0f;
        }
        float atmosphere = io.github.georgwav.cosmetics.sol.atmosphere.Atmosphere.lockedRain();
        if (atmosphere >= 0f) {
            return atmosphere;
        }
        WeatherChanger m = Modules.WEATHER;
        return !m.enabled() ? -1f : m.weather.get().equals("clear") ? 0f : 1f;
    }

    /** The thunder level to show (0 to 1), or a negative number for the server's. */
    public static float thunder() {
        if (io.github.georgwav.cosmetics.devtools.SolScenario.lockedTime >= 0L) {
            return 0f;
        }
        float atmosphere = io.github.georgwav.cosmetics.sol.atmosphere.Atmosphere.lockedThunder();
        if (atmosphere >= 0f) {
            return atmosphere;
        }
        WeatherChanger m = Modules.WEATHER;
        return !m.enabled() ? -1f : m.weather.get().equals("thunder") ? 1f : 0f;
    }
}
