package io.github.georgwav.cosmetics.sol.sky;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

class StarCatalogTest {
    private static StarCatalog read() throws IOException {
        try (InputStream s = StarCatalogTest.class.getResourceAsStream("/assets/cosmeticsmod/sky/stars.dat");
             InputStream c = StarCatalogTest.class.getResourceAsStream("/assets/cosmeticsmod/sky/constellations.dat")) {
            assertNotNull(s);
            assertNotNull(c);
            return StarCatalog.read(s, c);
        }
    }

    private static int find(StarCatalog cat, String name) {
        for (int i = 0; i < cat.size(); i++) {
            if (name.equals(cat.name[i])) {
                return i;
            }
        }
        return -1;
    }

    @Test
    void everyStarDownToMagnitudeSixAndAHalf() throws IOException {
        StarCatalog cat = read();
        assertTrue(cat.size() > 8500 && cat.size() < 9500, "about 9,100 stars: " + cat.size());
        for (int i = 1; i < cat.size(); i++) {
            assertTrue(cat.mag[i] >= cat.mag[i - 1], "brightest first");
            float len = cat.x[i] * cat.x[i] + cat.y[i] * cat.y[i] + cat.z[i] * cat.z[i];
            assertEquals(1f, len, 1e-4f);
        }
        assertTrue(cat.mag[cat.size() - 1] <= 6.5f);
        assertEquals("Sirius", cat.name[0]);
        assertEquals(-1.44f, cat.mag[0], 0.05f);
    }

    @Test
    void realPlacesAndColours() throws IOException {
        StarCatalog cat = read();
        int polaris = find(cat, "Polaris");
        assertTrue(polaris >= 0);
        assertTrue(cat.z[polaris] > 0.999f, "at the pole");
        int betelgeuse = find(cat, "Betelgeuse"), rigel = find(cat, "Rigel");
        assertTrue(betelgeuse >= 0 && rigel >= 0);
        int b = cat.color[betelgeuse], r = cat.color[rigel];
        assertTrue((b >> 16 & 255) > (b & 255), "Betelgeuse is red");
        assertTrue((r & 255) > (r >> 16 & 255), "Rigel is blue");
    }

    @Test
    void theConstellations() throws IOException {
        StarCatalog cat = read();
        // The lab's 21, the zodiac and the northern sky.
        assertTrue(cat.constellations.size() >= 40);
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (StarCatalog.Constellation c : cat.constellations) {
            assertTrue(c.segments() > 0, c.id());
            ids.add(c.id());
            float[] l = c.lines();
            for (int i = 0; i < l.length; i += 3) {
                assertEquals(1f, l[i] * l[i] + l[i + 1] * l[i + 1] + l[i + 2] * l[i + 2], 1e-4f);
            }
        }
        for (String id : new String[]{"Ori", "Tau", "Gem", "Aur", "CMa", "CMi", "UMa", "UMi", "Cas", "Cyg", "Lyr", "Aql", "Sco",
                "Sgr", "Leo", "Boo", "CrB", "Peg", "And", "Per", "Ari", "Cnc", "Vir", "Lib", "Cap", "Aqr", "Psc", "Dra", "Her"}) {
            assertTrue(ids.contains(id), id);
        }
    }

    @Test
    void colourByIndex() {
        assertEquals(0xFFA0BEFF, StarCatalog.bvColor(-1f));
        assertEquals(0xFFFFA674, StarCatalog.bvColor(3f));
        assertEquals(0xFFF6F6FF, StarCatalog.bvColor(0.35f));
    }
}
