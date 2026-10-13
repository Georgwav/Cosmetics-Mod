package io.github.georgwav.cosmetics.source;

import io.github.georgwav.cosmetics.CosmeticsMod;
import io.github.georgwav.cosmetics.config.CosmeticsConfig;
import io.github.georgwav.cosmetics.model.BakedCosmetic;
import io.github.georgwav.cosmetics.model.CosmeticRegistry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Which cosmetics a player wears. Resolved once per player and cached, so the render path is a single
 * hash lookup with no allocation. The cache is rebuilt after a resource reload or a config change.
 *
 * <p>Render thread only.
 */
public final class EquippedCosmetics {
    private static final BakedCosmetic[] NONE = new BakedCosmetic[0];
    private static final int MAX_CACHED_PLAYERS = 512;
    private static final Map<UUID, BakedCosmetic[]> CACHE = new HashMap<>();
    private static BakedCosmetic[] localCache;
    private static int cachedGeneration = -1;
    private static int configVersion;
    private static int cachedConfigVersion = -1;

    /** A cosmetic the settings screen shows on you while you point at it (trying it on), or null. */
    private static String previewId;
    /** When the screen last asked for the preview; it lapses on its own if the screen stops asking. */
    private static long previewTouched;
    private static final long PREVIEW_LAPSE_NANOS = 500_000_000L;

    /** Test/benchmark switch: every player wears every loaded cosmetic. */
    private static final boolean EQUIP_ALL = Boolean.getBoolean(CosmeticsMod.MOD_ID + ".equipAll");

    private EquippedCosmetics() {
    }

    /**
     * Shows {@code id} on the local player in place of what they wear in its slot, without equipping it;
     * null stops. The settings screen calls this every frame while something is pointed at, and the preview
     * lapses half a second after the last call, so it can't stay on by accident.
     */
    public static void preview(String id) {
        previewTouched = System.nanoTime();
        if (!java.util.Objects.equals(id, previewId)) {
            previewId = id;
            localCache = null;
        }
    }

    /** Call after changing {@link CosmeticsConfig#equipped}. */
    public static void invalidate() {
        configVersion++;
    }

    public static BakedCosmetic[] forPlayer(UUID player, boolean local) {
        int generation = CosmeticRegistry.generation();
        if (generation != cachedGeneration || configVersion != cachedConfigVersion) {
            CACHE.clear();
            localCache = null;
            cachedGeneration = generation;
            cachedConfigVersion = configVersion;
        }
        if (local) {
            if (previewId != null && System.nanoTime() - previewTouched > PREVIEW_LAPSE_NANOS) {
                previewId = null;
                localCache = null;
            }
            if (localCache == null) {
                localCache = withPreview(resolve(player.toString(), "local"));
            }
            return localCache;
        }
        BakedCosmetic[] cached = CACHE.get(player);
        if (cached == null) {
            if (CACHE.size() >= MAX_CACHED_PLAYERS) {
                // Bounded memory on big servers; re-resolving is cheap.
                CACHE.clear();
            }
            cached = resolve(player.toString(), null);
            CACHE.put(player, cached);
        }
        return cached;
    }

    /** What the local player wears with the preview (if any) in place of the cosmetics in its slot. */
    private static BakedCosmetic[] withPreview(BakedCosmetic[] worn) {
        BakedCosmetic shown = previewId != null && !EQUIP_ALL ? CosmeticRegistry.get(previewId) : null;
        if (shown == null) {
            return worn;
        }
        List<BakedCosmetic> result = new ArrayList<>(worn.length + 1);
        for (BakedCosmetic cosmetic : worn) {
            if (cosmetic.slot() != shown.slot()) {
                result.add(cosmetic);
            }
        }
        result.add(shown);
        return result.toArray(NONE);
    }

    private static BakedCosmetic[] resolve(String uuidKey, String aliasKey) {
        if (EQUIP_ALL) {
            Collection<BakedCosmetic> all = CosmeticRegistry.all();
            return all.toArray(NONE);
        }
        Map<String, List<String>> equipped = CosmeticsConfig.get().equipped;
        List<String> ids = new ArrayList<>();
        if (equipped.get(uuidKey) != null) {
            ids.addAll(equipped.get(uuidKey));
        }
        if (aliasKey != null && equipped.get(aliasKey) != null) {
            ids.addAll(equipped.get(aliasKey));
        }
        if (ids.isEmpty()) {
            return NONE;
        }
        List<BakedCosmetic> result = new ArrayList<>(ids.size());
        for (String id : ids) {
            BakedCosmetic cosmetic = CosmeticRegistry.get(id);
            if (cosmetic == null) {
                CosmeticsMod.LOGGER.warn("Equipped cosmetic '{}' does not exist (check the id and your resource packs)", id);
            } else if (!result.contains(cosmetic)) {
                result.add(cosmetic);
            }
        }
        return result.toArray(NONE);
    }
}
