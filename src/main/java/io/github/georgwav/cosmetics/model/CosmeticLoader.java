package io.github.georgwav.cosmetics.model;

import io.github.georgwav.cosmetics.CosmeticsMod;
import io.github.georgwav.cosmetics.render.RenderGuard;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.FileNotFoundException;
import java.io.Reader;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Loads every {@code assets/<namespace>/cosmetics/*.json} from the active resource packs and bakes it.
 * Cosmetics can use Bedrock geometry models from {@code assets/<namespace>/cosmetic_models/}.
 * A broken file is logged and skipped; it never stops the other cosmetics or the game from loading.
 */
public final class CosmeticLoader {
    private static final String DIRECTORY = "cosmetics";
    private static final String MODEL_DIRECTORY = "cosmetic_models";
    private static final String SUFFIX = ".json";
    private static final String GEO_SUFFIX = ".geo.json";

    private CosmeticLoader() {
    }

    public static void reload(ResourceManager manager) {
        // Model files by id ("namespace:name" for cosmetic_models/name.geo.json or name.json).
        Map<String, Resource> modelFiles = new HashMap<>();
        for (var entry : manager.listResources(MODEL_DIRECTORY, location -> location.getPath().endsWith(SUFFIX)).entrySet()) {
            String path = entry.getKey().getPath().substring(MODEL_DIRECTORY.length() + 1);
            path = path.substring(0, path.length() - (path.endsWith(GEO_SUFFIX) ? GEO_SUFFIX : SUFFIX).length());
            modelFiles.put(entry.getKey().getNamespace() + ":" + path, entry.getValue());
        }
        CosmeticParser.ModelSource models = modelId -> {
            Resource resource = modelFiles.get(modelId);
            if (resource == null) {
                throw new FileNotFoundException("no " + MODEL_DIRECTORY + " file for " + modelId);
            }
            return resource.openAsReader();
        };

        Map<String, BakedCosmetic> loaded = new LinkedHashMap<>();
        // `var`: the key type is ResourceLocation before 1.21.11 and Identifier after.
        for (var entry : manager.listResources(DIRECTORY, location -> location.getPath().endsWith(SUFFIX)).entrySet()) {
            String path = entry.getKey().getPath();
            String id = entry.getKey().getNamespace() + ":"
                    + path.substring(DIRECTORY.length() + 1, path.length() - SUFFIX.length());
            try (Reader reader = entry.getValue().openAsReader()) {
                loaded.put(id, CosmeticBaker.bake(CosmeticParser.parse(id, reader, models)));
            } catch (Exception e) {
                CosmeticsMod.LOGGER.error("Skipping cosmetic '{}': {}", id, e.toString());
            }
        }
        CosmeticRegistry.replace(loaded);
        RenderGuard.reset();
        CosmeticsMod.LOGGER.info("Loaded {} cosmetic(s)", loaded.size());
    }
}
