package io.github.georgwav.cosmetics.model;

import com.google.gson.JsonParseException;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextureAnimationTest {
    private static final String CUBE = "{\"from\":[0,0,0],\"to\":[2,2,2],\"uv\":[0,0]}";

    private static BakedCosmetic bake(String json) {
        return CosmeticBaker.bake(CosmeticParser.parse("t:x", new StringReader(json)));
    }

    @Test
    void stripFramesScaleVAndOffsetPerFrame() {
        BakedCosmetic still = bake("{\"slot\":\"head\",\"texture\":\"t:a.png\",\"texture_size\":[16,16],"
                + "\"parts\":[{\"cubes\":[" + CUBE + "]}]}");
        BakedCosmetic strip = bake("{\"slot\":\"head\",\"texture\":\"t:a.png\",\"texture_size\":[16,16],"
                + "\"texture_animation\":{\"frames\":4,\"frame_time\":50},\"parts\":[{\"cubes\":[" + CUBE + "]}]}");
        float[] a = still.layers()[0].mesh().data();
        float[] b = strip.layers()[0].mesh().data();
        assertEquals(a.length, b.length);
        for (int i = 0; i < a.length; i += BakedMesh.FLOATS_PER_VERTEX) {
            assertEquals(a[i + 3], b[i + 3], 1e-7f, "u stays");
            assertEquals(a[i + 4] / 4f, b[i + 4], 1e-7f, "v addresses the first quarter of the strip");
        }
        TextureFrames frames = strip.layers()[0].textureFrames();
        assertTrue(frames.strip());
        assertEquals(0f, frames.vOffset(0));
        assertEquals(0.75f, frames.vOffset(3));
        assertEquals(0, frames.frameAt(0.0, 1f));
        assertEquals(1, frames.frameAt(0.051, 1f));
        assertEquals(3, frames.frameAt(0.199, 1f));
        assertEquals(0, frames.frameAt(0.201, 1f), "loops after the last frame");
        assertEquals(2, frames.frameAt(0.051, 2f), "speed scales time");
        assertTrue(strip.hasAnimation(), "the animation settings apply");
        assertFalse(still.hasAnimation());
        assertNull(still.layers()[0].textureFrames());
    }

    @Test
    void perFrameTimes() {
        TextureFrames frames = bake("{\"slot\":\"head\",\"texture\":\"t:a.png\",\"texture_animation\":"
                + "{\"frames\":3,\"frame_times\":[100,20,300]},\"parts\":[{\"cubes\":[" + CUBE + "]}]}")
                .layers()[0].textureFrames();
        assertEquals(0, frames.frameAt(0.0995, 1f));
        assertEquals(1, frames.frameAt(0.1005, 1f));
        assertEquals(1, frames.frameAt(0.1195, 1f));
        assertEquals(2, frames.frameAt(0.1205, 1f));
        assertEquals(2, frames.frameAt(0.4195, 1f));
        assertEquals(0, frames.frameAt(0.4205, 1f));
        assertEquals(1, frames.frameAt(1000 * 0.42 + 0.110, 1f), "long running clocks stay exact enough");
    }

    @Test
    void oneFilePerFrame() {
        BakedCosmetic cosmetic = bake("{\"slot\":\"head\",\"texture\":\"t:a.png\",\"texture_size\":[16,16],"
                + "\"texture_animation\":{\"textures\":[\"a0.png\",\"other:a1.png\"],\"frame_time\":80},"
                + "\"parts\":[{\"cubes\":[" + CUBE + "]}]}");
        TextureFrames frames = cosmetic.layers()[0].textureFrames();
        assertFalse(frames.strip());
        assertEquals(2, frames.count());
        assertEquals("t:a0.png", frames.texture(0));
        assertEquals("other:a1.png", frames.texture(1));
        assertEquals(0f, frames.vOffset(1), "frame files don't move V");
        BakedCosmetic still = bake("{\"slot\":\"head\",\"texture\":\"t:a.png\",\"texture_size\":[16,16],"
                + "\"parts\":[{\"cubes\":[" + CUBE + "]}]}");
        assertArrayEquals(still.layers()[0].mesh().data(), cosmetic.layers()[0].mesh().data(), "UVs unchanged");
    }

    @Test
    void cosmeticAnimationOnlyAppliesToPartsWithItsTexture() {
        BakedCosmetic cosmetic = bake("{\"slot\":\"head\",\"texture\":\"t:a.png\",\"texture_animation\":{\"frames\":2},"
                + "\"parts\":[{\"cubes\":[" + CUBE + "]},{\"texture\":\"t:glow.png\",\"mode\":\"emissive\",\"cubes\":[" + CUBE + "]},"
                + "{\"texture\":\"t:b.png\",\"texture_animation\":{\"frames\":8},\"cubes\":[" + CUBE + "]}]}");
        assertEquals(3, cosmetic.layers().length);
        assertEquals(2, cosmetic.layers()[0].textureFrames().count());
        assertEquals(8, cosmetic.layers()[1].textureFrames().count());
        assertEquals("t:b.png", cosmetic.layers()[1].texture());
        assertNull(cosmetic.layers()[2].textureFrames(), "the glow overlay has its own still texture");
    }

    @Test
    void partsWithDifferentAnimationsGetTheirOwnLayers() {
        BakedCosmetic cosmetic = bake("{\"slot\":\"head\",\"texture\":\"t:a.png\",\"parts\":["
                + "{\"texture_animation\":{\"frames\":2},\"cubes\":[" + CUBE + "]},"
                + "{\"texture_animation\":{\"frames\":2},\"cubes\":[" + CUBE + "]},"
                + "{\"cubes\":[" + CUBE + "]}]}");
        assertEquals(2, cosmetic.layers().length, "equal animations share a layer");
        assertEquals(2 * 6, cosmetic.layers()[0].mesh().quadCount());
    }

    @Test
    void badAnimationsAreRejected() {
        String[] bad = {
                "{}",
                "{\"frames\":0}",
                "{\"frames\":2,\"frame_time\":0}",
                "{\"frames\":2,\"frame_time\":1.5}",
                "{\"frames\":2,\"frame_times\":[10]}",
                "{\"frames\":3,\"textures\":[\"a.png\",\"b.png\"]}",
                "{\"textures\":[\"Bad Name.png\"]}",
                "[]",
        };
        for (String animation : bad) {
            assertThrows(JsonParseException.class, () -> CosmeticParser.parse("t:x", new StringReader(
                    "{\"slot\":\"head\",\"texture\":\"t:a.png\",\"texture_animation\":" + animation
                            + ",\"parts\":[{\"cubes\":[" + CUBE + "]}]}")), animation);
        }
    }
}
