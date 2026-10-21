package io.github.georgwav.cosmetics.client;

import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingPixelsTest {
    private static long count(String v) {
        return v.chars().filter(c -> c == '1').count();
    }

    @Test
    void savesAndReadsBackAndCleansWhatItReads() {
        Setting.Pixels p = new Setting.Pixels("shape", 3, "100010001");
        assertTrue(p.at(0, 0) && p.at(1, 1) && p.at(2, 2));
        assertFalse(p.at(1, 0));
        assertFalse(p.at(-1, 0), "outside is off");
        assertEquals("100010001", p.save().getAsString());
        p.load(new JsonPrimitive("11x"));
        assertEquals("110000000", p.get(), "anything but a 1 is off, and it's padded to its size");
        p.load(new JsonPrimitive("1111111111111"));
        assertEquals(9, p.get().length(), "and cut to its size");
    }

    @Test
    void theBrushAndTheEraserChangeOnePixel() {
        String v = "000000000";
        v = Setting.Pixels.with(v, 3, 1, 1, true);
        assertEquals("000010000", v);
        v = Setting.Pixels.with(v, 3, 1, 1, false);
        assertEquals("000000000", v);
        assertEquals(v, Setting.Pixels.with(v, 3, 5, 1, true), "outside the picture nothing changes");
    }

    @Test
    void theFillStopsAtOnPixelsAndTheEdges() {
        // A ring round the middle of a 5 by 5: filling inside fills only the middle; outside, the rest.
        String ring = "00000" + "01110" + "01010" + "01110" + "00000";
        String inside = Setting.Pixels.filled(ring, 5, 2, 2);
        assertEquals(count(ring) + 1, count(inside));
        String outside = Setting.Pixels.filled(ring, 5, 0, 0);
        assertEquals(24, count(outside), "everything but the hole in the middle");
        assertEquals(ring, Setting.Pixels.filled(ring, 5, 1, 1), "filling on an on pixel changes nothing");
        assertEquals(25, count(Setting.Pixels.filled("0".repeat(25), 5, 4, 4)), "an empty picture fills completely");
    }
}
