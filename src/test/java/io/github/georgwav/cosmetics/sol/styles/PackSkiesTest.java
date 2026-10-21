package io.github.georgwav.cosmetics.sol.styles;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The sky properties parser (B.4): OptiFine's layers and fades, FabricSkyboxes' skies, and the box's faces. */
class PackSkiesTest {
    @Test
    void optifineLayersReadInOrderWithTheirSources() {
        Map<String, String> files = Map.of(
                "optifine/sky/world0/sky1.properties", "source=./stars.png\nstartFadeIn=17:00\nendFadeIn=18:00\nendFadeOut=6:00\nblend=add\n",
                "optifine/sky/world0/sky2.properties", "source=~/sky/clouds.png\nblend=alpha\nrotate=false\nweather=clear rain\n",
                "optifine/sky/world0/stars.png", "x", "optifine/sky/clouds.png", "x");
        List<PackSkies.Layer> layers = PackSkies.read(files::get, files::containsKey, List.copyOf(files.keySet()));
        assertEquals(2, layers.size(), "sky0 can be missing; the list goes on until one is");
        PackSkies.Layer stars = layers.get(0);
        assertEquals(List.of("optifine/sky/world0/stars.png"), stars.textures());
        assertTrue(stars.additive() && stars.rotate());
        // 17:00 is 11,000 ticks; the fade out takes as long as the fade in (an hour) and ends at 6:00 (0).
        assertArrayEquals(new int[]{11000, 12000, 23000, 0}, stars.fade());
        PackSkies.Layer clouds = layers.get(1);
        assertEquals(List.of("optifine/sky/clouds.png"), clouds.textures());
        assertFalse(clouds.additive() || clouds.rotate());
        assertEquals(List.of("clear", "rain"), clouds.weather());
    }

    @Test
    void fadesInAndOutRoundMidnight() {
        int[] fade = {11000, 12000, 23000, 0};
        assertEquals(0f, PackSkies.brightness(fade, 6000L), "noon: gone");
        assertEquals(0.5f, PackSkies.brightness(fade, 11500L), 0.01f);
        assertEquals(1f, PackSkies.brightness(fade, 18000L), "midnight: full");
        assertEquals(0.5f, PackSkies.brightness(fade, 23500L), 0.01f);
        assertEquals(1f, PackSkies.brightness(null, 6000L), "no times: always on");
        assertEquals(-1, PackSkies.ticks("noon"));
        assertEquals(6000, PackSkies.ticks("12:00"));
    }

    @Test
    void fabricSkyboxesSixFacesSpriteAndColour() {
        PackSkies.Layer six = PackSkies.fabric("""
                {"schemaVersion":2,"type":"square-textured","properties":{"fade":{"startFadeIn":12000,"endFadeIn":13000,
                "startFadeOut":23000,"endFadeOut":24000},"maxAlpha":0.8},
                "textures":{"north":"minecraft:sky/n.png","south":"sky:s.png","east":"e.png","west":"w.png","top":"t.png","bottom":"b.png"},
                "blend":{"type":"add"}}""");
        assertTrue(six.sixFaces());
        assertEquals("b.png", six.textures().get(PackSkies.BOTTOM));
        assertEquals("sky/n.png", six.textures().get(PackSkies.NORTH));
        assertTrue(six.additive());
        assertEquals(0.8f, six.maxAlpha());
        PackSkies.Layer sprite = PackSkies.fabric("{\"type\":\"single-sprite-square-textured\",\"texture\":\"minecraft:textures/sky/box.png\"}");
        assertEquals(List.of("textures/sky/box.png"), sprite.textures());
        PackSkies.Layer mono = PackSkies.fabric("{\"type\":\"monocolor\",\"color\":{\"red\":1,\"green\":0,\"blue\":0,\"alpha\":1}}");
        assertEquals(0xFFFF0000, mono.colour());
        assertNull(PackSkies.fabric("not json"));
    }

    @Test
    void theBoxFacesPointTheRightWay() {
        float[] c = new float[3];
        // Each face's middle points out through its side.
        PackSkies.corner(PackSkies.TOP, 0.5f, 0.5f, c);
        assertArrayEquals(new float[]{0f, 1f, 0f}, c, 1e-6f);
        PackSkies.corner(PackSkies.BOTTOM, 0.5f, 0.5f, c);
        assertArrayEquals(new float[]{0f, -1f, 0f}, c, 1e-6f);
        PackSkies.corner(PackSkies.NORTH, 0.5f, 0.5f, c);
        assertArrayEquals(new float[]{0f, 0f, -1f}, c, 1e-6f);
        PackSkies.corner(PackSkies.EAST, 0.5f, 0.5f, c);
        assertArrayEquals(new float[]{1f, 0f, 0f}, c, 1e-6f);
        // A side's texture runs top to bottom down the sky (v grows downward).
        PackSkies.corner(PackSkies.SOUTH, 0.5f, 0f, c);
        assertEquals(1f, c[1], 1e-6f);
        // Round the horizon the picture runs on without a jump: north's right edge is east's left edge.
        float[] a = new float[3], b = new float[3];
        PackSkies.corner(PackSkies.NORTH, 1f, 0.3f, a);
        PackSkies.corner(PackSkies.EAST, 0f, 0.3f, b);
        assertArrayEquals(a, b, 1e-6f);
        PackSkies.corner(PackSkies.EAST, 1f, 0.3f, a);
        PackSkies.corner(PackSkies.SOUTH, 0f, 0.3f, b);
        assertArrayEquals(a, b, 1e-6f);
    }
}
