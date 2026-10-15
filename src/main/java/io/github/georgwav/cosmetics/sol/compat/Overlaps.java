package io.github.georgwav.cosmetics.sol.compat;

import io.github.georgwav.cosmetics.sol.config.SolConfig;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Who does a job both Sol and an installed mod can do (D7): with the mod installed, the mod keeps it until the
 * player picks Sol on the Mods page; without it, Sol does it. Sol's part then simply stays still (its key isn't read),
 * so nothing happens twice.
 */
public final class Overlaps {
    private Overlaps() {
    }

    /** Whether Sol does the job {@code modId} could also do. */
    public static boolean solDoes(String modId) {
        if (!FabricLoader.getInstance().isModLoaded(modId)) {
            return true;
        }
        return "sol".equals(SolConfig.get().overlaps.get(modId));
    }

    /** Sol's zoom, unless Zoomify (or OK Zoomer) does it. */
    public static boolean solZooms() {
        return solDoes("zoomify") && solDoes("ok_zoomer");
    }

    /** Sol's freelook, unless Freelook (or Perspective) does it. */
    public static boolean solFreelooks() {
        return solDoes("freelook") && solDoes("perspectivemod");
    }
}
