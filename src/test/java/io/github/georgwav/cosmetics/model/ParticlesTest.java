package io.github.georgwav.cosmetics.model;

import com.google.gson.JsonParseException;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The cosmetic file's {@code particles}: auras and trails. */
class ParticlesTest {
    private static CosmeticDefinition parse(String json) {
        return CosmeticParser.parse("t:x", new StringReader(json));
    }

    @Test
    void anAuraNeedsNoPartsAndNoTexture() {
        CosmeticDefinition aura = parse("{\"slot\":\"aura\",\"particles\":{\"texture\":\"minecraft:heart\"}}");
        assertEquals(CosmeticSlot.AURA, aura.slot());
        assertTrue(aura.parts().isEmpty());
        CosmeticDefinition.ParticleEmitter e = aura.particles().get(0);
        assertEquals(List.of("minecraft:heart"), e.sprites());
        // Defaults: 10 a second for 1-2 s, rising gently around the body, white, fading out.
        assertEquals(10f, e.rate());
        assertEquals(1f, e.minLife());
        assertEquals(2f, e.maxLife());
        assertArrayEquals(new float[]{0, 16, 0}, e.origin());
        assertArrayEquals(new float[]{0, 8, 0}, e.velocity());
        assertArrayEquals(new int[]{CosmeticDefinition.WHITE}, e.colors());
        assertEquals(1f, e.startAlpha());
        assertEquals(0f, e.endAlpha());
        assertEquals(CosmeticDefinition.ParticleTrigger.ALWAYS, e.when());
        assertEquals(0, CosmeticBaker.bake(aura).layers().length);
    }

    @Test
    void everySettingIsRead() {
        CosmeticDefinition cape = parse("{\"slot\":\"cape\",\"texture\":\"t:c.png\",\"parts\":[{\"cubes\":"
                + "[{\"from\":[-5,0,-1],\"to\":[5,16,0],\"uv\":[0,0]}]}],\"particles\":[{\"textures\":[\"spark_0\","
                + "\"spark_1\"],\"rate\":30,\"lifetime\":[0.5,0.25],\"origin\":[0,10,3],\"area\":[5,8,0],"
                + "\"velocity\":[0,-4,6],\"spread\":[1,1,1],\"gravity\":-3,\"drag\":0.4,\"size\":[3,1],"
                + "\"alpha\":[0.8,0.1],\"colors\":[\"#ff0000\",\"#00ff00\"],\"glow\":true,\"spin\":-45,"
                + "\"when\":\"moving\"}]}");
        CosmeticDefinition.ParticleEmitter e = cape.particles().get(0);
        assertEquals(List.of("t:spark_0", "t:spark_1"), e.sprites()); // the file's namespace
        assertEquals(30f, e.rate());
        assertEquals(0.25f, e.minLife()); // swapped into order
        assertEquals(0.5f, e.maxLife());
        assertArrayEquals(new float[]{0, 10, 3}, e.origin());
        assertArrayEquals(new float[]{0, -4, 6}, e.velocity());
        assertEquals(-3f, e.gravity());
        assertEquals(0.4f, e.drag());
        assertEquals(3f, e.startSize());
        assertEquals(1f, e.endSize());
        assertEquals(0.8f, e.startAlpha());
        assertArrayEquals(new int[]{0xFF0000, 0x00FF00}, e.colors());
        assertTrue(e.glow());
        assertEquals(45f, e.spin());
        assertEquals(CosmeticDefinition.ParticleTrigger.MOVING, e.when());
        assertEquals(1, cape.parts().size());
    }

    @Test
    void mistakesAreReported() {
        assertThrows(JsonParseException.class, () -> parse("{\"slot\":\"aura\",\"particles\":{}}"));
        assertThrows(JsonParseException.class, () -> parse(
                "{\"slot\":\"aura\",\"particles\":{\"texture\":\"x\",\"rate\":100000}}"));
        assertThrows(JsonParseException.class, () -> parse(
                "{\"slot\":\"aura\",\"particles\":{\"texture\":\"x\",\"alpha\":[2,0]}}"));
        assertThrows(JsonParseException.class, () -> parse(
                "{\"slot\":\"aura\",\"particles\":{\"texture\":\"x\",\"when\":\"sometimes\"}}"));
        assertThrows(JsonParseException.class, () -> parse("{\"slot\":\"aura\",\"texture\":\"t:a.png\"}"));
    }
}
