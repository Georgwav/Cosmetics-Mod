package io.github.georgwav.cosmetics.sol.client;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bed Wars timers, read from what Hypixel shows (no game data of its own): the seconds until the nearest diamond and
 * emerald generators spawn, from the floating text above each generator ("Diamond" over "Spawns in 12 seconds"),
 * and the next event from the sidebar ("Diamond II in 4:59", "Bed gone in 5:00"). Plain strings in, so it's tested
 * without the game.
 */
public final class BedWarsTimers {
    /** A floating name and where it floats. */
    public record Label(double x, double y, double z, String text) {
    }

    private static final Pattern SPAWNS = Pattern.compile("spawns in (\\d+) seconds?");
    private static final Pattern EVENT = Pattern.compile("^(.*\\S) in (\\d{1,2}:\\d{2})$");

    private BedWarsTimers() {
    }

    /** Text without the game's § colour codes, trimmed. */
    public static String plain(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '§') {
                i++;
            } else {
                b.append(c);
            }
        }
        return b.toString().trim();
    }

    /** Whether a sidebar title is Bed Wars'. */
    public static boolean isBedWars(String title) {
        return plain(title).toUpperCase(Locale.ROOT).contains("BED WARS");
    }

    /** The first sidebar line counting down to an event ("Diamond II in 4:59"), or null. */
    public static String event(List<String> sidebar) {
        for (String line : sidebar) {
            Matcher m = EVENT.matcher(plain(line));
            if (m.matches()) {
                return m.group(1) + " in " + m.group(2);
            }
        }
        return null;
    }

    /** The seconds a generator's countdown text says, or -1. */
    public static int seconds(String text) {
        Matcher m = SPAWNS.matcher(plain(text).toLowerCase(Locale.ROOT));
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    /**
     * The nearest diamond and emerald generators' seconds to their next spawn ({diamond, emerald}, -1 for none in
     * sight): each countdown belongs to the "Diamond" or "Emerald" text floating just above it.
     */
    public static int[] nearest(List<Label> labels, double px, double pz) {
        int[] out = {-1, -1};
        double[] best = {Double.MAX_VALUE, Double.MAX_VALUE};
        for (Label count : labels) {
            int s = seconds(count.text());
            if (s < 0) {
                continue;
            }
            for (Label kind : labels) {
                double dx = kind.x() - count.x(), dz = kind.z() - count.z(), dy = kind.y() - count.y();
                if (dx * dx + dz * dz > 1.0 || dy < 0.0 || dy > 1.5) {
                    continue;
                }
                String k = plain(kind.text()).toLowerCase(Locale.ROOT);
                int which = k.equals("diamond") ? 0 : k.equals("emerald") ? 1 : -1;
                if (which < 0) {
                    continue;
                }
                double d = (count.x() - px) * (count.x() - px) + (count.z() - pz) * (count.z() - pz);
                if (d < best[which]) {
                    best[which] = d;
                    out[which] = s;
                }
            }
        }
        return out;
    }
}
