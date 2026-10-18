package io.github.georgwav.cosmetics.sol.sky;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MoonLooksTest {
    private static final String[] IDS = {"silver", "newmoon", "earthshine", "harvest", "supermoon", "halo", "corona", "blue",
            "blood", "ember", "pink", "honey", "carian", "falling"};
    private static final List<String> problems = new ArrayList<>();

    @BeforeAll
    static void load() throws IOException {
        Map<String, String> files = new LinkedHashMap<>();
        for (String id : IDS) {
            try (InputStream in = MoonLooksTest.class.getResourceAsStream("/assets/cosmeticsmod/moon_looks/" + id + ".json")) {
                assertNotNull(in, id);
                files.put(id, new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        MoonLooks.load(files, (id, m) -> problems.add(id + ": " + m));
    }

    @Test
    void everyLookReadsCleanly() {
        assertEquals(List.of(), problems);
        assertEquals(IDS.length, MoonLooks.all().size());
        assertTrue(MoonLooks.find("blood").eclipse());
        assertTrue(MoonLooks.find("newmoon").hidden());
        assertEquals("carian", MoonLooks.find("carian").body());
        assertEquals(1.3f, MoonLooks.find("supermoon").size(), 1e-6f);
        assertEquals("silver", MoonLooks.find("no such look").id(), "an unknown look is the silver moon");
    }

    @Test
    void aThemesOwnLookOnAnOrdinaryNight() {
        // A waning crescent: no special moon allows it but earthshine, which needs its night.
        int ordinary = 0;
        for (long night = 0; night < 64; night++) {
            MoonLook l = MoonLooks.tonight(night, 3, "honey");
            assertTrue(l.id().equals("honey") || l.id().equals("earthshine"), l.id());
            ordinary += l.id().equals("honey") ? 1 : 0;
        }
        assertTrue(ordinary > 40, "mostly the theme's own: " + ordinary);
    }

    @Test
    void specialMoonsComeOnlyOnTheirPhasesAndNights() {
        int blood = 0, blue = 0;
        for (long night = 0; night < 4000; night++) {
            for (int phase = 0; phase < 8; phase++) {
                MoonLook l = MoonLooks.tonight(night, phase, "silver");
                if (l.id().equals("blood")) {
                    blood++;
                    assertEquals(0, phase, "a blood moon is a full moon");
                }
                if (l.id().equals("blue")) {
                    blue++;
                    assertEquals(0, phase);
                }
            }
        }
        assertTrue(blood > 5 && blood < 80, "blood moons now and then: " + blood);
        assertTrue(blue < blood, "blue moons are rarer");
        assertEquals(MoonLooks.tonight(1234, 0, "silver").id(), MoonLooks.tonight(1234, 0, "silver").id(), "the same night, the same moon");
    }

    @Test
    void badValuesFallBack() {
        List<String> warn = new ArrayList<>();
        MoonLook l = MoonLook.parse("x", "{\"size\": 99, \"body\": \"pluto\", \"validPhases\": [\"blue_moon\"], \"lightColor\": \"nope\"}",
                warn::add);
        assertEquals(4f, l.size(), 1e-6f, "clamped");
        assertEquals("moon", l.body());
        assertNull(l.lightColor());
        assertEquals(3, warn.size(), warn.toString());
        assertEquals("silver", MoonLook.parse("y", "not json", m -> { }).body().equals("moon") ? "silver" : "?");
    }

    @Test
    void theNightChangesAtSunrise() {
        SkySphere s = new SkySphere();
        s.update(10.0 + 0.24, -0.26f, 0, 34, 96);
        long before = s.night;
        s.update(10.0 + 0.26, -0.24f, 1, 34, 96);
        assertEquals(before + 1, s.night, "a new night after sunrise");
        s.update(10.0 + 0.75, 0.5f, 1, 34, 96);
        assertEquals(before + 1, s.night, "the same night at midnight");
    }
}
