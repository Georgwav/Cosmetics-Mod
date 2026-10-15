package io.github.georgwav.cosmetics.client.modules;

/**
 * The time of day you see (sky, sun, light), whatever the server's is: only on your screen. Set by Atmosphere's
 * time lock (the Time module moved there) and by the test scenario.
 */
public final class TimeChanger {
    private TimeChanger() {
    }

    /** The day time to show (0 is 06:00, 6000 noon), or -1 for the server's. */
    public static long dayTime() {
        long sol = io.github.georgwav.cosmetics.devtools.SolScenario.lockedTime;
        if (sol >= 0L) {
            return sol;
        }
        return io.github.georgwav.cosmetics.sol.atmosphere.Atmosphere.lockedTime();
    }
}
