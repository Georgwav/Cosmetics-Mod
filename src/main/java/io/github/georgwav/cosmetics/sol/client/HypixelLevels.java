package io.github.georgwav.cosmetics.sol.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.georgwav.cosmetics.sol.util.SolExecutors;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Players' Hypixel network levels, from Hypixel's public API with your own key: asked once per player (a player
 * Hypixel doesn't know, or a nicked one, is remembered as none), at most one request a second, kept for ten minutes.
 * Only real accounts are asked (version 4 ids; Hypixel's NPCs have others). The level formula is unit tested.
 */
public final class HypixelLevels {
    private static final long KEEP_MS = 10 * 60_000L;
    private static final long GAP_MS = 1_000L;

    private record Known(int level, long at) {
    }

    private static final Map<UUID, Known> KNOWN = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> ASKING = new ConcurrentHashMap<>();
    private static volatile long lastAsk;
    private static volatile boolean keyRefused;

    private HypixelLevels() {
    }

    /** Hypixel's network level for its experience: 1 at none, then a little more experience per level. */
    public static int level(double networkExp) {
        return (int) Math.floor(Math.sqrt(2.0 * Math.max(0.0, networkExp) + 30625.0) / 50.0 - 2.5);
    }

    /** The level if known (-1: none or not yet); asks for it when it isn't, with {@code key}. */
    public static int of(UUID player, String key) {
        if (player == null || player.version() != 4 || key == null || key.isBlank() || keyRefused) {
            return -1;
        }
        Known k = KNOWN.get(player);
        long now = System.currentTimeMillis();
        if (k != null && now - k.at < KEEP_MS) {
            return k.level;
        }
        if (now - lastAsk >= GAP_MS && ASKING.putIfAbsent(player, Boolean.TRUE) == null) {
            lastAsk = now;
            SolExecutors.IO.execute(() -> {
                try {
                    KNOWN.put(player, new Known(fetch(player, key), System.currentTimeMillis()));
                } finally {
                    ASKING.remove(player);
                }
            });
        }
        return k != null ? k.level : -1;
    }

    private static int fetch(UUID player, String key) {
        var reply = io.github.georgwav.cosmetics.sol.music.Http.get("https://api.hypixel.net/v2/player?uuid=" + player,
                Map.of("API-Key", key));
        if (reply == null) {
            return -1;
        }
        if (reply.status() == 403) {
            // A wrong or expired key: stop asking until it's changed.
            keyRefused = true;
            return -1;
        }
        try {
            JsonObject root = JsonParser.parseString(reply.body()).getAsJsonObject();
            if (!root.has("player") || root.get("player").isJsonNull()) {
                return -1;
            }
            JsonObject p = root.getAsJsonObject("player");
            return p.has("networkExp") ? level(p.get("networkExp").getAsDouble()) : 1;
        } catch (RuntimeException e) {
            return -1;
        }
    }

    /** A new key: ask again. */
    public static void keyChanged() {
        keyRefused = false;
        KNOWN.clear();
    }
}
