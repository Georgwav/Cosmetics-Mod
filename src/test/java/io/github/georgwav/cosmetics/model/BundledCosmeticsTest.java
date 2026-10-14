package io.github.georgwav.cosmetics.model;

import org.junit.jupiter.api.Test;

import java.io.Reader;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every cosmetic shipped in the jar must parse without warnings, bake and reference a texture that exists. */
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
        // As the loader finds them: cosmetic_models/<name>.geo.json, or <name>.json (animation files).
        CosmeticParser.ModelSource models = modelId -> {
            Path folder = assets.resolve(modelId.substring(0, modelId.indexOf(':'))).resolve("cosmetic_models");
            String name = modelId.substring(modelId.indexOf(':') + 1);
            Path geo = folder.resolve(name + ".geo.json");
            return Files.newBufferedReader(Files.isRegularFile(geo) ? geo : folder.resolve(name + ".json"));
        };
        CosmeticParser.FileSource meshFiles = location -> Files.newInputStream(assets
                .resolve(location.substring(0, location.indexOf(':')))
                .resolve(location.substring(location.indexOf(':') + 1)));
        for (Path file : files) {
            String name = file.getFileName().toString();
            String id = "cosmeticsmod:" + name.substring(0, name.length() - ".json".length());
            List<String> warnings = new ArrayList<>();
            CosmeticParser.Context context = new CosmeticParser.Context(models, meshFiles, warnings::add,
                    CosmeticParser.Context.DEFAULT_MAX_MESH_TRIANGLES);
            CosmeticDefinition definition;
            try (Reader reader = Files.newBufferedReader(file)) {
                definition = CosmeticParser.parse(id, reader, context);
            }
            assertEquals(List.of(), warnings, id);
            for (CosmeticDefinition.Part part : definition.parts()) {
                for (CosmeticDefinition.Mesh mesh : part.meshes()) {
                    assertNormalsMatchWinding(id, mesh);
                }
            }
            BakedCosmetic cosmetic = CosmeticBaker.bake(definition);
            assertTrue(cosmetic.layers().length > 0 || !definition.particles().isEmpty(), id + " has no geometry or particles");
            for (CosmeticDefinition.ParticleEmitter emitter : definition.particles()) {
                for (String sprite : emitter.sprites()) {
                    String namespace = sprite.substring(0, sprite.indexOf(':'));
                    if (!namespace.equals("minecraft")) {
                        Path png = assets.resolve(namespace).resolve("textures/particle/"
                                + sprite.substring(sprite.indexOf(':') + 1) + ".png");
                        assertTrue(Files.exists(png), id + " uses a particle texture that doesn't exist: " + png);
                    }
                }
            }
            for (BakedCosmetic.Layer layer : cosmetic.layers()) {
                String texture = layer.texture();
                String namespace = texture.substring(0, texture.indexOf(':'));
                Path png = assets.resolve(namespace).resolve(texture.substring(texture.indexOf(':') + 1));
                assertTrue(Files.exists(png), id + " references missing texture " + texture);
            }
        }
    }

    /**
     * Each face's corners must wind counter-clockwise seen from the side their normals point to, or the
     * face is lit as if seen from behind (and culled from the wrong side by culling render types).
     */
    private static void assertNormalsMatchWinding(String id, CosmeticDefinition.Mesh mesh) {
        float[] c = mesh.corners();
        int stride = CosmeticDefinition.Mesh.FLOATS_PER_CORNER;
        int first = 0;
        for (int f = 0; f < mesh.faceSizes().length; f++) {
            int size = mesh.faceSizes()[f];
            float[][] positions = new float[size][];
            int[] face = new int[size];
            for (int i = 0; i < size; i++) {
                int o = (first + i) * stride;
                positions[i] = new float[]{c[o], c[o + 1], c[o + 2]};
                face[i] = i;
            }
            float[] winding = MeshBuilder.newell(face, positions);
            for (int i = 0; i < size; i++) {
                int o = (first + i) * stride;
                float dot = winding[0] * c[o + 5] + winding[1] * c[o + 6] + winding[2] * c[o + 7];
                assertTrue(dot > 0, id + " (" + mesh.source() + "): face " + f + " winds against its normals");
            }
            first += size;
        }
    }
}
