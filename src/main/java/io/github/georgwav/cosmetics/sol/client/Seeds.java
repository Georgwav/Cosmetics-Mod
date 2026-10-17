package io.github.georgwav.cosmetics.sol.client;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * World seeds as players have them: typed in (a number, or words as the game turns them into a seed), or read back
 * from the game's {@code /seed} answer ("Seed: [1234]"). Plain strings, so it's tested without the game.
 */
public final class Seeds {
    private static final Pattern NUMBER = Pattern.compile("-?\\d+");

    private Seeds() {
    }

    /** A typed seed: a whole number as it is, other text as the game makes a seed of it (its hash); null if blank. */
    public static Long typed(String text) {
        String t = text == null ? "" : text.strip();
        if (t.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(t);
        } catch (NumberFormatException e) {
            return (long) t.hashCode();
        }
    }

    /** The seed in the game's answer to {@code /seed} (the number in its brackets), or null. */
    public static Long fromReply(String seedArgument) {
        if (seedArgument == null) {
            return null;
        }
        Matcher m = NUMBER.matcher(seedArgument);
        if (!m.find()) {
            return null;
        }
        try {
            return Long.parseLong(m.group());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
