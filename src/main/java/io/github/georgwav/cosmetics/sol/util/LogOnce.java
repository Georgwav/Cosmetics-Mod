package io.github.georgwav.cosmetics.sol.util;

import io.github.georgwav.cosmetics.CosmeticsMod;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Warnings that are logged once per key, so nothing Sol does ever repeats a line in the log. */
public final class LogOnce {
    private static final Set<String> SEEN = ConcurrentHashMap.newKeySet();

    private LogOnce() {
    }

    /** Logs the warning the first time {@code key} is seen; returns whether it was logged. */
    public static boolean warn(String key, String message, Object... args) {
        if (!SEEN.add(key)) {
            return false;
        }
        CosmeticsMod.LOGGER.warn("[Sol] " + message, args);
        return true;
    }

    /** Logs the info line the first time {@code key} is seen. */
    public static boolean info(String key, String message, Object... args) {
        if (!SEEN.add(key)) {
            return false;
        }
        CosmeticsMod.LOGGER.info("[Sol] " + message, args);
        return true;
    }

    /** For tests: forget what was logged. */
    public static void reset() {
        SEEN.clear();
    }
}
