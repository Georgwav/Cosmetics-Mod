package io.github.georgwav.cosmetics.render;

import io.github.georgwav.cosmetics.CosmeticsMod;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Crash containment for the render path. If a cosmetic throws while rendering, that one cosmetic is
 * switched off for the rest of the session and the error is logged once; every other cosmetic and the
 * game keep running. A failure that can't be pinned on a single cosmetic switches off the whole layer.
 *
 * <p>Reloading resources (F3+T) clears the list so a fixed resource pack can be retried without
 * restarting.
 */
public final class RenderGuard {
    private static final Set<String> DISABLED = ConcurrentHashMap.newKeySet();
    private static final AtomicInteger FAILURES = new AtomicInteger();
    private static volatile boolean layerDisabled;

    private RenderGuard() {
    }

    public static boolean isLayerDisabled() {
        return layerDisabled;
    }

    public static boolean isDisabled(String cosmeticId) {
        return !DISABLED.isEmpty() && DISABLED.contains(cosmeticId);
    }

    /** Called when rendering one cosmetic threw. */
    public static void cosmeticFailed(String cosmeticId, Throwable error) {
        FAILURES.incrementAndGet();
        if (DISABLED.add(cosmeticId)) {
            CosmeticsMod.LOGGER.error("Cosmetic '{}' failed to render and is disabled for this session. "
                    + "The game keeps running; press F3+T to retry after fixing it.", cosmeticId, error);
        }
    }

    /** Called when the layer itself threw outside of any single cosmetic. */
    public static void layerFailed(Throwable error) {
        FAILURES.incrementAndGet();
        if (!layerDisabled) {
            layerDisabled = true;
            CosmeticsMod.LOGGER.error("The cosmetics render layer failed and is disabled for this session. "
                    + "The game keeps running; press F3+T to retry.", error);
        }
    }

    public static int failureCount() {
        return FAILURES.get();
    }

    /** Re-enables everything; called on resource reload. */
    public static void reset() {
        DISABLED.clear();
        layerDisabled = false;
    }
}
