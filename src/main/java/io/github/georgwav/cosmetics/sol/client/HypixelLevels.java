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
 * A failed request (no network, Hypixel busy or limiting) is tried again after half a minute. Only real accounts
 * are asked (version 4 ids; Hypixel's NPCs have others). A new key starts afresh, and answers to requests made with
 * an older key are dropped. The level formula is unit tested.
 */
public final class HypixelLevels {
    private static final long KEEP_MS = 10 * 60_000L;
    private static final long RETRY_MS = 30_000L;
    private static final long GAP_MS = 1_000L;
    private static final int MAX_KNOWN = 2_000;
    /** A request that failed (not an answer): asked again after {@link #RETRY_MS}. */
    private static final int FAILED = -2;

    private record Known(int level, long at) {
    }

    private static final Map<UUID, Known> KNOWN = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> ASKING = new ConcurrentHashMap<>();
    private static volatile long lastAsk;
    private static volatile boolean keyRefused;
    private static volatile String keyInUse = "";
    /** Bumped with every new key: an answer to a request made under an older one is dropped. */
    private static final java.util.concurrent.atomic.AtomicInteger KEY_GENERATION = new java.util.concurrent.atomic.AtomicInteger();

    private HypixelLevels() {
    }

    /** Hypixel's network level for its experience: 1 at none, then a little more experience per level. */
    public static int level(double networkExp) {
        return (int) Math.floor(Math.sqrt(2.0 * Math.max(0.0, networkExp) + 30625.0) / 50.0 - 2.5);
    }

    /** The level if known (-1: none or not yet); asks for it when it isn't, with {@code key}. */
    public static int of(UUID player, String key) {
        if (key == null) {
            key = "";
        }
        if (!key.equals(keyInUse)) {
            keyInUse = key;
            keyChanged();
        }
        if (player == null || player.version() != 4 || key.isBlank() || keyRefused) {
            return -1;
        }
        Known k = KNOWN.get(player);
        long now = System.currentTimeMillis();
        if (k != null && now - k.at < (k.level == FAILED ? RETRY_MS : KEEP_MS)) {
            return Math.max(-1, k.level);
        }
        if (now - lastAsk >= GAP_MS && ASKING.putIfAbsent(player, Boolean.TRUE) == null) {
            lastAsk = now;
            String asked = key;
            int generation = KEY_GENERATION.get();
            SolExecutors.IO.execute(() -> {
                try {
                    int level = fetch(player, asked, generation);
                    if (KEY_GENERATION.get() == generation) {
                        if (KNOWN.size() >= MAX_KNOWN) {
                            KNOWN.clear();
                        }
                        KNOWN.put(player, new Known(level, System.currentTimeMillis()));
                    }
                } finally {
                    ASKING.remove(player);
                }
            });
        }
        return k != null ? Math.max(-1, k.level) : -1;
    }

    /** The player's level, -1 when Hypixel has none for them, or {@link #FAILED} when the request didn't work. */
    private static int fetch(UUID player, String key, int generation) {
        var reply = io.github.georgwav.cosmetics.sol.music.Http.get("https://api.hypixel.net/v2/player?uuid=" + player,
                Map.of("API-Key", key));
        if (reply == null || reply.status() < 0) {
            return FAILED;
        }
        if (reply.status() == 403) {
            // A wrong or expired key: stop asking until it's changed (unless a newer key came meanwhile).
            if (KEY_GENERATION.get() == generation) {
                keyRefused = true;
            }
            return FAILED;
        }
        if (!reply.ok()) {
            // Busy, limited (429) or down: try again later.
            return FAILED;
        }
        try {
            JsonObject root = JsonParser.parseString(reply.body()).getAsJsonObject();
            if (!root.has("player") || root.get("player").isJsonNull()) {
                return -1;
            }
            JsonObject p = root.getAsJsonObject("player");
            return p.has("networkExp") ? level(p.get("networkExp").getAsDouble()) : 1;
        } catch (RuntimeException e) {
            return FAILED;
        }
    }

    /** A new key: ask again. */
    private static void keyChanged() {
        KEY_GENERATION.incrementAndGet();
        keyRefused = false;
        KNOWN.clear();
    }
}
