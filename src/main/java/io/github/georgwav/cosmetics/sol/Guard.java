package io.github.georgwav.cosmetics.sol;

import io.github.georgwav.cosmetics.CosmeticsMod;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Sol's error boundary. A feature that throws (or hits a missing class or method, a {@link LinkageError}) inside
 * the game's own code is switched off for the rest of the session, logged once with its stack trace, and reported
 * once to the player; the game keeps running. {@code -Dcosmeticsmod.fault=<feature>} throws inside that feature on
 * purpose, for the fault-injection test run.
 */
public final class Guard {
    /** Features switched off; failures come from the render thread and Sol's worker threads alike. */
    private static final Set<Feature> OFF = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final String FAULT = System.getProperty("cosmeticsmod.fault", "");
    /** Told once per failed feature (the notifications show it). */
    public static volatile Consumer<Feature> onFailure = f -> {
    };

    private Guard() {
    }

    public static boolean on(Feature feature) {
        return !OFF.contains(feature);
    }

    /** Runs {@code body} unless the feature is off; a failure switches the feature off. Returns whether it ran fine. */
    public static boolean run(Feature feature, Runnable body) {
        if (OFF.contains(feature)) {
            return false;
        }
        try {
            if (!FAULT.isEmpty() && FAULT.equals(feature.key)) {
                throw new IllegalStateException("Injected fault in " + feature.key);
            }
            body.run();
            return true;
        } catch (RuntimeException | LinkageError e) {
            fail(feature, e);
            return false;
        }
    }

    /** Switches a feature off after a failure caught elsewhere. */
    public static void fail(Feature feature, Throwable e) {
        if (OFF.add(feature)) {
            CosmeticsMod.LOGGER.error("[Sol] {} failed and is switched off for this session", feature.key, e);
            try {
                onFailure.accept(feature);
            } catch (RuntimeException ignored) {
                // The report itself must never take the game down.
            }
        }
    }

    /** Whether {@code -Dcosmeticsmod.fault} names this feature. */
    public static boolean faultInjected(Feature feature) {
        return FAULT.equals(feature.key);
    }

    /** The features switched off this session. */
    public static Set<Feature> failed() {
        return EnumSet.copyOf(OFF.isEmpty() ? EnumSet.noneOf(Feature.class) : OFF);
    }

    /** Allows a feature again (tests, and "Try again" in the notification). */
    public static void reset(Feature feature) {
        OFF.remove(feature);
    }
}
