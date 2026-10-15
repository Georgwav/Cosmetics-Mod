package io.github.georgwav.cosmetics.sol.client;

import io.github.georgwav.cosmetics.CosmeticsMod;
import io.github.georgwav.cosmetics.sol.config.SolConfig;
import net.minecraft.client.server.IntegratedServer;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Fast quit: leaving a single-player world goes straight to the menu while the world finishes saving on its own
 * thread. Safe because nothing can touch a world until every save is done: opening, editing or deleting any world
 * waits for them first, and so does closing the game.
 */
public final class FastQuit {
    private static final List<IntegratedServer> saving = new CopyOnWriteArrayList<>();
    private static final long WAIT_MS = 120_000L;

    private FastQuit() {
    }

    public static boolean on() {
        return SolConfig.get().fastQuit;
    }

    /**
     * The game asks whether its world has finished saving, to wait for it: with fast quit, it has (as far as the
     * client is concerned) and the server is remembered until it really has.
     */
    public static boolean shutdownOrLeave(IntegratedServer server) {
        if (server.isShutdown()) {
            return true;
        }
        if (!on()) {
            return false;
        }
        if (!saving.contains(server)) {
            saving.add(server);
            CosmeticsMod.LOGGER.info("[Sol] Fast quit: the world saves in the background");
        }
        return true;
    }

    /** Whether a world is still saving in the background. */
    public static boolean saving() {
        saving.removeIf(IntegratedServer::isShutdown);
        return !saving.isEmpty();
    }

    /** Waits (up to two minutes) for every background save; before any world is opened, edited or deleted, and on exit. */
    public static void waitForSaves() {
        long end = System.currentTimeMillis() + WAIT_MS;
        while (saving() && System.currentTimeMillis() < end) {
            try {
                Thread.sleep(10L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    public static void register() {
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> waitForSaves());
    }
}
