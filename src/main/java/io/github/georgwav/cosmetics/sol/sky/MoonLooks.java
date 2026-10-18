package io.github.georgwav.cosmetics.sol.sky;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Every moon look from the resource packs, and which one shows tonight: a special moon when its night comes (a blood
 * moon on some full moons, a rare blue moon, a supermoon), otherwise the theme's own. The same night always gives
 * the same look. Pure Java.
 */
public final class MoonLooks {
    private static volatile Map<String, MoonLook> looks = Map.of();
    private static volatile List<MoonLook> specials = List.of();

    private MoonLooks() {
    }

    /** Replaces the looks with these files (id to JSON text); problems go to {@code warn} (id, message). */
    public static void load(Map<String, String> files, BiConsumer<String, String> warn) {
        Map<String, MoonLook> map = new LinkedHashMap<>();
        List<MoonLook> special = new ArrayList<>();
        files.keySet().stream().sorted().forEach(id -> {
            MoonLook l = MoonLook.parse(id, files.get(id), m -> warn.accept(id, m));
            map.put(id, l);
            if (l.chance() > 0f) {
                special.add(l);
            }
        });
        looks = map;
        specials = List.copyOf(special);
    }

    public static MoonLook find(String id) {
        MoonLook l = id == null ? null : looks.get(id);
        return l != null ? l : MoonLook.SILVER;
    }

    public static Map<String, MoonLook> all() {
        return looks;
    }

    /**
     * Tonight's look: night {@code night} (counted from the world's start), the game's moon phase, and the theme's
     * own look. A special look is considered once every {@code minNightsBetween} nights, on a night of its phases,
     * and comes with its chance.
     */
    public static MoonLook tonight(long night, int phase, String themeLook) {
        for (MoonLook l : specials) {
            if (!l.allows(phase)) {
                continue;
            }
            int salt = l.id().hashCode();
            int every = Math.max(1, l.minNightsBetween());
            if (Math.floorMod(night + salt, every) != 0) {
                continue;
            }
            if (LabNoise.h3((int) night, salt, 77) < l.chance()) {
                return l;
            }
        }
        return find(themeLook);
    }
}
