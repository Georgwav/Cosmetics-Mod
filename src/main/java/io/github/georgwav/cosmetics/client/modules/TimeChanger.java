package io.github.georgwav.cosmetics.client.modules;

import io.github.georgwav.cosmetics.client.Module;
import io.github.georgwav.cosmetics.client.Modules;
import io.github.georgwav.cosmetics.client.Setting;

import java.util.Locale;

/** The time of day you see (sky, sun, light), whatever the server's is: only on your screen. */
public final class TimeChanger extends Module {
    private final Setting.Slider time = add(new Setting.Slider("time", 6000, 0, 23750, 250, TimeChanger::clock));

    public TimeChanger() {
        super("time", Category.WORLD, "clock");
    }

    /** Whether this Minecraft version can change it yet: 26.1 replaced the day time with world clocks. */
    public static boolean supported() {
        //? if >=26.1 {
        /*return false;
        *///?} else {
        return true;
        //?}
    }

    /** The day time to show (0 is 06:00, 6000 noon), or -1 for the server's. */
    public static long dayTime() {
        TimeChanger m = Modules.TIME;
        return m.enabled() ? Math.round(m.time.get()) : -1L;
    }

    /** A day time as the clock shows it: 0 is 06:00. */
    static String clock(double ticks) {
        int minutes = (int) Math.round((ticks / 1000.0 + 6.0) * 60.0) % (24 * 60);
        return String.format(Locale.ROOT, "%02d:%02d", minutes / 60, minutes % 60);
    }
}
