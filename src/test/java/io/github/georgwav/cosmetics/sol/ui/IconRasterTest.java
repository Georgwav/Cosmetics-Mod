package io.github.georgwav.cosmetics.sol.ui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.georgwav.cosmetics.sol.ui.kit.IconRaster;
import io.github.georgwav.cosmetics.sol.ui.kit.ModuleIcons;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class IconRasterTest {
    static JsonObject icons() throws IOException {
        Path p = Path.of("src/main/resources/assets/cosmeticsmod/sol/icons.json");
        if (!Files.exists(p)) {
            p = Path.of("../../").resolve(p);
        }
        return JsonParser.parseString(Files.readString(p)).getAsJsonObject();
    }

    @Test
    void everyModuleAndSectionIconIsThere() throws IOException {
        JsonObject icons = icons();
        for (String name : Set.of("grid4", "hud", "sun", "shirt", "note", "puzzle", "gear", "search", "sword", "burst",
                "hitbox", "hand", "cross", "tag", "zoom", "eye", "lookat", "screen", "dots", "bulb", "cube", "item", "grid",
                "heart", "bars", "compass", "shield", "potion", "key", "run", "chat", "chest", "camera", "bolt", "people",
                "star", "map", "mic", "ring", "crown", "cape", "wings", "orbit", "flame", "sparkle", "check", "close")) {
            assertTrue(icons.has(name), name);
        }
    }

    @Test
    void everyModuleHasAnIconOfItsOwn() throws IOException {
        JsonObject icons = icons();
        assertEquals(32, ModuleIcons.MODULES.size());
        java.util.Map<String, String> seen = new java.util.HashMap<>();
        for (var e : ModuleIcons.MODULES.entrySet()) {
            assertTrue(icons.has(e.getValue()), e.getKey() + " -> " + e.getValue());
            String other = seen.put(e.getValue(), e.getKey());
            assertNull(other, e.getKey() + " and " + other + " share " + e.getValue());
        }
        for (var e : ModuleIcons.EARLIER.entrySet()) {
            assertTrue(icons.has(e.getValue()), e.getKey() + " -> " + e.getValue());
        }
        for (String name : ModuleIcons.KEPT.values()) {
            assertTrue(icons.has(name), name);
        }
        for (String name : ModuleIcons.SECTIONS.values()) {
            assertTrue(icons.has(name), name);
        }
    }

    @Test
    void everyIconDrawsLinesInsideItsSquareAndTheSoftFillStaysInside() throws IOException {
        JsonObject icons = icons();
        int px = 32;
        float[] fill = new float[px * px], stroke = new float[px * px];
        for (String name : icons.keySet()) {
            IconRaster.Shape s = IconRaster.parse(icons.getAsJsonArray(name));
            IconRaster.raster(s, px, fill, stroke);
            float lines = 0f, soft = 0f;
            for (int i = 0; i < px * px; i++) {
                lines += stroke[i];
                soft += fill[i];
                assertTrue(stroke[i] >= 0f && stroke[i] <= 1f && fill[i] >= 0f && fill[i] <= 1f);
            }
            assertTrue(lines > px * 0.8f, name + " draws lines");
            assertTrue(soft < px * px * 0.6f, name + "'s soft fill doesn't flood the square");
        }
    }

    @Test
    void theStrokeIsOneAndThreeQuarterUnitsWide() throws IOException {
        // "close" is two diagonal lines: at 48 px (2 px a unit) a horizontal cut through the middle row crosses
        // each line once, about 1.75 × 2 × √2 ≈ 5 px wide.
        IconRaster.Shape s = IconRaster.parse(icons().getAsJsonArray("check"));
        int px = 48;
        float[] fill = new float[px * px], stroke = new float[px * px];
        IconRaster.raster(s, px, fill, stroke);
        float sum = 0f;
        for (int x = 0; x < px; x++) {
            sum += stroke[24 * px + x];
        }
        assertTrue(sum > 3f && sum < 9f, "coverage across the middle row: " + sum);
    }

    @Test
    void grid4HasItsSoftFillInTheTopRightSquareOnly() throws IOException {
        IconRaster.Shape s = IconRaster.parse(icons().getAsJsonArray("grid4"));
        int px = 24;
        float[] fill = new float[px * px], stroke = new float[px * px];
        IconRaster.raster(s, px, fill, stroke);
        assertTrue(fill[7 * px + 17] > 0.9f, "inside the top-right square");
        assertEquals(0f, fill[7 * px + 7], 1e-6, "not in the top-left one");
        assertEquals(0f, fill[17 * px + 17], 1e-6, "nor the bottom-right");
    }
}
