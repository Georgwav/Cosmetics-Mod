package io.github.georgwav.cosmetics.model;

import com.google.gson.JsonParseException;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The cosmetic file's {@code options}, {@code default_color} and per-part {@code tint}. */
class CosmeticOptionsTest {
    private static final String CUBE = "{\"from\":[0,0,0],\"to\":[1,1,1],\"uv\":[0,0]}";

    private static BakedCosmetic bake(String extra, String parts) {
        return CosmeticBaker.bake(CosmeticParser.parse("t:x", new StringReader(
                "{\"slot\":\"head\",\"texture\":\"t:a.png\"" + extra + ",\"parts\":" + parts + "}")));
    }

    @Test
    void everythingIsAllowedByDefault() {
        BakedCosmetic cosmetic = bake("", "[{\"cubes\":[" + CUBE + "]}]");
        for (CosmeticOption option : CosmeticOption.values()) {
            assertTrue(cosmetic.allows(option), option.id());
        }
        assertEquals(CosmeticDefinition.WHITE, cosmetic.defaultColor());
        assertTrue(cosmetic.layers()[0].tinted());
    }

    @Test
    void optionsCanBeTurnedOff() {
        BakedCosmetic cosmetic = bake(",\"options\":{\"rainbow\":false,\"saturation\":false,\"size\":true}",
                "[{\"cubes\":[" + CUBE + "]}]");
        assertFalse(cosmetic.allows(CosmeticOption.RAINBOW));
        assertFalse(cosmetic.allows(CosmeticOption.SATURATION));
        assertTrue(cosmetic.allows(CosmeticOption.SIZE));
        assertTrue(cosmetic.allows(CosmeticOption.TINT));
    }

    @Test
    void aHeldCosmeticCanFaceTheOtherWayInFirstPerson() {
        String cube = "[{\"cubes\":[" + CUBE + "]}]";
        assertFalse(bake("", cube).definition().firstPersonTurned());
        assertTrue(bake(",\"first_person_turned\":true", cube).definition().firstPersonTurned());
        assertThrows(JsonParseException.class, () -> bake(",\"first_person_turned\":\"yes\"", cube));
    }

    @Test
    void defaultColour() {
        assertEquals(0xFF8800, bake(",\"default_color\":\"#ff8800\"", "[{\"cubes\":[" + CUBE + "]}]").defaultColor());
        assertEquals(0x00FF00, bake(",\"default_color\":65280", "[{\"cubes\":[" + CUBE + "]}]").defaultColor());
    }

    @Test
    void onlyTintedPartsTakeTheColour() {
        BakedCosmetic cosmetic = bake("", "[{\"cubes\":[" + CUBE + "]},{\"tint\":false,\"cubes\":[" + CUBE + "]},"
                + "{\"tint\":true,\"cubes\":[" + CUBE + "]}]");
        assertEquals(2, cosmetic.layers().length, "tinted and untinted parts can't share a layer");
        assertTrue(cosmetic.layers()[0].tinted());
        assertEquals(12, cosmetic.layers()[0].mesh().quadCount(), "the two tinted parts share one");
        assertFalse(cosmetic.layers()[1].tinted());
    }

    @Test
    void kindIsTheSlotOrWingsUnlessTheFileSaysOtherwise() {
        String cube = "[{\"cubes\":[" + CUBE + "]}]";
        assertEquals("head", bake("", cube).kind());
        assertEquals("ears", bake(",\"kind\":\"Ears\"", cube).kind());
        BakedCosmetic wings = CosmeticBaker.bake(CosmeticParser.parse("t:w", new StringReader(
                "{\"slot\":\"back\",\"texture\":\"t:a.png\",\"wings\":{},\"parts\":" + cube + "}")));
        assertEquals("wings", wings.kind());
        assertThrows(JsonParseException.class, () -> bake(",\"kind\":\"not ok!\"", cube));
    }

    @Test
    void petPositionComesFromTheFile() {
        String cube = "[{\"cubes\":[" + CUBE + "]}]";
        assertEquals(PetPosition.LEFT_SHOULDER, bake("", cube).definition().petPosition());
        assertEquals(PetPosition.FLOATING, bake(",\"pet_position\":\"floating\"", cube).definition().petPosition());
        assertEquals(PetPosition.HEAD, bake(",\"pet_position\":\"head\"", cube).definition().petPosition());
        assertThrows(JsonParseException.class, () -> bake(",\"pet_position\":\"tail\"", cube));
        assertEquals("air", PetPosition.FLOATING.preferredStyle());
        assertEquals("shoulder", PetPosition.RIGHT_SHOULDER.preferredStyle());
    }

    @Test
    void badValuesAreRejected() {
        String[] bad = {
                ",\"options\":{\"chroma\":false}",
                ",\"options\":{\"rainbow\":\"no\"}",
                ",\"options\":[]",
                ",\"default_color\":\"orange\"",
                ",\"default_color\":\"#12345\"",
                ",\"default_color\":-1",
                ",\"default_color\":16777216",
        };
        for (String extra : bad) {
            assertThrows(JsonParseException.class, () -> bake(extra, "[{\"cubes\":[" + CUBE + "]}]"), extra);
        }
        assertThrows(JsonParseException.class, () -> bake("", "[{\"tint\":1,\"cubes\":[" + CUBE + "]}]"));
    }
}
