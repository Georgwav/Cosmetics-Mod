package io.github.georgwav.cosmetics.client.skins;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reading the capes out of Mojang's profile response. */
class MojangCapesTest {
    @Test
    void readsCapesAndTheWornOne() {
        String json = """
                {"id":"0123","name":"Steve","skins":[{"id":"s","state":"ACTIVE","url":"http://textures.minecraft.net/texture/a"}],
                 "capes":[{"id":"c1","state":"INACTIVE","url":"http://textures.minecraft.net/texture/b","alias":"Migrator"},
                          {"id":"c2","state":"ACTIVE","url":"http://textures.minecraft.net/texture/c","alias":"Vanilla"}]}""";
        List<MojangCapes.Cape> capes = MojangCapes.parse(json);
        assertEquals(2, capes.size());
        assertEquals("Migrator", capes.get(0).alias());
        assertFalse(capes.get(0).active());
        assertTrue(capes.get(1).active());
        assertEquals("c2", capes.get(1).id());
    }

    @Test
    void skipsWhatItCantUse() {
        // No capes at all, a broken entry, an entry without a URL, and one without a name (named by its id).
        assertTrue(MojangCapes.parse("{\"id\":\"x\"}").isEmpty());
        assertTrue(MojangCapes.parse("[]").isEmpty());
        List<MojangCapes.Cape> capes = MojangCapes.parse(
                "{\"capes\":[1,{\"id\":\"a\"},{\"id\":\"b\",\"url\":\"http://t/b\"}]}");
        assertEquals(1, capes.size());
        assertEquals("b", capes.get(0).alias());
    }
}
