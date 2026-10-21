package io.github.georgwav.cosmetics.client.modules;

/**
 * The weather you see, whatever the server's is: only on your screen (rain falls as snow where it's cold). Set by
 * Atmosphere's weather lock (the Weather module moved there); the test scenario keeps the world's weather clear
 * (its storm is Atmosphere's own).
 */
public final class WeatherChanger {
    private WeatherChanger() {
    }

    /** The rain level to show (0 to 1), or a negative number for the server's. */
    public static float rain() {
        if (io.github.georgwav.cosmetics.devtools.SolScenario.clearSky()) {
            return 0f;
        }
        return io.github.georgwav.cosmetics.sol.atmosphere.Atmosphere.lockedRain();
    }

    /** The thunder level to show (0 to 1), or a negative number for the server's. */
    public static float thunder() {
        if (io.github.georgwav.cosmetics.devtools.SolScenario.clearSky()) {
            return 0f;
        }
        return io.github.georgwav.cosmetics.sol.atmosphere.Atmosphere.lockedThunder();
    }
}
