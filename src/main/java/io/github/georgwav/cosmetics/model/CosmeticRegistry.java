package io.github.georgwav.cosmetics.model;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * All cosmetics loaded from resource packs, baked. Replaced as a whole on every resource reload, so
 * the render thread always sees a consistent snapshot without locking.
 */
public final class CosmeticRegistry {
    private static volatile Map<String, BakedCosmetic> cosmetics = Map.of();
    private static volatile int generation;

    private CosmeticRegistry() {
    }

    public static BakedCosmetic get(String id) {
        return cosmetics.get(id);
    }

    public static Collection<BakedCosmetic> all() {
        return cosmetics.values();
    }

    /** Increments on every reload; caches compare against it to know when to rebuild. */
    public static int generation() {
        return generation;
    }

    public static void replace(Map<String, BakedCosmetic> loaded) {
        cosmetics = Collections.unmodifiableMap(new LinkedHashMap<>(loaded));
        generation++;
    }
}
