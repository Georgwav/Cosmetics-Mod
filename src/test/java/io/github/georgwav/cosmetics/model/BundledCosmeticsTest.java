package io.github.georgwav.cosmetics.model;

import org.junit.jupiter.api.Test;

import java.io.Reader;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every cosmetic shipped in the jar must parse, bake and reference a texture that exists. */
class BundledCosmeticsTest {
    @Test
    void allBundledCosmeticsBake() throws Exception {
        URL marker = getClass().getClassLoader().getResource("assets/cosmeticsmod/cosmetics/top_hat.json");
        assertNotNull(marker, "bundled cosmetics not on the test classpath");
        Path dir = Path.of(marker.toURI()).getParent();
        Path assets = dir.getParent().getParent();
        List<Path> files;
        try (Stream<Path> stream = Files.list(dir)) {
            files = stream.filter(p -> p.toString().endsWith(".json")).toList();
        }
        assertFalse(files.isEmpty());
        for (Path file : files) {
            String name = file.getFileName().toString();
            String id = "cosmeticsmod:" + name.substring(0, name.length() - ".json".length());
            BakedCosmetic cosmetic;
            CosmeticParser.ModelSource models = modelId -> Files.newBufferedReader(assets
                    .resolve(modelId.substring(0, modelId.indexOf(':')))
                    .resolve("cosmetic_models/" + modelId.substring(modelId.indexOf(':') + 1) + ".geo.json"));
            try (Reader reader = Files.newBufferedReader(file)) {
                cosmetic = CosmeticBaker.bake(CosmeticParser.parse(id, reader, models));
            }
            assertTrue(cosmetic.layers().length > 0, id + " has no geometry");
            for (BakedCosmetic.Layer layer : cosmetic.layers()) {
                String texture = layer.texture();
                String namespace = texture.substring(0, texture.indexOf(':'));
                Path png = assets.resolve(namespace).resolve(texture.substring(texture.indexOf(':') + 1));
                assertTrue(Files.exists(png), id + " references missing texture " + texture);
            }
        }
    }
}
