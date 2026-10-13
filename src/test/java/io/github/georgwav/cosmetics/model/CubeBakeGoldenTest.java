package io.github.georgwav.cosmetics.model;

import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Cube baking must stay byte-identical: these SHA-256 hashes of the baked vertex data (every float's
 * raw bits, layer by layer) were produced by the cube baker as first released. A change to how cubes
 * are baked, however small, shows up here.
 */
class CubeBakeGoldenTest {
    private static final String[][] DEFINITIONS = {
            {"""
                    {"slot":"head","texture":"t:a.png","parts":[{"cubes":[{"from":[-3,-9,-2.5],"to":[2.25,-4,3],"uv":[3,7],"pivot":[1.5,-6,0.25],"rotation":[17.5,-33,61],"inflate":0.3,"mirror":true}]}]}""",
                    "a881b27f983012a166d7cdff84ca76ba8c7be774094661b994cfeb6f75abe837"},
            {"""
                    {"slot":"back","texture":"t:a.png","parts":[{"cubes":[{"from":[0,0,0],"to":[4,5,6],"faces":{"north":[0,0,4,5],"up":[4,0,8,6]},"pivot":[-2,3,1],"rotation":[0,45,0]}]}]}""",
                    "8abed3ad642d472df902deab28fc518accdc26e180dc8172b72218cfd49b4190"},
            {"""
                    {"slot":"body","texture":"t:a.png","texture_size":[32,32],"parts":[{"cubes":[{"from":[-1,2,-3],"to":[1,7,-1],"uv":[0,0],"pivot":[0.5,4.5,-2],"rotation":[-22.5,0,0]},{"from":[2,0,0],"to":[3,1,1],"uv":[8,8],"pivot":[2.5,0.5,0.5],"rotation":[0,0,37]}]},{"mode":"emissive","texture":"t:g.png","cubes":[{"from":[0,0,0],"to":[1,1,1],"uv":[0,0]}]}]}""",
                    "9c4e14e9936617480d7e046d7f9c127f59fda880780b005c9a2ee348f467b0ad"},
    };

    private static final String[][] BUNDLED = {
            {"top_hat", "add22527f213542c36daaac325936f4428613d95f0f5513434df566aef0afce5"},
            {"halo", "35e30b3f3060c71aa6965d29f309b14e11a1bc929308554aab804495dccf7aa2"},
            {"explorer_backpack", "da77417d9f6cca3e7860eb10925112914d0bf2174f9b3a587fe9f96f8a451be0"},
            {"starlight_cape", "0a0060e699305f0c58cd67b01f3e3063c54fc281bc6c40b5d1180d5476c47b00"},
    };

    static String hash(BakedCosmetic cosmetic) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (BakedCosmetic.Layer layer : cosmetic.layers()) {
            for (float f : layer.mesh().data()) {
                int bits = Float.floatToRawIntBits(f);
                digest.update(new byte[]{(byte) (bits >> 24), (byte) (bits >> 16), (byte) (bits >> 8), (byte) bits});
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    @Test
    void handWrittenCubesBakeExactlyAsBefore() throws Exception {
        for (String[] definition : DEFINITIONS) {
            BakedCosmetic cosmetic = CosmeticBaker.bake(CosmeticParser.parse("t:x", new StringReader(definition[0])));
            assertEquals(definition[1], hash(cosmetic), definition[0]);
        }
    }

    @Test
    void bundledCubeCosmeticsBakeExactlyAsBefore() throws Exception {
        for (String[] bundled : BUNDLED) {
            var stream = getClass().getClassLoader().getResourceAsStream("assets/cosmeticsmod/cosmetics/" + bundled[0] + ".json");
            assertNotNull(stream, bundled[0]);
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                assertEquals(bundled[1], hash(CosmeticBaker.bake(CosmeticParser.parse("cosmeticsmod:" + bundled[0], reader))), bundled[0]);
            }
        }
    }
}
