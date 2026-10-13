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

    /** True if Iris is installed and a shaderpack is currently active. */
    public static boolean isShaderPackActive() {
        return IRIS && IrisCompat.isShaderPackInUse();
    }

    /** True if a Figura avatar is active for this player (it may hide or replace the vanilla model). */
    public static boolean hasFiguraAvatar(UUID player) {
        return FIGURA && FiguraCompat.hasAvatar(player);
    }

    /** Mods that matter for cosmetics, by mod id, with a display name. */
    private static final String[][] TRACKED = {
            {"sodium", "Sodium"}, {"iris", "Iris"}, {"voxy", "Voxy"}, {"distanthorizons", "Distant Horizons"},
            {"immediatelyfast", "ImmediatelyFast"}, {"entityculling", "Entity Culling"},
            {"entity_model_features", "Entity Model Features"}, {"entity_texture_features", "Entity Texture Features"},
            {"skinlayers3d", "3D Skin Layers"}, {"figura", "Figura"}, {"essential", "Essential"},
            {"modmenu", "Mod Menu"},
    };

    /** Installed tracked mods: display name to version. Used by the log and the settings screen. */
    public static Map<String, String> detectedMods() {
        Map<String, String> found = new LinkedHashMap<>();
        for (String[] mod : TRACKED) {
            FabricLoader.getInstance().getModContainer(mod[0]).ifPresent(container ->
                    found.put(mod[1], container.getMetadata().getVersion().getFriendlyString()));
        }
        return found;
    }

    public static void logEnvironment() {
        // Not used for behaviour, only to make bug reports and the compat matrix easier to fill in.
        Map<String, String> found = detectedMods();
        CosmeticsMod.LOGGER.info("Detected mods relevant to cosmetics compatibility: {}", found.isEmpty() ? "none" : found);
    }
}
