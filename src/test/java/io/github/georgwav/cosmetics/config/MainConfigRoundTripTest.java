package io.github.georgwav.cosmetics.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * A cosmeticsmod.json written by main's own CosmeticsConfig (main at 8282e47, with every setting away from its
 * default: swaps, outfits with their settings, both emote wheels with pages, slot priorities, other players'
 * cosmetics) loads in Sol Client and is written back byte for byte: switching to Sol Client loses nothing, and
 * switching back finds the file as it was.
 */
class MainConfigRoundTripTest {
    /** The Gson main writes the file with. */
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Test
    void aFileFromMainLoadsAndSavesUnchanged() throws IOException {
        String file;
        try (InputStream in = MainConfigRoundTripTest.class.getResourceAsStream("main-cosmeticsmod.json")) {
            assertNotNull(in, "fixture");
            file = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        // As CosmeticsConfig.load() and save() do.
        CosmeticsConfig loaded = GSON.fromJson(file, CosmeticsConfig.class);
        loaded.sanitize();
        assertEquals(file, GSON.toJson(loaded));
    }

    @Test
    void followingTheThemeIsNeverWrittenUnlessPicked() {
        CosmeticsConfig c = new CosmeticsConfig();
        c.editOptions("cosmeticsmod:halo").scale = 1.5f;
        String json = GSON.toJson(c);
        assertEquals(-1, json.indexOf("themeColor"));
        c.editOptions("cosmeticsmod:halo").themeColor = "accent";
        CosmeticsConfig back = GSON.fromJson(GSON.toJson(c), CosmeticsConfig.class);
        back.sanitize();
        assertEquals("accent", back.options("cosmeticsmod:halo").themeColor);
        back.editOptions("cosmeticsmod:halo").themeColor = "purple";
        back.sanitize();
        assertEquals(null, back.options("cosmeticsmod:halo").themeColor, "only accent or glow");
    }
}
