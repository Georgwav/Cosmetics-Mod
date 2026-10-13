package io.github.georgwav.cosmetics.model;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BedrockGeometryTest {
    private static final float EPS = 1.0e-4f;
    private static final BedrockGeometry.BoneOptions NONE = new BedrockGeometry.BoneOptions(Map.of(), Map.of(), Map.of());

    private static BedrockGeometry.Model convert(String bones) {
        String json = """
                {"format_version": "1.12.0", "minecraft:geometry": [{
                  "description": {"identifier": "geometry.test", "texture_width": 64, "texture_height": 64},
                  "bones": %s}]}
                """.formatted(bones);
        return BedrockGeometry.convert(JsonParser.parseString(json).getAsJsonObject(), "test", "test:t.png", NONE, List.of());
    }

    private static CosmeticDefinition.Cube onlyCube(BedrockGeometry.Model model) {
        return model.parts().get(0).cubes().get(0);
    }

    @Test
    void vanillaPlayerPartsLineUpWithTheJavaModel() {
        // The Bedrock player model's head, body and right arm, as in the vanilla Bedrock resource pack...
        BedrockGeometry.Model model = convert("""
                [{"name": "head", "pivot": [0, 24, 0], "cubes": [{"origin": [-4, 24, -4], "size": [8, 8, 8], "uv": [0, 0]}]},
                 {"name": "body", "pivot": [0, 24, 0], "cubes": [{"origin": [-4, 12, -2], "size": [8, 12, 4], "uv": [16, 16]}]},
                 {"name": "rightArm", "pivot": [-5, 22, 0], "cubes": [{"origin": [-8, 12, -2], "size": [4, 12, 4], "uv": [40, 16]}]}]
                """);
        // ...are exactly the Java PlayerModel boxes (in model-root space).
        CosmeticDefinition.Cube head = model.parts().get(0).cubes().get(0);
        assertArrayEquals(new float[]{-4, -8, -4}, head.from(), EPS);
        assertArrayEquals(new float[]{4, 0, 4}, head.to(), EPS);
        assertArrayEquals(new float[]{0, 0}, head.boxUv(), EPS);
        CosmeticDefinition.Cube body = model.parts().get(1).cubes().get(0);
        assertArrayEquals(new float[]{-4, 0, -2}, body.from(), EPS);
        assertArrayEquals(new float[]{4, 12, 2}, body.to(), EPS);
        CosmeticDefinition.Cube arm = model.parts().get(2).cubes().get(0);
        assertArrayEquals(new float[]{-8, 0, -2}, arm.from(), EPS);
        assertArrayEquals(new float[]{-4, 12, 2}, arm.to(), EPS);
        assertEquals(64, model.textureWidth());
    }

    @Test
    void anUnrotatedModelBakesLikeTheSameNativeCosmetic() {
        BakedMesh imported = CosmeticBaker.bake(new CosmeticDefinition("test:a", "A", CosmeticSlot.HEAD, "test:t.png", 64, 64,
                convert("""
                        [{"name": "hat", "pivot": [0, 24, 0], "cubes": [{"origin": [-3, 32, -3], "size": [6, 4, 6], "uv": [0, 0]}]}]
                        """).parts())).layers()[0].mesh();
        BakedMesh nativeMesh = CosmeticBaker.bake(CosmeticParser.parse("test:b", new StringReader("""
                {"slot": "head", "texture": "test:t.png",
                 "parts": [{"cubes": [{"from": [-3, -12, -3], "to": [3, -8, 3], "uv": [0, 0]}]}]}
                """))).layers()[0].mesh();
        assertArrayEquals(nativeMesh.data(), imported.data(), EPS);
    }

    @Test
    void rotationsFlipWithTheYAxis() {
        // Bedrock [x, y, z] degrees is [-x, y, -z] for us; spinning around Y keeps its sign.
        CosmeticDefinition.Cube cube = onlyCube(convert("""
                [{"name": "b", "cubes": [{"origin": [0, 0, 0], "size": [1, 1, 1], "uv": [0, 0],
                                          "pivot": [0, 24, 0], "rotation": [10, 20, 30]}]}]
                """));
        float[] expected = CosmeticBaker.rotationMatrix(new float[]{-10, 20, -30});
        for (int i = 0; i < 9; i++) {
            assertEquals(expected[i], cube.transform()[i], EPS);
        }
        // The pivot (0, 24, 0) is our origin, so there's no translation.
        assertArrayEquals(new float[]{0, 0, 0}, new float[]{cube.transform()[9], cube.transform()[10], cube.transform()[11]}, EPS);
    }

    @Test
    void parentBonesRotateTheirChildren() {
        // A child cube one pixel to the +X side of a parent that turns 90 degrees around Y.
        CosmeticDefinition.Cube cube = onlyCube(convert("""
                [{"name": "parent", "pivot": [0, 24, 0], "rotation": [0, 90, 0]},
                 {"name": "child", "parent": "parent", "pivot": [0, 24, 0],
                  "cubes": [{"origin": [1, 24, 0], "size": [0, 0, 0], "uv": [0, 0]}]}]
                """));
        float[] p = CosmeticBaker.rotate(cube.transform(), 1, 0, 0);
        // Ry(90) takes +X to -Z.
        assertArrayEquals(new float[]{0, 0, -1}, new float[]{p[0] + cube.transform()[9], p[1] + cube.transform()[10], p[2] + cube.transform()[11]}, EPS);
    }

    @Test
    void topAndBottomFacesSwap() {
        CosmeticDefinition.Cube cube = onlyCube(convert("""
                [{"name": "b", "cubes": [{"origin": [0, 0, 0], "size": [2, 2, 2],
                  "uv": {"up": {"uv": [0, 0], "uv_size": [2, 2]}, "down": {"uv": [4, 4], "uv_size": [2, -2]},
                         "north": {"uv": [8, 0], "uv_size": [2, 2]}}}]}]
                """));
        assertArrayEquals(new float[]{0, 0, 2, 2}, cube.faces()[CosmeticDefinition.Face.DOWN.ordinal()], EPS);
        assertArrayEquals(new float[]{4, 4, 6, 2}, cube.faces()[CosmeticDefinition.Face.UP.ordinal()], EPS);
        assertArrayEquals(new float[]{8, 0, 10, 2}, cube.faces()[CosmeticDefinition.Face.NORTH.ordinal()], EPS);
        assertEquals(null, cube.faces()[CosmeticDefinition.Face.SOUTH.ordinal()]);
    }

    @Test
    void cosmeticFileSetsBoneModesTexturesAndAnimations() {
        String geo = """
                {"format_version": "1.12.0", "minecraft:geometry": [{"description": {"texture_width": 32, "texture_height": 16},
                 "bones": [{"name": "Frame", "cubes": [{"origin": [0, 30, 0], "size": [1, 1, 1], "uv": [0, 0]}]},
                           {"name": "Gem", "pivot": [2, 30, 0], "cubes": [{"origin": [2, 30, 0], "size": [1, 1, 1], "uv": [8, 0]}]}]}]}
                """;
        CosmeticDefinition definition = CosmeticParser.parse("pack:thing", new StringReader("""
                {"slot": "head", "texture": "textures/thing.png", "model": "thing",
                 "bones": {"gem": {"mode": "emissive", "texture": "textures/glow.png", "animation": {"type": "spin"}}}}
                """), modelId -> {
            assertEquals("pack:thing", modelId);
            return new StringReader(geo);
        });
        assertEquals(32, definition.textureWidth());
        assertEquals(RenderMode.CUTOUT, definition.parts().get(0).mode());
        CosmeticDefinition.Part gem = definition.parts().get(1);
        assertEquals(RenderMode.EMISSIVE, gem.mode());
        assertEquals("pack:textures/glow.png", gem.texture());
        // The spin turns around the bone's pivot, (2, 30, 0) in Bedrock space.
        assertEquals(2f, gem.animations().get(0).pivotX(), EPS);
        assertEquals(-6f, gem.animations().get(0).pivotY(), EPS);
    }

    @Test
    void notAGeometryFileIsAClearError() {
        assertThrows(RuntimeException.class, () -> CosmeticParser.parse("pack:thing", new StringReader("""
                {"slot": "head", "texture": "t.png", "model": "thing"}
                """), modelId -> new StringReader("{\"parent\": \"block/cube\"}")));
    }
}
