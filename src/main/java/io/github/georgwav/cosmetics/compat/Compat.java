package io.github.georgwav.cosmetics.compat;

import io.github.georgwav.cosmetics.CosmeticsMod;
import io.github.georgwav.cosmetics.compat.figura.FiguraCompat;
import io.github.georgwav.cosmetics.compat.iris.IrisCompat;
import net.fabricmc.loader.api.FabricLoader;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Single entry point for everything that depends on another mod being installed.
 *
 * <p>Rule: each {@code *Compat} class under this package references the other mod (by reflection),
 * and is only touched behind one of the {@code static final boolean} flags below, which come from
 * {@code FabricLoader.isModLoaded}. The JVM loads classes lazily, so with the mod missing the compat
 * class is never loaded and can never cause a classloading error. The only hard dependency is Fabric API.
 */
public final class Compat {
    public static final boolean IRIS = loaded("iris");
    public static final boolean FIGURA = loaded("figura");

    private Compat() {
    }

    private static boolean loaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    /** True while Iris renders its shadow map. Always false without Iris. */
    public static boolean isShadowPass() {
        return IRIS && IrisCompat.isRenderingShadowPass();
    }

    /** True if a Figura avatar is active for this player (it may hide or replace the vanilla model). */
    public static boolean hasFiguraAvatar(UUID player) {
        return FIGURA && FiguraCompat.hasAvatar(player);
    }

    public static void logEnvironment() {
        // Not used for behaviour, only to make bug reports and the compat matrix easier to fill in.
        Map<String, String> found = new LinkedHashMap<>();
        for (String id : new String[]{"sodium", "iris", "voxy", "distanthorizons", "immediatelyfast",
                "entityculling", "entity_model_features", "entity_texture_features", "skinlayers3d",
                "figura", "essential"}) {
            FabricLoader.getInstance().getModContainer(id).ifPresent(mod ->
                    found.put(id, mod.getMetadata().getVersion().getFriendlyString()));
        }
        CosmeticsMod.LOGGER.info("Detected mods relevant to cosmetics compatibility: {}", found.isEmpty() ? "none" : found);
    }
}
