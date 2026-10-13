package io.github.georgwav.cosmetics.model;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Checks a resource pack written by a tool (such as tools/badlion-import) against the real parser: every cosmetic
 * loads without warnings and bakes, and every texture it names exists with the size its {@code texture_size}
 * and frame count say. Skipped unless {@code -Dcosmetics.validatePack=<pack folder>} is given (CI passes a pack
 * the importer made from synthetic files).
 */
class ImportedPackTest {
    @Test
    void everyCosmeticInThePackLoads() throws Exception {
        String folder = System.getProperty("cosmetics.validatePack");
        assumeTrue(folder != null && !folder.isEmpty(), "no pack given");
        Path assets = Path.of(folder).resolve("assets");
        List<Path> files;
        try (Stream<Path> stream = Files.walk(assets)) {
            files = stream.filter(p -> p.getParent().getFileName().toString().equals("cosmetics")
                    && p.toString().endsWith(".json")).sorted().toList();
        }
        assertFalse(files.isEmpty(), "the pack has no cosmetics");
        CosmeticParser.FileSource packFiles = location -> Files.newInputStream(resolve(assets, location));
        CosmeticParser.ModelSource models = modelId -> {
            throw new java.io.FileNotFoundException(modelId);
        };
        for (Path file : files) {
            String namespace = file.getParent().getParent().getFileName().toString();
            String name = file.getFileName().toString();
            String id = namespace + ":" + name.substring(0, name.length() - ".json".length());
            List<String> warnings = new ArrayList<>();
            CosmeticParser.Context context = new CosmeticParser.Context(models, packFiles, warnings::add,
                    CosmeticParser.Context.DEFAULT_MAX_MESH_TRIANGLES);
            CosmeticDefinition definition;
            try (Reader reader = Files.newBufferedReader(file)) {
                definition = CosmeticParser.parse(id, reader, context);
            }
            assertEquals(List.of(), warnings, id);
            BakedCosmetic cosmetic = CosmeticBaker.bake(definition);
            assertTrue(cosmetic.layers().length > 0, id + " has no geometry");

            JsonObject json;
            try (Reader reader = Files.newBufferedReader(file)) {
                json = JsonParser.parseReader(reader).getAsJsonObject();
            }
            int frames = json.has("texture_animation") ? json.getAsJsonObject("texture_animation").get("frames").getAsInt() : 1;
            BufferedImage image = ImageIO.read(resolve(assets, definition.texture()).toFile());
            assertNotNull(image, id + ": texture can't be read: " + definition.texture());
            assertEquals(definition.textureWidth(), image.getWidth(), id + ": texture width");
            assertEquals(definition.textureHeight() * frames, image.getHeight(), id + ": texture height (frames stacked)");
            for (BakedCosmetic.Layer layer : cosmetic.layers()) {
                assertTrue(Files.exists(resolve(assets, layer.texture())), id + " references missing texture " + layer.texture());
            }
        }
    }

    private static Path resolve(Path assets, String location) {
        int colon = location.indexOf(':');
        return assets.resolve(location.substring(0, colon)).resolve(location.substring(colon + 1));
    }
}
