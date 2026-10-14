package io.github.georgwav.cosmetics.model;

import io.github.georgwav.cosmetics.CosmeticsMod;
import io.github.georgwav.cosmetics.config.CosmeticsConfig;
import io.github.georgwav.cosmetics.emote.Emotes;
import io.github.georgwav.cosmetics.render.RenderGuard;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
//? if >=1.21.11 {
import net.minecraft.resources.Identifier;
//?} else {
/*import net.minecraft.resources.ResourceLocation;
*///?}

import java.io.FileNotFoundException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads every {@code assets/<namespace>/cosmetics/*.json} from the active resource packs and bakes it.
 * Cosmetics can use Bedrock geometry models from {@code assets/<namespace>/cosmetic_models/} and mesh
 * files ({@code .obj}, {@code .bbmodel}) by their full location, such as
 * {@code namespace:models/cosmetic/name.obj}.
 * A broken file is logged and skipped; it never stops the other cosmetics or the game from loading.
 * Emotes ({@code assets/<namespace>/emotes/}) are loaded along with them, see {@link Emotes}.
 */
public final class CosmeticLoader {
    private static final String DIRECTORY = "cosmetics";
    private static final String MODEL_DIRECTORY = "cosmetic_models";
    private static final String SUFFIX = ".json";
    private static final String GEO_SUFFIX = ".geo.json";
    /**
     * Imported packs' namespaces and the import format the mod's fixes need: a pack whose
     * {@code <namespace>:cosmetics_import.json} says less (or that has none) was made by an older importer, and the
     * Wardrobe says to import it again. Badlion 2: capes the vanilla length, wings sized and placed; 3: shields
     * facing as Badlion has them in each view; 4: every forward-bending wing model turned around.
     */
    private static final Map<String, Integer> IMPORT_FORMATS = Map.of("blc_import", 4);
    private static final String IMPORT_STAMP = "cosmetics_import.json";

    /** Cosmetics the last reload couldn't load, as "id: why", and imported namespaces that are out of date. */
    private static volatile List<String> failures = List.of();
    private static volatile List<String> outdatedImports = List.of();

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

        // Mesh files are opened by their full location when a cosmetic asks for one (the parser has
        // already checked it's a valid .obj or .bbmodel location), so nothing is scanned up front.
        // Vanilla only reads .json files under models/, so these never reach the block and item models.
        CosmeticParser.FileSource files = location -> {
            //? if >=1.21.11 {
            Identifier id = Identifier.parse(location);
            //?} else {
            /*ResourceLocation id = ResourceLocation.parse(location);
            *///?}
            Resource resource = manager.getResource(id).orElse(null);
            if (resource == null) {
                throw new FileNotFoundException("no such file in any resource pack");
            }
            return resource.open();
        };
        int maxMeshTriangles = CosmeticsConfig.get().maxMeshTriangles;

        // Parsed and baked on every core: a pack of thousands of cosmetics would otherwise hold up the loading
        // screen for a long time. Parsing and baking share no state, and packs are read-only here.
        long start = System.nanoTime();
        // `var`: the key type is ResourceLocation before 1.21.11 and Identifier after.
        var entries = new java.util.ArrayList<>(
                manager.listResources(DIRECTORY, location -> location.getPath().endsWith(SUFFIX)).entrySet());
        String[] ids = new String[entries.size()];
        BakedCosmetic[] baked = new BakedCosmetic[entries.size()];
        String[] failed = new String[entries.size()];
        java.util.stream.IntStream.range(0, entries.size()).parallel().forEach(i -> {
            var entry = entries.get(i);
            String path = entry.getKey().getPath();
            String id = entry.getKey().getNamespace() + ":"
                    + path.substring(DIRECTORY.length() + 1, path.length() - SUFFIX.length());
            String file = "assets/" + entry.getKey().getNamespace() + "/" + path;
            ids[i] = id;
            CosmeticParser.Context context = new CosmeticParser.Context(models, files,
                    warning -> CosmeticsMod.LOGGER.warn("Cosmetic '{}' ({}): {}", id, file, warning), maxMeshTriangles);
            try (Reader reader = entry.getValue().openAsReader()) {
                baked[i] = CosmeticBaker.bake(CosmeticParser.parse(id, reader, context));
            } catch (Exception e) {
                CosmeticsMod.LOGGER.error("Skipping cosmetic '{}' ({}): {}", id, file, e.toString());
                failed[i] = id + ": " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            }
        });
        Map<String, BakedCosmetic> loaded = new LinkedHashMap<>();
        for (int i = 0; i < baked.length; i++) {
            if (baked[i] != null) {
                loaded.put(ids[i], baked[i]);
            }
        }
        CosmeticRegistry.replace(loaded);
        List<String> failedList = new ArrayList<>();
        for (String f : failed) {
            if (f != null) {
                failedList.add(f);
            }
        }
        failures = List.copyOf(failedList);
        outdatedImports = outdated(manager, ids);
        RenderGuard.reset();
        io.github.georgwav.cosmetics.render.RecoloredTextures.clear();
        CosmeticsMod.LOGGER.info("Loaded {} cosmetic(s) in {} ms", loaded.size(), (System.nanoTime() - start) / 1_000_000);
        Emotes.reload(manager);
    }

    /** Cosmetics the last resource reload couldn't load (the Wardrobe lists them), as "id: why". */
    public static List<String> failures() {
        return failures;
    }

    /** Imported packs' namespaces with cosmetics from an importer older than the mod's fixes need. */
    public static List<String> outdatedImports() {
        return outdatedImports;
    }

    private static List<String> outdated(ResourceManager manager, String[] ids) {
        List<String> out = new ArrayList<>();
        for (var need : IMPORT_FORMATS.entrySet()) {
            String prefix = need.getKey() + ":";
            if (java.util.Arrays.stream(ids).noneMatch(id -> id != null && id.startsWith(prefix))) {
                continue;
            }
            //? if >=1.21.11 {
            Identifier stamp = Identifier.fromNamespaceAndPath(need.getKey(), IMPORT_STAMP);
            //?} else {
            /*ResourceLocation stamp = ResourceLocation.fromNamespaceAndPath(need.getKey(), IMPORT_STAMP);
            *///?}
            int format = 0;
            Resource resource = manager.getResource(stamp).orElse(null);
            if (resource != null) {
                try (Reader reader = resource.openAsReader()) {
                    var json = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
                    format = json.has("format") ? json.get("format").getAsInt() : 0;
                } catch (Exception e) {
                    CosmeticsMod.LOGGER.warn("Couldn't read {}: {}", stamp, e.toString());
                }
            }
            if (format < need.getValue()) {
                out.add(need.getKey());
            }
        }
        return List.copyOf(out);
    }
}
